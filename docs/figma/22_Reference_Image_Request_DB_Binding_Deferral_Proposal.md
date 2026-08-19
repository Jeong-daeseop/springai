# REFERENCE_STYLE / IMAGE_REFERENCE DB 테이블 바인딩 지연 절충안 (제안서)

> **상태: 제안 — 구현 착수 전 사용자 승인 필요.**
> 작성일: 2026-08-18
> 관련 문서: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md) §11.4.1(원 반영 위치), §4 DEC 목록, §19 변경이력 v4.1(DB 테이블 바인딩 통일 결정)

이 문서는 12번 문서 §11.4.1에 요약 반영된 절충안을 독립적으로 상세화한 제안서다. 실제 착수 여부와
세부 설계는 사용자 승인 이후 확정하며, 착수 시 이 문서의 PROP-ID는 12번 문서의 정식 `R6-0xx`
ID로 재번호될 수 있다.

> 이 제안을 실행 가능한 체크리스트로 분해한 문서:
> [23_REFERENCE_STYLE_IMAGE_REFERENCE_DB_Binding_Deferral_Implementation_List.md](./23_REFERENCE_STYLE_IMAGE_REFERENCE_DB_Binding_Deferral_Implementation_List.md)

---

## 1. 배경

2026-08-17 세션에서 R6-032~038(7가지 디자인 요청 중 6개) 실제 생성 파이프라인을 연결하던 중
근본적인 모델 불일치를 발견했다.

- `analyzeFigmaReference`/`analyzeDesignReference` → `createScreenSpecification`으로 이어지는
  유일한 실제 화면 생성 경로는 **DB 테이블 스키마 바인딩이 필수**(`database`/`tableName` 파라미터).
- 반면 7가지 디자인 요청(`FigmaDesignRequest`)은 자연어 prompt만 가지고 있어 DB 테이블 개념이
  없었다.

당시 3가지 선택지를 검토했다.

| 옵션 | 내용 | 채택 여부 |
|---|---|---|
| (A) DB 테이블 바인딩으로 통일 | 기존 CRUD 생성 경로와 동일하게 6개 요청 모두 DB 테이블에 바인딩 | **채택** |
| (B) DB 없는 새 경로 구축 | `UiDesignSpec`/컴포넌트 목록만으로 `ScreenSpecification` 생성(신규 아키텍처) | 미채택 |
| 기술적으로 가능한 부분만 우선 구현 | DB 바인딩 없이도 닫히는 항목만 먼저(COMPONENT_SPECIFIED 등) | 미채택 |

(A)가 채택되어 `FigmaDesignRequest`/`FigmaScreenRequest`에 `database`/`tableName`/`screenName`/
`featureType`/`screenSpecificationId` 필드가 신설됐고, R6-033(REFERENCE_STYLE)·R6-035
(IMAGE_REFERENCE)를 포함한 대부분의 요청 유형이 이 방향으로 구현 완료됐다(12번 문서 §11.4 참고).

**남은 한계**: (A)를 선택한 결과, REFERENCE_STYLE·IMAGE_REFERENCE도 `database`/`tableName`을
먼저 알아야 생성이 시작된다. "디자인(참조 화면/이미지)을 먼저 보고, 테이블은 사람이 나중에
고르는" 워크플로우는 지원하지 않는다. 이 문서는 그 한계만 국소적으로 보완하는 절충 설계다.

---

## 2. (A) vs (B) 재요약과 절충의 위치

| | (A) DB 바인딩 통일 (현재) | 절충안 (이 문서) | (B) DB 없는 새 경로 |
|---|---|---|---|
| 실행 가능한 코드로 이어짐 | O | O (2단계 이후 A와 동일 경로) | X (별도 매핑 필요) |
| 필드 존재성·타입 검증 | O (schemaBindings 물리 COLUMN) | O (동일) | X |
| 디자인 우선 워크플로우 | X | **O (1단계에서 DB 없이 Preview)** | O |
| 신규 개발 범위 | 없음(이미 구현) | 4개 컴포넌트(PROP-01~04) | 전면 신규 아키텍처 |
| 적용 대상 | 7종 전체 | REFERENCE_STYLE·IMAGE_REFERENCE 2종만 | 7종 전체(설계 시) |

