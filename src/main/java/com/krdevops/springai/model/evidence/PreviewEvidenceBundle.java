package com.krdevops.springai.model.evidence;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.VersionedArtifactReference;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Preview 생성의 입력 Reference·Artifact·검증 Report를 하나로 고정한 Evidence Bundle. */
public record PreviewEvidenceBundle(
        String bundleId,
        String schemaVersion,
        String contentHash,
        Status status,
        String operationId,
        String sourceRevision,
        String fixtureModelHash,
        List<VersionedArtifactReference> references,
        List<VersionedArtifactReference> artifacts,
        Reports reports,
        List<String> fallbackAssessments,
        List<String> warnings,
        FinalDecision finalDecision,
        String auditSnapshotHash
) {
    public static final String SCHEMA_VERSION = "1.0";
    public PreviewEvidenceBundle {
        requireText(bundleId, "bundleId");
        if (!SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("schemaVersion은 1.0이어야 합니다.");
        contentHash = ContentHashes.requireValid(contentHash);
        if (status == null || finalDecision == null || reports == null) throw new IllegalArgumentException("status·reports·finalDecision은 필수입니다.");
        requireText(operationId, "operationId"); requireText(sourceRevision, "sourceRevision");
        ContentHashes.requireValid(fixtureModelHash); auditSnapshotHash = ContentHashes.requireValid(auditSnapshotHash);
        references = List.copyOf(references == null ? List.of() : references);
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
        fallbackAssessments = List.copyOf(fallbackAssessments == null ? List.of() : fallbackAssessments);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        if (references.size() < 3) throw new IllegalArgumentException("Evidence Reference는 최소 3개가 필요합니다.");
    }

    public boolean hasValidContentHash() { return contentHash.equals(ContentHashes.sha256Hex(canonical().getBytes(StandardCharsets.UTF_8))); }
    public boolean hasValidAuditSnapshotHash() { return auditSnapshotHash != null && auditSnapshotHash.matches("[0-9a-f]{64}"); }

    public static Builder builder(String bundleId, String operationId, String sourceRevision, String fixtureModelHash) {
        return new Builder(bundleId, operationId, sourceRevision, fixtureModelHash);
    }

    public enum Status { INCOMPLETE, COMPLETE, INVALID }
    public enum FinalDecision { PENDING, PASS, FAIL }
    public record Reports(VersionedArtifactReference binding, VersionedArtifactReference build,
                          VersionedArtifactReference render, VersionedArtifactReference security,
                          VersionedArtifactReference accessibility, VersionedArtifactReference visualDiff,
                          VersionedArtifactReference interactionFlow) {
        public boolean hasRequired() { return binding != null && build != null && render != null; }
    }

    public static final class Builder {
        private final String bundleId, operationId, sourceRevision, fixtureModelHash;
        private String schemaVersion = SCHEMA_VERSION;
        private List<VersionedArtifactReference> references = List.of(), artifacts = List.of();
        private Reports reports;
        private List<String> fallbackAssessments = List.of(), warnings = List.of();
        private FinalDecision finalDecision = FinalDecision.PENDING;
        private String auditSnapshotHash = "0".repeat(64);
        private Builder(String bundleId, String operationId, String sourceRevision, String fixtureModelHash) { this.bundleId=bundleId; this.operationId=operationId; this.sourceRevision=sourceRevision; this.fixtureModelHash=fixtureModelHash; }
        public Builder references(List<VersionedArtifactReference> v) { references=v; return this; }
        public Builder artifacts(List<VersionedArtifactReference> v) { artifacts=v; return this; }
        public Builder reports(Reports v) { reports=v; return this; }
        public Builder fallbackAssessments(List<String> v) { fallbackAssessments=v; return this; }
        public Builder warnings(List<String> v) { warnings=v; return this; }
        public Builder finalDecision(FinalDecision v) { finalDecision=v; return this; }
        public Builder auditSnapshotHash(String v) { auditSnapshotHash=v; return this; }
        public PreviewEvidenceBundle build() {
            Status status = reports != null && reports.hasRequired() ? Status.COMPLETE : Status.INCOMPLETE;
            PreviewEvidenceBundle draft = new PreviewEvidenceBundle(bundleId, schemaVersion, "0".repeat(64), status, operationId, sourceRevision, fixtureModelHash, references, artifacts, reports, fallbackAssessments, warnings, finalDecision, auditSnapshotHash);
            return new PreviewEvidenceBundle(bundleId, schemaVersion, ContentHashes.sha256Hex(draft.canonical().getBytes(StandardCharsets.UTF_8)), status, operationId, sourceRevision, fixtureModelHash, draft.references(), draft.artifacts(), reports, draft.fallbackAssessments(), draft.warnings(), finalDecision, auditSnapshotHash);
        }
    }
    private String canonical() { return bundleId+"|"+operationId+"|"+sourceRevision+"|"+fixtureModelHash+"|"+references+"|"+artifacts+"|"+reports+"|"+fallbackAssessments+"|"+warnings+"|"+status+"|"+finalDecision+"|"+auditSnapshotHash; }
    private static void requireText(String v, String f) { if (v == null || v.isBlank()) throw new IllegalArgumentException(f+"는 필수입니다."); }
}
