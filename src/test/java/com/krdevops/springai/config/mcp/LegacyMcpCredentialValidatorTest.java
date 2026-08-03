package com.krdevops.springai.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMcpCredentialValidatorTest {
    private final LegacyMcpCredentialValidator validator =
            new LegacyMcpCredentialValidator(new ObjectMapper(), List.of("legacy-secret"),
                    Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC),
                    Instant.parse("2026-08-03T00:01:00Z"));

    @Test
    void acceptsOnlyKnownLegacyFieldsWithConfiguredSecret() {
        assertThat(validator.isValid("{\"figmaMcpSecret\":\"legacy-secret\"}")).isTrue();
        assertThat(validator.isValid("{\"sharedSecret\":\"legacy-secret\"}")).isTrue();
        assertThat(validator.isValid("{\"other\":\"legacy-secret\"}")).isFalse();
        assertThat(validator.isValid("{\"figmaMcpSecret\":\"wrong\"}")).isFalse();
        assertThat(validator.isValid("not-json")).isFalse();
    }

    @Test
    void rejectsLegacySecretWhenCompatibilityWindowExpired() {
        LegacyMcpCredentialValidator expired = new LegacyMcpCredentialValidator(
                new ObjectMapper(), List.of("legacy-secret"),
                Clock.fixed(Instant.parse("2026-08-03T00:01:00Z"), ZoneOffset.UTC),
                Instant.parse("2026-08-03T00:01:00Z"));

        assertThat(expired.isValid("{\"sharedSecret\":\"legacy-secret\"}")).isFalse();
    }
}
