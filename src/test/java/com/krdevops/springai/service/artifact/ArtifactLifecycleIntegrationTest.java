package com.krdevops.springai.service.artifact;

import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.mapper.ArtifactCatalogRepository;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ARCH-0505/0506/0516/0519: ArtifactService(store+catalog 조합)와 ArtifactReconciler를
 * 실제 MySQL + 실제 filesystem으로 end-to-end 검증한다.
 * - ingest 재호출 시 content-addressed 멱등 재사용(같은 artifactId)
 * - Operation↔Artifact link 저장·조회
 * - catalog 저장이 실패해도(예: DB 예외) staging 디렉토리에는 아무것도 남지 않고,
 *   이미 commit된 파일은 reconciler가 orphan으로 정확히 탐지한다.
 */
class ArtifactLifecycleIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ArtifactCatalogRepository catalog = new ArtifactCatalogRepository(jdbcTemplate);

    @TempDir
    Path tempRoot;

    private ArtifactStoreProperties properties;
    private FilesystemArtifactStore store;
    private ArtifactService artifactService;
    private ArtifactReconciler reconciler;

    private String artifactId;
    private String operationId;

    @BeforeEach
    void setUp() {
        properties = new ArtifactStoreProperties();
        properties.setRootPath(tempRoot);
        store = new FilesystemArtifactStore(properties);
        artifactService = new ArtifactService(store, catalog);
        reconciler = new ArtifactReconciler(properties, catalog, store);
    }

    @AfterEach
    void cleanUp() {
        if (artifactId != null) {
            jdbcTemplate.update("DELETE FROM AI_ARTIFACT_LINK WHERE ARTIFACT_ID = ?", artifactId);
            jdbcTemplate.update("DELETE FROM AI_ARTIFACT WHERE ARTIFACT_ID = ?", artifactId);
        }
    }

    @Test
    void ingestSameContentTwice_reusesSameArtifactAndDoesNotDuplicateCatalogRow() {
        byte[] content = ("lifecycle-" + UUID.randomUUID()).getBytes();

        Artifact first = artifactService.ingest(content, "text/plain", "THYMELEAF_PREVIEW", "rev-1");
        artifactId = first.artifactId();
        Artifact second = artifactService.ingest(content, "text/plain", "THYMELEAF_PREVIEW", "rev-2");

        assertThat(second.artifactId()).isEqualTo(first.artifactId());
        assertThat(catalog.findByContentHash(first.contentHash())).hasValueSatisfying(
                a -> assertThat(a.sourceRevision()).isEqualTo("rev-1"));
    }

    @Test
    void ingestAndLink_linksArtifactToOperationAndIsQueryable() {
        byte[] content = ("linked-" + UUID.randomUUID()).getBytes();
        operationId = "op-" + UUID.randomUUID();

        Artifact artifact = artifactService.ingestAndLink(
                content, "text/html", "THYMELEAF_PREVIEW", "rev-1", operationId, "THYMELEAF_PROJECT_OPERATION");
        artifactId = artifact.artifactId();

        List<Artifact> byOperation = artifactService.findByOperation(operationId);
        assertThat(byOperation).extracting(Artifact::artifactId).containsExactly(artifactId);

        jdbcTemplate.update("DELETE FROM AI_ARTIFACT_LINK WHERE OPERATION_ID = ?", operationId);
    }

    @Test
    void catalogSaveFailure_leavesNoStagingLeftoverAndCommittedFileBecomesDetectableOrphan() throws IOException {
        ArtifactCatalogPort failingCatalog = new FailingCatalogDelegate(catalog);
        ArtifactService serviceWithFailingCatalog = new ArtifactService(store, failingCatalog);
        byte[] content = ("orphan-on-db-failure-" + UUID.randomUUID()).getBytes();

        assertThatThrownBy(() -> serviceWithFailingCatalog.ingest(content, "text/plain", "THYMELEAF_PREVIEW", "rev-1"))
                .isInstanceOf(RuntimeException.class);

        Path stagingDir = tempRoot.resolve(".staging");
        try (var stream = Files.exists(stagingDir) ? Files.list(stagingDir) : java.util.stream.Stream.<Path>empty()) {
            assertThat(stream.toList()).isEmpty();
        }

        ArtifactReconciliationReport report = reconciler.reconcile(true);
        assertThat(report.orphanContentHashes()).isNotEmpty();
    }

    private static final class FailingCatalogDelegate implements ArtifactCatalogPort {
        private final ArtifactCatalogPort delegate;

        FailingCatalogDelegate(ArtifactCatalogPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public Artifact save(Artifact artifact) {
            throw new RuntimeException("simulated DB outage during catalog save");
        }

        @Override
        public java.util.Optional<Artifact> findByContentHash(String contentHash) {
            return delegate.findByContentHash(contentHash);
        }

        @Override
        public java.util.Optional<Artifact> findById(String artifactId) {
            return delegate.findById(artifactId);
        }

        @Override
        public List<Artifact> findAll() {
            return delegate.findAll();
        }

        @Override
        public void linkToOperation(String operationId, String operationType, String artifactId) {
            delegate.linkToOperation(operationId, operationType, artifactId);
        }

        @Override
        public List<Artifact> findByOperation(String operationId) {
            return delegate.findByOperation(operationId);
        }

        @Override
        public void updateStatus(String artifactId, ArtifactStatus status) {
            delegate.updateStatus(artifactId, status);
        }
    }
}
