# ProjectInitializrService 통합 설계서 v2.1

> v1 검토에서 발견된 6가지 결함 반영 (v2) + 추가 검토 3건 패치 (v2.1)
> 변경점은 `[v2]` / `[v2.1]` 태그로 표시

---

## 1. 현재 문제 요약

| # | 문제 | 영향 | 출처 |
|---|---|---|---|
| P1 | egovVersion이 CRUD 생성까지 전파되지 않음 | `initializeProject(4.3)` 후 CRUD에서 `jakarta.*` 혼입 | A |
| P2 | 1408줄 God Class | 책임 혼재, 테스트/확장 어려움 | B |
| P3 | Capability 4개 메서드가 동일 조건 (`v >= 5.0`) | Matrix 패턴 의도 미실현 | B |
| P4 | Java 문자열 내 XML/YAML 인라인 | IDE 지원 없음, 오타 런타임 발견 | B |
| P5 | 즉시 평가 + 절차형 writeFile | 파일 단위 에러 격리 불가 | A+B |

---

## 2. 목표 아키텍처

| 구분 | As-Is | To-Be |
|---|---|---|
| 구조 | 단일 클래스 1408줄 | 조율 ~30줄 + 11개 협력 컴포넌트 |
| Capability | static boolean 메서드 4개 (동일 조건) | `VersionCapability` record + `Resolver` (독립 임계값) |
| FilePlan | 없음 (절차형 writeFile) | `Supplier<String>` 지연 평가 + `FileKind` |
| 템플릿 | Java 문자열 인라인 | 하이브리드 (정적=외부화 / 조건부=빌더) |
| 에러 처리 | `created`/`errors` 리스트 | `GenerationReport` + 파일 단위 try/catch |
| egovVersion 전파 | `initializeProject`에서 끊김 | Tool 시그니처 + `ProjectContext` 블록 |

---

## 3. 파이프라인 흐름

```
ProjectInitializrService (orchestrator, thin ~30줄)
        │
        ▼
① ProjectSpec 조립 ──────────── VersionCapabilityResolver
        │                          (버전 문자열 → VersionCapability record)
        ▼
② FilePlan 목록 생성 ─────────── FilePlanFactory
        │                          ├─ StaticTemplateRenderer  [v2] 정적 .tpl
        │                          └─ BuildFileRenderer       [v2] 조건부 빌더
        │                          └─ Supplier<String> 지연 평가
        ▼
③ 사전 검증 ─────────────────── ProjectValidator.validatePlans()
        │
        ▼
④ FilePlan 루프 실행 ─────────── FilePlanExecutor
        │                          └─ EgovFileWriter (I/O 격리)
        ▼
⑤ 사후 검증 ─────────────────── ProjectValidator.validateResult()
        │
        ▼
⑥ 결과 빌드 ─────────────────── ResultBuilder
        │                          └─ ProjectContext 블록 포함
        ▼
⑦ 이력 저장 ─────────────────── GenerationHistoryRecorder [v2]
```

> [v2] 변경: ③사전검증 / ⑤사후검증을 별도 단계로 명시,
> TemplateRenderer를 Static/Build 두 인터페이스로 분리,
> GenerationHistoryRecorder로 명칭 변경 (기존 서비스와 혼동 방지)

---

## 4. 클래스 책임 분리

| 클래스 | 책임 | v2 변경 |
|---|---|---|
| `ProjectInitializrService` | 파이프라인 조율만 (thin) | |
| `VersionCapability` | Capability Matrix 불변 record | [v2] 버전 문자열도 룩업 |
| `VersionCapabilityResolver` | 버전 문자열 → Capability 해석 | [v2] VersionTable 도입 |
| `ProjectSpec` | 입력 + Capability + 파생 경로 (불변) | |
| `ProjectContext` | 이후 CRUD Tool에 전달할 컨텍스트 record | [v2] 축약 버전 통일 |
| `FilePlan` | path + kind + `Supplier<String>` | |
| `FilePlanFactory` | spec 기반 `List<FilePlan>` 조립 | |
| `StaticTemplateRenderer` | 정적 `.tpl` 파일 로드 + 치환 | [v2] 분리 |
| `BuildFileRenderer` | 조건부 빌더 위임 (pom, gradle, web.xml) | [v2] 분리 |
| `ClassPathTemplateLoader` | `.tpl` I/O + `${key}` 치환 | |
| `FilePlanExecutor` | FilePlan 루프 + 파일 단위 에러 격리 | [v2] preview() 추가 |
| `EgovFileWriter` | 디스크 쓰기 I/O 격리 | |
| `ProjectValidator` | 사전 Plan + 사후 파일/내용 검증 | [v2] 내용 검증 추가 |
| `ResultBuilder` | MCP 반환 텍스트 + `ProjectContext` 블록 조립 | [v2] 축약 버전 |
| `GenerationReport` | created/failed/warnings 누적 | |
| `GenerationHistoryRecorder` | 이력 저장 (Phase 별 구현) | [v2] 신규 명칭 |

---

## 5. 핵심 스켈레톤 코드

### 5.1 VersionCapability — [v2] 버전 문자열도 독립 룩업

```java
/** 버전별 런타임 특성 — 불변 스냅샷 */
public record VersionCapability(
        // ── boolean Capability ──
        boolean jakarta,           // javax → jakarta
        boolean spring6,           // Spring Framework 6.x
        boolean boot3,             // Spring Boot 3.x
        boolean java17,            // Java 17 toolchain
        boolean egovParent,        // 전용 Parent POM
        boolean hyphenArtifactId,  // 5.0+ artifactId 명명 규칙
        boolean myBatisSpring3,    // mybatis-spring 3.x
        // ── 버전 문자열 (독립 해석) ──
        String  egovVersion,       // "5.0" / "4.3" (축약형 통일)
        String  javaVersion,       // "17" / "11"
        String  springVersion,     // "6.2.11" / "5.3.37"
        String  springBootVersion, // "3.5.6" / "2.7.18"
        String  securityVersion    // "6.5.5" / "5.8.13"
) {
    /** [v2] 축약 레이블 — 사용자 표시/ProjectContext용 */
    public String label() { return egovVersion; }
}
```

