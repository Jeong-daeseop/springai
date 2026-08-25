# ADR: 이중 파이프라인 기존 저장소와 공통 조회 Adapter 유지 전략

- 상태: 채택
- 결정일: 2026-08-25
- 관련 문서: `docs/figma/32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md`, `docs/figma/33_Dual_Pipeline_Common_Control_Plane_Implementation_Specification.md`

## 배경

CRUD 생성과 Thymeleaf 전환은 실행 방식, 승인 방식, 충돌 단위와 기존 저장 테이블이 다르다.
기존 데이터를 하나의 신규 Operation 테이블로 일괄 이전하면 식별자 의미와 상태 전이를
재해석해야 하며, 검증·충돌 정보가 원래 저장되지 않은 CRUD 과거 이력을 사실처럼 보강할 위험이 있다.

## 결정

기존 `AI_CRUD_GENERATION_SNAPSHOT`과 `AI_THYMELEAF_PROJECT_OPERATION`을 각 파이프라인의
원본 이력 저장소로 유지한다. 공통 제어 계층은 읽기 Adapter로 두 저장소를 공통
`GenerationOperation`에 투영한다. 기존 테이블을 일괄 이전하거나 다시 쓰지 않는다.

기존 저장소에 없던 교차 기능 데이터만 별도 Sidecar 테이블에 기록한다.

- 실행·충돌 감사: `AI_GENERATION_OPERATION_AUDIT`
- 검증 증적: `AI_GENERATION_VALIDATION_EVIDENCE`

과거 CRUD 충돌과 영역별 보존 결과처럼 원본에 기록되지 않은 정보는 추정하지 않고
`NOT_RECORDED` 또는 빈 충돌 참조로 표시한다.

## 검토한 대안

1. 신규 통합 저장소로 즉시 이전: 단일 조회는 단순해지지만 과거 의미 왜곡, 이중 쓰기와 전환 실패 위험이 크다.
2. 기존 저장소만 사용: 변경은 적지만 공통 감사·검증 증적을 저장할 수 없다.
3. 기존 저장소 + 읽기 Adapter + Sidecar: 원본 보존과 공통 관측을 함께 만족한다.

따라서 3안을 채택한다.

## 결과와 제약

- 기존 실행기와 상태 전이는 변경하지 않는다.
- 조회 시 Adapter 조합 비용과 원본 식별자 중복 처리가 필요하다.
- 신규 Sidecar 기록 실패가 기존 Apply 성공을 뒤집지 않도록 관측 실패를 별도로 다룬다.
- 공통 조회 응답은 원본 사실과 신규 관측 사실을 구분한다.

## 재검토 조건

Readiness 병행 비교를 최소 30일 관측한 뒤 다음 조건이 모두 충족되면 통합 저장소 전환을 다시 검토한다.

- 파이프라인별 Operation 식별자와 Revision 의미가 안정적으로 대응됨
- Gate 판정 불일치 원인이 분류되고 허용 기준이 합의됨
- 이중 쓰기·재처리·Rollback 절차와 데이터 검증 계획이 마련됨
- 과거 `NOT_RECORDED` 정보를 추정하지 않는 이전 방안이 검증됨
