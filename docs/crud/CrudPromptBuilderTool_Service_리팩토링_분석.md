# CrudPromptBuilderTool 오케스트레이션 로직 — Service 레이어 리팩토링 분석

> 작성일: 2026-06-17  
> 대상 파일: `tools/CrudPromptBuilderTool.java`  
> 영향검토 반영: 1차 설계안 → 보완 설계안 업데이트

---

## 1. 현재 문제 진단

`CrudPromptBuilderTool`의 `orchestrateAuto()` 메서드는 Tool 레이어임에도 아래 책임을 직접 수행합니다.

```
CrudPromptBuilderTool (현재)
├── [데이터 정의]    LAYERS[][]        — 11개 레이어 경로/파일명 상수
├── [순수 함수]      resolveFileName() — 레이어별 파일명 결정
└── [오케스트레이션] orchestrateAuto()
     ├── 스키마 조회  crudSchemaQueryService.fetchColumns()
     ├── 모델 생성   crudModelFactory.fromSchema()
     ├── 루프 (11회)
     │    ├── 경로 조립  subPath, filePath 문자열 연산
     │    ├── 렌더링    crudTemplateRenderer.renderByLayerKey()
     │    └── 저장      codeService.saveGeneratedCode()
     ├── 결과 집계  StringBuilder (successCount / failCount)
     ├── 검증      codeValidatorService.validateDirectory()
     └── 이력      generationHistoryService.saveHistory()
```

**문제점 요약:**

| 문제 | 설명 |
|---|---|
| **단일 책임 위반** | Tool이 서비스 8개를 직접 주입받아 파이프라인을 구동 |
| **레이어 정의 중복** | `CrudPromptBuilderTool.LAYERS`(auto 모드)와 `CrudPromptBuilderService` 내 인라인 `layers[][]`(claude 모드, L213-225)가 각각 존재 → 파일 경로/파일명 불일치 위험 |
| **테스트 불가** | `orchestrateAuto()`는 비즈니스 로직이지만 MCP 컨텍스트 없이 단위 테스트 불가 |
| **테스트 취약성** | `CrudPromptBuilderToolTest`가 `reflection`으로 `CrudPromptBuilderTool.LAYERS` 필드를 직접 조회 (L47) — LAYERS 이동 시 즉시 깨짐 |

---

## 2. 영향검토에서 발견된 1차 설계안 문제

영향검토 결과 1차 설계안을 그대로 적용하면 아래 회귀와 비호환이 발생합니다.

### 2-1. `CrudPromptBuilderToolTest` 즉시 파손

```java
// CrudPromptBuilderToolTest.java:46-49
private static Map<String, String> layerPaths() throws Exception {
    Field field = CrudPromptBuilderTool.class.getDeclaredField("LAYERS");  // ← reflection 직접 참조
    field.setAccessible(true);
    String[][] layers = (String[][]) field.get(null);
    ...
}
```

`LAYERS`를 Tool에서 제거하면 `getDeclaredField("LAYERS")`가 `NoSuchFieldException`을 던져 두 테스트 모두 즉시 실패합니다.

### 2-2. `IllegalArgumentException` → UX 회귀

1차 설계안은 테이블이 없을 때 예외를 던지도록 제안했으나, 현재 `buildFullCrudPrompt()`는 문자열을 반환합니다.

```java
// CrudPromptBuilderService.java:161-163 (현재)
if (pv == null) {
    return "테이블을 찾을 수 없습니다: " + database + "." + tableName;
}
```

MCP Tool은 예외가 전파되면 Claude가 에러 응답을 받게 되므로, 기존처럼 실패 내용을 결과 객체/문자열로 반환하는 방식이 안전합니다.

### 2-3. `LAYERS` private static → "재사용 가능" 효과 없음

1차 설계안대로 `CrudOrchestrationService.private static LAYERS`로 옮기면  
`CrudPromptBuilderService`의 claude 모드 인라인 `layers[][]`(L213-225)는 그대로 남아  
두 정의가 **여전히 따로 존재**합니다.  
재사용이 목표라면 `public` 접근 가능한 별도 클래스로 분리해야 합니다.

### 2-4. 신규 테스트가 단위 테스트가 아님

1차 설계안의 `@SpringBootTest` 예시는 실제 DB·파일 I/O·이력 저장에 가까워  
"단위 테스트 가능" 효과를 살리지 못합니다.  
의존성을 mock 처리하는 방식으로 작성해야 합니다.

---

## 3. 보완 설계안

### 3-1. 전체 구조

