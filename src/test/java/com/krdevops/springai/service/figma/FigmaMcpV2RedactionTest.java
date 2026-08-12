package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FigmaMcpV2RedactionTest {
    @Test
    void generatedV2ResponseDoesNotExposePublishedKeys() {
        FigmaScreenExportService exportService = mock(FigmaScreenExportService.class);
        FigmaNodeSpec node = new FigmaNodeSpec("screen/action/save", FigmaNodeSpec.NodeType.COMPONENT,
                "krds.button", Map.of("semanticRole", "action.primary"),
                new ResolvedComponentRef(SemanticRole.ACTION_PRIMARY, "krds.button", "SECRET_SET_KEY",
                        "SECRET_VARIANT_KEY", Map.of("Style", "Primary"), Map.of("Label", "저장"),
                        "2.0.0", "1.0.0", "rule", "a".repeat(64)), List.of());
        FigmaScreenSpec spec = new FigmaScreenSpec("screen", 1, "spec", 1, FigmaScreenType.FORM,
                LayoutPattern.STANDARD, "등록", null, "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("krds", "2.0.0", "2.0.0"), node, List.of());
        when(exportService.export(any())).thenReturn(new FigmaExportResult(
                FigmaExportResult.Status.SUCCESS, spec, List.of(), LocalDateTime.now(), null));
        FigmaMcpFacadeService service = new FigmaMcpFacadeService(
                exportService, mock(DesignSystemQueryService.class), new ObjectMapper());

        String response = service.generateScreen(new com.krdevops.springai.model.figma.FigmaScreenExportRequest(
                "spec", 1, "screen", "krds", "DESKTOP", null, null));

        assertThat(response).doesNotContain("SECRET_SET_KEY", "SECRET_VARIANT_KEY", "componentSetKey", "variantKey");
        assertThat(response).contains("action.primary", "krds.button");
    }
}
