# buildBoardFeature URL·PK·표시정보·CSS 생성기 구현 목록

- 작성일: 2026-07-14
- 상태: 생성기 구현 및 자동화 검증 완료 / 브라우저 런타임 QA 대기
- 기준 문서: [buildBoardFeature URL·PK·표시정보·CSS 생성기 개선 영향평가](./buildBoardFeature_URL_PK_표시정보_CSS_생성기_영향평가.md)
- 대상: `CrudPromptBuilderTool.buildBoardFeature`와 게시판 단일 화면 생성 Tool
- 목표: 공지사항 화면 생성 후 URL·PK·표시정보·CSS를 수동 보정하지 않아도 되는 생성 파이프라인 구축

## 1. 상태 표기

| 표기 | 의미 |
|---|---|
| `[ ]` | 미착수 |
| `[-]` | 진행 중 |
| `[x]` | 완료 및 검증됨 |
| `[!]` | 차단 또는 별도 결정 필요 |

완료 체크는 코드 작성만으로 변경하지 않는다. 각 항목의 테스트 및 완료 조건을 충족한 뒤 `[x]`로 변경한다.

## 2. 구현 원칙

- [x] 기존 canonical URL을 유지한다.
- [x] 기존 DB URL은 Controller alias로 수용한다.
- [x] `LETTNPROGRMLIST`와 `LETTNMENUINFO`는 읽기 전용으로 조회한다.
- [x] 생성기가 프로그램·메뉴 테이블을 UPDATE 또는 INSERT하지 않는다.
- [x] 명시 파라미터가 DB 자동 조회보다 우선한다.
- [x] DB 조회 결과가 여러 개면 임의 선택하지 않는다.
- [x] `defaultBbsId`가 확인된 경우에만 게시판 ID를 자동 주입한다.
- [x] 기존 `styles.css` 전체를 덮어쓰지 않는다.
- [x] CSS 보강은 marker 기반으로 중복 없이 수행한다.
- [x] 기존 `InfoNotice*` / `EgovInfoNotice*` 레이어별 명명 규칙은 이번 구현에서 변경하지 않는다.
- [x] 현재 작업 트리의 미커밋 변경을 보존한다.
- [x] 메타데이터 우선순위는 `명시 파라미터 > DB 자동 조회 > 기존 규칙 fallback`으로 고정한다.
- [x] 신규 파라미터를 사용하지 않는 기존 호출은 100% 호환한다.
- [x] URL alias 충돌 시 자동 선택하지 않고 실패 또는 명시 입력을 요구한다.

## 3. 구현 단계 요약

| 단계 | 작업 묶음 | 선행 조건 | 완료 기준 |
|---|---|---|---|
| 0 | 기존 미커밋 변경 테스트 및 베이스라인 커밋 | 없음 | 기존 변경 단독 테스트·커밋 완료 |
| 1 | 복합 PK 및 null 방어 확장 | 0 | PK 관련 템플릿·회귀 테스트 통과 |
| 2 | 프로그램 메타데이터 모델·URL parser | 0 | 순수 단위 테스트 통과 |
| 3 | DB 프로그램 메타데이터 조회 | 2 | 조회 성공·없음·중복 분기 테스트 통과 |
| 4 | 공개 Tool 및 오케스트레이터 연결 | 2, 3 | 기존 호출 100% 호환 테스트 통과 |
| 5 | 생성 모델·렌더러·URL alias | 2~4 | URL·모델 템플릿 테스트 통과 |
| 6 | 표시정보 View 생성 | 5 | title/H1/caption 검증 통과 |
| 7 | 공통 CRUD 클래스 체계 정의 | 0 | 클래스·토큰 계약 검토 완료 |
| 8 | board 템플릿 전체 CSS 체계 마이그레이션 | 7 | 모든 View 정적 감사 통과 |
| 9 | marker 기반 기존 CSS 보강 | 7, 8 | idempotency 테스트 통과 |
| 10 | 결과 보고 및 검증 강화 | 1~9 | Tool 결과에 연동 상태 표시 |
| 11 | 전체 빌드·생성 프로젝트 검증 | 1~10 | 전체 테스트·bootJar·브라우저 검증 통과 |

## 3.1 선행 게이트 — 기존 변경의 베이스라인 고정

### BASE-001 기존 미커밋 변경 범위 분리

- [x] `git diff -- board/controller.java.ftl`로 상세 PK·조회수 관련 변경을 분리 확인한다.
- [x] `git diff -- crud/layout/gnb-menu-interceptor.java.ftl`로 DB 표시명·브레드크럼 관련 변경을 분리 확인한다.
- [x] 두 파일과 직접 연관된 Service 및 테스트 변경만 베이스라인 범위로 식별한다.
- [x] URL 메타데이터·신규 공통 CSS 체계 변경을 베이스라인에 섞지 않는다.
- [x] 사용자 소유의 다른 미커밋 변경을 포함하지 않는다.

