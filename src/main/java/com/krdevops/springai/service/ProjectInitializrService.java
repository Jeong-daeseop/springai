package com.krdevops.springai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * eGovFrame 프로젝트 골격 생성 서비스
 *
 * projectType : war  — XML 기반 전통 eGovFrame (web.xml + context-*.xml + Tomcat WAR 배포)
 *               boot — Spring Boot 기반 eGovFrame (application.yml + 내장 서버)
 *
 * egovVersion : 4.3     — eGovFrame 4.3.0 / Spring 5.3.x / Java 11 / javax.servlet 4.0
 *               latest  — eGovFrame 4.2.0 (LTS) / Spring Boot 3.2.x / Java 17 / Jakarta EE 10
 */
@Slf4j
@Service
public class ProjectInitializrService {

    // ── 버전 상수 ────────────────────────────────────────────────────────────
    // eGovFrame 4.3 (Spring 5.3.x / Spring Boot 2.7.x / Java 11)
    private static final String EGOV_43       = "4.3.0";
    private static final String SPRING_5      = "5.3.37";   // eGovFrame 4.3 기준
    private static final String SPRING_BOOT_2 = "2.7.18";   // eGovFrame 4.3 Boot 지원
    private static final String SPRING_SEC_5  = "5.8.13";   // eGovFrame 4.3 Security
    private static final String SPRING_BAT_4  = "4.3.10";   // eGovFrame 4.3 Batch
    private static final String MYBATIS_35      = "3.5.16";
    private static final String MYBATIS_SPRING_2 = "2.1.2"; // WAR — Spring 5.x (eGovFrame 4.3)
    private static final String MYBATIS_SB2   = "2.3.2";    // Spring Boot 2.x 전용

    // eGovFrame 5.0 (Spring 6.2.x / Spring Boot 3.5.x / Java 17)
    private static final String EGOV_50       = "5.0.0";
    private static final String SPRING_6      = "6.2.11";   // eGovFrame 5.0 기준
    private static final String SPRING_BOOT_3 = "3.5.6";    // eGovFrame 5.0 Boot 지원
    private static final String SPRING_SEC_6  = "6.5.5";    // eGovFrame 5.0 Security
    private static final String SPRING_BAT_5  = "5.2.3";    // eGovFrame 5.0 Batch
    private static final String MYBATIS_SPRING_3 = "3.0.3"; // WAR — Spring 6.x (eGovFrame 5.0)
    private static final String MYBATIS_SB3   = "3.0.3";    // Spring Boot 3.x 전용

    private static final String JAVA_11       = "11";
    private static final String JAVA_17       = "17";

    // compareVersion("latest", ...) 해석 기준 버전
    private static final String EGOV_LATEST   = EGOV_50;

    // eGovFrame Parent POM 좌표 (5.0+ 전용)
    private static final String EGOV_WAR_PARENT_GROUP    = "org.egovframe.web";
    private static final String EGOV_WAR_PARENT_ARTIFACT = "egovframe-web-config-parent";
    private static final String EGOV_BOOT_PARENT_GROUP   = "org.egovframe.boot";
    private static final String EGOV_BOOT_PARENT_ARTIFACT = "org.egovframe.boot.starter.parent";

    // ── 공개 API ─────────────────────────────────────────────────────────────

    /**
     * eGovFrame 설정 파일 템플릿 단독 반환 (추가_권장_항목 12번)
     *
     * configType : contextCommon / contextDatasource / contextTransaction /
     *              dispatcherServlet / webXml / logback / log4j2 / applicationYml
     * packageName: Java 패키지명 (contextCommon, dispatcherServlet, applicationYml 에 사용)
     *              생략 시 "egovframework.let.sample" 적용
     */
    public String getConfigTemplate(String configType, String packageName) {
        String pkg  = (packageName == null || packageName.isBlank())
                      ? "egovframework.let.sample" : packageName;
        String name = pkg.contains(".") ? pkg.substring(pkg.lastIndexOf('.') + 1) : pkg;

        return switch (configType.toLowerCase()) {
            case "contextcommon"      -> contextCommon(pkg);
            case "contextdatasource"  -> contextDatasource();
            case "contexttransaction" -> contextTransaction();
            case "dispatcherservlet"  -> dispatcherServlet(pkg, "4.3");
            case "webxml"             -> webXml(name, "5.0");  // Jakarta EE 기준
            case "logback"            -> logbackSpringXml(name);
            case "log4j2"             -> log4j2Xml(name);
            case "applicationyml"     -> bootApplicationYml(name, pkg);
            default -> "지원하지 않는 configType 입니다: " + configType + "\n"
                     + "지원 목록: contextCommon / contextDatasource / contextTransaction /\n"
                     + "          dispatcherServlet / webXml / logback / log4j2 / applicationYml";
        };
    }

    public String initializeProject(String projectName, String groupId, String artifactId,
                                    String packageName, String buildTool,
                                    String projectType, String egovVersion, String outputPath) {

        boolean isBoot = "boot".equalsIgnoreCase(projectType);
        Spec spec = new Spec(isBoot, egovVersion, groupId, artifactId, packageName, buildTool);

        List<String> created = new ArrayList<>();
        List<String> errors  = new ArrayList<>();
        String packagePath   = packageName.replace(".", "/");
        Path root            = Paths.get(outputPath, projectName);

        try {
            createDirectories(root, packagePath, isBoot, created);
            createBuildFile(root, spec, projectName, buildTool, created, errors);
            if (isBoot) {
                createBootFiles(root, spec, projectName, created, errors);
            } else {
                createWarFiles(root, spec, projectName, created, errors);
            }
            writeFile(root, ".gitignore", gitignore(buildTool), created, errors);

        } catch (Exception e) {
            errors.add("프로젝트 초기화 실패: " + e.getMessage());
            log.error("프로젝트 초기화 실패", e);
        }

        return buildResult(root.toString(), isBoot, egovVersion, buildTool, created, errors);
    }

