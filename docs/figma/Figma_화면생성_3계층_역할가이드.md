# Figma 화면 생성 3계층 역할 가이드

> 공식 구현 계약: [KRDS Figma Role·Variant 구현명세서](./KRDS_Figma_Role_Variant_구현명세서.md)

## 1. 핵심 구조

Figma 화면 생성은 다음 세 역할로 분리한다.

```text
generate_figma_design
→ Visual Candidate Generator

ScreenSpecification
→ Source of Truth

Builder + KRDS Runtime Resolver
→ Deterministic Screen Generator
```

핵심 의미는 다음과 같다.

```text
generate_figma_design
→ 화면을 어떻게 보이게 할지 제안

ScreenSpecification
→ 화면에 무엇이 반드시 있어야 하는지 확정

Builder + Runtime Resolver
→ 확정된 내용을 KRDS 규칙으로 정확하게 생성
```

## 2. 전체 처리 흐름

```text
JSP·HTML·Thymeleaf·화면 캡처·스케치
                   ↓
        Visual Candidate Generator
          generate_figma_design
                   ↓
          시각적 후보·레이아웃 제안
                   ↓
         ScreenSpecification 작성
                   ↓
        업무 담당자 검토·승인
                   ↓
       Deterministic Screen Generator
        Builder + Runtime Resolver
                   ↓
          FigmaScreenSpec + Bundle
                   ↓
              Figma Plugin
                   ↓
       실제 KRDS Published Instance 화면
```

## 3. Visual Candidate Generator

### 3.1 담당

```text
generate_figma_design
```

화면의 시각적 후보를 빠르게 만드는 역할이다. 정답 화면이 아니라 업무 담당자와 디자이너가 검토할 수 있는 시안을 만든다.

### 3.2 입력

- 기존 JSP 화면
- HTML·Thymeleaf 화면
- 화면 캡처
- 직접 그린 스케치
- 자연어 설명
- 기존 Figma 화면
- 디자인 참고 이미지

요청 예시는 다음과 같다.

```text
기존 Q&A 등록 화면을 KRDS 스타일로 현대화해줘.
작성자·이메일은 한 행에 두고,
제목과 질문 내용은 전체 폭으로 배치해줘.
하단에는 취소와 등록 버튼을 우측 정렬해줘.
```

### 3.3 출력

- 화면 시안
- 레이아웃 후보
- Section 구성
- 필드 배치
- 시각적 위계
- 여백과 크기 제안
- 버튼 위치

```text
질문 등록
├── 신청자 정보
│   ├── 작성자
│   ├── 연락처
│   └── 이메일
├── 질문 정보
│   ├── 제목
│   ├── 질문 내용
│   └── 공개 여부
└── 취소 · 등록
```

### 3.4 확정하지 않는 항목

- 실제 DB 컬럼
- API Binding
- 필수 입력 여부
- 수정 권한
- Route
- 실제 KRDS Component Key
- Published Variant Key
- Component Property ID
- 업무 검증 규칙

예를 들어 시안에 이메일 필드가 있어도 다음 업무 정보는 이미지에서 확정할 수 없다.

```text
이메일이 필수인지
DB 컬럼명이 무엇인지
개인정보 마스킹 대상인지
관리자에게만 보이는지
수정 가능한지
```

### 3.5 결과의 성격

```text
후보
참고 자료
논의 대상
비결정적 결과
```

동일한 입력으로 다시 실행해도 여백이나 배치가 달라질 수 있으므로 Source of Truth로 사용하지 않는다.

## 4. ScreenSpecification

### 4.1 담당

```text
업무 화면의 Source of Truth
```

ScreenSpecification은 화면이 반드시 지켜야 하는 업무 계약이다. 화면의 시각적 표현보다 무엇이 있어야 하고 어떻게 동작해야 하는지를 확정한다.

### 4.2 입력

- 업무 요구사항
- DB Schema
- Controller·API
- 기존 JSP·HTML·Thymeleaf
- Visual Candidate
- 권한 정책
- 검증 규칙
- 업무 담당자 승인

### 4.3 화면 정보

```yaml
screenId: qna-update
name: 질문 수정
pattern: crud.update
route: /qna/{id}/edit
viewport: DESKTOP
formMode: UPDATE
```

### 4.4 필드 정보

```yaml
fields:
  - id: title
    label: 제목
    semanticRole: field.text
    required: true
    readOnly: false
    dataBinding: qna.title
```

