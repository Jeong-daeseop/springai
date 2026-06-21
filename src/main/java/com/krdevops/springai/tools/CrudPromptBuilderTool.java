package com.krdevops.springai.tools;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.BoardOrchestrationService;
import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.CrudOrchestrationService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.MasterDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrudPromptBuilderTool {

    private final CrudOrchestrationService crudOrchestrationService;
    private final CrudPromptBuilderService crudPromptBuilderService;
    private final MasterDetailService      masterDetailService;
    private final BoardOrchestrationService boardOrchestrationService;

    @Tool(description = """
            eGovFrame 5.x CRUD 전체 소스 생성에 필요한 통합 프롬프트를 반환합니다.
            이 Tool 하나로 getTableSchema + 공통코드 조회 + 플레이스홀더 매핑을 한 번에 처리합니다.
            반환된 프롬프트의 지시에 따라 viewType별 레이어 소스를 순서대로 생성하고 저장하세요.
            (JSP: 11개, Thymeleaf: layout/default.html 포함 12개)
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
                                      @Nullable String viewType) {
        String resolved = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        String provider = (llmProvider == null || llmProvider.isBlank()) ? "auto"
                          : llmProvider.trim().toLowerCase();
        String resolvedViewType = (viewType == null || viewType.isBlank()) ? "jsp" : viewType;

        if ("auto".equals(provider)) {
            CrudOrchestrationResult result = crudOrchestrationService.orchestrate(
                    database, tableName, domain, packageName, outputPath, resolved, resolvedViewType);
            return formatResult(result);
        }
        return crudPromptBuilderService.buildFullCrudPrompt(
                database, tableName, domain, packageName, outputPath, resolved, resolvedViewType);
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

    @Tool(description = """
            eGovFrame 게시판(BBS) 소스를 업무 단위로 생성합니다.
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
            @Nullable String viewType) {

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
            resolvedVersion, resolvedViewType);

        return formatBoardResult(result);
    }

    private String formatBoardResult(BoardOrchestrationResult r) {
        if (r.tableNotFound()) {
            return "게시판 테이블을 찾을 수 없습니다: " + r.database() + "." + r.mainTable();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== [auto] eGovFrame 게시판(BBS) 소스 생성 완료 ===\n\n");
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

    // ── 결과 포맷터 ───────────────────────────────────────────────────────────
    // 기존 orchestrateAuto() 출력 형식과 동일하게 유지하여 MCP 사용자 UX 회귀 방지

    private String formatResult(CrudOrchestrationResult r) {
        if (r.tableNotFound()) {
            return "테이블을 찾을 수 없습니다: " + r.database() + "." + r.tableName();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===\n\n");
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
