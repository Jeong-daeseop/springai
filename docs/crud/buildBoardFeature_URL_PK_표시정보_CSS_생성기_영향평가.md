# buildBoardFeature URL·PK·표시정보·CSS 생성기 개선 영향평가

- 작성일: 2026-07-14
- 대상: `CrudPromptBuilderTool.buildBoardFeature` 및 게시판 Thymeleaf 생성 파이프라인
- 상태: 구현 전 영향 검토
- 변경 원칙: 기존 DB 데이터는 수정하지 않고, 생성기가 기존 프로그램 메타데이터를 읽어 생성 결과에 반영한다.

## 1. 검토 배경

현재 게시판 생성기는 다음 항목을 생성 후 수동으로 보정해야 하는 경우가 있다.

1. `LETTNPROGRMLIST.URL`과 생성 Controller URL 불일치
2. `bbsId + nttId` 복합 PK 방어 부족
3. 테이블명 기반 화면 표시명과 DB 프로그램 표시명 불일치
4. KRDS 입력·셀렉트·textarea·페이지네이션 크기 토큰 및 공통 CRUD 클래스 부족

반복적인 후처리를 없애려면 FreeMarker 화면 템플릿만 수정해서는 충분하지 않다. 공개 MCP Tool 파라미터, DB 프로그램 메타데이터 조회, 생성 모델, Controller/View, 공통 CSS, 생성 결과 검증을 하나의 흐름으로 연결해야 한다.

## 2. 결론

구현 위험도는 **높음**이다. 최초 검토의 `중간 이상` 판정은 개별 기능의 난이도에는 맞지만 전체 변경 범위를 과소평가했다. 특히 CSS는 기존 토큰 몇 개를 보강하는 작업이 아니라 **공통 CRUD 클래스 체계를 새로 정의하고 board Thymeleaf 템플릿 전체를 그 체계로 마이그레이션하는 작업**이다. 여기에 공개 MCP Tool schema, DB 메타데이터 조회, URL 충돌 방어, 기존 미커밋 템플릿 변경까지 동시에 영향을 받는다.

다음 설계 판단은 유지한다.

- 기존 canonical URL은 유지한다.
- 기존 DB URL path는 Controller alias로 추가한다.
- 공개 Tool에는 프로그램 메타데이터 선택 파라미터를 추가한다.
- 파라미터가 없으면 `LETTNPROGRMLIST`와 `LETTNMENUINFO`를 읽기 전용으로 조회한다.
- 조회 결과가 없으면 기존 규칙으로 fallback하고 메뉴 미연동 경고를 반환한다.
- 기존 `styles.css`는 덮어쓰지 않고 marker 기반으로 필요한 블록만 보강한다.
- DB의 프로그램·메뉴 URL을 UPDATE하거나 신규 행을 INSERT하지 않는다.

확정 우선순위는 다음과 같다.

```text
명시 파라미터 > DB 자동 조회 > 기존 규칙 fallback
```

이 우선순위는 신규 기능을 사용하지 않는 기존 호출을 100% 호환시키기 위한 핵심 계약이다.

권장 처리 흐름은 다음과 같다.

```text
buildBoardFeature 입력
        ↓
명시 테이블명 또는 LETTN/COMTN 테이블군 해석
        ↓
프로그램 메타데이터 해석
(URL, bbsId, 표시명, 프로그램명)
        ↓
LETTNBBSMASTER/COMTNBBSMASTER에서 게시판 존재 검증
        ↓
LETTNBBS/COMTNBBS 스키마와 프로그램 메타데이터 결합
        ↓
BoardTemplateModel
  ├─ displayName
  ├─ defaultBbsId
  └─ routes / alias
        ↓
Controller + View 생성
        ↓
공통 CRUD CSS 체계 적용 및 생성 결과 검증
```

## 3. 공개 MCP Tool 파라미터 영향

현재 `buildBoardFeature`에는 기존 프로그램 URL·표시명·고정 `bbsId`를 전달할 파라미터가 없다.

다음 선택 파라미터 추가가 적절하다.

```java
@Nullable String programFileName,
@Nullable String programUrl,
@Nullable String programKoreanName,
@Nullable String programStorePath,
@Nullable String defaultBbsId
```

### 3.1 호환성 원칙

- 기존 호출은 변경 없이 허용한다.
- 명시한 값이 있으면 DB 자동 조회 결과보다 우선한다.
- 명시값이 없으면 프로그램 테이블에서 자동 조회한다.
- 조회 결과가 없으면 기존 `domain`·`packageName` 기반 규칙으로 fallback한다.
- fallback한 경우 결과에 `GNB/LNB 미연동` 경고를 포함한다.
- 조회 결과가 여러 개이면 임의 선택하지 않는다. 프로그램명이나 `bbsId`를 명시하도록 안내한다.

`buildBoardFeature`만 변경하면 단일 화면 생성 Tool은 이전 동작을 유지하게 된다. 다음 Tool도 동일한 메타데이터 해석기를 사용해야 한다.

- `generateBoardList`
- `generateBoardDetail`
- `generateBoardRegist`
- `generateBoardUpdt`

선택 파라미터 추가는 기존 MCP 호출과 호환되지만, 실행 중인 MCP 서버와 클라이언트 Tool schema cache에는 재시작이 필요하다.

