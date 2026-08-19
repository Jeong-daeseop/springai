# REFERENCE_STYLE / IMAGE_REFERENCE DB 테이블 바인딩 지연 절충안 — 구현 목록

> 문서 버전: 1.6
> 작성일: 2026-08-19
> 상태: **구현 완료** — P0/P1 전 항목(C-01~05, S-01~05, A-01~02, T-01~07) 완료, 전체 Java 테스트 스위트 통과. 잔여는 DEC-03(12번 문서 §11.4.1 정식 `R6-0xx` 재번호 시점) 결정뿐.
> 근거 문서: [22_Reference_Image_Request_DB_Binding_Deferral_Proposal.md](./22_Reference_Image_Request_DB_Binding_Deferral_Proposal.md)
> 관련 문서: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md) §11.4.1(원 반영 위치)

이 문서는 22번 제안서의 설계를 실행 가능한 체크리스트로 분해한 것이다. 22번 문서의 PROP-01~04를
그대로 승계하며, 착수가 승인되면 완료된 항목부터 `[x]`로 갱신하고 최종적으로 12번 문서 §11.4.1을
정식 `R6-0xx` ID로 재번호한다.

---

## 1. 상태와 우선순위 표기

### 1.1 상태

| 표기 | 의미 |
|---|---|
| `[x]` | 구현과 완료 Gate 검증 완료 |
| `[~]` | 일부 구현되었으나 목표 구조 미완료 |
| `[ ]` | 미구현 |
| `[!]` | 선행 결정 또는 외부 조건 대기 |

### 1.2 우선순위

| 우선순위 | 의미 |
|---|---:|
| P0 | 이 절충안의 핵심 동작(상태 전이·필드 매칭)에 직접 영향 |
| P1 | 1차 동작에는 필요하지만 대체 경로로 우회 가능 |
| P2 | 운영 편의·정확도 개선 |

---

## 2. 선행 결정

- [x] **DEC-01 · P0**(2026-08-19) 이 절충안의 착수 여부와 시점을 확정한다 — 세션 내내 항목별 사용자 승인을 받으며 순차 착수, C-01~T-07 전체 완료로 사실상 확정됨
- [x] **DEC-02 · P1**(대상 소멸, 2026-08-19) ~~`FieldRoleToColumnMatcher`의 신뢰도 threshold 초기값을 확정한다~~ — S-03 삭제로 이 결정 자체가 불필요해짐. 실제 필드↔컬럼 매칭은 기존 `ScreenSpecAssembler.bindingsFromHints()`가 담당하며, 그쪽의 매칭/threshold 정책은 이 절충안이 새로 정의할 대상이 아니라 기존 CRUD 생성 파이프라인의 기존 정책을 그대로 따름
- [!] **DEC-03 · P2** `AWAITING_TABLE_BINDING` 도입 완료 후 12번 문서 §11.4.1을 정식 `R6-0xx` ID로 재번호할 시점을 확정한다(착수 직후 vs 전체 완료 후)

---

## 3. 계약

- [x] **C-01 · P0** `FigmaDesignOperationStatus`(Java enum)에 `AWAITING_TABLE_BINDING` 값을 추가한다 — (2026-08-19)
  완료. 원래 초안이 "REVIEW_REQUIRED"로의 전이를 명시했으나, 실제로는 `FigmaDesignOperationStatus`에
  `REVIEW_REQUIRED` 값 자체가 없음을 구현 중 발견(그건 `ScreenSpecStatus`의 값). 매칭이 애매할 때는
  기존 `rejectReviewRequired()`와 동일하게 `REJECTED`로 전이하고 이슈 메시지로 수동 경로를 안내하는
  기존 패턴을 그대로 따르도록 설계를 정정했다.
  - 완료 Gate(정정): `ANALYZED → AWAITING_TABLE_BINDING → (PREVIEW_READY | REJECTED | FAILED)` 전이만
    허용, 그 외 전이는 기존과 동일하게 차단 — `FigmaDesignOperationStateServiceTest`의
    `analyzedCanMoveToAwaitingTableBinding`/`awaitingTableBindingResolvesDirectlyToPreviewReadyOnHighConfidenceMatch`/
    `awaitingTableBindingCannotSkipDirectlyToApplyRequiredOrApplied`/`awaitingTableBindingIsNotTerminal`로 검증
- [x] **C-02 · P0** `FigmaDesignOperationStateService`의 상태 전이 그래프에 `AWAITING_TABLE_BINDING` 갈래를 추가한다 — (2026-08-19)
  완료. `ANALYZED`의 허용 대상에 추가하고 `AWAITING_TABLE_BINDING → {PREVIEW_READY, FAILED, REJECTED}`를
  신설.
  - 완료 Gate: `FigmaDesignOperationStateTransitionSnapshotTest`의 baseline
    fixture(`figma-design-operation-transitions-baseline.json`)를 재생성해 전체 8×8 전이 matrix로
    고정 — APPLIED 전이는 여전히 `assertTransitionToAppliedAllowed`만으로 가능하고
    `AWAITING_TABLE_BINDING`은 그 경로에 포함되지 않음을 확인(R6-047 계약 위반 없음)
