package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.ProjectSpec;
import com.krdevops.springai.service.initializr.template.BuildFileRenderer;
import com.krdevops.springai.service.initializr.template.StaticTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.krdevops.springai.model.FilePlan.FileKind.*;

@Component
@RequiredArgsConstructor
public class FilePlanFactory {

    // ── 버전 상수 (Builder 클래스에서 import static으로 참조) ────────────────
    public static final String EGOV_43            = "4.3.0";
    public static final String SPRING_5           = "5.3.37";
    public static final String SPRING_BOOT_2      = "2.7.18";
    public static final String MYBATIS_35         = "3.5.16";
    public static final String MYBATIS_SPRING_2   = "2.1.2";
    public static final String MYBATIS_SB2        = "2.3.2";

    public static final String EGOV_50            = "5.0.0";
    public static final String SPRING_6           = "6.2.11";
    public static final String SPRING_BOOT_3      = "3.5.6";
    public static final String MYBATIS_SPRING_3   = "3.0.5";
    public static final String MYBATIS_SB3        = "3.0.3";

    public static final String JAVA_11            = "11";
    public static final String JAVA_17            = "17";

    public static final String EGOV_WAR_PARENT_GROUP     = "org.egovframe.web";
    public static final String EGOV_WAR_PARENT_ARTIFACT  = "egovframe-web-config-parent";
    public static final String EGOV_BOOT_PARENT_GROUP    = "org.egovframe.boot";
    public static final String EGOV_BOOT_PARENT_ARTIFACT = "egovframe-boot-starter-parent";

    private final StaticTemplateRenderer stpl;
    private final BuildFileRenderer bld;

    // ── FilePlan 목록 조립 ───────────────────────────────────────────────────

    public List<FilePlan> plan(ProjectSpec s) {
        List<FilePlan> plans = new ArrayList<>();
        plans.addAll(buildFilePlans(s));
        plans.addAll(s.boot() ? bootFiles(s) : warFiles(s));
        plans.add(FilePlan.of(".gitignore", META, () -> stpl.gitignore(s)));
        return plans;
    }

    /** 디렉터리 목록 반환 (createDirectories에서 사용) */
    public List<String> directoryPlans(ProjectSpec s) {
        List<String> dirs = new ArrayList<>(List.of(
            "src/main/java/" + s.packagePath(),
            "src/main/resources/egovframework/mapper",
            "src/test/java/" + s.packagePath()
        ));
        if (s.boot()) {
            dirs.add("src/main/resources/static/resources/css");
            dirs.add("src/main/resources/static/resources/js");
            dirs.add("src/main/resources/templates");
        } else {
            dirs.add("src/main/java/" + s.packagePath() + "/main/service");
            dirs.add("src/main/java/" + s.packagePath() + "/main/service/impl");
            dirs.add("src/main/resources/egovframework/spring");
            dirs.add("src/main/webapp/WEB-INF/spring/appServlet");
            dirs.add("src/main/webapp/WEB-INF/jsp/egovframework");
            if (s.thymeleaf()) {
                dirs.add("src/main/resources/templates/egovframework/main");
                dirs.add("src/main/resources/templates/layout");
            }
            dirs.add("src/main/webapp/resources/css");
            dirs.add("src/main/webapp/resources/js");
        }
        return dirs;
    }

    private List<FilePlan> buildFilePlans(ProjectSpec s) {
        if (s.gradle()) {
            return List.of(
                FilePlan.of("build.gradle",      BUILD, () -> s.boot() ? bld.bootBuildGradle(s) : bld.warBuildGradle(s)),
                FilePlan.of("settings.gradle",   BUILD, () -> "rootProject.name = '" + s.artifactId() + "'\n"),
                FilePlan.of("gradle.properties", BUILD, () -> "org.gradle.jvmargs=-Xmx1024m\norg.gradle.daemon=true\n")
            );
        }
        return List.of(
            FilePlan.of("pom.xml", BUILD, () -> s.boot() ? bld.bootPom(s) : bld.warPom(s))
        );
    }