```
CrudLayerDefinition (신규 — public record)
└── layerKey, fileNameSuffix, subPathTemplate 필드
└── static LAYERS 목록 (public 접근)
└── resolveFileName() (static — 외부 참조 가능)

CrudOrchestrationResult (신규 — record)
└── tableNotFound 플래그 포함 (예외 대신 결과 객체로 실패 표현)

CrudOrchestrationService (신규 — @Service)
└── orchestrate() — CrudLayerDefinition.LAYERS 사용
└── 서비스 6개 주입 (Tool에서 이전)

CrudPromptBuilderService (수정)
└── claude 모드 인라인 layers[][] → CrudLayerDefinition.LAYERS 공유

CrudPromptBuilderTool (수정)
└── 주입 서비스 8개 → 3개
└── orchestrateAuto() → orchestrate() 위임
└── formatResult() 포맷팅 분리

CrudLayerDefinitionTest (신규 — 기존 CrudPromptBuilderToolTest 대체)
└── LAYERS 경로 검증 (reflection 불필요)

CrudOrchestrationServiceTest (신규)
└── mock 기반 단위 테스트
```

---

### 3-2. `CrudLayerDefinition` (신규)

레이어 정의를 auto 모드·claude 모드가 공유하는 단일 진실 공급원(Single Source of Truth)입니다.  
`public`으로 선언해 `CrudOrchestrationService`와 `CrudPromptBuilderService` 모두 참조합니다.

```java
// model/crud/CrudLayerDefinition.java (신규)
public record CrudLayerDefinition(
    String layerKey,          // "vo", "controller", "jspList" 등
    String fileNameSuffix,    // "VO.java", "Controller.java", "List.jsp" 등
    String subPathTemplate    // "egovframework/let/{PKG}/service/", "jsp/{DOMAIN_LC}/" 등
) {
    /** auto 모드(FreeMarker)와 claude 모드(프롬프트) 공통 레이어 순서 정의 */
    public static final List<CrudLayerDefinition> LAYERS = List.of(
        new CrudLayerDefinition("vo",               "VO.java",               "egovframework/let/{PKG}/service/"),
        new CrudLayerDefinition("mapper",           "Mapper.java",            "egovframework/let/{PKG}/service/impl/"),
        new CrudLayerDefinition("mapperXml",        "Mapper.xml",             "egovframework/let/{PKG}/service/impl/"),
        new CrudLayerDefinition("service",          "Service.java",           "egovframework/let/{PKG}/service/"),
        new CrudLayerDefinition("serviceImpl",      "ServiceImpl.java",       "egovframework/let/{PKG}/service/impl/"),
        new CrudLayerDefinition("controller",       "Controller.java",        "egovframework/let/{PKG}/web/"),
        new CrudLayerDefinition("controlleradvice", "ValidationHandler.java", "egovframework/let/{PKG}/web/"),
        new CrudLayerDefinition("jspList",          "List.jsp",               "jsp/{DOMAIN_LC}/"),
        new CrudLayerDefinition("jspDetail",        "Detail.jsp",             "jsp/{DOMAIN_LC}/"),
        new CrudLayerDefinition("jspRegist",        "Regist.jsp",             "jsp/{DOMAIN_LC}/"),
        new CrudLayerDefinition("jspUpdt",          "Updt.jsp",               "jsp/{DOMAIN_LC}/")
    );

    /** vo/mapper/mapperXml/service는 {Domain}Xxx, 나머지는 Egov{Domain}Xxx */
    public static String resolveFileName(String layerKey, String domain, String suffix) {
        return switch (layerKey) {
            case "vo", "mapper", "mapperXml", "service" -> domain + suffix;
            default                                      -> "Egov" + domain + suffix;
        };
    }

    /** subPathTemplate의 플레이스홀더를 실제 값으로 치환 */
    public String resolveSubPath(String pkgSub, String domainLc) {
        return subPathTemplate
            .replace("{PKG}",       pkgSub)
            .replace("{DOMAIN_LC}", domainLc);
    }
}
```

---

### 3-3. `CrudOrchestrationResult` (신규)

테이블 미존재를 예외 대신 결과 객체로 표현해 Tool의 문자열 반환 방식을 유지합니다.

```java
// service/CrudOrchestrationResult.java (신규)
public record CrudOrchestrationResult(
    boolean tableNotFound,        // true 이면 아래 필드는 무의미
    String database,
    String tableName,
    String domain,
    String outputPath,
    List<String> succeededFiles,
    List<String> failedFiles,
    String validationSummary,
    String historySummary
) {
    /** 테이블 미존재 케이스 생성자 */
    public static CrudOrchestrationResult notFound(String database, String tableName) {
        return new CrudOrchestrationResult(
            true, database, tableName, null, null,
            List.of(), List.of(), null, null);
    }

    public int successCount()   { return succeededFiles.size(); }
    public int failCount()      { return failedFiles.size(); }
    public boolean hasFailure() { return !failedFiles.isEmpty(); }
}
```

