package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.ProjectSpec;
import com.krdevops.springai.service.initializr.template.BootBuildGradleBuilder;
import com.krdevops.springai.service.initializr.template.BootPomBuilder;
import com.krdevops.springai.service.initializr.template.BuildFileRenderer;
import com.krdevops.springai.service.initializr.template.StaticTemplateRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot + Thymeleaf 조합의 initializeProject 산출물 검증 — Boot 지원 구현
 * (docs/crud/thymeleaf-layout-boot-support-plan.md Phase 1).
 *
 * <p>기존에는 {@code bootFiles()}에 thymeleaf 분기가 없어 {@code viewType="thymeleaf"}가
 * Boot에서 no-op였다. 이제 layout 5종/main.html/MainController와 build 파일 thymeleaf 의존성이
 * 함께 생성된다.
 */
class ProjectInitializrBoot50ThymeleafWorkflowTest {

    private final VersionCapabilityResolver resolver = new VersionCapabilityResolver();

    @Test
    void boot50ThymeleafFilePlan_includesLayoutMainHtmlAndMainController() {
        ProjectSpec spec = boot50ThymeleafSpec("gradle");
        FilePlanFactory factory = new FilePlanFactory(staticRenderer(), buildRenderer());

        assertThat(factory.directoryPlans(spec)).contains(
                "src/main/resources/templates/egovframework/main",
                "src/main/resources/templates/layout",
                "src/main/java/egovframework/let/sample/main/web"
        );

        List<FilePlan> plans = factory.plan(spec);
        List<String> paths = plans.stream().map(FilePlan::relativePath).toList();

        assertThat(paths).contains(
                "src/main/resources/templates/egovframework/main/main.html",
                "src/main/resources/templates/layout/default.html",
                "src/main/resources/templates/layout/gnb.html",
                "src/main/resources/templates/layout/lnb.html",
                "src/main/resources/templates/layout/breadcrumb.html",
                "src/main/resources/templates/layout/footer.html",
                "src/main/java/egovframework/let/sample/main/web/MainController.java"
        );
        // Boot 는 JSP main / servlet-context / web.xml 을 만들지 않는다.
        assertThat(paths).doesNotContain(
                "src/main/webapp/WEB-INF/jsp/egovframework/main/main.jsp",
                "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml",
                "src/main/webapp/index.jsp"
        );

        String mainController = plans.stream()
                .filter(p -> p.relativePath().endsWith("main/web/MainController.java"))
                .findFirst().orElseThrow().content().get();
        assertThat(mainController).contains(
                "package egovframework.let.sample.main.web;",
                "@GetMapping({\"/\", \"/egovframework/com/main.do\"})",
                "return \"egovframework/main/main\";"
        );
    }

    @Test
    void boot50ThymeleafBuildFiles_addThymeleafDependencies() {
        ProjectSpec gradleSpec = boot50ThymeleafSpec("gradle");
        ProjectSpec mavenSpec = boot50ThymeleafSpec("maven");

        String gradle = new BootBuildGradleBuilder().build(gradleSpec);
        assertThat(gradle).contains(
                "org.springframework.boot:spring-boot-starter-thymeleaf",
                "nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect"
        );

        String pom = new BootPomBuilder().build(mavenSpec);
        assertThat(pom).contains(
                "<artifactId>spring-boot-starter-thymeleaf</artifactId>",
                "<artifactId>thymeleaf-layout-dialect</artifactId>"
        );
    }

    @Test
    void boot50JspBuildFiles_omitThymeleafDependencies() {
        ProjectSpec spec = ProjectSpec.of(
                "sample-boot", "egovframework.let", "sample-boot", "egovframework.let.sample",
                "gradle", "boot", "/tmp", resolver.resolve("5.0"), "jsp");

        assertThat(new BootBuildGradleBuilder().build(spec))
                .doesNotContain("spring-boot-starter-thymeleaf");
    }

    private ProjectSpec boot50ThymeleafSpec(String buildTool) {
        return ProjectSpec.of(
                "sample-boot", "egovframework.let", "sample-boot", "egovframework.let.sample",
                buildTool, "boot", "/tmp", resolver.resolve("5.0"), "thymeleaf");
    }

    private StaticTemplateRenderer staticRenderer() {
        return new StaticTemplateRenderer() {
            @Override public String contextCommon(ProjectSpec s) { return "context-common"; }
            @Override public String contextDatasource() { return "context-datasource"; }
            @Override public String contextTransaction() { return "context-transaction"; }
            @Override public String rootContext() { return "root-context"; }
            @Override public String applicationYml(ProjectSpec s) { return "application"; }
            @Override public String logback(ProjectSpec s) { return "logback"; }
            @Override public String log4j2(ProjectSpec s) { return "log4j2"; }
            @Override public String gitignore(ProjectSpec s) { return "gitignore"; }
            @Override public String indexJsp() { return "index"; }
            @Override public String stylesCss() { return "styles-css"; }
            @Override public String dsBundleCss() { return "ds-bundle-css"; }
            @Override public String krdsJs() { return "krds-js"; }
            @Override public String bootMain(ProjectSpec s) { return "boot-main"; }
            @Override public String bootTest(ProjectSpec s) { return "boot-test"; }
        };
    }

    private BuildFileRenderer buildRenderer() {
        return new BuildFileRenderer() {
            @Override public String warPom(ProjectSpec s) { return "pom"; }
            @Override public String bootPom(ProjectSpec s) { return "boot-pom"; }
            @Override public String warBuildGradle(ProjectSpec s) { return "gradle"; }
            @Override public String bootBuildGradle(ProjectSpec s) { return "boot-gradle"; }
            @Override public String dispatcherServlet(ProjectSpec s) { return "dispatcher"; }
            @Override public String webXml(ProjectSpec s) { return "webxml"; }
        };
    }
}