- [x] **C-03 · P1**(2026-08-19) 관련 JSON Schema(`figma-design-operation-v1.schema.json` 등)에 새 상태값을 반영한다
  완료. `$defs.status.enum`에 `ANALYZED` 다음(Java enum 선언 순서와 동일) `AWAITING_TABLE_BINDING`
  추가. `website-figma-contract`의 `node test/contract-test.mjs` 통과 확인 —
  `invalid-figma-design-operation-bad-status.json` fixture는 `IN_PROGRESS`(존재한 적 없는 값)를
  써서 이번 변경과 무관하게 계속 invalid로 남음. 이 스키마 파일명을 직접 참조하는 Java 테스트는 없음.
- [x] **C-04 · P1**(2026-08-19) REFERENCE_STYLE·IMAGE_REFERENCE 2종만 `AWAITING_TABLE_BINDING`을 거칠 수 있다는 예외 규칙을 계약 문서와 MCP Tool description에 명시한다(22번 문서 §7 "요청 유형 분기" 리스크 대응)
  완료. `createDesignFromReference`/`createDesignFromImage`/`generateFigmaBundleForOperation`의
  Tool description을 갱신 — S-01 구현 전 문구("생략하면 ANALYZED에서 멈추고 거부됩니다")가 실제
  동작과 어긋난 stale 상태였던 것도 함께 정정. 12번 문서 §11.4.1은 건드리지 않음(재번호는
  DEC-03/§9 8단계로 명시적으로 미뤄진 별도 작업).
- [x] **C-05 · P1**(2026-08-19) 정상·경계·오류 fixture를 작성한다 — `AWAITING_TABLE_BINDING` 상태의 Operation, 매칭 성공/애매/PK 누락 케이스 포함
  별도 fixture 파일 대신 T-04 테스트의 인라인 fixture로 충족(2026-08-19 확인). 매칭 애매/PK 누락은
  둘 다 `ScreenSpecificationService.create()`가 `REVIEW_REQUIRED` 상태 명세를 반환하는 동일
  지점으로 수렴하고(PK 강제 로직 자체는 기존 `ScreenSpecAssembler` 소유, 이 절충안이 새로 만든
  게 아님), `bindFigmaDesignRequestTableRejectsWhenScreenSpecificationReviewRequired`가 그
  지점을 검증한다.

---

## 4. 서버 도메인과 서비스

- [x] **S-01 · P0** `FigmaDesignOrchestrationService.generateBundle()`에 분기를 추가한다 — `database`/`tableName`이 없으면 기존 FATAL 거부 대신 `AWAITING_TABLE_BINDING`으로 저장 — (2026-08-19, **경로 A로 정정**)
  완료. `generateFromReference()`(REFERENCE_STYLE)·`generateFromImage()`(IMAGE_REFERENCE) 두 곳에만
  분기를 추가했고, 같은 `generateFromScreenSpecification()`을 공유하는 COMPONENT_SPECIFIED는
  이 메서드에 진입하기 전 자체 Registry 검증을 거치고 이후에도 기존 `DATABASE_TABLE_REQUIRED`/
  `REJECTED` 경로를 그대로 유지(변경 없음, 스코프 밖 확인됨).
  - **1차 구현(경로 B, 폐기)**: DB 체크를 분석 API 호출 **전**에 유지해 DB 없으면 분석 자체를
    건너뛰었다. "S-02 없이 분석 결과를 못 쓰니 API 호출은 낭비"라는 국소적 판단이었으나,
    사용자 재검토 결과 22번 문서 §4 원 설계("DB 없어도 분석은 실행해 필드 후보를 뽑는다")를
    위배하고 이 절충안의 핵심 가치("디자인 먼저, 테이블은 나중")를 무력화한다는 지적을 받아
    **경로 A로 되돌렸다.**
  - **경로 A(현재)**: DB 체크를 분석 API 호출 **뒤**로 옮겼다. `database`/`tableName`이 없어도
    `analyzeFigma`/`queryImages`+`analyze`를 그대로 실행하고, `DesignFieldCandidateExtractor`
    (S-02, 같은 커밋에서 함께 구현)로 필드 후보를 뽑아 `GenerationIssue` 목록에 실어(코드
    `DATABASE_TABLE_PENDING` 1건 + 후보별 `FIELD_CANDIDATE` N건) `AWAITING_TABLE_BINDING`으로
    전이한다. `FigmaDesignOperation` 모델에는 후보 전용 슬롯이 없어, 별도 Artifact 저장소를
    새로 만들지 않고 기존 `issues` 채널을 재사용했다(신규 저장 인프라 없음, 최소 변경).
  - **⚠️ 과도기적 제약(다음 항목 필요)**: `bindFigmaDesignRequestTable`(A-01)이 아직 없어, 사람이
    이 필드 후보를 issues에서 확인한 뒤에도 이 Operation 자체를 이어서 완료할 방법은 없다
    (`database`/`tableName`을 채운 새 요청으로 다시 시도해야 함). A-01이 갖춰지면 해소된다.
  - 완료 Gate: `database`/`tableName`이 있는 기존 호출은 분기 이전과 100% 동일하게 동작(회귀 없음),
    DB 없을 때 분석은 반드시 실행되고 그 결과의 필드 후보가 issues에 담겨 나온다 —
    `FigmaDesignOrchestrationServiceTest`의
    `generateBundleForReferenceStyleWithoutDatabaseStillAnalyzesAndAwaitsTableBindingWithCandidates`/
    `generateBundleForImageReferenceWithoutDatabaseStillAnalyzesAndAwaitsTableBindingWithCandidates`/
    `generateBundleForImageReferenceStillRejectsWhenImageExportFailsRegardlessOfDatabase`(이미지
    export 자체가 실패하면 분석 전 단계라 여전히 즉시 REJECTED)로 검증
