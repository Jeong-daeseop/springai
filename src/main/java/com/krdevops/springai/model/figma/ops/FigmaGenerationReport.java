package com.krdevops.springai.model.figma.ops;

import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)

/** FigmaScreenSpec Plugin이 화면 동기화 후 서버에 업로드하는 실행 보고서. */
public record FigmaGenerationReport(
        @jakarta.validation.constraints.NotBlank String reportId,
        @jakarta.validation.constraints.NotNull Status status,
        @jakarta.validation.Valid FigmaScreenSpec figmaScreenSpec,
        @jakarta.validation.constraints.NotBlank String screenId,
        @jakarta.validation.constraints.Positive int screenVersion,
        @jakarta.validation.constraints.NotNull FigmaSyncMode mode,
        @jakarta.validation.constraints.NotNull Instant startedAt,
        @jakarta.validation.constraints.NotNull Instant completedAt,
        boolean success,
        @jakarta.validation.constraints.PositiveOrZero int reusedInstanceCount,
        @jakarta.validation.constraints.PositiveOrZero int createdInstanceCount,
        @jakarta.validation.constraints.PositiveOrZero int archivedNodeCount,
        @jakarta.validation.constraints.PositiveOrZero int fallbackCount,
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull List<Change> changes,
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull List<FigmaExportIssue> issues
) {
    public FigmaGenerationReport {
        if (reportId == null || reportId.isBlank()) {
            throw new IllegalArgumentException("reportId는 필수입니다.");
        }
        if (screenId == null || screenId.isBlank() || screenVersion < 1) {
            throw new IllegalArgumentException("screenId와 screenVersion은 필수입니다.");
        }
        if (status == null || mode == null || startedAt == null || completedAt == null) {
            throw new IllegalArgumentException("status/mode/startedAt/completedAt은 필수입니다.");
        }
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt은 startedAt보다 빠를 수 없습니다.");
        }
        reusedInstanceCount = nonNegative(reusedInstanceCount, "reusedInstanceCount");
        createdInstanceCount = nonNegative(createdInstanceCount, "createdInstanceCount");
        archivedNodeCount = nonNegative(archivedNodeCount, "archivedNodeCount");
        fallbackCount = nonNegative(fallbackCount, "fallbackCount");
        changes = changes == null ? List.of() : List.copyOf(changes);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public long durationMillis() {
        return Duration.between(startedAt, completedAt).toMillis();
    }

    public int affectedNodeCount() {
        return reusedInstanceCount + createdInstanceCount + archivedNodeCount;
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + "는 음수일 수 없습니다.");
        }
        return value;
    }

    public enum Status { SUCCESS, PARTIAL, FAILED }

    public record Change(
            String logicalNodeId,
            String logicalType,
            ChangeType changeType,
            String detail
    ) {
        public enum ChangeType { ADD, REUSE, MOVE, UPDATE, ARCHIVE, CONFLICT }
    }
}
