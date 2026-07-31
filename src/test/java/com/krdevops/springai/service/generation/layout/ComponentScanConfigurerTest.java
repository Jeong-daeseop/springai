package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentScanConfigurerTest {

    private final ComponentScanConfigurer configurer = new ComponentScanConfigurer(new MyBatisRuntimeConfigurer());

    @Test
    void patch_noComponentScanTag_returnsUnchangedContentWithConfirmationMessage(@TempDir Path tempDir) {
        String content = "<beans></beans>";

        ComponentScanConfigurer.ComponentScanPatch result =
                configurer.patch(content, "egovframework.let.emp", tempDir.resolve("servlet-context.xml"));

        assertThat(result.content()).isEqualTo(content);
        assertThat(result.changed()).isFalse();
        assertThat(result.message()).contains("확인 필요:");
    }

    @Test
    void patch_existingBasePackageAlreadyCovers_preservesWithoutChange(@TempDir Path tempDir) {
        String content = "<context:component-scan base-package=\"egovframework.let\"/>";

        ComponentScanConfigurer.ComponentScanPatch result =
                configurer.patch(content, "egovframework.let.bbs", tempDir.resolve("servlet-context.xml"));

        assertThat(result.changed()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.message()).contains("보존:").contains("이미 포함");
    }

    @Test
    void patch_narrowerBasePackage_broadensButNeverNarrows(@TempDir Path tempDir) {
        String content = "<context:component-scan base-package=\"egovframework.let.sample\"/>";

        ComponentScanConfigurer.ComponentScanPatch result =
                configurer.patch(content, "egovframework.let.bbs", tempDir.resolve("servlet-context.xml"));

        assertThat(result.changed()).isTrue();
        assertThat(result.content()).contains("base-package=\"egovframework.let\"");
        assertThat(result.message()).contains("변경:").contains("확장");
    }
}
