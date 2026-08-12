# KRDS Figma Role·Variant 구현목록

> `KRDS_Figma_Role_Variant_구현명세서.md` 실행 백로그

- 대상 시스템: `springai`
- 기준 명세: [`KRDS_Figma_Role_Variant_구현명세서.md`](./KRDS_Figma_Role_Variant_구현명세서.md)
- 작성일: 2026-08-11
- 문서 버전: 1.1.0
- 상태: 실제 Library Inventory·Q&A 6화면 Preview 자동 검증 완료, 사람 승인 후속

## 1. 추진 기준

### 1.1 우선순위

| 등급 | 의미 |
|---|---|
| P0 | 잘못된 Component·Variant 생성을 차단하는 필수 항목 |
| P1 | 품질 검증과 운영 안정성 확보 항목 |
| P2 | 확장성·운영 편의 개선 항목 |

### 1.2 상태

- `[ ]` 미착수
- `[~]` 진행 중 또는 부분 구현
- `[x]` 완료 및 수용 기준 통과
- `[!]` 차단됨

### 1.3 선행 원칙

Resolver와 Plugin을 동시에 전환하지 않습니다. 계약 v2와 회귀 Fixture를 먼저 확정하고 서버 Shadow Mode, Plugin Preview, Apply 순서로 활성화합니다.

## 2. 마일스톤

| 단계 | 목표 | 종료 조건 |
|---|---|---|
| M0 | 현행 기준선 고정 | Q&A 6개 입력·현재 결과·실패 유형 저장 |
| M1 | 의미 계약 v2 | Role·Pattern·Context Schema와 Java 모델 통과 |
| M2 | Component Contract v2 | KRDS Inventory와 Registry v2 승인 |
| M3 | 결정형 Resolver | Role·Variant 0/1/N 검증 통과 |
| M4 | Export 통합 | v2 Screen Spec·Bundle 생성, 폴백 0 |
| M5 | Plugin 엄격 적용 | 첫 Variant·Placeholder·로컬 이름 폴백 제거 |
| M6 | 5단계 Gate | Q&A 6개 구조·Layout·Visual 검증 통과 |
| M7 | 운영 전환 | Preview 승인·Rollback·지표·Runbook 완료 |

### 2.1 2026-08-11 구현 현황

아래 표가 현재 구현 상태의 기준입니다. 이후 절의 체크박스는 이 표와 동기화되어 있습니다 (동기화 기준일: 2026-08-13).

| 상태 | 작업 ID | 비고 |
|---|---|---|
| 완료 | KRV-001~003, 010~011, 014~015, 017, 020, 022~026, 028, 030~034, 040~048, 050~058, 060~063, 067~068, 076 | 실제 Published Library Inventory, Q&A 6개 Screen Spec·Runtime Resolver·Plugin Preview, 모델·결정 엔진·v2 계약·엄격 Plugin 및 전체 자동 테스트 완료 |
| 부분 | KRV-004, 012~013, 016, 021, 027, 035, 064~066, 071 | Drift·결정성·Layout·Accessibility·Visual 자동화와 승인 Workflow를 추가 보강해야 함 |
| 사람 승인/리허설 필요 | KRV-070, 073 | Design System Owner 승인과 이전 Snapshot Rollback 재생성 필요 |
| 후속 | KRV-049, 072, 074~075 | Shadow 비교, 영향 분석, 운영 지표, Runbook 보강 |

구현된 핵심 산출물은 다음과 같습니다.

- `SemanticRole`, `ScreenPattern`, `ComponentResolutionContext`, `ResolvedComponentRef`
- `ComponentRoleResolver`, `VariantRuleResolver`, `KrdsComponentResolutionService`
- `ComponentContractValidator`, `FigmaPropertyDriftValidator`, `ScreenPatternValidator`, `ScreenSuiteManifestValidator`
- Component Registry·Figma Screen Spec·Export Bundle v2 Schema
- Q&A 6화면 Suite Manifest와 KRDS 초기 Variant Rule Set
- Published Variant Key 직접 import 및 첫 Variant·첫 Component·로컬 이름·Placeholder 폴백 차단
- 실제 KRDS Inventory와 Q&A 6개 Screen Spec v2: `website-figma-contract/fixtures/qna/`
- Figma 검증 보고서와 운영 승인 체크리스트: `docs/figma/KRDS_QNA_6화면_*`
- Runtime 교차 검증: `./gradlew figmaRuntimeBundlePluginTest`

