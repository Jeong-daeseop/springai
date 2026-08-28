package com.krdevops.springai.service;

import com.krdevops.springai.model.capture.BreakpointObservation;
import com.krdevops.springai.model.capture.CaptureWarning;
import com.krdevops.springai.model.capture.ComponentMatch;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
import com.krdevops.springai.model.capture.ResponsiveRegressionReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsiveRegressionAnalyzerTest {

    @Test
    void allMatchedAllWithNoObservationsYieldsZeroCounts() {
        RenderedDesignBundle bundle = new RenderedDesignBundle(
                RenderedDesignBundle.SCHEMA_VERSION, "bundle-1",
                Map.of("desktop", "artifact-1", "tablet", "artifact-2", "mobile", "artifact-3"),
                List.of(new ComponentMatch("table.list", Map.of("desktop", "n1"), ComponentMatch.Status.MATCHED_ALL)),
                List.of(), List.of());

        ResponsiveRegressionReport report = ResponsiveRegressionAnalyzer.analyze(bundle);

        assertThat(report.bundleId()).isEqualTo("bundle-1");
        assertThat(report.matchedAllCount()).isEqualTo(1);
        assertThat(report.hiddenInSomeCount()).isZero();
        assertThat(report.movedCount()).isZero();
        assertThat(report.suspiciousMoves()).isEmpty();
        assertThat(report.captureWarnings()).isEmpty();
    }

    @Test
    void movedObservationsAreCollectedAsSuspicious() {
        BreakpointObservation moved = new BreakpointObservation(
                "div.search-panel", "desktop", "tablet", BreakpointObservation.Change.MOVED);
        BreakpointObservation hidden = new BreakpointObservation(
                "button.extra", "tablet", "mobile", BreakpointObservation.Change.HIDDEN);
        BreakpointObservation shown = new BreakpointObservation(
                "button.hamburger", "desktop", "mobile", BreakpointObservation.Change.SHOWN);
        RenderedDesignBundle bundle = new RenderedDesignBundle(
                RenderedDesignBundle.SCHEMA_VERSION, "bundle-2",
                Map.of("desktop", "artifact-1"),
                List.of(new ComponentMatch("div.search-panel", Map.of("desktop", "n1"), ComponentMatch.Status.MOVED)),
                List.of(moved, hidden, shown), List.of());

        ResponsiveRegressionReport report = ResponsiveRegressionAnalyzer.analyze(bundle);

        assertThat(report.movedCount()).isEqualTo(1);
        assertThat(report.hiddenInSomeCount()).isEqualTo(1);
        assertThat(report.suspiciousMoves()).containsExactly(moved);
        assertThat(report.matchedAllCount()).isZero();
    }

    @Test
    void captureWarningsArePassedThrough() {
        CaptureWarning warning = new CaptureWarning("VIEWPORT_CAPTURE_FAILED", null, "mobile viewport 캡처 실패");
        RenderedDesignBundle bundle = new RenderedDesignBundle(
                RenderedDesignBundle.SCHEMA_VERSION, "bundle-3",
                Map.of("desktop", "artifact-1"), List.of(), List.of(), List.of(warning));

        ResponsiveRegressionReport report = ResponsiveRegressionAnalyzer.analyze(bundle);

        assertThat(report.captureWarnings()).containsExactly(warning);
    }
}