### BASE-002 기존 변경 단독 검증

- [x] `BoardTemplateRendererTest`의 상세 null 방어·조회수 분리 테스트를 실행한다.
- [x] `CrudTemplateRendererTest`의 GNB/LNB/브레드크럼 모델 테스트를 실행한다.
- [x] 관련 `ThymeleafLayoutToolTest`를 실행한다.
- [x] 실패하면 신규 구현을 시작하지 않고 기존 변경부터 안정화한다.

### BASE-003 베이스라인 커밋

- [x] BASE-001 범위만 stage한다.
- [x] stage된 diff를 다시 검토한다.
- [x] 테스트 결과를 커밋 메시지 또는 진행 기록에 남긴다.
- [x] 베이스라인 커밋을 생성한다.
- [x] 커밋 후 신규 구현용 작업 트리 상태를 기록한다.

완료 조건:

- [x] 기존 상세 500 방어와 동적 브레드크럼 변경이 독립 커밋으로 추적된다.
- [x] 이후 URL·메타데이터·CSS 회귀가 베이스라인 변경과 구분된다.

> 실제 커밋은 구현 착수 시 명시적으로 수행한다. 이 문서 작성 단계에서는 실행하지 않는다.

## 4. 1단계 — 프로그램 메타데이터 모델과 URL parser

### BBI-001 `BoardProgramMetadata` 추가

- [x] `src/main/java/com/krdevops/springai/model/board/BoardProgramMetadata.java`를 추가한다.
- [x] 다음 정보를 표현한다.
  - [x] `programFileName`
  - [x] `programStorePath`
  - [x] `programKoreanName`
  - [x] `registeredUrl`
  - [x] `registeredPath`
  - [x] `defaultBbsId`
  - [x] `upperMenuName`
  - [x] 메타데이터 출처: 명시 입력 / DB 조회 / fallback
- [x] null과 blank 정규화 정책을 한 곳에서 적용한다.

완료 조건:

- [x] 명시값, DB 조회값, fallback 값을 구분할 수 있다.
- [x] 모델이 DB 쓰기 상태를 포함하지 않는다.

### BBI-002 `BoardProgramUrlParser` 추가

- [x] `src/main/java/com/krdevops/springai/service/BoardProgramUrlParser.java`를 추가한다.
- [x] 상대 URL에서 path와 query를 분리한다.
- [x] `bbsId` query 값을 URL decode한다.
- [x] query 순서와 무관하게 동일 결과를 반환한다.
- [x] query가 없거나 `bbsId`가 없으면 빈 값으로 처리한다.
- [x] 잘못된 URL은 원문을 포함한 명확한 오류 또는 안전한 미해석 결과로 처리한다.
- [x] query string을 Controller `@RequestMapping` path에 포함하지 않는다.

테스트:

- [x] `/cop/bbs/selectBoardList.do?bbsId=BBSMSTR_AAAAAAAAAAAA`
- [x] query 파라미터 순서 변경
- [x] URL 인코딩된 `bbsId`
- [x] `bbsId` 없음
- [x] query 없음
- [x] 빈 URL
- [x] 잘못된 percent encoding
- [x] 동일 query key 중복

완료 조건:

- [x] URL parser 단위 테스트가 모두 통과한다.

## 5. 2단계 — DB 프로그램 메타데이터 조회

### BBI-003 `BoardProgramMetadataService` 추가

- [x] `src/main/java/com/krdevops/springai/service/BoardProgramMetadataService.java`를 추가한다.
- [x] `JdbcTemplate`을 사용해 프로그램·메뉴 정보를 읽기 전용으로 조회한다.
- [x] 조회 대상 DB 스키마를 `database` 인자로 제한한다.
- [x] 스키마명과 테이블명에 `[A-Za-z0-9_]+` 식별자 검증을 적용한다.
- [x] `LETTNPROGRMLIST` 우선, 필요 시 `COMTNPROGRMLIST` 호환 정책을 적용한다.
- [x] `LETTNMENUINFO` 연결 여부와 상위 메뉴명을 함께 조회한다.
- [x] SQL 값 조건에는 JDBC `?` 바인딩을 사용한다.
- [x] 프로그램·메뉴 테이블에 쓰기 SQL을 추가하지 않는다.

### BBI-004 메타데이터 조회 우선순위 구현

- [x] `programFileName` 정확 일치를 1순위로 처리한다.
- [x] `defaultBbsId`와 URL query의 `bbsId` 일치를 2순위로 처리한다.
- [x] `programKoreanName` 일치를 3순위로 처리한다.
- [x] `domain` 유사어 검색을 마지막 fallback으로 처리한다.
- [x] 명시 파라미터가 확인된 DB 값보다 우선하도록 병합한다.
- [x] 결과가 0개면 fallback 상태를 반환한다.
- [x] 결과가 2개 이상이면 ambiguous 상태를 반환하고 임의 선택하지 않는다.

