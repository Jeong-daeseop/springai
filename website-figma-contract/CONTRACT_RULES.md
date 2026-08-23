# Semantic Figma 계약 운영 규칙

검증 기준일: 2026-07-28

이 문서는 `website-figma-contract`의 JSON Schema만으로 표현하기 어려운 교차 문서 일관성,
식별자 수명주기, 변경 정책과 컴포넌트 카탈로그 운영 규칙을 정의한다.

## 0. 공식 파이프라인 명칭

`ScreenSpecification`을 의미 기반 `FigmaScreenSpec`과 `FigmaExportBundle`로 변환하고,
Figma Plugin의 Preview·MERGE/REPLACE, 품질 Gate, 적용 결과 보고까지 수행하는 전체 흐름의
공식 영문 명칭은 **Semantic Figma Generation Pipeline**으로 정의한다.

공식 한국어 명칭은 **Semantic Figma 화면 생성 파이프라인**으로 정의한다.
문서, 운영 보고서와 사용자 UI에서는 이 명칭을 우선 사용하며, 코드의 기존 클래스·메서드명은
각 단계의 구현 명칭으로 유지한다.

파이프라인의 표준 처리 범위는 다음과 같다.

`ScreenSpecification → FigmaScreenSpec → FigmaExportBundle → Plugin Preview → MERGE/REPLACE → 품질 Gate → Figma 적용 → Generation Report`

## 1. 식별자와 버전

| 값 | 형식 | 수명주기 |
|---|---|---|
| `screenId`, `designSystemId`, `profileId` | 소문자 영숫자로 시작, 소문자 영숫자와 `-_.` 사용, 1~64자 | 논리 자산이 유지되는 동안 불변 |
| `screenSpecificationId` | 위 형식과 동일하며 UUID 허용 | 원본 ScreenSpecification과 동일 |
| `screenVersion`, `designSystemVersion`, `profileVersion` | 1 이상의 정수 | 내용 변경 시 단조 증가 |
| `registryVersion` | 영숫자로 시작, 영숫자와 `-_.` 사용, 1~64자 | Figma Library Publish 단위로 신규 발급 |
| `schemaVersion` | 현재 `1` | breaking change 시 `v2` Schema를 추가 |

`registryVersion`은 Library 표시 버전과 동일할 필요는 없지만, 하나의 Published Library 상태를
유일하게 가리켜야 한다. 기존 버전에 다른 Component Key 집합을 덮어쓰지 않는다.

## 2. logicalNodeId

`logicalNodeId`는 `{pageId}/{section}/{fieldId}` 구조이며 각 세그먼트는 영문자 또는 숫자로 시작하고
영문자·숫자·`._:-`만 포함한다. `/`는 세그먼트 구분자다. `section`은 `table/row`처럼 계층 경로를
받을 수 있지만 빈 세그먼트, 선행·후행 `/`, 세그먼트 안의 기타 문자는 허용하지 않는다.

- 같은 업무 의미의 노드는 화면 버전이 바뀌어도 ID를 보존한다.
- 표시명, 순서, 스타일 변경은 ID 변경 사유가 아니다.
- 업무 의미가 달라진 신규 노드만 새 ID를 발급한다.
- 한 화면 트리에서 중복 ID는 `DUPLICATE_LOGICAL_NODE_ID` 오류로 생성을 중단한다.
- 삭제된 ID를 같은 화면 버전 계열의 다른 의미에 재사용하지 않는다.
- 반복 행은 데이터 PK가 아닌 반복 템플릿 ID를 사용한다. 실제 행은 Figma 생성기가 별도 인스턴스로 관리한다.

## 3. 변경 정책

| 정책 | 의미 | 기존 노드가 있을 때 |
|---|---|---|
| `CREATE` | 신규 화면/노드 생성 전용 | 충돌 오류 |
| `MERGE` | 기본 갱신 정책 | 동일 `logicalNodeId`를 재사용하고 Screen 소유 속성만 갱신 |
| `REPLACE` | 명시적으로 전체 재생성 | 사용자 Preview 승인 후 교체, 제거 노드는 Archive |
| `SKIP` | 해당 노드 변경 제외 | 현재 Figma 상태 유지 |

사용자 직접 수정 속성은 `USER_OVERRIDE`, 컴포넌트 외형은 `DESIGN_SYSTEM`, 업무 값과 구조는
`SCREEN_SPEC` 소유로 취급한다. `MERGE`는 다른 소유자의 값을 덮어쓰지 않는다.

## 4. 화면유형 매핑과 실패 정책

`screenType`은 `PageSpec.template`의 `_LIST`, `_FORM`/`_REGIST`, `_DETAIL` 접미사를 우선 사용하고,
값이 없을 때만 `ScreenSpecification.archetype`에 같은 규칙을 적용한다.

