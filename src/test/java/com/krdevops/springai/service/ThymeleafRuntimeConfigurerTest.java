package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafRuntimeConfigurerTest {

    private final ThymeleafRuntimeConfigurer sut = new ThymeleafRuntimeConfigurer();

    // ── pom.xml 없으면 failed에 추가 없이 조용히 종료 ─────────────────────────

    @Test
    void ensureThymeleafRuntime_noPom_noFailure(@TempDir Path dir) {
        List<String> failed = new ArrayList<>();
        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);
        assertThat(failed).isEmpty();
    }

    // ── Spring 6 (egovVersion 5.x / latest) — thymeleaf-spring6 + dialect 3.3.0 ─

    @Test
    void ensureThymeleafRuntime_spring6_injectsSpring6Artifact(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);

        String pom = readPom(dir);
        assertThat(failed).isEmpty();
        assertThat(pom).contains("<artifactId>thymeleaf-spring6</artifactId>");
        assertThat(pom).contains("<version>3.1.3.RELEASE</version>");
        assertThat(pom).contains("<artifactId>thymeleaf-layout-dialect</artifactId>");
        assertThat(pom).contains("<version>3.3.0</version>");
        assertThat(pom).doesNotContain("thymeleaf-spring5");
    }

    @Test
    void ensureThymeleafRuntime_latest_treatedAsSpring6(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "latest", failed);

        assertThat(readPom(dir)).contains("<artifactId>thymeleaf-spring6</artifactId>");
    }

    // ── Spring 5 (egovVersion 4.3) — thymeleaf-spring5 + dialect 3.1.0 ─────────

    @Test
    void ensureThymeleafRuntime_spring5_injectsSpring5Artifact(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "4.3", failed);

        String pom = readPom(dir);
        assertThat(failed).isEmpty();
        assertThat(pom).contains("<artifactId>thymeleaf-spring5</artifactId>");
        assertThat(pom).contains("<version>3.0.15.RELEASE</version>");
        assertThat(pom).contains("<artifactId>thymeleaf-layout-dialect</artifactId>");
        assertThat(pom).contains("<version>3.1.0</version>");
        assertThat(pom).doesNotContain("thymeleaf-spring6");
    }

    // ── 멱등성 — 이미 의존성 있으면 중복 삽입 없음 ──────────────────────────────

    @Test
    void ensureThymeleafRuntime_alreadyHasSpring6_noDuplicate(@TempDir Path dir) throws IOException {
        String existing = minimalPom().replace("</dependencies>",
                """
                <dependency>
                    <groupId>org.thymeleaf</groupId>
                    <artifactId>thymeleaf-spring6</artifactId>
                    <version>3.1.3.RELEASE</version>
                </dependency>
                <dependency>
                    <groupId>nz.net.ultraq.thymeleaf</groupId>
                    <artifactId>thymeleaf-layout-dialect</artifactId>
                    <version>3.3.0</version>
                </dependency>
                </dependencies>""");
        writePom(dir, existing);
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);

        String pom = readPom(dir);
        long spring6Count = pom.lines()
                .filter(l -> l.contains("thymeleaf-spring6")).count();
        assertThat(spring6Count).isEqualTo(1);
        assertThat(failed).isEmpty();
    }

    // ── servlet-context.xml 없으면 failed에 추가 없이 조용히 종료 ───────────────

    @Test
    void ensureThymeleafRuntime_noServletContext_noFailure(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);

        assertThat(failed).isEmpty();
    }

    // ── servlet-context.xml — Spring 6 클래스 주입 ──────────────────────────────

    @Test
    void ensureThymeleafRuntime_spring6_injectsSpring6ViewResolverClasses(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        writeServletContext(dir, minimalServletContext());
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);

        String xml = readServletContext(dir);
        assertThat(failed).isEmpty();
        assertThat(xml).contains("org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver");
        assertThat(xml).contains("org.thymeleaf.spring6.SpringTemplateEngine");
        assertThat(xml).contains("org.thymeleaf.spring6.view.ThymeleafViewResolver");
        assertThat(xml).contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect");
        assertThat(xml).doesNotContain("org.thymeleaf.spring5");
        assertThat(xml).contains("<property name=\"order\" value=\"2\"/>");
        assertThat(xml).contains("<property name=\"order\" value=\"1\"/>");
    }

    @Test
    void ensureThymeleafRuntime_existingJspResolverOrder_replacesWith2WithoutDuplicate(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        writeServletContext(dir, servletContextWithJspResolverOrder1());
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);

        String xml = readServletContext(dir);
        assertThat(failed).isEmpty();
        assertThat(xml).contains("org.thymeleaf.spring6.view.ThymeleafViewResolver");
        assertThat(xml).contains("<property name=\"order\" value=\"2\"/>");
        assertThat(xml).contains("<property name=\"order\" value=\"1\"/>");
        assertThat(xml.lines()
                .filter(line -> line.contains("<property name=\"order\""))
                .count()).isEqualTo(2);
        assertThat(xml).doesNotContain("<property name=\"order\"  value=\"1\"/>");
    }

    // ── servlet-context.xml — Spring 5 클래스 주입 ──────────────────────────────

    @Test
    void ensureThymeleafRuntime_spring5_injectsSpring5ViewResolverClasses(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        writeServletContext(dir, minimalServletContext());
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "4.3", failed);

        String xml = readServletContext(dir);
        assertThat(failed).isEmpty();
        assertThat(xml).contains("org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver");
        assertThat(xml).contains("org.thymeleaf.spring5.SpringTemplateEngine");
        assertThat(xml).contains("org.thymeleaf.spring5.view.ThymeleafViewResolver");
        assertThat(xml).contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect");
        assertThat(xml).doesNotContain("org.thymeleaf.spring6");
    }

    // ── servlet-context.xml — LayoutDialect 만 누락된 경우 보강 ─────────────────

    @Test
    void ensureThymeleafRuntime_viewResolverExistsButNoLayoutDialect_addsDialect(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        String xmlWithoutDialect = minimalServletContext().replace("</beans>",
                """
                    <bean id="thymeleafTemplateEngine"
                          class="org.thymeleaf.spring6.SpringTemplateEngine">
                        <property name="enableSpringELCompiler" value="true"/>
                    </bean>
                    <bean class="org.thymeleaf.spring6.view.ThymeleafViewResolver">
                        <property name="templateEngine" ref="thymeleafTemplateEngine"/>
                    </bean>
                </beans>""");
        writeServletContext(dir, xmlWithoutDialect);
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);

        String xml = readServletContext(dir);
        assertThat(xml).contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect");
        assertThat(failed).isEmpty();
    }

    // ── servlet-context.xml — 이미 LayoutDialect 있으면 중복 없음 ───────────────

    @Test
    void ensureThymeleafRuntime_layoutDialectAlreadyPresent_noDuplicate(@TempDir Path dir) throws IOException {
        writePom(dir, minimalPom());
        String xmlWithDialect = minimalServletContext().replace("</beans>",
                """
                    <bean class="org.thymeleaf.spring6.view.ThymeleafViewResolver"/>
                    <bean class="nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect"/>
                </beans>""");
        writeServletContext(dir, xmlWithDialect);
        List<String> failed = new ArrayList<>();

        sut.ensureThymeleafRuntime(dir.toString(), "5.0", failed);

        String xml = readServletContext(dir);
        long dialectCount = xml.lines()
                .filter(l -> l.contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect")).count();
        assertThat(dialectCount).isEqualTo(1);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private static String minimalPom() {
        return """
                <project>
                    <dependencies>
                    </dependencies>
                </project>
                """;
    }

    private static String minimalServletContext() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean class="org.springframework.web.servlet.view.InternalResourceViewResolver">
                        <property name="prefix" value="/WEB-INF/jsp/"/>
                        <property name="suffix" value=".jsp"/>
                    </bean>
                </beans>
                """;
    }

    private static String servletContextWithJspResolverOrder1() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean class="org.springframework.web.servlet.view.InternalResourceViewResolver">
                        <property name="prefix" value="/WEB-INF/jsp/"/>
                        <property name="suffix" value=".jsp"/>
                        <property name="order"  value="1"/>
                    </bean>
                </beans>
                """;
    }

    private static void writePom(Path dir, String content) throws IOException {
        Files.writeString(dir.resolve("pom.xml"), content, StandardCharsets.UTF_8);
    }

    private static String readPom(Path dir) throws IOException {
        return Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    }

    private static void writeServletContext(Path dir, String content) throws IOException {
        Path target = dir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static String readServletContext(Path dir) throws IOException {
        return Files.readString(
                dir.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml"),
                StandardCharsets.UTF_8);
    }
}
