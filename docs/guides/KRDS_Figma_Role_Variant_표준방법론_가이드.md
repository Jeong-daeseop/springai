    # KRDS 기반 Figma 자동생성 표준 방법론 가이드

> Semantic Role · Component Contract · Deterministic Variant Resolution

- 대상: `springai` Figma MCP 자동생성 파이프라인
- 작성일: 2026-08-11
- 문서 버전: 1.0
- 상태: 적용 가이드

## 1. 실행 요약

`Role` 도입은 필요하지만 Role만으로는 부족하다. 안정적인 Figma 자동생성을 위해 다음 네 요소를 하나의 결정형 파이프라인으로 운영해야 한다.

1. **Semantic Role**: 화면에서 컴포넌트가 담당하는 의미
2. **Component Contract**: 공개 Property와 지원 Context에 대한 계약
3. **Variant 결정표**: Context를 Variant Property로 변환하는 규칙
4. **Validation Gate**: 잘못된 해석과 품질 저하를 차단하는 검증

권장 책임 분리는 다음과 같다.

```text
LLM: 화면의 의미와 요구사항 분석
          ↓
ScreenSpecification
          ↓
ScreenPatternValidator
          ↓
ComponentRoleResolver
          ↓
VariantRuleResolver
          ↓
ComponentRegistryPreflight
          ↓
FigmaScreenSpec
          ↓
use_figma
          ↓
LayoutValidator + VisualReview
```

핵심 원칙은 다음과 같다.

> Variant를 AI가 고르게 하지 말고, AI가 만든 의미 명세를 버전 관리되는 결정표가 Variant로 변환하게 한다.

## 2. 현재 방식이 실패한 원인

현재 Figma 자동생성 품질이 낮아진 원인은 다음과 같다.

- Component Set의 첫 번째 자식을 기본 Variant로 사용했다.
- 화면 Context에 맞는 `Type`, `State`, `Density`, `Size`를 결정하지 않았다.
- Component Property 대신 내부 `TEXT` 노드를 탐색해 문자열을 변경했다.
- `Hug contents`와 Auto Layout 정책을 확인하지 않고 크기를 강제로 변경했다.
- 실제 Field와 Table Cell 대신 일반 Text를 덧씌웠다.
- Registry 해석 실패를 중단하지 않고 Placeholder 또는 일반 Frame으로 폴백했다.
- 구조 검증은 수행했지만 시각적 유사성과 업무 완결성 검증이 부족했다.

이 문제는 프롬프트를 길게 작성하는 것만으로 해결할 수 없다. 의미 분석과 컴포넌트 결정을 분리해야 한다.

## 3. Semantic Role 체계

Role은 Figma 컴포넌트 이름이 아니라 화면에서 담당하는 의미다.

| Role | 의미 | 대표 Logical Type |
|---|---|---|
| `page.header` | 페이지 제목, 설명, Breadcrumb | `egov.pageHeader` |
| `search.panel` | 목록 검색 조건과 조회 액션 | `egov.searchPanel` |
| `data.table` | 목록형 데이터 표현 | `egov.dataTable` |
| `data.pagination` | 페이지 이동 | `krds.pagination` |
| `form.container` | 등록·수정 폼 전체 구조 | `egov.formPage` |
| `form.section` | 상세 또는 입력 필드 그룹 | `egov.formSection` |
| `field.text` | 단일행 입력 또는 읽기 전용 값 | `krds.textField` |
| `field.textarea` | 여러 줄 입력 | `krds.textarea` |
| `field.select` | 제한된 옵션 선택 | `krds.select` |
| `field.checkbox` | Boolean 선택 | `krds.checkbox` |
| `action.primary` | 화면의 핵심 완료 액션 | `krds.button` |
| `action.secondary` | 목록, 취소 등 보조 액션 | `krds.button` |
| `action.destructive` | 삭제 등 위험 액션 | `krds.button` |

### 3.1 Role 명명 규칙

