package com.krdevops.springai.service.generation.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentScanConfigurerTest {

    // 이 테스트는 MyBatisRuntimeConfigurer의 순수 패키지 병합 로직만 쓰고 ensureConfigured(파일
    // write)는 호출하지 않으므로, write 관련 협력자는 어떤 값이든 상관없다.
    private final ComponentScanConfigurer configurer = new ComponentScanConfigurer(new MyBatisRuntimeConfigurer(
            new CodeService(new EgovProperties()),
            new FileSystemApprovedProjectWritePort(new SafePathResolver(), new OperationHashFactory(new ObjectMapper())),
            new OperationHashFactory(new ObjectMapper())));

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
