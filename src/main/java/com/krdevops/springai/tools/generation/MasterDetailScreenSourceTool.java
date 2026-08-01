package com.krdevops.springai.tools.generation;

import com.krdevops.springai.service.generation.mcp.ScreenSourceMcpFacade;
import com.krdevops.springai.service.generation.model.ScreenType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** Master/Detail 단일 화면 Source MCP Adapter. */
@Component
@RequiredArgsConstructor
public class MasterDetailScreenSourceTool {
    private final ScreenSourceMcpFacade facade;

    private String generate(ScreenType type, String database, String masterTable, String detailTable, String domain,
                            String packageName, String outputPath, String egovVersion, String viewType) {
        return facade.generateMasterDetailScreenSource(type, database, masterTable, detailTable, domain, packageName,
                outputPath, egovVersion, viewType);
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
    public String generateMasterList(String database, String masterTable, String detailTable, String domain,
                                     String packageName, String outputPath, @Nullable String egovVersion,
                                     @Nullable String viewType) {
        return generate(ScreenType.LIST, database, masterTable, detailTable, domain, packageName, outputPath,
                egovVersion, viewType);
    }

    @Tool(description = """
            마스터-디테일 구조의 마스터 상세 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateMasterDetail(String database, String masterTable, String detailTable, String domain,
                                       String packageName, String outputPath, @Nullable String egovVersion,
                                       @Nullable String viewType) {
        return generate(ScreenType.DETAIL, database, masterTable, detailTable, domain, packageName, outputPath,
                egovVersion, viewType);
    }

    @Tool(description = """
            마스터-디테일 구조의 마스터 등록 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateMasterRegist(String database, String masterTable, String detailTable, String domain,
                                       String packageName, String outputPath, @Nullable String egovVersion,
                                       @Nullable String viewType) {
        return generate(ScreenType.REGIST, database, masterTable, detailTable, domain, packageName, outputPath,
                egovVersion, viewType);
    }

    @Tool(description = """
            마스터-디테일 구조의 마스터 수정 화면 1개만 렌더링하여 반환합니다.
            파일 저장은 하지 않으며, 권장 저장 경로와 화면 코드만 반환합니다.
            """)
    public String generateMasterUpdt(String database, String masterTable, String detailTable, String domain,
                                     String packageName, String outputPath, @Nullable String egovVersion,
                                     @Nullable String viewType) {
        return generate(ScreenType.UPDT, database, masterTable, detailTable, domain, packageName, outputPath,
                egovVersion, viewType);
    }
}