테스트:

- [x] 정확한 `programFileName` 한 건 조회
- [x] `bbsId`로 한 건 조회
- [x] 한글 프로그램명으로 한 건 조회
- [x] 조회 결과 없음
- [x] 조회 결과 중복
- [x] 프로그램은 있으나 메뉴 연결 없음
- [x] 잘못된 database 식별자 거부
- [x] 프로그램 테이블 없음

완료 조건:

- [x] 조회 성공·없음·중복·미연동 상태가 서로 구분된다.
- [x] 모든 SQL 값 조건이 파라미터 바인딩을 사용한다.

## 6. 3단계 — 공개 Tool과 오케스트레이터 연결

### BBI-005 `buildBoardFeature` 선택 파라미터 추가

- [x] `CrudPromptBuilderTool.buildBoardFeature`에 다음 optional 파라미터를 추가한다.

```java
@Nullable String programFileName,
@Nullable String programUrl,
@Nullable String programKoreanName,
@Nullable String programStorePath,
@Nullable String defaultBbsId
```

- [x] Tool description에 각 파라미터의 의미와 우선순위를 문서화한다.
- [x] 기존 파라미터만 전달한 호출이 계속 동작하도록 한다.
- [x] 신규 파라미터를 내부 옵션 객체로 변환한다.

### BBI-006 단일 게시판 화면 Tool 연결

- [x] `generateBoardList`가 동일한 옵션 해석기를 사용한다.
- [x] `generateBoardDetail`이 동일한 옵션 해석기를 사용한다.
- [x] `generateBoardRegist`가 동일한 옵션 해석기를 사용한다.
- [x] `generateBoardUpdt`가 동일한 옵션 해석기를 사용한다.
- [x] 전체 생성과 단일 화면 생성의 URL·표시명 결과가 같도록 한다.

### BBI-007 `BoardOrchestrationService` 흐름 확장

- [x] 스키마 조회 후 프로그램 메타데이터를 해석한다.
- [x] ambiguous 상태에서는 저장 전에 실패한다.
- [x] 미조회 상태에서는 기존 규칙으로 생성하되 warning을 남긴다.
- [x] URL alias 충돌 검사를 렌더링 전에 수행한다.
- [x] CSS 보강은 Thymeleaf 생성에만 실행한다.
- [x] CSS 보강 실패를 생성 결과에 포함한다.

테스트:

- [x] 기존 Tool 호출이 기존 기본값으로 동작
- [x] 명시 파라미터가 서비스까지 전달
- [x] DB 자동 조회 결과가 모델 팩토리로 전달
- [x] ambiguous 결과에서 파일 저장이 실행되지 않음
- [x] JSP 생성에서는 CSS 보강이 실행되지 않음

완료 조건:

- [x] 공개 Tool 하위 호환 테스트가 통과한다.
- [x] 전체 생성과 단일 화면 생성이 같은 메타데이터 정책을 사용한다.

## 7. 4단계 — 생성 모델과 렌더러 확장

### BBI-008 표시정보 모델 추가

- [x] `BoardDisplayModel` record를 추가한다.
- [x] `programFileName`, `displayName`, `upperMenuName`을 포함한다.
- [x] `programKoreanName`이 있으면 `displayName`으로 사용한다.
- [x] 없으면 기존 `domainKr`로 fallback한다.

### BBI-009 route 모델 추가

- [x] `BoardRouteModel` record를 추가한다.
- [x] 다음 값을 포함한다.
  - [x] `canonicalPrefix`
  - [x] `registeredListUrl`
  - [x] `registeredListPath`
  - [x] `defaultBbsId`
- [x] 필요하면 상세·등록·수정 별도 alias로 확장할 수 있는 구조로 만든다.

### BBI-010 `BoardTemplateModel` 확장

- [x] `BoardDisplayModel`을 포함한다.
- [x] `BoardRouteModel`을 포함한다.
- [x] 기존 `domainKr`과 `urlPrefix`는 호환을 위해 당분간 유지한다.
- [x] record 생성자 변경에 따른 모든 테스트 fixture를 갱신한다.

### BBI-011 `BoardModelFactory` 확장

- [x] 스키마 모델과 프로그램 메타데이터를 함께 입력받는다.
- [x] 명시값 → DB 값 → 기존 추출값 순서로 표시명을 결정한다.
- [x] canonical URL은 기존 규칙을 유지한다.
- [x] DB URL path를 alias 후보로 저장한다.
- [x] 확인된 경우에만 `defaultBbsId`를 저장한다.

### BBI-012 `BoardTemplateRenderer` 확장

