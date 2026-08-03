package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommonCodeTool {

    private final CommonCodeService commonCodeService;

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            eGovFrame 공통 코드 상세 목록을 조회합니다. (LETTCCMMNDETAILCODE)
            codeId: 조회할 코드ID (예: COM034, COM001)
            반환값: 코드값(CODE), 코드명(CODE_NM), 사용여부(USE_AT), 설명(CODE_DC)
            JSP select box 생성용 HTML도 함께 반환합니다.
            VO 필드 매핑, JSP <select> 옵션 생성, 검색 조건 코드값 확인 시 사용합니다.
            코드ID를 모를 경우 searchCommonCode 로 먼저 검색하세요.
            """)
    public String getCommonCode(String codeId) {
        return commonCodeService.getCommonCode(codeId);
    }

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            eGovFrame 공통 코드 그룹을 키워드로 검색합니다. (LETTCCMMNCODE)
            keyword: 검색어 (코드ID명, 설명 대상) — 비어 있으면 전체 50건 반환
            예: "직원" 검색 → 직원 관련 코드 그룹(CODE_ID) 목록 반환
            원하는 코드 그룹을 찾은 후 getCommonCode(codeId)로 상세 코드를 조회하세요.
            """)
    public String searchCommonCode(String keyword) {
        return commonCodeService.searchCommonCode(keyword);
    }
}
