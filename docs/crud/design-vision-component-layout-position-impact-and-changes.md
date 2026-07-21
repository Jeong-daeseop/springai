# 디자인 참조 컴포넌트 좌표/배치 반영 — 영향평가 및 구현·수정 목록

> **작성일:** 2026-07-18
> **성격:** 착수 전 계획 문서. 코드는 아직 수정하지 않았다(CLAUDE.md 원칙에 따름). 단계별 승인 후 진행한다.
> **관계:** `docs/crud/design-vision-component-layout-position-impact-analysis.md`(1차, 개념·범위 정의 문서)를 실제 소스 재확인을 거쳐 실행 가능한 파일별 구현·수정 목록으로 구체화한 문서. 1차 문서의 결론(§0 목표/비목표, §3-0 이산 버킷 설계 원칙)은 그대로 유지하고, 이번 재확인 과정에서 발견된 2건의 정정 사항을 §1에 반영했다.

---

## 0. 목표/비목표 재확인 (1차 문서 §0과 동일, 변경 없음)

| 구분 | 내용 |
|---|---|
| 목표 | 업로드된 디자인 참조 화면에서 컴포넌트(테이블/레이블/입력필드/버튼)의 **상대적 배치 구조(순서·그룹핑·단수)** 를 추출해 생성 템플릿에 반영. 픽셀 단위 일치는 목표가 아니다. |
| 비목표 | 색상/폰트/radius/간격 토큰은 기존 KRDS 표준 CSS를 그대로 사용(현재도 이미 그러함, 변경 없음). 픽셀 좌표 재현, GNB/LNB 등 공통 레이아웃 구조 변경은 범위 밖. |

---

## 1. 1차 문서 대비 정정 사항 (실제 코드 재확인, 2건)

### 정정 1 — `ScreenDataBindingResolver.resolve()` 유실 위험, 실제로는 이미 안전함

1차 문서(§3-2)는 "design-vision-4scenario 문서의 `selectionSource` 유실 버그와 동일한 함정이 재발할 수 있다"고 막연히 우려했다. 그러나 실제 코드(`ScreenDataBindingResolver.java:63-72`)를 재확인한 결과:

```java
List<PageSpec> pages = specification.pages().stream()
        .map(page -> new PageSpec(page.id(), page.template(), page.fields().stream()
                .map(field -> replaceField(field, joinsBySourceColumn, aliases)).toList(),
                page.actions(), page.selectionSource()))   // ← 이미 명시 전달됨(:66)
        .toList();
return new ScreenSpecification(
        specification.id(), specification.version(), specification.status(),
        specification.screenName(), specification.featureType(), specification.archetype(),
        specification.database(), specification.primaryTable(), dataSources, pages,
        specification.issues(), specification.layoutDensity(), specification.createdAt());  // ← layoutDensity도 이미 명시 전달됨(:72)
```

`selectionSource`(`PageSpec` 재생성, `:66`)와 `layoutDensity`(`ScreenSpecification` 재생성, `:72`) 모두 **이미 올바르게 전달되고 있다** — design-vision-4scenario 문서가 지적했던 버그는 이미 수정된 상태다. 따라서 이번 기능의 실제 리스크는 "버그가 재발할 수 있다"는 막연한 우려가 아니라, **`ScreenSpecification`에 3개 신규 필드(`formColumnLayout` 등)를 추가하면 바로 이 `ScreenDataBindingResolver.java:68-72` 재생성 지점을 같은 커밋에서 반드시 함께 갱신해야 한다는 구체적 체크리스트 항목**이다(§3-1, §7 체크포인트에 명시).

### 정정 2 — Thymeleaf 폼과 JSP 폼의 마크업 구조가 완전히 다름

1차 문서(§3-3)는 폼 2단 배치를 `<div class="egov-form-row-two-col">`로 감싸는 단일 설계를 제안했다. 그러나 실제 두 템플릿을 확인한 결과 **구조 자체가 다르다**:

- **Thymeleaf** (`thymeleaf-regist-body.html.ftl:13-59`, `thymeleaf-updt-body.html.ftl` 동일 구조): `<table class="tbl col egov-form-table">` 안에 `<tr><th scope="row"><label>...</th><td><input.../></td></tr>` 행이 `<#list formFields as f>`(`:37`)로 반복되는 **테이블 기반** 구조.
- **JSP** (`jsp-regist.jsp.ftl:36-54`): `<div class="form-group"><div class="form-tit"><label>.../div><div class="form-conts"><form:input.../></div></div>`가 `<#list formFields as f>`(`:36`)로 반복되는 **div 기반** 구조.

