# Figma Manual Refinement Mode 운영 아키텍처

## 1. 문서 목적

Figma Plugin이 화면을 재생성할 때 사용자가 Figma에서 직접 조정한 레이아웃과 시각 품질을 보존하기 위한 운영 아키텍처를 정의한다.

현재 시스템은 `ScreenSpecification → Builder → KRDS Runtime Resolver → Bundle → Figma Plugin` 흐름으로 화면을 생성한다. 이 구조에 수동 시각 보정 계층인 `Manual Refinement Overlay`를 추가한다.

## 2. 권장 목표 구조

```text
ScreenSpecification
        ↓
KRDS Runtime Resolver
        ↓
Deterministic Base Bundle
        ↓
Figma Plugin Apply
        ↓
Manual Refinement Overlay
        ↓
Approved Visual Snapshot
```

핵심 원칙은 업무 의미와 시각 보정의 Source of Truth를 분리하는 것이다.

## 3. Source of Truth 분리

### 3.1 ScreenSpecification

업무와 컴포넌트 구조를 관리한다.

- 화면 ID
- 업무 필드
- Label·Data 의미
- Component 종류
- Variant
- 필수 여부
- Readonly 여부
- Table 열·행 정의
- 접근성 규칙

### 3.2 Manual Refinement Overlay

Figma에서 승인한 시각 보정값을 관리한다.

- Label 열 폭
- 행 높이
- Border
- Padding
- Gap
- Section 간격
- 버튼 위치
- Frame 크기
- 질문내용 높이

수동 수정값은 업무 명세를 대체하지 않으며, 시각 보정값으로만 취급한다.

## 4. Plugin Apply 모드

### 4.1 Replace

초기 생성 또는 강제 재생성 모드다.

```text
기존 화면 Archive
전체 화면 재생성
수동 수정값 무시
```

### 4.2 Reconcile

일반 운영 모드다.

```text
Component·Variant·업무 데이터 갱신
사용자 Layout 수정 유지
```

### 4.3 Visual Lock

디자인 검토가 완료된 화면의 수동 수정값을 승인하는 모드다.

```text
수동 수정값을 Overlay로 저장
이후 Reconcile에서 해당 값 보호
```

Plugin UI에는 다음 옵션을 제공한다.

```text
[ ] 기존 Layout 보존
[ ] 수동 Refinement 잠금
[ ] Component만 갱신
[ ] 강제 전체 재생성
```

## 5. Manual Refinement 메타데이터

각 Wrapper에 수동 보정 상태를 저장한다.

```json
{
  "logicalNodeId": "qna-detail/detail/contact",
  "manualRefinement": {
    "locked": true,
    "width": 176,
    "minHeight": 56,
    "paddingTop": 8,
    "paddingBottom": 8,
    "itemSpacing": 16,
    "borderBottom": 1
  },
  "baseHash": "base-layout-hash",
  "approvedAt": "2026-08-16T10:30:00+09:00"
}
```

### Apply 정책

```text
Manual Refinement Lock 있음
→ Figma 수동 Layout 유지
→ KRDS Instance와 업무 Property만 갱신

Manual Refinement Lock 없음
→ 기본 Layout Recipe 적용
```

## 6. 수동 수정 자동 감지

Plugin은 이전 적용값과 현재 Figma 값을 비교한다.

비교 대상:

- width / height
- x / y
- padding
- gap
- alignment
- border
- visible
- text size

값이 다르면 `MANUAL_REFINEMENT_DETECTED` 상태로 기록하고 자동 덮어쓰기를 중단한다.

```text
수동 수정 감지:
qna-detail/detail/contact
- Label 열: 160px → 176px
- 행 높이: 44px → 56px

[수동 수정 유지] [기본값 복원]
```

## 7. Detail Table 공통 Pattern

상세 화면의 행 정렬을 화면별 임시 코드로 처리하지 않고 공통 Pattern으로 등록한다.

```text
krds.detailTable
```

구조:

