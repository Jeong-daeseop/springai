package com.krdevops.springai.service;

import com.krdevops.springai.service.TableRelationService.RelationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// RAG 문서 검색은 QuestionAnswerAdvisor(EgovRagConfig)가 담당.
// ContextAssembler는 DB 스키마·생성 이력·테이블 관계만 조립한다.

/**
 * RAG 결과·DB 스키마·테이블 관계·생성 이력을 하나의 컨텍스트 블록으로 통합합니다.
 *
 * [우선순위 (토큰 예산 초과 시 하위 항목부터 생략)]
 *   1순위: DB 스키마 — 컬럼·PK·타입 (CRUD 생성 필수)
 *   2순위: 이전 생성 이력 — 도메인·패키지명 재활용 (일관성)
 *   3순위: 테이블 관계 — FK·암묵적 JOIN 후보 (참고)
 *   4순위: RAG 문서 — eGovFrame 표준 가이드 (참고)
 *
 * [테이블명 자동 감지]
 *   eGovFrame 표준 테이블 접두사 패턴: COMTN / COMTC / COMTH / LETGW
 *   사용자 질문에서 이 패턴과 일치하는 토큰을 추출하여 스키마 조회에 사용합니다.
 *   감지 실패 시 스키마·관계·이력 조회를 건너뛰고 RAG만 반환합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextAssembler {

    /**
     * 컨텍스트 최대 문자 수. Ollama mistral 4096 토큰 기준, 시스템 역할·사용자 질문
     * 여유분을 제외한 안전 예산 (한/영 혼재 기준 1토큰 ≈ 2~3자).
     */
    private static final int MAX_CONTEXT_CHARS = 6_000;

    private static final String DEFAULT_DATABASE = "com";

    /**
     * eGovFrame 표준 테이블 접두사 패턴.
     * COMTN / COMTC / COMTH / LETGW 로 시작하는 6자 이상 대문자+숫자+밑줄 조합.
     */
    private static final Pattern EGOV_TABLE_PATTERN =
        Pattern.compile("\\b(COMTN|COMTC|COMTH|LETGW)[A-Z0-9_]{2,}\\b");

    private final SchemaService schemaService;
    private final TableRelationService tableRelationService;
    private final GenerationHistoryService generationHistoryService;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 사용자 쿼리를 분석하여 DB 스키마·생성 이력·테이블 관계를 통합한 컨텍스트 문자열을 반환합니다.
     * RAG 문서 검색은 QuestionAnswerAdvisor가 별도로 처리하므로 여기서는 제외됩니다.
     *
     * @param query 사용자 질문 (테이블명 자동 감지)
     * @return 통합 컨텍스트 블록 (빈 문자열이면 컨텍스트 없음)
     */
    public String build(String query) {
        String tableName = extractTableName(query);
        log.debug("ContextAssembler - 감지된 테이블: {}, 쿼리: {}", tableName,
            query.length() > 60 ? query.substring(0, 60) + "..." : query);

        StringBuilder ctx = new StringBuilder();

        if (tableName != null) {
            // 1순위: 스키마
            appendSchema(ctx, tableName);

            // 2순위: 이전 생성 이력
            appendHistory(ctx, tableName);

            // 3순위: 테이블 관계
            appendRelations(ctx, tableName);
        }

        String result = ctx.toString().trim();
        log.info("ContextAssembler 완료 - table={}, chars={}", tableName, result.length());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 각 정보원 추가 (예산 초과 시 건너뜀)
    // ─────────────────────────────────────────────────────────────────────────

    private void appendSchema(StringBuilder ctx, String tableName) {
        if (ctx.length() >= MAX_CONTEXT_CHARS) return;

        try {
            String schema = schemaService.getTableSchema(DEFAULT_DATABASE, tableName);
            if (schema.startsWith("테이블을 찾을 수 없습니다")) return;

            String block = "[스키마] " + tableName + "\n" + schema + "\n";
            append(ctx, block);
        } catch (Exception e) {
            log.warn("스키마 조회 실패: table={}, {}", tableName, e.getMessage());
        }
    }

    private void appendHistory(StringBuilder ctx, String tableName) {
        if (ctx.length() >= MAX_CONTEXT_CHARS) return;

        try {
            String summary = generationHistoryService.getRecentSummary(tableName);
            if (!summary.isBlank()) {
                append(ctx, "[이전 생성 이력]\n" + summary + "\n");
            }
        } catch (Exception e) {
            log.warn("생성 이력 조회 실패: table={}, {}", tableName, e.getMessage());
        }
    }

    private void appendRelations(StringBuilder ctx, String tableName) {
        if (ctx.length() >= MAX_CONTEXT_CHARS) return;

        try {
            StringBuilder rel = new StringBuilder();

            List<RelationInfo> parents = tableRelationService.getPhysicalFkParents(DEFAULT_DATABASE, tableName);
            for (RelationInfo r : parents) {
                rel.append("  FK(부모) ").append(r.sourceColumn())
                   .append(" → ").append(r.targetTable()).append(".").append(r.targetColumn()).append("\n");
            }

            List<RelationInfo> children = tableRelationService.getPhysicalFkChildren(DEFAULT_DATABASE, tableName);
            for (RelationInfo r : children) {
                rel.append("  FK(자식) ").append(r.targetTable()).append(".").append(r.sourceColumn())
                   .append(" → ").append(r.targetColumn()).append("\n");
            }

            List<RelationInfo> implicit = tableRelationService.getImplicitJoinCandidates(DEFAULT_DATABASE, tableName);
            for (RelationInfo r : implicit) {
                rel.append("  암묵적JOIN ").append(r.sourceColumn())
                   .append(" → ").append(r.targetTable()).append("\n");
            }

            if (!rel.isEmpty()) {
                append(ctx, "[테이블 관계]\n" + rel + "\n");
            }
        } catch (Exception e) {
            log.warn("테이블 관계 조회 실패: table={}, {}", tableName, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────────────────────────────

    /** 예산 범위 안에서 블록을 추가. 초과분은 잘라냄. */
    private void append(StringBuilder ctx, String block) {
        int remaining = MAX_CONTEXT_CHARS - ctx.length();
        if (remaining <= 0) return;
        if (block.length() <= remaining) {
            ctx.append(block);
        } else {
            ctx.append(block, 0, remaining - 4).append("...\n");
        }
    }

    /**
     * 사용자 질문에서 eGovFrame 표준 테이블명을 추출합니다.
     * 여러 개 감지 시 첫 번째를 사용합니다.
     */
    private String extractTableName(String query) {
        Matcher m = EGOV_TABLE_PATTERN.matcher(query.toUpperCase());
        return m.find() ? m.group() : null;
    }
}
