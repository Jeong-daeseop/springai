# KRDS Figma Role·Variant 구현명세서

> Semantic Role · Component Contract · Deterministic Variant Resolution

- 대상 시스템: `springai` Figma MCP 자동생성 파이프라인
- 기준 문서: [`KRDS_Figma_Role_Variant_표준방법론_가이드.md`](../guides/KRDS_Figma_Role_Variant_표준방법론_가이드.md)
- 작성일: 2026-08-11
- 문서 버전: 1.1.0
- 상태: 구현 기준안

## 1. 개요

### 1.1 목적

본 문서는 KRDS Component Set을 화면 목적에 맞게 결정적으로 선택하기 위한 서버·계약·Figma Plugin 구현 명세를 정의합니다. `ScreenSpecification`의 의미 정보가 `ComponentRegistry`와 Variant 결정표를 거쳐 유일한 Published Instance로 변환되도록 하는 것이 목적입니다.

### 1.2 구현 결론

현행 파이프라인은 Logical Type과 Registry를 이미 보유하고 있으나 Semantic Role, Screen Pattern, Variant Rule Set이 독립 계약으로 존재하지 않습니다. 또한 Registry 해석 및 Variant 선택 실패를 일부 경고와 폴백으로 처리합니다. 따라서 다음 변경을 하나의 릴리스 단위로 적용해야 합니다.

1. `ScreenSpecification`에 화면 의미를 표현하는 Pattern·Role·Field Mode·Action Role을 추가합니다.
2. `ComponentRegistryEntry`를 Component Contract로 확장합니다.
3. `ComponentRoleResolver`와 `VariantRuleResolver`를 서버의 결정 엔진으로 추가합니다.
4. 해결된 Component Key와 Variant Property를 `FigmaScreenSpec`에 명시합니다.
5. 서버와 Plugin에서 첫 Variant·일반 Frame·로컬 이름 기반 폴백을 차단합니다.
6. Q&A 6개 화면을 구조·해석·Layout·Visual 회귀 Fixture로 고정합니다.

### 1.3 범위

본 구현의 1차 범위는 다음과 같습니다.

- Desktop 기반 `crud.list`, `crud.detail`, `crud.create`, `crud.edit`
- `page.header`, `search.panel`, `data.table`, `data.pagination`
- `form.container`, `form.section`
- `field.text`, `field.textarea`, `field.select`, `field.checkbox`
- `action.primary`, `action.secondary`, `action.destructive`
- KRDS/eGovFrame Published Component Set의 Property·Variant 검증
- Figma Bundle Preview 생성 전 4개 서버 Gate와 Plugin Apply 전 최종 Gate

반응형 Component Swap, 모바일 전용 Pattern, 고급 Table 상호작용은 후속 범위로 분리합니다.

## 2. 현행 구현 분석

### 2.1 재사용 가능한 기반

| 영역 | 현행 구현 | 재사용 방안 |
|---|---|---|
| 의미 명세 | `ScreenSpecification`, `PageSpec`, `ScreenFieldBinding` | Pattern·Role Context를 추가하여 확장 |
| Figma 실행 명세 | `FigmaScreenSpec`, `FigmaNodeSpec` | 해석 결과와 버전 참조를 추가하여 v2로 확장 |
| Registry | `ComponentRegistry`, `ComponentRegistryEntry` | Component Contract 필드를 추가하여 v2로 확장 |
| Logical Type 해석 | `ComponentRegistryResolver` | alias·replacement 해석기로 유지 |
| Registry 검증 | `ComponentRegistryValidator` | Role·Property·Variant Axis·Rule 정합성 검증 추가 |
| Builder | `ListFigmaScreenBuilder`, `FormFigmaScreenBuilder`, `DetailFigmaScreenBuilder` | 의미 트리 생성기로 역할 축소 |
| Export | `FigmaScreenExportService` | Pattern→Role→Variant→Preflight 순서를 총괄 |
| Bundle | `FigmaExportBundle`, Snapshot 모델 | Rule Set·Pattern 버전 Snapshot 추가 |
| Plugin | `figma-screen-spec-plugin` | 서버 해석 결과를 검증·적용하는 실행기로 변경 |
| 승인·이력 | Operation·Review·Registry Sync | Preview·승인·Rollback 흐름 재사용 |

### 2.2 확인된 구조적 격차

