package com.krdevops.springai.service;

import org.springframework.stereotype.Service;

@Service
public class SecurityTemplateService {

    public String getSecurityTemplate(String securityType, String packageName, String egovVersion) {
        String pkg = (packageName == null || packageName.isBlank())
                     ? "egovframework.let.sample" : packageName;
        String ver = (egovVersion == null || egovVersion.isBlank())
                     ? "5.0" : egovVersion.trim();
        return switch (securityType.toLowerCase()) {
            case "webxmlfilter"       -> webXmlFilter();
            case "contextsecurity"    -> contextSecurity(ver);
            case "securitymapper"     -> securityMapper();
            case "javaconfig"         -> ver.startsWith("4") ? javaConfig43(pkg) : javaConfig50(pkg);
            case "userdetailsservice" -> userDetailsService(pkg);
            case "rolehierarchy"      -> roleHierarchy(pkg);
            case "loginpage"          -> loginPage();
            default                   -> unsupported(securityType);
        };
    }

    // -------------------------------------------------------------------------
    // 1. 레거시 XML 방식
    // -------------------------------------------------------------------------

    private String webXmlFilter() {
        return """
                <!-- ============================================================
                     web.xml — DelegatingFilterProxy 설정
                     eGovFrame Security 진입점
                     모든 HTTP 요청을 Spring Security Filter Chain으로 위임
                ============================================================ -->

                <!-- CharacterEncodingFilter: Security 필터보다 앞에 위치 -->
                <filter>
                    <filter-name>encodingFilter</filter-name>
                    <filter-class>
                        org.springframework.web.filter.CharacterEncodingFilter
                    </filter-class>
                    <init-param>
                        <param-name>encoding</param-name>
                        <param-value>UTF-8</param-value>
                    </init-param>
                    <init-param>
                        <param-name>forceEncoding</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
                <filter-mapping>
                    <filter-name>encodingFilter</filter-name>
                    <url-pattern>/*</url-pattern>
                </filter-mapping>

                <!-- Spring Security Filter Chain (DelegatingFilterProxy) -->
                <filter>
                    <filter-name>springSecurityFilterChain</filter-name>
                    <filter-class>
                        org.springframework.web.filter.DelegatingFilterProxy
                    </filter-class>
                </filter>
                <filter-mapping>
                    <filter-name>springSecurityFilterChain</filter-name>
                    <url-pattern>/*</url-pattern>
                </filter-mapping>

                <!-- context-security.xml ApplicationContext 로드 -->
                <context-param>
                    <param-name>contextConfigLocation</param-name>
                    <param-value>
                        classpath*:egovframework/spring/context-*.xml
                        classpath*:egovframework/spring/context-security.xml
                    </param-value>
                </context-param>
                """;
    }

    private String contextSecurity(String ver) {
        return ver.startsWith("4") ? contextSecurity43() : contextSecurity50();
    }

    private String contextSecurity43() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans:beans xmlns="http://www.springframework.org/schema/security"
                    xmlns:beans="http://www.springframework.org/schema/beans"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="
                        http://www.springframework.org/schema/beans
                        http://www.springframework.org/schema/beans/spring-beans.xsd
                        http://www.springframework.org/schema/security
                        http://www.springframework.org/schema/security/spring-security.xsd">

                    <!--
                    ============================================================
                    eGovFrame 5.x Spring Security 설정
                    구조: DelegatingFilterProxy → springSecurityFilterChain → DB 인증
                    인증: JDBC DB 기반 (COMTNEMPLYRINFO)
                    권한: ROLE 기반 (COMTNROLEINFO → URL 패턴 매칭)
                    세션: Session 기반 유지 (공공 SI 레거시 호환)
                    ============================================================
                    -->

                    <!-- 1. 정적 자원 접근 제외 (Security 필터 미적용) -->
                    <http pattern="/css/**"    security="none"/>
                    <http pattern="/images/**" security="none"/>
                    <http pattern="/js/**"     security="none"/>
                    <http pattern="/favicon.ico" security="none"/>

                    <!-- 2. 메인 Security 설정 -->
                    <http auto-config="false"
                          use-expressions="true"
                          access-decision-manager-ref="accessDecisionManager">

