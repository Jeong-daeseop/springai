package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record DesignSystemKnowledgeSnapshot(String snapshotId, String version, String contentHash,
                                            Status status, List<VersionedArtifactReference> references) {
    public DesignSystemKnowledgeSnapshot {
        if (snapshotId == null || snapshotId.isBlank() || version == null || version.isBlank() || status == null) throw new IllegalArgumentException("Snapshot 필수값이 누락되었습니다.");
        contentHash = ContentHashes.requireValid(contentHash);
        references = List.copyOf(references == null ? List.of() : references);
    }
    public boolean hasValidContentHash() { return contentHash.equals(ContentHashes.sha256Hex((snapshotId+"|"+version+"|"+references).getBytes(StandardCharsets.UTF_8))); }
    public enum Status { DRAFT, APPROVED, SUPERSEDED }
    public static Builder builder(String id, String version) { return new Builder(id, version); }
    public static final class Builder {
        private final String id, version; private List<VersionedArtifactReference> refs = List.of(); private Status status = Status.DRAFT;
        private Builder(String id, String version) { this.id=id; this.version=version; }
        public Builder references(List<VersionedArtifactReference> value) { refs=value; return this; }
        public Builder status(Status value) { status=value; return this; }
        public DesignSystemKnowledgeSnapshot build() {
            String hash = ContentHashes.sha256Hex((id+"|"+version+"|"+List.copyOf(refs == null ? List.of() : refs)).getBytes(StandardCharsets.UTF_8));
            return new DesignSystemKnowledgeSnapshot(id, version, hash, status, refs);
        }
    }
}
