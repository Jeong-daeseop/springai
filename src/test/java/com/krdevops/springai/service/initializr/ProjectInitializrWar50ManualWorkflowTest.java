package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.ProjectSpec;
import com.krdevops.springai.service.initializr.template.BuildFileRenderer;
import com.krdevops.springai.service.initializr.template.ClassPathTemplateLoader;
import com.krdevops.springai.service.initializr.template.DefaultStaticTemplateRenderer;
import com.krdevops.springai.service.initializr.template.DispatcherServletBuilder;
import com.krdevops.springai.service.initializr.template.StaticTemplateRenderer;
import com.krdevops.springai.service.initializr.template.WarBuildGradleBuilder;
import com.krdevops.springai.service.initializr.template.WarPomBuilder;
import com.krdevops.springai.service.initializr.template.WebXmlBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectInitializrWar50ManualWorkflowTest {

    private final VersionCapabilityResolver resolver = new VersionCapabilityResolver();

    @Test
    void war50FilePlan_usesManualWorkflowSpringContextPaths() {
        ProjectSpec spec = war50Spec("maven");
        FilePlanFactory factory = new FilePlanFactory(staticRenderer(), buildRenderer());

        assertThat(factory.directoryPlans(spec)).contains(
                "src/main/java/egovframework/let/sample/web",
                "src/main/java/egovframework/let/sample/service",
                "src/main/java/egovframework/let/sample/service/impl",
                "src/main/webapp/WEB-INF/spring/appServlet"
        );

        List<String> paths = factory.plan(spec).stream()
                .map(FilePlan::relativePath)
                .toList();

        assertThat(paths).contains(
                "src/main/webapp/WEB-INF/spring/root-context.xml",
                "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml",
                "src/main/webapp/WEB-INF/web.xml",
                "src/main/webapp/resources/css/krds.min.css",
                "src/main/webapp/resources/js/krds.min.js"
        );
        assertThat(paths).doesNotContain(
                "src/main/webapp/WEB-INF/config/egovframework/springmvc/dispatcher-servlet.xml"
        );
    }

    @Test
    void war50WebXml_referencesRootContextAndServletContext() {
        String webXml = new WebXmlBuilder().build(war50Spec("maven"));

        assertThat(webXml).contains("<param-value>/WEB-INF/spring/root-context.xml</param-value>");
        assertThat(webXml).contains("<param-value>/WEB-INF/spring/appServlet/servlet-context.xml</param-value>");
        assertThat(webXml).contains("https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd");
    }

    @Test
    void war50ServletContext_scansParentPackageForGeneratedCrudControllers() {
        String servletContext = new DispatcherServletBuilder().build(war50Spec("maven"));

        assertThat(servletContext).contains(
                "<context:component-scan base-package=\"egovframework.let\" use-default-filters=\"false\">"
        );
        assertThat(servletContext).doesNotContain("base-package=\"egovframework.let.sample\"");
    }

    @Test
    void war50ContextCommon_scansParentPackageForGeneratedCrudServicesAndMappers() {
        DefaultStaticTemplateRenderer renderer =
                new DefaultStaticTemplateRenderer(new ClassPathTemplateLoader());
        String contextCommon = renderer.contextCommon(war50Spec("maven"));

        assertThat(contextCommon).contains(
                "<context:component-scan base-package=\"egovframework.let\">",
                "<property name=\"basePackage\" value=\"egovframework.let\"/>"
        );
        assertThat(contextCommon).doesNotContain("egovframework.let.sample");
    }

    @Test
    void war50Pom_containsManualWorkflowEgovRuntimeDependencies() {
        String pom = new WarPomBuilder().build(war50Spec("maven"));

        assertThat(pom).contains(
                "<artifactId>egovframe-rte-fdl-property</artifactId>",
                "<artifactId>egovframe-rte-fdl-idgnr</artifactId>",
                "<artifactId>egovframe-rte-fdl-logging</artifactId>",
                "<artifactId>log4j-slf4j-impl</artifactId>",
                "<artifactId>log4j-slf4j2-impl</artifactId>",
                "<version>2.25.2</version>"
        );
    }

    @Test
    void war50Pom_declaresDependencyVersionsAndLombokProcessor() {
        String pom = new WarPomBuilder().build(war50Spec("maven"));

        assertThat(pom).contains(
                "<mybatis.version>3.5.16</mybatis.version>",
                "<junit.jupiter.version>5.12.1</junit.jupiter.version>",
                "<lombok.version>1.18.46</lombok.version>",
                "<artifactId>mybatis</artifactId>",
                "<version>${mybatis.version}</version>",
                "<artifactId>mybatis-spring</artifactId>",
                "<version>3.0.5</version>",
                "<artifactId>junit-jupiter</artifactId>",
                "<version>${junit.jupiter.version}</version>",
                "<annotationProcessorPaths>",
                "<artifactId>lombok</artifactId>\n                            <version>${lombok.version}</version>"
        );
    }

    @Test
    void war50Gradle_containsManualWorkflowEgovRuntimeDependencies() {
        String gradle = new WarBuildGradleBuilder().build(war50Spec("gradle"));

        assertThat(gradle).contains(
                "egovframe-rte-fdl-property:${egovVersion}",
                "egovframe-rte-fdl-idgnr:${egovVersion}",
                "egovframe-rte-fdl-logging:${egovVersion}"
        );
    }

    private ProjectSpec war50Spec(String buildTool) {
        return ProjectSpec.of(
                "sample-war",
                "egovframework.let",
                "sample-war",
                "egovframework.let.sample",
                buildTool,
                "war",
                "/tmp",
                resolver.resolve("5.0")
        );
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
            @Override public String krdsCss() { return "krds-css"; }
            @Override public String krdsJs() { return "krds-js"; }
            @Override public String bootMain(ProjectSpec s) { return "boot-main"; }
            @Override public String bootTest(ProjectSpec s) { return "boot-test"; }
        };
    }

    private BuildFileRenderer buildRenderer() {
        DispatcherServletBuilder dispatcher = new DispatcherServletBuilder();
        WebXmlBuilder webXml = new WebXmlBuilder();
        return new BuildFileRenderer() {
            @Override public String warPom(ProjectSpec s) { return "pom"; }
            @Override public String bootPom(ProjectSpec s) { return "boot-pom"; }
            @Override public String warBuildGradle(ProjectSpec s) { return "gradle"; }
            @Override public String bootBuildGradle(ProjectSpec s) { return "boot-gradle"; }
            @Override public String dispatcherServlet(ProjectSpec s) { return dispatcher.build(s); }
            @Override public String webXml(ProjectSpec s) { return webXml.build(s); }
        };
    }
}