- [x] **S-02 · P0** `DesignFieldCandidateExtractor` 신규 서비스를 구현한다 — `UiDesignSpec`에서 DB 매핑 없이 필드 후보(`label` + `roleHint`)만 추출 — (2026-08-19, S-01과 함께 구현)
  완료. `UiDesignSpec.fieldHints()`(이미 `id`/`label`/`role`/`control`/`confidence`를 갖춘 기존 타입)를
  그대로 재사용해 별도 타입을 새로 만들지 않았고, `id` 기준 중복만 제거한다.
  - 완료 Gate: 동일 `UiDesignSpec` 입력에 결정적으로 동일한 후보 목록을 반환 — `null`/빈
    `fieldHints`는 빈 목록으로 방어. 전용 단위테스트(T-01, `DesignFieldCandidateExtractorTest`)는
    S-03과 함께(2026-08-19) 추가
- [!] **S-03 · P0**(삭제됨, 2026-08-19) ~~`FieldRoleToColumnMatcher` 신규 서비스를 구현한다~~
  일단 실제 `egov-mysql` 컬럼 데이터를 근거로 구현·테스트(12건)까지 완료했으나, S-04/S-05를
  설계하는 과정에서 **`ScreenSpecificationService.create(database, tableName, ..., uiSpec)`이
  이미 `ScreenSpecAssembler.assemble()` → `bindingsFromHints(rawColumns, uiSpec.fieldHints(), issues)`로
  거의 동일한 역할 힌트↔실제 컬럼 매칭을 수행하고 있음을 발견**했다(매칭 실패 시
  `NO_COLUMN_CANDIDATE` ERROR Issue → `validator.validate()`가 `REVIEW_REQUIRED`로 판정하는
  경로까지 이미 존재). 즉 이 매처는 이미 있는 기능을 중복 구현한 것이었다. 사용자 확인 후
  **완전히 삭제**했다 — `FieldRoleToColumnMatcher.java`/`FieldRoleToColumnMatcherTest.java`/
  `FieldColumnMatch.java`/`FieldMatchResult.java` 전부 제거, `ColumnMeta.fromRow()`(이 매처
  전용으로 신설했던 정적 팩토리)도 다른 소비자가 없어 함께 제거해 `ColumnMeta.java`를 원상
  복구했다. `DesignFieldCandidateExtractor`(S-02)는 그대로 유지 — 실제 컬럼 매칭과 무관하게
  `AWAITING_TABLE_BINDING` 단계의 "사람이 볼 미리보기" 용도로는 여전히 유효하다.
- [x] **S-04 · P1** 매칭 결과가 전부 고신뢰 + PK 포함이면 즉시 완료 경로로 연결한다 — **새 코드 불필요, 기존 로직으로 이미 충족(2026-08-19 확인)**
  `finalizeFromScreenSpecification()`(기존 코드, `generateFromReference`/`generateFromImage`가
  이미 호출)이 `spec.status() == APPROVED`면 곧바로 `exportAndTransition()`(Bundle 생성)으로
  진행한다. `database`/`tableName`이 채워진 request로 기존 파이프라인을 재호출하기만 하면
  자동으로 이 경로를 탄다 — "사용자 승인 경로: 기존 파이프라인에 그대로 위임" 결정에 따름.