| ID | 근거 | 문제 | 영향 |
|---|---|---|---|
| GAP-01 | `ScreenFieldBinding.role`은 `UiFieldRole`의 업무 데이터 역할임 | `field.text`, `action.primary`와 같은 UI Semantic Role이 없음 | 업무 역할과 UI 구성 역할을 구분할 수 없음 |
| GAP-02 | `PageSpec.actions`가 `List<String>`임 | Action 의미·위험도·상태를 문자열로 추론함 | `DELETE` 외 변형이나 지역화에 취약 |
| GAP-03 | `BuilderSupport.actionButton()`이 `variant`를 직접 기록함 | Builder가 의미 분석과 Variant 결정을 동시에 수행함 | Rule 변경 시 Java Builder 수정 필요 |
| GAP-04 | `FieldComponentMapper`가 미지원 Control을 `krds.textField`로 대체함 | 지원하지 않는 컴포넌트가 조용히 생성됨 | 실제 업무 Control과 Figma 결과 불일치 |
| GAP-05 | `ListFigmaScreenBuilder`가 Table Cell을 `TEXT` 노드로 생성함 | 실제 Library Instance 사용 원칙 위반 | Table 구조·상태·토큰 연결 상실 |
| GAP-06 | `FigmaScreenExportService.resolveProfile()`이 빈 기본 Profile을 생성함 | Published Profile 부재가 생성 차단 조건이 아님 | 빈 Registry Bundle 생성 가능 |
| GAP-07 | `checkComponentRegistry()`가 미등록 컴포넌트를 WARNING으로 처리함 | 실패가 Plugin 폴백으로 전가됨 | 서버 결과가 성공 또는 부분 성공으로 저장됨 |
| GAP-08 | `ComponentRegistryEntry.variants`가 Variant 이름→Key Map임 | Axis, 허용 값, 필수 Axis, Context 규칙이 없음 | 결정표 기반 해석 불가 |
| GAP-09 | Plugin `selectVariantName()`이 조건이 없으면 첫 Variant를 반환함 | Component Set 순서가 결과를 결정함 | 동일 요구에서도 Library 편집 순서에 따라 결과 변동 |
| GAP-10 | Plugin이 Variant 불일치 시 첫 Component를 선택함 | 0개 매칭을 실패로 처리하지 않음 | 잘못된 State·Density·Type 인스턴스 생성 |
| GAP-11 | Plugin이 import 실패 시 Placeholder 또는 로컬 이름 후보를 사용함 | 승인된 Published Key 계약이 우회됨 | 결과의 출처·버전·품질 보장 불가 |
| GAP-12 | `FigmaScreenSpecValidator`가 구조와 중복 ID 중심임 | Pattern·Role·Variant·Layout 검증이 없음 | 6개 화면 누락과 Variant 오류를 사전 검출하지 못함 |
| GAP-13 | Plugin은 `DETAIL`을 미지원으로 차단함 | 6개 화면 중 상세 화면을 완결할 수 없음 | 3개 프레임 수준으로 축소될 위험 |

## 3. 설계 원칙

### 3.1 화면 생성 3계층 아키텍처

화면 생성 파이프라인은 시각 후보, 업무 계약, 결정적 KRDS 생성의 세 계층으로 분리합니다.

```text
JSP·HTML·Thymeleaf·기존 Figma·화면 캡처·스케치·자연어
                              ↓
                  Visual Candidate Generator
                     generate_figma_design
                              ↓
                    시각적 후보·레이아웃 제안
                              ↓
             ScreenSpecification 후보 작성·검증
                              ↓
                      업무 담당자 승인
                              ↓
                  ScreenSpecification v2
                      Source of Truth
                              ↓
               Deterministic Screen Generator
             Semantic Builder + KRDS Runtime Resolver
                              ↓
                  FigmaScreenSpec v2 + Bundle
                              ↓
                  Figma Plugin Preview·Apply
```

#### 3.1.1 Visual Candidate Generator

`generate_figma_design`은 기존 소스와 이미지 자료를 바탕으로 시각적 후보를 생성합니다. 출력은 레이아웃·Section·필드 배치·시각 위계에 대한 제안이며 승인된 업무 계약이 아닙니다.

허용 입력은 다음과 같습니다.

- JSP·HTML·Thymeleaf와 관련 Fragment·CSS
- 기존 Figma 화면
- 화면 캡처와 직접 그린 스케치
- 자연어 요구사항과 디자인 참고 이미지

Visual Candidate는 다음 값을 확정할 수 없습니다.

- DB Column·API Binding·Route
- 필수 여부·권한·검증 규칙
- KRDS Component Set Key·Variant Key
- Figma Property ID·Rule ID·Context Hash

동일 입력으로도 시각 결과가 달라질 수 있으므로 Visual Candidate를 `FigmaScreenSpec`, Bundle 또는 Component Registry로 직접 승격하지 않습니다.

#### 3.1.2 Source Reference와 후보 분석

소스 파일과 이미지는 Role 내부에 원문을 저장하지 않고 별도 Reference로 연결합니다.

```text
SourceReference
├── referenceId
├── type: JSP | HTML | THYMELEAF | FIGMA | SCREENSHOT | SKETCH
├── path 또는 artifactId
├── selector 또는 nodeId
├── usage: STRUCTURE | LAYOUT | VISUAL | IMPLEMENTATION
├── checksum
└── analyzedAt
```

Reference 분석 결과는 `ScreenSpecification` 후보를 만드는 근거로만 사용합니다. 템플릿이나 JavaScript를 실행하지 않고 정적 분석하며, 외부 URL·비밀정보·개인정보는 보안 검증과 마스킹을 거칩니다.

#### 3.1.3 ScreenSpecification Source of Truth

`ScreenSpecification`은 다음 업무 계약을 확정합니다.

- 화면 ID·이름·Pattern·Route·Form Mode
- Field·Data Binding·필수 여부·읽기/쓰기 Mode
- Action·Label·Role·이동 대상·권한
- Section·Slot·필드 순서와 Layout 제약

Visual Candidate, 기존 소스 또는 캡처와 승인된 `ScreenSpecification`이 충돌하면 다음 우선순위를 적용합니다.

```text
승인된 ScreenSpecification
> 승인된 Screen Pattern·Component Registry·Variant Rule Set
> 검증된 기존 구현 소스
> Visual Candidate·화면 캡처·스케치
```

