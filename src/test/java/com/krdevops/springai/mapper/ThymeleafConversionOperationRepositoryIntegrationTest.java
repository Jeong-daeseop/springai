package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.thymeleaf.ThymeleafConversionOperationStateService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** I-5B 완료 게이트: 동일 계약 재시도 시 Operation 중복 없음, APPLIED/VALIDATED는 전용 메서드로만. */
class ThymeleafConversionOperationRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ThymeleafConversionOperationStateService stateService = new ThymeleafConversionOperationStateService();
    private final OperationHashFactory operationHashFactory = new OperationHashFactory(objectMapper);
    private final ThymeleafConversionOperationRepository repository = new ThymeleafConversionOperationRepository(
            jdbcTemplate, objectMapper, operationHashFactory, stateService);

    @Test
    void createTableIfNotExistsIsIdempotent() {
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
    }

    @Test
    void retryingSameContractReusesTheSameOperationWithoutDuplication() {
        repository.createTableIfNotExists();
        ThymeleafBindingContract contract = contract("emp-list-" + UUID.randomUUID());
        ThymeleafConversionOperation first = repository.createOrReuse(contract, "employer/EgovEmployerList.html");
        try {
            ThymeleafConversionOperation retried = repository.createOrReuse(contract, "employer/EgovEmployerList.html");

            assertThat(retried.operationId()).isEqualTo(first.operationId());
            assertThat(retried.revision()).isEqualTo(1);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM AI_THYMELEAF_CONVERSION_OPERATION WHERE OPERATION_ID = ?",
                    Integer.class, first.operationId());
            assertThat(count).isEqualTo(1);
        } finally {
            cleanup(first);
        }
    }

    @Test
    void statusProgressesThroughFullPipelineToValidated() {
        repository.createTableIfNotExists();
        ThymeleafBindingContract contract = contract("emp-list-" + UUID.randomUUID());
        ThymeleafConversionOperation created = repository.createOrReuse(contract, "employer/EgovEmployerList.html");
        try {
            assertThat(created.status()).isEqualTo(ThymeleafConversionOperationStatus.ANALYZED);

            ThymeleafConversionOperation contractReady = repository.appendTransition(
                    created.operationId(), ThymeleafConversionOperationStatus.CONTRACT_READY,
                    contract, null, null, List.of(), List.of());
            ThymeleafConversionOperation previewReady = repository.appendTransition(
                    contractReady.operationId(), ThymeleafConversionOperationStatus.PREVIEW_READY,
                    null, "<html></html>", null, List.of(), List.of());
            ThymeleafConversionOperation approved = repository.appendTransition(
                    previewReady.operationId(), ThymeleafConversionOperationStatus.APPROVED,
                    null, null, null, List.of(), List.of());

            assertThatThrownBy(() -> repository.appendTransition(
                    approved.operationId(), ThymeleafConversionOperationStatus.APPLIED,
                    null, null, null, List.of(), List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REQUIRES_EXPLICIT_EVIDENCE");

            ThymeleafConversionOperation applied = repository.transitionToApplied(
                    approved.operationId(), true,
                    List.of(new ArtifactRef("artifact-1", "THYMELEAF_APPLY_BACKUP", "backup/1",
                            "a".repeat(64), Instant.now())));
            assertThat(applied.status()).isEqualTo(ThymeleafConversionOperationStatus.APPLIED);

            ThymeleafConversionOperation validated = repository.transitionToValidated(
                    applied.operationId(), true, List.of());
            assertThat(validated.status()).isEqualTo(ThymeleafConversionOperationStatus.VALIDATED);

            assertThat(repository.findAllRevisions(created.operationId())).hasSize(6);
        } finally {
            cleanup(created);
        }
    }

    private ThymeleafBindingContract contract(String screenId) {
        return new ThymeleafBindingContract(
                screenId, LegacyScreenRole.LIST,
                new ThymeleafRouteBinding("/emp/employerList.do", "GET", "selectEmployerList",
                        "searchVO", "EmployerVO", false, false, List.of()),
                List.of(), List.of(), null, List.of(), List.of(),
                BindingContractStatus.RESOLVED, List.of(),
                new SourceRevisionRef("emp-project", "rev-1", Instant.now()), Instant.now());
    }

    private void cleanup(ThymeleafConversionOperation created) {
        jdbcTemplate.update(
                "DELETE FROM AI_THYMELEAF_CONVERSION_OPERATION WHERE OPERATION_ID = ?", created.operationId());
        jdbcTemplate.update(
                "DELETE FROM AI_THYMELEAF_CONVERSION_OPERATION_IDEMPOTENCY WHERE OPERATION_ID = ?",
                created.operationId());
    }
}
