package com.krdevops.springai.model.artifact;

import java.time.Instant;

/**
 * ARCH-0501: Figma Bundle, Web Capture, Thymeleaf Preview, Validation Report 등
 * 서로 다른 도메인 산출물을 하나의 계약으로 표현하는 catalog entry.
 * {@code contentHash}가 실제 저장 위치를 결정하므로(content-addressed) 동일 내용은
 * 항상 동일 Artifact로 귀결된다({@link ArtifactStatus#ACTIVE} 상태에서).
 */
public record Artifact(
        String artifactId,
        String artifactType,
        String mediaType,
        long sizeBytes,
        String contentHash,
        String sourceRevision,
        String storageUri,
        ArtifactStatus status,
        Instant createdAt
) {
    public Artifact {
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId는 필수입니다.");
        }
        if (artifactType == null || artifactType.isBlank()) {
            throw new IllegalArgumentException("artifactType은 필수입니다.");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType은 필수입니다.");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes는 0 이상이어야 합니다.");
        }
        ContentHashes.requireValid(contentHash);
        if (storageUri == null || storageUri.isBlank()) {
            throw new IllegalArgumentException("storageUri는 필수입니다.");
        }
        status = status == null ? ArtifactStatus.ACTIVE : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
