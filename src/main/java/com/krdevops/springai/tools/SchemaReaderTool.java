package com.krdevops.springai.tools;

import com.krdevops.springai.service.SchemaService;
import com.krdevops.springai.service.TableRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaReaderTool {

    private final SchemaService schemaService;
    private final TableRelationService tableRelationService;

    @Tool(description = """
            MySQL 데이터베이스 내 테이블 목록을 조회합니다.
            database 파라미터에 조회할 데이터베이스명을 입력하세요. 예: com, egovfrm
            eGovFrame CRUD 소스 생성 전에 테이블 목록을 확인할 때 사용합니다.
            """)
    public String getTableList(String database) {
        return schemaService.getTableList(database);
    }

    @Tool(description = """
            MySQL 테이블의 컬럼 상세 정보를 조회합니다.
            database: 데이터베이스명 (예: com)
            tableName: 테이블명 (예: LETTNEMPLYRINFO)
            반환값: 컬럼명, 데이터타입, PK여부, Null허용여부, 기본값, 컬럼설명
            eGovFrame 5.x CRUD 소스(VO, Mapper, Service, Controller) 자동 생성에 사용합니다.
            """)
    public String getTableSchema(String database, String tableName) {
        return schemaService.getTableSchema(database, tableName);
    }

    @Tool(description = """
            테이블의 연관관계를 분석합니다.
            FK(물리적 외래키) 및 컬럼명 매칭 기반 암묵적 JOIN 후보를 함께 반환합니다.
            database  : 데이터베이스명 (예: com)
            tableName : 분석할 테이블명 (예: LETTNEMPLYRINFO)
            반환값:
              - 이 테이블이 참조하는 부모 테이블 목록 (자식→부모 FK)
              - 이 테이블을 참조하는 자식 테이블 목록 (부모→자식 FK)
              - 컬럼명 매칭 기반 암묵적 JOIN 후보
              - 공통코드(LETTCCMMNDETAILCODE) JOIN 후보
              - 마스터-디테일 구조 여부 판단 및 다음 Tool 호출 권장
            CRUD 소스 생성 전 테이블 관계 파악이 필요할 때 사용하세요.
            자식 테이블이 있으면 buildMasterDetailPrompt(), JOIN 후보만 있으면 buildJoinSelectPrompt() 사용을 권장합니다.
            """)
    public String getTableRelations(String database, String tableName) {
        return tableRelationService.buildRelationText(database, tableName);
    }
}
