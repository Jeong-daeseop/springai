# 디자인 참조 기반 화면명세서와 DB 매핑 Flow 설계

> **작성일:** 2026-07-17  
> **목적:** 이미지·PDF·JSP·사이트 참조에서 추출한 화면 정보를 실제 DB 테이블·컬럼·JOIN·동작과 연결하는 방법 정의  
> **관련 컴포넌트:** `CrudModelFactory`, `BoardModelFactory`, `FieldModel`, `CrudPromptBuilderTool`, `CrudTemplateRenderer`, `DesignReferenceAnalysisService`  
> **관련 문서:** `local-vision-design-reference-integration-review.md`, `template-registry-role-and-evolution.md`

---

## 1. 결론

화면 시안과 DB 테이블·컬럼은 직접 매핑되지 않는다.

```text
화면 시안
  → “제목·등록일·첨부가 필요하다”는 의미 제공

DB 스키마
  → “NTT_SJ·FRST_REGIST_PNTTM·ATCH_FILE_ID가 있다”는 사실 제공

화면명세서
  → TITLE=NTT_SJ
  → CREATED_AT=FRST_REGIST_PNTTM
  → ATTACHMENT=ATCH_FILE_ID + 파일 상세 조회로 확정
```

따라서 비전 분석 또는 참조 자료 분석과 실제 코드 생성 사이에 **화면명세서 작성·검토·승인 Flow**를 추가하는 것이 맞다.

화면명세서는 단순 설명 문서가 아니라 다음 세 계약을 결합하는 실행 가능한 중간 모델이다.

```text
Visual Contract
  무엇을 어떻게 보여줄 것인가

Data Contract
  어느 테이블·컬럼·JOIN에서 가져올 것인가

Behavior Contract
  검색·등록·수정·삭제·다운로드가 어떻게 동작하는가
```

권장 전체 흐름:

```text
참조 자료 분석
  → DB 스키마·관계·프로그램 메타데이터 조회
  → 화면명세 초안 자동 생성
  → 충돌·미매핑·낮은 신뢰도 검사
  → 필요한 항목만 사용자 확인
  → 승인된 화면명세
  → Registry / ModelFactory / Renderer
  → 코드 생성·검증
```

---

## 2. 현재 생성기의 컬럼 선택 방식

### 2.1 일반 CRUD

현재 `CrudModelFactory`는 DB 컬럼을 `FieldModel`로 변환한 뒤 다음 규칙으로 화면 필드를 선택한다.

- DB PK 컬럼을 화면 PK로 사용
- PK가 없으면 첫 번째 컬럼을 PK로 간주
- 등록·수정 폼에서는 PK와 감사 컬럼 제외
- 목록에서는 하드코딩된 우선 컬럼을 먼저 선택
- 이후 민감 컬럼을 제외하고 최대 6개까지 선택

현재 우선 컬럼 예시:

```java
List<String> preferred = List.of(
    "userNm",
    "emplNo",
    "ofcpsNm",
    "emailAdres",
    "mbtlnum",
    "orgnztId",
    "emplyrSttusCode",
    "brthdy",
    "sexdstnCode"
);
```

이 규칙은 특정 eGovFrame 업무 테이블에는 적합하지만 임의의 업무 테이블이나 화면 시안의 의미를 반영하지 못한다.

### 2.2 Board

`BoardModelFactory`는 게시판 표준 컬럼명을 직접 우선한다.

```java
List<String> preferredList = List.of(
    "noticeAt",
    "nttId",
    "nttSj",
    "ntcrNm",
    "frstRegistPnttm"
);
```

검색 필드도 컬럼명을 기준으로 고정돼 있다.

```java
List<String> preferredSearch = List.of(
    "nttSj",
    "nttCn",
    "ntcrNm"
);
```

이는 Board convention으로는 유효하지만 다음 정보를 표현하지 못한다.

- 화면의 “구분”이 어떤 컬럼인지
- “담당부서”를 어느 테이블에서 가져오는지
- 공통코드 라벨을 어떻게 조회하는지
- 첨부파일 ID를 실제 파일명으로 변환하는 방법
- 목록에서 표시하지 않는 hidden key
- 파생 행 번호와 집계값

