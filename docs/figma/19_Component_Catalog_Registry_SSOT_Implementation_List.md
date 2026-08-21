# Component Catalog·ComponentRegistry 논리 계약 단일 원천화 구현목록

> 문서 버전: 1.0  
> 작성일: 2026-08-17  
> 기준 명세: [18_Component_Catalog_Registry_SSOT_Impact_and_Implementation_Specification.md](./18_Component_Catalog_Registry_SSOT_Impact_and_Implementation_Specification.md)  
> 상태: 핵심 계약·Resolver·Registry v3 저장 구현 완료 / 운영 전환·Figma E2E 진행 전

---

## 1. 상태와 우선순위

| 표기 | 의미 |
|---|---|
| `[x]` | 코드·테스트·증적으로 완료 확인 |
| `[~]` | 일부 구현 또는 기존 자산 재사용 가능, 목표 Gate 미충족 |
| `[ ]` | 미구현 |
| `[!]` | 선행 결정이나 외부 조건으로 차단 |

| 우선순위 | 의미 |
|---|---|
| P0 | 계약 정합성·안전성·Rollback에 필수 |
| P1 | 운영 전환에 필요 |
| P2 | 운영 편의와 유지보수 개선 |

## 2. 현재 기준선

- [x] **SSOT-BASE-001 · P0** `component-catalog-v1.json`과 Schema 존재
- [x] **SSOT-BASE-002 · P0** `component-registry-v2.schema.json`과 Registry fixture 존재
- [x] **SSOT-BASE-003 · P0** `ComponentRegistry`, `ComponentRegistryEntry` Java 모델 존재
- [x] **SSOT-BASE-004 · P0** `AI_COMPONENT_REGISTRY` 버전별 JSON 저장소 존재
- [x] **SSOT-BASE-005 · P0** `ComponentRegistrySyncService`, Validator, Resolver 존재
- [x] **SSOT-BASE-006 · P1** Registry 불변 저장·차이 분석·Rollback 기반 존재
- [~] **SSOT-BASE-007 · P0** Catalog와 Registry가 `logicalType`을 공유하지만 자동 교차 검증은 없음
- [~] **SSOT-BASE-008 · P0** Registry가 별칭·속성·대체 관계 등 Catalog 계약을 중복 보유

## 3. 선행 결정

- [x] **SSOT-DEC-001 · P0** Catalog를 논리 계약의 유일한 소유자로 승인 — 18번 명세와 `CONTRACT_RULES.md` §5.1
- [x] **SSOT-DEC-002 · P0** Registry를 버전별 Published Binding Snapshot으로 정의 — `component-registry-v3`
- [x] **SSOT-DEC-003 · P0** `logicalType`을 Catalog·Registry·Screen Spec 공통 식별자로 확정
- [x] **SSOT-DEC-004 · P0** `egov.pageHeader`를 `krds.pageHeader` 합성 Pattern으로 확정
- [x] **SSOT-DEC-005 · P0** `egov.dataTable`을 `krds.tableHeader`·`krds.tableCell` 합성으로 확정
- [x] **SSOT-DEC-006 · P1** `krds.textarea`를 선택 Component로 확정하고 `krds.textField` 대체 경로 지정
- [~] **SSOT-DEC-007 · P0** 신규 `ResolvedComponentRegistryService`는 정확한 Catalog/Registry 버전을 요구하나 기존 Apply/Materialization 전체 호출 경로 전환은 남음
- [x] **SSOT-DEC-008 · P0** Registry v3에서 Catalog 중복 필드를 제거하고 Binding·Publish 증적만 유지

## 4. 계약과 Schema