`layoutPattern`은 별도로 `MASTER_DETAIL`, `DASHBOARD`, 그 외 `STANDARD`로 판정한다.
따라서 `MASTER_DETAIL`처럼 두 판정에 관련되는 문자열도 서로 충돌하지 않는다.

지원 접미사가 없는 자유 문자열은 임의의 화면유형으로 바꾸지 않는다.
`UNSUPPORTED_SCREEN_TYPE` 오류를 반환하고 사용자 또는 상위 호출자가 화면유형을 명시해야 한다.

## 5. Bundle 교차 문서 일관성

JSON Schema 검증 후 다음 값의 동일성을 의미 검증한다.

- Screen의 `profileId/profileVersion/registryVersion`
- Profile의 `id/version/registryVersion`
- Registry의 `profileId/profileVersion/registryVersion`
- Bundle metadata의 Screen/Profile/Registry 버전

불일치는 `*_MISMATCH` 오류로 import를 중단한다. 실제 Published Library의 Component Key가 Registry와
다르면 `COMPONENT_KEY_MISMATCH`로 처리하고 Registry 동기화 전에는 화면을 생성하지 않는다.

## 6. 컴포넌트 카탈로그

`component-catalog-v1.json`이 논리 컴포넌트, Figma Property, 코드 속성, fallback의 기준이다.

### 5.1 논리 계약 단일 원천화(v2)

신규 계약은 `component-catalog-v2.json`을 논리 컴포넌트 정의의 단일 진실 공급원으로 사용한다.
`logicalType`, 별칭, 대체 관계, Property, Role, 지원 Platform, 합성 관계와 fallback은 Catalog만
소유한다. `component-registry-v3`는 해당 Catalog 버전에 대해 실제 Publish된 Figma
Component Set·Variant·Variable Key와 승인 증적만 보존하는 불변 Binding Snapshot이다.

- Preview/Apply는 `catalogVersion`과 `registryVersion`을 명시해야 한다.
- Apply 시 `catalogHash`가 다르거나 필수 원자 Component Binding이 없으면 fail-closed 한다.

Profile별 `overrides`는 Catalog 논리 계약을 바꾸지 않는 제한적 Binding 선택 정책이다. `components`에는
`componentSetKey`와 `variantKeys`만, `variables`에는 `variableId`와 `collectionName`만 허용한다.
별칭·replacement·Property·Role·composition·허용 Variant 값과 같은 논리 계약 필드는 Override로
재정의할 수 없으며, 빈 Override 객체와 미정의 필드는 Schema에서 거부한다. Override는 해당 Profile의
Published Binding을 선택하는 용도로만 사용하고 Catalog/Registry 교차 검증과 승인·Hash 정책을 우회하지 않는다.
- Pattern과 Page Template은 `composition`을 재귀적으로 해석하되 순환 참조를 허용하지 않는다.
- 승인된 Registry Snapshot은 제자리 변경하지 않고 새 `registryVersion`으로 저장한다.
- 과거 산출물 재현과 Rollback을 위해 Catalog/Registry 버전과 Hash를 함께 보존한다.

- `requiredComponents`: 1차 생성에 반드시 존재해야 하는 KRDS/eGovFrame 컴포넌트
- `optionalComponents`: 없어도 fallback 가능한 확장 컴포넌트
- `patterns`: 여러 컴포넌트의 의미 조합
- `pageTemplates`: 화면 골격 템플릿
- `aliases`: 논리명 변경 호환성
- `replacement`: 폐기 컴포넌트의 대체 논리명

지원하지 않는 선택 속성은 카탈로그의 `fallback`에 따라 기본값 사용, 노드 생략 또는 경고를 적용한다.
필수 컴포넌트가 카탈로그나 Published Registry에 없으면 임의 프레임으로 대체하지 않고 오류를 반환한다.
카탈로그의 초기 목록은 기술 기준선이며, 조직 Library 담당자의 Preview 승인 후 운영 기준으로 확정한다.

## 7. Plugin 입력 정책

Semantic Figma v1의 기본 입력은 `figma-export-bundle-v1` 계약을 따르는
`.figma-export-bundle.json` 파일이다. REST 직접 조회는 운영 환경에서 명시적으로 활성화하는
선택 기능이며, REST를 사용하지 않아도 모든 핵심 생성·동기화 기능이 동작해야 한다.

- REST 사용 시 단기 Bearer Token을 우선한다.
- Plugin에 장기 비밀정보를 저장하지 않는다.
- 허용 서버 도메인과 CORS origin은 배포 환경별 설정으로 관리한다.
- REST 실패 시 동일 Bundle의 파일 입력으로 복귀할 수 있어야 한다.

