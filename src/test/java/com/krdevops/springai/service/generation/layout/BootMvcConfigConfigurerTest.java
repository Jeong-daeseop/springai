package com.krdevops.springai.service.generation.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BootMvcConfigConfigurerTest {

    private static final String PKG = "egovframework.let.emp";
    private static final String CONFIG_REL =
            "src/main/java/egovframework/let/emp/config/EgovWebMvcConfig.java";

    private BootMvcConfigConfigurer configurer(Path root) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(root.toString());
        properties.setOutput(output);
        return new BootMvcConfigConfigurer(
                new CodeService(properties),
                new FileSystemApprovedProjectWritePort(
                        new SafePathResolver(), new OperationHashFactory(new ObjectMapper())),
                new OperationHashFactory(new ObjectMapper()));
    }

    @Test
    void createsWebMvcConfig_whenAbsent(@TempDir Path root) {
        BootMvcConfigConfigurer.InterceptorRegistrationResult result =
                configurer(root).configure(root, PKG);

        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("생성:", "WebMvcConfigurer");

        Path config = root.resolve(CONFIG_REL);
        assertThat(config).isRegularFile();
        String source = readString(config);
        assertThat(source).contains(
                "package egovframework.let.emp.config;",
                "implements WebMvcConfigurer",
                "import egovframework.let.emp.cmm.web.EgovGnbMenuInterceptor;",
                "import egovframework.let.emp.cmm.service.GnbMenuMapper;",
                "registry.addInterceptor(new EgovGnbMenuInterceptor(gnbMenuMapper))",
                ".addPathPatterns(\"/**\")");
    }

    @Test
    void preservesWebMvcConfig_whenAlreadyRegistered(@TempDir Path root) {
        configurer(root).configure(root, PKG);
        String first = readString(root.resolve(CONFIG_REL));

        BootMvcConfigConfigurer.InterceptorRegistrationResult second =
                configurer(root).configure(root, PKG);

        assertThat(second.failed()).isFalse();
        assertThat(second.message()).contains("보존:", "이미 등록됨");
        assertThat(readString(root.resolve(CONFIG_REL))).isEqualTo(first);
    }

    @Test
    void advisesManualRegistration_whenConfigExistsWithoutInterceptor(@TempDir Path root) throws Exception {
        Path config = root.resolve(CONFIG_REL);
        Files.createDirectories(config.getParent());
        Files.writeString(config, "package egovframework.let.emp.config;\npublic class EgovWebMvcConfig {}\n");

        BootMvcConfigConfigurer.InterceptorRegistrationResult result =
                configurer(root).configure(root, PKG);

        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("수동 등록 필요", "addInterceptors");
        // 사용자 파일은 자동 편집하지 않는다.
        assertThat(readString(config)).isEqualTo(
                "package egovframework.let.emp.config;\npublic class EgovWebMvcConfig {}\n");
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