                        <!-- 2-1. 익명 접근 허용 URL -->
                        <intercept-url pattern="/uat/uia/**"           access="IS_AUTHENTICATED_ANONYMOUSLY"/>
                        <intercept-url pattern="/cmm/fms/FileDown.do"  access="IS_AUTHENTICATED_ANONYMOUSLY"/>
                        <intercept-url pattern="/sym/ccm/zip/**"        access="IS_AUTHENTICATED_ANONYMOUSLY"/>

                        <!-- 2-2. 나머지는 DB 기반 동적 접근 제어
                             EgovFilterInvocationSecurityMetadataSource 가 COMTNROLEINFO 로드 -->

                        <!-- 2-3. 로그인 설정 -->
                        <form-login
                            login-page="/uat/uia/egovLoginUsr.do"
                            login-processing-url="/uat/uia/actionLogin.do"
                            default-target-url="/index.jsp"
                            authentication-failure-url="/uat/uia/egovLoginUsr.do?login_error=1"
                            authentication-success-handler-ref="loginSuccessHandler"
                            authentication-failure-handler-ref="loginFailureHandler"/>

                        <!-- 2-4. 로그아웃 설정 -->
                        <logout
                            logout-url="/uat/uia/actionLogout.do"
                            logout-success-url="/index.jsp"
                            invalidate-session="true"/>

                        <!-- 2-5. 세션 관리 (Session 기반 — 공공 SI 표준) -->
                        <session-management
                            session-fixation-protection="newSession"
                            invalid-session-url="/uat/uia/egovLoginUsr.do">
                            <concurrency-control
                                max-sessions="1"
                                error-if-maximum-exceeded="false"
                                expired-url="/uat/uia/egovLoginUsr.do?expired=1"/>
                        </session-management>

                        <!-- 2-6. 접근 거부 처리 -->
                        <access-denied-handler ref="accessDeniedHandler"/>

                        <!-- 2-7. CSRF 활성화 (JSP Form 기반이므로 필수) -->
                        <csrf/>

                        <!-- 2-8. DB 기반 동적 URL 접근 제어 필터 -->
                        <custom-filter ref="egovSecurityFilter" before="FILTER_SECURITY_INTERCEPTOR"/>

                    </http>

                    <!-- 3. 인증 관리자 -->
                    <authentication-manager alias="authenticationManager">
                        <authentication-provider ref="egovAuthenticationProvider"/>
                    </authentication-manager>

                    <!-- 4. eGovFrame AuthenticationProvider -->
                    <beans:bean id="egovAuthenticationProvider"
                        class="egovframework.rte.fdl.security.userdetails.EgovUserDetailsHelper">
                        <beans:property name="userDetailsService" ref="egovUserDetailsService"/>
                        <beans:property name="passwordEncoder"    ref="passwordEncoder"/>
                    </beans:bean>

                    <!-- 5. 패스워드 인코더 (BCrypt 권장; 레거시는 SHA256PasswordEncoder) -->
                    <beans:bean id="passwordEncoder"
                        class="org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder"/>

                    <!-- 6. DB 기반 동적 URL 접근 제어 필터 -->
                    <beans:bean id="egovSecurityFilter"
                        class="org.springframework.security.web.access.intercept.FilterSecurityInterceptor">
                        <beans:property name="authenticationManager" ref="authenticationManager"/>
                        <beans:property name="accessDecisionManager" ref="accessDecisionManager"/>
                        <beans:property name="securityMetadataSource" ref="egovSecurityMetadataSource"/>
                    </beans:bean>

                    <!-- 7. COMTNROLEINFO 기반 URL 패턴 로드
                         서버 시작 시 COMTNROLEINFO 전체 조회
                         URL 패턴(ROLE_PTTRN) → 필요 ROLE(AUTHOR_CODE) Map 구성 -->
                    <beans:bean id="egovSecurityMetadataSource"
                        class="egovframework.rte.fdl.security.intercept.EgovReloadableFilterInvocationSecurityMetadataSource">
                        <beans:constructor-arg ref="dataSource"/>
                        <beans:property name="roleHierarchy" ref="roleHierarchy"/>
                    </beans:bean>

