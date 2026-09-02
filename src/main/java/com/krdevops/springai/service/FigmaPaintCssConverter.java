package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;

import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

/** Figma gradient 메타데이터를 CSS gradient 선언으로 변환하는 근사 변환기. */
public final class FigmaPaintCssConverter {

    private FigmaPaintCssConverter() {}

    public static String toCss(UiDesignSpec.PaintSpec paint) {
        if (paint == null || paint.gradientStops().isEmpty()) return null;
        String function = switch (paint.type()) {
            case "GRADIENT_RADIAL" -> "radial-gradient";
            case "GRADIENT_ANGULAR" -> "conic-gradient";
            default -> "linear-gradient";
        };
        String prefix = "linear-gradient".equals(function) ? angle(paint) + "deg, " : "";
        String stops = paint.gradientStops().stream()
                .sorted(Comparator.comparingDouble(UiDesignSpec.PaintSpec.GradientStop::position))
                .map(stop -> stop.color() == null ? null
                        : stop.color() + " " + formatPercent(stop.position()))
                .filter(value -> value != null)
                .collect(Collectors.joining(", "));
        return stops.isBlank() ? null : function + "(" + prefix + stops + ")";
    }

    private static double angle(UiDesignSpec.PaintSpec paint) {
        if (paint.gradientHandlePositions().size() < 2) return 90.0;
        var start = paint.gradientHandlePositions().get(0);
        var end = paint.gradientHandlePositions().get(1);
        double degrees = Math.toDegrees(Math.atan2(end.y() - start.y(), end.x() - start.x())) + 90.0;
        return (degrees + 360.0) % 360.0;
    }

    private static String formatPercent(double position) {
        return String.format(Locale.ROOT, "%.2f%%", Math.max(0, Math.min(1, position)) * 100);
    }
}