### 5.2 VersionCapabilityResolver — [v2] VersionTable 룩업

```java
@Component
public class VersionCapabilityResolver {

    // ── Capability별 독립 임계값 ──
    private static final String JAKARTA_SINCE    = "5.0";
    private static final String SPRING6_SINCE    = "5.0";
    private static final String BOOT3_SINCE      = "5.0";
    private static final String JAVA17_SINCE     = "5.0";
    private static final String PARENT_SINCE     = "5.0";
    private static final String HYPHEN_ID_SINCE  = "5.0";
    private static final String MYBATIS3_SINCE   = "5.0";

    /**
     * [v2] 버전 문자열 룩업 테이블 — is50 삼항 제거
     * 5.1이 나오면 행 하나만 추가, 다른 행에 영향 없음
     */
    private record VersionTable(
        String egovVersion, String javaVersion,
        String springVersion, String springBootVersion, String securityVersion
    ) {}

    private static final VersionTable V50 = new VersionTable("5.0", "17", "6.2.11", "3.5.6", "6.5.5");
    private static final VersionTable V43 = new VersionTable("4.3", "11", "5.3.37", "2.7.18", "5.8.13");
    // [v2] 미래: private static final VersionTable V51 = new VersionTable("5.1", "21", "6.3.x", ...);

    public VersionCapability resolve(String egovVersion) {
        VersionTable t = gte(egovVersion, "5.0") ? V50 : V43;
        // [v2] 미래: gte("5.1") → V51, gte("5.0") → V50, else → V43

        return new VersionCapability(
            gte(egovVersion, JAKARTA_SINCE),
            gte(egovVersion, SPRING6_SINCE),
            gte(egovVersion, BOOT3_SINCE),
            gte(egovVersion, JAVA17_SINCE),
            gte(egovVersion, PARENT_SINCE),
            gte(egovVersion, HYPHEN_ID_SINCE),
            gte(egovVersion, MYBATIS3_SINCE),
            t.egovVersion(), t.javaVersion(), t.springVersion(),
            t.springBootVersion(), t.securityVersion()
        );
    }

    /** 시맨틱 버전 비교 — "latest"/"5.0" → 5.0.0 해석 */
    private static boolean gte(String version, String threshold) {
        String v = (version == null || version.isBlank()) ? "5.0.0"
                 : "latest".equalsIgnoreCase(version) ? "5.0.0"
                 : version;
        String[] vp = v.split("\\.");
        String[] tp = threshold.split("\\.");
        int len = Math.max(vp.length, tp.length);
        for (int i = 0; i < len; i++) {
            int vn = i < vp.length ? seg(vp[i]) : 0;
            int tn = i < tp.length ? seg(tp[i]) : 0;
            if (vn != tn) return vn > tn;
        }
        return true;
    }

    private static int seg(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
```

> **[v2] 결함 #3 수정**: `is50 ? A : B` 삼항을 `VersionTable` 레코드 배열로 대체.
> boolean Capability와 버전 문자열이 모두 독립적으로 확장 가능.
> 5.1 추가 시: VersionTable 행 1개 + 임계값 상수 변경(필요 시) → 끝.

### 5.3 ProjectSpec (A+B 병합)

```java
public record ProjectSpec(
        String projectName, String groupId, String artifactId,
        String packageName, String buildTool, boolean boot,
        Path root, String packagePath,
        VersionCapability cap
) {
    public static ProjectSpec of(String projectName, String groupId, String artifactId,
                                 String packageName, String buildTool, String projectType,
                                 String outputPath, VersionCapability cap) {
        boolean boot = "boot".equalsIgnoreCase(projectType);
        return new ProjectSpec(
            projectName, groupId, artifactId, packageName, buildTool, boot,
            Paths.get(outputPath, projectName),
            packageName.replace(".", "/"), cap);
    }

    public boolean gradle()      { return "gradle".equalsIgnoreCase(buildTool); }
    public String egovVersion()  { return cap.egovVersion(); }  // "4.3" / "5.0" 축약
    public String egovLabel()    { return cap.label(); }        // [v2] 표시용
}
```

### 5.4 ProjectContext — [v2] 축약 버전 통일 (결함 #1 수정)

```java
/**
 * initializeProject 결과를 이후 Tool 호출에서 재사용하기 위한 컨텍스트.
 *
 * [v2] egovVersion은 축약형("4.3"/"5.0")으로 통일.
 * 이유: buildFullCrudPrompt()가 받는 값, ProjectContext 블록 값,
 * ResultBuilder 안내문 값이 모두 동일해야 복붙 시 혼동이 없다.
 * VersionCapability.egovVersion()이 이미 축약형을 반환하므로 변환 불필요.
 */
public record ProjectContext(
        String projectName,
        String rootPath,
        String packageName,
        String projectType,     // "war" / "boot"
        String buildTool,       // "maven" / "gradle"
        String egovVersion      // "4.3" / "5.0" (축약형 통일)
) {
    public static ProjectContext from(ProjectSpec s) {
        return new ProjectContext(
            s.projectName(), s.root().toString(), s.packageName(),
            s.boot() ? "boot" : "war", s.buildTool(), s.egovVersion());
    }

    /** buildResult()에 포함할 구조화 블록 */
    public String toBlock() {
        return """
            [PROJECT_CONTEXT]
            projectName=%s
            rootPath=%s
            packageName=%s
            projectType=%s
            buildTool=%s
            egovVersion=%s
            [/PROJECT_CONTEXT]""".formatted(
                projectName, rootPath, packageName, projectType, buildTool, egovVersion);
    }
}
```

> **[v2] 결함 #1 수정**: egovVersion 레이블을 전 영역에서 축약형으로 통일.
> `VersionTable.egovVersion` = "5.0", `ProjectContext.egovVersion` = "5.0",
> `ResultBuilder` 안내문 = "5.0", `buildFullCrudPrompt(egovVersion="5.0")` — 일관.

### 5.5 FilePlan (변경 없음)