                    <!-- 8. RoleHierarchy 적용 접근 결정 관리자 -->
                    <beans:bean id="accessDecisionManager"
                        class="org.springframework.security.access.vote.AffirmativeBased">
                        <beans:constructor-arg>
                            <beans:list>
                                <beans:bean class="org.springframework.security.access.vote.RoleHierarchyVoter">
                                    <beans:constructor-arg ref="roleHierarchy"/>
                                </beans:bean>
                                <beans:bean class="org.springframework.security.access.vote.WebExpressionVoter"/>
                                <beans:bean class="org.springframework.security.access.vote.AuthenticatedVoter"/>
                            </beans:list>
                        </beans:constructor-arg>
                    </beans:bean>

                    <!-- 9. ROLE 계층 구조 (COMTNROLES_HIERARCHY 기반)
                         ROLE_USER > ROLE_ADMIN (상위가 하위 권한 자동 상속)
                         실제 DB 값: ROLE_USER > ROLE_ADMIN,
                                    IS_AUTHENTICATED_FULLY > IS_AUTHENTICATED_REMEMBERED 등 -->
                    <beans:bean id="roleHierarchy"
                        class="org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl">
                        <beans:property name="hierarchy">
                            <beans:value>
                                ROLE_USER > ROLE_ADMIN
                                IS_AUTHENTICATED_FULLY > IS_AUTHENTICATED_REMEMBERED
                                IS_AUTHENTICATED_REMEMBERED > IS_AUTHENTICATED_ANONYMOUSLY
                            </beans:value>
                        </beans:property>
                    </beans:bean>

                    <!-- 10. 로그인 성공/실패 핸들러 -->
                    <beans:bean id="loginSuccessHandler"
                        class="egovframework.rte.fdl.security.userdetails.EgovAuthenticationSuccessHandler">
                        <beans:property name="defaultTargetUrl" value="/index.jsp"/>
                    </beans:bean>

                    <beans:bean id="loginFailureHandler"
                        class="egovframework.rte.fdl.security.userdetails.EgovAuthenticationFailureHandler">
                        <beans:property name="defaultFailureUrl"
                            value="/uat/uia/egovLoginUsr.do?login_error=1"/>
                    </beans:bean>

                    <!-- 11. 접근 거부 핸들러 -->
                    <beans:bean id="accessDeniedHandler"
                        class="egovframework.rte.fdl.security.userdetails.EgovAccessDeniedHandler">
                        <beans:property name="accessDeniedUrl" value="/cmm/error/accessDenied.do"/>
                    </beans:bean>

                </beans:beans>
                """;
    }

    private String contextSecurity50() {
        // eGovFrame 5.0 — context-security.xml은 4.3과 구조 동일
        // eGovFrame 런타임 클래스(egovframework.rte.fdl.security.*) 경로도 동일
        // Spring Security XML 네임스페이스가 버전 간 하위 호환 유지
        return contextSecurity43();
    }

    private String securityMapper() {
        return """
                -- ============================================================
                -- egov-security-mapper 참조 SQL
                -- EgovReloadableFilterInvocationSecurityMetadataSource 가
                -- 아래 SQL로 DB에서 직접 로드 (별도 XML 파일 불필요)
                -- ============================================================

                -- [1] URL 패턴 → 권한 매핑 조회 (서버 시작 시 자동 실행)
                SELECT ri.ROLE_PTTRN, ar.AUTHOR_CODE
                FROM   COMTNROLEINFO ri
                JOIN   COMTNAUTHORROLERELATE ar ON ri.ROLE_CODE = ar.ROLE_CODE
                ORDER  BY ri.ROLE_SORT ASC;

                -- 결과 예시:
                --   ROLE_PTTRN                         AUTHOR_CODE
                --   \\A/uat/uia/.*\\.do.*\\Z            IS_AUTHENTICATED_ANONYMOUSLY
                --   \\A/.*\\.do.*\\Z                    ROLE_ADMIN
                --   \\A/.*\\.do.*\\Z                    ROLE_USER
                --   \\A/uss/umt/.*\\.do.*\\Z            ROLE_ADMIN

                -- [2] ROLE 계층 조회 (COMTNROLES_HIERARCHY)
                SELECT PARNTS_ROLE, CHLDRN_ROLE
                FROM   COMTNROLES_HIERARCHY;

