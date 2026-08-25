# CRUD 생성·Thymeleaf 마이그레이션 공통 제어 계층 구현명세서

> 문서 버전: 1.0
> 작성일: 2026-08-25
> 상태: 1차 공통 제어 계층 구현 반영
> 영향검토: [32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md](./32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md)
> 구현목록: [34_Dual_Pipeline_Common_Control_Plane_Implementation_List.md](./34_Dual_Pipeline_Common_Control_Plane_Implementation_List.md)

## 1. 목적과 범위

이 명세는 CRUD 생성과 Thymeleaf 마이그레이션의 실행기를 통합하지 않고, 두 경로가 공통으로
추적해야 하는 Operation·승인·검증·감사 계약을 정의한다.

포함 범위:

- 공통 Generation Operation 식별·조회
- 승인 방식과 쓰기 정책의 분리
- Validation Evidence 정규화
- Conflict·Rollback·Revalidate 결과의 공통 표현
- 기존 이력 Adapter와 관측 필드
- 공통 Release Readiness 조회

제외 범위:

- CRUD 3-way 알고리즘과 Thymeleaf 상태기계의 통합
- 기존 DB 이력의 일괄 변환
- 기존 REST/MCP Entry Point의 즉시 폐기
- 운영 기본 Mode의 즉시 전환

## 2. 목표 구조

```text
CrudGenerationAdapter ───────────────┐
ThymeleafWorkflowAdapter ────────────┼─ GenerationControlPlane
                                    │   OperationRegistry
                                    │   ApprovalRegistry
                                    │   EvidenceRegistry
                                    │   AuditRegistry
                                    └──────────────┐
                                                   ↓
                                      ApprovedProjectWritePort
```

제어 계층은 실행기 내부 로직을 호출하지 않는다. 각 실행기는 자신의 기존 상태 저장소와 실행
전략을 유지하고, 완료·충돌·실패·적용 결과를 공통 Snapshot으로 투영한다.

## 3. 공통 계약

### 3.1 Generation Operation

```text
GenerationOperation
├─ operationId
├─ sourceType: CRUD | THYMELEAF_MIGRATION
├─ projectRootRef
├─ screenId / tableName
├─ sourceRevision
├─ operationRevision
├─ approvalMode
├─ writePolicy
├─ status
├─ changedFiles[]
├─ evidenceRefs[]
├─ conflictRefs[]
├─ actor
├─ callerType
├─ environment
├─ createdAt / updatedAt
└─ auditSnapshotHash
```

`operationId`는 기존 ID를 재생성하지 않는다. CRUD와 Thymeleaf 원본 ID는 각각
`sourceType + legacyOperationId`로 연결한다.

### 3.2 ApprovalMode

```text
EXPLICIT_HASH_APPROVAL       // Thymeleaf Preview Hash 승인
AUTOMATED_OWNERSHIP_CHECK    // CRUD 생성 영역·사용자 수정 충돌 검사 통과
EXTERNAL_APPROVAL            // 향후 사람·Agent 승인 연계
UNKNOWN                      // 과거 이력 또는 불완전한 Adapter
```

### 3.3 ProjectWritePolicy

```text
ATOMIC_APPROVED              // staging → backup → atomic replace → rollback
BEST_EFFORT_COMPATIBILITY    // 신규 프로젝트·레거시 호환용 부분 적용
```

`ProjectWritePolicy`는 승인 여부를 나타내지 않는다. 승인 여부는 반드시 `ApprovalMode`와
`approvalState`로 별도 저장한다.

### 3.4 OperationStatus

공통 조회 계층의 정규화 상태는 다음으로 제한한다.

```text
PREVIEW_READY
APPROVAL_REQUIRED
APPROVED
APPLYING
APPLIED
CONFLICT
FAILED
VALIDATED
REJECTED
UNKNOWN
```

원본 상태가 다르면 `sourceStatus`에 원문을 보존하고, 정규화 실패 시 `UNKNOWN`으로 둔다.

## 4. 검증 증적 계약

```text
ValidationEvidence
├─ evidenceId
├─ operationId
├─ gateType: BINDING | BUILD | RENDER | ACCESSIBILITY | VISUAL | INTERACTION
├─ status: PASSED | FAILED | SKIPPED | INCOMPLETE
├─ severity: BLOCK | WARN | INFO
├─ inputRefs[]
├─ outputRefs[]
├─ contentHash
├─ sourceRevision
├─ validatorVersion
└─ createdAt
```

