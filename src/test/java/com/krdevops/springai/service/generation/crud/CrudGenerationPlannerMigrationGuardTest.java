package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import com.krdevops.springai.service.migration.LegacyCompatibilityService;
import com.krdevops.springai.service.migration.PipelineMigrationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * R0-DUAL: {@link PipelineMigrationGuard}·{@link LegacyCompatibilityService}가
 * {@link CrudGenerationPlanner}에 실제로 배선되어, OBSERVE/DUAL_READ 단계에서 v2 Design IR
 * 참조를 가진 명세로 Apply를 시도하면 fail-closed 하는지 검증한다. 두 Guard는 mock이 아니라
 * 실제 구현을 그대로 사용한다 — "연결되어 있다"는 사실 자체가 검증 대상이기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrudGenerationPlannerMigrationGuardTest {

    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudProgramMetadataService crudProgramMetadataService;
    @Mock GenerationDesignContextService generationDesignContextService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock BoardRouteCollisionDetector routeCollisionDetector;

    ThymeleafLayoutValidator thymeleafLayoutValidator = new ThymeleafLayoutValidator();

    @BeforeEach
    void stubCommonPreconditions() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .willReturn(CrudProgramMetadata.fallback("fallback"));
    }

    @Test
    void observeModeBlocksApplyWhenSpecificationCarriesV2Reference() {
        CrudGenerationPlanner planner = plannerWithMode(PipelineEvolutionProperties.Mode.OBSERVE);
        given(generationDesignContextService.resolve(any(), any(), any(), any(), any(), any()))
                .willReturn(specificationWithV2Reference());

        assertThatThrownBy(() -> planner.plan(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("관찰 모드");
    }

    @Test
    void dualReadModeBlocksApplyWhenSpecificationCarriesV2Reference() {
        CrudGenerationPlanner planner = plannerWithMode(PipelineEvolutionProperties.Mode.DUAL_READ);
        given(generationDesignContextService.resolve(any(), any(), any(), any(), any(), any()))
                .willReturn(specificationWithV2Reference());

        assertThatThrownBy(() -> planner.plan(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이중 읽기");
    }

    /** v2 참조가 없으면 OBSERVE/DUAL_READ에서도 Guard가 관여하지 않고 그대로 통과해야 한다. */
    @Test
    void observeModeAllowsApplyWhenSpecificationHasNoV2Reference() {
        CrudGenerationPlanner planner = plannerWithMode(PipelineEvolutionProperties.Mode.OBSERVE);
        given(generationDesignContextService.resolve(any(), any(), any(), any(), any(), any()))
                .willReturn(specificationWithoutV2Reference());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willThrow(new RuntimeException("PAST_GUARD_MARKER"));

        assertThatThrownBy(() -> planner.plan(command()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("PAST_GUARD_MARKER");
    }

    private CrudGenerationPlanner plannerWithMode(PipelineEvolutionProperties.Mode mode) {
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setMode(mode);
        return new CrudGenerationPlanner(
                crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, thymeleafLayoutValidator, routeCollisionDetector,
                null, null, null,
                properties, new PipelineMigrationGuard(), new LegacyCompatibilityService());
    }

    private static CrudGenerationCommand command() {
        return new CrudGenerationCommand(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                Path.of("/tmp/egov-test"), "auto", "5.0", "jsp",
                LayoutOptions.empty(), ProgramMetadataOverrides.empty(), DesignContextReference.empty());
    }

    private static ScreenSpecification specificationWithV2Reference() {
        return new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "Employer", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(),
                null, null, null, null, LocalDateTime.now(),
                new VersionedArtifactReference(
                        "ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0", "a".repeat(64), null),
                null);
    }

    private static ScreenSpecification specificationWithoutV2Reference() {
        return new ScreenSpecification(
                "spec-2", 1, ScreenSpecStatus.APPROVED, "Employer", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(),
                null, null, null, null, LocalDateTime.now(), null, null);
    }

    private static List<Map<String, Object>> fakeColumns() {
        Map<String, Object> col = new HashMap<>();
        col.put("COLUMN_NAME", "EMPLYR_ID");
        col.put("DATA_TYPE", "varchar");
        col.put("CHARACTER_MAXIMUM_LENGTH", 20L);
        col.put("IS_NULLABLE", "NO");
        col.put("COLUMN_COMMENT", "직원ID");
        col.put("COLUMN_KEY", "PRI");
        return List.of(col);
    }
}
