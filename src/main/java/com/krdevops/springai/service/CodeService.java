package com.krdevops.springai.service;

import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.write.AllowAllProjectRootRegistryPort;
import com.krdevops.springai.service.write.ProjectRootRegistryPort;
import com.krdevops.springai.service.write.SafePathResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@Service
public class CodeService {

    private final EgovProperties egovProperties;
    private final ProjectRootRegistryPort projectRootRegistryPort;
    private final SafePathResolver pathResolver;

    @Autowired
    public CodeService(EgovProperties egovProperties, ProjectRootRegistryPort projectRootRegistryPort,
            SafePathResolver pathResolver) {
        this.egovProperties = egovProperties;
        this.projectRootRegistryPort = projectRootRegistryPort;
        this.pathResolver = pathResolver;
    }

    /**
     * 기존 1-arg 호출자·테스트 호환용 — registry 등록은 통과 처리한다
     * ({@link AllowAllProjectRootRegistryPort}).
     */
    public CodeService(EgovProperties egovProperties) {
        this(egovProperties, new AllowAllProjectRootRegistryPort(), new SafePathResolver());
    }

    /**
     * @deprecated text-block 치환 방식은 FreeMarker 전환으로 제거되었습니다.
     *             buildFullCrudPrompt(llmProvider="auto")를 사용하세요.
     */
    @Deprecated(forRemoval = true)
    public String generateSource(String layer, Map<String, String> values) {
        return generateSourceDeprecationNotice();
    }

    public String generateSourceDeprecationNotice() {
        return "[DEPRECATED] generateSource()는 더 이상 지원되지 않습니다. "
            + "buildFullCrudPrompt(llmProvider=\"auto\")를 사용하세요.";
    }

    public String saveGeneratedCode(String filePath, String code) {
        try {
            Path target = resolveAndValidateTarget(filePath);
            Files.createDirectories(target.getParent());
            Files.writeString(target, code);
            return "파일 저장 완료: " + target + " (" + code.length() + " chars)";
        } catch (PathNotAllowedException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "파일 저장 실패: " + e.getMessage();
        }
    }

    /**
     * 이미지 등 텍스트로 표현할 수 없는 바이너리 자산(로고 등)을 저장한다.
     * 경로 검증(allowlist/Path Traversal 방어)은 saveGeneratedCode와 동일하게 적용된다.
     */
    public String saveGeneratedBinary(String filePath, byte[] content) {
        try {
            Path target = resolveAndValidateTarget(filePath);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return "파일 저장 완료: " + target + " (" + content.length + " bytes)";
        } catch (PathNotAllowedException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "파일 저장 실패: " + e.getMessage();
        }
    }

    /**
     * 생성기 관리 대상 파일을 안전하게 삭제한다.
     * 저장 API와 동일한 allowlist 및 Path Traversal 검증을 적용한다.
     */
    public String deleteGeneratedFile(String filePath) {
        try {
            Path target = resolveAndValidateTarget(filePath);
            if (Files.deleteIfExists(target)) {
                return "파일 삭제 완료: " + target;
            }
            return "파일 없음: " + target;
        } catch (PathNotAllowedException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "파일 삭제 실패: " + e.getMessage();
        }
    }

    private Path resolveAndValidateTarget(String filePath) throws PathNotAllowedException {
        Path target = Paths.get(filePath);
        if (target.isAbsolute()) {
            target = target.normalize();
            try {
                validateOutputRoot(target.toString());
            } catch (SecurityException e) {
                throw new PathNotAllowedException("파일 저장 실패: " + e.getMessage());
            }
            return target;
        }
        // 상대경로: base-path 아래로 한정 (Path Traversal 방어)
        Path base = Paths.get(egovProperties.getOutput().getBasePath()).toAbsolutePath().normalize();
        target = base.resolve(filePath).normalize();
        if (!target.startsWith(base)) {
            log.warn("Path Traversal 시도 차단: {}", filePath);
            throw new PathNotAllowedException("파일 저장 실패: 허용 범위 밖 경로입니다.");
        }
        return target;
    }

    /**
     * {@code outputPath}(파이프라인 생성 결과의 루트)가 허용된 위치(basePath, workspace root,
     * allowedPaths) 아래인지 검증한다. {@code resolveAndValidateTarget}의 절대경로 검증과 동일한
     * 규칙이며, {@code CodeServiceGenerationExecutor}가 {@code ApprovedProjectWritePort}로 파일을
     * 쓰기 전에 이 메서드를 먼저 호출해 {@code saveGeneratedCode}가 해오던 것과 같은 보안 경계를
     * 유지한다 — {@code SafePathResolver}는 주어진 root 안에서의 이탈만 막지, 그 root 자체가
     * 허용된 위치인지는 모른다(ARCH-0704 project root registry 부재, WP7 1차 pass 메모 참고).
     */
    public void validateOutputRoot(String outputPath) {
        // 절대경로 allowlist: basePath, 그 부모(workspace root), 명시 allowedPaths 하위만 허용
        // e.g., basePath=~/Desktop/egov-generated -> workspace=~/Desktop 까지 허용
        Path target = Paths.get(outputPath).toAbsolutePath().normalize();
        Path basePath = Paths.get(egovProperties.getOutput().getBasePath()).toAbsolutePath().normalize();
        Path workspaceRoot = basePath.getParent();
        boolean underBase = target.startsWith(basePath);
        boolean underWorkspace = workspaceRoot != null && target.startsWith(workspaceRoot);
        boolean underAllowedPath = egovProperties.getOutput().getAllowedPaths().stream()
                .map(path -> Paths.get(path).toAbsolutePath().normalize())
                .anyMatch(target::startsWith);
        if (!underBase && !underWorkspace && !underAllowedPath) {
            log.warn("허용 범위 밖 절대경로 차단: {}", outputPath);
            throw new SecurityException(
                    "허용 범위 밖 경로입니다 (egov.output.base-path, workspace 또는 allowed-paths 하위만 허용).");
        }
        // ARCH-0704: 정적 allowlist를 통과한 경로는 project root registry에도 지연 등록
        // (lazy registration)한다 — 이미 이 allowlist로 검증된 위치라 새로 위험을 추가하지
        // 않으면서, ApprovedProjectWritePort.apply()가 항상 요구하는 registry 등록을
        // 호출자가 별도 조치 없이도 자연스럽게 채워준다.
        projectRootRegistryPort.register(
                pathResolver.canonicalKey(target), "CODE_SERVICE_LEGACY_ALLOWLIST", "LEGACY_ALLOWLIST");
    }

    private static final class PathNotAllowedException extends Exception {
        PathNotAllowedException(String message) {
            super(message);
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