필수 Gate는 실행기별로 다르게 정의한다.

| 실행기 | 기본 필수 Gate |
|---|---|
| CRUD 현재 기준 | BINDING, BUILD, RENDER — 단, 현재 Snapshot에는 검증 결과가 영속화되어 있지 않으므로 별도 저장 단계가 필요 |
| Thymeleaf 마이그레이션 현재 기준 | BINDING, BUILD, RENDER |
| Browser 확장 검증 | ACCESSIBILITY, VISUAL, INTERACTION 추가 |
| 향후 확장 후보 | SECURITY — 현재 Thymeleaf Gate 타입·필수 조건에는 포함하지 않음 |

Release Readiness는 Gate 이름의 개수보다 `BLOCK` 실패와 필수 Gate 누락을 기준으로 판정한다.
기존 Operation에 현재 존재하지 않는 향후 Gate를 소급해 실패 처리하지 않는다.

## 5. Adapter 명세

### 5.1 CrudGenerationAdapter

- `AI_CRUD_GENERATION_SNAPSHOT`에서 최신 생성 영역 관리 Snapshot을 조회한다.
- 기존 Snapshot에서는 성공한 파일·Region 기준선만 공통 Operation으로 투영한다.
- `approvalMode=AUTOMATED_OWNERSHIP_CHECK`를 기록한다.
- 기존 `CodeServiceGenerationExecutor`의 Apply 순서는 변경하지 않는다.

기존 Snapshot에는 충돌·보존 판정 결과가 없다. 충돌 시 Snapshot 저장 전에 실행이 중단되고,
성공 Snapshot의 `mergePolicy`도 `REGENERATE`로 고정되므로 기존 이력에서 충돌·보존 여부를
추정하지 않는다. 해당 값은 `auditRecordingStatus=NOT_RECORDED`로 반환한다.

V16 이후 신규 실행은 `AI_GENERATION_OPERATION_AUDIT`에 변경·보존·충돌 Region과 변경 파일,
실패 단계·상태를 Apply 성공 여부와 무관하게 병행 기록한다. 이 신규 감사 이력이 있는 경우에만
Operation의 충돌·보존 정보를 투영한다.

현재 `CodeServiceGenerationExecutor`는 Gate/Validation 모델을 직접 사용하지 않으며,
`CrudGenerationSnapshotRepository`도 검증 결과 필드를 저장하지 않는다. 따라서 CRUD의
검증 증적은 기존 Snapshot을 읽기 전용으로 투영하는 것만으로 만들 수 없다.

- 1단계: 기존 Snapshot에서 생성 영역 관리·적용 결과를 투영하고, 검증 증적이 없으면
  `validationEvidenceStatus=NOT_RECORDED`로 표시한다.
- 2단계: `GenerationVerifier`/`CodeDirectoryVerifier` 결과를 검증 경계에서 별도
  `ValidationEvidence` 저장소에 기록한다.
- 검증 결과가 저장되기 전에는 CRUD를 완전한 공통 Readiness 결과로 간주하지 않는다.

### 5.2 ThymeleafWorkflowAdapter

- `AI_THYMELEAF_PROJECT_OPERATION`과 Snapshot을 공통 Operation으로 투영한다.
- `PREVIEW_READY → APPROVED → APPLIED → VALIDATED` 흐름을 보존한다.
- `approvalMode=EXPLICIT_HASH_APPROVAL`를 기록한다.
- `AI_ARTIFACT`의 Report·Screenshot·DOM Evidence를 `ValidationEvidence`로 연결한다.
- 현재 코드에 없는 `SECURITY` Gate를 기존 이력의 필수 조건으로 소급하지 않는다.

### 5.3 Legacy Adapter 원칙

- 기존 테이블은 읽기 전용으로 취급한다.
- 기존 Row를 새 테이블로 복사하지 않는다.
- 없는 값은 추정하지 않고 `UNKNOWN`으로 반환한다.
- Adapter 결과에는 원본 테이블·PK·상태를 함께 표시한다.

## 6. 공통 Release Readiness

신규 조회 API는 기존 Apply API와 분리한다.