- [x] **SSOT-R0-001 · P0** `component-catalog-v2.schema.json` 작성
- [x] **SSOT-R0-002 · P0** 배열 대신 `logicalType` Map 기반 Catalog 구조 정의
- [x] **SSOT-R0-003 · P0** `kind`(`COMPONENT`/`PATTERN`/`PAGE_TEMPLATE`) 정의
- [x] **SSOT-R0-004 · P0** `requirement`(`REQUIRED`/`OPTIONAL`) 정의
- [x] **SSOT-R0-005 · P0** `properties`에 논리·Figma·코드 속성 계약 통합
- [x] **SSOT-R0-006 · P0** `composition`과 순환 금지 규칙 정의
- [x] **SSOT-R0-007 · P0** alias·replacement canonicalization 규칙 정의
- [x] **SSOT-R0-008 · P0** `component-registry-v3.schema.json` 작성
- [x] **SSOT-R0-009 · P0** Registry `catalogVersion`·`schemaVersion`·`sourceRevision` 정의
- [x] **SSOT-R0-010 · P0** Registry 승인자·승인시각·content hash 계약 정의
- [ ] **SSOT-R0-011 · P1** Profile별 제한적 `overrides` Schema와 허용 정책 정의
- [x] **SSOT-R0-012 · P0** Catalog/Registry 교차 검증 오류 코드 정의
- [x] **SSOT-R0-013 · P1** v1 Catalog → v2 결정형 변환 규칙·누락 합성 대상 fail-closed 구현
- [x] **SSOT-R0-014 · P1** v2 Registry → v3 Binding Snapshot 결정형 변환·Hash·비원자 Binding 제외 규칙 구현

### R0 완료 Gate

- [x] **SSOT-R0-T01** 정상 Catalog·Registry fixture가 각 Schema를 통과
- [x] **SSOT-R0-T02** alias 충돌과 replacement 순환을 계약/Java Validator가 차단
- [x] **SSOT-R0-T03** composition 누락·순환을 계약/Java Validator가 차단
- [x] **SSOT-R0-T04** Registry의 미등록 logicalType과 필수 Binding 누락이 실패
- [x] **SSOT-R0-T05** v2 Catalog와 v3 Registry 예제가 실제 Schema를 통과

## 5. Spring 도메인과 Loader

- [x] **SSOT-R1-001 · P0** `ComponentCatalog` 모델 구현
- [x] **SSOT-R1-002 · P0** `ComponentCatalog.Entry`와 Property 계약 모델 구현
- [x] **SSOT-R1-003 · P0** `composition` 모델 구현
- [x] **SSOT-R1-004 · P0** `ComponentRegistrySnapshotV3` Binding 모델 구현
- [x] **SSOT-R1-005 · P0** `ResolvedComponentRegistry` 읽기 전용 모델 구현
- [x] **SSOT-R1-006 · P0** 버전별 `ComponentCatalogLoader` 구현
- [x] **SSOT-R1-007 · P1** Catalog SHA-256 계산과 버전 캐시 구현
- [~] **SSOT-R1-008 · P1** 기존 Registry v2 Repository Reader 재사용, Catalog v1 전용 Reader는 남음
- [x] **SSOT-R1-009 · P1** `ComponentRegistryV2ToV3Converter` Legacy 호환 Adapter 구현

### R1 완료 Gate

- [x] **SSOT-R1-T01** Catalog JSON 직렬화·역직렬화 round-trip
- [x] **SSOT-R1-T02** 같은 Catalog의 content hash 결정성 보장
- [~] **SSOT-R1-T03** Registry v2→v3 및 Catalog v1→v2 결정성 테스트 통과, Legacy Catalog의 실제 운영 교체 승인 Gate는 남음
- [x] **SSOT-R1-T04** Legacy Reader가 기존 운영 Snapshot을 손실 없이 조회 — (2026-08-20)
      `ComponentRegistryRepositoryIntegrationTest.legacyShapedRegistryJsonIsReadWithoutDataLoss()`
      신규. `repository.save()`로 만든 현재-형태 JSON이 아니라 가장 오래된 R1 형태
      (`componentSetKey`/`properties`만 있는 raw JSON)를 SQL로 직접 삽입해 진짜 legacy
      데이터를 재현하고, `findVersion()`으로 조회한 결과가 예외 없이 실제 데이터
      (componentSetKey, properties)를 보존하며 당시 없던 필드는 기존 legacy 호환
      생성자와 동일한 기본값(UNPUBLISHED/ACTIVE/빈 컬렉션)으로 해석됨을 확인