## 8. Removed Node 정책

새 Spec에서 사라진 기존 논리 노드는 삭제하지 않고 화면별
`🗄 Removed — {screenId}` Frame으로 Archive한다. `REPLACE` 대상의 기존 Root도 같은 정책을 따른다.

- v1 Plugin은 자동 `DELETE`와 노드별 `ASK` 분기를 제공하지 않는다.
- Archive 영구 삭제는 동기화와 분리된 사람의 운영 작업이다.
- 삭제 전 Backup, 생성 보고서와 복구 필요성을 확인한다.
- DELETE·ASK 요구가 생기면 보고서 enum과 호환성 영향을 포함해 별도 결정으로 재검토한다.

## 9. 호환성

- v1 소비자는 v1 Schema와 카탈로그만 지원한다.
- 선택 속성 추가는 하위 호환 변경으로 허용한다.
- 필수 속성 추가, enum 제거/이름 변경, 의미 변경은 breaking change이며 v2 파일로 추가한다.
- producer는 자신이 생성한 `schemaVersion`을 명시하고 consumer는 미지원 버전을 명확히 거부한다.
- Schema 변경 시 계약 fixture, Spring 테스트, Extractor 테스트, Plugin 테스트를 모두 통과해야 한다.

## 10. Manual Refinement 선행 결정 (MR-DEC-01~06)

`docs/figma/17_Semantic_Figma_Generation_Pipeline_Manual_Refinement_Implementation_List.md`의
계약·서버·Plugin 구현이 참조하는 확정 결정이다. 이후 섹션에서 값을 바꾸려면 이 절을 먼저 갱신한다.

### 10.1 SyncMode 정리 (MR-C01)

서버 `FigmaSyncMode` Java enum에만 `RECONCILE` 값이 선언되어 있었으나 실제로는 어떤 분기에서도
처리되지 않는 죽은 값이었다(TypeScript `SyncMode` union과 `figma-generation-report-v1` Schema의
`mode` enum 모두 `PREVIEW`/`MERGE`/`REPLACE` 3종만 정의). Manual Refinement MVP 범위에도
`RECONCILE`은 등장하지 않으므로, 정식 지원 모드로 승격하지 않고 **Java enum에서 제거**해 3곳을
`PREVIEW`/`MERGE`/`REPLACE`로 통일한다. `RECONCILE`이 필요해지면 그때 TS/Schema를 함께 갱신하는
별도 결정으로 다시 추가한다.

### 10.2 Manual Refinement 수명주기 (MR-DEC-01)

```
DRAFT → CAPTURED → REVIEW_REQUIRED → APPROVED | REJECTED → APPLIED | SUPERSEDED
```

- `DRAFT`: Plugin이 캡처를 시작했지만 아직 서버에 전송하지 않음(Plugin 로컬 상태, 서버 저장 대상 아님).
- `CAPTURED`: Plugin이 Diff를 계산해 서버에 `POST /api/figma/refinements/capture`로 저장한 직후.
- `REVIEW_REQUIRED`: Preview 계산 결과 차단·충돌 없이 사람 승인 대기 상태.
- `APPROVED` / `REJECTED`: 운영자가 명시적으로 승인/반려한 종결 상태 중 하나.
- `APPLIED`: 승인된 Patch Set이 실제 `MERGE`/`REPLACE` 재적용에 성공적으로 반영됨.
- `SUPERSEDED`: 같은 화면의 더 최신 Patch Set이 승인되어 이전 Patch Set을 대체함.

Java enum, TypeScript union, `figma-refinement-patch-set-v1.schema.json`의 `status` enum은 이
6개 값을 정확히 동일한 철자로 사용한다.

### 10.3 속성 소유자 (MR-DEC-02)

Manual Refinement는 §3의 3종 소유자(`USER_OVERRIDE`/`DESIGN_SYSTEM`/`SCREEN_SPEC`)를 다음
5종으로 구체화한다.

| 소유자 | 의미 | 갱신 규칙 |
|---|---|---|
| `SCREEN_SPEC` | 업무 데이터·구조 값 | `ScreenSpecification` 변경 시 갱신, Refinement가 덮어쓰지 않음 |
| `DESIGN_SYSTEM` | 컴포넌트 외형·Variant | Registry/Rule Set 변경 시 갱신, Refinement가 덮어쓰지 않음 |
| `MANUAL_REFINEMENT` | 승인된 Patch로 확정된 시각 속성 값 | 새 Patch Set 승인 시에만 갱신. §3의 기존 `USER_OVERRIDE`(암묵적, `DATA_MANAGED_PROPERTIES` 기반)는 Component Property 전용으로 계속 동작하며, `MANUAL_REFINEMENT`는 그 상위에 Wrapper Frame 시각 속성까지 포괄하는 명시적·감사 가능한 계층이다. 두 메커니즘은 병행하며 이번 범위에서 기존 `applyOwnedProperties()` 로직을 대체하지 않는다 |
| `SYSTEM_LAYOUT` | Auto Layout 방향, 필수 노드 존재 등 시스템이 항상 강제하는 규칙 | 승인된 Patch라도 이 소유자의 값은 적용하지 않는다(MR-R06) |
| `RUNTIME_DATA` | 조회 시점에만 유효한 파생 값(카운트 등) | 정적 계약에 저장하지 않는다 |

