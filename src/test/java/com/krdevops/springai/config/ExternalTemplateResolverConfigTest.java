package com.krdevops.springai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExternalTemplateResolverConfigTest {

    @TempDir
    Path tempDirectory;

    @Test
    void 실제_절대_디렉터리를_우선순위가_높은_resolver로_구성한다() {
        var resolver = new ExternalTemplateResolverConfig().externalTemplateResolver(
                mock(ApplicationContext.class), tempDirectory.toString());

        assertThat(resolver.getPrefix()).startsWith("file:");
        assertThat(resolver.getSuffix()).isEqualTo(".html");
        assertThat(resolver.getOrder()).isZero();
        assertThat(resolver.getCheckExistence()).isTrue();
    }

    @Test
    void 상대경로는_거부한다() {
        assertThatThrownBy(() -> new ExternalTemplateResolverConfig().externalTemplateResolver(
                mock(ApplicationContext.class), "relative/templates"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("절대 경로");
    }
}