## 6. Validator와 Resolver

- [x] **SSOT-R2-001 · P0** `ComponentCatalogValidator` 구현
- [x] **SSOT-R2-002 · P0** `ComponentRegistryBindingValidator` 구현
- [x] **SSOT-R2-003 · P0** alias를 canonical logicalType으로 변환
- [x] **SSOT-R2-004 · P0** composition DAG 해석과 순환 검출 구현
- [x] **SSOT-R2-005 · P0** 필수 원자 컴포넌트 Binding 완전성 검사
- [x] **SSOT-R2-006 · P0** Binding Validator가 Catalog Variant 허용값과 Registry Variant Key 호환성을 검증
- [x] **SSOT-R2-007 · P0** Catalog/Registry 버전·Hash 불일치 fail-closed
- [x] **SSOT-R2-008 · P0** 미승인 Registry Resolve/Apply 차단
- [x] **SSOT-R2-009 · P0** `ResolvedComponentRegistryService` 구현
- [x] **SSOT-R2-010 · P1** 기존 Resolver에 v3 입력 위임 경로 추가
- [x] **SSOT-R2-011 · P1** Resolved Registry Preview에 Optional fallback 정책과 Issue 기록 연결

### R2 완료 Gate

- [x] **SSOT-R2-T01** Catalog에 없는 Registry Binding이 실패
- [x] **SSOT-R2-T02** 필수 Binding 누락 시 Preview 오류, Apply 차단
- [x] **SSOT-R2-T03** Optional 누락은 Preview-only fallback, Apply는 fail-closed로 공통 처리
- [x] **SSOT-R2-T04** `egov.dataTable` 합성이 결정적으로 해석됨
- [x] **SSOT-R2-T05** 정확한 Catalog/Registry 버전·Hash 조합만 Resolve 가능

## 7. Registry Sync와 저장소

- [x] **SSOT-R3-001 · P0** Author Plugin UI에서 서버 결합 Registry를 Binding-only v3 후보 JSON으로 Export
- [x] **SSOT-R3-002 · P0** `ComponentRegistrySnapshotV3SyncService`가 Sync 전 Catalog 교차 검증 수행
- [x] **SSOT-R3-003 · P0** 승인 전 후보 Preview와 actor·시각이 기록된 승인 Snapshot 구분
- [x] **SSOT-R3-004 · P0** `saveImmutable`과 복합 PK로 기존 Snapshot 불변성 유지
- [x] **SSOT-R3-005 · P0** Catalog/Schema/Source Revision/Hash 메타데이터 저장
- [x] **SSOT-R3-006 · P1** `V12__component_registry_v3_ssot.sql` DB 마이그레이션 추가
- [x] **SSOT-R3-007 · P1** 동일 내용·동일 버전 재동기화는 기존 승인 Snapshot 반환
- [x] **SSOT-R3-008 · P0** Published Component/Variant Key Breaking Change를 `breakingChangeConfirmed=true` 별도 승인 없이는 Apply 차단
- [x] **SSOT-R3-009 · P0** `ComponentRegistryRollbackService`와 명시적 확인 REST 경로로 이전 승인 Snapshot 연결 Rollback 구현

### R3 완료 Gate

- [x] **SSOT-R3-T01** 동일 Registry Snapshot 중복 저장 멱등 처리 및 다른 내용 버전 충돌 구현
- [~] **SSOT-R3-T02** 승인되지 않은 후보 저장은 차단되지만 기존 DesignSystemProfile 운영 연결은 v3로 전환 전
- [x] **SSOT-R3-T03** 이전 승인 Snapshot 조회·연결 Rollback 테스트 통과
- [x] **SSOT-R3-T04** `ComponentRegistryMigrationService.MigrationPreview`가 기존/변환 Binding 수·제외 사유·검증 Issue 제공

