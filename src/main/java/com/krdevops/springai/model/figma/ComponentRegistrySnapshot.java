package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.designsystem.ComponentRegistry;

import java.time.LocalDateTime;

/** Export 시점의 ComponentRegistry를 고정 복사한 값. FigmaExportBundle 안에 담겨 파일로 전달된다. */
public record ComponentRegistrySnapshot(
        ComponentRegistry registry,
        LocalDateTime snapshotAt
) {
    public ComponentRegistrySnapshot {
        if (registry == null) {
            throw new IllegalArgumentException("registry는 필수입니다.");
        }
        snapshotAt = snapshotAt == null ? LocalDateTime.now() : snapshotAt;
    }
}