절충안은 (A)의 최종 산출물(실행 가능한 코드)과 무결성 보장을 그대로 유지하면서, (B)가 주는
"디자인 우선 체감"을 1단계 Preview 국면에서만 국소적으로 얹는 방식이다.

---

## 3. 적용 범위

**REFERENCE_STYLE**(`create_design_from_reference`)과 **IMAGE_REFERENCE**
(`create_design_from_image`) 2종에 한정한다.

- TEXT_DESCRIPTION: 테이블을 먼저 아는 게 자연스러운 요청 유형 — 대상 아님
- MODIFY_EXISTING: 이미 `screenSpecificationId`로 기존 명세를 참조 — 대상 아님
- COMPONENT_SPECIFIED: 이미 Registry 기반 allowlist 검증이 전제 — 대상 아님
- MULTI_SCREEN_FLOW: 화면별 DB 바인딩이 필수 구조 — 대상 아님
- PLATFORM_CONVERT: 기존 승인 명세의 뷰포트 변환 — 대상 아님

---

## 4. 제안 흐름

```
1) generateFigmaBundleForOperation(operationId)
   ├─ request.database/tableName 있음 → 기존 경로 그대로(변경 없음)
   └─ request.database/tableName 없음(신규 허용)
        → analyzeFigmaReference / (queryImages+analyzeDesignReference) 그대로 실행
        → UiDesignSpec 생성
        → DesignFieldCandidateExtractor(신규)가 필드 후보만 추출
          예: [{label:"제목", roleHint:TITLE}, {label:"작성자", roleHint:AUTHOR}, ...]
        → DB 바인딩 없이 후보 목록을 Operation에 저장, 상태 AWAITING_TABLE_BINDING

2) 사람이 필드 후보 Preview를 보고 신규 MCP Tool 호출
   bindFigmaDesignRequestTable(operationId, database, tableName)
        → SchemaReaderTool.getTableSchema()로 실제 컬럼 목록 조회
        → FieldRoleToColumnMatcher(신규)가 roleHint/label ↔ 실제 컬럼 매칭 시도

3a) 전부 고신뢰 매칭 + PK 포함
        → createScreenSpecification() 즉시 APPROVED
        → generateBundle 재실행 → PREVIEW_READY

3b) 일부 매칭 애매/누락/PK 없음
        → 기존 REVIEW_REQUIRED 경로 재사용
        → reviseScreenSpecification → approve → Bundle
```

### 상태 모델

```
ANALYZED
  └─(database/tableName 없이 generateBundle 호출)─▶ AWAITING_TABLE_BINDING   ← 신규
                                                          │
                                          (사람이 bind-table 호출)
                                                          ▼
                                   REVIEW_REQUIRED(매칭 애매) 또는 곧장 PREVIEW_READY(매칭 확정)
```

