package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.thymeleaf.ThymeleafConversionOperationStateService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * I-5B: {@link ThymeleafConversionOperation}을 revision 단위로 불변 저장한다.
 * 같은 (screenId+targetRelativePath+contract 내용)으로 재시도해도 Operation이 중복 생성되지
 * 않는다. source revision 충돌 판정은 실제 JSP/Controller/VO 재읽기가 필요해 이 Repository가
 * 아니라 호출자(Orchestration Service)의 책임이다 — 이 Repository는 결정된 상태를 저장만 한다.
 */
@Slf4j
@Repository
public class ThymeleafConversionOperationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OperationHashFactory operationHashFactory;
    private final ThymeleafConversionOperationStateService stateService;

    public ThymeleafConversionOperationRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            OperationHashFactory operationHashFactory,
            ThymeleafConversionOperationStateService stateService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.operationHashFactory = operationHashFactory;
        this.stateService = stateService;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_THYMELEAF_CONVERSION_OPERATION (
                OPERATION_ID     VARCHAR(64) NOT NULL,
                REVISION         INT NOT NULL,
                STATUS           VARCHAR(32) NOT NULL,
                OPERATION_JSON   LONGTEXT NOT NULL,
                CREATED_AT       DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (OPERATION_ID, REVISION)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_THYMELEAF_CONVERSION_OPERATION_IDEMPOTENCY (
                REQUEST_HASH     VARCHAR(128) NOT NULL,
                OPERATION_ID     VARCHAR(64) NOT NULL,
                CREATED_AT       DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (REQUEST_HASH)
            )
            """);
        log.info("AI_THYMELEAF_CONVERSION_OPERATION / _IDEMPOTENCY 테이블 초기화 완료");
    }

    public ThymeleafConversionOperation createOrReuse(ThymeleafBindingContract contract, String targetRelativePath) {
        String hash = operationHashFactory.canonicalHash(canonicalView(contract, targetRelativePath));

        Optional<String> existingOperationId = findOperationIdByHash(hash);
        if (existingOperationId.isPresent()) {
            return findLatest(existingOperationId.get()).orElseThrow(() -> idempotencyIndexCorrupt(hash));
        }

        String operationId = "thyop-" + UUID.randomUUID();
        Instant now = Instant.now();
        ThymeleafConversionOperation initial = new ThymeleafConversionOperation(
                operationId, 1, contract.screenId(), contract.screenRole(),
                ThymeleafConversionOperationStatus.ANALYZED, targetRelativePath,
                contract, null, contract.sourceRevision(), List.of(), List.of(), now, now);

        try {
            jdbcTemplate.update("""
                INSERT INTO AI_THYMELEAF_CONVERSION_OPERATION_IDEMPOTENCY (REQUEST_HASH, OPERATION_ID)
                VALUES (?, ?)
                """, hash, operationId);
        } catch (DuplicateKeyException exception) {
            String winnerOperationId = findOperationIdByHash(hash).orElseThrow(() -> idempotencyIndexCorrupt(hash));
            return findLatest(winnerOperationId).orElseThrow(() -> idempotencyIndexCorrupt(hash));
        }

        return insertRevision(initial);
    }

    /** {@code APPLIED}/{@code VALIDATED}로는 전이할 수 없다 — 전용 메서드만 사용할 수 있다. */
    public ThymeleafConversionOperation appendTransition(
            String operationId,
            ThymeleafConversionOperationStatus nextStatus,
            ThymeleafBindingContract nextContract,
            String nextRenderedHtml,
            SourceRevisionRef nextSourceRevision,
            List<GenerationIssue> issues,
            List<ArtifactRef> artifacts
    ) {
        ThymeleafConversionOperation current = findLatest(operationId)
                .orElseThrow(() -> operationNotFound(operationId));
        stateService.assertTransitionAllowed(current.status(), nextStatus);
        return insertRevision(current.withNextRevision(
                nextStatus, nextContract, nextRenderedHtml, nextSourceRevision, issues, artifacts));
    }

    public ThymeleafConversionOperation transitionToApplied(
            String operationId, boolean fileWritten, List<ArtifactRef> artifacts) {
        ThymeleafConversionOperation current = findLatest(operationId)
                .orElseThrow(() -> operationNotFound(operationId));
        stateService.assertTransitionToAppliedAllowed(current.status(), fileWritten);
        return insertRevision(current.withNextRevision(
                ThymeleafConversionOperationStatus.APPLIED, null, null, null, current.issues(), artifacts));
    }

    public ThymeleafConversionOperation transitionToValidated(
            String operationId, boolean postApplyValidationPassed, List<GenerationIssue> issues) {
        ThymeleafConversionOperation current = findLatest(operationId)
                .orElseThrow(() -> operationNotFound(operationId));
        stateService.assertTransitionToValidatedAllowed(current.status(), postApplyValidationPassed);
        return insertRevision(current.withNextRevision(
                ThymeleafConversionOperationStatus.VALIDATED, null, null, null, issues, current.artifacts()));
    }

    public Optional<ThymeleafConversionOperation> findLatest(String operationId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT OPERATION_JSON FROM AI_THYMELEAF_CONVERSION_OPERATION
             WHERE OPERATION_ID = ? ORDER BY REVISION DESC LIMIT 1
            """, String.class, operationId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public List<ThymeleafConversionOperation> findAllRevisions(String operationId) {
        return jdbcTemplate.queryForList("""
            SELECT OPERATION_JSON FROM AI_THYMELEAF_CONVERSION_OPERATION
             WHERE OPERATION_ID = ? ORDER BY REVISION ASC
            """, String.class, operationId).stream().map(this::fromJson).toList();
    }

    private Object canonicalView(ThymeleafBindingContract contract, String targetRelativePath) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("screenId", contract.screenId());
        view.put("screenRole", contract.screenRole());
        view.put("route", contract.route());
        view.put("fields", contract.fields());
        view.put("displayFieldNames", contract.displayFieldNames());
        view.put("primaryDisplayAttributeName", contract.primaryDisplayAttributeName());
        view.put("targetRelativePath", targetRelativePath);
        return view;
    }

    private ThymeleafConversionOperation insertRevision(ThymeleafConversionOperation operation) {
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_THYMELEAF_CONVERSION_OPERATION (OPERATION_ID, REVISION, STATUS, OPERATION_JSON)
                VALUES (?, ?, ?, ?)
                """, operation.operationId(), operation.revision(), operation.status().name(), toJson(operation));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_REVISION_CONFLICT: 동시 갱신으로 revision이 이미 존재합니다: "
                            + operation.operationId() + "/" + operation.revision(), exception);
        }
        return operation;
    }

    private Optional<String> findOperationIdByHash(String hash) {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT OPERATION_ID FROM AI_THYMELEAF_CONVERSION_OPERATION_IDEMPOTENCY WHERE REQUEST_HASH = ?
            """, String.class, hash);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private IllegalArgumentException operationNotFound(String operationId) {
        return new IllegalArgumentException("THYMELEAF_OPERATION_NOT_FOUND: " + operationId);
    }

    private IllegalStateException idempotencyIndexCorrupt(String hash) {
        return new IllegalStateException("THYMELEAF_OPERATION_IDEMPOTENCY_INDEX_CORRUPT: " + hash);
    }

    private String toJson(ThymeleafConversionOperation operation) {
        try {
            return objectMapper.writeValueAsString(operation);
        } catch (Exception exception) {
            throw new IllegalStateException("ThymeleafConversionOperation JSON 직렬화 실패", exception);
        }
    }

    private ThymeleafConversionOperation fromJson(String json) {
        try {
            return objectMapper.readValue(json, ThymeleafConversionOperation.class);
        } catch (Exception exception) {
            throw new IllegalStateException("ThymeleafConversionOperation JSON 역직렬화 실패", exception);
        }
    }
}