`<div>` 래핑 설계는 Thymeleaf의 `<table>` 구조에 그대로 적용할 수 없다(테이블 행 중간에 div를 끼워 넣을 수 없음). **viewType별로 서로 다른 2단 배치 마크업 전략이 필요**하다(§3-3에 각각 구체화).

---

## 2. 확정된 실행 순서

```
0단계) UiDesignSpec.LayoutSpec 확장 + 신규 enum 3종 + 비전 프롬프트 1줄 추가
1단계) ScreenSpecification 3필드 추가
       + ScreenDataBindingResolver.java:68-72 재생성 지점 동시 갱신(정정 1 반영)
       + ScreenSpecAssembler에서 3개 값 파싱
2단계) CrudTemplateModel/CrudModelFactory/CrudTemplateRenderer로 formColumnLayout 전달
       + Thymeleaf(table 2단) / JSP(div 2단) 각각 별도 마크업 구현(정정 2 반영)
       + FormColumnLayoutCssContract 신규(TableDensityCssContract와 동일 패턴)
(3단계 — actionPlacement/searchPanelPlacement 구조적 섹션 재배치는 별도 승인 후 착수, 이번 목록 범위 밖)
```

---

## 3. 단계별 구현·수정 목록

### 3-0. 단계 0 — 비전 스키마 확장

```java
// UiDesignSpec.java:29 — 기존
public record LayoutSpec(String shell, String contentWidth, String density) {}

// 변경 후
public record LayoutSpec(
        String shell, String contentWidth, String density,
        String formColumnLayout   // "single" | "two-column"
) {
    /** formColumnLayout 도입 전 호출자 호환. */
    public LayoutSpec(String shell, String contentWidth, String density) {
        this(shell, contentWidth, density, null);
    }
}
```

```java
// 신규: model/design/FormColumnLayout.java (LayoutDensity.java:6-21과 동일 패턴)
package com.krdevops.springai.model.design;

import java.util.Locale;

public enum FormColumnLayout {
    SINGLE_COLUMN,
    TWO_COLUMN;

    public static FormColumnLayout from(String raw) {
        if (raw == null || raw.isBlank()) return SINGLE_COLUMN;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 form column layout: " + raw
                            + " (지원값: single-column, two-column)", e);
        }
    }
}
```

프롬프트 추가(`AbstractChatVisionAnalysisClient.java:12-21` 블록 끝에 한 줄):
```
layout.formColumnLayout은 등록/수정 폼에서 관찰되는 배치를 "single-column" 또는
"two-column" 중 하나로 판단하세요. 불확실하면 uncertainties에 기록하고 기본값
"single-column"을 사용하세요.
```

| 파일 | 종류 | 변경 |
|---|---|---|
| `model/design/UiDesignSpec.java` | 수정 | `LayoutSpec`에 `formColumnLayout` 필드 + 하위호환 3-arg 생성자 |
| `model/design/FormColumnLayout.java` | 신규 | enum + `from(String)` |
| `service/AbstractChatVisionAnalysisClient.java` | 수정 | 프롬프트 1줄 추가 |
| `test/.../model/design/UiDesignSpecTest.java` | 신규/수정 | `formColumnLayout=null` → 하위호환 생성자 동작 확인 |
| `test/.../model/design/FormColumnLayoutTest.java` | 신규 | `from("single-column")`/`from("two-column")`/`from(null)`/`from("invalid")` 4케이스(LayoutDensityTest와 동일 패턴) |

**리스크: 낮음**

---

### 3-1. 단계 1 — `ScreenSpecification` 반영 (정정 1 체크리스트 포함)