## 4. 프로그램 메타데이터 조회 영향

`BoardSchemaService`는 게시판 관련 테이블 컬럼을 조회하는 책임만 유지한다. 프로그램·메뉴 조회는 별도 서비스로 분리하는 것이 적절하다.

### 4.1 권장 신규 구성

```text
BoardProgramMetadataService
BoardProgramMetadata
BoardProgramUrlParser
```

예상 메타데이터 모델은 다음과 같다.

```java
record BoardProgramMetadata(
    String programFileName,
    String programStorePath,
    String programKoreanName,
    String registeredUrl,
    String registeredPath,
    String defaultBbsId,
    String upperMenuName
) {}
```

### 4.2 조회 우선순위

1. 명시된 `programFileName` 정확 일치
2. 명시된 `defaultBbsId`와 DB URL query의 `bbsId` 일치
3. 명시된 `programKoreanName` 정확 또는 부분 일치
4. `domain` 유사어 검색은 최후 fallback

### 4.3 DB 조회 시 주의사항

- `database` 인자가 현재 DataSource 기본 스키마와 다를 수 있다.
- 스키마명과 테이블명은 JDBC 바인딩 파라미터로 처리할 수 없으므로 영문·숫자·밑줄만 허용하는 식별자 검증이 필요하다.
- 프로젝트에 따라 `LETTNPROGRMLIST` 또는 `COMTNPROGRMLIST`를 사용할 수 있다.
- URL query 순서와 URL 인코딩을 단순 문자열 비교로 처리하지 않는다.
- 프로그램 등록은 있지만 `LETTNMENUINFO` 연결이 없을 수 있다. 이 경우 `프로그램 등록됨 / GNB 미연동`으로 구분해야 한다.
- 자동 조회는 SELECT 전용이며 DB UPDATE/INSERT를 수행하지 않는다.

### 4.4 LETTN/COMTN 게시판 테이블군 해석

프로그램 테이블만 `LETTNPROGRMLIST`/`COMTNPROGRMLIST` fallback을 지원하고 게시판 테이블은
`COMTNBBS*` 기본값으로 고정하면, `ebt`처럼 실제 게시판 테이블이 `LETTNBBS*`인 스키마에서
프로그램 메타데이터를 찾기 전에 필수 테이블 없음으로 종료된다. 게시판 테이블군에도 동일한
존재 확인 정책이 필요하다.

우선순위는 다음과 같이 고정한다.

```text
명시적으로 전달된 테이블명
  > 현재 database에 존재하는 LETTN 게시판 테이블군
  > 기존 COMTN 기본값
```

같은 실행에서 `LETTNBBS`와 `COMTNBBSMASTER`를 임의로 혼합하지 않는다. main/master/use/option
테이블은 한 계열로 확정하고, 파일 테이블은 프로젝트에 실제 존재하는 별도 계열을 탐지한다.

```text
LETTN 계열
  LETTNBBS
  LETTNBBSMASTER
  LETTNBBSUSE
  LETTNBBSMASTEROPTN

COMTN 계열
  COMTNBBS
  COMTNBBSMASTER
  COMTNBBSUSE
  COMTNBBSMASTEROPTN
```

기존 호출의 기본값을 즉시 제거하는 변경은 하위 호환성을 깨뜨린다. Tool 표면의 기존 optional
파라미터는 유지하고, 내부 `BoardTableSetResolver` 또는 동등한 서비스가 실제 테이블 존재 여부를
판정하도록 분리하는 것이 안전하다.

### 4.5 게시판 관련 테이블별 책임

프로그램 메타데이터와 게시판 데이터는 목적이 다르므로 다음 책임을 혼합하지 않는다.

| 테이블 | 생성기에서의 책임 |
|---|---|
| `LETTNPROGRMLIST` | 프로그램 파일명, 표시명, 등록 URL, URL query의 기본 `bbsId` 후보 |
| `LETTNMENUINFO` | GNB/LNB 연결, 상위 메뉴명, 브레드크럼 문맥 |
| `LETTNBBSMASTER` | 게시판 존재 여부, 게시판명과 기본 설정의 검증 기준 |
| `LETTNBBS` | 게시물 컬럼 스키마와 생성된 소스의 실행 시 CRUD 대상 |
| `LETTNBBSUSE` | 대상별 게시판 사용 여부. 실제 PK는 `(BBS_ID, TRGET_ID)` |
| `LETTNBBSMASTEROPTN` | 댓글·답변 및 만족도 같은 선택 기능 활성화 여부 |

최소 생성 계약은 프로그램/메뉴 메타데이터, 게시판 마스터, 게시물 테이블이다. `LETTNBBSUSE`는
사용 가능 여부를 검사할 때, `LETTNBBSMASTEROPTN`은 해당 부가기능을 실제 화면에 생성할 때만
적용한다. 옵션 행이 없다는 이유로 기본 목록·상세·등록·수정 생성을 실패시키지 않는다.

`LETTNBBSUSE`는 `BBS_ID` 단일키가 아니다. 생성 Mapper가 다음처럼 `BBS_ID`만으로 단건 문자열을
조회하면 대상이 추가됐을 때 다중 행 오류가 날 수 있다.

```sql
SELECT USE_AT FROM LETTNBBSUSE WHERE BBS_ID = #{bbsId}
```

