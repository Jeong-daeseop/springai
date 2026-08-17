# Component Catalog·ComponentRegistry 논리 계약 단일 원천화 영향검토 및 구현명세서

> 문서 버전: 1.0  
> 작성일: 2026-08-17  
> 상태: 구현 검토 완료 / 구현 착수 전  
> 관련 문서: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md), [19_Component_Catalog_Registry_SSOT_Implementation_List.md](./19_Component_Catalog_Registry_SSOT_Implementation_List.md)

---

## 1. 목적

현재 `component-catalog-v1.json`과 `ComponentRegistry`가 함께 보유한 논리 컴포넌트 정의를
Catalog로 일원화하고, ComponentRegistry는 특정 Figma Library Publish 결과를 보존하는
불변 Binding Snapshot으로 축소한다.

이 문서에서 말하는 단일 원천화는 두 파일을 하나로 합치는 물리적 통합이 아니다.

- Catalog: 논리 계약의 단일 진실 공급원(SSOT)
- Registry Snapshot: Catalog 논리 타입에 대한 실제 Published Figma 자산 연결
- Resolved Registry: 실행 시 Catalog와 Registry Snapshot을 결합한 읽기 전용 모델

과거 Registry 재현, 승인 이력, Rollback을 보장해야 하므로 Catalog와 Registry Snapshot은
서로 다른 버전과 저장 수명을 유지한다.

## 2. 결정 요약

| 항목 | 결정 |
|---|---|
| 물리적 단일 JSON | 채택하지 않음 |
| 논리 계약 SSOT | Component Catalog |
| Figma 공개 Key SSOT | 버전별 ComponentRegistry Snapshot |
| 공통 연결 키 | `logicalType` |
| 실행 모델 | Catalog + Registry Snapshot을 결합한 `ResolvedComponentRegistry` |
| Registry 불변성 | 승인·저장된 Snapshot 수정 금지 |
| 불일치 처리 | 생성·승인·Materialization 전에 fail-closed |
| 과거 실행 재현 | Generation Report에 Catalog/Registry 버전을 함께 기록 |

## 3. 현재 구조와 문제점

### 3.1 Component Catalog

현재 기준 파일은 `website-figma-contract/component-catalog-v1.json`이다.

주요 데이터:

- `contractVersion`
- `requiredComponents`, `optionalComponents`
- `patterns`, `pageTemplates`
- `logicalType`, `aliases`, `replacement`
- `figmaProperties`, `codeProperties`
- `fallbackPolicy`

Catalog는 논리 명칭과 속성 계약의 기준이지만 배열 기반 분류라서 동일한
`logicalType`이 `requiredComponents`와 `pageTemplates` 등 여러 위치에 중복될 가능성이 있다.

### 3.2 ComponentRegistry

현재 Java 모델은 `ComponentRegistry`와 `ComponentRegistryEntry`이며,
`AI_COMPONENT_REGISTRY`의 `REGISTRY_JSON`에 `PROFILE_ID + REGISTRY_VERSION` 단위로 저장한다.

Registry에 필요한 운영 데이터:

- `profileId`, `profileVersion`, `registryVersion`
- Library `fileKey`, 이름
- `componentSetKey`, Variant Component Key
- Variable/Collection Key
- Publish/Lifecycle 상태

현재 Registry에 중복 저장되는 논리 계약:

- `aliases`
- `replacementLogicalType`
- `properties`
- `roles`, `supportedPlatforms`, `requiredProperties`
- `codeComponent`, `documentationUrl`, `contractVersion`

이 중 어떤 항목을 Catalog가 소유하고 어떤 항목을 Profile별 Registry가 재정의할지 명시돼
있지 않아 두 JSON이 서로 다른 값을 가질 수 있다.

### 3.3 확인된 표현 차이

| Catalog | Registry 후보 | 판정 |
|---|---|---|
| `egov.pageHeader` | `krds.pageHeader` | 별칭인지 Pattern 합성인지 명시 필요 |
| `egov.dataTable` | `krds.tableHeader`, `krds.tableCell` | 단일 별칭이 아닌 복합 구성 관계 필요 |
| 없음(필수 목록 기준) | `krds.textarea` | 선택 컴포넌트 등록 여부 결정 필요 |

현재 `ComponentRegistryValidator`는 Registry 내부의 공개 Key, 별칭, Lifecycle, Variable
정합성을 검사하지만 Catalog 필수 타입과의 전체 교집합·합성 관계를 검증하지 않는다.

## 4. 목표 데이터 소유권

### 4.1 Catalog 소유 필드

