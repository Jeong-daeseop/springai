package com.krdevops.springai.service.generation;

import com.krdevops.springai.service.generation.model.FileBlueprint;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationScopeClassifierTest {

    @Test
    void 생성계획을_네_범주로_결정적으로_분류한다() {
        GenerationScopeClassifier classifier = new GenerationScopeClassifier();
        GenerationScopeClassifier.ScopeClassification result = classifier.classify(List.of(
                file("controller"), file("layoutHtml"), file("controlleradvice"), file("custom")));

        assertThat(result.root()).extracting(FileBlueprint::layerKey).containsExactly("controller");
        assertThat(result.dependency()).extracting(FileBlueprint::layerKey).containsExactly("layoutHtml");
        assertThat(result.validationOnly()).extracting(FileBlueprint::layerKey).containsExactly("controlleradvice");
        assertThat(result.preserved()).extracting(FileBlueprint::layerKey).containsExactly("custom");
    }

    @Test
    void layout_접두사는_새_layout_layer도_dependency로_분류한다() {
        assertThat(new GenerationScopeClassifier().classifyLayer("layoutCustom"))
                .isEqualTo(GenerationScopeClassifier.Category.DEPENDENCY);
    }

    private static FileBlueprint file(String layer) {
        return new FileBlueprint(layer, layer + ".txt", Path.of("build/" + layer), null);
    }
}
