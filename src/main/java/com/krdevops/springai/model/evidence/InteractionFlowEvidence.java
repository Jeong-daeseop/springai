package com.krdevops.springai.model.evidence;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import java.util.List;

public record InteractionFlowEvidence(String flowId, List<Step> steps, FlowStatus status) {
    public InteractionFlowEvidence {
        if (flowId == null || flowId.isBlank() || status == null) throw new IllegalArgumentException("flowId·status는 필수입니다.");
        steps = List.copyOf(steps == null ? List.of() : steps);
    }
    public record Step(int order, String action, String route, String domState, VersionedArtifactReference screenshot) {
        public Step { if (order < 1 || action == null || action.isBlank() || route == null || route.isBlank()) throw new IllegalArgumentException("Flow Step 값이 올바르지 않습니다."); }
    }
    public enum FlowStatus { PASS, FAIL, INCOMPLETE }
}
