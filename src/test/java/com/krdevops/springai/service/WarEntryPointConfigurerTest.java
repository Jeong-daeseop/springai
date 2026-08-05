package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WP7 2차 pass 잔여 항목/ARCH-0717: index.jsp 생성 + web.xml 패치 + index.html 삭제 3단계가 공용
 * {@code ApprovedProjectWritePort}(ATOMIC_APPROVED)의 단일 {@code ProjectChangeSet}으로 묶여
 * 부분 적용(예: index.jsp만 쓰이고 web.xml 패치는 실패) 없이 처리되는지 검증한다.
 */
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

        WarEntryPointConfigurer configurer = configurer(project);
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

        WarEntryPointConfigurer configurer = configurer(project);

        assertThat(configurer.configure(project.toString(), "/emp/employerList.do").success()).isTrue();
        assertThat(configurer.configure(project.toString(), "/emp/employerList.do").success()).isTrue();
        assertThat(Files.readString(webXml)).isEqualTo("<welcome-file>index.jsp</welcome-file>");
    }

    @Test
    void configure_rejectsUnsafeForwardUrl() {
        WarEntryPointConfigurer configurer = configurer(tempDir);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> configurer.configure(tempDir.toString(), "relative.do\n<% bad %>"));
    }

    @Test
    void configure_withoutWebXml_doesNotLeavePartialIndexJsp() {
        Path project = tempDir.resolve("workspace/egov-web");
        WarEntryPointConfigurer configurer = configurer(project);

        WarEntryPointConfigurer.ConfigurationResult result =
                configurer.configure(project.toString(), "/emp/employerList.do");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("web.xml 파일이 없어");
        assertThat(project.resolve("src/main/webapp/index.jsp")).doesNotExist();
    }

    /**
     * ARCH-0717 전환 전에는 index.jsp 생성(codeService.saveGeneratedCode)과 index.html 삭제
     * (codeService.deleteGeneratedFile)가 완전히 독립적인 호출이라, symlink escape 같은 방어가
     * 파일별로만 걸리고 배치 전체를 막지 못했다. ATOMIC_APPROVED 전환 후에는
     * {@code ApprovedProjectWritePort.apply}가 실제로 아무 것도 쓰기 전에 배치의 모든 대상 경로를
     * 먼저 검증해, index.html이 symlink escape 시도면 index.jsp도 전혀 쓰이지 않아야 한다.
     */
    @Test
    void configure_rejectsSymlinkEscapeAndWritesNothingAtAll(@TempDir Path outside) throws Exception {
        Path project = tempDir.resolve("workspace/egov-web");
        Path webXml = project.resolve("src/main/webapp/WEB-INF/web.xml");
        Path indexHtml = project.resolve("src/main/webapp/index.html");
        Files.createDirectories(webXml.getParent());
        Files.writeString(webXml, "<welcome-file>index.html</welcome-file>");
        Path escapeTarget = outside.resolve("secret.txt");
        Files.writeString(escapeTarget, "outside-project");
        Files.createSymbolicLink(indexHtml, escapeTarget);

        WarEntryPointConfigurer configurer = configurer(project);

        assertThatThrownBy(() -> configurer.configure(project.toString(), "/emp/employerList.do"))
                .isInstanceOf(SecurityException.class);
        assertThat(project.resolve("src/main/webapp/index.jsp")).doesNotExist();
        assertThat(Files.readString(webXml)).isEqualTo("<welcome-file>index.html</welcome-file>");
    }

    private WarEntryPointConfigurer configurer(Path project) {
        ObjectMapper objectMapper = new ObjectMapper();
        OperationHashFactory hashFactory = new OperationHashFactory(objectMapper);
        FileSystemApprovedProjectWritePort writePort =
                new FileSystemApprovedProjectWritePort(new SafePathResolver(), hashFactory);
        return new WarEntryPointConfigurer(codeService(project), writePort, hashFactory);
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
