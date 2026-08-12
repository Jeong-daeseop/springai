# KRDS Figma Role·Variant 구현 아키텍처 가이드

> 기준 문서: [KRDS Figma Role·Variant 구현명세서](./KRDS_Figma_Role_Variant_구현명세서.md) 1.1.0  
> 상세 역할 해설: [Figma 화면 생성 3계층 역할 가이드](./Figma_화면생성_3계층_역할가이드.md)

## 1. 핵심 결론

```text
ScreenSpecification에는 Figma Key를 저장하지 않는다.
Component와 Variant는 서버가 모두 결정한다.
Figma Plugin은 추론하지 않고 검증·적용만 한다.
```

전체 구조는 시각 후보 생성, 업무 명세 승인, 결정적 KRDS 화면 생성의 세 계층으로 구성한다.

```text
JSP·HTML·Thymeleaf·기존 Figma·캡처·스케치
                         ↓
             Visual Candidate Generator
                generate_figma_design
                         ↓
               시각적 화면 후보 생성
                         ↓
            ScreenSpecification 후보 작성
                         ↓
                업무 담당자 승인
                         ↓
       ScreenSpecification v2 · Source of Truth
                         ↓
                Semantic Builder
                         ↓
              Screen Pattern 검증
                         ↓
              Component Role 해석
                         ↓
                 Variant 해석
                         ↓
            Component Contract 검증
                         ↓
               FigmaScreenSpec v2
                         ↓
                     Bundle
                         ↓
              Figma Plugin Preview
                         ↓
                  사용자 승인
                         ↓
                  Atomic Apply
                         ↓
         Layout·Accessibility·Visual Gate
```

## 2. 계층별 책임

| 계층 | 핵심 책임 |
|---|---|
| Visual Candidate Generator | 화면의 시각적 후보 생성 |
| Source Reference | 기존 소스·이미지의 출처와 분석 근거 관리 |
| ScreenSpecification | 업무 의미와 화면 요구사항 확정 |
| Semantic Builder | 의미 기반 화면 구조 생성 |
| Screen Pattern Validator | 구조·Slot·순서 검증 |
| Component Role Resolver | Role에 맞는 Component 결정 |
| Variant Rule Resolver | 문맥에 맞는 Variant 결정 |
| Component Contract Preflight | 실제 Figma Library 계약 검증 |
| FigmaScreenSpec | 완전히 해결된 실행 명세 |
| Bundle | 실행에 필요한 버전별 계약 묶음 |
| Figma Plugin | 실제 Figma Preview·Apply |
| Quality Gate | Layout·접근성·Visual 검증 |

## 3. Visual Candidate Generator

담당 기능은 `generate_figma_design`이다.

### 3.1 입력

- JSP
- HTML
- Thymeleaf
- 기존 Figma 화면
- 화면 캡처
- 직접 그린 스케치
- 자연어 요구사항
- 디자인 참고 이미지

### 3.2 출력

- 화면 배치 후보
- Section 구성 제안
- 필드 위치 제안
- 시각적 위계 제안
- 여백·크기 제안
- 버튼 위치 제안

### 3.3 확정할 수 없는 정보

```text
DB Column
API Binding
Route
필수 여부
권한
업무 검증 규칙
Component Set Key
Variant Key
Figma Property ID
Rule ID
Context Hash
```

Visual Candidate는 비결정적 참고 자료다. 다음 경로는 허용하지 않는다.

```text
Visual Candidate → Bundle 직접 생성
Visual Candidate → 승인된 FigmaScreenSpec 직접 저장
Visual Candidate → Component Registry로 사용
```

## 4. Source Reference

JSP·HTML·Thymeleaf·이미지를 Role 속에 직접 저장하지 않고 별도 참조로 연결한다.

```text
SourceReference
├── referenceId
├── type
├── path 또는 artifactId
├── selector 또는 nodeId
├── usage
├── checksum
└── analyzedAt
```

지원 타입:

```text
JSP
HTML
THYMELEAF
FIGMA
SCREENSHOT
SKETCH
```

사용 목적:

```text
STRUCTURE
LAYOUT
VISUAL
IMPLEMENTATION
```

Reference 분석 결과는 ScreenSpecification 후보를 작성하는 근거로만 사용한다.

## 5. ScreenSpecification v2

ScreenSpecification은 최종 업무 기준인 Source of Truth다.

### 5.1 확정 정보

