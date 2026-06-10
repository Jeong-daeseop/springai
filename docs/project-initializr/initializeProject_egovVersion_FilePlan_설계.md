# initializeProject 개선 설계 — egovVersion 전파와 FilePlan 리팩터링

> 작성일: 2026-06-08  
> 대상: `ProjectInitializrService`, `ProjectInitializrTool`, `CrudPromptBuilderTool`, `CrudPromptBuilderService`

---

## 1. 결론

`initializeProject()`의 핵심 구현 방식은 기존처럼 **Capability Matrix 패턴**을 유지한다.

다만 다음 두 가지는 개선한다.

1. `initializeProject()`에서 선택한 `egovVersion`을 이후 CRUD 생성까지 명확하게 전파한다.
2. `initializeProject()` 내부의 파일 생성 절차를 `FilePlan` 기반 선언형 구조로 리팩터링한다.

최종 구조는 다음과 같다.

```text
initializeProject
  = Capability Matrix + FilePlan 기반 결정적 파일 생성

buildFullCrudPrompt
  = DB Schema 분석 + PlaceholderValues + CRUD 레이어 생성
```

`initializeProject()`를 `buildFullCrudPrompt()`처럼 프롬프트 반환 후 LLM이 생성하는 방식으로 전환하는 것은 비추천한다. 프로젝트 초기 골격은 버전과 프로젝트 타입 조합이 같으면 항상 같은 결과가 나와야 하므로 Java가 직접 생성하는 현재 방향이 더 안전하다.

---

## 1.1 목표 아키텍처

`ProjectInitializrService`는 다음 책임 단위로 재구성한다.

```text
ProjectInitializrService
  - VersionCapability / Capability Matrix
  - ProjectSpec
  - FilePlan 목록 생성
  - FilePlan 루프 실행
  - 생성 후 validate/buildResult/history
```

각 책임의 의미는 다음과 같다.

| 책임 | 역할 |
|---|---|
| `VersionCapability` / Capability Matrix | eGovFrame 버전별 런타임 특성 판단. `supportsJakarta`, `supportsSpring6`, `supportsBoot3`, `supportsJava17` 등 |
| `ProjectSpec` | 사용자 입력과 해석된 프로젝트 생성 조건을 담는 불변 값 객체 |
| `FilePlan` 목록 생성 | 생성할 파일의 경로, 내용, 종류를 실행 전에 선언형 목록으로 구성 |
| `FilePlan` 루프 실행 | 목록을 순회하며 디렉터리 생성과 파일 저장 수행 |
| `validate/buildResult/history` | 생성 결과 검증, 사용자 응답 메시지 구성, 생성 이력 저장 |

권장 흐름:

```text
initializeProject(request)
  ↓
ProjectSpec 생성
  ↓
VersionCapability 판단
  ↓
DirectoryPlan / FilePlan 목록 생성
  ↓
Plan 검증
  ↓
DirectoryPlan / FilePlan 루프 실행
  ↓
생성 결과 validate
  ↓
buildResult 생성
  ↓
history 저장
```

이 구조는 `buildFullCrudPrompt(auto)`의 장점인 "계획 → 루프 실행 → 검증 → 이력" 흐름을 가져오되, 프로젝트 초기화 자체는 LLM 프롬프트가 아니라 Java 결정적 템플릿으로 유지한다.

---

## 2. 현재 문제

### 2.1 egovVersion 전파 불완전

`initializeProject()`는 `egovVersion`을 알고 있다.

```java
initializeProject(projectName, groupId, artifactId, packageName,
                  buildTool, projectType, egovVersion, outputPath)
```

하지만 이후 CRUD 생성 도구는 이 값을 자동으로 알지 못한다.

현재 `CrudPromptBuilderService`에는 `egovVersion` 오버로드가 있다.

```java
buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, egovVersion)
buildPlaceholderValues(database, tableName, domain, packageName, outputPath, egovVersion)
```

그러나 MCP Tool 진입점인 `CrudPromptBuilderTool.buildFullCrudPrompt()`는 `egovVersion`을 받지 않는다.

