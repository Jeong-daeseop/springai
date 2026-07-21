package com.krdevops.springai.service;

import java.util.List;

/**
 * CrudOrchestrationService.orchestrate() 결과 VO.
 *
 * <p>테이블 미존재 케이스도 예외 대신 이 객체로 표현하여
 * MCP Tool 레이어의 문자열 반환 UX를 유지한다.
 * 호출자는 반드시 {@link #tableNotFound()}를 먼저 확인해야 한다.
 */
public record CrudOrchestrationResult(
        boolean tableNotFound,
        String database,
        String tableName,
        String domain,
        String outputPath,
        List<String> succeededFiles,
        List<String> failedFiles,
        String validationSummary,
        String historySummary,
        String menuIntegrationStatus,
        String resolvedProgramName,
        String resolvedProgramUrl,
        String canonicalUrl,
        List<String> warnings
) {
    /** 메타데이터 조회 이전 호출자를 위한 하위 호환 생성자. */
    public CrudOrchestrationResult(
            boolean tableNotFound, String database, String tableName, String domain, String outputPath,
            List<String> succeededFiles, List<String> failedFiles,
            String validationSummary, String historySummary) {
        this(tableNotFound, database, tableName, domain, outputPath, succeededFiles, failedFiles,
                validationSummary, historySummary, null, null, null, null, List.of());
    }

    /** 테이블 미존재 케이스 — 나머지 필드는 빈 값으로 채워 NPE를 방지한다. */
    public static CrudOrchestrationResult notFound(String database, String tableName) {
        return new CrudOrchestrationResult(
                true, database, tableName, "", "",
                List.of(), List.of(), "", "");
    }

    public int successCount()   { return succeededFiles.size(); }
    public int failCount()      { return failedFiles.size(); }
    public boolean hasFailure() { return !failedFiles.isEmpty(); }
}