사용 여부 검사가 필요하면 `TRGET_ID`까지 조건에 포함하거나 `EXISTS` 기반으로 생성해야 한다.
시스템 기본 게시판의 기본 대상은 실제 데이터에서 확인된 경우에만 사용하며 임의 문자열을
하드코딩하지 않는다.

### 4.6 URL bbsId의 게시판 마스터 검증과 빈 게시판 허용

`LETTNPROGRMLIST.URL`에서 추출한 `defaultBbsId`는 문자열 파싱 성공만으로 확정하지 않는다.
동일 database의 게시판 마스터에서 실제 존재 여부를 교차검증해야 한다.

```sql
SELECT BBS_ID, BBS_NM, USE_AT
FROM LETTNBBSMASTER
WHERE BBS_ID = ?
```

검증 기준은 `LETTNBBS` 게시물 존재 여부가 아니라 `LETTNBBSMASTER` 행 존재 여부다. 게시판은
정상 등록됐지만 게시물이 0건일 수 있기 때문이다.

```text
마스터 있음 + 게시물 0건  → 정상적인 빈 게시판, 생성 계속
마스터 있음 + 게시물 있음 → 정상 게시판, 생성 계속
마스터 없음                → 잘못된 defaultBbsId, 저장 전 실패 또는 명시 입력 요구
```

이 검증이 없으면 오타가 포함된 URL의 `bbsId`를 Controller 상수와 Mapper 조건으로 굳힐 수 있고,
반대로 `LETTNBBS`에서 게시물 존재만 확인하면 신규/빈 게시판을 존재하지 않는 것으로 오판한다.

### 4.7 LETTNBBS의 생성 시점·실행 시점 역할과 nttId 출처

생성 시점에는 `LETTNBBS`의 기존 게시물 내용을 HTML에 정적으로 넣지 않는다. 컬럼 메타데이터를
읽어 VO 필드, 복합 PK, Mapper SQL, 목록/상세/폼 필드를 결정한다. 실행 시점에는 생성된 Mapper가
`LETTNBBS`를 실제 CRUD 대상으로 사용한다.

```text
생성 시점: INFORMATION_SCHEMA의 LETTNBBS 컬럼 → 소스 구조 생성
실행 시점: 생성된 Mapper → LETTNBBS 게시물 조회·등록·수정·삭제
```

`nttId`는 프로그램 메타데이터가 아니므로 `LETTNPROGRMLIST`에서 가져오거나 기존 게시물의 첫 번째
값을 기본값으로 선택하지 않는다.

| 화면/처리 | `bbsId` 출처 | `nttId` 출처 |
|---|---|---|
| 목록 | 요청값 또는 검증된 `defaultBbsId` | 사용하지 않음 |
| 등록 화면 | 요청값 또는 검증된 `defaultBbsId` | 사용하지 않음 |
| 등록 처리 | 요청값 또는 검증된 `defaultBbsId` | Service의 ID 생성기 |
| 상세 | 요청값 또는 검증된 `defaultBbsId` | 목록에서 선택한 `LETTNBBS.NTT_ID` |
| 수정·삭제 | 상세/폼에서 유지된 값 | 상세/폼에서 유지된 값 |

### 4.8 PROGRM_STRE_PATH와 신규 패키지 결정

기존 프로그램 등록이 있고 재사용할 레이어가 없는 경우 `PROGRM_STRE_PATH`는 URL 정보뿐 아니라
신규 Java 패키지의 업무 세그먼트에도 반영한다.

```text
PROGRM_STRE_PATH=/cop/bbs/
  → egovframework.let.cop.bbs
```

다만 같은 테이블/업무의 기존 VO/Mapper/Service/Controller를 발견했다면 기존 패키지와 시그니처가
최우선이다. 메타데이터에 맞추기 위해 기존 파일을 이동하거나 개명하지 않는다. 프로그램 등록이
없거나 `PROGRM_STRE_PATH`가 확정되지 않은 경우에는 기존 `packageName + domain` fallback을 유지한다.

## 5. 생성 모델 영향

현재 `BoardTemplateModel`은 `domainKr`과 단일 `urlPrefix`만 보유한다. URL·표시정보를 모두 문자열 필드로 추가하면 record 생성자가 지나치게 길어지고 모든 테스트 fixture가 복잡해진다.

중첩 모델 분리를 권장한다.

이 분리는 새로운 실험적 패턴이 아니다. master-detail 생성 경로에서 역할별 모델을 분리하고 `CrudTemplateModel`을 재사용하는 방식이 이미 적용되어 있어, 모델 결합도를 낮추는 방향의 선례가 검증되어 있다.

```java
record BoardDisplayModel(
    String programFileName,
    String displayName,
    String upperMenuName
) {}

record BoardRouteModel(
    String canonicalPrefix,
    String registeredListUrl,
    String registeredListPath,
    String defaultBbsId
) {}
```

### 5.1 fallback 규칙

```text
displayName
  = programKoreanName이 있으면 그 값
  = 없으면 기존 domainKr

canonicalPrefix
  = 기존 packageName + domain 규칙 유지

registeredListPath
  = DB URL이 확인된 경우에만 alias로 추가

defaultBbsId
  = 명시 파라미터 또는 DB URL query에서 확인된 경우에만 설정
```

