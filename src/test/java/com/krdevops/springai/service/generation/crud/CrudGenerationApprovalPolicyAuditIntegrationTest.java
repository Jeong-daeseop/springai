package com.krdevops.springai.service.generation.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.CrudGenerationApprovalProperties;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.mapper.GenerationControlPlaneRepository;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import com.krdevops.springai.service.migration.LegacyCompatibilityService;
import com.krdevops.springai.service.migration.PipelineMigrationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * CRUD_명시적_승인_단계_구현목록.md 완료 조건 — 승인 정책으로 차단된 시도의 감사 이력이
 * mock이 아니라 실제 운영 DB({@code AI_GENERATION_OPERATION_AUDIT})에 남고, 같은 방식으로
 * {@code GenerationOperationsController}가 쓰는 {@link GenerationControlPlaneRepository}로
 * 그대로 조회되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class CrudGenerationApprovalPolicyAuditIntegrationTest {

    private static final String OUTPUT_PATH = "/tmp/egov-approval-policy-audit-test";
    private static final String TABLE_NAME = "LETTNEMPLYRINFO";
    private static final String VIEW_TYPE = "jsp";

    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudProgramMetadataService crudProgramMetadataService;
    @Mock GenerationDesignContextService generationDesignContextService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock BoardRouteCollisionDetector routeCollisionDetector;

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            requiredDbPassword()));
    private final GenerationControlPlaneRepository auditRepository =
            new GenerationControlPlaneRepository(jdbc, new ObjectMapper().findAndRegisterModules());
    private final String operationId =
            CrudGenerationOperationIdFactory.forScreen(OUTPUT_PATH, TABLE_NAME, VIEW_TYPE);

    private static String requiredDbPassword() {
        String value = System.getenv("DB_PASSWORD");
        if (value == null || value.isBlank()) throw new IllegalStateException("DB_PASSWORD 환경변수가 필요합니다.");
        return value;
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM AI_GENERATION_OPERATION_AUDIT WHERE OPERATION_ID = ?", operationId);
    }

    @Test
    void 승인_정책_차단_이력이_실제_DB에_기록되고_조회된다() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .willReturn(CrudProgramMetadata.fallback("fallback"));

        CrudGenerationApprovalProperties properties = new CrudGenerationApprovalProperties();
        properties.setApprovalRequiredTables(List.of(TABLE_NAME));
        CrudGenerationApprovalPolicy policy = new CrudGenerationApprovalPolicy(properties);

        CrudGenerationPlanner planner = new CrudGenerationPlanner(
                crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, new ThymeleafLayoutValidator(), routeCollisionDetector,
                null, null, null,
                new PipelineEvolutionProperties(), new PipelineMigrationGuard(),
                new LegacyCompatibilityService(), policy, auditRepository);

        CrudGenerationPlan plan = planner.plan(command());
        assertThat(plan.failed()).isTrue();

        assertThat(auditRepository.findAudits(operationId)).singleElement().satisfies(audit -> {
            assertThat(audit.status()).isEqualTo(GenerationOperationStatus.REJECTED);
            assertThat(audit.failureStage()).isEqualTo("approval-policy");
            assertThat(audit.tableName()).isEqualTo(TABLE_NAME);
            assertThat(audit.projectRoot()).isEqualTo(OUTPUT_PATH);
        });
    }

    private static CrudGenerationCommand command() {
        return new CrudGenerationCommand(
                "com", TABLE_NAME, "Employer", "egovframework.let.emp",
                Path.of(OUTPUT_PATH), "auto", "5.0", VIEW_TYPE,
                LayoutOptions.empty(), ProgramMetadataOverrides.empty(), DesignContextReference.empty());
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
