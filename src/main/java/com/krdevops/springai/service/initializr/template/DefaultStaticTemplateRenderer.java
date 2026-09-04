package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultStaticTemplateRenderer implements StaticTemplateRenderer {

    private final ClassPathTemplateLoader loader;

    @Override
    public String contextCommon(ProjectSpec s) {
        return loader.load("context-common.xml.tpl",
                Map.of("scanBasePackage", PackageScanBase.from(s.packageName())));
    }

    @Override
    public String contextDatasource() {
        return loader.load("context-datasource.xml.tpl");
    }

    @Override
    public String contextTransaction() {
        return loader.load("context-transaction.xml.tpl");
    }

    @Override
    public String rootContext() {
        return loader.load("root-context.xml.tpl");
    }

    @Override
    public String applicationYml(ProjectSpec s) {
        return loader.load("application.yml.tpl",
                Map.of("artifactId",     s.artifactId(),
                       "packageName",    s.packageName(),
                       // server.port 는 SERVER_PORT 환경변수 우선, 미지정 시 serverPort 기본값
                       "serverPortExpr", "${SERVER_PORT:" + s.serverPort() + "}"));
    }

    @Override
    public String logback(ProjectSpec s) {
        return loader.load("logback-spring.xml.tpl",
                Map.of("projectName", s.projectName()));
    }

    @Override
    public String log4j2(ProjectSpec s) {
        return loader.load("log4j2.xml.tpl",
                Map.of("projectName", s.projectName()));
    }

    @Override
    public String gitignore(ProjectSpec s) {
        String extra = s.gradle() ? ".gradle/\ngradle/wrapper/gradle-wrapper.jar\n" : "";
        return loader.load("gitignore.tpl",
                Map.of("gradleExtra", extra));
    }

    @Override
    public String indexJsp() {
        return loader.load("index.jsp.tpl");
    }

    @Override
    public String stylesCss() {
        return loader.load("styles.css.tpl");
    }

    @Override
    public String dsBundleCss() {
        return loader.load("_ds_bundle.css.tpl");
    }

    @Override
    public String krdsJs() {
        return loader.load("krds.min.js.tpl");
    }

    @Override
    public String bootMain(ProjectSpec s) {
        return loader.load("BootApplication.java.tpl",
                Map.of("packageName", s.packageName(),
                       "className",   s.className()));
    }

    @Override
    public String bootTest(ProjectSpec s) {
        return loader.load("BootApplicationTests.java.tpl",
                Map.of("packageName", s.packageName(),
                       "className",   s.className()));
    }
}
