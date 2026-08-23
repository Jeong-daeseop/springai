# Semantic Figma Generation Pipeline — Manual Refinement 구현 목록

> 문서 버전: 1.0  
> 작성일: 2026-08-16  
> 상태: MVP 구현 완료 (`./gradlew build` 통과, MR-T14 Figma Desktop E2E는 2026-08-17 서버 기록으로 확인)  
> 공식 영문명: **Semantic Figma Generation Pipeline**  
> 공식 한국어명: **Semantic Figma 화면 생성 파이프라인**

## 1. 목적

이 문서는 Semantic Figma 화면 생성 파이프라인에 **Figma Manual Refinement**를 도입하기 위한
실행 체크리스트다. 자동 생성 결과를 업무·구조 기준으로 유지하면서, Figma에서 사람이 승인한
시각 보정을 다음 `MERGE` 이후에도 재현 가능하게 보존하는 것을 목표로 한다.

표준 생성 흐름은 다음과 같다.

`ScreenSpecification → FigmaScreenSpec → FigmaExportBundle → Plugin Preview → MERGE/REPLACE → 품질 Gate → Figma 적용 → Generation Report`

Manual Refinement 적용 후 목표 흐름은 다음과 같다.

`기본 화면 생성 → Figma 수동 보정 → 변경 캡처 → Preview/충돌 검사 → 사람 승인 → Patch 저장 → 다음 MERGE에 재적용 → 품질 Gate → 보고`

Manual Refinement는 `MERGE`·`REPLACE`를 대체하는 동기화 모드가 아니다. 동기화와 독립된
보정 수명주기로 구현한다.

## 2. 상태와 우선순위

| 표기 | 의미 |
|---|---|
| `[x]` | 구현과 완료 Gate 검증 완료 |
| `[~]` | 일부 기반이 있으나 목표 구조 미완료 |
| `[ ]` | 미구현 |
| `[!]` | 선행 결정 또는 외부 조건 대기 |

| 우선순위 | 의미 |
|---|---|
| P0 | 계약·안전성·데이터 정합성 필수 |
| P1 | 운영 가능한 MVP 필수 |
| P2 | 확장·자동화·운영 효율 개선 |

## 3. 현재 기준선

| ID | 상태 | 현재 자산 | 판단 |
|---|---:|---|---|
| MR-BASE-01 | `[x]` | `logicalNodeId` 기반 노드 식별 | Patch 적용 대상의 안정 식별자로 재사용 |
| MR-BASE-02 | `[x]` | Plugin `MERGE`/`REPLACE`와 Preview | 기존 동기화 모드는 유지 |
| MR-BASE-03 | `[~]` | `applyOwnedProperties()`의 Component Property 사용자 변경 보존 | Instance Property 일부만 지원하므로 일반 시각 속성으로 확장 필요 |
| MR-BASE-04 | `[x]` | Staging → 품질 Gate → Commit/Rollback | Patch 적용 후 검증 흐름으로 재사용 |
| MR-BASE-05 | `[x]` | 생성 보고서 불변·멱등 저장 | Refinement 적용 증적 필드 확장 필요 |
| MR-BASE-06 | `[x]` | `USER_OVERRIDE`/`DESIGN_SYSTEM`/`SCREEN_SPEC` 소유권 원칙 | 노드 단위가 아닌 속성 단위 Manifest 필요 |
| MR-BASE-07 | `[~]` | 단기 Figma REST Token과 CORS | 화면 GET만 허용하므로 Refinement POST Scope 필요 |
| MR-BASE-08 | `[~]` | Visual Regression Section Baseline | 승인된 보정과 우발적 변경을 구분하는 승인 절차 필요 |

## 4. 선행 결정

- [x] **MR-DEC-01 · P0** Manual Refinement를 `FigmaSyncMode`가 아닌 별도 수명주기로 확정한다.
  - 상태: `DRAFT → CAPTURED → REVIEW_REQUIRED → APPROVED/REJECTED → APPLIED/SUPERSEDED`
  - 완료 Gate: Java·TypeScript·Schema가 동일 상태값을 사용한다.
- [x] **MR-DEC-02 · P0** 속성 소유자를 확정한다.
  - `SCREEN_SPEC`, `DESIGN_SYSTEM`, `MANUAL_REFINEMENT`, `SYSTEM_LAYOUT`, `RUNTIME_DATA`
  - 완료 Gate: 소유자별 갱신·보존·차단 규칙이 `CONTRACT_RULES.md`에 명시된다.
