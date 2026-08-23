package com.krdevops.springai.service;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.contract.PipelineEvolutionErrorCode;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.service.artifact.ArtifactCatalogPort;
import com.krdevops.springai.service.contract.VersionedArtifactReferenceValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DesignContextArtifactReferenceValidatorTest {

    private final ArtifactCatalogPort catalog = mock(ArtifactCatalogPort.class);
    private final DesignContextArtifactReferenceValidator validator =
            new DesignContextArtifactReferenceValidator(
                    catalog, new VersionedArtifactReferenceValidator());

    @Test
    void Active_Artifact의_ID_Type_Hash_Revision이_일치하면_허용한다() {
        VersionedArtifactReference reference = reference("a".repeat(64), "r1");
        when(catalog.findById("ui-1")).thenReturn(Optional.of(
                artifact("a".repeat(64), "r1", ArtifactStatus.ACTIVE)));

        assertThatCode(() -> validator.requireActiveExact(reference)).doesNotThrowAnyException();
    }

    @Test
    void Artifact가_없으면_Evidence_Missing으로_차단한다() {
        when(catalog.findById("ui-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.requireActiveExact(reference("a".repeat(64), "r1")))
                .isInstanceOfSatisfying(
                        DesignContextArtifactReferenceValidator.DesignContextArtifactException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(PipelineEvolutionErrorCode.DESIGN_EVIDENCE_MISSING));
    }

    @Test
    void Hash나_Revision이_바뀌면_Stale로_차단한다() {
        when(catalog.findById("ui-1")).thenReturn(Optional.of(
                artifact("b".repeat(64), "r2", ArtifactStatus.ACTIVE)));

        assertThatThrownBy(() -> validator.requireActiveExact(reference("a".repeat(64), "r1")))
                .isInstanceOfSatisfying(
                        DesignContextArtifactReferenceValidator.DesignContextArtifactException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(PipelineEvolutionErrorCode.DESIGN_SYSTEM_SNAPSHOT_STALE));
    }

    @Test
    void Quarantined_Artifact는_차단한다() {
        when(catalog.findById("ui-1")).thenReturn(Optional.of(
                artifact("a".repeat(64), "r1", ArtifactStatus.QUARANTINED)));

        assertThatThrownBy(() -> validator.requireActiveExact(reference("a".repeat(64), "r1")))
                .isInstanceOf(DesignContextArtifactReferenceValidator.DesignContextArtifactException.class)
                .hasMessageContaining("비활성");
    }

    private VersionedArtifactReference reference(String hash, String revision) {
        return new VersionedArtifactReference(
                "ui-1", "UI_DESIGN_SPEC_V2", "2.0", hash, revision);
    }

    private Artifact artifact(String hash, String revision, ArtifactStatus status) {
        return new Artifact("ui-1", "UI_DESIGN_SPEC_V2", "application/json", 100,
                hash, revision, "artifact://ui-1", status, Instant.now());
    }
}
