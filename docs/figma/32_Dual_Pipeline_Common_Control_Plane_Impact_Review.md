# CRUD 생성·Thymeleaf 마이그레이션 이중 파이프라인 공통 제어 계층 영향검토서

> 문서 버전: 1.0
> 작성일: 2026-08-25
> 상태: 권장 아키텍처 결정 전 영향검토
> 관련 명세: [33_Dual_Pipeline_Common_Control_Plane_Implementation_Specification.md](./33_Dual_Pipeline_Common_Control_Plane_Implementation_Specification.md)
> 관련 목록: [34_Dual_Pipeline_Common_Control_Plane_Implementation_List.md](./34_Dual_Pipeline_Common_Control_Plane_Implementation_List.md)

## 1. 검토 결론

CRUD 생성기와 Thymeleaf 마이그레이션 Workflow의 실행기를 하나로 합치지 않는다. 두 경로는
위험도와 변경 단위가 다르므로 실행 전략을 유지한다. 대신 `Generation Operation`, 승인 상태,
검증 증적, 충돌·Rollback 감사, 호출 주체를 공통 제어 계층으로 표준화한다.

```text
CRUD 생성기 ─────────────────────┐
  생성 영역 3-way 비교·병합      │
  사용자 수정 보호 자동 검사     ├─ Common Generation Control Plane
                                 │    Operation / Approval / Evidence / Audit
Thymeleaf 마이그레이션 ──────────┘
  Preview → Hash Approve → Apply
  Source Revision → Revalidate
                                      ↓
                              ApprovedProjectWritePort
```

결정 사항은 다음과 같다.

1. 실행기는 분리한다.
2. 실제 파일 쓰기는 기존 `ApprovedProjectWritePort`를 공통 하부 계층으로 유지한다.
3. `ATOMIC_APPROVED`는 승인 방식이 아니라 원자적 쓰기 정책으로 명확히 정의한다.
4. CRUD의 자동 승인은 `AUTOMATED_OWNERSHIP_CHECK`, 마이그레이션의 승인은
   `EXPLICIT_HASH_APPROVAL`로 분리해 기록한다.
5. 기존 `AI_THYMELEAF_PROJECT_OPERATION`과 `AI_CRUD_GENERATION_SNAPSHOT` 이력은 삭제·재작성하지
   않고, 새 공통 Operation 조회 계층에서 Adapter로 읽는다.

## 2. 현재 구조와 근거

### 2.1 실제 연결 관계

두 파이프라인은 완전히 독립적이지 않다.

- `ThymeleafBindingGenerationService`는 `ThymeleafProjectWorkflowService.preview()`를 호출한다.
- `ThymeleafProjectWorkflowService`는 `ApprovedProjectWritePort`에 실제 파일 적용을 위임한다.
- `CodeServiceGenerationExecutor`도 같은 `ApprovedProjectWritePort`를 사용한다.
- 따라서 현재 공통점은 파일 쓰기 Port이고, Operation·승인·검증 증적은 각각 분리되어 있다.
- 특히 CRUD의 `CrudGenerationSnapshotRepository`에는 검증/Gate 필드가 없고 실행기도
  Validation 모델을 직접 사용하지 않으므로, CRUD 검증 증적은 기존 이력의 읽기 Projection만으로
  구성할 수 없다. 검증 경계에서 별도 영속화하는 작업이 선행되어야 한다.

### 2.2 CRUD 생성 경로

- 입력: DB Schema 및 CRUD 생성 계획
- 적용 단위: 생성 파일과 Region
- 보호 방식: `GenerationOwnershipManifest`(생성 영역 관리 명세), 3-way Region 비교,
  사용자 수정 충돌 방지
- 반복성: Schema 변경에 따른 재생성에 적합
- 현재 상태 저장: `AI_CRUD_GENERATION_SNAPSHOT`
- 기본 위험: 자동 적용이 사람 승인 없이 실행될 수 있음

### 2.3 Thymeleaf 마이그레이션 경로

- 입력: JSP·Controller·VO 소스 증거와 Binding Contract
- 적용 단위: 프로젝트 Operation과 파일 ChangeSet
- 보호 방식: Preview Hash, Source Revision, 명시적 승인, 프로젝트 Lock
- 반복성: 운영 중 레거시 화면의 고위험 변환에 적합
- 현재 상태 저장: `AI_THYMELEAF_PROJECT_OPERATION`, `AI_ARTIFACT`,
  `AI_OPERATION_EVENT`, `AI_OPERATION_LOCK`
- 적용 후: 정적·브라우저 Validation Gate와 Revalidate 수행
- 현재 코드에 존재하지 않는 `SECURITY` Gate를 기존 Thymeleaf Operation의 필수 조건으로
  소급하지 않는다. 필요 시 별도 확장 항목으로 검토한다.

## 3. 실측 운영 데이터와 해석

2026-08-24 `ebt` DB 조회 기준:

| 테이블/상태 | 건수 |
|---|---:|
| `AI_THYMELEAF_PROJECT_OPERATION` 전체 | 2,991 |
| `PREVIEW_READY` | 1,282 |
| `APPROVED` | 838 |
| `APPLIED` | 703 |
| `CONFLICT` | 168 |
| `AI_CRUD_GENERATION_SNAPSHOT` 전체 | 51 |

이 수치는 Thymeleaf Workflow가 단순 미사용 프로토타입이라고 단정하기 어렵다는 근거다.
다만 해당 호출이 운영자 실행인지 자동화 테스트인지, 프로젝트별 성공률이 어떤지는 과거 이력만으로
확정할 수 없다. V16 이후 CRUD 감사 이력과 V17 이후 Thymeleaf 상태 전이에 `callerType`,
`actorId`, `environment`, `projectRoot`, `screenId`를 기록한다. 과거 미기록 값은 추정하지 않고
`UNKNOWN`으로 유지한다.

## 4. 영향 평가

| 영역 | 영향 | 위험 | 대응 |
|---|---|---|---|
| 파일 적용 | 낮음 | 기존 Port 의미 변경 시 회귀 | Port 인터페이스 유지, Adapter 우선 |
| 승인 | 높음 | 자동 승인과 사람 승인의 혼동 | `ApprovalMode` 명시 저장 |
| 검증 | 높음 | 두 보고서 형식 불일치 | 공통 `ValidationEvidence`로 정규화 |
| 충돌 | 중간 | Region Conflict와 파일 Drift를 혼동 | Conflict 종류·단위 분리 |
| DB 이력 | 높음 | 기존 이력 마이그레이션 중 손실 | 기존 테이블 보존, 읽기 Adapter 추가 |
| API/MCP | 중간 | 기존 Tool·REST 계약 변경 | 기존 Entry Point 유지, 공통 조회 API 별도 추가 |
| 보안 | 높음 | 승인 없는 Apply 또는 잘못된 프로젝트 Root | 기존 Authorization·SafePath·Lock 재사용 |
| 운영 | 중간 | 2,991건의 의미 불명확 | 호출 주체·환경·retention 관측 추가 |
| 문서 | 중간 | 04/05 단계와 Mode 04/05 번호 혼동 | 파이프라인 단계와 전환 Mode 번호 분리 표기 |

검증 영향은 현재 상태와 목표 상태를 구분해야 한다. 기존 Thymeleaf 이력은 현재 구현된
Gate만으로 해석하고, CRUD 이력에 검증 결과가 없으면 `NOT_RECORDED`로 표시한다. 새 필수
Gate를 추가했다는 이유만으로 과거 Operation을 실패로 소급하지 않는다.

## 5. 대안 비교

| 대안 | 평가 |
|---|---|
| 두 실행기를 하나로 통합 | 비권장. 승인·병합·충돌 단위가 달라 조건 분기가 커지고 안전 경계가 흐려짐 |
| 현재 구조 그대로 유지 | 단기 안전하지만 승인·증적·운영 분석이 계속 이원화됨 |
| 실행기는 유지하고 제어 계층만 공통화 | 권장. 위험 모델을 보존하면서 조회·감사·Release Gate를 통일할 수 있음 |

## 6. 전환 원칙

- 기존 생성 경로의 기본 동작을 한 번에 바꾸지 않는다.
- 기존 Operation/Snapshot을 새 테이블로 일괄 복사하지 않는다.
- 공통 조회 시 `sourceType=CRUD|THYMELEAF_MIGRATION`을 명시한다.
- Preview와 Apply를 하나의 상태로 축약하지 않는다.
- `ATOMIC_APPROVED`와 사람 승인 상태를 동일한 의미로 취급하지 않는다.
- 기존 이력에 없는 메타데이터는 `UNKNOWN`으로 두고 추정해 채우지 않는다.

## 7. 미확인 사항

다음은 코드와 현재 DB 조회만으로 결정할 수 없다.

1. `AI_THYMELEAF_PROJECT_OPERATION` 2,991건 중 실제 운영자 승인 비율
2. `previewThymeleafBindingGeneration`의 실제 외부 호출량과 호출 주체
3. CRUD 자동 Apply가 운영 환경에서 허용되는 업무 범위
4. 30일 Readiness 병행 관측에서 허용할 불일치율과 예외 기준

기존 두 이력 테이블은 ADR에 따라 장기 Adapter로 유지하고 신규 감사·검증·비교 데이터만
Sidecar 테이블에 저장한다. 나머지 항목은 삭제나 통합보다 먼저 관측 데이터로 확인한다.

## 8. 남은 위험과 종료 조건

현재 구현과 자동 검증은 완료됐지만 다음 두 조건 전에는 Merge·전환 완료로 단정하지 않는다.

1. `code-reviewer`와 `architect`의 독립 검토 증적 확보
2. 2026-08-25부터 최소 30일간 Readiness 비교 데이터를 수집하고 2026-09-24 이후 분석

독립 검토 실행 표면이 없는 세션의 단독 검토는 승인 증적으로 대체하지 않는다. 30일 관측은
`AI_GENERATION_READINESS_COMPARISON`과 기간별 보고 API를 기준으로 하며 서버 메모리 계수만으로
판단하지 않는다.