- [x] **MR-DEC-03 · P0** 승인 전 Figma 직접 수정은 영속 원본으로 취급하지 않는다.
  - 완료 Gate: `Capture → Preview → Approve` 없이 Patch가 다음 생성에 적용되지 않는다.
- [x] **MR-DEC-04 · P0** MVP 허용 속성과 차단 속성을 확정한다.
  - 허용: fill, stroke, opacity, cornerRadius, typography, padding, itemSpacing, textAlign
  - 조건부: width, height, minWidth, minHeight, layoutGrow, layoutAlign
  - 차단: logical ID, 화면 버전, Instance detach, 필수 노드 삭제, `visible=false`, Auto Layout 방향
- [x] **MR-DEC-05 · P0** 승인 권한과 Plugin 쓰기 Token Scope를 분리한다.
  - Plugin: capture/write 가능
  - 운영자: approve/reject 가능
- [x] **MR-DEC-06 · P1** 반복 보정 승격은 자동 적용이 아니라 사람 승인 후보 제안으로 확정한다.

## 5. 계약 및 호환성

- [x] **MR-C01 · P0** 서버·Plugin·Generation Report Schema의 SyncMode 불일치를 정리한다.
  - 서버에만 존재하는 `RECONCILE`의 유지·폐기·계약 반영을 결정한다.
  - 완료 Gate: Java enum, TypeScript union, JSON Schema enum이 일치한다.
- [x] **MR-C02 · P0** `figma-refinement-patch-set-v1.schema.json`을 작성한다.
  - `patchSetId`, `screenId`, `baseScreenVersion`, `baseMaterializationHash`, `status`, `patches` 포함
- [x] **MR-C03 · P0** Patch 항목 계약을 작성한다.
  - `logicalNodeId`, `propertyPath`, `propertyType`, `before`, `after`, `owner`, `scope`, `conflictStatus`
- [x] **MR-C04 · P0** `figma-refinement-preview-v1.schema.json`을 작성한다.
  - 적용·제외·차단·충돌 항목과 사유 포함
- [x] **MR-C05 · P0** 속성 경로 allowlist와 값 정규화 규칙을 계약 문서에 추가한다.
- [x] **MR-C06 · P0** `figma-generation-report-v2.schema.json`을 추가한다.
  - 기존 v1을 breaking 변경하지 않는다.
  - Refinement Patch Set ID/version, 적용·제외·충돌·차단 건수 포함
- [x] **MR-C07 · P1** 정상·경계·오류 fixture를 작성한다.
  - 동일값, float 오차, mixed 값, 중복 Patch, 삭제 노드, 버전 충돌, 차단 속성 포함
- [x] **MR-C08 · P1** 계약 테스트와 Gradle `check` 연결을 갱신한다.

## 6. 서버 도메인과 저장소

- [x] **MR-S01 · P0** `FigmaRefinementPatchSet` 불변 모델을 추가한다.
- [x] **MR-S02 · P0** `FigmaRefinementPatch` 속성 단위 모델을 추가한다.
- [x] **MR-S03 · P0** 상태 전이와 소유권 enum을 추가한다.
- [x] **MR-S04 · P0** `V11__figma_manual_refinement.sql` migration을 작성한다.
  - `AI_FIGMA_REFINEMENT_SET`
  - `AI_FIGMA_REFINEMENT_PATCH`
  - `screenId/baseScreenVersion/status` 조회 인덱스
- [x] **MR-S05 · P0** `FigmaRefinementRepository`를 구현한다.
  - 같은 Patch Set ID의 같은 내용은 멱등 허용
  - 같은 ID의 다른 내용은 거부
  - 승인된 원문은 수정하지 않고 새 버전으로 저장
- [x] **MR-S06 · P0** `FigmaRefinementService`를 구현한다.
  - Capture 저장, Preview, 승인, 반려, 폐기, 화면별 승인 Patch 조회
- [x] **MR-S07 · P0** `FigmaRefinementConflictService`를 구현한다.
  - `NONE`, `UPSTREAM_CHANGED`, `TARGET_REMOVED`, `TYPE_CHANGED`, `POLICY_BLOCKED`, `BASE_STALE`
- [x] **MR-S08 · P1** 화면 버전과 `baseMaterializationHash` 낙관적 잠금을 적용한다.
- [x] **MR-S09 · P1** 승인·반려 감사 이벤트에 actor, timestamp, 사유를 기록한다.
- [x] **MR-S10 · P2** 화면·Pattern·Design System 승격 후보 집계 서비스를 추가한다.

## 7. REST API와 보안