`AWAITING_TABLE_BINDING`은 R6-047 계약("모든 응답은 operationId/artifactId/PREVIEW_READY 등
상태를 포함하고 Apply 전엔 APPLIED 반환 금지")을 깨지 않는 추가 중간 상태다. 기존 상태 전이
검증기(`FigmaDesignOperationStateService`)에 이 한 갈래만 얹으면 되고, 전체 상태 그래프를 다시
설계할 필요는 없다.

---

## 5. 신규 구성요소 (PROP-01~04)

| ID | 구성요소 | 성격 | 내용 | 재사용 가능 자산 |
|---|---|---|---|---|
| PROP-01 | 상태 모델 확장 | 상태 머신 확장(소) | `FigmaDesignOperationStatus`에 `AWAITING_TABLE_BINDING` 신설 | `FigmaDesignOperationStateService` 기존 패턴 |
| PROP-02 | `DesignFieldCandidateExtractor` | 신규 서비스(소~중) | `UiDesignSpec`에서 DB 매핑 없이 필드 후보(label+roleHint)만 추출 | `UiDesignSpec` 힌트 구조 이미 존재 |
| PROP-03 | `FieldRoleToColumnMatcher` | 신규 서비스(중) | roleHint/label ↔ 실제 컬럼 매칭. 신뢰도 미달 시 REVIEW_REQUIRED | `SchemaReaderTool.getTableSchema()` 그대로 사용 |
| PROP-04 | `bindFigmaDesignRequestTable` | 신규 진입점(소) | `AWAITING_TABLE_BINDING` 상태 Operation에 `database`/`tableName` 사후 지정 | 기존 `figmaMcpSecret` 인증 패턴 |

**매칭 신호 우선순위(PROP-03 세부)**:

1. roleHint와 컬럼 코멘트/명명 규칙 일치(예: `AUTHOR` ↔ `WRTER_ID` 등 eGovFrame 명명 패턴)
2. label 텍스트와 컬럼 코멘트 문자열 유사도
3. 타입 정합성(예: DATE 힌트인데 컬럼이 VARCHAR면 신뢰도 하락)

신뢰도 threshold 미달 시 자동 확정하지 않고 REVIEW_REQUIRED로 넘긴다(임의 추측으로 필드를
확정하지 않는다는 기존 원칙과 동일선상). PK는 항상 강제 포함한다.

---

## 6. 재사용 자산 (신규 개발 최소화)

다음은 **변경 없이 그대로** 재사용한다.

- `analyzeFigmaReference` / `analyzeDesignReference`(Vision 분석)
- `SchemaReaderTool.getTableSchema()`(컬럼 메타 조회)
- `createScreenSpecification()`의 REVIEW_REQUIRED 경로
- `reviseScreenSpecification()` / `approveScreenSpecification()`
- `generateBundle()`(ScreenSpecification → FigmaScreenSpec → Bundle)
- `figmaMcpSecret` 기반 MCP Tool 인증

신규 개발은 PROP-01~04 네 조각으로 국한되며, 규모는 R6-033 또는 R6-035 개별 항목 하나를 새로
만드는 정도로 추정한다. (B)처럼 CRUD 엔진 밖에 완전히 별도 아키텍처를 세우는 것보다 훨씬 작다.

---

## 7. 리스크와 완화

| 리스크 | 내용 | 완화 |
|---|---|---|
| 계약 재검토 | R6-047(고정 상태 집합) 재검토 필요 | 상태 "교체"가 아니라 "삽입"이라 하위 호환 유지 가능 |
| 요청 유형 분기 | REFERENCE_STYLE·IMAGE_REFERENCE 2종만 `AWAITING_TABLE_BINDING`을 거칠 수 있음 | 문서·MCP Tool description에 예외 규칙 명시 |
| 매칭 정확도 | 오매칭이 매칭 실패보다 위험 | 초기엔 신뢰도 threshold를 보수적으로 높게 설정 |
| 필드 수 불일치 | 후보 수와 실제 컬럼 수가 다를 수 있음 | 기존 REVIEW_REQUIRED로 사람이 채움(R7이 이미 겪는 문제와 동일 패턴, 신규 처리 불필요) |
| UX 왕복 증가 | Preview가 2단계(디자인만 → DB 매핑 후)로 늘어남 | MCP round-trip 증가는 불가피하나, 두 요청 유형에만 국한되어 전체 영향은 제한적 |

---

## 8. 다음 단계

이 문서는 설계 후보이며 코드 변경은 포함하지 않는다. 착수를 승인하면:

1. `AWAITING_TABLE_BINDING` 상태와 전이 규칙을 `FigmaDesignOperationStatus`/
   `FigmaDesignOperationStateService`에 추가(PROP-01)
2. `DesignFieldCandidateExtractor` 구현 및 단위 테스트(PROP-02)
3. `FieldRoleToColumnMatcher` 구현 및 단위 테스트 — 매칭 threshold 값은 실제 eGovFrame 컬럼
   명명 샘플로 튜닝 필요(PROP-03)
4. `bindFigmaDesignRequestTable` MCP Tool + REST 엔드포인트 신설, 기존 인증 연결(PROP-04)
5. 12번 문서 §11.4.1을 정식 `R6-0xx` ID로 재번호하고 완료 Gate 명시

각 단계는 별도 승인 후 순차 진행한다.