## 3. M0 — 기준선 및 Inventory

- [x] **KRV-001 · P0** Q&A 6개 원본 화면 식별자와 기대 화면 수를 Fixture Manifest로 고정
  - 대상: `website-figma-contract/fixtures/qna/qna-screen-suite-v1.json`
  - 작업: 목록·등록·상세·답변 목록·답변 상세·답변 등록의 ID, Pattern, Viewport, 필수 업무 요소 기록
  - 수용 기준: 화면 수가 6개가 아니면 계약 테스트 실패

- [x] **KRV-002 · P0** 현행 생성 결과 Snapshot 저장
  - 작업: 기존 3개 KRDS 프레임과 원본 6개 화면의 구조·스크린샷·Generation Report 저장
  - 수용 기준: 누락 화면, 일반 Text/Frame, 폴백, 잘못된 Variant가 증거와 함께 식별됨

- [x] **KRV-003 · P0** KRDS Component Set Inventory 추출
  - 대상: `PageHeader`, `SearchPanel`, `DataTable`, `FormPage`, `FormSection`, Field, Button, Pagination
  - 작업: Set Key, Variant Key, 공개 Property, Axis, 허용 값, Auto Layout, Platform 추출
  - 수용 기준: 필수 Component 전부에 Inventory 행이 존재하고 중복 Key가 없음

- [~] **KRV-004 · P1** 기존 Registry와 실제 Library Drift 보고서 생성
  - 수용 기준: 누락 Property·값·Variant·Lifecycle이 코드별로 보고됨

## 4. M1 — Semantic Role 및 Screen Pattern

- [x] **KRV-010 · P0** `SemanticRole` enum과 JSON 직렬화 구현
  - 추가: `model/design/role/SemanticRole.java`
  - 테스트: 유효 코드 round-trip, 알 수 없는 코드 거부
  - 수용 기준: `page.header` 등 표준 코드만 허용

- [x] **KRV-011 · P0** `ScreenPattern`, `FieldMode`, `ComponentState`, `Platform` 구현
  - 수용 기준: CREATE와 EDIT, DETAIL Read-only를 Context로 구별 가능

- [~] **KRV-012 · P0** `ScreenActionSpec` 추가 및 문자열 Action 호환 변환기 구현
  - 수정: `PageSpec.actions`
  - 추가: `ScreenSemanticNormalizer`
  - 수용 기준: `DELETE`는 `action.destructive`, `LIST`는 `action.secondary`로 결정됨

- [~] **KRV-013 · P0** `ScreenFieldBinding`에 UI Semantic Role과 Field Mode 추가
  - 주의: 기존 `UiFieldRole`은 데이터 역할이므로 이름을 `dataRole`로 명확화
  - 수용 기준: 데이터 역할과 UI 역할이 서로 독립적으로 직렬화됨

- [x] **KRV-014 · P0** Screen Pattern 계약 Schema·기준 파일 작성
  - 추가: `screen-patterns-v1.schema.json`, `screen-patterns-v1.json`
  - 수용 기준: `crud.list/detail/create/edit`의 Slot·Cardinality가 Schema를 통과

- [x] **KRV-015 · P0** `ScreenPatternValidator` 구현
  - 테스트: 필수 Slot 누락, 중복 Slot, Cardinality 초과, Action 누락
  - 수용 기준: 각 화면이 선택한 Pattern의 Slot 계약을 통과

- [~] **KRV-016 · P1** 기존 ScreenSpecification v1→v2 Migration Preview 구현
  - 수용 기준: 불확실한 Control 또는 Action을 임의 변환하지 않고 검토 항목으로 반환

- [x] **KRV-017 · P0** `ScreenSuiteManifestValidator` 구현
  - 테스트: 기대 화면 누락, 중복 Screen ID, 잘못된 Pattern, 추가 화면 정책
  - 수용 기준: Q&A 5개 또는 3개 화면 입력은 실패하고 지정된 6개 화면만 통과

## 5. M2 — Component Contract 및 Rule Set