```java
/** 생성할 파일 1개. content는 지연 평가 → 렌더 실패를 파일 단위로 격리 */
public record FilePlan(
        String relativePath,
        FileKind kind,
        Supplier<String> content
) {
    public enum FileKind { BUILD, CONFIG, SOURCE, RESOURCE, WEB, TEST, META }

    public static FilePlan of(String path, FileKind kind, Supplier<String> content) {
        return new FilePlan(path, kind, content);
    }
}
```

> **Supplier 채택 근거 [v2 수정]**
> 주요 이점: 파일 단위 에러 격리 (Executor try/catch).
> 부가 이점: dry-run(preview) 확장 여지 — 단, 현재는 미구현이며 Phase 3 이후 선택 사항.
> (v1에서 dry-run을 주요 근거처럼 강조했으나, 실제 노출 경로가 없어 부가로 격하)

### 5.6 TemplateRenderer — [v2] 두 인터페이스로 분리 (결함 #5 수정)

```java
/**
 * [v2] v1의 단일 TemplateRenderer(16메서드)를 역할별로 분리.
 * 이유: 정적 템플릿(치환만)과 조건부 빌더(8블록 조합)는 구현 방식이 다르다.
 * FilePlanFactory는 두 렌더러를 모두 주입받아 FilePlan을 조립한다.
 */

// ── 정적 템플릿: .tpl 로드 + ${key} 치환 ──
public interface StaticTemplateRenderer {
    String contextCommon(ProjectSpec s);
    String contextDatasource(ProjectSpec s);
    String contextTransaction(ProjectSpec s);
    String applicationYml(ProjectSpec s);
    String logback(ProjectSpec s);
    String log4j2(ProjectSpec s);
    String gitignore(ProjectSpec s);
    String indexJsp(ProjectSpec s);
    String bootMain(ProjectSpec s);
    String bootTest(ProjectSpec s);
}

// ── 조건부 빌더: Java 코드로 블록 조합 ──
public interface BuildFileRenderer {
    String warPom(ProjectSpec s);
    String bootPom(ProjectSpec s);
    String warBuildGradle(ProjectSpec s);
    String bootBuildGradle(ProjectSpec s);
    String dispatcherServlet(ProjectSpec s);
    String webXml(ProjectSpec s);
}
```

### 5.6.1 DefaultStaticTemplateRenderer

```java
@Component
@RequiredArgsConstructor
public class DefaultStaticTemplateRenderer implements StaticTemplateRenderer {

    private final ClassPathTemplateLoader loader;

    @Override
    public String contextCommon(ProjectSpec s) {
        return loader.render("context-common.xml", Map.of(
            "packageName", s.packageName(),
            "namespace", s.cap().jakarta() ? "jakarta" : "javax"
        ));
    }

    @Override
    public String applicationYml(ProjectSpec s) {
        return loader.render("application.yml", Map.of(
            "artifactId", s.artifactId(),
            "egovVersion", s.egovVersion()
        ));
    }

    // ... 나머지 8개 메서드 동일 패턴
}
```

### 5.6.2 DefaultBuildFileRenderer (빌더 위임)

```java
@Component
@RequiredArgsConstructor
public class DefaultBuildFileRenderer implements BuildFileRenderer {

    private final WarPomBuilder warPomBuilder;
    private final BootPomBuilder bootPomBuilder;
    private final WarBuildGradleBuilder warGradleBuilder;
    private final BootBuildGradleBuilder bootGradleBuilder;
    private final DispatcherServletBuilder dispatcherBuilder;
    private final WebXmlBuilder webXmlBuilder;

    @Override public String warPom(ProjectSpec s)           { return warPomBuilder.build(s); }
    @Override public String bootPom(ProjectSpec s)          { return bootPomBuilder.build(s); }
    @Override public String warBuildGradle(ProjectSpec s)    { return warGradleBuilder.build(s); }
    @Override public String bootBuildGradle(ProjectSpec s)   { return bootGradleBuilder.build(s); }
    @Override public String dispatcherServlet(ProjectSpec s) { return dispatcherBuilder.build(s); }
    @Override public String webXml(ProjectSpec s)            { return webXmlBuilder.build(s); }
}
```

### 5.6.3 ClassPathTemplateLoader

```java
@Component
public class ClassPathTemplateLoader {

    /** resources/templates/egov/{name}.tpl 로드 후 ${key} 치환 */
    public String render(String name, Map<String, String> vars) {
        String tpl = read("templates/egov/" + name + ".tpl");
        for (var e : vars.entrySet())
            tpl = tpl.replace("${" + e.getKey() + "}", e.getValue());
        return tpl;
    }

    private String read(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("템플릿 로드 실패: " + path, e);
        }
    }
}
```

### 5.7 FilePlanFactory — [v2] 두 렌더러 주입

