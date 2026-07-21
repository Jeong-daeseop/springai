# CRUD(Thymeleaf/JSP 혼합) list/detail 중심 디자인 참조 지원 — 영향평가 및 구현·수정 목록

> **작성일:** 2026-07-18 (1차) / **개정:** 2차~9차(코드리뷰#1~#8, 총 61건) / **10차(코드리뷰#9, 5건)**
> **성격:** 착수 전 계획 문서. 코드는 아직 수정하지 않았다(CLAUDE.md 원칙에 따름). 단계별 승인 후 진행한다.
> **10차 핵심**: (1) 9차의 민감 필드 접미사 규칙이 스스로 든 양성 예제(`USER_LOCK_YN` 등)와 모순됨을 자체 검증으로 확인(`_YN`/`_NO`로 끝나 규칙 자체가 안 맞음) → 접미사 방식을 폐기하고 토큰 단위 정확 일치로 재설계. (2) `ScreenDataBindingResolver.resolve()`가 `PageSpec`을 재생성하며 `selectionSource`를 누락시켜 JOIN 해석 후 조용히 `DEFAULT`로 리셋됨을 확인 → 명시 보존 + null 정규화 추가. (3) `listColumns`/`detailColumns`가 Tool→Service→Assembler까지 실제로 흐르는 시그니처 체인이 미정의였음을 확인 → 3단 시그니처 확정. (4) claude 경로 "전체 프롬프트에 민감 컬럼 없음" 테스트가 VO/Mapper 섹션과 원천적으로 모순됨을 확인 → 검증 대상을 `JSP_DETAIL_ROWS`로 한정. (5) `updateDefaultIndexForward()`가 private라 mock 불가 확인 → 검증 대상을 주입된 협력 객체로 교체.

---

## 0-1 ~ 0-8. 1차→9차 변경 이력 요약 (코드리뷰#1~#8, 총 61건, 전부 검증·반영됨)

- role/subset 책임 분리, `fields`(VO/Mapper) 불변, detail은 물리 컬럼만 지원
- density 보존·검증·CSS marker 계약, `revise()` density 불변, 명시 density 오류 즉시 거부
- `schemaBindings` COLUMN 고정으로 COMMON_CODE 누수 차단, `fromSchema()`에 `viewType` 추가
- `CrudTemplateRenderer` Map 전달 누락 수정(치명적), `SensitiveFieldPolicy`를 `policy` 패키지로 분리
- `ScreenSubsetMode`(NONE/LIST_ONLY/LIST_AND_DETAIL) 도입, `queryContract.displayFields()` 확장도 게이팅
- `PageSpec.selectionSource`(DEFAULT/DESIGN_REFERENCE/EXPLICIT) 도입, CSS 실패 시 `orchestrate()` 즉시 반환

## 0-9. 9차→10차 변경 이력 (코드리뷰#9, 5건, 전부 검증됨)

| # | 심각도 | 지적 | 검증 결과 |
|---|---|---|---|
| 1 | 높음 | 민감 필드 접미사 규칙(`_lock`/`_uniq`/`_cert`)이 스스로 든 양성 예제(`USER_LOCK_YN`, `BIZ_UNIQ_NO`, `CERT_NO`)와 모순 | 직접 재현: `"USER_LOCK_YN".endsWith("_LOCK")`=False, `"BIZ_UNIQ_NO".endsWith("_UNIQ")`=False, `"CERT_NO".endsWith("_CERT")`=False — 전부 실패 확인. 한국 공공 컬럼 네이밍은 의미 토큰이 중간에 오고 `_YN`/`_NO`/`_CD`가 실제 접미사인 경우가 많아 접미사 방식 자체가 구조적으로 안 맞음 — 사실 |
| 2 | 높음 | `ScreenDataBindingResolver.resolve()`가 `PageSpec`을 재생성하며 `selectionSource`를 전달하지 않아 JOIN 해석 후 조용히 `DEFAULT`로 리셋 | `ScreenDataBindingResolver.java:63` 재확인 — `new PageSpec(page.id(), page.template(), ..., page.actions())`로 재생성, `selectionSource` 인자 없음(4-arg 구조). 이전 라운드의 `withValidation()`/`resolve()` density 유실 버그와 동일한 패턴이 `selectionSource`에도 그대로 재발할 뻔함 — 사실 |
| 3 | 높음 | `listColumns`/`detailColumns`가 Tool→Service→Assembler까지 흐르는 실제 시그니처 체인이 미정의 | `ScreenSpecAssembler.assemble()` 현재 시그니처 확인 — `(database, tableName, screenName, featureType, rawColumns, uiSpec)` 6-arg, `listColumns`/`detailColumns` 없음. `selectPageBindings()`도 현재 코드에 존재하지 않는 신규 제안 메서드였는데 "이미 있는 것처럼" 서술됨 — 사실 |
| 4 | 높음 | claude 경로 "전체 프롬프트에 민감 컬럼 없음" 테스트가 VO/Mapper 섹션과 모순 | `CrudPromptBuilderService.java:245-259` 확인 — `{{VO_FIELDS}}`/`{{MAPPER_COLUMNS}}`/`{{INSERT_COLUMNS}}`/`{{RESULT_MAP_FIELDS}}` 등은 전체 컬럼 기반(VO/Mapper 계약 유지 원칙상 당연). "전체 프롬프트"에 민감 컬럼명이 없다는 테스트는 애초에 성립 불가 — 사실. `toFieldModel(col)`도 `CrudModelFactory`의 `private` 메서드(`:268`)라 `CrudPromptBuilderService`에서 재사용 불가 확인 |
| 5 | 중간 | `updateDefaultIndexForward()`가 `private`이라 mock 검증 대상이 될 수 없음 | `CrudOrchestrationService.java:264` `private void updateDefaultIndexForward(...)` 확인 — 사실 |

---

## 1. 배경 및 범위 (변경 없음)

---

## 2. 실행 순서 — 변경 없음

```
0단계) UiFieldRolePolicy + SensitiveFieldPolicy(토큰 기반 재설계, 10차)
B-3)   복합 PK, 6개 초과 예외
A)     BindingAssemblyResult, schemaBindings COLUMN 고정,
       PageSpec.selectionSource(+ null 정규화, + ScreenDataBindingResolver 보존, 10차)
B-1/B-2) listColumns/detailColumns 3단 시그니처 체인 확정(10차),
         ScreenSubsetMode, claude 경로 필터링 범위 정정(10차)
C)     table density — CSS 실패 테스트를 mockable 협력 객체 기준으로 정정(10차)
```

---

## 3.-1. 단계 0 — `SensitiveFieldPolicy` 토큰 기반 재설계(10차 항목 1, 전면 수정)

**폐기**: 접미사 매칭(`_lock`/`_uniq`/`_cert`/`_dn`)은 실제 네이밍 패턴과 안 맞아 폐기한다. `USER_LOCK_YN`처럼 의미 토큰이 중간에 오고 데이터 타입 접미사(`_YN`/`_NO`/`_CD`)가 끝에 오는 게 일반적이기 때문이다.

**신규 설계 — 토큰 단위 정확 일치**:

```java
public final class SensitiveFieldPolicy {

    private static final Set<String> SENSITIVE_TOKENS = Set.of(
            "PASSWORD", "PWD", "IHID", "ESNTL", "CERT", "LOCK", "UNIQ",
            "SECRET", "DN", "HASH", "KEY");

    /** 원본 컬럼 정보(Map)에서 바로 판정할 때 사용 — CrudPromptBuilderService 등 FieldModel이 없는 경로용. */
    public static boolean isSensitiveDisplayField(String javaName, String columnName) {
        return tokenize(columnName != null ? columnName : javaName).stream()
                .anyMatch(SENSITIVE_TOKENS::contains);
    }

    /** FieldModel이 있는 경로(CrudModelFactory)용 오버로드. */
    public static boolean isSensitiveDisplayField(FieldModel field) {
        return isSensitiveDisplayField(field.javaName(), field.columnName());
    }

    private static Set<String> tokenize(String name) {
        String snake = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2");  // camelCase → snake_case
        return Arrays.stream(snake.toUpperCase(Locale.ROOT).split("_"))
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }
}
```

**검증(전부 재확인)**:

| 입력 | 토큰 | 판정 | 비고 |
|---|---|---|---|
| `CLOCK_TM`(clockTime) | {CLOCK, TM} | 정상(제외 안 됨) | `CLOCK`≠`LOCK`(정확 일치이므로 부분 일치 문제 없음) |
| `UNIQUE_NM`(uniqueName) | {UNIQUE, NM} | 정상(제외 안 됨) | `UNIQUE`≠`UNIQ` |
| `CERTIFICATE_TITLE`(certificateTitle) | {CERTIFICATE, TITLE} | 정상(제외 안 됨) | `CERTIFICATE`≠`CERT` |
| `USER_LOCK_YN`(userLockYn) | {USER, LOCK, YN} | **민감(제외)** | `LOCK` 토큰 정확 일치 — 9차의 접미사 방식으로는 놓쳤던 케이스, 토큰 방식으로 해결 |
| `BIZ_UNIQ_NO`(bizUniqNo) | {BIZ, UNIQ, NO} | **민감(제외)** | `UNIQ` 토큰 정확 일치 |
| `CERT_NO`(certNo) | {CERT, NO} | **민감(제외)** | `CERT` 토큰 정확 일치 |
| `PASSWORD_HASH`(passwordHash) | {PASSWORD, HASH} | **민감(제외)** | 코드리뷰#8이 지적했던 누락 케이스 해결 |
| `SECRET_KEY`(secretKey) | {SECRET, KEY} | **민감(제외)** | 〃 |
| `USER_CERT_VALUE`(userCertValue) | {USER, CERT, VALUE} | **민감(제외)** | 〃 |
| `ACCOUNT_DN_VALUE`(accountDnValue) | {ACCOUNT, DN, VALUE} | **민감(제외)** | 〃 |

기존 `isSensitiveListField()`(목록용, `contains()` 기반)는 이번 범위에서 변경하지 않는다(이미 배포된 동작이라 별도 이슈로 분리, 8차 결정 유지).

| 파일 | 종류 | 변경 내용 |
|---|---|---|
| `src/main/java/com/krdevops/springai/policy/SensitiveFieldPolicy.java` | 수정(전면 재작성) | 토큰 기반 판정, `String` 오버로드와 `FieldModel` 오버로드 둘 다 제공 |
| `src/test/java/com/krdevops/springai/policy/SensitiveFieldPolicyTest.java` | 수정 | 위 표 10개 케이스 전부(음성 3 + 양성 7) |

---

## 3.1. 단계 2 — A: `selectionSource` 유실 방지 + 시그니처 체인 확정(10차 재설계 — 항목 2·3)

### A-1. `PageSpec` compact constructor null 정규화(항목 2)

```java
public record PageSpec(
        String id, String template, List<ScreenFieldBinding> fields, List<String> actions,
        FieldSelectionSource selectionSource) {
    public PageSpec {
        fields = fields == null ? List.of() : List.copyOf(fields);
        actions = actions == null ? List.of() : List.copyOf(actions);
        selectionSource = selectionSource == null ? FieldSelectionSource.DEFAULT : selectionSource;  // 신규
    }
}
```

기존 DB에 저장된 JSON(신규 필드 도입 전 데이터)이 역직렬화될 때 `selectionSource`가 `null`이면 `DEFAULT`로 정규화되므로, `null != DEFAULT`로 오판정해 불필요한 JSP 경고가 뜨는 사고를 막는다.

### A-2. `ScreenDataBindingResolver.resolve()`에서 `selectionSource` 명시 보존(항목 2)

```java
// ScreenDataBindingResolver.java:63 부근 — PageSpec 재생성 시 selectionSource 추가 전달
List<PageSpec> pages = specification.pages().stream()
        .map(page -> new PageSpec(page.id(), page.template(),
                page.fields().stream().map(f -> replaceField(f, joinsBySourceColumn, aliases)).toList(),
                page.actions(),
                page.selectionSource()))   // ← 신규: 기존엔 누락되던 인자
        .toList();
```

`withValidation()`(density 유실 버그, 3차에서 발견·수정) 때와 완전히 같은 유형의 함정이다 — "record를 재생성하는 내부 경로는 신규 필드를 항상 명시적으로 전달해야 한다"는 §4 원칙을 이번에도 그대로 적용한다.

### A-3. `listColumns`/`detailColumns` 3단 시그니처 체인 확정(항목 3)

현재 실제 시그니처(`ScreenSpecAssembler.assemble(database, tableName, screenName, featureType, rawColumns, uiSpec)`, 6-arg)를 기준으로 다음과 같이 확장한다.

```
DesignReferenceTool.createScreenSpecification(
    database, tableName, screenName, featureType, designAnalysisId,
    listColumns, detailColumns)                              // 7-arg, @Tool 유지(기존 5-arg는 하위 호환)
        │
        ▼
ScreenSpecificationService.create(
    database, tableName, screenName, featureType, uiSpec,
    listColumns, detailColumns)                              // 신규 7-arg(기존은 하위 호환 오버로드)
        │
        ▼
ScreenSpecAssembler.assemble(
    database, tableName, screenName, featureType, rawColumns, uiSpec,
    listColumns, detailColumns)                               // 신규 8-arg(기존 6-arg는 하위 호환 오버로드)
        │
        ▼ 내부에서 신규 private 메서드 selectPageBindings() 호출
           (기존 코드에 없던 신규 메서드 — B-1-b 규칙 + listColumns/detailColumns 우선순위 적용)
```

`selectPageBindings()`는 지금 코드에 존재하지 않는 **신규 메서드**임을 명시한다(9차 문서가 마치 기존 메서드인 것처럼 서술했던 부정확함을 정정).

**리스크: 낮음~중간**

| 파일 | 종류 | 변경 내용 |
|---|---|---|
| `src/main/java/com/krdevops/springai/model/design/FieldSelectionSource.java` | 신규(9차와 동일) | 3값 enum |
| `src/main/java/com/krdevops/springai/model/design/PageSpec.java` | 수정 | `selectionSource` 필드, compact constructor null 정규화(A-1), 하위 호환 생성자(5→4-arg 위임 시 `DEFAULT`) |
| `src/main/java/com/krdevops/springai/service/ScreenDataBindingResolver.java` | 수정 | `resolve()`가 `page.selectionSource()` 명시 전달(A-2) |
| `src/main/java/com/krdevops/springai/service/ScreenSpecAssembler.java` | 수정 | `assemble()` 8-arg 신규(기존 6-arg 하위 호환) + `selectPageBindings()` 신규 private 메서드(A-3) |
| `src/main/java/com/krdevops/springai/service/ScreenSpecificationService.java` | 수정 | `create()` 7-arg 신규(기존 하위 호환) |
| `src/main/java/com/krdevops/springai/tools/DesignReferenceTool.java` | 수정 | `createScreenSpecification()` 7-arg 신규 `@Tool`(기존 5-arg 하위 호환) |
| `src/test/java/com/krdevops/springai/service/ScreenDataBindingResolverTest.java` | 수정 | **JOIN 해석 후에도 `selectionSource`가 유지되는지(항목 2 핵심 회귀)** |
| `src/test/java/com/krdevops/springai/model/design/PageSpecTest.java` | 신규 | `selectionSource=null` 입력 시 `DEFAULT`로 정규화되는지 |
| `src/test/java/com/krdevops/springai/service/ScreenSpecAssemblerTest.java` | 수정 | `listColumns`/`detailColumns`가 `selectPageBindings()`를 거쳐 `EXPLICIT`으로 기록되는지 |
| `src/test/java/com/krdevops/springai/service/ScreenSpecificationServiceTest.java` / `tools/DesignReferenceToolTest.java` | 수정 | 3단 체인 전체 통합 확인 + 하위 호환 오버로드 컴파일 확인 |

---

## 3.2. 단계 3 — B-1/B-2: `ScreenSubsetMode`(9차안 유지) + claude 필터 범위 정정(10차 항목 4)

### B-1-i'. claude 경로 민감 필터 — **검증 대상을 `JSP_DETAIL_ROWS`로 한정(항목 4, 정정)**

"전체 프롬프트에 민감 컬럼이 없다"는 계약은 VO/Mapper 섹션(`{{VO_FIELDS}}`/`{{MAPPER_COLUMNS}}`/`{{INSERT_COLUMNS}}`/`{{RESULT_MAP_FIELDS}}` 등, 전부 스키마 계약상 전체 컬럼이 필요)과 원천적으로 모순되므로 **폐기**한다. 대신 **상세 화면 표시 영역만** 필터링 대상으로 좁힌다.

```java
// CrudPromptBuilderService — JSP_DETAIL_ROWS 조립 시에만 필터링(String 오버로드 사용, private toFieldModel() 재사용 안 함)
List<Map<String, Object>> safeDetailColumns = columns.stream()
        .filter(col -> !SensitiveFieldPolicy.isSensitiveDisplayField(
                toCamelCase((String) col.get("COLUMN_NAME")), (String) col.get("COLUMN_NAME")))
        .toList();
String jspDetailRows = buildJspDetailRows(safeDetailColumns);
```

**계약 범위 확정**:
- `{{JSP_DETAIL_ROWS}}`(및 Thymeleaf 상세 표시 영역에 해당하는 자리가 있다면 동일 원칙) — 민감 컬럼 제외 필수
- `{{VO_FIELDS}}`/`{{MAPPER_COLUMNS}}`/`{{INSERT_COLUMNS}}`/`{{INSERT_VALUES}}`/`{{UPDATE_SET}}`/`{{RESULT_MAP_FIELDS}}`/`{{JSP_FORM_INPUTS}}` — **스키마 계약상 전체 컬럼 유지**(민감 필터 미적용, 의도된 것)
- 프롬프트 텍스트에 "상세 화면에는 다음 필드를 표시하지 마세요: {제외된 컬럼 목록}"이라는 명시 지시를 추가해 Claude가 파일을 직접 작성할 때도 같은 allowlist를 따르게 한다

**리스크: 중간~높음**

| 파일 | 종류 | 변경 내용 |
|---|---|---|
| `src/main/java/com/krdevops/springai/service/CrudPromptBuilderService.java` | 수정 | `{{JSP_DETAIL_ROWS}}` 조립에만 `SensitiveFieldPolicy.isSensitiveDisplayField(String, String)` 필터 적용, 제외 목록 안내 문구 추가 |
| (`CrudModelFactory`/`CrudOrchestrationService`/`CrudPromptBuilderTool` 관련 파일은 9차안과 동일 — `subsetMode` 게이팅, `detailSubsetRequested` 판정) | | |
| `src/test/java/com/krdevops/springai/service/CrudPromptBuilderServiceTest.java` | 수정 | **`{{JSP_DETAIL_ROWS}}` 영역에는 민감 컬럼이 없고, `{{VO_FIELDS}}`/`{{MAPPER_COLUMNS}}` 영역에는 그대로 남아있는지 둘 다 확인(항목 4 핵심 회귀 — 이전의 "전체 프롬프트" 검증은 폐기)** |

---

## 3.3. 단계 4 — C: table density (10차 보강 — 항목 5)

### C-2'''. CSS 실패 테스트 대상을 mockable 협력 객체로 교체(항목 5, 정정)

`updateDefaultIndexForward()`는 `CrudOrchestrationService`의 `private` 메서드라 Mockito로 직접 검증할 수 없다. 로직 자체(CSS 실패 시 `orchestrate()` 즉시 `return`, 9차 확정)는 그대로 유지하고, **테스트 검증 대상만 실제로 mock 가능한 주입된 협력 객체로 교체**한다.

```java
// 검증 대상 정정
verify(warEntryPointConfigurer, never()).configure(any(), any());
verify(thymeleafRuntimeConfigurer, never()).ensureThymeleafRuntime(any(), any(), any());
verify(thymeleafRuntimeConfigurer, never()).ensureControllerComponentScan(any(), any(), any());
verify(myBatisRuntimeConfigurer, never()).ensureConfigured(any(), any());
verify(codeService, never()).saveGeneratedCode(any(), any());   // 레이어 파일 저장 자체도 없었는지
assertThat(result.failedFiles()).containsExactly("styles.css — " + expectedMessage);  // styles.css 하나만 기록
```

`updateDefaultIndexForward()`가 실제로 호출 안 됐는지는 간접적으로 `codeService.saveGeneratedCode()`가 전혀 호출되지 않았다는 사실(레이어 loop 자체가 안 돌았으므로)로 충분히 방증된다 — private 메서드를 직접 검증하려 하지 않는다.

**리스크: 높음**(변경 없음, 테스트 검증 방식만 정정)

| 파일 | 종류 | 변경 내용 |
|---|---|---|
| `src/test/java/com/krdevops/springai/service/CrudOrchestrationServiceTest.java` | 수정 | CSS 실패 시 `warEntryPointConfigurer`/`thymeleafRuntimeConfigurer`/`myBatisRuntimeConfigurer`/`codeService` 전부 `never()` 호출 확인 + `failedFiles`가 `styles.css` 항목 하나만 포함하는지(항목 5 핵심 회귀) |

(나머지 C 섹션은 9차안과 동일 — CSS marker 계약, `TableDensityCssContract`, 명시 density 즉시 거부)

---

## 4. 레코드 필드 추가 완화 전략 (변경 없음, `PageSpec`도 §A-1/A-2와 함께 적용)

---

## 5. 보완된 테스트 목록 (10차 신규만 표기)

| 대상 | 신규 테스트(10차) |
|---|---|
| 0단계 | `SensitiveFieldPolicy` — 토큰 기반 재설계 후 10개 케이스(§3.-1 표) 전부 |
| A | **`ScreenDataBindingResolver.resolve()`가 JOIN 해석 후에도 `selectionSource`를 유지하는지(최우선 회귀, 항목 2)** |
| A | `PageSpec` compact constructor — `selectionSource=null` → `DEFAULT` 정규화 |
| A | `listColumns`/`detailColumns`가 Tool→Service→Assembler 3단을 거쳐 `EXPLICIT`으로 정확히 기록되는지(항목 3) |
| B-1 | **`{{JSP_DETAIL_ROWS}}`는 민감 컬럼 제외, `{{VO_FIELDS}}`/`{{MAPPER_COLUMNS}}`는 전체 컬럼 유지 — 둘 다 한 테스트에서 확인(최우선 회귀, 항목 4)** |
| C | CSS 실패 시 `warEntryPointConfigurer`/`thymeleafRuntimeConfigurer`/`myBatisRuntimeConfigurer`/`codeService`가 전부 미호출인지(항목 5) |

---

## 6. 종합 리스크 표 (10차 개정)

| 단계 | 변경 파일 수(추정) | 신규 필드/개념 | 리스크 |
|---|---|---|---|
| 0 | 2(전면 재작성) | `SensitiveFieldPolicy` 토큰 기반 | 낮음 |
| B-3 | 1 (+테스트 1) | 없음 | 낮음~중간 |
| A | 9(+테스트 5) | `PageSpec.selectionSource` + null 정규화 + `ScreenDataBindingResolver` 보존 + 3단 시그니처 체인 | 중간 |
| B-1/B-2 | 20(+테스트 15) | claude 필터 범위를 `JSP_DETAIL_ROWS`로 한정 | 높음 |
| C | 23+(+테스트 15+) | 테스트 검증 대상만 정정(로직 변경 없음) | 높음 |

---

## 7. 승인 체크포인트 (변경 없음, 게이트 추가)

기존 게이트에 추가: 0단계는 §3.-1 표 10개 케이스, A 단계는 "`ScreenDataBindingResolver` selectionSource 보존" + "3단 시그니처 체인" 테스트, B-1 단계는 "`JSP_DETAIL_ROWS` vs `VO_FIELDS` 범위 분리" 테스트, C 단계는 "mockable 협력 객체 미호출" 테스트가 통과해야 완료로 표시한다.

---

## 8. 결론

코드리뷰#9의 5가지 사항을 전부 반영했다.

1. **민감 필드 판정을 토큰 기반으로 전면 재설계**: 접미사 규칙이 스스로의 양성 예제와 모순됐던 걸 확인하고, camelCase/snake_case를 토큰화해 정확 일치로 판정하도록 바꿨다 — `clockTime`/`uniqueName`/`certificateTitle` 오탐 없이 `userLockYn`/`bizUniqNo`/`passwordHash`/`secretKey` 등은 정확히 잡아낸다.
2. **`selectionSource` 유실 방지**: `PageSpec` compact constructor에 null 정규화를 추가하고, `ScreenDataBindingResolver.resolve()`가 재생성 시 값을 명시 전달하도록 고쳤다 — 3차에서 발견했던 density 유실 버그와 동일 패턴이 이번엔 사전에 잡혔다.
3. **`listColumns`/`detailColumns`의 3단 시그니처 체인을 실제 현재 코드 기준으로 확정**: `ScreenSpecAssembler.assemble()`의 실제 6-arg 시그니처를 확인하고 거기서부터 Tool→Service→Assembler로 이어지는 정확한 인자 목록과 하위 호환 오버로드 계획을 세웠다. `selectPageBindings()`가 신규 메서드라는 점도 명시했다.
4. **claude 경로 민감 필터 검증 범위를 `JSP_DETAIL_ROWS`로 한정**: "전체 프롬프트"라는 성립 불가능한 계약을 폐기하고, VO/Mapper 섹션은 전체 컬럼을 유지한다는 걸 명확히 했다.
5. **CSS 실패 테스트를 mockable 협력 객체 기준으로 정정**: `private` 메서드를 직접 mock하려던 계획을 버리고 실제로 주입된 협력 객체(`warEntryPointConfigurer` 등)의 미호출 여부로 검증하도록 바꿨다.

10라운드에 걸쳐 총 66개 항목(1~9차 61건 + 10차 5건)을 실제 소스 검증 후 반영했다. 이번 개정 이후 새 이슈가 없다면 §2 실행 순서(0단계부터)로 착수 가능하다고 판단한다.
