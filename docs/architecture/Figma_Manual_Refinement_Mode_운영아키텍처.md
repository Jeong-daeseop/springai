# Figma Manual Refinement Mode 운영 아키텍처

> 개정 안내(2026-08-17): 이 문서는 원래 Wrapper 단위 `Overlay`/`locked` 제안으로 작성됐으나,
> 실제 구현은 **속성 단위 Patch 모델**로 확정·완료됐다(선행 결정: MR-DEC-01~06, 상세 계약:
> `website-figma-contract/CONTRACT_RULES.md` §10, 구현 체크리스트:
> `docs/figma/17_Semantic_Figma_Generation_Pipeline_Manual_Refinement_Implementation_List.md`).
> 이 문서는 그 상위 운영 아키텍처 관점(Source of Truth 분리, 품질 Gate, 운영 흐름)을 설명하는
> 용도로 남기며, 데이터 모델·API·상태값은 실제 구현 기준으로 갱신했다. 세부 필드·엔드포인트는
> 항상 17번 문서와 `CONTRACT_RULES.md` §10을 1차 근거로 삼는다.

## 1. 문서 목적

Figma Plugin이 화면을 재생성할 때 사용자가 Figma에서 직접 조정한 시각 보정을 승인 후에만,
그리고 업무·디자인 시스템 계약을 침범하지 않는 범위에서만 다음 재생성에 보존하기 위한 운영
아키텍처를 정의한다.

기존 시스템은 `ScreenSpecification → Builder → KRDS Runtime Resolver → Bundle → Figma Plugin`
흐름으로 화면을 생성한다. 이 구조에 **Manual Refinement**라는 별도 수명주기 계층을 추가한다.

## 2. 목표 구조

```text
ScreenSpecification
        ↓
KRDS Runtime Resolver
        ↓
Deterministic Base Bundle
        ↓
Figma Plugin Apply (MERGE/REPLACE)
        ↓
Manual Refinement Patch Set 승인
        ↓
다음 MERGE/REPLACE에 결정적 재적용
```

핵심 원칙은 업무 의미(ScreenSpecification)·디자인 시스템 계약(Registry/Rule Set)·시각 보정
(Manual Refinement)의 Source of Truth를 분리하는 것이다. Manual Refinement는 `FigmaSyncMode`
(`PREVIEW`/`MERGE`/`REPLACE`)와 독립된 자체 수명주기를 가지며, Plugin의 Sync 모드 선택에
끼어들지 않는다(MR-DEC-01). 이전에 검토했던 `RECONCILE` 모드는 실제로 어떤 분기에서도 쓰이지
않는 죽은 값이었음이 확인되어 서버 enum에서 제거했다.

## 3. Source of Truth 분리

### 3.1 ScreenSpecification

업무와 컴포넌트 구조를 관리한다.

- 화면 ID, 업무 필드, Label·Data 의미
- Component 종류·Variant, 필수/Readonly 여부
- Table 열·행 정의, 접근성 규칙

### 3.2 Registry / Rule Set (Design System)

컴포넌트 외형·Variant 계약을 관리한다.

### 3.3 Manual Refinement Patch Set

Figma에서 승인한 시각 보정값을 **속성 단위 Patch**로 관리한다. Wrapper 전체를 잠그는 것이
아니라, 노드별로 어떤 속성이 바뀌었는지를 개별 Patch로 기록한다.

- `logicalNodeId` + `propertyPath`(예: `width`, `fill`, `typography.fontSize`) 단위
- 각 Patch는 소유자(`owner`)와 정책(`scope`)을 함께 가진다(§5)
- Wrapper 전체가 아니라 실제로 사람이 바꾼 속성만 Patch로 남으므로, 승인 검토 범위가
  Wrapper 단위보다 작고 명확하다

수동 수정값은 업무 명세나 디자인 시스템 계약을 대체하지 않으며, 항상 그 위에 얹히는 시각
보정값으로만 취급한다.

## 4. Manual Refinement 수명주기 (MR-DEC-01)

```text
DRAFT → CAPTURED → REVIEW_REQUIRED → APPROVED | REJECTED → APPLIED | SUPERSEDED
```

