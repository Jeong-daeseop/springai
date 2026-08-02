package com.krdevops.springai.model.thymeleaf;

/**
 * I-5A: 통합계획서가 정의한 3개 Viewport 기준(Desktop 1440/12열, Tablet 768/8열, Mobile 390/4열).
 *
 * <p>이 저장소에는 아직 Playwright 기반 실제 렌더링·overflow 검증 인프라가 없다(I-2D의 Frontend
 * Source Graph 모듈화와 같은 이유로 후속 범위로 남김). 따라서 이 정책은 생성 HTML에
 * {@code data-egov-breakpoint-*} 주석을 부여하는 데만 쓰이며, table→card 전환이나 form column
 * 재배치가 실제로 세 Viewport에서 overflow 없이 동작하는지는 검증하지 않는다 — 주석만 남긴다.
 */
public final class ResponsiveBreakpointPolicy {

    private ResponsiveBreakpointPolicy() {
    }

    public static final int DESKTOP_WIDTH = 1440;
    public static final int DESKTOP_GRID_COLUMNS = 12;
    public static final int TABLET_WIDTH = 768;
    public static final int TABLET_GRID_COLUMNS = 8;
    public static final int MOBILE_WIDTH = 390;
    public static final int MOBILE_GRID_COLUMNS = 4;
}
