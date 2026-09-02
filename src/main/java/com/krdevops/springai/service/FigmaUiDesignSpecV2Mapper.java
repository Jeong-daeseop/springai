package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Figma Node Tree의 실제 Node ID와 Geometry를 보존하는 결정형 UiDesignSpec v2 Mapper. */
@Component
public class FigmaUiDesignSpecV2Mapper {

    private final ObjectMapper canonicalMapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public UiDesignSpecV2 map(
            String specId,
            FigmaReference reference,
            FigmaNodeDocument source,
            String featureType) {
        if (reference == null) throw new IllegalArgumentException("FigmaReference는 필수입니다.");
        if (source == null) throw new IllegalArgumentException("FigmaNodeDocument는 필수입니다.");
        JsonNode root = source.document();
        if (!"FRAME".equalsIgnoreCase(root.path("type").asText())) {
            throw new IllegalArgumentException("UiDesignSpec v2는 단일 Figma FRAME만 분석합니다.");
        }

        List<NodeEntry> entries = new ArrayList<>();
        collect(root, "0", entries);
        List<NodeEntry> visible = entries.stream().filter(NodeEntry::visible).toList();
        List<UiDesignSpecV2.SemanticNode> nodes = visible.stream().map(this::node).toList();
        List<String> visibleIds = nodes.stream().map(UiDesignSpecV2.SemanticNode::semanticId).toList();
        List<UiDesignSpecV2.RenderabilityAssessment> assessments = visible.stream()
                .map(this::renderability).toList();
        List<UiDesignSpecV2.DesignIssue> issues = assessments.stream()
                .filter(value -> !value.approved())
                .map(value -> new UiDesignSpecV2.DesignIssue(
                        "RENDERABILITY_REVIEW_REQUIRED", UiDesignSpecV2.Severity.WARNING,
                        "생성 손실 또는 미지원 가능성을 검토해야 합니다.", value.semanticId()))
                .toList();
        double confidence = nodes.isEmpty() ? 0 : nodes.stream()
                .map(UiDesignSpecV2.SemanticNode::evidence)
                .mapToDouble(UiDesignSpecV2.InferenceEvidence::confidence).average().orElse(0);

        UiDesignSpecV2.Source designSource = new UiDesignSpecV2.Source(
                UiDesignSpecV2.SourceType.FIGMA, reference.fileKey(), reference.nodeId(),
                source.fileVersion());
        return new UiDesignSpecV2(
                specId, UiDesignSpecV2.SCHEMA_VERSION,
                contentHash(reference, source, featureType), designSource, null,
                nodes, responsivePolicies(visible),
                List.of(new UiDesignSpecV2.ResponsiveStructure(
                        viewportId(root), visibleIds, visibleIds)),
                assessments, issues, confidence);
    }

    private UiDesignSpecV2.SemanticNode node(NodeEntry entry) {
        JsonNode raw = entry.raw();
        JsonNode box = raw.path("absoluteBoundingBox");
        UiDesignSpecV2.Geometry geometry = box.isObject()
                ? new UiDesignSpecV2.Geometry(
                        box.path("x").asDouble(), box.path("y").asDouble(),
                        Math.max(0, box.path("width").asDouble()),
                        Math.max(0, box.path("height").asDouble())) : null;
        Map<String, String> constraints = Map.of(
                "layoutMode", raw.path("layoutMode").asText("NONE"),
                "primaryAxisSizingMode", raw.path("primaryAxisSizingMode").asText("AUTO"),
                "counterAxisSizingMode", raw.path("counterAxisSizingMode").asText("AUTO"));
        UiDesignSpecV2.VisualStyle visualStyle = new UiDesignSpecV2.VisualStyle(
                raw.path("opacity").asDouble(1.0), visualPaints(raw.path("fills")), visualPaints(raw.path("strokes")));
        return new UiDesignSpecV2.SemanticNode(
                entry.semanticId(), role(raw), logicalType(raw), geometry, constraints, null, visualStyle,
                List.of(), interactions(entry),
                new UiDesignSpecV2.InferenceEvidence(
                        List.of(entry.sourceNodeRef()), confidence(raw), "FIGMA_NODE_TREE", false, false));
    }

    private List<UiDesignSpecV2.VisualPaint> visualPaints(JsonNode paints) {
        if (!paints.isArray()) return List.of();
        List<UiDesignSpecV2.VisualPaint> result = new ArrayList<>();
        for (JsonNode paint : paints) {
            String type = paint.path("type").asText("UNKNOWN");
            JsonNode color = paint.path("color");
            String rgba = color.isObject() ? "rgba(%d,%d,%d,%.2f)".formatted(
                    Math.round(color.path("r").asDouble(0) * 255),
                    Math.round(color.path("g").asDouble(0) * 255),
                    Math.round(color.path("b").asDouble(0) * 255),
                    Math.max(0, Math.min(1, color.path("a").asDouble(1)))) : null;
            result.add(new UiDesignSpecV2.VisualPaint(type, paint.path("visible").asBoolean(true),
                    paint.path("opacity").asDouble(1), rgba,
                    paint.path("imageRef").isTextual() ? paint.path("imageRef").asText() : null,
                    paint.path("scaleMode").isTextual() ? paint.path("scaleMode").asText() : null));
        }
        return List.copyOf(result);
    }