`domainKr`을 즉시 제거하면 기존 템플릿과 테스트가 광범위하게 깨진다. 과도기에는 유지하되 화면 출력은 `displayName`을 우선 사용한다.

## 6. URL 매핑 영향

현재 생성기는 `urlPrefix` 하나로 모든 URL을 만든다.

```text
{urlPrefix}List.do
{urlPrefix}Detail.do
{urlPrefix}RegistView.do
{urlPrefix}Regist.do
{urlPrefix}UpdtView.do
{urlPrefix}Updt.do
{urlPrefix}Delete.do
```

기존 프로그램 URL은 다음처럼 prefix 규칙과 다를 수 있다.

```text
/cop/bbs/selectBoardList.do?bbsId=BBSMSTR_AAAAAAAAAAAA
```

생성 canonical URL을 유지하면서 DB URL path를 목록 alias로 추가하는 방식이 가장 안전하다.

```java
@RequestMapping({
    "/cop/bbs/infoNoticeList.do",
    "/cop/bbs/selectBoardList.do"
})
```

DB URL의 query string은 `@RequestMapping`에 넣지 않는다. `bbsId`는 요청 파라미터로 전달한다.

### 6.1 장점

- 기존 생성 URL을 사용하는 코드가 깨지지 않는다.
- 기존 DB 메뉴 URL과 북마크가 유지된다.
- DB URL UPDATE가 필요 없다.
- 동일한 `bbsId`를 기준으로 GNB/LNB/브레드크럼 문맥을 유지할 수 있다.

### 6.2 URL 충돌 위험

대상 프로젝트에 같은 URL을 처리하는 기존 Controller가 있으면 Spring ambiguous mapping 오류가 발생한다. 생성 전에 Java 소스에서 alias 후보 URL을 검색해야 한다.

- 충돌 없음: alias 자동 추가
- 동일 Controller 재생성: 기존 매핑 보존
- 다른 Controller와 충돌: alias를 추가하지 않고 생성 실패 또는 명시적 선택 요구

DB에 상세·등록·수정 URL이 별도 프로그램으로 등록돼 있다면 목록 alias만으로 충분하지 않다. 이 경우 작업별 route/alias 모델로 확장해야 한다.

### 6.3 동일 path와 서로 다른 bbsId를 사용하는 프로그램

실제 프로그램 데이터는 서로 다른 게시판이 같은 목록 path를 공유하고 query의 `bbsId`만 다를 수
있다.

```text
공지사항   /cop/bbs/selectBoardList.do?bbsId=BBSMSTR_AAAAAAAAAAAA
업무게시판 /cop/bbs/selectBoardList.do?bbsId=BBSMSTR_CCCCCCCCCCCC
```

게시판별 Controller에 query를 제거한 동일 alias를 무조건 생성하면 두 Controller가 모두
`/cop/bbs/selectBoardList.do`를 처리해 ApplicationContext 시작 시 ambiguous mapping이 발생한다.
단순히 “현재 대상 프로젝트에 alias path가 있는가”뿐 아니라 이번 생성 결과 안에서 다른 게시판이
같은 registered path를 사용하게 되는지도 검사해야 한다.

권장 우선순위는 다음과 같다.

1. 같은 테이블과 공통 path를 처리하는 기존 게시판 Controller가 있으면 재사용한다.
2. 기존 매핑이 없고 alias가 유일하면 일반 alias를 생성한다.
3. 동일 path를 게시판별 Controller가 나눠 처리해야 한다면 별도 위임 메서드에 Spring
   `params = "bbsId=..."` 조건을 적용하는 방식을 검토한다.
4. 기존 일반 매핑과 조건부 매핑의 우선순위를 안전하게 판정할 수 없으면 저장 전에 실패하고
   기존 Controller 재사용 또는 명시 route 입력을 요구한다.

query string 전체를 `@RequestMapping` path에 넣지는 않되, query의 `bbsId`를 매핑 조건으로
사용하는 것과 요청 파라미터로 바인딩하는 것은 구분해야 한다. 현재 구현 범위에서 조건부 alias를
도입하지 않는다면 보수적인 기본값은 충돌 시 생성 실패다.

## 7. `bbsId` 유지 영향

`bbsId`는 링크뿐 아니라 Controller 진입 시점에 확정해야 한다.

목록 핸들러에는 다음 기본값 적용이 필요하다.

```java
if ((searchVO.getBbsId() == null || searchVO.getBbsId().isBlank())
        && DEFAULT_BBS_ID != null) {
    searchVO.setBbsId(DEFAULT_BBS_ID);
}
```

`defaultBbsId`도 없고 요청 파라미터도 없다면 DB 조회 전에 안내 화면으로 분기한다.

함께 확인해야 할 전달 경로는 다음과 같다.

- `index.jsp` 기본 forward
- 목록 최초 진입
- 검색 및 검색 초기화
- 페이지 이동
- 목록에서 상세 이동
- 상세의 이전글·다음글
- 등록·수정·삭제
- 첨부파일
- LNB 로컬 메뉴 URL

현재 View 링크는 대부분 `bbsId`를 전달하고 있으므로 핵심 변경 지점은 Controller 기본값 주입과 `index.jsp` 생성이다.