- [x] display 모델을 FreeMarker 데이터 모델에 넣는다.
- [x] route 모델을 FreeMarker 데이터 모델에 넣는다.
- [x] 기존 템플릿 key를 제거하지 않는다.
- [x] null metadata에서도 렌더링되는지 확인한다.

완료 조건:

- [x] 기존 모델 fallback 렌더링 테스트가 통과한다.
- [x] DB 메타데이터 적용 렌더링 테스트가 통과한다.

## 8. 5단계 — URL·bbsId·PK Controller 생성

### BBI-013 목록 URL alias 생성

- [x] `board/controller.java.ftl`에서 canonical 목록 URL을 유지한다.
- [x] DB URL path가 canonical path와 다를 때만 alias를 추가한다.
- [x] DB URL query string은 `@RequestMapping`에 포함하지 않는다.
- [x] alias가 없으면 기존 단일 `@RequestMapping` 형식을 유지한다.

예상 결과:

```java
@RequestMapping({
    "/cop/bbs/infoNoticeList.do",
    "/cop/bbs/selectBoardList.do"
})
```

### BBI-014 URL alias 충돌 검사

- [x] 대상 `outputPath/src/main/java`에서 alias path 사용 여부를 검색한다.
- [x] 생성 대상 Controller 자신에 있는 동일 매핑은 허용한다.
- [x] 다른 Controller의 동일 매핑은 충돌로 처리한다.
- [x] 충돌 시 ambiguous mapping 위험을 포함한 오류를 반환한다.
- [x] 충돌 상태에서는 해당 Controller를 저장하지 않는다.

### BBI-015 기본 `bbsId` 적용

- [x] 목록 진입 시 요청 `bbsId`가 없으면 확인된 `defaultBbsId`를 적용한다.
- [x] 요청에 `bbsId`가 있으면 요청값을 우선한다.
- [x] 둘 다 없으면 DB 조회 전에 안내 분기한다.
- [x] 임의의 게시판 ID를 코드에 하드코딩하지 않는다.
- [x] `index.jsp` forward에 확인된 `bbsId`를 포함한다.

### BBI-016 복합 PK 공통 검사 생성

- [x] `BBS_ID + NTT_ID`를 함께 검사하는 Controller helper를 생성한다.
- [x] String PK에는 null과 blank 검사를 적용한다.
- [x] 숫자 PK에는 null 검사를 적용한다.
- [x] 오류 메시지와 목록 복귀 경로를 일관되게 생성한다.

### BBI-017 PK 및 조회 결과 방어 적용

- [x] 상세 화면 진입
- [x] 수정 화면 진입
- [x] 수정 처리
- [x] 삭제 처리
- [x] 조회수 증가 전
- [x] 첨부파일 상세 forward
- [x] 상세 조회 결과 null
- [x] 수정 조회 결과 null

완료 조건:

- [x] PK 없는 상세 요청이 상세 View로 진입하지 않는다.
- [x] 존재하지 않는 PK가 500 오류를 발생시키지 않는다.
- [x] 조회수는 정상 게시물을 찾은 뒤에만 증가한다.
- [x] Mapper의 update/delete가 유효 PK 없이 호출되지 않는다.

## 9. 6단계 — 표시정보 View 생성

### BBI-018 `displayName` 적용

- [x] 목록 `<title>`에 `displayName + 목록`을 사용한다.
- [x] 상세 `<title>`에 `displayName + 상세`를 사용한다.
- [x] 등록 `<title>`에 `displayName + 등록`을 사용한다.
- [x] 수정 `<title>`에 `displayName + 수정`을 사용한다.
- [x] 각 화면 H1에 `displayName`을 사용한다.
- [x] table caption에 `displayName`을 사용한다.
- [x] fallback 시 기존 `domainKr`가 출력된다.

### BBI-019 레이아웃 모델 계약 유지

- [x] Controller는 `currentPageSuffix`만 설정한다.
- [x] Controller가 `lnbTitle`을 직접 설정하지 않는다.
- [x] Controller가 `breadcrumbs`를 직접 설정하지 않는다.
- [x] 목록 suffix는 `목록`이다.
- [x] 상세 suffix는 `상세`이다.
- [x] 등록 suffix는 `등록`이다.
- [x] 수정 suffix는 `수정`이다.
- [x] 동일한 `bbsId`가 모든 화면에서 인터셉터까지 전달된다.

### BBI-020 브레드크럼 회귀 검증

- [x] DB 프로그램 표시명이 `공지사항`이면 LNB 제목이 `공지사항`인지 확인한다.
- [x] 상위 메뉴명이 `알림정보`이면 목록 브레드크럼이 `홈 > 알림정보 > 공지사항 목록`인지 확인한다.
- [x] 상세가 `공지사항 상세`인지 확인한다.
- [x] 등록이 `공지사항 등록`인지 확인한다.
- [x] 수정이 `공지사항 수정`인지 확인한다.