```text
화면 ID·이름
Screen Pattern
Route
Form Mode
Field
Data Binding
필수 여부
읽기·쓰기 Mode
Action
Action 이동 대상
권한
Section·Slot
필드 순서
Layout 제약
```

예:

```yaml
screenId: qna-update
name: 질문 수정
pattern: crud.edit
route: /qna/{id}/edit
formMode: UPDATE

fields:
  - id: title
    label: 제목
    semanticRole: field.text
    mode: EDITABLE
    required: true

actions:
  - id: save
    command: UPDATE
    role: action.primary
    label: 저장
```

### 5.2 금지 정보

ScreenSpecification에는 다음 값을 저장하지 않는다.

```text
componentSetKey
variantKey
Figma Variant 이름
Figma Property ID
Published Component Key
```

### 5.3 자료 충돌 우선순위

```text
1. 승인된 ScreenSpecification
2. 승인된 Screen Pattern·Component Registry·Variant Rule Set
3. 검증된 기존 구현 소스
4. Visual Candidate·화면 캡처·스케치
```

## 6. 앞단 승인 Gate

결정적 화면 생성 전에 다음 조건을 통과해야 한다.

```text
필수 Field 확인
필수 Action 확인
Route 확인
DB·API Binding 확인
Source Reference 출처 기록
Checksum 기록
Visual Candidate와 업무 명세 차이 검토
ScreenSpecification 상태 APPROVED
승인자 기록
승인 시각 기록
명세 Version 기록
```

상태 흐름:

```text
DRAFT
→ REVIEW_REQUIRED
→ APPROVED
→ Builder·Resolver 실행 가능
```

`generate_figma_design` 성공만으로 승인 Gate를 통과한 것으로 간주하지 않는다.

## 7. Semantic Role

Semantic Role은 UI 요소의 의미를 표현한다.

```text
page.header

search.panel

data.table
data.table.cell
data.pagination

form.container
form.section

field.text
field.textarea
field.select
field.checkbox

action.primary
action.secondary
action.destructive
```

Role과 Component는 분리한다.

```text
field.text
= 업무·UI 의미

krds.textField
= Registry가 연결한 구현 Component
```

## 8. Screen Pattern

지원 Pattern:

```text
crud.list
crud.detail
crud.create
crud.edit
```

### 8.1 목록

```text
PageHeader
→ SearchPanel
→ DataTable
→ Pagination
→ Action Area
```

### 8.2 등록

```text
PageHeader
→ Form Container
→ Form Section
→ Editable Fields
→ Action Area
```

### 8.3 상세

```text
PageHeader
→ Read-only Fields
→ Action Area
```

### 8.4 수정

```text
PageHeader
→ Form Container
→ Editable Fields
→ 저장·취소 Action
```

Pattern Validator는 필수 Slot, 부모·자식 관계, Slot 순서, 개수, 허용 Role과 Field Mode를 검사한다.

## 9. Semantic Builder

Builder는 승인된 업무 명세를 의미 기반 노드 트리로 변환한다.

```text
LIST
→ ListFigmaScreenBuilder

CREATE·EDIT
→ FormFigmaScreenBuilder

DETAIL
→ DetailFigmaScreenBuilder
```

Builder가 결정하는 내용:

```text
Page·Section 구조
Field 순서
부모·자식 관계
Slot
Logical Node ID
Semantic Role
Field Mode
Layout 속성
Action Area
```

Builder가 결정하지 않는 내용:

```text
Component Set Key
Variant Key
Variant 이름
Figma Property ID
Rule ID
Context Hash
```

Builder 결과 예:

```json
{
  "logicalNodeId": "qna-update/title",
  "nodeType": "COMPONENT",
  "properties": {
    "semanticRole": "field.text",
    "label": "제목",
    "mode": "EDITABLE",
    "required": true
  }
}
```

## 10. Component Resolution Context

Component와 Variant 선택에는 다음 화면 문맥을 사용한다.

```text
Screen Pattern
Screen Type
Platform
Layout Density
Field Mode
Component State
Required
Disabled
Field Count
Semantic Role
```

문자열 기반 추론을 피하고 enum으로 제한한다.

## 11. Component Role Resolver

역할:

```text
Semantic Role
→ 정확히 하나의 Logical Component
```

선택 절차:

```text
1. 요청 Role을 지원하는 Registry Entry 조회
2. publishStatus=CURRENT 확인
3. lifecycle=CURRENT 확인
4. Platform 지원 확인
5. Alias·Replacement 정규화
6. 후보가 정확히 하나인지 확인
```