`defaultBbsId`는 URL에서 추출된 문자열이 아니라 게시판 마스터에서 존재가 검증된 값만 사용한다.
검증되지 않은 값은 Controller 기본값, `index.jsp` forward, LNB 링크에 주입하지 않는다.

## 8. 복합 PK 방어 영향

현재 상세 핸들러는 `nttId == null`과 조회 결과 null을 방어하지만 `bbsId`를 검사하지 않는다.

게시판의 실질 PK는 `BBS_ID + NTT_ID`이므로 다음 조건을 모두 검사해야 한다.

```text
bbsId == null 또는 빈 문자열
nttId == null
nttId가 String이면 빈 문자열
```

FreeMarker는 필드 Java 타입에 따라 검사식을 다르게 생성해야 한다.

```freemarker
<#if bbsId.javaType == "String">
    bbsId == null || bbsId.isBlank()
<#else>
    bbsId == null
</#if>
```

상세 핸들러만 방어하면 다음 경로에 문제가 남는다.

- 수정 화면에서 존재하지 않는 PK 조회
- 수정 처리
- 삭제 처리
- 조회수 증가
- 첨부파일에서 상세 화면으로 forward

Controller에 공통 PK 검사 메서드를 생성하고 PK가 필요한 모든 핸들러에서 재사용하는 방식이 안전하다. 수정 화면도 조회 결과가 null이면 상세과 동일하게 목록 또는 안내 화면으로 분기해야 한다.

등록 화면과 등록 처리에는 기존 `nttId`가 없어야 정상이다. 신규 `nttId`는 Service의 ID 생성기가
만들며, 생성기가 기존 `LETTNBBS` 행의 최소값·최대값·첫 번째 값을 기본 PK로 선택하면 안 된다.

## 9. 표시정보 영향

현재 화면 title, H1, caption은 테이블명에서 추출한 `domainKr`을 사용한다. 따라서 `LETTNBBS 목록` 같은 문구가 생성될 수 있다.

표시정보의 source of truth는 다음과 같이 분리한다.

```text
페이지 title/H1/caption → PROGRM_KOREAN_NM 기반 displayName
LNB 제목              → 인터셉터의 PROGRM_KOREAN_NM
브레드크럼 상위명      → LETTNMENUINFO.MENU_NM
브레드크럼 마지막      → displayName + currentPageSuffix
```

Controller는 다음 suffix만 전달한다.

```java
currentPageSuffix = "목록" | "상세" | "등록" | "수정"
```

Controller가 다음 속성을 직접 넣으면 DB 기반 인터셉터 모델을 가릴 수 있으므로 생성하지 않는다.

```text
lnbTitle
breadcrumbs
```

기존 `EgovGnbMenuInterceptor`는 `PROGRM_KOREAN_NM + currentPageSuffix` 조합을 지원하므로 핵심 구조 변경은 필요하지 않다. 단, URL alias 또는 같은 `bbsId`가 전달되지 않으면 현재 프로그램을 찾을 수 없으므로 URL 개선과 표시정보 개선을 함께 적용해야 한다.

## 10. CSS 공통 체계 정의 및 board 전체 마이그레이션 영향

CSS는 다음 두 종류의 프로젝트를 모두 지원해야 한다.

1. 앞으로 `initializeProject`로 생성할 프로젝트
2. 이미 생성되어 기존 `styles.css`가 있는 프로젝트

`styles.css.tpl`만 수정하면 기존 프로젝트는 보정되지 않는다. `KrdsStylesConfigurer`와 같은 idempotent 보강 서비스가 필요하다. 다만 이 작업을 단순한 CSS 추가로 보면 안 된다. 먼저 공통 클래스의 책임·토큰·상태를 정의하고, 목록·상세·등록·수정 템플릿 전체를 새 클래스 계약으로 옮겨야 한다.

범위는 다음 세 묶음으로 재산정한다.

1. **공통 클래스 체계 설계**: `egov-crud-page`, `egov-search-form`, `egov-control`, `egov-btn`, `egov-list-table`, `egov-form-table`, `egov-pagination`의 책임과 크기 토큰 정의
2. **board 템플릿 전체 마이그레이션**: 목록·상세·등록·수정 및 standalone/body 변형에 공통 클래스와 KRDS size modifier 적용
3. **기존 프로젝트 idempotent 보강**: WAR/Boot의 기존 `styles.css`에 marker 블록을 안전하게 추가하고 중복 실행을 방지

marker 기반 처리에는 프로젝트 내부 선례가 있다. `ThymeleafLayoutTool.patchContextCommonXml()`과 `patchServletContextXml()`이 이미 대상 문자열 존재 여부를 확인한 뒤 보존 또는 patch하는 패턴을 사용한다. CSS도 이 흐름의 공통 보조 메서드와 결과 상태 표현 방식을 재사용할 수 있다.

### 10.1 공통 체계 및 CSS 보강 원칙

1. WAR와 Boot 정적 CSS 경로를 탐지한다.
2. 기존 `styles.css` 전체를 덮어쓰지 않는다.
3. 식별 가능한 marker 블록만 추가한다.
4. 동일 marker가 있으면 중복 추가하지 않는다.
5. CSS가 없으면 명확한 실패 또는 신규 생성 정책을 적용한다.
6. 공통 클래스의 책임과 토큰을 먼저 정의한 뒤 View를 마이그레이션한다.
7. Thymeleaf 게시판 생성 후 토큰과 클래스 적용 여부를 검증한다.