완료 조건:

- [x] 테이블명 `LETTNBBS`가 LNB 제목과 페이지 제목에 노출되지 않는다.
- [x] `소식·뉴스` 같은 fallback 고정값이 정상 DB 연동 화면에 노출되지 않는다.

## 10. 7~9단계 — 공통 CRUD 클래스 체계 정의와 board 전체 마이그레이션

이 단계는 기존 CSS에 선택자 몇 개를 추가하는 작업이 아니다. 공통 클래스의 책임과 토큰 계약을 먼저 정의한 뒤 board의 목록·상세·등록·수정 템플릿 전체를 새 계약으로 마이그레이션하고, 마지막으로 기존 프로젝트 CSS를 marker 기반으로 보강한다.

실행 순서는 `BBI-023 → BBI-021/BBI-022 → BBI-024 → BBI-025`로 고정한다.

### BBI-021 게시판 View 크기 modifier 적용

- [x] 모든 `.krds-input`에 `medium`, `small`, `large` 중 하나가 있다.
- [x] 모든 `.krds-form-select`에 크기 modifier가 있다.
- [x] 모든 `.krds-btn`에 크기 modifier가 있다.
- [x] textarea에 `medium`과 `egov-textarea`가 함께 적용된다.

### BBI-022 CRUD 공통 클래스 적용

- [x] 화면 content 루트에 `egov-crud-page`를 적용한다.
- [x] 검색 form에 `egov-search-form`을 적용한다.
- [x] 입력 요소에 `egov-control`을 적용한다.
- [x] 버튼에 `egov-btn`을 적용한다.
- [x] 목록 table에 `egov-list-table`을 적용한다.
- [x] 등록·수정 table에 `egov-form-table`을 적용한다.
- [x] 페이지네이션에 `egov-pagination`을 적용한다.
- [x] layout 적용형과 standalone형 템플릿 모두 같은 클래스 계약을 사용한다.
- [x] 목록·상세·등록·수정 body 변형 전체를 마이그레이션한다.

### BBI-023 `styles.css.tpl` 기준 CSS 추가

- [x] 각 공통 클래스의 책임 범위를 문서화한다.
- [x] 공통 클래스 간 중복 선언과 우선순위 규칙을 정의한다.
- [x] 기존 `egov-*` 클래스 중 유지·통합·폐기 대상을 분류한다.
- [x] `--egov-screen-*` 화면 토큰을 정의한다.
- [x] `.egov-crud-page` 화면 스코프를 정의한다.
- [x] CRUD 공통 클래스의 font-size, min-height, padding, line-height를 정의한다.
- [x] `.krds-input` 선택자에 textarea 높이 토큰을 검토·적용한다.
- [x] 기존 버튼·입력·셀렉트 높이 토큰을 보존한다.
- [x] 기존 레거시 페이지네이션 정렬·크기 보정을 보존한다.

### BBI-024 `KrdsStylesConfigurer` 추가

- [x] WAR CSS 경로를 탐지한다.
  - [x] `src/main/webapp/resources/css/styles.css`
- [x] Boot CSS 경로를 탐지한다.
  - [x] `src/main/resources/static/resources/css/styles.css`
- [x] marker 블록을 사용해 필요한 CSS만 추가한다.
- [x] 동일 marker가 있으면 중복 추가하지 않는다.
- [x] `ThymeleafLayoutTool.patchContextCommonXml()`의 문자열 존재 확인 후 skip 패턴을 재사용한다.
- [x] `ThymeleafLayoutTool.patchServletContextXml()`의 보존/patch/실패 결과 보고 방식을 재사용한다.
- [x] 기존 사용자 CSS를 전체 교체하지 않는다.
- [x] CSS가 없을 때 생성 또는 실패 정책을 명확히 적용한다.
- [x] 적용 결과를 `보강`, `보존`, `미발견`, `실패`로 반환한다.

### BBI-025 CSS 감사 및 정적 검증

- [x] 생성 HTML에서 사용한 KRDS 클래스 목록을 수집한다.
- [x] size modifier가 없는 input/select/button을 실패 처리한다.
- [x] textarea 토큰 또는 명시적 예외 근거를 확인한다.
- [x] 레거시 페이지네이션 대응 CSS가 있는지 확인한다.
- [x] `egov-*` 텍스트/링크 클래스의 font-size 누락을 확인한다.
- [x] CSS 보강을 업무 생성 파일 12개 집계와 분리한다.

완료 조건:

- [x] 공통 클래스 정의 문서 또는 코드 주석이 존재한다.
- [x] board Thymeleaf 목록·상세·등록·수정 전체가 새 클래스 체계를 사용한다.
- [x] 이전 클래스와 새 클래스의 부분 혼용으로 화면별 크기가 달라지지 않는다.
- [x] CSS 보강을 두 번 실행해도 파일 내용이 한 번만 추가된다.
- [x] 기존 사용자 CSS 앞부분과 사용자 정의 규칙이 보존된다.
- [x] KRDS token audit가 종료 코드 0을 반환하거나 승인된 ignore 근거가 기록된다.
- [ ] 실제 브라우저에서 input/select/button 높이가 일관된다.
- [ ] 페이지 번호와 이동 링크가 같은 수직 기준으로 정렬된다.

## 11. 8단계 — 생성 결과와 검증 결과 강화

### BBI-026 `BoardOrchestrationResult` 확장

- [x] `menuIntegrationStatus`를 추가한다.
- [x] `resolvedProgramName`을 추가한다.
- [x] `resolvedProgramUrl`을 추가한다.
- [x] `resolvedBbsId`를 추가한다.
- [x] `cssStatus`를 추가한다.
- [x] `warnings`를 추가한다.
- [x] `notFound` factory와 기존 생성 지점을 갱신한다.

### BBI-027 Tool 결과 포맷 확장

- [x] GNB/LNB 연동 상태를 출력한다.
- [x] 최종 프로그램 표시명을 출력한다.
- [x] 적용된 DB URL과 canonical URL을 출력한다.
- [x] 적용된 기본 `bbsId`를 출력한다.
- [x] 복합 PK 방어 상태를 출력한다.
- [x] CSS 보강 상태를 출력한다.
- [x] fallback 또는 미연동 warning을 출력한다.

예상 출력:

```text
GNB/LNB 연동: DB URL 유지 + Controller alias
프로그램 표시명: 공지사항
등록 URL: /cop/bbs/selectBoardList.do?bbsId=BBSMSTR_AAAAAAAAAAAA
Canonical URL: /cop/bbs/infoNoticeList.do
기본 bbsId: BBSMSTR_AAAAAAAAAAAA
PK 방어: BBS_ID + NTT_ID 적용
CSS: 기존 styles.css 보강 완료
```

### BBI-028 생성 후 코드 검증 강화

- [x] 생성 Controller의 alias와 DB URL path 일치를 확인한다.
- [x] 모든 목록·상세·등록·수정 링크의 `bbsId` 전달을 확인한다.
- [x] 상세·수정 PK 방어 코드를 확인한다.
- [x] Controller model attribute와 View 참조 이름을 비교한다.
- [x] Mapper XML의 `${}` SQL 바인딩 사용 여부를 검사한다.
- [x] FreeMarker 문법이 최종 산출물에 남지 않았는지 확인한다.
- [x] Thymeleaf layout과 fragment 경로 존재 여부를 확인한다.
- [x] 생성 Controller 패키지가 `servlet-context.xml` component-scan에 포함됐는지 확인한다.
- [x] 생성형 `nttId`에 입력 필수 검증이 붙지 않았는지 확인한다.
- [x] 숫자형 `nttId` 채번 쿼리와 `FOR UPDATE` 잠금이 있는지 확인한다.
- [x] 등록·수정·삭제 CSRF 조건부 hidden input을 확인한다.

완료 조건:

- [x] 생성 결과만 보고 메뉴 연동·PK·CSS 상태를 판단할 수 있다.

## 12. 9단계 — 테스트 및 최종 검증

### BBI-029 단위 테스트 목록

- [x] `BoardProgramUrlParserTest`
- [x] `BoardProgramMetadataServiceTest`
- [x] `BoardModelFactoryTest`
- [x] `BoardTemplateRendererTest`
- [x] `BoardOrchestrationServiceTest`
- [x] `BoardGeneratedCodeAuditorTest`
- [x] `CrudPromptBuilderToolTest`
- [x] `KrdsStylesConfigurerTest`
- [x] `ThymeleafRuntimeConfigurerTest`
- [x] `ThymeleafLayoutToolTest`
- [x] Project Initializr CSS template 관련 테스트

### BBI-030 회귀 테스트 조건

- [x] Thymeleaf reuse 생성 파일 수가 12개로 유지된다.
- [x] Thymeleaf create 생성 파일 수가 17개로 유지된다.
- [x] JSP 생성 파일 수가 12개로 유지된다.
- [x] CSS 보강은 위 파일 수에 포함되지 않는다.
- [x] 기존 metadata 없는 호출도 렌더링된다.
- [x] 기존 `InfoNotice*` / `EgovInfoNotice*` 명명이 변하지 않는다.
- [x] 조회수 select와 update 트랜잭션 분리가 유지된다.
- [x] GNB 인터셉터의 `bbsId` 문맥 매칭이 유지된다.
- [x] 생성형 `nttId`는 등록 검증 전에 Service가 할당한다.
- [x] 숫자형 `nttId`는 UUID 문자열 변환이 아닌 잠금 채번 쿼리를 사용한다.

