package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.FigmaReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaCacheKeyFactoryTest {

    @Test
    void keyChangesForVersionFeatureTypeMapperVersionAndDepth() {
        DesignVisionProperties properties = new DesignVisionProperties();
        FigmaCacheKeyFactory factory = new FigmaCacheKeyFactory(properties);
        FigmaReference reference = new FigmaReference("abcdef", "1:2");
        String baseline = factory.create(reference, "v1", "crud");

        assertThat(factory.create(reference, "v2", "crud")).isNotEqualTo(baseline);
        assertThat(factory.create(reference, "v1", "board")).isNotEqualTo(baseline);
        properties.getFigma().setMapperVersion("figma-mapper-v3");
        assertThat(factory.create(reference, "v1", "crud")).isNotEqualTo(baseline);
        properties.getFigma().setMapperVersion("figma-mapper-v2");
        properties.getFigma().setDepthLimit(5);
        assertThat(factory.create(reference, "v1", "crud")).isNotEqualTo(baseline);
    }

    @Test
    void normalizesFeatureTypeAndProducesSha256() {
        FigmaCacheKeyFactory factory = new FigmaCacheKeyFactory(new DesignVisionProperties());
        FigmaReference reference = new FigmaReference("abcdef", "1:2");

        assertThat(factory.create(reference, "v1", " CRUD "))
                .isEqualTo(factory.create(reference, "v1", "crud"))
                .hasSize(64);
    }
}
