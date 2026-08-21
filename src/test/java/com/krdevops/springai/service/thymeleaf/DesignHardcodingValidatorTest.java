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

    @Test
    void rejectsRawSpacingTypographyRadiusAndShadow() {
        String html = """
                <div style="padding: 12px; gap: 1rem; font-size: 14px; border-radius: 8px; box-shadow: 0 2px 4px #0003">x</div>
                """;

        assertThat(validator.validate(html))
                .hasSize(5)
                .allMatch(issue -> issue.startsWith("DESIGN_TOKEN_HARDCODED"));
    }

    @Test
    void acceptsTokenReferencesAndInheritedValues() {
        String html = """
                <div style="color: var(--krds-color-primary-60); padding: var(--krds-spacing-3); \
                border-radius: var(--krds-radius-md); box-shadow: var(--krds-shadow-sm); color: currentColor">x</div>
                """;

        assertThat(validator.validate(html)).isEmpty();
    }
}
