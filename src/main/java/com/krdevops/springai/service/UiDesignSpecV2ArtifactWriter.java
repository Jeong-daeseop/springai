package com.krdevops.springai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.StagedArtifact;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.service.artifact.ArtifactCatalogPort;
import com.krdevops.springai.service.artifact.ArtifactStorePort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

/**
 * {@link UiDesignSpecV2}를 {@code UI_DESIGN_SPEC_V2} Artifact로 영속화하고, 코드 생성 게이트
 * ({@code DesignContextArtifactReferenceValidator})가 재검증할 수 있는 content-addressed
 * {@link VersionedArtifactReference}를 반환한다.
 *
 * <p>Artifact의 {@code contentHash}는 저장 JSON 바이트의 sha256이며
 * ({@code UiDesignSpecArtifactReader.readAndVerify}가 요구), {@code UiDesignSpecV2.contentHash()}
 * 레코드 필드(원본 입력 해시)와는 별개다 — 참조에는 저장 해시를 쓴다. specId를 artifactId로
 * 사용해 {@code readV2}의 ID 일치 검사를 만족시킨다. stage/commit/save 모두 contentHash 기준
 * 멱등이므로 동일 분석을 다시 저장해도 새 레코드가 생기지 않는다.</p>
 */
@Service
public class UiDesignSpecV2ArtifactWriter {

    private static final String ARTIFACT_TYPE = "UI_DESIGN_SPEC_V2";
    private static final String MEDIA_TYPE = "application/json";

    private final ArtifactStorePort store;
    private final ArtifactCatalogPort catalog;
    private final ObjectMapper objectMapper;

    public UiDesignSpecV2ArtifactWriter(
            ArtifactStorePort store, ArtifactCatalogPort catalog, ObjectMapper objectMapper) {
        this.store = store;
        this.catalog = catalog;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public VersionedArtifactReference write(UiDesignSpecV2 spec) {
        if (spec == null) throw new IllegalArgumentException("UiDesignSpecV2는 필수입니다.");
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("UiDesignSpecV2를 직렬화할 수 없습니다.", e);
        }
        try {
            StagedArtifact staged = store.stage(bytes, MEDIA_TYPE);
            String storageUri = store.commit(staged);
            Artifact saved = catalog.save(new Artifact(
                    spec.specId(), ARTIFACT_TYPE, MEDIA_TYPE, staged.sizeBytes(),
                    staged.contentHash(), spec.source().sourceRevision(), storageUri,
                    ArtifactStatus.ACTIVE, Instant.now()));
            return new VersionedArtifactReference(
                    saved.artifactId(), ARTIFACT_TYPE, spec.schemaVersion(),
                    saved.contentHash(), saved.sourceRevision());
        } catch (IOException e) {
            throw new UncheckedIOException("UiDesignSpecV2 Artifact 저장에 실패했습니다.", e);
        }
    }
}