                -- 실제 데이터 (com DB):
                --   PARNTS_ROLE                      CHLDRN_ROLE
                --   IS_AUTHENTICATED_ANONYMOUSLY     IS_AUTHENTICATED_REMEMBERED
                --   IS_AUTHENTICATED_FULLY           ROLE_USER
                --   IS_AUTHENTICATED_REMEMBERED      IS_AUTHENTICATED_FULLY
                --   ROLE_ANONYMOUS                   IS_AUTHENTICATED_ANONYMOUSLY
                --   ROLE_USER                        ROLE_ADMIN

                -- [3] 신규 URL 패턴 등록 시 사용 SQL (generateAuthInsertSql 참조)
                -- COMTNROLEINFO INSERT → COMTNAUTHORROLERELATE INSERT
                -- 등록 후 EgovSecurityContextRefresher.refreshSecurityContext() 호출 시
                -- 서버 재기동 없이 즉시 반영됨

                -- [4] 등록된 프로그램 목록 확인
                SELECT PROGRM_FILE_NM, PROGRM_KOR_NM, URL
                FROM   COMTNPROGRMLIST
                ORDER  BY PROGRM_FILE_NM;

                -- [5] 메뉴-프로그램 연결 확인
                SELECT m.MENU_NO, m.MENU_NM, m.PROGRM_FILE_NM, p.URL
                FROM   COMTNMENUINFO m
                JOIN   COMTNPROGRMLIST p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM
                ORDER  BY m.MENU_NO;
                """;
    }

    // -------------------------------------------------------------------------
    // 2. 신규 Java Config 방식
    // -------------------------------------------------------------------------

    private String javaConfig43(String pkg) {
        return """
                package %s.config;

                import java.util.Arrays;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.access.AccessDecisionManager;
                import org.springframework.security.access.vote.AffirmativeBased;
                import org.springframework.security.access.vote.AuthenticatedVoter;
                import org.springframework.security.access.vote.RoleHierarchyVoter;
                import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.builders.WebSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

                /**
                 * eGovFrame 4.3 Spring Security Java Config
                 *
                 * 구조: WebSecurityConfigurerAdapter 상속 방식 (Spring Security 4.x~5.x 표준)
                 * 인증: JDBC DB 기반 (COMTNEMPLYRINFO)
                 * 권한: ROLE 기반 DB 동적 로드 (COMTNROLEINFO)
                 * 세션: Session 기반 유지 (공공 SI 레거시 호환)
                 *
                 * 참고: Spring Security 6.0 이상에서 WebSecurityConfigurerAdapter 삭제됨.
                 *       eGovFrame 5.0 이상은 javaConfig (egovVersion=5.0) 사용 권장.
                 */
                @Configuration
                @EnableWebSecurity
                public class EgovSecurityConfig extends WebSecurityConfigurerAdapter {

                    @Autowired
                    private EgovUserDetailsServiceImpl userDetailsService;

                    @Autowired
                    private EgovSecurityMetadataSource egovSecurityMetadataSource;

                    @Autowired
                    private EgovAccessDeniedHandler egovAccessDeniedHandler;

                    // 정적 자원 Security 필터 완전 제외
                    @Override
                    public void configure(WebSecurity web) throws Exception {
                        web.ignoring()
                            .antMatchers("/css/**", "/images/**", "/js/**", "/favicon.ico");
                    }

