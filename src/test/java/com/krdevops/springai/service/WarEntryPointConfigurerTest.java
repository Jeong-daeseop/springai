package com.krdevops.springai.service;

import com.krdevops.springai.config.EgovProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WarEntryPointConfigurerTest {

    @TempDir
    Path tempDir;

    @Test
    void configure_usesIndexJspForThymeleafTargetAndRemovesLegacyIndexHtml() throws Exception {
        Path project = tempDir.resolve("workspace/egov-web");
        Path webXml = project.resolve("src/main/webapp/WEB-INF/web.xml");
        Path indexHtml = project.resolve("src/main/webapp/index.html");
        Files.createDirectories(webXml.getParent());
        Files.writeString(webXml, """
                <web-app>
                    <welcome-file-list>
                        <welcome-file>index.html</welcome-file>
                    </welcome-file-list>
                </web-app>
                """);
        Files.writeString(indexHtml, "legacy");

        WarEntryPointConfigurer configurer = new WarEntryPointConfigurer(codeService(project));
        WarEntryPointConfigurer.ConfigurationResult result = configurer.configure(
                project.toString(), "/cop/bbs/infoNoticeList.do?bbsId=BBS_NOTICE");

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(project.resolve("src/main/webapp/index.jsp")))
                .contains("<jsp:forward page=\"/cop/bbs/infoNoticeList.do?bbsId=BBS_NOTICE\"/>");
        assertThat(Files.readString(webXml))
                .contains("<welcome-file>index.jsp</welcome-file>")
                .doesNotContain("<welcome-file>index.html</welcome-file>");
        assertThat(indexHtml).doesNotExist();
    }

    @Test
    void configure_whenAlreadyIndexJsp_isIdempotent() throws Exception {
        Path project = tempDir.resolve("workspace/egov-web");
        Path webXml = project.resolve("src/main/webapp/WEB-INF/web.xml");
        Files.createDirectories(webXml.getParent());
        Files.writeString(webXml, "<welcome-file>index.jsp</welcome-file>");

        WarEntryPointConfigurer configurer = new WarEntryPointConfigurer(codeService(project));

        assertThat(configurer.configure(project.toString(), "/emp/employerList.do").success()).isTrue();
        assertThat(configurer.configure(project.toString(), "/emp/employerList.do").success()).isTrue();
        assertThat(Files.readString(webXml)).isEqualTo("<welcome-file>index.jsp</welcome-file>");
    }

    @Test
    void configure_rejectsUnsafeForwardUrl() {
        WarEntryPointConfigurer configurer = new WarEntryPointConfigurer(codeService(tempDir));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> configurer.configure(tempDir.toString(), "relative.do\n<% bad %>"));
    }

    @Test
    void configure_withoutWebXml_doesNotLeavePartialIndexJsp() {
        Path project = tempDir.resolve("workspace/egov-web");
        WarEntryPointConfigurer configurer = new WarEntryPointConfigurer(codeService(project));

        WarEntryPointConfigurer.ConfigurationResult result =
                configurer.configure(project.toString(), "/emp/employerList.do");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("web.xml 파일이 없어");
        assertThat(project.resolve("src/main/webapp/index.jsp")).doesNotExist();
    }

    private CodeService codeService(Path project) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(project.resolve("generated").toString());
        output.setAllowedPaths(List.of(project.toString()));
        properties.setOutput(output);
        return new CodeService(properties);
    }
}