- 도메인명보다 UI 의미를 우선한다. `qna.header`보다 `page.header`를 사용한다.
- Figma 파일명과 Component Set 이름을 Role에 포함하지 않는다.
- 시각 스타일을 Role에 포함하지 않는다. `blue.button`이 아니라 `action.primary`를 사용한다.
- 상태와 크기는 Role이 아니라 Context 및 Variant Axis로 분리한다.
- 하나의 Role은 교체 가능한 여러 Logical Type 후보를 가질 수 있다.

### 3.2 올바른 매핑

```text
Role: page.header
Logical Type: egov.pageHeader
Figma Component: eGovFrame/PageHeader
Code Component: KrdsPageHeader
```

Role은 안정적으로 유지하고 Figma·코드 컴포넌트는 Registry에서 교체 가능해야 한다.

## 4. Component Contract

각 Component Set은 자동화에서 사용할 수 있는 공개 계약을 가져야 한다.

```json
{
  "logicalType": "egov.pageHeader",
  "roles": ["page.header"],
  "figma": {
    "componentSetKey": "8c7d0632...",
    "propertyMap": {
      "title": "Title",
      "description": "Description",
      "screenType": "Type",
      "density": "Density",
      "showBreadcrumb": "Show breadcrumb"
    }
  },
  "variantAxes": {
    "screenType": ["List", "Detail", "Create", "Edit"],
    "density": ["Comfortable", "Compact"]
  },
  "requiredProperties": ["title", "screenType"],
  "supportedPlatforms": ["DESKTOP"],
  "lifecycle": "CURRENT"
}
```

### 4.1 계약 필수 항목

- Logical Type
- 지원 Semantic Role
- Figma Component 또는 Component Set Key
- 공개 Component Property Map
- Variant Axis와 허용 값
- 필수 Property
- 지원 Platform과 Viewport
- Lifecycle: `DRAFT`, `CURRENT`, `DEPRECATED`, `REMOVED`
- Registry Version과 Replacement Logical Type
- Code Component 및 문서 링크

### 4.2 계약 강제 규칙

- 내부 `TEXT` 노드를 탐색해 문구를 변경하지 않는다.
- 공개된 Text, Boolean, Variant, Instance Swap, Slot Property만 변경한다.
- Component Set의 첫 번째 자식을 기본 Variant로 사용하지 않는다.
- 필수 Property가 없으면 생성을 중단한다.
- Registry 정의와 Figma Property가 다르면 Drift 오류를 반환한다.
- Breaking Property 변경은 Preview 승인과 Major Version 갱신을 요구한다.

## 5. Variant 결정 방법

Variant 선택은 LLM 추론이 아니라 명시적인 결정표로 수행한다.

| Role | Screen Type | Context | 결정 Property |
|---|---|---|---|
| `page.header` | `LIST` | Desktop / Comfortable | `Type=List`, `Density=Comfortable` |
| `page.header` | `DETAIL` | Read-only | `Type=Detail`, `Density=Comfortable` |
| `search.panel` | `LIST` | Field 2개 | `FieldCount=2`, `Density=Compact` |
| `field.text` | `CREATE` | Required | `State=Default`, `Required=True` |
| `field.text` | `DETAIL` | Read-only | `State=ReadOnly` |
| `action.primary` | `CREATE` | Enabled | `Type=Primary`, `Size=Medium` |
| `action.destructive` | `DETAIL` | Enabled | `Type=Danger`, `Size=Medium` |

### 5.1 Resolver 알고리즘

1. Role이 일치하는 `CURRENT` 컴포넌트를 검색한다.
2. `screenType`, `platform`, `mode`, `state`, `density`로 후보를 필터링한다.
3. 결정표와 정확히 일치하는 Variant Property 조합을 계산한다.
4. Figma의 실제 Property 이름과 허용 값을 대조한다.
5. 후보가 정확히 하나일 때만 인스턴스를 생성한다.
6. 후보가 0개 또는 복수이면 생성을 차단한다.

### 5.2 금지되는 폴백

```java
componentSet.getChildren().getFirst();
```

다음과 같은 자동 폴백은 금지한다.

- 첫 번째 Variant 선택
- `DEFAULT` 강제 선택
- 이름 유사도만으로 결정
- Property 불일치 무시
- 일반 `FRAME` 또는 `TEXT`로 대체
- 임의 RGB, 폰트, 간격 생성