```java
// ScreenSpecification.java:6-20 — 기존 13-arg에 1개 추가한 14-arg
public record ScreenSpecification(
        String id, int version, ScreenSpecStatus status, String screenName,
        String featureType, String archetype, String database, String primaryTable,
        List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
        LayoutDensity layoutDensity, FormColumnLayout formColumnLayout,   // 신규 필드
        LocalDateTime createdAt
) {
    public ScreenSpecification {
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        pages = pages == null ? List.of() : List.copyOf(pages);
        issues = issues == null ? List.of() : List.copyOf(issues);
        layoutDensity = layoutDensity == null ? LayoutDensity.STANDARD : layoutDensity;
        formColumnLayout = formColumnLayout == null ? FormColumnLayout.SINGLE_COLUMN : formColumnLayout;  // 신규
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    /** formColumnLayout 도입 전 호출자 호환(기존 13-arg 생성자, :30-37과 동일 패턴으로 위임). */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName,
            String featureType, String archetype, String database, String primaryTable,
            List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
            LayoutDensity layoutDensity, LocalDateTime createdAt) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, layoutDensity, FormColumnLayout.SINGLE_COLUMN, createdAt);
    }

    // withValidation()/withStatus()(:39-46)도 formColumnLayout을 그대로 전달하도록 수정 필요
}
```

**정정 1 반영 — 반드시 함께 수정할 지점**: `ScreenDataBindingResolver.java:68-72`의 `ScreenSpecification` 재생성 호출에 `specification.formColumnLayout()`을 추가해야 한다. 이 지점을 빠뜨리면 JOIN 해석이 일어나는 화면(FK가 있는 테이블)에서만 조용히 `formColumnLayout`이 `SINGLE_COLUMN`으로 리셋되는, 발견하기 어려운 회귀가 생긴다(`layoutDensity`가 이미 `:72`에서 명시 전달되고 있는 것과 나란히 추가하면 된다).

```java
// ScreenSpecAssembler.java:79-86 — density 옆에 나란히 추가
LayoutDensity density = LayoutDensity.from(
        resolvedUi.layout() == null ? null : resolvedUi.layout().density());
FormColumnLayout formColumnLayout = FormColumnLayout.from(     // 신규
        resolvedUi.layout() == null ? null : resolvedUi.layout().formColumnLayout());
ScreenSpecification draft = new ScreenSpecification(
        UUID.randomUUID().toString(), 1, ScreenSpecStatus.DRAFT,
        blank(screenName) ? tableName : screenName,
        blank(featureType) ? "crud" : featureType,
        archetype, database, tableName,
        List.of(DataSourceSpec.primary(database, tableName)), pages, issues,
        density, formColumnLayout, LocalDateTime.now());   // 신규 인자 추가
```

`reviseScreenSpecification()`은 density와 동일하게 `formColumnLayout`도 재생성 시 불변으로 유지한다(디자인 참조 분석 시점에 확정, 이후 변경 불가).

| 파일 | 종류 | 변경 |
|---|---|---|
| `model/design/ScreenSpecification.java` | 수정 | 14-arg 확장, `withValidation()`/`withStatus()` 갱신, 13-arg 하위호환 생성자 |
| `service/ScreenSpecAssembler.java` | 수정 | `:79-86` 인근에 `FormColumnLayout.from()` 호출 + 생성자 인자 추가 |
| `service/ScreenDataBindingResolver.java` | 수정 | **`:68-72` 재생성 호출에 `specification.formColumnLayout()` 추가(정정 1 핵심)** |
| `service/ScreenSpecificationPromptFormatter.java` | 수정 | `:19` density 라인 옆에 `formColumnLayout` 텍스트 라인 추가 |
| `test/.../model/design/ScreenSpecificationTest.java` | 신규/수정 | 13-arg 호출 시 `formColumnLayout`이 `SINGLE_COLUMN`으로 정규화되는지 |
| `test/.../service/ScreenDataBindingResolverTest.java` | 수정 | **JOIN 해석이 발생하는 케이스에서 `formColumnLayout`이 유지되는지(정정 1 회귀 테스트, 최우선)** |
| `test/.../service/ScreenSpecAssemblerTest.java` | 수정 | 비전 출력 `two-column` → `ScreenSpecification.formColumnLayout()==TWO_COLUMN` 확인 |

**리스크: 중간** — 레코드 필드 추가 자체는 기계적이나, `ScreenDataBindingResolver.java:68-72` 갱신을 빠뜨리면 조용히 실패하는 회귀이므로 반드시 전용 테스트로 게이트.

---

### 3-2. 단계 2 — `CrudTemplateModel`/렌더링 반영 (정정 2 반영, viewType별 분리)

density와 동일한 전달 경로(`CrudModelFactory.java:185-186` → `CrudTemplateRenderer.java:240`)를 그대로 따른다.

