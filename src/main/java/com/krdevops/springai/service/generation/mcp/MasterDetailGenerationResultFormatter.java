package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailToolResult;
import org.springframework.stereotype.Component;

/**
 * {@link MasterDetailToolResult}를 MCP 응답 문자열로 조립한다. auto 경로(원래 출력 형식)와
 * claude 경로(Prompt 문자열 그대로 반환) 모두 리팩터링 전과 동일한 형식을 유지한다.
 */
@Component
public class MasterDetailGenerationResultFormatter {

    public String format(MasterDetailToolResult result) {
        if (result instanceof MasterDetailToolResult.Orchestrated orchestrated) {
            return formatOrchestrated(orchestrated.result());
        }
        return ((MasterDetailToolResult.Prompted) result).result().prompt();
    }

    private String formatOrchestrated(MasterDetailOrchestrationResult r) {
        if (r.tableNotFound()) {
            return "마스터 또는 디테일 테이블을 찾을 수 없습니다: "
                    + r.database() + "." + r.masterTable() + " / " + r.detailTable();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(r.successCount() == 0 && r.hasFailure()
                ? "=== [auto] eGovFrame 마스터-디테일 CRUD 소스 생성 실패 ===\n\n"
                : "=== [auto] eGovFrame 마스터-디테일 CRUD 소스 생성 완료 ===\n\n");
        sb.append("DB: ").append(r.database())
          .append(" | 마스터: ").append(r.masterTable())
          .append(" | 디테일: ").append(r.detailTable())
          .append(" | 도메인: ").append(r.domain()).append("\n");
        sb.append("출력 경로: ").append(r.outputPath()).append("\n\n");
        sb.append("[생성 파일 목록]\n");
        r.succeededFiles().forEach(f -> sb.append("  ✅ ").append(f).append("\n"));
        r.failedFiles().forEach(f    -> sb.append("  ❌ ").append(f).append("\n"));
        sb.append("\n총 ").append(r.successCount()).append("개 성공");
        if (r.hasFailure()) sb.append(", ").append(r.failCount()).append("개 실패");
        sb.append("\n");
        sb.append("\n[코드 검증 결과]\n").append(r.validationSummary()).append("\n");
        sb.append("\n[생성 이력]\n").append(r.historySummary()).append("\n");
        return sb.toString();
    }
}
