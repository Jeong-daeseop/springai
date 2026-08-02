package com.krdevops.springai.model.thymeleaf;

/**
 * I-5A: Viewport 제약 조건.
 * Desktop/Tablet/Mobile 3개 Viewport의 CSS 및 레이아웃 규칙을 정의한다.
 */
public record ViewportConstraint(
        ViewportType viewport,
        int width,
        int gridColumns,
        NavigationType navigation,
        String breakpointRule
) {
    public enum ViewportType {
        DESKTOP(1440, 12, "no breakpoint"),
        TABLET(768, 8, "@media (max-width: 1023px)"),
        MOBILE(390, 4, "@media (max-width: 767px)");

        public final int defaultWidth;
        public final int gridColumns;
        public final String mediaQuery;

        ViewportType(int defaultWidth, int gridColumns, String mediaQuery) {
            this.defaultWidth = defaultWidth;
            this.gridColumns = gridColumns;
            this.mediaQuery = mediaQuery;
        }
    }

    public enum NavigationType {
        SIDE_NAV("side-navigation"),
        DRAWER("drawer-navigation"),
        BOTTOM_NAV("bottom-navigation");

        public final String cssClass;

        NavigationType(String cssClass) {
            this.cssClass = cssClass;
        }
    }

    public static ViewportConstraint desktop() {
        return new ViewportConstraint(
            ViewportType.DESKTOP,
            1440,
            12,
            NavigationType.SIDE_NAV,
            ""
        );
    }

    public static ViewportConstraint tablet() {
        return new ViewportConstraint(
            ViewportType.TABLET,
            768,
            8,
            NavigationType.DRAWER,
            "@media (max-width: 1023px)"
        );
    }

    public static ViewportConstraint mobile() {
        return new ViewportConstraint(
            ViewportType.MOBILE,
            390,
            4,
            NavigationType.BOTTOM_NAV,
            "@media (max-width: 767px)"
        );
    }
}
