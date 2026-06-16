package com.krdevops.springai.service;

import com.krdevops.springai.config.EgovProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void saveGeneratedCode_relativePath_savesUnderBasePath() throws Exception {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));

        String result = service.saveGeneratedCode("emp/EmployerVO.java", "class EmployerVO {}");

        assertThat(result).startsWith("파일 저장 완료:");
        assertThat(basePath.resolve("emp/EmployerVO.java")).exists();
        assertThat(Files.readString(basePath.resolve("emp/EmployerVO.java"))).isEqualTo("class EmployerVO {}");
    }

    @Test
    void saveGeneratedCode_relativeTraversal_isBlocked() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));

        String result = service.saveGeneratedCode("../escape.txt", "escape");

        assertThat(result).startsWith("파일 저장 실패: 허용 범위 밖 경로입니다.");
        assertThat(basePath.getParent().resolve("escape.txt")).doesNotExist();
    }

    @Test
    void saveGeneratedCode_absolutePathUnderBasePath_isAllowed() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));
        Path target = basePath.resolve("emp/EmployerService.java");

        String result = service.saveGeneratedCode(target.toString(), "interface EmployerService {}");

        assertThat(result).startsWith("파일 저장 완료:");
        assertThat(target).exists();
    }

    @Test
    void saveGeneratedCode_absolutePathUnderWorkspaceRoot_isAllowed() {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path basePath = workspaceRoot.resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));
        Path target = workspaceRoot.resolve("existing-project/src/main/java/EmployerVO.java");

        String result = service.saveGeneratedCode(target.toString(), "class EmployerVO {}");

        assertThat(result).startsWith("파일 저장 완료:");
        assertThat(target).exists();
    }

    @Test
    void saveGeneratedCode_absolutePathUnderAllowedPath_isAllowed() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        Path allowedPath = tempDir.resolve("external").resolve("egov-boot-web");
        CodeService service = new CodeService(egovProperties(basePath, allowedPath));
        Path target = allowedPath.resolve("src/main/java/EmployerVO.java");

        String result = service.saveGeneratedCode(target.toString(), "class EmployerVO {}");

        assertThat(result).startsWith("파일 저장 완료:");
        assertThat(target).exists();
    }

    @Test
    void saveGeneratedCode_absolutePathOutsideWorkspaceRoot_isBlocked() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));
        Path target = tempDir.resolve("outside/escape.txt");

        String result = service.saveGeneratedCode(target.toString(), "escape");

        assertThat(result).startsWith("파일 저장 실패: 허용 범위 밖 경로입니다");
        assertThat(target).doesNotExist();
    }

    private static EgovProperties egovProperties(Path basePath) {
        return egovProperties(basePath, null);
    }

    private static EgovProperties egovProperties(Path basePath, Path allowedPath) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(basePath.toString());
        if (allowedPath != null) {
            output.setAllowedPaths(List.of(allowedPath.toString()));
        }
        properties.setOutput(output);
        return properties;
    }
}
