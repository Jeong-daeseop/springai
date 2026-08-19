# REFERENCE_STYLE/IMAGE_REFERENCE DB 바인딩 절충안 검토 — 세션 요약

> 작성일: 2026-08-18
> 성격: 이 대화 세션의 분석 흐름을 정리한 요약 문서(번호 미부여, `docs/figma/` 번호 문서 체계 밖).
> 관련 문서:
> - [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md) — 미구현 현황 원본, §11.4.1(절충안 반영 위치)
> - [22_Reference_Image_Request_DB_Binding_Deferral_Proposal.md](./22_Reference_Image_Request_DB_Binding_Deferral_Proposal.md) — 절충안 상세 제안서

---

## 1. 세션 목적

12번 문서(`Semantic Figma Design System 구현 목록`, v4.11 기준)를 읽고 미구현 항목을 정리하는
것에서 시작해, 7가지 디자인 요청(REFERENCE_STYLE/IMAGE_REFERENCE 등)의 DB 연결 방식에 대한
과거 결정을 회고하고, 그 결정이 남긴 한계를 보완하는 절충 설계를 구체화해 문서화했다.

---

## 2. 12번 문서 미구현 현황 정리 (요약)

전체 969줄 체크리스트(당시 v4.11) 완독 결과, 완전 미구현은 1건뿐이었다.

| 구분 | 건수 | 대표 항목 |
|---|---|---|
| 완전 미구현 `[ ]` | 1건 | **DEC-08**(플러그인 배포 방식) — 조직 IT 정책 결정이라 코드로 대신 불가 |
| 부분구현 `[~]` | 21건 | 아래 카테고리 참고 |

부분구현 21건은 성격별로 다음과 같이 묶인다.

- **(A) Figma Desktop/브라우저 런타임 필요**: R5-T03, R6-060/T18 등 — 코드 편집 환경에서 구현 불가
- **(B) 픽셀/이미지 비교 미구현**: R7-015/016/T04, R0-028/029 — 구조 비교만 완료, 시각 비교는 남음
- **(C) 검사 대상 미정**: R6-063/T16 — "승인 Token 밖 값 하드코딩 검사" 대상 표면 자체가 현재 템플릿에 없음
- **(D) 설계 결정/죽은 코드 처리 미결**: **R6-030**(FigmaDesignRequestRouter 삭제 여부), **R6-032**(자연어→DB 테이블 자동 매핑 의도적 범위 밖), R5-043(Plugin UX 편의 기능만 잔여)
- **(E) 아키텍처 확장 선행 필요**: R6-T08(page 소속 검증 — 요청 계약에 page 필드 자체 없음)
- **(F) E2E/운영 검증 잔여**: R6-T04, R8-023, R7-T01/002 등

상세 목록과 근거는 12번 문서 본문을 참고(이 세션에서 별도로 요약 답변으로만 제공, 문서 자체는
수정하지 않음).

---

## 3. 회고: "DB 스키마 기반 vs interface" 질문

사용자가 "앞에서 DB 스키마 기반으로 할지 interface로 할지 물어본 것 같은데 기억나"라고 질문해,
`session_search`로 과거 세션(2026-08-17, `7afc9575-...`)을 조회해 확인했다.

### 3.1 당시 발견한 문제

R6-032~038(7가지 요청 중 6개) 구현 중 근본적인 모델 불일치를 발견했다.

- `analyzeFigmaReference`/`analyzeDesignReference` → `createScreenSpecification` 경로는
  **DB 테이블 스키마 바인딩 필수**(`database`/`tableName`).
- `FigmaDesignRequest`는 자연어 prompt만 가지고 있어 DB 테이블 개념이 없었다.

### 3.2 제시했던 3가지 선택지

| 옵션 | 내용 |
|---|---|
| (A) DB 테이블 바인딩으로 통일 | 기존 CRUD 생성 경로와 동일하게 6개 요청 모두 DB 테이블에 바인딩(계약 확장) |
| (B) DB 없는 새 경로 구축 | `UiDesignSpec`/컴포넌트 목록만으로 `ScreenSpecification` 생성(신규 아키텍처, 최대 작업량) |
| 기술적으로 가능한 부분만 우선 구현(권장) | DB 바인딩 없이도 닫히는 부분만(COMPONENT_SPECIFIED/PLATFORM_CONVERT/MULTI_SCREEN_FLOW 구조), 나머지는 `[~]` 유지 |

### 3.3 결과

사용자가 **(A) DB 테이블 바인딩으로 통일**을 선택했고, `FigmaDesignRequest`/`FigmaScreenRequest`에
`database`/`tableName`/`screenName`/`featureType`/`screenSpecificationId` 필드가 신설되어
R6-033~038이 이 방향으로 실제 구현·완료됐다(12번 문서 §11.4 참고). `create_design_from_text`
(R6-032)만 "자연어→DB 테이블 자동 매핑"이 의도적으로 범위 밖으로 남았다.

---

## 4. (A) vs (B) 장단점 비교

### (A) DB 테이블 바인딩으로 통일 — 실제 채택

**장점**: 기존 CRUD 파이프라인 재사용, 실행 가능한 코드(Controller/Service/DAO/VO)로 이어짐,
`schemaBindings` 물리 COLUMN 원칙과 일치(필드 무결성 보장), 보안/거버넌스 정책 자동 적용,
구현 공수·회귀 위험 최소.