- [x] **S-05 · P1** 매칭이 애매/누락되면 기존 `REVIEW_REQUIRED` 경로로 위임한다 — **새 코드 불필요, 기존 로직으로 이미 충족(2026-08-19 확인)**
  같은 `finalizeFromScreenSpecification()`이 `spec.status() != APPROVED`면 `rejectReviewRequired()`로
  `reviseScreenSpecification`/`approveScreenSpecification`/`createFigmaBundleFromApprovedSpecification`
  수동 경로를 안내하며 `REJECTED`로 전이한다 — R6-033/R6-035가 이미 이 경로로 검증돼 있음
  (12번 문서 §11.4). 신규 배선 자체가 필요 없다.

---

## 5. REST/MCP

- [x] **A-01 · P0**(2026-08-19) `bindFigmaDesignRequestTable(operationId, database, tableName)` MCP Tool을 신설한다
  - **선행 발견(2026-08-19)**: `FigmaDesignOperationRepository.appendTransition()`은 `request`를 절대 바꾸지 않는다(`withNextRevision()`에 request 파라미터 자체가 없음) — Operation은 `createOrReuse()` 시점의 request 해시로 identity가 고정되는 구조라, "같은 Operation에 database/tableName만 나중에 채워 넣기"가 원래 설계상 불가능했다. 아래 A-01a~c로 먼저 모델을 확장해야 함(사용자 확인 후 착수, 기존 `createOrReuse`/`appendTransition`/`withNextRevision`은 그대로 두고 새 오버로드만 추가)
  - **A-01a**(완료): `FigmaDesignRequest.withDatabaseTable(database, tableName)` wither 추가(기존 `withNodeIds()`와 동일 패턴)
  - **A-01b**(완료): `FigmaDesignOperation.withRequestAndNextRevision(...)` 오버로드 추가(request/hash까지 함께 갱신, hash 재계산은 Repository 책임)
  - **A-01c**(완료): `FigmaDesignOperationRepository.appendTransitionWithRequest(...)` 신규 — 새 request로 해시 재계산해 비교. **선행 발견(2026-08-19)**: `canonicalRequestView()`가 애초에 `database`/`tableName`을 해시 계산 대상에 포함하지 않아, 이 흐름에서는 해시가 보통 바뀌지 않는다. 사용자 확인 후 `canonicalRequestView()`는 건드리지 않고, 해시가 실제로 바뀐 경우에만 멱등성 테이블에 새 행을 추가하도록(기존 해시 행은 유지) 조건부 구현 — 해시 불변 시 revision 갱신만 수행
  - **A-01d**(완료): 상태 그래프에 `AWAITING_TABLE_BINDING → ANALYZED` 전이 추가(PROP-01/S-01과 동일 절차, baseline 재생성 완료)
  - **A-01e**(완료): `FigmaDesignOrchestrationService.bindTable()` 본체 — AWAITING_TABLE_BINDING 확인(아니면 `IllegalStateException`) → request에 테이블 채워 ANALYZED로 전이 → 기존 `generateBundle(operationId)` 재호출(REFERENCE_STYLE/IMAGE_REFERENCE만 발생 가능하므로 자연히 `generateFromReference`/`generateFromImage`로 라우팅, 별도 분기 불필요) → `FigmaDesignOrchestrationTool.bindFigmaDesignRequestTable(...)` MCP Tool로 노출(기존 6개 Tool과 동일 패턴)
  - 완료 Gate: `AWAITING_TABLE_BINDING` 상태가 아닌 Operation에 호출하면 명확한 오류로 거부, 기존 `figmaMcpSecret` 인증을 그대로 적용 — T-04/T-07로 검증
- [x] **A-02 · P1**(2026-08-19) 대응 REST 엔드포인트(선택)를 신설한다 — 기존 `/api/figma/**` 인증 정책 재사용
  완료. `FigmaDesignOrchestrationController`에 `POST /api/figma/orchestration/bind-table` 추가.
  `/api/figma/orchestration/**`는 이미 기본 `/api/**` → X-API-Key 인증(`authenticated()` +
  `apiKeyFilter()`) 대상이라 `SecurityConfig` 수정 불필요. 서비스의 `IllegalArgumentException`/
  `IllegalStateException`은 `FigmaRequestException`으로 감싸 기존 `GlobalExceptionHandler`가 400
  표준 오류로 변환(`FigmaOperationsController`와 동일 패턴). `FigmaDesignOrchestrationControllerTest`
  신규 2건으로 검증.
- [x] **A-03 · P1**(2026-08-19 확인, 신규 코드 없음) `AWAITING_TABLE_BINDING` 상태 Operation의 필드 후보 목록을 사람이 조회할 수 있는 API(또는 기존 `GET /operations/{operationId}/info` 응답 확장)를 제공한다
  기존 `FigmaOperationsController`의 `GET /api/figma/operations/{operationId}/info`(R5-040)가
  `FigmaDesignOperation` 전체를 그대로 반환하며 여기에는 S-01/S-02가 채워 넣는 `issues`(
  `DATABASE_TABLE_PENDING` + `FIELD_CANDIDATE` N건)가 이미 포함돼 있어 별도 API 신설 없이
  충족됨(S-04/S-05와 같은 "기존 인프라로 이미 충족" 패턴).