```java
@Component
@RequiredArgsConstructor
public class FilePlanFactory {

    private final StaticTemplateRenderer stpl;   // [v2] 정적
    private final BuildFileRenderer      bld;    // [v2] 조건부

    public List<FilePlan> plan(ProjectSpec s) {
        List<FilePlan> plans = new ArrayList<>();
        plans.addAll(buildFilePlans(s));
        plans.addAll(s.boot() ? bootFiles(s) : warFiles(s));
        plans.add(FilePlan.of(".gitignore", META, () -> stpl.gitignore(s)));
        return plans;
    }

    private List<FilePlan> buildFilePlans(ProjectSpec s) {
        if (s.gradle()) {
            return List.of(
                FilePlan.of("build.gradle", BUILD,
                    () -> s.boot() ? bld.bootBuildGradle(s) : bld.warBuildGradle(s)),
                FilePlan.of("settings.gradle", BUILD,
                    () -> "rootProject.name = '" + s.artifactId() + "'\n"),
                FilePlan.of("gradle.properties", BUILD,
                    () -> "org.gradle.jvmargs=-Xmx1024m\norg.gradle.daemon=true\n")
            );
        }
        return List.of(
            FilePlan.of("pom.xml", BUILD,
                () -> s.boot() ? bld.bootPom(s) : bld.warPom(s))
        );
    }

    private List<FilePlan> warFiles(ProjectSpec s) {
        return List.of(
            FilePlan.of("src/main/resources/egovframework/spring/context-common.xml",
                        CONFIG, () -> stpl.contextCommon(s)),
            FilePlan.of("src/main/resources/egovframework/spring/context-datasource.xml",
                        CONFIG, () -> stpl.contextDatasource(s)),
            FilePlan.of("src/main/resources/egovframework/spring/context-transaction.xml",
                        CONFIG, () -> stpl.contextTransaction(s)),
            FilePlan.of("src/main/webapp/WEB-INF/config/egovframework/springmvc/dispatcher-servlet.xml",
                        CONFIG, () -> bld.dispatcherServlet(s)),
            FilePlan.of("src/main/webapp/WEB-INF/web.xml",
                        WEB, () -> bld.webXml(s)),
            FilePlan.of("src/main/webapp/index.jsp",
                        WEB, () -> stpl.indexJsp(s)),
            FilePlan.of("src/main/resources/log4j2.xml",
                        RESOURCE, () -> stpl.log4j2(s))
        );
    }

    private List<FilePlan> bootFiles(ProjectSpec s) {
        String cls  = toPascal(s.artifactId());
        String base = "src/main/java/" + s.packagePath();
        String test = "src/test/java/" + s.packagePath();
        return List.of(
            FilePlan.of("src/main/resources/application.yml",
                        RESOURCE, () -> stpl.applicationYml(s)),
            FilePlan.of("src/main/resources/logback-spring.xml",
                        RESOURCE, () -> stpl.logback(s)),
            FilePlan.of(base + "/" + cls + "Application.java",
                        SOURCE, () -> stpl.bootMain(s)),
            FilePlan.of(test + "/" + cls + "ApplicationTests.java",
                        TEST, () -> stpl.bootTest(s))
        );
    }

    private static String toPascal(String id) {
        StringBuilder sb = new StringBuilder();
        for (String p : id.split("[-_]"))
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return sb.toString();
    }
}
```

### 5.8 FilePlanExecutor — [v2] preview() 추가 (결함 #6 수정)

```java
@Component
@RequiredArgsConstructor
public class FilePlanExecutor {

    private final EgovFileWriter writer;

    /** 실행: 디스크에 쓰기 */
    public GenerationReport execute(ProjectSpec s, List<FilePlan> plans) {
        GenerationReport report = new GenerationReport(s.root().toString());
        for (FilePlan p : plans) {
            try {
                writer.write(s.root(), p.relativePath(), p.content().get());
                report.added(p);
            } catch (Exception e) {
                report.failed(p, e.getMessage());
            }
        }
        return report;
    }

    /**
     * [v2] 프리뷰: 디스크 쓰기 없이 Supplier만 호출하여 렌더링 검증.
     * 용도: 테스트 / MCP Tool에서 dryRun=true 파라미터 지원 시 활용.
     * Phase 3 이후 선택적 노출 — 현재는 내부 테스트 전용.
     */
    public GenerationReport preview(ProjectSpec s, List<FilePlan> plans) {
        GenerationReport report = new GenerationReport(s.root().toString());
        for (FilePlan p : plans) {
            try {
                p.content().get();  // 렌더링만, 쓰기 안 함
                report.added(p);
            } catch (Exception e) {
                report.failed(p, e.getMessage());
            }
        }
        return report;
    }
}
```

### 5.9 EgovFileWriter + GenerationReport — [v2.1] 경로 정규화 추가

```java
@Component
public class EgovFileWriter {

    /**
     * [v2.1] 경로 정규화 + 탈출 방지 추가.
     * validatePlans()의 ".." 체크만으로는 절대경로("/etc/passwd")나
     * symlink 우회를 막을 수 없으므로, normalize() + startsWith()로 이중 방어.
     */
    public void write(Path root, String relativePath, String content) throws IOException {
        Path base   = root.toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("허용 범위 밖 경로: " + relativePath);
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
```

```java
public class GenerationReport {
    private final String rootPath;
    private final List<String> created  = new ArrayList<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public GenerationReport(String rootPath) { this.rootPath = rootPath; }

    public void added(FilePlan p)            { created.add(p.relativePath()); }
    public void failed(FilePlan p, String m) { errors.put(p.relativePath(), m); }
    public void warn(String msg)             { warnings.add(msg); }

    public String rootPath()            { return rootPath; }
    public List<String> created()       { return List.copyOf(created); }
    public Map<String,String> errors()  { return Map.copyOf(errors); }
    public List<String> warnings()      { return List.copyOf(warnings); }
    public boolean hasErrors()          { return !errors.isEmpty(); }
    public int totalFiles()             { return created.size(); }
}
```

### 5.10 ProjectValidator — [v2] 내용 검증 추가 (결함 #2 수정)