```java
public String buildFullCrudPrompt(String database, String tableName,
                                  String domain, String packageName,
                                  String outputPath, String llmProvider)
```

이 때문에 Tool 호출 경로에서는 기본값 `5.0`이 사용될 수 있다.

결과적으로 `initializeProject(..., egovVersion="4.3")`로 프로젝트를 생성한 뒤 CRUD를 만들 때, `egovVersion`을 명시적으로 전달하지 못하면 `jakarta.validation.*`가 들어가는 등 버전 불일치가 발생할 수 있다.

---

### 2.2 initializeProject 파일 생성 절차가 절차형 writeFile 호출에 묶여 있음

현재 구조는 다음과 같다.

```text
initializeProject()
  ├─ createDirectories()
  ├─ createBuildFile()
  ├─ createBootFiles() 또는 createWarFiles()
  ├─ writeFile(".gitignore")
  └─ buildResult()
```

`createBuildFile()`, `createBootFiles()`, `createWarFiles()` 내부에서 `writeFile()`이 직접 반복 호출된다.

이 구조도 동작은 명확하지만, 다음 확장에는 불리하다.

- dry-run 또는 preview 제공
- 생성 파일 목록 사전 검증
- 파일 종류별 통계
- 생성 결과 테스트
- 생성 이력 저장
- 향후 projectType 또는 egovVersion 조합 추가

---

## 3. 개선 방향 요약

```text
1단계
  buildFullCrudPrompt Tool 시그니처에 egovVersion 추가
  claude 모드와 auto 모드 모두 같은 egovVersion 전달

2단계
  initializeProject 결과 메시지에 ProjectContext 블록 추가
  다음 CRUD 호출 시 사용할 egovVersion 안내

3단계
  initializeProject 내부를 FilePlan / DirectoryPlan 기반으로 리팩터링
```

---

## 4. egovVersion 전파 설계

### 4.1 Tool 시그니처 확장

`CrudPromptBuilderTool.buildFullCrudPrompt()`에 `egovVersion` 파라미터를 추가한다.

```java
public String buildFullCrudPrompt(
    String database,
    String tableName,
    String domain,
    String packageName,
    String outputPath,
    String llmProvider,
    String egovVersion
)
```

기본값 처리 규칙은 다음과 같다.

```java
String resolvedEgovVersion =
    (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
```

`claude` 모드는 이 값을 `CrudPromptBuilderService`에 전달한다.

```java
return crudPromptBuilderService.buildFullCrudPrompt(
    database,
    tableName,
    domain,
    packageName,
    outputPath,
    resolvedEgovVersion
);
```

`auto` 모드도 동일하게 전달한다.

```java
if ("auto".equals(provider)) {
    return orchestrateAuto(
        database,
        tableName,
        domain,
        packageName,
        outputPath,
        resolvedEgovVersion
    );
}
```

---

### 4.2 orchestrateAuto 확장

기존:

```java
private String orchestrateAuto(String database, String tableName,
                               String domain, String packageName,
                               String outputPath) {
    PlaceholderValues pv = crudPromptBuilderService.buildPlaceholderValues(
        database, tableName, domain, packageName, outputPath
    );
}
```

변경:

```java
private String orchestrateAuto(String database, String tableName,
                               String domain, String packageName,
                               String outputPath, String egovVersion) {
    PlaceholderValues pv = crudPromptBuilderService.buildPlaceholderValues(
        database, tableName, domain, packageName, outputPath, egovVersion
    );
}
```

이렇게 하면 `auto` 모드에서도 `javax.validation.*`와 `jakarta.validation.*` 분기가 정확해진다.

---

### 4.3 initializeProject 결과 메시지 개선

현재 결과 메시지는 다음 단계로 `buildFullCrudPrompt()` 호출만 안내한다.

```text
3. buildFullCrudPrompt() 로 CRUD 소스 생성
```

변경 후에는 `egovVersion`을 명시한다.