필요한 보강 예시는 다음과 같다.

```css
.krds-input {
    --krds-input--textarea-size-height: 220px;
}

.egov-crud-page { /* 화면 스코프 */ }
.egov-search-form { /* 검색 영역 */ }
.egov-control { /* 공통 입력 */ }
.egov-btn { /* 공통 버튼 */ }
.egov-list-table { /* 목록 표 */ }
.egov-form-table { /* 입력 표 */ }
.egov-pagination { /* 페이지네이션 */ }
```

화면 템플릿도 다음처럼 크기 modifier와 공통 클래스를 사용해야 한다.

```html
<section class="egov-crud-page" layout:fragment="content">
    <select class="krds-form-select medium egov-control"></select>
    <input class="krds-input medium egov-control">
    <textarea class="krds-input medium egov-control egov-textarea"></textarea>
</section>
```

현재 공통 CSS 템플릿에는 버튼·입력·셀렉트 높이와 레거시 페이지네이션 보정이 포함되어 있으나 게시판 템플릿의 `medium` modifier 및 `egov-crud-page` 계열 공통 클래스 적용은 추가로 필요하다.

CSS 보강을 업무 레이어 파일 수에 포함하면 기존 `12개 생성` 계약과 테스트가 바뀐다. CSS는 업무 파일 수에서 제외하고 다음처럼 별도 상태로 보고하는 것이 좋다.

```text
업무 파일: 12개
공통 CSS: 보강 / 보존 / 미발견
```

## 11. 생성 결과 모델 영향

현재 `BoardOrchestrationResult`는 메뉴 연동과 CSS 보강 상태를 표현하지 못한다. 다음 정보를 추가하는 것이 적절하다.

```java
String resolvedMainTable;
String resolvedMasterTable;
String menuIntegrationStatus;
String resolvedProgramName;
String resolvedProgramUrl;
String resolvedBbsId;
String cssStatus;
List<String> warnings;
```

최종 Tool 출력 예시는 다음과 같다.

```text
GNB/LNB 연동: DB URL 유지 + Controller alias
프로그램 표시명: 공지사항
게시판 테이블군: LETTNBBS / LETTNBBSMASTER / LETTNBBSUSE
기본 bbsId: BBSMSTR_AAAAAAAAAAAA
CSS: 기존 styles.css 보강 완료
PK 방어: BBS_ID + NTT_ID 적용
```

## 12. 파일별 예상 영향

| 구분 | 예상 대상 | 변경 내용 |
|---|---|---|
| 공개 Tool | `CrudPromptBuilderTool.java` | 선택 메타데이터 파라미터 추가 및 공통 옵션 변환 |
| 테이블군 해석 | 신규 `BoardTableSetResolver` 또는 동등 서비스 | 명시값 우선, LETTN/COMTN 게시판 테이블군 존재 확인 및 혼합 방지 |
| 오케스트레이션 | `BoardOrchestrationService.java` | 테이블군 해석, 메타데이터 조회, 마스터 검증, 충돌 검사, CSS 보강, 결과 상태 조립 |
| 스키마 조회 | `BoardSchemaService.java` | 확정된 게시판 테이블군의 컬럼 조회, 빈 게시판과 테이블 미존재 구분 |
| 프로그램 조회 | 신규 `BoardProgramMetadataService` | 프로그램·메뉴 조회, URL 파싱 결과와 게시판 마스터 교차검증 |
| 생성 모델 | `BoardTemplateModel.java` | 표시정보 및 route 모델 포함 |
| 모델 팩토리 | `BoardModelFactory.java` | DB 메타데이터 우선 및 fallback 적용 |
| 파일명 정의 | `BoardLayerDefinition.java` | 직접 영향은 낮음. 이번 변경에서 명명 정책은 유지 |
| 렌더러 | `BoardTemplateRenderer.java` | 신규 모델을 FreeMarker 데이터로 전달 |
| Controller 템플릿 | `board/controller.java.ftl` | URL alias, 기본 bbsId, 복합 PK 및 null 방어 |
| 목록 View | `board/thymeleaf-list*.ftl` | displayName, medium modifier, CRUD 공통 클래스 |
| 상세 View | `board/thymeleaf-detail*.ftl` | displayName, 공통 클래스, bbsId 유지 검증 |
| 등록/수정 View | `board/thymeleaf-regist*.ftl`, `thymeleaf-updt*.ftl` | displayName, medium modifier, textarea 토큰 대상 클래스 |
| 공통 CSS | `egov/styles.css.tpl` | textarea 토큰과 공통 CRUD 클래스 추가 |
| CSS 보강 | 신규 `KrdsStylesConfigurer` | 기존 WAR/Boot 프로젝트 CSS의 idempotent 보정 |
| 메뉴 인터셉터 | `gnb-menu-interceptor.java.ftl` | 핵심 변경은 낮음. 회귀 테스트 중심 |
| 결과 | `BoardOrchestrationResult.java` | 메뉴·표시명·bbsId·CSS 상태와 warning 추가 |

## 13. 테스트 영향

### 13.1 모델 및 메타데이터 테스트