### 4.5 배치 정보

```yaml
layout:
  parentId: question-section
  row: 1
  column: 1
  columnSpan: 12
  order: 10
```

### 4.6 Action 정보

```yaml
actions:
  - type: UPDATE
    label: 저장
    semanticRole: action.primary
    targetPageId: qna-detail

  - type: DETAIL
    label: 취소
    semanticRole: action.secondary
    targetPageId: qna-detail
```

### 4.7 업무 규칙

```yaml
rules:
  - 답변 완료 전에는 작성자만 수정 가능
  - 관리자는 모든 질문 수정 가능
  - 제목과 질문 내용은 필수
  - 비공개 질문은 작성자와 관리자만 조회 가능
```

### 4.8 Source of Truth의 의미

화면 캡처, Figma 시안, JSP와 ScreenSpecification이 서로 다르면 ScreenSpecification을 기준으로 판단한다.

```text
Figma 시안: 이메일이 선택값처럼 표시
ScreenSpecification: required=true
최종 결과: 필수 입력으로 생성
```

```text
JSP: 수정 버튼이 왼쪽 배치
ScreenSpecification: BOTTOM_RIGHT
최종 결과: 오른쪽 배치
```

### 4.9 버전 관리

승인된 ScreenSpecification은 같은 버전의 내용을 덮어쓰지 않고 새 버전으로 관리한다.

```text
qna-suite v3
→ qna-update 추가 전

qna-suite v4
→ qna-update 추가
→ 제목 필수
→ 취소 대상 qna-detail
```

## 5. Deterministic Screen Generator

### 5.1 담당

```text
공통 Builder
+ KRDS Runtime Resolver
```

승인된 ScreenSpecification을 실제 Figma 실행 명세로 변환한다.

`Deterministic`은 동일한 입력과 동일한 계약 버전이면 동일한 결과를 생성한다는 뜻이다.

```text
동일 ScreenSpecification
+ 동일 Profile
+ 동일 Registry
+ 동일 Rule Set
+ 동일 Screen Pattern
= 동일 FigmaScreenSpec
```

## 6. Builder 역할

Builder는 업무 명세를 의미 기반 화면 트리로 변환한다.

```text
LIST
→ ListFigmaScreenBuilder

CREATE·UPDATE
→ FormFigmaScreenBuilder

DETAIL
→ DetailFigmaScreenBuilder
```

### 6.1 Builder 입력

- ScreenSpecification
- PageSpec
- Field
- Action
- Layout
- Form Mode

### 6.2 Builder 출력

Builder 출력에는 실제 Figma Component Key가 없다.

```json
{
  "logicalNodeId": "qna-update/title",
  "nodeType": "COMPONENT",
  "type": "krds.textField",
  "properties": {
    "semanticRole": "field.text",
    "label": "제목",
    "mode": "EDITABLE",
    "required": true
  }
}
```

### 6.3 Builder가 결정하는 항목

- Page·Section 구조
- 필드 순서
- 부모·자식 관계
- Slot
- Logical Node ID
- Semantic Role
- Layout 속성
- Action Area

### 6.4 Builder가 결정하지 않는 항목

- Component Set Key
- Variant Key
- Figma Property ID
- Rule ID
- Context Hash

## 7. Runtime Resolver 역할

Runtime Resolver는 Builder가 만든 의미 기반 노드를 실제 KRDS Published Component로 해석한다.

### 7.1 입력

- Semantic Role
- Screen Pattern
- Form Mode
- Screen Type
- Field Mode
- Layout Density
- Viewport
- Component Registry
- Variant Rule Set

### 7.2 처리

```text
Role 확인
→ Registry Entry 검색
→ Rule 조건 평가
→ Component Variant 선택
→ Component Property 매핑
→ Contract Version 확인
→ Context Hash 생성
```

### 7.3 출력

```json
{
  "componentResolution": {
    "role": "field.text",
    "logicalType": "krds.textField",
    "componentSetKey": "e2643...",
    "variantKey": "f5e5...",
    "variantProperties": {
      "Size": "medium",
      "State": "default"
    },
    "componentProperties": {
      "LabelProperty": "제목",
      "PlaceholderProperty": "제목을 입력하세요"
    },
    "ruleId": "text-editable",
    "contractVersion": "2.1.0",
    "contextHash": "..."
  }
}
```

