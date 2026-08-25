# CRUD 생성·Thymeleaf 마이그레이션 공통 제어 계층 구현목록

> 문서 버전: 1.0
> 작성일: 2026-08-25
> 기준 영향검토: [32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md](./32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md)
> 기준 명세: [33_Dual_Pipeline_Common_Control_Plane_Implementation_Specification.md](./33_Dual_Pipeline_Common_Control_Plane_Implementation_Specification.md)
> 상태: 1차 구현 진행 목록

## 1. 우선순위와 상태 규칙

- `P0`: 안전 경계·데이터 무결성·기존 동작 보호
- `P1`: 공통 조회·검증·운영 관측
- `P2`: 편의 기능·장기 통합
- `[ ]`: 미착수
- `[~]`: 진행 중 또는 외부 확인 필요
- `[x]`: 구현 및 검증 완료

## 2. 결정·기준선

- [x] **DPC-001 · P0** 실행기 통합이 아닌 공통 제어 계층 통합으로 결정
- [x] **DPC-002 · P0** CRUD와 Thymeleaf의 승인 모드를 분리하기로 결정
- [x] **DPC-003 · P0** `ATOMIC_APPROVED`를 승인 상태가 아닌 Write Policy로 정의
- [x] **DPC-004 · P0** 기존 Operation/Snapshot 이력 보존 및 Adapter 우선 원칙 확정
- [x] **DPC-005 · P0** 현재 `application.yaml` 기본 Mode와 관련 문서의 기본 Mode 불일치 확인·정리
  - `8445977` 커밋 및 `application.yaml:122` 기준으로 확인·수정 완료

## 3. P0 — 공통 안전 계약

- [x] **DPC-101 · P0** `GenerationOperation` 공통 모델 추가
  - `sourceType`, `legacyOperationId`, `projectRootRef`, `sourceRevision`, `status` 포함
  - CRUD·Thymeleaf 원본 식별자 역추적 가능
- [x] **DPC-102 · P0** `ApprovalMode`와 `ProjectWritePolicy` 분리 계약 추가
  - `EXPLICIT_HASH_APPROVAL`
  - `AUTOMATED_OWNERSHIP_CHECK`
  - `ATOMIC_APPROVED`
  - `BEST_EFFORT_COMPATIBILITY`
- [x] **DPC-103 · P0** 공통 상태 정규화기 구현
  - 원본 상태 보존
  - 매핑 불가 상태는 `UNKNOWN`
  - 상태를 추정해 보정하지 않음
- [x] **DPC-104 · P0** 기존 Write Port 우회 방지 테스트 추가
  - 두 실행기가 `ApprovedProjectWritePort` 외 직접 파일 적용을 하지 않는지 검증
- [x] **DPC-105 · P0** SafePath·Project Root Registry·Lock 경계 회귀 테스트 추가

## 4. P1 — 기존 이력 Adapter

- [x] **DPC-201 · P1** `CrudGenerationSnapshotAdapter` 구현
  - `AI_CRUD_GENERATION_SNAPSHOT` 읽기 전용
  - 기존 이력은 최신 성공 Revision·파일·Region 기준선만 투영
  - 기존 이력의 충돌·보존 결과는 `NOT_RECORDED`, V16 이후 신규 감사 이력에서만 조회
- [x] **DPC-202 · P1** `ThymeleafOperationAdapter` 구현
  - `AI_THYMELEAF_PROJECT_OPERATION`·Snapshot·Event·Artifact 연결
  - Preview·Approve·Apply·Conflict·Validate 흐름 보존
- [x] **DPC-203 · P1** Legacy Adapter 공통 조회 인터페이스 구현
  - 기존 DB Row 복사 금지
  - 누락 필드 `UNKNOWN` 반환
- [x] **DPC-204 · P1** Adapter별 원본 추적 필드 테스트 추가
  - source table
  - source primary key
  - source status
  - source revision

## 5. P1 — Validation Evidence와 Release Readiness

- [x] **DPC-301 · P1** `ValidationEvidence` 공통 모델 구현
  - BINDING·BUILD·RENDER·ACCESSIBILITY·VISUAL·INTERACTION
  - `SECURITY`는 현재 구현하지 않는 향후 확장 후보로 별도 관리
- [x] **DPC-302 · P1** CRUD 생성 영역 관리·적용 결과 Projection 구현
  - `AI_CRUD_GENERATION_SNAPSHOT` 읽기 전용 조회
  - 과거 충돌·보존 결과는 추정하지 않고 `NOT_RECORDED`
  - 신규 실행은 `AI_GENERATION_OPERATION_AUDIT`의 충돌·보존 Region 연결
  - 검증 결과가 없으면 `validationEvidenceStatus=NOT_RECORDED`로 표시
