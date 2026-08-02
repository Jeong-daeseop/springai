package com.krdevops.springai.model.contract;

/**
 * I-0: 디자인 시스템 스냅샷 참조.
 * 프로필 ID, 버전, 릴리스 버전, 추가 메타데이터를 포함.
 */
public record DesignSystemSnapshotRef(
        String profileId,
        String profileVersion,
        String releaseVersion,
        String metadata
) {
    public DesignSystemSnapshotRef {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId는 필수입니다");
        }
        if (profileVersion == null || profileVersion.isBlank()) {
            throw new IllegalArgumentException("profileVersion은 필수입니다");
        }
        if (releaseVersion == null || releaseVersion.isBlank()) {
            throw new IllegalArgumentException("releaseVersion은 필수입니다");
        }
    }
}
