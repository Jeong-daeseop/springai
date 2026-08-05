package com.krdevops.springai.service.generation.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WP7 5차 pass: {@code Files.writeString} 원시 호출을 공용
 * {@code ApprovedProjectWritePort}(ATOMIC_APPROVED)로 전환한다.
 */
class ServletContextConfigurerTest {

    private static final String SERVLET_CONTEXT_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns="http://www.springframework.org/schema/beans">
                <mvc:resources mapping="/resources/**" location="/resources/"/>
            </beans>
            """;

    private ServletContextConfigurer configurer(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        MyBatisRuntimeConfigurer myBatisRuntimeConfigurer = new MyBatisRuntimeConfigurer(
                codeService, writePort, new OperationHashFactory(new ObjectMapper()));
        return new ServletContextConfigurer(
                new ComponentScanConfigurer(myBatisRuntimeConfigurer), codeService, writePort,
                new OperationHashFactory(new ObjectMapper()));
    }

    @Test
    void patch_fileMissing_returnsSkipGuidanceWithoutFailure(@TempDir Path tempDir) {
        ServletContextConfigurer.ServletContextPatchResult result =
                configurer(tempDir).patch(tempDir, "egovframework.let.emp");

        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("건너뜀:").contains("servlet-context.xml");
    }

    @Test
    void patch_validXml_insertsInterceptorBeforeClosingBeansTag(@TempDir Path tempDir) throws Exception {
        Path servletContext = writeServletContext(tempDir, SERVLET_CONTEXT_XML);

        ServletContextConfigurer.ServletContextPatchResult result =
                configurer(tempDir).patch(tempDir, "egovframework.let.emp");

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
        ServletContextConfigurer configurer = configurer(tempDir);
        configurer.patch(tempDir, "egovframework.let.emp");
        String afterFirst = Files.readString(servletContext);

        ServletContextConfigurer.ServletContextPatchResult result =
                configurer.patch(tempDir, "egovframework.let.emp");

        assertThat(Files.readString(servletContext)).isEqualTo(afterFirst);
        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("보존:").contains("이미 등록됨");
    }

    @Test
    void patch_zeroClosingBeansTags_failsWithoutWriting(@TempDir Path tempDir) throws Exception {
        String malformed = "<beans><mvc:resources mapping=\"/resources/**\"/>";
        Path servletContext = writeServletContext(tempDir, malformed);

        ServletContextConfigurer.ServletContextPatchResult result =
                configurer(tempDir).patch(tempDir, "egovframework.let.emp");

        assertThat(result.failed()).isTrue();
        assertThat(result.message()).contains("실패:").contains("0개");
        assertThat(Files.readString(servletContext)).isEqualTo(malformed);
    }

    @Test
    void patch_twoClosingBeansTags_failsWithoutWriting(@TempDir Path tempDir) throws Exception {
        String malformed = SERVLET_CONTEXT_XML + "</beans>";
        Path servletContext = writeServletContext(tempDir, malformed);

        ServletContextConfigurer.ServletContextPatchResult result =
                configurer(tempDir).patch(tempDir, "egovframework.let.emp");

        assertThat(result.failed()).isTrue();
        assertThat(result.message()).contains("실패:").contains("2개");
        assertThat(Files.readString(servletContext)).isEqualTo(malformed);
    }

    /**
     * interceptor는 이미 있지만 component-scan base-package가 아직 좁아서 componentScanPatch만
     * 변경이 필요한 경로 — 인터셉터 삽입 없이 componentScan 변경만 write돼야 한다.
     */
    @Test
    void patch_interceptorPresentButComponentScanNarrow_writesComponentScanChangeOnly(@TempDir Path tempDir)
            throws Exception {
        String withNarrowScanAndInterceptor = """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <context:component-scan base-package="egovframework.other"/>
                    <mvc:interceptors>
                        <mvc:interceptor>
                            <mvc:mapping path="/**"/>
                            <bean class="egovframework.let.emp.cmm.web.EgovGnbMenuInterceptor" autowire="constructor"/>
                        </mvc:interceptor>
                    </mvc:interceptors>
                </beans>
                """;
        Path servletContext = writeServletContext(tempDir, withNarrowScanAndInterceptor);

        ServletContextConfigurer.ServletContextPatchResult result =
                configurer(tempDir).patch(tempDir, "egovframework.let.emp");

        assertThat(result.failed()).isFalse();
        assertThat(result.message()).contains("변경:").contains("보존:").contains("이미 등록됨");
        String patched = Files.readString(servletContext);
        assertThat(patched).contains("base-package=\"egovframework.other, egovframework.let\"");
        // 인터셉터 블록이 중복 삽입되지 않아야 한다.
        assertThat(countOccurrences(patched, "EgovGnbMenuInterceptor")).isEqualTo(1);
    }

    /** ATOMIC_APPROVED 전환 확인: 디스크 쓰기가 실패하면 원본 파일이 그대로 보존돼야 한다. */
    @Test
    void patch_diskWriteFailure_leavesOriginalFileUntouched(@TempDir Path tempDir) throws Exception {
        Path servletContext = writeServletContext(tempDir, SERVLET_CONTEXT_XML);
        String original = Files.readString(servletContext);
        Path appServletDir = servletContext.getParent();
        boolean readOnlySet = appServletDir.toFile().setWritable(false);
        Assumptions.assumeTrue(readOnlySet, "이 실행 환경(예: root)에서는 디렉터리 쓰기 금지가 걸리지 않아 이 테스트를 건너뛴다.");
        try {
            ServletContextConfigurer.ServletContextPatchResult result =
                    configurer(tempDir).patch(tempDir, "egovframework.let.emp");

            assertThat(result.failed()).isTrue();
        } finally {
            appServletDir.toFile().setWritable(true);
        }
        assertThat(Files.readString(servletContext)).isEqualTo(original);
    }

    private Path writeServletContext(Path tempDir, String content) throws Exception {
        Path path = tempDir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
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
