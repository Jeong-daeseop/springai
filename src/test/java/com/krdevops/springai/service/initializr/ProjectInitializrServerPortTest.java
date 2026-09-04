package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.ProjectSpec;
import com.krdevops.springai.service.initializr.template.ClassPathTemplateLoader;
import com.krdevops.springai.service.initializr.template.DefaultStaticTemplateRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * initializeProject 의 serverPort 파라미터 — Boot application.yml 의 server.port 기본값 제어.
 */
class ProjectInitializrServerPortTest {

    private final VersionCapabilityResolver resolver = new VersionCapabilityResolver();
    private final DefaultStaticTemplateRenderer renderer =
            new DefaultStaticTemplateRenderer(new ClassPathTemplateLoader());

    @Test
    void applicationYml_defaultPortIs8080_whenServerPortOmitted() {
        String yml = renderer.applicationYml(bootSpec(null));
        assertThat(yml).contains("port: ${SERVER_PORT:8080}");
    }

    @Test
    void applicationYml_usesServerPort_whenProvided() {
        String yml = renderer.applicationYml(bootSpec("9090"));
        assertThat(yml)
                .contains("port: ${SERVER_PORT:9090}")
                .doesNotContain("port: ${SERVER_PORT:8080}");
    }

    @Test
    void serverPort_rejectsNonNumeric() {
        assertThatThrownBy(() -> bootSpec("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("숫자");
    }

    @Test
    void serverPort_rejectsOutOfRange() {
        assertThatThrownBy(() -> bootSpec("70000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~65535");
    }

    @Test
    void serverPort_trimsWhitespace() {
        assertThat(bootSpec("  9090  ").serverPort()).isEqualTo("9090");
    }

    private ProjectSpec bootSpec(String serverPort) {
        return ProjectSpec.of(
                "egov-emp", "kr.go.myorg", "emp", "egovframework.let.emp",
                "gradle", "boot", "/tmp", resolver.resolve("5.0"), "jsp", serverPort);
    }
}
