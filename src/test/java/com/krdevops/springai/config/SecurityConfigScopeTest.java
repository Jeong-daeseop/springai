package com.krdevops.springai.config;

import com.krdevops.springai.service.figma.FigmaRestTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MR-A07/MR-DEC-05: Refinement REST 경로별로 Plugin 단기 Token이 요구하는 Scope를 검증한다.
 * 특히 승인/반려 경로는 어떤 Scope로도 Plugin Token을 통과시키지 않아야 한다(null 반환).
 */
class SecurityConfigScopeTest {

    private final SecurityConfig securityConfig = new SecurityConfig(
            Mockito.mock(AppProperties.class),
            Mockito.mock(FigmaRestTokenService.class),
            Mockito.mock(com.krdevops.springai.config.mcp.McpCredentialValidator.class),
            Mockito.mock(com.krdevops.springai.config.mcp.McpSecurityAuditLogger.class));

    @Test
    void screenReadRequiresScreensReadScope() {
        assertThat(securityConfig.requiredScopeFor("GET", "/api/figma/screens/user-list"))
                .isEqualTo(FigmaRestTokenService.SCOPE_SCREENS_READ);
    }

    @Test
    void refinementScreenListRequiresScreensReadScope() {
        assertThat(securityConfig.requiredScopeFor("GET", "/api/figma/refinements/screens/user-list"))
                .isEqualTo(FigmaRestTokenService.SCOPE_SCREENS_READ);
    }

    @Test
    void refinementByIdRequiresScreensReadScope() {
        assertThat(securityConfig.requiredScopeFor("GET", "/api/figma/refinements/patch-set-1"))
                .isEqualTo(FigmaRestTokenService.SCOPE_SCREENS_READ);
    }

    @Test
    void previewAndCaptureRequireRefinementsWriteScope() {
        assertThat(securityConfig.requiredScopeFor("POST", "/api/figma/refinements/preview"))
                .isEqualTo(FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE);
        assertThat(securityConfig.requiredScopeFor("POST", "/api/figma/refinements/capture"))
                .isEqualTo(FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE);
    }

    @Test
    void generationReportUploadRequiresReportsWriteScope() {
        assertThat(securityConfig.requiredScopeFor("POST", "/api/figma/operations/reports"))
                .isEqualTo(FigmaRestTokenService.SCOPE_REPORTS_WRITE);
    }

    @Test
    void approveAndRejectAreNeverReachableByPluginToken() {
        assertThat(securityConfig.requiredScopeFor("POST", "/api/figma/refinements/patch-set-1/approve")).isNull();
        assertThat(securityConfig.requiredScopeFor("POST", "/api/figma/refinements/patch-set-1/reject")).isNull();
    }

    @Test
    void unrelatedPathsRequireNoScope() {
        assertThat(securityConfig.requiredScopeFor("GET", "/api/design-systems/krds")).isNull();
        assertThat(securityConfig.requiredScopeFor("POST", "/api/figma/operations/design-system-impact/krds")).isNull();
    }
}
