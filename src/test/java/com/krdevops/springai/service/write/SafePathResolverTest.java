package com.krdevops.springai.service.write;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WP7/ARCH-0705: {@code ThymeleafProjectWorkflowService}의 private
 * resolveTarget/containsSymbolicLink/resolveWithin/realDirectory 로직을 일반화해 옮긴 공용
 * 클래스를 검증한다.
 */
class SafePathResolverTest {

    private final SafePathResolver resolver = new SafePathResolver();

    @Test
    void resolveTargetReturnsNormalizedPathWithinRoot(@TempDir Path root) {
        Path resolved = resolver.resolveTarget(root, "sub/dir/File.java");

        assertThat(resolved).isEqualTo(root.resolve("sub/dir/File.java").normalize());
    }

    @Test
    void resolveTargetRejectsAbsolutePath(@TempDir Path root) {
        assertThatThrownBy(() -> resolver.resolveTarget(root, "/etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PROJECT_TARGET_PATH_INVALID");
    }

    @Test
    void resolveTargetRejectsPathEscapingRoot(@TempDir Path root) {
        assertThatThrownBy(() -> resolver.resolveTarget(root, "../outside.txt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PROJECT_TARGET_PATH_ESCAPE");
    }

    @Test
    void resolveTargetRejectsSymlinkComponent(@TempDir Path root) throws IOException {
        Path outside = Files.createTempDirectory("safe-path-resolver-outside-");
        Path link = root.resolve("linked");
        Files.createSymbolicLink(link, outside);

        assertThatThrownBy(() -> resolver.resolveTarget(root, "linked/File.java"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PROJECT_TARGET_PATH_ESCAPE");
    }

    @Test
    void resolveWithinRejectsPathEscapingGivenRoot(@TempDir Path root) {
        assertThatThrownBy(() -> resolver.resolveWithin(root, "../escape.txt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PROJECT_STAGE_PATH_ESCAPE");
    }

    @Test
    void realDirectoryRejectsNonDirectory(@TempDir Path root) throws IOException {
        Path file = root.resolve("file.txt");
        Files.writeString(file, "x");

        assertThatThrownBy(() -> resolver.realDirectory(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROJECT_ROOT_INVALID");
    }

    @Test
    void realDirectoryRejectsMissingPath(@TempDir Path root) {
        Path missing = root.resolve("does-not-exist");

        assertThatThrownBy(() -> resolver.realDirectory(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROJECT_ROOT_NOT_FOUND");
    }

    // ── ARCH-0704: registry/lock key canonicalization ───────────────────────

    @Test
    void canonicalKeyOfExistingPathReturnsRealPath(@TempDir Path root) throws IOException {
        Path existing = Files.createDirectories(root.resolve("sub"));

        String key = resolver.canonicalKey(existing);

        assertThat(key).isEqualTo(existing.toRealPath().toString());
    }

    @Test
    void canonicalKeyOfMissingPathReturnsNormalizedAbsolutePath(@TempDir Path root) {
        Path missing = root.resolve("not-yet-created");

        String key = resolver.canonicalKey(missing);

        assertThat(key).isEqualTo(missing.toAbsolutePath().normalize().toString());
    }

    @Test
    void canonicalKeyResolvesSymlinkToSameKeyAsRealTarget(@TempDir Path root) throws IOException {
        Path realTarget = Files.createDirectories(root.resolve("real-target"));
        Path link = root.resolve("linked-alias");
        Files.createSymbolicLink(link, realTarget);

        assertThat(resolver.canonicalKey(link)).isEqualTo(resolver.canonicalKey(realTarget));
    }
}
