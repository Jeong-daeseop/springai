package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaMcpFacadeServiceTest {

    @Test
    void unauthenticatedRegistryAuditIsRejectedBeforeRepositoryAccess() {
        DesignSystemQueryService queryService = mock(DesignSystemQueryService.class);
        FigmaMcpFacadeService service = new FigmaMcpFacadeService(
                new FigmaToolAuthorizationService("secret"),
                mock(FigmaScreenExportService.class),
                queryService,
                new ObjectMapper());

        assertThatThrownBy(() -> service.auditRegistry("wrong", "ftc-krds", "registry-1"))
                .isInstanceOf(SecurityException.class);
        verify(queryService, never()).auditRegistry("ftc-krds", "registry-1");
    }

    @Test
    void registryAuditResponseContainsSummaryButNoPublishedKeys() {
        DesignSystemQueryService queryService = mock(DesignSystemQueryService.class);
        when(queryService.auditRegistry("ftc-krds", "registry-1"))
                .thenReturn(new DesignSystemQueryService.RegistryAuditResult(
                        "ftc-krds", "registry-1", true, 0, List.<DesignSystemIssue>of()));
        FigmaMcpFacadeService service = new FigmaMcpFacadeService(
                new FigmaToolAuthorizationService("secret"),
                mock(FigmaScreenExportService.class),
                queryService,
                new ObjectMapper());

        String json = service.auditRegistry("secret", "ftc-krds", "registry-1");

        assertThat(json).contains("\"valid\":true", "\"registryVersion\":\"registry-1\"");
        assertThat(json).doesNotContain("componentSetKey", "variableKey", "BUTTON_KEY");
        verify(queryService).auditRegistry("ftc-krds", "registry-1");
    }

    @Test
    void registryPreflightReturnsLogicalResolutionWithoutPublishedKeys() {
        DesignSystemQueryService queryService = mock(DesignSystemQueryService.class);
        when(queryService.preflightRegistry("ftc-krds", "registry-2", List.of("egov.button")))
                .thenReturn(new DesignSystemQueryService.RegistryPreflightResult(
                        "ftc-krds", "registry-2", true,
                        java.util.Map.of("egov.button", "krds.action-button"),
                        List.of()));
        FigmaMcpFacadeService service = new FigmaMcpFacadeService(
                new FigmaToolAuthorizationService("secret"),
                mock(FigmaScreenExportService.class),
                queryService,
                new ObjectMapper());

        String json = service.preflightRegistry(
                "secret", "ftc-krds", "registry-2", List.of("egov.button"));

        assertThat(json).contains("egov.button", "krds.action-button", "\"valid\":true");
        assertThat(json).doesNotContain("componentSetKey", "variableKey");
    }
}
