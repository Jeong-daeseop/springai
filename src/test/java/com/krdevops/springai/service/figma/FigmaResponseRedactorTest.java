package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaResponseRedactorTest {

    private final FigmaResponseRedactor redactor = new FigmaResponseRedactor();

    @Test
    void redactsFigmaAccessToken() {
        String masked = redactor.redact("figma_token: figd_abc123XYZ_secret 호출 실패");

        assertThat(masked).doesNotContain("figd_abc123XYZ_secret");
        assertThat(masked).contains("***REDACTED***");
    }

    @Test
    void redactsApiKeyAndSecret() {
        String masked = redactor.redact("api_key=sk-verySensitiveOpenAiKey123");

        assertThat(masked).doesNotContain("sk-verySensitiveOpenAiKey123");
    }

    @Test
    void redactsQueryParameterToken() {
        String masked = redactor.redact(
                "이미지 URL 조회 실패: https://figma-alpha-api.s3.amazonaws.com/img.png?token=abcDEF123&size=large");

        assertThat(masked).doesNotContain("abcDEF123");
        assertThat(masked).contains("token=***REDACTED***");
        assertThat(masked).contains("size=large");
    }

    @Test
    void redactsEmailAddress() {
        String masked = redactor.redact("연락처: designer@example.com");

        assertThat(masked).doesNotContain("designer@example.com");
        assertThat(masked).contains("***@***");
    }

    @Test
    void nullAndBlankPassThroughUnchanged() {
        assertThat(redactor.redact(null)).isNull();
        assertThat(redactor.redact("")).isEmpty();
    }

    @Test
    void leavesUnrelatedTextUnchanged() {
        String message = "요청한 논리 노드를 찾을 수 없습니다";

        assertThat(redactor.redact(message)).isEqualTo(message);
    }

    @Test
    void isSensitiveFieldRecognizesKnownSensitiveNames() {
        assertThat(redactor.isSensitiveField("accessToken")).isTrue();
        assertThat(redactor.isSensitiveField("apiKey")).isTrue();
        assertThat(redactor.isSensitiveField("password")).isTrue();
        assertThat(redactor.isSensitiveField("screenId")).isFalse();
    }

    @Test
    void redactFieldMasksOnlySensitiveFieldNames() {
        assertThat(redactor.redactField("secret-value", "apiKey")).isEqualTo("***REDACTED***");
        assertThat(redactor.redactField("qna-detail", "screenId")).isEqualTo("qna-detail");
    }
}