```text
GET /api/generation-operations/{operationId}
GET /api/generation-operations/{operationId}/evidence
GET /api/generation-operations/{operationId}/readiness
```

응답 필수 필드:

```json
{
  "operationId": "...",
  "sourceType": "CRUD",
  "status": "VALIDATED",
  "approvalMode": "AUTOMATED_OWNERSHIP_CHECK",
  "writePolicy": "ATOMIC_APPROVED",
  "releaseReady": true,
  "failedGateNames": [],
  "missingGateNames": [],
  "auditSnapshotHash": "..."
}
```

CRUD의 기존 이력처럼 검증 결과가 저장되지 않은 Operation은 `releaseReady`를 임의로
`true`로 만들지 않고, `missingGateNames` 또는 `validationEvidenceStatus=NOT_RECORDED`
를 반환한다. 이는 기존 이력을 실패로 소급하는 것과도 구분한다.

기존 `/api/pipeline/release-readiness`는 하위 호환을 유지하고, 내부적으로 공통 Evidence를
소비하도록 단계적으로 전환한다.

## 7. 동시성·보안 계약

- 프로젝트 Root는 `SafePathResolver`와 Project Root Registry를 통과해야 한다.
- Apply 전후 Source Revision과 beforeHash를 검증한다.
- Thymeleaf의 프로젝트 Lock은 유지한다.
- 신규 CRUD 실행의 생성 영역 충돌은 파일 Drift와 별도 실패 단계·상태로 저장한다.
- 공통 조회 API는 기존 `/api/**` 인증 정책을 따른다.
- MCP Tool은 기존 위험 등급과 공유 비밀키 검증을 유지한다.
- 공통 계층은 승인·Apply를 우회하는 Write API를 제공하지 않는다.

## 8. 호환성·전환 정책

1. Phase 1: 공통 읽기 Projection만 추가한다.
2. Phase 2: 두 경로의 결과를 공통 Evidence 형식으로 병행 기록한다.
3. Phase 3: Release Readiness 조회를 공통 Projection 우선으로 전환한다.
4. Phase 4: 운영 지표가 확인된 후에만 승인 정책의 세분화를 적용한다.

기존 CRUD와 Thymeleaf의 Apply 호출 경로는 Phase 3 이전에 변경하지 않는다.

### 8.1 승인 방식 운영 기준

승인 방식과 파일 쓰기 정책은 별개의 계약이다. `ATOMIC_APPROVED`는 staging, backup,
원자 교체와 실패 시 복원을 보장하는 쓰기 정책이며 사람의 승인을 뜻하지 않는다.

| 승인 방식 | 적용 대상 | 운영 판단 기준 | 차단 조건 |
|---|---|---|---|
| `AUTOMATED_OWNERSHIP_CHECK` | 반복 실행되는 CRUD 생성 | 생성기가 관리하는 영역과 사용자 수정 영역의 변경 여부를 자동 비교 | 같은 영역이 양쪽에서 변경된 경우 전체 Apply 차단 및 감사 이력 기록 |
| `EXPLICIT_HASH_APPROVAL` | 레거시 JSP의 Thymeleaf 전환 | Preview Hash와 승인 요청 Hash의 정확한 일치 및 Apply 직전 Source Revision 재검증 | 미승인, Hash 불일치, 원본 변경 또는 프로젝트 충돌 |

운영자는 Apply 전에 Operation의 `approvalMode`, `approvalState`, `writePolicy`, 충돌 참조와
필수 Gate 증적을 각각 확인한다. 공통 조회 화면은 이 정보를 읽기 전용으로 제공하며 승인이나
Apply를 대신 수행하지 않는다. 충돌이나 증적 누락을 수동으로 성공 처리하지 않고 원 실행기의
재시도·재승인 절차를 사용한다.

## 9. 완료 기준

- 두 실행기의 기존 핵심 테스트가 모두 유지된다.
- 기존 Operation/Snapshot을 새 조회 API에서 조회할 수 있다.
- 승인 방식과 쓰기 정책이 응답에서 구분된다.
- 저장된 검증 증적에 대해서 필수 Gate 누락과 BLOCK 실패가 동일한 Release Readiness 규칙으로 판정된다.
- 검증 증적이 없는 기존 CRUD 이력은 `NOT_RECORDED`로 명시되며 실패로 소급되지 않는다.
- 기존 이력에 대한 쓰기·삭제·재작성 없이 Adapter 조회가 가능하다.
- 호출 주체·환경·프로젝트 단위 사용량을 확인할 수 있다.