---

## 6. 테스트

- [x] **T-01 · P0** `DesignFieldCandidateExtractorTest` — 동일 입력 결정성, 빈 힌트 처리 — (2026-08-19) 5건(순서 보존, 결정성, id 중복 제거, null/빈 입력 방어)
- [!] **T-02 · P0**(대상 소멸, 2026-08-19) ~~`FieldRoleToColumnMatcherTest`~~ — S-03 삭제와 함께 제거(12건 작성 후 삭제). 실제 컬럼 매칭 검증은 기존 `ScreenSpecAssembler`/`bindingsFromHints()`의 기존 테스트 범위(이 문서 밖)에 이미 포함돼 있음
- [x] **T-03 · P0**(2026-08-19 확인, 신규 코드 없음) `generateBundle()`이 `database`/`tableName` 없을 때 `AWAITING_TABLE_BINDING`으로 전이하는지 검증
  S-01 구현 시 이미 작성된 `generateBundleForReferenceStyleWithoutDatabaseStillAnalyzesAndAwaitsTableBindingWithCandidates`/
  `generateBundleForImageReferenceWithoutDatabaseStillAnalyzesAndAwaitsTableBindingWithCandidates`가
  REFERENCE_STYLE·IMAGE_REFERENCE 양쪽 다 이미 검증하고 있어 중복 테스트를 추가하지 않음
  (S-03/S-04/S-05와 같은 패턴).
- [x] **T-04 · P0**(2026-08-19) `bindFigmaDesignRequestTable` 호출 후 고신뢰 매칭 시 즉시 `PREVIEW_READY`, 애매 시 `REVIEW_REQUIRED`로 전이하는지 검증
  신규 테스트 3건: `bindFigmaDesignRequestTableReanalyzesAndReachesPreviewReadyOnApprovedSpecification`
  (고신뢰 매칭 → PREVIEW_READY), `bindFigmaDesignRequestTableRejectsWhenScreenSpecificationReviewRequired`
  (애매한 매칭 → REJECTED — C-01에서 이미 REVIEW_REQUIRED가 존재하지 않는 상태값임이 밝혀져
  REJECTED로 정정된 설계이므로 이 테스트도 REJECTED를 검증), `bindFigmaDesignRequestTableRejectsWhenOperationNotAwaitingTableBinding`
  (AWAITING_TABLE_BINDING이 아닌 상태에서 호출 시 거부 — A-01e 완료 게이트).
- [x] **T-05 · P1**(2026-08-19) REFERENCE_STYLE·IMAGE_REFERENCE 외 5종 요청(TEXT_DESCRIPTION/MODIFY_EXISTING/COMPONENT_SPECIFIED/MULTI_SCREEN_FLOW/PLATFORM_CONVERT)은 `AWAITING_TABLE_BINDING`을 거치지 않고 기존과 동일하게 거부/처리되는지 회귀 검증
  신규 테스트 2건: `generateBundleForComponentSpecifiedWithoutDatabaseStillRejectsInsteadOfAwaitingTableBinding`
  (컴포넌트가 Registry에서 정상 해석돼도 database 없으면 기존과 동일하게 즉시
  REJECTED/DATABASE_TABLE_REQUIRED), `generateBundleForTextDescriptionThrowsUnsupported`
  (TEXT_DESCRIPTION은 `generateBundle()` 자체가 즉시 예외). MULTI_SCREEN_FLOW는 기존
  `generateBundleForMultiScreenFlowRejectsAllWhenOneScreenMissingDatabase`가 이미 커버.
  MODIFY_EXISTING·PLATFORM_CONVERT는 `database`/`tableName`을 아예 참조하지 않는 코드 경로라
  (`screenSpecificationId` 기반) 이 회귀 자체가 코드상 구조적으로 불가능함을 확인 — 테스트 추가 안 함.
- [x] **T-06 · P0**(2026-08-19 확인, 신규 코드 없음) 기존 R6-033(REFERENCE_STYLE)·R6-035(IMAGE_REFERENCE)의 `database`/`tableName` 지정 경로가 이번 변경 이후에도 회귀 없이 동일하게 동작하는지 검증
  `generateBundleForReferenceStyleAnalyzesAndExportsApprovedSpecification`(REFERENCE_STYLE)·
  `generateBundleForImageReferenceDownloadsAndAnalyzesFirstImageNode`(IMAGE_REFERENCE)가 이미
  database/tableName 지정 경로 → PREVIEW_READY를 검증하고 있고, 전체 스위트가 A-01/C-04 변경
  이후에도 정상 통과해 회귀 없음을 재확인.
