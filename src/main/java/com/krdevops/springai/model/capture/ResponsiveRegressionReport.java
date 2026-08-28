package com.krdevops.springai.model.capture;

import java.util.List;

/**
 * claude 경로로 생성된 화면이 componentGeometry 반응형 가드레일 지시를 실제로 지켰는지
 * 확인하기 위해 {@link RenderedDesignBundle#breakpointObservations()}를 재분류한 요약.
 * MOVED 발생은 가드레일 위반(좌표를 인라인/고정폭으로 옮김) 의심 신호일 뿐이며, 의도된
 * 반응형 재배치일 수도 있으므로 최종 판단은 사람이 해야 한다 —
 * Figma_픽셀재현_2차구현_반응형검증_구현계획.md 참고.
 */
public record ResponsiveRegressionReport(
        String bundleId,
        int matchedAllCount,
        int hiddenInSomeCount,
        int movedCount,
        List<BreakpointObservation> suspiciousMoves,
        List<CaptureWarning> captureWarnings
) {
    public ResponsiveRegressionReport {
        suspiciousMoves = suspiciousMoves == null ? List.of() : List.copyOf(suspiciousMoves);
        captureWarnings = captureWarnings == null ? List.of() : List.copyOf(captureWarnings);
    }
}
