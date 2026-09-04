# CRUD 상세·등록·수정 2단 폼 — 폼 입력 필드 수 기반 자동 전환 구현 계획 (옵션 2)

> 작성일: 2026-09-04
> 대상: `buildFullCrudPrompt`(Thymeleaf) auto 경로의 `FormColumnLayout` 결정
> 방식: 옵션 2 — 임계값을 `CrudModelFactory` 한 곳에만 두고, dormant한 `LayoutTypeResolver.resolveFormColumnLayout`은 제거

---

## 구현 완료 (2026-09-04) — 계획 대비 차이

Thymeleaf 범위 구현·검증 완료. 계획 대비 조정:

1. **정책 클래스 위치·이름**: `com.krdevops.springai.model.design.FormColumnLayoutPolicy` (계획의 `service/generation/crud/CrudFormColumnLayoutPolicy` 아님). `service` → `service.generation.crud` 패키지 순환을 피하고, `FormColumnLayout`·`ScreenSpecification`과 같은 패키지에 둠. 메서드 `resolve()`, 상수 `TWO_COLUMN_MIN_FORM_FIELDS = 10`.
2. **CSS selector 특이도**: `.krds-table-wrap .tbl.col.egov-layout-two-col ...` 사용 (계획의 `.egov-form-table.egov-layout-two-col`은 base `.krds-table-wrap .tbl.col th{width:180px}`에 밀려 무효였음).
3. **base 템플릿 동기화**: `src/main/resources/templates/egov/styles.css.tpl`의 marker 블록도 `FormColumnLayoutCssContract.CSS`와 동일하게 갱신 (`KrdsStylesConfigurerTest.generatedBaseFormColumnLayoutMarkerMatchesRuntimePatchContract`가 일치 강제).
4. **`LayoutTypeResolver` 정리 = 2a(삭제)**: `resolveFormColumnLayout` + `FormColumnLayoutDecision` record + `LayoutTypeResolverTest.testSingleColumnFormLayout` + 관련 `FormColumnLayout` import 제거.

5. **[계획 누락 → 추가] Renderer Profile 템플릿 세트 해시 갱신**: `TemplateSetFingerprintService`가 `templates/crud/*.ftl` 28종을 SHA-256으로 fingerprint하고, `renderer-profile-thymeleaf-krds-v1.json`이 그 `templateSetHash`를 pin한다. 상세/등록/수정 `-body.ftl` 3종을 고쳤으므로 해시가 바뀌어 `RendererProfileValidator`가 `TEMPLATE_SET_HASH_MISMATCH`로 `buildFullCrudPrompt`를 차단했다. 갱신 위치 2곳:
   - `website-figma-contract/renderer-profile-thymeleaf-krds-v1.json` `templateSetHash`: `14534bdc…` → `e70a93e3…`
   - `TemplateSetFingerprintServiceTest.java` golden 상수 동일 갱신
   - `contentHash`(`8e2801b4…`)는 JSON 필드와 `RendererProfileReference.DEFAULT_CONTENT_HASH` 두 곳이 서로 문자열 일치만 하면 되고 실제 내용 대비 재계산·검증은 없어 그대로 둠. `templateSetVersion`(`crud-thymeleaf-1.0`)도 유지(코드 상수 + 테스트가 pin).

**검증**: `FormColumnLayoutPolicyTest`(4), `CrudModelFactoryTest`(29, +4), `LayoutTypeResolverTest`(8, −1), `KrdsStylesConfigurerTest`(13), `CrudTemplateRendererTest`(46), `CrudTemplateIntegrationTest`(22), `CrudOrchestrationServiceTest`(22), `GenerationBaselineFixtureTest`(8), `BindingComposerTest`(18), `generation.crud.*`(~30), `generation.mcp.*`(4), `service.renderer.*`(17 — `TemplateSetFingerprintServiceTest`·`RendererProfileLoaderTest` 포함), `RestMcpWorkflowCrossE2ETest`(9), `MasterDetailTemplateRendererTest`(18), `CrudScreenSourceToolTest`(5) — 전부 통과, 0 실패. 전체 `./gradlew test`도 실행.

**후속(미구현)**: JSP `jsp-detail.jsp.ftl` 2단, 3단 폼, 값 긴 필드 full-width, `layoutDensity` heuristic 배선.

