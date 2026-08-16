package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementConflictStatus;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementOwner;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatch;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatchSet;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPropertyType;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementScope;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MR-S05: Manual Refinement Patch Set 불변 저장소. 같은 {@code patchSetId}에 같은 내용을 다시
 * 저장하면 멱등 허용(no-op), 다른 내용을 저장하면 거부한다. 승인된 원문은 수정하지 않고
 * {@link #transition}으로 상태만 갱신한다(낙관적 잠금).
 */
@Repository
public class FigmaRefinementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public FigmaRefinementRepository(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) return;
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_REFINEMENT_SET (
                PATCH_SET_ID VARCHAR(64) NOT NULL,
                SCREEN_ID VARCHAR(64) NOT NULL,
                BASE_SCREEN_VERSION INT NOT NULL,
                BASE_MATERIALIZATION_HASH VARCHAR(128) NOT NULL,
                REFINEMENT_STATUS VARCHAR(32) NOT NULL,
                CAPTURED_AT DATETIME(6) NOT NULL,
                APPROVED_AT DATETIME(6),
                APPROVED_BY VARCHAR(128),
                APPROVAL_COMMENT VARCHAR(2000),
                CREATED_AT DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                PRIMARY KEY (PATCH_SET_ID),
                INDEX IDX_FIGMA_REFINEMENT_SET_SCREEN (SCREEN_ID, BASE_SCREEN_VERSION, REFINEMENT_STATUS)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_REFINEMENT_PATCH (
                PATCH_SET_ID VARCHAR(64) NOT NULL,
                PATCH_INDEX INT NOT NULL,
                LOGICAL_NODE_ID VARCHAR(512) NOT NULL,
                BASELINE_LOGICAL_TYPE VARCHAR(128) NOT NULL,
                PROPERTY_PATH VARCHAR(128) NOT NULL,
                PROPERTY_TYPE VARCHAR(16) NOT NULL,
                BEFORE_VALUE_JSON TEXT,
                AFTER_VALUE_JSON TEXT,
                PATCH_OWNER VARCHAR(32) NOT NULL,
                PATCH_SCOPE VARCHAR(16) NOT NULL,
                CONFLICT_STATUS VARCHAR(32) NOT NULL DEFAULT 'NONE',
                PRIMARY KEY (PATCH_SET_ID, PATCH_INDEX),
                INDEX IDX_FIGMA_REFINEMENT_PATCH_NODE (PATCH_SET_ID, LOGICAL_NODE_ID)
            )
            """);
    }

    /** 같은 ID+같은 내용은 멱등 허용, 같은 ID+다른 내용은 거부한다(MR-S05). */
    @Transactional
    public void saveImmutable(FigmaRefinementPatchSet patchSet) {
        Optional<FigmaRefinementPatchSet> existing = findById(patchSet.patchSetId());
        if (existing.isPresent()) {
            if (existing.get().equals(patchSet)) return;
            throw new IllegalStateException("REFINEMENT_PATCH_SET_CONFLICT: " + patchSet.patchSetId());
        }
        jdbcTemplate.update("""
            INSERT INTO AI_FIGMA_REFINEMENT_SET
                (PATCH_SET_ID, SCREEN_ID, BASE_SCREEN_VERSION, BASE_MATERIALIZATION_HASH,
                 REFINEMENT_STATUS, CAPTURED_AT, APPROVED_AT, APPROVED_BY, APPROVAL_COMMENT)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                patchSet.patchSetId(), patchSet.screenId(), patchSet.baseScreenVersion(),
                patchSet.baseMaterializationHash(), patchSet.status().name(),
                toTimestamp(patchSet.capturedAt()), toTimestamp(patchSet.approvedAt()),
                patchSet.approvedBy(), patchSet.approvalComment());
        int index = 0;
        for (FigmaRefinementPatch patch : patchSet.patches()) {
            jdbcTemplate.update("""
                INSERT INTO AI_FIGMA_REFINEMENT_PATCH
                    (PATCH_SET_ID, PATCH_INDEX, LOGICAL_NODE_ID, BASELINE_LOGICAL_TYPE, PROPERTY_PATH,
                     PROPERTY_TYPE, BEFORE_VALUE_JSON, AFTER_VALUE_JSON, PATCH_OWNER, PATCH_SCOPE, CONFLICT_STATUS)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    patchSet.patchSetId(), index++, patch.logicalNodeId(), patch.baselineLogicalType(),
                    patch.propertyPath(), patch.propertyType().name(), toJson(patch.before()),
                    toJson(patch.after()), patch.owner().name(), patch.scope().name(),
                    patch.conflictStatus().name());
        }
    }

    public Optional<FigmaRefinementPatchSet> findById(String patchSetId) {
        List<FigmaRefinementPatchSet> results = jdbcTemplate.query("""
            SELECT * FROM AI_FIGMA_REFINEMENT_SET WHERE PATCH_SET_ID = ?
            """, (rs, rowNum) -> mapSet(rs), patchSetId);
        if (results.isEmpty()) return Optional.empty();
        FigmaRefinementPatchSet set = results.get(0);
        return Optional.of(withPatches(set));
    }

    /** MR-R01: 화면의 승인된(가장 최근) Patch Set을 조회한다. APPLIED도 이미 승인된 것으로 취급한다. */
    public Optional<FigmaRefinementPatchSet> findLatestApprovedByScreen(String screenId) {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT PATCH_SET_ID FROM AI_FIGMA_REFINEMENT_SET
             WHERE SCREEN_ID = ? AND REFINEMENT_STATUS IN ('APPROVED', 'APPLIED')
             ORDER BY APPROVED_AT DESC, CREATED_AT DESC LIMIT 1
            """, String.class, screenId);
        return ids.isEmpty() ? Optional.empty() : findById(ids.get(0));
    }

    public List<FigmaRefinementPatchSet> findByScreen(String screenId) {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT PATCH_SET_ID FROM AI_FIGMA_REFINEMENT_SET
             WHERE SCREEN_ID = ? ORDER BY CREATED_AT DESC
            """, String.class, screenId);
        return ids.stream().map(id -> findById(id).orElseThrow()).toList();
    }

    /** MR-S08: 상태 낙관적 잠금 전이. 기대 상태가 아니면 동시 전이로 거부한다. */
    @Transactional
    public FigmaRefinementPatchSet transition(
            String patchSetId, FigmaRefinementStatus expected, FigmaRefinementStatus target,
            String actor, String comment) {
        FigmaRefinementPatchSet current = findById(patchSetId)
                .orElseThrow(() -> new IllegalArgumentException("Refinement Patch Set을 찾을 수 없습니다: " + patchSetId));
        if (current.status() != expected) {
            throw new IllegalStateException("REFINEMENT_INVALID_TRANSITION: " + current.status() + " -> " + target);
        }
        boolean isApproval = target == FigmaRefinementStatus.APPROVED || target == FigmaRefinementStatus.REJECTED;
        LocalDateTime approvedAt = isApproval ? LocalDateTime.now() : current.approvedAt();
        String approvedBy = isApproval ? actor : current.approvedBy();
        String approvalComment = isApproval ? comment : current.approvalComment();
        int updated = jdbcTemplate.update("""
            UPDATE AI_FIGMA_REFINEMENT_SET
               SET REFINEMENT_STATUS = ?, APPROVED_AT = ?, APPROVED_BY = ?, APPROVAL_COMMENT = ?
             WHERE PATCH_SET_ID = ? AND REFINEMENT_STATUS = ?
            """, target.name(), toTimestamp(approvedAt), approvedBy, approvalComment,
                patchSetId, expected.name());
        if (updated != 1) throw new IllegalStateException("REFINEMENT_CONCURRENT_TRANSITION");
        return new FigmaRefinementPatchSet(current.patchSetId(), current.screenId(), current.baseScreenVersion(),
                current.baseMaterializationHash(), target, current.capturedAt(), approvedAt, approvedBy,
                approvalComment, current.patches());
    }

    private FigmaRefinementPatchSet withPatches(FigmaRefinementPatchSet set) {
        List<FigmaRefinementPatch> patches = jdbcTemplate.query("""
            SELECT * FROM AI_FIGMA_REFINEMENT_PATCH WHERE PATCH_SET_ID = ? ORDER BY PATCH_INDEX ASC
            """, (rs, rowNum) -> new FigmaRefinementPatch(
                    rs.getString("LOGICAL_NODE_ID"), rs.getString("BASELINE_LOGICAL_TYPE"),
                    rs.getString("PROPERTY_PATH"),
                    FigmaRefinementPropertyType.valueOf(rs.getString("PROPERTY_TYPE")),
                    fromJson(rs.getString("BEFORE_VALUE_JSON")), fromJson(rs.getString("AFTER_VALUE_JSON")),
                    FigmaRefinementOwner.valueOf(rs.getString("PATCH_OWNER")),
                    FigmaRefinementScope.valueOf(rs.getString("PATCH_SCOPE")),
                    FigmaRefinementConflictStatus.valueOf(rs.getString("CONFLICT_STATUS"))),
                set.patchSetId());
        return new FigmaRefinementPatchSet(set.patchSetId(), set.screenId(), set.baseScreenVersion(),
                set.baseMaterializationHash(), set.status(), set.capturedAt(), set.approvedAt(),
                set.approvedBy(), set.approvalComment(), patches);
    }

    private FigmaRefinementPatchSet mapSet(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FigmaRefinementPatchSet(
                rs.getString("PATCH_SET_ID"), rs.getString("SCREEN_ID"), rs.getInt("BASE_SCREEN_VERSION"),
                rs.getString("BASE_MATERIALIZATION_HASH"),
                FigmaRefinementStatus.valueOf(rs.getString("REFINEMENT_STATUS")),
                toLocalDateTime(rs.getTimestamp("CAPTURED_AT")), toLocalDateTime(rs.getTimestamp("APPROVED_AT")),
                rs.getString("APPROVED_BY"), rs.getString("APPROVAL_COMMENT"), List.of());
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Refinement Patch 값 JSON 직렬화 실패", exception); }
    }

    private Object fromJson(String value) {
        if (value == null) return null;
        try { return objectMapper.readValue(value, Object.class); }
        catch (Exception exception) { throw new IllegalStateException("Refinement Patch 값 JSON 역직렬화 실패", exception); }
    }
}