---

### 3-4. `CrudOrchestrationService` (신규)

```java
// service/CrudOrchestrationService.java (신규)
@Slf4j
@Service
@RequiredArgsConstructor
public class CrudOrchestrationService {

    private final CrudSchemaQueryService   crudSchemaQueryService;
    private final CrudModelFactory         crudModelFactory;
    private final CrudTemplateRenderer     crudTemplateRenderer;
    private final CodeService              codeService;
    private final CodeValidatorService     codeValidatorService;
    private final GenerationHistoryService generationHistoryService;

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion) {

        // 1. 스키마 조회 — 실패 시 예외 대신 결과 객체 반환
        List<Map<String, Object>> rawColumns =
            crudSchemaQueryService.fetchColumns(database, tableName);
        if (rawColumns.isEmpty()) {
            return CrudOrchestrationResult.notFound(database, tableName);
        }

        // 2. FreeMarker 모델 생성
        String pkgSub = packageName
            .replace("egovframework.let.", "").replace(".", "/");
        CrudTemplateModel model =
            crudModelFactory.fromSchema(tableName, domain, packageName, egovVersion, rawColumns);

        // 3. CrudLayerDefinition.LAYERS 사용 — Tool의 LAYERS[][] 제거
        List<String> succeeded = new ArrayList<>();
        List<String> failed    = new ArrayList<>();

        for (CrudLayerDefinition layer : CrudLayerDefinition.LAYERS) {
            String fileName = CrudLayerDefinition.resolveFileName(
                layer.layerKey(), domain, layer.fileNameSuffix());
            String subPath  = layer.resolveSubPath(pkgSub, model.domainLc());
            String filePath = outputPath + "/" + subPath + fileName;

            try {
                String code       = crudTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
                String saveResult = codeService.saveGeneratedCode(filePath, code);
                if (saveResult.startsWith("파일 저장 실패")) {
                    failed.add(fileName + " — " + saveResult);
                    log.error("[orchestrate] 저장 실패: {}", filePath);
                } else {
                    succeeded.add(fileName);
                    log.info("[orchestrate] 저장 완료: {}", filePath);
                }
            } catch (Exception e) {
                failed.add(fileName + " — 오류: " + e.getMessage());
                log.error("[orchestrate] 실패: layer={}, error={}", layer.layerKey(), e.getMessage());
            }
        }

        // 4. 코드 검증
        String validationSummary;
        try {
            validationSummary = codeValidatorService.validateDirectory(outputPath);
        } catch (Exception e) {
            validationSummary = "검증 실패: " + e.getMessage();
        }

        // 5. 생성 이력
        String historySummary;
        try {
            historySummary = generationHistoryService.saveHistory(
                tableName, domain, packageName, outputPath, succeeded.size() + "개 파일");
        } catch (Exception e) {
            historySummary = "이력 저장 실패: " + e.getMessage();
        }

        return new CrudOrchestrationResult(
            false, database, tableName, domain, outputPath,
            succeeded, failed, validationSummary, historySummary);
    }
}
```

---

### 3-5. `CrudPromptBuilderService` 수정 (claude 모드 레이어 공유)

`buildFullCrudPrompt()`의 인라인 `String[][] layers`(L213-225)를  
`CrudLayerDefinition.LAYERS`로 교체합니다.

```java
// CrudPromptBuilderService.java — buildFullCrudPrompt() 내 레이어 목록 부분만 수정
// 변경 전 (L213-229):
String[][] layers = {
    {pv.domain() + "VO.java", packagePath + "/service/"},
    ...
};
for (int i = 0; i < layers.length; i++) {
    sb.append(String.format("  Step %2d: saveGeneratedCode(\"%s/%s%s\", ...)\n", ...));
}

// 변경 후:
int step = 1;
String pkgSub = pv.packageName().replace("egovframework.let.", "").replace(".", "/");
for (CrudLayerDefinition layer : CrudLayerDefinition.LAYERS) {
    String fileName = CrudLayerDefinition.resolveFileName(
        layer.layerKey(), pv.domain(), layer.fileNameSuffix());
    String subPath  = layer.resolveSubPath(pkgSub, pv.domainLc());
    sb.append(String.format("  Step %2d: saveGeneratedCode(\"%s/%s%s\", code)\n",
        step++, outputPath, subPath, fileName));
}
```