## 8. Export·Plugin·Generation Report

- [x] **SSOT-R4-001 · P0** SSOT Export Bundle Metadata에 Catalog 버전과 SHA-256 포함
- [x] **SSOT-R4-002 · P0** SSOT Export Bundle Metadata에 Registry 버전과 Hash 포함
- [x] **SSOT-R4-003 · P0** `/download-ssot`와 `/preview`가 Resolved Registry를 사용하고 `APP_FIGMA_SSOT_BUNDLE_ENABLED=true`에서 기본 경로도 SSOT로 전환
- [x] **SSOT-R4-004 · P0** Plugin Preview와 Apply가 서버 생성 `resolvedComponentRegistry` 투영을 동일하게 사용
- [x] **SSOT-R4-005 · P0** Plugin은 Catalog/Registry를 병합하지 않고 서버 결합 투영을 우선 소비
- [x] **SSOT-R4-006 · P0** Plugin Generation Report `ssotEvidence`에 Catalog/Registry 버전·Hash 전달
- [x] **SSOT-R4-007 · P0** Generation Report Change에 logicalType과 실제 Published Variant `componentKey` 기록
- [x] **SSOT-R4-008 · P1** Optional Component fallback을 `OPTIONAL_COMPONENT_NOT_IN_REGISTRY` Issue와 `fallbackCount`로 기록하고 Plugin Data에 표시

### R4 완료 Gate

- [x] **SSOT-R4-T01** Preview 검증과 Apply `registryFor()`가 동일 서버 결합 투영을 선택하는 parity 테스트 통과
- [x] **SSOT-R4-T02** Generation Report Schema와 Plugin 타입에 계약/Binding 버전·Hash·Component Key 증적 반영
- [x] **SSOT-R4-T03** Registry v3 Apply 전 Published Binding payload를 재계산해 `REGISTRY_CONTENT_HASH_MISMATCH`이면 저장 차단
- [x] **SSOT-R4-T04** Plugin Materialization이 자식·Published Instance 적용 완료 후에만 재사용/신규 성공 건수로 집계되도록 보정

## 9. 호환 전환

- [x] **SSOT-R5-001 · P0** `observe-v3`에서 Legacy/Resolved Registry를 함께 실행하고 Key·Variant 차이를 비교
- [x] **SSOT-R5-002 · P0** `observe-v3` 비교 결과를 DB Snapshot Report로 저장·반환
- [x] **SSOT-R5-003 · P0** `dual-read-v3`에서 v3 Resolver를 병렬 실행하되 호환 기간 선택값은 Legacy로 유지
- [x] **SSOT-R5-004 · P1** `APP_FIGMA_SSOT_BUNDLE_ENABLED` Feature Flag로 기본 다운로드의 신규 SSOT 경로 우선 전환 구현
- [x] **SSOT-R5-005 · P0** `/download-ssot`는 Registry v3/Resolver 오류 시 자동 Legacy Bundle 적용 없이 실패
- [x] **SSOT-R5-006 · P0** `confirmed=true`와 actor를 모두 요구하는 운영자 명시 Rollback만 허용
- [x] **SSOT-R5-007 · P1** Registry v3 Schema·Contract Test에서 Binding을 Published Key/Variant 운영 필드로 제한하고 Catalog 논리 계약 중복 필드 차단
- [x] **SSOT-R5-008 · P2** 14일 관찰·7일 SSOT 전환 안정화·최소 3개 Snapshot 보존 기준을 Runbook에 확정

## 10. CI와 테스트 자동화