후보는 `DRAFT` 또는 `REVIEW_REQUIRED`로 저장하며, 업무 담당자의 승인을 통과한 `APPROVED` 명세만 결정적 생성기로 전달합니다.

#### 3.1.4 Deterministic Screen Generator

공통 Builder와 KRDS Runtime Resolver는 승인된 업무 계약을 Figma 실행 명세로 변환합니다.

```text
동일 ScreenSpecification Version
+ 동일 Profile Version
+ 동일 Registry Version
+ 동일 Screen Pattern Version
+ 동일 Variant Rule Set Version
= 동일 FigmaScreenSpec Resolution과 Context Hash
```

Builder는 의미 기반 노드 구조만 생성하고, Runtime Resolver가 Role → Logical Type → Variant → Property를 해결합니다. Builder와 Plugin이 Component 또는 Variant를 임의 선택하지 않습니다.

#### 3.1.5 앞단 승인 Gate

결정적 생성기 진입 전 다음 조건을 모두 충족해야 합니다.

- 필수 Field·Action·Route가 업무 담당자에게 확인됨
- DB·API Binding이 유효하거나 명시적인 미연결 상태로 검토됨
- Source Reference의 출처·Checksum·분석 결과가 기록됨
- Visual Candidate에서 추출한 항목과 ScreenSpecification 간 차이가 검토됨
- ScreenSpecification 상태가 `APPROVED`임
- 승인자·승인 시각·ScreenSpecification Version이 기록됨

`generate_figma_design`의 성공은 승인 Gate 통과를 의미하지 않습니다. 승인되지 않은 후보로 Runtime Resolver, Bundle 생성 또는 Plugin Apply를 실행하지 않습니다.

### 3.2 서버·Plugin 책임 경계

```text
소스 정적 분석기 또는 Visual Candidate Generator
  └─ 업무 의미·시각 후보 추출
      ↓
승인된 ScreenSpecification v2
  └─ Pattern, Semantic Role, Context만 보유
      ↓
ScreenPatternValidator
      ↓
ComponentRoleResolver
  └─ Role → CURRENT Logical Type
      ↓
VariantRuleResolver
  └─ Context → Figma Variant Property 조합
      ↓
ComponentContractPreflight
  └─ Key, Property, Axis, 실제 Variant 일치 검증
      ↓
FigmaScreenSpec v2
  └─ 완전히 해결된 Published Instance 실행 명세
      ↓
Figma Plugin PREVIEW → 사람 승인 → APPLY
      ↓
LayoutValidator → VisualRegressionValidator
```

### 3.3 강제 규칙

- Visual Candidate는 ScreenSpecification 승인 없이 Bundle 또는 Apply 입력이 될 수 없습니다.
- ScreenSpecification은 Figma Key·Variant 이름·Property ID를 보유하지 않습니다.
- Builder는 Semantic Node Tree만 생성하며 Component·Variant를 선택하지 않습니다.
- 서버에서 해결되지 않은 Role 또는 Variant가 하나라도 있으면 Bundle을 생성하지 않습니다.
- `CURRENT`가 아닌 Registry Entry는 신규 화면 생성에 사용할 수 없습니다.
- Figma Plugin은 Component 또는 Variant를 추론하지 않습니다.
- Component Set의 첫 자식, 이름 유사도, 기본값, 일반 Frame·Text를 생성 폴백으로 사용하지 않습니다.
- 모든 Field, Button, Table Cell은 Registry가 지정한 Published Instance여야 합니다.
- Preview는 허용하되 FATAL 또는 ERROR가 있으면 Apply를 비활성화합니다.

## 4. 도메인 모델 명세

### 4.1 Semantic Role

신규 패키지 `com.krdevops.springai.model.design.role`에 다음 타입을 추가합니다.

```java
public enum SemanticRole {
    PAGE_HEADER("page.header"),
    SEARCH_PANEL("search.panel"),
    DATA_TABLE("data.table"),
    DATA_TABLE_CELL("data.table.cell"),
    DATA_PAGINATION("data.pagination"),
    FORM_CONTAINER("form.container"),
    FORM_SECTION("form.section"),
    FIELD_TEXT("field.text"),
    FIELD_TEXTAREA("field.textarea"),
    FIELD_SELECT("field.select"),
    FIELD_CHECKBOX("field.checkbox"),
    ACTION_PRIMARY("action.primary"),
    ACTION_SECONDARY("action.secondary"),
    ACTION_DESTRUCTIVE("action.destructive");
}
```

JSON은 enum 이름이 아닌 `page.header` 형식의 안정된 코드로 직렬화합니다. 알 수 없는 값은 자동 치환하지 않고 역직렬화 오류로 처리합니다.

### 4.2 Screen Pattern

```java
public enum ScreenPattern {
    CRUD_LIST("crud.list"),
    CRUD_DETAIL("crud.detail"),
    CRUD_CREATE("crud.create"),
    CRUD_EDIT("crud.edit");
}
```

`FigmaScreenType.FORM`은 등록과 수정을 구별하지 못하므로 유지하되, Variant 해석은 `ScreenPattern`을 우선 사용합니다.

### 4.3 화면 Context

