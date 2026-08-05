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

    private String hash(String content) {
        return hashFactory.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }
}