- [x] **T-07 · P1**(2026-08-19) `AWAITING_TABLE_BINDING` 상태에서 잘못된 Operation ID·잘못된 인증으로 `bindFigmaDesignRequestTable` 호출 시 거부되는지 검증
  신규 테스트 2건: (Service) `bindFigmaDesignRequestTableRejectsWhenOperationIdNotFound`(존재하지
  않는 operationId), (Tool) `FigmaDesignOrchestrationToolAuthorizationTest.authenticationRunsBeforeBindingTable`
  (잘못된 `figmaMcpSecret`).

---

## 7. 예상 변경 파일

### 신규

- `src/main/java/com/krdevops/springai/service/figma/DesignFieldCandidateExtractor.java`(완료)
- 위 서비스의 단위 테스트(완료)
- ~~`FieldRoleToColumnMatcher.java`~~ — 구현 후 기존 `ScreenSpecAssembler.bindingsFromHints()`와
  중복 확인돼 삭제(2026-08-19, S-03 참고)

### 수정

- `src/main/java/com/krdevops/springai/model/figma/contract/FigmaDesignOperationStatus.java`(완료 — `AWAITING_TABLE_BINDING` 추가)
- `FigmaDesignOperationStateService`(완료 — 전이 그래프 확장, A-01d로 `AWAITING_TABLE_BINDING → ANALYZED` 추가 완료)
- `FigmaDesignOrchestrationService.java`(완료 — S-01 분기 + A-01e `bindTable()`)
- `src/main/java/com/krdevops/springai/model/figma/contract/FigmaDesignRequest.java`(완료 — A-01a `withDatabaseTable()` wither)
- `src/main/java/com/krdevops/springai/model/figma/contract/FigmaDesignOperation.java`(완료 — A-01b `withRequestAndNextRevision()` 오버로드)
- `src/main/java/com/krdevops/springai/mapper/FigmaDesignOperationRepository.java`(완료 — A-01c `appendTransitionWithRequest()` 신규)
- `src/main/java/com/krdevops/springai/tools/FigmaDesignOrchestrationTool.java`(완료 — A-01e `bindFigmaDesignRequestTable` Tool 추가, C-04로 6개 Tool description 갱신. `McpConfig.java`는 기존 등록 Bean이라 수정 불필요)
- `src/main/java/com/krdevops/springai/controller/FigmaDesignOrchestrationController.java`(완료 — A-02 `POST /api/figma/orchestration/bind-table` 신규)
- `website-figma-contract/figma-design-operation-v1.schema.json`(완료 — C-03 `AWAITING_TABLE_BINDING` enum 추가)
- `src/test/resources/state-machine/figma-design-operation-transitions-baseline.json`(완료 — A-01d로 재생성)
- `FigmaDesignOrchestrationServiceTest.java`/`FigmaDesignOrchestrationToolAuthorizationTest.java`(완료 — T-04/T-05/T-07 신규 테스트)
- `FigmaDesignOrchestrationControllerTest.java`(신규, A-02 검증)
- `12_Semantic_Figma_Design_System_Implementation_List.md` §11.4.1(잔여 — 정식 ID 재번호는 DEC-03 결정 후 별도 진행)

---

## 8. MVP 범위

### 포함

- REFERENCE_STYLE·IMAGE_REFERENCE 2종만
- `AWAITING_TABLE_BINDING` 단일 신규 상태
- 필드 후보 추출·컬럼 매칭·PK 강제 포함
- 기존 `REVIEW_REQUIRED`/`REST 인증`/`generateBundle` 인프라 재사용

### 제외

- PLATFORM_CONVERT/MULTI_SCREEN_FLOW/COMPONENT_SPECIFIED로의 확장
- 매칭 threshold 자동 튜닝(운영 데이터 축적 후 별도 검토)
- Plugin(TypeScript)·pluginData 변경(서버/MCP 범위로 한정)
- 여러 테이블 후보를 사람이 비교 선택하는 UX(1차는 단일 `database`/`tableName` 지정만)

---

## 9. 착수 순서

1. `DEC-01~03` 결정 확정(DEC-01/02 완료, DEC-03은 재번호 시점만 잔여)
2. `C-01~05` 상태·계약 정의(완료)
3. `S-01` generateBundle() 분기 추가(완료, 경로 A로 정정)
4. `S-02` DesignFieldCandidateExtractor 구현(완료)
5. ~~S-03 FieldRoleToColumnMatcher 구현~~(착수 후 기존 `ScreenSpecAssembler`와 중복 확인돼 삭제) →
   S-04/S-05는 기존 파이프라인 위임으로 새 코드 없이 충족 확인(완료)
6. `A-01(a~e)~03` Operation 모델 확장(request 갱신 가능하도록) + MCP Tool/REST 진입점 구현(완료)
7. `T-01~07` 테스트 및 회귀 검증(완료, T-02는 대상 소멸)
8. 12번 문서 §11.4.1을 정식 `R6-0xx` ID로 재번호(**잔여** — DEC-03 결정 필요, 별도 확인 후 진행)

