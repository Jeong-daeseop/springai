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

/**
 * WP7 2차 pass 잔여 항목/ARCH-0717: {@code CodeService.saveGeneratedBinary} 직접 호출 대신 공용
 * {@code ApprovedProjectWritePort}(BEST_EFFORT_COMPATIBILITY)로 바이너리 로고 자산을 저장한다.
 */
class ClasspathAssetCopierTest {

    private ClasspathAssetCopier copier(Path basePath) {
        CodeService codeService = new CodeService(egovProperties(basePath));
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        return new ClasspathAssetCopier(codeService, writePort);
    }

    @Test
    void copyLogo_writesLogoFromClasspathAsset(@TempDir Path tempDir) {
        ClasspathAssetCopier copier = copier(tempDir);

        String line = copier.copyLogo(tempDir, true);

        Path logo = tempDir.resolve("src/main/webapp/resources/images/egov-logo.png");
        assertThat(logo).isRegularFile();
        assertThat(line).contains("생성:").contains("egov-logo.png");
    }

    @Test
    void copyLogo_overwriteFalseAndExisting_preservesFileWithoutWriting(@TempDir Path tempDir) throws Exception {
        ClasspathAssetCopier copier = copier(tempDir);
        Path logo = tempDir.resolve("src/main/webapp/resources/images/egov-logo.png");
        Files.createDirectories(logo.getParent());
        Files.write(logo, new byte[] {1, 2, 3});

        String line = copier.copyLogo(tempDir, false);

        assertThat(Files.readAllBytes(logo)).isEqualTo(new byte[] {1, 2, 3});
        assertThat(line).contains("보존:").contains("egov-logo.png");
    }

    @Test
    void copyLogo_saveFailure_returnsFailureLineWithPathAndReason(@TempDir Path tempDir) throws Exception {
        ClasspathAssetCopier copier = copier(tempDir);
        // 대상 디렉터리 자리를 일반 파일로 막아 Files.createDirectories가 실패하게 한다.
        Path imagesDir = tempDir.resolve("src/main/webapp/resources/images");
        Files.createDirectories(imagesDir.getParent());
        Files.writeString(imagesDir, "blocked-by-file");

        String line = copier.copyLogo(tempDir, true);

        assertThat(line).contains("실패:").contains("egov-logo.png");
        // logo.resolve("egov-logo.png")는 부모가 파일이라 Files.exists/notExists 둘 다 false를
        // 반환하는 판정 불가 상태다 — 대신 블로킹 파일 자체가 그대로인지로 "쓰지 않았음"을 확인한다.
        assertThat(Files.readString(imagesDir)).isEqualTo("blocked-by-file");
    }

    private EgovProperties egovProperties(Path basePath) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(basePath.toString());
        properties.setOutput(output);
        return properties;
    }
}