```text
DetailTable
 ├─ DetailRow
 │   ├─ DetailLabel
 │   └─ DetailValue
 ├─ DetailRow
 │   ├─ DetailLabel
 │   └─ DetailValue
 └─ DetailRow
     ├─ DetailLabel
     └─ DetailValue
```

권장 Pattern 속성:

```json
{
  "labelColumnWidth": 176,
  "rowMinHeight": 56,
  "longTextRowMinHeight": 104,
  "rowBorder": "bottom",
  "dataStartAlignment": "fixed",
  "actionPlacement": "right"
}
```

이 Pattern은 `qna-detail`, `qna-answer-detail` 및 향후 다른 상세 화면에서 재사용한다.

## 8. 변경 충돌 정책

수동 수정과 서버 계약이 충돌할 수 있으므로 다음 우선순위를 적용한다.

```text
FATAL 계약 오류
→ Apply 중단

Component Variant 변경
→ 수동 Layout 유지 가능

업무 필드 추가·삭제
→ 수동 Overlay와 비교 후 Preview

Frame 구조 변경
→ 기존 Overlay 자동 적용 금지
→ Migration Preview 필요
```

필드 삭제 시 기존 위치를 자동 재사용하지 않는다.

```text
FIELD_REMOVED
LAYOUT_OVERLAY_ORPHANED
MANUAL_APPROVAL_REQUIRED
```

## 9. 계층별 역할

```text
ScreenSpecification
= 업무·구조 Source of Truth

Registry / Rule Set
= KRDS Component·Variant Source of Truth

Builder / Resolver
= 결정형 화면 생성기

Figma Plugin
= Apply·Reconcile·검증 실행기

Manual Refinement Overlay
= 승인된 시각 보정값

Figma
= 최종 시각 검토·승인 화면
```

## 10. 운영 처리 흐름

```text
1. ScreenSpecification 작성·승인
2. KRDS Registry와 Variant Rule 검증
3. Runtime Resolver로 Base Bundle 생성
4. Figma Plugin에서 최초 Apply
5. Figma에서 간격·폭·Border·정렬 보정
6. Visual Lock 승인
7. Manual Refinement Overlay 저장
8. 이후 Reconcile Apply
9. Component·업무 데이터만 갱신
10. 수동 Layout은 자동 보존
```

## 11. 품질 Gate

### 계약 Gate

- Registry Version 일치
- Component Set Key 일치
- Variant Key 일치
- Component Property 검증

### Plugin Gate

- 모든 Component에 `componentResolution` 존재
- `unresolved=0`
- `fallback=0`
- Layout 오류 없음
- Accessibility 오류 없음

### Refinement Gate

- Manual Refinement Overlay의 `baseHash` 일치
- 잠긴 Layout 값 보존
- 고아 Overlay 없음
- 변경 Preview 승인 완료

## 12. 기대 효과

이 구조를 도입하면 사용자가 매번 다음과 같은 요청을 반복할 필요가 없다.

```text
Label 폭을 다시 조정해줘
Border를 추가해줘
질문내용 높이를 늘려줘
버튼을 오른쪽으로 옮겨줘
```

한 번 Figma에서 수정하고 `수동 Refinement 잠금`을 실행하면 다음부터는 다음과 같이 동작한다.

```text
Bundle 재생성
→ Component와 업무 데이터만 갱신
→ Detail Table 정렬·Border·간격 보존
→ 변경 충돌만 Preview로 표시
```

## 13. 결론

`Figma Manual Refinement Mode`는 단순한 편의 기능이 아니라 디자인과 코드의 지속적인 동기화를 위한 별도 계층이다.

ScreenSpecification은 업무와 구조를 책임지고, KRDS Registry는 컴포넌트 계약을 책임지며, Figma Manual Refinement Overlay는 승인된 시각 보정만 책임져야 한다.

이 분리를 통해 Plugin 재Apply의 반복 부담을 줄이고, 사용자가 Figma에서 직접 조정한 화면 품질을 안정적으로 보존할 수 있다.
