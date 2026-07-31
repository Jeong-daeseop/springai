package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.designsystem.DesignSystemProfile;

import java.time.LocalDateTime;

/** Export 시점의 DesignSystemProfile을 고정 복사한 값. FigmaExportBundle 안에 담겨 파일로 전달된다. */
public record DesignSystemProfileSnapshot(
        DesignSystemProfile profile,
        LocalDateTime snapshotAt
) {
    public DesignSystemProfileSnapshot {
        if (profile == null) {
            throw new IllegalArgumentException("profile은 필수입니다.");
        }
        snapshotAt = snapshotAt == null ? LocalDateTime.now() : snapshotAt;
    }
}
