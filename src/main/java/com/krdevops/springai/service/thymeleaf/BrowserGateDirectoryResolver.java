package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.service.write.SafePathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * WP8 3차 pass: baseline 승인({@link BaselineApprovalService})과 재검증
 * ({@code ThymeleafProjectWorkflowService.revalidate})이 브라우저 Gate 디렉터리를 같은 계산식으로
 * 도출하게 만드는 단일 지점. 양쪽이 다른 경로를 쓰면 승인한 baseline을 재검증이 찾지 못한다.
 *
 * <p>대상 프로젝트 경로를 그대로 노출하지 않고 {@link SafePathResolver#canonicalKey(Path)}의
 * SHA-256으로 격리 키를 만든다 — WP7이 registry/lock key에 canonicalKey를 쓰는 것과 같은 이유로
 * macOS {@code /tmp}→{@code /private/tmp} 같은 symlink 차이가 같은 프로젝트를 다른 키로 갈라놓지
 * 않게 한다.
 */
@Component
public class BrowserGateDirectoryResolver {

    private final SafePathResolver pathResolver;
    private final Path baselineRoot;

    @Autowired
    public BrowserGateDirectoryResolver(
            SafePathResolver pathResolver,
            @Value("${app.thymeleaf.browser-gate.baseline-root:${java.io.tmpdir}/springai-thymeleaf-baselines}")
            String baselineRoot) {
        this(pathResolver, Path.of(baselineRoot));
    }

    public BrowserGateDirectoryResolver(SafePathResolver pathResolver, Path baselineRoot) {
        this.pathResolver = pathResolver;
        this.baselineRoot = baselineRoot.toAbsolutePath().normalize();
    }

    /** 프로젝트 단위로 공유하는 승인 baseline 보관 위치(operation과 무관하게 누적된다). */
    public Path baselineDirectory(Path projectRoot) {
        return projectDirectory(projectRoot).resolve("baselines");
    }

    /** operation별 렌더 scratch 영역(스크린샷·diff·runner report). */
    public Path artifactDirectory(Path projectRoot, String operationId) {
        if (operationId == null || !operationId.matches("[A-Za-z0-9._-]{1,120}")) {
            throw new SecurityException("BROWSER_GATE_OPERATION_ID_INVALID: " + operationId);
        }
        return projectDirectory(projectRoot).resolve("operations").resolve(operationId);
    }

    /** 승인 메타데이터에 남기는 프로젝트 식별자. 실제 경로를 노출하지 않는다. */
    public String projectKey(Path projectRoot) {
        return sha256Hex(pathResolver.canonicalKey(projectRoot));
    }

    private Path projectDirectory(Path projectRoot) {
        return baselineRoot.resolve(projectKey(projectRoot));
    }

    private String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
