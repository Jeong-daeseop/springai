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
import com.krdevops.springai.model.figma.refinement.FigmaRefinementScope;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MR-S06: Manual Refinement Patch Set의 Capture 저장, Preview, 승인, 반려, 폐기,
 * 화면별 승인 Patch 조회를 담당한다. 상태 전이·승인 권한 원칙은 `CONTRACT_RULES.md` §10을 따른다.
 */
@Service
public class FigmaRefinementService {

    private final FigmaRefinementRepository repository;
    private final FigmaRefinementConflictService conflictService;
    private final FigmaReviewHistoryRepository reviewHistoryRepository;
    private final ObjectMapper objectMapper;

    public FigmaRefinementService(
            FigmaRefinementRepository repository,
            FigmaRefinementConflictService conflictService,
            FigmaReviewHistoryRepository reviewHistoryRepository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.conflictService = conflictService;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * MR-C02: baseMaterializationHash 계산. Figma Plugin 샌드박스(QuickJS)는 Web Crypto
     * SHA-256을 쓸 수 없으므로, Plugin의 {@code stableByteHash}(FNV-1a32, KRV-066에서 도입)와
     * 정확히 같은 알고리즘·포맷(`fnv1a32:{8자리 hex}:{byte 길이}`)을 재사용해 두 쪽이
     * 동일 입력에서 항상 같은 해시를 계산하게 한다.
     */
    public String computeMaterializationHash(FigmaNodeSpec tree) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(tree);
            int hash = 0x811c9dc5;
            for (byte b : json) {
                hash ^= (b & 0xFF);
                hash *= 0x01000193;
            }
            return "fnv1a32:" + String.format("%08x", hash) + ":" + json.length;
        } catch (Exception exception) {
            throw new IllegalStateException("Materialization Hash 계산에 실패했습니다.", exception);
        }
    }

    /** MR-A02: Plugin이 계산한 Diff를 CAPTURED 상태로 저장한다. 같은 ID+같은 내용은 멱등 허용. */
    public FigmaRefinementPatchSet capture(FigmaRefinementPatchSet candidate) {
        if (candidate.status() != FigmaRefinementStatus.CAPTURED) {
            throw new IllegalArgumentException("REFINEMENT_CAPTURE_STATUS_INVALID: capture는 CAPTURED 상태만 허용합니다.");
        }
        repository.saveImmutable(candidate);
        return repository.findById(candidate.patchSetId()).orElseThrow();
    }

    /** MR-A01: 저장 여부와 무관하게 Patch Set 후보를 즉시 재분류해 미리보기를 계산한다. */
    public FigmaRefinementPreview preview(FigmaRefinementPatchSet candidate, FigmaNodeSpec newTree, boolean baseHashMatches) {
        List<FigmaRefinementPreview.Entry> applied = new ArrayList<>();
        List<FigmaRefinementPreview.Entry> excluded = new ArrayList<>();
        List<FigmaRefinementPreview.Entry> blocked = new ArrayList<>();
        List<FigmaRefinementPreview.Entry> conflicts = new ArrayList<>();
        for (FigmaRefinementPatch patch : candidate.patches()) {
            FigmaRefinementConflictStatus status = conflictService.classify(patch, newTree, baseHashMatches);
            FigmaRefinementPreview.Entry entry = new FigmaRefinementPreview.Entry(
                    patch.logicalNodeId(), patch.propertyPath(), reasonFor(patch, status), status);
            if (patch.scope() == FigmaRefinementScope.BLOCKED || patch.owner() == FigmaRefinementOwner.SYSTEM_LAYOUT) {
                blocked.add(entry);
            } else if (status == FigmaRefinementConflictStatus.NONE) {
                applied.add(entry);
            } else if (status == FigmaRefinementConflictStatus.POLICY_BLOCKED) {
                blocked.add(entry);
            } else {
                conflicts.add(entry);
            }
        }
        return new FigmaRefinementPreview(candidate.patchSetId(), candidate.screenId(), LocalDateTime.now(),
                applied, excluded, blocked, conflicts);
    }

    /** 이미 저장된 Patch Set을 조회해 미리보기를 계산한다. */
    public FigmaRefinementPreview previewStored(String patchSetId, FigmaNodeSpec newTree, boolean baseHashMatches) {
        FigmaRefinementPatchSet stored = repository.findById(patchSetId)
                .orElseThrow(() -> new IllegalArgumentException("Refinement Patch Set을 찾을 수 없습니다: " + patchSetId));
        return preview(stored, newTree, baseHashMatches);
    }

    /** CAPTURED 상태에서 차단/충돌 없는 Preview 결과를 확인한 뒤 사람 승인 대기로 전환한다. */
    public FigmaRefinementPatchSet markReviewRequired(String patchSetId) {
        return repository.transition(patchSetId, FigmaRefinementStatus.CAPTURED,
                FigmaRefinementStatus.REVIEW_REQUIRED, null, null);
    }

    /**
     * MR-A05/MR-DEC-05: 운영자만 승인 가능(REST Controller가 승인 Scope를 강제한다).
     * 같은 화면의 기존 APPROVED Patch Set은 SUPERSEDED로 전이한다.
     */
    @Transactional
    public FigmaRefinementPatchSet approve(String patchSetId, String actor, String comment) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("승인에는 actor가 필요합니다.");
        }
        FigmaRefinementPatchSet target = repository.findById(patchSetId)
                .orElseThrow(() -> new IllegalArgumentException("Refinement Patch Set을 찾을 수 없습니다: " + patchSetId));
        FigmaRefinementPatchSet approved = repository.transition(
                patchSetId, FigmaRefinementStatus.REVIEW_REQUIRED, FigmaRefinementStatus.APPROVED, actor, comment);
        supersedePrevious(target.screenId(), patchSetId);
        recordAudit(patchSetId, FigmaReviewEvent.EventType.APPROVAL, "APPROVED", actor, comment);
        return approved;
    }

    /**
     * MR-A05/MR-R08: 운영자가 반려한다. 사람 승인 대기(REVIEW_REQUIRED)뿐 아니라 아직 승인
     * 절차를 시작하지 않은 CAPTURED 상태의 Patch Set도 이 메서드로 폐기(discard)할 수 있다 —
     * 별도 "폐기" 상태를 신설하지 않고 REJECTED로 통일해 상태 종류를 늘리지 않는다.
     */
    public FigmaRefinementPatchSet reject(String patchSetId, String actor, String comment) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("반려에는 actor가 필요합니다.");
        }
        FigmaRefinementPatchSet current = repository.findById(patchSetId)
                .orElseThrow(() -> new IllegalArgumentException("Refinement Patch Set을 찾을 수 없습니다: " + patchSetId));
        FigmaRefinementStatus expected = current.status() == FigmaRefinementStatus.CAPTURED
                ? FigmaRefinementStatus.CAPTURED : FigmaRefinementStatus.REVIEW_REQUIRED;
        FigmaRefinementPatchSet rejected = repository.transition(
                patchSetId, expected, FigmaRefinementStatus.REJECTED, actor, comment);
        recordAudit(patchSetId, FigmaReviewEvent.EventType.REJECTION, "REJECTED", actor, comment);
        return rejected;
    }

    /** MR-R: 승인된 Patch Set이 실제 재적용에 성공했음을 기록한다. */
    public FigmaRefinementPatchSet markApplied(String patchSetId) {
        return repository.transition(patchSetId, FigmaRefinementStatus.APPROVED,
                FigmaRefinementStatus.APPLIED, null, null);
    }

    public Optional<FigmaRefinementPatchSet> findLatestApprovedByScreen(String screenId) {
        return repository.findLatestApprovedByScreen(screenId);
    }

    public List<FigmaRefinementPatchSet> findByScreen(String screenId) {
        return repository.findByScreen(screenId);
    }

    public Optional<FigmaRefinementPatchSet> findById(String patchSetId) {
        return repository.findById(patchSetId);
    }

    public static String newPatchSetId(String screenId) {
        return screenId + "-refine-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void supersedePrevious(String screenId, String excludePatchSetId) {
        for (FigmaRefinementPatchSet candidate : repository.findByScreen(screenId)) {
            if (candidate.patchSetId().equals(excludePatchSetId)) continue;
            if (candidate.status() != FigmaRefinementStatus.APPROVED) continue;
            repository.transition(candidate.patchSetId(), FigmaRefinementStatus.APPROVED,
                    FigmaRefinementStatus.SUPERSEDED, null, null);
        }
    }

    private void recordAudit(String patchSetId, FigmaReviewEvent.EventType eventType, String status,
            String actor, String comment) {
        reviewHistoryRepository.save(new FigmaReviewEvent(
                UUID.randomUUID().toString(), FigmaReviewEvent.TargetType.MANUAL_REFINEMENT_PATCH_SET,
                patchSetId, "1", eventType, status, actor, comment, LocalDateTime.now()));
    }

    private String reasonFor(FigmaRefinementPatch patch, FigmaRefinementConflictStatus status) {
        return switch (status) {
            case NONE -> "ALLOWED/CONDITIONAL 속성이며 baseline과 현재 값이 일치함";
            case UPSTREAM_CHANGED -> "baseline 값과 새 Screen Spec 값이 같은 속성을 다르게 변경함";
            case TARGET_REMOVED -> "Patch 대상 logicalNodeId가 새 화면 트리에서 삭제됨";
            case TYPE_CHANGED -> "Patch 대상 logicalNodeId의 노드 타입이 baseline과 다름";
            case POLICY_BLOCKED -> patch.owner() == FigmaRefinementOwner.SYSTEM_LAYOUT
                    ? "SYSTEM_LAYOUT 소유 속성은 승인돼도 적용하지 않음" : "MVP 차단 속성 정책";
            case BASE_STALE -> "baseMaterializationHash가 현재 화면 상태와 다름";
        };
    }
}
