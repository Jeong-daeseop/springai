package com.krdevops.springai.service.figma;

import com.krdevops.springai.mapper.FigmaGenerationReportRepository;
import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.ops.DesignSystemImpact;
import com.krdevops.springai.model.figma.ops.FigmaGenerationReport;
import com.krdevops.springai.model.figma.ops.FigmaOperationalMetrics;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 생성 보고서 수집, 운영 지표 집계, Design System 영향 화면 조회를 담당한다. */
@Service
public class FigmaOperationsService {

    private static final Set<String> REGISTRY_ISSUE_CODES = Set.of(
            "COMPONENT_NOT_IN_REGISTRY", "REQUIRED_COMPONENT_MISSING",
            "REQUIRED_COMPONENT_NOT_CURRENT", "REGISTRY_VERSION_MISMATCH",
            "PROFILE_REGISTRY_VERSION_MISMATCH", "LIBRARY_FILE_KEY_MISMATCH");

    private final FigmaGenerationReportRepository reportRepository;
    private final FigmaScreenSpecRepository screenSpecRepository;
    private final FigmaReviewHistoryRepository reviewRepository;
    private final OperationalTelemetry telemetry;

    public FigmaOperationsService(
            FigmaGenerationReportRepository reportRepository,
            FigmaScreenSpecRepository screenSpecRepository,
            FigmaReviewHistoryRepository reviewRepository,
            OperationalTelemetry telemetry
    ) {
        this.reportRepository = reportRepository;
        this.screenSpecRepository = screenSpecRepository;
        this.reviewRepository = reviewRepository;
        this.telemetry = telemetry;
    }

    public FigmaGenerationReport record(FigmaGenerationReport report) {
        if (report.figmaScreenSpec() != null
                && (!report.screenId().equals(report.figmaScreenSpec().screenId())
                || report.screenVersion() != report.figmaScreenSpec().screenVersion())) {
            throw new IllegalArgumentException(
                    "보고서 화면 버전과 포함된 FigmaScreenSpec이 일치하지 않습니다.");
        }
        validateQualityGates(report);
        recordRefinementTelemetry(report);
        return reportRepository.saveImmutable(report);
    }

    /** MR-Q06: Refinement 적용률·충돌률·차단률과 Refinement 포함 Apply의 Rollback률을 기록한다. */
    private void recordRefinementTelemetry(FigmaGenerationReport report) {
        if (report.refinement() != null) {
            var refinement = report.refinement();
            telemetry.figmaRefinementApplyOutcome("APPLIED", refinement.appliedCount());
            telemetry.figmaRefinementApplyOutcome("EXCLUDED", refinement.excludedCount());
            telemetry.figmaRefinementApplyOutcome("CONFLICT", refinement.conflictCount());
            telemetry.figmaRefinementApplyOutcome("BLOCKED", refinement.blockedCount());
            if (!report.success()) {
                telemetry.figmaRefinementRollback();
            }
        }
    }

