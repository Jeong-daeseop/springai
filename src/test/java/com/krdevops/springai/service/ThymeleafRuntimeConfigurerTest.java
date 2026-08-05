package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WP7 5차 pass: {@code Files.writeString} 원시 호출을 공용
 * {@code ApprovedProjectWritePort}(ATOMIC_APPROVED)로 전환한다. 이 클래스는 이번 pass 전까지
 * 전용 테스트가 없었다(다른 테스트에서 통째로 mock돼 내부 로직이 실행된 적이 없음) — 리팩터링
 * 안전망으로 신규 작성.
 */
class ThymeleafRuntimeConfigurerTest {

    private ThymeleafRuntimeConfigurer configurer(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        OperationHashFactory hashFactory = new OperationHashFactory(new ObjectMapper());
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), hashFactory);
        return new ThymeleafRuntimeConfigurer(codeService, writePort, hashFactory);
    }

    private Path pom(Path outputRoot, String dependenciesBlock) throws Exception {
        Path pomPath = outputRoot.resolve("pom.xml");
        Files.writeString(pomPath, "<project>\n    <dependencies>" + dependenciesBlock + "</dependencies>\n</project>");
        return pomPath;
    }

    private Path servletContext(Path outputRoot, String beansBody) throws Exception {
        Path path = outputRoot.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "<beans>" + beansBody + "</beans>");
        return path;
    }

    // ── ensureThymeleafRuntime: pom.xml ─────────────────────────────────────

    @Test
    void ensureThymeleafRuntime_missingPomAndServletContext_isNoOpWithoutFailure(@TempDir Path outputRoot) {
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
    }

    @Test
    void ensureThymeleafRuntime_addsSpring6ThymeleafAndLayoutDialectDependencies(@TempDir Path outputRoot)
            throws Exception {
        Path pomPath = pom(outputRoot, "");
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
        String pom = Files.readString(pomPath);
        assertThat(pom)
                .contains("<artifactId>thymeleaf-spring6</artifactId>")
                .contains("<version>3.1.3.RELEASE</version>")
                .contains("<artifactId>thymeleaf-layout-dialect</artifactId>")
                .contains("<version>3.4.0</version>");
    }

    @Test
    void ensureThymeleafRuntime_addsSpring5DependenciesForLegacyEgovVersion(@TempDir Path outputRoot)
            throws Exception {
        Path pomPath = pom(outputRoot, "");
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "4.3", failed);

        assertThat(failed).isEmpty();
        String pom = Files.readString(pomPath);
        assertThat(pom)
                .contains("<artifactId>thymeleaf-spring5</artifactId>")
                .contains("<version>3.0.15.RELEASE</version>")
                .contains("<version>3.1.0</version>");
    }

    @Test
    void ensureThymeleafRuntime_pomAlreadyConfigured_doesNotRewritePom(@TempDir Path outputRoot) throws Exception {
        Path pomPath = pom(outputRoot,
                "<dependency><artifactId>thymeleaf-spring6</artifactId></dependency>"
                        + "<dependency><artifactId>thymeleaf-layout-dialect</artifactId></dependency>");
        String before = Files.readString(pomPath);
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
        assertThat(Files.readString(pomPath)).isEqualTo(before);
    }

    @Test
    void ensureThymeleafRuntime_pomWithoutDependenciesCloseTag_reportsFailureWithoutWriting(
            @TempDir Path outputRoot) throws Exception {
        Path pomPath = outputRoot.resolve("pom.xml");
        Files.writeString(pomPath, "<project></project>");
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).anyMatch(message -> message.contains("pom.xml") && message.contains("삽입 위치"));
        assertThat(Files.readString(pomPath)).isEqualTo("<project></project>");
    }

    // ── ensureThymeleafRuntime: servlet-context.xml ─────────────────────────

    @Test
    void ensureThymeleafRuntime_addsViewResolverBeansWhenMissing(@TempDir Path outputRoot) throws Exception {
        Path servletContextPath = servletContext(outputRoot, "");
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
        String xml = Files.readString(servletContextPath);
        assertThat(xml)
                .contains("org.thymeleaf.spring6.view.ThymeleafViewResolver")
                .contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect");
    }

    @Test
    void ensureThymeleafRuntime_viewResolverPresentButLayoutDialectMissing_addsLayoutDialectOnly(
            @TempDir Path outputRoot) throws Exception {
        Path servletContextPath = servletContext(outputRoot, """
                <bean id="thymeleafTemplateEngine" class="org.thymeleaf.spring6.SpringTemplateEngine">
                    <property name="enableSpringELCompiler" value="true"/>
                </bean>
                <bean class="org.thymeleaf.spring6.view.ThymeleafViewResolver">
                </bean>
                """);
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
        String xml = Files.readString(servletContextPath);
        assertThat(xml).contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect");
        // ViewResolver bean이 중복 삽입되지 않아야 한다.
        assertThat(countOccurrences(xml, "ThymeleafViewResolver")).isEqualTo(1);
    }

    @Test
    void ensureThymeleafRuntime_alreadyFullyConfigured_isNoOpWithoutWriting(@TempDir Path outputRoot)
            throws Exception {
        Path servletContextPath = servletContext(outputRoot, """
                <bean id="thymeleafTemplateEngine" class="org.thymeleaf.spring6.SpringTemplateEngine">
                    <property name="additionalDialects">
                        <set>
                            <bean class="nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect"/>
                        </set>
                    </property>
                </bean>
                <bean class="org.thymeleaf.spring6.view.ThymeleafViewResolver">
                </bean>
                """);
        String before = Files.readString(servletContextPath);
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
        assertThat(Files.readString(servletContextPath)).isEqualTo(before);
    }

    // ── 두 파일 동시 갱신과 원자성 ────────────────────────────────────────────

    @Test
    void ensureThymeleafRuntime_bothFilesNeedingUpdate_areAppliedTogether(@TempDir Path outputRoot)
            throws Exception {
        Path pomPath = pom(outputRoot, "");
        Path servletContextPath = servletContext(outputRoot, "");
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
        assertThat(Files.readString(pomPath)).contains("thymeleaf-spring6");
        assertThat(Files.readString(servletContextPath)).contains("ThymeleafViewResolver");
    }

    /**
     * ARCH-0718 잔여 항목의 핵심 동기: 전환 전에는 pom.xml write와 servlet-context.xml write가
     * 완전히 독립적이라, 하나가 디스크 쓰기 단계에서 실패해도 이미 성공한 다른 파일은 롤백되지
     * 않는 부분 적용 구멍이 있었다. ATOMIC_APPROVED 전환 후에는 두 파일이 한 배치로 묶여, 하나가
     * 실패하면 이미 스테이징까지 갔던 다른 파일도 원래 상태로 롤백돼야 한다.
     */
    @Test
    void ensureThymeleafRuntime_oneFileWriteFailureRollsBackTheOtherAlreadyStagedFile(@TempDir Path outputRoot)
            throws Exception {
        Path pomPath = pom(outputRoot, "");
        Path servletContextPath = servletContext(outputRoot, "");
        Path appServletDir = servletContextPath.getParent();
        boolean readOnlySet = appServletDir.toFile().setWritable(false);
        Assumptions.assumeTrue(readOnlySet, "이 실행 환경(예: root)에서는 디렉터리 쓰기 금지가 걸리지 않아 이 테스트를 건너뛴다.");
        try {
            List<String> failed = new ArrayList<>();

            configurer(outputRoot).ensureThymeleafRuntime(outputRoot.toString(), "5.0", failed);

            assertThat(failed).isNotEmpty();
        } finally {
            appServletDir.toFile().setWritable(true);
        }
        assertThat(Files.readString(pomPath)).doesNotContain("thymeleaf-spring6");
        assertThat(Files.readString(servletContextPath)).doesNotContain("ThymeleafViewResolver");
    }

    // ── ensureControllerComponentScan ───────────────────────────────────────

    @Test
    void ensureControllerComponentScan_missingServletContext_isNoOpWithoutFailure(@TempDir Path outputRoot) {
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureControllerComponentScan(outputRoot.toString(), "egovframework.let.emp.web", failed);

        assertThat(failed).isEmpty();
    }

    @Test
    void ensureControllerComponentScan_blankPackage_reportsFailureWithoutWriting(@TempDir Path outputRoot)
            throws Exception {
        servletContext(outputRoot, "<context:component-scan base-package=\"egovframework.let\"/>");
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureControllerComponentScan(outputRoot.toString(), " ", failed);

        assertThat(failed).anyMatch(message -> message.contains("비어 있습니다"));
    }

    @Test
    void ensureControllerComponentScan_addsPackageToExistingScan(@TempDir Path outputRoot) throws Exception {
        Path servletContextPath = servletContext(
                outputRoot, "<context:component-scan base-package=\"egovframework.let\"/>");
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureControllerComponentScan(
                outputRoot.toString(), "egovframework.let.emp.web", failed);

        assertThat(failed).isEmpty();
        assertThat(Files.readString(servletContextPath))
                .contains("base-package=\"egovframework.let,egovframework.let.emp.web\"");
    }

    @Test
    void ensureControllerComponentScan_alreadyRegistered_isIdempotentWithoutWriting(@TempDir Path outputRoot)
            throws Exception {
        Path servletContextPath = servletContext(outputRoot,
                "<context:component-scan base-package=\"egovframework.let,egovframework.let.emp.web\"/>");
        String before = Files.readString(servletContextPath);
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureControllerComponentScan(
                outputRoot.toString(), "egovframework.let.emp.web", failed);

        assertThat(failed).isEmpty();
        assertThat(Files.readString(servletContextPath)).isEqualTo(before);
    }

    @Test
    void ensureControllerComponentScan_patternNotFound_reportsFailureWithoutWriting(@TempDir Path outputRoot)
            throws Exception {
        Path servletContextPath = servletContext(outputRoot, "<no-component-scan-here/>");
        String before = Files.readString(servletContextPath);
        List<String> failed = new ArrayList<>();

        configurer(outputRoot).ensureControllerComponentScan(
                outputRoot.toString(), "egovframework.let.emp.web", failed);

        assertThat(failed).anyMatch(message -> message.contains("component-scan base-package 패턴을 찾을 수 없습니다"));
        assertThat(Files.readString(servletContextPath)).isEqualTo(before);
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
