package com.krdevops.springai.service.artifact;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.StagedArtifact;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ARCH-0505/0506: stage → commit(filesystem) → save(catalog) → (선택) Operation link를
 * 한 번에 수행하는 상위 조합 서비스. store.commit()과 catalog.save() 모두 contentHash 기준
 * 개별적으로 멱등이므로, 동일 내용을 다시 ingest해도 새 파일이나 새 catalog row가 생기지 않고
 * 기존 Artifact를 그대로 재사용한다.
 */
@Service
public class ArtifactService {

    private final ArtifactStorePort store;
    private final ArtifactCatalogPort catalog;
    private final OperationalTelemetry telemetry;

    public ArtifactService(ArtifactStorePort store, ArtifactCatalogPort catalog) {
        this(store, catalog, null);
    }

    @Autowired
    public ArtifactService(ArtifactStorePort store, ArtifactCatalogPort catalog,
                           OperationalTelemetry telemetry) {
        this.store = store;
        this.catalog = catalog;
        this.telemetry = telemetry;
    }

    public Artifact ingest(byte[] content, String mediaType, String artifactType, String sourceRevision) {
        long startedAt = System.nanoTime();
        try {
            StagedArtifact staged = store.stage(content, mediaType);
            String storageUri = store.commit(staged);
            Artifact artifact = new Artifact(
                    UUID.randomUUID().toString(), artifactType, mediaType, staged.sizeBytes(),
                    staged.contentHash(), sourceRevision, storageUri, ArtifactStatus.ACTIVE, Instant.now());
            Artifact saved = catalog.save(artifact);
            record(saved.artifactId(), artifactType, "INGEST", "SUCCESS", startedAt);
            return saved;
        } catch (IOException e) {
            record(null, artifactType, "INGEST", "FAILURE", startedAt);
            throw new UncheckedIOException("Artifact 저장 중 오류가 발생했습니다.", e);
        } catch (RuntimeException e) {
            record(null, artifactType, "INGEST", "FAILURE", startedAt);
            throw e;
        }
    }

    public Artifact ingestAndLink(byte[] content, String mediaType, String artifactType, String sourceRevision,
                                   String operationId, String operationType) {
        try (var operationScope = ObservabilityContextHolder.openOperation(operationId)) {
            Artifact artifact = ingest(content, mediaType, artifactType, sourceRevision);
            long startedAt = System.nanoTime();
            try (var artifactScope = ObservabilityContextHolder.openArtifact(artifact.artifactId())) {
            catalog.linkToOperation(operationId, operationType, artifact.artifactId());
            record(artifact.artifactId(), artifactType, "LINK", "SUCCESS", startedAt);
            } catch (RuntimeException e) {
                record(artifact.artifactId(), artifactType, "LINK", "FAILURE", startedAt);
                throw e;
            }
            return artifact;
        }
    }

    public List<Artifact> findByOperation(String operationId) {
        return catalog.findByOperation(operationId);
    }

    public Optional<byte[]> read(Artifact artifact) {
        long startedAt = System.nanoTime();
        try {
            Optional<byte[]> result = store.read(artifact.contentHash());
            record(artifact.artifactId(), artifact.artifactType(), "READ",
                    result.isPresent() ? "SUCCESS" : "FAILURE", startedAt);
            return result;
        } catch (IOException e) {
            record(artifact.artifactId(), artifact.artifactType(), "READ", "FAILURE", startedAt);
            throw new UncheckedIOException("Artifact 조회 중 오류가 발생했습니다.", e);
        }
    }

    private void record(String artifactId, String artifactType, String action,
                        String outcome, long startedAt) {
        if (telemetry != null) telemetry.artifactAction(artifactId, artifactType, action,
                outcome, System.nanoTime() - startedAt);
    }
}
