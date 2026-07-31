package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GNB 브랜드 영역(egov-header-brand)에 쓸 eGovFrame 로고 이미지를 클래스패스 자산에서
 * 프로젝트 정적 리소스 경로로 복사한다. layout 파일과 동일하게 overwrite=false면 기존 파일을 보존한다.
 */
@Component
@RequiredArgsConstructor
public class ClasspathAssetCopier {

    private static final String LOGO_CLASSPATH_RESOURCE = "templates/egov/assets/egov-logo.png";
    private static final String LOGO_RELATIVE_PATH = "src/main/webapp/resources/images/egov-logo.png";

    private final CodeService codeService;

    public String copyLogo(Path outputPath, boolean overwrite) {
        Path filePath = Paths.get(outputPath.toString(), LOGO_RELATIVE_PATH).normalize();
        if (!overwrite && Files.exists(filePath)) {
            return "  보존: " + filePath + "\n";
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(LOGO_CLASSPATH_RESOURCE)) {
            if (in == null) {
                return "  실패: " + LOGO_CLASSPATH_RESOURCE + " 클래스패스 자산을 찾을 수 없습니다.\n";
            }
            String saveResult = codeService.saveGeneratedBinary(filePath.toString(), in.readAllBytes());
            if (saveResult.startsWith("파일 저장 실패")) {
                return "  실패: " + filePath + " — " + saveResult + "\n";
            }
            return "  생성: " + filePath + "\n";
        } catch (IOException e) {
            return "  실패: " + filePath + " 로고 이미지 복사 오류 — " + e.getMessage() + "\n";
        }
    }
}