### 2.3 FieldModel의 한계

현재 `FieldModel`은 물리 DB 메타데이터를 표현한다.

```java
public record FieldModel(
    String columnName,
    String javaName,
    String javaType,
    String comment,
    boolean pk,
    boolean required,
    boolean stringType,
    Integer maxLength,
    String jdbcType
) {}
```

현재 모델에 없는 정보:

- UI 시맨틱 역할
- 페이지별 표시 여부
- input/select/radio/textarea 등 control 종류
- 검색 연산자
- 정렬 가능 여부
- 공통코드 그룹
- JOIN 대상
- 파생값 계산 방법
- 화면 action과 권한
- 표시 형식

따라서 `FieldModel`만 확장해 모든 정보를 담기보다 물리 컬럼 모델과 화면 바인딩 모델을 분리해야 한다.

---

## 3. 화면 시안에서 추출할 수 있는 정보

비전 모델 또는 시안 분석기는 실제 컬럼명이 아니라 **시맨틱 슬롯**을 추출한다.

예시 화면:

```text
번호 | 구분 | 제목 | 담당부서 | 등록일 | 첨부
```

추출 결과:

```json
{
  "archetype": "BOARD_LIST",
  "fields": [
    {"label": "번호", "role": "ROW_NUMBER"},
    {"label": "구분", "role": "CATEGORY"},
    {"label": "제목", "role": "TITLE"},
    {"label": "담당부서", "role": "DEPARTMENT"},
    {"label": "등록일", "role": "CREATED_AT"},
    {"label": "첨부", "role": "ATTACHMENT"}
  ]
}
```

이 결과만으로는 다음 질문에 답할 수 없다.

```text
TITLE       = NTT_SJ인가, SUBJECT인가?
CATEGORY    = BBS_ID인가, 별도 분류 코드인가?
DEPARTMENT  = 작성자 소속인가, 담당 부서인가?
CREATED_AT  = FRST_REGIST_PNTTM인가, 별도 업무일자인가?
ATTACHMENT  = ATCH_FILE_ID만 필요한가, 파일 상세 JOIN이 필요한가?
```

따라서 비전 분석 결과를 DB 컬럼에 직접 연결하면 안 된다. 비전 결과는 매핑 후보를 생성하는 힌트로만 사용한다.

---

## 4. 테이블 매핑과 컬럼 매핑 분리

### 4.1 1단계 — 화면과 데이터 소스 매핑

먼저 화면이 사용할 주 테이블과 보조 테이블을 결정한다.

```text
화면 유형: BOARD_LIST

주 테이블:
  LETTNBBS

보조 테이블 후보:
  LETTNBBSMASTER
  LETTNBBSUSE
  COMTNFILE
  COMTNFILEDETAIL
```

테이블 선택 우선순위:

1. 사용자가 명시한 테이블
2. 승인된 화면명세의 데이터 소스
3. 기존 JSP/Controller/Mapper의 실제 바인딩
4. `featureType=board` 등 기능 유형
5. LETTNPROGRMLIST 프로그램 정보
6. DB FK와 관계 정보
7. 테이블명 convention 기반 후보

화면 시안은 물리 테이블 정보를 포함하지 않으므로 테이블을 확정하는 근거로 사용하면 안 된다.

### 4.2 2단계 — 시맨틱 역할과 실제 컬럼 매핑

테이블이 결정된 뒤 화면의 필드 역할을 실제 컬럼에 연결한다.

```text
TITLE       → LETTNBBS.NTT_SJ
CONTENT     → LETTNBBS.NTT_CN
AUTHOR      → LETTNBBS.NTCR_NM
CREATED_AT  → LETTNBBS.FRST_REGIST_PNTTM
ATTACHMENT  → LETTNBBS.ATCH_FILE_ID
NOTICE      → LETTNBBS.NOTICE_AT
```

필드 매핑 우선순위:

```text
승인된 화면명세 fieldBindings
  > 기존 JSP/Controller/Mapper의 실제 바인딩
  > 사용자가 명시한 fieldBindings
  > DB 컬럼 코멘트와 화면 라벨 정확 일치
  > 기능 유형별 표준 컬럼명 사전
  > 컬럼명·Java 필드명 convention
  > 데이터 타입과 FK 관계
  > 비전 모델의 시맨틱 역할 후보
  > UNMAPPED
```

프로그램 메타데이터는 화면명·URL·메뉴·업무 문맥을 결정하는 데 유용하지만 일반적으로 개별 화면 필드와 DB 컬럼을 직접 연결하지는 않는다.

---

## 5. 실제 Board 매핑 예시

### 5.1 화면 시안

```text
번호 | 구분 | 제목 | 담당부서 | 등록일 | 첨부
```

### 5.2 DB 스키마

```text
LETTNBBS
  BBS_ID
  NTT_ID
  NTT_SJ
  NTT_CN
  NTCR_NM
  NOTICE_AT
  ATCH_FILE_ID
  FRST_REGIST_PNTTM
  FRST_REGISTER_ID
```

### 5.3 매핑 초안

| 화면 필드 | 매핑 결과 | 소스 유형 | 판단 |
|---|---|---|---|
| 번호 | 페이지 offset + row index | `DERIVED` | DB 컬럼이 아님 |
| 구분 | 미확정 | `UNMAPPED` | `BBS_ID`를 카테고리라고 단정할 수 없음 |
| 제목 | `LETTNBBS.NTT_SJ` | `COLUMN` | Board 표준 convention |
| 담당부서 | 미확정 | `UNMAPPED` 또는 `JOIN_COLUMN` | 주 테이블에 부서 컬럼 없음 |
| 등록일 | `LETTNBBS.FRST_REGIST_PNTTM` | `COLUMN` | 생성일 표준 컬럼 |
| 첨부 | `LETTNBBS.ATCH_FILE_ID` | `COLUMN` | 실제 파일명은 파일 상세 조회 필요 |
| 상세 이동 키 | `BBS_ID + NTT_ID` | `COLUMN` | 화면에는 hidden key |
| 공지 행 강조 | `NOTICE_AT` | `COLUMN` | 출력 컬럼 또는 행 스타일 조건 |

### 5.4 번호

화면의 번호는 반드시 DB 컬럼일 필요가 없다.

```text
totalCount - offset - rowIndex
```

또는 SQL의 `ROW_NUMBER()` 같은 파생값일 수 있다. 화면명세에는 `DERIVED`로 기록한다.

### 5.5 구분

화면의 “구분”은 다음 중 하나일 수 있다.

- 공지/일반
- 정책/보도자료/설명자료
- 업무 카테고리
- 별도 공통코드
- 게시판 마스터
- 고정 표시값

`BBS_ID`는 게시판 자체의 식별자일 수 있으므로 화면 라벨만 보고 카테고리로 자동 확정하면 안 된다.

### 5.6 담당부서

주 테이블에 담당부서 컬럼이 없으면 다음 정책 중 하나를 선택해야 한다.

- `FRST_REGISTER_ID`로 사용자 테이블을 JOIN하고 조직 테이블을 추가 JOIN
- 프로그램 메타데이터의 담당 부서 사용
- 별도 담당부서 컬럼 사용
- 고정 표시값 사용
- 화면에서 제거

이 결정은 시각 분석이 아니라 업무명세에서 확정한다.

### 5.7 첨부파일

`ATCH_FILE_ID`는 화면에 표시할 원본 파일명이 아니다.

```text
LETTNBBS.ATCH_FILE_ID
       ↓
COMTNFILEDETAIL 조회
       ↓
원본 파일명·크기·다운로드 URL
```

따라서 화면명세에는 컬럼뿐 아니라 조회 방식과 다운로드 action이 포함돼야 한다.

---

## 6. 화면 필드 소스 유형

모든 화면 필드를 `table.column`으로만 표현할 수 없으므로 다음 소스 유형이 필요하다.

```java
public enum FieldSourceType {
    COLUMN,
    JOIN_COLUMN,
    DERIVED,
    COMMON_CODE,
    CONSTANT,
    RUNTIME,
    UNMAPPED
}
```