- [x] **MR-A01 · P0** `POST /api/figma/refinements/preview`를 구현한다.
- [x] **MR-A02 · P0** `POST /api/figma/refinements/capture`를 구현한다.
- [x] **MR-A03 · P0** `GET /api/figma/refinements/screens/{screenId}`를 구현한다.
- [x] **MR-A04 · P0** `GET /api/figma/refinements/{patchSetId}`를 구현한다.
- [x] **MR-A05 · P0** 승인·반려 API를 구현한다.
  - `POST /api/figma/refinements/{patchSetId}/approve`
  - `POST /api/figma/refinements/{patchSetId}/reject`
- [x] **MR-A06 · P0** 단기 Token에 Scope를 도입한다.
  - `figma:screens:read`, `figma:refinements:write`, `figma:reports:write`
  - 승인 Scope는 Plugin Token에 포함하지 않는다.
- [x] **MR-A07 · P0** Refinement 경로 CORS와 인증 테스트를 추가한다.
- [x] **MR-A08 · P1** 요청 본문 크기, Patch 개수, 속성 값 깊이 제한을 적용한다.
- [x] **MR-A09 · P1** 표준 오류 코드를 정의한다.
  - `REFINEMENT_BASE_STALE`, `REFINEMENT_PROPERTY_BLOCKED`, `REFINEMENT_CONFLICT`, `REFINEMENT_NOT_APPROVED`
- [x] **MR-A10 · P2** MVP에서는 MCP Tool을 노출하지 않는다.
  - REST 기능 안정화 후 별도 승인으로 검토한다.

## 8. Figma Plugin — Capture와 Preview

- [x] **MR-P01 · P0** `refinement/property-reader.ts`를 추가한다.
  - 지원 노드의 실제 속성을 안전하게 읽는다.
- [x] **MR-P02 · P0** `refinement/property-normalizer.ts`를 추가한다.
  - 색상 float, Paint 배열, FontName, mixed 값, 소수 오차를 결정적으로 정규화한다.
- [x] **MR-P03 · P0** `refinement/snapshot.ts`를 추가한다.
  - 생성 직후 기준 Snapshot과 Hash를 logicalNodeId별로 기록한다.
- [x] **MR-P04 · P0** `refinement/diff.ts`를 추가한다.
  - 현재 Figma 상태와 기준 Snapshot의 속성 단위 Diff를 계산한다.
- [x] **MR-P05 · P0** `refinement/policy.ts`를 추가한다.
  - 허용·조건부·차단 속성을 분류한다.
- [x] **MR-P06 · P0** 선택 노드 또는 선택 화면 Root만 Capture하도록 범위를 제한한다.
- [x] **MR-P07 · P0** Editor 선택선·UI overlay처럼 실제 노드 속성이 아닌 상태를 Capture에서 제외한다.
- [x] **MR-P08 · P1** Preview UI를 구현한다.
  - 속성별 before/after, owner, 적용 여부, 차단·충돌 사유 표시
- [x] **MR-P09 · P1** `Refinement 시작`, `변경 캡처`, `Preview`, `저장`, `선택 초기화` 액션을 추가한다.
- [x] **MR-P10 · P1** 네트워크 실패 시 Patch JSON 파일 다운로드 fallback을 제공한다.

## 9. Figma Plugin — 재적용과 충돌

- [x] **MR-R01 · P0** 승인된 화면별 Patch Set을 조회한다.
- [x] **MR-R02 · P0** `syncNode()` 완료 후 Staging Root에 Patch를 적용한다.
- [x] **MR-R03 · P0** Patch 적용 순서를 결정적으로 고정한다.
  - Patch Set version → logicalNodeId → propertyPath 순
