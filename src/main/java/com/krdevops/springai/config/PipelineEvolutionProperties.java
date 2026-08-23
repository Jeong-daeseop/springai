package com.krdevops.springai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 5축 파이프라인 계약을 관찰 모드부터 신규 Apply까지 단계적으로 전환하는 Flag. */
@ConfigurationProperties(prefix = "app.pipeline-evolution")
public class PipelineEvolutionProperties {

    private Mode mode = Mode.DISABLED;
    private double autoApprovalConfidenceThreshold = 0.85;
    private double evidenceConfidenceThreshold = 0.60;

    @PostConstruct
    void validate() {
        if (mode == null) {
            throw new IllegalStateException("app.pipeline-evolution.mode는 필수입니다.");
        }
        requireConfidence(autoApprovalConfidenceThreshold,
                "app.pipeline-evolution.auto-approval-confidence-threshold");
        requireConfidence(evidenceConfidenceThreshold,
                "app.pipeline-evolution.evidence-confidence-threshold");
        if (evidenceConfidenceThreshold > autoApprovalConfidenceThreshold) {
            throw new IllegalStateException(
                    "evidence-confidence-threshold는 auto-approval-confidence-threshold보다 클 수 없습니다.");
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public double getAutoApprovalConfidenceThreshold() {
        return autoApprovalConfidenceThreshold;
    }

    public void setAutoApprovalConfidenceThreshold(double value) {
        autoApprovalConfidenceThreshold = value;
    }

    public double getEvidenceConfidenceThreshold() {
        return evidenceConfidenceThreshold;
    }

    public void setEvidenceConfidenceThreshold(double value) {
        evidenceConfidenceThreshold = value;
    }

    public boolean writesV2Artifacts() {
        return mode != Mode.DISABLED;
    }

    public boolean readsV2Artifacts() {
        return mode == Mode.DUAL_READ || mode == Mode.V2_PREVIEW || mode == Mode.V2_APPLY;
    }

    public boolean usesV2Preview() {
        return mode == Mode.V2_PREVIEW || mode == Mode.V2_APPLY;
    }

    public boolean usesV2Apply() {
        return mode == Mode.V2_APPLY;
    }

    public enum Mode {
        DISABLED,
        OBSERVE,
        DUAL_READ,
        V2_PREVIEW,
        V2_APPLY
    }

    private static void requireConfidence(double value, String property) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalStateException(property + "는 0.0 이상 1.0 이하여야 합니다.");
        }
    }
}