- `DRAFT`: Plugin이 캡처를 시작했지만 아직 서버에 전송하지 않은 로컬 상태(서버 저장 대상 아님).
- `CAPTURED`: Plugin이 Diff를 계산해 `POST /api/figma/refinements/capture`로 저장한 직후.
- `REVIEW_REQUIRED`: Preview 결과 차단·충돌이 없어 사람 승인만 남은 상태(자동 전환).
- `APPROVED` / `REJECTED`: 운영자가 명시적으로 승인/반려한 종결 상태.
- `APPLIED`: 승인된 Patch Set이 실제 `MERGE`/`REPLACE` 재적용에 성공적으로 반영됨.
- `SUPERSEDED`: 같은 화면에서 더 최신 Patch Set이 승인되어 이전 Patch Set을 대체함.

승인 전(≤ `CAPTURED`/`REVIEW_REQUIRED`) 상태의 Patch는 영속 원본으로 취급하지 않는다(MR-DEC-03).
Plugin을 닫거나 다른 화면으로 이동하면 서버에 저장되지 않은 값은 유실될 수 있다.

## 5. 속성 소유자와 정책 (MR-DEC-02, MR-DEC-04)

| 소유자(`owner`) | 의미 | 갱신 규칙 |
|---|---|---|
| `SCREEN_SPEC` | 업무 데이터·구조 값 | `ScreenSpecification` 변경 시 갱신, Refinement가 덮어쓰지 않음 |
| `DESIGN_SYSTEM` | 컴포넌트 외형·Variant | Registry/Rule Set 변경 시 갱신, Refinement가 덮어쓰지 않음 |
| `MANUAL_REFINEMENT` | 승인된 Patch로 확정된 시각 속성 값 | 새 Patch Set 승인 시에만 갱신 |
| `SYSTEM_LAYOUT` | Auto Layout 방향, 필수 노드 존재 등 시스템이 항상 강제하는 규칙 | 승인된 Patch라도 이 소유자의 값은 적용하지 않음(MR-R06) |
| `RUNTIME_DATA` | 조회 시점에만 유효한 파생 값(카운트 등) | 정적 계약에 저장하지 않음 |

각 Patch는 속성 경로 기준으로 정책(`scope`)도 함께 갖는다.

| 정책 | 속성 | 처리 |
|---|---|---|
| `ALLOWED` | `fill`, `stroke`, `opacity`, `cornerRadius`, `typography.*`, `padding.*`, `itemSpacing`, `textAlign` | 승인 시 그대로 적용 |
| `CONDITIONAL` | `width`, `height`, `minWidth`, `minHeight`, `layoutGrow`, `layoutAlign` | 품질 Gate(Layout/Accessibility) 통과 시에만 최종 적용 |
| `BLOCKED` | `logicalNodeId` 변경, 화면 버전 필드, Instance detach, 필수 노드 삭제, `visible=false`, `layoutMode` | Capture 단계부터 차단 표시, Apply 대상에서 제외 |

목록에 없는 속성 경로는 화이트리스트 방식으로 기본 `BLOCKED`다.

## 6. 승인 흐름과 재적용

```text
1. Plugin: 대상 노드 선택 → Refinement 시작 → 변경 캡처(Snapshot Diff)
2. Plugin → 서버: POST /api/figma/refinements/capture (CAPTURED)
3. 서버: Preview 계산(applied/excluded/blocked/conflicts) — 차단·충돌 없으면 REVIEW_REQUIRED 자동 전환
4. 운영자(X-API-Key): POST /{patchSetId}/approve 또는 /reject
5. 다음 MERGE/REPLACE: populateStaging에서 syncNode() 직후
   승인된 Patch를 logicalNodeId → propertyPath 순서로 결정적 적용
6. 품질 Gate(Layout/Accessibility/Visual) 실패 시 Atomic Apply의 기존 Rollback으로
   Refinement 변경까지 함께 원복
7. 성공 시 APPLIED로 전이, Generation Report v2에 적용 결과 기록
```

승인·반려 API(`/approve`, `/reject`)는 Plugin의 단기 Token(Scope: `figma:screens:read`,
`figma:refinements:write`, `figma:reports:write`)으로는 호출할 수 없다. 운영자 인증
(`X-API-Key`) 경로로만 도달 가능하다(MR-DEC-05, `SecurityConfig.requiredScopeFor`).