- 명시 메타데이터가 자동 조회보다 우선하는지
- DB `PROGRM_KOREAN_NM`이 `displayName`이 되는지
- URL query에서 `bbsId`를 추출하는지
- 프로그램 조회 결과가 없거나 여러 개인 경우 처리
- 스키마·테이블 식별자 검증
- 명시 테이블명 우선 및 LETTN/COMTN 게시판 테이블군 fallback
- LETTN main과 COMTN master가 혼합되지 않는지
- URL에서 추출한 `bbsId`가 게시판 마스터에 없을 때 저장 전 실패하는지
- 게시판 마스터는 있으나 `LETTNBBS` 게시물이 0건일 때 정상 생성하는지
- `LETTNBBSUSE` 다중 대상에서 `BBS_ID` 단일 조회를 생성하지 않는지
- `LETTNBBSMASTEROPTN` 행이 없어도 기본 CRUD 생성이 가능한지
- 기존 레이어가 있으면 `PROGRM_STRE_PATH`보다 기존 패키지를 우선하는지

### 13.2 템플릿 테스트

- canonical URL과 목록 alias가 함께 생성되는지
- `bbsId + nttId` 복합 PK 검사가 생성되는지
- 상세 및 수정 조회 결과 null 방어
- title/H1/caption이 `displayName`을 사용하는지
- 모든 KRDS input/select/textarea에 크기 modifier가 있는지
- `egov-crud-page`와 CRUD 공통 클래스가 적용되는지
- 등록 화면에 기존 `nttId` 기본값을 생성하지 않는지
- 목록에서는 `nttId`를 요청 필수값으로 검사하지 않는지

### 13.3 오케스트레이션 테스트

- `index.jsp` forward에 `bbsId` 포함
- CSS 보강 서비스 호출과 실패 처리
- URL alias 충돌 검사
- 업무 생성 파일 수 12개 유지
- 메뉴 연동 상태와 warning 출력
- 동일 registered path에 서로 다른 `bbsId`를 가진 프로그램의 alias 충돌 처리
- 검증된 `defaultBbsId`만 Controller와 `index.jsp`에 전달되는지

### 13.4 URL parser 테스트

- query 순서 변경
- URL 인코딩
- `bbsId` 없음
- 상대 URL
- 잘못된 URL
- 동일 키 중복

### 13.5 통합 검증

```bash
./gradlew test
./gradlew bootJar
```

생성 프로젝트에서는 다음 요청을 확인한다.

```text
DB 메뉴 목록 URL
생성 canonical 목록 URL
PK 없는 상세 URL
존재하지 않는 PK 상세 URL
정상 상세 URL
등록/수정 후 목록 복귀
게시물이 0건인 신규 게시판 목록
동일 path를 공유하는 공지사항/업무게시판의 메뉴 진입
```

Thymeleaf 화면은 실제 브라우저에서 입력·셀렉트·textarea·페이지네이션 크기와 브레드크럼을 확인한다.

## 14. 주요 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 프로그램 조회 결과가 여러 개 | 잘못된 URL·표시명·bbsId 적용 | 임의 선택 금지, 명시 파라미터 요구 |
| 기존 Controller와 alias 충돌 | ApplicationContext 시작 실패 | 생성 전 URL 매핑 검색 및 충돌 시 중단 |
| 서로 다른 게시판이 같은 path 사용 | 게시판별 Controller 간 ambiguous mapping | 기존 공통 Controller 재사용 우선, 조건부 params 매핑 검토, 불명확하면 중단 |
| URL의 `bbsId`가 마스터에 없음 | 잘못된 게시판 ID가 생성 코드에 고정 | `LETTNBBSMASTER`에서 교차검증 후에만 default 확정 |
| 게시물 0건을 게시판 미존재로 오판 | 신규·빈 게시판 생성 차단 | 게시판 존재는 master로 판단하고 게시물 0건은 허용 |
| LETTN/COMTN 테이블군 혼합 | 생성 SQL의 테이블 관계 불일치 | 테이블군 resolver로 한 계열을 원자적으로 확정 |
| `LETTNBBSUSE`를 BBS_ID로 단건 조회 | 대상 추가 시 다중 행 조회 오류 | `(BBS_ID, TRGET_ID)` 조건 또는 EXISTS 사용 |
| 기존 게시물에서 nttId 기본값 선택 | 엉뚱한 게시물 상세·수정 진입 | nttId는 목록 선택값 또는 등록 시 ID 생성기로만 결정 |
| `bbsId` 없는 일반 게시판 생성 | 특정 게시판으로 잘못 고정 | default가 확인된 경우에만 자동 주입 |
| DB URL query 파싱 오류 | 메뉴 문맥 유실 | URI/query 전용 parser와 인코딩 테스트 |
| CSS 전체 덮어쓰기 | 사용자 스타일 유실 | marker 기반 append/replace, 전체 overwrite 금지 |
| CSS 보강 중복 | 규칙 중복 및 유지보수 악화 | idempotent marker 검사 |
| `BoardTemplateModel` 생성자 확대 | 테스트 fixture 대량 수정 | 중첩 record로 캡슐화 |
| 실행 서버 Tool schema stale | 신규 파라미터가 클라이언트에 안 보임 | 빌드 후 MCP 서버 재시작 및 클라이언트 재연결 |

## 15. 구현 권장 순서