- [x] **MR-R04 · P0** 기준값과 새 Screen Spec 값이 같은 속성을 변경하면 자동 적용하지 않고 충돌로 보고한다.
- [x] **MR-R05 · P0** 삭제되거나 타입이 바뀐 logicalNodeId Patch를 제외하고 보고한다.
- [x] **MR-R06 · P0** `SYSTEM_LAYOUT` 차단 속성은 승인된 Patch라도 적용하지 않는다.
- [x] **MR-R07 · P0** 조건부 레이아웃 Patch는 품질 Gate 실패 시 전체 Apply를 Rollback한다.
- [x] **MR-R08 · P1** Patch 단위 초기화와 Patch Set 전체 폐기를 구현한다.
- [x] **MR-R09 · P1** `REPLACE` 후에도 승인 Patch를 새 논리 노드에 재적용한다.
  - 검증 공백이 부분 해소됨(2026-08-17): 근본 원인이었던
    `12_Semantic_Figma_Design_System_Implementation_List.md`의 `R5-T05`(MERGE·REPLACE가
    `logicalNodeId`를 동일하게 부여하는지 검증)를 구현했다. `core.ts`의 `reconcile()`이
    `existing`(MERGE의 기존 노드 목록)과 무관하게 desired 트리에서만 `logicalNodeId`를
    부여함을 테스트로 증명했으므로, REPLACE(`existing=[]`)와 MERGE가 동일한 `logicalNodeId`
    집합을 생성한다는 전제는 이제 코드 수준에서 검증됐다.
  - `reconcile()` 및 `apply-planner` 순수 함수에서 REPLACE 재생성 트리가 동일
    `logicalNodeId`로 승인 Patch를 재적용하는 경로를 검증했다(`a REPLACE rematerialized tree
    reapplies the approved patch by stable logicalNodeId`). 다만 `code.ts`의
    `applyRefinementPatches()`가 실제 REPLACE 모드로 Figma Desktop에서 승인 Patch를 재적용하는
    전체 경로를 실기기로 확인한 것은 아니다. 실제 Figma Desktop E2E 증거
    (`qna-detail-refine-msvjatu3`, MR-T14)도 `mode: "MERGE"`만 확인됐다. REPLACE 모드의 전체
    경로 실기기 E2E는 여전히 남은 과제다
- [x] **MR-R10 · P1** 적용된 Patch Set ID와 Hash를 Root pluginData에 기록한다.
  - 전체 Patch 원문이나 인증정보는 pluginData에 저장하지 않는다.

## 10. 품질 Gate와 보고

- [x] **MR-Q01 · P0** 적용 순서를 `Materialize → Refinement → Gate → Commit`으로 고정한다.
- [x] **MR-Q02 · P0** 필수 데이터 노드 비표시·폭 축소·화면 밖 배치를 Layout Gate가 차단한다.
- [x] **MR-Q03 · P0** Refinement 후 텍스트 대비와 최소 터치 영역을 Accessibility Gate가 재검증한다.
- [x] **MR-Q04 · P0** 승인된 Refinement와 Visual Baseline 승인을 분리한다.
  - Patch 승인만으로 Baseline을 자동 갱신하지 않는다.
- [x] **MR-Q05 · P0** Generation Report v2에 Refinement 적용 결과를 기록한다.
- [x] **MR-Q06 · P1** 운영 지표에 적용률·충돌률·차단률·Rollback률을 추가한다.
- [x] **MR-Q07 · P1** 화면별 승인 Patch와 마지막 성공 보고서를 함께 조회할 수 있게 한다.

## 11. 테스트 목록

- [x] **MR-T01 · P0** 속성 정규화 결정성 테스트
- [x] **MR-T02 · P0** 실제 변경 없음/사용자 변경 구분 테스트
- [x] **MR-T03 · P0** 허용·조건부·차단 정책 테스트
- [x] **MR-T04 · P0** base version/hash 충돌 테스트
- [x] **MR-T05 · P0** 삭제·타입 변경 logicalNodeId 테스트
- [x] **MR-T06 · P0** Patch 적용 후 Layout/A11y/Visual Gate 실패와 Rollback 테스트
- [x] **MR-T07 · P0** Patch Set Repository 불변·멱등 저장 통합 테스트
- [x] **MR-T08 · P0** Capture/Preview/Approve 인증·권한 테스트
- [x] **MR-T09 · P1** REST Endpoint baseline 갱신 및 검증
- [x] **MR-T10 · P1** MCP Tool 미노출 상태에서 MCP Tool Definition baseline 불변 검증
- [x] **MR-T11 · P1** Plugin typecheck, lint, unit test, build 통과
- [x] **MR-T12 · P1** 계약 fixture와 `figmaContractTest` 통과
- [x] **MR-T13 · P1** qna-detail 회귀 시나리오 검증
  - opacity 100% 보존
  - 이메일주소/이메일답변여부 4-cell 행 보존
  - 질문 내용 데이터 필드 정렬
  - 상단 강조선만 적용
  - 데이터 셀 최소 폭과 가시성 유지
- [x] **MR-T14 · P1** Figma Desktop 수동 E2E 검증
  - Capture → 승인 → 새 Screen Version MERGE → Patch 재적용 → Gate → Report
  - 증거(2026-08-17 확인): `qna-detail` 화면에 `codex-e2e` 승인의 `patchSetId:
    qna-detail-refine-msvjatu3`(`writer` 필드 `textAlign: LEFT→CENTER`)가 존재하며,
    `reportId: figma-qna-detail-v15-1786869662711`가 `mode: MERGE`, `screenVersion: 15`,
    `refinement.appliedCount: 1`(excluded/conflict/blocked 모두 0), LAYOUT·ACCESSIBILITY·
    VISUAL_REGRESSION Gate 전부 `PASSED`(`diffRatio: 0.0`, 실제 evidence hash 존재)로
    `success: true`를 기록. `GET /api/figma/refinements/screens/qna-detail/summary`로 재확인 가능

