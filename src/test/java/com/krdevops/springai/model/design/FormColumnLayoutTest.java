package com.krdevops.springai.model.design;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormColumnLayoutTest {

    @Test
    void blankOrNullDefaultsToSingleColumn() {
        assertThat(FormColumnLayout.from(null)).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
        assertThat(FormColumnLayout.from("")).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
        assertThat(FormColumnLayout.from("  ")).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
    }

    @Test
    void singleColumnParsesCaseInsensitiveWithHyphen() {
        assertThat(FormColumnLayout.from("single-column")).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
        assertThat(FormColumnLayout.from("SINGLE-COLUMN")).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
    }

    @Test
    void twoColumnParsesCaseInsensitiveWithHyphen() {
        assertThat(FormColumnLayout.from("two-column")).isEqualTo(FormColumnLayout.TWO_COLUMN);
        assertThat(FormColumnLayout.from("Two-Column")).isEqualTo(FormColumnLayout.TWO_COLUMN);
    }

    @Test
    void unsupportedValueThrows() {
        assertThatThrownBy(() -> FormColumnLayout.from("three-column"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three-column");
    }
}
