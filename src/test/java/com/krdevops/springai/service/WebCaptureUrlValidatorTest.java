package com.krdevops.springai.service;

import com.krdevops.springai.config.WebCaptureProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebCaptureUrlValidatorTest {

    private WebCaptureUrlValidator validator() {
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setAllowedOrigins(List.of("http://localhost:8080"));
        return new WebCaptureUrlValidator(properties);
    }

    @Test
    void acceptsExactOriginAndMasksEveryQueryValue() {
        var result = validator().validate("http://localhost:8080/list.do?token=secret&page=1");
        assertThat(result.maskedUrl()).doesNotContain("secret", "page=1").contains("page=");
    }

    @Test
    void rejectsPrefixHostUserInfoAndDifferentPort() {
        assertThatThrownBy(() -> validator().validate("http://localhost.evil:8080/list"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator().validate("http://user@localhost:8080/list"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator().validate("http://localhost:8081/list"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
