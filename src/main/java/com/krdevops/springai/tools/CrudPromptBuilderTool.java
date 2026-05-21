package com.krdevops.springai.tools;

import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.MasterDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrudPromptBuilderTool {

    private final CrudPromptBuilderService crudPromptBuilderService;
    private final MasterDetailService masterDetailService;

    @Tool(description = """
            eGovFrame 5.x CRUD 전체 소스 생성에 필요한 통합 프롬프트를 반환합니다.
            이 Tool 하나로 getTableSchema + 공통코드 조회 + 플레이스홀더 매핑을 한 번에 처리합니다.
            반환된 프롬프트의 지시에 따라 10개 레이어 소스를 순서대로 생성하고 저장하세요.
            database   : 데이터베이스명 (예: com)
            tableName  : 테이블명 (예: COMTNEMPLYRINFO)
            domain     : 도메인명 대문자 시작 (예: Employer)
            packageName: 패키지명 (예: egovframework.let.emp)
            outputPath : 소스 저장 절대경로 (예: /Users/user/Desktop/egov-gen/emp)

            [중요] outputPath 결정 규칙 — 반드시 아래 순서를 따르세요:
            1. 사용자가 저장 경로를 명시한 경우 → 그 경로를 그대로 사용
            2. 사용자가 기존 프로젝트 경로를 알려준 경우 → resolveProjectOutputPath() 먼저 호출하여 경로 확정
            3. 경로를 모르거나 언급이 없는 경우 → getDefaultOutputPath(domain) 호출하여 기본 경로 사용
               (기본 경로: ~/Desktop/egov-generated/{domain})
            절대로 경로를 임의로 결정하거나 추측하지 마세요.
            outputPath를 확정한 후 사용자에게 "이 경로에 생성합니다: {path}" 라고 먼저 알리고 진행하세요.
            """)
    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath) {
        return crudPromptBuilderService.buildFullCrudPrompt(
            database, tableName, domain, packageName, outputPath);
    }

    @Tool(description = """
            1:N 마스터-디테일 구조의 eGovFrame CRUD 소스 생성 지시를 반환합니다.
            마스터 테이블 상세화면에 디테일 테이블 목록 그리드 탭이 포함됩니다.
            getTableRelations()에서 자식 테이블이 탐지된 경우 이 Tool을 사용하세요.
            database    : 데이터베이스명 (예: com)
            masterTable : 마스터(부모) 테이블명 (예: COMTNEMPLYRINFO)
            detailTable : 디테일(자식) 테이블명 (예: COMTNEMPLYRATTRBINFO)
            domain      : 마스터 도메인명 대문자 시작 (예: Employer)
            packageName : 패키지명 (예: egovframework.let.emp)
            outputPath  : 소스 저장 절대경로 (예: /Users/user/Desktop/egov-gen/emp)
            생성 파일: 마스터 VO+Mapper+Service+ServiceImpl+Controller + 디테일 VO+Mapper + JSP 5개 (총 12개)

            [중요] outputPath 결정 규칙 — 반드시 아래 순서를 따르세요:
            1. 사용자가 저장 경로를 명시한 경우 → 그 경로를 그대로 사용
            2. 사용자가 기존 프로젝트 경로를 알려준 경우 → resolveProjectOutputPath() 먼저 호출하여 경로 확정
            3. 경로를 모르거나 언급이 없는 경우 → getDefaultOutputPath(domain) 호출하여 기본 경로 사용
               (기본 경로: ~/Desktop/egov-generated/{domain})
            절대로 경로를 임의로 결정하거나 추측하지 마세요.
            outputPath를 확정한 후 사용자에게 "이 경로에 생성합니다: {path}" 라고 먼저 알리고 진행하세요.
            """)
    public String buildMasterDetailPrompt(String database, String masterTable, String detailTable,
                                          String domain, String packageName, String outputPath) {
        return masterDetailService.buildMasterDetailPrompt(
            database, masterTable, detailTable, domain, packageName, outputPath);
    }

    @Tool(description = """
            단일 테이블에 JOIN이 필요한 경우 SELECT 쿼리·resultMap·VO 추가 필드를 자동 생성합니다.
            getTableRelations()에서 공통코드·부서 등 JOIN 후보 컬럼이 탐지된 경우 사용하세요.
            기존 buildFullCrudPrompt()로 생성된 소스에 JOIN을 추가할 때 활용합니다.
            database  : 데이터베이스명 (예: com)
            tableName : JOIN을 추가할 테이블명 (예: COMTNEMPLYRINFO)
            반환값: JOIN SELECT 쿼리 초안 + resultMap 추가 항목 + VO 추가 필드 목록
            """)
    public String buildJoinSelectPrompt(String database, String tableName) {
        return masterDetailService.buildJoinSelectPrompt(database, tableName);
    }
}