---

## 10. 완료 Gate

- [x] `database`/`tableName` 없이 REFERENCE_STYLE·IMAGE_REFERENCE 요청 시 `AWAITING_TABLE_BINDING`으로 저장되고, 필드 후보를 사람이 조회할 수 있다(S-01/S-02 + A-03 기존 `/info` API)
- [x] `bindFigmaDesignRequestTable` 호출 후 고신뢰 매칭이면 즉시 `PREVIEW_READY`, 애매하면 REJECTED로 넘어간다(C-01에서 REVIEW_REQUIRED가 `FigmaDesignOperationStatus`에 존재하지 않는 값임이 밝혀져 REJECTED로 정정된 설계 — T-04로 검증)
- [x] PK가 없는 매칭 결과는 자동 확정되지 않는다(기존 `ScreenSpecAssembler`의 PK 강제 로직 + REVIEW_REQUIRED→REJECTED 경로, C-05로 확인)
- [x] 나머지 5종 요청 유형과 기존 `database`/`tableName` 지정 경로는 회귀 없이 동일하게 동작한다(T-05/T-06)
- [x] 전체 Java 테스트 스위트가 통과한다(2026-08-19, C-03/A-02 포함 최종 확인)
- [ ] 12번 문서 §11.4.1이 정식 `R6-0xx` ID로 재번호되고 완료 Gate가 명시된다(**잔여** — DEC-03 결정 후 별도 진행)

---

