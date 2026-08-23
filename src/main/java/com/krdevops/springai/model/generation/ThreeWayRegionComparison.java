package com.krdevops.springai.model.generation;

import com.krdevops.springai.model.artifact.ContentHashes;

import java.util.List;

/** Base·Current·New Region Hash를 함께 보유하는 증분 생성 비교 모델. */
public record ThreeWayRegionComparison(
        String regionId,
        String baseHash,
        String currentHash,
        String newHash,
        ChangeStatus status
) {
    public ThreeWayRegionComparison {
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId는 필수입니다.");
        validateOptionalHash(baseHash, "baseHash");
        validateOptionalHash(currentHash, "currentHash");
        validateOptionalHash(newHash, "newHash");
        if (status == null) throw new IllegalArgumentException("status는 필수입니다.");
    }

    public static ThreeWayRegionComparison compare(String regionId, String baseHash,
                                                   String currentHash, String newHash) {
        ChangeStatus status;
        boolean currentChanged = !same(baseHash, currentHash);
        boolean newChanged = !same(baseHash, newHash);
        if (!currentChanged && !newChanged) status = ChangeStatus.UNCHANGED;
        else if (!currentChanged) status = ChangeStatus.NEW_ONLY;
        else if (!newChanged) status = ChangeStatus.CURRENT_ONLY;
        else if (same(currentHash, newHash)) status = ChangeStatus.SAME_CHANGE;
        else status = ChangeStatus.BOTH_CHANGED;
        return new ThreeWayRegionComparison(regionId, baseHash, currentHash, newHash, status);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void validateOptionalHash(String value, String field) {
        if (value != null && !ContentHashes.isValid(value)) throw new IllegalArgumentException(field + "는 SHA-256이거나 null이어야 합니다.");
    }

    public enum ChangeStatus { UNCHANGED, CURRENT_ONLY, NEW_ONLY, SAME_CHANGE, BOTH_CHANGED }
}
