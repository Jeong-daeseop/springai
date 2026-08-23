package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.artifact.ContentHashes;

public record AssetManifestEntry(String assetId, String uri, String license, String contentHash, String integrityHash) {
    public AssetManifestEntry {
        if (assetId == null || assetId.isBlank() || uri == null || uri.isBlank() || license == null || license.isBlank()) throw new IllegalArgumentException("Asset Manifest 필수값이 누락되었습니다.");
        ContentHashes.requireValid(contentHash); ContentHashes.requireValid(integrityHash);
    }
}