    // ── 디렉터리 구조 ─────────────────────────────────────────────────────────
    private void createDirectories(Path root, String packagePath,
                                   boolean isBoot, List<String> created) throws IOException {
        List<String> dirs = new ArrayList<>(List.of(
            "src/main/java/" + packagePath,
            "src/main/resources/egovframework/mapper",
            "src/test/java/" + packagePath
        ));
        if (isBoot) {
            dirs.add("src/main/resources/static/css");
            dirs.add("src/main/resources/static/js");
            dirs.add("src/main/resources/templates");
        } else {
            dirs.add("src/main/resources/egovframework/spring");
            dirs.add("src/main/webapp/WEB-INF/config/egovframework/springmvc");
            dirs.add("src/main/webapp/WEB-INF/jsp/egovframework");
            dirs.add("src/main/webapp/resources/css");
            dirs.add("src/main/webapp/resources/js");
        }
        for (String dir : dirs) {
            Files.createDirectories(root.resolve(dir));
            created.add("📁 " + dir + "/");
        }
    }

    // ── 빌드 파일 ─────────────────────────────────────────────────────────────
    private void createBuildFile(Path root, Spec spec, String projectName,
                                 String buildTool, List<String> created, List<String> errors) {
        if ("gradle".equalsIgnoreCase(buildTool)) {
            writeFile(root, "build.gradle",
                spec.isBoot ? bootBuildGradle(spec) : warBuildGradle(spec), created, errors);
            writeFile(root, "settings.gradle",
                "rootProject.name = '" + spec.artifactId + "'\n", created, errors);
            writeFile(root, "gradle.properties",
                "org.gradle.jvmargs=-Xmx1024m\norg.gradle.daemon=true\n", created, errors);
        } else {
            writeFile(root, "pom.xml",
                spec.isBoot ? bootPomXml(spec, projectName) : warPomXml(spec, projectName),
                created, errors);
        }
    }

    // ── WAR 타입 파일 ─────────────────────────────────────────────────────────
    private void createWarFiles(Path root, Spec spec, String projectName,
                                List<String> created, List<String> errors) {
        writeFile(root, "src/main/resources/egovframework/spring/context-common.xml",
            contextCommon(spec.packageName), created, errors);
        writeFile(root, "src/main/resources/egovframework/spring/context-datasource.xml",
            contextDatasource(), created, errors);
        writeFile(root, "src/main/resources/egovframework/spring/context-transaction.xml",
            contextTransaction(), created, errors);
        writeFile(root, "src/main/webapp/WEB-INF/config/egovframework/springmvc/dispatcher-servlet.xml",
            dispatcherServlet(spec.packageName, spec.egovVersion), created, errors);
        writeFile(root, "src/main/webapp/WEB-INF/web.xml",
            webXml(spec.artifactId, spec.egovVersion), created, errors);
        writeFile(root, "src/main/webapp/index.jsp",
            "<%@ page contentType=\"text/html;charset=UTF-8\" %>\n"
            + "<jsp:forward page=\"/egovframework/com/main.do\"/>\n", created, errors);
        writeFile(root, "src/main/resources/log4j2.xml", log4j2Xml(projectName), created, errors);
    }

    // ── Boot 타입 파일 ────────────────────────────────────────────────────────
    private void createBootFiles(Path root, Spec spec, String projectName,
                                 List<String> created, List<String> errors) {
        writeFile(root, "src/main/resources/application.yml",
            bootApplicationYml(spec.artifactId, spec.packageName), created, errors);
        writeFile(root, "src/main/resources/logback-spring.xml",
            logbackSpringXml(projectName), created, errors);
        // @SpringBootApplication 메인 클래스
        String mainClass = bootMainClass(spec.packageName, toPascalCase(spec.artifactId));
        String mainPath  = "src/main/java/" + spec.packageName.replace(".", "/")
                         + "/" + toPascalCase(spec.artifactId) + "Application.java";
        writeFile(root, mainPath, mainClass, created, errors);
        // 테스트 클래스
        String testClass = bootTestClass(spec.packageName, toPascalCase(spec.artifactId));
        String testPath  = "src/test/java/" + spec.packageName.replace(".", "/")
                         + "/" + toPascalCase(spec.artifactId) + "ApplicationTests.java";
        writeFile(root, testPath, testClass, created, errors);
    }

    // ── 공통 유틸 ─────────────────────────────────────────────────────────────
    private void writeFile(Path root, String relativePath, String content,
                           List<String> created, List<String> errors) {
        try {
            Path target = root.resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            created.add("📄 " + relativePath);
        } catch (IOException e) {
            errors.add("❌ " + relativePath + " → " + e.getMessage());
        }
    }

