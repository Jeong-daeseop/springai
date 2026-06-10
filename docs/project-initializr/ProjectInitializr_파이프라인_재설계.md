# ProjectInitializrService 파이프라인 재설계

> 1408줄 God Class → 책임 분리된 6단계 파이프라인으로 전환
> Capability Matrix 패턴 유지 + 보완

---

## 1. 목표

| 구분 | 현재 (As-Is) | 목표 (To-Be) |
|---|---|---|
| 구조 | 단일 클래스 1408줄 | 조율 ~30줄 + 협력 컴포넌트 |
| Capability | 4개 메서드 동일 조건 (`v >= 5.0`) | Capability별 독립 임계값 상수 분리 |
| 템플릿 | Java 문자열 인라인 | 하이브리드 (정적=외부화 / 조건부=빌더) |
| 에러 처리 | created/errors 리스트 | 파일 단위 격리 (Supplier 지연 평가) |
| 흐름 | 절차적 호출 | Spec → Plan → Execute → Validate → Result → History |

---

## 2. 파이프라인 흐름

```
ProjectInitializrService (orchestrator, thin)
        │
        ▼
① ProjectSpec 조립 ──────────── VersionCapabilityResolver
        │                          (버전 문자열 → Capability)
        ▼
② FilePlan 목록 생성 ─────────── TemplateRenderer
        │                          (파일 내용 문자열)
        ▼
③ FilePlan 루프 실행 ─────────── EgovFileWriter
        │                          (디스크 쓰기, I/O 격리)
        ▼
④ 검증            ProjectValidator.validate(spec, report)
        │
        ▼
⑤ 결과 빌드        ResultBuilder.build(spec, report)
        │
        ▼
⑥ 이력 저장        GenerationHistoryService.save(spec, report)
```

---

## 3. 클래스 책임 분리

| 클래스 | 책임 | 비고 |
|---|---|---|
| `ProjectInitializrService` | 파이프라인 조율만 (thin) | 6단계 호출 |
| `VersionCapability` | Capability Matrix (불변 record) | boolean 캐싱 |
| `VersionCapabilityResolver` | 버전 문자열 → Capability 해석 | `compareVersion` 소유 |
| `ProjectSpec` | 입력 + Capability + 파생 경로 (불변) | 전 단계 공유 |
| `FilePlan` | "생성할 파일 1개" 추상 (record) | path + kind + 지연 content |
| `FilePlanFactory` | spec 기반 `List<FilePlan>` 조립 | war/boot 분기 |
| `TemplateRenderer` | 파일 내용 문자열 생성 | 전략 인터페이스 |
| `EgovFileWriter` | 디스크 쓰기 + 결과 누적 | I/O 격리 |
| `ProjectValidator` | 생성 후 정합성 검증 | 신규 |
| `ResultBuilder` | MCP 반환 텍스트 조립 | 표현 격리 |
| `GenerationReport` | created/failed 누적 (가변) | 단계 간 전달 |

---

## 4. 핵심 스켈레톤 코드

### 4.1 VersionCapability — Matrix를 record로

```java
/** 버전별 런타임 특성 — 불변 스냅샷 (호출마다 재계산 방지) */
public record VersionCapability(
        boolean jakarta,        // javax → jakarta 패키지
        boolean spring6,        // Spring Framework 6.x
        boolean boot3,          // Spring Boot 3.x
        boolean java17,         // Java 17 toolchain
        boolean egovParent,     // 전용 Parent POM
        boolean hyphenArtifactId,
        boolean myBatisSpring3,
        String   egovVersion,   // 해석된 실제 버전 (5.0.0 / 4.3.0)
        String   javaVersion,   // "17" / "11"
        String   springVersion  // "6.2.11" / "5.3.37"
) {}
```

### 4.2 VersionCapabilityResolver — 해석 책임 격리

```java
@Component
public class VersionCapabilityResolver {

    private static final String EGOV_50 = "5.0.0";
    private static final String EGOV_43 = "4.3.0";

    // 미래 대비: Capability별 독립 임계값 — 지금은 전부 5.0이지만
    // 5.1에서 jakarta만 바뀌면 JAKARTA_SINCE만 수정
    private static final String JAKARTA_SINCE = "5.0";
    private static final String SPRING6_SINCE = "5.0";
    private static final String BOOT3_SINCE   = "5.0";

    public VersionCapability resolve(String egovVersion) {
        boolean jakarta = gte(egovVersion, JAKARTA_SINCE);
        boolean spring6 = gte(egovVersion, SPRING6_SINCE);
        boolean boot3   = gte(egovVersion, BOOT3_SINCE);
        boolean is50    = gte(egovVersion, "5.0");

        return new VersionCapability(
            jakarta, spring6, boot3, /*java17*/ is50,
            /*egovParent*/ is50, /*hyphen*/ is50, /*mybatis3*/ is50,
            is50 ? EGOV_50 : EGOV_43,
            is50 ? "17" : "11",
            is50 ? "6.2.11" : "5.3.37"
        );
    }

    /** 시맨틱 버전 비교 — "latest"는 5.0으로 해석 (기존 compareVersion 이관) */
    private static boolean gte(String version, String threshold) {
        String v = "latest".equalsIgnoreCase(version) ? "5.0.0" : version;
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

> **핵심**: Capability별 임계값을 상수로 분리해두면 지금은 다 `5.0`이라 동일하지만,
> 5.1에서 특정 Capability만 바뀔 때 그 상수 하나만 고치면 된다.
> 이것이 Capability Matrix 패턴의 원래 의도를 살리는 방식.

### 4.3 ProjectSpec — 단계 간 공유 불변 객체

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
}
```

