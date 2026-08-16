package com.krdevops.springai.model.figma.refinement;

/**
 * MR-S02: 속성 단위 Manual Refinement Patch 한 건.
 * `figma-refinement-patch-set-v1.schema.json`의 `$defs/refinementPatch`와 동일 구조.
 */
public record FigmaRefinementPatch(
        String logicalNodeId,
        String baselineLogicalType,
        String propertyPath,
        FigmaRefinementPropertyType propertyType,
        Object before,
        Object after,
        FigmaRefinementOwner owner,
        FigmaRefinementScope scope,
        FigmaRefinementConflictStatus conflictStatus
) {
    public FigmaRefinementPatch {
        if (logicalNodeId == null || logicalNodeId.isBlank()) {
            throw new IllegalArgumentException("logicalNodeId는 필수입니다.");
        }
        if (baselineLogicalType == null || baselineLogicalType.isBlank()) {
            throw new IllegalArgumentException("baselineLogicalType은 필수입니다.");
        }
        if (propertyPath == null || propertyPath.isBlank()) {
            throw new IllegalArgumentException("propertyPath는 필수입니다.");
        }
        if (propertyType == null) {
            throw new IllegalArgumentException("propertyType은 필수입니다.");
        }
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope는 필수입니다.");
        }
        conflictStatus = conflictStatus == null ? FigmaRefinementConflictStatus.NONE : conflictStatus;
    }
}