    private String buildResult(String rootPath, boolean isBoot, String egovVersion,
                               String buildTool, List<String> created, List<String> errors) {
        String typeLabel    = isBoot ? "Spring Boot (내장 서버)" : "WAR (Tomcat 외부 배포)";
        String versionLabel = supportsJakarta(egovVersion)
            ? "5.0 — eGovFrame 5.0.0 / Spring Boot 3.5.6 / Spring 6.2.11 / Security 6.5.5 / Java 17"
            : "4.3 — eGovFrame 4.3.0 / Spring Boot 2.7.18 / Spring 5.3.37 / Security 5.8.13 / Java 11";
        String buildCmd     = "gradle".equalsIgnoreCase(buildTool)
                            ? (isBoot ? "./gradlew bootRun" : "./gradlew build")
                            : (isBoot ? "mvn spring-boot:run" : "mvn clean package");

        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 프로젝트 초기화 완료 ===\n\n");
        sb.append("📌 경로   : ").append(rootPath).append("\n");
        sb.append("📌 타입   : ").append(typeLabel).append("\n");
        sb.append("📌 버전   : ").append(versionLabel).append("\n");
        sb.append("📌 빌드   : ").append(buildTool).append("\n\n");

        sb.append("✅ 생성 완료 (").append(created.size()).append("개)\n");
        created.forEach(f -> sb.append("  ").append(f).append("\n"));

        if (!errors.isEmpty()) {
            sb.append("\n⚠️  오류 (").append(errors.size()).append("개)\n");
            errors.forEach(e -> sb.append("  ").append(e).append("\n"));
        }

        // Security 템플릿 권장 타입 안내
        String securityType = isBoot
            ? (supportsBoot3(egovVersion)   ? "boot-security-filter-chain" : "boot-security-adapter")
            : (supportsSpring6(egovVersion) ? "java-config-filter-chain"   : "xml-legacy");
        String egovVerLabel = supportsJakarta(egovVersion) ? "5.0" : "4.3";

        sb.append("\n📋 다음 단계\n");
        if (isBoot) {
            sb.append("  1. application.yml 의 datasource URL/계정 설정\n");
        } else {
            sb.append("  1. context-datasource.xml 의 DB 연결 정보 설정\n");
        }
        sb.append("  2. Spring Security 설정 추가 (선택)\n");
        sb.append("     → getSecurityTemplate(\"").append(securityType)
          .append("\", \"<packageName>\", \"").append(egovVerLabel).append("\")\n");
        sb.append("  3. buildFullCrudPrompt() 로 CRUD 소스 생성\n");
        sb.append("  4. ").append(buildCmd).append(" 로 빌드/실행\n");
        return sb.toString();
    }

    // =========================================================================
    // Capability Matrix — 버전별 런타임 특성을 독립 메서드로 분리
    // eGovFrame 5.1·5.2 등 이후 버전에서 특정 Capability만 변경될 경우
    // 해당 메서드만 수정하면 되며 다른 분기에는 영향 없음
    // =========================================================================

    /** 시맨틱 버전 비교 — "latest" 는 EGOV_LATEST(5.0.0)로 해석 */
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

    /** eGovFrame 5.0+ : Jakarta EE (javax → jakarta 패키지) */
    private static boolean supportsJakarta(String v) {
        return compareVersion(v, "5.0") >= 0;
    }

    /** eGovFrame 5.0+ : Spring Framework 6.x */
    private static boolean supportsSpring6(String v) {
        return compareVersion(v, "5.0") >= 0;
    }

    /** eGovFrame 5.0+ : Spring Boot 3.x */
    private static boolean supportsBoot3(String v) {
        return compareVersion(v, "5.0") >= 0;
    }

    /** eGovFrame 5.0+ : Java 17 */
    private static boolean supportsJava17(String v) {
        return compareVersion(v, "5.0") >= 0;
    }

    /** eGovFrame 5.0+ : artifactId 명명 규칙 변경 (점(.) → 하이픈(-)) */
    private static boolean supportsHyphenArtifactId(String v) {
        return compareVersion(v, "5.0") >= 0;
    }

    /** eGovFrame 5.0+ : mybatis-spring 3.x (Spring 6 완전 지원) */
    private static boolean supportsMyBatisSpring3(String v) {
        return compareVersion(v, "5.0") >= 0;
    }

    /** egovVersion 입력값("5.0", "latest", "4.3" 등) → 실제 버전 상수 */
    private static String resolveEgovVersion(String v) {
        return supportsJakarta(v) ? EGOV_50 : EGOV_43;
    }

    /** eGovFrame 5.0+: 전용 Parent POM 사용
     *  WAR  → org.egovframe.web:egovframe-web-config-parent
     *  Boot → org.egovframe.boot:org.egovframe.boot.starter.parent */
    private static boolean supportsEgovParent(String v) {
        return compareVersion(v, "5.0") >= 0;
    }

