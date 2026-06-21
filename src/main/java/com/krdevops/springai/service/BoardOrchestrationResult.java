package com.krdevops.springai.service;

import java.util.List;

/**
 * BoardOrchestrationService.orchestrate() 결과 VO.
 *
 * <p>필수 테이블 미존재 케이스도 예외 대신 이 객체로 표현하여
 * MCP Tool 레이어의 문자열 반환 UX를 유지한다.
 * 호출자는 반드시 {@link #tableNotFound()}를 먼저 확인해야 한다.
 */
public record BoardOrchestrationResult(
        boolean tableNotFound,
        String database,
        String mainTable,
        String domain,
        String outputPath,
        List<String> succeededFiles,
        List<String> failedFiles,
        String validationSummary,
        String historySummary
) {
    public static BoardOrchestrationResult notFound(String database, String mainTable) {
        return new BoardOrchestrationResult(true, database, mainTable, null, null,
                List.of(), List.of(), null, null);
    }

    public int successCount()   { return succeededFiles.size(); }
    public int failCount()      { return failedFiles.size(); }
    public boolean hasFailure() { return !failedFiles.isEmpty(); }
}
