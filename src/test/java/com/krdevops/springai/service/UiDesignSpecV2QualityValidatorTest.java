package com.krdevops.springai.service;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.contract.PipelineEvolutionErrorCode;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiDesignSpecV2QualityValidatorTest {

    private final PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
    private final UiDesignSpecV2QualityValidator validator =
            new UiDesignSpecV2QualityValidator(properties);

    @Test
    void 높은_Confidence와_Native_Node는_자동_승인과_Apply가_가능하다() {
        UiDesignSpecV2 spec = spec("text", 0.95,
                UiDesignSpecV2.RenderabilityDecision.NATIVE, true);

        assertThat(validator.validateForAutoApproval(spec).allowed()).isTrue();
        assertThat(validator.validateForApply(spec).allowed()).isTrue();
    }

    @Test
    void 자동_승인_임계값_미만은_사람_검토가_필요하다() {
        UiDesignSpecV2 spec = spec("text", 0.8,
                UiDesignSpecV2.RenderabilityDecision.NATIVE, true);

        UiDesignSpecV2QualityValidator.ValidationResult result =
                validator.validateForAutoApproval(spec);

        assertThat(result.allowed()).isFalse();
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(PipelineEvolutionErrorCode.DESIGN_CONFIDENCE_TOO_LOW);
            assertThat(issue.severity()).isEqualTo(UiDesignSpecV2QualityValidator.Severity.BLOCK);
        });
    }

    @Test
    void Evidence_최소_기준_미만은_Apply를_차단한다() {
        UiDesignSpecV2 spec = spec("container", 0.5,
                UiDesignSpecV2.RenderabilityDecision.COMPOSED, true);

        UiDesignSpecV2QualityValidator.ValidationResult result = validator.validateForApply(spec);

        assertThat(result.allowed()).isFalse();
        assertThat(result.issues()).extracting(UiDesignSpecV2QualityValidator.QualityIssue::code)
                .contains(PipelineEvolutionErrorCode.DESIGN_CONFIDENCE_TOO_LOW);
    }

    @Test
    void Form_Table_Text의_Raster_Fallback은_승인_여부와_무관하게_차단한다() {
        for (String role : List.of("form", "data-table", "text")) {
            UiDesignSpecV2 spec = spec(role, 0.95,
                    UiDesignSpecV2.RenderabilityDecision.RASTERIZED, true);

            UiDesignSpecV2QualityValidator.ValidationResult result = validator.validateForApply(spec);

            assertThat(result.allowed()).as(role).isFalse();
            assertThat(result.issues()).anySatisfy(issue -> {
                assertThat(issue.code())
                        .isEqualTo(PipelineEvolutionErrorCode.RENDERER_CAPABILITY_UNSUPPORTED);
                assertThat(issue.message()).contains("Raster Fallback");
            });
        }
    }

    @Test
    void 일반_이미지_Raster는_명시적_승인_후_Apply할_수_있다() {
        UiDesignSpecV2 spec = spec("decorative-image", 0.95,
                UiDesignSpecV2.RenderabilityDecision.RASTERIZED, true);

        assertThat(validator.validateForApply(spec).allowed()).isTrue();
    }

    @Test
    void Unsupported_Node는_항상_차단한다() {
        UiDesignSpecV2 spec = spec("unknown", 0.95,
                UiDesignSpecV2.RenderabilityDecision.UNSUPPORTED, false);

        assertThat(validator.validateForApply(spec).allowed()).isFalse();
    }

    private UiDesignSpecV2 spec(
            String role,
            double confidence,
            UiDesignSpecV2.RenderabilityDecision decision,
            boolean approved) {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:1"), confidence, "TEST", false, false);
        UiDesignSpecV2.SemanticNode node = new UiDesignSpecV2.SemanticNode(
                "node-1", role, null, evidence, List.of());
        String loss = decision == UiDesignSpecV2.RenderabilityDecision.APPROXIMATED
                || decision == UiDesignSpecV2.RenderabilityDecision.RASTERIZED
                || decision == UiDesignSpecV2.RenderabilityDecision.UNSUPPORTED
                ? "테스트 손실" : null;
        return new UiDesignSpecV2(
                "ui-1", "2.0", "a".repeat(64),
                new UiDesignSpecV2.Source(
                        UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", "r1"),
                null, List.of(node), List.of(),
                List.of(new UiDesignSpecV2.ResponsiveStructure(
                        "desktop", List.of("node-1"), List.of("node-1"))),
                List.of(new UiDesignSpecV2.RenderabilityAssessment(
                        "node-1", decision, loss, approved)),
                List.of(), confidence);
    }
}
