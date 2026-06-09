package com.krdevops.springai.service;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.SecuritySpec;
import com.krdevops.springai.model.VersionCapability;
import com.krdevops.springai.service.initializr.FilePlanExecutor;
import com.krdevops.springai.service.initializr.VersionCapabilityResolver;
import com.krdevops.springai.service.security.SecurityFilePlanFactory;
import com.krdevops.springai.service.security.SecurityResultBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * eGovFrame Security 템플릿 생성 서비스 — 조율자 역할.
 *
 * <p>Phase 1: 기존 3-인자 API를 유지하면서 내부 구조를 분리한다.
 * 템플릿 로직은 {@link SecurityFilePlanFactory} → {@link com.krdevops.springai.service.security.template.SecurityTemplateRenderer}
 * → classpath:templates/security/ 의 .tpl 파일로 위임된다.
 *
 * <p>Phase 2에서 {@code outputPath}, {@code projectType} 인자가 추가된다.
 */
@Service
@RequiredArgsConstructor
public class SecurityTemplateService {

    private final VersionCapabilityResolver resolver;
    private final SecurityFilePlanFactory   factory;
    private final FilePlanExecutor          executor;
    private final SecurityResultBuilder     resultBuilder;

    /**
     * 하위 호환 API — 기존 3개 인자로 호출 시 문자열 반환.
     */
    public String getSecurityTemplate(String securityType,
                                      String packageName,
                                      String egovVersion) {
        return getSecurityTemplate(securityType, packageName, egovVersion, null, null);
    }

    // -------------------------------------------------------------------------
    // Phase 2 진입점 (stub) — outputPath 저장 지원
    // -------------------------------------------------------------------------

    /**
     * Phase 2 API — outputPath가 있으면 파일을 직접 저장하고 결과를 반환한다.
     * outputPath가 null/blank이면 문자열 반환 경로(하위 호환)로 처리된다.
     */
    public String getSecurityTemplate(String securityType,
                                      String packageName,
                                      String egovVersion,
                                      String outputPath,
                                      String projectType) {
        VersionCapability cap  = resolver.resolve(egovVersion);
        SecuritySpec      spec = SecuritySpec.of(securityType, packageName, projectType, outputPath, cap);

        if (!spec.hasOutputPath()) {
            // outputPath 없음 → 문자열 반환 (하위 호환)
            try {
                return factory.renderSingle(spec);
            } catch (IllegalArgumentException e) {
                return versionMismatch(e) ? e.getMessage() : unsupported(securityType);
            }
        }

        // outputPath 있음 → 파일 직접 저장
        try {
            List<FilePlan> plans = factory.plan(spec);
            GenerationReport report = executor.execute(spec.root(), plans);
            return resultBuilder.build(report);
        } catch (IllegalArgumentException e) {
            return versionMismatch(e) ? e.getMessage() : unsupported(securityType);
        }
    }

    // -------------------------------------------------------------------------
    // private
    // -------------------------------------------------------------------------

    /** 버전 불일치 예외 여부 — 메시지를 그대로 반환해야 하는 경우를 판별한다. */
    private boolean versionMismatch(IllegalArgumentException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("전용입니다");
    }

    private String unsupported(String securityType) {
        return """
                지원하지 않는 securityType 입니다: %s

                사용 가능한 securityType 목록:

                [레거시 XML 방식]
                  webXmlFilter        → web.xml 6-filter 체인 (CharacterEncodingFilter ~
                                        EgovSpringSecurityLogoutFilter, 선언 순서 포함)
                  contextSecurity     → context-security.xml
                                        4.3: egov-security 네임스페이스 + <egov-security:config>
                                        5.0: EgovSecurityConfig Bean (32개 property POJO)
                  securityMapper      → URL-ROLE 매핑 참조 SQL

                [Java Config 방식]
                  javaConfig          → 4.3: WebSecurityConfigurerAdapter 방식
                                        5.0: @Import(EgovSecurityConfiguration.class) 진입점
                  userDetailsService  → 4.3: EgovUserDetailsServiceImpl.java
                                        5.0: contextSecurity XML 프로퍼티 방식 안내
                  roleHierarchy       → EgovRoleHierarchyConfig.java (버전 공통)

                [인증/로그아웃 필터 구현체 — bopr 방식]
                  loginFilter         → EgovSpringSecurityLoginFilter.java
                                        (DB 인증 + SecurityContext 직접 설정, jakarta)
                  logoutFilter        → EgovSpringSecurityLogoutFilter.java
                                        (세션 초기화 + Spring Security 로그아웃 위임, jakarta)
                  loginPolicyFilter   → EgovLoginPolicyFilter.java
                                        (비밀번호 만료/계정 잠금 체크, jakarta)
                  sessionMapping      → EgovSessionMapping.java
                                        (DB ResultSet → EgovUserDetails 변환, jdbcMapClass 역할)

                [핸들러 구현체 — javaConfig 4.3 전용]
                  successHandler      → EgovAuthenticationSuccessHandler.java (javax)
                  failureHandler      → EgovAuthenticationFailureHandler.java (javax)
                  accessDeniedHandler → EgovAccessDeniedHandler.java (javax/jakarta)

                [인증 헬퍼]
                  userDetailsHelper    → EgovUserDetailsHelper 사용 예시
                                         (컨트롤러에서 isAuthenticated / getAuthenticatedUser / getAuthorities)
                  userDetailsHelperXml → context-egovuserdetailshelper.xml
                                         (dummy/session/security Spring Profile 분기 XML)

                [공통]
                  loginPage           → egovLoginUsr.jsp (CSRF 토큰 포함 표준 로그인 폼)

                egovVersion 입력값: "4.3" 또는 "5.0" (미입력 시 5.0 기본값)
                """.formatted(securityType);
    }
}
