package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaUiDesignSpecQualityEvaluatorTest {
    private final FigmaUiDesignSpecQualityEvaluator evaluator = new FigmaUiDesignSpecQualityEvaluator();

    @Test
    void reportsLowQualityForEmptyFigpackProjection() {
        var result = evaluator.evaluate(UiDesignSpec.empty("LIST"));
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).contains("COMPONENTS_EMPTY", "FIELD_HINTS_EMPTY");
    }

    @Test
    void evaluatesStableSemanticProjection() {
        var spec = new UiDesignSpec("LIST", new UiDesignSpec.LayoutSpec(
                "standard", "standard", "standard", "single-column", "top-right", "none"),
                List.of(new UiDesignSpec.ComponentSpec("TABLE", List.of("users"))),
                List.of(new UiDesignSpec.ActionSpec("SEARCH", "SECONDARY")),
                List.of(new UiDesignSpec.FieldHint("name", "이름", com.krdevops.springai.model.design.UiFieldRole.TITLE, "TEXT", .9)),
                Map.of("color.primary", "#0b5fff"), List.of(), List.of("추가 근거"));
        var result = evaluator.evaluate(spec);
        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
    }
}
