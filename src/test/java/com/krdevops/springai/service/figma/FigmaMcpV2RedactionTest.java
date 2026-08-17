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
                exportService, mock(DesignSystemQueryService.class),
                mock(com.krdevops.springai.service.FigmaApiClient.class),
                new FigmaStyleExtractor(), new StyleTokenDiffService(), new ObjectMapper());

        String response = service.generateScreen(new com.krdevops.springai.model.figma.FigmaScreenExportRequest(
                "spec", 1, "screen", "krds", "DESKTOP", null, null));

        assertThat(response).doesNotContain("SECRET_SET_KEY", "SECRET_VARIANT_KEY", "componentSetKey", "variantKey");
        assertThat(response).contains("action.primary", "krds.button");
    }

    /**
     * R6-048: REST(FigmaExportController.export())와 MCP(FigmaExportTool→FigmaMcpFacadeService)는
     * 같은 FigmaScreenExportService.export() 호출 결과를 서로 다른 신뢰 수준으로 노출하는 하나의
     * 계약이다 — REST는 Publish 검증을 위해 사람이 원문 Key를 봐야 해서(DEC-07 감사로 확정된 설계)
     * 그대로 반환하고, MCP는 LLM 컨텍스트로 Key가 새지 않도록 redaction한다. 이 테스트는 그 둘이
     * "필드 이름은 완전히 같고 Key 3종만 있고 없고가 다르다"는 계약을 고정한다 — REST 응답이
     * MCP처럼 redaction되거나, 반대로 MCP가 REST처럼 원문을 새는 회귀를 잡는다.
     */
    @Test
    void restAndMcpShareTheSameFieldShapeAndDifferOnlyByKeyRedaction() throws Exception {
        FigmaScreenExportService exportService = mock(FigmaScreenExportService.class);
        FigmaNodeSpec node = new FigmaNodeSpec("screen/action/save", FigmaNodeSpec.NodeType.COMPONENT,
                "krds.button", Map.of("semanticRole", "action.primary"),
                new ResolvedComponentRef(SemanticRole.ACTION_PRIMARY, "krds.button", "SECRET_SET_KEY",
                        "SECRET_VARIANT_KEY", Map.of("Style", "Primary"), Map.of("Label", "저장"),
                        "2.0.0", "1.0.0", "rule", "a".repeat(64)), List.of());
        FigmaScreenSpec spec = new FigmaScreenSpec("screen", 1, "spec", 1, FigmaScreenType.FORM,
                LayoutPattern.STANDARD, "등록", null, "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("krds", "2.0.0", "2.0.0"), node, List.of());
        FigmaExportResult result = new FigmaExportResult(
                FigmaExportResult.Status.SUCCESS, spec, List.of(), LocalDateTime.now(), null);
        var request = new com.krdevops.springai.model.figma.FigmaScreenExportRequest(
                "spec", 1, "screen", "krds", "DESKTOP", null, null);
        when(exportService.export(request)).thenReturn(result);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FigmaMcpFacadeService service = new FigmaMcpFacadeService(
                exportService, mock(DesignSystemQueryService.class),
                mock(com.krdevops.springai.service.FigmaApiClient.class),
                new FigmaStyleExtractor(), new StyleTokenDiffService(), objectMapper);

        // REST 경로: FigmaExportController.export()가 하는 것과 동일하게 원본 객체를 그대로 직렬화.
        String restResponse = objectMapper.writeValueAsString(exportService.export(request));
        String mcpResponse = service.generateScreen(request);

        assertThat(restResponse).contains("SECRET_SET_KEY", "SECRET_VARIANT_KEY");
        assertThat(mcpResponse).doesNotContain("SECRET_SET_KEY", "SECRET_VARIANT_KEY");

        var restTree = objectMapper.readTree(restResponse);
        var mcpTree = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(mcpResponse);
        var restTreeWithoutKeys = (com.fasterxml.jackson.databind.node.ObjectNode) restTree.deepCopy();
        stripKeys(restTreeWithoutKeys);
        assertThat(mcpTree).isEqualTo(restTreeWithoutKeys);
    }

    private static void stripKeys(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            object.remove(List.of("componentSetKey", "variantKey", "variableKey"));
            object.elements().forEachRemaining(FigmaMcpV2RedactionTest::stripKeys);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(FigmaMcpV2RedactionTest::stripKeys);
        }
    }
}