- [x] **KRV-020 · P0** `ComponentRegistryEntry` v2 확장
  - 추가 필드: roles, platforms, variantAxes, requiredProperties, codeComponent, documentationUrl
  - 수용 기준: v2 직렬화 round-trip 및 v1 Reader 호환 통과

- [~] **KRV-021 · P0** Lifecycle 상태 정규화
  - 작업: `DRAFT`, `CURRENT`, `DEPRECATED`, `REMOVED` 적용 및 Publish 상태 관계 검증
  - 수용 기준: 신규 생성은 CURRENT/CURRENT 조합만 허용

- [x] **KRV-022 · P0** `component-registry-v2.schema.json` 작성
  - 수용 기준: Role·Axis·Property·Platform 누락 Fixture가 실패

- [x] **KRV-023 · P0** KRDS Registry v2 후보 작성
  - 대상: Q&A 6개 화면에 필요한 Logical Type 전부
  - 수용 기준: Field·Button·Table Cell에 실제 Published Key와 Property 계약 존재

- [x] **KRV-024 · P0** Variant Rule Set 모델과 Schema 구현
  - 추가: `VariantRuleSet`, `VariantRule`, `RuleCondition`
  - 추가 계약: `variant-rule-set-v1.schema.json`
  - 수용 기준: 동일 Rule ID, 잘못된 Axis, 비정상 Priority가 차단됨

- [x] **KRV-025 · P0** KRDS 초기 결정표 작성
  - 추가: `variant-rule-set-krds-v1.json`
  - 필수 Context: Pattern, Role, Platform, Mode, State, Density, Required, Field Count
  - 수용 기준: Q&A 6개 모든 Component Context에 정확히 한 Rule이 일치

- [x] **KRV-026 · P0** `ComponentContractValidator` 구현
  - 수용 기준: 필수 Property, Axis, Value, Platform, Lifecycle 오류를 모두 차단

- [~] **KRV-027 · P1** `FigmaPropertyDriftValidator` 구현
  - 입력: Registry v2와 Author Plugin 또는 Figma Library Snapshot
  - 수용 기준: Property 이름·Type·Variant 조합 차이를 명확한 오류 코드로 반환

- [x] **KRV-028 · P1** Rule Set·Pattern 불변 저장소 구현
  - 추가: `VariantRuleSetRepository`, `ScreenPatternRepository`
  - 수용 기준: 동일 Version 다른 내용 저장이 충돌로 거부됨

## 6. M3 — 결정형 Resolver

- [x] **KRV-030 · P0** `ComponentResolutionContext` 구현
  - 수용 기준: null 허용 필드와 필수 필드가 명시되고 안정적인 Context Hash 생성

- [x] **KRV-031 · P0** `ComponentRoleResolver` 구현
  - 의존: 기존 `ComponentRegistryResolver` 재사용
  - 테스트: 0개, 1개, 복수 후보, alias, replacement, Platform 불일치
  - 수용 기준: 후보가 정확히 하나가 아니면 결과를 반환하지 않음

- [x] **KRV-032 · P0** `VariantRuleResolver` 구현
  - 테스트: 정확 일치, Rule 없음, 동순위 복수, Axis 누락, 허용 값 위반
  - 수용 기준: 모든 Q&A Context가 Variant Key 하나로 결정됨

- [x] **KRV-033 · P0** `ResolvedComponentRef` 구현
  - 수용 기준: Role, Logical Type, Set Key, Variant Key, Property, Contract·Rule Version 포함

- [x] **KRV-034 · P0** `ComponentResolutionValidator` 구현
  - 수용 기준: UI Component 노드 중 미해결 항목이 하나라도 있으면 Export 실패

- [~] **KRV-035 · P1** 결정성 테스트 추가
  - 작업: Registry Map 순서와 Rule 입력 순서를 무작위 변경하여 결과 비교
  - 수용 기준: 동일 Version·Context의 결과 Hash가 항상 동일

## 7. M4 — Builder·Export·Bundle 통합

- [x] **KRV-040 · P0** Builder를 Semantic Node 생성 방식으로 변경
  - 수정: `BuilderSupport`, `FieldComponentMapper`, LIST/FORM/DETAIL Builder
  - 수용 기준: Builder 출력에 Figma Key와 Figma Variant 이름이 없음

