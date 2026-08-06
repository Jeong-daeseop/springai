package com.krdevops.springai.service;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 *
 * <p>WP7 5차 pass: 저장은 {@code Files.writeString} 원시 호출 대신 공용
 * {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#ATOMIC_APPROVED})로 위임한다.
 * {@code ensureThymeleafRuntime}이 건드리는 pom.xml/servlet-context.xml 중 실제로 갱신이
 * 필요한 파일들은 한 배치로 묶여, 하나의 디스크 쓰기가 실패하면 이미 성공했던 다른 파일도 함께
 * 롤백된다 — 예전에는 각 파일이 독립적으로 저장돼 부분 적용(예: pom.xml만 갱신되고
 * servlet-context.xml은 실패)이 가능했다. "삽입 위치를 찾을 수 없음" 같은 논리적 실패는 여전히
 * 파일별로 독립적으로 판정한다(한쪽의 논리적 실패가 다른 쪽의 정상 갱신을 막지 않는다) — 이 배치는
 * 오직 디스크 쓰기 단계의 원자성만 새로 보장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThymeleafRuntimeConfigurer {

    private static final String POM_RELATIVE_PATH = "pom.xml";
    private static final String SERVLET_CONTEXT_RELATIVE_PATH =
            "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml";

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final OperationHashFactory hashFactory;

    public void ensureThymeleafRuntime(String outputPath, String egovVersion, List<String> failed) {
        boolean spring6 = isSpring6(egovVersion);
        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        computePomDependencyChange(outputPath, spring6, failed).ifPresent(changes::add);
        computeServletContextViewResolverChange(outputPath, spring6, failed).ifPresent(changes::add);
        applyChanges(outputPath, changes, failed);
    }

    /** 생성된 Controller 패키지를 기존 필터형 component-scan에 멱등적으로 추가한다. */
    public void ensureControllerComponentScan(
            String outputPath, String controllerPackage, List<String> failed) {
        Path servletContextPath = Path.of(outputPath, SERVLET_CONTEXT_RELATIVE_PATH);
        if (!Files.exists(servletContextPath)) {
            log.info("[thymeleaf-configurer] servlet-context.xml 없음 — Controller scan 보강 생략: {}",
                    servletContextPath);
            return;
        }
        if (controllerPackage == null || controllerPackage.isBlank()) {
            failed.add("servlet-context.xml — 추가할 Controller 패키지가 비어 있습니다.");
            return;
        }
        try {
            String xml = Files.readString(servletContextPath, StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile(
                    "(<context:component-scan\\b[^>]*\\bbase-package=\")([^\"]*)(\"[^>]*>)");
            Matcher matcher = pattern.matcher(xml);
            if (!matcher.find()) {
                failed.add("servlet-context.xml — component-scan base-package 패턴을 찾을 수 없습니다.");
                return;
            }
            List<String> packages = List.of(matcher.group(2).split("\\s*[,;]\\s*"));
            if (packages.contains(controllerPackage)) {
                log.info("[thymeleaf-configurer] Controller component-scan 이미 등록됨: {}", controllerPackage);
                return;
            }
            String replacement = matcher.group(1) + matcher.group(2) + "," + controllerPackage
                    + matcher.group(3);
            String updated = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
            applyChanges(outputPath,
                    List.of(fileChange(SERVLET_CONTEXT_RELATIVE_PATH, xml, updated)), failed);
        } catch (IOException e) {
            failed.add("servlet-context.xml — Controller component-scan 보강 실패: " + e.getMessage());
            log.warn("[thymeleaf-configurer] Controller component-scan 보강 실패: {}", e.getMessage());
        }
    }

    /** egovVersion이 5.x 또는 latest면 Spring 6 기반으로 판단한다. */
    private boolean isSpring6(String egovVersion) {
        return egovVersion != null
                && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));
    }

    private Optional<ProjectChangeSet.FileChange> computePomDependencyChange(
            String outputPath, boolean spring6, List<String> failed) {
        Path pomPath = Path.of(outputPath, POM_RELATIVE_PATH);
        if (!Files.exists(pomPath)) {
            log.info("[thymeleaf-configurer] pom.xml 없음 — Thymeleaf Maven 의존성 보강 생략: {}", pomPath);
            return Optional.empty();
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

            if (updated.equals(pom)) {
                return Optional.empty();
            }
            return Optional.of(fileChange(POM_RELATIVE_PATH, pom, updated));
        } catch (IOException e) {
            failed.add("pom.xml — Thymeleaf 의존성 추가 실패: " + e.getMessage());
            log.warn("[thymeleaf-configurer] Thymeleaf Maven 의존성 추가 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ProjectChangeSet.FileChange> computeServletContextViewResolverChange(
            String outputPath, boolean spring6, List<String> failed) {
        Path servletContextPath = Path.of(outputPath, SERVLET_CONTEXT_RELATIVE_PATH);
        if (!Files.exists(servletContextPath)) {
            log.info("[thymeleaf-configurer] servlet-context.xml 없음 — Thymeleaf ViewResolver 보강 생략: {}",
                    servletContextPath);
            return Optional.empty();
        }
        try {
            String xml = Files.readString(servletContextPath, StandardCharsets.UTF_8);
            return computeServletContextUpdatedContent(xml, spring6, failed)
                    .map(updated -> fileChange(SERVLET_CONTEXT_RELATIVE_PATH, xml, updated));
        } catch (IOException e) {
            failed.add("servlet-context.xml — Thymeleaf ViewResolver 추가 실패: " + e.getMessage());
            log.warn("[thymeleaf-configurer] Thymeleaf ViewResolver 추가 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> computeServletContextUpdatedContent(String xml, boolean spring6, List<String> failed) {
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
                return Optional.empty();
            }
            return Optional.of(updated);
        }

        // ViewResolver 는 있으나 LayoutDialect 가 없으면 additionalDialects 보강
        if (xml.contains("nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect")) {
            return Optional.empty();
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
            return Optional.empty();
        }
        return Optional.of(updated);
    }

    private ProjectChangeSet.FileChange fileChange(String relativePath, String before, String after) {
        return new ProjectChangeSet.FileChange(
                relativePath, hashFactory.sha256Hex(before.getBytes(StandardCharsets.UTF_8)), after, null);
    }

    private void applyChanges(String outputPath, List<ProjectChangeSet.FileChange> changes, List<String> failed) {
        if (changes.isEmpty()) {
            return;
        }
        codeService.validateOutputRoot(outputPath);
        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath, null, changes, List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);
        if (outcome.status() == ApplyOutcome.Status.APPLIED) {
            for (var change : changes) {
                log.info("[thymeleaf-configurer] 파일 갱신 완료: {}", change.path());
            }
            return;
        }
        String detail = switch (outcome.status()) {
            case CONFLICT -> "적용 직전 파일이 변경됨: " + outcome.conflictingPaths();
            case ROLLED_BACK -> outcome.failureDetail();
            case ROLLBACK_FAILED -> "복구까지 실패함(" + outcome.failureDetail()
                    + ") — 원본 상태로 안 돌아갔을 수 있습니다: " + outcome.failureMessages();
            default -> "알 수 없는 결과: " + outcome.status();
        };
        for (var change : changes) {
            failed.add(change.path() + " — " + detail);
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
