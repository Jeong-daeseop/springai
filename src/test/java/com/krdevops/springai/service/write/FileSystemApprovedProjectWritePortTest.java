package com.krdevops.springai.service.write;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WP7/ARCH-0703/0706/0710/0711/0712/0720: {@code ThymeleafProjectWorkflowService.apply()}가
 * 검증해온 staging→backup→atomic replace→rollback 의미를 일반화한 공용 Port를 검증한다.
 */
class FileSystemApprovedProjectWritePortTest {

    private OperationHashFactory hashFactory;
    private FileSystemApprovedProjectWritePort port;

    @BeforeEach
    void setUp() {
        hashFactory = new OperationHashFactory(new ObjectMapper());
        port = new FileSystemApprovedProjectWritePort(new SafePathResolver(), hashFactory);
    }

    @Test
    void appliesNewAndModifiedFilesWhenNoDrift(@TempDir Path root) throws Exception {
        Path existing = root.resolve("a.html");
        Files.writeString(existing, "original");
        String beforeHash = hash("original");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(
                        new ProjectChangeSet.FileChange("a.html", beforeHash, "generated-a", null),
                        new ProjectChangeSet.FileChange("sub/b.html", null, "generated-b", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(outcome.appliedPaths()).containsExactlyInAnyOrder("a.html", "sub/b.html");
        assertThat(existing).hasContent("generated-a");
        assertThat(root.resolve("sub/b.html")).hasContent("generated-b");
    }

    @Test
    void appliedOutcomeExposesBackupPathContainingOriginalContentForPostSuccessRecovery(
            @TempDir Path root) throws Exception {
        Path existing = root.resolve("a.html");
        Files.writeString(existing, "original");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", hash("original"), "generated-a", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(outcome.backupPath()).isNotBlank();
        assertThat(Path.of(outcome.backupPath()).resolve("a.html")).hasContent("original");
    }

    @Test
    void detectsConflictWhenBeforeHashMismatchesAndWritesNothing(@TempDir Path root) throws Exception {
        Path existing = root.resolve("a.html");
        Files.writeString(existing, "changed-after-preview");
        Path untouched = root.resolve("untouched.html");
        Files.writeString(untouched, "keep-me");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(
                        new ProjectChangeSet.FileChange("a.html", hash("stale-preview-content"), "generated-a", null),
                        new ProjectChangeSet.FileChange("untouched.html", hash("keep-me"), "generated-untouched", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.CONFLICT);
        assertThat(outcome.conflictingPaths()).containsExactly("a.html");
        assertThat(existing).hasContent("changed-after-preview");
        assertThat(untouched).hasContent("keep-me");
    }

    @Test
    void rollsBackAppliedFileWhenLaterFileFailsToApply(@TempDir Path root) throws Exception {
        Path first = root.resolve("a.html");
        Files.writeString(first, "original");
        // "blocked"를 파일로 만들어 blocked/b.html의 부모 디렉터리 생성이 실패하게 한다.
        Files.writeString(root.resolve("blocked"), "parent-is-a-file");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(
                        new ProjectChangeSet.FileChange("a.html", hash("original"), "generated-a", null),
                        new ProjectChangeSet.FileChange("blocked/b.html", null, "generated-b", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.ROLLED_BACK);
        assertThat(outcome.failureDetail()).isNotBlank();
        assertThat(first).hasContent("original");
    }

    @Test
    void rolledBackOutcomeExposesBackupPathForForensicRecovery(@TempDir Path root) throws Exception {
        Path first = root.resolve("a.html");
        Files.writeString(first, "original");
        Files.writeString(root.resolve("blocked"), "parent-is-a-file");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(
                        new ProjectChangeSet.FileChange("a.html", hash("original"), "generated-a", null),
                        new ProjectChangeSet.FileChange("blocked/b.html", null, "generated-b", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.ROLLED_BACK);
        assertThat(outcome.backupPath()).isNotBlank();
        assertThat(Path.of(outcome.backupPath()).resolve("a.html")).hasContent("original");
    }

    @Test
    void deletesFileWhenNoDrift(@TempDir Path root) throws Exception {
        Path target = root.resolve("obsolete.html");
        Files.writeString(target, "gone-soon");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1", List.of(),
                List.of(new ProjectChangeSet.FileDeletion("obsolete.html", hash("gone-soon"))),
                ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(outcome.appliedPaths()).containsExactly("obsolete.html");
        assertThat(target).doesNotExist();
    }

    @Test
    void detectsConflictForDeletionDriftAndKeepsFile(@TempDir Path root) throws Exception {
        Path target = root.resolve("obsolete.html");
        Files.writeString(target, "changed-after-preview");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1", List.of(),
                List.of(new ProjectChangeSet.FileDeletion("obsolete.html", hash("stale-preview-content"))),
                ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.CONFLICT);
        assertThat(target).exists();
    }

    @Test
    void bestEffortCompatibilityPolicySkipsDriftCheckAndOverwrites(@TempDir Path root) throws Exception {
        Path existing = root.resolve("a.html");
        Files.writeString(existing, "changed-after-preview");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange(
                        "a.html", hash("stale-preview-content"), "generated-a", null)),
                List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(existing).hasContent("generated-a");
    }

    @Test
    void bestEffortCompatibilityCreatesProjectRootWhenMissing(@TempDir Path parent) {
        Path freshRoot = parent.resolve("brand-new-project");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                freshRoot.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("EmployerVO.java", null, "class EmployerVO {}", null)),
                List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(freshRoot.resolve("EmployerVO.java")).hasContent("class EmployerVO {}");
    }

    @Test
    void bestEffortCompatibilityAppliesEachFileIndependentlyContinuingPastFailure(
            @TempDir Path root) throws Exception {
        Path good = root.resolve("a.html");
        // "blocked"를 파일로 만들어 blocked/b.html의 부모 디렉터리 생성이 실패하게 한다 —
        // ATOMIC_APPROVED의 rollback 테스트와 같은 기법이지만 여기서는 a.html이 롤백되지 않아야 한다.
        Files.writeString(root.resolve("blocked"), "parent-is-a-file");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(
                        new ProjectChangeSet.FileChange("a.html", null, "generated-a", null),
                        new ProjectChangeSet.FileChange("blocked/b.html", null, "generated-b", null)),
                List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.PARTIALLY_APPLIED);
        assertThat(outcome.appliedPaths()).containsExactly("a.html");
        assertThat(good).hasContent("generated-a");
        assertThat(outcome.failureMessages()).containsOnlyKeys("blocked/b.html");
        assertThat(outcome.failureMessages().get("blocked/b.html")).isNotBlank();
    }

    @Test
    void rejectsChangeSetPathEscapingProjectRootWithoutWritingAnything(@TempDir Path root) throws Exception {
        Path decoy = root.resolve("decoy.html");
        Files.writeString(decoy, "keep-me");

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(
                        new ProjectChangeSet.FileChange("decoy.html", hash("keep-me"), "generated", null),
                        new ProjectChangeSet.FileChange("../escape.html", null, "malicious", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        assertThatThrownBy(() -> port.apply(changeSet)).isInstanceOf(SecurityException.class);
        assertThat(decoy).hasContent("keep-me");
        assertThat(root.getParent().resolve("escape.html")).doesNotExist();
    }

    // ── ARCH-0717/0718: 바이너리 콘텐츠(로고 등 이미지 자산) ────────────────────────

    @Test
    void atomicApprovedWritesBinaryContentAsRawBytesNotUtf8Text(@TempDir Path root) {
        // 유효한 UTF-8 텍스트가 아닌 바이트(PNG 매직 넘버 포함) — writeString 경로로 잘못 타면
        // 깨지거나 예외가 난다.
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, (byte) 0xFF, (byte) 0xFE};

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("logo.png", null, null, null, pngBytes)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(readBytes(root.resolve("logo.png"))).isEqualTo(pngBytes);
    }

    @Test
    void bestEffortCompatibilityWritesBinaryContentAsRawBytes(@TempDir Path root) {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, (byte) 0xFF, (byte) 0xFE};

        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("logo.png", null, null, null, pngBytes)),
                List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(readBytes(root.resolve("logo.png"))).isEqualTo(pngBytes);
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String hash(String content) {
        return hashFactory.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    // ── ARCH-0713/M3-G5: rollback 실패 보고 + afterHash 검증 ────────────────────

    /**
     * {@code rollback()}을 {@code apply()} 전체 흐름을 통해 간접 유발하지 않고 직접 호출한다 —
     * "정방향 write가 성공한 뒤 같은 경로의 복구만 실패"하는 상황은 이 파일시스템에서 move/copy
     * 둘 다 디렉터리 쓰기 권한 하나로만 게이트되는 걸 실측 확인했고(둘 다 성공 or 둘 다 실패),
     * 따라서 apply() 전체를 거치는 통합 시나리오로는 재현 불가능하다 — rollback()의 로직 변경
     * (예외를 삼키지 않고 수집)만 직접 검증한다.
     */
    @Test
    void rollbackReturnsFailureMessagesWhenRestoreItselfFails(@TempDir Path root) throws Exception {
        Path backup = Files.createTempDirectory(root, ".rb-test-backup-");
        Files.writeString(backup.resolve("a.html"), "original");
        Path target = root.resolve("a.html");
        Files.writeString(target, "moved-in-content");
        boolean readOnlySet = root.toFile().setWritable(false);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                readOnlySet, "이 실행 환경(예: root)에서는 디렉터리 쓰기 금지가 걸리지 않아 이 테스트를 건너뛴다.");
        try {
            var failures = port.rollback(root, backup, List.of(target), java.util.Set.of());

            assertThat(failures).containsKey("a.html");
        } finally {
            root.toFile().setWritable(true);
        }
    }

    @Test
    void rollbackSucceedsWithoutFailuresWhenBackupIsRestorable(@TempDir Path root) throws Exception {
        Path backup = Files.createTempDirectory(root, ".rb-test-backup-");
        Files.writeString(backup.resolve("a.html"), "original");
        Path target = root.resolve("a.html");
        Files.writeString(target, "moved-in-content");

        var failures = port.rollback(root, backup, List.of(target), java.util.Set.of());

        assertThat(failures).isEmpty();
        assertThat(target).hasContent("original");
    }

    @Test
    void atomicApprovedRollsBackNewFileWhenWrittenContentDoesNotMatchAfterHash(@TempDir Path root) {
        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", null, "generated-a", "not-the-real-hash")),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.ROLLED_BACK);
        assertThat(root.resolve("a.html")).doesNotExist();
    }

    @Test
    void atomicApprovedSucceedsWhenAfterHashNullAndContentWrittenCorrectly(@TempDir Path root) {
        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", null, "generated-a", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(root.resolve("a.html")).hasContent("generated-a");
    }

    @Test
    void bestEffortCompatibilityFailsOnlyFileWithHashMismatchAndKeepsOthers(@TempDir Path root) {
        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(
                        new ProjectChangeSet.FileChange("good.html", null, "good-content", null),
                        new ProjectChangeSet.FileChange("bad.html", null, "bad-content", "not-the-real-hash")),
                List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.PARTIALLY_APPLIED);
        assertThat(outcome.appliedPaths()).containsExactly("good.html");
        assertThat(outcome.failureMessages()).containsOnlyKeys("bad.html");
        assertThat(root.resolve("good.html")).hasContent("good-content");
    }