```java
@Component
public class ProjectValidator {

    /** 사전 검증: FilePlan 실행 전 */
    public void validatePlans(List<FilePlan> plans) {
        Set<String> paths = new HashSet<>();
        for (FilePlan p : plans) {
            if (p.relativePath() == null || p.relativePath().isBlank())
                throw new IllegalArgumentException("FilePlan relativePath가 비어 있습니다.");
            if (p.relativePath().contains(".."))
                throw new IllegalArgumentException("상위 경로 이동 불가: " + p.relativePath());
            if (!paths.add(p.relativePath()))
                throw new IllegalArgumentException("중복 FilePlan 경로: " + p.relativePath());
        }
    }

    /**
     * [v2] 사후 검증: 파일 존재 + 내용 정합성
     * §9.2 테스트 항목과 1:1 대응하도록 확장
     */
    public void validateResult(ProjectSpec s, GenerationReport report) {
        // ── 1. 필수 파일 존재 검증 ──
        List<String> required = s.boot()
            ? List.of("src/main/resources/application.yml")
            : List.of(
                "src/main/resources/egovframework/spring/context-common.xml",
                "src/main/webapp/WEB-INF/web.xml");

        for (String path : required) {
            if (!Files.exists(s.root().resolve(path))) {
                report.warn("필수 파일 누락: " + path);
            }
        }

        // ── 2. [v2] 내용 검증: namespace 일관성 ──
        validateNamespace(s, report);

        // ── 3. [v2] 내용 검증: Java 버전 ──
        validateJavaVersion(s, report);
    }

    /**
     * [v2] javax/jakarta namespace가 Capability와 일치하는지 검증.
     * §9.2 "javax.* / jakarta.* namespace" 항목 대응.
     */
    private void validateNamespace(ProjectSpec s, GenerationReport report) {
        String expected = s.cap().jakarta() ? "jakarta" : "javax";
        String unexpected = s.cap().jakarta() ? "javax." : "jakarta.";

        // pom.xml 또는 build.gradle 검사
        Path buildFile = s.gradle()
            ? s.root().resolve("build.gradle")
            : s.root().resolve("pom.xml");

        if (Files.exists(buildFile)) {
            try {
                String content = Files.readString(buildFile);
                // servlet-api 의존성의 namespace 확인
                if (content.contains(unexpected + "servlet")) {
                    report.warn("빌드 파일에 " + unexpected + " namespace 혼입: " + buildFile.getFileName());
                }
            } catch (IOException e) {
                report.warn("빌드 파일 읽기 실패: " + e.getMessage());
            }
        }
    }

    /**
     * [v2] pom.xml/build.gradle의 Java 버전이 Capability와 일치하는지 검증.
     * §9.2 "pom.xml / build.gradle Java 버전" 항목 대응.
     */
    private void validateJavaVersion(ProjectSpec s, GenerationReport report) {
        String expected = s.cap().javaVersion(); // "17" / "11"
        Path pom = s.root().resolve("pom.xml");
        if (Files.exists(pom)) {
            try {
                String content = Files.readString(pom);
                if (!content.contains("<java.version>" + expected + "</java.version>")
                    && !content.contains("<maven.compiler.source>" + expected)) {
                    report.warn("pom.xml Java 버전이 " + expected + "이 아닙니다.");
                }
            } catch (IOException e) {
                report.warn("pom.xml 읽기 실패: " + e.getMessage());
            }
        }
    }
}
```

> **[v2] 결함 #2 수정**: v1은 파일 존재만 확인했으나,
> v2는 §9.2 테스트 항목(namespace, Java 버전)을 Validator에서도 런타임 검증.
> 테스트 계획과 Validator 구현의 갭 해소.

### 5.11 ResultBuilder — [v2] 축약 버전 일관 적용

```java
@Component
public class ResultBuilder {

    public String build(ProjectSpec s, GenerationReport report) {
        ProjectContext ctx = ProjectContext.from(s);
        String typeLabel = s.boot() ? "Spring Boot (내장 서버)" : "WAR (Tomcat 외부 배포)";
        String buildCmd  = s.gradle()
            ? (s.boot() ? "./gradlew bootRun" : "./gradlew build")
            : (s.boot() ? "mvn spring-boot:run" : "mvn clean package");

        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 프로젝트 초기화 완료 ===\n\n");
        sb.append("📌 경로   : ").append(report.rootPath()).append("\n");
        sb.append("📌 타입   : ").append(typeLabel).append("\n");
        sb.append("📌 버전   : eGovFrame ").append(s.egovLabel()).append("\n");   // [v2] 축약
        sb.append("📌 빌드   : ").append(s.buildTool()).append("\n\n");

        sb.append("✅ 생성 완료 (").append(report.totalFiles()).append("개)\n");
        report.created().forEach(f -> sb.append("  📄 ").append(f).append("\n"));

        if (report.hasErrors()) {
            sb.append("\n⚠️  오류 (").append(report.errors().size()).append("개)\n");
            report.errors().forEach((f, m) ->
                sb.append("  ❌ ").append(f).append(" → ").append(m).append("\n"));
        }

        if (!report.warnings().isEmpty()) {
            sb.append("\n⚠️  경고\n");
            report.warnings().forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
        }

        // [v2] 안내문의 egovVersion도 축약형 사용 — ProjectContext와 동일
        sb.append("\n📋 다음 단계\n");
        sb.append("  1. ").append(s.boot() ? "application.yml" : "context-datasource.xml")
          .append(" DB 정보 설정\n");
        sb.append("  2. Spring Security 설정 추가 (선택)\n");
        sb.append("     → getSecurityTemplate(..., \"").append(s.egovLabel()).append("\")\n");
        sb.append("  3. buildFullCrudPrompt(..., egovVersion=\"")
          .append(s.egovLabel()).append("\") 로 CRUD 생성\n");
        sb.append("  4. ").append(buildCmd).append(" 로 빌드/실행\n");

        sb.append("\n").append(ctx.toBlock()).append("\n");
        return sb.toString();
    }
}
```

### 5.12 GenerationHistoryRecorder — [v2] 단계별 구현 (결함 #4 수정)

```java
/**
 * [v2] 기존 GenerationHistoryService와 시그니처 충돌을 피하기 위해 별도 클래스.
 *
 * Phase 2: 로그 전용 (SLF4J)
 * Phase 3+: DB 저장 (COMTN_GENERATION_HISTORY 테이블) — 선택
 */
@Component
@RequiredArgsConstructor
public class GenerationHistoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(GenerationHistoryRecorder.class);

    // Phase 3+: private final GenerationHistoryMapper mapper;

    public void record(ProjectSpec s, GenerationReport report) {
        // ── Phase 2: 로그만 ──
        log.info("[initializeProject] {} | {} | eGov {} | files={} errors={}",
            s.projectName(),
            s.boot() ? "boot" : "war",
            s.egovVersion(),
            report.totalFiles(),
            report.errors().size());

        // ── Phase 3+: DB 저장 (주석 해제) ──
        // mapper.insertHistory(GenerationHistoryVO.from(s, report));
    }
}
```

> **[v2] 결함 #4 수정**: 기존 `GenerationHistoryService.save()`와 시그니처 충돌 방지.
> `GenerationHistoryRecorder`로 이름을 바꾸고, Phase 2에서는 로그만 남긴다.
> Phase 3+ 에서 DB 저장이 필요하면 `GenerationHistoryMapper` 주입 후 주석 해제.

