# featureType: board 전용 생성 기능 영향검토

## 1. 검토 목적

`CrudPromptBuilderTool`은 현재 DB 테이블 하나를 기준으로 eGovFrame CRUD 소스를 생성한다.

예를 들어 `COMTNBBS`를 요청하면 게시판 업무 기능이 아니라 `COMTNBBS` 단일 테이블에 대한 기본 CRUD가 생성된다.

`featureType: board`는 이 한계를 보완하기 위해 단일 CRUD가 아닌 게시판 업무 기능 단위로 소스를 생성하기 위한 전용 옵션이다.

## 2. 현재 구조

현재 CRUD 생성 흐름은 다음과 같다.

```text
CrudPromptBuilderTool
 └─ llmProvider=auto
    └─ CrudOrchestrationService
       ├─ CrudSchemaQueryService
       ├─ CrudModelFactory
       ├─ CrudTemplateRenderer
       └─ templates/crud/*.ftl
```

현재 구조의 특징은 다음과 같다.

- `tableName` 하나만 기준으로 스키마를 조회한다.
- 대표 PK 하나를 기준으로 상세, 수정, 삭제 URL을 만든다.
- 생성 대상은 VO, Mapper, Service, Controller, 화면 파일이다.
- 화면은 `viewType`에 따라 JSP 또는 Thymeleaf로 생성할 수 있다.
- 게시판 마스터, 권한, 첨부파일, 답글, 조회수 증가 같은 게시판 업무 규칙은 별도 처리하지 않는다.

## 3. featureType: board 목표 구조

`featureType: board`를 추가하면 기존 CRUD 흐름과 분리된 게시판 전용 생성 흐름이 필요하다.

```text
CrudPromptBuilderTool
 ├─ featureType 미입력 또는 crud
 │  └─ CrudOrchestrationService
 │
 └─ featureType=board
    └─ BoardOrchestrationService
       ├─ BoardSchemaService
       ├─ BoardModelFactory
       ├─ BoardTemplateRenderer
       └─ templates/board/*.ftl
```

기존 `CrudOrchestrationService`에 게시판 로직을 직접 넣는 방식은 권장하지 않는다. 게시판은 단일 테이블 CRUD보다 관계 테이블과 업무 규칙이 많아서 기존 CRUD 생성 경로를 복잡하게 만들 위험이 크다.

## 4. 입력 파라미터 설계

권장 입력 형태는 다음과 같다.

```text
database: com
featureType: board
mainTable: COMTNBBS
masterTable: COMTNBBSMASTER
useTable: COMTNBBSUSE
fileTable: COMTNFILE
fileDetailTable: COMTNFILEDETAIL
domain: Bbs
packageName: egovframework.let.bbs
outputPath: /Users/jeongdaeseob/workspace-egov/egov-sample
llmProvider: auto
egovVersion: 5.0
viewType: thymeleaf
```

파라미터 의미는 다음과 같다.

| 파라미터 | 설명 |
|---|---|
| `featureType` | 생성 기능 유형. `crud`, `board` |
| `mainTable` | 게시글 본문 테이블. 기본값 후보: `COMTNBBS` |
| `masterTable` | 게시판 마스터 테이블. 기본값 후보: `COMTNBBSMASTER` |
| `useTable` | 게시판 사용/권한 테이블. 기본값 후보: `COMTNBBSUSE` |
| `fileTable` | 첨부파일 묶음 테이블. 기본값 후보: `COMTNFILE` |
| `fileDetailTable` | 첨부파일 상세 테이블. 기본값 후보: `COMTNFILEDETAIL` |
| `domain` | 생성 도메인명. 예: `Bbs` |
| `packageName` | Java 패키지명. 예: `egovframework.let.bbs` |
| `viewType` | 화면 생성 방식. `jsp` 또는 `thymeleaf` |

## 5. 생성 대상 범위

게시판 전용 생성은 단일 CRUD 11개 파일보다 생성 범위가 넓다.

예상 생성 파일은 다음과 같다.