    private void validateQualityGates(FigmaGenerationReport report) {
        if (!report.success()) return; // 실패 보고서는 원인 증적 보존을 위해 그대로 저장한다.
        java.util.Map<FigmaGenerationReport.Gate, FigmaGenerationReport.QualityGateResult> gates =
                new java.util.EnumMap<>(FigmaGenerationReport.Gate.class);
        for (var gate : report.qualityGates()) {
            if (gates.put(gate.gate(), gate) != null) {
                throw new IllegalArgumentException("중복 품질 Gate입니다: " + gate.gate());
            }
        }
        for (var required : FigmaGenerationReport.Gate.values()) {
            var result = gates.get(required);
            if (result == null) throw new IllegalArgumentException("필수 품질 Gate가 없습니다: " + required);
            if (result.status() == FigmaGenerationReport.GateStatus.FAILED) {
                throw new IllegalArgumentException("품질 Gate 실패 보고서는 success일 수 없습니다: " + required);
            }
        }
        var visual = gates.get(FigmaGenerationReport.Gate.VISUAL_REGRESSION);
        if (visual.evidenceHash() == null || visual.evidenceHash().isBlank()) {
            throw new IllegalArgumentException("Visual Regression evidenceHash가 없습니다.");
        }
        if (visual.status() == FigmaGenerationReport.GateStatus.PASSED
                && (visual.baselineHash() == null || visual.baselineHash().isBlank())) {
            throw new IllegalArgumentException("Visual Regression 기준선 Hash가 없습니다.");
        }
        if (visual.diffRatio() == null || visual.threshold() == null
                || visual.diffRatio() > visual.threshold()) {
            throw new IllegalArgumentException("Visual Regression 임계값을 통과하지 못했습니다.");
        }
        if (visual.sectionEvidenceJson() == null || visual.sectionEvidenceJson().isBlank()) {
            throw new IllegalArgumentException("Visual Regression Section Diff Artifact가 없습니다.");
        }
    }

    public List<FigmaGenerationReport> reports(String screenId) {
        return reportRepository.findByScreen(screenId);
    }

    public FigmaOperationalMetrics metrics() {
        List<FigmaGenerationReport> reports = reportRepository.findAll();
        long total = reports.size();
        long success = reports.stream().filter(FigmaGenerationReport::success).count();
        long affected = reports.stream().mapToLong(FigmaGenerationReport::affectedNodeCount).sum();
        long reused = reports.stream().mapToLong(FigmaGenerationReport::reusedInstanceCount).sum();
        long created = reports.stream().mapToLong(FigmaGenerationReport::createdInstanceCount).sum();
        long archived = reports.stream().mapToLong(FigmaGenerationReport::archivedNodeCount).sum();
        long fallback = reports.stream().mapToLong(FigmaGenerationReport::fallbackCount).sum();
        long registryIssues = issueCount(reports, code -> REGISTRY_ISSUE_CODES.contains(code));
        long conflicts = reports.stream()
                .flatMap(report -> report.changes().stream())
                .filter(change -> change.changeType()
                        == FigmaGenerationReport.Change.ChangeType.CONFLICT)
                .count();
        long overrideFailures = issueCount(
                reports, code -> code.contains("USER_OVERRIDE") && code.contains("FAIL"));
        long reviewCount = reviewRepository.countByEventType(FigmaReviewEvent.EventType.REVIEW);
        long rejectionCount = reviewRepository.countByEventType(FigmaReviewEvent.EventType.REJECTION);
        return new FigmaOperationalMetrics(
                total, success, ratio(success, total),
                reports.stream().mapToLong(FigmaGenerationReport::durationMillis).average().orElse(0),
                affected, reused, created, ratio(reused, reused + created),
                archived, fallback, ratio(fallback, reused + created),
                registryIssues, conflicts, overrideFailures,
                rejectionCount, reviewCount, Instant.now());
    }

    public DesignSystemImpact impact(
            String profileId,
            String profileVersion,
            String registryVersion
    ) {
        List<FigmaScreenSpec> specs = screenSpecRepository.findLatestByDesignSystem(
                profileId, profileVersion, registryVersion);
        List<DesignSystemImpact.AffectedScreen> screens = specs.stream()
                .map(spec -> new DesignSystemImpact.AffectedScreen(
                        spec.screenId(), spec.screenVersion(),
                        spec.screenSpecificationId(), spec.screenSpecificationVersion(),
                        spec.screenType().name(), spec.name()))
                .toList();
        return new DesignSystemImpact(
                profileId, profileVersion, registryVersion, screens.size(), screens);
    }

    private long issueCount(
            List<FigmaGenerationReport> reports,
            java.util.function.Predicate<String> predicate
    ) {
        return reports.stream().flatMap(report -> report.issues().stream())
                .map(FigmaExportIssue::code)
                .filter(code -> code != null)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .filter(predicate)
                .count();
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