- [x] **DPC-302A · P1** CRUD Validation Evidence 영속화 구현
  - `GenerationVerifier`·`CodeDirectoryVerifier` 결과를 검증 경계에서 저장
  - 기존 Snapshot에서 검증 결과를 추정하지 않음
  - 영속화 전에는 CRUD Readiness를 완전한 검증 완료로 표시하지 않음
- [x] **DPC-303 · P1** Thymeleaf 검증 결과 Projection 구현
  - 현재 필수 기준은 Binding·Build·Render
  - `SECURITY` Gate는 현재 코드에 없으므로 기존 Operation의 필수 조건으로 추가하지 않음
  - Browser Gate 확장 결과 연결
- [x] **DPC-304 · P1** 필수 Gate 누락과 BLOCK 실패를 구분하는 Readiness Evaluator 구현
- [x] **DPC-305 · P1** 공통 조회 API 추가
  - `/api/generation-operations/{operationId}`
  - `/api/generation-operations/{operationId}/evidence`
  - `/api/generation-operations/{operationId}/readiness`
- [x] **DPC-306 · P1** 기존 `/api/pipeline/release-readiness` 하위 호환 Adapter 연결
  - 기존 Boolean Gate Body 계약 유지
  - `operationId`·`sourceType` Query가 있으면 공통 Evidence 기반 판정 사용

## 6. P1 — 운영 관측과 데이터 품질

- [x] **DPC-401 · P1** `callerType`, `actorId`, `environment`, `projectRoot`, `screenId` 기록
  - 신규 CRUD 감사 이력에는 호출 채널·행위자·환경·프로젝트·테이블 문맥 기록 완료
  - 신규 Thymeleaf 상태 전이 이벤트에 호출 채널·환경을 기록하고 최신 이벤트 문맥을 공통 조회에 투영
  - 별도 `screenId`가 없는 CRUD 요청과 기존 이력의 미기록 값은 추정하지 않고 `UNKNOWN` 처리
- [x] **DPC-402 · P1** CRUD·Thymeleaf별 Preview·Approve·Apply·Conflict·Failed·Validated 지표 추가
  - `/api/generation-operations/metrics`에서 전체 Revision과 최신 상태를 분리 집계
- [x] **DPC-403 · P1** 현재 `AI_THYMELEAF_PROJECT_OPERATION` 2,991건의 의미를 확인할 수 있는
  운영 조회 리포트 추가
- [x] **DPC-404 · P1** 테스트·운영 환경 분리 기준과 보존 기간 문서화
- [x] **DPC-405 · P1** 기존 이력 삭제·일괄 재작성 없이 조회 가능한지 Integration Test 추가

## 7. P2 — 단계적 전환

- [x] **DPC-501 · P2** 공통 Projection을 내부 운영 화면에 병행 노출
  - `/ai/generation-operations`에서 지표와 Operation·증적·Readiness를 읽기 전용 조회
- [x] **DPC-502 · P2** 기존 파이프라인과 공통 Readiness 결과 비교 로그 추가
  - 기존 Gate 입력과 `operationId`가 함께 전달된 요청만 개인정보 없는 로그·계수로 비교
- [~] **DPC-503 · P2** 30일 이상 관측 후 Gate 불일치 원인 분류
  - 2026-08-25 관측 시작. `COMMON_EVIDENCE_MISSING`, `COMMON_STRICTER`,
    `LEGACY_STRICTER`, `GATE_DETAIL_DIFFERENCE` 원인별 지표까지 준비됨
  - V18부터 비교 결과·Gate 차이를 DB에 영속화하여 서버 재시작 후에도 30일 누적 관측 가능
  - `/api/generation-operations/metrics/readiness-comparison/report`에서 기본 최근 30일의
    불일치율·원인별·파이프라인별·일자별 집계를 조회 가능
  - 30일 실측 전에는 완료 처리하지 않음
- [x] **DPC-504 · P2** 승인 정책을 `AUTOMATED_OWNERSHIP_CHECK`와
  `EXPLICIT_HASH_APPROVAL` 기준으로 운영 문서에 반영
- [x] **DPC-505 · P2** 기존 테이블을 장기 Adapter로 유지할지 통합 저장소로 전환할지 ADR 작성
  - 기존 저장소 + 읽기 Adapter + 신규 증적 Sidecar 유지로 결정

## 8. 문서·다이어그램 정리

- [x] **DPC-601 · P1** 14번 섹션에 두 파이프라인 비교표 추가
- [x] **DPC-602 · P1** 실행기와 공통 제어 계층의 관계도 추가
  - 14번 섹션에 14.1 "실행기 ↔ 공통 제어 계층(Control Plane) 관계도"를 "제안 · 미구현" 배지로 추가
