package com.krdevops.springai.model.thymeleaf;

import java.util.EnumSet;
import java.util.Set;

/** R6-050 단계 상태와 허용 전이. 알 수 없는 상태는 enum 역직렬화 단계에서 거부된다. */
public enum ThymeleafGenerationStageStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    REVIEW_REQUIRED,
    FAILED,
    SKIPPED;

    public boolean terminal() {
        return this == SUCCEEDED || this == REVIEW_REQUIRED || this == FAILED || this == SKIPPED;
    }

    public boolean canTransitionTo(ThymeleafGenerationStageStatus next) {
        if (next == null) {
            return false;
        }
        Set<ThymeleafGenerationStageStatus> allowed = switch (this) {
            case PENDING -> EnumSet.of(RUNNING, SKIPPED);
            case RUNNING -> EnumSet.of(SUCCEEDED, REVIEW_REQUIRED, FAILED);
            case REVIEW_REQUIRED, FAILED -> EnumSet.of(RUNNING);
            case SUCCEEDED, SKIPPED -> EnumSet.noneOf(ThymeleafGenerationStageStatus.class);
        };
        return allowed.contains(next);
    }

    public void requireTransitionTo(ThymeleafGenerationStageStatus next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않은 Thymeleaf 생성 단계 전이입니다: " + this + " -> " + next);
        }
    }
}