---

## 1. 확정된 결정

| 항목 | 값 |
|---|---|
| 기준 리스트 | `formFields` (전체 컬럼 − PK − 감사 4종 `frstRegistPnttm`/`frstRegisterId`/`lastUpdtPnttm`/`lastUpdusrId`) = 실제 사용자가 입력·조작하는 필드 |
| 임계값 | `formFields.size() >= 10` → `TWO_COLUMN` (상수, 조정 가능) |
| 7~9개 | `SINGLE_COLUMN` 유지 (회색지대는 보수적으로; 나중에 상수만 낮추면 됨) |
| heuristic 발동 조건 | `screenSpecification == null`(디자인 참조 없는 순수 스키마 생성)일 때만. 디자인 참조가 있으면 `screenSpec.formColumnLayout()` 존중 |
| 적용 화면 | 상세·등록·수정 3종 모두 |
| 상세 가드 | `effectiveDetailFields?size >= 6`이 아니면 `TWO_COLUMN`이어도 1단 유지 (6행짜리 2단 방지) |
| 홀수 tail | `?chunk(2)` 마지막 1개 쌍 → 빈 `<th></th><td></td>` 채움 |
| 임계값 리터럴 위치 | `CrudFormColumnLayoutPolicy.TWO_COLUMN_MIN_FORM_FIELDS` 한 곳 |
| `LayoutTypeResolver.resolveFormColumnLayout` | 삭제 (2a) — 외부 참조 0건 확인됨. 보존 원하면 `@Deprecated`(2b) |

### 배경: 왜 `LayoutTypeResolver`를 정리하나

- `LayoutTypeResolver`는 2026-08-02 커밋 `17e46bf`(I-4A)에서 **레거시 Thymeleaf 변환 파이프라인용**으로 생성됨.
- 소비자에 연결되기 전, 2026-08-03 커밋 `162bb3c`에서 그 파이프라인(`LegacyBindingContractAssembler`, `ThymeleafSkeletonPlanner`, `ThymeleafConversionOrchestrationService` 등 30여 파일)이 통째로 삭제되고 승인 기반 `ThymeleafProjectWorkflowService`로 교체됨.
- 새 경로(`BindingComposer`)는 레이아웃을 승인된 `ScreenSpecification`에서 직접 읽으므로 필드 수 heuristic이 불필요 → `LayoutTypeResolver`는 배선되지 않은 채 잔존.
- `resolveFormColumnLayout`의 `> 6` 임계값은 **한 번도 실행/검증된 적 없는 초안**. `FormColumnLayoutDecision` record와 `resolveFormColumnLayout`은 `LayoutTypeResolver.java` 내부와 `LayoutTypeResolverTest` 외에 참조 0건 (`grep` 확인). `TWO_COLUMN` 분기를 검증하는 테스트도 없음(기본값 SINGLE_COLUMN 케이스만 존재).
- 우리 `>= 10`을 추가하면 같은 판정에 숫자가 다른 규칙 두 개가 됨 → 나중에 `LayoutTypeResolver`가 배선되면 조용한 충돌. 옵션 2로 초안 규칙을 제거해 단일화.

---

## 2. 범위

**포함**: `buildFullCrudPrompt` auto 경로의 Thymeleaf 상세/등록/수정 3종, CSS 계약, dormant 코드 정리, 테스트.

**제외 (후속 항목)**:
- `jsp-detail.jsp.ftl` 2단 (JSP viewType) — 필요 시 별도 후속
- 3단 이상 폼
- 값이 긴 필드의 full-width(`colspan`) 예외 처리
- `layoutDensity` heuristic 배선 (`LayoutTypeResolver.resolveLayoutDensity`도 orphan이나 이번 범위 밖)
- `LayoutTypeResolver`의 나머지 메서드(`resolveActionPlacement`/`resolveSearchPanelPlacement`/`resolveLayoutDensity`)

---

## 3. 변경 세트

### 3.1 [신규] `CrudFormColumnLayoutPolicy`

`src/main/java/com/krdevops/springai/service/generation/crud/CrudFormColumnLayoutPolicy.java`