결과:

```text
0개 → ROLE_NOT_RESOLVED
1개 → 정상
2개 이상 → ROLE_AMBIGUOUS
```

첫 번째 Component, 이름 유사 Component, Deprecated Component 또는 임의 TextField 대체를 사용하지 않는다.

## 12. Component Registry v2

Registry Entry는 Component Contract다.

```text
Component Set Key
Component 이름
Publish 상태
Lifecycle 상태
지원 Role
지원 Platform
Variant Axis
Published Variant Map
Property Mapping
필수 Property
Alias
Replacement
Contract Version
```

## 13. Variant Rule Resolver

역할:

```text
Component Resolution Context
→ Variant Property 조합
→ Published Variant Key
```

처리 순서:

```text
1. Role 일치 Rule 조회
2. Context 조건 완전 일치 확인
3. 구체성과 Priority 비교
4. 최상위 Rule 하나 확인
5. 결과 Axis의 Contract 존재 확인
6. 논리값을 Figma 값으로 변환
7. Registry Variant와 완전 일치
8. Variant Key 하나 확정
```

실패 처리:

```text
Rule 없음 → VARIANT_RULE_NOT_FOUND
Rule 복수 → VARIANT_RULE_AMBIGUOUS
Variant 없음 → VARIANT_NOT_RESOLVED
Variant 복수 → VARIANT_AMBIGUOUS
```

부분 일치와 기본값 보완은 허용하지 않는다.

## 14. ResolvedComponentRef

각 Component 노드에 다음 해석 결과를 기록한다.

```text
role
logicalType
componentSetKey
variantKey
variantProperties
componentProperties
contractVersion
ruleSetVersion
ruleId
contextHash
```

이 결과가 생성된 이후 Plugin은 Component나 Variant를 다시 선택하지 않는다.

## 15. Component Contract Preflight

서버가 결정한 Component를 실제 Figma Library Inventory와 비교한다.

```text
Component Set Key 존재
Variant Key 존재
Property 이름 존재
Property Type 일치
필수 Property 존재
Variant Axis 존재
허용값 일치
Registry와 Library Drift 0
```

주요 차단 오류:

```text
COMPONENT_PROPERTY_DRIFT
REQUIRED_COMPONENT_PROPERTY_MISSING
VARIANT_AXIS_NOT_DECLARED
VARIANT_VALUE_NOT_ALLOWED
```

## 16. FigmaScreenSpec v2

FigmaScreenSpec은 서버가 자동 생성한 Figma 실행 명세다.

```text
ScreenSpecification
= 사람이 승인하는 업무 원본

FigmaScreenSpec
= 서버가 생성한 Figma 실행 결과
```

포함 정보:

```text
화면 구조
Logical Node ID
Semantic Role
Resolved Component
Variant Key
Variant Property
Component Property
Profile Version
Registry Version
Pattern Version
Rule Set Version
Contract Version
ScreenSpecification Version
```

## 17. FigmaScreenExportService

서버 생성 파이프라인 실행 순서:

```text
1. APPROVED ScreenSpecification 조회
2. PageSpec 선택
3. Screen Pattern 확정
4. Published Profile 조회
5. 정확한 Registry Version 조회
6. 정확한 Rule Set Version 조회
7. 정확한 Pattern Version 조회
8. Semantic Builder 실행
9. Screen Pattern 검증
10. 모든 Role 해석
11. 모든 Variant 해석
12. Component Contract Preflight
13. FigmaScreenSpec v2 생성
14. Schema·Resolution 검증
15. 성공 시에만 DB 저장
16. Bundle 생성
```

저장 정책:

```text
FATAL 또는 ERROR 없음
→ FigmaScreenSpec 저장
→ Bundle 생성

FATAL 또는 ERROR 발생
→ FigmaScreenSpec 저장 금지
→ Bundle 생성 금지
→ Generation Report에 실패 기록
```

## 18. Bundle

```text
FigmaExportBundle
├── FigmaScreenSpec
├── DesignSystemProfile Snapshot
├── ComponentRegistry Snapshot
└── ExportMetadata
```

Metadata에 다음 버전을 고정한다.

```text
profileVersion
registryVersion
screenPatternVersion
variantRuleSetVersion
componentContractVersion
screenSpecificationVersion
```

최신 버전을 임의로 선택하지 않고 FigmaScreenSpec이 참조한 정확한 버전을 사용한다.

## 19. Figma Plugin

