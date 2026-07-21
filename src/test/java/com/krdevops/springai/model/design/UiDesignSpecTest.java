package com.krdevops.springai.model.design;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiDesignSpecTest {

    @Test
    void legacyLayoutSpecConstructorDefaultsFormColumnLayoutToNull() {
        UiDesignSpec.LayoutSpec layout = new UiDesignSpec.LayoutSpec("shell", "wide", "compact");

        assertThat(layout.formColumnLayout()).isNull();
        assertThat(layout.density()).isEqualTo("compact");
    }

    @Test
    void fourArgLayoutSpecKeepsFormColumnLayout() {
        UiDesignSpec.LayoutSpec layout = new UiDesignSpec.LayoutSpec("shell", "wide", "compact", "two-column");

        assertThat(layout.formColumnLayout()).isEqualTo("two-column");
    }
}
