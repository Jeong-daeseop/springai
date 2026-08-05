package com.krdevops.springai.service;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WAR 프로젝트의 기본 진입점을 화면 기술과 무관하게 {@code index.jsp}로 통일한다.
 *
 * <p>{@code index.jsp}는 화면을 렌더링하지 않고 생성된 목록 Controller의 {@code .do}
 * URL로 forward하는 부트스트랩 역할만 담당한다. 따라서 대상 화면이 JSP인지 Thymeleaf인지와
 * 관계없이 동일한 진입점 정책을 사용할 수 있다.</p>
 *
 * <p>WP7 2차 pass 잔여 항목/ARCH-0717: index.jsp 생성 + web.xml 패치 + index.html 삭제 3단계를
 * 공용 {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#ATOMIC_APPROVED})로 단일
 * {@link ProjectChangeSet}에 묶는다 — 예전에는 각 단계가 독립적인 {@code codeService} 호출이라
 * 중간 단계 실패(예: web.xml 패치 실패) 시 이미 쓴 index.jsp가 롤백되지 않는 부분 적용 구멍이
 * 있었다. {@code beforeHash}는 이 메서드가 직접 읽은 현재 파일 상태로 계산해, 그 사이의 드리프트만
 * 막는다.</p>
 */
@Service
@RequiredArgsConstructor
public class WarEntryPointConfigurer {

    private static final String WEB_XML_RELATIVE_PATH = "src/main/webapp/WEB-INF/web.xml";
    private static final String INDEX_JSP_RELATIVE_PATH = "src/main/webapp/index.jsp";
    private static final String INDEX_HTML_RELATIVE_PATH = "src/main/webapp/index.html";
    private static final Pattern WELCOME_FILE_PATTERN = Pattern.compile(
            "<welcome-file>\\s*index\\.(?:jsp|html)\\s*</welcome-file>");

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final OperationHashFactory hashFactory;

    public ConfigurationResult configure(String outputPath, String targetUrl) {
        validateTargetUrl(targetUrl);

        Path projectRoot = Paths.get(outputPath).normalize();
        Path indexJsp = projectRoot.resolve(INDEX_JSP_RELATIVE_PATH);
        Path webXml = projectRoot.resolve(WEB_XML_RELATIVE_PATH);
        Path indexHtml = projectRoot.resolve(INDEX_HTML_RELATIVE_PATH);

        if (!Files.exists(webXml)) {
            return ConfigurationResult.failure("web.xml 파일이 없어 welcome-file을 index.jsp로 설정하지 못했습니다: " + webXml);
        }

        String webXmlContent;
        try {
            webXmlContent = Files.readString(webXml);
        } catch (IOException e) {
            return ConfigurationResult.failure("web.xml 읽기 실패 — " + e.getMessage());
        }

        Matcher welcomeMatcher = WELCOME_FILE_PATTERN.matcher(webXmlContent);
        if (!welcomeMatcher.find()) {
            return ConfigurationResult.failure(
                    "web.xml에서 index.jsp 또는 index.html welcome-file 선언을 찾지 못했습니다: " + webXml);
        }

        codeService.validateOutputRoot(outputPath);

        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        changes.add(new ProjectChangeSet.FileChange(
                INDEX_JSP_RELATIVE_PATH, currentHashOrNull(indexJsp), indexJsp(targetUrl), null));

        String patchedWebXml = welcomeMatcher.replaceAll("<welcome-file>index.jsp</welcome-file>");
        if (!patchedWebXml.equals(webXmlContent)) {
            changes.add(new ProjectChangeSet.FileChange(
                    WEB_XML_RELATIVE_PATH, hashFactory.sha256Hex(webXmlContent.getBytes(StandardCharsets.UTF_8)),
                    patchedWebXml, null));
        }

        List<ProjectChangeSet.FileDeletion> deletions = List.of(
                new ProjectChangeSet.FileDeletion(INDEX_HTML_RELATIVE_PATH, currentHashOrNull(indexHtml)));

        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath, null, changes, deletions, ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);

        return switch (outcome.status()) {
            case APPLIED -> ConfigurationResult.success(
                    "index.jsp → " + targetUrl + ", web.xml welcome-file=index.jsp, index.html 정리 완료");
            case CONFLICT -> ConfigurationResult.failure(
                    "웹 진입점 설정 충돌(적용 직전 파일이 변경됨): " + outcome.conflictingPaths());
            default -> ConfigurationResult.failure("웹 진입점 설정 실패 — " + outcome.failureDetail());
        };
    }

    private String currentHashOrNull(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return hashFactory.sha256Hex(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new IllegalStateException("파일 읽기 실패: " + path, e);
        }
    }

    private static String indexJsp(String targetUrl) {
        return """
                <%%@ page contentType="text/html;charset=UTF-8" %%>
                <jsp:forward page="%s"/>
                """.formatted(targetUrl);
    }

    private static void validateTargetUrl(String targetUrl) {
        if (targetUrl == null || !targetUrl.startsWith("/")
                || targetUrl.indexOf('"') >= 0 || targetUrl.indexOf('<') >= 0 || targetUrl.indexOf('>') >= 0
                || targetUrl.indexOf('\r') >= 0 || targetUrl.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "WAR 기본 진입 URL은 '/'로 시작하고 따옴표·태그·개행을 포함하지 않아야 합니다: " + targetUrl);
        }
    }

    public record ConfigurationResult(boolean success, String message) {
        public static ConfigurationResult success(String message) {
            return new ConfigurationResult(true, message);
        }

        public static ConfigurationResult failure(String message) {
            return new ConfigurationResult(false, message);
        }
    }
}
