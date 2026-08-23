package com.krdevops.springai.service.generation;

import org.springframework.stereotype.Service;

/** Semantic Merge Plan의 Conflict가 있으면 Approved Write Port 호출 전 Apply를 차단한다. */
@Service
public class ApprovedWriteConflictGuard {
    public void requireApplyAllowed(SemanticMergePlanService.SemanticMergePlan plan) {
        if (plan == null) throw new IllegalArgumentException("SemanticMergePlan은 필수입니다.");
        if (!plan.applyAllowed()) throw new ApplyConflictBlockedException(plan);
    }

    public static final class ApplyConflictBlockedException extends IllegalStateException {
        private final SemanticMergePlanService.SemanticMergePlan plan;
        public ApplyConflictBlockedException(SemanticMergePlanService.SemanticMergePlan plan) {
            super("Semantic Merge Conflict로 Approved Write Port Apply가 차단되었습니다: " + plan.conflictRegionIds());
            this.plan = plan;
        }
        public SemanticMergePlanService.SemanticMergePlan plan() { return plan; }
    }
}
