package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.service.generation.mcp.ThymeleafLayoutMcpFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThymeleafLayoutTool {

    private final ThymeleafLayoutMcpFacade thymeleafLayoutMcpFacade;

    public String generateThymeleafLayout(
            String outputPath,
            @Nullable String layoutBasePath,
            @Nullable Boolean overwriteLayout,
            @Nullable String packageName) {
        return generateThymeleafLayout(
                outputPath,
                layoutBasePath,
                overwriteLayout,
                packageName,
                null,
                null);
    }

    @McpToolRisk(McpToolRiskLevel.FILE_WRITE)
    @Tool(description = """
            Thymeleaf 공통 layout 파일 5종(default.html, gnb.html, lnb.html, breadcrumb.html, footer.html)과
            GNB 동적 메뉴 컴포넌트 4종(GnbMenuVO.java, GnbMenuMapper.java/xml, EgovGnbMenuInterceptor.java)을 생성하고,
            GNB 브랜드 영역에 쓸 eGovFrame 로고 이미지(src/main/webapp/resources/images/egov-logo.png)를 생성하고,
            MainController가 반환하는 egovframework/main/main 뷰를 Thymeleaf main.html로 렌더링하도록 메인 화면을 생성합니다.
            WAR 기본 진입점(index.jsp/index.html/web.xml)은 화면 생성기의 책임이므로 변경하지 않습니다.
            WAR 프로젝트의 servlet-context.xml에 EgovGnbMenuInterceptor 등록 블록을 자동으로 patch합니다(이미 등록되어 있으면 skip).
            GNB Mapper가 동작하도록 context-common.xml의 mapperLocations와 MapperScannerConfigurer도 자동으로 보강합니다.
            또한 Thymeleaf 런타임 의존성과 ViewResolver를 보강해 JSP resolver보다 classpath:/templates/*.html 화면을 우선 렌더링합니다.
            GNB는 menuTableName(기본 LETTNMENUINFO, UPPER_MENU_NO=0)+programTableName(기본 LETTNPROGRMLIST)을 조회해 매 요청마다 동적으로 렌더링됩니다.
            생성되는 layout HTML은 인라인 style을 생성하지 않고 initializeProject()가 만든 /resources/css/styles.css의 egov-* 공통 클래스를 사용합니다.
            CrudGenerationTool의 Thymeleaf 생성은 layoutMode=reuse가 기본값이므로,
            신규 프로젝트에서는 buildFullCrudPrompt/buildBoardFeature/buildMasterDetailPrompt 실행 전에 이 Tool을 먼저 호출하세요.

            outputPath      : 프로젝트 루트 절대경로
            layoutBasePath  : templates 아래 layout base 경로 (기본값: "layout")
              - "layout"       => src/main/resources/templates/layout/*.html
              - "layout/admin" => src/main/resources/templates/layout/admin/*.html
            overwriteLayout : 기존 layout/GNB 컴포넌트 파일 덮어쓰기 여부 (기본값 true)
              - true : 기존 파일 갱신(생략 시 기본 동작)
              - false: 기존 파일 보존(커스터마이징한 layout/인터셉터를 유지하려면 명시적으로 false 지정)
            packageName     : GNB 메뉴 컴포넌트가 생성될 패키지 (예: egovframework.let.emp)
              [중요] initializeProject()에 전달했던 packageName과 반드시 동일해야 합니다.
              다르면 EgovGnbMenuInterceptor가 실제 CRUD 패키지와 어긋난 위치에 생성되어 동작하지 않습니다.
              생략 시 "egovframework.let.sample"을 쓰지만 실제 프로젝트에서는 반드시 명시하세요.
            menuTableName   : 메뉴 테이블명 (기본값: "LETTNMENUINFO")
            programTableName: 프로그램 테이블명 (기본값: "LETTNPROGRMLIST")
            [1차 구현 제약] WAR 프로젝트만 지원(Boot는 서보플릿 XML이 없어 인터셉터 등록 불가),
              Jakarta Servlet(eGovFrame 5.0)만 지원(4.3/javax는 미지원).
            """)
    public String generateThymeleafLayout(
            String outputPath,
            @Nullable String layoutBasePath,
            @Nullable Boolean overwriteLayout,
            @Nullable String packageName,
            @Nullable String menuTableName,
            @Nullable String programTableName) {
        return thymeleafLayoutMcpFacade.generateThymeleafLayout(
                outputPath, layoutBasePath, overwriteLayout, packageName, menuTableName, programTableName);
    }
}