| 유형 | 예 |
|---|---|
| `COLUMN` | `LETTNBBS.NTT_SJ` |
| `JOIN_COLUMN` | 작성자→조직 JOIN으로 가져온 부서명 |
| `DERIVED` | 목록 행 번호, 전체 건수, 상태 라벨 |
| `COMMON_CODE` | `USE_AT=Y`를 “사용”으로 표시 |
| `CONSTANT` | 고정 기관명, 고정 화면 분류 |
| `RUNTIME` | 현재 사용자, 권한, 요청 URL |
| `UNMAPPED` | 화면에는 있으나 데이터 출처 미확정 |

이 구분을 Mapper, Controller, FTL 생성기가 동일하게 사용해야 한다.

---

## 7. 화면명세서의 역할

이미지 시안은 주로 Visual Contract만 제공한다. DB 스키마는 Data Contract 후보만 제공한다. 코드 생성에 필요한 것은 시각·데이터·동작 계약이 합쳐진 결과다.

예를 들어 검색 버튼은 이미지에서 식별할 수 있지만 다음 정보는 이미지로 결정할 수 없다.

- 검색 대상 컬럼
- `LIKE`, `=`, 범위 검색 중 사용할 연산자
- 대소문자 처리
- 공통코드 select 여부
- HTTP method와 URL
- 검색 후 페이지 번호 초기화 여부
- 날짜 범위의 시작·종료 포함 규칙

따라서 화면명세서는 디자인 설명서가 아니라 코드 생성 입력 계약이어야 한다.

---

## 8. 권장 화면명세 구조

화면별로 완전히 독립된 명세를 만들기보다 하나의 업무 프로그램에 하나의 `ScreenSpecification`을 만들고 그 안에 목록·상세·등록·수정 `PageSpec`을 둔다.

```yaml
screen:
  id: bbs-notice
  name: 공지사항
  featureType: board
  archetype: BOARD

dataSources:
  primary:
    schema: com
    table: LETTNBBS
    alias: b

  joins:
    - id: fileDetail
      table: COMTNFILEDETAIL
      alias: f
      type: LEFT
      on:
        - left: b.ATCH_FILE_ID
          right: f.ATCH_FILE_ID

keys:
  - b.BBS_ID
  - b.NTT_ID

pages:
  list:
    template: BOARD_LIST

    fields:
      - id: rowNumber
        label: 번호
        role: ROW_NUMBER
        source:
          type: DERIVED
          expression: PAGE_ROW_NUMBER
        visible: true

      - id: title
        label: 제목
        role: TITLE
        source:
          type: COLUMN
          table: b
          column: NTT_SJ
        visible: true
        link:
          action: VIEW_DETAIL

      - id: createdAt
        label: 등록일
        role: CREATED_AT
        source:
          type: COLUMN
          table: b
          column: FRST_REGIST_PNTTM
        format: yyyy-MM-dd
        visible: true
        sortable: true

      - id: attachment
        label: 첨부
        role: ATTACHMENT
        source:
          type: JOIN_COLUMN
          table: f
          column: ORIGNL_FILE_NM
        visible: true

    search:
      - id: searchTitle
        label: 제목
        source: b.NTT_SJ
        operator: CONTAINS
        control: TEXT

    actions:
      - id: search
        type: SEARCH
        method: GET

      - id: create
        type: CREATE
        method: GET
        permission: BBS_CREATE

  detail:
    template: BOARD_DETAIL

  regist:
    template: BOARD_FORM

  updt:
    template: BOARD_FORM
```

명세는 사람이 읽을 수 있으면서 ModelFactory, Mapper 생성기, Renderer가 직접 소비할 수 있어야 한다.

---

## 9. 화면명세 작성 Flow

화면명세 작성은 참조 분석 다음, 코드 생성 전에 위치한다.