### BBI-031 빌드 검증

```bash
./gradlew test
./gradlew bootJar
```

- [x] 전체 테스트 성공
- [x] `bootJar` 성공
- [x] 컴파일 warning과 신규 정적 분석 오류 검토

### BBI-032 생성 프로젝트 검증

- [x] 테스트용 eGovFrame 프로젝트를 새로 초기화한다.
- [x] `generateThymeleafLayout`을 실행한다.
- [x] 개선된 `buildBoardFeature`를 실행한다.
- [x] 생성 결과에 프로그램명·DB URL·bbsId·CSS 상태가 표시되는지 확인한다.
- [x] 생성 프로젝트 `./gradlew test` 또는 `./gradlew war`를 실행한다.

### BBI-033 런타임·브라우저 시나리오 검증

- [x] DB 메뉴의 공지사항 링크로 목록 진입
- [x] canonical 목록 URL로 진입
- [x] `bbsId` 없는 목록 URL 처리
- [x] `nttId` 없는 상세 URL 처리
- [x] 존재하지 않는 `nttId` 상세 URL 처리
- [x] 정상 상세 조회와 조회수 증가
- [x] 목록 → 상세 → 목록에서 `bbsId` 유지
- [x] 상세 → 수정 → 상세 또는 목록에서 `bbsId` 유지
- [x] 등록 후 목록 복귀
- [x] 삭제 후 목록 복귀 및 `USE_AT=N` 확인
- [x] LNB 제목 `공지사항`
- [x] 브레드크럼 `홈 > 알림정보 > 공지사항 목록`
- [x] 상세·등록·수정 suffix 표시
- [ ] input/select/button/textarea 크기 일치
- [ ] 페이지네이션 수직 정렬

완료 조건:

- [x] 최신 생성 산출물의 주요 서버 시나리오에서 4xx/5xx가 발생하지 않는다.
- [ ] 생성 후 URL·PK·표시정보·CSS 수동 수정이 필요하지 않다.

## 13. 파일 변경 예상 목록

### 13.1 신규 파일

- [x] `src/main/java/com/krdevops/springai/model/board/BoardProgramMetadata.java`
- [x] `src/main/java/com/krdevops/springai/model/board/BoardDisplayModel.java`
- [x] `src/main/java/com/krdevops/springai/model/board/BoardRouteModel.java`
- [x] `src/main/java/com/krdevops/springai/service/BoardProgramUrlParser.java`
- [x] `src/main/java/com/krdevops/springai/service/BoardProgramMetadataService.java`
- [x] `src/main/java/com/krdevops/springai/service/KrdsStylesConfigurer.java`
- [x] 위 신규 클래스별 테스트 파일

### 13.2 수정 파일

- [x] `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java`
- [x] `src/main/java/com/krdevops/springai/model/board/BoardTemplateModel.java`
- [x] `src/main/java/com/krdevops/springai/service/BoardModelFactory.java`
- [x] `src/main/java/com/krdevops/springai/service/BoardTemplateRenderer.java`
- [x] `src/main/java/com/krdevops/springai/service/BoardOrchestrationService.java`
- [x] `src/main/java/com/krdevops/springai/service/BoardOrchestrationResult.java`
- [x] `src/main/resources/templates/board/controller.java.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-list.html.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-list-body.html.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-detail.html.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-detail-body.html.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-regist.html.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-regist-body.html.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-updt.html.ftl`
- [x] `src/main/resources/templates/board/thymeleaf-updt-body.html.ftl`
- [x] `src/main/resources/templates/egov/styles.css.tpl`
- [x] 관련 기존 테스트 파일

### 13.3 직접 변경하지 않을 파일

- [x] `BoardLayerDefinition.java`의 레이어별 `Egov` 명명 규칙
- [x] 기존 DB의 `LETTNPROGRMLIST` 데이터
- [x] 기존 DB의 `LETTNMENUINFO` 데이터
- [x] 생성 대상 프로젝트의 사용자 정의 CSS 전체

## 14. 결정 필요 항목

다음 항목은 구현 전에 정책을 확정하거나 보수적인 기본값을 적용한다.

### DEC-001 프로그램 테이블명

- [ ] 기본 `LETTNPROGRMLIST`만 지원
- [x] `LETTNPROGRMLIST` 우선 + `COMTNPROGRMLIST` fallback

권장: 두 번째 방식. 단, 테이블 존재 여부를 먼저 조회한다.

### DEC-002 CSS 파일 미존재 처리

- [ ] 생성 실패 후 `initializeProject` 안내
- [x] `styles.css.tpl`로 신규 생성

권장: Thymeleaf layout이 존재하고 CSS만 없으면 신규 생성, 정적 리소스 구조 자체가 없으면 실패한다.

