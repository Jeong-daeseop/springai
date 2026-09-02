package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaPaintCssConverterTest {
    @Test
    void convertsOrderedLinearGradientStopsAndHandleAngle() {
        var paint = new UiDesignSpec.PaintSpec("GRADIENT_LINEAR", true, 0.5, null,
                List.of(new UiDesignSpec.PaintSpec.GradientStop(1, "rgba(0,0,255,1.00)"),
                        new UiDesignSpec.PaintSpec.GradientStop(0, "rgba(255,0,0,1.00)")),
                List.of(new UiDesignSpec.PaintSpec.GradientHandlePosition(0, 0.5),
                        new UiDesignSpec.PaintSpec.GradientHandlePosition(1, 0.5)));

        assertThat(FigmaPaintCssConverter.toCss(paint))
                .isEqualTo("linear-gradient(90.0deg, rgba(255,0,0,1.00) 0.00%, rgba(0,0,255,1.00) 100.00%)");
    }

    @Test
    void returnsNullWhenGradientHasNoUsableColors() {
        var paint = new UiDesignSpec.PaintSpec("GRADIENT_LINEAR", true, 1, null,
                List.of(new UiDesignSpec.PaintSpec.GradientStop(0.5, null)), List.of());
        assertThat(FigmaPaintCssConverter.toCss(paint)).isNull();
    }
}
