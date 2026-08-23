package com.krdevops.springai.service.generation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedWriteConflictGuardTest {
    @Test
    void Conflict가_있으면_Approved_Write를_차단한다() {
        var plan = new SemanticMergePlanService.SemanticMergePlan(
                List.of("binding.vo"), List.of(), List.of("binding.vo"), true, false);
        assertThatThrownBy(() -> new ApprovedWriteConflictGuard().requireApplyAllowed(plan))
                .isInstanceOf(ApprovedWriteConflictGuard.ApplyConflictBlockedException.class);
    }
}