```text
3. buildFullCrudPrompt(..., egovVersion="4.3") 로 CRUD 소스 생성
```

또는 다음처럼 안내한다.

```text
3. CRUD 생성 시 반드시 egovVersion="4.3" 값을 함께 전달하세요.
```

---

## 5. ProjectContext 설계

`initializeProject()` 결과를 이후 Tool 호출에서 재사용하기 위해 ProjectContext 개념을 도입한다.

초기 단계에서는 DB 저장 없이 결과 문자열에 구조화된 블록을 포함한다.

```text
[PROJECT_CONTEXT]
projectName=egov-sample
rootPath=/Users/user/Desktop/egov-sample
packageName=egovframework.let.sample
projectType=boot
buildTool=gradle
egovVersion=4.3
[/PROJECT_CONTEXT]
```

권장 record:

```java
public record ProjectContext(
    String projectName,
    String rootPath,
    String packageName,
    String projectType,
    String buildTool,
    String egovVersion
) {}
```

1차 적용 범위:

- `buildResult()`에 ProjectContext 블록 출력
- 다음 CRUD 호출 예시에서 `egovVersion` 포함

2차 확장 후보:

- `ProjectContextService` 추가
- 최근 생성 프로젝트 컨텍스트 저장
- `buildFullCrudPrompt()`에서 `egovVersion` 미입력 시 최근 컨텍스트 참조
- `GenerationHistory` 또는 별도 테이블에 프로젝트 초기화 이력 저장

---

## 6. FilePlan 기반 리팩터링 설계

### 6.1 목표

현재 `initializeProject()`는 파일 생성 절차와 파일 목록이 섞여 있다.

이를 다음 구조로 변경한다.

```text
Spec 생성
  ↓
DirectoryPlan 목록 생성
  ↓
FilePlan 목록 생성
  ↓
DirectoryPlan 실행
  ↓
FilePlan 실행
  ↓
결과 요약 생성
```

---

### 6.2 ProjectSpec 설계

현재 내부 `Spec` record는 역할이 적절하지만, 리팩터링 후에는 의미가 더 분명한 `ProjectSpec` 명칭을 권장한다.

```java
private record ProjectSpec(
    boolean boot,
    String egovVersion,
    String groupId,
    String artifactId,
    String packageName,
    String buildTool,
    String projectName,
    Path rootPath
) {}
```

`ProjectSpec`은 사용자 입력값과 파생된 핵심 값을 담는다.

권장 생성 방식:

```java
private ProjectSpec toProjectSpec(String projectName, String groupId, String artifactId,
                                  String packageName, String buildTool,
                                  String projectType, String egovVersion, String outputPath) {
    boolean boot = "boot".equalsIgnoreCase(projectType);
    Path rootPath = Paths.get(outputPath, projectName);

    return new ProjectSpec(
        boot,
        normalizeEgovVersion(egovVersion),
        groupId,
        artifactId,
        packageName,
        buildTool,
        projectName,
        rootPath
    );
}
```

`normalizeEgovVersion()`은 `"latest"`와 `"5.0"`을 일관되게 처리하기 위한 진입점이다.

```java
private static String normalizeEgovVersion(String version) {
    if (version == null || version.isBlank()) {
        return "5.0";
    }
    return version.trim();
}
```

---

### 6.3 VersionCapability 설계

현재 Capability Matrix 메서드는 `ProjectInitializrService`에 static method로 존재한다.

단기적으로는 현 구조를 유지해도 된다.

```java
private static boolean supportsJakarta(String v) { ... }
private static boolean supportsSpring6(String v) { ... }
private static boolean supportsBoot3(String v) { ... }
private static boolean supportsJava17(String v) { ... }
```

중기 리팩터링에서는 `VersionCapability` record로 묶을 수 있다.

```java
private record VersionCapability(
    String egovVersion,
    boolean jakarta,
    boolean spring6,
    boolean boot3,
    boolean java17,
    boolean hyphenArtifactId,
    boolean myBatisSpring3,
    boolean egovParent
) {}
```