**단점**: 디자인 우선 워크플로우 미지원(테이블을 먼저 알아야 함), 자연어→테이블 자동 추론 없음,
그린필드 프로토타입 불가, MODIFY_EXISTING이 "세밀 편집"이 아닌 "재동기화"로 의미 축소,
REFERENCE_STYLE은 참조 화면과 최종 필드가 달라질 수 있음.

### (B) DB 없는 새 경로 구축

**장점**: 순수 디자인 우선 워크플로우 지원, 자연어/이미지/참조 화면 의도를 그대로 반영,
MODIFY_EXISTING을 진짜 "자유 텍스트 → 구조화 diff"로 구현할 여지, 순수 디자인 데모 용도에 적합.

**단점**: 전면 신규 아키텍처(작업량 최대), 실행 가능 코드와 단절(반쪽 산출물 위험), 필드
존재성·타입 검증 없음, 보안 정책 중복 구현 필요, eGovFrame 4계층 생성 원칙과 이탈, 현재
코드베이스에 기반 자체가 없음.

---

## 5. 절충안 개요

(A)의 무결성·실행 가능성은 유지하면서 (B)의 "디자인 우선 체감"을 REFERENCE_STYLE·
IMAGE_REFERENCE 2종에만 국소적으로 적용하는 3단계 흐름을 설계했다.

```
1단계(ANALYZED)   DB 없이 UiDesignSpec만으로 필드 역할 후보 Preview(AWAITING_TABLE_BINDING)
2단계(DB 매핑)     사람이 Preview를 보고 database/tableName 지정 → 역할↔컬럼 자동 매칭 시도
3단계(APPROVED)    매칭 확정 → 기존 CRUD 엔진((A) 경로)이 그대로 이어받아 생성
```

기존 R7(`.figpack` 하이브리드 흐름)의 "후보 → Preview/수정 → 승인" 패턴과 유사하지만, R7은
후보 생성 시점에 이미 `database`/`tableName`을 요구하는 반면, 이 절충안은 그 시점을 한 단계
뒤로 미룬다는 점이 다르다.

**장점**: (A) 핵심 가치 유지, (B) 핵심 가치 일부 확보, R6-032 문제를 LLM 추론이 아닌 사람의
Preview 확인으로 우회, 기존 R7 인프라 재사용 가능.

**단점**: 상태 모델 확장 필요, Preview 2회로 UX 왕복 증가, 신규 역할↔컬럼 매칭 로직 필요,
필드 개수 불일치 시 재작업, 구현 비용은 (A)와 (B) 사이의 "중간".

구현 비용 순서: **(A) 순수 채택 < 절충안 < (B) 완전 신규 구축**.

---

## 6. REFERENCE_STYLE/IMAGE_REFERENCE 절충안 구체화

절충안을 이 2개 요청 유형에 한정해 구체화한 상세 설계는 별도 제안서로 분리했다.

→ [22_Reference_Image_Request_DB_Binding_Deferral_Proposal.md](./22_Reference_Image_Request_DB_Binding_Deferral_Proposal.md)

핵심 요약:

- **적용 범위**: REFERENCE_STYLE·IMAGE_REFERENCE만(나머지 5종은 이미 테이블/기존 명세/Registry
  기반이라 불필요)
- **신규 상태**: `AWAITING_TABLE_BINDING`(`ANALYZED`와 `PREVIEW_READY` 사이 삽입, R6-047 계약과
  하위 호환)
- **신규 구성요소 4개(PROP-01~04)**: 상태 모델 확장 / `DesignFieldCandidateExtractor` /
  `FieldRoleToColumnMatcher` / `bindFigmaDesignRequestTable` MCP Tool
- **재사용 자산**: `analyzeFigmaReference`/`analyzeDesignReference`,
  `SchemaReaderTool.getTableSchema()`, `createScreenSpecification`의 REVIEW_REQUIRED 경로,
  `generateBundle` — 전부 변경 없이 그대로 사용
- **주요 리스크**: 계약 재검토 필요, 매칭 정확도(오매칭이 매칭 실패보다 위험 — 보수적 threshold
  권장), 필드 수 불일치 시 REVIEW_REQUIRED로 흡수

---

## 7. 12번 문서 반영 내역

이 절충안은 12번 문서에도 요약 반영했다(별도 승인 후 §11.4.1을 정식 `R6-0xx`로 재번호 예정).

- **버전**: 4.11 → 4.12
- **§11.4.1 신설**(§11.4 오케스트레이션 섹션 직후): "후속 제안(미착수) — REFERENCE_STYLE/
  IMAGE_REFERENCE DB 테이블 바인딩 지연 절충안" — 상태를 "제안 — 구현 착수 전 사용자 승인
  필요"로 명시해 기존 확정 항목(R6-xxx)과 구분
- **§19 변경 이력**에 v4.12 항목 추가

이 단계까지는 **문서 반영만 수행했고 실제 코드 변경은 없다.**

---

## 8. 결론 및 다음 단계

- 12번 문서의 실질적 미구현 잔여는 DEC-08(조직 배포 정책) 하나이며, 나머지는 대부분 런타임
  QA·픽셀 비교·검사 대상 미정·조직 결정 등 "코드 작업 이상의 것"이 남은 상태였다.
- REFERENCE_STYLE/IMAGE_REFERENCE의 DB 바인딩 순서 문제는 (A)를 유지하면서 절충 설계로
  국소 보완이 가능하다는 결론에 도달했고, 상세 설계를 22번 문서로 분리해 남겼다.
- **다음 단계는 사용자 승인 대기**: 22번 문서 §8(다음 단계)의 PROP-01~04 순차 구현 여부를
  결정하면 그때 코드 작업을 시작한다.