### 10.4 승인 전 Figma 직접 수정의 취급 (MR-DEC-03)

Figma에서 사람이 직접 조정한 값은 `Capture → Preview → Approve`를 통과하기 전까지 영속 원본으로
취급하지 않는다. 승인되지 않은 캡처는 다음 재생성에 반영되지 않으며, Plugin을 닫거나 다른 화면으로
이동해도 서버에 `CAPTURED` 이상으로 저장되지 않은 값은 유실될 수 있다.

### 10.5 MVP 속성 정책 (MR-DEC-04)

| 분류 | 속성 |
|---|---|
| 허용 | `fill`, `stroke`, `opacity`, `cornerRadius`, `typography`, `padding`, `itemSpacing`, `textAlign` |
| 조건부 | `width`, `height`, `minWidth`, `minHeight`, `layoutGrow`, `layoutAlign` — 품질 Gate(Layout/Accessibility) 통과 시에만 최종 적용 |
| 차단 | `logicalNodeId` 변경, 화면 버전 필드, Instance detach, 필수 노드 삭제, `visible=false`, Auto Layout 방향(`layoutMode`) |

차단 속성에 대한 Patch는 Capture 단계에서부터 `POLICY_BLOCKED`로 표시하고 Preview에 포함하되
Apply 대상에서 제외한다.

값 정규화 규칙(MR-C05, `refinement/property-normalizer.ts` 구현 기준):

- 색상(`fill`/`stroke`)은 Figma의 0~1 float RGBA를 소수점 4자리로 반올림한 뒤 `#RRGGBBAA` 문자열로
  정규화한다. 반올림 전 float 오차(예: `0.4999999999`)만 다른 값은 동일 값으로 취급해 Diff에 포함하지
  않는다.
- `fill`/`stroke`가 Paint 배열인 경우 배열 순서를 `type` → `opacity` → `color` 기준으로 정렬한 뒤
  비교한다. Figma가 반환하는 원래 배열 순서에 의존하지 않는다.
- `typography`는 `FontName`(`family`+`style`)과 크기·자간·행간을 각각 별도 `propertyPath`
  (`typography.fontFamily`, `typography.fontStyle`, `typography.fontSize` 등)로 분해해 기록한다.
- 값이 Figma `figma.mixed` 심볼인 속성은 Patch를 생성하지 않고 `MIXED_VALUE_UNSUPPORTED` 경고로
  Capture 결과에 남긴다.
- `width`/`height`/`padding`/`itemSpacing` 등 숫자 속성은 소수점 2자리로 반올림한 뒤 비교한다.

MR-C08(계약 테스트 연결)은 기존 `figmaContractTest` Gradle task와 `contract-test.mjs`의
`schemaNames`/`validator`/`expectValid`·`expectInvalid` 등록 패턴을 그대로 재사용했으며, 별도
Gradle task를 신설하지 않았다.

### 10.6 승인 권한과 Token Scope 분리 (MR-DEC-05)

| Scope | 대상 | 권한 |
|---|---|---|
| `figma:screens:read` | Plugin 단기 Token | 화면 Bundle GET만 |
| `figma:refinements:write` | Plugin 단기 Token | capture/preview POST만 (승인 불가) |
| `figma:reports:write` | Plugin 단기 Token | Generation Report POST만 |

승인·반려(`/approve`, `/reject`)는 Plugin 단기 Token으로 호출할 수 없다. 운영자 인증
경로(`X-API-Key`, 기존 `apiKeyFilter()`)로만 호출 가능하며, MVP에서 별도 사람 승인자 전용 Token
체계는 신설하지 않고 기존 API Key 인증을 그대로 승인 권한 경계로 사용한다.

### 10.7 반복 보정 승격 (MR-DEC-06)

같은 속성이 여러 화면에서 반복 승인되어도 Pattern/Design System으로 자동 승격하지 않는다. 승격은
사람이 후보를 검토해 명시적으로 반영하는 별도 기능(MR-S10)이며, MVP 범위에는 포함하지 않는다.
