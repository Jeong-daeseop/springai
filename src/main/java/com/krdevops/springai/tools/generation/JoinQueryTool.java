package com.krdevops.springai.tools.generation;

import com.krdevops.springai.service.MasterDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** JOIN SELECT 보강 프롬프트 MCP Adapter. */
@Component
@RequiredArgsConstructor
public class JoinQueryTool {
    private final MasterDetailService service;

    @Tool(description = """
            단일 테이블에 JOIN이 필요한 경우 SELECT 쿼리·resultMap·VO 추가 필드를 자동 생성합니다.
            getTableRelations()에서 공통코드·부서 등 JOIN 후보 컬럼이 탐지된 경우 사용하세요.
            기존 buildFullCrudPrompt()로 생성된 소스에 JOIN을 추가할 때 활용합니다.
            이 Tool은 Mapper XML/VO 보강 지시만 반환하며 HTML/CSS/JS 화면 파일은 생성하지 않습니다.
            database  : 데이터베이스명 (예: com)
            tableName : JOIN을 추가할 테이블명 (예: LETTNEMPLYRINFO)
            반환값: JOIN SELECT 쿼리 초안 + resultMap 추가 항목 + VO 추가 필드 목록
            """)
    public String buildJoinSelectPrompt(String database, String tableName) {
        return service.buildJoinSelectPrompt(database, tableName);
    }
}