```java
// CrudTemplateModel.java:16-40 — 필드 추가
List<FieldModel> detailFields,
LayoutDensity layoutDensity,
FormColumnLayout formColumnLayout   // 신규
) {
    public CrudTemplateModel {
        queryContract = queryContract == null ? GenerationQueryContract.empty() : queryContract;
        layoutDensity = layoutDensity == null ? LayoutDensity.STANDARD : layoutDensity;
        formColumnLayout = formColumnLayout == null ? FormColumnLayout.SINGLE_COLUMN : formColumnLayout;  // 신규
    }
    // 기존 4개 하위호환 생성자(:43-78) 각각에 FormColumnLayout.SINGLE_COLUMN 인자 추가
}
```

```java
// CrudModelFactory.java:185-187 — 기존
screenSpecification == null
        ? LayoutDensity.STANDARD : screenSpecification.layoutDensity(),
screenSpecification == null                                    // 신규
        ? FormColumnLayout.SINGLE_COLUMN : screenSpecification.formColumnLayout()
```

```java
// CrudTemplateRenderer.java:240 — 기존 옆에 한 줄
data.put("layoutDensity",     model.layoutDensity());
data.put("formColumnLayout",  model.formColumnLayout());   // 신규
```

#### 3-2-a. Thymeleaf — `<table>` 구조 2단 배치 (정정 2: table 기반)

`thymeleaf-regist-body.html.ftl:37-56`, `thymeleaf-updt-body.html.ftl` 동일 위치. **테이블 행 안에서 `<th>/<td>` 쌍을 2개씩 묶어야 하므로, `<div>` 래핑이 아니라 `<tr>` 안에 반복 횟수를 조정**한다.

```html
<#-- 기존(:14) -->
<table class="tbl col egov-form-table">

<#-- 변경 후 -->
<table class="tbl col egov-form-table<#if formColumnLayout == "TWO_COLUMN"> egov-layout-two-col</#if>">
```

```html
<#-- 기존(:37-56)을 대체 -->
<#if formColumnLayout == "TWO_COLUMN">
<#list formFields?chunk(2) as pair>
                <tr>
<#list pair as f>
                    <th scope="row">
                        <label for="${f.javaName}">
                            ${f.comment}<#if f.required><span class="egov-required-mark">*</span></#if>
                        </label>
                    </th>
                    <td>
                        <input type="<#if f.javaName?lower_case?contains('password')>password<#else>text</#if>"
                               th:field="*{${f.javaName}}"
                               id="${f.javaName}"
                               class="krds-input medium egov-control"
                               <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                               placeholder="${f.comment}을(를) 입력하세요"/>
                        <p th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                           class="egov-field-error"
                           th:errors="*{${f.javaName}}"></p>
                    </td>
</#list>
                </tr>
</#list>
<#else>
<#-- 기존 단일 컬럼 마크업 그대로 유지 -->
<#list formFields as f>
                <tr>
                    ...(기존과 동일)...
                </tr>
</#list>
</#if>
```

`formFields?chunk(2)`는 필드 수가 홀수면 마지막 행에 1쌍만 남는다(FreeMarker 기본 동작 — 별도 padding 없음) — 시각적으로 마지막 행만 좁아 보일 수 있으나 기능상 문제는 없다. 이 각주를 테스트/QA 체크리스트에 명시한다.

#### 3-2-b. JSP — `<div>` 구조 2단 배치 (정정 2: div 기반, 기존 1차 설계와 유사하되 대상 파일이 다름)

`jsp-regist.jsp.ftl:36-54`, `jsp-updt.jsp.ftl` 동일 위치.

```html
<#if formColumnLayout == "TWO_COLUMN">
<#list formFields?chunk(2) as pair>
            <div class="form-row-two-col">
<#list pair as f>
                <div class="form-group">
                    <div class="form-tit">
                        <label for="${f.javaName}">${f.comment}</label>
                    </div>
                    <div class="form-conts">
                        <#if f.javaName?lower_case?contains('password')>
                        <form:password path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                    <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                                    placeholder="${f.comment}을(를) 입력하세요"/>
                        <#else>
                        <form:input path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                    <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                                    placeholder="${f.comment}을(를) 입력하세요"/>
                        </#if>
                        <form:errors path="${f.javaName}" cssClass="form-hint-invalid" element="p"/>
                    </div>
                </div>
</#list>
            </div>
</#list>
<#else>
<#-- 기존 단일 컬럼 마크업 그대로 유지 -->
</#if>
```

#### 3-2-c. CSS — `FormColumnLayoutCssContract` 신규(`TableDensityCssContract` 패턴 그대로 재사용)