- [x] **DPC-603 · P1** `04 Business Binding`, `05 Approval Gate`가 유지되는 단계임을 명시
- [x] **DPC-604 · P1** 기존 파이프라인 단계 번호와 Mode 전환 번호를 서로 다른 표기 체계로 분리
- [x] **DPC-605 · P1** “두 파이프라인이 서로 참조하지 않는다”는 표현을
  “실행기는 분리되어 있으나 Thymeleaf는 공통 Workflow와 두 경로는 Write Port를 공유한다”로 정정
- [x] **DPC-606 · P1** 문서의 `DISABLED` 기본값과 `application.yaml`의 실제 기본값을 일치시킴

## 9. 검증 Gate

- [x] **DPC-T01 · P0** 기존 CRUD 생성 영역 관리·사용자 수정 보호 테스트 전체 통과
- [x] **DPC-T02 · P0** 기존 Thymeleaf Preview·Hash Approve·Source Drift 테스트 전체 통과
- [x] **DPC-T03 · P0** 두 경로의 Apply가 공통 Write Port를 사용하는지 검증
- [x] **DPC-T04 · P0** ApprovalMode와 WritePolicy가 혼동되지 않는지 계약 테스트
- [x] **DPC-T05 · P1** 기존 Thymeleaf 이력 Adapter·운영 집계 회귀 테스트
- [x] **DPC-T06 · P1** 필수 Gate 누락·BLOCK·WARN·SKIPPED 판정 테스트
- [x] **DPC-T07 · P1** REST/API 인증 및 MCP 위험 등급 회귀 테스트
- [x] **DPC-T08 · P1** 운영·테스트 호출 주체 집계 결과 검증
- [x] **DPC-T09 · P2** CRUD·Thymeleaf 동일 Operation의 Readiness Projection 일관성 테스트

## 10. 완료 조건

다음 조건을 모두 만족해야 공통 제어 계층 1차 완료로 기록한다.

- [x] 기존 두 실행기의 Apply 동작과 상태 전이가 변경되지 않음
- [x] 기존 이력을 삭제·재작성하지 않고 공통 Operation으로 조회 가능
- [x] ApprovalMode와 ProjectWritePolicy가 별도 필드로 노출됨
- [x] 저장된 검증 증적에 대해 필수 Gate 누락과 BLOCK 실패가 공통 규칙으로 판정됨
- [x] 검증 증적이 없는 기존 CRUD 이력이 `NOT_RECORDED`로 표시되고 실패로 소급되지 않음
- [x] CRUD와 Thymeleaf의 충돌 단위가 각각 Region·Project/File로 보존됨
- [x] 호출 주체·환경·프로젝트별 운영 사용량을 `UNKNOWN` 포함 집계로 확인할 수 있음
- [x] 14번 아키텍처 문서와 32~34번 문서의 용어·기본 Mode가 일치함

## 11. 다음 진행 체크리스트

### 11.1 독립 리뷰

- [ ] **DPC-R01** `code-reviewer` 독립 검토 증적 확보
- [ ] **DPC-R02** `architect` 독립 검토 증적과 `CLEAR|WATCH|BLOCK` 판정 확보
- [ ] **DPC-R03** 두 검토 결과를 종합해 Merge 판정 확정
  - 현재 환경은 tmux 밖이며 네이티브 `agent_type` 호출 표면이 없어 증적 미확보
  - 자동 테스트 성공만으로 독립 리뷰를 통과한 것으로 처리하지 않음

### 11.2 운영 관측

- [~] **DPC-O01** 2026-08-25~2026-09-24 최소 30일 비교 데이터 수집
- [ ] **DPC-O02** 기간별 보고 API로 불일치율·원인·파이프라인·일자 분포 분석
- [ ] **DPC-O03** 공통 판정 규칙 조정 및 기존 Release Readiness 종료 여부 결정
- [ ] **DPC-O04** 결정 결과를 32~34번 문서와 ADR에 반영하고 DPC-503 완료 처리

### 11.3 최종 검증

- [x] **DPC-V01** 현재 변경분 `./gradlew check --console=plain` 통과
- [x] **DPC-V02** 임시 MySQL 스키마에서 V1~V18 전체 Migration 통과
- [x] **DPC-V03** `git diff --check` 통과
- [ ] **DPC-V04** 독립 리뷰 조치와 30일 관측 결정 반영 후 위 검증 재실행

현재 상태는 **구현과 자동 검증 완료, 독립 코드 리뷰 및 30일 운영 관측 대기**다.
