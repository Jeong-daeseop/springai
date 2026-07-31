package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ServletContextConfigurerTest {

    private static final String SERVLET_CONTEXT_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns="http://www.springframework.org/schema/beans">
                <mvc:resources mapping="/resources/**" location="/resources/"/>
            </beans>
            """;

    private final ServletContextConfigurer configurer =
            new ServletContextConfigurer(new ComponentScanConfigurer(new MyBatisRuntimeConfigurer()));

    @Test
    void patch_fileMissing_returnsSkipGuidanceWithoutFailure(@TempDir Path tempDir) {
        ServletContextConfigurer.ServletContextPatchResult result =
                configurer.patch(tempDir, "egovframework.let.emp");

        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("건너뜀:").contains("servlet-context.xml");
    }

    @Test
    void patch_validXml_insertsInterceptorBeforeClosingBeansTag(@TempDir Path tempDir) throws Exception {
        Path servletContext = writeServletContext(tempDir, SERVLET_CONTEXT_XML);

        ServletContextConfigurer.ServletContextPatchResult result = configurer.patch(tempDir, "egovframework.let.emp");

        String patched = Files.readString(servletContext);
        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("등록:");
        assertThat(patched)
                .contains("<bean class=\"egovframework.let.emp.cmm.web.EgovGnbMenuInterceptor\" autowire=\"constructor\"/>");
        assertThat(patched.indexOf("<mvc:interceptors>")).isLessThan(patched.indexOf("</beans>"));
    }

    @Test
    void patch_interceptorAlreadyPresent_skipsWithoutRewriting(@TempDir Path tempDir) throws Exception {
        Path servletContext = writeServletContext(tempDir, SERVLET_CONTEXT_XML);
        configurer.patch(tempDir, "egovframework.let.emp");
        String afterFirst = Files.readString(servletContext);

        ServletContextConfigurer.ServletContextPatchResult result = configurer.patch(tempDir, "egovframework.let.emp");

        assertThat(Files.readString(servletContext)).isEqualTo(afterFirst);
        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("보존:").contains("이미 등록됨");
    }

    @Test
    void patch_zeroClosingBeansTags_failsWithoutWriting(@TempDir Path tempDir) throws Exception {
        String malformed = "<beans><mvc:resources mapping=\"/resources/**\"/>";
        Path servletContext = writeServletContext(tempDir, malformed);

        ServletContextConfigurer.ServletContextPatchResult result = configurer.patch(tempDir, "egovframework.let.emp");

        assertThat(result.failed()).isTrue();
        assertThat(result.message()).contains("실패:").contains("0개");
        assertThat(Files.readString(servletContext)).isEqualTo(malformed);
    }

    @Test
    void patch_twoClosingBeansTags_failsWithoutWriting(@TempDir Path tempDir) throws Exception {
        String malformed = SERVLET_CONTEXT_XML + "</beans>";
        Path servletContext = writeServletContext(tempDir, malformed);

        ServletContextConfigurer.ServletContextPatchResult result = configurer.patch(tempDir, "egovframework.let.emp");

        assertThat(result.failed()).isTrue();
        assertThat(result.message()).contains("실패:").contains("2개");
        assertThat(Files.readString(servletContext)).isEqualTo(malformed);
    }

    private Path writeServletContext(Path tempDir, String content) throws Exception {
        Path path = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }
}