```java
public record ComponentResolutionContext(
        ScreenPattern pattern,
        FigmaScreenType screenType,
        Platform platform,
        LayoutDensity density,
        FieldMode mode,
        ComponentState state,
        Boolean required,
        Boolean disabled,
        Integer fieldCount,
        SemanticRole role
) {}
```

`platform`, `density`, `mode`, `state`는 enum으로 제한합니다. 문자열 기반 조건 비교를 금지합니다.

### 4.4 ScreenSpecification v2

기존 모델의 DB·업무 필드 정보는 유지하며 다음 의미 필드를 확장합니다.

```java
public record PageSpec(
        String id,
        String template,
        ScreenPattern pattern,
        List<ScreenFieldBinding> fields,
        List<ScreenActionSpec> actions,
        FieldSelectionSource selectionSource
) {}

public record ScreenFieldBinding(
        String id,
        String label,
        UiFieldRole dataRole,
        SemanticRole semanticRole,
        FieldMode mode,
        FieldSource source,
        boolean visible,
        boolean required,
        boolean searchable,
        boolean sortable,
        String control,
        double confidence
) {}

public record ScreenActionSpec(
        String id,
        String command,
        SemanticRole role,
        String label,
        ComponentState state
) {}
```

호환 생성자는 한시적으로 유지합니다. 기존 문자열 Action과 Control은 `ScreenSemanticNormalizer`가 명시적 표를 사용하여 v2 의미로 변환합니다. 변환할 수 없는 값은 `SEMANTIC_ROLE_NOT_DERIVED` 오류로 승인 단계에서 차단합니다.

### 4.5 Screen Pattern Contract

신규 모델은 다음 구조를 사용합니다.

```java
public record ScreenPatternDefinition(
        String patternId,
        String version,
        List<SlotDefinition> slots
) {}

public record SlotDefinition(
        SemanticRole role,
        int minCount,
        Integer maxCount,
        List<SemanticRole> allowedChildren,
        int order
) {}
```

초기 정의는 `website-figma-contract/screen-patterns-v1.json`으로 관리합니다. `maxCount=null`은 무제한을 뜻합니다.

화면 하나의 Slot 검증과 여러 화면으로 구성된 업무 묶음 검증은 분리합니다. `ScreenPatternValidator`는 한 화면의 구조를 검증하고, `ScreenSuiteManifestValidator`는 Q&A와 같은 업무 묶음의 기대 화면 수·ID·Pattern을 검증합니다.

```java
public record ScreenSuiteManifest(
        String suiteId,
        String version,
        List<ExpectedScreen> screens
) {}

public record ExpectedScreen(
        String screenId,
        ScreenPattern pattern,
        boolean required
) {}
```

### 4.6 Component Contract v2

`ComponentRegistryEntry`에 다음 필드를 추가합니다.

```java
public record ComponentRegistryEntry(
        String componentSetKey,
        String componentName,
        PublishStatus publishStatus,
        LifecycleStatus lifecycleStatus,
        String replacementLogicalType,
        List<String> aliases,
        Set<SemanticRole> roles,
        Set<Platform> supportedPlatforms,
        Map<String, VariantAxisDefinition> variantAxes,
        Map<String, String> variants,
        Map<String, PropertyMapping> properties,
        Set<String> requiredProperties,
        String codeComponent,
        String documentationUrl
) {}
```

Lifecycle은 `DRAFT`, `CURRENT`, `DEPRECATED`, `REMOVED`로 정규화합니다. 기존 `PublishStatus`와 중복되는 상태는 마이그레이션 기간에만 병행하고, 신규 Resolver는 `lifecycle=CURRENT`와 `publishStatus=CURRENT`를 모두 요구합니다.

```java
public record VariantAxisDefinition(
        String logicalName,
        String figmaProperty,
        Set<String> allowedValues,
        boolean required
) {}
```

### 4.7 Variant Rule Set

```java
public record VariantRuleSet(
        String id,
        String version,
        String profileId,
        String registryVersion,
        List<VariantRule> rules
) {}

public record VariantRule(
        String ruleId,
        int priority,
        SemanticRole role,
        RuleCondition when,
        Map<String, String> result
) {}
```

Rule의 `result`는 논리 Axis 이름과 논리 값으로 저장합니다. Figma Property 이름과 실제 값으로의 변환은 Component Contract의 `variantAxes`와 `PropertyMapping.values`를 사용합니다.

동일 우선순위의 Rule 두 개가 같은 Context에 일치하면 `VARIANT_RULE_AMBIGUOUS`로 차단합니다. 일치 Rule이 없으면 기본값을 사용하지 않고 `VARIANT_RULE_NOT_FOUND`로 차단합니다.

### 4.8 해결 결과

`FigmaNodeSpec`의 `type`은 추적용 Logical Type으로 유지하고, 다음 `componentResolution`을 추가합니다.

```java
public record ResolvedComponentRef(
        SemanticRole role,
        String logicalType,
        String componentSetKey,
        String variantKey,
        Map<String, String> variantProperties,
        Map<String, Object> componentProperties,
        String contractVersion,
        String ruleSetVersion
) {}
```

Published Key는 Bundle과 승인된 REST/Plugin 경로에만 포함합니다. MCP 텍스트 응답은 현행 `FigmaResponseRedactor` 정책에 따라 Key를 제거합니다.

## 5. 계약 및 Schema 명세