- [x] **KRV-041 · P0** Action Variant 직접 지정 제거
  - 제거 대상: `BuilderSupport.actionButton()`의 `variant=primary/secondary`
  - 수용 기준: Action Role만으로 Rule Resolver가 Variant를 결정

- [x] **KRV-042 · P0** 미지원 Control의 textField 자동 대체 제거
  - 수용 기준: 명시된 Rule 또는 Replacement Contract가 없으면 `SEMANTIC_ROLE_NOT_DERIVED`

- [x] **KRV-043 · P0** DataTable Cell을 Published Component Role로 변경
  - 제거 대상: `NodeType.TEXT` 기반 `krds.tableCell`
  - 수용 기준: 모든 Header·Body Cell이 Registry Instance로 해석됨

- [x] **KRV-044 · P0** `FigmaScreenExportService` 파이프라인 재구성
  - 순서: Pattern → Role → Variant → Registry Drift → Spec
  - 수용 기준: 해석 오류 시 Spec 저장과 Bundle 생성이 발생하지 않음

- [x] **KRV-045 · P0** 빈 기본 Profile·Registry 폴백 제거
  - 제거 대상: `defaultProfile()`, 경고형 `PROFILE_NOT_FOUND`
  - 수용 기준: PUBLISHED Profile과 정확한 Registry Version이 없으면 FATAL

- [x] **KRV-046 · P0** `FigmaScreenSpec` v2 및 Schema 구현
  - 수용 기준: 모든 UI 노드에 `ResolvedComponentRef`가 포함됨

- [x] **KRV-047 · P0** `FigmaExportBundle` v2 구현
  - 추가 Snapshot: Pattern, Rule Set, Contract Version
  - 수용 기준: 모든 버전 불일치 Fixture 실패

- [x] **KRV-048 · P1** MCP Facade Redaction 확장
  - 수용 기준: Set Key·Variant Key·Variable Key가 MCP 응답과 로그에 노출되지 않음

- [ ] **KRV-049 · P1** Shadow Mode 구현
  - 작업: v1 결과와 v2 해결 결과를 비교하되 Apply하지 않음
  - 수용 기준: 차이를 화면·노드·Rule ID 단위로 조회 가능

## 8. M5 — Figma Plugin 엄격 적용

- [x] **KRV-050 · P0** Plugin v2 Type과 Bundle Validator 구현
  - 수정: `figma-screen-spec-plugin/src/types.ts`, `core.ts`
  - 수용 기준: v1 Bundle은 Migration Preview만 가능

- [x] **KRV-051 · P0** 첫 Variant 선택 제거
  - 제거 대상: `Object.keys(entry.variants)[0]`
  - 수용 기준: Variant 정보가 없으면 FATAL

- [x] **KRV-052 · P0** Variant 불일치 시 첫 Component 선택 제거
  - 제거 대상: 두 번째 `componentSet.children.find(...)`
  - 수용 기준: 0개·복수 Variant 모두 Apply 불가

- [x] **KRV-053 · P0** 서버가 지정한 Variant Key 직접 import 구현
  - 수용 기준: 적용 Instance의 Main Component Key가 해결 결과와 일치

- [x] **KRV-054 · P0** 정상 Apply의 Placeholder 폴백 제거
  - 수정: `planFallback`, `ensureFallbackPlaceholder`, `syncNode`
  - 수용 기준: Registry 누락·import 실패 시 Wrapper 또는 Placeholder가 생성되지 않음

- [x] **KRV-055 · P0** 로컬 이름 기반 Component Set 폴백 제거
  - 제거 대상: `findLocalComponent`, `findLocalComponentSet`의 Apply 사용
  - 수용 기준: Published Key import 실패는 FATAL

- [x] **KRV-056 · P0** `fallbackCount=0` Gate 적용
  - 수용 기준: 1건 이상이면 Generation Report가 FAILED이며 APPLIED 전이 불가

- [x] **KRV-057 · P0** DETAIL 화면 지원
  - 제거 대상: `SCREEN_TYPE_UNSUPPORTED` DETAIL 차단
  - 수용 기준: Q&A 상세·답변 상세 Preview와 Apply 성공

