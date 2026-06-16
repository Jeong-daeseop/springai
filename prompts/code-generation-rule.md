# Code Generation Rule

코드를 생성할 때 반드시 지켜야 하는 개발 표준 규칙이다.

## Java

- Java 17 이상 기준으로 작성한다.
- 패키지명은 사용자가 제공한 값 또는 Tool 결과를 사용한다.
- 임의 패키지명을 생성하지 않는다.
- 클래스명은 도메인명을 기준으로 일관되게 작성한다.
- import는 실제 사용하는 클래스만 선언한다.
- 사용하지 않는 import를 남기지 않는다.
- null, 빈 문자열, 잘못된 입력값에 대한 검증을 포함한다.
- 날짜/시간 API는 `java.time` 패키지를 우선 사용한다.

## Spring

- 화면 Controller는 `@Controller`를 사용한다.
- REST API Controller는 `@RestController`를 사용한다.
- URL 매핑은 `@RequestMapping`, `@GetMapping`, `@PostMapping`을 명확히 사용한다.
- Service는 interface + impl 구조를 사용한다.
- ServiceImpl은 `@Service`를 사용한다.
- 트랜잭션이 필요한 변경 로직은 `@Transactional`을 사용한다.
- 생성자 주입을 우선 사용한다.

## eGovFrame

- eGovFrame 5.0 기준을 우선 적용한다.
- eGovFrame 5.0은 `jakarta.*` namespace를 사용한다.
- eGovFrame 4.3은 `javax.*` namespace를 사용한다.
- 패키지는 `egovframework` 기준 구조를 따른다.
- 페이징은 `PaginationInfo`를 사용한다.
- 공통 유틸은 프로젝트에 존재하면 eGovFrame 유틸을 우선 사용한다.
- VO에는 검색 조건과 페이징 필드를 포함한다.
- Controller는 목록/상세/등록/수정/삭제 흐름을 모두 제공한다.

## MyBatis

- Mapper Interface를 생성한다.
- Mapper XML을 생성한다.
- Mapper Interface method와 Mapper XML SQL id를 일치시킨다.
- XML namespace는 Mapper Interface의 fully qualified name과 일치시킨다.
- `resultMap`을 사용한다.
- SQL id는 다음 접두어를 사용한다.
  - 목록 조회: `select`
  - 건수 조회: `select...TotCnt`
  - 단건 조회: `select`
  - 등록: `insert`
  - 수정: `update`
  - 삭제: `delete`
- 동적 검색 조건은 `<where>`, `<if>`를 사용한다.
- 페이징 SQL은 DB 방언을 고려한다.
- 컬럼명은 DB 스키마 조회 결과와 정확히 일치해야 한다.
- Java 필드명은 camelCase로 변환한다.

## Thymeleaf

- 사용자가 별도 지정하지 않으면 화면은 Thymeleaf 기준으로 생성한다.
- 레이아웃 구조를 사용한다.
- header, footer 영역은 분리 가능한 구조로 작성한다.
- form submit은 `th:action`을 사용한다.
- 반복 출력은 `th:each`를 사용한다.
- 조건 출력은 `th:if`, `th:unless`를 사용한다.
- URL은 `@{...}` 문법을 사용한다.
- form field name은 VO 필드명과 일치시킨다.
- CSRF 사용 프로젝트에서는 CSRF hidden input을 포함한다.

## JSP

- 기존 프로젝트가 JSP/eGov tag 기반이면 JSP를 따른다.
- JSP는 UTF-8 page directive를 포함한다.
- JSTL core taglib를 선언한다.
- URL은 `<c:url>`을 우선 사용한다.
- 목록 화면은 `PaginationInfo`와 eGov paging tag를 사용할 수 있게 작성한다.
- form field name은 VO 필드명과 일치시킨다.

## Controller

- 목록 화면 메서드를 제공한다.
- 상세 화면 메서드를 제공한다.
- 등록 화면 메서드를 제공한다.
- 등록 처리 메서드를 제공한다.
- 수정 화면 메서드를 제공한다.
- 수정 처리 메서드를 제공한다.
- 삭제 처리 메서드를 제공한다.
- 처리 후에는 redirect를 사용한다.
- validation 오류는 입력 화면으로 되돌린다.
- URL prefix는 Tool 또는 사용자 입력값을 그대로 따른다.

## Service

- Service interface에는 CRUD 메서드를 선언한다.
- ServiceImpl은 interface를 구현한다.
- ServiceImpl은 Mapper를 주입받아 사용한다.
- 등록/수정/삭제는 트랜잭션 대상이다.
- 예외 정책은 프로젝트 기존 패턴을 따른다.

## VO

- 테이블 컬럼을 기준으로 필드를 생성한다.
- PK 필드를 반드시 포함한다.
- 검색 필드를 포함한다.
  - `searchCondition`
  - `searchKeyword`
  - `pageIndex`
  - `firstIndex`
  - `recordCountPerPage`
- validation annotation은 eGovFrame 버전에 맞는 namespace를 사용한다.
- Lombok 사용 여부는 기존 프로젝트 패턴을 따른다.

## Mapper XML

- XML 선언과 MyBatis mapper doctype을 포함한다.
- namespace를 정확히 지정한다.
- `resultMap`을 정의한다.
- select list, count, detail, insert, update, delete SQL을 포함한다.
- 검색 조건과 페이징 조건을 포함한다.
- SQL injection 위험이 있는 `${}` 사용을 피하고 `#{}`를 사용한다.

## Security

- eGovFrame 버전별 Security 구조를 따른다.
- 4.3 XML Security와 4.3 Java Config 방식을 혼합하지 않는다.
- 5.0에서는 Spring Security 6.x 구조를 따른다.
- 로그인 URL, 로그아웃 URL, 성공 URL, 실패 URL은 사용자의 값 또는 프로젝트 기존 값을 따른다.
- DB URL 권한은 `COMTNROLEINFO`, `COMTNAUTHORROLERELATE` 기준으로 생성한다.
- Security SQL은 실행하지 않고 검토용으로 출력한다.

## Menu/Auth SQL

- 메뉴 등록 전 상위 메뉴를 조회한다.
- 프로그램 등록 전 `PROGRM_FILE_NM` 중복을 확인한다.
- `MENU_NO`, `MENU_ORDR`, `ROLE_CODE`는 Tool 계산 결과를 따른다.
- SQL은 DB 방언 설정을 따른다.
- SQL은 실행하지 않고 사용자 검토용으로 출력한다.

## Error Handling

- 필수 입력값이 없으면 코드를 생성하지 않는다.
- 테이블 스키마가 없으면 코드를 생성하지 않는다.
- 출력 경로가 없으면 파일 저장을 하지 않는다.
- Tool 결과가 오류이면 오류를 먼저 해결한다.
- 생성 코드 검증에서 실패 항목이 있으면 수정 후 재검증한다.

## Output Format

- 파일별로 경로와 코드를 분리해서 출력한다.
- 긴 코드는 파일 단위로 나누어 출력한다.
- 생성된 파일 목록을 먼저 제시한다.
- SQL은 코드와 분리해서 출력한다.
- 마지막에 검증 결과와 후속 작업을 제시한다.