    private List<FilePlan> warFiles(ProjectSpec s) {
        List<FilePlan> plans = new ArrayList<>(List.of(
                FilePlan.of("src/main/resources/egovframework/spring/context-common.xml",
                            CONFIG, () -> stpl.contextCommon(s)),
                FilePlan.of("src/main/resources/egovframework/spring/context-datasource.xml",
                            CONFIG, stpl::contextDatasource),
                FilePlan.of("src/main/resources/egovframework/spring/context-transaction.xml",
                            CONFIG, stpl::contextTransaction),
                FilePlan.of("src/main/webapp/WEB-INF/spring/root-context.xml",
                            CONFIG, stpl::rootContext),
                FilePlan.of("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml",
                            CONFIG, () -> bld.dispatcherServlet(s)),
                FilePlan.of("src/main/webapp/WEB-INF/web.xml",
                            WEB,    () -> bld.webXml(s)),
                FilePlan.of(s.thymeleaf() ? "src/main/webapp/index.html" : "src/main/webapp/index.jsp",
                            WEB,    () -> s.thymeleaf() ? indexHtml() : stpl.indexJsp()),
                FilePlan.of("src/main/webapp/resources/css/styles.css",
                            WEB,    stpl::stylesCss),
                FilePlan.of("src/main/webapp/resources/css/_ds_bundle.css",
                            WEB,    stpl::dsBundleCss),
                FilePlan.of("src/main/webapp/resources/js/krds.min.js",
                            WEB,    stpl::krdsJs),
                FilePlan.of("src/main/java/" + s.packagePath() + "/main/web/MainController.java",
                            SOURCE, () -> mainController(s)),
                FilePlan.of("src/main/webapp/WEB-INF/jsp/egovframework/error/error404.jsp",
                            WEB,    () -> error404Jsp()),
                FilePlan.of("src/main/webapp/WEB-INF/jsp/egovframework/error/error500.jsp",
                            WEB,    () -> error500Jsp()),
                FilePlan.of("src/main/resources/log4j2.xml",
                            RESOURCE, () -> stpl.log4j2(s))
        ));
        if (s.thymeleaf()) {
            plans.add(FilePlan.of("src/main/resources/templates/egovframework/main/main.html",
                    RESOURCE, () -> mainThymeleafHtml()));
            plans.add(FilePlan.of("src/main/resources/templates/layout/default.html",
                    RESOURCE, () -> defaultLayoutHtml()));
            plans.add(FilePlan.of("src/main/resources/templates/layout/gnb.html",
                    RESOURCE, () -> gnbLayoutHtml()));
            plans.add(FilePlan.of("src/main/resources/templates/layout/lnb.html",
                    RESOURCE, () -> lnbLayoutHtml()));
            plans.add(FilePlan.of("src/main/resources/templates/layout/breadcrumb.html",
                    RESOURCE, () -> breadcrumbLayoutHtml()));
            plans.add(FilePlan.of("src/main/resources/templates/layout/footer.html",
                    RESOURCE, () -> footerLayoutHtml()));
        } else {
            plans.add(FilePlan.of("src/main/webapp/WEB-INF/jsp/egovframework/main/main.jsp",
                    WEB, () -> mainJsp()));
        }
        return plans;
    }

    private List<FilePlan> bootFiles(ProjectSpec s) {
        String base = "src/main/java/" + s.packagePath();
        String test = "src/test/java/" + s.packagePath();
        String cls  = s.className();
        return List.of(
            FilePlan.of("src/main/resources/application.yml",
                        RESOURCE, () -> stpl.applicationYml(s)),
            FilePlan.of("src/main/resources/logback-spring.xml",
                        RESOURCE, () -> stpl.logback(s)),
            FilePlan.of("src/main/resources/static/resources/css/styles.css",
                        RESOURCE, stpl::stylesCss),
            FilePlan.of("src/main/resources/static/resources/css/_ds_bundle.css",
                        RESOURCE, stpl::dsBundleCss),
            FilePlan.of("src/main/resources/static/resources/js/krds.min.js",
                        RESOURCE, stpl::krdsJs),
            FilePlan.of(base + "/" + cls + "Application.java",
                        SOURCE,   () -> stpl.bootMain(s)),
            FilePlan.of(test + "/" + cls + "ApplicationTests.java",
                        TEST,     () -> stpl.bootTest(s))
        );
    }

    // ── 버전 비교 헬퍼 (Builder 클래스에서 참조) ──────────────────────────────

    private static final String EGOV_LATEST = EGOV_50;

    public static boolean supportsJakarta(String v)         { return compareVersion(v, "5.0") >= 0; }
    public static boolean supportsSpring6(String v)         { return compareVersion(v, "5.0") >= 0; }
    public static boolean supportsMyBatisSpring3(String v)  { return compareVersion(v, "5.0") >= 0; }
    public static boolean supportsJava17(String v)          { return compareVersion(v, "5.0") >= 0; }
    public static boolean supportsHyphenArtifactId(String v){ return compareVersion(v, "5.0") >= 0; }
    public static boolean supportsEgovParent(String v)      { return compareVersion(v, "5.0") >= 0; }
    public static boolean supportsBoot3(String v)           { return compareVersion(v, "5.0") >= 0; }

