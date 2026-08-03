package com.krdevops.springai.service.artifact;

import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.artifact.StagedArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ARCH-0504/0505/0512/0518: stage→commit atomic move, content-addressed 멱등 저장,
 * traversal/symlink/size-limit 방어를 검증한다.
 */
class FilesystemArtifactStoreTest {

    @TempDir
    Path tempRoot;

    private FilesystemArtifactStore store;
    private ArtifactStoreProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ArtifactStoreProperties();
        properties.setRootPath(tempRoot);
        properties.setMaxArtifactSizeBytes(1024);
        properties.setAllowedMediaTypes(List.of("text/plain", "application/json"));
        store = new FilesystemArtifactStore(properties);
    }

    @Test
    void stageThenCommit_writesContentAddressedFileAndRemovesStaging() throws IOException {
        byte[] content = "hello artifact".getBytes();
        StagedArtifact staged = store.stage(content, "text/plain");

        assertThat(Files.exists(staged.stagingPath())).isTrue();
        assertThat(staged.contentHash()).isEqualTo(ContentHashes.sha256Hex(content));

        String storageUri = store.commit(staged);

        assertThat(Files.exists(staged.stagingPath())).isFalse();
        assertThat(store.exists(staged.contentHash())).isTrue();
        assertThat(storageUri).isEqualTo(
                staged.contentHash().substring(0, 2) + "/" + staged.contentHash().substring(2, 4) + "/" + staged.contentHash());

        Optional<byte[]> read = store.read(staged.contentHash());
        assertThat(read).isPresent();
        assertThat(read.get()).isEqualTo(content);
    }

    @Test
    void commitTwiceWithSameContent_isIdempotentAndDeduplicates() throws IOException {
        byte[] content = "duplicate content".getBytes();

        StagedArtifact first = store.stage(content, "text/plain");
        String uri1 = store.commit(first);

        StagedArtifact second = store.stage(content, "text/plain");
        assertThat(Files.exists(second.stagingPath())).isTrue();
        String uri2 = store.commit(second);

        assertThat(uri1).isEqualTo(uri2);
        assertThat(Files.exists(second.stagingPath())).isFalse();
    }

    @Test
    void stage_rejectsContentOverSizeLimit() {
        byte[] tooLarge = new byte[2048];

        assertThatThrownBy(() -> store.stage(tooLarge, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용 한도");
    }

    @Test
    void stage_rejectsDisallowedMediaType() {
        assertThatThrownBy(() -> store.stage("x".getBytes(), "application/x-msdownload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않은");
    }

    @Test
    void read_rejectsMalformedContentHashInsteadOfResolvingPath() {
        assertThatThrownBy(() -> store.read("../../../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists("not-a-valid-hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void read_rejectsSymlinkAtFinalPathInsteadOfFollowingIt() throws IOException {
        byte[] content = "symlink target".getBytes();
        StagedArtifact staged = store.stage(content, "text/plain");
        store.commit(staged);

        // 별도 hash 경로를 symlink로 만들어, symlink를 절대 따라가지 않고 fail-closed로 거부하는지 확인한다.
        String fakeHash = "a".repeat(64);
        Path fanoutDir = tempRoot.resolve(fakeHash.substring(0, 2)).resolve(fakeHash.substring(2, 4));
        Files.createDirectories(fanoutDir);
        Path symlinkPath = fanoutDir.resolve(fakeHash);
        Path realFile = tempRoot.resolve(staged.contentHash().substring(0, 2))
                .resolve(staged.contentHash().substring(2, 4)).resolve(staged.contentHash());
        Files.createSymbolicLink(symlinkPath, realFile);

        assertThatThrownBy(() -> store.read(fakeHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists(fakeHash)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quarantine_movesFileOutOfActiveTree() throws IOException {
        byte[] content = "quarantine me".getBytes();
        StagedArtifact staged = store.stage(content, "text/plain");
        store.commit(staged);

        store.quarantine(staged.contentHash());

        assertThat(store.exists(staged.contentHash())).isFalse();
        assertThat(Files.exists(tempRoot.resolve(".quarantine").resolve(staged.contentHash()))).isTrue();
    }

    @Test
    void commit_survivesTargetDirectoryUnwritable_stagedFileNotLost() throws IOException {
        byte[] content = "atomic move failure".getBytes();
        StagedArtifact staged = store.stage(content, "text/plain");

        Path fanoutParent = tempRoot.resolve(staged.contentHash().substring(0, 2));
        Files.createDirectories(fanoutParent);
        assertThat(fanoutParent.toFile().setWritable(false)).isTrue();
        try {
            assertThatIOException().isThrownBy(() -> store.commit(staged));
            assertThat(Files.exists(staged.stagingPath())).isTrue();
        } finally {
            fanoutParent.toFile().setWritable(true);
        }
    }
}