생성 예:

```java
private VersionCapability resolveCapability(String egovVersion) {
    return new VersionCapability(
        resolveEgovVersion(egovVersion),
        supportsJakarta(egovVersion),
        supportsSpring6(egovVersion),
        supportsBoot3(egovVersion),
        supportsJava17(egovVersion),
        supportsHyphenArtifactId(egovVersion),
        supportsMyBatisSpring3(egovVersion),
        supportsEgovParent(egovVersion)
    );
}
```

초기 구현에서는 기존 메서드를 그대로 두고, `ProjectSpec`을 먼저 도입하는 편이 변경 범위가 작다.

---

### 6.4 Plan record 설계

```java
private record DirectoryPlan(
    String relativePath
) {}
```

```java
private record FilePlan(
    String relativePath,
    String content,
    FileKind kind
) {}
```

```java
private enum FileKind {
    BUILD,
    CONFIG,
    SOURCE,
    TEST,
    WEB,
    META
}
```

`FileKind`는 초기에는 결과 요약과 테스트 편의를 위한 메타데이터로만 사용한다.

---

### 6.5 initializeProject 변경 후 구조

```java
public String initializeProject(String projectName, String groupId, String artifactId,
                                String packageName, String buildTool,
                                String projectType, String egovVersion, String outputPath) {

    List<String> created = new ArrayList<>();
    List<String> errors  = new ArrayList<>();
    ProjectSpec spec = toProjectSpec(
        projectName, groupId, artifactId, packageName,
        buildTool, projectType, egovVersion, outputPath
    );

    try {
        List<DirectoryPlan> directories = buildDirectoryPlans(spec);
        List<FilePlan> files = buildFilePlans(spec);

        validatePlans(directories, files);
        executeDirectoryPlans(spec.rootPath(), directories, created);
        executeFilePlans(spec.rootPath(), files, created, errors);

        validateGeneratedProject(spec, created, errors);
        saveProjectHistory(spec, created, errors);

    } catch (Exception e) {
        errors.add("프로젝트 초기화 실패: " + e.getMessage());
        log.error("프로젝트 초기화 실패", e);
    }

    return buildResult(spec, created, errors);
}
```

---

### 6.6 DirectoryPlan 생성

```java
private List<DirectoryPlan> buildDirectoryPlans(ProjectSpec spec) {
    String packagePath = spec.packageName().replace(".", "/");

    List<DirectoryPlan> dirs = new ArrayList<>(List.of(
        new DirectoryPlan("src/main/java/" + packagePath),
        new DirectoryPlan("src/main/resources/egovframework/mapper"),
        new DirectoryPlan("src/test/java/" + packagePath)
    ));

    if (spec.boot()) {
        dirs.add(new DirectoryPlan("src/main/resources/static/css"));
        dirs.add(new DirectoryPlan("src/main/resources/static/js"));
        dirs.add(new DirectoryPlan("src/main/resources/templates"));
    } else {
        dirs.add(new DirectoryPlan("src/main/resources/egovframework/spring"));
        dirs.add(new DirectoryPlan("src/main/webapp/WEB-INF/config/egovframework/springmvc"));
        dirs.add(new DirectoryPlan("src/main/webapp/WEB-INF/jsp/egovframework"));
        dirs.add(new DirectoryPlan("src/main/webapp/resources/css"));
        dirs.add(new DirectoryPlan("src/main/webapp/resources/js"));
    }

    return dirs;
}
```

---

### 6.7 FilePlan 생성

```java
private List<FilePlan> buildFilePlans(ProjectSpec spec) {
    List<FilePlan> files = new ArrayList<>();

    files.addAll(buildBuildFilePlans(spec));

    if (spec.boot()) {
        files.addAll(buildBootFilePlans(spec));
    } else {
        files.addAll(buildWarFilePlans(spec));
    }

    files.add(new FilePlan(".gitignore", gitignore(spec.buildTool()), FileKind.META));
    return files;
}
```

---

### 6.8 Build FilePlan

