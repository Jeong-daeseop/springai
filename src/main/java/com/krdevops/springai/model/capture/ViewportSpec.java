package com.krdevops.springai.model.capture;

public record ViewportSpec(String name, int width, int height, double deviceScaleFactor) {
    public ViewportSpec {
        name = name == null || name.isBlank() ? "desktop" : name;
        width = width == 0 ? 1440 : width;
        height = height == 0 ? 1200 : height;
        deviceScaleFactor = deviceScaleFactor == 0 ? 1 : deviceScaleFactor;
        if (width < 320 || width > 3840 || height < 320 || height > 10000
                || deviceScaleFactor < 0.5 || deviceScaleFactor > 4) {
            throw new IllegalArgumentException("지원하지 않는 viewport입니다.");
        }
    }

    public static ViewportSpec desktop() { return new ViewportSpec("desktop", 1440, 1200, 1); }

    /** R8(04번 문서 §11): {@code ResponsiveBreakpointPolicy}의 Tablet 기준(768/8열)과 동일한 폭. */
    public static ViewportSpec tablet() { return new ViewportSpec("tablet", 768, 1200, 1); }

    /** R8(04번 문서 §11): {@code ResponsiveBreakpointPolicy}의 Mobile 기준(390/4열)과 동일한 폭. */
    public static ViewportSpec mobile() { return new ViewportSpec("mobile", 390, 1200, 1); }
}
