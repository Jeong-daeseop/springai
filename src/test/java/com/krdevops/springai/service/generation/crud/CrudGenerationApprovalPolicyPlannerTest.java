package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.config.CrudGenerationApprovalProperties;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.controlplane.GenerationAuditRecord;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.controlplane.CrudGenerationAuditPort;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import com.krdevops.springai.service.migration.LegacyCompatibilityService;
import com.krdevops.springai.service.migration.PipelineMigrationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CRUD_명시적_승인_단계_구현목록.md APR-T05/T06/T08 — 옵션 B(조건부 승인 게이트)가
 * {@link CrudGenerationPlanner}에 실제로 배선되어, 고위험 테이블(또는 전체)이 승인된
 * 화면명세 없이 auto 생성을 시도하면 DB 조회 전에 fail-closed 하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrudGenerationApprovalPolicyPlannerTest {

    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudProgramMetadataService crudProgramMetadataService;
    @Mock GenerationDesignContextService generationDesignContextService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock BoardRouteCollisionDetector routeCollisionDetector;
    @Mock CrudGenerationAuditPort auditPort;

    ThymeleafLayoutValidator thymeleafLayoutValidator = new ThymeleafLayoutValidator();

    @BeforeEach
    void stubCommonPreconditions() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .willReturn(CrudProgramMetadata.fallback("fallback"));
    }

    /** APR-T05: 고위험 테이블로 지정되면 화면명세 없는 auto 생성은 DB 조회도 하기 전에 차단된다. */
    @Test
    void blocksHighRiskTableWithoutDesignReference() {
        CrudGenerationPlanner planner = plannerWithPolicy(
                List.of("LETTNEMPLYRINFO"), false);

        CrudGenerationPlan plan = planner.plan(command(DesignContextReference.empty()));

        assertThat(plan.failed()).isTrue();
        assertThat(plan.failure().kind()).isEqualTo(CrudPlanFailure.Kind.MAPPING_BLOCKED);
        assertThat(plan.failure().validationSummary()).contains("고위험 테이블 정책");
        verify(crudSchemaQueryService, never()).fetchColumns(any(), any());
    }

    /** APR-T05: 승인된 화면명세(screenSpecificationId)가 있으면 정책을 통과해 다음 단계로 진행한다. */
    @Test
    void allowsHighRiskTableWithScreenSpecificationId() {
        CrudGenerationPlanner planner = plannerWithPolicy(
                List.of("LETTNEMPLYRINFO"), false);
        given(crudSchemaQueryService.fetchColumns(any(), any()))
                .willThrow(new RuntimeException("PAST_APPROVAL_POLICY_MARKER"));

        assertThatThrownBy(() -> planner.plan(
                command(new DesignContextReference(null, "spec-approved-1"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("PAST_APPROVAL_POLICY_MARKER");
    }

    /** APR-T06: 고위험 목록에 없는 테이블은 정책이 전혀 관여하지 않고 기존 동작 그대로다(회귀 없음). */
    @Test
    void doesNotAffectTableNotInHighRiskList() {
        CrudGenerationPlanner planner = plannerWithPolicy(
                List.of("SOME_OTHER_TABLE"), false);
        given(crudSchemaQueryService.fetchColumns(any(), any()))
                .willThrow(new RuntimeException("PAST_APPROVAL_POLICY_MARKER"));

        assertThatThrownBy(() -> planner.plan(command(DesignContextReference.empty())))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("PAST_APPROVAL_POLICY_MARKER");
    }

    /** APR-T08: approvalRequiredForAll=true면 목록에 없는 테이블도, viewType이 CRUD(jsp)여도 차단된다. */
    @Test
    void approvalRequiredForAllBlocksAnyTableRegardlessOfViewType() {
        CrudGenerationPlanner planner = plannerWithPolicy(List.of(), true);

        CrudGenerationPlan plan = planner.plan(command(DesignContextReference.empty()));

        assertThat(plan.failed()).isTrue();
        assertThat(plan.failure().kind()).isEqualTo(CrudPlanFailure.Kind.MAPPING_BLOCKED);
        verify(crudSchemaQueryService, never()).fetchColumns(any(), any());
    }

    /** APR-B04: 차단 시 감사 이력이 REJECTED 상태·approval-policy 단계로 기록된다. */
    @Test
    void recordsAuditWhenBlockedByPolicy() {
        CrudGenerationPlanner planner = plannerWithPolicy(List.of("LETTNEMPLYRINFO"), false);

        planner.plan(command(DesignContextReference.empty()));

        ArgumentCaptor<GenerationAuditRecord> captor = ArgumentCaptor.forClass(GenerationAuditRecord.class);
        verify(auditPort).append(captor.capture());
        GenerationAuditRecord record = captor.getValue();
        assertThat(record.status()).isEqualTo(GenerationOperationStatus.REJECTED);
        assertThat(record.failureStage()).isEqualTo("approval-policy");
        assertThat(record.tableName()).isEqualTo("LETTNEMPLYRINFO");
    }

    private CrudGenerationPlanner plannerWithPolicy(List<String> highRiskTables, boolean requireForAll) {
        CrudGenerationApprovalProperties properties = new CrudGenerationApprovalProperties();
        properties.setApprovalRequiredTables(highRiskTables);
        properties.setApprovalRequiredForAll(requireForAll);
        CrudGenerationApprovalPolicy policy = new CrudGenerationApprovalPolicy(properties);
        return new CrudGenerationPlanner(
                crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, thymeleafLayoutValidator, routeCollisionDetector,
                null, null, null,
                new PipelineEvolutionProperties(), new PipelineMigrationGuard(),
                new LegacyCompatibilityService(), policy, auditPort);
    }

    private static CrudGenerationCommand command(DesignContextReference designContext) {
        return new CrudGenerationCommand(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                Path.of("/tmp/egov-test"), "auto", "5.0", "jsp",
                LayoutOptions.empty(), ProgramMetadataOverrides.empty(), designContext);
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
