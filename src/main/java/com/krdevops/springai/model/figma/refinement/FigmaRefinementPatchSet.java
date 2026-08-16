package com.krdevops.springai.model.figma.refinement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MR-S01: 화면 하나에 대한 Manual Refinement Patch 묶음. 승인 전에는 영속 원본으로 취급하지
 * 않는다(MR-DEC-03). `equals()`는 record 기본 구현을 그대로 사용해
 * {@code FigmaRefinementRepository.saveImmutable}의 멱등 판정에 활용한다.
 */
public record FigmaRefinementPatchSet(
        String patchSetId,
        String screenId,
        int baseScreenVersion,
        String baseMaterializationHash,
        FigmaRefinementStatus status,
        LocalDateTime capturedAt,
        LocalDateTime approvedAt,
        String approvedBy,
        String approvalComment,
        List<FigmaRefinementPatch> patches
) {
    /** MR-A08: 단일 Patch Set이 담을 수 있는 최대 Patch 개수. 초과 요청은 REST 계층에서 400으로 거부한다. */
    public static final int MAX_PATCHES = 500;

    public FigmaRefinementPatchSet {
        if (patchSetId == null || patchSetId.isBlank()) {
            throw new IllegalArgumentException("patchSetId는 필수입니다.");
        }
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다.");
        }
        if (baseScreenVersion < 1) {
            throw new IllegalArgumentException("baseScreenVersion은 1 이상이어야 합니다.");
        }
        if (baseMaterializationHash == null || baseMaterializationHash.isBlank()) {
            throw new IllegalArgumentException("baseMaterializationHash는 필수입니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다.");
        }
        patches = patches == null ? List.of() : List.copyOf(patches);
        if (patches.size() > MAX_PATCHES) {
            throw new IllegalArgumentException(
                    "REFINEMENT_TOO_MANY_PATCHES: 최대 " + MAX_PATCHES + "개까지 허용됩니다 (요청 " + patches.size() + "개).");
        }
    }

    /** MR-R06: 승인된 Patch 중 실제 재적용 대상(ALLOWED/CONDITIONAL, SYSTEM_LAYOUT 아님)만 반환한다. */
    public List<FigmaRefinementPatch> applicablePatches() {
        return patches.stream()
                .filter(patch -> patch.scope() != FigmaRefinementScope.BLOCKED)
                .filter(patch -> patch.owner() != FigmaRefinementOwner.SYSTEM_LAYOUT)
                .toList();
    }
}
