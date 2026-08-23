package com.krdevops.springai.service;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.contract.PipelineEvolutionErrorCode;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.service.artifact.ArtifactCatalogPort;
import com.krdevops.springai.service.contract.VersionedArtifactReferenceValidator;
import org.springframework.stereotype.Component;

/** 코드 생성 전에 ScreenSpecification이 고정한 Design IR·Snapshot Artifact를 재검증한다. */
@Component
public class DesignContextArtifactReferenceValidator {

    private final ArtifactCatalogPort catalog;
    private final VersionedArtifactReferenceValidator referenceValidator;

    public DesignContextArtifactReferenceValidator(
            ArtifactCatalogPort catalog,
            VersionedArtifactReferenceValidator referenceValidator) {
        this.catalog = catalog;
        this.referenceValidator = referenceValidator;
    }

    public void requireActiveExact(VersionedArtifactReference expected) {
        if (expected == null) throw failure(
                PipelineEvolutionErrorCode.DESIGN_EVIDENCE_MISSING,
                "생성에 필요한 Design Artifact 참조가 없습니다.");
        Artifact artifact = catalog.findById(expected.artifactId())
                .orElseThrow(() -> failure(
                        PipelineEvolutionErrorCode.DESIGN_EVIDENCE_MISSING,
                        "Design Artifact를 찾을 수 없습니다: " + expected.artifactId()));
        if (artifact.status() != ArtifactStatus.ACTIVE) {
            throw failure(PipelineEvolutionErrorCode.DESIGN_SYSTEM_SNAPSHOT_STALE,
                    "격리되거나 비활성인 Design Artifact는 생성에 사용할 수 없습니다: "
                            + expected.artifactId());
        }
        VersionedArtifactReference actual = new VersionedArtifactReference(
                artifact.artifactId(), artifact.artifactType(), expected.schemaVersion(),
                artifact.contentHash(), artifact.sourceRevision());
        try {
            referenceValidator.requireExact(expected, actual);
        } catch (VersionedArtifactReferenceValidator.ArtifactReferenceMismatchException exception) {
            throw failure(PipelineEvolutionErrorCode.DESIGN_SYSTEM_SNAPSHOT_STALE,
                    "Design Artifact ID·Type·Version·Hash가 승인 참조와 다릅니다: "
                            + expected.artifactId());
        }
        if (expected.sourceRevision() != null
                && !expected.sourceRevision().equals(artifact.sourceRevision())) {
            throw failure(PipelineEvolutionErrorCode.DESIGN_SYSTEM_SNAPSHOT_STALE,
                    "Design Artifact Source Revision이 승인 참조와 다릅니다: "
                            + expected.artifactId());
        }
    }

    private DesignContextArtifactException failure(
            PipelineEvolutionErrorCode code, String message) {
        return new DesignContextArtifactException(code, message);
    }

    public static final class DesignContextArtifactException extends IllegalStateException {
        private final PipelineEvolutionErrorCode errorCode;

        public DesignContextArtifactException(PipelineEvolutionErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public PipelineEvolutionErrorCode errorCode() {
            return errorCode;
        }
    }
}
