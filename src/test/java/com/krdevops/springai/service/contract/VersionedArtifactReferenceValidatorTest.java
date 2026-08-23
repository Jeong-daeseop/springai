package com.krdevops.springai.service.contract;

import com.krdevops.springai.model.contract.PipelineEvolutionErrorCode;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionedArtifactReferenceValidatorTest {

    private final VersionedArtifactReferenceValidator validator = new VersionedArtifactReferenceValidator();

    @Test
    void 정확한_ID_Type_Version_Hash만_허용한다() {
        VersionedArtifactReference reference = reference("a".repeat(64));
        assertThatCode(() -> validator.requireExact(reference, reference)).doesNotThrowAnyException();
    }

    @Test
    void 같은_ID의_다른_Hash를_fail_closed한다() {
        VersionedArtifactReference expected = reference("a".repeat(64));
        VersionedArtifactReference actual = reference("b".repeat(64));

        assertThatThrownBy(() -> validator.requireExact(expected, actual))
                .isInstanceOfSatisfying(
                        VersionedArtifactReferenceValidator.ArtifactReferenceMismatchException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(PipelineEvolutionErrorCode.ARTIFACT_REFERENCE_MISMATCH));
    }

    private VersionedArtifactReference reference(String hash) {
        return new VersionedArtifactReference("ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0", hash, "r1");
    }
}
