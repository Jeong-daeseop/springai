package com.krdevops.springai.model.design;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPanelPlacementTest {

    @Test
    void blankOrNullDefaultsToAboveTable() {
        assertThat(SearchPanelPlacement.from(null)).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
        assertThat(SearchPanelPlacement.from("")).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
        assertThat(SearchPanelPlacement.from("  ")).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
    }

    @Test
    void aboveTableParsesCaseInsensitiveWithHyphen() {
        assertThat(SearchPanelPlacement.from("above-table")).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
        assertThat(SearchPanelPlacement.from("Above-Table")).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
    }

    @Test
    void noneParsesCaseInsensitive() {
        assertThat(SearchPanelPlacement.from("none")).isEqualTo(SearchPanelPlacement.NONE);
        assertThat(SearchPanelPlacement.from("NONE")).isEqualTo(SearchPanelPlacement.NONE);
    }

    @Test
    void unsupportedValueThrows() {
        assertThatThrownBy(() -> SearchPanelPlacement.from("beside-table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("beside-table");
    }
}