Plugin 담당:

```text
Bundle Schema 검증
v1·v2 판정
Published Variant Key Import
Property 적용
Layout Recipe 적용
Preview
사용자 승인
Atomic Apply
후검증
Generation Report
```

Plugin 금지 사항:

```text
Role 해석
Component 선택
Variant 선택
첫 Component 사용
첫 Variant 사용
이름 유사도 검색
로컬 Component 대체
정상 Apply에서 Placeholder 사용
```

### 19.1 Atomic Apply

```text
기존 Root 백업
→ Staging Root 생성
→ Instance Import
→ Property 적용
→ Layout 적용
→ 후검증
→ 성공 시 기존 Root 교체
```

실패 시 Staging을 삭제하고 기존 Root를 복구한다.

## 20. Validation Gate

### Gate 1: Specification

```text
ScreenPatternValidator
ScreenSuiteManifestValidator
```

검증 항목:

```text
화면 구조
Slot 개수와 순서
Field·Action
Mode 정합성
업무 묶음 화면 ID와 개수
```

### Gate 2: Registry Contract

```text
ComponentContractValidator
FigmaPropertyDriftValidator
```

검증 항목:

```text
CURRENT 상태
Role·Platform
Property
Variant Axis
필수 Property
Library Drift
```

### Gate 3: Resolution

```text
모든 Role 해결
모든 Variant 해결
ResolvedComponentRef 100%
unresolved 0
fallback 0
```

### Gate 4: Layout·Accessibility

```text
Overflow
Overlap
Auto Layout
최소 크기
Focus
Error
Disabled
Read-only
Target Size
```

### Gate 5: Visual

```text
Viewport
Anchor 좌표
영역 크기
픽셀 차이율
구조 차이율
사람 승인 기록
```

## 21. 실패 정책

```text
Role 미해결 → FATAL
Variant Rule 없음 → FATAL
Variant 복수 → FATAL
Registry Drift → FATAL
Published Import 실패 → FATAL
Fallback 발생 → FAILED
Layout Overflow → Apply 차단
Visual 차이 초과 → 사람 재검토
```

부분 성공 결과는 최종 성공으로 인정하지 않는다.

## 22. 데이터 저장 및 버전 정책

저장 대상:

```text
Design System Profile
Component Registry
Variant Rule Set
Screen Pattern
ScreenSpecification
FigmaScreenSpec
Library Inventory Snapshot
Generation Report
Operation·승인 기록
Source Reference
```

버전 정책:

```text
동일 ID·Version + 동일 내용
→ 멱등 성공

동일 ID·Version + 다른 내용
→ VERSION_CONFLICT

새로운 내용
→ 새 Version
```

## 23. 보안·감사

```text
Bundle·승인된 Plugin 경로
→ Component Key 포함 가능

MCP 텍스트 응답·일반 로그
→ Component Key 마스킹
```

추적 정보:

```text
ruleId
ruleSetVersion
contextHash
contractVersion
ScreenSpecification Version
승인자
승인 시각
```

## 24. 구현 책임 요약

| 단계 | 담당 |
|---|---|
| 시각 후보 | `generate_figma_design` |
| 소스·이미지 참조 | Source Reference 분석 |
| 업무 계약 | `ScreenSpecification` |
| 업무 승인 | 업무 담당자 |
| 의미 구조 | Builder |
| 구조 검증 | Screen Pattern Validator |
| Component 선택 | Component Role Resolver |
| Variant 선택 | Variant Rule Resolver |
| Library 정합성 | Contract Preflight |
| 실행 명세 | FigmaScreenSpec |
| 실행 패키지 | Bundle |
| 실제 Figma 적용 | Plugin |
| 최종 품질 | Layout·Accessibility·Visual Gate |

## 25. 남은 구현 과제

```text
SourceReference 모델·Repository
JSP·HTML·Thymeleaf 정적 분석 서비스
Visual Candidate와 ScreenSpecification 차이 검토 기능
앞단 승인 Gate 저장 모델
Q&A 업무 원본의 ScreenSpecification 전환
qna-update 추가
Q&A 6개 기준을 7개로 개정
Suite 전체 원자적 생성·저장
```

## 26. 최종 요약

```text
시각 아이디어
→ generate_figma_design

업무 확정
→ ScreenSpecification

결정적 KRDS 변환
→ Builder + Runtime Resolver

실행 명세
→ FigmaScreenSpec + Bundle

실제 화면
→ Plugin Atomic Apply
```