                    @Override
                    protected void configure(HttpSecurity http) throws Exception {
                        http
                            .authorizeRequests()
                                .antMatchers("/uat/uia/**").permitAll()
                                .antMatchers("/cmm/fms/FileDown.do").permitAll()
                                .antMatchers("/sym/ccm/zip/**").permitAll()
                                .anyRequest().authenticated()
                            .and()
                            // 로그인 설정 (Session 기반 — 공공 SI 표준)
                            .formLogin()
                                .loginPage("/uat/uia/egovLoginUsr.do")
                                .loginProcessingUrl("/uat/uia/actionLogin.do")
                                .defaultSuccessUrl("/index.jsp")
                                .failureUrl("/uat/uia/egovLoginUsr.do?login_error=1")
                                .successHandler(loginSuccessHandler())
                                .failureHandler(loginFailureHandler())
                            .and()
                            // 로그아웃 설정
                            .logout()
                                .logoutUrl("/uat/uia/actionLogout.do")
                                .logoutSuccessUrl("/index.jsp")
                                .invalidateHttpSession(true)
                            .and()
                            // 세션 관리 (Session 기반 유지 — STATELESS 아님)
                            .sessionManagement()
                                .sessionFixation().newSession()
                                .invalidSessionUrl("/uat/uia/egovLoginUsr.do")
                                .maximumSessions(1)
                                .expiredUrl("/uat/uia/egovLoginUsr.do?expired=1")
                            .and()
                            .and()
                            // CSRF 활성화 (JSP Form 기반이므로 필수)
                            .csrf()
                                .ignoringAntMatchers("/api/**")
                            .and()
                            // 접근 거부 처리
                            .exceptionHandling()
                                .accessDeniedHandler(egovAccessDeniedHandler)
                            .and()
                            // DB 기반 동적 URL 접근 제어 필터
                            .addFilterBefore(egovSecurityFilter(), FilterSecurityInterceptor.class);
                    }

                    @Override
                    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
                        auth.userDetailsService(userDetailsService)
                            .passwordEncoder(passwordEncoder());
                    }

                    // DB 기반 동적 URL 접근 제어 필터
                    // COMTNROLEINFO URL 패턴 → ROLE 매핑 동적 로드
                    @Bean
                    public FilterSecurityInterceptor egovSecurityFilter() throws Exception {
                        FilterSecurityInterceptor filter = new FilterSecurityInterceptor();
                        filter.setAuthenticationManager(authenticationManagerBean());
                        filter.setAccessDecisionManager(accessDecisionManager());
                        filter.setSecurityMetadataSource(egovSecurityMetadataSource);
                        return filter;
                    }

                    // RoleHierarchy 적용 접근 결정 관리자
                    @Bean
                    public AccessDecisionManager accessDecisionManager() {
                        return new AffirmativeBased(Arrays.asList(
                            new RoleHierarchyVoter(roleHierarchy()),
                            new AuthenticatedVoter()
                        ));
                    }

