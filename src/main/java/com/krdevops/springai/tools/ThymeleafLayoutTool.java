package com.krdevops.springai.tools;

import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ThymeleafLayoutTool {

    private final CrudTemplateRenderer crudTemplateRenderer;
    private final CodeService codeService;
    private final ThymeleafLayoutValidator thymeleafLayoutValidator;
    private final ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;

    private static final String DEFAULT_PACKAGE_NAME = "egovframework.let.sample";
    private static final String DEFAULT_EGOV_VERSION = "5.0";
    private static final String DEFAULT_MENU_TABLE_NAME = "LETTNMENUINFO";
    private static final String DEFAULT_PROGRAM_TABLE_NAME = "LETTNPROGRMLIST";
    private static final String SERVLET_CONTEXT_XML_RELATIVE_PATH =
            "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml";
    private static final String CONTEXT_COMMON_XML_RELATIVE_PATH =
            "src/main/resources/egovframework/spring/context-common.xml";
    private static final Pattern COMPONENT_SCAN_BASE_PACKAGE_PATTERN = Pattern.compile(
            "<context:component-scan\\b([^>]*?\\bbase-package\\s*=\\s*\")([^\"]*)(\"[^>]*>)",
            Pattern.DOTALL);
    private static final Pattern SQL_SESSION_FACTORY_BEAN_PATTERN = Pattern.compile(
            "(<bean\\s+id=\"sqlSessionFactory\"\\s+class=\"org\\.mybatis\\.spring\\.SqlSessionFactoryBean\"[^>]*>.*?"
                    + "<property\\s+name=\"dataSource\"\\s+ref=\"dataSource\"\\s*/>)(.*?</bean>)",
            Pattern.DOTALL);
    private static final Pattern MAPPER_SCANNER_BASE_PACKAGE_PATTERN = Pattern.compile(
            "(<bean\\b[^>]*class=\"org\\.mybatis\\.spring\\.mapper\\.MapperScannerConfigurer\"[^>]*>.*?"
                    + "<property\\s+name=\"basePackage\"\\s+value=\")([^\"]*)(\"[^>]*/>.*?</bean>)",
            Pattern.DOTALL);

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

    @Tool(description = """
            Thymeleaf 공통 layout 파일 5종(default.html, gnb.html, lnb.html, breadcrumb.html, footer.html)과
            GNB 동적 메뉴 컴포넌트 4종(GnbMenuVO.java, GnbMenuMapper.java/xml, EgovGnbMenuInterceptor.java)을 생성하고,
            MainController가 반환하는 egovframework/main/main 뷰를 Thymeleaf main.html로 렌더링하도록 메인 화면을 생성하고,
            WAR 기본 진입점은 index.jsp 대신 index.html로 전환합니다.
            WAR 프로젝트의 servlet-context.xml에 EgovGnbMenuInterceptor 등록 블록을 자동으로 patch합니다(이미 등록되어 있으면 skip).
            GNB Mapper가 동작하도록 context-common.xml의 mapperLocations와 MapperScannerConfigurer도 자동으로 보강합니다.
            또한 Thymeleaf 런타임 의존성과 ViewResolver를 보강해 JSP resolver보다 classpath:/templates/*.html 화면을 우선 렌더링합니다.
            GNB는 menuTableName(기본 LETTNMENUINFO, UPPER_MENU_NO=0)+programTableName(기본 LETTNPROGRMLIST)을 조회해 매 요청마다 동적으로 렌더링됩니다.
            생성되는 layout HTML은 인라인 style을 생성하지 않고 initializeProject()가 만든 /resources/css/styles.css의 egov-* 공통 클래스를 사용합니다.
            CrudPromptBuilderTool의 Thymeleaf 생성은 layoutMode=reuse가 기본값이므로,
            신규 프로젝트에서는 buildFullCrudPrompt/buildBoardFeature/buildMasterDetailPrompt 실행 전에 이 Tool을 먼저 호출하세요.

            outputPath      : 프로젝트 루트 절대경로
            layoutBasePath  : templates 아래 layout base 경로 (기본값: "layout")
              - "layout"       => src/main/resources/templates/layout/*.html
              - "layout/admin" => src/main/resources/templates/layout/admin/*.html
            overwriteLayout : 기존 layout/GNB 컴포넌트 파일 덮어쓰기 여부 (기본값 false)
              - false: 기존 파일 보존
              - true : 기존 파일 갱신
            packageName     : GNB 메뉴 컴포넌트가 생성될 패키지 (예: egovframework.let.emp)
              [중요] initializeProject()에 전달했던 packageName과 반드시 동일해야 합니다.
              다르면 EgovGnbMenuInterceptor가 실제 CRUD 패키지와 어긋난 위치에 생성되어 동작하지 않습니다.
              생략 시 "egovframework.let.sample"을 쓰지만 실제 프로젝트에서는 반드시 명시하세요.
            menuTableName   : 메뉴 테이블명 (기본값: "LETTNMENUINFO")
                              "COMTNMENUINFO" 또는 "com.COMTNMENUINFO" 입력 시 "LETTNMENUINFO"로 매핑합니다.
            programTableName: 프로그램 테이블명 (기본값: "LETTNPROGRMLIST")
                              "COMTNPROGRMLIST" 또는 "com.COMTNPROGRMLIST" 입력 시 "LETTNPROGRMLIST"로 매핑합니다.
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

        String resolvedBasePath = thymeleafLayoutValidator.normalizeLayoutBasePath(layoutBasePath);
        boolean overwrite = Boolean.TRUE.equals(overwriteLayout);
        boolean packageNameMissing = packageName == null || packageName.isBlank();
        String resolvedPackageName = packageNameMissing ? DEFAULT_PACKAGE_NAME : packageName;
        String resolvedMenuTableName = normalizeLetTableName(menuTableName, DEFAULT_MENU_TABLE_NAME);
        String resolvedProgramTableName = normalizeLetTableName(programTableName, DEFAULT_PROGRAM_TABLE_NAME);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Thymeleaf layout 생성 결과 ===\n\n");
        if (packageNameMissing) {
            sb.append("⚠ packageName 미지정 — 기본값 '").append(DEFAULT_PACKAGE_NAME).append("' 사용. ")
              .append("실제 프로젝트 packageName과 다르면 GNB 컴포넌트가 컴파일되지 않거나 등록되지 않습니다.\n\n");
        }
        sb.append("출력 경로: ").append(outputPath).append("\n");
        sb.append("layoutBasePath: ").append(resolvedBasePath).append("\n");
        sb.append("packageName: ").append(resolvedPackageName).append("\n\n");
        sb.append("menuTableName: ").append(resolvedMenuTableName).append("\n");
        sb.append("programTableName: ").append(resolvedProgramTableName).append("\n\n");

        for (CrudLayerDefinition layer : CrudLayerDefinition.thymeleafLayoutLayers()) {
            String fileName = switch (layer.layerKey()) {
                case CrudLayerDefinition.LAYOUT_HTML -> "default.html";
                case CrudLayerDefinition.LAYOUT_GNB_HTML -> "gnb.html";
                case CrudLayerDefinition.LAYOUT_LNB_HTML -> "lnb.html";
                case CrudLayerDefinition.LAYOUT_BREADCRUMB_HTML -> "breadcrumb.html";
                case CrudLayerDefinition.LAYOUT_FOOTER_HTML -> "footer.html";
                default -> throw new IllegalArgumentException("layout layer가 아닙니다: " + layer.layerKey());
            };
            Path filePath = Paths.get(outputPath, "src/main/resources/templates", resolvedBasePath, fileName)
                    .normalize();
            if (!overwrite && Files.exists(filePath)) {
                sb.append("  보존: ").append(filePath).append("\n");
                continue;
            }
            String code = crudTemplateRenderer.renderLayoutByLayerKey(layer.layerKey(), resolvedBasePath);
            String saveResult = codeService.saveGeneratedCode(filePath.toString(), code);
            if (saveResult.startsWith("파일 저장 실패")) {
                sb.append("  실패: ").append(filePath).append(" — ").append(saveResult).append("\n");
            } else {
                sb.append("  생성: ").append(filePath).append("\n");
            }
        }

        String pkgSub = resolvedPackageName.replace(".", "/");
        for (CrudLayerDefinition layer : CrudLayerDefinition.GNB_MENU_COMPONENT_LAYERS) {
            String relativePath = layer.resolveSubPath(pkgSub, "") + layer.fileNameSuffix();
            Path filePath = Paths.get(outputPath, relativePath).normalize();
            if (!overwrite && Files.exists(filePath)) {
                sb.append("  보존: ").append(filePath).append("\n");
                continue;
            }
            String code = crudTemplateRenderer.renderGnbMenuComponent(
                    layer.layerKey(),
                    resolvedPackageName,
                    resolvedMenuTableName,
                    resolvedProgramTableName);
            String saveResult = codeService.saveGeneratedCode(filePath.toString(), code);
            if (saveResult.startsWith("파일 저장 실패")) {
                sb.append("  실패: ").append(filePath).append(" — ").append(saveResult).append("\n");
            } else {
                sb.append("  생성: ").append(filePath).append("\n");
            }
        }

        Path mainHtmlPath = Paths.get(outputPath,
                "src/main/resources/templates/egovframework/main/main.html").normalize();
        if (!overwrite && Files.exists(mainHtmlPath)) {
            sb.append("  보존: ").append(mainHtmlPath).append("\n");
        } else {
            String saveResult = codeService.saveGeneratedCode(
                    mainHtmlPath.toString(),
                    mainThymeleafHtml(resolvedBasePath + "/default", resolvedBasePath + "/breadcrumb"));
            if (saveResult.startsWith("파일 저장 실패")) {
                sb.append("  실패: ").append(mainHtmlPath).append(" — ").append(saveResult).append("\n");
            } else {
                sb.append("  생성: ").append(mainHtmlPath).append("\n");
            }
        }

        sb.append(convertWarWelcomeFileToIndexHtml(outputPath, overwrite));

        ThymeleafLayoutValidator.LayoutValidationResult validation =
                thymeleafLayoutValidator.validateExisting(
                        outputPath,
                        resolvedBasePath + "/default",
                        resolvedBasePath + "/breadcrumb");
        if (!validation.valid()) {
            sb.append("\n[검증 실패]\n")
              .append(String.join("\n", validation.missingFiles()))
              .append("\n");
        } else {
            sb.append("\n[검증 완료] layout 5종이 모두 존재합니다.\n");
        }
        sb.append("\n[servlet-context.xml 등록]\n");
        String servletContextPatchResult = patchServletContextXml(outputPath, resolvedPackageName);
        sb.append(servletContextPatchResult);

        sb.append("\n[context-common.xml MyBatis 설정]\n");
        sb.append(patchContextCommonXml(outputPath, resolvedPackageName));

        sb.append("\n[Thymeleaf 런타임 설정]\n");
        if (servletContextPatchResult.contains("실패:")) {
            sb.append("  건너뜀: servlet-context.xml patch 실패 상태라 ViewResolver 보강을 생략합니다.\n");
            return sb.toString();
        }

        List<String> runtimeFailures = new ArrayList<>();
        thymeleafRuntimeConfigurer.ensureThymeleafRuntime(outputPath, DEFAULT_EGOV_VERSION, runtimeFailures);
        if (runtimeFailures.isEmpty()) {
            sb.append("  완료: eGovFrame ").append(DEFAULT_EGOV_VERSION)
              .append(" 기준 Thymeleaf ViewResolver/classpath:/templates 런타임 설정을 확인했습니다.\n");
        } else {
            runtimeFailures.forEach(failure -> sb.append("  실패: ").append(failure).append("\n"));
        }
        return sb.toString();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static String normalizeLetTableName(String value, String defaultValue) {
        String tableName = defaultIfBlank(value, defaultValue);
        int schemaSeparator = tableName.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            tableName = tableName.substring(schemaSeparator + 1);
        }
        if (tableName.regionMatches(true, 0, "COM", 0, 3)) {
            return "LET" + tableName.substring(3);
        }
        return tableName;
    }

    private static String mainThymeleafHtml(String layoutView, String breadcrumbView) {
        return """
                <!DOCTYPE html>
                <html lang="ko"
                      xmlns:th="http://www.thymeleaf.org"
                      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
                      layout:decorate="~{%s}">
                <head>
                    <title>메인</title>
                </head>
                <body>
                <section layout:fragment="content" class="egov-main-dashboard">
                    <div class="egov-main-hero">
                        <p class="egov-main-kicker">eGovFrame 5.0</p>
                        <h1 class="egov-main-title">전자정부 표준프레임워크 메인 화면입니다.</h1>
                        <p class="egov-main-description">
                            Thymeleaf 공통 layout과 동적 GNB 메뉴를 사용하는 기본 메인 대시보드입니다.
                        </p>
                    </div>

                    <div class="egov-main-metrics" aria-label="프로젝트 상태 요약">
                        <div class="egov-main-metric">
                            <span class="egov-main-metric-label">Framework</span>
                            <strong class="egov-main-metric-value">5.0</strong>
                        </div>
                        <div class="egov-main-metric">
                            <span class="egov-main-metric-label">View</span>
                            <strong class="egov-main-metric-value">Thymeleaf</strong>
                        </div>
                        <div class="egov-main-metric">
                            <span class="egov-main-metric-label">Menu</span>
                            <strong class="egov-main-metric-value">Dynamic GNB</strong>
                        </div>
                    </div>

                    <div class="egov-main-grid">
                        <article class="egov-main-card">
                            <h2>업무 화면 생성</h2>
                            <p>CRUD, 게시판, 마스터-디테일 화면을 공통 layout 기반으로 확장할 수 있습니다.</p>
                        </article>
                        <article class="egov-main-card">
                            <h2>메뉴 연동</h2>
                            <p>LETTNMENUINFO와 LETTNPROGRMLIST를 조회해 GNB, LNB, 브레드크럼을 구성합니다.</p>
                        </article>
                        <article class="egov-main-card">
                            <h2>배포 준비</h2>
                            <p>Gradle WAR 빌드 후 외부 Tomcat 환경에 배포하는 표준 구성을 사용합니다.</p>
                        </article>
                    </div>
                </section>
                </body>
                </html>
                """.formatted(layoutView);
    }

    private String convertWarWelcomeFileToIndexHtml(String outputPath, boolean overwrite) {
        StringBuilder sb = new StringBuilder();
        Path indexHtmlPath = Paths.get(outputPath, "src/main/webapp/index.html").normalize();
        Path indexJspPath = Paths.get(outputPath, "src/main/webapp/index.jsp").normalize();

        if (!overwrite && Files.exists(indexHtmlPath)) {
            sb.append("  보존: ").append(indexHtmlPath).append("\n");
        } else {
            String saveResult = codeService.saveGeneratedCode(indexHtmlPath.toString(), indexHtml());
            if (saveResult.startsWith("파일 저장 실패")) {
                sb.append("  실패: ").append(indexHtmlPath).append(" — ").append(saveResult).append("\n");
            } else {
                sb.append("  생성: ").append(indexHtmlPath).append("\n");
            }
        }

        try {
            if (Files.deleteIfExists(indexJspPath)) {
                sb.append("  삭제: ").append(indexJspPath).append("\n");
            }
        } catch (IOException e) {
            sb.append("  실패: ").append(indexJspPath).append(" 삭제 오류 — ").append(e.getMessage()).append("\n");
        }

        sb.append(patchWebXmlWelcomeFile(outputPath));
        return sb.toString();
    }

    private static String indexHtml() {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta http-equiv="refresh" content="0;url=/egovframework/com/main.do">
                    <title>eGovFrame</title>
                </head>
                <body>
                    <a href="/egovframework/com/main.do">메인 화면으로 이동</a>
                </body>
                </html>
                """;
    }

    private String patchWebXmlWelcomeFile(String outputPath) {
        Path webXmlPath = Paths.get(outputPath, "src/main/webapp/WEB-INF/web.xml").normalize();
        if (!Files.exists(webXmlPath)) {
            return "  건너뜀: " + webXmlPath + " 파일이 없어 welcome-file 변경을 생략합니다.\n";
        }

        try {
            String webXml = Files.readString(webXmlPath);
            if (webXml.contains("<welcome-file>index.html</welcome-file>")) {
                return "  보존: " + webXmlPath + " (welcome-file=index.html)\n";
            }
            String patched = webXml.replace("<welcome-file>index.jsp</welcome-file>", "<welcome-file>index.html</welcome-file>");
            if (patched.equals(webXml)) {
                return "  실패: " + webXmlPath + " — index.jsp welcome-file을 찾을 수 없습니다.\n";
            }
            Files.writeString(webXmlPath, patched);
            return "  변경: " + webXmlPath + " welcome-file=index.html\n";
        } catch (IOException e) {
            return "  실패: " + webXmlPath + " welcome-file 변경 오류 — " + e.getMessage() + "\n";
        }
    }

    private String patchContextCommonXml(String outputPath, String packageName) {
        Path contextCommonPath = Paths.get(outputPath, CONTEXT_COMMON_XML_RELATIVE_PATH).normalize();
        if (!Files.exists(contextCommonPath)) {
            return "  건너뜀: " + contextCommonPath + " 파일이 없어 MyBatis mapper 설정 보강을 생략합니다.\n";
        }

        String content;
        try {
            content = Files.readString(contextCommonPath);
        } catch (IOException e) {
            return "  실패: " + contextCommonPath + " 읽기 오류 — " + e.getMessage() + "\n";
        }

        StringBuilder message = new StringBuilder();
        String patched = content;
        boolean changed = false;

        if (patched.contains("mapperLocations")) {
            message.append("  보존: ").append(contextCommonPath).append(" mapperLocations 이미 등록됨\n");
        } else {
            Matcher sqlSessionFactoryMatcher = SQL_SESSION_FACTORY_BEAN_PATTERN.matcher(patched);
            if (sqlSessionFactoryMatcher.find()) {
                patched = patched.substring(0, sqlSessionFactoryMatcher.end(1))
                        + "\n        <property name=\"mapperLocations\" value=\"classpath*:egovframework/mapper/**/*.xml\"/>"
                        + patched.substring(sqlSessionFactoryMatcher.end(1));
                changed = true;
                message.append("  등록: ").append(contextCommonPath).append(" mapperLocations 추가\n");
            } else {
                message.append("  실패: ").append(contextCommonPath)
                        .append(" SqlSessionFactoryBean 위치를 찾지 못해 mapperLocations를 추가하지 못했습니다.\n");
            }
        }

        MapperScannerPatch scannerPatch = patchMapperScannerConfigurer(patched, packageName, contextCommonPath);
        patched = scannerPatch.content();
        changed = changed || scannerPatch.changed();
        message.append(scannerPatch.message());

        if (changed) {
            try {
                Files.writeString(contextCommonPath, patched);
            } catch (IOException e) {
                return "  실패: " + contextCommonPath + " 쓰기 오류 — " + e.getMessage() + "\n";
            }
        }
        return message.toString();
    }

    private MapperScannerPatch patchMapperScannerConfigurer(
            String content,
            String packageName,
            Path contextCommonPath) {
        String requiredBasePackage = requiredComponentScanBasePackage(packageName);
        String targetMapperPackage = packageName + ".cmm.service";

        if (!content.contains("org.mybatis.spring.mapper.MapperScannerConfigurer")) {
            int beansCloseCount = countOccurrences(content, "</beans>");
            if (beansCloseCount != 1) {
                return new MapperScannerPatch(
                        content,
                        "  실패: " + contextCommonPath + " — </beans> 태그가 " + beansCloseCount
                                + "개 발견되어 MapperScannerConfigurer를 안전하게 삽입할 수 없습니다.\n",
                        false);
            }
            String scannerBlock = """

                    <bean class=\"org.mybatis.spring.mapper.MapperScannerConfigurer\">
                        <property name=\"basePackage\" value=\"%s\"/>
                        <property name=\"sqlSessionFactoryBeanName\" value=\"sqlSessionFactory\"/>
                        <property name=\"annotationClass\" value=\"org.apache.ibatis.annotations.Mapper\"/>
                    </bean>
                    """.formatted(requiredBasePackage);
            return new MapperScannerPatch(
                    content.replace("</beans>", scannerBlock + "\n</beans>"),
                    "  등록: " + contextCommonPath + " MapperScannerConfigurer 추가(basePackage="
                            + requiredBasePackage + ")\n",
                    true);
        }

        Matcher scannerMatcher = MAPPER_SCANNER_BASE_PACKAGE_PATTERN.matcher(content);
        if (!scannerMatcher.find()) {
            return new MapperScannerPatch(
                    content,
                    "  확인 필요: " + contextCommonPath
                            + " MapperScannerConfigurer basePackage 속성을 찾지 못해 스캔 범위를 자동 확인하지 못했습니다.\n",
                    false);
        }

        String basePackages = scannerMatcher.group(2);
        if (componentScanCovers(basePackages, targetMapperPackage)) {
            return new MapperScannerPatch(
                    content,
                    "  보존: " + contextCommonPath + " MapperScannerConfigurer basePackage="
                            + basePackages + " (GNB Mapper 스캔 범위 포함)\n",
                    false);
        }

        String patchedBasePackages = appendBasePackage(basePackages, requiredBasePackage);
        String patchedContent = content.substring(0, scannerMatcher.start(2))
                + patchedBasePackages
                + content.substring(scannerMatcher.end(2));
        return new MapperScannerPatch(
                patchedContent,
                "  변경: " + contextCommonPath + " MapperScannerConfigurer basePackage="
                        + patchedBasePackages + " (GNB Mapper 스캔 범위 보강)\n",
                true);
    }

    private record MapperScannerPatch(String content, String message, boolean changed) {
    }

    /**
     * WAR 프로젝트의 servlet-context.xml에 EgovGnbMenuInterceptor 등록 블록을 patch한다.
     * 파일을 새로 만들지 않는다 — servlet-context.xml 최초 생성은 ProjectInitializrTool 전속.
     * Boot 프로젝트(파일 자체가 없음)와 WAR 비표준 구조는 조용히 skip하고 안내만 반환한다(1차 구현 범위).
     */
    private String patchServletContextXml(String outputPath, String packageName) {
        Path servletContextPath = Paths.get(outputPath, SERVLET_CONTEXT_XML_RELATIVE_PATH).normalize();
        if (!Files.exists(servletContextPath)) {
            return "  건너뜀: " + servletContextPath + " 파일이 없습니다. "
                 + "Boot 프로젝트라면 정상입니다(1차 미지원 — WebMvcConfigurer 방식 별도 필요). "
                 + "WAR 프로젝트라면 initializeProject()로 먼저 생성했는지 확인하거나 "
                 + "<mvc:interceptors> 블록을 수동으로 추가하세요.\n";
        }

        String interceptorClass = packageName + ".cmm.web.EgovGnbMenuInterceptor";
        String content;
        try {
            content = Files.readString(servletContextPath);
        } catch (IOException e) {
            return "  실패: " + servletContextPath + " 읽기 오류 — " + e.getMessage() + "\n";
        }

        ComponentScanPatch componentScanPatch = patchComponentScanBasePackage(content, packageName, servletContextPath);
        content = componentScanPatch.content();

        if (content.contains(interceptorClass)) {
            if (componentScanPatch.changed()) {
                try {
                    Files.writeString(servletContextPath, content);
                } catch (IOException e) {
                    return "  실패: " + servletContextPath + " 쓰기 오류 — " + e.getMessage() + "\n";
                }
            }
            return componentScanPatch.message()
                 + "  보존: " + servletContextPath + " (EgovGnbMenuInterceptor 이미 등록됨)\n";
        }

        int beansCloseCount = countOccurrences(content, "</beans>");
        if (beansCloseCount != 1) {
            return "  실패: " + servletContextPath + " — </beans> 태그가 " + beansCloseCount
                 + "개 발견되어 안전하게 삽입할 수 없습니다. <mvc:interceptors> 블록을 수동으로 추가하세요.\n";
        }

        String interceptorBlock = """

                <mvc:interceptors>
                    <mvc:interceptor>
                        <mvc:mapping path="/**"/>
                        <bean class="%s" autowire="constructor"/>
                    </mvc:interceptor>
                </mvc:interceptors>

                """.formatted(interceptorClass);

        String patched = content.replace("</beans>", interceptorBlock + "</beans>");
        try {
            Files.writeString(servletContextPath, patched);
        } catch (IOException e) {
            return "  실패: " + servletContextPath + " 쓰기 오류 — " + e.getMessage() + "\n";
        }
        return componentScanPatch.message()
             + "  등록: " + servletContextPath + " 에 EgovGnbMenuInterceptor patch 완료 "
             + "(bean class=" + interceptorClass + ", autowire=constructor)\n";
    }

    private ComponentScanPatch patchComponentScanBasePackage(
            String content,
            String packageName,
            Path servletContextPath) {
        Matcher matcher = COMPONENT_SCAN_BASE_PACKAGE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return new ComponentScanPatch(
                    content,
                    "  확인 필요: " + servletContextPath
                            + " 에 <context:component-scan base-package=\"...\">가 없어 "
                            + "GNB 컴포넌트 스캔 범위를 자동 확인하지 못했습니다.\n",
                    false);
        }

        String basePackages = matcher.group(2).trim();
        String requiredBasePackage = requiredServletComponentScanBasePackage();
        if (basePackages.equals(requiredBasePackage)) {
            return new ComponentScanPatch(
                    content,
                    "  보존: " + servletContextPath + " component-scan base-package="
                            + basePackages + " (GNB servlet 스캔 범위 일치)\n",
                    false);
        }

        String patchedContent = content.substring(0, matcher.start(2))
                + requiredBasePackage
                + content.substring(matcher.end(2));
        return new ComponentScanPatch(
                patchedContent,
                "  변경: " + servletContextPath + " component-scan base-package="
                        + requiredBasePackage + " (GNB servlet 스캔 범위 단일화)\n",
                true);
    }

    private String requiredServletComponentScanBasePackage() {
        return "egovframework.let.com.cmm.service";
    }

    private boolean componentScanCovers(String basePackages, String targetPackage) {
        for (String basePackage : splitBasePackages(basePackages)) {
            if (targetPackage.equals(basePackage) || targetPackage.startsWith(basePackage + ".")) {
                return true;
            }
        }
        return false;
    }

    private String requiredComponentScanBasePackage(String packageName) {
        if (packageName.equals("egovframework.let.com") || packageName.startsWith("egovframework.let.com.")) {
            return "egovframework.let.com";
        }
        if (packageName.equals("egovframework.let") || packageName.startsWith("egovframework.let.")) {
            return "egovframework.let";
        }
        int lastDot = packageName.lastIndexOf('.');
        return lastDot > 0 ? packageName.substring(0, lastDot) : packageName;
    }

    private String appendBasePackage(String basePackages, String requiredBasePackage) {
        String normalized = basePackages == null ? "" : basePackages.trim();
        if (normalized.isBlank()) {
            return requiredBasePackage;
        }
        for (String basePackage : splitBasePackages(normalized)) {
            if (basePackage.equals(requiredBasePackage)) {
                return normalized;
            }
        }
        return normalized + ", " + requiredBasePackage;
    }

    private List<String> splitBasePackages(String basePackages) {
        if (basePackages == null || basePackages.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : basePackages.split("[,;\\s]+")) {
            if (!token.isBlank()) {
                result.add(token.trim());
            }
        }
        return result;
    }

    private record ComponentScanPatch(String content, String message, boolean changed) {
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
