# ProjectInitializrService 통합 설계서

> A (`initializeProject_egovVersion_FilePlan_설계.md`)와 B (`ProjectInitializr_파이프라인_재설계.md`)의 병합
> A의 "무엇을" + B의 "어떻게" → 단일 실행 계획

---

## 1. 현재 문제 요약

| # | 문제 | 영향 | 출처 |
|---|---|---|---|
| P1 | egovVersion이 CRUD 생성까지 전파되지 않음 | `initializeProject(4.3)` 후 CRUD에서 `jakarta.*` 혼입 | A |
| P2 | 1408줄 God Class | 책임 혼재, 테스트/확장 어려움 | B |
| P3 | Capability 4개 메서드가 동일 조건 (`v >= 5.0`) | Matrix 패턴 의도 미실현 | B |
| P4 | Java 문자열 내 XML/YAML 인라인 | IDE 지원 없음, 오타 런타임 발견 | B |
| P5 | 즉시 평가 + 절차형 writeFile | 파일 단위 에러 격리 불가, dry-run 불가 | A+B |

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
        │                          ├─ TemplateRenderer (인터페이스)
        │                          │    ├─ 정적: ClassPathTemplateLoader
        │                          │    └─ 조건부: WarPomBuilder 등
        │                          └─ Supplier<String> 지연 평가
        ▼
③ FilePlan 루프 실행 ─────────── FilePlanExecutor
        │                          └─ EgovFileWriter (I/O 격리)
        ▼
④ 검증 ──────────────────────── ProjectValidator
        │
        ▼
⑤ 결과 빌드 ─────────────────── ResultBuilder
        │                          └─ ProjectContext 블록 포함
        ▼
⑥ 이력 저장 ─────────────────── GenerationHistoryService
```

---

## 4. 클래스 책임 분리

| 클래스 | 책임 | 출처 |
|---|---|---|
| `ProjectInitializrService` | 파이프라인 조율만 (thin) | B |
| `VersionCapability` | Capability Matrix 불변 record (boolean 캐싱) | A+B |
| `VersionCapabilityResolver` | 버전 문자열 → Capability 해석, `compareVersion` 소유 | B |
| `ProjectSpec` | 입력 + Capability + 파생 경로 (불변) | A+B |
| `ProjectContext` | 이후 CRUD Tool에 전달할 컨텍스트 record | A |
| `FilePlan` | "생성할 파일 1개" (path + kind + `Supplier<String>`) | A개념+B구현 |
| `FilePlanFactory` | spec 기반 `List<FilePlan>` 조립 (war/boot 분기) | B |
| `TemplateRenderer` | 파일 내용 문자열 생성 (전략 인터페이스) | B |
| `ClassPathTemplateLoader` | 정적 `.tpl` 파일 로드 + `${key}` 치환 | B |
| `FilePlanExecutor` | FilePlan 루프 + 파일 단위 에러 격리 | B |
| `EgovFileWriter` | 디스크 쓰기 I/O 격리 | B |
| `ProjectValidator` | 생성 후 정합성 검증 + 사전 Plan 검증 | A+B |
| `ResultBuilder` | MCP 반환 텍스트 + `ProjectContext` 블록 조립 | A+B |
| `GenerationReport` | created/failed 누적 (가변, 단계 간 전달) | B |

---

## 5. 핵심 스켈레톤 코드

### 5.1 VersionCapability (B 기반)

```java
/** 버전별 런타임 특성 — 불변 스냅샷 */
public record VersionCapability(
        boolean jakarta,           // javax → jakarta
        boolean spring6,           // Spring Framework 6.x
        boolean boot3,             // Spring Boot 3.x
        boolean java17,            // Java 17 toolchain
        boolean egovParent,        // 전용 Parent POM
        boolean hyphenArtifactId,  // 5.0+ artifactId 명명 규칙
        boolean myBatisSpring3,    // mybatis-spring 3.x
        String  egovVersion,       // 해석된 실제 버전 (5.0.0 / 4.3.0)
        String  javaVersion,       // "17" / "11"
        String  springVersion,     // "6.2.11" / "5.3.37"
        String  springBootVersion, // "3.5.6" / "2.7.18"
        String  securityVersion    // "6.5.5" / "5.8.13"
) {}
```

### 5.2 VersionCapabilityResolver (B 기반)

```java
@Component
public class VersionCapabilityResolver {

