package com.krdevops.springai.service.contract;

import com.krdevops.springai.model.contract.PipelineEvolutionErrorCode;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.springframework.stereotype.Component;

/** ID가 같더라도 Version 또는 Hash가 다른 산출물의 혼용을 fail-closed 한다. */
@Component
public class VersionedArtifactReferenceValidator {

    public void requireExact(
            VersionedArtifactReference expected,
            VersionedArtifactReference actual) {
        if (expected == null || actual == null || !expected.identifies(actual)) {
            throw new ArtifactReferenceMismatchException(expected, actual);
        }
    }

    public static final class ArtifactReferenceMismatchException extends IllegalArgumentException {
        private final PipelineEvolutionErrorCode errorCode;

        private ArtifactReferenceMismatchException(
                VersionedArtifactReference expected,
                VersionedArtifactReference actual) {
            super("Artifact 참조의 ID·Type·Schema Version·Content Hash가 일치하지 않습니다: expected="
                    + describe(expected) + ", actual=" + describe(actual));
            this.errorCode = PipelineEvolutionErrorCode.ARTIFACT_REFERENCE_MISMATCH;
        }

        public PipelineEvolutionErrorCode errorCode() {
            return errorCode;
        }

        private static String describe(VersionedArtifactReference reference) {
            if (reference == null) return "null";
            return reference.artifactType() + "/" + reference.artifactId()
                    + "@" + reference.schemaVersion() + "#" + reference.contentHash();
        }
    }
}
