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

    public ArtifactService(ArtifactStorePort store, ArtifactCatalogPort catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    public Artifact ingest(byte[] content, String mediaType, String artifactType, String sourceRevision) {
        try {
            StagedArtifact staged = store.stage(content, mediaType);
            String storageUri = store.commit(staged);
            Artifact artifact = new Artifact(
                    UUID.randomUUID().toString(), artifactType, mediaType, staged.sizeBytes(),
                    staged.contentHash(), sourceRevision, storageUri, ArtifactStatus.ACTIVE, Instant.now());
            return catalog.save(artifact);
        } catch (IOException e) {
            throw new UncheckedIOException("Artifact 저장 중 오류가 발생했습니다.", e);
        }
    }

    public Artifact ingestAndLink(byte[] content, String mediaType, String artifactType, String sourceRevision,
                                   String operationId, String operationType) {
        Artifact artifact = ingest(content, mediaType, artifactType, sourceRevision);
        catalog.linkToOperation(operationId, operationType, artifact.artifactId());
        return artifact;
    }

    public List<Artifact> findByOperation(String operationId) {
        return catalog.findByOperation(operationId);
    }

    public Optional<byte[]> read(Artifact artifact) {
        try {
            return store.read(artifact.contentHash());
        } catch (IOException e) {
            throw new UncheckedIOException("Artifact 조회 중 오류가 발생했습니다.", e);
        }
    }
}