### 5.1 계약 버전

| 계약 | 신규 버전 | 변경 유형 |
|---|---|---|
| `figma-screen-spec` | `figma-screen-spec-v2` | Breaking |
| `component-registry` | `component-registry-v2` | Breaking |
| `figma-export-bundle` | `figma-export-bundle-v2` | Breaking |
| `screen-patterns` | `screen-patterns-v1` | 신규 |
| `variant-rule-set` | `variant-rule-set-v1` | 신규 |

v1 읽기 호환은 유지하되, v1 Bundle은 Role·Variant 결정 보장이 없으므로 Apply를 허용하지 않고 Migration Preview만 제공합니다.

### 5.2 추가 파일

- `website-figma-contract/screen-patterns-v1.schema.json`
- `website-figma-contract/screen-patterns-v1.json`
- `website-figma-contract/variant-rule-set-v1.schema.json`
- `website-figma-contract/variant-rule-set-krds-v1.json`
- `website-figma-contract/component-registry-v2.schema.json`
- `website-figma-contract/figma-screen-spec-v2.schema.json`
- `website-figma-contract/figma-export-bundle-v2.schema.json`
- `website-figma-contract/fixtures/qna/*.json`

### 5.3 추가 버전 참조

`FigmaScreenSpec.DesignSystemRef` 또는 Bundle Metadata에 다음 값을 고정합니다.

- `profileVersion`
- `registryVersion`
- `screenPatternVersion`
- `variantRuleSetVersion`
- `componentContractVersion`
- `screenSpecificationVersion`

하나라도 조회 시점과 다르면 `*_VERSION_MISMATCH`로 Preview를 차단합니다.

## 6. Resolver 명세

### 6.1 ComponentRoleResolver

입력은 `SemanticRole`, `ComponentResolutionContext`, `ComponentRegistry`입니다. 처리 규칙은 다음과 같습니다.

1. `roles`에 요청 Role을 포함하는 Entry를 조회합니다.
2. `publishStatus=CURRENT`, `lifecycle=CURRENT`만 남깁니다.
3. 요청 Platform이 `supportedPlatforms`에 포함된 후보만 남깁니다.
4. alias·replacement는 기존 `ComponentRegistryResolver`로 정규화합니다.
5. 후보가 1개이면 Logical Type과 Contract를 반환합니다.
6. 후보가 0개이면 `ROLE_NOT_RESOLVED`, 복수이면 `ROLE_AMBIGUOUS`를 반환합니다.

Registry Map 순서, 컴포넌트 이름 유사도 및 첫 번째 후보는 선택 기준으로 사용하지 않습니다.

### 6.2 VariantRuleResolver

입력은 해결된 Contract, Context, `VariantRuleSet`입니다.

1. Role이 일치하는 Rule만 선택합니다.
2. Rule Condition의 모든 값이 Context와 일치하는 Rule을 선택합니다.
3. 가장 구체적인 조건 수와 명시적 `priority`를 기준으로 정렬합니다.
4. 최상위 후보가 정확히 하나인지 확인합니다.
5. Rule 결과의 모든 Axis가 Contract에 존재하는지 확인합니다.
6. 논리 값을 실제 Figma 값으로 변환합니다.
7. 조합한 `Property=Value` 집합과 Registry의 Published Variant Map을 완전 일치 비교합니다.
8. 일치 Variant Key가 정확히 하나일 때만 `ResolvedComponentRef`를 반환합니다.

부분 일치, 대소문자 무시, 누락 Axis 보완은 허용하지 않습니다. Text·Boolean Property는 별도 Property Mapping을 통해 변환합니다.

### 6.3 ScreenSemanticNormalizer

기존 ScreenSpecification과 LLM 결과를 다음 표로 정규화합니다.

| 입력 | Semantic Role | Context 보강 |
|---|---|---|
| Page Header | `page.header` | Page Pattern, Density |
| 검색 가능 Field 그룹 | `search.panel` | Field Count, Compact 권장 규칙 |
| `TEXT`, `NUMBER`, `DATE` | `field.text` | Mode, Required, State |
| `TEXTAREA` | `field.textarea` | Mode, Required, State |
| `SELECT` | `field.select` | Mode, Required, State |
| `CHECKBOX` | `field.checkbox` | Mode, Required, State |
| `CREATE`, `SAVE`, `UPDATE`, `SEARCH` | `action.primary` | Enabled/Disabled |
| `LIST`, `CANCEL`, `VIEW_DETAIL` | `action.secondary` | Enabled/Disabled |
| `DELETE` | `action.destructive` | Enabled/Disabled |

`DATE`를 `field.text`로 처리하는 규칙은 Registry에 Date Picker Contract가 정식 등록되기 전 임시 명시 규칙입니다. 자동 폴백이 아니라 버전 관리되는 Rule로 기록합니다.

## 7. Builder 및 Export 통합 명세

### 7.1 Builder 책임 변경

Builder는 Semantic Node Tree만 생성합니다. Logical Type과 Variant를 직접 선택하지 않습니다.

- `BuilderSupport.pageHeader()`: `role=page.header`만 기록
- `BuilderSupport.actionButton()`: Action Role과 label만 기록
- `FieldComponentMapper`: `FieldSemanticRoleMapper`로 교체
- `ListFigmaScreenBuilder.dataTable()`: Cell을 `TEXT`가 아닌 `data.table.cell` Role로 생성
- `DetailFigmaScreenBuilder`: 각 Field에 `mode=READ_ONLY`를 기록
- `FormFigmaScreenBuilder`: CREATE와 EDIT Pattern을 구분

