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
        String historySummary
) {
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