현재 `.tbl.col th`는 `width: 180px`(styles.css.tpl:105) 고정폭이라, 2단 배치 시 한 행에 라벨 2개+입력 2개가 들어가면 폭 재계산이 필요하다.

```java
// 신규: service/FormColumnLayoutCssContract.java (TableDensityCssContract.java와 동일 구조)
package com.krdevops.springai.service;

public final class FormColumnLayoutCssContract {
    public static final String START_MARKER = "/* === egov-form-column-layout:start === */";
    public static final String END_MARKER = "/* === egov-form-column-layout:end === */";
    public static final String CSS = """

/* === egov-form-column-layout:start === */
.egov-form-table.egov-layout-two-col th { width: 90px; }
.egov-form-table.egov-layout-two-col td { width: auto; }
.form-row-two-col { display: flex; gap: 16px; }
.form-row-two-col .form-group { flex: 1; min-width: 0; }
/* === egov-form-column-layout:end === */
""";

    private FormColumnLayoutCssContract() {
    }
}
```

`KrdsStylesConfigurer`에 `ensureTableDensityStyles()`(`:135-172`)와 동일한 패턴으로 `ensureFormColumnLayoutStyles(outputPath)`를 추가하고, `ThymeleafLayoutTool`/`ThymeleafRuntimeConfigurer` 또는 CRUD 생성 오케스트레이션(`CrudOrchestrationService`) 중 실제로 CSS 보강을 호출하는 지점에 나란히 연결한다(정확한 호출 지점은 착수 시 `ensureTableDensityStyles()` 호출부를 grep해 동일 지점에 추가).

| 파일 | 종류 | 변경 |
|---|---|---|
| `model/crud/CrudTemplateModel.java` | 수정 | `formColumnLayout` 필드 + 4개 하위호환 생성자 갱신(`:43-78`) |
| `service/CrudModelFactory.java` | 수정 | `:185-187` 인근 `formColumnLayout` 인자 추가 |
| `service/CrudTemplateRenderer.java` | 수정 | `:240` 인근 FreeMarker 데이터맵 주입 |
| `templates/crud/thymeleaf-regist-body.html.ftl` | 수정 | `:14`, `:37-56` — table 2단 배치 분기(§3-2-a) |
| `templates/crud/thymeleaf-updt-body.html.ftl` | 수정 | 동일 구조(pkFields는 hidden input이라 영향 없음, formFields 블록만 대상) |
| `templates/crud/jsp-regist.jsp.ftl` | 수정 | `:36-54` — div 2단 배치 분기(§3-2-b) |
| `templates/crud/jsp-updt.jsp.ftl` | 수정 | 동일 구조 |
| `service/FormColumnLayoutCssContract.java` | 신규 | 마커 + CSS(§3-2-c) |
| `templates/egov/styles.css.tpl` | 수정 | 동일 CSS 블록을 스캐폴드 시점 정적 파일에도 반영(density의 `:95-98`과 동일 패턴) |
| `service/KrdsStylesConfigurer.java` | 수정 | `ensureFormColumnLayoutStyles()` 신규 메서드(`ensureTableDensityStyles():135-172`와 동일 패턴) + 마커 상수 추가(`:18-19`와 동일 패턴) |
| (CSS 보강 호출 지점 — `ensureTableDensityStyles()` 호출부와 동일한 곳) | 수정 | 나란히 `ensureFormColumnLayoutStyles()` 호출 추가 |
| 테스트: `CrudTemplateModelTest`, `CrudModelFactoryTest` | 수정 | `formColumnLayout` 하위호환/전달 확인 |
| 테스트: `CrudTemplateRendererTest` | 수정 | Thymeleaf/JSP 각각 `TWO_COLUMN`/`SINGLE_COLUMN` 렌더링 스냅샷(regist/updt × 2 viewType = 4케이스) |
| 테스트: `KrdsStylesConfigurerTest` | 신규/수정 | `ensureFormColumnLayoutStyles()` 멱등성(재실행 시 중복/손상 없음) — `ensureTableDensityStyles()` 테스트와 동일 패턴 |

**리스크: 중간~높음** — FTL 수정 자체는 국소적(2개 조건 분기 블록)이지만, viewType 2종(Thymeleaf/JSP) × 화면 2종(regist/updt) = 4개 템플릿 파일을 동시에 정확히 맞춰야 하고, `formFields?chunk(2)` 홀수 처리 등 렌더링 스냅샷 회귀가 필수.

