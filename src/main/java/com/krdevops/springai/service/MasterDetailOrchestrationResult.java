package com.krdevops.springai.service;

import java.util.List;

/**
 * Master/Detail 생성 Pipeline의 외부 반환 결과 VO.
 */
public record MasterDetailOrchestrationResult(
        boolean tableNotFound,
        String database,
        String masterTable,
        String detailTable,
        String domain,
        String outputPath,
        List<String> succeededFiles,
        List<String> failedFiles,
        String validationSummary,
        String historySummary
) {
    public static MasterDetailOrchestrationResult notFound(
            String database, String masterTable, String detailTable) {
        return new MasterDetailOrchestrationResult(
                true, database, masterTable, detailTable, "", "",
                List.of(), List.of(), "", "");
    }

    public int successCount()   { return succeededFiles.size(); }
    public int failCount()      { return failedFiles.size(); }
    public boolean hasFailure() { return !failedFiles.isEmpty(); }
}
