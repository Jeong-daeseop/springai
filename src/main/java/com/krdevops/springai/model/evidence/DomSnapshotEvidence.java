package com.krdevops.springai.model.evidence;

import com.krdevops.springai.model.artifact.ContentHashes;

public record DomSnapshotEvidence(String artifactId, String contentHash, String route, int nodeCount) {
    public DomSnapshotEvidence {
        if (artifactId == null || artifactId.isBlank() || route == null || route.isBlank()) throw new IllegalArgumentException("DOM Snapshot 식별자·route는 필수입니다.");
        ContentHashes.requireValid(contentHash);
        if (nodeCount < 0) throw new IllegalArgumentException("nodeCount는 0 이상이어야 합니다.");
    }
}
