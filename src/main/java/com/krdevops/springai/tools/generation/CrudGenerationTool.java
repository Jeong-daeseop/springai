package com.krdevops.springai.tools.generation;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.service.generation.mcp.CrudGenerationMcpFacade;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** CRUD 전체 생성·프롬프트 MCP Adapter. 기존 Tool 계약을 유지한다. */
@Component
@RequiredArgsConstructor
public class CrudGenerationTool {
    private final CrudGenerationMcpFacade facade;

    @McpToolRisk(McpToolRiskLevel.FILE_WRITE)
    @Tool(description = """
            eGovFrame 5.x CRUD 전체 소스 생성에 필요한 통합 프롬프트를 반환합니다.
            이 Tool 하나로 getTableSchema + 공통코드 조회 + 플레이스홀더 매핑을 한 번에 처리합니다.
            반환된 프롬프트의 지시에 따라 viewType별 레이어 소스를 순서대로 생성하고 저장하세요.
            (JSP: 11개, Thymeleaf 기본 reuse: 화면/Java/Mapper 11개, layoutMode=create: 16개)
            database   : 데이터베이스명 (예: com)
            tableName  : 테이블명 (예: LETTNEMPLYRINFO)
            domain     : 도메인명 대문자 시작 (예: Employer)
            packageName: 패키지명 (예: egovframework.let.emp)
            outputPath : 소스 저장 절대경로 (예: /Users/user/Desktop/egov-gen/emp)
            llmProvider: 소스 생성 주체 선택 (생략 시 "auto" 기본값)
              - "auto"  : 서버 내부 오케스트레이션 — CrudOrchestrationService가 viewType별 파일을 생성·저장 (Claude 토큰 97% 절감)
              - "claude": 스키마 정보와 지시를 반환하고 Claude가 소스를 직접 작성·저장
            viewType   : 화면 템플릿 종류 (선택, 기본값 "jsp")
              - "jsp"       : /WEB-INF/jsp/{domainLc}/Egov{Domain}*.jsp 생성
              - "thymeleaf" : src/main/resources/templates/{domainLc}/Egov{Domain}*.html 생성
            layoutMode : Thymeleaf layout 처리 방식 (선택, 기본값 "reuse")
              - "reuse" : 기존 layout 재사용. layout이 없으면 generateThymeleafLayout() 실행 안내와 함께 실패
              - "create": layout 레이어까지 함께 생성
            layoutView     : 화면이 참조할 layout 경로 (선택, 기본값 "layout/default")
            breadcrumbView : 화면이 참조할 breadcrumb 경로 (선택, 기본값 "layout/breadcrumb")
            layout 파일은 generateThymeleafLayout()로 먼저 생성하는 것을 권장합니다.
            정적 리소스: 생성 화면은 initializeProject()가 만든 /resources/css/styles.css와 /resources/js/krds.min.js를 사용합니다.
              - WAR는 webapp/resources/**, BOOT는 static/resources/** 에 파일이 생성되며 URL은 /resources/** 로 동일하게 유지합니다.
              - _ds_bundle.css는 styles.css 내부 @import 대상이므로 화면에서 별도 링크하지 않습니다.
              - Thymeleaf 화면과 layout은 인라인 style을 생성하지 않고 styles.css의 egov-* 공통 클래스를 사용합니다.
            egovVersion: eGovFrame 버전 (선택, 기본값 "5.0")
              - "5.0" 또는 "latest" : jakarta.validation.* import 사용
              - "4.3"               : javax.validation.* import 사용
              initializeProject() 완료 후 PROJECT_CONTEXT 블록의 egovVersion 값을 그대로 전달하세요.
            programFileName : LETTNPROGRMLIST의 목록(list) 화면 프로그램 파일명. 명시값이 DB 자동조회보다 우선합니다.
            programUrl      : LETTNPROGRMLIST 목록 화면 URL. Controller alias로 사용합니다.
            programKoreanName: 화면 title/H1/캡션에 사용할 프로그램 한글명입니다.
            programStorePath: 프로그램 저장 경로 메타데이터입니다.
            designReferenceId: analyzeDesignReference()가 반환한 분석 ID입니다. 화면명세 초안 생성에 사용합니다.
            screenSpecificationId: APPROVED 상태의 화면명세 ID입니다. designReferenceId보다 우선합니다.
            우선순위는 명시 파라미터 > DB 자동조회(LETTNPROGRMLIST/LETTNMENUINFO, domain 기준 목록/상세/등록/수정/삭제 화면별 매칭) > 기존 규칙(packageName+domain) fallback 입니다.
            domain과 일치하는 프로그램이 LETTNPROGRMLIST에 여러 건(목록 화면 기준) 있으면 자동 선택하지 않고 실패하니 programFileName을 명시하세요.

            [중요] outputPath 결정 규칙 — 반드시 아래 순서를 따르세요:
            1. 사용자가 저장 경로를 명시한 경우 → 그 경로를 그대로 사용
            2. 사용자가 기존 프로젝트 경로를 알려준 경우 → resolveProjectOutputPath() 먼저 호출하여 경로 확정
            3. 경로를 모르거나 언급이 없는 경우 → getDefaultOutputPath(domain) 호출하여 기본 경로 사용
               (기본 경로: ~/Desktop/egov-generated/{domain})
            절대로 경로를 임의로 결정하거나 추측하지 마세요.
            outputPath를 확정한 후 사용자에게 "이 경로에 생성합니다: {path}" 라고 먼저 알리고 진행하세요.
            """)
    public String buildFullCrudPrompt(String database, String tableName, String domain, String packageName,
                                      String outputPath, String llmProvider, @Nullable String egovVersion,
                                      @Nullable String viewType, @Nullable String layoutMode,
                                      @Nullable String layoutView, @Nullable String breadcrumbView,
                                      @Nullable String programFileName, @Nullable String programUrl,
                                      @Nullable String programKoreanName, @Nullable String programStorePath,
                                      @Nullable String designReferenceId, @Nullable String screenSpecificationId) {
        return facade.buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, llmProvider,
                egovVersion, viewType, layoutMode, layoutView, breadcrumbView, programFileName, programUrl,
                programKoreanName, programStorePath, designReferenceId, screenSpecificationId);
    }
}
