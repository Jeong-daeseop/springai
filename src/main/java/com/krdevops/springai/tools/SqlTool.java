package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.service.SqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SqlTool {

    private final SqlService sqlService;

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            SELECT 쿼리를 직접 실행하고 결과를 테이블 형식으로 반환합니다.
            SELECT / SHOW / EXPLAIN / DESC 만 실행 가능합니다. INSERT·UPDATE·DELETE·DDL은 차단됩니다.
            결과는 최대 100행까지 표시됩니다.
            예시:
              SELECT * FROM com.LETTNEMPLYRINFO LIMIT 10
              SHOW TABLES FROM com
              DESC com.LETTNEMPLYRINFO
            CRUD 생성 후 실제 데이터 확인, 컬럼 값 범위 파악, 쿼리 동작 검증에 사용하세요.
            """)
    public String executeQuery(String sql) {
        return sqlService.executeQuery(sql);
    }

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            테이블의 샘플 데이터를 조회합니다.
            database : 데이터베이스명 (예: com)
            tableName: 테이블명 (예: LETTNEMPLYRINFO)
            limit    : 조회 행 수 (1~100, 기본 10)
            VO 필드의 실제 값 범위를 파악하거나 CRUD 생성 후 데이터를 확인할 때 사용하세요.
            """)
    public String getSampleData(String database, String tableName, int limit) {
        return sqlService.getSampleData(database, tableName, limit);
    }

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            EXPLAIN으로 쿼리 실행 계획을 분석합니다.
            인덱스 사용 여부, 풀스캔 여부, 조인 순서 등을 확인할 수 있습니다.
            CRUD 생성 후 selectList 쿼리 성능을 점검할 때 사용하세요.
            예시:
              SELECT * FROM com.LETTNEMPLYRINFO WHERE ORGNZT_ID = 'ORG001'
            """)
    public String explainQuery(String sql) {
        return sqlService.explainQuery(sql);
    }
}