### 7.2 FigmaScreenExportService 처리 순서

```text
1. APPROVED ScreenSpecification 조회
2. PageSpec 선택 및 ScreenPattern 확정
3. Published DesignSystemProfile 조회
4. 정확한 ComponentRegistry·RuleSet·Pattern 버전 조회
5. Semantic Builder 실행
6. ScreenPatternValidator 실행
7. 모든 노드의 Role 해석
8. 모든 Component의 Variant 해석
9. ComponentContractPreflight 실행
10. FigmaScreenSpec v2 생성
11. Schema·Resolution Validator 실행
12. 오류가 없을 때만 저장 및 Bundle 생성
```

`resolveProfile()`의 빈 기본 Profile 생성은 제거합니다. 요청 Profile이 없거나 PUBLISHED가 아니면 즉시 실패합니다. `checkComponentRegistry()`는 경고 수집 메서드에서 차단형 `resolveComponents()`로 교체합니다.

### 7.3 저장 정책

Registry, Rule Set, Pattern은 JSON Snapshot으로 저장하므로 초기 구현은 기존 `LONGTEXT` 패턴을 재사용할 수 있습니다. 다음 테이블을 추가합니다.

```text
AI_VARIANT_RULE_SET
  PROFILE_ID, RULE_SET_VERSION, REGISTRY_VERSION, RULE_SET_JSON, CREATED_AT

AI_SCREEN_PATTERN
  PATTERN_ID, PATTERN_VERSION, PATTERN_JSON, CREATED_AT
```

두 테이블 모두 `(ID, VERSION)` 복합 PK와 불변 저장을 적용합니다. 동일 Version의 다른 내용은 충돌로 차단합니다.

## 8. Validation Gate 명세

### 8.1 Gate 1 — Specification

담당 클래스: `ScreenPatternValidator`

- Pattern별 필수 Slot과 Cardinality
- 필수 Field·Action 존재 여부
- CREATE/EDIT/DETAIL의 Field Mode 정합성

담당 클래스: `ScreenSuiteManifestValidator`

- 요청한 Screen ID 중복 여부
- 업무 묶음의 기대 화면 수와 필수 Screen ID
- 각 Screen ID와 Pattern의 정합성
- Q&A 6개 기대 화면 존재 여부

### 8.2 Gate 2 — Registry Contract

담당 클래스: `ComponentContractValidator`, `FigmaPropertyDriftValidator`

- CURRENT Lifecycle·Publish 상태
- Role·Platform 지원 여부
- 공개 Property 이름과 Type
- 필수 Property 존재 여부
- Variant Axis와 허용 값
- Registry의 Variant 이름과 실제 Figma Component Property 조합
- Component Set Key와 Variant Key의 유효성

Figma REST 또는 Author Plugin이 수집한 Library Snapshot을 입력으로 사용합니다. Key 원문은 보안 경계를 벗어나지 않습니다.

### 8.3 Gate 3 — Resolution

담당 클래스: `ComponentResolutionValidator`

- 모든 Semantic Role이 정확히 하나의 Logical Type으로 해결되었는지 확인
- 모든 Variant Rule이 정확히 하나의 조합을 반환하는지 확인
- 모든 UI 노드에 `ResolvedComponentRef`가 존재하는지 확인
- 일반 Frame·Text 및 Placeholder 폴백 플래그가 없는지 확인

### 8.4 Gate 4 — Layout·Accessibility

담당 모듈: Figma Plugin Preview + 서버 Generation Report 검증

- 화면 Bounding Box 이탈
- 형제 노드 중첩
- Auto Layout 정책과 Slot Layout 계약
- Field와 Button 최소 크기
- Focus, Error, Disabled, Read-only Variant 존재 여부
- Target Size 및 Focus Appearance 점검 결과

### 8.5 Gate 5 — Visual

담당 모듈: `jsp-design-extractor`의 Browser Gate 확장

- 동일 Viewport 스크린샷 생성
- 영역 Anchor 좌표와 크기 비교
- 픽셀 차이율 및 구조 차이율 계산
- 허용치를 초과한 화면의 Apply 차단
- 사람 승인 기록과 Operation 연결

초기 임계값은 Fixture 기준선을 만든 뒤 화면별로 승인합니다. 임계값을 코드 상수로 숨기지 않고 Fixture Manifest에 기록합니다.

## 9. 오류 코드 및 차단 정책

