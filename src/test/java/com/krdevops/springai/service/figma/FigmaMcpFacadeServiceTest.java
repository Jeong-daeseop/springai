package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaMcpFacadeServiceTest {

    @Test
    void registryAuditResponseContainsSummaryButNoPublishedKeys() {
        DesignSystemQueryService queryService = mock(DesignSystemQueryService.class);
        when(queryService.auditRegistry("ftc-krds", "registry-1"))
                .thenReturn(new DesignSystemQueryService.RegistryAuditResult(
                        "ftc-krds", "registry-1", true, 0, List.<DesignSystemIssue>of()));
        FigmaMcpFacadeService service = new FigmaMcpFacadeService(
                mock(FigmaScreenExportService.class),
                queryService,
                mock(com.krdevops.springai.service.FigmaApiClient.class),
                new FigmaStyleExtractor(),
                new StyleTokenDiffService(),
                new ObjectMapper());

        String json = service.auditRegistry("ftc-krds", "registry-1");

        assertThat(json).contains("\"valid\":true", "\"registryVersion\":\"registry-1\"");
        assertThat(json).doesNotContain("componentSetKey", "variableKey", "BUTTON_KEY");
        verify(queryService).auditRegistry("ftc-krds", "registry-1");
    }

    @Test
    void registryPreflightReturnsLogicalResolutionWithoutPublishedKeys() {
        DesignSystemQueryService queryService = mock(DesignSystemQueryService.class);
        when(queryService.preflightRegistry("ftc-krds", "registry-2", List.of("egov.button"), null))
                .thenReturn(new DesignSystemQueryService.RegistryPreflightResult(
                        "ftc-krds", "registry-2", true,
                        java.util.Map.of("egov.button", "krds.action-button"),
                        List.of()));
        FigmaMcpFacadeService service = new FigmaMcpFacadeService(
                mock(FigmaScreenExportService.class),
                queryService,
                mock(com.krdevops.springai.service.FigmaApiClient.class),
                new FigmaStyleExtractor(),
                new StyleTokenDiffService(),
                new ObjectMapper());

        String json = service.preflightRegistry(
                "ftc-krds", "registry-2", List.of("egov.button"), null);

        assertThat(json).contains("egov.button", "krds.action-button", "\"valid\":true");
        assertThat(json).doesNotContain("componentSetKey", "variableKey");
    }
}
