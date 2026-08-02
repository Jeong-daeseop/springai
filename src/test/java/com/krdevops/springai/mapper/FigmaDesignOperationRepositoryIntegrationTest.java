package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.DesignSystemSnapshotRef;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.figma.request.FigmaDesignOperation;
import com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.request.FigmaDesignRequest;
import com.krdevops.springai.model.figma.request.FigmaDesignRequestType;
import com.krdevops.springai.model.figma.request.FigmaDesignScreenRequest;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.figma.FigmaDesignOperationStateService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I-1 완료 게이트: 동일 requestHash 재시도 시 Operation 중복 없음, source revision 불일치 시 CONFLICT,
 * Plugin 보고 전 APPLIED 저장 불가.
 */
class FigmaDesignOperationRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final FigmaDesignOperationStateService stateService = new FigmaDesignOperationStateService();
    private final OperationHashFactory operationHashFactory = new OperationHashFactory(objectMapper);
    private final FigmaDesignOperationRepository repository = new FigmaDesignOperationRepository(
            jdbcTemplate, objectMapper, operationHashFactory, stateService);

    @Test
    void createTableIfNotExistsIsIdempotent() {
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
    }

    @Test
    void retryingSameRequestReusesTheSameOperationWithoutDuplication() {
        repository.createTableIfNotExists();
        FigmaDesignRequest request = request("emp-list-" + UUID.randomUUID());
        FigmaDesignOperation first = repository.createOrReuse(request);
        try {
            FigmaDesignOperation retried = repository.createOrReuse(request);

            assertThat(retried.operationId()).isEqualTo(first.operationId());
            assertThat(retried.revision()).isEqualTo(1);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM AI_FIGMA_DESIGN_OPERATION WHERE OPERATION_ID = ?",
                    Integer.class, first.operationId());
            assertThat(count).isEqualTo(1);
        } finally {
            cleanup(first);
        }
    }

    @Test
    void statusProgressesThroughPreviewAndApplyRequired() {
        repository.createTableIfNotExists();
        FigmaDesignRequest request = request("emp-list-" + UUID.randomUUID());
        FigmaDesignOperation created = repository.createOrReuse(request);
        try {
            assertThat(created.status()).isEqualTo(FigmaDesignOperationStatus.ANALYZED);

            FigmaDesignOperation previewReady = repository.appendTransition(
                    created.operationId(), FigmaDesignOperationStatus.PREVIEW_READY,
                    sourceRevision(request, "rev-1"), List.of(), List.of());
            assertThat(previewReady.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);
            assertThat(previewReady.revision()).isEqualTo(2);

            FigmaDesignOperation applyRequired = repository.appendTransition(
                    previewReady.operationId(), FigmaDesignOperationStatus.APPLY_REQUIRED,
                    sourceRevision(request, "rev-1"), List.of(), List.of());
            assertThat(applyRequired.status()).isEqualTo(FigmaDesignOperationStatus.APPLY_REQUIRED);

            assertThatThrownBy(() -> repository.appendTransition(
                    applyRequired.operationId(), FigmaDesignOperationStatus.APPLIED,
                    sourceRevision(request, "rev-1"), List.of(), List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIGMA_OPERATION_APPLIED_REQUIRES_PLUGIN_REPORT");

            FigmaDesignOperation applied = repository.transitionToApplied(
                    applyRequired.operationId(), true,
                    List.of(new ArtifactRef(
                            "artifact-1", "FIGMA_GENERATION_REPORT", "report/1",
                            "a".repeat(64), Instant.now())));
            assertThat(applied.status()).isEqualTo(FigmaDesignOperationStatus.APPLIED);
            assertThat(applied.artifacts()).hasSize(1);

            assertThat(repository.findAllRevisions(created.operationId())).hasSize(4);
        } finally {
            cleanup(created);
        }
    }

    @Test
    void sourceRevisionMismatchProducesConflictInsteadOfRequestedStatus() {
        repository.createTableIfNotExists();
        FigmaDesignRequest request = request("emp-list-" + UUID.randomUUID());
        FigmaDesignOperation created = repository.createOrReuse(request);
        try {
            FigmaDesignOperation previewReady = repository.appendTransition(
                    created.operationId(), FigmaDesignOperationStatus.PREVIEW_READY,
                    sourceRevision(request, "rev-1"), List.of(), List.of());

            FigmaDesignOperation conflicted = repository.appendTransition(
                    previewReady.operationId(), FigmaDesignOperationStatus.APPLY_REQUIRED,
                    sourceRevision(request, "rev-2-changed"), List.of(), List.of());

            assertThat(conflicted.status()).isEqualTo(FigmaDesignOperationStatus.CONFLICT);
            assertThat(conflicted.sourceRevision().revisionToken()).isEqualTo("rev-2-changed");
        } finally {
            cleanup(created);
        }
    }

    @Test
    void invalidTransitionIsRejectedWithoutWritingARevision() {
        repository.createTableIfNotExists();
        FigmaDesignRequest request = request("emp-list-" + UUID.randomUUID());
        FigmaDesignOperation created = repository.createOrReuse(request);
        try {
            assertThatThrownBy(() -> repository.appendTransition(
                    created.operationId(), FigmaDesignOperationStatus.APPLY_REQUIRED,
                    null, List.of(), List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIGMA_OPERATION_INVALID_TRANSITION");

            assertThat(repository.findAllRevisions(created.operationId())).hasSize(1);
        } finally {
            cleanup(created);
        }
    }

    private FigmaDesignRequest request(String pageId) {
        return new FigmaDesignRequest(
                "req-" + UUID.randomUUID(),
                FigmaDesignRequestType.CREATE_DESIGN_FROM_TEXT,
                null,
                List.of(new FigmaDesignScreenRequest(
                        pageId, "테스트 화면", "직원 목록 화면", null, null, null, List.of(), null, null)),
                new DesignSystemSnapshotRef("krds", "1.0", "2026.07", null),
                Instant.now());
    }

    private SourceRevisionRef sourceRevision(FigmaDesignRequest request, String token) {
        return new SourceRevisionRef(request.screens().get(0).pageId(), token, Instant.now());
    }

    private void cleanup(FigmaDesignOperation created) {
        jdbcTemplate.update(
                "DELETE FROM AI_FIGMA_DESIGN_OPERATION WHERE OPERATION_ID = ?", created.operationId());
        jdbcTemplate.update(
                "DELETE FROM AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY WHERE REQUEST_HASH = ?", created.requestHash());
    }
}