| 필드 | 설명 |
|---|---|
| `logicalType` | 전 계층 공통 불변 식별자 |
| `kind` | `COMPONENT`, `PATTERN`, `PAGE_TEMPLATE` |
| `requirement` | `REQUIRED`, `OPTIONAL` |
| `aliases` | 입력·이전 명칭 호환 별칭 |
| `replacementLogicalType` | 폐기 타입의 논리 대체 대상 |
| `properties` | 논리 속성, Figma Property, 코드 속성 계약 |
| `composition` | Pattern/Template을 구성하는 하위 논리 타입 |
| `roles` | 컴포넌트의 의미 역할 |
| `supportedPlatforms` | 계약상 지원 플랫폼 |
| `requiredProperties` | 필수 논리 속성 |
| `codeComponent` | 코드 생성 대상 컴포넌트 |
| `documentationUrl` | 계약 문서 위치 |
| `fallback` | 누락·미지원·폐기 처리 정책 |

### 4.2 Registry Snapshot 소유 필드

| 필드 | 설명 |
|---|---|
| `profileId`, `profileVersion` | 적용 Design System Profile |
| `registryVersion` | 불변 Publish Snapshot 버전 |
| `catalogVersion` | 검증에 사용한 Catalog 버전 |
| `library` | Figma Library 식별 정보 |
| `bindings.*.componentSetKey` | Published Component Set Key |
| `bindings.*.variants` | Variant별 Published Component Key |
| `variables` | Published Variable/Collection Key |
| `publishStatus` | Snapshot 생성 시 Publish 상태 |
| `lifecycleStatus` | 해당 자산의 운영 상태 |
| `sourceRevision` | Publish 원본 추적 정보 |
| `approvedBy`, `approvedAt` | 사람 승인 증적 |

Profile별 예외가 필요한 경우 Registry에 원본 계약을 복제하지 않고 제한된
`overrides` 필드만 허용한다. 허용 필드와 사유가 Schema에 없는 Override는 실패시킨다.

## 5. 목표 JSON 계약

### 5.1 Component Catalog v2 예시

```json
{
  "schemaVersion": "component-catalog-v2",
  "contractVersion": "2.0.0",
  "components": {
    "krds.button": {
      "kind": "COMPONENT",
      "requirement": "REQUIRED",
      "aliases": ["button", "actionButton"],
      "replacementLogicalType": null,
      "properties": {
        "label": {
          "type": "TEXT",
          "figmaProperty": "Label",
          "codeProperty": "button.text",
          "values": {}
        }
      },
      "composition": [],
      "roles": ["action.submit"],
      "supportedPlatforms": ["DESKTOP", "TABLET", "MOBILE"],
      "requiredProperties": ["label"],
      "codeComponent": "KrdsButton",
      "documentationUrl": null
    },
    "egov.dataTable": {
      "kind": "PATTERN",
      "requirement": "REQUIRED",
      "aliases": ["table", "grid"],
      "replacementLogicalType": null,
      "properties": {},
      "composition": ["krds.tableHeader", "krds.tableCell"]
    }
  },
  "fallbackPolicy": {
    "required": "FATAL",
    "optional": "PREVIEW_ONLY",
    "unsupportedProperty": "PRESERVE_AS_METADATA",
    "deprecated": "RESOLVE_REPLACEMENT_OR_FAIL"
  }
}
```

### 5.2 ComponentRegistry Snapshot v3 예시

```json
{
  "schemaVersion": "component-registry-v3",
  "profileId": "krds",
  "profileVersion": "1.0.0",
  "registryVersion": "3.0.0",
  "catalogVersion": "2.0.0",
  "library": {
    "fileKey": "mVy5h1UbORVqQoBm8Wr1bT",
    "name": "FTC 정부 포털 Design System"
  },
  "bindings": {
    "krds.button": {
      "componentSetKey": "published-component-set-key",
      "componentName": "Button",
      "publishStatus": "CURRENT",
      "lifecycleStatus": "CURRENT",
      "variants": {
        "primary": "published-primary-component-key"
      }
    }
  },
  "variables": {},
  "sourceRevision": "figma-publish-revision",
  "approvedBy": "operator-id",
  "approvedAt": "2026-08-17T00:00:00Z"
}
```

## 6. 실행 시 결합 모델

`ResolvedComponentRegistryService`는 다음 입력을 받는다.

1. `catalogVersion`
2. `profileId`와 `profileVersion`
3. `registryVersion`
4. Screen Spec이 요구하는 `logicalType` 집합

결합 순서:

1. 정확한 Catalog 버전을 조회한다.
2. 정확한 Registry Snapshot 버전을 조회한다.
3. Registry의 `catalogVersion`과 요청 Catalog 버전을 비교한다.
4. 별칭을 canonical `logicalType`으로 변환한다.
5. Pattern과 Page Template의 `composition`을 재귀적으로 펼친다.
6. 모든 필수 원자 컴포넌트에 Published Binding이 있는지 확인한다.
7. 논리 속성과 실제 Figma Variant/Property의 호환성을 검사한다.
8. 검증을 통과한 읽기 전용 `ResolvedComponentRegistry`를 반환한다.

최신 버전을 암묵적으로 선택하는 경로는 Preview 편의 기능에서만 허용한다. 승인 Apply,
Materialization, Rollback에는 Catalog와 Registry의 정확한 버전이 반드시 전달돼야 한다.

## 7. 검증 및 fail-closed 규칙

다음 조건이면 Preview에 오류를 표시하고 Apply/Materialization을 차단한다.

| 오류 코드 | 조건 |
|---|---|
| `CATALOG_VERSION_NOT_FOUND` | 요청한 Catalog 버전이 없음 |
| `REGISTRY_VERSION_NOT_FOUND` | 요청한 Registry 버전이 없음 |
| `CATALOG_REGISTRY_VERSION_MISMATCH` | Snapshot의 Catalog 버전과 요청 버전 불일치 |
| `UNKNOWN_LOGICAL_TYPE` | Catalog에 없는 논리 타입 참조 |
| `REQUIRED_BINDING_MISSING` | 필수 원자 컴포넌트 Binding 누락 |
| `COMPOSITION_TARGET_MISSING` | 합성 대상이 Catalog에 없음 |
| `COMPOSITION_CYCLE` | Pattern/Page Template 합성 순환 |
| `PROPERTY_CONTRACT_MISMATCH` | 속성 타입 또는 Figma Property 불일치 |
| `VARIANT_BINDING_MISSING` | 필수 Variant의 공개 Key 누락 |
| `UNAPPROVED_REGISTRY` | 사람 승인이 없는 Snapshot 사용 |
| `PUBLISHED_KEY_DUPLICATED` | 서로 다른 타입이 같은 공개 Key를 소유 |

Optional 컴포넌트 누락은 Catalog의 fallback 정책에 따라서만 Preview 전용 대체를 허용하며,
그 결과를 Generation Report에 기록한다.

## 8. 코드 영향 범위

### 8.1 계약 및 fixture

- `component-catalog-v2.schema.json` 신규
- `component-registry-v3.schema.json` 신규
- v1/v2 → 신규 계약 변환 fixture와 invalid fixture 추가
- 계약 테스트에 교차 참조, 합성 순환, 필수 Binding 검증 추가

### 8.2 Spring 도메인

- `ComponentCatalog`, `ComponentCatalogEntry`, `ComponentComposition` 도입
- `ComponentRegistry`를 Snapshot 표현으로 변경하거나 별도 `ComponentRegistrySnapshotV3` 도입
- `ResolvedComponentRegistry`, `ResolvedComponentEntry` 도입
- 기존 `ComponentRegistryEntry.properties` 등 중복 필드는 전환 기간에 deprecated 처리

### 8.3 Service와 Validator

- `ComponentCatalogLoader`: 버전별 Catalog 로딩·캐시
- `ComponentCatalogValidator`: Catalog 내부 정합성 검사
- `ComponentRegistryBindingValidator`: Catalog 대비 Registry Snapshot 검사
- `ResolvedComponentRegistryService`: 결합과 alias/composition 해석
- `ComponentRegistrySyncService`: Publish 추출 결과를 Binding Snapshot으로 저장
- `ComponentRegistryResolver`: 기존 호출자를 새 결합 서비스로 위임

### 8.4 저장소와 마이그레이션

기존 Snapshot과 Rollback 호환을 위해 `AI_COMPONENT_REGISTRY.REGISTRY_JSON`은 유지한다.
다음 메타데이터 컬럼 추가를 검토한다.

```text
CATALOG_VERSION
SCHEMA_VERSION
SOURCE_REVISION
APPROVED_BY
APPROVED_AT
CONTENT_HASH
```

기존 Registry JSON을 제자리 수정하지 않는다. 변환된 v3 Snapshot은 새로운
`REGISTRY_VERSION`으로 저장하고 이전 버전을 계속 조회할 수 있어야 한다.

### 8.5 Export·Plugin·Report

- Export Bundle에 Catalog Snapshot 또는 `catalogVersion + contentHash` 포함
- Plugin은 Resolved Registry만 소비하고 Catalog/Registry 중복 병합을 직접 수행하지 않음
- Generation Report에 `catalogVersion`, `catalogHash`, `registryVersion`, `registryHash` 기록
- Apply 성공 결과가 어느 계약과 Binding으로 생성됐는지 재현 가능해야 함

## 9. 호환성 및 버전 정책