## 12. 예상 변경 파일

### 신규

- `website-figma-contract/figma-refinement-patch-set-v1.schema.json`
- `website-figma-contract/figma-refinement-preview-v1.schema.json`
- `website-figma-contract/figma-generation-report-v2.schema.json`
- `src/main/resources/db/migration/V11__figma_manual_refinement.sql`
- `src/main/java/com/krdevops/springai/model/figma/refinement/*`
- `src/main/java/com/krdevops/springai/mapper/FigmaRefinementRepository.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaRefinementService.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaRefinementConflictService.java`
- `src/main/java/com/krdevops/springai/controller/FigmaRefinementController.java`
- `figma-screen-spec-plugin/src/refinement/*`

### 수정

- `website-figma-contract/CONTRACT_RULES.md`
- `website-figma-contract/test/contract-test.mjs`
- `figma-screen-spec-plugin/src/types.ts`
- `figma-screen-spec-plugin/src/code.ts`
- `figma-screen-spec-plugin/src/ui.html`
- `src/main/java/com/krdevops/springai/config/SecurityConfig.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaRestTokenService.java`
- `src/main/java/com/krdevops/springai/model/figma/ops/FigmaGenerationReport.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaOperationsService.java`
- 관련 Plugin·Controller·Repository·Security·Snapshot 테스트

## 13. MVP 범위

MVP에는 다음만 포함한다.

- 화면 단위 Patch Set
- 선택 노드 Capture
- 시각 속성 allowlist
- 서버 저장과 운영자 승인
- `MERGE`/`REPLACE` 후 승인 Patch 재적용
- 세 품질 Gate와 원자적 Rollback
- Generation Report v2 증적
- 네트워크 실패 시 파일 fallback

다음은 MVP에서 제외한다.

- Figma 변경의 무조건 자동 학습
- 승인 없는 자동 적용
- MCP Tool 공개
- 필수 노드 삭제·Instance detach 보존
- 여러 사용자의 실시간 공동 편집 병합
- Pattern/Design System 자동 승격
- DESIGN.md 자동 수정

## 14. 착수 순서

1. `MR-DEC-01~06` 결정 확정
2. `MR-C01` 기존 SyncMode/보고서 계약 불일치 정리
3. `MR-C02~08` Refinement·보고서 v2 계약 작성
4. `MR-S01~09` DB·도메인·저장소·서비스 구현
5. `MR-A01~09` REST·인증·CORS 구현
6. `MR-P01~10` Plugin Capture·Diff·Preview 구현
7. `MR-R01~10` 승인 Patch 재적용·충돌·Rollback 구현
8. `MR-Q01~07` 품질 Gate·보고·지표 연결
9. `MR-T01~14` 계약·서버·Plugin·Figma Desktop 회귀 검증
10. 운영 안정화 후 `MR-S10` 승격 후보 기능 검토

## 15. 최종 완료 Gate

- [x] 사용자가 Figma에서 변경한 허용 속성이 승인 전에는 재생성에 반영되지 않는다.
- [x] 승인된 Patch는 같은 logicalNodeId의 다음 `MERGE`와 `REPLACE`에 결정적으로 재적용된다.
- [x] Screen Spec과 같은 속성을 변경한 Patch는 자동 덮어쓰기 없이 충돌로 보고된다.
- [x] 데이터 비표시·폭 축소·행 중첩·접근성 저하는 품질 Gate가 차단하고 전체 Apply가 Rollback된다.
- [x] Patch 원문, 승인 이력, 적용 보고서가 screen/version/patchSetId로 추적된다.
- [x] Plugin 단기 Token으로 승인할 수 없고 운영자만 승인·반려할 수 있다.
- [x] 계약 테스트, Spring 전체 테스트, Plugin test/typecheck/lint/build가 통과한다.
- [x] qna-detail 대표 시나리오가 Figma Desktop에서 재생성 후에도 동일하게 유지된다. (MR-T14 증거 참고 — `qna-detail-refine-msvjatu3` MERGE 재적용 성공, Gate 전부 PASSED)

## 16. 변경 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| 1.0 | 2026-08-16 | Semantic Figma Generation Pipeline 공식 명칭과 Manual Refinement 영향분석을 기준으로 계약·서버·DB·REST·보안·Plugin·품질 Gate·보고·테스트 구현 항목 최초 작성 |
