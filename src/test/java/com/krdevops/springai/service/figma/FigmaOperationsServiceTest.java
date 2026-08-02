package com.krdevops.springai.service.figma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.krdevops.springai.mapper.FigmaGenerationReportRepository;
import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportIssue.Severity;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.model.figma.ops.DesignSystemImpact;
import com.krdevops.springai.model.figma.ops.FigmaGenerationReport;
import com.krdevops.springai.model.figma.ops.FigmaOperationalMetrics;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FigmaOperationsServiceTest {

    @Mock
    private FigmaGenerationReportRepository reportRepository;
    @Mock
    private FigmaScreenSpecRepository screenSpecRepository;
    @Mock
    private FigmaReviewHistoryRepository reviewRepository;

    private FigmaOperationsService service;

    @BeforeEach
    void setUp() {
        service = new FigmaOperationsService(
                reportRepository, screenSpecRepository, reviewRepository);
    }

    @Test
    void Plugin보고서에서성공률재사용률Fallback과충돌지표를집계한다() {
        when(reportRepository.findAll()).thenReturn(List.of(
                report("r1", true, 80, 20, 0, List.of()),
                report("r2", false, 10, 10, 5, List.of(
                        new FigmaExportIssue(
                                "REGISTRY_VERSION_MISMATCH", Severity.ERROR,
                                "Registry 불일치", null, null, null),
                        new FigmaExportIssue(
                                "USER_OVERRIDE_PRESERVATION_FAILED", Severity.ERROR,
                                "사용자 수정 보존 실패", "node", null, null)))));
        when(reviewRepository.countByEventType(FigmaReviewEvent.EventType.REVIEW)).thenReturn(7L);
        when(reviewRepository.countByEventType(FigmaReviewEvent.EventType.REJECTION)).thenReturn(2L);

        FigmaOperationalMetrics metrics = service.metrics();

        assertThat(metrics.totalRuns()).isEqualTo(2);
        assertThat(metrics.successRate()).isEqualTo(0.5);
        assertThat(metrics.instanceReuseRate()).isEqualTo(0.75);
        assertThat(metrics.fallbackCount()).isEqualTo(5);
        assertThat(metrics.registryMismatchCount()).isEqualTo(1);
        assertThat(metrics.mergeConflictCount()).isEqualTo(2);
        assertThat(metrics.userOverridePreservationFailureCount()).isEqualTo(1);
        assertThat(metrics.previewReviewCount()).isEqualTo(7);
        assertThat(metrics.previewRejectionCount()).isEqualTo(2);
    }

    @Test
    void DesignSystem버전별최신영향화면을반환한다() {
        FigmaScreenSpec spec = new FigmaScreenSpec(
                "users-list", 3, "users", 2,
                FigmaScreenType.LIST, LayoutPattern.STANDARD, "사용자 목록",
                null, "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("krds", "1.0", "registry-2"),
                null, List.of());
        when(screenSpecRepository.findLatestByDesignSystem(
                "krds", "1.0", "registry-2")).thenReturn(List.of(spec));

        DesignSystemImpact impact = service.impact("krds", "1.0", "registry-2");

        assertThat(impact.affectedScreenCount()).isEqualTo(1);
        assertThat(impact.screens().get(0).screenId()).isEqualTo("users-list");
        assertThat(impact.screens().get(0).screenType()).isEqualTo("LIST");
    }

    private FigmaGenerationReport report(
            String id,
            boolean success,
            int reused,
            int created,
            int fallback,
            List<FigmaExportIssue> issues
    ) {
        Instant started = Instant.parse("2026-07-27T00:00:00Z");
        return new FigmaGenerationReport(
                id,
                success ? FigmaGenerationReport.Status.SUCCESS
                        : FigmaGenerationReport.Status.FAILED,
                null, "users-list", 1, FigmaSyncMode.MERGE,
                started, started.plusMillis(success ? 100 : 300), success,
                reused, created, 0, fallback,
                List.of(
                        new FigmaGenerationReport.Change(
                                "a", "krds.button",
                                FigmaGenerationReport.Change.ChangeType.CONFLICT, "충돌"),
                        new FigmaGenerationReport.Change(
                                "b", "krds.button",
                                FigmaGenerationReport.Change.ChangeType.REUSE, "재사용")),
                issues);
    }
}