| 코드 | Severity | 처리 |
|---|---|---|
| `SCREEN_PATTERN_NOT_RESOLVED` | FATAL | Export 중단 |
| `PATTERN_REQUIRED_SLOT_MISSING` | ERROR | Bundle 생성 중단 |
| `PATTERN_SLOT_CARDINALITY_VIOLATION` | ERROR | Bundle 생성 중단 |
| `SEMANTIC_ROLE_NOT_DERIVED` | ERROR | 승인 차단 |
| `ROLE_NOT_RESOLVED` | FATAL | Export 중단 |
| `ROLE_AMBIGUOUS` | FATAL | Export 중단 |
| `COMPONENT_NOT_CURRENT` | FATAL | Export 중단 |
| `COMPONENT_PLATFORM_UNSUPPORTED` | ERROR | Export 중단 |
| `VARIANT_RULE_NOT_FOUND` | FATAL | Export 중단 |
| `VARIANT_RULE_AMBIGUOUS` | FATAL | Export 중단 |
| `VARIANT_AXIS_NOT_DECLARED` | ERROR | Registry 승인 차단 |
| `VARIANT_VALUE_NOT_ALLOWED` | ERROR | Registry 승인 차단 |
| `VARIANT_NOT_RESOLVED` | FATAL | Export 중단 |
| `VARIANT_AMBIGUOUS` | FATAL | Export 중단 |
| `COMPONENT_PROPERTY_DRIFT` | FATAL | Preflight 중단 |
| `REQUIRED_COMPONENT_PROPERTY_MISSING` | FATAL | Preflight 중단 |
| `UNAPPROVED_COMPONENT_FALLBACK` | FATAL | Plugin Apply 차단 |
| `PUBLISHED_COMPONENT_IMPORT_FAILED` | FATAL | Plugin Apply 차단 |
| `LAYOUT_OVERFLOW` | ERROR | Apply 차단 |
| `LAYOUT_OVERLAP` | ERROR | Apply 차단 |
| `VISUAL_DIFF_THRESHOLD_EXCEEDED` | ERROR | 사람 재검토 요구 |

`PARTIAL` 결과는 Preview 보조 정보에만 사용합니다. 실제 UI Component 폴백이 발생한 결과는 `FAILED`로 처리합니다.

## 10. Figma Plugin 변경 명세

### 10.1 Variant 선택 제거

Plugin의 `selectVariantName()`은 삭제하거나 검증 전용으로 변경합니다. Plugin은 서버가 제공한 `variantKey`로 Published Component를 직접 import합니다. Component Set을 import해야 하는 경우 `variantProperties`와 정확히 일치하는 자식이 하나인지 검증한 후 생성합니다.

다음 코드는 금지합니다.

```typescript
Object.keys(entry.variants ?? {})[0]
componentSet.children.find(child => child.type === "COMPONENT")
```

### 10.2 폴백 차단

- `ensureFallbackPlaceholder()`는 Migration Preview 시각화에만 사용합니다.
- 정상 Apply 경로에서 `planFallback()` 호출을 제거합니다.
- `findLocalComponent()`와 `findLocalComponentSet()`은 Published Apply 경로에서 제거합니다.
- import 실패는 `PUBLISHED_COMPONENT_IMPORT_FAILED` FATAL로 반환합니다.
- `fallbackCount > 0`이면 Generation Report 상태를 무조건 `FAILED`로 처리합니다.

### 10.3 DETAIL 지원

`validateBundle()`의 DETAIL 차단을 제거하고 `crud.detail` Pattern, Read-only Field, Secondary·Destructive Action을 지원합니다. Q&A 상세 및 답변 상세 Fixture가 모두 통과해야 합니다.

### 10.4 Property 적용

`applyOwnedProperties()`의 사용자 수정 보존 정책은 유지합니다. 다만 다음 조건을 추가합니다.

- Contract에 선언되지 않은 Property는 적용하지 않음
- 필수 Property 누락 시 Apply 중단
- Variant Property는 서버 해결 결과와 Instance의 실제 값이 일치하는지 재검증
- 내부 `TEXT` 노드 탐색 및 문자열 덮어쓰기 금지

## 11. MCP·REST 인터페이스 영향

### 11.1 기존 Tool 변경

| Tool | 변경 내용 |
|---|---|
| `generateFigmaScreenSpec` | Role·Variant 해석 실패 시 저장하지 않고 차단형 오류 반환 |
| `validateFigmaScreenSpec` | Pattern·Resolution·Contract Version 검증 포함 |
| `preflightComponentRegistry` | Required Logical Type 대신 Role·Context 또는 Screen Spec 입력 지원 |
| `createFigmaBundleFromApprovedSpecification` | v2 Bundle만 PREVIEW_READY로 전환 |

### 11.2 신규 Tool 후보

도구 수 증가보다 기존 Tool 확장을 우선합니다. 운영상 독립 실행이 필요한 경우에만 다음 도구를 추가합니다.

- `validateKrdsVariantRuleSet`
- `previewKrdsComponentResolution`
- `validateKrdsScreenPatterns`

신규 Tool은 `FigmaToolAuthorizationService` 인증과 Key Redaction을 동일하게 적용합니다.

## 12. 보안·감사·운영 명세

- Component Set Key와 Variant Key는 MCP 응답과 일반 로그에서 마스킹합니다.
- Rule 적용 결과에는 `ruleId`, `ruleSetVersion`, Context Hash를 기록합니다.
- 각 노드의 해결 경로를 Generation Report에 남깁니다.
- Preview 승인에는 Screen Spec, Registry, Pattern, Rule Set 버전을 모두 기록합니다.
- Registry 또는 Rule Set 변경 후 이전 Snapshot으로 Rollback할 수 있어야 합니다.
- 동일 입력과 동일 버전 조합은 동일한 `ResolvedComponentRef`를 생성해야 합니다.

권장 운영 지표는 다음과 같습니다.

