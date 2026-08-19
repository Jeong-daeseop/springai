package com.krdevops.springai.model.capture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R7(04번 문서 §10): 사전 등록된 6종만 허용하는 닫힌 interaction step 검증. */
class InteractionStepTest {

    @Test
    void acceptsClickWithSelector() {
        var step = new InteractionStep("click", "#reveal", null);
        assertThat(step.type()).isEqualTo("click");
    }

    @Test
    void acceptsScrollWithoutSelectorOrValue() {
        var step = new InteractionStep("scroll", null, null);
        assertThat(step.selector()).isNull();
    }

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> new InteractionStep("eval", "#x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("click/fill/select/scroll/hover/keydown");
    }

    @Test
    void rejectsClickWithoutSelector() {
        assertThatThrownBy(() -> new InteractionStep("click", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selector");
    }

    @Test
    void rejectsFillWithoutValue() {
        assertThatThrownBy(() -> new InteractionStep("fill", "#name", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void rejectsKeydownWithoutValue() {
        assertThatThrownBy(() -> new InteractionStep("keydown", "#submit", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void acceptsKeydownWithoutSelector() {
        var step = new InteractionStep("keydown", null, "Enter");
        assertThat(step.value()).isEqualTo("Enter");
    }
}