    private String toPascalCase(String artifactId) {
        StringBuilder sb = new StringBuilder();
        for (String part : artifactId.split("[-_]")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // =========================================================================
    // WAR — pom.xml / build.gradle
    // =========================================================================

    private String warPomXml(Spec s, String projectName) {
        String egovVer          = resolveEgovVersion(s.egovVersion);
        String javaVer          = supportsJava17(s.egovVersion)          ? JAVA_17          : JAVA_11;
        String springVer        = supportsSpring6(s.egovVersion)         ? SPRING_6         : SPRING_5;
        String mybatisSpringVer = supportsMyBatisSpring3(s.egovVersion)  ? MYBATIS_SPRING_3 : MYBATIS_SPRING_2;
        boolean useParent       = supportsEgovParent(s.egovVersion);

        // ── [1] Parent POM 블록 ────────────────────────────────────────────────
        // 5.0: egovframe-web-config-parent (dependencyManagement + pluginManagement 제공)
        // 4.3: Parent POM 없음 → 수동 버전 관리 유지
        String parentBlock = useParent
            ? """

    <parent>
        <groupId>%s</groupId>
        <artifactId>%s</artifactId>
        <version>%s</version>
        <relativePath/>
    </parent>
""".formatted(EGOV_WAR_PARENT_GROUP, EGOV_WAR_PARENT_ARTIFACT, egovVer)
            : "";

        // ── [2] Properties ────────────────────────────────────────────────────
        // 5.0 parent: spring.version, mybatis.version 제거 (parent BOM 관리)
        // 4.3: 전부 수동 명시
        String versionProps = useParent
            ? """
        <java.version>%s</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <egov.version>%s</egov.version>""".formatted(javaVer, egovVer)
            : """
        <java.version>%s</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <egov.version>%s</egov.version>
        <spring.version>%s</spring.version>
        <mybatis.version>%s</mybatis.version>""".formatted(javaVer, egovVer, springVer, MYBATIS_35);

        // ── [3] eGovFrame RTE 의존성 ──────────────────────────────────────────
        // 5.0 parent: <version> 제거 (parent BOM 관리)
        // 4.3: 점(.) 명명 + <version> 명시
        String egovDeps = useParent
            ? """
                <!-- eGovFrame 5.0 — version managed by egovframe-web-config-parent -->
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-ptl-mvc</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-psl-dataaccess</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-fdl-cmmn</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>egovframe-rte-fdl-security</artifactId>
                </dependency>"""
            : """
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.ptl.mvc</artifactId>
                    <version>${egov.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.psl.dataaccess</artifactId>
                    <version>${egov.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.fdl.cmmn</artifactId>
                    <version>${egov.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.egovframe.rte</groupId>
                    <artifactId>org.egovframe.rte.fdl.security</artifactId>
                    <version>${egov.version}</version>
                </dependency>""";

        // ── [4] MyBatis ───────────────────────────────────────────────────────
        // 5.0 parent: <version> 제거 (parent BOM 관리)
        // 4.3: ${mybatis.version} property 참조 유지
        String mybatisBlock = useParent
            ? """
        <!-- MyBatis — version managed by egovframe-web-config-parent -->
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis-spring</artifactId>
        </dependency>"""
            : """
        <!-- MyBatis -->
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis</artifactId>
            <version>${mybatis.version}</version>
        </dependency>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis-spring</artifactId>
            <version>%s</version>
        </dependency>""".formatted(mybatisSpringVer);

        // ── [5] Servlet / JSP ─────────────────────────────────────────────────
        // 5.0 parent: servlet-api version 제거, JSTL 구현체 version 유지 (불확실)
        // 4.3: javax + 전부 version 명시
        String servletDep = useParent
            ? """
                <!-- Servlet/JSP — servlet-api version managed by egovframe-web-config-parent -->
                <dependency>
                    <groupId>jakarta.servlet</groupId>
                    <artifactId>jakarta.servlet-api</artifactId>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>jakarta.servlet.jsp</groupId>
                    <artifactId>jakarta.servlet.jsp-api</artifactId>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>jakarta.servlet.jsp.jstl</groupId>
                    <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
                    <version>3.0.0</version>
                </dependency>
                <dependency>
                    <groupId>org.glassfish.web</groupId>
                    <artifactId>jakarta.servlet.jsp.jstl</artifactId>
                    <version>3.0.1</version>
                </dependency>"""
            : """
                <dependency>
                    <groupId>javax.servlet</groupId>
                    <artifactId>javax.servlet-api</artifactId>
                    <version>4.0.1</version>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>javax.servlet.jsp</groupId>
                    <artifactId>javax.servlet.jsp-api</artifactId>
                    <version>2.3.3</version>
                    <scope>provided</scope>
                </dependency>
                <dependency>
                    <groupId>javax.servlet</groupId>
                    <artifactId>jstl</artifactId>
                    <version>1.2</version>
                </dependency>""";

        // ── [6] Validation ────────────────────────────────────────────────────
        // 5.0 parent: jakarta.validation-api version 제거 (parent 관리)
        //             hibernate-validator, jakarta.el version 유지 (불확실)
        // 4.3: javax + 전부 version 명시
        String validationDep = useParent
            ? """
                <!-- Validation — jakarta.validation-api version managed by egovframe-web-config-parent -->
                <dependency>
                    <groupId>jakarta.validation</groupId>
                    <artifactId>jakarta.validation-api</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.hibernate.validator</groupId>
                    <artifactId>hibernate-validator</artifactId>
                    <version>8.0.1.Final</version>
                </dependency>
                <dependency>
                    <groupId>org.glassfish</groupId>
                    <artifactId>jakarta.el</artifactId>
                    <version>4.0.2</version>
                </dependency>"""
            : """
                <dependency>
                    <groupId>javax.validation</groupId>
                    <artifactId>validation-api</artifactId>
                    <version>2.0.1.Final</version>
                </dependency>
                <dependency>
                    <groupId>org.hibernate.validator</groupId>
                    <artifactId>hibernate-validator</artifactId>
                    <version>6.2.5.Final</version>
                </dependency>
                <dependency>
                    <groupId>org.glassfish</groupId>
                    <artifactId>jakarta.el</artifactId>
                    <version>3.0.4</version>
                </dependency>""";

        // ── [7] Test ──────────────────────────────────────────────────────────
        // 5.0 parent: junit-jupiter, spring-test version 제거 (Spring parent chain 관리)
        // 4.3: ${spring.version} property 참조 유지
        String testBlock = useParent
            ? """
        <!-- Test — version managed by egovframe-web-config-parent (Spring chain) -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <scope>test</scope>
        </dependency>"""
            : """
        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>""";

        // ── [8] Build plugins ─────────────────────────────────────────────────
        // 5.0 parent: 제거 (parent pluginManagement 관리)
        // 4.3: maven-compiler-plugin, maven-war-plugin 수동 명시
        String buildSection = useParent ? "" : """

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>${java.version}</release>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
            </plugin>
        </plugins>
    </build>
""";

        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
%s
    <groupId>%s</groupId>
    <artifactId>%s</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>war</packaging>
    <name>%s</name>

    <!-- ── 버전 정의 ── -->
    <properties>
%s
    </properties>

    <repositories>
        <repository>
            <id>egovframe</id>
            <url>https://maven.egovframe.go.kr/maven/</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- eGovFrame 핵심 -->
%s

%s

        <!-- DB (버전 직접 명시 — parent 관리 여부 미확인) -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>8.4.0</version>
        </dependency>
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <version>5.1.0</version>
        </dependency>

        <!-- Servlet / JSP -->
%s

        <!-- Validation -->
%s

        <!-- Lombok (버전 직접 명시 — parent 관리 여부 미확인) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.32</version>
            <scope>provided</scope>
        </dependency>

%s
    </dependencies>
%s
</project>
""".formatted(parentBlock, s.groupId, s.artifactId, projectName,
              versionProps, egovDeps, mybatisBlock,
              servletDep, validationDep, testBlock, buildSection);
    }

    private String warBuildGradle(Spec s) {
        String egovVer         = resolveEgovVersion(s.egovVersion);
        String javaVer         = supportsJava17(s.egovVersion)         ? JAVA_17          : JAVA_11;
        String springVer       = supportsSpring6(s.egovVersion)        ? SPRING_6         : SPRING_5;
        String mybatisSpringVer = supportsMyBatisSpring3(s.egovVersion) ? MYBATIS_SPRING_3 : MYBATIS_SPRING_2;
        // eGovFrame 5.0+: artifactId 명명 규칙 변경 (점(.) → 하이픈(-))
        String egovGradleDeps = supportsHyphenArtifactId(s.egovVersion)
            ? """
    // eGovFrame 5.0 (새 artifactId 명명 규칙)
    implementation "org.egovframe.rte:egovframe-rte-ptl-mvc:${egovVersion}"
    implementation "org.egovframe.rte:egovframe-rte-psl-dataaccess:${egovVersion}"
    implementation "org.egovframe.rte:egovframe-rte-fdl-cmmn:${egovVersion}" """
            : """
    // eGovFrame 4.3 (기존 artifactId 명명 규칙)
    implementation "org.egovframe.rte:org.egovframe.rte.ptl.mvc:${egovVersion}"
    implementation "org.egovframe.rte:org.egovframe.rte.psl.dataaccess:${egovVersion}"
    implementation "org.egovframe.rte:org.egovframe.rte.fdl.cmmn:${egovVersion}" """;
        String servletDeps = supportsJakarta(s.egovVersion)
            ? """
    providedCompile 'jakarta.servlet:jakarta.servlet-api:6.0.0'
    providedCompile 'jakarta.servlet.jsp:jakarta.servlet.jsp-api:3.1.1'
    implementation  'jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.0'
    implementation  'org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1'"""
            : """
    providedCompile 'javax.servlet:javax.servlet-api:4.0.1'
    providedCompile 'javax.servlet.jsp:javax.servlet.jsp-api:2.3.3'
    implementation  'javax.servlet:jstl:1.2'""";
        String validationDeps = supportsJakarta(s.egovVersion)
            ? """
    implementation 'jakarta.validation:jakarta.validation-api:3.0.2'
    implementation 'org.hibernate.validator:hibernate-validator:8.0.1.Final'
    implementation 'org.glassfish:jakarta.el:4.0.2'"""
            : """
    implementation 'javax.validation:validation-api:2.0.1.Final'
    implementation 'org.hibernate.validator:hibernate-validator:6.2.5.Final'
    implementation 'org.glassfish:jakarta.el:3.0.4'""";
        return """
plugins {
    id 'java'
    id 'war'
}

group   = '%s'
version = '1.0.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(%s)
    }
}

repositories {
    mavenCentral()
    maven { url 'https://maven.egovframe.go.kr/maven/' }
}

ext {
    egovVersion   = '%s'
    springVersion = '%s'
}

dependencies {
%s

    // MyBatis
    implementation 'org.mybatis:mybatis:%s'
    implementation 'org.mybatis:mybatis-spring:%s'

    // DB
    implementation 'com.mysql:mysql-connector-j:8.4.0'
    implementation 'com.zaxxer:HikariCP:5.1.0'

    // Servlet / JSP
%s

    // Validation
%s

    // Lombok
    compileOnly         'org.projectlombok:lombok:1.18.32'
    annotationProcessor 'org.projectlombok:lombok:1.18.32'

    // Test
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testImplementation "org.springframework:spring-test:${springVersion}"
}

tasks.named('test') { useJUnitPlatform() }
""".formatted(s.groupId, javaVer, egovVer, springVer, egovGradleDeps, MYBATIS_35, mybatisSpringVer, servletDeps, validationDeps);
    }

    // =========================================================================
    // Boot — pom.xml / build.gradle
    // =========================================================================

    private String bootPomXml(Spec s, String projectName) {
        String sbVer     = supportsBoot3(s.egovVersion)              ? SPRING_BOOT_3              : SPRING_BOOT_2;
        String javaVer   = supportsJava17(s.egovVersion)             ? JAVA_17                    : JAVA_11;
        String mbsbVer   = supportsBoot3(s.egovVersion)              ? MYBATIS_SB3                : MYBATIS_SB2;
        String egovVer   = resolveEgovVersion(s.egovVersion);
        // eGovFrame 5.0+: artifactId 명명 규칙 변경 (점(.) → 하이픈(-))
        String fdlCmmnId = supportsHyphenArtifactId(s.egovVersion)   ? "egovframe-rte-fdl-cmmn"  : "org.egovframe.rte.fdl.cmmn";

        boolean useParent = supportsEgovParent(s.egovVersion);

        // [1] parent 좌표: 5.0 → eGovFrame Boot parent, 4.3 → spring-boot-starter-parent
        String parentGroupId    = useParent ? EGOV_BOOT_PARENT_GROUP    : "org.springframework.boot";
        String parentArtifactId = useParent ? EGOV_BOOT_PARENT_ARTIFACT : "spring-boot-starter-parent";
        String parentVersion    = useParent ? egovVer                   : sbVer;

        // [2] egov.version property: 5.0 → parent BOM 관리, 4.3 → 수동 명시
        String egovVersionProp = useParent ? ""
                : "        <egov.version>" + egovVer + "</egov.version>\n";

        // [3] eGov RTE 의존성 version: 5.0 → parent BOM 관리, 4.3 → ${egov.version}
        String egovRteVersion = useParent ? ""
                : "            <version>${egov.version}</version>\n";

        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>%s</groupId>
        <artifactId>%s</artifactId>
        <version>%s</version>
        <relativePath/>
    </parent>

    <groupId>%s</groupId>
    <artifactId>%s</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>%s</name>

    <properties>
        <java.version>%s</java.version>
%s    </properties>

    <repositories>
        <repository>
            <id>egovframe</id>
            <url>https://maven.egovframe.go.kr/maven/</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MyBatis Spring Boot Starter -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>%s</version>
        </dependency>

        <!-- eGovFrame 공통 (fdl.cmmn 기반 서비스 레이어 표준) -->
        <dependency>
            <groupId>org.egovframe.rte</groupId>
            <artifactId>%s</artifactId>
%s            <exclusions>
                <exclusion>
                    <groupId>org.springframework</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <!-- DB -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter-test</artifactId>
            <version>%s</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
""".formatted(parentGroupId, parentArtifactId, parentVersion,
              s.groupId, s.artifactId, projectName,
              javaVer, egovVersionProp,
              mbsbVer,
              fdlCmmnId, egovRteVersion,
              mbsbVer);
    }