```java
private List<FilePlan> buildBuildFilePlans(ProjectSpec spec) {
    if ("gradle".equalsIgnoreCase(spec.buildTool())) {
        return List.of(
            new FilePlan(
                "build.gradle",
                spec.boot() ? bootBuildGradle(spec) : warBuildGradle(spec),
                FileKind.BUILD
            ),
            new FilePlan(
                "settings.gradle",
                "rootProject.name = '" + spec.artifactId() + "'\n",
                FileKind.BUILD
            ),
            new FilePlan(
                "gradle.properties",
                "org.gradle.jvmargs=-Xmx1024m\norg.gradle.daemon=true\n",
                FileKind.BUILD
            )
        );
    }

    return List.of(
        new FilePlan(
            "pom.xml",
            spec.boot() ? bootPomXml(spec, spec.projectName()) : warPomXml(spec, spec.projectName()),
            FileKind.BUILD
        )
    );
}
```

---

### 6.9 Boot FilePlan

```java
private List<FilePlan> buildBootFilePlans(ProjectSpec spec) {
    String packagePath = spec.packageName().replace(".", "/");
    String appName = toPascalCase(spec.artifactId());

    return List.of(
        new FilePlan(
            "src/main/resources/application.yml",
            bootApplicationYml(spec.artifactId(), spec.packageName()),
            FileKind.CONFIG
        ),
        new FilePlan(
            "src/main/resources/logback-spring.xml",
            logbackSpringXml(spec.projectName()),
            FileKind.CONFIG
        ),
        new FilePlan(
            "src/main/java/" + packagePath + "/" + appName + "Application.java",
            bootMainClass(spec.packageName(), appName),
            FileKind.SOURCE
        ),
        new FilePlan(
            "src/test/java/" + packagePath + "/" + appName + "ApplicationTests.java",
            bootTestClass(spec.packageName(), appName),
            FileKind.TEST
        )
    );
}
```

---

### 6.10 WAR FilePlan

```java
private List<FilePlan> buildWarFilePlans(ProjectSpec spec) {
    return List.of(
        new FilePlan(
            "src/main/resources/egovframework/spring/context-common.xml",
            contextCommon(spec.packageName()),
            FileKind.CONFIG
        ),
        new FilePlan(
            "src/main/resources/egovframework/spring/context-datasource.xml",
            contextDatasource(),
            FileKind.CONFIG
        ),
        new FilePlan(
            "src/main/resources/egovframework/spring/context-transaction.xml",
            contextTransaction(),
            FileKind.CONFIG
        ),
        new FilePlan(
            "src/main/webapp/WEB-INF/config/egovframework/springmvc/dispatcher-servlet.xml",
            dispatcherServlet(spec.packageName(), spec.egovVersion()),
            FileKind.CONFIG
        ),
        new FilePlan(
            "src/main/webapp/WEB-INF/web.xml",
            webXml(spec.artifactId(), spec.egovVersion()),
            FileKind.WEB
        ),
        new FilePlan(
            "src/main/webapp/index.jsp",
            "<%@ page contentType=\"text/html;charset=UTF-8\" %>\n"
                + "<jsp:forward page=\"/egovframework/com/main.do\"/>\n",
            FileKind.WEB
        ),
        new FilePlan(
            "src/main/resources/log4j2.xml",
            log4j2Xml(spec.projectName()),
            FileKind.CONFIG
        )
    );
}
```

---

### 6.11 Plan 실행

```java
private void executeDirectoryPlans(Path root, List<DirectoryPlan> directories,
                                   List<String> created) throws IOException {
    for (DirectoryPlan dir : directories) {
        Files.createDirectories(root.resolve(dir.relativePath()));
        created.add("📁 " + dir.relativePath() + "/");
    }
}
```

```java
private void executeFilePlans(Path root, List<FilePlan> files,
                              List<String> created, List<String> errors) {
    for (FilePlan file : files) {
        writeFile(root, file.relativePath(), file.content(), created, errors);
    }
}
```

