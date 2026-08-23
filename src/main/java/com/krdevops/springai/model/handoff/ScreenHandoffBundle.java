package com.krdevops.springai.model.handoff;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record ScreenHandoffBundle(String bundleId, String contentHash, String operationId, String sourceRevision,
                                  List<VersionedArtifactReference> references, List<String> diffs, List<String> issues,
                                  List<String> migrationNotes, List<VersionedArtifactReference> rollbackReferences,
                                  String auditSnapshotHash) {
    public ScreenHandoffBundle(String bundleId, String contentHash, String operationId, String sourceRevision,
                               List<VersionedArtifactReference> references, List<String> diffs, List<String> issues,
                               List<String> migrationNotes, List<VersionedArtifactReference> rollbackReferences) {
        this(bundleId, contentHash, operationId, sourceRevision, references, diffs, issues, migrationNotes,
                rollbackReferences, "0".repeat(64));
    }
    public ScreenHandoffBundle {
        if (bundleId == null || bundleId.isBlank() || operationId == null || operationId.isBlank() || sourceRevision == null || sourceRevision.isBlank()) throw new IllegalArgumentException("Handoff 필수값이 누락되었습니다.");
        contentHash = ContentHashes.requireValid(contentHash); auditSnapshotHash = ContentHashes.requireValid(auditSnapshotHash); references=List.copyOf(references==null?List.of():references); diffs=List.copyOf(diffs==null?List.of():diffs); issues=List.copyOf(issues==null?List.of():issues); migrationNotes=List.copyOf(migrationNotes==null?List.of():migrationNotes); rollbackReferences=List.copyOf(rollbackReferences==null?List.of():rollbackReferences);
    }
    public boolean hasValidContentHash() { return contentHash.equals(ContentHashes.sha256Hex((bundleId+"|"+operationId+"|"+sourceRevision+"|"+references+"|"+diffs+"|"+issues+"|"+migrationNotes+"|"+rollbackReferences+"|"+auditSnapshotHash).getBytes(StandardCharsets.UTF_8))); }
    public boolean hasValidAuditSnapshotHash() { return auditSnapshotHash.matches("[0-9a-f]{64}"); }
}