    private String bootBuildGradle(Spec s) {
        String sbVer     = supportsBoot3(s.egovVersion)              ? SPRING_BOOT_3              : SPRING_BOOT_2;
        String javaVer   = supportsJava17(s.egovVersion)             ? JAVA_17                    : JAVA_11;
        String mbsbVer   = supportsBoot3(s.egovVersion)              ? MYBATIS_SB3                : MYBATIS_SB2;
        String egovVer   = resolveEgovVersion(s.egovVersion);
        // eGovFrame 5.0+: artifactId 명명 규칙 변경 (점(.) → 하이픈(-))
        String fdlCmmnId = supportsHyphenArtifactId(s.egovVersion)   ? "egovframe-rte-fdl-cmmn"  : "org.egovframe.rte.fdl.cmmn";
        return """
plugins {
    id 'java'
    id 'org.springframework.boot' version '%s'
    id 'io.spring.dependency-management' version '1.1.5'
}

group   = '%s'
version = '1.0.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(%s)
    }
}

repositories {
    mavenCentral()
    maven { url 'https://maven.egovframe.go.kr/maven/' }
}

dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // MyBatis Spring Boot Starter
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:%s'

    // eGovFrame (fdl.cmmn 서비스 레이어 표준)
    implementation("org.egovframe.rte:%s:%s") {
        exclude group: 'org.springframework'
    }

    // DB
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Lombok
    compileOnly         'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:%s'
}

tasks.named('test') { useJUnitPlatform() }
""".formatted(sbVer, s.groupId, javaVer, mbsbVer, fdlCmmnId, egovVer, mbsbVer);
    }

