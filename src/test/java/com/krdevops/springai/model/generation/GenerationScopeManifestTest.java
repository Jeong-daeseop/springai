package com.krdevops.springai.model.generation;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationScopeManifestTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void 입력순서와_무관하게_동일한_hash를_계산한다() {
        VersionedArtifactReference root = ref("screen", HASH_A);
        VersionedArtifactReference dependency = ref("fragment", HASH_B);

        GenerationScopeManifest first = GenerationScopeManifest.builder("scope-1")
                .rootArtifacts(java.util.List.of(root))
                .dependencyArtifacts(java.util.List.of(dependency))
                .affectedScreens(java.util.List.of("detail", "list"))
                .selectionReason("approved screen")
                .unresolvedDependencies(java.util.List.of())
                .build();
        GenerationScopeManifest second = GenerationScopeManifest.builder("scope-1")
                .rootArtifacts(java.util.List.of(root))
                .dependencyArtifacts(java.util.List.of(dependency))
                .affectedScreens(java.util.List.of("list", "detail"))
                .selectionReason("approved screen")
                .build();

        assertThat(first.contentHash()).isEqualTo(second.contentHash());
        assertThat(first.hasValidContentHash()).isTrue();
    }

    @Test
    void 서로_다른_범주에_동일_Artifact를_중복할수없다() {
        VersionedArtifactReference root = ref("screen", HASH_A);

        assertThatThrownBy(() -> GenerationScopeManifest.builder("scope-1")
                .rootArtifacts(java.util.List.of(root))
                .dependencyArtifacts(java.util.List.of(root))
                .selectionReason("approved screen")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    private static VersionedArtifactReference ref(String id, String hash) {
        return new VersionedArtifactReference(id, "SCREEN", "1.0", hash, null);
    }
}
