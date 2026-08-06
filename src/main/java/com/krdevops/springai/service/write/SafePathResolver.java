package com.krdevops.springai.service.write;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * WP7/ARCH-0705: 프로젝트 루트 하위 경로를 안전하게 확인하는 공용 로직. 원래
 * {@code ThymeleafProjectWorkflowService}의 private 메서드(resolveTarget/containsSymbolicLink/
 * resolveWithin/realDirectory)였던 것을 일반화해 옮겼다 — path traversal, symlink escape, 절대
 * 경로 주입을 막는 검증은 Thymeleaf Apply 경로에서 이미 검증된 그대로다. {@link ApprovedProjectWritePort}
 * 구현체와 {@code ThymeleafProjectWorkflowService}가 이 클래스를 공유한다.
 */
@Component
public class SafePathResolver {

    /** {@code root} 아래 실제 파일시스템 대상 경로. 절대 경로, root 이탈, symlink 경유를 거부한다. */
    public Path resolveTarget(Path root, String relative) {
        if (relative == null || relative.isBlank() || Path.of(relative).isAbsolute()) {
            throw new SecurityException("PROJECT_TARGET_PATH_INVALID: " + relative);
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || containsSymbolicLink(root, target)) {
            throw new SecurityException("PROJECT_TARGET_PATH_ESCAPE: " + relative);
        }
        return target;
    }

    /** {@code root}부터 {@code target}까지 각 경로 구성요소에 symlink가 있는지 확인한다. */
    public boolean containsSymbolicLink(Path root, Path target) {
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
        }
        return false;
    }

    /** staging/backup처럼 임시로 만든 디렉터리 안에서만 움직이는지 확인한다(대상 root와는 별개 검증). */
    public Path resolveWithin(Path root, String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("PROJECT_STAGE_PATH_ESCAPE: " + relative);
        }
        return resolved;
    }

    /**
     * WP7 6차 pass/ARCH-0704: registry·lock key로 쓸 정규화된 문자열을 만든다. 경로가 실제로
     * 존재하면 {@code toRealPath()}(symlink까지 해소한 canonical 경로)를, 아직 없으면(신규
     * 프로젝트 생성 이전 시점) {@code toAbsolutePath().normalize()}를 쓴다. macOS에서 {@code /tmp}가
     * {@code /private/tmp}로 symlink되는 것처럼, 등록 시점과 검사 시점에 서로 다른
     * canonicalization 방식을 쓰면 같은 논리적 경로가 다른 키로 취급될 수 있다 — 이 메서드
     * 하나로만 registry/lock key를 만들어 그 불일치를 막는다.
     */
    public String canonicalKey(Path path) {
        try {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                return path.toRealPath().toString();
            }
        } catch (IOException ignored) {
            // toRealPath 실패(권한 등) 시 아래 fallback으로 넘어간다.
        }
        return path.toAbsolutePath().normalize().toString();
    }

    /** symlink가 아닌 실제 디렉터리인지 확인하고 정규화된 real path를 반환한다. */
    public Path realDirectory(Path root) {
        try {
            Path real = root.toRealPath();
            if (!Files.isDirectory(real) || Files.isSymbolicLink(root)) {
                throw new IllegalArgumentException("PROJECT_ROOT_INVALID: " + root);
            }
            return real;
        } catch (IOException exception) {
            throw new IllegalArgumentException("PROJECT_ROOT_NOT_FOUND: " + root, exception);
        }
    }
}
