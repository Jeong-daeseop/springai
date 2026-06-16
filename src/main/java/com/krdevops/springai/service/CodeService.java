package com.krdevops.springai.service;

import com.krdevops.springai.config.EgovProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeService {

    private final EgovProperties egovProperties;

    /**
     * @deprecated text-block 치환 방식은 FreeMarker 전환으로 제거되었습니다.
     *             buildFullCrudPrompt(llmProvider="auto")를 사용하세요.
     */
    @Deprecated
    public String generateSource(String layer, Map<String, String> values) {
        return "[DEPRECATED] generateSource()는 더 이상 지원되지 않습니다. "
            + "buildFullCrudPrompt(llmProvider=\"auto\")를 사용하세요.";
    }

    public String saveGeneratedCode(String filePath, String code) {
        try {
            Path target = Paths.get(filePath);
            if (target.isAbsolute()) {
                target = target.normalize();
                // 절대경로 allowlist: basePath, 그 부모(workspace root), 명시 allowedPaths 하위만 허용
                // e.g., basePath=~/Desktop/egov-generated -> workspace=~/Desktop 까지 허용
                Path basePath      = Paths.get(egovProperties.getOutput().getBasePath()).toAbsolutePath().normalize();
                Path workspaceRoot = basePath.getParent();
                boolean underBase      = target.startsWith(basePath);
                boolean underWorkspace = workspaceRoot != null && target.startsWith(workspaceRoot);
                boolean underAllowedPath = egovProperties.getOutput().getAllowedPaths().stream()
                    .map(path -> Paths.get(path).toAbsolutePath().normalize())
                    .anyMatch(target::startsWith);
                if (!underBase && !underWorkspace && !underAllowedPath) {
                    log.warn("허용 범위 밖 절대경로 차단: {}", filePath);
                    return "파일 저장 실패: 허용 범위 밖 경로입니다 (egov.output.base-path, workspace 또는 allowed-paths 하위만 허용).";
                }
            } else {
                // 상대경로: base-path 아래로 한정 (Path Traversal 방어)
                Path base = Paths.get(egovProperties.getOutput().getBasePath()).toAbsolutePath().normalize();
                target = base.resolve(filePath).normalize();
                if (!target.startsWith(base)) {
                    log.warn("Path Traversal 시도 차단: {}", filePath);
                    return "파일 저장 실패: 허용 범위 밖 경로입니다.";
                }
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, code);
            return "파일 저장 완료: " + target + " (" + code.length() + " chars)";
        } catch (IOException e) {
            return "파일 저장 실패: " + e.getMessage();
        }
    }

    public String checkOutputDirectory(String baseDir) {
        Path path = Paths.get(baseDir);
        if (!Files.exists(path)) {
            return "디렉터리가 존재하지 않습니다. saveGeneratedCode 호출 시 자동 생성됩니다: " + baseDir;
        }
        try {
            StringBuilder sb = new StringBuilder("디렉터리: " + baseDir + "\n");
            Files.walk(path, 3)
                .filter(Files::isRegularFile)
                .forEach(f -> sb.append("  ").append(path.relativize(f)).append("\n"));
            return sb.toString();
        } catch (IOException e) {
            return "디렉터리 확인 실패: " + e.getMessage();
        }
    }
}
