package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.BoundThymeleafView;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import com.krdevops.springai.model.thymeleaf.ThymeleafSkeleton;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyThymeleafViewComposerTest {

    private final ThymeleafSkeletonPlanner planner = new ThymeleafSkeletonPlanner();
    private final LegacyThymeleafViewComposer composer = new LegacyThymeleafViewComposer(new GenerationIssueFactory());

    @Test
    void matchingScreenIdAndRoleComposeSuccessfully() {
        ThymeleafSkeleton skeleton = planner.plan("emp-list", LegacyScreenRole.LIST, "직원 목록");
        ThymeleafBindingContract contract = contract("emp-list", LegacyScreenRole.LIST);

        ThymeleafGenerationStageResult<BoundThymeleafView> result = composer.compose(skeleton, contract);

        assertThat(result.successful()).isTrue();
        assertThat(result.value().skeleton()).isEqualTo(skeleton);
        assertThat(result.value().contract()).isEqualTo(contract);
    }

    @Test
    void mismatchedScreenIdFailsWithFatalIssue() {
        ThymeleafSkeleton skeleton = planner.plan("emp-list", LegacyScreenRole.LIST, "직원 목록");
        ThymeleafBindingContract contract = contract("emp-detail", LegacyScreenRole.LIST);

        ThymeleafGenerationStageResult<BoundThymeleafView> result = composer.compose(skeleton, contract);

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("SKELETON_CONTRACT_MISMATCH"));
    }

    @Test
    void mismatchedScreenRoleFailsWithFatalIssue() {
        ThymeleafSkeleton skeleton = planner.plan("emp-list", LegacyScreenRole.LIST, "직원 목록");
        ThymeleafBindingContract contract = contract("emp-list", LegacyScreenRole.FORM);

        ThymeleafGenerationStageResult<BoundThymeleafView> result = composer.compose(skeleton, contract);

        assertThat(result.successful()).isFalse();
    }

    private ThymeleafBindingContract contract(String screenId, LegacyScreenRole role) {
        return new ThymeleafBindingContract(
                screenId, role,
                new ThymeleafRouteBinding("/emp/employerList.do", "GET", "selectEmployerList",
                        "searchVO", "EmployerVO", false, false, List.of()),
                List.of(), List.of(), null,
                List.of(), List.of(), BindingContractStatus.RESOLVED, List.of(),
                new SourceRevisionRef("emp-project", "rev-1", Instant.now()), Instant.now());
    }
}