    // ── ARCH-0704: project root registry ────────────────────────────────────

    @Test
    void rejectsUnregisteredRootEvenWhenChangeSetIsOtherwiseValidAtomic(@TempDir Path root) {
        var registryPort = new InMemoryProjectRootRegistryPort();
        var registryEnforcingPort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), hashFactory, registryPort, new com.krdevops.springai.service.operation.NoopOperationLockPort());
        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", null, "generated-a", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        assertThatThrownBy(() -> registryEnforcingPort.apply(changeSet))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PROJECT_ROOT_NOT_REGISTERED");
        assertThat(root.resolve("a.html")).doesNotExist();
    }

    @Test
    void rejectsUnregisteredRootForBestEffortToo(@TempDir Path root) {
        var registryPort = new InMemoryProjectRootRegistryPort();
        var registryEnforcingPort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), hashFactory, registryPort, new com.krdevops.springai.service.operation.NoopOperationLockPort());
        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", null, "generated-a", null)),
                List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);

        assertThatThrownBy(() -> registryEnforcingPort.apply(changeSet))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PROJECT_ROOT_NOT_REGISTERED");
        assertThat(root.resolve("a.html")).doesNotExist();
    }

    @Test
    void appliesNormallyWhenRootIsRegistered(@TempDir Path root) {
        var registryPort = new InMemoryProjectRootRegistryPort();
        var resolver = new SafePathResolver();
        registryPort.register(resolver.canonicalKey(root), "tester", "TEST");
        var registryEnforcingPort = new FileSystemApprovedProjectWritePort(
                resolver, hashFactory, registryPort, new com.krdevops.springai.service.operation.NoopOperationLockPort());
        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", null, "generated-a", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = registryEnforcingPort.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
        assertThat(root.resolve("a.html")).hasContent("generated-a");
    }

    @Test
    void twoArgCompatConstructorAllowsAnyRootForExistingCallers(@TempDir Path root) {
        // 2-arg 생성자(기존 10곳 이상의 호출자/테스트가 그대로 씀)는 AllowAllProjectRootRegistryPort로
        // 기본값을 채워 registry 검증을 건너뛴다 — 하위호환 확인.
        ProjectChangeSet changeSet = new ProjectChangeSet(
                root.toString(), "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", null, "generated-a", null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);

        ApplyOutcome outcome = port.apply(changeSet);

        assertThat(outcome.status()).isEqualTo(ApplyOutcome.Status.APPLIED);
    }
}
