package com.krdevops.springai.model.generation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GenerationOwnershipManifestTest {
    @Test
    void Ownership_Manifest를_결정적으로_생성한다() {
        var region = new GenerationOwnershipManifest.Region("controller.generated",
                GenerationOwnershipManifest.RegionType.GENERATED, "a".repeat(64));
        var artifact = new GenerationOwnershipManifest.ArtifactOwnership("src/Controller.java",
                List.of(region), GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai");
        var manifest = GenerationOwnershipManifest.builder("ownership-1").artifacts(List.of(artifact)).build();
        assertThat(manifest.hasValidContentHash()).isTrue();
        assertThat(manifest.artifacts()).containsExactly(artifact);
    }
}