## 11. 변경 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| 1.6 | 2026-08-19 | **A-01(a~e)~A-03, C-03~C-05, T-03~T-07 전체 완료.** A-01b(`FigmaDesignOperation.withRequestAndNextRevision`)·A-01c(`FigmaDesignOperationRepository.appendTransitionWithRequest` — `canonicalRequestView()`가 database/tableName을 해시 계산에서 애초에 제외하고 있어 사용자 확인 후 그 부분은 그대로 두고 해시 변경 시에만 멱등성 행 추가하도록 조건부 구현)·A-01d(`AWAITING_TABLE_BINDING → ANALYZED` 전이 + baseline 재생성)·A-01e(`FigmaDesignOrchestrationService.bindTable()` + `FigmaDesignOrchestrationTool.bindFigmaDesignRequestTable` MCP Tool) 순서로 구현. C-04로 관련 Tool description을 S-01 이후 실제 동작에 맞게 정정(기존 문구가 stale 상태였음을 발견). A-02로 `POST /api/figma/orchestration/bind-table` REST 엔드포인트 추가(기존 X-API-Key 인증 재사용, 신규 SecurityConfig 변경 없음). A-03은 기존 `GET /operations/{operationId}/info`가 `issues`를 그대로 반환해 신규 API 없이 충족 확인. C-03으로 `figma-design-operation-v1.schema.json`에 상태값 반영, 계약 테스트 통과. T-03/T-06은 기존 테스트가 이미 커버해 신규 코드 없이 완료 처리, T-04/T-05/T-07은 신규 테스트 8건(Service 6 + Tool 1 + Controller 1) 추가. C-05는 별도 fixture 파일 대신 T-04의 인라인 fixture로 충족. 매 항목마다 사용자 승인 후 순차 착수했으며, 전체 Java 테스트 스위트를 여러 차례 재확인해 회귀 없음을 검증. 잔여는 DEC-03(12번 문서 §11.4.1 정식 `R6-0xx` 재번호 시점)뿐 |
| 1.5 | 2026-08-19 | **S-03(`FieldRoleToColumnMatcher`) 삭제 + S-04/S-05 재해석 + A-01 구조적 제약 발견·확장 계획 수립.** S-04/S-05 설계 중 `ScreenSpecificationService.create(...)`가 이미 `ScreenSpecAssembler.bindingsFromHints()`로 역할힌트↔실제컬럼 매칭과 `NO_COLUMN_CANDIDATE`→`REVIEW_REQUIRED` 판정을 수행하고 있음을 발견 — S-03이 기존 기능 중복 구현이었음이 확인돼 사용자 승인 후 전체 삭제(`FieldRoleToColumnMatcher`/`FieldColumnMatch`/`FieldMatchResult`/전용 테스트 12건 제거, `ColumnMeta.fromRow()`도 함께 제거해 `ColumnMeta` 원상복구). S-04/S-05는 기존 `finalizeFromScreenSpecification()`이 이미 충족함을 확인해 새 코드 없이 완료 처리. 이어서 A-01(`bindFigmaDesignRequestTable`) 설계 중 `FigmaDesignOperationRepository.appendTransition()`이 `request`를 절대 변경하지 않는(`withNextRevision()`에 request 파라미터 없음, operationId가 request 해시로 identity 고정) 구조적 제약을 발견 — 사용자 검토 후 Operation 모델을 확장하는 방향(A-01a~e)으로 계획 수립. 이 모델이 7가지 디자인 요청 전체가 공유하고 `.figpack` 하이브리드 흐름과는 무관함을 코드로 확인. 전체 스위트(1,588개) 통과. 실제 A-01a~e 구현은 다음 세션 |
| 1.4 | 2026-08-19 | **S-03(`FieldRoleToColumnMatcher`) 구현 완료 + S-02 전용 테스트(T-01) 보강.** `SchemaReaderTool.getTableSchema()`가 구조화 데이터가 아닌 포맷 문자열을 반환함을 발견해 착수 전 사용자와 확인 후 `CrudSchemaQueryService`/`ColumnMeta`(CRUD 생성 파이프라인 재사용, `ColumnMeta.fromRow()` 정적 팩토리 신설) 기반으로 전환. 매처는 DB에 접근하지 않는 순수 함수(`List<ColumnMeta>` 입력)로 설계. DEC-02(역할↔컬럼 키워드 사전)는 임의 추측 대신 `docker exec`로 실제 `ebt.LETTNQAINFO`/`LETTNBBS`/`LETTNBBSMASTER`/`LETTNEMPLYRINFO` 컬럼·코멘트를 조회해 근거 마련(사용자 승인 경로). 테스트 작성 중 STATUS 키워드 "여부"가 `EMAIL_ANSWER_AT`(메일답변여부)와 오매칭되는 실제 충돌을 발견해 정정. 신규 테스트 17건(매처 12 + 추출기 5), 전체 스위트(1,600개) 통과. S-04/S-05(실제 오케스트레이션 흐름 연결)는 착수 전 별도 확인 예정 |
| 1.3 | 2026-08-19 | **S-01 경로 A로 정정 + S-02 구현 완료.** 사용자 재검토 결과 1.2의 "DB 체크를 분석 전에 유지"가 22번 문서 §4 원 설계("DB 없어도 분석 실행 후 필드 후보 저장")를 위배하고 절충안의 핵심 가치("디자인 먼저, 테이블은 나중")를 무력화한다는 지적을 받아 되돌림. DB 체크를 분석 API 호출 뒤로 옮겨 `analyzeFigma`/`analyze`가 항상 실행되도록 하고, `DesignFieldCandidateExtractor`(S-02, `UiDesignSpec.fieldHints()` 재사용)로 뽑은 필드 후보를 `FIELD_CANDIDATE` 이슈로 `AWAITING_TABLE_BINDING` 전이에 실어 보냄. 관련 테스트 3건 신규/교체, 전체 스위트(1,583개) 통과. A-01 전까지 여전히 과도기 상태라는 제약은 유지 |
| 1.2 | 2026-08-19 | **S-01 구현 완료.** `generateFromReference()`/`generateFromImage()`에 분기 추가 — database/tableName 없으면 `DATABASE_TABLE_REQUIRED`/`REJECTED` 대신 `DATABASE_TABLE_PENDING` WARNING + `AWAITING_TABLE_BINDING`으로 전이. DB 체크는 분석 API 호출 전에 유지(S-02 없이는 분석 결과를 못 쓰므로 API 낭비 방지). COMPONENT_SPECIFIED는 영향 없음(기존 경로 유지 확인). 기존 REFERENCE_STYLE without-database 테스트를 새 기대값으로 갱신 + IMAGE_REFERENCE 대응 테스트 신규 추가. **다음 항목(S-02/S-03/A-01) 없이는 AWAITING_TABLE_BINDING 상태에서 실제로 벗어날 방법이 없는 과도기 상태임을 명시.** |
| 1.1 | 2026-08-19 | **PROP-01(C-01/C-02) 구현 완료.** `FigmaDesignOperationStatus.AWAITING_TABLE_BINDING` 신설 + `FigmaDesignOperationStateService` 전이 그래프 확장(`ANALYZED→AWAITING_TABLE_BINDING→{PREVIEW_READY,FAILED,REJECTED}`). 구현 중 원안의 "REVIEW_REQUIRED 전이"가 실제로는 존재하지 않는 상태값이었음을 발견해 `REJECTED`(기존 `rejectReviewRequired()` 패턴)로 설계 정정. `FigmaDesignOperationStateServiceTest` 신규 테스트 4건 + `FigmaDesignOperationStateTransitionSnapshotTest` baseline 재생성. 관련 패키지 테스트 및 전체 스위트 통과. C-03(JSON Schema 반영)은 아직 미착수(현재 기존 fixture만으로는 회귀 없음 확인) |
| 1.0 | 2026-08-19 | 22번 제안서(PROP-01~04)를 실행 체크리스트로 분해해 최초 작성. 전 항목 `[ ]` 미구현, 착수 전 사용자 승인 대기 |