- [x] **KRV-058 · P1** Property 계약 재검증
  - 수용 기준: 미공개 Property는 무시가 아닌 오류, 필수 Property 누락은 FATAL

## 9. M6 — 품질 Gate 및 회귀 테스트

- [x] **KRV-060 · P0** Q&A 6개 ScreenSpecification v2 Fixture 작성
  - 수용 기준: 업무 필드·액션·Pattern이 기준 문서와 일치

- [x] **KRV-061 · P0** Java Resolver 회귀 Suite 추가
  - 수용 기준: 6개 화면의 모든 Role·Variant가 유일하게 해결됨

- [x] **KRV-062 · P0** Contract Test에 v2 Schema와 오류 Fixture 연결
  - 수정: `website-figma-contract/test/contract-test.mjs`
  - 수용 기준: `./gradlew figmaContractTest` 통과

- [x] **KRV-063 · P0** Plugin 단위 테스트 보강
  - 수용 기준: 첫 Variant·첫 Component·Placeholder·로컬 이름 폴백을 금지하는 음수 테스트 통과

- [~] **KRV-064 · P1** Layout Gate 구현
  - 검사: Overflow, Overlap, 최소 크기, Auto Layout
  - 수용 기준: 오류 Fixture가 Apply 차단됨

- [~] **KRV-065 · P1** Accessibility Gate 구현
  - 검사: Focus, Error, Disabled, Read-only, Target Size
  - 수용 기준: 필수 State Variant 누락 시 Registry 승인 실패

- [~] **KRV-066 · P1** Visual Regression Fixture 구축
  - 대상: Desktop 동일 Viewport의 Q&A 6개
  - 수용 기준: 기준선·임계값·Diff Artifact가 화면별로 저장됨
  - 구현: 최초 신규 Frame의 PNG Hash를 기준선으로 저장하고 이후 staging PNG Hash를 0% 임계값으로 비교한다. 기존 Frame에 기준선이 없으면 자동 승인하지 않고 Apply를 롤백한다. 보고서에는 baseline/evidence Hash와 diffRatio가 저장된다.

- [x] **KRV-067 · P0** 6개 화면 수 검증을 Release Gate로 연결
  - 수용 기준: 생성 Frame이 6개 미만이거나 중복이면 CI 실패

- [x] **KRV-068 · P1** 전체 검증 실행
  - 명령: `./gradlew test`, `./gradlew figmaContractTest`, Plugin `npm test`
  - 수용 기준: 관련 테스트 전부 통과하고 기존 핵심 회귀 없음

## 10. M7 — 승인·운영 전환

- [~] **KRV-070 · P0** Registry v2 Preview와 사람 승인 기록
  - 수용 기준: Design System Owner 승인 이벤트와 Version 연결

- [~] **KRV-071 · P0** Pattern·Rule Set 승인 Workflow 구현
  - 수용 기준: 미승인 Rule Set은 Export에 사용할 수 없음

- [ ] **KRV-072 · P1** Breaking Change 영향 분석 확장
  - 검사: Role 제거, Axis 제거, Property 이름 변경, Rule 결과 변경
  - 수용 기준: 영향받는 최신 Screen Spec 목록 제공

- [~] **KRV-073 · P0** Rollback 리허설
  - 수용 기준: 이전 Registry·Rule Set·Pattern Snapshot으로 Preview 재생성 성공

- [ ] **KRV-074 · P1** 운영 지표와 로그 추가
  - 수용 기준: Role/Variant/Drift/Visual 실패 건수와 처리 시간 조회 가능

- [ ] **KRV-075 · P1** Runbook 갱신
  - 대상: `13_Semantic_Figma_Operations_Runbook.md`
  - 수용 기준: Inventory, Preview, 승인, Publish, Rollback, 장애 대응 절차 포함

- [x] **KRV-076 · P0** v2 기본 전환 및 v1 Apply 차단
  - 수용 기준: 운영 Bundle은 v2만 생성되고 v1은 읽기·Migration Preview만 가능

## 11. 구현 순서와 의존성

```text
KRV-001~004
    ↓
KRV-010~016
    ↓
KRV-020~028
    ↓
KRV-030~035
    ↓
KRV-040~049
    ↓
KRV-050~058
    ↓
KRV-060~068
    ↓
KRV-070~076
```