    // =========================================================================
    // WAR — Spring XML 설정 파일
    // =========================================================================

    private String contextCommon(String packageName) {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           http://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan base-package="%s">
        <context:exclude-filter type="annotation"
            expression="org.springframework.stereotype.Controller"/>
    </context:component-scan>

    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
        <property name="mapperLocations" value="classpath*:egovframework/mapper/**/*.xml"/>
    </bean>

    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
        <property name="basePackage" value="%s"/>
        <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
    </bean>

</beans>
""".formatted(packageName, packageName);
    }

    private String contextDatasource() {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- TODO: 실제 DB 정보로 변경하세요 -->
    <bean id="dataSource" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">
        <property name="driverClassName" value="com.mysql.cj.jdbc.Driver"/>
        <property name="jdbcUrl"         value="jdbc:mysql://localhost:3306/com?characterEncoding=UTF-8&amp;serverTimezone=Asia/Seoul"/>
        <property name="username"        value="com"/>
        <property name="password"        value="com01"/>
        <property name="maximumPoolSize" value="10"/>
        <property name="minimumIdle"     value="2"/>
        <property name="connectionTimeout" value="30000"/>
    </bean>

</beans>
""";
    }

    private String contextTransaction() {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:tx="http://www.springframework.org/schema/tx"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/tx
           http://www.springframework.org/schema/tx/spring-tx.xsd
           http://www.springframework.org/schema/aop
           http://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="transactionManager"
          class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <tx:advice id="txAdvice" transaction-manager="transactionManager">
        <tx:attributes>
            <tx:method name="*"       rollback-for="Exception"/>
            <tx:method name="get*"    read-only="true"/>
            <tx:method name="select*" read-only="true"/>
            <tx:method name="find*"   read-only="true"/>
        </tx:attributes>
    </tx:advice>

    <aop:config>
        <aop:pointcut id="requiredTx"
            expression="execution(* egovframework.let..impl.*Impl.*(..))"/>
        <aop:advisor advice-ref="txAdvice" pointcut-ref="requiredTx"/>
    </aop:config>

</beans>
""";
    }

    private String dispatcherServlet(String packageName, String egovVersion) {
        // CommonsMultipartResolver는 Spring 6에서 제거됨 → Spring 6(eGovFrame 5.0+)은 StandardServletMultipartResolver 사용
        // StandardServletMultipartResolver는 web.xml <multipart-config> 와 함께 동작
        String multipartBean = supportsSpring6(egovVersion)
            ? """
    <!-- Spring 6+ : StandardServletMultipartResolver (CommonsMultipartResolver 제거됨)
         파일 크기 제한은 web.xml <multipart-config> 에서 설정 -->
    <bean id="multipartResolver"
          class="org.springframework.web.multipart.support.StandardServletMultipartResolver"/>"""
            : """
    <!-- Spring 5 : CommonsMultipartResolver -->
    <bean id="multipartResolver"
          class="org.springframework.web.multipart.commons.CommonsMultipartResolver">
        <property name="defaultEncoding" value="UTF-8"/>
        <property name="maxUploadSize"   value="52428800"/>
    </bean>""";

        // 4.3.x: <mvc:annotation-driven/> 이 클래스패스에서 Hibernate Validator를 자동 감지하므로
        //        LocalValidatorFactoryBean을 명시해 MethodValidationPostProcessor와 공유한다.
        // 6.2.x: Spring MVC 6.1+ 내장 메서드 검증 지원 — MethodValidationPostProcessor 불필요.
        //        LocalValidatorFactoryBean은 <mvc:annotation-driven validator="validator"/> 연동 필수.
        String validatorBean = """
    <!-- Bean Validation (JSR-303/380) — Hibernate Validator 구현체 자동 감지 -->
    <bean id="validator"
          class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean"/>""";

        String methodValidationBean = supportsSpring6(egovVersion) ? "" : """

    <!-- 메서드 레벨 파라미터 검증 활성화 (Spring 5 / eGovFrame 4.3 — AOP 기반) -->
    <bean class="org.springframework.validation.beanvalidation.MethodValidationPostProcessor">
        <property name="validator" ref="validator"/>
    </bean>""";

        return """
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:mvc="http://www.springframework.org/schema/mvc"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           http://www.springframework.org/schema/context/spring-context.xsd
           http://www.springframework.org/schema/mvc
           http://www.springframework.org/schema/mvc/spring-mvc.xsd">

    <context:component-scan base-package="%s" use-default-filters="false">
        <context:include-filter type="annotation"
            expression="org.springframework.stereotype.Controller"/>
    </context:component-scan>

%s%s

    <mvc:annotation-driven validator="validator"/>
    <mvc:resources mapping="/resources/**" location="/resources/"/>

    <bean class="org.springframework.web.servlet.view.InternalResourceViewResolver">
        <property name="prefix" value="/WEB-INF/jsp/"/>
        <property name="suffix" value=".jsp"/>
        <property name="order"  value="1"/>
    </bean>

%s

</beans>
""".formatted(packageName, validatorBean, methodValidationBean, multipartBean);
    }

