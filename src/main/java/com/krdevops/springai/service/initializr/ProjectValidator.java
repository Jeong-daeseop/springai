package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ProjectValidator {

    /** 사전 검증: FilePlan 실행 전 */
    public void validatePlans(List<FilePlan> plans) {
        Set<String> paths = new HashSet<>();
        for (FilePlan p : plans) {
            if (p.relativePath() == null || p.relativePath().isBlank())
                throw new IllegalArgumentException("FilePlan relativePath가 비어 있습니다.");
            if (p.relativePath().contains(".."))
                throw new IllegalArgumentException("상위 경로 이동 불가: " + p.relativePath());
            if (!paths.add(p.relativePath()))
                throw new IllegalArgumentException("중복 FilePlan 경로: " + p.relativePath());
        }
    }

    /** 사후 검증: 파일 존재 + 내용 정합성 */
    public void validateResult(ProjectSpec s, GenerationReport report) {
        // 1. 필수 파일 존재 검증
        List<String> required = s.boot()
            ? List.of("src/main/resources/application.yml")
            : List.of(
                "src/main/resources/egovframework/spring/context-common.xml",
                "src/main/webapp/WEB-INF/web.xml");

        for (String path : required) {
            if (!Files.exists(s.root().resolve(path))) {
                report.warn("필수 파일 누락: " + path);
            }
        }

        // 2. 내용 검증: namespace 일관성
        validateNamespace(s, report);

        // 3. 내용 검증: Java 버전
        validateJavaVersion(s, report);
    }

    private void validateNamespace(ProjectSpec s, GenerationReport report) {
        String unexpected = s.cap().jakarta() ? "javax." : "jakarta.";

        java.nio.file.Path buildFile = s.gradle()
            ? s.root().resolve("build.gradle")
            : s.root().resolve("pom.xml");

        if (Files.exists(buildFile)) {
            try {
                String content = Files.readString(buildFile);
                if (content.contains(unexpected + "servlet")) {
                    report.warn("빌드 파일에 " + unexpected + " namespace 혼입: " + buildFile.getFileName());
                }
            } catch (IOException e) {
                report.warn("빌드 파일 읽기 실패: " + e.getMessage());
            }
        }
    }

    private void validateJavaVersion(ProjectSpec s, GenerationReport report) {
        String expected = s.cap().javaVersion();
        java.nio.file.Path pom = s.root().resolve("pom.xml");
        if (Files.exists(pom)) {
            try {
                String content = Files.readString(pom);
                if (!content.contains("<java.version>" + expected + "</java.version>")
                    && !content.contains("<maven.compiler.source>" + expected)) {
                    report.warn("pom.xml Java 버전이 " + expected + "이 아닙니다.");
                }
            } catch (IOException e) {
                report.warn("pom.xml 읽기 실패: " + e.getMessage());
            }
        }
    }
}