병렬 수행 가능한 작업은 다음과 같습니다.

- M1 Java 모델과 M2 JSON Schema 초안
- M3 Resolver 단위 테스트와 M0 Fixture 정리
- M4 서버 통합과 M5 Plugin v2 Type 준비
- M6 Layout Gate와 Visual Fixture 구축

## 12. 코드 변경 예상 목록

### 12.1 신규 Java 파일

- `model/design/role/SemanticRole.java`
- `model/design/role/ScreenPattern.java`
- `model/design/role/FieldMode.java`
- `model/design/role/ComponentState.java`
- `model/design/role/Platform.java`
- `model/design/ScreenActionSpec.java`
- `model/designsystem/VariantAxisDefinition.java`
- `model/designsystem/VariantRuleSet.java`
- `model/designsystem/VariantRule.java`
- `model/designsystem/ScreenPatternDefinition.java`
- `model/figma/ResolvedComponentRef.java`
- `service/figma/ScreenSemanticNormalizer.java`
- `service/figma/ScreenPatternValidator.java`
- `service/figma/ScreenSuiteManifestValidator.java`
- `service/designsystem/ComponentRoleResolver.java`
- `service/designsystem/VariantRuleResolver.java`
- `service/designsystem/ComponentContractValidator.java`
- `service/designsystem/FigmaPropertyDriftValidator.java`
- `service/figma/ComponentResolutionValidator.java`
- `mapper/VariantRuleSetRepository.java`
- `mapper/ScreenPatternRepository.java`

### 12.2 주요 수정 Java 파일

- `model/design/PageSpec.java`
- `model/design/ScreenFieldBinding.java`
- `model/designsystem/ComponentRegistryEntry.java`
- `model/designsystem/ComponentRegistry.java`
- `model/figma/FigmaNodeSpec.java`
- `model/figma/FigmaScreenSpec.java`
- `model/figma/FigmaExportBundle.java`
- `service/figma/builder/BuilderSupport.java`
- `service/figma/builder/FieldComponentMapper.java`
- `service/figma/builder/ListFigmaScreenBuilder.java`
- `service/figma/builder/FormFigmaScreenBuilder.java`
- `service/figma/builder/DetailFigmaScreenBuilder.java`
- `service/figma/FigmaScreenExportService.java`
- `service/figma/FigmaScreenSpecValidator.java`
- `service/designsystem/ComponentRegistryValidator.java`
- `service/designsystem/DesignSystemQueryService.java`
- `service/figma/FigmaMcpFacadeService.java`

### 12.3 TypeScript·계약 파일

- `figma-screen-spec-plugin/src/types.ts`
- `figma-screen-spec-plugin/src/core.ts`
- `figma-screen-spec-plugin/src/code.ts`
- `figma-screen-spec-plugin/test/core.test.mjs`
- `website-figma-contract/*.schema.json`
- `website-figma-contract/fixtures/qna/*.json`
- `website-figma-contract/test/contract-test.mjs`

## 13. 릴리스 차단 체크리스트

- [ ] Q&A 화면 수가 정확히 6개임
- [ ] 모든 화면이 승인된 Screen Pattern을 사용함
- [ ] Role 미해결 0건
- [ ] Variant 미해결·복수 해석 0건
- [ ] Property Drift 0건
- [ ] 일반 Text·Frame 기반 Field·Cell·Button 0건
- [ ] 첫 Variant 또는 첫 Component 선택 코드 0건
- [ ] 정상 Apply의 Placeholder·로컬 이름 폴백 0건
- [ ] Generation Report `fallbackCount=0`
- [ ] DETAIL 포함 Plugin 테스트 통과
- [ ] Layout·Accessibility 오류 0건
- [ ] Visual Diff 승인 완료
- [ ] Preview와 사람 승인 이력 존재
- [ ] Rollback 리허설 완료

## 14. 완료 정의

본 구현은 단순히 6개 Frame을 생성했을 때 완료된 것으로 보지 않습니다. 동일 ScreenSpecification·Registry·Pattern·Rule Set Version으로 반복 생성했을 때 동일한 Published Component와 Variant가 선택되고, Q&A 6개 화면의 업무 요소·레이아웃·시각 기준이 모두 검증되어야 완료됩니다.
