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
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull List<FigmaExportIssue> issues,
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull List<QualityGateResult> qualityGates,
        @jakarta.validation.Valid RefinementSummary refinement
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
        qualityGates = qualityGates == null ? List.of() : List.copyOf(qualityGates);
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

    /** v2 품질 Gate 도입 전 Java 호출자 호환. 외부 성공 보고서는 Service에서 빈 Gate로 거부된다. */
    public FigmaGenerationReport(
            String reportId, Status status, FigmaScreenSpec figmaScreenSpec,
            String screenId, int screenVersion, FigmaSyncMode mode,
            Instant startedAt, Instant completedAt, boolean success,
            int reusedInstanceCount, int createdInstanceCount, int archivedNodeCount, int fallbackCount,
            List<Change> changes, List<FigmaExportIssue> issues
    ) {
        this(reportId, status, figmaScreenSpec, screenId, screenVersion, mode,
                startedAt, completedAt, success, reusedInstanceCount, createdInstanceCount,
                archivedNodeCount, fallbackCount, changes, issues, List.of(), null);
    }

    /** MR-Q05 Refinement 필드 도입 전 Java 호출자 호환. */
    public FigmaGenerationReport(
            String reportId, Status status, FigmaScreenSpec figmaScreenSpec,
            String screenId, int screenVersion, FigmaSyncMode mode,
            Instant startedAt, Instant completedAt, boolean success,
            int reusedInstanceCount, int createdInstanceCount, int archivedNodeCount, int fallbackCount,
            List<Change> changes, List<FigmaExportIssue> issues, List<QualityGateResult> qualityGates
    ) {
        this(reportId, status, figmaScreenSpec, screenId, screenVersion, mode,
                startedAt, completedAt, success, reusedInstanceCount, createdInstanceCount,
                archivedNodeCount, fallbackCount, changes, issues, qualityGates, null);
    }

    /** MR-Q05: 이번 적용에 사용된 Manual Refinement Patch Set 증적. */
    public record RefinementSummary(
            @jakarta.validation.constraints.NotBlank String patchSetId,
            @jakarta.validation.constraints.Positive int patchSetVersion,
            @jakarta.validation.constraints.PositiveOrZero int appliedCount,
            @jakarta.validation.constraints.PositiveOrZero int excludedCount,
            @jakarta.validation.constraints.PositiveOrZero int conflictCount,
            @jakarta.validation.constraints.PositiveOrZero int blockedCount
    ) {}

    public record QualityGateResult(
            @jakarta.validation.constraints.NotNull Gate gate,
            @jakarta.validation.constraints.NotNull GateStatus status,
            @jakarta.validation.constraints.NotNull List<String> issueCodes,
            String evidenceHash,
            String baselineHash,
            Double diffRatio,
            Double threshold,
            String sectionEvidenceJson,
            List<String> changedSections
    ) {
        public QualityGateResult {
            issueCodes = issueCodes == null ? List.of() : List.copyOf(issueCodes);
            changedSections = changedSections == null ? List.of() : List.copyOf(changedSections);
        }

        public QualityGateResult(Gate gate, GateStatus status, List<String> issueCodes,
                String evidenceHash, String baselineHash, Double diffRatio, Double threshold) {
            this(gate, status, issueCodes, evidenceHash, baselineHash, diffRatio, threshold, null, List.of());
        }
    }

    public enum Gate { LAYOUT, ACCESSIBILITY, VISUAL_REGRESSION }
    public enum GateStatus { PASSED, FAILED, BASELINE_CREATED }

    public record Change(
            String logicalNodeId,
            String logicalType,
            ChangeType changeType,
            String detail
    ) {
        public enum ChangeType { ADD, REUSE, MOVE, UPDATE, ARCHIVE, CONFLICT }
    }
}
