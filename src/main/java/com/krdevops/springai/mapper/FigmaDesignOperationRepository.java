package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.figma.FigmaDesignOperationStateService;
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
import com.krdevops.springai.service.observability.OperationalTelemetry;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * R1-014/R1-029: {@link FigmaDesignOperation}을 revision 단위로 불변 저장하고,
 * requestHash 기반 멱등 재사용과 source revision 낙관적 충돌(CONFLICT) 판정을 담당한다.
 * {@code (OPERATION_ID, REVISION)} 기본 키가 동시 쓰기 경쟁을 차단하므로, 경쟁에서 진
 * 쪽은 {@code FIGMA_OPERATION_REVISION_CONFLICT}로 실패한다(멀티 화면 Operation도 상태가
 * 하나뿐이라 원자적으로 전이된다).
 */
@Slf4j
@Repository
public class FigmaDesignOperationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OperationHashFactory operationHashFactory;
    private final FigmaDesignOperationStateService stateService;
    private final LegacyRepositoryDdlProperties ddlProperties;
    private OperationalTelemetry telemetry;

    public FigmaDesignOperationRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            OperationHashFactory operationHashFactory,
            FigmaDesignOperationStateService stateService,
            LegacyRepositoryDdlProperties ddlProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.operationHashFactory = operationHashFactory;
        this.stateService = stateService;
        this.ddlProperties = ddlProperties;
    }

    @Autowired
    void configureTelemetry(OperationalTelemetry telemetry) { this.telemetry = telemetry; }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) {
            return;
        }
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_DESIGN_OPERATION (
                OPERATION_ID     VARCHAR(64) NOT NULL,
                REVISION         INT NOT NULL,
                STATUS           VARCHAR(32) NOT NULL,
                OPERATION_JSON   LONGTEXT NOT NULL,
                CREATED_AT       DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (OPERATION_ID, REVISION)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY (
                REQUEST_HASH     VARCHAR(128) NOT NULL,
                OPERATION_ID     VARCHAR(64) NOT NULL,
                CREATED_AT       DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (REQUEST_HASH)
            )
            """);
        log.info("AI_FIGMA_DESIGN_OPERATION / AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY 테이블 초기화 완료");
    }

    /** 동일 requestHash로 재시도해도 새 Operation을 만들지 않고 기존 Operation을 반환한다. */
    public FigmaDesignOperation createOrReuse(FigmaDesignRequest request) {
        return createOrReuse(request, null);
    }

    /** 화면명세/Profile/Viewport 같은 실행 문맥까지 멱등 hash에 포함한다. */
    public FigmaDesignOperation createOrReuse(FigmaDesignRequest request, Object idempotencyContext) {
        Map<String, Object> hashView = new LinkedHashMap<>();
        hashView.put("request", canonicalRequestView(request));
        hashView.put("context", idempotencyContext);
        String hash = operationHashFactory.canonicalHash(hashView);

        Optional<String> existingOperationId = findOperationIdByHash(hash);
        if (existingOperationId.isPresent()) {
            return findLatest(existingOperationId.get()).orElseThrow(() -> idempotencyIndexCorrupt(hash));
        }

        String operationId = "figop-" + UUID.randomUUID();
        Instant now = Instant.now();
        FigmaDesignOperation initial = new FigmaDesignOperation(
                operationId, 1, request, hash, FigmaDesignOperationStatus.ANALYZED,
                null, List.of(), List.of(), now, now);

        try {
            jdbcTemplate.update("""
                INSERT INTO AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY (REQUEST_HASH, OPERATION_ID)
                VALUES (?, ?)
                """, hash, operationId);
        } catch (DuplicateKeyException exception) {
            String winnerOperationId = findOperationIdByHash(hash).orElseThrow(() -> idempotencyIndexCorrupt(hash));
            return findLatest(winnerOperationId).orElseThrow(() -> idempotencyIndexCorrupt(hash));
        }

        FigmaDesignOperation saved = insertRevision(initial);
        recordTransition(saved.operationId(), null, saved.status(), "CREATED");
        return saved;
    }

    /**
     * 다음 상태로 전이한다. 이전에 기록된 sourceRevision이 있고 새로 관측한 값과 다르면
     * 요청한 상태 대신 {@code CONFLICT}로 기록한다(§I-1 완료 게이트: source revision 불일치 시 CONFLICT).
     * {@code APPLIED}로는 전이할 수 없다 — {@link #transitionToApplied}만 사용할 수 있다.
     */
    public FigmaDesignOperation appendTransition(
            String operationId,
            FigmaDesignOperationStatus requestedStatus,
            SourceRevisionRef observedSourceRevision,
            List<GenerationIssue> issues,
            List<ArtifactRef> artifacts
    ) {
        FigmaDesignOperation current = findLatest(operationId)
                .orElseThrow(() -> operationNotFound(operationId));

        if (hasSourceRevisionConflict(current, observedSourceRevision)) {
            stateService.assertTransitionAllowed(current.status(), FigmaDesignOperationStatus.CONFLICT);
            FigmaDesignOperation saved = insertRevision(current.withNextRevision(
                    FigmaDesignOperationStatus.CONFLICT, observedSourceRevision, issues, artifacts));
            recordTransition(operationId, current.status(), saved.status(), "CONFLICT");
            return saved;
        }

        stateService.assertTransitionAllowed(current.status(), requestedStatus);
        FigmaDesignOperation saved = insertRevision(
                current.withNextRevision(requestedStatus, observedSourceRevision, issues, artifacts));
        recordTransition(operationId, current.status(), saved.status(), requestedStatus.name());
        return saved;
    }

    /** 실제 Plugin 적용 보고를 받은 뒤에만 호출할 수 있다({@code pluginReportReceived=true}). */
    public FigmaDesignOperation transitionToApplied(
            String operationId, boolean pluginReportReceived, List<ArtifactRef> artifacts) {
        FigmaDesignOperation current = findLatest(operationId)
                .orElseThrow(() -> operationNotFound(operationId));
        stateService.assertTransitionToAppliedAllowed(current.status(), pluginReportReceived);
        FigmaDesignOperation saved = insertRevision(current.withNextRevision(
                FigmaDesignOperationStatus.APPLIED, current.sourceRevision(), current.issues(), artifacts));
        recordTransition(operationId, current.status(), saved.status(), "APPLIED");
        return saved;
    }

    /**
     * 22/23번 문서 A-01c: {@code AWAITING_TABLE_BINDING}에서 {@code bindFigmaDesignRequestTable}로
     * database/tableName을 채운 request로 다음 revision에 전이한다. 해시는 {@code
     * canonicalRequestView()} 기준으로 재계산하되, 그 뷰는 database/tableName을 포함하지 않으므로
     * (사용자 확인 후 확정 — 테이블 바인딩은 "동일 디자인 요청"의 정체성을 바꾸지 않는다는 판단)
     * 이 흐름에서는 보통 해시가 바뀌지 않는다. 해시가 실제로 바뀐 경우에만 멱등성 테이블에 새 행을
     * 추가한다(기존 해시 행은 유지 — 원래 요청으로 재조회해도 여전히 같은 Operation을 가리켜야
     * 함). 새 해시가 이미 다른 Operation에 등록돼 있으면 기존 {@code createOrReuse}와 동일하게
     * 그 Operation을 그대로 반환한다.
     */
    public FigmaDesignOperation appendTransitionWithRequest(
            String operationId,
            FigmaDesignRequest nextRequest,
            FigmaDesignOperationStatus requestedStatus,
            List<GenerationIssue> issues,
            List<ArtifactRef> artifacts
    ) {
        FigmaDesignOperation current = findLatest(operationId)
                .orElseThrow(() -> operationNotFound(operationId));
        stateService.assertTransitionAllowed(current.status(), requestedStatus);

        Map<String, Object> hashView = new LinkedHashMap<>();
        hashView.put("request", canonicalRequestView(nextRequest));
        hashView.put("context", null);
        String nextHash = operationHashFactory.canonicalHash(hashView);

        if (!nextHash.equals(current.hash())) {
            try {
                jdbcTemplate.update("""
                    INSERT INTO AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY (REQUEST_HASH, OPERATION_ID)
                    VALUES (?, ?)
                    """, nextHash, operationId);
            } catch (DuplicateKeyException exception) {
                String winnerOperationId = findOperationIdByHash(nextHash)
                        .orElseThrow(() -> idempotencyIndexCorrupt(nextHash));
                return findLatest(winnerOperationId).orElseThrow(() -> idempotencyIndexCorrupt(nextHash));
            }
        }

        FigmaDesignOperation saved = insertRevision(current.withRequestAndNextRevision(
                nextRequest, nextHash, requestedStatus, current.sourceRevision(), issues, artifacts));
        recordTransition(operationId, current.status(), saved.status(), requestedStatus.name());
        return saved;
    }

    public Optional<FigmaDesignOperation> findLatest(String operationId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT OPERATION_JSON FROM AI_FIGMA_DESIGN_OPERATION
             WHERE OPERATION_ID = ? ORDER BY REVISION DESC LIMIT 1
            """, String.class, operationId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public Optional<FigmaDesignOperation> findRevision(String operationId, int revision) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT OPERATION_JSON FROM AI_FIGMA_DESIGN_OPERATION
             WHERE OPERATION_ID = ? AND REVISION = ?
            """, String.class, operationId, revision);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public List<FigmaDesignOperation> findAllRevisions(String operationId) {
        return jdbcTemplate.queryForList("""
            SELECT OPERATION_JSON FROM AI_FIGMA_DESIGN_OPERATION
             WHERE OPERATION_ID = ? ORDER BY REVISION ASC
            """, String.class, operationId).stream().map(this::fromJson).toList();
    }

    private boolean hasSourceRevisionConflict(FigmaDesignOperation current, SourceRevisionRef observed) {
        return current.sourceRevision() != null && observed != null
                && !current.sourceRevision().revisionToken().equals(observed.revisionToken());
    }

    private Object canonicalRequestView(FigmaDesignRequest request) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", request.type());
        view.put("prompt", request.prompt());
        view.put("fileKey", request.fileKey());
        view.put("referenceNodeIds", request.referenceNodeIds());
        view.put("editableNodeIds", request.editableNodeIds());
        view.put("imageNodeIds", request.imageNodeIds());
        view.put("targetPlatform", request.targetPlatform());
        view.put("components", request.components());
        view.put("screens", request.screens());
        return view;
    }

    private FigmaDesignOperation insertRevision(FigmaDesignOperation operation) {
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_FIGMA_DESIGN_OPERATION (OPERATION_ID, REVISION, STATUS, OPERATION_JSON)
                VALUES (?, ?, ?, ?)
                """, operation.operationId(), operation.revision(), operation.status().name(), toJson(operation));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException(
                    "FIGMA_OPERATION_REVISION_CONFLICT: 동시 갱신으로 revision이 이미 존재합니다: "
                            + operation.operationId() + "/" + operation.revision(), exception);
        }
        return operation;
    }

    private void recordTransition(String operationId, FigmaDesignOperationStatus from,
                                  FigmaDesignOperationStatus to, String event) {
        if (telemetry != null) telemetry.operationTransition(operationId, "FIGMA_DESIGN",
                from == null ? null : from.name(), to.name(), event);
    }

    private Optional<String> findOperationIdByHash(String hash) {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT OPERATION_ID FROM AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY WHERE REQUEST_HASH = ?
            """, String.class, hash);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private IllegalArgumentException operationNotFound(String operationId) {
        return new IllegalArgumentException("FIGMA_OPERATION_NOT_FOUND: " + operationId);
    }

    private IllegalStateException idempotencyIndexCorrupt(String hash) {
        return new IllegalStateException("FIGMA_OPERATION_IDEMPOTENCY_INDEX_CORRUPT: " + hash);
    }

    private String toJson(FigmaDesignOperation operation) {
        try {
            return objectMapper.writeValueAsString(operation);
        } catch (Exception exception) {
            throw new IllegalStateException("FigmaDesignOperation JSON 직렬화 실패", exception);
        }
    }

    private FigmaDesignOperation fromJson(String json) {
        try {
            return objectMapper.readValue(json, FigmaDesignOperation.class);
        } catch (Exception exception) {
            throw new IllegalStateException("FigmaDesignOperation JSON 역직렬화 실패", exception);
        }
    }
}