### 5.13 ProjectInitializrService — 최종 조율

```java
@Service
@RequiredArgsConstructor
public class ProjectInitializrService {

    private final VersionCapabilityResolver resolver;
    private final FilePlanFactory factory;
    private final FilePlanExecutor executor;
    private final ProjectValidator validator;
    private final ResultBuilder resultBuilder;
    private final GenerationHistoryRecorder recorder;   // [v2] 명칭 변경

    public String initializeProject(String projectName, String groupId, String artifactId,
                                    String packageName, String buildTool,
                                    String projectType, String egovVersion, String outputPath) {

        // ① Capability 해석 + Spec 조립
        VersionCapability cap = resolver.resolve(egovVersion);
        ProjectSpec spec = ProjectSpec.of(projectName, groupId, artifactId,
                packageName, buildTool, projectType, outputPath, cap);

        // ② FilePlan 목록 생성 (Supplier 지연 — 렌더링 안 함)
        List<FilePlan> plans = factory.plan(spec);

        // ③ 사전 검증 (중복 경로, null, 경로 탈출)
        validator.validatePlans(plans);

        // ④ FilePlan 루프 실행 (파일 단위 에러 격리)
        GenerationReport report = executor.execute(spec, plans);

        // ⑤ 사후 검증 (필수 파일 + namespace + Java 버전)
        validator.validateResult(spec, report);

        // ⑥ 이력 기록
        recorder.record(spec, report);

        // ⑦ 결과 빌드 (ProjectContext 블록 포함)
        return resultBuilder.build(spec, report);
    }
}
```

---

## 6. egovVersion 전파 설계 (A 기반)

### 6.1 현재 문제

```
initializeProject(egovVersion="4.3")   ← egovVersion 있음
        ↓
buildFullCrudPrompt(...)               ← egovVersion 파라미터 없음!
        ↓
PlaceholderValues.validationImport     ← 기본값 5.0 → jakarta.validation.* 혼입
```

### 6.2 CrudPromptBuilderTool 시그니처 확장 — [v2.1] MCP 호환성 고려

```java
/**
 * [v2.1] MCP Tool 하위 호환성 주의사항:
 *
 * 기존 MCP 클라이언트(Claude Desktop)가 6개 인자 기준으로 호출하고 있으므로,
 * egovVersion을 required로 노출하면 기존 호출이 깨진다.
 *
 * 방법 1 (권장): @Nullable + Tool description에 기본값 명시
 *   → Spring AI @Tool schema에서 optional로 처리 가능한지 확인 필요
 *   → description: "egovVersion (선택, 기본값 5.0)"
 *
 * 방법 2 (안전): 기존 메서드 유지 + 새 Tool 추가
 *   → buildFullCrudPrompt(6개) 그대로 유지
 *   → buildFullCrudPromptV2(7개) 신규 등록
 *   → 기존 Tool은 deprecated 표시 후 점진 전환
 *
 * 현재 채택: 방법 1 시도 → 실패 시 방법 2 전환
 */
public String buildFullCrudPrompt(String database, String tableName,
                                  String domain, String packageName,
                                  String outputPath, String llmProvider,
                                  @Nullable String egovVersion)   // [v2.1] optional
```

기본값 처리:

```java
String resolved = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
```

> [v2.1] `@Nullable` 적용 후 MCP schema 노출 테스트 필수.
> Claude Desktop에서 6개 인자로 호출 시 정상 작동하는지 확인.

### 6.3 orchestrateAuto 확장

```java
private String orchestrateAuto(..., String egovVersion) {
    PlaceholderValues pv = service.buildPlaceholderValues(
        database, tableName, domain, packageName, outputPath, egovVersion);
}
```

### 6.4 ProjectContext 2차 확장 후보

- `ProjectContextService` 추가 (최근 생성 프로젝트 컨텍스트 저장)
- `buildFullCrudPrompt()`에서 `egovVersion` 미입력 시 최근 컨텍스트 자동 참조
- 이력 테이블에 프로젝트 초기화 이력 영구 저장

---

## 7. 템플릿 처리 전략

### 7.1 비교

| 항목 | 외부화 (.xml.tpl) | Java 문자열 유지 |
|---|---|---|
| IDE 지원 (XML 자동완성) | ✅ 완전 | ❌ 없음 |
| Git diff 가독성 | ✅ 우수 | ⚠️ formatted 노이즈 |
| 조건부 조립 (pom parentBlock 등) | ❌ 엔진 필요 | ✅ 자연스러움 |
| 의존성 추가 | 치환 유틸 | 없음 |
| 런타임 비용 | ClassPathResource I/O | 없음 |

### 7.2 채택: 하이브리드

```
정적 파일 → 외부화 (StaticTemplateRenderer)
  context-common.xml.tpl
  context-datasource.xml.tpl
  context-transaction.xml.tpl
  logback-spring.xml.tpl
  log4j2.xml.tpl
  application.yml.tpl
  gitignore.tpl
  index.jsp.tpl
  BootApplication.java.tpl
  BootApplicationTests.java.tpl

조건부 조립 파일 → Java 빌더 (BuildFileRenderer)     [v2] 인터페이스 분리
  WarPomBuilder
  BootPomBuilder
  WarBuildGradleBuilder
  BootBuildGradleBuilder
  DispatcherServletBuilder
  WebXmlBuilder
```

---

## 8. 통합 마이그레이션 순서

> 원칙: 실제 버그 해결 먼저, 구조 개선은 점진적으로

### Phase 1: egovVersion 전파 (즉시 효과, 리스크 낮음)

| # | 작업 | 리스크 |
|---|---|---|
| 1 | `CrudPromptBuilderTool.buildFullCrudPrompt()`에 `egovVersion` 추가 | 낮음 |
| 2 | `claude` 모드에서 `egovVersion` 전달 | 낮음 |
| 3 | `orchestrateAuto()`에도 `egovVersion` 전달 | 낮음 |
| 4 | `buildResult()`에 CRUD 호출 시 `egovVersion` 안내 추가 | 낮음 |

> 여기까지만 해도 P1(javax/jakarta 불일치) 해결

