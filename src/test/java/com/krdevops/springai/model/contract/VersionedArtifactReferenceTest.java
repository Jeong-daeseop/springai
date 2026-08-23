package com.krdevops.springai.model.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionedArtifactReferenceTest {

    private static final String HASH_A = "a".repeat(64);

    @Test
    void 유효한_참조는_ID_Type_Version_Hash를_고정한다() {
        VersionedArtifactReference reference = new VersionedArtifactReference(
                "ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0", HASH_A, " figma-r1 ");

        assertThat(reference.sourceRevision()).isEqualTo("figma-r1");
        assertThat(reference.identifies(new VersionedArtifactReference(
                "ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0", HASH_A, "figma-r2"))).isTrue();
    }

    @Test
    void Schema_Version과_Hash_형식이_잘못되면_거부한다() {
        assertThatThrownBy(() -> new VersionedArtifactReference(
                "ui-spec-1", "UI_DESIGN_SPEC_V2", "v2", HASH_A, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> new VersionedArtifactReference(
                "ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0", "bad", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentHash");
    }

    @Test
    void 같은_ID라도_Hash가_다르면_같은_참조가_아니다() {
        VersionedArtifactReference expected = new VersionedArtifactReference(
                "ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0", HASH_A, null);
        VersionedArtifactReference changed = new VersionedArtifactReference(
                "ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0", "b".repeat(64), null);

        assertThat(expected.identifies(changed)).isFalse();
    }
}
