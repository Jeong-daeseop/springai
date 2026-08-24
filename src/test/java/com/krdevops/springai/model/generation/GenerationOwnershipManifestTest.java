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

    @Test
    void regionsFor는_해당_artifactPath의_Region_목록을_반환하고_없으면_빈_리스트다() {
        var region = new GenerationOwnershipManifest.Region("controller.generated",
                GenerationOwnershipManifest.RegionType.GENERATED, "a".repeat(64));
        var artifact = new GenerationOwnershipManifest.ArtifactOwnership("src/Controller.java",
                List.of(region), GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai");
        var manifest = GenerationOwnershipManifest.builder("ownership-2").artifacts(List.of(artifact)).build();

        assertThat(manifest.regionsFor("src/Controller.java")).containsExactly(region);
        assertThat(manifest.regionsFor("없는파일.java")).isEmpty();
    }
}