---

### 3-6. `CrudPromptBuilderTool` 수정

```java
// tools/CrudPromptBuilderTool.java (리팩토링 후)
@Slf4j
@Component
@RequiredArgsConstructor
public class CrudPromptBuilderTool {

    // 주입 서비스 8개 → 3개
    private final CrudOrchestrationService crudOrchestrationService;
    private final CrudPromptBuilderService crudPromptBuilderService;
    private final MasterDetailService      masterDetailService;

    // LAYERS, resolveFileName, orchestrateAuto 삭제

    @Tool(description = "...")
    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName,
                                      String outputPath, String llmProvider,
                                      @Nullable String egovVersion) {
        String resolved = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        String provider = (llmProvider == null || llmProvider.isBlank()) ? "auto"
                          : llmProvider.trim().toLowerCase();

        if ("auto".equals(provider)) {
            CrudOrchestrationResult result = crudOrchestrationService.orchestrate(
                database, tableName, domain, packageName, outputPath, resolved);
            return formatResult(result);   // Tool은 포맷팅만 담당
        }
        return crudPromptBuilderService.buildFullCrudPrompt(
            database, tableName, domain, packageName, outputPath, resolved);
    }

    private String formatResult(CrudOrchestrationResult r) {
        // 테이블 미존재: 기존 문자열 반환 방식 유지 (예외 불전파)
        if (r.tableNotFound()) {
            return "테이블을 찾을 수 없습니다: " + r.database() + "." + r.tableName();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===\n\n");
        sb.append("DB: ").append(r.database())
          .append(" | 테이블: ").append(r.tableName())
          .append(" | 도메인: ").append(r.domain()).append("\n");
        sb.append("출력 경로: ").append(r.outputPath()).append("\n\n[생성 파일 목록]\n");
        r.succeededFiles().forEach(f -> sb.append("  ✅ ").append(f).append("\n"));
        r.failedFiles().forEach(f    -> sb.append("  ❌ ").append(f).append("\n"));
        sb.append("\n총 ").append(r.successCount()).append("개 성공");
        if (r.hasFailure()) sb.append(", ").append(r.failCount()).append("개 실패");
        sb.append("\n\n[코드 검증 결과]\n").append(r.validationSummary());
        sb.append("\n\n[생성 이력]\n").append(r.historySummary());
        return sb.toString();
    }

    // buildMasterDetailPrompt, buildJoinSelectPrompt — 변경 없음
}
```

---

## 4. 변경 범위 요약

| 항목 | 현재 | 보완 설계 후 |
|---|---|---|
| Tool 주입 서비스 수 | **8개** | **3개** |
| `LAYERS` 위치 | `CrudPromptBuilderTool` (private) | `CrudLayerDefinition` (**public** record) |
| `resolveFileName()` 위치 | `CrudPromptBuilderTool` (private) | `CrudLayerDefinition` (public static) |
| claude 모드 인라인 `layers[][]` | `CrudPromptBuilderService` L213-225 | `CrudLayerDefinition.LAYERS` 공유 |
| 테이블 미존재 처리 | 문자열 반환 | `CrudOrchestrationResult.notFound()` → Tool에서 문자열로 포맷 (UX 유지) |
| 오케스트레이션 로직 | Tool | `CrudOrchestrationService` |
| 결과 타입 | StringBuilder 직접 조립 | `CrudOrchestrationResult` record |
| **신규 파일** | — | `CrudLayerDefinition.java`, `CrudOrchestrationService.java`, `CrudOrchestrationResult.java` |
| **수정 파일** | — | `CrudPromptBuilderTool.java`, `CrudPromptBuilderService.java` |
| **삭제 코드** | — | Tool의 `LAYERS`, `resolveFileName`, `orchestrateAuto` / Service의 인라인 `layers[][]` |

---

## 5. 테스트 전략

### 5-1. `CrudPromptBuilderToolTest` → `CrudLayerDefinitionTest`로 교체

현재 테스트는 reflection으로 Tool 내부 필드를 직접 참조하므로,  
레이어 정의가 `CrudLayerDefinition`으로 이동하면 테스트도 함께 이전합니다.

