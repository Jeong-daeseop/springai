package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CrudGenerationSnapshotRepository}가 revision 기반 compare-and-set과 재시작 후 복구를
 * 실제로 제공하는지 실 MySQL로 검증한다. docker start egov-mysql 필요 — `-Pci`에서는 제외된다.
 */
class CrudGenerationSnapshotRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private CrudGenerationSnapshotRepository newRepository() {
        return new CrudGenerationSnapshotRepository(jdbcTemplate, objectMapper);
    }

    private GenerationOwnershipManifest manifest(String suffix) {
        var region = new GenerationOwnershipManifest.Region("generated.file",
                GenerationOwnershipManifest.RegionType.GENERATED, "a".repeat(64));
        var artifact = new GenerationOwnershipManifest.ArtifactOwnership("Employer" + suffix + ".java",
                List.of(region), GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai");
        return GenerationOwnershipManifest.builder("snap-" + suffix).artifacts(List.of(artifact)).build();
    }

    @Test
    void findLatest는_없으면_빈값을_반환한다() {
        String operationId = "crudop-missing-" + UUID.randomUUID();

        assertThat(newRepository().findLatest(operationId)).isEmpty();
    }

    @Test
    void save를_두번_하면_findLatest는_가장_최근_revision을_반환한다() {
        String operationId = "crudop-" + UUID.randomUUID();
        CrudGenerationSnapshotRepository repository = newRepository();

        repository.save(operationId, manifest("V1"));
        repository.save(operationId, manifest("V2"));

        GenerationOwnershipManifest latest = repository.findLatest(operationId).orElseThrow();
        assertThat(latest.artifacts().get(0).artifactPath()).isEqualTo("EmployerV2.java");
    }

    @Test
    void 재시작_시뮬레이션_새_Repository_인스턴스도_이전에_저장된_스냅샷을_본다() {
        String operationId = "crudop-restart-" + UUID.randomUUID();
        newRepository().save(operationId, manifest("Restart"));

        GenerationOwnershipManifest recovered = newRepository().findLatest(operationId).orElseThrow();

        assertThat(recovered.artifacts().get(0).artifactPath()).isEqualTo("EmployerRestart.java");
    }
}
