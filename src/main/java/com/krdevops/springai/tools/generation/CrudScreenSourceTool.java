package com.krdevops.springai.tools.generation;

import com.krdevops.springai.service.generation.mcp.ScreenSourceMcpFacade;
import com.krdevops.springai.service.generation.model.ScreenType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** CRUD 단일 화면 Source MCP Adapter. */
@Component
@RequiredArgsConstructor
public class CrudScreenSourceTool {
    private final ScreenSourceMcpFacade facade;

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
    public String generateCrudList(String database, String tableName, String domain, String packageName,
                                   String outputPath, @Nullable String egovVersion, @Nullable String viewType) {
        return facade.generateCrudScreenSource(ScreenType.LIST, database, tableName, domain, packageName,
                outputPath, egovVersion, viewType);
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
    public String generateCrudDetail(String database, String tableName, String domain, String packageName,
                                     String outputPath, @Nullable String egovVersion, @Nullable String viewType) {
        return facade.generateCrudScreenSource(ScreenType.DETAIL, database, tableName, domain, packageName,
                outputPath, egovVersion, viewType);
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
    public String generateCrudRegist(String database, String tableName, String domain, String packageName,
                                     String outputPath, @Nullable String egovVersion, @Nullable String viewType) {
        return facade.generateCrudScreenSource(ScreenType.REGIST, database, tableName, domain, packageName,
                outputPath, egovVersion, viewType);
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
    public String generateCrudUpdt(String database, String tableName, String domain, String packageName,
                                   String outputPath, @Nullable String egovVersion, @Nullable String viewType) {
        return facade.generateCrudScreenSource(ScreenType.UPDT, database, tableName, domain, packageName,
                outputPath, egovVersion, viewType);
    }
}