### 4.4 FilePlan — 최적 추상화 (record + 지연 평가 + kind)

```java
/** 생성할 파일 1개. content는 지연 평가 → 렌더 실패를 파일 단위로 격리 */
public record FilePlan(
        String relativePath,
        FileKind kind,                 // 검증/분류용
        Supplier<String> content       // 지연: 호출 시점에 렌더
) {
    public enum FileKind { BUILD, CONFIG, SOURCE, RESOURCE, WEB, TEST, META }

    public static FilePlan of(String path, FileKind kind, Supplier<String> content) {
        return new FilePlan(path, kind, content);
    }
}
```

> **왜 `Supplier<String>` 지연 평가인가**
> 기존 코드가 created/errors 리스트로 파일 단위 에러 누적을 한다.
> Supplier로 감싸면 Executor가 파일마다 try/catch를 걸 수 있어,
> 한 템플릿이 깨져도 나머지는 생성된다.
> `FileKind`는 ④검증 단계에서 "BUILD 파일이 정확히 1개인가" 같은 룰에 사용.

### 4.5 FilePlanFactory — 분기를 한 곳에

```java
@Component
@RequiredArgsConstructor
public class FilePlanFactory {

    private final TemplateRenderer tpl;

    public List<FilePlan> plan(ProjectSpec s) {
        List<FilePlan> plans = new ArrayList<>();
        plans.add(buildFile(s));                       // pom.xml or build.gradle
        plans.addAll(s.boot() ? bootFiles(s) : warFiles(s));
        plans.add(FilePlan.of(".gitignore", FileKind.META, () -> tpl.gitignore(s)));
        return plans;
    }

    private FilePlan buildFile(ProjectSpec s) {
        if (s.gradle()) {
            return FilePlan.of("build.gradle", FileKind.BUILD,
                () -> s.boot() ? tpl.bootBuildGradle(s) : tpl.warBuildGradle(s));
        }
        return FilePlan.of("pom.xml", FileKind.BUILD,
            () -> s.boot() ? tpl.bootPom(s) : tpl.warPom(s));
    }

    private List<FilePlan> warFiles(ProjectSpec s) {
        return List.of(
            FilePlan.of("src/main/resources/egovframework/spring/context-common.xml",
                        FileKind.CONFIG, () -> tpl.contextCommon(s)),
            FilePlan.of("src/main/resources/egovframework/spring/context-datasource.xml",
                        FileKind.CONFIG, () -> tpl.contextDatasource(s)),
            FilePlan.of("src/main/resources/egovframework/spring/context-transaction.xml",
                        FileKind.CONFIG, () -> tpl.contextTransaction(s)),
            FilePlan.of("src/main/webapp/WEB-INF/config/egovframework/springmvc/dispatcher-servlet.xml",
                        FileKind.CONFIG, () -> tpl.dispatcherServlet(s)),
            FilePlan.of("src/main/webapp/WEB-INF/web.xml",
                        FileKind.WEB, () -> tpl.webXml(s)),
            FilePlan.of("src/main/webapp/index.jsp",
                        FileKind.WEB, () -> tpl.indexJsp(s)),
            FilePlan.of("src/main/resources/log4j2.xml",
                        FileKind.RESOURCE, () -> tpl.log4j2(s))
        );
    }

    private List<FilePlan> bootFiles(ProjectSpec s) {
        String main = toPascal(s.artifactId());
        String base = "src/main/java/" + s.packagePath();
        String test = "src/test/java/" + s.packagePath();
        return List.of(
            FilePlan.of("src/main/resources/application.yml",
                        FileKind.RESOURCE, () -> tpl.applicationYml(s)),
            FilePlan.of("src/main/resources/logback-spring.xml",
                        FileKind.RESOURCE, () -> tpl.logback(s)),
            FilePlan.of(base + "/" + main + "Application.java",
                        FileKind.SOURCE, () -> tpl.bootMain(s)),
            FilePlan.of(test + "/" + main + "ApplicationTests.java",
                        FileKind.TEST, () -> tpl.bootTest(s))
        );
    }

    private static String toPascal(String artifactId) {
        StringBuilder sb = new StringBuilder();
        for (String p : artifactId.split("[-_]"))
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return sb.toString();
    }
}
```

