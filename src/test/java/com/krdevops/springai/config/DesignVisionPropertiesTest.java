package com.krdevops.springai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignVisionPropertiesTest {

    @Test
    void disabledDefaultConfigurationIsValid() {
        DesignVisionProperties properties = new DesignVisionProperties();

        assertThatCode(properties::validateFigmaConfiguration).doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRequiresToken() {
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.getFigma().setEnabled(true);

        assertThatThrownBy(properties::validateFigmaConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_ACCESS_TOKEN")
                .hasMessageNotContaining("figd_");
    }

    @Test
    void rejectsValuesOutsideOperationalBounds() {
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.getFigma().setDepthLimit(11);

        assertThatThrownBy(properties::validateFigmaConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("depth-limit");
    }
}