## 7. 충돌 판정

Patch를 재적용하기 전 다음 상태로 분류한다(`FigmaRefinementConflictStatus`).

| 상태 | 의미 |
|---|---|
| `NONE` | 충돌 없음, 정상 적용 |
| `UPSTREAM_CHANGED` | 기준값과 새 Screen Spec 값이 같은 속성을 다르게 변경 — 자동 덮어쓰지 않고 충돌 보고 |
| `TARGET_REMOVED` | 대상 `logicalNodeId`가 삭제됨 |
| `TYPE_CHANGED` | 대상 노드의 `baselineLogicalType`이 바뀜 |
| `POLICY_BLOCKED` | `SYSTEM_LAYOUT` 소유이거나 `BLOCKED` 정책 속성 |
| `BASE_STALE` | Patch Set을 캡처할 때의 화면 상태 해시(`baseMaterializationHash`)와 현재 화면이 불일치 |

`baseMaterializationHash`는 Figma Plugin 샌드박스(QuickJS)가 Web Crypto SHA-256을 안정적으로
지원하지 않는 제약 때문에 SHA-256이 아니라 Plugin과 동일한 FNV-1a32 해시
(`fnv1a32:{8자리 hex}:{byte 길이}`)를 사용한다.

## 8. 품질 Gate

### 계약 Gate

- Registry Version 일치, Component Set Key 일치, Variant Key 일치, Component Property 검증

### Plugin Gate

- 모든 Component에 `componentResolution` 존재, `unresolved=0`, `fallback=0`

### Refinement 관련 Gate(MR-Q)

- 필수 데이터 노드 비표시·폭 축소·화면 밖 배치를 Layout Gate가 차단(MR-Q02)
- Refinement 적용 후 텍스트 대비(WCAG 2.1 AA)를 Accessibility Gate가 재검증(MR-Q03)
- 승인된 Refinement가 실제로 적용된 경우 Visual Baseline 자동 갱신은 하지 않고 분리 처리(MR-Q04)
- 위 Gate 중 하나라도 실패하면 Atomic Apply 전체가 Rollback되며, Refinement 적용분도 함께
  원복된다(MR-R07 — 별도 코드 없이 기존 Rollback 메커니즘으로 충족)

## 9. 계층별 역할

```text
ScreenSpecification        = 업무·구조 Source of Truth
Registry / Rule Set        = KRDS Component·Variant Source of Truth
Builder / Resolver         = 결정형 화면 생성기
Figma Plugin                = Apply·Capture·Preview 실행기
Manual Refinement Patch Set = 승인된 시각 보정값(속성 단위)
운영자(X-API-Key)            = 승인·반려 결정권자
Figma                       = 최종 시각 검토 화면
```

## 10. 향후 검토 과제 (MVP 범위 밖)

다음 항목은 이 아키텍처가 원래 제안했던 방향이지만 MVP 구현 범위에는 포함하지 않았다. 착수
여부는 별도 결정이 필요하다.

- **반복 보정의 Pattern/Design System 승격**: 같은 속성이 여러 화면에서 반복 승인되어도
  Pattern으로 자동 승격하지 않는다. 사람이 후보를 검토해 명시적으로 반영하는 별도 기능으로
  운영 안정화 이후 검토한다(MR-DEC-06, MR-S10).
- **`krds.detailTable` 공통 Pattern 등록**: 상세 화면 행 정렬을 Registry에 공식 Pattern으로
  등록하는 안은 아직 구현되지 않았다. 현재는 Patch가 개별 화면·노드 단위로만 적용되며, 여러
  화면에 걸친 공통 Pattern 승격은 위 항목과 함께 검토 대상이다.

## 11. 관련 문서

- 구현 체크리스트·완료 상태: `docs/figma/17_Semantic_Figma_Generation_Pipeline_Manual_Refinement_Implementation_List.md`
- 계약·선행 결정 전문: `website-figma-contract/CONTRACT_RULES.md` §10
- 전체 파이프라인 개요: `docs/figma/05_Overall_Architecture_Diagram.md`
