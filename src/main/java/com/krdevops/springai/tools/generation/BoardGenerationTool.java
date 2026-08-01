package com.krdevops.springai.tools.generation;

import com.krdevops.springai.service.generation.mcp.BoardGenerationMcpFacade;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** 게시판 전체 생성 MCP Adapter. */
@Component
@RequiredArgsConstructor
public class BoardGenerationTool {
    private final BoardGenerationMcpFacade facade;

    @Tool(description = """
            eGovFrame 게시판(BBS) 소스를 업무 단위로 생성합니다.
            이 Tool은 프로젝트 초기화용이 아닙니다. initializeProject()와는 별도 단계입니다.
            사용자가 게시판(BBS) 생성을 명시했을 때만 호출하세요.
            LETTNBBS(게시글), LETTNBBSMASTER(게시판마스터), LETTNBBSUSE(사용권한) 연동 포함.
            목록/상세/등록/수정/논리삭제 + 조회수 증가 + 마스터 이름 조회를 한 번에 생성합니다.
            database        : 데이터베이스명 (예: com)
            domain          : 도메인명 PascalCase (예: Bbs)
            packageName     : 패키지명 (예: egovframework.let.bbs)
            outputPath      : 소스 저장 절대경로
            mainTable       : 게시글 테이블 (기본값: LETTNBBS)
            masterTable     : 게시판 마스터 테이블 (기본값: LETTNBBSMASTER)
            useTable        : 게시판 사용/권한 테이블 (기본값: LETTNBBSUSE, 생략 가능)
            fileTable       : 첨부파일 묶음 테이블 (기본값: LETTNFILE, 생략 가능)
            fileDetailTable : 첨부파일 상세 테이블 (기본값: LETTNFILEDETAIL, 생략 가능)
            egovVersion     : eGovFrame 버전 (기본값: "5.0")
            viewType        : 화면 종류 (기본값: "jsp", "thymeleaf" 선택 가능)
              - "jsp"       : JSP 화면 4개 포함 12개 파일 생성
              - "thymeleaf" : HTML 화면 4개 포함 12개 파일 생성(layoutMode=create 시 17개)
            layoutMode      : Thymeleaf layout 처리 방식 (선택, 기본값 "reuse")
              - "reuse" : 기존 layout 재사용. layout이 없으면 generateThymeleafLayout() 실행 안내와 함께 실패
              - "create": layout 레이어까지 함께 생성
            layoutView      : 화면이 참조할 layout 경로 (선택, 기본값 "layout/default")
            breadcrumbView  : 화면이 참조할 breadcrumb 경로 (선택, 기본값 "layout/breadcrumb")
            programFileName : 프로그램 파일명. 명시값이 DB 자동조회보다 우선합니다.
            programUrl      : LETTNPROGRMLIST URL. query는 bbsId로, path는 Controller alias로 사용합니다.
            programKoreanName: 화면 title/H1/caption에 사용할 프로그램 한글명입니다.
            programStorePath: 프로그램 저장 경로 메타데이터입니다.
            defaultBbsId    : 요청 bbsId가 없을 때만 사용할 게시판 ID이며 마스터 테이블에서 검증합니다.
            designReferenceId: analyzeDesignReference()가 반환한 분석 ID입니다.
            screenSpecificationId: 게시글 주 테이블 기준 APPROVED 화면명세 ID입니다.
            우선순위는 명시 파라미터 > DB 자동조회 > 기존 규칙 fallback 입니다.
            layout 파일은 generateThymeleafLayout()로 먼저 생성하는 것을 권장합니다.
            정적 리소스    : 생성 화면은 initializeProject()가 만든 /resources/css/styles.css와 /resources/js/krds.min.js를 사용합니다.
              - WAR는 webapp/resources/**, BOOT는 static/resources/** 에 파일이 생성되며 URL은 /resources/** 로 동일하게 유지합니다.
              - _ds_bundle.css는 styles.css 내부 @import 대상이므로 화면에서 별도 링크하지 않습니다.
            """)
    public String buildBoardFeature(String database, String domain, String packageName, String outputPath,
                                    @Nullable String mainTable, @Nullable String masterTable,
                                    @Nullable String useTable, @Nullable String fileTable,
                                    @Nullable String fileDetailTable, @Nullable String egovVersion,
                                    @Nullable String viewType, @Nullable String layoutMode,
                                    @Nullable String layoutView, @Nullable String breadcrumbView,
                                    @Nullable String programFileName, @Nullable String programUrl,
                                    @Nullable String programKoreanName, @Nullable String programStorePath,
                                    @Nullable String defaultBbsId, @Nullable String designReferenceId,
                                    @Nullable String screenSpecificationId) {
        return facade.buildBoardFeature(database, domain, packageName, outputPath, mainTable, masterTable,
                useTable, fileTable, fileDetailTable, egovVersion, viewType, layoutMode, layoutView,
                breadcrumbView, programFileName, programUrl, programKoreanName, programStorePath,
                defaultBbsId, designReferenceId, screenSpecificationId);
    }
}