```java
package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.ScreenSpecification;
import org.jspecify.annotations.Nullable;

/**
 * 등록/수정/상세 폼의 단수(1단/2단) 결정 규칙 — 임계값 단일 출처.
 *
 * <p>디자인 참조(ScreenSpecification)가 있으면 그 값을 그대로 존중한다.
 * 디자인 참조가 없는 순수 스키마 생성일 때만, 폼 입력 필드 수 기반 heuristic을 적용한다.</p>
 */
public final class CrudFormColumnLayoutPolicy {

    /** 이 값 이상의 폼 입력 필드(PK·감사컬럼 제외)면 2단. 조정은 이 상수 한 곳만. */
    public static final int TWO_COLUMN_MIN_FORM_FIELDS = 10;

    private CrudFormColumnLayoutPolicy() {}

    public static FormColumnLayout resolve(int formFieldCount, @Nullable ScreenSpecification screenSpec) {
        if (screenSpec != null) {
            return screenSpec.formColumnLayout() == null
                    ? FormColumnLayout.SINGLE_COLUMN : screenSpec.formColumnLayout();
        }
        return formFieldCount >= TWO_COLUMN_MIN_FORM_FIELDS
                ? FormColumnLayout.TWO_COLUMN : FormColumnLayout.SINGLE_COLUMN;
    }
}
```

### 3.2 `CrudModelFactory.fromSchema` — 삼항 교체

`src/main/java/com/krdevops/springai/service/CrudModelFactory.java` (약 203~205행, `CrudTemplateModel` 생성 인자)

```java
// 변경 전
screenSpecification == null
        ? FormColumnLayout.SINGLE_COLUMN : screenSpecification.formColumnLayout(),

// 변경 후
CrudFormColumnLayoutPolicy.resolve(formFields.size(), screenSpecification),
```

- `formFields`는 같은 메서드 약 146행에서 이미 확정 (`buildFormFields(nonPkFields, screenSpecification)` + label override). label override는 개수에 영향 없음.
- `import com.krdevops.springai.service.generation.crud.CrudFormColumnLayoutPolicy;` 추가.
- `layoutDensity`/`actionPlacement`/`searchPanelPlacement` 삼항은 그대로 둠 (이번 범위 아님).

**부수 효과 (자동, 추가 배선 불필요)**: `CrudFormColumnCssProcessor.supports()` = `model.formColumnLayout() != SINGLE_COLUMN` → 위 결과가 `TWO_COLUMN`이면 `PRE_WRITE` 단계에서 `KrdsStylesConfigurer.ensureFormColumnLayoutStyles(outputPath)`가 `styles.css`를 멱등 보강. (`CrudGenerationPlanner:370`에 이미 `ProcessorStep`으로 등록됨)

### 3.3 `thymeleaf-detail-body.html.ftl` — 2단 분기 + 가드

`src/main/resources/templates/crud/thymeleaf-detail-body.html.ftl`

`<table>` ~ `</tbody>` 구간(현재 17~26행) 교체:

```ftl
<#assign twoColDetail = (formColumnLayout == "TWO_COLUMN") && (effectiveDetailFields?size >= 6)>
            <table class="tbl col egov-form-table<#if twoColDetail> egov-layout-two-col</#if>">
                <caption>${domainKr} 상세 정보</caption>
                <tbody>
<#if twoColDetail>
<#list effectiveDetailFields?chunk(2) as pair>
                <tr>
<#list pair as f>
                    <th scope="row">${f.comment}</th>
                    <td th:text="${'$'}{result.${f.javaName}}"></td>
</#list>
<#if pair?size == 1>
                    <th></th>
                    <td></td>
</#if>
                </tr>
</#list>
<#else>
<#list effectiveDetailFields as f>
                <tr>
                    <th scope="row">${f.comment}</th>
                    <td th:text="${'$'}{result.${f.javaName}}"></td>
                </tr>
</#list>
</#if>
                </tbody>
            </table>
```

### 3.4 `thymeleaf-regist-body.html.ftl` / `thymeleaf-updt-body.html.ftl` — 홀수 tail

두 파일 모두 `<#if formColumnLayout == "TWO_COLUMN">` → `formFields?chunk(2)` 루프의 내부 `<#list pair as f> … </#list>` 닫힌 직후, `</tr>` 앞에 삽입:

```ftl
<#if pair?size == 1>
                    <th></th>
                    <td></td>
</#if>
```

