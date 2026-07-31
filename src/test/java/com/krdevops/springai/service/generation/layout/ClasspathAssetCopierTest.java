package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.service.CodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClasspathAssetCopierTest {

    @Mock CodeService codeService;

    ClasspathAssetCopier copier;

    @Test
    void copyLogo_writesLogoFromClasspathAsset(@TempDir Path tempDir) {
        copier = new ClasspathAssetCopier(codeService);
        when(codeService.saveGeneratedBinary(any(), any())).thenAnswer(invocation -> {
            Path path = Path.of(invocation.getArgument(0, String.class));
            byte[] content = invocation.getArgument(1, byte[].class);
            Files.createDirectories(path.getParent());
            Files.write(path, content);
            return "파일 저장 완료: " + path;
        });

        String line = copier.copyLogo(tempDir, true);

        Path logo = tempDir.resolve("src/main/webapp/resources/images/egov-logo.png");
        assertThat(logo).isRegularFile();
        assertThat(line).contains("생성:").contains("egov-logo.png");
    }

    @Test
    void copyLogo_overwriteFalseAndExisting_preservesFileWithoutTouchingCodeService(@TempDir Path tempDir)
            throws Exception {
        copier = new ClasspathAssetCopier(codeService);
        Path logo = tempDir.resolve("src/main/webapp/resources/images/egov-logo.png");
        Files.createDirectories(logo.getParent());
        Files.write(logo, new byte[] {1, 2, 3});

        String line = copier.copyLogo(tempDir, false);

        assertThat(Files.readAllBytes(logo)).isEqualTo(new byte[] {1, 2, 3});
        assertThat(line).contains("보존:").contains("egov-logo.png");
    }

    @Test
    void copyLogo_saveFailure_returnsFailureLineWithPathAndSaveResult(@TempDir Path tempDir) {
        copier = new ClasspathAssetCopier(codeService);
        when(codeService.saveGeneratedBinary(any(), any())).thenReturn("파일 저장 실패: 디스크 오류");

        String line = copier.copyLogo(tempDir, true);

        Path logo = tempDir.resolve("src/main/webapp/resources/images/egov-logo.png");
        assertThat(line).contains("실패: " + logo).contains("파일 저장 실패: 디스크 오류");
    }
}
