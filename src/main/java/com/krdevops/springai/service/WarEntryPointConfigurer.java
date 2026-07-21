package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WAR 프로젝트의 기본 진입점을 화면 기술과 무관하게 {@code index.jsp}로 통일한다.
 *
 * <p>{@code index.jsp}는 화면을 렌더링하지 않고 생성된 목록 Controller의 {@code .do}
 * URL로 forward하는 부트스트랩 역할만 담당한다. 따라서 대상 화면이 JSP인지 Thymeleaf인지와
 * 관계없이 동일한 진입점 정책을 사용할 수 있다.</p>
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

        // web.xml 구조를 먼저 확인한 뒤 진입 파일을 생성한다. 설정이 비정상인 프로젝트에
        // index.jsp만 새로 남는 부분 변경을 피하기 위한 순서다.
        String indexSave = codeService.saveGeneratedCode(indexJsp.toString(), indexJsp(targetUrl));
        if (isFailure(indexSave)) {
            return ConfigurationResult.failure("index.jsp 생성 실패 — " + indexSave);
        }

        String patchedWebXml = welcomeMatcher.replaceAll("<welcome-file>index.jsp</welcome-file>");
        if (!patchedWebXml.equals(webXmlContent)) {
            String webXmlSave = codeService.saveGeneratedCode(webXml.toString(), patchedWebXml);
            if (isFailure(webXmlSave)) {
                return ConfigurationResult.failure("web.xml 변경 실패 — " + webXmlSave);
            }
        }

        String deleteResult = codeService.deleteGeneratedFile(indexHtml.toString());
        if (isFailure(deleteResult)) {
            return ConfigurationResult.failure("기존 index.html 정리 실패 — " + deleteResult);
        }

        return ConfigurationResult.success(
                "index.jsp → " + targetUrl + ", web.xml welcome-file=index.jsp, index.html 정리 완료");
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

    private static boolean isFailure(String result) {
        return result == null || result.startsWith("파일 저장 실패") || result.startsWith("파일 삭제 실패");
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
