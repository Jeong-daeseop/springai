package com.krdevops.springai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineEvolutionPropertiesTest {

    @Test
    void 기본값은_기존_경로만_사용한다() {
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();

        assertThat(properties.getMode()).isEqualTo(PipelineEvolutionProperties.Mode.DISABLED);
        assertThat(properties.writesV2Artifacts()).isFalse();
        assertThat(properties.readsV2Artifacts()).isFalse();
        assertThat(properties.usesV2Preview()).isFalse();
        assertThat(properties.usesV2Apply()).isFalse();
    }

    @Test
    void 단계가_올라갈수록_하위_기능을_포함한다() {
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setMode(PipelineEvolutionProperties.Mode.V2_APPLY);

        assertThat(properties.writesV2Artifacts()).isTrue();
        assertThat(properties.readsV2Artifacts()).isTrue();
        assertThat(properties.usesV2Preview()).isTrue();
        assertThat(properties.usesV2Apply()).isTrue();
    }

    @Test
    void Confidence_임계값은_범위와_순서를_검증한다() {
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setAutoApprovalConfidenceThreshold(0.8);
        properties.setEvidenceConfidenceThreshold(0.9);

        org.assertj.core.api.Assertions.assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("클 수 없습니다");

        properties.setEvidenceConfidenceThreshold(-0.1);
        org.assertj.core.api.Assertions.assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0.0 이상 1.0 이하");
    }
}
