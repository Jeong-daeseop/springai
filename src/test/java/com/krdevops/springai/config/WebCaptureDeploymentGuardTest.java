package com.krdevops.springai.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebCaptureDeploymentGuardTest {

    @Test
    void releaseOneIsDisabledByDefault() {
        WebCaptureProperties properties = new WebCaptureProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledCaptureRequiresDifferentSecretsAndLoopbackExtractor() {
        WebCaptureProperties properties = enabledProperties();
        properties.setDocumentKeySecret(properties.getExtractorApiKey());

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("달라야");

        properties.setDocumentKeySecret("document-secret");
        properties.setExtractorBaseUrl(URI.create("http://192.0.2.10:4319"));
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void enabledCaptureRejectsNonLoopbackSpringServerBind() {
        WebCaptureProperties properties = enabledProperties();
        properties.validate();
        var external = new WebCaptureDeploymentGuard(properties,
                new MockEnvironment().withProperty("server.address", "0.0.0.0"));
        var loopback = new WebCaptureDeploymentGuard(properties,
                new MockEnvironment().withProperty("server.address", "127.0.0.1"));

        assertThatThrownBy(external::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
        assertThatCode(loopback::validate).doesNotThrowAnyException();
    }

    private static WebCaptureProperties enabledProperties() {
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setEnabled(true);
        properties.setExtractorApiKey("extractor-secret");
        properties.setDocumentKeySecret("document-secret");
        return properties;
    }
}
