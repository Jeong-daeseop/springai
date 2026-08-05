package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * GNB 브랜드 영역(egov-header-brand)에 쓸 eGovFrame 로고 이미지를 클래스패스 자산에서
 * 프로젝트 정적 리소스 경로로 복사한다. layout 파일과 동일하게 overwrite=false면 기존 파일을 보존한다.
 *
 * <p>WP7 2차 pass 잔여 항목/ARCH-0717: 저장은 {@code CodeService.saveGeneratedBinary} 직접 호출
 * 대신 공용 {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY} —
 * 단일 파일이라 배치 이점은 없지만 write 경로를 일원화한다)로 위임한다. {@code outputPath}가 허용된
 * 위치인지는 {@link CodeService#validateOutputRoot}로 먼저 검증한다 — {@code ApprovedProjectWritePort}는
 * 주어진 root 안에서의 이탈만 막지, root 자체가 허용됐는지는 모르기 때문이다(ARCH-0704 부재,
 * {@code CodeServiceGenerationExecutor}와 동일한 방어 이유).
 */
@Component
@RequiredArgsConstructor
public class ClasspathAssetCopier {

    private static final String LOGO_CLASSPATH_RESOURCE = "templates/egov/assets/egov-logo.png";
    private static final String LOGO_RELATIVE_PATH = "src/main/webapp/resources/images/egov-logo.png";

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;

    public String copyLogo(Path outputPath, boolean overwrite) {
        Path filePath = Paths.get(outputPath.toString(), LOGO_RELATIVE_PATH).normalize();
        if (!overwrite && Files.exists(filePath)) {
            return "  보존: " + filePath + "\n";
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(LOGO_CLASSPATH_RESOURCE)) {
            if (in == null) {
                return "  실패: " + LOGO_CLASSPATH_RESOURCE + " 클래스패스 자산을 찾을 수 없습니다.\n";
            }
            codeService.validateOutputRoot(outputPath.toString());
            ProjectChangeSet changeSet = new ProjectChangeSet(
                    outputPath.toString(), null,
                    List.of(new ProjectChangeSet.FileChange(LOGO_RELATIVE_PATH, null, null, null, in.readAllBytes())),
                    List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);
            ApplyOutcome outcome = writePort.apply(changeSet);
            String failureMessage = outcome.failureMessages().get(LOGO_RELATIVE_PATH);
            if (failureMessage != null) {
                return "  실패: " + filePath + " — " + failureMessage + "\n";
            }
            return "  생성: " + filePath + "\n";
        } catch (IOException e) {
            return "  실패: " + filePath + " 로고 이미지 복사 오류 — " + e.getMessage() + "\n";
        }
    }
}
