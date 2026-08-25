package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.controlplane.GenerationAuditRecord;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class GenerationControlPlaneRepositoryIntegrationTest {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            requiredDbPassword()));
    private final GenerationControlPlaneRepository repository =
            new GenerationControlPlaneRepository(jdbc, new ObjectMapper().findAndRegisterModules());
    private final String operationId = "control-plane-test-" + UUID.randomUUID();

    private static String requiredDbPassword() {
        String value = System.getenv("DB_PASSWORD");
        if (value == null || value.isBlank()) throw new IllegalStateException("DB_PASSWORD 환경변수가 필요합니다.");
        return value;
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM AI_GENERATION_VALIDATION_EVIDENCE WHERE OPERATION_ID = ?", operationId);
        jdbc.update("DELETE FROM AI_GENERATION_OPERATION_AUDIT WHERE OPERATION_ID = ?", operationId);
    }

    @Test
    void 감사_이력과_검증_증적을_독립적으로_저장하고_조회한다() {
        repository.append(new GenerationAuditRecord(UUID.randomUUID().toString(), operationId, 0,
                "/project", "EMP", "emp", "REST", "tester", "test",
                List.of("A::r1"), List.of("A::r2"), List.of("A::r3"), List.of("A.java"),
                GenerationOperationStatus.CONFLICT, "ownership-guard", "동시 변경", Instant.now()));
        repository.append(new ValidationEvidence(UUID.randomUUID().toString(), operationId,
                ValidationEvidence.GateType.BUILD, ValidationEvidence.Status.PASSED,
                ValidationEvidence.Severity.INFO, List.of("src"), List.of("build"),
                null, "1", "test-v1", Instant.now()));

        assertThat(repository.findAudits(operationId)).singleElement().satisfies(audit -> {
            assertThat(audit.operationRevision()).isEqualTo(1);
            assertThat(audit.conflictRegionIds()).containsExactly("A::r3");
            assertThat(audit.status()).isEqualTo(GenerationOperationStatus.CONFLICT);
        });
        assertThat(repository.findEvidence(operationId)).singleElement().satisfies(evidence -> {
            assertThat(evidence.gateType()).isEqualTo(ValidationEvidence.GateType.BUILD);
            assertThat(evidence.status()).isEqualTo(ValidationEvidence.Status.PASSED);
        });
        var crudMetrics = new GenerationControlPlaneMetricsRepository(jdbc).load().crud();
        assertThat(crudMetrics.callerTypeCounts()).containsEntry("REST", 1L);
        assertThat(crudMetrics.actorCounts()).containsEntry("tester", 1L);
        assertThat(crudMetrics.environmentCounts()).containsEntry("test", 1L);
        assertThat(crudMetrics.projectCounts()).containsEntry("/project", 1L);
        assertThat(crudMetrics.screenCounts()).containsEntry("emp", 1L);
    }
}
