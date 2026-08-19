package com.krdevops.springai.service.thymeleaf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesignHardcodingValidatorTest {
    private final DesignHardcodingValidator validator = new DesignHardcodingValidator();

    @Test
    void rejectsRawColorInInlineStyle() {
        assertThat(validator.validate("<div style=\"color:#0b5fff\">x</div>"))
                .anyMatch(issue -> issue.startsWith("DESIGN_TOKEN_HARDCODED"));
    }

    @Test
    void acceptsClassBasedTokenUsage() {
        assertThat(validator.validate("<div class=\"text-primary\">x</div>"))
                .isEmpty();
    }
}