### 7.4 결정적 생성의 장점

#### 재현 가능

```text
개발자 A가 생성
개발자 B가 생성
CI에서 생성
서버 재시작 후 생성
```

모두 같은 계약 버전을 사용하면 같은 결과를 얻는다.

#### 검증 가능

```text
필수 Role 존재
허용된 부모·자식 관계
Variant Rule 일치
Component Resolution 존재
unresolved 0
fallback 0
```

#### 변경 추적 가능

ScreenSpecification이나 Registry가 변경되면 Context Hash와 FigmaScreenSpec 버전 차이로 확인할 수 있다.

## 8. 역할별 책임 경계

| 질문 | 담당 |
|---|---|
| 화면을 어떤 분위기로 구성할까? | Visual Candidate Generator |
| 어떤 필드가 반드시 필요한가? | ScreenSpecification |
| 필드는 수정 가능한가? | ScreenSpecification |
| 저장 버튼은 어디로 이동하는가? | ScreenSpecification |
| 어떤 Section으로 구성할까? | ScreenSpecification + Builder |
| 어떤 KRDS Component를 쓸 것인가? | Runtime Resolver |
| 어떤 Variant를 선택할 것인가? | Runtime Resolver |
| 실제 Figma Instance를 누가 만드는가? | Figma Plugin |
| 최종 업무 기준은 무엇인가? | 승인된 ScreenSpecification |

## 9. Q&A 수정 화면 예시

### 9.1 Visual Candidate

```text
질문 수정 화면을 만들어줘.
신청자 정보와 질문 정보를 구분하고,
저장 버튼은 우측 하단에 배치해줘.
```

결과는 시각적 `qna-update` 후보이며 확정 계약은 아니다.

### 9.2 ScreenSpecification 확정

```yaml
screenId: qna-update
pattern: crud.update
route: /qna/{id}/edit
formMode: UPDATE

fields:
  - writer: EDITABLE
  - contact: EDITABLE
  - email: EDITABLE
  - private: EDITABLE
  - title: EDITABLE, REQUIRED
  - content: EDITABLE, REQUIRED

actions:
  - UPDATE: 저장
  - DETAIL: 취소
```

업무 담당자가 내용을 검토하고 승인한다.

### 9.3 Builder 결과

```text
egov.formPage
├── krds.pageHeader
├── egov.formContainer
│   ├── requester-section
│   └── question-section
└── egov.actionArea
```

### 9.4 Runtime Resolver 결과

```text
field.text + EDITABLE
→ krds.textField Default Variant

field.textarea + EDITABLE
→ krds.textarea Default Variant

action.primary + UPDATE
→ KRDS Primary Button
```

### 9.5 Bundle

```text
FigmaScreenSpec
+ Profile Snapshot
+ Registry Snapshot
+ Export Metadata
```

### 9.6 Plugin

```text
Bundle 검증
→ Published Component Import
→ 임시 Root 생성
→ Variant·Property 적용
→ 후검증
→ 기존 화면 교체
```

## 10. 역할을 합치면 발생하는 문제

### 10.1 generate_figma_design이 업무 계약까지 담당하는 경우

- 필수값 누락
- 권한 누락
- Route 불명확
- Component Key 비결정
- 동일 입력의 결과 변경

### 10.2 ScreenSpecification이 Figma Key까지 관리하는 경우

- 업무 명세가 KRDS Library 내부 구조에 종속
- Library 변경 시 모든 업무 명세 수정
- 비개발자가 이해하기 어려움

### 10.3 Builder가 Variant를 임의 선택하는 경우

- 화면마다 Variant 선택 기준 불일치
- Rule 추적 불가능
- Registry Drift 발견 어려움
- 테스트 재현성 저하

## 11. 최종 정리

```text
Visual Candidate Generator
= 보여줄 후보를 만든다
= 빠르지만 확정적이지 않다

ScreenSpecification
= 업무 규칙을 확정한다
= 최종 판단 기준이다

Deterministic Screen Generator
= 확정된 명세를 KRDS 화면으로 변환한다
= 동일 계약이면 동일 결과를 만든다
```

최종 책임 흐름은 다음과 같다.

```text
아이디어
→ generate_figma_design

업무 승인
→ ScreenSpecification

정확한 KRDS 구현
→ Builder + Runtime Resolver

실제 Figma 생성
→ Bundle + Plugin
```