```java
// test/.../model/crud/CrudLayerDefinitionTest.java (신규)
class CrudLayerDefinitionTest {

    @Test
    void javaLayers_packagePathに_domainFolder不포함() {
        CrudLayerDefinition.LAYERS.stream()
            .filter(l -> !l.layerKey().startsWith("jsp"))
            .forEach(l ->
                assertThat(l.subPathTemplate()).doesNotContain("{DOMAIN_LC}")
            );
    }

    @Test
    void jspLayers_domainFolder포함() {
        CrudLayerDefinition.LAYERS.stream()
            .filter(l -> l.layerKey().startsWith("jsp"))
            .forEach(l ->
                assertThat(l.subPathTemplate()).contains("{DOMAIN_LC}")
            );
    }

    @Test
    void resolveFileName_voMapper는도메인접두사없음() {
        assertThat(CrudLayerDefinition.resolveFileName("vo", "Employee", "VO.java"))
            .isEqualTo("EmployeeVO.java");
        assertThat(CrudLayerDefinition.resolveFileName("controller", "Employee", "Controller.java"))
            .isEqualTo("EgovEmployeeController.java");
    }

    @Test
    void layers_총11개() {
        assertThat(CrudLayerDefinition.LAYERS).hasSize(11);
    }
}
```

### 5-2. `CrudOrchestrationServiceTest` — mock 기반 단위 테스트

```java
// test/.../service/CrudOrchestrationServiceTest.java (신규)
@ExtendWith(MockitoExtension.class)
class CrudOrchestrationServiceTest {

    @Mock CrudSchemaQueryService   schemaQueryService;
    @Mock CrudModelFactory         modelFactory;
    @Mock CrudTemplateRenderer     templateRenderer;
    @Mock CodeService              codeService;
    @Mock CodeValidatorService     validatorService;
    @Mock GenerationHistoryService historyService;

    @InjectMocks
    CrudOrchestrationService sut;

    @Test
    void orchestrate_테이블없음_tableNotFound반환() {
        given(schemaQueryService.fetchColumns("com", "NOTEXIST")).willReturn(List.of());

        CrudOrchestrationResult result =
            sut.orchestrate("com", "NOTEXIST", "Test", "egovframework.let.test", "/tmp", "5.0");

        assertThat(result.tableNotFound()).isTrue();
    }

    @Test
    void orchestrate_11개파일_모두성공() {
        given(schemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(modelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(templateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(validatorService.validateDirectory(any())).willReturn("검증 통과");
        given(historyService.saveHistory(any(), any(), any(), any(), any())).willReturn("이력 저장 완료");

        CrudOrchestrationResult result =
            sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.tableNotFound()).isFalse();
        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.hasFailure()).isFalse();
    }

    @Test
    void orchestrate_저장실패_failedFiles에기록() {
        given(schemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(modelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(templateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 실패: 권한 없음");

        CrudOrchestrationResult result =
            sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failCount()).isEqualTo(11);
    }
}
```

---

## 6. 적용 순서 (권장)

```
Step 1  CrudLayerDefinition.java 생성 (model/crud/)
        — LAYERS, resolveFileName, resolveSubPath 정의

Step 2  CrudOrchestrationResult.java 생성 (service/)
        — tableNotFound 포함, notFound() 팩토리 메서드 추가

Step 3  CrudOrchestrationService.java 생성 (service/)
        — orchestrateAuto() 내용 이전, CrudLayerDefinition.LAYERS 사용

Step 4  CrudPromptBuilderService.java 수정
        — 인라인 layers[][] → CrudLayerDefinition.LAYERS 공유

Step 5  CrudPromptBuilderTool.java 수정
        — 주입 서비스 교체, LAYERS/resolveFileName/orchestrateAuto 삭제
        — formatResult() 추가 (tableNotFound 분기 포함)

Step 6  CrudPromptBuilderToolTest.java 삭제
        → CrudLayerDefinitionTest.java 신규 작성 (reflection 제거)
        → CrudOrchestrationServiceTest.java 신규 작성 (mock 기반)

Step 7  빌드 + 테스트 실행으로 회귀 확인
        ./gradlew test
```

---

## 7. 기대 효과 요약

| 관점 | 개선 내용 |
|---|---|
| **단일 책임** | Tool = 위임 + 포맷팅만, Service = 파이프라인 실행 |
| **레이어 정의 일원화** | auto/claude 모드 양쪽이 `CrudLayerDefinition.LAYERS` 하나를 공유 → 경로 불일치 위험 제거 |
| **테스트 용이성** | `CrudOrchestrationService` mock 기반 단위 테스트 가능, reflection 의존 테스트 제거 |
| **UX 호환성** | 테이블 미존재·실패 케이스 모두 기존처럼 문자열로 반환 (예외 미전파) |
| **유지보수성** | 레이어 추가/삭제 시 `CrudLayerDefinition` 한 곳만 수정 |
| **Tool 가독성** | 주입 서비스 8개 → 3개, Tool 클래스 라인 수 약 50% 감소 |