```text
참조 자료 입력
  ├─ 기존 JSP
  ├─ 사이트 HTML
  ├─ 이미지/PDF
  └─ 손그림
          ↓
참조별 정보 추출
  ├─ JSP: EL, form action, 반복문, URL
  ├─ HTML: DOM, label, table, control
  └─ 이미지: archetype, 시맨틱 슬롯, 레이아웃
          ↓
DB 스키마·관계·프로그램 메타데이터 조회
          ↓
ScreenSpecification DRAFT 생성
          ↓
필드·테이블 후보 매핑
          ↓
충돌·미매핑·낮은 신뢰도 검사
          ↓
필요한 항목만 사용자 확인
          ↓
ScreenSpecification APPROVED
          ↓
ModelFactory → Renderer → 코드 생성
          ↓
컴파일·화면·동작 검증
```

### 9.1 기존 JSP 입력

JSP에는 실제 EL과 URL이 있으므로 높은 신뢰도로 매핑할 수 있다.

```jsp
<c:out value="${result.nttSj}"/>
```

추출 결과:

```text
화면 라벨: 제목
model field: result.nttSj
Java field: nttSj
DB 후보: NTT_SJ
```

```jsp
<form action="/bbs/update.do">
```

위 정보에서는 수정 action과 URL을 직접 추출할 수 있다.

### 9.2 사이트 HTML 입력

DOM에서 다음 정보를 추출한다.

- label과 input 연결
- table header와 cell 순서
- form action과 method
- button과 link
- select option
- 반복 DOM 구조
- CSS class와 데이터 속성

사이트 HTML은 실제 프로젝트 DB 컬럼을 알지 못할 수 있으므로 데이터 바인딩은 여전히 화면명세에서 확정한다.

### 9.3 이미지/PDF 입력

이미지는 실제 바인딩이 없으므로 낮은 신뢰도의 시맨틱 후보만 생성한다.

```text
“제목”     → TITLE
“등록일”   → CREATED_AT
“담당부서” → DEPARTMENT
```

그 후 DB 스키마에서 각 역할에 대응하는 후보를 찾는다.

### 9.4 기존 화면명세 입력

사용자가 이미 화면명세를 제공하면 비전 분석을 생략할 수 있다.

```text
사용자 화면명세
  → 스키마 검증
  → 미매핑 검사
  → 승인
  → 코드 생성
```

화면명세는 JSP·사이트·PDF·손그림을 동일한 생성 경로로 정규화하는 중심 산출물이다.

---

## 10. 자동 매핑과 사용자 확인 기준

모든 매핑을 사용자에게 확인받으면 자동화 효과가 떨어진다. 확인은 불확실하거나 영향이 큰 항목에 한정한다.

### 10.1 자동 승인 가능한 항목

- DB가 명시한 PK
- `NTT_SJ → TITLE` 같은 표준 Board convention
- `FRST_REGIST_PNTTM → CREATED_AT`
- DB NOT NULL과 최대 길이
- 감사 컬럼의 폼 제외
- 사용자가 명시한 `fieldBindings`
- 기존 JSP의 직접 EL 바인딩
- 한 개의 후보만 존재하고 타입까지 일치하는 표준 컬럼

### 10.2 사용자 확인이 필요한 항목

- 한 역할에 후보 컬럼이 여러 개
- 화면 필드가 DB에 없음
- 다른 테이블 JOIN 필요
- 공통코드 그룹을 결정해야 함
- 삭제가 물리 삭제인지 논리 삭제인지 불명확
- 등록·수정 권한이 불명확
- 첨부파일 처리 정책이 불명확
- 이미지에서만 발견된 동작
- CRUD와 Board 중 archetype이 모호함
- 고정값, 파생값, runtime 값의 의미가 불명확

### 10.3 자동 생성 중단 조건

- PK를 결정하지 못함
- 필수 TITLE 또는 주요 표시 필드 미매핑
- 필요한 JOIN 조건이 없음
- 등록 폼 필수 필드가 명세에서 누락
- 같은 URL에 서로 다른 action 충돌
- 이미지 추론과 DB 스키마가 모순
- `REVIEW_REQUIRED` 상태의 필수 이슈가 남아 있음

---

## 11. 화면명세 상태와 승인 게이트

