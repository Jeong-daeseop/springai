package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.ProjectSpec;
import com.krdevops.springai.service.initializr.template.BuildFileRenderer;
import com.krdevops.springai.service.initializr.template.StaticTemplateRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectInitializrBoot50StaticResourceWorkflowTest {

    private final VersionCapabilityResolver resolver = new VersionCapabilityResolver();

    @Test
    void boot50FilePlan_includesStaticResourcesUnderResourcesPath() {
        ProjectSpec spec = boot50Spec("maven");
        FilePlanFactory factory = new FilePlanFactory(staticRenderer(), buildRenderer());

        assertThat(factory.directoryPlans(spec)).contains(
                "src/main/resources/static/resources/css",
                "src/main/resources/static/resources/js",
                "src/main/resources/templates"
        );

        List<String> paths = factory.plan(spec).stream()
                .map(FilePlan::relativePath)
                .toList();

        assertThat(paths).contains(
                "src/main/resources/application.yml",
                "src/main/resources/logback-spring.xml",
                "src/main/resources/static/resources/css/styles.css",
                "src/main/resources/static/resources/css/_ds_bundle.css",
                "src/main/resources/static/resources/js/krds.min.js"
        );
    }

    @Test
    void boot50Validator_requiresStaticResourcesUnderResourcesPath() {
        ProjectSpec spec = boot50Spec("maven");
        ProjectValidator validator = new ProjectValidator();
        com.krdevops.springai.model.GenerationReport report =
                new com.krdevops.springai.model.GenerationReport(spec.root().toString());

        validator.validateResult(spec, report);

        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("src/main/resources/static/resources/css/styles.css"));
        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("src/main/resources/static/resources/css/_ds_bundle.css"));
        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("src/main/resources/static/resources/js/krds.min.js"));
    }

    private ProjectSpec boot50Spec(String buildTool) {
        return ProjectSpec.of(
                "sample-boot",
                "egovframework.let",
                "sample-boot",
                "egovframework.let.sample",
                buildTool,
                "boot",
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