| 변경 | 버전 정책 |
|---|---|
| 별칭 추가, 선택 컴포넌트 추가 | Catalog MINOR |
| 필수 컴포넌트 추가, 속성 타입 변경 | Catalog MAJOR |
| Figma 공개 Key 변경 | Registry MAJOR 또는 승인된 신규 Snapshot |
| 동일 공개 Key의 메타데이터 보완 | Registry PATCH |
| Profile 지원 플랫폼 추가 | Catalog/Profile MINOR |
| 합성 구조의 의미 변경 | Catalog MAJOR |

전환 기간에는 v1 Catalog와 v2 Registry를 읽을 수 있어야 한다. 신규 쓰기는 v2 Catalog와
v3 Registry로만 허용하고, Legacy Reader 제거는 운영 Snapshot Rollback 기간 종료 후 별도
결정한다.

## 10. 단계별 전환 전략

### 단계 A — 관찰 모드

- 신규 Catalog 모델과 교차 Validator를 추가한다.
- 기존 생성 흐름은 유지한다.
- 불일치를 Warning Report로 수집한다.

### 단계 B — 이중 읽기

- 기존 Registry와 `Catalog + Binding Snapshot` 결과를 동시에 계산한다.
- 결과 차이를 테스트와 운영 Report로 비교한다.
- Apply 결과는 기존 경로를 유지한다.

### 단계 C — 신규 경로 우선

- Preview와 Export가 Resolved Registry를 사용한다.
- 불일치는 fail-closed 한다.
- 기존 Registry는 Rollback 경로로 유지한다.

### 단계 D — 중복 필드 제거

- Registry 신규 쓰기에서 Catalog 중복 필드를 제거한다.
- Legacy Reader는 과거 Snapshot 전용으로 유지한다.
- 운영 검증과 Rollback 리허설 후 신규 경로를 확정한다.

## 11. 테스트 및 완료 Gate

### 11.1 계약 테스트

- Catalog와 Registry 각각 JSON Schema 통과
- 모든 Registry Binding 키가 Catalog에 존재
- 모든 필수 원자 컴포넌트 Binding 존재
- 합성 참조 누락과 순환 검출
- Alias canonicalization 결정성 보장

### 11.2 단위·통합 테스트

- Catalog Loader 버전·Hash 검증
- Registry Snapshot 불변 저장과 멱등 재적용
- v1/v2 Legacy 변환
- Catalog/Registry 버전 불일치 fail-closed
- Preview와 Materialization의 Resolved 속성 동일성
- Generation Report 버전·Hash 기록

### 11.3 Figma Desktop E2E

- 7개 기준 화면 Preview/Apply 성공
- 필수 Binding 제거 시 Apply 차단
- `egov.dataTable` 합성이 실제 Table 하위 컴포넌트로 해석됨
- 새 Registry 적용 후 이전 Snapshot Rollback 성공
- Generation Report와 실제 생성 노드가 같은 Catalog/Registry 버전을 사용함

## 12. 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 기존 Registry fixture 대량 변경 | 테스트와 E2E 회귀 | Legacy Reader 및 자동 변환기 유지 |
| Catalog 변경이 모든 Profile에 전파 | Profile별 Publish 지연 | 호환 Catalog 범위와 승인 상태 관리 |
| Pattern 합성 과도한 복잡성 | 순환·모호한 해석 | DAG 검증과 단일 canonical 해석 규칙 |
| 최신 버전 암묵 선택 | 재현·Rollback 실패 | Apply에서 정확한 버전과 Hash 필수화 |
| 이중 읽기 기간 결과 차이 | 운영 결과 불일치 | 비교 Report와 전환 Gate 운영 |

## 13. 제외 범위

- Figma Library 자체의 컴포넌트 재디자인
- KRDS 전체 컴포넌트 카탈로그 확장
- 구현목록 Markdown의 JSON 데이터베이스화
- 과거 Registry Snapshot의 삭제 또는 제자리 변환
- 사람 승인 절차의 자동 승인 전환

## 14. 최종 승인 기준

다음 조건을 모두 만족하면 단일 원천화 구현을 완료로 판정한다.

1. 논리 계약이 Catalog 한 곳에서만 작성된다.
2. Registry 신규 Snapshot에는 Published Binding과 운영 증적만 저장된다.
3. Catalog와 Registry의 불일치가 Apply 전에 fail-closed 된다.
4. 기존 Registry Snapshot으로 실제 Rollback할 수 있다.
5. 7개 기준 화면 Figma Desktop E2E가 통과한다.
6. Generation Report로 사용 버전과 Hash를 재현할 수 있다.
7. 기존 v1/v2 산출물의 호환 또는 명확한 마이그레이션 오류가 보장된다.