화면명세에는 상태가 필요하다.

```text
DRAFT
  자동 추론 결과, 변경 가능

REVIEW_REQUIRED
  미매핑·충돌·낮은 신뢰도 존재

APPROVED
  생성에 사용할 수 있는 승인 명세

SUPERSEDED
  새로운 버전으로 대체됨
```

권장 생성 정책:

```text
APPROVED
  → 정상 생성

DRAFT
  → preview 또는 dry-run만 허용

REVIEW_REQUIRED
  → 코드 저장 차단

SUPERSEDED
  → 최신 버전 안내
```

비전 분석 결과를 바로 코드 생성에 연결하지 않고 승인된 화면명세를 통과시키면 잘못된 컬럼 매핑이 반복 생성되는 것을 막을 수 있다.

---

## 12. 화면명세의 필수 적용 범위

화면명세 단계는 모든 생성에 존재하되 사람이 직접 작성·승인하는 과정은 복잡도에 따라 달라져야 한다.

### 12.1 자동 초안과 자동 승인으로 충분한 경우

- 단일 테이블 CRUD
- 표준 목록·상세·폼
- 명확한 PK
- JOIN 없음
- 공통코드·파일 업로드 없음
- 기본 FTL 사용
- 컬럼 의미가 명확함

이 경우 시스템이 화면명세를 내부적으로 생성하고 검증 후 자동 승인할 수 있다.

### 12.2 명시적 검토가 필요한 경우

- 이미지/PDF 기반 커스텀 화면
- 기존 JSP 마이그레이션
- 다중 테이블 JOIN
- MasterDetail
- 파일 업로드·다운로드
- 공통코드 라벨 변환
- 권한별 버튼 노출
- 파생 컬럼과 집계
- 같은 역할에 후보 컬럼이 여러 개
- 기관별 커스텀 템플릿

즉 화면명세 단계는 항상 존재하지만 사용자 개입은 복잡하거나 불확실한 항목에만 요구한다.

---

## 13. SpringAI 권장 구조

비전 분석 결과와 최종 화면명세를 분리한다.

```text
UiDesignSpec
  시각 분석 결과
  DB 컬럼을 모름

SchemaModel
  물리 DB 구조
  화면 의도를 모름

ProgramMetadata
  화면명·URL·메뉴 정보

          ↓ ScreenSpecAssembler

ScreenSpecification
  시각 + 데이터 + 동작 계약
```

권장 컴포넌트:

```text
DesignReferenceAnalysisService
  → UiDesignSpec

CrudSchemaQueryService
  → SchemaModel

CrudProgramMetadataService
  → ProgramMetadata

ScreenSpecAssembler
  → ScreenSpecification DRAFT

ScreenSpecValidator
  → 충돌·미매핑·호환성 결과

ScreenSpecRepository
  → 승인 명세 저장·버전 관리

CrudModelFactory 계열
  → APPROVED ScreenSpecification을 반영한 TemplateModel
```

가능한 모델 구조:

```java
public record ScreenSpecification(
    String id,
    int version,
    ScreenSpecStatus status,
    String screenName,
    String featureType,
    String archetype,
    List<DataSourceSpec> dataSources,
    List<PageSpec> pages,
    List<SpecIssue> issues
) {}
```

```java
public record ScreenFieldBinding(
    String id,
    String label,
    UiFieldRole role,
    FieldSource source,
    List<PageUsage> usages,
    ControlType control,
    boolean required,
    boolean searchable,
    boolean sortable
) {}
```

기존 `FieldModel`에 이 정보를 전부 추가하기보다 물리 컬럼 모델과 화면 바인딩 모델을 분리하는 것이 적합하다.

---

## 14. 구현 단계 제안

### 1차 — 명세 모델과 자동 초안

- `UiFieldRole`, `FieldSourceType` 정의
- `ScreenSpecification`, `PageSpec`, `ScreenFieldBinding` 정의
- 현재 `CrudModelFactory`와 `BoardModelFactory`의 휴리스틱을 `ScreenSpecAssembler`로 이관 또는 공유
- 단일 테이블 CRUD의 DRAFT 자동 생성
- 미매핑·복수 후보 이슈 기록