### DEC-003 URL alias 충돌 처리

- [ ] alias 생략 후 warning
- [x] 전체 생성 실패

권장: ApplicationContext 시작 실패를 방지하기 위해 전체 생성 실패로 처리한다.

### DEC-004 프로그램 조회 결과 중복

- [ ] 첫 번째 결과 사용
- [x] 명시 파라미터 요구

권장: 명시 파라미터 요구. 첫 번째 결과 자동 선택은 금지한다.

## 15. 구현 제외 범위

- [x] 모든 클래스를 `EgovInfoNotice*`로 개명하는 작업
- [x] 기존 생성 프로젝트의 클래스·파일 자동 rename
- [x] `LETTNPROGRMLIST` UPDATE 또는 INSERT
- [x] `LETTNMENUINFO` UPDATE 또는 INSERT
- [x] 사용자 CSS 전체 재생성
- [x] 일반 CRUD와 master-detail 생성기의 같은 기능 동시 확장
- [x] 첨부파일 실제 스트리밍 구현

## 16. 완료 정의

다음 조건을 모두 만족해야 구현 완료로 판정한다.

- [x] 기존 `buildBoardFeature` 호출이 계속 동작한다.
- [x] DB 프로그램 URL로 생성 Controller에 진입할 수 있다.
- [x] canonical URL도 계속 동작한다.
- [x] `bbsId`가 최초 진입부터 모든 CRUD 흐름에서 유지된다.
- [x] `bbsId` 또는 `nttId`가 없는 상세 요청이 500을 발생시키지 않는다.
- [x] 존재하지 않는 게시물 상세·수정 요청이 500을 발생시키지 않는다.
- [x] DB 표시명과 상위 메뉴명이 LNB/브레드크럼에 반영된다.
- [x] 목록 브레드크럼이 `홈 > 알림정보 > 공지사항 목록`으로 표시된다.
- [ ] KRDS input/select/button/textarea 크기가 브라우저에서 일관된다.
- [ ] 페이지네이션 정렬과 글자 크기가 브라우저에서 정상이다.
- [x] 기존 CSS가 보존되고 보강 블록이 중복되지 않는다.
- [x] 생성 결과에 URL·메뉴 연동·PK·CSS 상태가 출력된다.
- [x] `./gradlew test`가 성공한다.
- [x] `./gradlew bootJar`가 성공한다.
- [x] 신규 생성 프로젝트 빌드가 성공한다.
- [ ] 브라우저 검증 후 생성 결과에 수동 URL·PK·표시정보·CSS 수정이 필요하지 않음을 확정한다.

## 17. 진행 기록

| 일자 | 작업 ID | 상태 | 내용 | 검증 |
|---|---|---|---|---|
| 2026-07-14 | 문서 작성 | 완료 | 구현 목록 최초 작성 | 문서 구조 확인 |
| 2026-07-14 | 재평가 반영 | 완료 | 위험도 상향, 베이스라인 게이트, PK 선행, CSS 전체 마이그레이션 반영 | 영향평가와 구현 목록 대조 |
| 2026-07-15 | BASE-001~003 | 완료 | 상세 PK·조회수 및 GNB 메타데이터 변경을 `b885e60`으로 분리 커밋 | 관련 테스트 3종 성공 |
| 2026-07-15 | BBI-001~030 | 완료 | LETTN/COMTN 테이블 해석, 프로그램 URL·표시명·bbsId, alias 충돌, 복합 PK, CRUD CSS, 생성 감사 구현 | 전체 `./gradlew test` 및 신규 단위 테스트 성공 |
| 2026-07-15 | BBI-031 | 완료 | 서버 JAR 빌드 검증 | `./gradlew bootJar` 성공 |
| 2026-07-15 | BBI-032 | 완료 | 실제 ebt DB로 신규 프로젝트 초기화→layout→게시판 12개 생성 | 신규 프로젝트 `war` 성공 |
| 2026-07-15 | CSS 멱등성 | 완료 | 실제 생성기를 같은 프로젝트에 2회 실행 | 1회 `PATCHED`, 2회 `PRESERVED` |
| 2026-07-15 | BBI-033 | 부분 완료 | WAR를 Tomcat 8082에 배포해 URL·PK 방어·표시정보·조회수·등록·수정·논리삭제 검증 | 서버 기능 통과, 계산 스타일·스크린샷 대기 |
| 2026-07-15 | GAP-005 | 완료 | 생성 Controller package를 servlet component-scan에 멱등 추가 | DB URL·canonical URL HTTP 200 |
| 2026-07-15 | GAP-006 | 완료 | 생성형 `nttId` 검증 제외, 숫자형 PK를 `MAX+1 FOR UPDATE`로 채번 | `NTT_ID=11` 등록→수정→`USE_AT=N` 삭제 통과 |
