package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnershipRegionClassifierTest {
    @Test
    void 영역_접두사를_소유권_유형으로_분류한다() {
        var classifier = new OwnershipRegionClassifier();
        assertThat(classifier.classify("generated.controller")).isEqualTo(GenerationOwnershipManifest.RegionType.GENERATED);
        assertThat(classifier.classify("binding.vo")).isEqualTo(GenerationOwnershipManifest.RegionType.BINDING);
        assertThat(classifier.classify("protected.custom")).isEqualTo(GenerationOwnershipManifest.RegionType.PROTECTED);
        assertThat(classifier.classify("user.notes")).isEqualTo(GenerationOwnershipManifest.RegionType.UNKNOWN);
    }
}