### Phase 2: 값 객체 도입 (리팩터링 기반)

| # | 작업 | 리스크 |
|---|---|---|
| 5 | `VersionCapability` record + `VersionCapabilityResolver` 추출 | 낮음 |
| 6 | [v2] `VersionTable` 룩업 적용 (is50 삼항 제거) | 낮음 |
| 7 | `ProjectSpec` record 도입 | 낮음 |
| 8 | `ProjectContext` record + `buildResult()`에 블록 출력 | 낮음 |
| 9 | [v2] `GenerationHistoryRecorder` 신규 (로그 전용) | 낮음 |

### Phase 3: FilePlan 파이프라인 (핵심 구조 변경)

| # | 작업 | 리스크 |
|---|---|---|
| 10 | `FilePlan` + `FilePlanFactory` 도입 | 중간 |
| 11 | `FilePlanExecutor` + `EgovFileWriter` 분리 | 중간 |
| 12 | `ProjectValidator` (사전+사후) + `GenerationReport` | 중간 |
| 13 | `ResultBuilder` 분리 | 낮음 |

### Phase 4: 템플릿 외부화 (마무리)

| # | 작업 | 리스크 |
|---|---|---|
| 14 | [v2] `StaticTemplateRenderer` + `BuildFileRenderer` 인터페이스 분리 | 중간 |
| 15 | 정적 파일 `.tpl` 외부화 + `ClassPathTemplateLoader` | 중간 |
| 16 | 조건부 빌더 분리 (`WarPomBuilder` 등 6개) | 중간 |

### Phase 외 (명시적 미구현)

| 항목 | 상태 | 비고 |
|---|---|---|
| [v2] dry-run MCP Tool 노출 | 미구현 | `preview()` 메서드는 존재, Tool 파라미터 추가는 선택 |
| [v2] `GenerationHistoryRecorder` DB 저장 | 미구현 | Phase 3+에서 선택 |
| `ProjectContextService` (자동 컨텍스트) | 미구현 | §6.4 2차 확장 후보 |

> 각 Phase 완료 시 8가지 조합 회귀 테스트 실행

---

## 9. 테스트 계획

### 9.1 필수 조합 (8가지)

```
war  + 4.3    + maven
war  + 4.3    + gradle
war  + latest + maven
war  + latest + gradle
boot + 4.3    + maven
boot + 4.3    + gradle
boot + latest + maven
boot + latest + gradle
```

### 9.2 검증 항목 — [v2] Validator 대응 표시, [v2.1] 파일 개수 수정

> [v2.1] 파일 개수 기대값 (FilePlanFactory 코드 기준)
>
> | 조합 | 산출 | 합계 |
> |---|---|---|
> | WAR + maven | pom.xml(1) + WAR config(7) + .gitignore(1) | **9** |
> | WAR + gradle | build.gradle/settings/properties(3) + WAR config(7) + .gitignore(1) | **11** |
> | Boot + maven | pom.xml(1) + Boot files(4) + .gitignore(1) | **6** |
> | Boot + gradle | build.gradle/settings/properties(3) + Boot files(4) + .gitignore(1) | **8** |

| 검증 | 기대값 | Validator 대응 |
|---|---|---|
| 생성 파일 개수 | [v2.1] WAR+maven=9, WAR+gradle=11, Boot+maven=6, Boot+gradle=8 | `report.totalFiles()` |
| pom.xml / build.gradle Java 버전 | 4.3→11, 5.0→17 | [v2] `validateJavaVersion()` |
| Spring / Boot / MyBatis 버전 | Capability에 따라 | 테스트 전용 |
| javax.* / jakarta.* namespace | 4.3→javax, 5.0→jakarta | [v2] `validateNamespace()` |
| web.xml schema version | 4.3→4.0, 5.0→6.0 | 테스트 전용 |
| dispatcher-servlet multipart | 4.3→Commons, 5.0→Standard | 테스트 전용 |
| buildResult 다음 단계 egovVersion | Spec과 일치 (축약형) | [v2] 축약 통일 |
| PROJECT_CONTEXT 블록 | egovVersion 포함 (축약형) | [v2] 축약 통일 |
| CRUD egovVersion 전파 | validation import 분기 일치 | Phase 1 |

### 9.3 회귀 테스트 방법

```text
1. 기존 코드로 8가지 조합 실행 → 파일 목록 + 내용 스냅샷 저장
2. 리팩터링 후 동일 조합 실행 → diff 비교
3. 차이가 있으면 의도적 변경인지 확인
4. [v2] preview() 활용: 디스크 쓰기 없이 렌더링 오류 사전 검출
```

---

## 10. 설계 결정 기록 (ADR)

| # | 결정 | 근거 | v2 변경 |
|---|---|---|---|
| D1 | Capability Matrix 패턴 유지 | 미래 5.1/5.2 확장 대비 | |
| D2 | FilePlan에 `Supplier<String>` 채택 | **파일 단위 에러 격리** (주), 향후 preview 확장 (부) | [v2] dry-run 근거 격하 |
| D3 | DirectoryPlan 별도 record 제거 | `EgovFileWriter.write()`가 `createDirectories()` 내장 | |
| D4 | 템플릿 하이브리드 | contextCommon은 치환만, warPom은 8블록 조합 | |
| D5 | egovVersion 전파를 Phase 1으로 선행 | 실제 버그(P1) 해결이 최우선 | |
| D6 | ProjectContext는 문자열 블록 (1차) | DB 저장 없이 즉시 적용, 2차에서 서비스화 | |
| D7 | Resolver 임계값을 Capability별 상수로 분리 | 현재 동일하지만, 향후 독립 변경 대비 | |
| D8 | [v2] `VersionTable` 레코드로 버전 문자열 룩업 | `is50 ? A : B` 삼항이 Capability 독립성 무너뜨림 | **신규** |
| D9 | [v2] egovVersion 축약형 통일 ("4.3"/"5.0") | Context 블록/안내문/CRUD 파라미터 간 불일치 방지 | **신규** |
| D10 | [v2] TemplateRenderer를 Static/Build 2개로 분리 | 단일 16메서드 인터페이스 비대화 방지 | **신규** |
| D11 | [v2] `GenerationHistoryRecorder` 신규 명칭 | 기존 `GenerationHistoryService` 시그니처 충돌 회피 | **신규** |
| D12 | [v2] Validator에 내용 검증 추가 | §9.2 테스트 항목과 구현 갭 해소 | **신규** |
| D13 | [v2] `preview()` 메서드 추가, MCP 노출은 미구현 | dry-run 근거를 코드로 뒷받침하되 과잉 구현 방지 | **신규** |
| D14 | [v2.1] `EgovFileWriter`에 `normalize()+startsWith()` 경로 검증 | `validatePlans()`의 `..` 체크만으로는 절대경로/symlink 우회 불가 | **신규** |
| D15 | [v2.1] `buildFullCrudPrompt` egovVersion은 `@Nullable` optional | 기존 MCP 클라이언트(6인자) 하위 호환, 실패 시 별도 Tool 추가 전환 | **신규** |
| D16 | [v2.1] 테스트 파일 개수를 빌드툴별 정확값으로 수정 | WAR+gradle=11(3빌드+7config+1gitignore), 기존 "8~10" 부정확 | **신규** |

