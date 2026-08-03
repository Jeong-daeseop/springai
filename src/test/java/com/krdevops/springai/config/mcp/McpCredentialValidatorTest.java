package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** ARCH-0105/0116: 누락·오류·만료·회전 token 검증. */
class McpCredentialValidatorTest {
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void currentAndUnexpiredPreviousTokensAreAccepted() {
        McpSecurityProperties properties = properties();
        properties.setPreviousTokenValidUntil(NOW.plusSeconds(60));
        McpCredentialValidator validator = validator(properties);

        assertThat(validator.validate("current").status())
                .isEqualTo(McpCredentialValidator.Status.VALID_CURRENT);
        assertThat(validator.validate("previous").status())
                .isEqualTo(McpCredentialValidator.Status.VALID_PREVIOUS);
    }

    @Test
    void expiredPreviousTokenIsRejectedWithDistinctStatus() {
        McpSecurityProperties properties = properties();
        properties.setPreviousTokenValidUntil(NOW);

        assertThat(validator(properties).validate("previous").status())
                .isEqualTo(McpCredentialValidator.Status.EXPIRED_PREVIOUS);
    }

    @Test
    void missingInvalidAndUnconfiguredAreDistinct() {
        McpSecurityProperties properties = properties();
        assertThat(validator(properties).validate(null).status()).isEqualTo(McpCredentialValidator.Status.MISSING);
        assertThat(validator(properties).validate("wrong").status()).isEqualTo(McpCredentialValidator.Status.INVALID);
        assertThat(validator(new McpSecurityProperties()).validate("anything").status())
                .isEqualTo(McpCredentialValidator.Status.NOT_CONFIGURED);
    }

    private McpSecurityProperties properties() {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setSharedToken("current");
        properties.setPreviousSharedToken("previous");
        return properties;
    }

    private McpCredentialValidator validator(McpSecurityProperties properties) {
        return new McpCredentialValidator(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
