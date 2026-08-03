package com.krdevops.springai.mapper;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.ContentHashes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-0503/0505/0506/0516: CONTENT_HASH 멱등 저장, Operation↔Artifact link,
 * missing 조회(ARCH-0519 준비)를 실제 MySQL로 검증한다.
 */
class ArtifactCatalogRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ArtifactCatalogRepository repository = new ArtifactCatalogRepository(jdbcTemplate);

    private String artifactId;
    private String operationId;

    @AfterEach
    void cleanUp() {
        if (artifactId != null) {
            jdbcTemplate.update("DELETE FROM AI_ARTIFACT_LINK WHERE ARTIFACT_ID = ?", artifactId);
            jdbcTemplate.update("DELETE FROM AI_ARTIFACT WHERE ARTIFACT_ID = ?", artifactId);
        }
    }

    @Test
    void save_isIdempotentByContentHash() {
        String contentHash = ContentHashes.sha256Hex(("test-" + UUID.randomUUID()).getBytes());
        Artifact first = artifact(contentHash, "art-" + UUID.randomUUID());
        artifactId = first.artifactId();

        Artifact saved1 = repository.save(first);
        Artifact different = artifact(contentHash, "art-" + UUID.randomUUID());
        Artifact saved2 = repository.save(different);

        assertThat(saved1.artifactId()).isEqualTo(saved2.artifactId()).isEqualTo(artifactId);
        assertThat(repository.findAll().stream().filter(a -> a.contentHash().equals(contentHash)).count()).isEqualTo(1);
    }

    @Test
    void findByContentHashAndFindById_returnSavedArtifact() {
        String contentHash = ContentHashes.sha256Hex(("find-" + UUID.randomUUID()).getBytes());
        Artifact saved = repository.save(artifact(contentHash, "art-" + UUID.randomUUID()));
        artifactId = saved.artifactId();

        assertThat(repository.findByContentHash(contentHash)).get()
                .extracting(Artifact::artifactId, Artifact::contentHash, Artifact::storageUri)
                .containsExactly(saved.artifactId(), saved.contentHash(), saved.storageUri());
        assertThat(repository.findById(saved.artifactId())).get()
                .extracting(Artifact::artifactId, Artifact::contentHash, Artifact::storageUri)
                .containsExactly(saved.artifactId(), saved.contentHash(), saved.storageUri());
    }

    @Test
    void linkToOperation_isIdempotentAndQueryableByOperation() {
        String contentHash = ContentHashes.sha256Hex(("link-" + UUID.randomUUID()).getBytes());
        Artifact saved = repository.save(artifact(contentHash, "art-" + UUID.randomUUID()));
        artifactId = saved.artifactId();
        operationId = "op-" + UUID.randomUUID();

        repository.linkToOperation(operationId, "THYMELEAF_PREVIEW", artifactId);
        repository.linkToOperation(operationId, "THYMELEAF_PREVIEW", artifactId);

        List<Artifact> linked = repository.findByOperation(operationId);
        assertThat(linked).hasSize(1);
        assertThat(linked.get(0).artifactId()).isEqualTo(artifactId);

        jdbcTemplate.update("DELETE FROM AI_ARTIFACT_LINK WHERE OPERATION_ID = ?", operationId);
    }

    @Test
    void updateStatus_persistsQuarantinedState() {
        String contentHash = ContentHashes.sha256Hex(("status-" + UUID.randomUUID()).getBytes());
        Artifact saved = repository.save(artifact(contentHash, "art-" + UUID.randomUUID()));
        artifactId = saved.artifactId();

        repository.updateStatus(artifactId, ArtifactStatus.QUARANTINED);

        assertThat(repository.findById(artifactId)).get()
                .extracting(Artifact::status).isEqualTo(ArtifactStatus.QUARANTINED);
    }

    private Artifact artifact(String contentHash, String artifactId) {
        return new Artifact(artifactId, "THYMELEAF_PREVIEW", "text/html", 42L, contentHash,
                "rev-1", "ab/cd/" + contentHash, ArtifactStatus.ACTIVE, Instant.now());
    }
}