```text
src/main/java/egovframework/let/bbs/service/BbsVO.java
src/main/java/egovframework/let/bbs/service/BbsSearchVO.java
src/main/java/egovframework/let/bbs/service/BbsFileVO.java
src/main/java/egovframework/let/bbs/service/BbsMasterVO.java
src/main/java/egovframework/let/bbs/service/BbsService.java
src/main/java/egovframework/let/bbs/service/impl/BbsMapper.java
src/main/java/egovframework/let/bbs/service/impl/EgovBbsServiceImpl.java
src/main/java/egovframework/let/bbs/web/EgovBbsController.java
src/main/java/egovframework/let/bbs/web/EgovBbsValidationHandler.java
src/main/resources/egovframework/mapper/bbs/BbsMapper.xml
```

`viewType: jsp`일 경우:

```text
src/main/webapp/WEB-INF/jsp/bbs/EgovBbsList.jsp
src/main/webapp/WEB-INF/jsp/bbs/EgovBbsDetail.jsp
src/main/webapp/WEB-INF/jsp/bbs/EgovBbsRegist.jsp
src/main/webapp/WEB-INF/jsp/bbs/EgovBbsUpdt.jsp
```

`viewType: thymeleaf`일 경우:

```text
src/main/resources/templates/bbs/EgovBbsList.html
src/main/resources/templates/bbs/EgovBbsDetail.html
src/main/resources/templates/bbs/EgovBbsRegist.html
src/main/resources/templates/bbs/EgovBbsUpdt.html
```

첨부파일 기능을 분리할 경우 다음 파일도 추가될 수 있다.

```text
src/main/java/egovframework/let/bbs/web/EgovBbsFileController.java
src/main/java/egovframework/let/bbs/service/BbsFileService.java
src/main/java/egovframework/let/bbs/service/impl/EgovBbsFileServiceImpl.java
```

## 6. 게시판 업무 기능 범위

`featureType: board`가 목표로 하는 게시판 기능은 다음이다.

- 게시글 목록 조회
- 게시글 상세 조회
- 게시글 등록
- 게시글 수정
- 게시글 삭제
- 상세 조회 시 조회수 증가
- `BBS_ID`, `NTT_ID` 기준 게시글 식별
- 게시판 마스터 정보 조회
- 게시판 사용/권한 정보 조회
- 첨부파일 목록 조회
- 첨부파일 다운로드
- 첨부파일 삭제
- 제목, 내용, 작성자 검색
- 공지글 우선 노출
- 답글 정렬 지원

다만 1차 구현에서는 모든 기능을 한 번에 넣기보다 범위를 제한하는 것이 안전하다.

## 7. 주요 영향 영역

### 7.1 Tool 입력 시그니처

`CrudPromptBuilderTool.buildFullCrudPrompt()`에 `featureType`을 추가하거나, 별도 메서드를 추가해야 한다.

선택지는 두 가지다.

```text
안 A: buildFullCrudPrompt(..., featureType, ...)
안 B: buildBoardPrompt(...) 또는 buildBoardFeature(...) 신규 추가
```

권장안은 안 B다. 기존 CRUD Tool의 파라미터가 이미 많고, 게시판은 입력 테이블도 여러 개라 별도 메서드가 더 명확하다.

### 7.2 Orchestration Service

기존 `CrudOrchestrationService`를 수정해 게시판을 분기할 수는 있지만 권장하지 않는다.

권장 구조:

```text
CrudOrchestrationService   일반 CRUD 전용
BoardOrchestrationService  게시판 기능 전용
```

이렇게 분리해야 일반 CRUD 회귀 위험을 줄일 수 있다.

### 7.3 Schema 조회

현재 `CrudSchemaQueryService`는 테이블 하나의 컬럼 목록만 조회한다.

게시판 기능에는 다음 조회가 추가로 필요하다.

- 여러 테이블의 컬럼 조회
- PK/FK 또는 암묵적 관계 조회
- `BBS_ID`, `NTT_ID`, `ATCH_FILE_ID` 존재 여부 검증
- 첨부파일 테이블 존재 여부 검증
- 게시판 마스터 테이블 존재 여부 검증

신규 `BoardSchemaService`를 두는 것이 적절하다.

### 7.4 모델 구조

현재 `CrudTemplateModel`은 단일 대표 PK 중심이다.

