package com.krdevops.springai.tools;

import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.masterdetail.MasterDetailLayerDefinition;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.BoardModelFactory;
import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.BoardOrchestrationService;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.CrudOrchestrationService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.MasterDetailOrchestrationService;
import com.krdevops.springai.service.MasterDetailService;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.util.CrudMappingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrudPromptBuilderTool {

    private final CrudOrchestrationService crudOrchestrationService;
    private final CrudSchemaQueryService crudSchemaQueryService;
    private final CrudModelFactory crudModelFactory;
    private final CrudTemplateRenderer crudTemplateRenderer;
    private final CrudPromptBuilderService crudPromptBuilderService;
    private final MasterDetailService      masterDetailService;
    private final MasterDetailTemplateRenderer masterDetailTemplateRenderer;
    private final MasterDetailOrchestrationService masterDetailOrchestrationService;
    private final BoardSchemaService boardSchemaService;
    private final BoardModelFactory boardModelFactory;
    private final BoardTemplateRenderer boardTemplateRenderer;
    private final BoardOrchestrationService boardOrchestrationService;

    @Tool(description = """
            eGovFrame 5.x CRUD 전체 소스 생성에 필요한 통합 프롬프트를 반환합니다.
            이 Tool 하나로 getTableSchema + 공통코드 조회 + 플레이스홀더 매핑을 한 번에 처리합니다.
            반환된 프롬프트의 지시에 따라 viewType별 레이어 소스를 순서대로 생성하고 저장하세요.
            (JSP: 11개, Thymeleaf 기본 reuse: 화면/Java/Mapper 11개, layoutMode=create: 16개)
            database   : 데이터베이스명 (예: com)
            tableName  : 테이블명 (예: COMTNEMPLYRINFO)
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

            [중요] outputPath 결정 규칙 — 반드시 아래 순서를 따르세요:
            1. 사용자가 저장 경로를 명시한 경우 → 그 경로를 그대로 사용
            2. 사용자가 기존 프로젝트 경로를 알려준 경우 → resolveProjectOutputPath() 먼저 호출하여 경로 확정
            3. 경로를 모르거나 언급이 없는 경우 → getDefaultOutputPath(domain) 호출하여 기본 경로 사용
               (기본 경로: ~/Desktop/egov-generated/{domain})
            절대로 경로를 임의로 결정하거나 추측하지 마세요.
            outputPath를 확정한 후 사용자에게 "이 경로에 생성합니다: {path}" 라고 먼저 알리고 진행하세요.
            """)
    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName,
                                      String outputPath, String llmProvider,
                                      @Nullable String egovVersion,
                                      @Nullable String viewType,
                                      @Nullable String layoutMode,
                                      @Nullable String layoutView,
                                      @Nullable String breadcrumbView) {
        String resolved = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        String provider = (llmProvider == null || llmProvider.isBlank()) ? "auto"
                          : llmProvider.trim().toLowerCase();
        String resolvedViewType = (viewType == null || viewType.isBlank()) ? "jsp" : viewType;

        if ("auto".equals(provider)) {
            CrudOrchestrationResult result = crudOrchestrationService.orchestrate(
                    database, tableName, domain, packageName, outputPath, resolved, resolvedViewType,
                    layoutMode, layoutView, breadcrumbView);
            return formatResult(result);
        }
        return crudPromptBuilderService.buildFullCrudPrompt(
                database, tableName, domain, packageName, outputPath, resolved, resolvedViewType,
                layoutMode, layoutView, breadcrumbView);
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
            llmProvider : 소스 생성 주체 선택 (생략 시 "auto" 기본값)
              - "auto"  : 서버 내부 오케스트레이션 — MasterDetailOrchestrationService가 파일을 생성·저장
              - "claude": 스키마 정보와 지시를 반환하고 Claude가 소스를 직접 작성·저장
            egovVersion : eGovFrame 버전 (선택, 기본값 "5.0")
              - "5.0" 또는 "latest" : jakarta.validation.* import 사용
              - "4.3"               : javax.validation.* import 사용
            viewType    : 화면 템플릿 종류 (선택, 기본값 "jsp")
              - "jsp"       : /WEB-INF/jsp/{domainLc}/Egov{Domain}*.jsp 생성 (총 13개)
              - "thymeleaf" : src/main/resources/templates/{domainLc}/Egov{Domain}*.html 생성
            layoutMode  : Thymeleaf layout 처리 방식 (선택, 기본값 "reuse")
              - "reuse" : 기존 layout 재사용. layout이 없으면 generateThymeleafLayout() 실행 안내와 함께 실패
              - "create": layout 레이어까지 함께 생성
            layoutView     : 화면이 참조할 layout 경로 (선택, 기본값 "layout/default")
            breadcrumbView : 화면이 참조할 breadcrumb 경로 (선택, 기본값 "layout/breadcrumb")
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
                                          @Nullable String viewType,
                                          @Nullable String egovVersion,
                                          @Nullable String llmProvider,
                                          @Nullable String layoutMode,
                                          @Nullable String layoutView,
                                          @Nullable String breadcrumbView) {
        String resolvedViewType = (viewType == null || viewType.isBlank()) ? "jsp" : viewType;
        String resolvedVersion = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        String provider = (llmProvider == null || llmProvider.isBlank()) ? "auto"
                          : llmProvider.trim().toLowerCase();

        if ("auto".equals(provider)) {
            MasterDetailOrchestrationResult result = masterDetailOrchestrationService.orchestrate(
                    database, masterTable, detailTable, domain, packageName,
                    outputPath, resolvedVersion, resolvedViewType,
                    layoutMode, layoutView, breadcrumbView);
            return formatMasterDetailResult(result);
        }
        return masterDetailService.buildMasterDetailPrompt(
                database, masterTable, detailTable, domain, packageName, outputPath, resolvedViewType,
                layoutMode, layoutView, breadcrumbView);
    }

    @Tool(description = """
            단일 테이블에 JOIN이 필요한 경우 SELECT 쿼리·resultMap·VO 추가 필드를 자동 생성합니다.
            getTableRelations()에서 공통코드·부서 등 JOIN 후보 컬럼이 탐지된 경우 사용하세요.
            기존 buildFullCrudPrompt()로 생성된 소스에 JOIN을 추가할 때 활용합니다.
            이 Tool은 Mapper XML/VO 보강 지시만 반환하며 HTML/CSS/JS 화면 파일은 생성하지 않습니다.
            database  : 데이터베이스명 (예: com)
            tableName : JOIN을 추가할 테이블명 (예: COMTNEMPLYRINFO)
            반환값: JOIN SELECT 쿼리 초안 + resultMap 추가 항목 + VO 추가 필드 목록
            """)
    public String buildJoinSelectPrompt(String database, String tableName) {
        return masterDetailService.buildJoinSelectPrompt(database, tableName);
    }

    @Tool(description = """
            eGovFrame 게시판(BBS) 소스를 업무 단위로 생성합니다.
            이 Tool은 프로젝트 초기화용이 아닙니다. initializeProject()와는 별도 단계입니다.
            사용자가 게시판(BBS) 생성을 명시했을 때만 호출하세요.
            COMTNBBS(게시글), COMTNBBSMASTER(게시판마스터), COMTNBBSUSE(사용권한) 연동 포함.
            목록/상세/등록/수정/논리삭제 + 조회수 증가 + 마스터 이름 조회를 한 번에 생성합니다.
            database        : 데이터베이스명 (예: com)
            domain          : 도메인명 PascalCase (예: Bbs)
            packageName     : 패키지명 (예: egovframework.let.bbs)
            outputPath      : 소스 저장 절대경로
            mainTable       : 게시글 테이블 (기본값: COMTNBBS)
            masterTable     : 게시판 마스터 테이블 (기본값: COMTNBBSMASTER)
            useTable        : 게시판 사용/권한 테이블 (기본값: COMTNBBSUSE, 생략 가능)
            fileTable       : 첨부파일 묶음 테이블 (기본값: COMTNFILE, 생략 가능)
            fileDetailTable : 첨부파일 상세 테이블 (기본값: COMTNFILEDETAIL, 생략 가능)
            egovVersion     : eGovFrame 버전 (기본값: "5.0")
            viewType        : 화면 종류 (기본값: "jsp", "thymeleaf" 선택 가능)
              - "jsp"       : JSP 화면 4개 포함 12개 파일 생성
              - "thymeleaf" : HTML 화면 4개 포함 12개 파일 생성(layoutMode=create 시 17개)
            layoutMode      : Thymeleaf layout 처리 방식 (선택, 기본값 "reuse")
              - "reuse" : 기존 layout 재사용. layout이 없으면 generateThymeleafLayout() 실행 안내와 함께 실패
              - "create": layout 레이어까지 함께 생성
            layoutView      : 화면이 참조할 layout 경로 (선택, 기본값 "layout/default")
            breadcrumbView  : 화면이 참조할 breadcrumb 경로 (선택, 기본값 "layout/breadcrumb")
            layout 파일은 generateThymeleafLayout()로 먼저 생성하는 것을 권장합니다.
            정적 리소스    : 생성 화면은 initializeProject()가 만든 /resources/css/styles.css와 /resources/js/krds.min.js를 사용합니다.
              - WAR는 webapp/resources/**, BOOT는 static/resources/** 에 파일이 생성되며 URL은 /resources/** 로 동일하게 유지합니다.
              - _ds_bundle.css는 styles.css 내부 @import 대상이므로 화면에서 별도 링크하지 않습니다.
            """)
    public String buildBoardFeature(
            String database,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String mainTable,
            @Nullable String masterTable,
            @Nullable String useTable,
            @Nullable String fileTable,
            @Nullable String fileDetailTable,
            @Nullable String egovVersion,
            @Nullable String viewType,
            @Nullable String layoutMode,
            @Nullable String layoutView,
            @Nullable String breadcrumbView) {

        String resolvedMain       = (mainTable == null || mainTable.isBlank())       ? "COMTNBBS"         : mainTable;
        String resolvedMaster     = (masterTable == null || masterTable.isBlank())   ? "COMTNBBSMASTER"   : masterTable;
        String resolvedUse        = (useTable == null || useTable.isBlank())         ? "COMTNBBSUSE"      : useTable;
        String resolvedFile       = (fileTable == null || fileTable.isBlank())       ? "COMTNFILE"        : fileTable;
        String resolvedFileDetail = (fileDetailTable == null || fileDetailTable.isBlank()) ? "COMTNFILEDETAIL" : fileDetailTable;
        String resolvedVersion    = (egovVersion == null || egovVersion.isBlank())   ? "5.0"              : egovVersion;
        String resolvedViewType   = (viewType == null || viewType.isBlank())         ? "jsp"              : viewType;

        BoardOrchestrationResult result = boardOrchestrationService.orchestrate(
            database, domain, packageName, outputPath,
            resolvedMain, resolvedMaster, resolvedUse,
            resolvedFile, resolvedFileDetail,
            resolvedVersion, resolvedViewType,
            layoutMode, layoutView, breadcrumbView);

        return formatBoardResult(result);
    }

    @Tool(description = """
            단일 테이블 CRUD 목록 화면 1개만 렌더링하여 반환합니다.
            Java/Mapper 전체 세트를 만들지 않고 List 화면 소스만 확인하거나 미세 조정할 때 사용하세요.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            database    : 데이터베이스명
            tableName   : 단일 CRUD 대상 테이블명
            domain      : 도메인명 PascalCase
            packageName : 패키지명 (egovframework.let.*)
            outputPath  : 저장 기준 절대경로
            egovVersion : eGovFrame 버전 (기본값 5.0)
            viewType    : jsp 또는 thymeleaf (기본값 jsp)
            """)
    public String generateCrudList(
            String database,
            String tableName,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateCrudScreen(database, tableName, domain, packageName, outputPath,
                egovVersion, viewType, "List", "jspList", "thymeleafList");
    }

    @Tool(description = """
            단일 테이블 CRUD 상세 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            database    : 데이터베이스명
            tableName   : 단일 CRUD 대상 테이블명
            domain      : 도메인명 PascalCase
            packageName : 패키지명 (egovframework.let.*)
            outputPath  : 저장 기준 절대경로
            egovVersion : eGovFrame 버전 (기본값 5.0)
            viewType    : jsp 또는 thymeleaf (기본값 jsp)
            """)
    public String generateCrudDetail(
            String database,
            String tableName,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateCrudScreen(database, tableName, domain, packageName, outputPath,
                egovVersion, viewType, "Detail", "jspDetail", "thymeleafDetail");
    }

    @Tool(description = """
            단일 테이블 CRUD 등록 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            database    : 데이터베이스명
            tableName   : 단일 CRUD 대상 테이블명
            domain      : 도메인명 PascalCase
            packageName : 패키지명 (egovframework.let.*)
            outputPath  : 저장 기준 절대경로
            egovVersion : eGovFrame 버전 (기본값 5.0)
            viewType    : jsp 또는 thymeleaf (기본값 jsp)
            """)
    public String generateCrudRegist(
            String database,
            String tableName,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateCrudScreen(database, tableName, domain, packageName, outputPath,
                egovVersion, viewType, "Regist", "jspRegist", "thymeleafRegist");
    }

    @Tool(description = """
            단일 테이블 CRUD 수정 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            database    : 데이터베이스명
            tableName   : 단일 CRUD 대상 테이블명
            domain      : 도메인명 PascalCase
            packageName : 패키지명 (egovframework.let.*)
            outputPath  : 저장 기준 절대경로
            egovVersion : eGovFrame 버전 (기본값 5.0)
            viewType    : jsp 또는 thymeleaf (기본값 jsp)
            """)
    public String generateCrudUpdt(
            String database,
            String tableName,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateCrudScreen(database, tableName, domain, packageName, outputPath,
                egovVersion, viewType, "Updt", "jspUpdt", "thymeleafUpdt");
    }

    @Tool(description = """
            게시판(BBS) 목록 화면 1개만 렌더링하여 반환합니다.
            `generateBoardList`는 기존 README의 화면별 MCP Tool 패턴과 호환하기 위해 추가된 세분 Tool입니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            database        : 데이터베이스명
            domain          : 도메인명 PascalCase
            packageName     : 패키지명 (egovframework.let.*)
            outputPath      : 저장 기준 절대경로
            mainTable       : 게시글 테이블 (기본값 COMTNBBS)
            masterTable     : 게시판 마스터 테이블 (기본값 COMTNBBSMASTER)
            useTable        : 게시판 사용/권한 테이블 (기본값 COMTNBBSUSE)
            fileTable       : 첨부파일 묶음 테이블 (기본값 COMTNFILE)
            fileDetailTable : 첨부파일 상세 테이블 (기본값 COMTNFILEDETAIL)
            egovVersion     : eGovFrame 버전 (기본값 5.0)
            viewType        : jsp 또는 thymeleaf (기본값 jsp)
            """)
    public String generateBoardList(
            String database,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String mainTable,
            @Nullable String masterTable,
            @Nullable String useTable,
            @Nullable String fileTable,
            @Nullable String fileDetailTable,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateBoardScreen(database, domain, packageName, outputPath,
                mainTable, masterTable, useTable, fileTable, fileDetailTable,
                egovVersion, viewType, "List", "jspList", "thymeleafList");
    }

    @Tool(description = """
            게시판(BBS) 상세 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateBoardDetail(
            String database,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String mainTable,
            @Nullable String masterTable,
            @Nullable String useTable,
            @Nullable String fileTable,
            @Nullable String fileDetailTable,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateBoardScreen(database, domain, packageName, outputPath,
                mainTable, masterTable, useTable, fileTable, fileDetailTable,
                egovVersion, viewType, "Detail", "jspDetail", "thymeleafDetail");
    }

    @Tool(description = """
            게시판(BBS) 등록 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateBoardRegist(
            String database,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String mainTable,
            @Nullable String masterTable,
            @Nullable String useTable,
            @Nullable String fileTable,
            @Nullable String fileDetailTable,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateBoardScreen(database, domain, packageName, outputPath,
                mainTable, masterTable, useTable, fileTable, fileDetailTable,
                egovVersion, viewType, "Regist", "jspRegist", "thymeleafRegist");
    }

    @Tool(description = """
            게시판(BBS) 수정 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateBoardUpdt(
            String database,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String mainTable,
            @Nullable String masterTable,
            @Nullable String useTable,
            @Nullable String fileTable,
            @Nullable String fileDetailTable,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateBoardScreen(database, domain, packageName, outputPath,
                mainTable, masterTable, useTable, fileTable, fileDetailTable,
                egovVersion, viewType, "Updt", "jspUpdt", "thymeleafUpdt");
    }

    @Tool(description = """
            마스터-디테일 구조의 마스터 목록 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            database    : 데이터베이스명
            masterTable : 마스터 테이블명
            detailTable : 디테일 테이블명
            domain      : 마스터 도메인명 PascalCase
            packageName : 패키지명 (egovframework.let.*)
            outputPath  : 저장 기준 절대경로
            egovVersion : eGovFrame 버전 (기본값 5.0)
            viewType    : jsp 또는 thymeleaf (기본값 jsp)
            """)
    public String generateMasterList(
            String database,
            String masterTable,
            String detailTable,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateMasterDetailScreen(database, masterTable, detailTable, domain, packageName, outputPath,
                egovVersion, viewType, "List", "jspList", "thymeleafList");
    }

    @Tool(description = """
            마스터-디테일 구조의 마스터 상세 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateMasterDetail(
            String database,
            String masterTable,
            String detailTable,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateMasterDetailScreen(database, masterTable, detailTable, domain, packageName, outputPath,
                egovVersion, viewType, "Detail", "jspDetail", "thymeleafDetail");
    }

    @Tool(description = """
            마스터-디테일 구조의 마스터 등록 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateMasterRegist(
            String database,
            String masterTable,
            String detailTable,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        return generateMasterDetailScreen(database, masterTable, detailTable, domain, packageName, outputPath,
                egovVersion, viewType, "Regist", "jspRegist", "thymeleafRegist");
    }

    private String generateCrudScreen(
            String database,
            String tableName,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType,
            String screenName,
            String jspLayerKey,
            String thymeleafLayerKey) {

        List<Map<String, Object>> columns = crudSchemaQueryService.fetchColumns(database, tableName);
        if (columns.isEmpty()) {
            return "테이블을 찾을 수 없습니다: " + database + "." + tableName;
        }

        String resolvedVersion = resolveEgovVersion(egovVersion);
        CrudViewType resolvedViewType = CrudViewType.from(viewType);
        CrudTemplateModel model = crudModelFactory.fromSchema(
                tableName, domain, packageName, resolvedVersion, columns);
        String layerKey = selectLayerKey(resolvedViewType, jspLayerKey, thymeleafLayerKey);
        String code = crudTemplateRenderer.renderByLayerKey(layerKey, model);
        String filePath = resolveCrudScreenPath(outputPath, packageName, model.domainLc(), domain, resolvedViewType, layerKey);
        return formatGeneratedScreen("CRUD", domain, screenName, resolvedViewType, layerKey, filePath, code);
    }

    private String generateBoardScreen(
            String database,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String mainTable,
            @Nullable String masterTable,
            @Nullable String useTable,
            @Nullable String fileTable,
            @Nullable String fileDetailTable,
            @Nullable String egovVersion,
            @Nullable String viewType,
            String screenName,
            String jspLayerKey,
            String thymeleafLayerKey) {

        String resolvedMain       = defaultIfBlank(mainTable, "COMTNBBS");
        String resolvedMaster     = defaultIfBlank(masterTable, "COMTNBBSMASTER");
        String resolvedUse        = defaultIfBlank(useTable, "COMTNBBSUSE");
        String resolvedFile       = defaultIfBlank(fileTable, "COMTNFILE");
        String resolvedFileDetail = defaultIfBlank(fileDetailTable, "COMTNFILEDETAIL");
        String resolvedVersion    = resolveEgovVersion(egovVersion);
        CrudViewType resolvedViewType = CrudViewType.from(viewType);

        Map<String, List<Map<String, Object>>> schemas;
        try {
            schemas = boardSchemaService.fetchBoardSchemas(
                    database, resolvedMain, resolvedMaster, resolvedUse, resolvedFile, resolvedFileDetail);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        BoardTemplateModel model = boardModelFactory.fromSchemas(
                resolvedMain, resolvedMaster, resolvedUse, resolvedFileDetail,
                domain, packageName, resolvedVersion, schemas);
        String layerKey = selectLayerKey(resolvedViewType, jspLayerKey, thymeleafLayerKey);
        String code = boardTemplateRenderer.renderByLayerKey(layerKey, model);
        String filePath = resolveBoardScreenPath(outputPath, packageName, model.domainLc(), domain, resolvedViewType, layerKey);
        return formatGeneratedScreen("BOARD", domain, screenName, resolvedViewType, layerKey, filePath, code);
    }

    private String generateMasterDetailScreen(
            String database,
            String masterTable,
            String detailTable,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType,
            String screenName,
            String jspLayerKey,
            String thymeleafLayerKey) {

        List<Map<String, Object>> masterColumns = crudSchemaQueryService.fetchColumns(database, masterTable);
        if (masterColumns.isEmpty()) {
            return "마스터 테이블을 찾을 수 없습니다: " + database + "." + masterTable;
        }
        List<Map<String, Object>> detailColumns = crudSchemaQueryService.fetchColumns(database, detailTable);
        if (detailColumns.isEmpty()) {
            return "디테일 테이블을 찾을 수 없습니다: " + database + "." + detailTable;
        }

        String resolvedVersion = resolveEgovVersion(egovVersion);
        CrudViewType resolvedViewType = CrudViewType.from(viewType);
        String detailDomain = deriveDetailDomain(detailTable);
        CrudTemplateModel masterModel =
                crudModelFactory.fromSchema(masterTable, domain, packageName, resolvedVersion, masterColumns);
        CrudTemplateModel detailModel =
                crudModelFactory.fromSchema(detailTable, detailDomain, packageName, resolvedVersion, detailColumns);
        String fkColumn = detectFkColumn(masterModel.pk().columnName(), detailColumns);
        MasterDetailTemplateModel model = new MasterDetailTemplateModel(
                masterModel, detailModel, fkColumn, CrudMappingUtils.toCamelCase(fkColumn));
        String layerKey = selectLayerKey(resolvedViewType, jspLayerKey, thymeleafLayerKey);
        String code = masterDetailTemplateRenderer.renderByLayerKey(layerKey, model);
        String filePath = resolveMasterDetailScreenPath(outputPath, packageName, masterModel.domainLc(),
                domain, detailDomain, resolvedViewType, layerKey);
        return formatGeneratedScreen("MASTER_DETAIL", domain, screenName, resolvedViewType, layerKey, filePath, code);
    }

    private String resolveCrudScreenPath(
            String outputPath, String packageName, String domainLc, String domain,
            CrudViewType viewType, String layerKey) {
        CrudLayerDefinition layer = CrudLayerDefinition.forViewType(viewType).stream()
                .filter(candidate -> candidate.layerKey().equals(layerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 layerKey: " + layerKey));
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        String fileName = CrudLayerDefinition.resolveFileName(layerKey, domain, layer.fileNameSuffix());
        return outputPath + "/" + layer.resolveSubPath(pkgSub, domainLc) + fileName;
    }

    private String resolveBoardScreenPath(
            String outputPath, String packageName, String domainLc, String domain,
            CrudViewType viewType, String layerKey) {
        BoardLayerDefinition layer = BoardLayerDefinition.forViewType(viewType).stream()
                .filter(candidate -> candidate.layerKey().equals(layerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 layerKey: " + layerKey));
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        String fileName = BoardLayerDefinition.resolveFileName(layer.layerKey(), domain, layer.fileNameSuffix());
        return outputPath + "/" + layer.resolveSubPath(pkgSub, domainLc) + fileName;
    }

    private String resolveMasterDetailScreenPath(
            String outputPath, String packageName, String domainLc, String masterDomain, String detailDomain,
            CrudViewType viewType, String layerKey) {
        MasterDetailLayerDefinition layer = MasterDetailLayerDefinition.forViewType(viewType).stream()
                .filter(candidate -> candidate.layerKey().equals(layerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 layerKey: " + layerKey));
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        String fileName = MasterDetailLayerDefinition.resolveFileName(layer, masterDomain, detailDomain);
        return outputPath + "/" + layer.resolveSubPath(pkgSub, domainLc) + fileName;
    }

    private String formatGeneratedScreen(
            String featureType,
            String domain,
            String screenName,
            CrudViewType viewType,
            String layerKey,
            String filePath,
            String code) {
        return """
                === 화면 소스 생성 완료 ===

                featureType: %s
                domain: %s
                screen: %s
                viewType: %s
                layerKey: %s
                권장 저장 경로: %s

                [source]
                %s
                """.formatted(featureType, domain, screenName, viewType.value(), layerKey, filePath, code);
    }

    private String selectLayerKey(CrudViewType viewType, String jspLayerKey, String thymeleafLayerKey) {
        return viewType == CrudViewType.THYMELEAF ? thymeleafLayerKey : jspLayerKey;
    }

    private String resolveEgovVersion(@Nullable String egovVersion) {
        return (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
    }

    private String defaultIfBlank(@Nullable String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String detectFkColumn(String masterPkColumn, List<Map<String, Object>> detailColumns) {
        return detailColumns.stream()
                .map(c -> (String) c.get("COLUMN_NAME"))
                .filter(masterPkColumn::equals)
                .findFirst()
                .orElse(masterPkColumn);
    }

    private String deriveDetailDomain(String tableName) {
        String base = tableName.replace("COMTN", "").replace("COMTS", "").replace("COMTC", "");
        String[] parts = base.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private String formatBoardResult(BoardOrchestrationResult r) {
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

    private String formatMasterDetailResult(MasterDetailOrchestrationResult r) {
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

    // ── 결과 포맷터 ───────────────────────────────────────────────────────────
    // 기존 orchestrateAuto() 출력 형식과 동일하게 유지하여 MCP 사용자 UX 회귀 방지

    private String formatResult(CrudOrchestrationResult r) {
        if (r.tableNotFound()) {
            return "테이블을 찾을 수 없습니다: " + r.database() + "." + r.tableName();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(r.successCount() == 0 && r.hasFailure()
                ? "=== [auto] eGovFrame 5.x CRUD 소스 생성 실패 ===\n\n"
                : "=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===\n\n");
        sb.append("DB: ").append(r.database())
          .append(" | 테이블: ").append(r.tableName())
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
