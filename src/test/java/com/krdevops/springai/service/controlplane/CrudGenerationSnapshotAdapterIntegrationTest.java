package com.krdevops.springai.service.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.CrudGenerationSnapshotRepository;
import com.krdevops.springai.mapper.GenerationControlPlaneRepository;
import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.ApprovalMode;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class CrudGenerationSnapshotAdapterIntegrationTest {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            requiredDbPassword()));
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String operationId = "adapter-test-" + UUID.randomUUID();

    private static String requiredDbPassword() {
        String value = System.getenv("DB_PASSWORD");
        if (value == null || value.isBlank()) throw new IllegalStateException("DB_PASSWORD 환경변수가 필요합니다.");
        return value;
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM AI_CRUD_GENERATION_SNAPSHOT WHERE OPERATION_ID = ?", operationId);
    }

    @Test
    void 기존_Snapshot을_조회해도_감사와_검증_테이블에는_아무것도_쓰지_않는다() {
        var snapshotRepository = new CrudGenerationSnapshotRepository(jdbc, mapper);
        var region = new GenerationOwnershipManifest.Region("generated.body",
                GenerationOwnershipManifest.RegionType.GENERATED, "a".repeat(64));
        var artifact = new GenerationOwnershipManifest.ArtifactOwnership("Employer.java", List.of(region),
                GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai");
        snapshotRepository.save(operationId,
                GenerationOwnershipManifest.builder(operationId).artifacts(List.of(artifact)).build());
        var commonRepository = new GenerationControlPlaneRepository(jdbc, mapper);
        var adapter = new CrudGenerationSnapshotAdapter(jdbc, mapper, commonRepository, commonRepository);

        var projected = adapter.find(operationId).orElseThrow();

        assertThat(projected.sourceTable()).isEqualTo("AI_CRUD_GENERATION_SNAPSHOT");
        assertThat(projected.sourcePrimaryKey()).isEqualTo(operationId + "/1");
        assertThat(projected.sourceStatus()).isEqualTo("APPLIED");
        assertThat(projected.sourceRevision()).isEqualTo("1");
        assertThat(projected.approvalMode()).isEqualTo(ApprovalMode.AUTOMATED_OWNERSHIP_CHECK);
        assertThat(projected.writePolicy()).isEqualTo(ProjectWritePolicy.ATOMIC_APPROVED);
        assertThat(projected.changedFiles()).containsExactly("Employer.java");
        assertThat(projected.auditRecordingStatus()).isEqualTo(EvidenceRecordingStatus.NOT_RECORDED);
        assertThat(projected.validationEvidenceStatus()).isEqualTo(EvidenceRecordingStatus.NOT_RECORDED);
        assertThat(commonRepository.findAudits(operationId)).isEmpty();
        assertThat(commonRepository.findEvidence(operationId)).isEmpty();
    }
}