게시판은 다음 모델이 필요하다.

```text
BoardTemplateModel
 ├─ boardTable
 ├─ masterTable
 ├─ useTable
 ├─ fileTable
 ├─ fileDetailTable
 ├─ bbsIdField
 ├─ nttIdField
 ├─ atchFileIdField
 ├─ listFields
 ├─ searchFields
 └─ viewType
```

`CrudTemplateModel`을 억지로 확장하면 일반 CRUD 모델이 게시판 전용 필드로 오염된다.

### 7.5 템플릿

현재 템플릿은 `templates/crud/*.ftl`에 있다.

게시판 전용 템플릿은 별도 디렉터리를 권장한다.

```text
src/main/resources/templates/board/vo.java.ftl
src/main/resources/templates/board/search-vo.java.ftl
src/main/resources/templates/board/mapper.java.ftl
src/main/resources/templates/board/mapper.xml.ftl
src/main/resources/templates/board/service.java.ftl
src/main/resources/templates/board/service-impl.java.ftl
src/main/resources/templates/board/controller.java.ftl
src/main/resources/templates/board/jsp-list.jsp.ftl
src/main/resources/templates/board/jsp-detail.jsp.ftl
src/main/resources/templates/board/jsp-regist.jsp.ftl
src/main/resources/templates/board/jsp-updt.jsp.ftl
src/main/resources/templates/board/thymeleaf-list.html.ftl
src/main/resources/templates/board/thymeleaf-detail.html.ftl
src/main/resources/templates/board/thymeleaf-regist.html.ftl
src/main/resources/templates/board/thymeleaf-updt.html.ftl
```

### 7.6 SQL

단일 CRUD SQL은 단순하다.

```sql
SELECT ...
FROM COMTNBBS
WHERE ...
ORDER BY ...
```

게시판 SQL은 다음 요소가 필요하다.

```sql
SELECT b.*,
       m.BBS_NM
FROM COMTNBBS b
LEFT JOIN COMTNBBSMASTER m
       ON b.BBS_ID = m.BBS_ID
WHERE b.BBS_ID = #{bbsId}
  AND b.USE_AT = 'Y'
ORDER BY b.NOTICE_AT DESC,
         b.SORT_ORDR DESC,
         b.NTT_ID DESC
```

또한 상세 조회 시 조회수 증가 SQL이 필요하다.

```sql
UPDATE COMTNBBS
   SET RDCNT = COALESCE(RDCNT, 0) + 1
 WHERE BBS_ID = #{bbsId}
   AND NTT_ID = #{nttId}
```

첨부파일을 지원하면 `COMTNFILE`, `COMTNFILEDETAIL` 조회 SQL도 추가된다.

### 7.7 Controller

기존 CRUD Controller는 단순 목록/상세/등록/수정/삭제만 처리한다.

게시판 Controller는 다음 요청을 처리해야 한다.

```text
/bbs/bbsList.do
/bbs/bbsDetail.do
/bbs/bbsRegistView.do
/bbs/bbsRegist.do
/bbs/bbsUpdtView.do
/bbs/bbsUpdt.do
/bbs/bbsDelete.do
/bbs/bbsFileDownload.do
/bbs/bbsFileDelete.do
```

첨부파일 업로드를 포함하면 `MultipartFile` 처리와 multipart resolver 설정도 검토해야 한다.

## 8. 주요 리스크

### 8.1 복합 키 처리

`COMTNBBS`는 일반적인 단일 PK CRUD처럼 다루기 어렵다.

`BBS_ID`, `NTT_ID`를 함께 넘기는 구조가 필요하다. 현재 `CrudTemplateModel`은 대표 PK 하나만 들고 있으므로 게시판에는 별도 모델이 필요하다.

### 8.2 첨부파일 처리

첨부파일은 단순 DB CRUD가 아니다.

- 파일 저장 경로
- 파일 업로드 multipart 설정
- 다운로드 응답 헤더
- 파일 삭제 정책
- DB 메타데이터 정합성

이 영역은 환경 의존성이 크므로 1차 구현에서는 “첨부파일 메타데이터 조회/다운로드 URL 생성” 수준으로 제한하는 것이 안전하다.

