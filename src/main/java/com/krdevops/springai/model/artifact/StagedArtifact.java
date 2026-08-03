package com.krdevops.springai.model.artifact;

import java.nio.file.Path;

/** stage()가 반환하는, 아직 catalog에 등록되지 않은 임시 저장 핸들. */
public record StagedArtifact(Path stagingPath, String contentHash, long sizeBytes, String mediaType) {
    public StagedArtifact {
        if (stagingPath == null) {
            throw new IllegalArgumentException("stagingPath는 필수입니다.");
        }
        ContentHashes.requireValid(contentHash);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes는 0 이상이어야 합니다.");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType은 필수입니다.");
        }
    }
}