해석 실패는 다음처럼 명시적으로 반환한다.

```text
VARIANT_NOT_RESOLVED
role=search.panel
screenType=LIST
density=COMPACT
candidates=2
```

## 6. Screen Pattern과 Slot

Component 규칙만으로는 화면 전체의 업무 완결성을 보장할 수 없다. 화면 유형별 필수 Slot과 Cardinality가 필요하다.

| Pattern | 필수 Slot | 선택 Slot |
|---|---|---|
| `crud.list` | `page.header` 1, `data.table` 1 | `search.panel` 0..1, `pagination` 0..1, Primary Action 0..1 |
| `crud.detail` | `page.header` 1, `form.section` 1..n | Secondary Action 1..n, Destructive Action 0..1 |
| `crud.create` | `page.header` 1, `form.container` 1, Primary Action 1 | `form.section` 1..n, Field 0..n, Secondary Action 0..1 |
| `crud.edit` | `page.header` 1, `form.container` 1, Primary Action 1 | Destructive Action 0..1, Secondary Action 0..1 |

```yaml
pattern: crud.create
requiredSlots:
  - role: page.header
    count: 1
  - role: form.container
    count: 1
  - role: form.section
    count: 1..n
  - role: action.primary
    count: 1
optionalSlots:
  - role: field.text
    count: 0..n
  - role: field.select
    count: 0..n
  - role: action.secondary
    count: 0..1
```

이 규칙을 사용하면 원본 Q&A 6개 화면 중 답변 목록·상세·등록이 누락되는 문제를 사전에 검출할 수 있다.

## 7. ScreenSpecification 경계

ScreenSpecification에는 화면의 의미만 기록하고 Figma Component Key나 Variant 이름을 포함하지 않는다.

```json
{
  "screenId": "qna-answer-create",
  "screenType": "CREATE",
  "pattern": "crud.create",
  "platform": "DESKTOP",
  "density": "COMFORTABLE",
  "title": "Q&A 답변 등록",
  "fields": [
    {
      "id": "writer",
      "role": "field.text",
      "label": "등록자",
      "mode": "READ_ONLY"
    },
    {
      "id": "status",
      "role": "field.select",
      "label": "진행상태",
      "required": true
    },
    {
      "id": "answer",
      "role": "field.textarea",
      "label": "답변내용",
      "required": true
    }
  ],
  "actions": [
    {
      "role": "action.primary",
      "label": "등록"
    },
    {
      "role": "action.secondary",
      "label": "목록"
    }
  ]
}
```

Resolver는 위 명세를 다음처럼 Figma 실행 명세로 변환한다.

```json
{
  "logicalType": "krds.textField",
  "componentSetKey": "ed27981c...",
  "variantProperties": {
    "State": "ReadOnly",
    "Size": "Medium"
  },
  "textProperties": {
    "Label": "등록자"
  }
}
```

## 8. LLM의 책임 경계

### 8.1 LLM이 담당하는 것

- 화면 유형 분류
- 필드, 액션, 상태 추출
- Required와 Read-only 판단
- 목록·상세·등록 관계 분석
- 화면 설명과 Microcopy 생성
- 불확실성과 누락 후보 보고

### 8.2 LLM이 담당하지 않는 것

- Figma Component Key 선택
- Component Set 첫 번째 Variant 선택
- Variant Property 최종 확정
- 내부 Text Node 탐색
- 임의 RGB, 폰트, 간격 생성
- Auto Layout 크기 강제 변경
- Registry에 없는 컴포넌트 생성
- 해석 실패 시 Placeholder 폴백

LLM은 의도를 만들고 결정 엔진이 설계를 확정해야 한다.

## 9. 디자인 토큰 운영

토큰은 다음 3계층으로 운영한다.

```text
Primitive Token
  blue.700 / gray.100 / spacing.16
          ↓
Semantic Token
  color.action.primary / color.text.default / spacing.form.row
          ↓
Component Token
  button.primary.background
  table.header.background
  textField.border.focus
```

