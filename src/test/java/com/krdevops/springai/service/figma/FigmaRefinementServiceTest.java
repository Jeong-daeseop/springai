package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.FigmaRefinementRepository;
import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementConflictStatus;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementOwner;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatch;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatchSet;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPreview;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPropertyType;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementScope;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaRefinementServiceTest {

    private final FigmaRefinementRepository repository = mock(FigmaRefinementRepository.class);
    private final FigmaRefinementConflictService conflictService = new FigmaRefinementConflictService();
    private final FigmaReviewHistoryRepository reviewHistoryRepository = mock(FigmaReviewHistoryRepository.class);
    private final FigmaRefinementService service = new FigmaRefinementService(
            repository, conflictService, reviewHistoryRepository, new ObjectMapper());

    @Test
    void captureRejectsCandidateNotInCapturedStatus() {
        FigmaRefinementPatchSet draft = patchSet("p1", FigmaRefinementStatus.DRAFT);

        assertThatThrownBy(() -> service.capture(draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REFINEMENT_CAPTURE_STATUS_INVALID");
    }

    @Test
    void captureSavesAndReturnsStoredPatchSet() {
        FigmaRefinementPatchSet candidate = patchSet("p1", FigmaRefinementStatus.CAPTURED);
        when(repository.findById("p1")).thenReturn(Optional.of(candidate));

        FigmaRefinementPatchSet result = service.capture(candidate);

        assertThat(result.patchSetId()).isEqualTo("p1");
    }

    @Test
    void previewClassifiesMatchingPatchAsAppliedAndBlockedScopeAsBlocked() {
        FigmaRefinementPatch okPatch = patch("n1", "width", 160, 176, FigmaRefinementScope.CONDITIONAL, FigmaRefinementOwner.MANUAL_REFINEMENT);
        FigmaRefinementPatch blockedPatch = patch("n1", "visible", true, false, FigmaRefinementScope.BLOCKED, FigmaRefinementOwner.MANUAL_REFINEMENT);
        FigmaRefinementPatchSet candidate = new FigmaRefinementPatchSet(
                "p1", "qna-detail", 3, "fnv1a32:hash:1", FigmaRefinementStatus.CAPTURED,
                LocalDateTime.now(), null, null, null, List.of(okPatch, blockedPatch));
        FigmaNodeSpec tree = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.COMPONENT, "krds.detailRow",
                Map.of("width", 160), null, List.of());

        FigmaRefinementPreview preview = service.preview(candidate, tree, true);

        assertThat(preview.applied()).hasSize(1);
        assertThat(preview.applied().get(0).propertyPath()).isEqualTo("width");
        assertThat(preview.blocked()).hasSize(1);
        assertThat(preview.blocked().get(0).propertyPath()).isEqualTo("visible");
    }

    @Test
    void approveRequiresActor() {
        assertThatThrownBy(() -> service.approve("p1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor");
    }

    @Test
    void approveTransitionsSupersedesPreviousApprovedAndRecordsAudit() {
        FigmaRefinementPatchSet target = patchSet("p2", FigmaRefinementStatus.REVIEW_REQUIRED);
        FigmaRefinementPatchSet approved = patchSet("p2", FigmaRefinementStatus.APPROVED);
        FigmaRefinementPatchSet previouslyApproved = patchSet("p1", FigmaRefinementStatus.APPROVED);
        when(repository.findById("p2")).thenReturn(Optional.of(target));
        when(repository.transition("p2", FigmaRefinementStatus.REVIEW_REQUIRED, FigmaRefinementStatus.APPROVED, "operator-1", "ok"))
                .thenReturn(approved);
        when(repository.findByScreen("qna-detail")).thenReturn(List.of(previouslyApproved, approved));

        FigmaRefinementPatchSet result = service.approve("p2", "operator-1", "ok");

        assertThat(result.status()).isEqualTo(FigmaRefinementStatus.APPROVED);
        verify(repository).transition("p1", FigmaRefinementStatus.APPROVED, FigmaRefinementStatus.SUPERSEDED, null, null);
        verify(reviewHistoryRepository).save(any(FigmaReviewEvent.class));
    }

    @Test
    void rejectRequiresActor() {
        assertThatThrownBy(() -> service.reject("p1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor");
    }

    @Test
    void rejectTransitionsFromReviewRequiredAndRecordsAudit() {
        FigmaRefinementPatchSet current = patchSet("p1", FigmaRefinementStatus.REVIEW_REQUIRED);
        FigmaRefinementPatchSet rejected = patchSet("p1", FigmaRefinementStatus.REJECTED);
        when(repository.findById("p1")).thenReturn(Optional.of(current));
        when(repository.transition("p1", FigmaRefinementStatus.REVIEW_REQUIRED, FigmaRefinementStatus.REJECTED, "operator-1", "반려"))
                .thenReturn(rejected);

        FigmaRefinementPatchSet result = service.reject("p1", "operator-1", "반려");

        assertThat(result.status()).isEqualTo(FigmaRefinementStatus.REJECTED);
        verify(reviewHistoryRepository).save(any(FigmaReviewEvent.class));
    }

    /** MR-R08: 사람 검토 전(CAPTURED) 상태도 폐기(discard) 가능해야 한다. */
    @Test
    void rejectAlsoDiscardsFromCapturedStatus() {
        FigmaRefinementPatchSet current = patchSet("p1", FigmaRefinementStatus.CAPTURED);
        FigmaRefinementPatchSet rejected = patchSet("p1", FigmaRefinementStatus.REJECTED);
        when(repository.findById("p1")).thenReturn(Optional.of(current));
        when(repository.transition("p1", FigmaRefinementStatus.CAPTURED, FigmaRefinementStatus.REJECTED, "operator-1", "폐기"))
                .thenReturn(rejected);

        FigmaRefinementPatchSet result = service.reject("p1", "operator-1", "폐기");

        assertThat(result.status()).isEqualTo(FigmaRefinementStatus.REJECTED);
    }

    @Test
    void markAppliedTransitionsFromApprovedToApplied() {
        FigmaRefinementPatchSet applied = patchSet("p1", FigmaRefinementStatus.APPLIED);
        when(repository.transition("p1", FigmaRefinementStatus.APPROVED, FigmaRefinementStatus.APPLIED, null, null))
                .thenReturn(applied);

        assertThat(service.markApplied("p1").status()).isEqualTo(FigmaRefinementStatus.APPLIED);
    }

    @Test
    void computeMaterializationHashIsDeterministicForSameTree() {
        FigmaNodeSpec tree = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.COMPONENT, "krds.detailRow",
                Map.of("width", 160), null, List.of());

        String first = service.computeMaterializationHash(tree);
        String second = service.computeMaterializationHash(tree);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("fnv1a32:");
    }

    @Test
    void computeMaterializationHashDiffersForDifferentTrees() {
        FigmaNodeSpec treeA = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.COMPONENT, "krds.detailRow",
                Map.of("width", 160), null, List.of());
        FigmaNodeSpec treeB = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.COMPONENT, "krds.detailRow",
                Map.of("width", 176), null, List.of());

        assertThat(service.computeMaterializationHash(treeA))
                .isNotEqualTo(service.computeMaterializationHash(treeB));
    }

    private FigmaRefinementPatch patch(
            String logicalNodeId, String propertyPath, Object before, Object after,
            FigmaRefinementScope scope, FigmaRefinementOwner owner) {
        return new FigmaRefinementPatch(logicalNodeId, "krds.detailRow", propertyPath,
                FigmaRefinementPropertyType.NUMBER, before, after, owner, scope, FigmaRefinementConflictStatus.NONE);
    }

    private FigmaRefinementPatchSet patchSet(String patchSetId, FigmaRefinementStatus status) {
        return new FigmaRefinementPatchSet(
                patchSetId, "qna-detail", 3, "fnv1a32:hash:1", status,
                LocalDateTime.now(), null, null, null, List.of());
    }
}