                    @Bean
                    public PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder();
                    }

                    @Bean
                    public EgovAuthenticationSuccessHandler loginSuccessHandler() {
                        return new EgovAuthenticationSuccessHandler("/index.jsp");
                    }

                    @Bean
                    public EgovAuthenticationFailureHandler loginFailureHandler() {
                        return new EgovAuthenticationFailureHandler(
                            "/uat/uia/egovLoginUsr.do?login_error=1");
                    }

                    // ROLE 계층 구조 (COMTNROLES_HIERARCHY 기반 — EgovRoleHierarchyConfig 분리 권장)
                    @Bean
                    public org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl roleHierarchy() {
                        org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl impl =
                            new org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl();
                        impl.setHierarchy(
                            "ROLE_USER > ROLE_ADMIN\\n" +
                            "IS_AUTHENTICATED_FULLY > IS_AUTHENTICATED_REMEMBERED\\n" +
                            "IS_AUTHENTICATED_REMEMBERED > IS_AUTHENTICATED_ANONYMOUSLY"
                        );
                        return impl;
                    }
                }
                """.formatted(pkg);
    }

    private String javaConfig50(String pkg) {
        return """
                package %s.config;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.config.http.SessionCreationPolicy;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

                /**
                 * eGovFrame 5.x Spring Security Java Config
                 *
                 * 구조: Spring Security 엔진 + eGovFrame 공통 권한관리
                 * 인증: JDBC DB 기반 (COMTNEMPLYRINFO)
                 * 권한: ROLE 기반 DB 동적 로드 (COMTNROLEINFO)
                 * 세션: Session 기반 유지 (공공 SI 레거시 호환)
                 *
                 * 참고: Spring Security 설정(context-security.xml의 <http> ↔ SecurityFilterChain Bean)은
                 *       동시에 선언 불가 — springSecurityFilterChain Bean 충돌로 기동 실패.
                 *       단, DataSource·TX 등 다른 Bean은 XML/Java Config 혼용 가능.
                 */
                @Configuration
                @EnableWebSecurity
                public class EgovSecurityConfig {

                    private final EgovUserDetailsServiceImpl userDetailsService;
                    private final EgovSecurityMetadataSource securityMetadataSource;
                    private final EgovAccessDeniedHandler    accessDeniedHandler;
                    private final RoleHierarchy              roleHierarchy;

                    public EgovSecurityConfig(EgovUserDetailsServiceImpl userDetailsService,
                                              EgovSecurityMetadataSource securityMetadataSource,
                                              EgovAccessDeniedHandler accessDeniedHandler,
                                              RoleHierarchy roleHierarchy) {
                        this.userDetailsService    = userDetailsService;
                        this.securityMetadataSource = securityMetadataSource;
                        this.accessDeniedHandler   = accessDeniedHandler;
                        this.roleHierarchy         = roleHierarchy;
                    }

                    @Bean
                    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                        http
                            // 정적 자원 제외
                            .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/css/**", "/images/**", "/js/**").permitAll()
                                .requestMatchers("/uat/uia/**").permitAll()
                                .requestMatchers("/cmm/fms/FileDown.do").permitAll()
                                .anyRequest().authenticated()
                            )

                            // 로그인 설정 (Session 기반 — 공공 SI 표준)
                            .formLogin(form -> form
                                .loginPage("/uat/uia/egovLoginUsr.do")
                                .loginProcessingUrl("/uat/uia/actionLogin.do")
                                .defaultSuccessUrl("/index.jsp")
                                .failureUrl("/uat/uia/egovLoginUsr.do?login_error=1")
                                .successHandler(loginSuccessHandler())
                                .failureHandler(loginFailureHandler())
                            )

                            // 로그아웃 설정
                            .logout(logout -> logout
                                .logoutUrl("/uat/uia/actionLogout.do")
                                .logoutSuccessUrl("/index.jsp")
                                .invalidateHttpSession(true)
                            )

                            // 세션 관리 (Session 기반 유지 — STATELESS 아님)
                            .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                .sessionFixation().newSession()
                                .maximumSessions(1)
                                .expiredUrl("/uat/uia/egovLoginUsr.do?expired=1")
                            )

                            // CSRF 활성화 (JSP Form 기반이므로 필수)
                            .csrf(csrf -> csrf
                                .ignoringRequestMatchers("/api/**")  // REST API만 제외
                            )

                            // 접근 거부 처리
                            .exceptionHandling(ex -> ex
                                .accessDeniedHandler(accessDeniedHandler)
                            )

                            // DB 기반 동적 URL 접근 제어 필터
                            // COMTNROLEINFO URL 패턴 → ROLE 매핑 동적 로드
                            .addFilterBefore(egovSecurityFilter(), FilterSecurityInterceptor.class);

                        return http.build();
                    }

                    // DB 기반 동적 URL 접근 제어 필터
                    @Bean
                    public FilterSecurityInterceptor egovSecurityFilter() {
                        FilterSecurityInterceptor filter = new FilterSecurityInterceptor();
                        filter.setSecurityMetadataSource(securityMetadataSource);
                        filter.setAccessDecisionManager(accessDecisionManager());
                        return filter;
                    }

                    // RoleHierarchy 적용 접근 결정 관리자
                    @Bean
                    public org.springframework.security.access.AccessDecisionManager accessDecisionManager() {
                        return new org.springframework.security.access.vote.AffirmativeBased(
                            java.util.List.of(
                                new org.springframework.security.access.vote.RoleHierarchyVoter(roleHierarchy),
                                new org.springframework.security.access.vote.AuthenticatedVoter()
                            )
                        );
                    }

                    @Bean
                    public PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder();
                    }

                    @Bean
                    public EgovAuthenticationSuccessHandler loginSuccessHandler() {
                        return new EgovAuthenticationSuccessHandler("/index.jsp");
                    }

                    @Bean
                    public EgovAuthenticationFailureHandler loginFailureHandler() {
                        return new EgovAuthenticationFailureHandler(
                            "/uat/uia/egovLoginUsr.do?login_error=1");
                    }
                }
                """.formatted(pkg);
    }

    private String userDetailsService(String pkg) {
        return """
                package %s.service;

                import org.springframework.jdbc.core.JdbcTemplate;
                import org.springframework.security.core.GrantedAuthority;
                import org.springframework.security.core.authority.SimpleGrantedAuthority;
                import org.springframework.security.core.userdetails.User;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.core.userdetails.UsernameNotFoundException;
                import org.springframework.stereotype.Service;

                import java.util.List;

                /**
                 * eGovFrame 표준 UserDetailsService 구현체
                 *
                 * 사용자 조회: COMTNEMPLYRINFO (EMPLYR_ID, PASSWORD, LOCK_AT, EMPLYR_STTUS_CODE)
                 * 권한 조회:  COMTNEMPLYRSCRTYESTBS → AUTHOR_CODE (ROLE_ADMIN, ROLE_USER 등)
                 *
                 * EMPLYR_STTUS_CODE = 'ESC01' → 재직중 사용자만 인증
                 */
                @Service
                public class EgovUserDetailsServiceImpl implements UserDetailsService {

                    private final JdbcTemplate jdbcTemplate;

                    public EgovUserDetailsServiceImpl(JdbcTemplate jdbcTemplate) {
                        this.jdbcTemplate = jdbcTemplate;
                    }

                    @Override
                    public UserDetails loadUserByUsername(String username)
                            throws UsernameNotFoundException {

                        // 1. COMTNEMPLYRINFO 에서 사용자 조회 (재직중만)
                        List<java.util.Map<String, Object>> users = jdbcTemplate.queryForList(
                            "SELECT EMPLYR_ID, PASSWORD, ESNTL_ID, LOCK_AT " +
                            "FROM COMTNEMPLYRINFO " +
                            "WHERE EMPLYR_ID = ? AND EMPLYR_STTUS_CODE = 'ESC01'",
                            username
                        );

                        if (users.isEmpty()) {
                            throw new UsernameNotFoundException(
                                "사용자를 찾을 수 없습니다: " + username);
                        }

                        java.util.Map<String, Object> user = users.get(0);
                        String  password = (String) user.get("PASSWORD");
                        boolean locked   = "Y".equals(user.get("LOCK_AT"));

                        // 2. COMTNEMPLYRSCRTYESTBS 에서 권한(AUTHOR_CODE) 조회
                        List<GrantedAuthority> authorities = jdbcTemplate.query(
                            "SELECT AUTHOR_CODE " +
                            "FROM COMTNEMPLYRSCRTYESTBS " +
                            "WHERE SCRTY_DTRMN_TRGET_ID = ?",
                            (rs, rowNum) ->
                                new SimpleGrantedAuthority(rs.getString("AUTHOR_CODE")),
                            username
                        );

                        if (authorities.isEmpty()) {
                            // 권한 미설정 시 기본 ROLE_USER 부여
                            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                        }

                        return User.builder()
                            .username(username)
                            .password(password)
                            .authorities(authorities)
                            .accountLocked(locked)
                            .build();
                    }
                }
                """.formatted(pkg);
    }

    private String roleHierarchy(String pkg) {
        return """
                package %s.config;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.jdbc.core.JdbcTemplate;
                import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
                import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

                import java.util.List;
                import java.util.Map;

                /**
                 * COMTNROLES_HIERARCHY 테이블 기반 권한 계층 구조 설정
                 *
                 * 실제 DB 데이터 (com DB):
                 *   PARNTS_ROLE                      CHLDRN_ROLE
                 *   IS_AUTHENTICATED_ANONYMOUSLY     IS_AUTHENTICATED_REMEMBERED
                 *   IS_AUTHENTICATED_FULLY           ROLE_USER
                 *   IS_AUTHENTICATED_REMEMBERED      IS_AUTHENTICATED_FULLY
                 *   ROLE_ANONYMOUS                   IS_AUTHENTICATED_ANONYMOUSLY
                 *   ROLE_USER                        ROLE_ADMIN
                 *
                 * → ROLE_USER 가 ROLE_ADMIN 권한 자동 상속
                 * → IS_AUTHENTICATED_FULLY(완전인증) > IS_AUTHENTICATED_REMEMBERED > IS_AUTHENTICATED_ANONYMOUSLY
                 */
                @Configuration
                public class EgovRoleHierarchyConfig {

                    private final JdbcTemplate jdbcTemplate;

                    public EgovRoleHierarchyConfig(JdbcTemplate jdbcTemplate) {
                        this.jdbcTemplate = jdbcTemplate;
                    }

                    @Bean
                    public RoleHierarchy roleHierarchy() {
                        // COMTNROLES_HIERARCHY 에서 계층 관계 동적 로드
                        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            "SELECT PARNTS_ROLE, CHLDRN_ROLE FROM COMTNROLES_HIERARCHY"
                        );

                        StringBuilder hierarchy = new StringBuilder();
                        for (Map<String, Object> row : rows) {
                            hierarchy.append(row.get("PARNTS_ROLE"))
                                     .append(" > ")
                                     .append(row.get("CHLDRN_ROLE"))
                                     .append("\\n");
                        }

                        RoleHierarchyImpl impl = new RoleHierarchyImpl();
                        impl.setHierarchy(hierarchy.toString());
                        return impl;
                    }
                }
                """.formatted(pkg);
    }

    private String loginPage() {
        return """
                <%@ page contentType="text/html;charset=UTF-8" language="java" %>
                <%@ taglib prefix="c"      uri="http://java.sun.com/jsp/jstl/core" %>
                <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>로그인 | 전자정부 표준프레임워크</title>
                </head>
                <body>

                <!-- ============================================================
                     eGovFrame 표준 로그인 폼
                     action: context-security.xml login-processing-url 과 일치
                     CSRF 토큰: Spring Security CSRF 활성화 시 필수
                ============================================================ -->
                <form action="<c:url value='/uat/uia/actionLogin.do'/>" method="post">

                    <!-- CSRF 토큰 (Spring Security 기본 활성화 — 반드시 포함) -->
                    <input type="hidden"
                           name="${_csrf.parameterName}"
                           value="${_csrf.token}"/>

                    <!-- 로그인 실패 메시지 -->
                    <c:if test="${param.login_error == '1'}">
                        <p style="color:red;">
                            아이디 또는 비밀번호가 올바르지 않습니다.
                        </p>
                    </c:if>

                    <!-- 세션 만료 메시지 -->
                    <c:if test="${param.expired == '1'}">
                        <p style="color:red;">
                            다른 기기에서 로그인되어 세션이 만료되었습니다.
                        </p>
                    </c:if>

                    <div>
                        <label for="j_username">아이디</label>
                        <input type="text" id="j_username" name="j_username"
                               autocomplete="username" required/>
                    </div>
                    <div>
                        <label for="j_password">비밀번호</label>
                        <input type="password" id="j_password" name="j_password"
                               autocomplete="current-password" required/>
                    </div>

                    <button type="submit">로그인</button>

                </form>

                <!--
                [참고] input name 매핑
                  j_username → Spring Security 기본 username 파라미터
                  j_password → Spring Security 기본 password 파라미터

                [참고] CSRF 비활성화 시 (REST API 서버 등)
                  .csrf(csrf -> csrf.disable()) 설정 후 해당 hidden input 제거 가능
                  JSP 기반 공공 SI 환경에서는 CSRF 반드시 활성화 유지
                -->

                </body>
                </html>
                """;
    }

    private String unsupported(String securityType) {
        return """
                지원하지 않는 securityType 입니다: %s

                사용 가능한 securityType 목록:

                [레거시 XML 방식 — eGovFrame 4.3 / 5.0 공통]
                  webXmlFilter      → web.xml DelegatingFilterProxy 설정
                  contextSecurity   → context-security.xml (Spring Security XML 네임스페이스)
                  securityMapper    → URL-ROLE 매핑 참조 SQL (COMTNROLEINFO / COMTNROLES_HIERARCHY)

                [Java Config 방식]
                  javaConfig        → egovVersion=4.3: WebSecurityConfigurerAdapter 방식
                                      egovVersion=5.0: SecurityFilterChain Bean 방식 (기본값)
                  userDetailsService → EgovUserDetailsServiceImpl.java (버전 공통)
                  roleHierarchy     → EgovRoleHierarchyConfig.java (버전 공통)

                [공통]
                  loginPage         → egovLoginUsr.jsp (CSRF 토큰 포함 표준 로그인 폼)

                egovVersion 입력값: "4.3" 또는 "5.0" (미입력 시 5.0 기본값)
                """.formatted(securityType);
    }
}
