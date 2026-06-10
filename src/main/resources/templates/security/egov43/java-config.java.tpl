package ${packageName}.config;

import ${packageName}.sec.handler.EgovAuthenticationSuccessHandler;
import ${packageName}.sec.handler.EgovAuthenticationFailureHandler;
import ${packageName}.sec.handler.EgovAccessDeniedHandler;
import ${packageName}.sec.service.impl.EgovUserDetailsServiceImpl;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
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
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
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
 *
 * 필요 Bean (별도 템플릿으로 제공):
 *   - EgovUserDetailsServiceImpl        : getSecurityTemplate("userdetailsservice", ...)
 *   - EgovRoleHierarchyConfig(roleHierarchy Bean) : getSecurityTemplate("rolehierarchy", ...)
 *   - EgovAuthenticationSuccessHandler  : getSecurityTemplate("successhandler", ...)
 *   - EgovAuthenticationFailureHandler  : getSecurityTemplate("failurehandler", ...)
 *   - EgovAccessDeniedHandler           : getSecurityTemplate("accessdeniedhandler", ...)
 */
@Configuration
@EnableWebSecurity
public class EgovProjectSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private EgovUserDetailsServiceImpl userDetailsService;

    // ⚠️ FilterInvocationSecurityMetadataSource — Spring Security 6.x deprecated
    //    eGovFrame 런타임(EgovReloadableFilterInvocationSecurityMetadataSource)이 이 인터페이스를 구현
    @Autowired
    private FilterInvocationSecurityMetadataSource egovSecurityMetadataSource;

    @Autowired
    private EgovAccessDeniedHandler egovAccessDeniedHandler;

    // EgovRoleHierarchyConfig @Bean 주입 — getSecurityTemplate("rolehierarchy", ...) 참조
    @Autowired
    private RoleHierarchy roleHierarchy;

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
                .usernameParameter("j_username")
                .passwordParameter("j_password")
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
            // DB 기반 동적 URL 접근 제어 필터 (COMTNROLEINFO URL 패턴 → ROLE 매핑)
            .addFilterBefore(egovSecurityFilter(), FilterSecurityInterceptor.class);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService)
            .passwordEncoder(passwordEncoder());
    }

    @Bean
    public FilterSecurityInterceptor egovSecurityFilter() throws Exception {
        FilterSecurityInterceptor filter = new FilterSecurityInterceptor();
        filter.setAuthenticationManager(authenticationManagerBean());
        filter.setAccessDecisionManager(accessDecisionManager());
        filter.setSecurityMetadataSource(egovSecurityMetadataSource);
        return filter;
    }

    @Bean
    public AccessDecisionManager accessDecisionManager() {
        // WebExpressionVoter 미포함 — authorizeRequests() Java 메서드 체인 방식 사용으로 불필요.
        // SpEL 표현식(hasRole() 등) 사용 시 직접 추가:
        //   new org.springframework.security.web.access.expression.WebExpressionVoter()
        return new AffirmativeBased(Arrays.asList(
            new RoleHierarchyVoter(roleHierarchy),
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
}
