# Figma 화면 생성 3계층 역할 가이드 (요약본 — worked example 중심)

> 정본(공식 구현 계약): [KRDS Figma Role·Variant 구현명세서](./KRDS_Figma_Role_Variant_구현명세서.md)
> 아키텍처 빠른 참고: [KRDS Figma Role·Variant 구현 아키텍처 가이드](./KRDS_Figma_Role_Variant_구현아키텍처_가이드.md)

> 2026-08-17 개정: 이 문서는 원래 3계층 각각의 입력·출력·필드 목록·Builder/Resolver 처리
> 세부까지 정본과 거의 동일한 내용으로 독립 서술하고 있었다. 세부 사양은 정본과만 동기화하고,
> 이 문서는 **왜 이렇게 나누는지를 구체적 예시로 보여주는 역할**로 축소했다. 정확한 필드
> 목록·Java 타입·Resolver 처리 순서는 항상 정본을 확인한다.

## 1. 핵심 구조

Figma 화면 생성은 다음 세 역할로 분리한다. 각 역할의 정확한 책임·강제 규칙은 정본
[§3.1 화면 생성 3계층 아키텍처](./KRDS_Figma_Role_Variant_구현명세서.md#31-화면-생성-3계층-아키텍처)를 참고한다.

```text
generate_figma_design       → Visual Candidate Generator   (화면을 어떻게 보이게 할지 제안)
ScreenSpecification         → Source of Truth              (화면에 무엇이 반드시 있어야 하는지 확정)
Builder + KRDS Runtime Resolver → Deterministic Screen Generator (확정된 내용을 KRDS 규칙으로 정확하게 생성)
```

## 2. 역할별 책임 경계

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

## 3. Q&A 수정 화면(`qna-update`) 예시로 보는 전체 흐름

### 3.1 Visual Candidate (비결정적 제안)

```text
질문 수정 화면을 만들어줘.
신청자 정보와 질문 정보를 구분하고,
저장 버튼은 우측 하단에 배치해줘.
```

결과는 시각적 후보일 뿐 확정 계약이 아니다. DB 컬럼명, 필수 여부, 수정 권한, 실제 KRDS
Component Key는 이 단계에서 확정할 수 없다.

### 3.2 ScreenSpecification 확정 (Source of Truth)

```yaml
screenId: qna-update
pattern: crud.edit
route: /qna/{id}/edit
formMode: UPDATE

fields:
  - writer: EDITABLE
  - contact: EDITABLE
  - email: EDITABLE
  - title: EDITABLE, REQUIRED
  - content: EDITABLE, REQUIRED

actions:
  - UPDATE: 저장
  - DETAIL: 취소
```

업무 담당자가 내용을 검토하고 승인해야만 다음 단계로 넘어간다. Visual Candidate와 충돌하면
항상 ScreenSpecification이 우선한다 — 예를 들어 시안에서 이메일이 선택값처럼 보여도
`required: true`면 최종 결과는 필수 입력으로 생성된다.

### 3.3 Builder + Runtime Resolver (결정적 생성)

```text
egov.formPage
├── krds.pageHeader
├── egov.formContainer
│   ├── requester-section
│   └── question-section
└── egov.actionArea
```

Builder는 `logicalNodeId`·`semanticRole`·구조만 결정하고, Runtime Resolver가 이를 실제 KRDS
Published Component·Variant로 해석한다(`field.text` + `EDITABLE` → `krds.textField` Default
Variant 등). 정확한 Builder 출력 구조, Resolver의 `componentResolution` 필드, 처리 순서는 정본
§4(도메인 모델)·§6(Resolver 명세)을 참고한다.

### 3.4 Bundle → Plugin

```text
FigmaScreenSpec + Profile/Registry Snapshot + Export Metadata
→ Bundle 검증 → Published Component Import → Preview → 사용자 승인 → Atomic Apply → 후검증
```

## 4. 역할을 합치면 발생하는 문제

### 4.1 `generate_figma_design`이 업무 계약까지 담당하는 경우

- 필수값·권한 누락, Route 불명확
- Component Key가 결정적이지 않음
- 동일 입력인데도 결과가 달라짐

### 4.2 ScreenSpecification이 Figma Key까지 관리하는 경우

- 업무 명세가 KRDS Library 내부 구조에 종속됨
- Library가 바뀔 때마다 모든 업무 명세를 수정해야 함
- 비개발자(업무 담당자)가 이해하기 어려워짐

### 4.3 Builder가 Variant를 임의 선택하는 경우

- 화면마다 Variant 선택 기준이 달라짐
- 어떤 Rule이 적용됐는지 추적 불가능
- Registry Drift를 발견하기 어려움, 테스트 재현성 저하

## 5. 최종 정리

```text
Visual Candidate Generator      = 보여줄 후보를 만든다. 빠르지만 확정적이지 않다.
ScreenSpecification             = 업무 규칙을 확정한다. 최종 판단 기준이다.
Deterministic Screen Generator  = 확정된 명세를 KRDS 화면으로 변환한다. 동일 계약이면 동일 결과.
```