완료 기준:

- 현재 기본 CRUD와 Board가 화면명세 초안을 거쳐 동일 결과 생성
- 명세 미사용 기존 경로 회귀 없음

### 2차 — 검증과 승인 상태

- `ScreenSpecValidator` 구현
- DRAFT/REVIEW_REQUIRED/APPROVED/SUPERSEDED 상태
- 필수 매핑, PK, JOIN, URL 충돌 검사
- dry-run과 저장 가능 상태 분리

완료 기준:

- 필수 이슈가 남은 명세는 파일 저장 차단
- 표준 단일 테이블 CRUD는 자동 승인 가능

### 3차 — 참조 자료 연결

- `UiDesignSpec → ScreenSpecification DRAFT` 병합
- 기존 JSP의 EL·form action 추출 결과 병합
- 사용자 명시 `fieldBindings` 적용
- 후보 출처와 confidence 기록

완료 기준:

- 같은 화면 필드에 대해 이미지·JSP·스키마 근거를 구분해 추적
- 이미지 추론이 DB 스키마를 덮어쓰지 않음

### 4차 — 다중 테이블과 기능 계약

- JOIN과 공통코드 바인딩
- 첨부파일 목록·다운로드 계약
- 권한별 action
- 파생값·집계값
- MasterDetail 페이지 계약

완료 기준:

- Mapper·Controller·FTL이 동일한 승인 화면명세를 사용

### 5차 — 저장·버전·재사용

- `ScreenSpecRepository`
- 승인자·승인 시각·변경 이력
- 기존 명세 복제와 버전 갱신
- RAG 기반 유사 화면명세 후보 검색

완료 기준:

- 승인 명세 재사용 가능
- 이전 버전으로 생성된 결과 추적 가능

---

## 15. 테스트 전략

### 단위 테스트

- 시맨틱 역할과 표준 컬럼명 매핑
- DB 컬럼 코멘트·화면 라벨 매칭
- 복수 후보와 미매핑 처리
- PK와 hidden key 처리
- 감사 컬럼 제외
- DERIVED/COMMON_CODE/JOIN_COLUMN 처리
- 상태 전환과 승인 조건

### 통합 테스트

- 단일 테이블 CRUD → 자동 승인 명세 → 기존 생성 결과
- Board → 표준 컬럼 역할 매핑
- 이미지 시안 → UiDesignSpec → DRAFT
- JSP EL → 실제 Java/DB 필드 후보
- 첨부파일 ID → 파일 상세 JOIN 계약
- MasterDetail → 부모·자식 데이터 소스

### 회귀 테스트

- 화면명세 미지정 기존 호출
- 명시 `fieldBindings` 우선순위
- `APPROVED` 명세의 결정론적 재생성
- `REVIEW_REQUIRED` 명세의 파일 저장 차단
- 명세 버전 변경 전후 생성 결과 비교

---

## 16. 최종 권고

화면명세 작성 Flow를 추가하는 것이 맞다. 다만 사용자가 모든 화면명세를 처음부터 수동으로 작성하게 해서는 안 된다.

권장 방식:

1. 시스템이 JSP·사이트·이미지·DB 스키마에서 화면명세 초안을 자동 생성한다.
2. 명확한 표준 매핑은 자동 승인한다.
3. 미매핑·JOIN·공통코드·권한·첨부파일 같은 항목만 사용자에게 확인한다.
4. 승인된 화면명세를 코드 생성의 단일 기준으로 사용한다.
5. 비전 모델이나 템플릿이 바뀌어도 승인 명세 없이 데이터 바인딩을 변경하지 않는다.

최종 구조:

```text
참조 분석
  → 화면명세 DRAFT
  → 필요한 부분만 확인
  → 화면명세 APPROVED
  → Registry / ModelFactory / Renderer
  → 코드 생성
```

화면명세서는 추가적인 산출물 하나가 아니라 비전 분석과 실제 CRUD 생성 사이를 연결하는 핵심 실행 계약으로 보는 것이 적합하다.