### 4.6 FilePlanExecutor — 루프 + 에러 격리

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

### 4.7 EgovFileWriter — I/O 격리

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

### 4.8 GenerationReport — 결과 누적

```java
public class GenerationReport {
    private final String rootPath;
    private final List<String> created = new ArrayList<>();
    private final Map<String, String> errors = new LinkedHashMap<>();

    public GenerationReport(String rootPath) { this.rootPath = rootPath; }

    public void added(FilePlan p)            { created.add(p.relativePath()); }
    public void failed(FilePlan p, String m) { errors.put(p.relativePath(), m); }

    public String rootPath()            { return rootPath; }
    public List<String> created()       { return created; }
    public Map<String,String> errors()  { return errors; }
    public boolean hasErrors()          { return !errors.isEmpty(); }
}
```

### 4.9 ProjectInitializrService — 조율만 남김

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

        VersionCapability cap = resolver.resolve(egovVersion);                 // ①
        ProjectSpec spec = ProjectSpec.of(projectName, groupId, artifactId,
                packageName, buildTool, projectType, outputPath, cap);

        List<FilePlan> plans = factory.plan(spec);                             // ②
        GenerationReport report = executor.execute(spec, plans);               // ③
        validator.validate(spec, report);                                     // ④
        history.save(spec, report);                                           // ⑥
        return resultBuilder.build(spec, report);                             // ⑤
    }
}
```

> 1408줄 → 조율 클래스는 약 30줄로 축소.

---

## 5. 템플릿 처리 전략 — 비교 + 추천

| 항목 | 외부화 (.xml 리소스) | Java 문자열 (현 방식 래핑) |
|---|---|---|
| IDE 지원 (XML 자동완성) | ✅ 완전 | ❌ 없음 |
| Git diff 가독성 | ✅ 우수 | ⚠️ formatted 노이즈 |
| 조건부 조립 (pom parentBlock 등) | ❌ 어려움 (엔진 필요) | ✅ 자연스러움 |
| 의존성 추가 | 치환 or Handlebars | 없음 |
| 런타임 로딩 비용 | ClassPathResource I/O | 없음 |

### 추천: 하이브리드 (파일 성격별 분리)

```
정적 파일 (조건 거의 없음)
  → 외부화: resources/templates/egov/context-common.xml.tpl
  → ${packageName} 단순 치환

조건부 조립 파일 (pom.xml, build.gradle, web.xml)
  → Java 코드 유지, 단 전용 빌더로 격리
  → WarPomBuilder, BootPomBuilder
```

**근거**
- `contextCommon`, `logback`, `applicationYml` 등은 플레이스홀더만 바꾸면 되므로 외부화 이득이 크다.
- `warPomXml`은 parentBlock / egovDeps / servletDep 등 8개 조건 블록을 동적 조립한다.
  이를 순수 외부 템플릿으로 빼면 Handlebars `{{#if}}` 류 엔진이 필요해져 오히려 복잡해진다.

### TemplateRenderer 인터페이스

```java
public interface TemplateRenderer {
    // 정적 → 외부 .tpl 로드 + 치환 구현
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

    // 조건부 → 전용 빌더 위임 구현
    String warPom(ProjectSpec s);
    String bootPom(ProjectSpec s);
    String warBuildGradle(ProjectSpec s);
    String bootBuildGradle(ProjectSpec s);
    String dispatcherServlet(ProjectSpec s);
    String webXml(ProjectSpec s);
}
```

### 정적 템플릿 로더 (치환 방식)

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

## 6. ProjectValidator (신규 ④단계)

```java
@Component
public class ProjectValidator {

    /** 생성 후 정합성 검증 — FileKind 기반 룰 */
    public void validate(ProjectSpec s, GenerationReport report) {
        // 예시 룰
        // 1. BUILD 파일이 정확히 1개 생성되었는가
        // 2. WAR면 web.xml 존재, BOOT면 application.yml 존재
        // 3. 패키지 경로 디렉터리 실제 생성 확인
        // 위반 시 report.warn(...) 추가 (생성 자체는 막지 않음)
    }
}
```

---

## 7. 마이그레이션 순서 (점진적)

| 단계 | 작업 | 리스크 |
|---|---|---|
| 1 | `VersionCapability` + `Resolver` 추출, 기존 boolean 메서드 위임 | 낮음 |
| 2 | `ProjectSpec` record 도입, 시그니처 교체 | 낮음 |
| 3 | `FilePlan` + `Factory` + `Executor` 도입 | 중간 |
| 4 | 템플릿 인터페이스화, 정적 파일부터 외부화 | 중간 |
| 5 | `Validator` / `ResultBuilder` 분리 | 낮음 |
| 6 | 기존 1408줄 클래스 제거 | 낮음 |

> 각 단계마다 기존 `initializeProject` 출력과 diff 비교하여 회귀 검증 권장.
