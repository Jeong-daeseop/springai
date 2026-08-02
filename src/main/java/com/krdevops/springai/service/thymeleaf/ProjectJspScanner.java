package com.krdevops.springai.service.thymeleaf;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * I-6: eGovFrame 프로젝트에서 변환 대상 JSP 파일을 자동 발견.
 * glob 패턴으로 필터링하고 제외 목록 적용.
 */
@Service
public class ProjectJspScanner {

    private static final List<String> DEFAULT_EXCLUDE_PATTERNS = List.of(
            "**/build/**",
            "**/target/**",
            "**/.idea/**",
            "**/.gradle/**",
            "**/node_modules/**"
    );

    public List<ScannedJspFile> scanJspFiles(Path projectRoot, String globPattern, List<String> additionalExcludes) {
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("프로젝트 루트가 디렉터리가 아닙니다: " + projectRoot);
        }

        List<String> allExcludes = new ArrayList<>(DEFAULT_EXCLUDE_PATTERNS);
        if (additionalExcludes != null) {
            allExcludes.addAll(additionalExcludes);
        }

        PathMatcher jspMatcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        List<PathMatcher> excludeMatchers = allExcludes.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .toList();

        List<ScannedJspFile> results = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> jspMatcher.matches(projectRoot.relativize(path)))
                    .filter(path -> !isExcluded(path, projectRoot, excludeMatchers))
                    .forEach(path -> {
                        Path relative = projectRoot.relativize(path);
                        results.add(new ScannedJspFile(
                                relative.toString(),
                                path,
                                inferControllerPath(relative),
                                inferVoPath(relative)
                        ));
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("JSP 스캔 실패: " + projectRoot, exception);
        }

        return results;
    }

    private boolean isExcluded(Path path, Path root, List<PathMatcher> excludeMatchers) {
        Path relative = root.relativize(path);
        return excludeMatchers.stream().anyMatch(matcher -> matcher.matches(relative));
    }

    private String inferControllerPath(Path jspRelative) {
        String pathStr = jspRelative.toString().replace("\\", "/");
        if (pathStr.contains("/jsp/")) {
            String controllerName = pathStr
                    .replaceAll("^.*/jsp/", "")
                    .replaceAll("\\.jsp$", "")
                    .replaceAll("^Egov", "")
                    .replaceAll("(List|Regist|Modify|Detail)$", "");
            return controllerName.substring(0, 1).toUpperCase(Locale.ROOT) + controllerName.substring(1) + "Controller.java";
        }
        return null;
    }

    private String inferVoPath(Path jspRelative) {
        String pathStr = jspRelative.toString().replace("\\", "/");
        if (pathStr.contains("/jsp/")) {
            String voName = pathStr
                    .replaceAll("^.*/jsp/", "")
                    .replaceAll("\\.jsp$", "")
                    .replaceAll("^Egov", "");
            return voName.substring(0, 1).toUpperCase(Locale.ROOT) + voName.substring(1) + "VO.java";
        }
        return null;
    }

    public record ScannedJspFile(
            String jspRelativePath,
            Path jspAbsolutePath,
            String inferredControllerPath,
            String inferredVoPath
    ) {
    }
}