| 계층 | 책임 | 예시 |
|---|---|---|
| Primitive | 원시 색상, 크기, 간격 | `blue.700`, `spacing.16` |
| Semantic | 제품 의미와 상태 | `color.text.danger`, `spacing.form.row` |
| Component | 컴포넌트별 소비 계약 | `button.primary.background` |

생성 코드는 RGB와 픽셀 값을 직접 기록하지 않고 Semantic 또는 Component Token을 참조한다.

교환 형식은 DTCG 2025.10을 기준으로 삼을 수 있다. 다만 이는 W3C Recommendation이 아니라 W3C Community Group 규격이므로 조직 표준으로 채택한다는 내부 결정을 별도로 기록한다.

## 10. 품질 검증 Gate

| Gate | 차단 조건 | 대표 검사 |
|---|---|---|
| 1. Specification | 필수 화면·Slot 누락 | 화면 수, Pattern, Cardinality |
| 2. Registry | Key·Property·Lifecycle 불일치 | `CURRENT`, Version, Property Map |
| 3. Resolution | Variant 0개 또는 복수 | 정확히 1개 매칭, 폴백 없음 |
| 4. Layout | 겹침·영역 이탈·최소 크기 위반 | Bounding Box, Auto Layout, Focus |
| 5. Visual | Reference 대비 허용 오차 초과 | Screenshot Diff, Anchor 비교, 사람 승인 |

### 10.1 Specification 검증

- 요청한 모든 화면이 존재하는가
- Pattern별 필수 Slot이 존재하는가
- 필수 Field와 Action이 있는가
- Screen ID가 중복되지 않는가

### 10.2 Registry 검증

- Logical Type이 `CURRENT` 상태인가
- Component Key가 유효한가
- Component Property 이름이 일치하는가
- 요청한 Variant 값이 실제로 존재하는가

### 10.3 Resolution 검증

- 모든 Role이 정확히 하나의 컴포넌트로 해석되는가
- 첫 번째 Variant 폴백이 발생하지 않았는가
- 일반 Frame 또는 Text로 대체된 Field가 없는가

### 10.4 Layout 검증

- 노드가 화면 밖으로 벗어나지 않는가
- 컴포넌트끼리 겹치지 않는가
- `Hug contents`, `Fill container`, Fixed 정책이 Slot 계약과 일치하는가
- 입력 Field와 Button의 최소 크기가 적절한가
- Focus, Error, Disabled, Read-only 상태가 정의됐는가

접근성 검증에는 WCAG 2.2의 Focus Appearance와 Target Size 기준을 포함한다.

### 10.5 Visual Regression

- 원본과 생성 화면을 같은 Viewport로 렌더링한다.
- 주요 Anchor의 위치와 크기를 비교한다.
- 영역별 이미지 차이를 계산한다.
- 허용 임계값을 초과하면 Preview 승인을 차단한다.
- 최종 반영 전 사람의 검토를 요구한다.

## 11. 버전과 변경 관리

| 버전 대상 | 권장 방식 | Breaking 변경 예시 |
|---|---|---|
| Component Registry | Semantic Version | Logical Type 삭제, Property 이름 변경 |
| Design System Profile | 프로필별 독립 Version | Token Collection 교체 |
| Screen Pattern | Pattern Version | 필수 Slot 추가, Cardinality 변경 |
| Variant Rule | Rule Set Version | 조건 우선순위 또는 결과 Property 변경 |
| ScreenSpecification | 화면별 Revision | 업무 Field, Action 변경 |

Library Publish와 Component Key 교체는 다음 절차를 거쳐야 한다.

1. Preview 생성
2. Registry 영향 분석
3. Q&A 6개 Fixture 회귀 테스트
4. Design System Owner 검토
5. Breaking Change 승인
6. Publish 및 Registry Version 갱신
7. 문제 발생 시 이전 Version으로 Rollback

## 12. 조직 역할과 승인

