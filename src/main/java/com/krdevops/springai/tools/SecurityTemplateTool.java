package com.krdevops.springai.tools;

import com.krdevops.springai.service.SecurityTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityTemplateTool {

    private final SecurityTemplateService securityTemplateService;

    @Tool(description = """
            eGovFrame 표준 Spring Security 설정 파일 템플릿을 반환합니다.
            eGovFrame 4.3(Spring Security 4.x)과 5.0(Spring Security 6.x) 모두 지원합니다.
            XML 방식(공공 SI 레거시 호환)과 Java Config 방식(신규) 모두 지원합니다.

            [레거시 XML 방식 — eGovFrame 4.3 / 5.0 공통]
              webXmlFilter      → web.xml DelegatingFilterProxy 설정
              contextSecurity   → context-security.xml (Spring Security XML 네임스페이스 전체 설정)
              securityMapper    → URL-ROLE 매핑 참조 SQL (COMTNROLEINFO / COMTNROLES_HIERARCHY)

            [Java Config 방식 — egovVersion에 따라 분기]
              javaConfig        → egovVersion=4.3: WebSecurityConfigurerAdapter 상속 방식
                                                   (antMatchers / authorizeRequests / .and() 체이닝)
                                  egovVersion=5.0: SecurityFilterChain Bean 방식 (기본값)
                                                   (requestMatchers / authorizeHttpRequests / Lambda DSL)
              userDetailsService → EgovUserDetailsServiceImpl.java
                                  (COMTNEMPLYRINFO 사용자 조회 + COMTNEMPLYRSCRTYESTBS 권한 조회)
                                  eGovFrame 4.3 / 5.0 공통 사용 가능
              roleHierarchy     → EgovRoleHierarchyConfig.java
                                  (COMTNROLES_HIERARCHY 테이블 기반 ROLE 계층 동적 로드)
                                  eGovFrame 4.3 / 5.0 공통 사용 가능

            [공통]
              loginPage         → egovLoginUsr.jsp (CSRF 토큰 포함 표준 로그인 폼)

            핵심 아키텍처:
              DelegatingFilterProxy → springSecurityFilterChain → DB 인증 (COMTNEMPLYRINFO)
              → Session 저장 → COMTNROLEINFO URL 패턴 매칭 → 접근 제어
              세션 기반 유지 (공공 SI 표준 — STATELESS 아님)

            주의: contextSecurity(XML의 <http>)와 javaConfig(SecurityFilterChain Bean)는
                  Spring Security 설정으로 동시 선언 불가 (springSecurityFilterChain Bean 충돌).
                  단, DataSource·TX 등 다른 Bean은 XML/Java Config 혼용 가능.
                  userDetailsService·roleHierarchy는 XML Security와도 함께 사용 가능.

            securityType: 위 목록 중 하나 (대소문자 무관)
            packageName : Java 패키지명 (예: egovframework.let.emp) — javaConfig/userDetailsService/roleHierarchy에서 사용
            egovVersion : "4.3" 또는 "5.0" (미입력 시 5.0 기본값)
            """)
    public String getSecurityTemplate(String securityType, String packageName, String egovVersion) {
        return securityTemplateService.getSecurityTemplate(securityType, packageName, egovVersion);
    }
}
