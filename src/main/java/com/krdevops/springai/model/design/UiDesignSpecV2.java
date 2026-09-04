package com.krdevops.springai.model.design;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 원본 Node, 추론 근거, 반응형 관찰 결과와 생성 손실을 보존하는 Design IR v2.
 * 업무 Field·Route·Permission은 확정하지 않고 시각 후보만 표현한다.
 */
public record UiDesignSpecV2(
        String specId,
        String schemaVersion,
        String contentHash,
        Source source,
        @Nullable VersionedArtifactReference designSystemSnapshotRef,
        List<SemanticNode> nodes,
        List<ResponsivePolicy> responsivePolicySet,
        List<ResponsiveStructure> responsiveStructureSet,
        List<RenderabilityAssessment> renderabilityAssessments,
        List<DesignIssue> issues,
        double confidenceSummary
) {
    public static final String SCHEMA_VERSION = "2.0";

    public UiDesignSpecV2 {
        specId = requireText(specId, "specId");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("UiDesignSpecV2 schemaVersion은 2.0이어야 합니다.");
        }
        contentHash = ContentHashes.requireValid(contentHash);
        if (source == null) throw new IllegalArgumentException("source는 필수입니다.");
        nodes = immutable(nodes);
        responsivePolicySet = immutable(responsivePolicySet);
        responsiveStructureSet = immutable(responsiveStructureSet);
        renderabilityAssessments = immutable(renderabilityAssessments);
        issues = immutable(issues);
        requireConfidence(confidenceSummary, "confidenceSummary");
        requireUniqueSemanticIds(nodes);
    }

    public record Source(
            SourceType sourceType,
            @Nullable String fileKey,
            @Nullable String nodeId,
            String sourceRevision
    ) {
        public Source {
            if (sourceType == null) throw new IllegalArgumentException("sourceType은 필수입니다.");
            fileKey = normalize(fileKey);
            nodeId = normalize(nodeId);
            sourceRevision = requireText(sourceRevision, "sourceRevision");
            if (sourceType == SourceType.FIGMA && (fileKey == null || nodeId == null)) {
                throw new IllegalArgumentException("FIGMA Source에는 fileKey와 nodeId가 필요합니다.");
            }
        }
    }

    public enum SourceType { FIGMA, IMAGE, PDF, WEB_CAPTURE }

    public record InferenceEvidence(
            List<String> sourceNodeRefs,
            double confidence,
            String inferenceMethod,
            boolean requiresReview,
            boolean legacyUnknown
    ) {
        public InferenceEvidence {
            sourceNodeRefs = immutable(sourceNodeRefs);
            requireConfidence(confidence, "confidence");
            inferenceMethod = requireText(inferenceMethod, "inferenceMethod");
            if (sourceNodeRefs.isEmpty() && !legacyUnknown) {
                throw new IllegalArgumentException(
                        "시각 추론에는 sourceNodeRefs가 필요하며 Legacy 변환만 legacyUnknown을 사용할 수 있습니다.");
            }
        }
    }

    public record SemanticNode(
            String semanticId,
            String role,
            @Nullable String logicalType,
            @Nullable Geometry geometry,
            Map<String, String> layoutConstraints,
            @Nullable ComponentReference componentRef,
            @Nullable VisualStyle visualStyle,
            List<TokenBinding> tokenBindings,
            List<InteractionCandidate> interactionCandidates,
            InferenceEvidence evidence
    ) {
        public SemanticNode {
            semanticId = requireText(semanticId, "semanticId");
            role = requireText(role, "role");
            logicalType = normalize(logicalType);
            layoutConstraints = layoutConstraints == null ? Map.of() : Map.copyOf(layoutConstraints);
            tokenBindings = immutable(tokenBindings);
            interactionCandidates = immutable(interactionCandidates);
            if (evidence == null) throw new IllegalArgumentException("node evidence는 필수입니다.");
        }

        /** 최소 Node 생성을 위한 호환 생성자. */
        public SemanticNode(
                String semanticId, String role, @Nullable String logicalType,
                @Nullable Geometry geometry, Map<String, String> layoutConstraints,
                @Nullable ComponentReference componentRef, List<TokenBinding> tokenBindings,
                List<InteractionCandidate> interactionCandidates, InferenceEvidence evidence) {
            this(semanticId, role, logicalType, geometry, layoutConstraints, componentRef, null,
                    tokenBindings, interactionCandidates, evidence);
        }

        /** 최소 Node 생성을 위한 호환 생성자. */
        public SemanticNode(
                String semanticId, String role, @Nullable String logicalType,
                InferenceEvidence evidence, List<TokenBinding> tokenBindings) {
            this(semanticId, role, logicalType, null, Map.of(), null, null,
                    tokenBindings, List.of(), evidence);
        }
    }

    public record VisualStyle(
            double opacity, List<VisualPaint> fills, List<VisualPaint> strokes) {
        public VisualStyle {
            opacity = Math.max(0, Math.min(1, opacity));
            fills = immutable(fills);
            strokes = immutable(strokes);
        }
    }

    public record VisualPaint(
            String type, boolean visible, double opacity,
            @Nullable String color, @Nullable String imageRef, @Nullable String scaleMode) {
        public VisualPaint {
            type = type == null || type.isBlank() ? "UNKNOWN" : type.toUpperCase();
            opacity = Math.max(0, Math.min(1, opacity));
        }
    }

    public record Geometry(double x, double y, double width, double height) {
        public Geometry {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(width) || !Double.isFinite(height)
                    || width < 0 || height < 0) {
                throw new IllegalArgumentException("Geometry는 유한한 좌표와 0 이상의 크기를 사용해야 합니다.");
            }
        }
    }

    public record ComponentReference(
            String logicalType,
            String componentSetKey,
            @Nullable VersionedArtifactReference mappingRef,
            Map<String, String> componentProperties
    ) {
        public ComponentReference {
            logicalType = requireText(logicalType, "component logicalType");
            componentSetKey = requireText(componentSetKey, "componentSetKey");
            componentProperties = componentProperties == null
                    ? Map.of() : Map.copyOf(componentProperties);
        }

        /** componentProperties 도입 전 호출자 호환. */
        public ComponentReference(
                String logicalType, String componentSetKey,
                @Nullable VersionedArtifactReference mappingRef) {
            this(logicalType, componentSetKey, mappingRef, Map.of());
        }
    }

    public record TokenBinding(String property, String tokenName, @Nullable String variableId) {
        public TokenBinding {
            property = requireText(property, "token property");
            tokenName = requireText(tokenName, "tokenName");
            variableId = normalize(variableId);
        }
    }

    public record InteractionCandidate(
            String trigger,
            String result,
            InferenceEvidence evidence
    ) {
        public InteractionCandidate {
            trigger = requireText(trigger, "interaction trigger");
            result = requireText(result, "interaction result");
            if (evidence == null) throw new IllegalArgumentException("interaction evidence는 필수입니다.");
        }
    }

    public record ResponsivePolicy(
            String semanticId,
            ResponsiveBehavior behavior,
            InferenceEvidence evidence
    ) {
        public ResponsivePolicy {
            semanticId = requireText(semanticId, "responsive semanticId");
            if (behavior == null) throw new IllegalArgumentException("responsive behavior는 필수입니다.");
            if (evidence == null) throw new IllegalArgumentException("responsive evidence는 필수입니다.");
        }
    }

    public enum ResponsiveBehavior { REFLOW, WRAP, RESIZE, HIDE, SWAP, FIXED }

    public record ResponsiveStructure(
            String viewportId,
            List<String> visibleSemanticIds,
            List<String> order
    ) {
        public ResponsiveStructure {
            viewportId = requireText(viewportId, "viewportId");
            visibleSemanticIds = immutable(visibleSemanticIds);
            order = immutable(order);
            if (!new HashSet<>(visibleSemanticIds).containsAll(order)) {
                throw new IllegalArgumentException("order는 visibleSemanticIds에 포함된 Node만 참조해야 합니다.");
            }
        }
    }

    public record RenderabilityAssessment(
            String semanticId,
            RenderabilityDecision decision,
            @Nullable String lossDescription,
            boolean approved
    ) {
        public RenderabilityAssessment {
            semanticId = requireText(semanticId, "renderability semanticId");
            if (decision == null) throw new IllegalArgumentException("renderability decision은 필수입니다.");
            lossDescription = normalize(lossDescription);
            if ((decision == RenderabilityDecision.APPROXIMATED
                    || decision == RenderabilityDecision.RASTERIZED) && lossDescription == null) {
                throw new IllegalArgumentException("손실이 있는 Renderability에는 lossDescription이 필요합니다.");
            }
            if (decision == RenderabilityDecision.UNSUPPORTED && approved) {
                throw new IllegalArgumentException("UNSUPPORTED Node는 승인할 수 없습니다.");
            }
        }
    }

    public enum RenderabilityDecision { NATIVE, COMPOSED, APPROXIMATED, RASTERIZED, UNSUPPORTED }

    public record DesignIssue(String code, Severity severity, String message, @Nullable String target) {
        public DesignIssue {
            code = requireText(code, "issue code");
            if (severity == null) throw new IllegalArgumentException("issue severity는 필수입니다.");
            message = requireText(message, "issue message");
            target = normalize(target);
        }
    }

    public enum Severity { INFO, WARNING, ERROR, FATAL }

    private static void requireUniqueSemanticIds(List<SemanticNode> nodes) {
        HashSet<String> ids = new HashSet<>();
        for (SemanticNode node : nodes) {
            if (!ids.add(node.semanticId())) {
                throw new IllegalArgumentException("semanticId는 중복될 수 없습니다: " + node.semanticId());
            }
        }
    }

    private static void requireConfidence(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(field + "는 0.0 이상 1.0 이하여야 합니다.");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "는 필수입니다.");
        return value.trim();
    }

    private static @Nullable String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> List<T> immutable(@Nullable List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
