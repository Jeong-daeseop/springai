package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.service.SecurityTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityTemplateTool {

    private final SecurityTemplateService securityTemplateService;

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            eGovFrame 표준 Spring Security 설정 파일 템플릿을 반환합니다.
            eGovFrame 4.3(Spring Security 5.x)과 5.0(Spring Security 6.x) 모두 지원합니다.
            XML 방식(공공 SI 레거시 호환)과 Java Config 방식(신규) 모두 지원합니다.

            [레거시 XML 방식]
              webXmlFilter      → web.xml 6-filter 체인 전체 (선언 순서 포함)
                                  ⚠️ eGovFrame 4.3 WAR XML Security 전용
                                     5.0에서는 미지원 — setup-war-50을 사용하세요.
                                  ① CharacterEncodingFilter (UTF-8, *.do)
                                  ② HTMLTagFilter (XSS 방어, *.do)
                                  ③ LoginPolicyFilter (비밀번호 만료/계정 잠금, 로그인 URL)
                                  ④ EgovSpringSecurityLoginFilter (DB 인증 핵심, *.do)
                                  ⑤ springSecurityFilterChain (Spring Security 전체, /*)
                                  ⑥ EgovSpringSecurityLogoutFilter (세션 초기화, 로그아웃 URL)
                                  ⚠️ ④는 반드시 ⑤ 앞에 선언 (순서 바뀌면 인증 우회됨)
              contextSecurity   → context-security.xml
                                  egovVersion=4.3: spring-beans-4.0.xsd + egov-security-4.3.0.xsd
                                                   <egov-security:config> 포함 (RTE 보안 초기화 필수)
                                                   <http> / <authentication-manager> / Bean 선언 포함
                                  egovVersion=5.0: spring-beans.xsd만 사용 (egov-security 네임스페이스 없음)
                                                   → EgovSecurityConfig Bean 1개 (설정값 POJO, 32개 property)
                                                   → RTE EgovSecurityConfiguration이 이 Bean을 읽어 SecurityFilterChain 자동 구성
                                                   → 반드시 javaConfig(5.0)과 함께 사용 (단독 사용 불가)
                                  XSD 선언이 버전에 따라 다르므로 egovVersion 반드시 명시
              securityMapper    → URL-ROLE 매핑 참조 SQL (LETTNROLEINFO / LETTNROLES_HIERARCHY)

            [Java Config 방식 — egovVersion에 따라 구조가 완전히 다름]
              javaConfig        → egovVersion=4.3: WebSecurityConfigurerAdapter 상속 방식
                                                   (antMatchers / authorizeRequests / .and() 체이닝)
                                  egovVersion=5.0: @Import(EgovSecurityConfiguration.class) 진입점만 생성
                                                   → EgovProjectSecurityConfig.java (내용 없음)
                                                   → RTE EgovSecurityConfiguration이 SecurityFilterChain 자동 구성
                                                   → 반드시 contextsecurity(5.0)과 함께 사용 (EgovSecurityConfig Bean 필요)
                                                   ⚠️ EgovSecurityConfiguration을 XML <bean>으로 직접 선언하면
                                                      Spring Security 6.5 + Java 17에서 BootstrapMethodError 발생
              userDetailsService → EgovUserDetailsServiceImpl.java
                                  (LETTNEMPLYRINFO 사용자 조회 + LETTNEMPLYRSCRTYESTBS 권한 조회)
                                  ⚠️ eGovFrame 4.3 전용 — 5.0은 EgovJdbcUserDetailsManager(RTE 자동 구성)가 대체
                                     5.0에서는 context-security.xml의 jdbcUsersByUsernameQuery 프로퍼티로 설정
              roleHierarchy     → EgovRoleHierarchyConfig.java
                                  (LETTNROLES_HIERARCHY 테이블 기반 ROLE 계층 동적 로드)
                                  eGovFrame 4.3 / 5.0 공통 사용 가능
                                  ⚠️ XML 조합(setup-war-43-xml)에서는 미포함.
                                     context-security.xml이 roleHierarchy Bean을 이미 XML로 선언하므로
                                     Java Config @Bean과 동시 로드 시 Bean 중복 → 기동 실패.

            [핸들러 구현체 — javaConfig 4.3 전용]
              ※ XML 방식(contextSecurity)에서는 eGovFrame RTE가 핸들러 클래스를 직접 제공하므로 불필요.
                XML에서 커스텀 핸들러로 교체 시 class 속성을 fully-qualified name으로 수정 필요.
              ※ javaConfig 5.0에서도 불필요 — RTE EgovSecurityConfiguration이 자동 처리.
                 로그인 성공/실패는 EgovSecurityConfig.defaultTargetUrl / loginFailureUrl 프로퍼티로 설정.

              successHandler    → EgovAuthenticationSuccessHandler.java
                                  (로그인 성공 후 defaultTargetUrl 리다이렉트)
                                  ⚠️ javaConfig 4.3 전용 — 5.0은 EgovSecurityConfig.defaultTargetUrl 프로퍼티로 대체
                                  eGovFrame 4.3(javax) 전용
              failureHandler    → EgovAuthenticationFailureHandler.java
                                  (로그인 실패 후 defaultFailureUrl 리다이렉트)
                                  ⚠️ javaConfig 4.3 전용 — 5.0은 EgovSecurityConfig.loginFailureUrl 프로퍼티로 대체
                                  eGovFrame 4.3(javax) 전용
              accessDeniedHandler → EgovAccessDeniedHandler.java
                                  (HTTP 403 접근 거부 시 /cmm/error/accessDenied.do 리다이렉트)
                                  javaConfig 내부에서 생성자 주입으로 EgovAccessDeniedHandler 참조
                                  eGovFrame 4.3(javax) / 5.0(jakarta) 분기

            [인증/로그아웃 필터 구현체 — bopr 방식, jakarta]
              loginFilter         → EgovSpringSecurityLoginFilter.java
                                    Spring Security formLogin() 우회 — DB 직접 인증
                                    SecurityContextHolder 설정 + 세션 저장
                                    ⚠️ verifyPassword() 구현 필수 (SHA-256+Base64 또는 BCrypt)
                                    ⚠️ web.xml에서 springSecurityFilterChain 앞에 선언
                                    ⚠️ UserDetailsService 생성자 주입이 있어 web.xml 직접 등록 불가.
                                       webXmlFilter 는 DelegatingFilterProxy + targetBeanName 방식으로 등록.
                                       context-security.xml(4.3)에 egovSpringSecurityLoginFilter Bean 자동 포함됨.
              logoutFilter        → EgovSpringSecurityLogoutFilter.java
                                    session loginVO=null 초기화 → /egov_security_logout 위임
              loginPolicyFilter   → EgovLoginPolicyFilter.java
                                    로그인 전 비밀번호 만료/계정 잠금 사전 체크
                                    ⚠️ 계정 정책 DB 조회 로직 직접 구현 필요
              sessionMapping      → EgovSessionMapping.java
                                    DB ResultSet → EgovUserDetails 변환
                                    context-security.xml jdbcMapClass 프로퍼티에서 참조

            [인증 헬퍼]
              userDetailsHelper    → EgovUserDetailsHelper 컨트롤러 사용 예시
                                     isAuthenticated() / getAuthenticatedUser() / getAuthorities()
                                     eGovFrame 4.3 / 5.0 공통
              userDetailsHelperXml → context-egovuserdetailshelper.xml
                                     dummy/session/security Spring Profile 분기 XML
                                     globals.properties Globals.Auth 값으로 활성 Profile 결정

            [공통]
              loginPage         → egovLoginUsr.jsp (CSRF 토큰 포함 표준 로그인 폼)

            핵심 아키텍처:
              DelegatingFilterProxy → springSecurityFilterChain → DB 인증 (LETTNEMPLYRINFO)
              → Session 저장 → LETTNROLEINFO URL 패턴 매칭 → 접근 제어
              세션 기반 유지 (공공 SI 표준 — STATELESS 아님)

            주의: contextSecurity(XML의 <http>)와 javaConfig(SecurityFilterChain Bean)는
                  Spring Security 설정으로 동시 선언 불가 (springSecurityFilterChain Bean 충돌).
                  단, DataSource·TX 등 다른 Bean은 XML/Java Config 혼용 가능.
                  userDetailsService·roleHierarchy·핸들러는 XML Security와도 함께 사용 가능.

            [조합 생성 키워드 — outputPath와 함께 사용]
              ⚠️ 4.3 전용 키워드(setup-war-43-*)에 egovVersion=5.0 지정 시 예외 발생.
                 5.0 전용 키워드(setup-war-50/setup-all-war-50)에 egovVersion=4.3 지정 시 예외 발생.

              --- 4.3 XML Security 방식 (공공 SI 표준) ---
              setup-war-43        → setup-war-43-xml alias (하위 호환 유지)
              setup-war-43-xml    → 4.3 XML Security 기본 셋업 (9개 파일)
                                    webXmlFilter + contextSecurity + userDetailsService +
                                    sessionMapping + loginFilter + logoutFilter +
                                    loginPolicyFilter + loginPage + userDetailsHelperXml
                                    ※ javaConfig / roleHierarchy 미포함
                                      (contextSecurity XML이 roleHierarchy Bean 직접 선언)
                                    ※ 필터 3종 포함 이유: webXmlFilter가 이 클래스를 참조하므로
                                      조합 안에서 참조가 완결되어야 함

              --- 4.3 Java Config 방식 (WebSecurityConfigurerAdapter) ---
              setup-war-43-java   → 4.3 Java Config 기본 셋업 (7개 파일)
                                    javaConfig + userDetailsService + roleHierarchy +
                                    successHandler + failureHandler + accessDeniedHandler + loginPage
                                    ※ contextSecurity 미포함 (XML <http>와 동시 사용 불가)

              --- 5.0 ---
              setup-war-50        → 5.0 WAR Security 기본 셋업 (5개 파일)
                                    contextSecurity + javaConfig + roleHierarchy +
                                    loginPage + userDetailsHelperXml

              --- 필터/핸들러 조합 ---
              setup-filters       → DB 인증 필터 셋업 (4개 파일)
                                    loginFilter + logoutFilter + loginPolicyFilter + sessionMapping
              setup-handlers-43   → 4.3 Java Config 핸들러 셋업 (3개 파일)
                                    successHandler + failureHandler + accessDeniedHandler

              --- 전체 조합 ---
              setup-all-war-43        → setup-all-war-43-xml alias (하위 호환 유지)
              setup-all-war-43-xml    → 4.3 XML Security 전체 셋업 (10개 파일, 중복 제거)
                                        setup-war-43-xml + setup-filters + securityMapper
              setup-all-war-43-java   → 4.3 Java Config 전체 셋업 (12개 파일)
                                        setup-war-43-java + setup-filters + securityMapper
              setup-all-war-50        → 5.0 전체 보안 셋업 (11개 파일)
                                        setup-war-50 + setup-filters + accessDeniedHandler + securityMapper

            securityType: 위 목록 중 하나 (대소문자 무관)
            packageName : Java 패키지명 (예: egovframework.let.emp) — javaConfig/userDetailsService/roleHierarchy/핸들러에서 사용
            egovVersion : "4.3" 또는 "5.0" (미입력 시 5.0 기본값)
            outputPath  : (선택) 파일을 직접 저장할 프로젝트 루트 경로. 예: /Users/me/myproject
                          입력 시 지정 경로에 파일을 생성하고 결과를 반환.
                          미입력 시 템플릿 내용을 문자열로 반환 (하위 호환).
            projectType : (선택) "war" 또는 "boot". 미입력 시 "war" 기본값.
            """)
    public String getSecurityTemplate(String securityType,
                                      String packageName,
                                      String egovVersion,
                                      @Nullable String outputPath,
                                      @Nullable String projectType) {
        return securityTemplateService.getSecurityTemplate(
                securityType, packageName, egovVersion, outputPath, projectType);
    }
}