(PK 루프는 원래 1단 유지 — 변경 없음.)

### 3.5 `FormColumnLayoutCssContract.CSS` — 4열 정렬 강화

`src/main/java/com/krdevops/springai/service/FormColumnLayoutCssContract.java` — marker(`START_MARKER`/`END_MARKER`)는 그대로, 본문 교체:

```java
public static final String CSS = """

/* === egov-form-column-layout:start === */
.egov-form-table.egov-layout-two-col { table-layout: fixed; }
.egov-form-table.egov-layout-two-col th { width: 140px; }
.egov-form-table.egov-layout-two-col td { width: auto; }
.egov-form-table.egov-layout-two-col th:empty,
.egov-form-table.egov-layout-two-col td:empty { border: 0; background: transparent; }
@media (max-width: 768px) {
  .egov-form-table.egov-layout-two-col { table-layout: auto; }
  .egov-form-table.egov-layout-two-col tr,
  .egov-form-table.egov-layout-two-col th,
  .egov-form-table.egov-layout-two-col td { display: block; width: auto; }
  .egov-form-table.egov-layout-two-col th:empty,
  .egov-form-table.egov-layout-two-col td:empty { display: none; }
}
.form-row-two-col { display: flex; gap: 16px; }
.form-row-two-col .form-group { flex: 1; min-width: 0; }
/* === egov-form-column-layout:end === */
""";
```

- `table-layout: fixed` + 첫 행 4셀(th 140px 고정, td 2개가 잔여폭 균등) → 상세(읽기전용)·등록·수정 공통 정렬.
- `:empty` 규칙으로 홀수 tail 빈 셀의 테두리 제거.
- 768px 이하에서 1단으로 collapse. **KRDS `_ds_bundle.css`의 기존 테이블 반응형 규칙과 충돌 여부 확인 필요** — 충돌 시 `@media` 블록은 KRDS 기본에 위임하고 제거.

### 3.6 `LayoutTypeResolver` 정리 (2a: 삭제)

`src/main/java/com/krdevops/springai/service/thymeleaf/LayoutTypeResolver.java`
- `public FormColumnLayoutDecision resolveFormColumnLayout(ScreenSpecification spec)` 메서드 삭제 (약 80~117행)
- `public record FormColumnLayoutDecision(...)` 삭제 (약 186행)
- `import ...FormColumnLayout;`가 다른 곳에서 안 쓰이면 제거
- 클래스 Javadoc에서 `formColumnLayout` 언급 제거, 주석 추가: `// formColumnLayout 결정은 CrudFormColumnLayoutPolicy로 이관됨 (2026-09)`

`src/test/java/com/krdevops/springai/service/thymeleaf/LayoutTypeResolverTest.java`
- `testSingleColumnFormLayout` 테스트 삭제 (약 63~75행)
- 미사용 `import ...FormColumnLayout;` 제거

> **2b 대안(보존)**: 삭제 대신 `resolveFormColumnLayout`에 `@Deprecated(forRemoval = true)` + Javadoc `"CrudFormColumnLayoutPolicy로 대체됨. 배선 금지."`. 테스트는 그대로. 외부 참조가 0건이므로 2a(삭제)를 권장.

---

## 4. 테스트 계획

| 파일 | 추가/수정 |
|---|---|
| **`CrudFormColumnLayoutPolicyTest`** (신규) | `resolve(10, null)==TWO_COLUMN`, `resolve(9, null)==SINGLE_COLUMN`, `resolve(0, null)==SINGLE_COLUMN`, `resolve(50, spec[SINGLE_COLUMN])==SINGLE_COLUMN`(spec 존중), `resolve(3, spec[TWO_COLUMN])==TWO_COLUMN`(spec 존중), `resolve(20, spec[null 내부])==SINGLE_COLUMN` |
| **`CrudModelFactoryTest`** | 스키마만(screenSpec=null)으로 `fromSchema` 호출 시: 비PK·비감사 컬럼 10개 → `model.formColumnLayout()==TWO_COLUMN`; 9개 → `SINGLE_COLUMN`. 감사 4컬럼은 카운트에서 빠지는지 확인(물리 13 = PK1+감사4+업무8 → SINGLE) |
| **`KrdsStylesConfigurerTest`** | `FormColumnLayoutCssContract.CSS` 기대 문자열 갱신. 멱등성(2회 patch 시 1개 블록) 유지 확인 |
| **`LayoutTypeResolverTest`** | (2a) `testSingleColumnFormLayout` 삭제 / (2b) 유지 |
| **FTL 렌더 테스트** (`CrudTemplateRendererTest` 등 기존 상세 렌더 테스트 있으면) | `formColumnLayout=TWO_COLUMN` + detailFields 10개 → 렌더 결과에 `egov-layout-two-col`, `<tr>`당 `<th>` 2개. detailFields 4개 → 가드로 1단 유지. 홀수(예: 5개) → 마지막 `<tr>`에 빈 셀 2개 |
| **E2E/골든** | `RestMcpWorkflowCrossE2ETest` 등에서 ≥10 컬럼 fixture가 2단으로 바뀌며 기대값 shift 나는지 점검, 필요 시 기대 HTML 갱신 |