0. 기존 `board/controller.java.ftl`과 `gnb-menu-interceptor.java.ftl` 미커밋 변경을 먼저 단위·회귀 테스트한다.
1. 위 기존 변경만 별도 베이스라인 커밋으로 고정한다.
2. 현재 구현된 `nttId` 방어 패턴을 `bbsId + nttId` 복합 PK 방어로 확장한다.
3. 상세뿐 아니라 수정·삭제·조회수·첨부파일 forward에 PK 및 null 결과 방어를 적용한다.
4. LETTN/COMTN 게시판 테이블군 resolver를 추가하고 기존 기본값 하위 호환을 검증한다.
5. `BoardProgramMetadata`와 URL parser를 추가한다.
6. URL의 `bbsId`를 게시판 마스터에서 검증하고 빈 게시판을 허용한다.
7. 공개 Tool optional 파라미터를 추가하고 `명시 > DB > fallback` 우선순위를 구현한다.
8. `BoardTemplateModel`에 중첩 표시·라우팅 모델을 추가한다.
9. `PROGRM_STRE_PATH` 기반 신규 패키지 결정과 기존 레이어 우선 규칙을 적용한다.
10. Controller alias와 검증된 `defaultBbsId`를 적용한다.
11. 동일 path·서로 다른 `bbsId`의 alias 충돌을 검사한다.
12. `LETTNBBSUSE` 복합키와 선택 옵션 테이블 정책을 Mapper 생성에 반영한다.
13. 공통 CRUD 클래스 체계를 설계한다.
14. board 목록·상세·등록·수정 템플릿 전체를 공통 클래스 체계로 마이그레이션한다.
15. marker 기반 CSS idempotent 보강 서비스를 추가한다.
16. 결과에 테이블군/메뉴/CSS/PK 상태를 표시한다.
17. 전체 테스트와 `bootJar`를 실행한다.
18. 생성 프로젝트 브라우저 렌더링을 검증한다.

PK 방어를 Tool 파라미터보다 먼저 처리하는 이유는 이미 구현된 `nttId` null 및 조회 결과 null 방어 패턴을 복합 PK로 확장하는 저위험 변경이기 때문이다. 이 변경을 먼저 분리하면 이후 메타데이터·URL·CSS 변경에서 발생한 회귀와 구분하기 쉽다.

## 16. 이번 변경에서 분리할 항목

`InfoNotice*`와 `EgovInfoNotice*` 명명 정책 변경은 URL·PK·표시정보·CSS 수정과 분리한다.

클래스명 전체를 `EgovInfoNotice*`로 변경하면 다음까지 동시에 영향을 받는다.

- Java 파일명과 public class 이름
- import
- Service/Mapper 메서드명
- Mapper namespace 및 statement ID
- Spring Bean 이름
- 테스트 fixture
- 기존 생성 결과와의 호환성

이번 개선은 기존 레이어별 명명 규칙을 유지하면서 프로그램 메타데이터만 화면 및 URL 연동에 사용하는 것이 안전하다.

## 17. 작업 트리 주의사항

검토 시점에 다음 핵심 파일을 포함한 미커밋 변경이 존재한다.

- `CrudPromptBuilderTool.java`
- `ThymeleafLayoutTool.java`
- `board/controller.java.ftl`
- `crud/layout/gnb-menu-interceptor.java.ftl`
- `egov/styles.css.tpl`
- 관련 테스트 파일

본 구현을 시작하기 전에 특히 `board/controller.java.ftl`과 `gnb-menu-interceptor.java.ftl` 변경을 먼저 테스트하고 별도 커밋으로 고정해야 한다. 이를 생략하면 기존 상세 500 방어·브레드크럼 수정과 신규 메타데이터·CSS 변경이 한 diff에 섞여 회귀 원인을 구분하기 어렵다.

베이스라인 커밋 이후에는 기존 변경을 되돌리거나 덮어쓰지 않고 증분 수정한다.

## 18. 최종 권고

가장 안전한 구현 범위는 다음 조합이다.

```text
명시값 우선 + LETTN/COMTN 게시판 테이블군 자동 해석
+ URL bbsId의 게시판 마스터 검증
+ 게시물 0건인 빈 게시판 허용
+ LETTNBBS는 생성 시 스키마, 실행 시 CRUD 데이터로 사용
+ nttId는 목록 선택값 또는 등록 시 ID 생성기로만 결정
+ 기존 canonical URL 유지
+ DB URL path Controller alias
+ 동일 path·서로 다른 bbsId의 alias 충돌 방어
+ optional 프로그램 메타데이터
+ defaultBbsId의 조건부 적용
+ BBS_ID/NTT_ID 복합 PK 방어
+ DB 표시명 우선
+ PROGRM_STRE_PATH 기반 신규 패키지 결정(기존 레이어 우선)
+ LETTNBBSUSE 복합키와 선택 옵션 테이블 정책
+ marker 기반 CSS 보강
```

이 방식은 DB 쓰기 없이 기존 메뉴 URL과 생성 URL을 함께 유지하며, 존재하지 않는 게시판 ID를
생성 코드에 고정하거나 빈 게시판을 잘못 거부하는 문제를 방지한다. 또한 생성 시점의 스키마 참조와
실행 시점의 게시물 CRUD 책임을 분리해 생성 후 사람이 URL·PK·표시정보·CSS·테이블 매핑을 반복
수정하는 과정을 제거할 수 있다.
