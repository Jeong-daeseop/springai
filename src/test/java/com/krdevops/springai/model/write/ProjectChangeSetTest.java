package com.krdevops.springai.model.write;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectChangeSetTest {

    @Test
    void rejectsBlankProjectRootRef() {
        assertThatThrownBy(() -> new ProjectChangeSet(
                " ", "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", "h1", "content", "h2")),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectRootRef");
    }

    @Test
    void rejectsEmptyChangesAndDeletions() {
        assertThatThrownBy(() -> new ProjectChangeSet(
                "/tmp/project", "rev-1", List.of(), List.of(), ProjectWritePolicy.ATOMIC_APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generatedFiles");
    }

    @Test
    void defaultsPolicyToAtomicApprovedWhenNull() {
        ProjectChangeSet changeSet = new ProjectChangeSet(
                "/tmp/project", "rev-1",
                List.of(new ProjectChangeSet.FileChange("a.html", null, "content", "h2")),
                null, null);

        assertThat(changeSet.policy()).isEqualTo(ProjectWritePolicy.ATOMIC_APPROVED);
        assertThat(changeSet.deletions()).isEmpty();
        assertThat(changeSet.generatedFiles().get(0).beforeHash()).isEqualTo("MISSING");
    }

    @Test
    void fileChangeRejectsBlankPath() {
        assertThatThrownBy(() -> new ProjectChangeSet.FileChange(" ", "h1", "content", "h2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    // ── ARCH-0717/0718: 바이너리 콘텐츠(로고 등 이미지 자산) 지원 ─────────────────

    @Test
    void legacyFourArgConstructorIsNotBinary() {
        var change = new ProjectChangeSet.FileChange("a.html", "h1", "content", "h2");

        assertThat(change.isBinary()).isFalse();
        assertThat(change.binaryContent()).isNull();
        assertThat(change.content()).isEqualTo("content");
    }

    @Test
    void fiveArgConstructorWithBinaryContentIsBinaryAndDefaultsStringContentToEmpty() {
        byte[] bytes = {(byte) 0x89, 0x50, 0x4E, 0x47};
        var change = new ProjectChangeSet.FileChange("logo.png", null, null, null, bytes);

        assertThat(change.isBinary()).isTrue();
        assertThat(change.binaryContent()).isEqualTo(bytes);
        assertThat(change.content()).isEmpty();
        assertThat(change.beforeHash()).isEqualTo("MISSING");
    }
}