- [x] **SSOT-R6-001 · P0** 기존 Gradle `check → figmaContractTest`에 v2/v3 Schema 검증 연결
- [x] **SSOT-R6-002 · P0** Catalog/Registry 정상·미등록 Binding fixture 추가
- [x] **SSOT-R6-003 · P0** Registry v3 Published Component/Variant Key와 Catalog Property/허용값을 실제 Inventory와 교차 검증
- [x] **SSOT-R6-004 · P0** alias/replacement/composition 순환 test 추가
- [x] **SSOT-R6-005 · P1** 승인된 전체 Registry v3 Snapshot의 Catalog·Binding·Content Hash 일괄 검증 서비스와 REST 추가
- [x] **SSOT-R6-006 · P1** Registry v2→v3 변환·Hash 결정성과 Catalog v1→v2 golden Fixture 검증 추가
- [x] **SSOT-R6-007 · P1** Registry Snapshot rollback service integration contract test 추가
- [x] **SSOT-R6-008 · P0** 전체 Spring 테스트와 Plugin typecheck·core 49건 회귀 통과

## 11. Figma Desktop E2E와 운영 승인

- [x] **SSOT-R7-001 · P0** 7개 기준 화면의 기존 Catalog/Registry 결과 캡처
- [x] **SSOT-R7-002 · P0** 7개 화면을 신규 Resolved Registry로 Preview
- [x] **SSOT-R7-003 · P0** 7개 화면 Materialization 성공
- [x] **SSOT-R7-004 · P0** 실제 Component Instance Key가 Registry Binding과 일치
- [x] **SSOT-R7-005 · P0** Text/Variant/Boolean/Instance Swap 속성 적용 확인
- [x] **SSOT-R7-006 · P0** 필수 Binding 제거 시 Apply fail-closed 확인
- [x] **SSOT-R7-007 · P0** 신규 Registry 승인·연결 수행
- [x] **SSOT-R7-008 · P0** 실제 이전 Snapshot Rollback 리허설
- [x] **SSOT-R7-009 · P0** 화면별 Generation Report 보관
- [x] **SSOT-R7-010 · P0** 운영자 최종 승인 기록

## 12. 문서 갱신

- [x] **SSOT-R8-001 · P1** `CONTRACT_RULES.md` §5.1에 데이터 소유권·fail-closed 규칙 반영
- [x] **SSOT-R8-002 · P1** 12번 문서 DEC-09·R0-029 수량과 분류 정정
- [x] **SSOT-R8-003 · P1** 12번 문서에서 18·19번 문서를 후속 작업으로 연결
- [x] **SSOT-R8-004 · P1** Catalog/Registry SSOT 운영·관찰·Rollback Runbook 작성
- [x] **SSOT-R8-005 · P2** `generate-component-catalog-summary.mjs`로 Catalog 요약표 자동 생성
- [x] **SSOT-R8-006 · P1** Catalog v1→v2 Preview와 Registry v3 Preview/Apply REST, SSOT Bundle REST 구현, MCP Tool 및 운영 Runbook 기록은 남음

## 13. 전체 완료 Gate

- [x] Catalog만 논리 계약을 소유한다.
- [x] Registry 신규 Snapshot은 Figma Binding과 운영 증적만 소유한다.
- [x] 모든 Apply 경로가 Resolved Registry를 사용한다.
- [x] Catalog/Registry 불일치는 fail-closed 된다.
- [x] 과거 Registry Snapshot과 Generation Report를 재현할 수 있다.
- [x] 7개 기준 화면 Figma Desktop E2E가 통과한다.
- [x] 실제 이전 Snapshot Rollback 리허설이 완료된다.
- [x] 전체 Spring·Plugin·계약 테스트가 통과한다.
- [x] 운영자 승인 기록과 변경 문서가 보관된다.

## 14. 권장 구현 순서

1. SSOT-DEC 선행 결정 확정
2. R0 계약·Schema와 fixture
3. R1 도메인·Loader·Legacy Adapter
4. R2 교차 Validator와 Resolver
5. R3 Sync·저장소
6. R4 Export·Plugin·Report
7. R5 관찰·이중 읽기·전환
8. R6 CI와 회귀 테스트
9. R7 Figma Desktop E2E·Rollback
10. R8 문서·운영 인계
