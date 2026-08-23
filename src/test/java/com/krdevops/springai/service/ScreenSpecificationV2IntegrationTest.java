package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenSpecificationV2IntegrationTest {

    private final CrudSchemaQueryService schemaQueryService = mock(CrudSchemaQueryService.class);
    private final ScreenSpecAssembler assembler = mock(ScreenSpecAssembler.class);
    private final ScreenDataBindingResolver bindingResolver = mock(ScreenDataBindingResolver.class);
    private final ScreenSpecRepository repository = mock(ScreenSpecRepository.class);
    private final UiDesignSpecV2ToV1Projection projection = mock(UiDesignSpecV2ToV1Projection.class);
    private final ScreenSpecValidator validator = new ScreenSpecValidator();
    private final UiDesignSpecV2QualityValidator qualityValidator =
            new UiDesignSpecV2QualityValidator(new PipelineEvolutionProperties());
    private ScreenSpecificationService service;

    @BeforeEach
    void setUp() {
        service = new ScreenSpecificationService(
                schemaQueryService, assembler, bindingResolver, validator, repository,
                projection, qualityValidator);
        when(schemaQueryService.fetchColumns("egov", "QNA"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "QNA_ID")));
        when(projection.project(any(), eq("crud"))).thenReturn(UiDesignSpec.empty("CRUD_LIST"));
        when(assembler.assemble(eq("egov"), eq("QNA"), eq("문의"), eq("crud"),
                anyList(), any(UiDesignSpec.class), eq(null), eq(null)))
                .thenReturn(cleanDraft());
        when(bindingResolver.resolve(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 높은_Confidence_v2는_참조를_보존하고_APPROVED로_저장한다() {
        UiDesignSpecV2 design = design(0.95);

        ScreenSpecification result = service.createFromV2(
                "egov", "QNA", "문의", "crud", design);

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.APPROVED);
        assertThat(result.uiDesignSpecReference().artifactId()).isEqualTo("ui-1");
        assertThat(result.uiDesignSpecReference().contentHash()).isEqualTo("a".repeat(64));
        verify(repository).save(result);
    }

    @Test
    void 낮은_Confidence는_실제_REVIEW_REQUIRED와_SpecIssue를_생성한다() {
        ScreenSpecification result = service.createFromV2(
                "egov", "QNA", "문의", "crud", design(0.8));

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("DESIGN_CONFIDENCE_TOO_LOW");
            assertThat(issue.fieldId()).isEqualTo("node-1");
        });
        assertThatThrownBy(() -> validator.approve(result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("승인할 수 없습니다");
    }

    @Test
    void v2_참조_도입_전_JSON은_null_참조로_호환_조회된다() throws Exception {
        String legacyJson = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(cleanDraft())
                .replace(",\"uiDesignSpecReference\":null", "")
                .replace(",\"designSystemSnapshotReference\":null", "");

        ScreenSpecification restored = new ObjectMapper().findAndRegisterModules()
                .readValue(legacyJson, ScreenSpecification.class);

        assertThat(restored.uiDesignSpecReference()).isNull();
        assertThat(restored.designSystemSnapshotReference()).isNull();
    }

    private ScreenSpecification cleanDraft() {
        return new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.DRAFT, "문의", "crud", "CRUD_LIST",
                "egov", "QNA", List.of(DataSourceSpec.primary("egov", "QNA")),
                List.of(new PageSpec("list", "CRUD_LIST", List.of(), List.of())),
                List.of(), null, null, null, null, LocalDateTime.now());
    }

    private UiDesignSpecV2 design(double confidence) {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:1"), confidence, "TEST", false, false);
        UiDesignSpecV2.SemanticNode node = new UiDesignSpecV2.SemanticNode(
                "node-1", "container", null, evidence, List.of());
        return new UiDesignSpecV2(
                "ui-1", "2.0", "a".repeat(64),
                new UiDesignSpecV2.Source(
                        UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", "r1"),
                null, List.of(node), List.of(),
                List.of(new UiDesignSpecV2.ResponsiveStructure(
                        "desktop", List.of("node-1"), List.of("node-1"))),
                List.of(new UiDesignSpecV2.RenderabilityAssessment(
                        "node-1", UiDesignSpecV2.RenderabilityDecision.COMPOSED, null, true)),
                List.of(), confidence);
    }
}