---

## 4. 종합 파일 변경 목록 (전체 취합)

| 단계 | 파일 | 종류 |
|---|---|---|
| 0 | `model/design/UiDesignSpec.java` | 수정 |
| 0 | `model/design/FormColumnLayout.java` | 신규 |
| 0 | `service/AbstractChatVisionAnalysisClient.java` | 수정 |
| 1 | `model/design/ScreenSpecification.java` | 수정 |
| 1 | `service/ScreenSpecAssembler.java` | 수정 |
| 1 | `service/ScreenDataBindingResolver.java` | 수정(정정 1 핵심) |
| 1 | `service/ScreenSpecificationPromptFormatter.java` | 수정 |
| 2 | `model/crud/CrudTemplateModel.java` | 수정 |
| 2 | `service/CrudModelFactory.java` | 수정 |
| 2 | `service/CrudTemplateRenderer.java` | 수정 |
| 2 | `templates/crud/thymeleaf-regist-body.html.ftl` | 수정 |
| 2 | `templates/crud/thymeleaf-updt-body.html.ftl` | 수정 |
| 2 | `templates/crud/jsp-regist.jsp.ftl` | 수정 |
| 2 | `templates/crud/jsp-updt.jsp.ftl` | 수정 |
| 2 | `service/FormColumnLayoutCssContract.java` | 신규 |
| 2 | `templates/egov/styles.css.tpl` | 수정 |
| 2 | `service/KrdsStylesConfigurer.java` | 수정 |
| 2 | (density CSS 보강 호출 지점) | 수정 |

프로덕션 코드 기준 총 17개 파일(신규 2 + 수정 15), 테스트 파일은 §3 각 단계표 기준 별도 약 10개.

---

## 5. 종합 리스크 표

| 단계 | 변경 파일 수 | 신규 개념 | 리스크 |
|---|---|---|---|
| 0 | 3(+테스트 2) | `FormColumnLayout` enum | 낮음 |
| 1 | 4(+테스트 3) | `ScreenSpecification.formColumnLayout` | 중간(정정 1 체크리스트 준수 시) |
| 2 | 10(+테스트 4+) | Thymeleaf/JSP 이원화된 2단 배치 마크업 + CSS marker 계약 | 중간~높음 |

---

## 6. 테스트 목록 (신규만 표기)

| 대상 | 테스트 |
|---|---|
| 0단계 | `FormColumnLayout.from()` 4케이스(single/two-column/null/invalid) |
| 1단계 | **`ScreenDataBindingResolver`가 JOIN 해석 후에도 `formColumnLayout`을 유지하는지(정정 1 핵심 회귀)** |
| 1단계 | `ScreenSpecification` 13-arg 하위호환 생성자 → `formColumnLayout=SINGLE_COLUMN` 정규화 |
| 2단계 | Thymeleaf regist/updt `TWO_COLUMN` 렌더링 — `<tr>` 안에 `<th>/<td>` 2쌍 생성 확인 |
| 2단계 | JSP regist/updt `TWO_COLUMN` 렌더링 — `.form-row-two-col` 래핑 확인 |
| 2단계 | 필드 수 홀수일 때 마지막 행 1쌍만 렌더링되는지(chunk 동작 확인) |
| 2단계 | `ensureFormColumnLayoutStyles()` 멱등성(2회 연속 실행 시 CSS 중복 없음) |

---

## 7. 승인 체크포인트

- 0단계: `FormColumnLayoutTest` 4케이스 통과
- 1단계: **`ScreenDataBindingResolverTest`의 JOIN 케이스에서 `formColumnLayout` 유지 확인(정정 1)** + `reviseScreenSpecification()`이 `formColumnLayout`을 불변 유지하는지 확인
- 2단계: Thymeleaf 2개 템플릿 + JSP 2개 템플릿 각각의 `TWO_COLUMN`/`SINGLE_COLUMN` 렌더링 스냅샷 통과 + CSS marker 멱등성 테스트 통과

이 문서는 계획 문서이며, 위 체크포인트가 통과해야 각 단계를 완료로 표시한다. 코드 수정은 사용자의 별도 승인 후 0단계부터 순서대로 진행한다. `actionPlacement`/`searchPanelPlacement`(구조적 섹션 재배치)는 1차 문서 §3-4/§5 권고에 따라 이번 목록 범위에서 제외했으며, 0~2단계가 실사용 검증된 뒤 별도 문서로 재논의한다.