## 10. 운영 환경·보존 정책

환경은 신규 감사 이력의 `environment`에 다음 기준으로 기록한다.

| 값 | 의미 |
|---|---|
| `prod` | 실제 운영 프로젝트에 적용되는 실행 |
| `stage` | 운영 전 통합 검증 실행 |
| `test` | 자동화·수동 테스트 실행 |
| `dev` | 개발자 로컬 실행 |
| `UNKNOWN` | 과거 이력 또는 실행 Profile을 확인할 수 없는 경우 |

호출 채널은 `callerType`, 행위자는 `actorId`로 분리한다. 공유 API Key·MCP Token만 사용하는
호출은 개인을 추정하지 않고 `system` 또는 현재 관측 문맥의 식별자를 기록한다.

Thymeleaf 상태 전이 이벤트는 V17부터 `callerType`과 `environment`를 함께 기록한다. V17 이전
이벤트와 인증 문맥에서 확인할 수 없는 값은 `UNKNOWN`으로 투영하며, 공유 토큰을 개인 사용자로
간주하지 않는다.

보존 기간 기준:

| 데이터 | 운영 | stage | test/dev |
|---|---:|---:|---:|
| 기존 Operation·Snapshot | 기존 정책 유지, 자동 삭제 금지 | 기존 정책 유지 | 기존 정책 유지 |
| 생성 감사 이력 | 365일 | 90일 | 30일 |
| 검증 증적 메타데이터 | 180일 | 90일 | 30일 |
| 실제 Report·Screenshot Artifact | `ArtifactRetentionPolicy` 적용 | 동일 | 동일 |

보존 기간이 지나도 승인·충돌 조사 또는 연결 Artifact가 필요한 이력은 삭제 대상에서 제외한다.
이번 구현은 조회·집계 기준만 정의하며 자동 삭제 Job은 포함하지 않는다.

Readiness 병행 비교 결과는 `AI_GENERATION_READINESS_COMPARISON`에 저장한다. 서버 프로세스의
메모리 계수는 즉시 관측용이며, 30일 불일치 분석과 운영 화면의 누적값은 이 테이블을 기준으로 한다.
Prompt·Credential·파일 내용은 저장하지 않고 Gate 이름과 판정 결과만 기록한다.

## 11. 전환 전 운영 검증 절차

### 11.1 독립 코드 리뷰

변경분은 `code-reviewer`와 `architect` 두 독립 검토 Lane을 모두 통과해야 한다. 역할이 설치돼
있더라도 현재 실행 환경에서 독립 Lane을 호출할 수 없으면 리뷰 상태는 “증적 미확보”로 기록하고
Merge 가능 판정을 보류한다. 단독 작성자의 자체 검토는 두 Lane을 대체하지 않는다.

### 11.2 30일 Readiness 관측

관측 기간은 2026-08-25부터 최소 30일이며 최초 완료 검토일은 2026-09-24이다.

```http
GET /api/generation-operations/metrics/readiness-comparison
GET /api/generation-operations/metrics/readiness-comparison/report
```

기간별 보고 API는 기본 최근 30일을 조회하며 `from`, `to` ISO-8601 파라미터를 지원한다.
최대 조회 범위는 366일이다. 다음 항목을 검토한다.

- 전체 비교 건수와 불일치율
- `COMMON_EVIDENCE_MISSING`, `COMMON_STRICTER`, `LEGACY_STRICTER`, `GATE_DETAIL_DIFFERENCE`
- CRUD·Thymeleaf 파이프라인별 분포
- 일자별 호출 누락·급증과 영속화 실패 로그

### 11.3 최종 결정과 검증

30일 결과로 공통 판정 규칙 조정 여부, 기존 Release Readiness 종료 여부와 ADR 재검토 필요성을
결정한다. 결정 후 32~34번 문서와 ADR을 함께 갱신하고 다음 검증을 통과해야 한다.

```bash
./gradlew check --console=plain
./gradlew test --tests "com.krdevops.springai.config.FlywayMigrationIntegrationTest.emptyDatabaseMigration_createsAllExpectedTables" --console=plain
git diff --check
```
