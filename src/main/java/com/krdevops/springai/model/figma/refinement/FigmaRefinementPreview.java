package com.krdevops.springai.model.figma.refinement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MR-S06/MR-C04: 승인 전/재적용 전 Patch 적용 가능성 미리보기.
 * `figma-refinement-preview-v1.schema.json`과 동일 구조.
 */
public record FigmaRefinementPreview(
        String patchSetId,
        String screenId,
        LocalDateTime generatedAt,
        List<Entry> applied,
        List<Entry> excluded,
        List<Entry> blocked,
        List<Entry> conflicts
) {
    public FigmaRefinementPreview {
        applied = applied == null ? List.of() : List.copyOf(applied);
        excluded = excluded == null ? List.of() : List.copyOf(excluded);
        blocked = blocked == null ? List.of() : List.copyOf(blocked);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public record Entry(
            String logicalNodeId, String propertyPath, String reason,
            FigmaRefinementConflictStatus conflictStatus) {}
}