    private String webXml(String artifactId, String egovVersion) {
        String ns      = supportsJakarta(egovVersion) ? "https://jakarta.ee/xml/ns/jakartaee" : "http://xmlns.jcp.org/xml/ns/javaee";
        String xsdLoc  = supportsJakarta(egovVersion)
            ? "https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
            : "http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd";
        String ver     = supportsJakarta(egovVersion) ? "6.0" : "4.0";
        // Spring 6+(eGovFrame 5.0+): StandardServletMultipartResolver 사용 → <multipart-config> 필요
        String multipartConfig = supportsSpring6(egovVersion)
            ? """

        <multipart-config>
            <!-- 파일 1개 최대 50MB, 요청 전체 최대 100MB, 임계값 초과 시 디스크 저장 -->
            <max-file-size>52428800</max-file-size>
            <max-request-size>104857600</max-request-size>
            <file-size-threshold>1048576</file-size-threshold>
        </multipart-config>"""
            : "";
        return """
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="%s"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="%s"
         version="%s">

    <display-name>%s</display-name>

    <context-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>classpath*:egovframework/spring/context-*.xml</param-value>
    </context-param>
    <listener>
        <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
    </listener>

    <filter>
        <filter-name>encodingFilter</filter-name>
        <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
        <init-param><param-name>encoding</param-name><param-value>UTF-8</param-value></init-param>
        <init-param><param-name>forceEncoding</param-name><param-value>true</param-value></init-param>
    </filter>
    <filter-mapping>
        <filter-name>encodingFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>

    <servlet>
        <servlet-name>dispatcher</servlet-name>
        <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>/WEB-INF/config/egovframework/springmvc/dispatcher-servlet.xml</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>%s
    </servlet>
    <servlet-mapping>
        <servlet-name>dispatcher</servlet-name>
        <url-pattern>*.do</url-pattern>
    </servlet-mapping>

    <welcome-file-list>
        <welcome-file>index.jsp</welcome-file>
    </welcome-file-list>

    <error-page><error-code>404</error-code><location>/WEB-INF/jsp/egovframework/error/error404.jsp</location></error-page>
    <error-page><error-code>500</error-code><location>/WEB-INF/jsp/egovframework/error/error500.jsp</location></error-page>

</web-app>
""".formatted(ns, xsdLoc, ver, artifactId, multipartConfig);
    }