    // ── 버전 상수 ──
    private static final String EGOV_50 = "5.0.0";
    private static final String EGOV_43 = "4.3.0";

    // ── Capability별 독립 임계값 ──
    // 지금은 전부 5.0이지만, 5.1에서 jakarta만 바뀌면 JAKARTA_SINCE만 수정
    private static final String JAKARTA_SINCE    = "5.0";
    private static final String SPRING6_SINCE    = "5.0";
    private static final String BOOT3_SINCE      = "5.0";
    private static final String JAVA17_SINCE     = "5.0";
    private static final String PARENT_SINCE     = "5.0";
    private static final String HYPHEN_ID_SINCE  = "5.0";
    private static final String MYBATIS3_SINCE   = "5.0";

    public VersionCapability resolve(String egovVersion) {
        boolean is50 = gte(egovVersion, "5.0");
        return new VersionCapability(
            gte(egovVersion, JAKARTA_SINCE),
            gte(egovVersion, SPRING6_SINCE),
            gte(egovVersion, BOOT3_SINCE),
            gte(egovVersion, JAVA17_SINCE),
            gte(egovVersion, PARENT_SINCE),
            gte(egovVersion, HYPHEN_ID_SINCE),
            gte(egovVersion, MYBATIS3_SINCE),
            is50 ? EGOV_50 : EGOV_43,
            is50 ? "17" : "11",
            is50 ? "6.2.11" : "5.3.37",
            is50 ? "3.5.6" : "2.7.18",
            is50 ? "6.5.5" : "5.8.13"
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
        return true; // 동일 = 이상
    }

    private static int seg(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
```

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

    public boolean gradle() { return "gradle".equalsIgnoreCase(buildTool); }
    public String egovVersion() { return cap.egovVersion(); }
}
```

### 5.4 ProjectContext — egovVersion 전파 핵심 (A 기반)

```java
/** initializeProject 결과를 이후 Tool 호출에서 재사용하기 위한 컨텍스트 */
public record ProjectContext(
        String projectName,
        String rootPath,
        String packageName,
        String projectType,     // "war" / "boot"
        String buildTool,       // "maven" / "gradle"
        String egovVersion      // "4.3.0" / "5.0.0"
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

### 5.5 FilePlan — Supplier 지연 평가 (A개념+B구현)

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

> **A와 B의 FilePlan 차이점과 선택 근거**
>
> | | A (즉시 평가) | B (지연 평가) | 채택 |
> |---|---|---|---|
> | content 타입 | `String` | `Supplier<String>` | **B** |
> | Plan 생성 시 비용 | 전체 렌더링 | 없음 (경로만) | **B** |
> | 에러 격리 | Plan 생성 시 전체 실패 | 파일 단위 try/catch | **B** |
> | dry-run | 불가 (이미 렌더) | 가능 (Supplier 미호출) | **B** |
> | DirectoryPlan 분리 | 별도 record | 불필요 (FileWriter에 위임) | **B** |

### 5.6 FilePlanFactory (B 기반)

```java
@Component
@RequiredArgsConstructor
public class FilePlanFactory {

    private final TemplateRenderer tpl;

    public List<FilePlan> plan(ProjectSpec s) {
        List<FilePlan> plans = new ArrayList<>();
        plans.addAll(buildFilePlans(s));
        plans.addAll(s.boot() ? bootFiles(s) : warFiles(s));
        plans.add(FilePlan.of(".gitignore", META, () -> tpl.gitignore(s)));
        return plans;
    }

    private List<FilePlan> buildFilePlans(ProjectSpec s) {
        if (s.gradle()) {
            return List.of(
                FilePlan.of("build.gradle", BUILD,
                    () -> s.boot() ? tpl.bootBuildGradle(s) : tpl.warBuildGradle(s)),
                FilePlan.of("settings.gradle", BUILD,
                    () -> "rootProject.name = '" + s.artifactId() + "'\n"),
                FilePlan.of("gradle.properties", BUILD,
                    () -> "org.gradle.jvmargs=-Xmx1024m\norg.gradle.daemon=true\n")
            );
        }
        return List.of(
            FilePlan.of("pom.xml", BUILD,
                () -> s.boot() ? tpl.bootPom(s) : tpl.warPom(s))
        );
    }

    private List<FilePlan> warFiles(ProjectSpec s) {
        return List.of(
            FilePlan.of("src/main/resources/egovframework/spring/context-common.xml",
                        CONFIG, () -> tpl.contextCommon(s)),
            FilePlan.of("src/main/resources/egovframework/spring/context-datasource.xml",
                        CONFIG, () -> tpl.contextDatasource(s)),
            FilePlan.of("src/main/resources/egovframework/spring/context-transaction.xml",
                        CONFIG, () -> tpl.contextTransaction(s)),
            FilePlan.of("src/main/webapp/WEB-INF/config/egovframework/springmvc/dispatcher-servlet.xml",
                        CONFIG, () -> tpl.dispatcherServlet(s)),
            FilePlan.of("src/main/webapp/WEB-INF/web.xml",
                        WEB, () -> tpl.webXml(s)),
            FilePlan.of("src/main/webapp/index.jsp",
                        WEB, () -> tpl.indexJsp(s)),
            FilePlan.of("src/main/resources/log4j2.xml",
                        RESOURCE, () -> tpl.log4j2(s))
        );
    }

    private List<FilePlan> bootFiles(ProjectSpec s) {
        String cls  = toPascal(s.artifactId());
        String base = "src/main/java/" + s.packagePath();
        String test = "src/test/java/" + s.packagePath();
        return List.of(
            FilePlan.of("src/main/resources/application.yml",
                        RESOURCE, () -> tpl.applicationYml(s)),
            FilePlan.of("src/main/resources/logback-spring.xml",
                        RESOURCE, () -> tpl.logback(s)),
            FilePlan.of(base + "/" + cls + "Application.java",
                        SOURCE, () -> tpl.bootMain(s)),
            FilePlan.of(test + "/" + cls + "ApplicationTests.java",
                        TEST, () -> tpl.bootTest(s))
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

### 5.7 FilePlanExecutor + EgovFileWriter (B 기반)

```java
@Component
@RequiredArgsConstructor
public class FilePlanExecutor {
    private final EgovFileWriter writer;

    public GenerationReport execute(ProjectSpec s, List<FilePlan> plans) {
        GenerationReport report = new GenerationReport(s.root().toString());
        for (FilePlan p : plans) {
            try {
                writer.write(s.root(), p.relativePath(), p.content().get());
                report.added(p);
            } catch (Exception e) {
                report.failed(p, e.getMessage());   // 격리: 나머지 계속
            }
        }
        return report;
    }
}
```

```java
@Component
public class EgovFileWriter {
    public void write(Path root, String relativePath, String content) throws IOException {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
```

### 5.8 GenerationReport (B 기반)

```java
public class GenerationReport {
    private final String rootPath;
    private final List<String> created = new ArrayList<>();
    private final Map<String, String> errors  = new LinkedHashMap<>();
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

### 5.9 ProjectValidator — 사전 + 사후 검증 통합 (A+B 병합)

```java
@Component
public class ProjectValidator {

    /** 사전 검증: FilePlan 실행 전 (A 기반 6.12절) */
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

    /** 사후 검증: 생성 후 정합성 (A 기반 6.13절 + B FileKind 룰) */
    public void validateResult(ProjectSpec s, GenerationReport report) {
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
    }
}
```

### 5.10 ResultBuilder — ProjectContext 포함 (A+B 병합)

```java
@Component
public class ResultBuilder {

    public String build(ProjectSpec s, GenerationReport report) {
        ProjectContext ctx = ProjectContext.from(s);
        String typeLabel = s.boot() ? "Spring Boot (내장 서버)" : "WAR (Tomcat 외부 배포)";
        String buildCmd  = s.gradle()
            ? (s.boot() ? "./gradlew bootRun" : "./gradlew build")
            : (s.boot() ? "mvn spring-boot:run" : "mvn clean package");
        String egovVer = s.cap().jakarta() ? "5.0" : "4.3";

        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 프로젝트 초기화 완료 ===\n\n");
        sb.append("📌 경로   : ").append(report.rootPath()).append("\n");
        sb.append("📌 타입   : ").append(typeLabel).append("\n");
        sb.append("📌 버전   : ").append(s.egovVersion()).append("\n");
        sb.append("📌 빌드   : ").append(s.buildTool()).append("\n\n");

        sb.append("✅ 생성 완료 (").append(report.totalFiles()).append("개)\n");
        report.created().forEach(f -> sb.append("  📄 ").append(f).append("\n"));

        if (report.hasErrors()) {
            sb.append("\n⚠️  오류 (").append(report.errors().size()).append("개)\n");
            report.errors().forEach((f, m) -> sb.append("  ❌ ").append(f).append(" → ").append(m).append("\n"));
        }

        if (!report.warnings().isEmpty()) {
            sb.append("\n⚠️  경고\n");
            report.warnings().forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
        }

        sb.append("\n📋 다음 단계\n");
        sb.append("  1. ").append(s.boot() ? "application.yml" : "context-datasource.xml").append(" DB 정보 설정\n");
        sb.append("  2. Spring Security 설정 추가 (선택)\n");
        sb.append("     → getSecurityTemplate(..., \"").append(egovVer).append("\")\n");
        sb.append("  3. buildFullCrudPrompt(..., egovVersion=\"").append(egovVer).append("\") 로 CRUD 생성\n");
        sb.append("  4. ").append(buildCmd).append(" 로 빌드/실행\n");

        // A 기반: ProjectContext 블록 — 이후 Tool 호출에서 egovVersion 전파용
        sb.append("\n").append(ctx.toBlock()).append("\n");

        return sb.toString();
    }
}
```

### 5.11 ProjectInitializrService — 최종 조율 (~30줄)

```java
@Service
@RequiredArgsConstructor
public class ProjectInitializrService {

    private final VersionCapabilityResolver resolver;
    private final FilePlanFactory factory;
    private final FilePlanExecutor executor;
    private final ProjectValidator validator;
    private final ResultBuilder resultBuilder;
    private final GenerationHistoryService history;

    public String initializeProject(String projectName, String groupId, String artifactId,
                                    String packageName, String buildTool,
                                    String projectType, String egovVersion, String outputPath) {

        // ① Capability 해석 + Spec 조립
        VersionCapability cap = resolver.resolve(egovVersion);
        ProjectSpec spec = ProjectSpec.of(projectName, groupId, artifactId,
                packageName, buildTool, projectType, outputPath, cap);

        // ② FilePlan 목록 생성 (지연 평가 — 아직 렌더링 없음)
        List<FilePlan> plans = factory.plan(spec);

        // ③ 사전 검증 (중복 경로, null content, 경로 탈출)
        validator.validatePlans(plans);

        // ④ FilePlan 루프 실행 (파일 단위 에러 격리)
        GenerationReport report = executor.execute(spec, plans);

        // ⑤ 사후 검증 (필수 파일 존재 확인)
        validator.validateResult(spec, report);

        // ⑥ 이력 저장
        history.save(spec, report);

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

### 6.2 CrudPromptBuilderTool 시그니처 확장

```java
// 변경 전
public String buildFullCrudPrompt(String database, String tableName,
                                  String domain, String packageName,
                                  String outputPath, String llmProvider)

// 변경 후
public String buildFullCrudPrompt(String database, String tableName,
                                  String domain, String packageName,
                                  String outputPath, String llmProvider,
                                  String egovVersion)
```

기본값 처리:

```java
String resolved = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
```

### 6.3 orchestrateAuto 확장

```java
// 변경 전
private String orchestrateAuto(...) {
    PlaceholderValues pv = service.buildPlaceholderValues(
        database, tableName, domain, packageName, outputPath);
}

// 변경 후
private String orchestrateAuto(..., String egovVersion) {
    PlaceholderValues pv = service.buildPlaceholderValues(
        database, tableName, domain, packageName, outputPath, egovVersion);
}
```

### 6.4 buildResult 안내 메시지 변경

```text
// 변경 전
3. buildFullCrudPrompt() 로 CRUD 소스 생성

// 변경 후
3. buildFullCrudPrompt(..., egovVersion="4.3") 로 CRUD 소스 생성
```

### 6.5 ProjectContext 2차 확장 후보

- `ProjectContextService` 추가 (최근 생성 프로젝트 컨텍스트 저장)
- `buildFullCrudPrompt()`에서 `egovVersion` 미입력 시 최근 컨텍스트 자동 참조
- `GenerationHistory` 테이블에 프로젝트 초기화 이력 영구 저장

---

## 7. 템플릿 처리 전략 (B 기반)

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
정적 파일 (조건 거의 없음) → 외부화
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

조건부 조립 파일 (8+ 블록 동적 조합) → Java 빌더
  WarPomBuilder
  BootPomBuilder
  WarBuildGradleBuilder
  BootBuildGradleBuilder
  DispatcherServletBuilder
  WebXmlBuilder
```

### 7.3 TemplateRenderer 인터페이스

```java
public interface TemplateRenderer {
    // 정적 → ClassPathTemplateLoader 위임
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

    // 조건부 → 전용 빌더 위임
    String warPom(ProjectSpec s);
    String bootPom(ProjectSpec s);
    String warBuildGradle(ProjectSpec s);
    String bootBuildGradle(ProjectSpec s);
    String dispatcherServlet(ProjectSpec s);
    String webXml(ProjectSpec s);
}
```

### 7.4 ClassPathTemplateLoader

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

---

## 8. 통합 마이그레이션 순서

> A의 16단계 + B의 6단계 → 병합 10단계
> 원칙: 실제 버그 해결 먼저, 구조 개선은 점진적으로

### Phase 1: egovVersion 전파 (즉시 효과, 리스크 낮음)

| # | 작업 | 출처 | 리스크 |
|---|---|---|---|
| 1 | `CrudPromptBuilderTool.buildFullCrudPrompt()`에 `egovVersion` 추가 | A | 낮음 |
| 2 | `claude` 모드에서 `egovVersion` 전달 | A | 낮음 |
| 3 | `orchestrateAuto()`에도 `egovVersion` 전달 | A | 낮음 |
| 4 | `buildResult()`에 CRUD 호출 시 `egovVersion` 안내 추가 | A | 낮음 |

> 여기까지만 해도 P1(javax/jakarta 불일치) 해결

### Phase 2: 값 객체 도입 (리팩터링 기반)

| # | 작업 | 출처 | 리스크 |
|---|---|---|---|
| 5 | `VersionCapability` record + `VersionCapabilityResolver` 추출 | B | 낮음 |
| 6 | `ProjectSpec` record 도입 (기존 `Spec` 대체) | A+B | 낮음 |
| 7 | `ProjectContext` record + `buildResult()`에 블록 출력 | A | 낮음 |

### Phase 3: FilePlan 파이프라인 (핵심 구조 변경)

| # | 작업 | 출처 | 리스크 |
|---|---|---|---|
| 8 | `FilePlan` + `FilePlanFactory` + `FilePlanExecutor` + `EgovFileWriter` 도입 | A+B | 중간 |
| 9 | `ProjectValidator` (사전+사후) + `ResultBuilder` + `GenerationReport` 분리 | A+B | 중간 |

### Phase 4: 템플릿 외부화 (마무리)

| # | 작업 | 출처 | 리스크 |
|---|---|---|---|
| 10 | `TemplateRenderer` 인터페이스 + 정적 파일 `.tpl` 외부화 + 조건부 빌더 분리 | B | 중간 |

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

### 9.2 검증 항목

| 검증 | 기대값 |
|---|---|
| 생성 파일 개수 | WAR=8~10, Boot=6~8 |
| pom.xml / build.gradle Java 버전 | 4.3→11, 5.0→17 |
| Spring / Boot / MyBatis 버전 | Capability에 따라 |
| javax.* / jakarta.* namespace | 4.3→javax, 5.0→jakarta |
| web.xml schema version | 4.3→4.0, 5.0→6.0 |
| dispatcher-servlet multipart | 4.3→Commons, 5.0→Standard |
| buildResult 다음 단계 egovVersion | Spec과 일치 |
| PROJECT_CONTEXT 블록 | egovVersion 포함 |
| CRUD egovVersion 전파 | validation import 분기 일치 |

### 9.3 회귀 테스트 방법

```text
1. 기존 코드로 8가지 조합 실행 → 파일 목록 + 내용 스냅샷 저장
2. 리팩터링 후 동일 조합 실행 → diff 비교
3. 차이가 있으면 의도적 변경인지 확인
```

---

## 10. 설계 결정 기록 (ADR)

| # | 결정 | 근거 | 출처 |
|---|---|---|---|
| D1 | Capability Matrix 패턴 유지 | 미래 5.1/5.2 확장 대비 | A+B |
| D2 | FilePlan에 `Supplier<String>` 채택 | 파일 단위 에러 격리 + dry-run | B |
| D3 | DirectoryPlan 별도 record 제거 | `EgovFileWriter.write()`가 `createDirectories()` 내장 | B |
| D4 | 템플릿 하이브리드 (정적=외부화, 조건부=Java) | contextCommon은 치환만, warPom은 8블록 조합 | B |
| D5 | egovVersion 전파를 Phase 1으로 선행 | 실제 버그(P1) 해결이 최우선 | A |
| D6 | ProjectContext는 문자열 블록 (1차) | DB 저장 없이 즉시 적용 가능, 2차에서 서비스화 | A |
| D7 | Resolver 임계값을 Capability별 상수로 분리 | 현재 동일하지만, 향후 독립 변경 대비 | B |

---

## 11. 파일 배치 (예상)

```
com.krdevops.springai.service
  ├── ProjectInitializrService.java        ← 조율 (~30줄)
  ├── FilePlanFactory.java
  ├── FilePlanExecutor.java
  ├── EgovFileWriter.java
  ├── ProjectValidator.java
  ├── ResultBuilder.java
  ├── VersionCapabilityResolver.java
  └── template/
       ├── TemplateRenderer.java           ← 인터페이스
       ├── DefaultTemplateRenderer.java    ← 구현
       ├── ClassPathTemplateLoader.java    ← 정적 .tpl 로드
       ├── WarPomBuilder.java              ← 조건부 빌더
       ├── BootPomBuilder.java
       ├── WebXmlBuilder.java
       └── DispatcherServletBuilder.java

com.krdevops.springai.service (기존 또는 vo/)
  ├── VersionCapability.java               ← record
  ├── ProjectSpec.java                     ← record
  ├── ProjectContext.java                  ← record
  ├── FilePlan.java                        ← record
  └── GenerationReport.java               ← 가변 결과 누적

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

## 부록: A/B 문서 출처 매핑

| 본 문서 섹션 | A 절 | B 절 |
|---|---|---|
| §3 파이프라인 흐름 | 1.1 목표 아키텍처 | §2 |
| §5.1~5.2 VersionCapability | 6.3 | §4.1~4.2 |
| §5.3 ProjectSpec | 6.2 | §4.3 |
| §5.4 ProjectContext | 5 | - |
| §5.5 FilePlan | 6.4 | §4.4 |
| §5.6 FilePlanFactory | 6.7~6.10 | §4.5 |
| §5.7 Executor/Writer | 6.11 | §4.6~4.7 |
| §5.9 Validator | 6.12~6.13 | §6 |
| §5.10 ResultBuilder | 6.13 buildResult | §4.9 |
| §6 egovVersion 전파 | 4.1~4.3 | - |
| §7 템플릿 전략 | - | §5 |
| §8 마이그레이션 | 8 (16단계) | §7 (6단계) |
| §9 테스트 | 9 | - |
