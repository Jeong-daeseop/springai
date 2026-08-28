package com.krdevops.springai.service;

import com.krdevops.springai.model.capture.BreakpointObservation;
import com.krdevops.springai.model.capture.ComponentMatch;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
import com.krdevops.springai.model.capture.ResponsiveRegressionReport;

import java.util.List;

/**
 * {@link RenderedDesignBundle}이 이미 계산해둔 {@code breakpointObservations}/{@code componentMatches}를
 * 재순회하지 않고 재분류만 해 {@link ResponsiveRegressionReport}를 만든다 — 신규 캡처·매칭 로직 없음
 * (Figma_픽셀재현_2차구현_반응형검증_구현계획.md §2/§6.1).
 */
public final class ResponsiveRegressionAnalyzer {

    private ResponsiveRegressionAnalyzer() {
    }

    public static ResponsiveRegressionReport analyze(RenderedDesignBundle bundle) {
        List<BreakpointObservation> moved = bundle.breakpointObservations().stream()
                .filter(observation -> observation.change() == BreakpointObservation.Change.MOVED)
                .toList();
        long hiddenCount = bundle.breakpointObservations().stream()
                .filter(observation -> observation.change() == BreakpointObservation.Change.HIDDEN)
                .count();
        long matchedAllCount = bundle.componentMatches().stream()
                .filter(match -> match.status() == ComponentMatch.Status.MATCHED_ALL)
                .count();

        return new ResponsiveRegressionReport(
                bundle.bundleId(), (int) matchedAllCount, (int) hiddenCount, moved.size(),
                moved, bundle.warnings());
    }
}
