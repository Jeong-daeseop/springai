package com.krdevops.springai.tools;

import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.generation.layout.ClasspathAssetCopier;
import com.krdevops.springai.service.generation.layout.ComponentScanConfigurer;
import com.krdevops.springai.service.generation.layout.MainPageRenderer;
import com.krdevops.springai.service.generation.layout.ServletContextConfigurer;
import com.krdevops.springai.service.generation.layout.ThymeleafLayoutGenerationPlanner;
import com.krdevops.springai.service.generation.layout.ThymeleafLayoutGenerationService;
import com.krdevops.springai.service.generation.mcp.ThymeleafLayoutMcpFacade;
import com.krdevops.springai.service.generation.mcp.ThymeleafLayoutResultFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tool → Facade → Use Case → Planner/Processor 경로 전체를 통해
 * 리팩터링 전 {@code ThymeleafLayoutTool}의 동작을 그대로 재현하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ThymeleafLayoutToolTest {

    @Mock CrudTemplateRenderer crudTemplateRenderer;
    @Mock CodeService codeService;
    @Mock ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;

    ThymeleafLayoutTool tool;

    @BeforeEach
    void setUp() {
        ThymeleafLayoutValidator thymeleafLayoutValidator = new ThymeleafLayoutValidator();
        MyBatisRuntimeConfigurer myBatisRuntimeConfigurer = new MyBatisRuntimeConfigurer();
        ThymeleafLayoutGenerationPlanner planner = new ThymeleafLayoutGenerationPlanner();
        MainPageRenderer mainPageRenderer = new MainPageRenderer();
        ClasspathAssetCopier classpathAssetCopier = new ClasspathAssetCopier(codeService);
        ComponentScanConfigurer componentScanConfigurer = new ComponentScanConfigurer(myBatisRuntimeConfigurer);
        ServletContextConfigurer servletContextConfigurer = new ServletContextConfigurer(componentScanConfigurer);

        ThymeleafLayoutGenerationService generationService = new ThymeleafLayoutGenerationService(
                crudTemplateRenderer,
                codeService,
                thymeleafLayoutValidator,
                thymeleafRuntimeConfigurer,
                myBatisRuntimeConfigurer,
                planner,
                mainPageRenderer,
                classpathAssetCopier,
                servletContextConfigurer);

        ThymeleafLayoutMcpFacade facade = new ThymeleafLayoutMcpFacade(
                thymeleafLayoutValidator,
                generationService,
                new ThymeleafLayoutResultFormatter());

        tool = new ThymeleafLayoutTool(facade);
    }

    @Test
    void generateThymeleafLayout_customLayoutBasePath_writesFiveFilesWithScopedPartials(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout/admin", false, "egovframework.let.emp");

        Path base = tempDir.resolve("src/main/resources/templates/layout/admin");
        assertThat(base.resolve("default.html")).isRegularFile();
        assertThat(base.resolve("gnb.html")).isRegularFile();
        assertThat(base.resolve("lnb.html")).isRegularFile();
        assertThat(base.resolve("breadcrumb.html")).isRegularFile();
        assertThat(base.resolve("footer.html")).isRegularFile();
        assertThat(Files.readString(base.resolve("default.html")))
                .contains("layout/admin/gnb")
                .contains("layout/admin/lnb")
                .contains("layout/admin/footer");
        assertThat(result).contains("[검증 완료]");
    }

    @Test
    void generateThymeleafLayout_overwriteFalse_preservesExistingLayoutFile(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path gnb = tempDir.resolve("src/main/resources/templates/layout/gnb.html");
        Files.createDirectories(gnb.getParent());
        Files.writeString(gnb, "custom gnb");

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        assertThat(Files.readString(gnb)).isEqualTo("custom gnb");
        assertThat(result).contains("보존:").contains("gnb.html").contains("[검증 완료]");
    }

    @Test
    void generateThymeleafLayout_overwriteUnspecified_defaultsToOverwritingExistingLayoutFile(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path gnb = tempDir.resolve("src/main/resources/templates/layout/gnb.html");
        Files.createDirectories(gnb.getParent());
        Files.writeString(gnb, "custom gnb");

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", null, "egovframework.let.emp");

        assertThat(Files.readString(gnb)).isNotEqualTo("custom gnb");
        assertThat(result).contains("생성:").contains("gnb.html").contains("[검증 완료]");
    }

    @Test
    void generateThymeleafLayout_writesGnbMenuComponentFourFilesUnderPackagePath(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        Path cmm = tempDir.resolve("src/main/java/egovframework/let/emp/cmm");
        assertThat(cmm.resolve("vo/GnbMenuVO.java")).isRegularFile();
        assertThat(cmm.resolve("service/GnbMenuMapper.java")).isRegularFile();
        assertThat(cmm.resolve("web/EgovGnbMenuInterceptor.java")).isRegularFile();
        assertThat(tempDir.resolve("src/main/resources/egovframework/mapper/cmm/GnbMenuMapper.xml"))
                .isRegularFile();
    }

    @Test
    void generateThymeleafLayout_writesThymeleafMainHtmlForMainControllerView(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        Path mainHtml = tempDir.resolve("src/main/resources/templates/egovframework/main/main.html");
        assertThat(mainHtml).isRegularFile();
        assertThat(Files.readString(mainHtml))
                .contains("layout:decorate=\"~{layout/default}\"")
                .contains("class=\"egov-main-dashboard\"")
                .contains("class=\"egov-main-metrics\"")
                .contains("class=\"egov-main-grid\"")
                .doesNotContain("lnbMenus")
                .doesNotContain("#lists.emptyList()")
                .doesNotContain("~{layout/breadcrumb :: breadcrumb}")
                .contains("전자정부 표준프레임워크 메인 화면입니다.");
        assertThat(Files.readString(tempDir.resolve("src/main/resources/templates/layout/default.html")))
                .contains("~{layout/breadcrumb :: breadcrumb}");
        assertThat(result).contains("egovframework/main/main.html");
    }

    @Test
    void generateThymeleafLayout_preservesWarEntryPointFiles(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path indexJsp = tempDir.resolve("src/main/webapp/index.jsp");
        Path indexHtml = tempDir.resolve("src/main/webapp/index.html");
        Path webXml = tempDir.resolve("src/main/webapp/WEB-INF/web.xml");
        Files.createDirectories(indexJsp.getParent());
        Files.createDirectories(webXml.getParent());
        String originalIndexJsp = "<jsp:forward page=\"/cop/bbs/noticeList.do\"/>";
        String originalIndexHtml = "<html><body>legacy</body></html>";
        Files.writeString(indexJsp, originalIndexJsp);
        Files.writeString(indexHtml, originalIndexHtml);
        Files.writeString(webXml, """
                <web-app>
                    <welcome-file-list>
                        <welcome-file>index.jsp</welcome-file>
                    </welcome-file-list>
                </web-app>
                """);

        tool.generateThymeleafLayout(tempDir.toString(), "layout", true, "egovframework.let.emp");

        assertThat(Files.readString(indexJsp)).isEqualTo(originalIndexJsp);
        assertThat(Files.readString(indexHtml)).isEqualTo(originalIndexHtml);
        assertThat(Files.readString(webXml))
                .contains("<welcome-file>index.jsp</welcome-file>")
                .doesNotContain("<welcome-file>index.html</welcome-file>");
    }

    @Test
    void generateThymeleafLayout_overwriteFalse_preservesExistingGnbMenuComponentFile(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path interceptor = tempDir.resolve("src/main/java/egovframework/let/emp/cmm/web/EgovGnbMenuInterceptor.java");
        Files.createDirectories(interceptor.getParent());
        Files.writeString(interceptor, "custom interceptor");

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        assertThat(Files.readString(interceptor)).isEqualTo("custom interceptor");
        assertThat(result).contains("보존:").contains("EgovGnbMenuInterceptor.java");
    }

    @Test
    void generateThymeleafLayout_packageNameMissing_usesDefaultAndWarns(@TempDir Path tempDir) throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, null);

        assertThat(result).contains("packageName 미지정").contains("egovframework.let.sample");
        assertThat(tempDir.resolve("src/main/java/egovframework/let/sample/cmm/web/EgovGnbMenuInterceptor.java"))
                .isRegularFile();
    }

    @Test
    void generateThymeleafLayout_invokesThymeleafRuntimeConfigurerWithEgov50(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        verify(thymeleafRuntimeConfigurer).ensureThymeleafRuntime(eq(tempDir.toString()), eq("5.0"), anyList());
        assertThat(result)
                .contains("[Thymeleaf 런타임 설정]")
                .contains("classpath:/templates");
    }

    @Test
    void generateThymeleafLayout_passesMenuTableNamesToGnbRenderer(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        String result = tool.generateThymeleafLayout(
                tempDir.toString(),
                "layout",
                false,
                "egovframework.let.emp",
                "custom.LETTNMENUINFO",
                "custom.LETTNPROGRMLIST");

        verify(crudTemplateRenderer).renderGnbMenuComponent(
                eq(CrudLayerDefinition.LAYOUT_GNB_MENU_MAPPER_XML),
                eq("egovframework.let.emp"),
                eq("LETTNMENUINFO"),
                eq("LETTNPROGRMLIST"));
        assertThat(result)
                .contains("menuTableName: LETTNMENUINFO")
                .contains("programTableName: LETTNPROGRMLIST");
    }

    // ─── Phase 4: servlet-context.xml patch ──────────────────────────────────

    private static final String SERVLET_CONTEXT_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns="http://www.springframework.org/schema/beans"
                   xmlns:mvc="http://www.springframework.org/schema/mvc">
                <mvc:resources mapping="/resources/**" location="/resources/"/>
            </beans>
            """;

    private static final String SERVLET_CONTEXT_XML_WITH_COMPONENT_SCAN =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns="http://www.springframework.org/schema/beans"
                   xmlns:context="http://www.springframework.org/schema/context"
                   xmlns:mvc="http://www.springframework.org/schema/mvc">
                <context:component-scan base-package="egovframework.let.sample" use-default-filters="false">
                    <context:include-filter type="annotation" expression="org.springframework.stereotype.Controller"/>
                </context:component-scan>
                <mvc:resources mapping="/resources/**" location="/resources/"/>
            </beans>
            """;

    private static final String CONTEXT_COMMON_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns="http://www.springframework.org/schema/beans">
                <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
                    <property name="dataSource" ref="dataSource"/>
                </bean>
            </beans>
            """;

    @Test
    void generateThymeleafLayout_contextCommonXmlPatchesMybatisMapperSettings(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path contextCommon = tempDir.resolve("src/main/resources/egovframework/spring/context-common.xml");
        Files.createDirectories(contextCommon.getParent());
        Files.writeString(contextCommon, CONTEXT_COMMON_XML);

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.com");

        String patched = Files.readString(contextCommon);
        assertThat(patched)
                .contains("<property name=\"mapperLocations\" value=\"classpath*:egovframework/mapper/**/*.xml\"/>")
                .contains("<bean class=\"org.mybatis.spring.mapper.MapperScannerConfigurer\">")
                .contains("<property name=\"basePackage\" value=\"egovframework.let\"/>")
                .contains("<property name=\"sqlSessionFactoryBeanName\" value=\"sqlSessionFactory\"/>")
                .contains("<property name=\"annotationClass\" value=\"org.apache.ibatis.annotations.Mapper\"/>");
        assertThat(result)
                .contains("context-common.xml")
                .contains("basePackage=egovframework.let");
    }

    @Test
    void generateThymeleafLayout_contextCommonXmlPatchesExistingMapperScannerBasePackage(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path contextCommon = tempDir.resolve("src/main/resources/egovframework/spring/context-common.xml");
        Files.createDirectories(contextCommon.getParent());
        Files.writeString(contextCommon, CONTEXT_COMMON_XML.replace("</beans>", """

                <bean class=\"org.mybatis.spring.mapper.MapperScannerConfigurer\">
                    <property name=\"basePackage\" value=\"egovframework.let.sample\"/>
                    <property name=\"sqlSessionFactoryBeanName\" value=\"sqlSessionFactory\"/>
                </bean>
            </beans>
            """));

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.com.bbs");

        String patched = Files.readString(contextCommon);
        assertThat(patched)
                .contains("<property name=\"basePackage\" value=\"egovframework.let\"/>")
                .doesNotContain("egovframework.let.sample, egovframework.let")
                .contains("mapperLocations");
        assertThat(result).contains("basePackage=egovframework.let");
    }

    @Test
    void generateThymeleafLayout_servletContextXmlMissing_skipsPatchWithGuidance(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        assertThat(result).contains("건너뜀:").contains("servlet-context.xml");
        assertThat(tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml")).doesNotExist();
    }

    @Test
    void generateThymeleafLayout_servletContextXmlExists_patchesInterceptorBeforeClosingBeansTag(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path servletContext = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        Files.writeString(servletContext, SERVLET_CONTEXT_XML);

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        String patched = Files.readString(servletContext);
        assertThat(patched)
                .contains("<mvc:interceptors>")
                .contains("<bean class=\"egovframework.let.emp.cmm.web.EgovGnbMenuInterceptor\" autowire=\"constructor\"/>")
                .contains("<mvc:mapping path=\"/**\"/>");
        assertThat(patched.indexOf("<mvc:interceptors>")).isLessThan(patched.indexOf("</beans>"));
        assertThat(result).contains("등록:").contains("servlet-context.xml");
    }

    @Test
    void generateThymeleafLayout_servletContextXmlBroadensComponentScanToCoverGnbPackage(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path servletContext = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        Files.writeString(servletContext, SERVLET_CONTEXT_XML_WITH_COMPONENT_SCAN);

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.bbs");

        String patched = Files.readString(servletContext);
        assertThat(patched)
                .contains("base-package=\"egovframework.let\"")
                .contains("<bean class=\"egovframework.let.bbs.cmm.web.EgovGnbMenuInterceptor\" autowire=\"constructor\"/>");
        assertThat(result).contains("component-scan base-package=egovframework.let");
    }

    @Test
    void generateThymeleafLayout_servletContextXmlKeepsExistingBroadComponentScan(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path servletContext = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        Files.writeString(servletContext, SERVLET_CONTEXT_XML_WITH_COMPONENT_SCAN
                .replace("egovframework.let.sample", "egovframework.let"));

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.bbs");

        String patched = Files.readString(servletContext);
        assertThat(patched)
                .contains("base-package=\"egovframework.let\"")
                .doesNotContain("egovframework.let.sample");
        assertThat(result).contains("component-scan base-package=egovframework.let (GNB servlet 스캔 범위 이미 포함)");
    }

    @Test
    void generateThymeleafLayout_servletContextXmlBroadensComponentScanForComSubPackage(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path servletContext = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        Files.writeString(servletContext, SERVLET_CONTEXT_XML_WITH_COMPONENT_SCAN);

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.com.bbs");

        String patched = Files.readString(servletContext);
        assertThat(patched)
                .contains("base-package=\"egovframework.let\"")
                .contains("<bean class=\"egovframework.let.com.bbs.cmm.web.EgovGnbMenuInterceptor\" autowire=\"constructor\"/>");
        assertThat(result).contains("component-scan base-package=egovframework.let");
    }

    @Test
    void generateThymeleafLayout_interceptorAlreadyRegistered_skipsDuplicatePatch(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path servletContext = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        Files.writeString(servletContext, SERVLET_CONTEXT_XML);

        tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");
        String afterFirstRun = Files.readString(servletContext);
        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");
        String afterSecondRun = Files.readString(servletContext);

        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
        assertThat(result).contains("보존:").contains("이미 등록됨");
    }

    @Test
    void generateThymeleafLayout_servletContextXmlHasNoClosingBeansTag_failsPatchSafely(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path servletContext = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        Files.writeString(servletContext, "<beans><mvc:resources mapping=\"/resources/**\"/>");

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        assertThat(result).contains("실패:").contains("</beans>").contains("0개");
        assertThat(result).contains("ViewResolver 보강을 생략");
        assertThat(Files.readString(servletContext)).isEqualTo("<beans><mvc:resources mapping=\"/resources/**\"/>");
        verify(thymeleafRuntimeConfigurer, never()).ensureThymeleafRuntime(any(), any(), anyList());
    }

    @Test
    void generateThymeleafLayout_servletContextXmlHasTwoClosingBeansTags_failsPatchSafely(@TempDir Path tempDir)
            throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path servletContext = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        String malformed = SERVLET_CONTEXT_XML + "</beans>";
        Files.writeString(servletContext, malformed);

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        assertThat(result).contains("실패:").contains("</beans>").contains("2개");
        assertThat(Files.readString(servletContext)).isEqualTo(malformed);
    }

    private void stubRenderer() {
        when(crudTemplateRenderer.renderLayoutByLayerKey(any(), any())).thenAnswer(invocation -> {
            String layerKey = invocation.getArgument(0);
            String layoutBasePath = invocation.getArgument(1);
            if (CrudLayerDefinition.LAYOUT_HTML.equals(layerKey)) {
                return """
                        <th:block th:replace="~{%s/gnb :: gnb}"></th:block>
                        <th:block th:replace="~{%s/breadcrumb :: breadcrumb}"></th:block>
                        <th:block th:replace="~{%s/lnb :: lnb}"></th:block>
                        <th:block th:replace="~{%s/footer :: footer}"></th:block>
                        """.formatted(layoutBasePath, layoutBasePath, layoutBasePath, layoutBasePath);
            }
            return layerKey;
        });
        when(crudTemplateRenderer.renderGnbMenuComponent(any(), any(), any(), any()))
                .thenAnswer(invocation -> "// " + invocation.getArgument(0, String.class));
    }

    private void stubCodeServiceWrite() {
        when(codeService.saveGeneratedCode(any(), any())).thenAnswer(invocation -> {
            Path path = Path.of(invocation.getArgument(0, String.class));
            String code = invocation.getArgument(1, String.class);
            Files.createDirectories(path.getParent());
            Files.writeString(path, code);
            return "파일 저장 완료: " + path;
        });
        lenient().when(codeService.saveGeneratedBinary(any(), any())).thenAnswer(invocation -> {
            Path path = Path.of(invocation.getArgument(0, String.class));
            byte[] content = invocation.getArgument(1, byte[].class);
            Files.createDirectories(path.getParent());
            Files.write(path, content);
            return "파일 저장 완료: " + path;
        });
    }

    @Test
    void generateThymeleafLayout_writesGnbLogoImageFromClasspathAsset(@TempDir Path tempDir) throws Exception {
        stubRenderer();
        stubCodeServiceWrite();

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        Path logo = tempDir.resolve("src/main/webapp/resources/images/egov-logo.png");
        assertThat(logo).isRegularFile();
        assertThat(Files.size(logo)).isGreaterThan(0);
        assertThat(result).contains("생성:").contains("egov-logo.png");
    }

    @Test
    void generateThymeleafLayout_overwriteFalse_preservesExistingLogoImage(@TempDir Path tempDir) throws Exception {
        stubRenderer();
        stubCodeServiceWrite();
        Path logo = tempDir.resolve("src/main/webapp/resources/images/egov-logo.png");
        Files.createDirectories(logo.getParent());
        Files.write(logo, new byte[] {9, 9, 9});

        String result = tool.generateThymeleafLayout(tempDir.toString(), "layout", false, "egovframework.let.emp");

        assertThat(Files.readAllBytes(logo)).isEqualTo(new byte[] {9, 9, 9});
        assertThat(result).contains("보존:").contains("egov-logo.png");
    }
}
