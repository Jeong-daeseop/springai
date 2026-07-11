package com.krdevops.springai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thymeleaf 런타임 보강 전용 Service.
 *
 * <p>CRUD/게시판 오케스트레이션이 viewType=thymeleaf 로 동작할 때
 * 대상 프로젝트의 pom.xml(Maven 의존성)과 servlet-context.xml(ViewResolver)을
 * 멱등적으로 보강한다.
 *
 * <p>egovVersion 5.x / latest → Spring 6 기반 (thymeleaf-spring6, layout-dialect 3.4.0)<br>
 * egovVersion 4.3 → Spring 5 기반 (thymeleaf-spring5, layout-dialect 3.1.0)
 */
@Slf4j
@Service
public class ThymeleafRuntimeConfigurer {

    public void ensureThymeleafRuntime(String outputPath, String egovVersion, List<String> failed) {
        boolean spring6 = isSpring6(egovVersion);
        ensureThymeleafPomDependency(outputPath, spring6, failed);
        ensureThymeleafServletContext(outputPath, spring6, failed);
    }

    /** egovVersion이 5.x 또는 latest면 Spring 6 기반으로 판단한다. */
    private boolean isSpring6(String egovVersion) {
        return egovVersion != null
                && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));
    }

    private void ensureThymeleafPomDependency(String outputPath, boolean spring6, List<String> failed) {
        Path pomPath = Path.of(outputPath, "pom.xml");
        if (!Files.exists(pomPath)) {
            log.info("[thymeleaf-configurer] pom.xml 없음 — Thymeleaf Maven 의존성 보강 생략: {}", pomPath);
            return;
        }
        try {
            String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
            String updated = pom;

            String thymeleafArtifact  = spring6 ? "thymeleaf-spring6"  : "thymeleaf-spring5";
            String thymeleafVersion   = spring6 ? "3.1.3.RELEASE"      : "3.0.15.RELEASE";
            String layoutVersion      = spring6 ? "3.4.0"              : "3.1.0";

            // thymeleaf-spring5/6 의존성 보강 (없을 때만, 독립 체크)
            if (!updated.contains("<artifactId>" + thymeleafArtifact + "</artifactId>")) {
                String dep = String.format("""

        <dependency>
            <groupId>org.thymeleaf</groupId>
            <artifactId>%s</artifactId>
            <version>%s</version>
        </dependency>
""", thymeleafArtifact, thymeleafVersion);
                String after = updated.replaceFirst("</dependencies>", dep + "    </dependencies>");
                if (after.equals(updated)) {
                    failed.add("pom.xml — Thymeleaf 의존성 삽입 위치를 찾을 수 없습니다.");
                } else {
                    updated = after;
                }
            }

            // thymeleaf-layout-dialect 의존성 보강 (없을 때만, 독립 체크)
            if (!updated.contains("<artifactId>thymeleaf-layout-dialect</artifactId>")) {
                String layoutDep = String.format("""

        <dependency>
            <groupId>nz.net.ultraq.thymeleaf</groupId>
            <artifactId>thymeleaf-layout-dialect</artifactId>
            <version>%s</version>
        </dependency>
""", layoutVersion);
                String after = updated.replaceFirst("</dependencies>", layoutDep + "    </dependencies>");
                if (after.equals(updated)) {
                    failed.add("pom.xml — Thymeleaf layout-dialect 의존성 삽입 위치를 찾을 수 없습니다.");
                } else {
                    updated = after;
                }
            }

            if (!updated.equals(pom)) {
                Files.writeString(pomPath, updated, StandardCharsets.UTF_8);
                log.info("[thymeleaf-configurer] Thymeleaf Maven 의존성 추가 완료: {}", pomPath);
            }
        } catch (Exception e) {
            failed.add("pom.xml — Thymeleaf 의존성 추가 실패: " + e.getMessage());
            log.warn("[thymeleaf-configurer] Thymeleaf Maven 의존성 추가 실패: {}", e.getMessage());
        }
    }

    private void ensureThymeleafServletContext(String outputPath, boolean spring6, List<String> failed) {
        Path servletContextPath = Path.of(outputPath, "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        if (!Files.exists(servletContextPath)) {
            log.info("[thymeleaf-configurer] servlet-context.xml 없음 — Thymeleaf ViewResolver 보강 생략: {}", servletContextPath);
            return;
        }
        try {
            String xml = Files.readString(servletContextPath, StandardCharsets.UTF_8);

            String pkg            = spring6 ? "org.thymeleaf.spring6" : "org.thymeleaf.spring5";
            String resolverClass  = pkg + ".templateresolver.SpringResourceTemplateResolver";
            String engineClass    = pkg + ".SpringTemplateEngine";
            String vrClass        = pkg + ".view.ThymeleafViewResolver";

            // ViewResolver 자체가 없으면 LayoutDialect 포함 전체 bean 블록 삽입
            if (!xml.contains(vrClass)) {
                String updated = ensureJspViewResolverOrder(xml);
                String thymeleafBeans = String.format("""

    <bean id="thymeleafTemplateResolver"
          class="%s">
        <property name="prefix" value="classpath:/templates/"/>
        <property name="suffix" value=".html"/>
        <property name="templateMode" value="HTML"/>
        <property name="characterEncoding" value="UTF-8"/>
        <property name="cacheable" value="false"/>
    </bean>

    <bean id="thymeleafTemplateEngine"
          class="%s">
        <property name="templateResolver" ref="thymeleafTemplateResolver"/>
        <property name="enableSpringELCompiler" value="true"/>
        <property name="additionalDialects">
            <set>
                <bean class="nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect"/>
            </set>
        </property>
    </bean>

    <bean class="%s">
        <property name="templateEngine" ref="thymeleafTemplateEngine"/>
        <property name="characterEncoding" value="UTF-8"/>
        <property name="order" value="1"/>
    </bean>
""", resolverClass, engineClass, vrClass);
                updated = updated.replaceFirst("</beans>", thymeleafBeans + "\n</beans>");
                if (updated.equals(xml)) {
                    failed.add("servlet-context.xml — Thymeleaf ViewResolver 삽입 위치를 찾을 수 없습니다.");
                    return;
                }
                Files.writeString(servletContextPath, updated, StandardCharsets.UTF_8);
                log.info("[thymeleaf-configurer] Thymeleaf ViewResolver 추가 완료: {}", servletContextPath);
                return;
            }

            // ViewResolver 는 있으나 LayoutDialect 가 없으면 additionalDialects 보강
            if (xml.contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect")) {
                return;
            }

            String updated;
            if (xml.contains("<property name=\"additionalDialects\">")) {
                // 이미 additionalDialects 가 있으면 <set> 안에 LayoutDialect bean 만 추가
                updated = xml.replaceFirst(
                        "(<property name=\"additionalDialects\">\\s*<set>)",
                        "$1\n                <bean class=\"nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect\"/>");
            } else {
                // additionalDialects 가 없으면 thymeleafTemplateEngine bean 에 property 삽입
                updated = xml.replaceFirst(
                        "(<property name=\"enableSpringELCompiler\" value=\"true\"/>)",
                        "$1\n        <property name=\"additionalDialects\">\n"
                        + "            <set>\n"
                        + "                <bean class=\"nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect\"/>\n"
                        + "            </set>\n"
                        + "        </property>");
            }
            if (updated.equals(xml)) {
                failed.add("servlet-context.xml — LayoutDialect 삽입 위치를 찾을 수 없습니다.");
                return;
            }
            Files.writeString(servletContextPath, updated, StandardCharsets.UTF_8);
            log.info("[thymeleaf-configurer] Thymeleaf LayoutDialect 추가 완료: {}", servletContextPath);
        } catch (Exception e) {
            failed.add("servlet-context.xml — Thymeleaf ViewResolver 추가 실패: " + e.getMessage());
            log.warn("[thymeleaf-configurer] Thymeleaf ViewResolver 추가 실패: {}", e.getMessage());
        }
    }

    private String ensureJspViewResolverOrder(String xml) {
        Pattern pattern = Pattern.compile(
                "(?s)(<bean\\s+class=\"org\\.springframework\\.web\\.servlet\\.view\\.InternalResourceViewResolver\"[^>]*>)(.*?)(</bean>)");
        Matcher matcher = pattern.matcher(xml);
        if (!matcher.find()) {
            return xml;
        }

        String body = matcher.group(2);
        String updatedBody;
        if (body.contains("<property name=\"order\"")) {
            updatedBody = body.replaceFirst(
                    "<property name=\"order\"\\s+value=\"[^\"]*\"\\s*/>",
                    "<property name=\"order\" value=\"2\"/>");
        } else {
            updatedBody = body.replace(
                    "<property name=\"suffix\" value=\".jsp\"/>",
                    "<property name=\"suffix\" value=\".jsp\"/>\n        <property name=\"order\" value=\"2\"/>");
        }

        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + updatedBody + matcher.group(3)));
    }
}
