package com.krdevops.springai.model.design;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionPlacementTest {

    @Test
    void blankOrNullDefaultsToTopRight() {
        assertThat(ActionPlacement.from(null)).isEqualTo(ActionPlacement.TOP_RIGHT);
        assertThat(ActionPlacement.from("")).isEqualTo(ActionPlacement.TOP_RIGHT);
        assertThat(ActionPlacement.from("  ")).isEqualTo(ActionPlacement.TOP_RIGHT);
    }

    @Test
    void topRightParsesCaseInsensitiveWithHyphen() {
        assertThat(ActionPlacement.from("top-right")).isEqualTo(ActionPlacement.TOP_RIGHT);
        assertThat(ActionPlacement.from("Top-Right")).isEqualTo(ActionPlacement.TOP_RIGHT);
    }

    @Test
    void bottomRightParsesCaseInsensitiveWithHyphen() {
        assertThat(ActionPlacement.from("bottom-right")).isEqualTo(ActionPlacement.BOTTOM_RIGHT);
        assertThat(ActionPlacement.from("BOTTOM-RIGHT")).isEqualTo(ActionPlacement.BOTTOM_RIGHT);
    }

    @Test
    void unsupportedValueThrows() {
        assertThatThrownBy(() -> ActionPlacement.from("bottom-center"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bottom-center");
    }
}