    // =========================================================================
    // Boot — application.yml / main class / test class
    // =========================================================================

    private String bootApplicationYml(String artifactId, String packageName) {
        return """
spring:
  application:
    name: %s
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

  datasource:
    # 환경변수로 주입하세요 (예: export DB_URL=jdbc:mysql://host:3306/dbname)
    url: ${DB_URL:jdbc:mysql://localhost:3306/com?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul}
    username: ${DB_USERNAME:com}
    password: ${DB_PASSWORD:com01}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:10}
      minimum-idle: ${DB_POOL_MIN:2}
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

mybatis:
  mapper-locations: classpath*:egovframework/mapper/**/*.xml
  type-aliases-package: %s
  configuration:
    map-underscore-to-camel-case: true
    default-fetch-size: 100
    default-statement-timeout: 30

server:
  port: ${SERVER_PORT:8080}
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

logging:
  level:
    root: INFO
    egovframework: DEBUG
    org.mybatis: ${MYBATIS_LOG_LEVEL:DEBUG}   # 운영 배포 시 INFO로 변경

---
# ── local 프로파일 (개발 환경) ──────────────────────────────
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:mysql://localhost:3306/com?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: com
    password: com01

logging:
  level:
    egovframework: DEBUG
    org.mybatis: DEBUG

---
# ── prod 프로파일 (운영 환경) ───────────────────────────────
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:30}
      minimum-idle: ${DB_POOL_MIN:5}

logging:
  level:
    egovframework: INFO
    org.mybatis: INFO
""".formatted(artifactId, packageName);
    }

    private String bootMainClass(String packageName, String className) {
        return """
package %s;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("%s")
public class %sApplication {

    public static void main(String[] args) {
        SpringApplication.run(%sApplication.class, args);
    }
}
""".formatted(packageName, packageName, className, className);
    }

    private String bootTestClass(String packageName, String className) {
        return """
package %s;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class %sApplicationTests {

    @Test
    void contextLoads() {
    }
}
""".formatted(packageName, className);
    }

    // =========================================================================
    // 공통 로그 / gitignore
    // =========================================================================

    private String log4j2Xml(String projectName) {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%%d{yyyy-MM-dd HH:mm:ss} [%%t] %%-5level %%logger{36} - %%msg%%n"/>
        </Console>
        <RollingFile name="File"
                     fileName="logs/%s.log"
                     filePattern="logs/%s-%%d{yyyy-MM-dd}.log">
            <PatternLayout pattern="%%d{yyyy-MM-dd HH:mm:ss} [%%t] %%-5level %%logger{36} - %%msg%%n"/>
            <Policies><TimeBasedTriggeringPolicy/></Policies>
            <DefaultRolloverStrategy max="30"/>
        </RollingFile>
    </Appenders>
    <Loggers>
        <Logger name="egovframework" level="DEBUG"/>
        <Logger name="org.springframework" level="WARN"/>
        <Logger name="org.mybatis"         level="DEBUG"/>
        <Root level="INFO">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="File"/>
        </Root>
    </Loggers>
</Configuration>
""".formatted(projectName, projectName);
    }

    private String logbackSpringXml(String projectName) {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATH"    value="logs"/>
    <property name="APP_NAME"    value="%s"/>
    <property name="LOG_PATTERN" value="%%d{yyyy-MM-dd HH:mm:ss} [%%thread] %%-5level %%logger{36} - %%msg%%n"/>

    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>${LOG_PATTERN}</pattern></encoder>
    </appender>

    <appender name="File" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APP_NAME}-%%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder><pattern>${LOG_PATTERN}</pattern></encoder>
    </appender>

    <logger name="egovframework" level="DEBUG"/>
    <logger name="org.springframework" level="WARN"/>
    <logger name="org.mybatis"         level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="Console"/>
        <appender-ref ref="File"/>
    </root>
</configuration>
""".formatted(projectName);
    }

    private String gitignore(String buildTool) {
        String base = """
# IDE
.idea/
.vscode/
*.iml

# OS
.DS_Store
Thumbs.db

# Build
target/
build/
out/

# Logs
logs/
*.log

# Env
.env
""";
        return "gradle".equalsIgnoreCase(buildTool)
            ? base + ".gradle/\ngradle/wrapper/gradle-wrapper.jar\n"
            : base;
    }

    // =========================================================================
    // 내부 Spec 레코드
    // =========================================================================
    private record Spec(boolean isBoot, String egovVersion,
                        String groupId, String artifactId,
                        String packageName, String buildTool) {}
}
