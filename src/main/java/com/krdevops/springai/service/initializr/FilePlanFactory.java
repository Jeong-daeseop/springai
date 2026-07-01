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
            dirs.add("src/main/java/" + s.packagePath() + "/web");
            dirs.add("src/main/java/" + s.packagePath() + "/service");
            dirs.add("src/main/java/" + s.packagePath() + "/service/impl");
            dirs.add("src/main/resources/egovframework/spring");
            dirs.add("src/main/webapp/WEB-INF/spring/appServlet");
            dirs.add("src/main/webapp/WEB-INF/jsp/egovframework");
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
        return List.of(
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
            FilePlan.of("src/main/webapp/index.jsp",
                        WEB,    stpl::indexJsp),
            FilePlan.of("src/main/webapp/resources/css/styles.css",
                        WEB,    stpl::stylesCss),
            FilePlan.of("src/main/webapp/resources/css/_ds_bundle.css",
                        WEB,    stpl::dsBundleCss),
            FilePlan.of("src/main/webapp/resources/js/krds.min.js",
                        WEB,    stpl::krdsJs),
            FilePlan.of("src/main/webapp/WEB-INF/jsp/egovframework/error/error404.jsp",
                        WEB,    () -> error404Jsp()),
            FilePlan.of("src/main/webapp/WEB-INF/jsp/egovframework/error/error500.jsp",
                        WEB,    () -> error500Jsp()),
            FilePlan.of("src/main/resources/log4j2.xml",
                        RESOURCE, () -> stpl.log4j2(s))
        );
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
                java.nio.file.Paths.get("/tmp"), packageName.replace(".", "/"), cap);
    }

    private static ProjectSpec minimalSpecForArtifact(String artifactId, String egovVersion) {
        com.krdevops.springai.model.VersionCapability cap = buildCap(egovVersion);
        return new ProjectSpec(artifactId, "com.example", artifactId,
                "egovframework.let.sample", "maven", false,
                java.nio.file.Paths.get("/tmp"), "egovframework/let/sample", cap);
    }

    private static ProjectSpec minimalSpecForProject(String projectName) {
        com.krdevops.springai.model.VersionCapability cap = buildCap("5.0");
        return new ProjectSpec(projectName, "com.example", projectName,
                "egovframework.let.sample", "maven", false,
                java.nio.file.Paths.get("/tmp"), "egovframework/let/sample", cap);
    }

    private static ProjectSpec minimalSpecForBoot(String artifactId, String packageName) {
        com.krdevops.springai.model.VersionCapability cap = buildCap("5.0");
        return new ProjectSpec(artifactId, "com.example", artifactId,
                packageName, "maven", true,
                java.nio.file.Paths.get("/tmp"), packageName.replace(".", "/"), cap);
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
