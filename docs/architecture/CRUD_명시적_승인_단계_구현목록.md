# CRUD 생성 경로 명시적 승인 단계 구현목록

> 문서 버전: 1.3
> 작성일: 2026-08-25 (최종 갱신 2026-08-26)
> 기준 명세: [CRUD_명시적_승인_단계_구현_명세서.md](./CRUD_명시적_승인_단계_구현_명세서.md)
> 상태: **1차 완료(2026-08-26)** — 옵션 A 항목은 비채택으로 참고용 보존

## 1. 우선순위와 상태 규칙

- `P0`: 착수 전 선결 조건
- `P1`: 옵션 B 핵심 구현 항목
- `P2`: 부가 기능·운영 편의
- `[ ]`: 미착수 / `[~]`: 진행 중 / `[x]`: 구현 및 검증 완료

## 2. 선결 조건

- [x] **APR-001 · P0** 옵션 A/B 중 최종 채택안 결정 — **옵션 B 확정(2026-08-25 사용자 승인)**.
  두 옵션을 동시에 지원할 수 없다는 판단에 따라 옵션 A는 구현하지 않는다.
- [x] **APR-003 · P0** "고위험 테이블" 목록 관리 방식(코드/설정) 및 초기 목록 결정 — **`application.yaml`의
  `app.crud-generation.approval-required-tables`(빈 목록)로 관리, 초기 목록은 빈 상태로 시작**을
  기본값으로 채택(사용자 명시 지정 없어 안전한 기본값으로 진행 — 기존 동작과 완전히 동일하게
  시작하고, 필요 시 운영 중 테이블명을 추가하는 방식). 실제 목록 내용은 별도 정책 결정 필요.

## 3. 옵션 A 항목 (비채택 · 미착수 상태로 보존, 참고용)

옵션 B 채택에 따라 아래 항목은 구현하지 않는다. 왜 검토했고 왜 채택하지 않았는지 근거를
남기기 위해 목록만 보존한다(명세서 §3 참고).

- [ ] ~~**APR-A01**~~ `AI_CRUD_GENERATION_PREVIEW` 테이블 Flyway 마이그레이션
- [ ] ~~**APR-A02**~~ `PreviewCrudGenerationUseCase`/`ApproveCrudGenerationUseCase`/`ApplyCrudGenerationUseCase` 분리
- [ ] ~~**APR-A03**~~ `CrudGenerationApplicationService.execute()` 3단계 분리
- [ ] ~~**APR-A04**~~ `approve()` 판정 로직
- [ ] ~~**APR-A05**~~ 신규 `CrudApprovalWorkflowTool` 3종
- [ ] ~~**APR-A06**~~ `McpToolDefinitionSnapshotTest` 기준선 갱신
- [ ] ~~**APR-A07**~~ `AI_CRUD_GENERATION_SNAPSHOT` 기록 시점 이동
- [ ] ~~**APR-A08**~~ 단계별 `recordAudit()` 배치
- [ ] ~~**APR-A09**~~ 대시보드 조회 확인

## 4. 옵션 B 구현 항목 (채택 확정 — 실제 작업 대상)

- [x] **APR-B01 · P1** `app.crud-generation.approval-required-tables`/`approval-required-for-all`
  설정 프로퍼티 추가 — `CrudGenerationApprovalProperties.java`, `application.yaml`에 기본값(빈
  목록·false) 등록 완료
- [x] **APR-B02 · P1** `CrudGenerationApprovalPolicy` 컴포넌트 구현(명세서 §4.1/§4.3) 완료
- [x] **APR-B03 · P1** `CrudGenerationPlanner`에 조건 분기 추가(기존 V2_APPLY 가드 바로 아래,
  명세서 §4.3 코드 참고) 완료 — 기존 12-arg 생성자는 하위 호환 오버로드로 보존
- [x] **APR-B04 · P1** 차단 시 `recordAudit()`에 `failureStage="approval-policy"`로 기록 완료
  (`GenerationOperationStatus.REJECTED`, `CrudGenerationOperationIdFactory`로 operationId 산출)
- [x] **APR-B05 · P2** 운영 문서(README 등)에 "고위험 테이블 지정 방법" 안내 추가 — `README.md`
  "CRUD 생성 승인 정책(고위험 테이블)" 절 추가 완료

## 5. 검증 Gate

- [x] **APR-T01 · P0** 고위험/승인 대상이 **아닌** 기존 CRUD 생성 경로는 회귀 없이 그대로
  동작하는지 전체 테스트 — 전체 스위트 1917개 테스트 통과(실패 0, 오류 0)
- [x] **APR-T05 · P1** 고위험 테이블 + 화면명세 없음 → 차단, 있음 → 통과
  (`CrudGenerationApprovalPolicyPlannerTest.blocksHighRiskTableWithoutDesignReference`/
  `allowsHighRiskTableWithScreenSpecificationId`)
- [x] **APR-T06 · P1** 고위험 목록에 없는 테이블은 기존 동작 그대로(회귀 없음)
  (`doesNotAffectTableNotInHighRiskList`)
- [x] **APR-T08 · P1** `approval-required-for-all=true`일 때 CRUD 뷰타입에도 정책이 적용되는지
  (`approvalRequiredForAllBlocksAnyTableRegardlessOfViewType`)
- [x] **APR-T09 · P1** 차단 이력이 실제 운영 DB(`egov-mysql`)에 기록되고 `GenerationControlPlaneRepository`
  로 그대로 조회되는지(`CrudGenerationApprovalPolicyAuditIntegrationTest`, `DB_PASSWORD` 필요)

## 6. 완료 조건

다음을 모두 만족해야 1차 완료로 기록한다.

- [x] §2 옵션 선택(APR-001) 확정됨
- [x] §2 고위험 테이블 목록 정책(APR-003) 확정됨 — 빈 목록으로 시작(기본값)
- [x] §4 옵션 B 항목 전부 구현·테스트 완료(P2 운영 문서 안내 포함)
- [x] §5 검증 Gate 전부 통과
- [x] 고위험/승인 대상이 아닌 기존 CRUD 생성 경로에 회귀가 없음(APR-T01) — 전체 1917개 테스트 통과
- [x] 승인 정책에 의한 차단/통과 이력이 `AI_GENERATION_OPERATION_AUDIT`/
  `GenerationOperationsController`(14.1절)로 실제 조회 가능함(APR-T09, 실제 `egov-mysql`로 확인)

**1차 완료 조건을 모두 만족했습니다(2026-08-26).**
