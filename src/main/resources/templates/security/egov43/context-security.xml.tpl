<?xml version="1.0" encoding="UTF-8"?>
<beans:beans xmlns="http://www.springframework.org/schema/security"
    xmlns:beans="http://www.springframework.org/schema/beans"
    xmlns:egov-security="http://www.egovframe.go.kr/schema/egov-security"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans-4.0.xsd
        http://www.springframework.org/schema/security
        http://www.springframework.org/schema/security/spring-security.xsd
        http://www.egovframe.go.kr/schema/egov-security
        http://www.egovframe.go.kr/schema/egov-security/egov-security-4.3.0.xsd">

    <!--
    ============================================================
    eGovFrame 4.3 Spring Security 설정
    구조: DelegatingFilterProxy → springSecurityFilterChain → DB 인증
    인증: JDBC DB 기반 (COMTNEMPLYRINFO)
    권한: ROLE 기반 (COMTNROLEINFO → URL 패턴 매칭)
    세션: Session 기반 유지 (공공 SI 레거시 호환)
    ============================================================
    -->

    <!-- 0. eGovFrame RTE 보안 초기화 설정 -->
    <egov-security:config
        loginUrl="/uat/uia/egovLoginUsr.do"
        logoutUrl="/uat/uia/actionLogout.do"
        loginFailUrl="/uat/uia/egovLoginUsr.do?login_error=1"
        accessDeniedUrl="/cmm/error/accessDenied.do"
        dataSource="dataSource"
        jdbcMapClass="${packageName}.uat.uia.service.impl.EgovSessionMapping"
        requestMatcherType="regex"/>
    <!--
    ⚠️ jdbcMapClass: sessionMapping 템플릿으로 생성한 클래스 경로와 일치해야 합니다.
       getSecurityTemplate("sessionmapping", packageName, "4.3") 로 생성된 클래스:
       {packageName}.uat.uia.service.impl.EgovSessionMapping
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
            username-parameter="j_username"
            password-parameter="j_password"
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
        <beans:property name="userDetailsService" ref="egovUserDetailsServiceImpl"/>
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

    <!-- 9. ROLE 계층 구조
         XML Security 방식 기본값: 이 파일의 roleHierarchy Bean을 사용합니다.
         ROLE_USER > ROLE_ADMIN 의미: ROLE_USER 가 ROLE_ADMIN 권한 자동 상속

         동적 DB 기반 RoleHierarchyConfig.java 를 사용하려면:
           1. 아래 roleHierarchy Bean을 제거하거나 id를 변경
           2. getSecurityTemplate("rolehierarchy", packageName, "4.3") 파일을 등록
         ⚠️ 이 XML Bean과 Java Config @Bean(roleHierarchy)을 동시 로드하면
            Bean 중복으로 애플리케이션 기동 실패 (Spring Boot 3.x Bean overriding=false) -->
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

    <!-- 12. EgovSpringSecurityLoginFilter Spring Bean 등록
         web.xml의 DelegatingFilterProxy(targetBeanName=egovSpringSecurityLoginFilter)가
         이 Bean을 참조합니다.
         UserDetailsService 생성자 주입이 있어 web.xml 직접 등록 불가.
         ⚠️ egovUserDetailsServiceImpl Bean이 반드시 먼저 등록되어 있어야 합니다.
            (getSecurityTemplate("userdetailsservice", packageName, "4.3") 생성 파일) -->
    <beans:bean id="egovSpringSecurityLoginFilter"
        class="${packageName}.sec.filter.EgovSpringSecurityLoginFilter">
        <beans:constructor-arg ref="egovUserDetailsServiceImpl"/>
    </beans:bean>

</beans:beans>