---

## 5. 회귀 영향 & 롤아웃

- **동작 변경**: `buildFullCrudPrompt`(스키마 전용)를 비PK·비감사 컬럼 ≥10개 테이블에 실행하면 상세/등록/수정 3종이 2단으로 생성되고 `styles.css`에 `egov-form-column-layout` 블록이 append(멱등)됨.
- 기존 생성된 프로젝트는 **재생성 전까지 영향 없음**. 재생성 시 `@region:protected:*` 커스텀 구간은 보존, 테이블 본문은 기존과 동일하게 재생성.
- 디자인 참조 경유(`screenSpecificationId`/`designReferenceId`) 생성은 **동작 불변** (heuristic이 `screenSpec != null`이면 미발동, 기존처럼 spec 값 사용).
- `LayoutTypeResolver`는 프로덕션 미배선이므로 3.6 삭제는 런타임 영향 0.

---

## 6. 검증 절차

1. `./gradlew test --tests "*CrudFormColumnLayoutPolicy*" --tests "*CrudModelFactory*" --tests "*KrdsStylesConfigurer*" --tests "*LayoutTypeResolver*" --tests "*CrudTemplateRenderer*"`
2. `./gradlew build`
3. MCP 서버 재기동 후 `buildFullCrudPrompt(database="ebt", tableName="LETTNEMPLYRINFO", domain="Employer", packageName="egovframework.let.emp", outputPath=..., llmProvider="auto", viewType="thymeleaf", egovVersion="5.0")`
   - `EgovEmployerDetail/Regist/Updt.html`에 `class="tbl col egov-form-table egov-layout-two-col"`, `<tr>`당 `<th scope="row">` 2개 확인
   - `src/main/resources/static/resources/css/styles.css`에 `/* === egov-form-column-layout:start === */` 블록 1개 확인
4. (가능 시) 브라우저 렌더로 2단·모바일 collapse·홀수 tail 확인

---

## 7. 근거 (파일:라인)

| 항목 | 위치 |
|---|---|
| `formColumnLayout` 삼항 (교체 대상) | `CrudModelFactory.java` ~203-205 |
| `formFields` 확정 지점 | `CrudModelFactory.java` ~146 (`buildFormFields`) |
| `SYSTEM_MANAGED_FIELDS` (감사 4종) | `CrudModelFactory.java:53-55` |
| CSS 자동 패치 트리거 | `CrudFormColumnCssProcessor.java` `supports()` + `CrudGenerationPlanner.java:370` |
| CSS 계약 | `FormColumnLayoutCssContract.java` |
| `effectiveDetailFields` 산출 | `CrudTemplateRenderer.java:237` |
| regist/updt 기존 2단 분기 | `thymeleaf-regist-body.html.ftl:37`, `thymeleaf-updt-body.html.ftl:35` |
| detail 2단 미지원 | `thymeleaf-detail-body.html.ftl:20` (`formColumnLayout` 미참조) |
| `resolveFormColumnLayout` 참조 0건 | `grep -rn 'resolveFormColumnLayout' src/` → `LayoutTypeResolver.java` + `LayoutTypeResolverTest.java`뿐 |
| `LayoutTypeResolver` orphan 경위 | 커밋 `17e46bf`(생성, 2026-08-02) → `162bb3c`(소비 파이프라인 삭제, 2026-08-03) |