기존 `writeFile()`은 유지하되, 직접 호출 위치를 `executeFilePlans()`로 모은다.

---

### 6.12 Plan 검증

`FilePlan` 실행 전에는 최소한 다음 항목을 검증한다.

- `relativePath`가 비어 있지 않은지
- `content`가 `null`이 아닌지
- 동일 경로가 중복되지 않는지
- `..` 경로가 포함되지 않는지

예:

```java
private void validatePlans(List<DirectoryPlan> directories, List<FilePlan> files) {
    Set<String> filePaths = new HashSet<>();

    for (FilePlan file : files) {
        if (file.relativePath() == null || file.relativePath().isBlank()) {
            throw new IllegalArgumentException("FilePlan relativePath가 비어 있습니다.");
        }
        if (file.relativePath().contains("..")) {
            throw new IllegalArgumentException("상위 경로 이동은 허용하지 않습니다: " + file.relativePath());
        }
        if (file.content() == null) {
            throw new IllegalArgumentException("FilePlan content가 null입니다: " + file.relativePath());
        }
        if (!filePaths.add(file.relativePath())) {
            throw new IllegalArgumentException("중복 FilePlan 경로: " + file.relativePath());
        }
    }
}
```

---

### 6.13 생성 후 validate/buildResult/history

`FilePlan` 실행 후에는 다음 순서로 후처리한다.

```text
validateGeneratedProject()
  ↓
buildResult()
  ↓
saveProjectHistory()
```

#### validateGeneratedProject

초기에는 가벼운 구조 검증만 수행한다.

```java
private void validateGeneratedProject(ProjectSpec spec, List<String> created, List<String> errors) {
    List<String> required = spec.boot()
        ? List.of("src/main/resources/application.yml")
        : List.of(
            "src/main/resources/egovframework/spring/context-common.xml",
            "src/main/webapp/WEB-INF/web.xml"
        );

    for (String path : required) {
        if (!Files.exists(spec.rootPath().resolve(path))) {
            errors.add("필수 파일 누락: " + path);
        }
    }
}
```

중기적으로는 다음 검증을 추가할 수 있다.

- `pom.xml` 또는 `build.gradle` 존재 여부
- Java 버전 분기 확인
- `javax.*` / `jakarta.*` namespace 확인
- `web.xml` schema version 확인
- Boot 프로젝트의 main/test class 존재 확인

#### buildResult

기존 `buildResult(rootPath, isBoot, egovVersion, buildTool, created, errors)`는 `ProjectSpec` 기반으로 변경한다.

```java
private String buildResult(ProjectSpec spec, List<String> created, List<String> errors) {
    ...
}
```

`buildResult()`에는 반드시 다음 정보를 포함한다.

- 생성 경로
- projectType
- buildTool
- egovVersion
- 생성 파일 목록
- 오류 목록
- 다음 단계
- `PROJECT_CONTEXT` 블록

#### saveProjectHistory

현재 `GenerationHistoryService`는 CRUD 기준 필드 중심이다.

초기에는 프로젝트 초기화 이력 저장을 생략하거나 로그만 남긴다.

```java
private void saveProjectHistory(ProjectSpec spec, List<String> created, List<String> errors) {
    log.info("프로젝트 초기화 완료: project={}, egovVersion={}, files={}, errors={}",
        spec.projectName(), spec.egovVersion(), created.size(), errors.size());
}
```

중기적으로는 별도 서비스를 권장한다.

```java
ProjectGenerationHistoryService.save(ProjectSpec spec, List<FilePlan> files, List<String> errors)
```

저장 항목 후보:

- projectName
- rootPath
- packageName
- projectType
- buildTool
- egovVersion
- generatedFiles
- errorCount
- createdAt

---

## 7. 기대 효과

### egovVersion 전파

- `initializeProject(egovVersion="4.3")` 이후 CRUD 생성도 `4.3` 기준으로 이어진다.
- `javax.validation.*` / `jakarta.validation.*` 불일치를 줄인다.
- MCP Tool 호출 계약이 명확해진다.
- `claude` 모드와 `auto` 모드의 결과 차이를 줄인다.