### 8.3 권한 처리

`COMTNBBSUSE`만으로 권한 처리를 끝낼지, Spring Security 인증 사용자와 연동할지 결정해야 한다.

초기 구현은 다음 정도가 안전하다.

```text
게시판 사용 여부 조회
로그인 사용자 연동은 TODO 또는 확장 포인트로 분리
```

### 8.4 eGovFrame 버전 차이

eGovFrame 5.0은 Jakarta 계열이고, 4.3은 Javax 계열이다.

Validation import, Servlet API, Multipart 관련 설정이 버전에 따라 달라질 수 있다. 기존 `egovVersion` 분기 정책을 재사용해야 한다.

### 8.5 viewType과 런타임 설정

`viewType: thymeleaf`는 HTML 파일만 생성해서 끝나지 않는다.

대상 프로젝트에 다음 설정이 필요하다.

- `thymeleaf-spring6` 의존성
- `ThymeleafViewResolver`
- 기존 JSP ViewResolver와 order 충돌 방지

현재 CRUD Thymeleaf 생성 기능에서 일부 보강 로직이 들어갔으므로 게시판 생성기에서도 재사용하거나 공통화해야 한다.

## 9. 단계별 구현안

### 1단계: 라우팅과 요청 모델

- `FeatureType` 또는 `GenerationFeatureType` enum 추가
- `featureType=crud` 기본값 유지
- `featureType=board` 분기 추가
- 기존 CRUD 동작 회귀 방지 테스트 추가

### 2단계: Board 기본 생성

- `BoardOrchestrationService` 추가
- `BoardSchemaService` 추가
- `BoardTemplateModel` 추가
- 기본 게시글 목록/상세/등록/수정/삭제 생성
- `BBS_ID + NTT_ID` 기준 처리
- 조회수 증가 처리

### 3단계: Master/Use 테이블 연동

- `COMTNBBSMASTER` 조회
- `COMTNBBSUSE` 조회
- 게시판명, 사용 여부, 권한 관련 필드 화면 반영

### 4단계: 첨부파일 1차 연동

- `ATCH_FILE_ID` 기준 파일 목록 조회
- 다운로드 URL 생성
- 상세 화면 첨부파일 목록 표시

### 5단계: 첨부파일 업로드/삭제

- multipart 설정 검토
- 등록/수정 화면 파일 업로드 처리
- 삭제 처리 정책 확정

### 6단계: 게시판 고도화

- 공지글 상단 노출
- 답글 정렬
- 제목/내용/작성자 검색
- 권한 체크 고도화

## 10. 테스트 영향

신규 테스트가 필요하다.

```text
BoardSchemaServiceTest
BoardModelFactoryTest
BoardTemplateRendererTest
BoardOrchestrationServiceTest
BoardTemplateIntegrationTest
```

기존 테스트에서 확인해야 할 항목:

- `featureType` 미입력 시 기존 CRUD 동작 유지
- `viewType=jsp` 기존 JSP 생성 유지
- `viewType=thymeleaf` 기존 Thymeleaf 생성 유지
- `featureType=board`가 기존 `CrudLayerDefinition`을 깨지 않음

## 11. 문서 영향

다음 문서 갱신이 필요하다.

```text
docs/tool-reference/CrudPromptBuilderTool_기능및역할_상세설명.md
docs/crud/buildFullCrudPrompt_사용가이드.md
docs/tool-reference/MCP_Tool_전체목록.md
docs/tool-catalog.md
```

## 12. 권장 결론

`featureType: board`는 구현 가치가 있지만 기존 CRUD 생성기에 직접 끼워 넣으면 복잡도가 빠르게 커진다.

권장 방향은 다음과 같다.

```text
기존 CRUD 경로 유지
게시판은 BoardOrchestrationService로 분리
템플릿도 templates/board로 분리
1차 구현은 목록/상세/등록/수정/삭제 + 조회수 증가까지 제한
첨부파일 업로드/권한/답글은 단계적으로 확장
```

이 구조가 일반 CRUD 생성 안정성을 유지하면서 게시판 기능을 업무 단위 생성기로 확장하는 가장 안전한 방향이다.
