package com.krdevops.springai.service;

import com.krdevops.springai.config.EgovProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void saveGeneratedBinary_relativePath_savesUnderBasePath() throws Exception {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));
        byte[] content = {1, 2, 3, 4};

        String result = service.saveGeneratedBinary("resources/images/egov-logo.png", content);

        assertThat(result).startsWith("파일 저장 완료:");
        Path saved = basePath.resolve("resources/images/egov-logo.png");
        assertThat(saved).exists();
        assertThat(Files.readAllBytes(saved)).isEqualTo(content);
    }

    @Test
    void saveGeneratedBinary_relativeTraversal_isBlocked() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));

        String result = service.saveGeneratedBinary("../escape.png", new byte[] {1});

        assertThat(result).startsWith("파일 저장 실패: 허용 범위 밖 경로입니다.");
        assertThat(basePath.getParent().resolve("escape.png")).doesNotExist();
    }

    @Test
    void deleteGeneratedFile_underAllowedPath_deletesFile() throws Exception {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));
        Path target = basePath.resolve("index.html");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "legacy");

        String result = service.deleteGeneratedFile(target.toString());

        assertThat(result).startsWith("파일 삭제 완료:");
        assertThat(target).doesNotExist();
    }

    @Test
    void validateOutputRoot_underBasePath_doesNotThrow() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));

        assertThatCode(() -> service.validateOutputRoot(basePath.resolve("emp").toString()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateOutputRoot_underAllowedPath_doesNotThrow() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        Path allowedPath = tempDir.resolve("external").resolve("egov-boot-web");
        CodeService service = new CodeService(egovProperties(basePath, allowedPath));

        assertThatCode(() -> service.validateOutputRoot(allowedPath.toString()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateOutputRoot_outsideAllowedLocations_throwsSecurityException() {
        Path basePath = tempDir.resolve("workspace").resolve("egov-generated");
        CodeService service = new CodeService(egovProperties(basePath));
        Path outside = tempDir.resolve("outside");

        assertThatThrownBy(() -> service.validateOutputRoot(outside.toString()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void generateSourceDeprecationNotice_guidesToAutoGenerator() {
        CodeService service = new CodeService(egovProperties(tempDir.resolve("egov-generated")));

        assertThat(service.generateSourceDeprecationNotice())
                .contains("[DEPRECATED]")
                .contains("buildFullCrudPrompt(llmProvider=\"auto\")");
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
