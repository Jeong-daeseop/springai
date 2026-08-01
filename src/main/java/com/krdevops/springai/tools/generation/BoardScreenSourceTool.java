package com.krdevops.springai.tools.generation;

import com.krdevops.springai.service.generation.mcp.ScreenSourceMcpFacade;
import com.krdevops.springai.service.generation.model.ScreenType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** 게시판 단일 화면 Source MCP Adapter. */
@Component
@RequiredArgsConstructor
public class BoardScreenSourceTool {
    private final ScreenSourceMcpFacade facade;

    private String generate(ScreenType type, String database, String domain, String packageName, String outputPath,
                            String mainTable, String masterTable, String useTable, String fileTable,
                            String fileDetailTable, String egovVersion, String viewType, String programFileName,
                            String programUrl, String programKoreanName, String programStorePath, String defaultBbsId) {
        return facade.generateBoardScreenSource(type, database, domain, packageName, outputPath, mainTable,
                masterTable, useTable, fileTable, fileDetailTable, egovVersion, viewType, programFileName,
                programUrl, programKoreanName, programStorePath, defaultBbsId);
    }

    @Tool(description = """
            게시판(BBS) 목록 화면 1개만 렌더링하여 반환합니다.
            `generateBoardList`는 기존 README의 화면별 MCP Tool 패턴과 호환하기 위해 추가된 세분 Tool입니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            database        : 데이터베이스명
            domain          : 도메인명 PascalCase
            packageName     : 패키지명 (egovframework.let.*)
            outputPath      : 저장 기준 절대경로
            mainTable       : 게시글 테이블 (기본값 LETTNBBS)
            masterTable     : 게시판 마스터 테이블 (기본값 LETTNBBSMASTER)
            useTable        : 게시판 사용/권한 테이블 (기본값 LETTNBBSUSE)
            fileTable       : 첨부파일 묶음 테이블 (기본값 LETTNFILE)
            fileDetailTable : 첨부파일 상세 테이블 (기본값 LETTNFILEDETAIL)
            egovVersion     : eGovFrame 버전 (기본값 5.0)
            viewType        : jsp 또는 thymeleaf (기본값 jsp)
            programFileName/programUrl/programKoreanName/programStorePath/defaultBbsId:
              buildBoardFeature와 동일한 프로그램 메타데이터 선택값이며 명시값 > DB > fallback 순입니다.
            """)
    public String generateBoardList(String database, String domain, String packageName, String outputPath,
                                    @Nullable String mainTable, @Nullable String masterTable, @Nullable String useTable,
                                    @Nullable String fileTable, @Nullable String fileDetailTable,
                                    @Nullable String egovVersion, @Nullable String viewType,
                                    @Nullable String programFileName, @Nullable String programUrl,
                                    @Nullable String programKoreanName, @Nullable String programStorePath,
                                    @Nullable String defaultBbsId) {
        return generate(ScreenType.LIST, database, domain, packageName, outputPath, mainTable, masterTable, useTable,
                fileTable, fileDetailTable, egovVersion, viewType, programFileName, programUrl, programKoreanName,
                programStorePath, defaultBbsId);
    }

    @Tool(description = """
            게시판(BBS) 상세 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            프로그램 메타데이터 선택값은 buildBoardFeature와 동일하게 적용됩니다.
            """)
    public String generateBoardDetail(String database, String domain, String packageName, String outputPath,
                                      @Nullable String mainTable, @Nullable String masterTable, @Nullable String useTable,
                                      @Nullable String fileTable, @Nullable String fileDetailTable,
                                      @Nullable String egovVersion, @Nullable String viewType,
                                      @Nullable String programFileName, @Nullable String programUrl,
                                      @Nullable String programKoreanName, @Nullable String programStorePath,
                                      @Nullable String defaultBbsId) {
        return generate(ScreenType.DETAIL, database, domain, packageName, outputPath, mainTable, masterTable, useTable,
                fileTable, fileDetailTable, egovVersion, viewType, programFileName, programUrl, programKoreanName,
                programStorePath, defaultBbsId);
    }

    @Tool(description = """
            게시판(BBS) 등록 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            프로그램 메타데이터 선택값은 buildBoardFeature와 동일하게 적용됩니다.
            """)
    public String generateBoardRegist(String database, String domain, String packageName, String outputPath,
                                      @Nullable String mainTable, @Nullable String masterTable, @Nullable String useTable,
                                      @Nullable String fileTable, @Nullable String fileDetailTable,
                                      @Nullable String egovVersion, @Nullable String viewType,
                                      @Nullable String programFileName, @Nullable String programUrl,
                                      @Nullable String programKoreanName, @Nullable String programStorePath,
                                      @Nullable String defaultBbsId) {
        return generate(ScreenType.REGIST, database, domain, packageName, outputPath, mainTable, masterTable, useTable,
                fileTable, fileDetailTable, egovVersion, viewType, programFileName, programUrl, programKoreanName,
                programStorePath, defaultBbsId);
    }

    @Tool(description = """
            게시판(BBS) 수정 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            프로그램 메타데이터 선택값은 buildBoardFeature와 동일하게 적용됩니다.
            """)
    public String generateBoardUpdt(String database, String domain, String packageName, String outputPath,
                                    @Nullable String mainTable, @Nullable String masterTable, @Nullable String useTable,
                                    @Nullable String fileTable, @Nullable String fileDetailTable,
                                    @Nullable String egovVersion, @Nullable String viewType,
                                    @Nullable String programFileName, @Nullable String programUrl,
                                    @Nullable String programKoreanName, @Nullable String programStorePath,
                                    @Nullable String defaultBbsId) {
        return generate(ScreenType.UPDT, database, domain, packageName, outputPath, mainTable, masterTable, useTable,
                fileTable, fileDetailTable, egovVersion, viewType, programFileName, programUrl, programKoreanName,
                programStorePath, defaultBbsId);
    }
}
