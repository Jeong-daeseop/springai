package com.krdevops.springai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 근거 정보가 없던 UiDesignSpec v1을 손실 상태가 명시된 v2 Design IR로 변환한다. */
@Component
public class UiDesignSpecV1ToV2Adapter {

    private final ObjectMapper canonicalMapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public UiDesignSpecV2 adapt(
            String specId,
            UiDesignSpec legacy,
            UiDesignSpecV2.Source source) {
        if (legacy == null) throw new IllegalArgumentException("legacy UiDesignSpec은 필수입니다.");
        if (source == null) throw new IllegalArgumentException("source는 필수입니다.");

        List<UiDesignSpecV2.SemanticNode> nodes = new ArrayList<>();
        LinkedHashSet<String> usedIds = new LinkedHashSet<>();
        UiDesignSpecV2.InferenceEvidence legacyEvidence = legacyEvidence();
        nodes.add(new UiDesignSpecV2.SemanticNode(
                unique("legacy-root", usedIds), "page", null, legacyEvidence, List.of()));

        for (UiDesignSpec.ComponentSpec component : legacy.components()) {
            nodes.add(new UiDesignSpecV2.SemanticNode(
                    unique("component-" + slug(component.type()), usedIds),
                    "component", normalize(component.type()), legacyEvidence, List.of()));
        }
        for (UiDesignSpec.FieldHint field : legacy.fieldHints()) {
            nodes.add(new UiDesignSpecV2.SemanticNode(
                    unique("field-" + slug(field.id()), usedIds),
                    "field-candidate", null,
                    new UiDesignSpecV2.InferenceEvidence(
                            List.of(), clamp(field.confidence()), "V1_ADAPTER", true, true),
                    List.of()));
        }
        for (UiDesignSpec.ActionSpec action : legacy.actions()) {
            nodes.add(new UiDesignSpecV2.SemanticNode(
                    unique("action-" + slug(action.type()), usedIds),
                    "action-candidate", null, legacyEvidence, List.of()));
        }

        List<UiDesignSpecV2.DesignIssue> issues = new ArrayList<>();
        issues.add(new UiDesignSpecV2.DesignIssue(
                "LEGACY_EVIDENCE_UNAVAILABLE", UiDesignSpecV2.Severity.WARNING,
                "UiDesignSpec v1에는 원본 Node별 Evidence가 없어 사람 검토가 필요합니다.", specId));
        for (String uncertainty : legacy.uncertainties()) {
            issues.add(new UiDesignSpecV2.DesignIssue(
                    "LEGACY_UNCERTAINTY", UiDesignSpecV2.Severity.WARNING,
                    uncertainty, specId));
        }

        String hash = contentHash(specId, legacy, source);
        List<String> visibleIds = nodes.stream().map(UiDesignSpecV2.SemanticNode::semanticId).toList();
        return new UiDesignSpecV2(
                specId, UiDesignSpecV2.SCHEMA_VERSION, hash, source, null,
                nodes, List.of(),
                List.of(new UiDesignSpecV2.ResponsiveStructure("legacy-default", visibleIds, visibleIds)),
                nodes.stream().map(node -> new UiDesignSpecV2.RenderabilityAssessment(
                        node.semanticId(), UiDesignSpecV2.RenderabilityDecision.APPROXIMATED,
                        "v1에는 Node별 생성 가능성 근거가 없습니다.", false)).toList(),
                issues, legacyConfidence(legacy));
    }

    private String contentHash(String specId, UiDesignSpec legacy, UiDesignSpecV2.Source source) {
        try {
            byte[] canonical = canonicalMapper.writeValueAsBytes(List.of(specId, source, legacy));
            return ContentHashes.sha256Hex(canonical);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("UiDesignSpec v1 변환 Hash를 계산할 수 없습니다.", e);
        }
    }

    private UiDesignSpecV2.InferenceEvidence legacyEvidence() {
        return new UiDesignSpecV2.InferenceEvidence(
                List.of(), 0.5, "V1_ADAPTER", true, true);
    }

    private double legacyConfidence(UiDesignSpec legacy) {
        return legacy.fieldHints().isEmpty()
                ? 0.5
                : legacy.fieldHints().stream().mapToDouble(UiDesignSpec.FieldHint::confidence)
                        .map(UiDesignSpecV1ToV2Adapter::clamp).average().orElse(0.5);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private String unique(String base, LinkedHashSet<String> used) {
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) candidate = base + "-" + suffix++;
        return candidate;
    }

    private String slug(String value) {
        String normalized = normalize(value).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9가-힣]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
