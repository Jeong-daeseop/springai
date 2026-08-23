package com.krdevops.springai.service;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.contract.PipelineEvolutionErrorCode;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** UiDesignSpec v2의 자동 승인과 Apply 가능 여부를 Evidence·Renderability 기준으로 판정한다. */
@Component
public class UiDesignSpecV2QualityValidator {

    private final PipelineEvolutionProperties properties;

    public UiDesignSpecV2QualityValidator(PipelineEvolutionProperties properties) {
        this.properties = properties;
    }

    public ValidationResult validateForAutoApproval(UiDesignSpecV2 spec) {
        return validate(spec, Purpose.AUTO_APPROVAL);
    }

    public ValidationResult validateForApply(UiDesignSpecV2 spec) {
        return validate(spec, Purpose.APPLY);
    }

    private ValidationResult validate(UiDesignSpecV2 spec, Purpose purpose) {
        if (spec == null) throw new IllegalArgumentException("UiDesignSpecV2는 필수입니다.");
        List<QualityIssue> issues = new ArrayList<>();

        Map<String, UiDesignSpecV2.SemanticNode> nodes = spec.nodes().stream()
                .collect(Collectors.toMap(
                        UiDesignSpecV2.SemanticNode::semanticId, Function.identity()));
        for (UiDesignSpecV2.SemanticNode node : spec.nodes()) {
            validateEvidence(node.semanticId(), node.evidence(), purpose, issues);
            for (UiDesignSpecV2.InteractionCandidate interaction : node.interactionCandidates()) {
                validateEvidence(node.semanticId(), interaction.evidence(), purpose, issues);
            }
        }
        for (UiDesignSpecV2.ResponsivePolicy policy : spec.responsivePolicySet()) {
            validateEvidence(policy.semanticId(), policy.evidence(), purpose, issues);
        }
        for (UiDesignSpecV2.RenderabilityAssessment assessment : spec.renderabilityAssessments()) {
            validateRenderability(nodes.get(assessment.semanticId()), assessment, purpose, issues);
        }

        boolean blocked = issues.stream().anyMatch(issue -> issue.severity() == Severity.BLOCK);
        return new ValidationResult(!blocked, List.copyOf(issues));
    }

    private void validateEvidence(
            String target,
            UiDesignSpecV2.InferenceEvidence evidence,
            Purpose purpose,
            List<QualityIssue> issues) {
        if (evidence.sourceNodeRefs().isEmpty()) {
            issues.add(issue(PipelineEvolutionErrorCode.DESIGN_EVIDENCE_MISSING,
                    purpose == Purpose.APPLY ? Severity.BLOCK : Severity.REVIEW,
                    target, "원본 Node Evidence가 없습니다."));
        }
        if (evidence.confidence() < properties.getEvidenceConfidenceThreshold()) {
            issues.add(issue(PipelineEvolutionErrorCode.DESIGN_CONFIDENCE_TOO_LOW,
                    Severity.BLOCK, target,
                    "Evidence Confidence가 최소 기준보다 낮습니다: " + evidence.confidence()));
        } else if (evidence.confidence() < properties.getAutoApprovalConfidenceThreshold()
                || evidence.requiresReview() || evidence.legacyUnknown()) {
            issues.add(issue(PipelineEvolutionErrorCode.DESIGN_CONFIDENCE_TOO_LOW,
                    purpose == Purpose.AUTO_APPROVAL || purpose == Purpose.APPLY
                            ? Severity.BLOCK : Severity.REVIEW,
                    target, "자동 승인 기준을 충족하지 못해 사람 검토가 필요합니다."));
        }
    }

    private void validateRenderability(
            UiDesignSpecV2.SemanticNode node,
            UiDesignSpecV2.RenderabilityAssessment assessment,
            Purpose purpose,
            List<QualityIssue> issues) {
        if (assessment.decision() == UiDesignSpecV2.RenderabilityDecision.UNSUPPORTED) {
            issues.add(issue(PipelineEvolutionErrorCode.RENDERER_CAPABILITY_UNSUPPORTED,
                    Severity.BLOCK, assessment.semanticId(), "지원하지 않는 Node는 생성할 수 없습니다."));
            return;
        }
        if (assessment.decision() == UiDesignSpecV2.RenderabilityDecision.RASTERIZED
                && isSemanticContent(node)) {
            issues.add(issue(PipelineEvolutionErrorCode.RENDERER_CAPABILITY_UNSUPPORTED,
                    Severity.BLOCK, assessment.semanticId(),
                    "Form·Table·Text 의미 콘텐츠는 Raster Fallback을 사용할 수 없습니다."));
            return;
        }
        if ((assessment.decision() == UiDesignSpecV2.RenderabilityDecision.APPROXIMATED
                || assessment.decision() == UiDesignSpecV2.RenderabilityDecision.RASTERIZED)
                && !assessment.approved()) {
            issues.add(issue(PipelineEvolutionErrorCode.RENDERER_CAPABILITY_UNSUPPORTED,
                    purpose == Purpose.APPLY ? Severity.BLOCK : Severity.REVIEW,
                    assessment.semanticId(), "손실이 있는 생성 전략은 명시적 승인이 필요합니다."));
        }
    }

    private boolean isSemanticContent(UiDesignSpecV2.SemanticNode node) {
        if (node == null) return false;
        String value = (node.role() + " " + (node.logicalType() == null ? "" : node.logicalType()))
                .toLowerCase(Locale.ROOT);
        return containsAny(value, "form", "field", "input", "table", "grid", "text",
                "폼", "필드", "입력", "표", "테이블", "텍스트");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private QualityIssue issue(
            PipelineEvolutionErrorCode code, Severity severity, String target, String message) {
        return new QualityIssue(code, severity, target, message);
    }

    private enum Purpose { AUTO_APPROVAL, APPLY }

    public enum Severity { REVIEW, BLOCK }

    public record QualityIssue(
            PipelineEvolutionErrorCode code,
            Severity severity,
            String target,
            String message
    ) {}

    public record ValidationResult(boolean allowed, List<QualityIssue> issues) {
        public ValidationResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}