    private List<UiDesignSpecV2.InteractionCandidate> interactions(NodeEntry entry) {
        JsonNode interactions = entry.raw().path("interactions");
        if (!interactions.isArray()) return List.of();
        List<UiDesignSpecV2.InteractionCandidate> result = new ArrayList<>();
        interactions.forEach(value -> {
            String trigger = value.path("trigger").path("type").asText("");
            String action = value.path("actions").isArray() && !value.path("actions").isEmpty()
                    ? value.path("actions").get(0).path("type").asText("") : "";
            if (!trigger.isBlank() && !action.isBlank()) {
                result.add(new UiDesignSpecV2.InteractionCandidate(
                        trigger, action, new UiDesignSpecV2.InferenceEvidence(
                                List.of(entry.sourceNodeRef()), 1, "FIGMA_PROTOTYPE", false, false)));
            }
        });
        return List.copyOf(result);
    }

    private List<UiDesignSpecV2.ResponsivePolicy> responsivePolicies(List<NodeEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.raw().path("layoutMode").asText("").isBlank())
                .map(entry -> new UiDesignSpecV2.ResponsivePolicy(
                        entry.semanticId(), UiDesignSpecV2.ResponsiveBehavior.REFLOW,
                        new UiDesignSpecV2.InferenceEvidence(
                                List.of(entry.sourceNodeRef()), 0.9,
                                "FIGMA_AUTO_LAYOUT", false, false)))
                .toList();
    }

    private UiDesignSpecV2.RenderabilityAssessment renderability(NodeEntry entry) {
        String type = entry.raw().path("type").asText("").toUpperCase(Locale.ROOT);
        UiDesignSpecV2.RenderabilityDecision decision;
        String loss = null;
        boolean approved;
        switch (type) {
            case "FRAME", "GROUP", "SECTION", "COMPONENT", "INSTANCE" -> {
                decision = UiDesignSpecV2.RenderabilityDecision.COMPOSED;
                approved = true;
            }
            case "TEXT", "RECTANGLE", "ELLIPSE", "LINE" -> {
                decision = UiDesignSpecV2.RenderabilityDecision.NATIVE;
                approved = true;
            }
            case "VECTOR", "BOOLEAN_OPERATION", "STAR", "POLYGON" -> {
                decision = UiDesignSpecV2.RenderabilityDecision.APPROXIMATED;
                loss = "복합 Vector는 CSS 또는 승인 Asset으로 근사해야 합니다.";
                approved = false;
            }
            case "IMAGE" -> {
                decision = UiDesignSpecV2.RenderabilityDecision.RASTERIZED;
                loss = "Figma Image Fill을 정적 Asset으로 전달합니다.";
                approved = false;
            }
            default -> {
                decision = UiDesignSpecV2.RenderabilityDecision.UNSUPPORTED;
                loss = "지원하지 않는 Figma Node Type: " + type;
                approved = false;
            }
        }
        return new UiDesignSpecV2.RenderabilityAssessment(
                entry.semanticId(), decision, loss, approved);
    }

    private void collect(JsonNode node, String path, List<NodeEntry> entries) {
        String sourceRef = node.path("id").asText(path);
        String semanticId = semanticId(sourceRef, path);
        entries.add(new NodeEntry(semanticId, sourceRef,
                node.path("visible").asBoolean(true), node));
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (int index = 0; index < children.size(); index++) {
                collect(children.get(index), path + "." + index, entries);
            }
        }
    }

    private String semanticId(String sourceRef, String path) {
        String normalized = sourceRef.replaceAll("[^A-Za-z0-9._:-]", "-");
        return normalized.isBlank() ? "node-" + path : "node-" + normalized;
    }

    private String role(JsonNode node) {
        String type = node.path("type").asText("unknown").toLowerCase(Locale.ROOT);
        String name = node.path("name").asText("").toLowerCase(Locale.ROOT);
        if (name.contains("search") || name.contains("검색")) return "search-candidate";
        if (name.contains("button") || name.contains("버튼")) return "action-candidate";
        if ("text".equals(type)) return "text";
        return type;
    }

    private String logicalType(JsonNode node) {
        String type = node.path("type").asText("").toUpperCase(Locale.ROOT);
        if (!"COMPONENT".equals(type) && !"INSTANCE".equals(type)) return null;
        String name = node.path("name").asText("").trim();
        return name.isBlank() ? null : name;
    }

    private double confidence(JsonNode node) {
        return node.path("id").isTextual() && node.path("absoluteBoundingBox").isObject() ? 1 : 0.8;
    }

    private String viewportId(JsonNode root) {
        double width = root.path("absoluteBoundingBox").path("width").asDouble(0);
        return width >= 1200 ? "desktop" : width >= 768 ? "tablet" : "mobile";
    }

    private String contentHash(FigmaReference reference, FigmaNodeDocument source, String featureType) {
        try {
            byte[] payload = canonicalMapper.writeValueAsBytes(List.of(
                    reference.fileKey(), reference.nodeId(), source.fileVersion(),
                    featureType == null ? "" : featureType, source.document()));
            return ContentHashes.sha256Hex(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Figma UiDesignSpec v2 Hash를 계산할 수 없습니다.", e);
        }
    }

    private record NodeEntry(String semanticId, String sourceNodeRef, boolean visible, JsonNode raw) {}
}