    private static int compareVersion(String version, String threshold) {
        if (version == null || version.isBlank()) return -1;
        String v = "latest".equalsIgnoreCase(version) ? EGOV_LATEST : version;
        String[] vParts = v.split("\\.");
        String[] tParts = threshold.split("\\.");
        int len = Math.max(vParts.length, tParts.length);
        for (int i = 0; i < len; i++) {
            int vNum = i < vParts.length ? parseVersionSegment(vParts[i]) : 0;
            int tNum = i < tParts.length ? parseVersionSegment(tParts[i]) : 0;
            if (vNum != tNum) return Integer.compare(vNum, tNum);
        }
        return 0;
    }

    private static int parseVersionSegment(String seg) {
        try { return Integer.parseInt(seg.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── getConfigTemplate() 위임용 public 편의 메서드 ────────────────────────
    // ProjectInitializrService.getConfigTemplate()에서 직접 호출됨

    public String contextCommon(String packageName) {
        return stpl.contextCommon(minimalSpec(packageName));
    }

    public String contextDatasource() {
        return stpl.contextDatasource();
    }

    public String contextTransaction() {
        return stpl.contextTransaction();
    }

    public String dispatcherServlet(String packageName, String egovVersion) {
        return bld.dispatcherServlet(minimalSpec(packageName, egovVersion));
    }

    public String webXml(String artifactId, String egovVersion) {
        return bld.webXml(minimalSpecForArtifact(artifactId, egovVersion));
    }

    public String logbackSpringXml(String projectName) {
        return stpl.logback(minimalSpecForProject(projectName));
    }

    public String log4j2Xml(String projectName) {
        return stpl.log4j2(minimalSpecForProject(projectName));
    }

    public String bootApplicationYml(String artifactId, String packageName) {
        return stpl.applicationYml(minimalSpecForBoot(artifactId, packageName));
    }

    // ── 최소 ProjectSpec 생성 헬퍼 (getConfigTemplate 편의 메서드용) ──────────

    private static ProjectSpec minimalSpec(String packageName) {
        return minimalSpec(packageName, "5.0");
    }

    private static ProjectSpec minimalSpec(String packageName, String egovVersion) {
        com.krdevops.springai.model.VersionCapability cap = buildCap(egovVersion);
        return new ProjectSpec("project", "com.example", "project",
                packageName, "maven", false,
                java.nio.file.Paths.get("/tmp"), packageName.replace(".", "/"), cap, "jsp");
    }

    private static ProjectSpec minimalSpecForArtifact(String artifactId, String egovVersion) {
        com.krdevops.springai.model.VersionCapability cap = buildCap(egovVersion);
        return new ProjectSpec(artifactId, "com.example", artifactId,
                "egovframework.let.sample", "maven", false,
                java.nio.file.Paths.get("/tmp"), "egovframework/let/sample", cap, "jsp");
    }

    private static ProjectSpec minimalSpecForProject(String projectName) {
        com.krdevops.springai.model.VersionCapability cap = buildCap("5.0");
        return new ProjectSpec(projectName, "com.example", projectName,
                "egovframework.let.sample", "maven", false,
                java.nio.file.Paths.get("/tmp"), "egovframework/let/sample", cap, "jsp");
    }

    private static ProjectSpec minimalSpecForBoot(String artifactId, String packageName) {
        com.krdevops.springai.model.VersionCapability cap = buildCap("5.0");
        return new ProjectSpec(artifactId, "com.example", artifactId,
                packageName, "maven", true,
                java.nio.file.Paths.get("/tmp"), packageName.replace(".", "/"), cap, "jsp");
    }

    /**
     * index.jsp(stpl::indexJsp)가 "/egovframework/com/main.do"로 forward하므로,
     * 이를 처리할 MainController를 항상 함께 생성해 welcome page 404를 방지한다.
     */
    private static String mainController(ProjectSpec s) {
        return """
package %s.main.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 메인 화면 Controller
 * @author eGovFrame
 */
@Controller
public class MainController {

    /**
     * 메인 화면을 조회한다.
     * @return 메인 화면 JSP 경로
     */
    @RequestMapping(value = "/egovframework/com/main.do", method = RequestMethod.GET)
    public String main() {
        return "egovframework/main/main";
    }

}
""".formatted(s.packageName());
    }

    private static String mainJsp() {
        return """
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8" />
    <title>메인 - eGovFrame</title>
</head>
<body>
<h2>전자정부 표준프레임워크 메인 화면입니다.</h2>
</body>
</html>
""";
    }

    private static String indexHtml() {
        return """
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8" />
    <meta http-equiv="refresh" content="0;url=/egovframework/com/main.do" />
    <title>eGovFrame</title>
</head>
<body>
<a href="/egovframework/com/main.do">메인 화면으로 이동</a>
</body>
</html>
""";
    }

    private static String mainThymeleafHtml() {
        return """
<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>메인 - eGovFrame</title>
</head>
<body>
<section layout:fragment="content" class="egov-main-dashboard">
    <div class="egov-main-hero">
        <p class="egov-main-kicker">eGovFrame</p>
        <h1 class="egov-main-title">전자정부 표준프레임워크 메인 화면입니다.</h1>
        <p class="egov-main-description">Thymeleaf 공통 layout을 사용하는 기본 메인 화면입니다.</p>
    </div>
</section>
</body>
</html>
""";
    }

    private static String defaultLayoutHtml() {
        return """
<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title layout:title-pattern="$CONTENT_TITLE - eGovFrame">eGovFrame</title>
    <link rel="stylesheet" th:href="@{/resources/css/styles.css}" />
    <script th:src="@{/resources/js/krds.min.js}" defer></script>
</head>
<body>
<header th:replace="~{layout/gnb :: gnb}"></header>
<div th:replace="~{layout/breadcrumb :: breadcrumb}"></div>
<main class="egov-layout-shell">
    <nav th:replace="~{layout/lnb :: lnb}"></nav>
    <div class="egov-layout-content">
        <section layout:fragment="content"></section>
    </div>
</main>
<footer th:replace="~{layout/footer :: footer}"></footer>
</body>
</html>
""";
    }

    private static String gnbLayoutHtml() {
        return """
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<body>
<header th:fragment="gnb" class="egov-header">
    <div class="egov-header-inner">
        <a class="egov-header-brand" th:href="@{/egovframework/com/main.do}">
            <span class="egov-brand-mark header" aria-hidden="true">eG</span>
            <span>eGovFrame</span>
        </a>
        <nav class="egov-main-menu" aria-label="주 메뉴">
            <ul class="egov-main-menu-list">
                <li class="egov-mega-item">
                    <a class="egov-main-menu-link gnb-active" th:href="@{/egovframework/com/main.do}" aria-current="page">Home</a>
                </li>
            </ul>
        </nav>
    </div>
</header>
</body>
</html>
""";
    }

    private static String lnbLayoutHtml() {
        return """
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<body>
<aside th:fragment="lnb" class="egov-lnb"></aside>
</body>
</html>
""";
    }

    private static String breadcrumbLayoutHtml() {
        return """
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<body>
<div th:fragment="breadcrumb" class="egov-breadcrumb-band">
    <nav class="egov-breadcrumb" aria-label="breadcrumb">
        <a class="egov-breadcrumb-home" th:href="@{/egovframework/com/main.do}" aria-label="Home">⌂</a>
        <span class="egov-breadcrumb-separator" aria-hidden="true">/</span>
        <span class="egov-breadcrumb-current">Home</span>
    </nav>
</div>
</body>
</html>
""";
    }

    private static String footerLayoutHtml() {
        return """
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<body>
<footer th:fragment="footer" class="egov-footer">
    <div class="egov-footer-inner">
        <div class="egov-footer-brand">
            <span class="egov-brand-mark footer" aria-hidden="true">eG</span>
            <span>eGovFrame</span>
        </div>
        <p class="egov-footer-text">전자정부 표준프레임워크 Thymeleaf 공통 레이아웃</p>
        <p class="egov-footer-text last">공통 header, breadcrumb, content, footer 영역을 사용합니다.</p>
    </div>
    <div class="egov-footer-bottom">
        <div class="egov-footer-bottom-inner">
            <a class="egov-footer-policy" th:href="@{/egovframework/com/main.do}">Home</a>
            <span class="egov-footer-copy">© eGovFrame</span>
        </div>
    </div>
</footer>
</body>
</html>
""";
    }

    private static String error404Jsp() {
        return """
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8" />
    <title>404 - 페이지를 찾을 수 없습니다</title>
</head>
<body>
<c:url var="homeUrl" value="/"/>
<h2>404 - 페이지를 찾을 수 없습니다.</h2>
<p>요청하신 페이지가 존재하지 않습니다.</p>
<p><a href="${homeUrl}">메인으로 이동</a></p>
</body>
</html>
""";
    }

    private static String error500Jsp() {
        return """
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8" />
    <title>500 - 서버 오류</title>
</head>
<body>
<c:url var="homeUrl" value="/"/>
<h2>500 - 서버 오류가 발생했습니다.</h2>
<p>일시적인 오류입니다. 잠시 후 다시 시도해 주세요.</p>
<p><a href="${homeUrl}">메인으로 이동</a></p>
</body>
</html>
""";
    }

    private static com.krdevops.springai.model.VersionCapability buildCap(String egovVersion) {
        boolean is50 = compareVersion(egovVersion, "5.0") >= 0;
        return new com.krdevops.springai.model.VersionCapability(
                is50, is50, is50, is50, is50, is50, is50,
                is50 ? "5.0" : "4.3",
                is50 ? "17" : "11",
                is50 ? SPRING_6 : SPRING_5,
                is50 ? SPRING_BOOT_3 : SPRING_BOOT_2,
                "6.3.1");
    }
}
