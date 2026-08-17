package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.DesignSystemSpec;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.service.FigmaApiClient;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 신규 Figma MCP Tool의 인증·서비스 호출·민감정보 제거 응답을 한 곳에서 처리한다. */
@Service
public class FigmaMcpFacadeService {

    private final FigmaScreenExportService exportService;
    private final DesignSystemQueryService designSystemQueryService;
    private final FigmaApiClient figmaApiClient;
    private final FigmaStyleExtractor styleExtractor;
    private final StyleTokenDiffService styleTokenDiffService;
    private final ObjectMapper objectMapper;

    public FigmaMcpFacadeService(
            FigmaScreenExportService exportService,
            DesignSystemQueryService designSystemQueryService,
            FigmaApiClient figmaApiClient,
            FigmaStyleExtractor styleExtractor,
            StyleTokenDiffService styleTokenDiffService,
            ObjectMapper objectMapper
    ) {
        this.exportService = exportService;
        this.designSystemQueryService = designSystemQueryService;
        this.figmaApiClient = figmaApiClient;
        this.styleExtractor = styleExtractor;
        this.styleTokenDiffService = styleTokenDiffService;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public String generateScreen(FigmaScreenExportRequest request) {
        FigmaExportResult result = exportService.export(request);
        return toRedactedJson(result);
    }

    public String validateScreen(String screenId, Integer version) {
        List<FigmaExportIssue> issues = exportService.validateStored(screenId, version);
        boolean valid = issues.stream().noneMatch(issue ->
                issue.severity() == FigmaExportIssue.Severity.FATAL
                        || issue.severity() == FigmaExportIssue.Severity.ERROR);
        return toJson(new ScreenValidationSummary(screenId, version, valid, issues));
    }

    public String validateDesignSystem(DesignSystemSpec spec) {
        List<DesignSystemIssue> issues = designSystemQueryService.validateSpec(spec);
        boolean valid = issues.stream().noneMatch(issue ->
                issue.severity() == DesignSystemIssue.Severity.FATAL
                        || issue.severity() == DesignSystemIssue.Severity.ERROR);
        return toJson(new DesignSystemValidationSummary(spec.id(), spec.version(), valid, issues));
    }

    /**
     * R5-045: 참조 fileKey의 Figma Styles에서 뽑은 Token 후보와 profileId의 운영 Profile Token
     * 차이를 반환한다. 조회 전용이며 Profile·Figma Library 어느 쪽도 쓰지 않는다.
     */
    public String previewStyleTokenDiff(String fileKey, String profileId) {
        var stylesResponse = figmaApiClient.queryStyles(fileKey);
        var candidates = styleExtractor.extractTokens(stylesResponse);
        var profile = designSystemQueryService.findLatestProfile(profileId);
        StyleTokenDiffService.StyleTokenDiffResult diff = styleTokenDiffService.diff(candidates, profile);
        return toJson(diff);
    }

    public String auditRegistry(String profileId, String registryVersion) {
        // Registry 원문을 반환하지 않아 Component/Variable 공개 Key가 MCP 응답에 노출되지 않는다.
        return toJson(designSystemQueryService.auditRegistry(profileId, registryVersion));
    }

    public String preflightRegistry(
            String profileId,
            String registryVersion,
            List<String> requiredLogicalTypes,
            String expectedLayoutPolicyVersion
    ) {
        // 해석 결과에는 논리 ID만 포함하고 Published Key 원문은 반환하지 않는다.
        return toJson(designSystemQueryService.preflightRegistry(
                profileId, registryVersion, requiredLogicalTypes, expectedLayoutPolicyVersion));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Figma MCP 응답 직렬화에 실패했습니다.", exception);
        }
    }

    /** MCP 채널에서는 Published Component/Variant/Variable Key를 구조적으로 제거한다. */
    private String toRedactedJson(Object value) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.valueToTree(value);
            redactKeys(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Figma MCP 응답 정제에 실패했습니다.", exception);
        }
    }

    private void redactKeys(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode object =
                    (com.fasterxml.jackson.databind.node.ObjectNode) node;
            object.remove(java.util.List.of("componentSetKey", "variantKey", "variableKey"));
            object.elements().forEachRemaining(this::redactKeys);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::redactKeys);
        }
    }

    public record ScreenValidationSummary(
            String screenId,
            Integer version,
            boolean valid,
            List<FigmaExportIssue> issues
    ) {}

    public record DesignSystemValidationSummary(
            String designSystemId,
            String version,
            boolean valid,
            List<DesignSystemIssue> issues
    ) {}
}
