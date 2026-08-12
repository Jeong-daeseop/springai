package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;

import java.time.LocalDateTime;

/** Bundle 생성 시점에 고정한 화면 Pattern 계약. */
public record ScreenPatternSnapshot(ScreenPatternDefinition pattern, LocalDateTime snapshotAt) {
    public ScreenPatternSnapshot {
        if (pattern == null) throw new IllegalArgumentException("pattern Snapshot은 필수입니다.");
        snapshotAt = snapshotAt == null ? LocalDateTime.now() : snapshotAt;
    }
}