- `figma_role_resolution_failure_total`
- `figma_variant_resolution_failure_total`
- `figma_component_property_drift_total`
- `figma_fallback_attempt_total`
- `figma_visual_gate_failure_total`
- `figma_resolution_duration_seconds`

## 13. 마이그레이션 명세

### 13.1 단계

1. v2 Schema와 모델을 추가하고 v1 Reader를 유지합니다.
2. 운영 KRDS Library에서 Contract Inventory를 추출합니다.
3. v1 Registry를 v2 후보로 변환하고 Preview 검증합니다.
4. `screen-patterns-v1`과 `variant-rule-set-krds-v1`을 승인합니다.
5. 서버 Resolver를 Shadow Mode로 실행하여 기존 결과와 비교합니다.
6. Q&A 6개 Fixture를 통과한 뒤 v2 Bundle 생성을 기본값으로 전환합니다.
7. Plugin Apply에서 v1 Bundle을 차단합니다.
8. 폴백 경로를 Migration Preview 전용으로 격리합니다.

### 13.2 호환 정책

- 기존 DB JSON은 삭제하거나 일괄 덮어쓰지 않습니다.
- v1 Registry는 조회 가능하나 신규 Apply에 사용할 수 없습니다.
- v1 ScreenSpecification은 Normalizer를 통해 v2 후보로 변환한 뒤 재승인을 요구합니다.
- 동일 Registry Version에 v2 내용을 덮어쓰지 않고 신규 Major Version을 발급합니다.

## 14. 테스트 명세

### 14.1 Java 단위 테스트

- `ScreenPatternValidatorTest`
- `ComponentRoleResolverTest`
- `VariantRuleResolverTest`
- `ComponentContractValidatorTest`
- `FigmaPropertyDriftValidatorTest`
- `ScreenSemanticNormalizerTest`
- `FigmaScreenExportServiceRoleVariantTest`

필수 경계값은 0개 후보, 1개 후보, 복수 후보, 필수 Axis 누락, 잘못된 Value, Deprecated Replacement, Version 불일치입니다.

### 14.2 계약 테스트

`figmaContractTest`에 v2 정상·오류 Fixture를 추가합니다.

- 모든 Q&A Screen Spec v2
- Role 누락
- Pattern Slot 누락
- Rule 0개·복수 일치
- Property Drift
- Registry·Rule Set·Pattern Version 불일치

### 14.3 Plugin 테스트

- Variant Axis가 완전 일치할 때만 인스턴스 생성
- Variant 0개·복수이면 FATAL
- 첫 Variant 선택이 발생하지 않음
- Published import 실패 시 Placeholder를 만들지 않음
- 로컬 이름 후보를 사용하지 않음
- DETAIL 화면 Preview·Apply 지원
- `fallbackCount=0` 강제

### 14.4 Q&A 6개 회귀 테스트

| Fixture | Pattern | 필수 검증 |
|---|---|---|
| `qna-list` | `crud.list` | SearchPanel, 6열 DataTable, Pagination, 등록 |
| `qna-create` | `crud.create` | 연락처, 이메일, Checkbox, 제목, 내용, 등록·목록 |
| `qna-detail` | `crud.detail` | 전체 Read-only, 수정·삭제·목록 |
| `qna-answer-list` | `crud.list` | 검색, 답변 대상 Table, Pagination |
| `qna-answer-detail` | `crud.detail` | 질문 Read-only, 답글·목록 |
| `qna-answer-create` | `crud.create` | 질문 Read-only, 상태 Select, 답변 Textarea, 등록·목록 |

6개 Fixture 모두 구조·Resolution·Layout·Visual Gate를 통과해야 릴리스할 수 있습니다.

## 15. 완료 기준

- `ScreenSpecification`과 Semantic Builder에 Figma Key·Figma Variant 이름이 없음
- 모든 UI Role이 정확히 하나의 CURRENT Logical Type으로 해결됨
- 모든 Variant가 Rule Set을 통해 정확히 하나로 해결됨
- Component Contract와 실제 Figma Property Drift가 0건임
- 서버 기본 Profile·빈 Registry 폴백이 제거됨
- Plugin의 첫 Variant·첫 Component·로컬 이름·Placeholder 폴백이 정상 Apply 경로에서 제거됨
- Field, Button, Table Cell이 모두 Published Library Instance임
- DETAIL을 포함한 Q&A 6개 화면이 생성됨
- 모든 Generation Report의 `fallbackCount`가 0임
- FINAL Apply 전 Preview와 사람 승인 이력이 존재함
- Registry·Pattern·Rule Set의 Version과 Rollback 경로가 검증됨

## 16. 관련 문서

- [KRDS Figma Role·Variant 표준방법론 가이드](../guides/KRDS_Figma_Role_Variant_표준방법론_가이드.md)
- [Semantic Figma Design System 구현목록](./12_Semantic_Figma_Design_System_Implementation_List.md)
- [Agent Design System FigmaScreenSpec 참조 아키텍처](./09_Agent_Design_System_FigmaScreenSpec_Reference_Architecture.md)
- [Semantic Figma Operations Runbook](./13_Semantic_Figma_Operations_Runbook.md)
- [KRDS Figma Role·Variant 구현목록](./KRDS_Figma_Role_Variant_구현목록.md)
- [Figma 화면 생성 3계층 역할 가이드](./Figma_화면생성_3계층_역할가이드.md)