### FilePlan 리팩터링

- 생성 파일 목록을 실행 전에 확인할 수 있다.
- dry-run 또는 preview 기능을 추가하기 쉽다.
- 파일 종류별 통계와 결과 요약이 쉬워진다.
- 생성 대상 테스트가 쉬워진다.
- 향후 eGovFrame 5.1, 5.2, projectType 추가 시 변경 지점이 줄어든다.

---

## 8. 적용 순서

1. `CrudPromptBuilderTool.buildFullCrudPrompt()`에 `egovVersion` 파라미터를 추가한다.
2. `claude` 모드에서 `CrudPromptBuilderService.buildFullCrudPrompt(..., egovVersion)`을 호출한다.
3. `auto` 모드의 `orchestrateAuto()`에도 `egovVersion`을 전달한다.
4. `initializeProject()` 결과 메시지에 CRUD 호출용 `egovVersion` 안내를 추가한다.
5. `ProjectContext` 출력 블록을 `buildResult()`에 추가한다.
6. 기존 `Spec`을 `ProjectSpec`으로 명확히 하거나 동일 역할의 record로 확장한다.
7. `VersionCapability` record 도입 여부를 결정한다. 1차에서는 기존 Capability Matrix 메서드 유지가 가능하다.
8. `DirectoryPlan`, `FilePlan`, `FileKind`를 `ProjectInitializrService` 내부에 추가한다.
9. `createDirectories()`를 `buildDirectoryPlans()`와 `executeDirectoryPlans()`로 분리한다.
10. `createBuildFile()`, `createBootFiles()`, `createWarFiles()`를 각각 `buildXxxFilePlans()`로 전환한다.
11. `validatePlans()`를 추가해 중복 경로, 빈 경로, `null` content를 사전 검증한다.
12. `initializeProject()`는 ProjectSpec 생성, Plan 생성, Plan 실행, 후처리만 조율하도록 단순화한다.
13. `validateGeneratedProject()`를 추가해 필수 파일 존재 여부를 검증한다.
14. `buildResult()`를 `ProjectSpec` 기반 시그니처로 변경한다.
15. `saveProjectHistory()`는 1차 로그 기반으로 추가하고, 추후 별도 이력 서비스로 확장한다.
16. 기존 생성 파일 목록과 리팩터링 후 생성 파일 목록이 동일한지 테스트한다.

---

## 9. 테스트 관점

최소 테스트 케이스:

```text
war + 4.3 + maven
war + 4.3 + gradle
war + latest + maven
war + latest + gradle
boot + 4.3 + maven
boot + 4.3 + gradle
boot + latest + maven
boot + latest + gradle
```

검증 항목:

- 생성 파일 개수
- 생성 경로
- `pom.xml` 또는 `build.gradle`의 Java 버전
- Spring / Spring Boot / MyBatis 버전
- `javax.*` / `jakarta.*` namespace
- `buildResult()`의 다음 단계 안내
- `PROJECT_CONTEXT` 블록의 `egovVersion`
- CRUD 생성 시 `{{VALIDATION_IMPORT}}` 분기

---

## 10. 최종 권장안

```text
Capability Matrix는 유지한다.
buildFullCrudPrompt 방식으로 전면 전환하지 않는다.

대신:
  - ProjectInitializrService를 다음 구조로 재정렬한다.
      VersionCapability / Capability Matrix
      ProjectSpec
      FilePlan 목록 생성
      FilePlan 루프 실행
      생성 후 validate/buildResult/history
  - egovVersion을 Tool 계약에 포함한다.
  - initializeProject 결과에 ProjectContext를 남긴다.
  - 파일 생성 절차를 FilePlan 기반으로 선언형 리팩터링한다.
```

이 방식이 현재 구조의 장점인 결정성, 속도, 버전 호환성을 유지하면서 `buildFullCrudPrompt auto` 모드의 좋은 구조만 가져오는 방향이다.
