package com.krdevops.springai.service.artifact;

import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.artifact.StagedArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ARCH-0509/0511/0519: catalog와 filesystem의 orphan/missing 불일치를 탐지하고 dry-run/execute를 분리 검증한다. */
class ArtifactReconcilerTest {

    @TempDir
    Path tempRoot;

    private FilesystemArtifactStore store;
    private InMemoryArtifactCatalog catalog;
    private ArtifactReconciler reconciler;

    @BeforeEach
    void setUp() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootPath(tempRoot);
        store = new FilesystemArtifactStore(properties);
        catalog = new InMemoryArtifactCatalog();
        reconciler = new ArtifactReconciler(properties, catalog, store);
    }

    @Test
    void reconcile_detectsOrphanFileWithoutCatalogRow() throws IOException {
        StagedArtifact staged = store.stage("orphan content".getBytes(), "text/plain");
        store.commit(staged);

        ArtifactReconciliationReport report = reconciler.reconcile(true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.orphanContentHashes()).containsExactly(staged.contentHash());
        assertThat(report.missingArtifacts()).isEmpty();
        assertThat(store.exists(staged.contentHash())).isTrue();
    }

    @Test
    void reconcile_detectsMissingFileWithCatalogRowButNoFile() {
        String contentHash = ContentHashes.sha256Hex(("missing-" + UUID.randomUUID()).getBytes());
        Artifact artifact = new Artifact("art-" + UUID.randomUUID(), "THYMELEAF_PREVIEW", "text/html", 10L,
                contentHash, "rev-1", "xx/yy/" + contentHash, ArtifactStatus.ACTIVE, Instant.now());
        catalog.save(artifact);

        ArtifactReconciliationReport report = reconciler.reconcile(true);

        assertThat(report.missingArtifacts()).extracting(Artifact::artifactId).containsExactly(artifact.artifactId());
        assertThat(report.orphanContentHashes()).isEmpty();
    }

    @Test
    void reconcile_dryRunDoesNotMutateFilesystemOrCatalog() throws IOException {
        StagedArtifact staged = store.stage("dry run".getBytes(), "text/plain");
        store.commit(staged);

        reconciler.reconcile(true);

        assertThat(store.exists(staged.contentHash())).isTrue();
    }

    @Test
    void reconcile_executeQuarantinesOrphanAndMarksMissingAsQuarantined() throws IOException {
        StagedArtifact staged = store.stage("execute orphan".getBytes(), "text/plain");
        store.commit(staged);

        String missingHash = ContentHashes.sha256Hex(("gone-" + UUID.randomUUID()).getBytes());
        Artifact missingArtifact = new Artifact("art-" + UUID.randomUUID(), "THYMELEAF_PREVIEW", "text/html", 10L,
                missingHash, "rev-1", "xx/yy/" + missingHash, ArtifactStatus.ACTIVE, Instant.now());
        catalog.save(missingArtifact);

        ArtifactReconciliationReport report = reconciler.reconcile(false);

        assertThat(report.dryRun()).isFalse();
        assertThat(report.quarantinedContentHashes()).containsExactly(staged.contentHash());
        assertThat(store.exists(staged.contentHash())).isFalse();
        assertThat(catalog.findById(missingArtifact.artifactId())).get()
                .extracting(Artifact::status).isEqualTo(ArtifactStatus.QUARANTINED);
    }

    @Test
    void reconcile_cleanStateReportsNoOrphanOrMissing() throws IOException {
        StagedArtifact staged = store.stage("clean".getBytes(), "text/plain");
        String uri = store.commit(staged);
        Artifact artifact = new Artifact("art-" + UUID.randomUUID(), "THYMELEAF_PREVIEW", "text/html",
                staged.sizeBytes(), staged.contentHash(), "rev-1", uri, ArtifactStatus.ACTIVE, Instant.now());
        catalog.save(artifact);

        ArtifactReconciliationReport report = reconciler.reconcile(true);

        assertThat(report.isClean()).isTrue();
    }

    /** DB 없이 reconciler 로직만 빠르게 검증하기 위한 in-memory catalog. */
    private static final class InMemoryArtifactCatalog implements ArtifactCatalogPort {
        private final List<Artifact> rows = new ArrayList<>();

        @Override
        public Artifact save(Artifact artifact) {
            rows.add(artifact);
            return artifact;
        }

        @Override
        public Optional<Artifact> findByContentHash(String contentHash) {
            return rows.stream().filter(a -> a.contentHash().equals(contentHash)).findFirst();
        }

        @Override
        public Optional<Artifact> findById(String artifactId) {
            return rows.stream().filter(a -> a.artifactId().equals(artifactId)).findFirst();
        }

        @Override
        public List<Artifact> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public void linkToOperation(String operationId, String operationType, String artifactId) {
            // 이 테스트 스위트에서는 사용하지 않는다.
        }

        @Override
        public List<Artifact> findByOperation(String operationId) {
            return List.of();
        }

        @Override
        public void updateStatus(String artifactId, ArtifactStatus status) {
            for (int i = 0; i < rows.size(); i++) {
                Artifact a = rows.get(i);
                if (a.artifactId().equals(artifactId)) {
                    rows.set(i, new Artifact(a.artifactId(), a.artifactType(), a.mediaType(), a.sizeBytes(),
                            a.contentHash(), a.sourceRevision(), a.storageUri(), status, a.createdAt()));
                }
            }
        }
    }
}
