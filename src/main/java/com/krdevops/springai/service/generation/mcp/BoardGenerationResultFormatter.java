package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.board.BoardToolResult;
import org.springframework.stereotype.Component;

/**
 * {@link BoardToolResult}를 MCP 응답 문자열로 조립한다. auto 경로(리팩터링 전과 동일한 형식)와
 * claude 경로(Prompt 문자열 그대로 반환) 모두 지원한다.
 */
@Component
public class BoardGenerationResultFormatter {

    public String format(BoardToolResult result) {
        if (result instanceof BoardToolResult.Orchestrated orchestrated) {
            return formatOrchestrated(orchestrated.result());
        }
        return ((BoardToolResult.Prompted) result).result().prompt();
    }

    private String formatOrchestrated(BoardOrchestrationResult r) {
        if (r.tableNotFound()) {
            return "게시판 테이블을 찾을 수 없습니다: " + r.database() + "." + r.mainTable();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(r.successCount() == 0 && r.hasFailure()
                ? "=== [auto] eGovFrame 게시판(BBS) 소스 생성 실패 ===\n\n"
                : "=== [auto] eGovFrame 게시판(BBS) 소스 생성 완료 ===\n\n");
        sb.append("DB: ").append(r.database())
          .append(" | 메인 테이블: ").append(r.mainTable())
          .append(" | 도메인: ").append(r.domain()).append("\n");
        sb.append("출력 경로: ").append(r.outputPath()).append("\n\n");
        sb.append("GNB/LNB 연동: ").append(valueOrDash(r.menuIntegrationStatus())).append("\n");
        sb.append("프로그램 표시명: ").append(valueOrDash(r.resolvedProgramName())).append("\n");
        sb.append("등록 URL: ").append(valueOrDash(r.resolvedProgramUrl())).append("\n");
        sb.append("Canonical URL: ").append(valueOrDash(r.canonicalUrl())).append("\n");
        sb.append("기본 bbsId: ").append(valueOrDash(r.resolvedBbsId())).append("\n");
        sb.append("PK 방어: BBS_ID + NTT_ID 적용\n");
        sb.append("CSS: ").append(valueOrDash(r.cssStatus())).append("\n\n");
        sb.append("[생성 파일 목록]\n");
        r.succeededFiles().forEach(f -> sb.append("  ✅ ").append(f).append("\n"));
        r.failedFiles().forEach(f    -> sb.append("  ❌ ").append(f).append("\n"));
        sb.append("\n총 ").append(r.successCount()).append("개 성공");
        if (r.hasFailure()) sb.append(", ").append(r.failCount()).append("개 실패");
        sb.append("\n");
        sb.append("\n[코드 검증 결과]\n").append(r.validationSummary()).append("\n");
        sb.append("\n[생성 이력]\n").append(r.historySummary()).append("\n");
        if (!r.warnings().isEmpty()) {
            sb.append("\n[경고]\n");
            r.warnings().forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
        }
        return sb.toString();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