| 담당 Role | 주요 책임 | 승인 대상 |
|---|---|---|
| Design System Owner | Role, Property, Variant Axis 정의 | Component Contract |
| Product Designer | Screen Pattern과 업무 흐름 정의 | Screen Pattern |
| Frontend Owner | Figma와 코드 컴포넌트 연결 | Code Connect, Logical Type |
| Registry Owner | Key, Version, Lifecycle, Alias 관리 | Registry Release |
| Automation Owner | Resolver, Validator, 회귀 테스트 구현 | Rule Set |
| Reviewer | Preview와 Breaking Change 검토 | FINAL Apply |

Component Set에 Variant를 추가하거나 Property를 변경할 때 Component Contract와 결정표를 함께 갱신해야 한다.

## 13. `springai` 적용 로드맵

### 1단계: Inventory

`PageHeader`, `SearchPanel`, `DataTable`, `FormPage`, `FormSection`, Field, Button의 Property와 Variant를 전수 추출한다.

### 2단계: Contract

각 Component Set에 Role, Logical Type, Property Map, Variant Axis, Lifecycle을 등록한다.

### 3단계: Pattern

`crud.list`, `crud.detail`, `crud.create`, `crud.edit` Pattern과 Slot Cardinality를 정의한다.

### 4단계: Resolver

첫 번째 Variant 폴백을 제거하고 결정표 기반 `ComponentRoleResolver`와 `VariantRuleResolver`를 구현한다.

### 5단계: Preflight

Component Registry, Design System Profile과 Figma Library의 Property Drift를 배치 전에 검사한다.

### 6단계: Validation

Instance, Layout, Accessibility, Screenshot Diff 검증 Gate를 자동화한다.

### 7단계: Regression

Q&A 6개 화면을 고정 Fixture로 등록하고 Registry 또는 Rule 변경 시마다 회귀 테스트한다.

### 8단계: Governance

Preview 승인, Breaking Change, Publish, Rollback 절차를 운영 규칙으로 확정한다.

## 14. Q&A 6개 회귀 Fixture

| 화면 | Pattern | 핵심 검증 |
|---|---|---|
| Q&A 목록 | `crud.list` | 검색, 6열 Table, Pagination, 등록 Action |
| Q&A 등록 | `crud.create` | 연락처, 이메일, Checkbox, 제목, 내용 |
| Q&A 상세조회 | `crud.detail` | Read-only Field, 수정·삭제·목록 |
| Q&A 답변 목록 | `crud.list` | 검색, 답변 대상 Table, Pagination |
| Q&A 답변 상세조회 | `crud.detail` | 질문 Read-only, 답글·목록 |
| Q&A 답변 등록 | `crud.create` | 질문 Read-only, 상태 Select, 답변 Textarea |

## 15. 구현 완료 기준

- [ ] ScreenSpecification에 Figma Key와 Variant 이름이 없다.
- [ ] 모든 Role이 Registry에서 정확히 하나의 `CURRENT` Logical Type으로 해석된다.
- [ ] 모든 Variant Axis가 결정표를 통해 정확히 하나의 조합으로 해석된다.
- [ ] 내부 Text 탐색과 첫 번째 Variant 폴백이 제거됐다.
- [ ] Field, Cell, Button이 실제 Library Instance다.
- [ ] 일반 Text 또는 Frame을 사용한 UI 폴백이 없다.
- [ ] 모든 디자인 값이 Semantic 또는 Component Token을 사용한다.
- [ ] Q&A 6개 Fixture가 구조·Layout·Visual Gate를 통과한다.
- [ ] FINAL 적용 전에 Preview와 사람의 승인이 존재한다.
- [ ] Breaking 변경의 Version과 Rollback 절차가 기록돼 있다.

## 16. 참고 자료

- [Figma: Create and use variants](https://help.figma.com/hc/en-us/articles/360056440594-Create-and-use-variants)
- [Figma: Explore component properties](https://help.figma.com/hc/en-us/articles/5579474826519-Explore-component-properties)
- [Figma: Component property fundamentals](https://help.figma.com/hc/en-us/articles/39636407507735-Components-collection-Component-property-fundamentals)
- [Design Tokens Format Module 2025.10](https://www.w3.org/community/reports/design-tokens/CG-FINAL-format-20251028/)
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/)