---

## 11. 파일 배치 (예상)

```
com.krdevops.springai.model                         [v2] 값 객체 패키지
  ├── VersionCapability.java                        record
  ├── ProjectSpec.java                              record
  ├── ProjectContext.java                           record
  ├── FilePlan.java                                 record
  └── GenerationReport.java                         가변 결과

com.krdevops.springai.service.initializr            [v2] 하위 패키지
  ├── ProjectInitializrService.java                 조율 (~30줄)
  ├── VersionCapabilityResolver.java                VersionTable 포함
  ├── FilePlanFactory.java
  ├── FilePlanExecutor.java
  ├── EgovFileWriter.java
  ├── ProjectValidator.java                         사전+사후 검증
  ├── ResultBuilder.java
  └── GenerationHistoryRecorder.java                [v2] 신규

com.krdevops.springai.service.initializr.template   [v2] 템플릿 하위 패키지
  ├── StaticTemplateRenderer.java                   [v2] 인터페이스
  ├── BuildFileRenderer.java                        [v2] 인터페이스
  ├── DefaultStaticTemplateRenderer.java
  ├── DefaultBuildFileRenderer.java
  ├── ClassPathTemplateLoader.java
  ├── WarPomBuilder.java
  ├── BootPomBuilder.java
  ├── WarBuildGradleBuilder.java
  ├── BootBuildGradleBuilder.java
  ├── DispatcherServletBuilder.java
  └── WebXmlBuilder.java

src/main/resources/templates/egov/
  ├── context-common.xml.tpl
  ├── context-datasource.xml.tpl
  ├── context-transaction.xml.tpl
  ├── logback-spring.xml.tpl
  ├── log4j2.xml.tpl
  ├── application.yml.tpl
  ├── gitignore.tpl
  ├── index.jsp.tpl
  ├── BootApplication.java.tpl
  └── BootApplicationTests.java.tpl
```

---

## 12. v1 → v2 변경 이력

| # | v1 결함 | v2 수정 | 영향 범위 |
|---|---|---|---|
| 1 | egovVersion 레이블 불일치 ("4.3.0" vs "4.3") | `VersionTable.egovVersion`을 축약형으로 통일, `ProjectContext`/`ResultBuilder`도 동일 | §5.2, §5.4, §5.11 |
| 2 | Validator가 파일 존재만 확인 (§9.2 테스트 갭) | `validateNamespace()`, `validateJavaVersion()` 내용 검증 추가 | §5.10 |
| 3 | `is50` 삼항이 Capability 독립성 파괴 | `VersionTable` record 도입, 버전 문자열도 독립 룩업 | §5.2 |
| 4 | `GenerationHistoryService.save()` 시그니처 미존재 | `GenerationHistoryRecorder` 신규 클래스, Phase 2=로그 / Phase 3+=DB | §5.12 |
| 5 | `TemplateRenderer` 16메서드 거대 인터페이스 | `StaticTemplateRenderer` + `BuildFileRenderer` 2개로 분리, `DefaultXxx` 구현 | §5.6 |
| 6 | dry-run 근거만 있고 기능 없음 | `FilePlanExecutor.preview()` 추가, MCP 노출은 "Phase 외 미구현" 명시 | §5.8, §8 |

### v2.1 패치 (검토 반영)

| # | 발견 | v2.1 수정 | 영향 범위 |
|---|---|---|---|
| 7 | `EgovFileWriter`에 경로 정규화/탈출 방지 없음 | `normalize()+startsWith()` 이중 방어 추가 | §5.9 |
| 8 | `buildFullCrudPrompt` 시그니처 변경이 MCP 하위 호환 깨짐 | `@Nullable` optional 처리 + 방법 2(별도 Tool) 대안 명시 | §6.2 |
| 9 | 테스트 파일 개수 "WAR=8~10" 부정확 | 빌드툴별 정확값 (maven=9, gradle=11 등) 산출 테이블 추가 | §9.2 |

---

## 부록: A/B 문서 출처 매핑

| 본 문서 섹션 | A 절 | B 절 |
|---|---|---|
| §3 파이프라인 흐름 | 1.1 목표 아키텍처 | §2 |
| §5.1~5.2 VersionCapability | 6.3 | §4.1~4.2 |
| §5.3 ProjectSpec | 6.2 | §4.3 |
| §5.4 ProjectContext | 5 | - |
| §5.5 FilePlan | 6.4 | §4.4 |
| §5.7 FilePlanFactory | 6.7~6.10 | §4.5 |
| §5.8 Executor | 6.11 | §4.6~4.7 |
| §5.10 Validator | 6.12~6.13 | §6 |
| §5.11 ResultBuilder | 6.13 buildResult | §4.9 |
| §6 egovVersion 전파 | 4.1~4.3 | - |
| §7 템플릿 전략 | - | §5 |
| §8 마이그레이션 | 8 (16단계) | §7 (6단계) |
| §9 테스트 | 9 | - |
