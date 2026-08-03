package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ARCH-0115/M1-G3: 응답·오류 fixture에 민감정보 원문이 남지 않아야 한다. */
class McpSensitiveDataRedactorTest {
    private final McpSensitiveDataRedactor redactor =
            new McpSensitiveDataRedactor(List.of("configured-secret", "sk-test-123"));

    @Test
    void redactsConfiguredValuesAndNamedSensitiveFields() {
        String source = "token=configured-secret {\"fileKey\":\"AbCdEf\",\"apiKey\":\"sk-test-123\"}";

        String redacted = redactor.redact(source);

        assertThat(redacted).doesNotContain("configured-secret", "sk-test-123", "AbCdEf");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    void preservesOrdinaryContent() {
        assertThat(redactor.redact("operationId=op-123 status=SUCCESS"))
                .isEqualTo("operationId=op-123 status=SUCCESS");
    }
}
