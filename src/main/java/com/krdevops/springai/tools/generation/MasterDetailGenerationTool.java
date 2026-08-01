package com.krdevops.springai.tools.generation;

import com.krdevops.springai.service.generation.mcp.MasterDetailGenerationMcpFacade;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** Master/Detail 전체 생성 MCP Adapter. */
@Component
@RequiredArgsConstructor
public class MasterDetailGenerationTool {
    private final MasterDetailGenerationMcpFacade facade;

    @Tool(description = """
            1:N 마스터-디테일 구조의 eGovFrame CRUD 소스 생성 지시를 반환합니다.
            마스터 테이블 상세화면에 디테일 테이블 목록 그리드 탭이 포함됩니다.
            getTableRelations()에서 자식 테이블이 탐지된 경우 이 Tool을 사용하세요.
            database    : 데이터베이스명 (예: com)
            masterTable : 마스터(부모) 테이블명 (예: LETTNEMPLYRINFO)
            detailTable : 디테일(자식) 테이블명 (예: LETTNEMPLYRATTRBINFO)
            domain      : 마스터 도메인명 대문자 시작 (예: Employer)
            packageName : 패키지명 (예: egovframework.let.emp)
            outputPath  : 소스 저장 절대경로 (예: /Users/user/Desktop/egov-gen/emp)
            llmProvider : 소스 생성 주체 선택 (생략 시 "auto" 기본값)
              - "auto"  : 서버 내부 Pipeline이 파일을 생성·저장
              - "claude": 스키마 정보와 지시를 반환하고 Claude가 소스를 직접 작성·저장
            egovVersion : eGovFrame 버전 (선택, 기본값 "5.0")
              - "5.0" 또는 "latest" : jakarta.validation.* import 사용
              - "4.3"               : javax.validation.* import 사용
            viewType    : 화면 템플릿 종류 (선택, 기본값 "jsp")
              - "jsp"       : /WEB-INF/jsp/{domainLc}/Egov{Domain}*.jsp 생성 (총 14개)
              - "thymeleaf" : src/main/resources/templates/{domainLc}/Egov{Domain}*.html 생성
            layoutMode  : Thymeleaf layout 처리 방식 (선택, 기본값 "reuse")
              - "reuse" : 기존 layout 재사용. layout이 없으면 generateThymeleafLayout() 실행 안내와 함께 실패
              - "create": layout 레이어까지 함께 생성
            layoutView     : 화면이 참조할 layout 경로 (선택, 기본값 "layout/default")
            breadcrumbView : 화면이 참조할 breadcrumb 경로 (선택, 기본값 "layout/breadcrumb")
            designReferenceId: analyzeDesignReference()가 반환한 분석 ID입니다.
            screenSpecificationId: 마스터 테이블 기준 APPROVED 화면명세 ID입니다.
            layout 파일은 generateThymeleafLayout()로 먼저 생성하는 것을 권장합니다.
            정적 리소스: 생성 화면은 initializeProject()가 만든 /resources/css/styles.css와 /resources/js/krds.min.js를 사용합니다.
              - WAR는 webapp/resources/**, BOOT는 static/resources/** 에 파일이 생성되며 URL은 /resources/** 로 동일하게 유지합니다.
              - _ds_bundle.css는 styles.css 내부 @import 대상이므로 화면에서 별도 링크하지 않습니다.

            [중요] outputPath 결정 규칙 — 반드시 아래 순서를 따르세요:
            1. 사용자가 저장 경로를 명시한 경우 → 그 경로를 그대로 사용
            2. 사용자가 기존 프로젝트 경로를 알려준 경우 → resolveProjectOutputPath() 먼저 호출하여 경로 확정
            3. 경로를 모르거나 언급이 없는 경우 → getDefaultOutputPath(domain) 호출하여 기본 경로 사용
               (기본 경로: ~/Desktop/egov-generated/{domain})
            절대로 경로를 임의로 결정하거나 추측하지 마세요.
            outputPath를 확정한 후 사용자에게 "이 경로에 생성합니다: {path}" 라고 먼저 알리고 진행하세요.
            """)
    public String buildMasterDetailPrompt(String database, String masterTable, String detailTable,
                                          String domain, String packageName, String outputPath,
                                          @Nullable String viewType, @Nullable String egovVersion,
                                          @Nullable String llmProvider, @Nullable String layoutMode,
                                          @Nullable String layoutView, @Nullable String breadcrumbView,
                                          @Nullable String designReferenceId,
                                          @Nullable String screenSpecificationId) {
        return facade.buildMasterDetailPrompt(database, masterTable, detailTable, domain, packageName,
                outputPath, viewType, egovVersion, llmProvider, layoutMode, layoutView, breadcrumbView,
                designReferenceId, screenSpecificationId);
    }
}
