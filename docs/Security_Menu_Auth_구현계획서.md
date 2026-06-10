# SecurityTemplateTool / MenuTool / AuthTool 구현 계획서

## 1. 목적

이 계획서는 `Security_Menu_Auth_구현순서.md`의 방향을 실제 구현 작업으로 전환하기 위한 문서이다.

현재 구조는 다음 3개 Tool로 분리되어 있다.

```text
SecurityTemplateTool = 보안 기반 생성
MenuTool             = 메뉴/프로그램 등록 SQL 생성
AuthTool             = URL 권한 등록 SQL 생성
```

이 분리 구조는 유지한다.

앞으로의 구현 목표는 Tool을 하나로 합치는 것이 아니라, 다음 세 가지를 강화하는 것이다.

- 각 Tool의 단일 책임 유지
- 생성 SQL의 안전성 강화
- AI가 올바른 순서로 Tool을 호출하도록 Workflow 강화

## 2. 현재 구현 상태 요약

### 2.1 SecurityTemplateTool

`SecurityTemplateTool`은 eGovFrame Security 기반 파일을 생성한다.

주요 역할은 다음과 같다.

- `web.xml.fragment` 생성
- `context-security.xml` 생성
- Security 필터 클래스 생성
- 로그인 JSP 생성
- UserDetails 관련 파일 생성
- `securityMapper` 생성

현재 판단으로는 Security 조합 완결성과 버전 분기는 상당 부분 정리되어 있다.

따라서 다음 구현의 중심은 `SecurityTemplateTool` 자체의 대규모 변경이 아니라, `MenuTool`, `AuthTool`이 이 기반 위에서 안전하게 동작하도록 연결성을 강화하는 것이다.

### 2.2 MenuTool

`MenuTool`은 다음 기능을 제공한다.

- `getMenuStructure(menuNo)`
  - `COMTNMENUINFO` 기준 메뉴 트리 조회
  - 신규 `MENU_NO`, `MENU_ORDR` 권장값 안내

- `generateMenuInsertSql(upperMenuNo, urlPrefix, menuNm, progrmFileNm)`
  - `COMTNPROGRMLIST` INSERT SQL 생성
  - `COMTNMENUINFO` INSERT SQL 생성

현재 보완이 필요한 부분은 다음이다.

- null / blank 입력 검증 부족
- `upperMenuNo` 숫자 검증 부족
- `upperMenuNo` 존재 여부 검증 부족
- `PROGRM_FILE_NM` 중복 확인 부족
- URL 중복 확인 부족
- SQL literal escape 부족
- `storePath` 데드 코드 존재
- DB 방언 의존성 명시 부족
- 단위 테스트 없음

### 2.3 AuthTool

`AuthTool`은 다음 기능을 제공한다.

- `getProgramList(keyword)`
  - `COMTNPROGRMLIST` 프로그램 목록 검색

- `generateAuthInsertSql(urlPrefix, programNm, domain)`
  - `COMTNROLEINFO` INSERT SQL 생성
  - `COMTNAUTHORROLERELATE` INSERT SQL 생성

현재 보완이 필요한 부분은 다음이다.

- null / blank 입력 검증 부족
- `urlPrefix` 형식 검증 부족
- `ROLE_CODE` 중복 가능성 안내 부족
- `ROLE_PTTRN` 실제 Security Filter 해석 검증 부족
- `ROLE_SORT` 숫자 컬럼 가능성 대비 부족
- SQL literal escape 부족
- `securityMapper` 의존 관계 안내 부족
- DB 방언 의존성 명시 부족
- 단위 테스트 없음

### 2.4 WorkflowGuideTool

현재 `WorkflowGuideTool`은 CRUD 소스 생성 워크플로우 안내 중심이다.

Security → Menu → Auth 흐름은 별도 안내가 부족하다.

따라서 새로운 Tool을 만들기 전에, 우선 `WorkflowGuideTool`에 Security/Menu/Auth 적용 흐름을 추가하는 방향이 적절하다.

## 3. 구현 원칙

### 3.1 Tool 단일 책임 유지

각 Tool의 책임은 다음과 같이 유지한다.

| Tool | 책임 | 하지 말아야 할 일 |
|---|---|---|
| `SecurityTemplateTool` | Security 기반 파일 생성 | 메뉴/권한 SQL 생성 |
| `MenuTool` | 메뉴/프로그램 등록 SQL 생성 | 권한 SQL 생성 |
| `AuthTool` | URL 권한 SQL 생성 | 메뉴 SQL 또는 Security 파일 생성 |
| `WorkflowGuideTool` | 호출 순서와 검증 절차 안내 | 실제 SQL 생성 또는 파일 생성 |

### 3.2 적용 순서 고정

AI와 사용자가 따라야 할 기본 순서는 다음으로 고정한다.

```text
1. SecurityTemplateTool
2. securityMapper 포함 여부 확인
3. MenuTool - 프로그램/메뉴 SQL 생성
4. AuthTool - URL 권한 SQL 생성
5. SQL 실행
6. 서버 재기동 또는 Security 캐시 갱신
7. 메뉴 노출 + URL 접근 권한 테스트
```

### 3.3 생성 SQL 안전성 강화

Tool은 SQL을 직접 실행하지 않더라도, 사용자가 복사해서 실행할 수 있는 SQL을 만든다.

따라서 다음을 필수로 적용한다.

- SQL 문자열 escape
- 입력값 null / blank 검증
- 숫자형 입력 검증
- URL 형식 검증
- 중복 가능성 안내
- DB 방언 대응 전략 수립

### 3.4 Phase 분류 기준 통일

기존 문서에는 두 종류의 Phase가 섞여 있었다.

- 실행 작업 기준 Phase: 입력 검증, SQL escape, 중복 검증, Workflow 확장, 테스트
- 아키텍처 기준 Phase: Spec, Validator, SqlBuilder, ResultBuilder, Repository, WorkflowDefinition

실제 구현에서는 아키텍처 기준을 기본 골격으로 삼고, 각 Phase 안에서 실행 작업을 처리한다.

다만 모든 구조를 한 번에 만들면 초기 리스크가 커지므로 다음 원칙을 따른다.

> 기존 Tool 메서드 시그니처는 유지한다.  
> 먼저 안전성 문제를 빠르게 줄인다.  
> 이후 Spec / SqlPlan / Validator / Builder 구조로 점진적으로 추출한다.

따라서 이 계획서는 아래 통합 Phase를 기준으로 진행한다.

| 통합 Phase | 아키텍처 기준 | 실행 작업 기준 |
|---|---|---|
| Phase 0 | SecurityTemplateTool 기준 확정 | Security 재구현 없음, securityMapper 선행 조건 확인 |
| Phase 1 | 시그니처 유지 + 안전성 보강 | 입력 검증, SQL escape, NPE 방지 |
| Phase 2 | Spec + SqlPlan 도입 | 입력값 정규화, SQL/경고/후속조치 모델 분리 |
| Phase 3 | Validator / SqlBuilder 분리 | 검증 로직과 SQL 생성 로직 추출 |
| Phase 4 | Repository + DB Dialect 전략 | 중복 검증, MySQL/Oracle 차이 대응 |
| Phase 5 | ResultBuilder 분리 | 사용자 반환 메시지, 경고, nextSteps 분리 |
| Phase 6 | WorkflowDefinition 기반화 | Security/Menu/Auth workflow 추가 |
| Phase 7 | 테스트 / description / 문서 | 단위 테스트, 통합 테스트, Tool 설명 업데이트 |

## 4. 단계별 구현 계획

## Phase 0. SecurityTemplateTool 기준 확정

`SecurityTemplateTool`은 이미 구현되어 있으므로 이번 계획에서 재구현하지 않는다.

이번 계획에서 `SecurityTemplateTool`의 역할은 다음이다.

- Security 기반 생성이 완료된 기준점
- `securityMapper` 선행 조건의 기준점
- `MenuTool`, `AuthTool`, `WorkflowGuideTool`이 따라야 할 아키텍처 참조 모델

확인 항목은 다음이다.

- `setup-all-war-43-xml` / `setup-all-war-50` 등 전체 조합에 `securityMapper`가 포함되는지 확인한다.
- `AuthTool` 안내 문구에 `securityMapper` 선행 필요성이 반영되는지 확인한다.
- `WorkflowGuideTool`이 Security 단계에서 `SecurityTemplateTool` 호출을 먼저 안내하도록 한다.

## Phase 1. 시그니처 유지 + 안전성 보강

이 Phase에서는 기존 MCP Tool 호출을 깨지 않는다.

유지해야 할 메서드는 다음이다.

```java
MenuTool.getMenuStructure(String menuNo)
MenuTool.generateMenuInsertSql(String upperMenuNo, String urlPrefix, String menuNm, String progrmFileNm)
AuthTool.getProgramList(String keyword)
AuthTool.generateAuthInsertSql(String urlPrefix, String programNm, String domain)
WorkflowGuideTool.suggestNextStep(String currentContext)
```

초기 구현은 범위를 줄이기 위해 각 Service의 private method로 시작해도 된다.

다만 Phase 2에서 `Spec`, Phase 3에서 `Validator`로 추출할 것을 전제로 한다.

검증 대상은 다음이다.

| 입력값 | 검증 기준 |
|---|---|
| `menuNo` | null 불가, blank 불가, 숫자 문자열 |
| `upperMenuNo` | null 불가, blank 불가, 숫자 문자열 |
| `urlPrefix` | null 불가, blank 불가, `/`로 시작, 끝 `/` 정규화 |
| `menuNm` | null 불가, blank 불가 |
| `programNm` | null 불가, blank 불가 |
| `domain` | null 불가, blank 불가 |
| `progrmFileNm` | null 불가, blank 불가, `.do` 제외 권장 |

### 1-1. MenuService 입력 검증

대상 메서드는 다음이다.

- `getMenuStructure(String menuNo)`
- `generateMenuInsertSql(String upperMenuNo, String urlPrefix, String menuNm, String progrmFileNm)`

수정 방향은 다음이다.

```text
getMenuStructure()
  → menuNo null/blank/숫자 검증

generateMenuInsertSql()
  → upperMenuNo 숫자 검증
  → urlPrefix 형식 검증
  → menuNm blank 검증
  → progrmFileNm blank 검증
```

### 1-2. AuthService 입력 검증

대상 메서드는 다음이다.

- `getProgramList(String keyword)`
- `generateAuthInsertSql(String urlPrefix, String programNm, String domain)`

수정 방향은 다음이다.

```text
getProgramList()
  → keyword null 허용
  → blank이면 전체 목록 50건 반환 유지

generateAuthInsertSql()
  → urlPrefix 형식 검증
  → programNm blank 검증
  → domain blank 검증
```

### 1-3. SQL literal escape 처리

SQL 문자열에 들어가는 사용자 입력값은 single quote를 escape해야 한다.

예시는 다음과 같다.

```java
private String sqlLiteral(String value) {
    return value.replace("'", "''");
}
```

적용 대상은 다음이다.

| Service | 대상 값 |
|---|---|
| `MenuService` | `menuNm`, `progrmFileNm`, `url`, `stre` |
| `AuthService` | `programNm`, `domain`, `rolePttrn`, `roleCode` |

현재 `StringBuilder.append()`로 직접 입력값을 붙이는 부분을 escape 함수로 감싼다.

주의할 점은 숫자 컬럼에 들어가는 값은 escape하지 않고 숫자 검증으로 처리한다.

### 1-4. 즉시 제거할 데드 코드

`MenuService.generateMenuInsertSql()`에는 `storePath` 데드 코드가 있다.

현재 형태는 다음 문제를 가진다.

```java
String storePath = prefix.substring(prefix.lastIndexOf("/") + 1).isEmpty()
    ? prefix + "/" : prefix + "/";
```

문제는 다음이다.

- 조건식의 두 분기가 모두 `prefix + "/"`를 반환한다.
- 계산된 `storePath` 변수는 이후 사용되지 않는다.
- 실제 SQL에는 별도 변수 `stre`가 사용된다.

따라서 Phase 1에서 입력 검증/escape와 함께 제거한다.

### 1-5. AuthService keyword null 처리 순서 정리

`AuthService.getProgramList()`는 현재 `keyword` null 체크 전에 `like` 문자열을 먼저 만든다.

```java
String like = "%" + keyword + "%";
```

실제 런타임 오류는 발생하지 않더라도, `keyword == null`일 때 `"%null%"`이 만들어지므로 코드 의도가 불명확하다.

따라서 다음처럼 분기 이후에 `like`를 생성하도록 정리한다.

```text
keyword null/blank
  → 전체 목록 50건

keyword 존재
  → like 생성 후 검색
```

## Phase 2. Spec + SqlPlan 도입

`SqlPlan`은 `SqlBuilder`와 `ResultBuilder` 사이의 중간 산출물이다.

따라서 너무 늦게 도입하면 이후 리팩터링 방향이 흐려진다.

이 계획에서는 Phase 2에서 `Spec`과 함께 `SqlPlan` 모델을 먼저 정의한다.

### 2-1. Spec 도입

입력값 정규화를 위해 다음 record를 도입한다.

```java
MenuRegistrationSpec
AuthRegistrationSpec
```

역할은 다음이다.

- null / blank 처리 결과를 명확히 표현
- `urlPrefix` 정규화
- `upperMenuNo` 숫자 변환
- `progrmFileNm` 입력 관례 정리
- `domain`, `programNm`, `menuNm` trim 처리

### 2-2. SqlPlan 도입

공통 SQL 결과 모델을 도입한다.

예시는 다음과 같다.

```java
public record SqlPlan(
    String title,
    List<String> statements,
    List<String> warnings,
    List<String> nextSteps
) {}
```

초기에는 `MenuService`, `AuthService`가 내부적으로 `SqlPlan`을 만든 뒤 문자열로 렌더링해도 된다.

이렇게 하면 기존 Tool 반환 타입 `String`은 유지하면서, 테스트에서는 SQL 본문과 경고/후속 단계를 분리해서 검증할 수 있다.

### 2-3. 도입 위치

권장 패키지는 다음이다.

```text
src/main/java/com/krdevops/springai/model/sql/SqlPlan.java
src/main/java/com/krdevops/springai/model/menu/MenuRegistrationSpec.java
src/main/java/com/krdevops/springai/model/auth/AuthRegistrationSpec.java
```

패키지 수를 줄이고 싶다면 초기에는 다음도 가능하다.

```text
src/main/java/com/krdevops/springai/model/SqlPlan.java
src/main/java/com/krdevops/springai/model/MenuRegistrationSpec.java
src/main/java/com/krdevops/springai/model/AuthRegistrationSpec.java
```

## Phase 3. Validator / SqlBuilder 분리

Phase 1에서 private method로 추가한 검증/escape 로직을 구조화한다.

### 3-1. Validator 분리

다음 클래스를 도입한다.

```text
MenuInputValidator
AuthInputValidator
```

역할은 다음이다.

- null / blank 검증
- 숫자형 입력 검증
- URL prefix 형식 검증
- 프로그램 파일명 형식 검증

### 3-2. SqlBuilder 분리

다음 클래스를 도입한다.

```text
MenuSqlBuilder
AuthSqlBuilder
```

역할은 다음이다.

- SQL literal escape
- INSERT SQL 조립
- URL 생성
- 저장 경로 생성
- `ROLE_PTTRN` 생성
- `SqlPlan` 생성

Phase 3의 `SqlBuilder`는 우선 MySQL/MariaDB 기준 SQL을 생성한다.

DB 방언별 SQL 분기는 Phase 4에서 `DbDialect`와 `SqlDialectRenderer`가 도입된 뒤 반영한다.

즉, 구현 흐름은 다음과 같이 명시한다.

```text
Phase 3:
  MenuSqlBuilder / AuthSqlBuilder 분리
  → MySQL/MariaDB 기준 SQL 생성

Phase 4:
  DbDialect / SqlDialectRenderer 도입
  → SqlBuilder가 SqlDialectRenderer에 방언별 SQL 생성을 위임하도록 수정
```

이 패스를 명시하는 이유는 Phase 3에서 만든 `SqlBuilder`가 Phase 4에서 한 번 더 수정되어야 하기 때문이다.

`ROLE_PTTRN` 생성은 Security 접근 제어의 핵심 규칙이므로, 필요하면 다음 별도 클래스로 분리한다.

```text
RolePatternFactory
```

`RolePatternFactory` 추출 조건은 다음으로 둔다.

- Phase 5의 `ROLE_PTTRN` 저장/해석/매칭 검증 로직이 `AuthSqlBuilder`를 과도하게 키우는 경우
- positive/negative URL 매칭 테스트를 위한 패턴 생성 API가 별도로 필요해지는 경우
- `ROLE_PTTRN` 생성 규칙이 DB 방언 또는 Security 버전에 따라 분기되는 경우

초기에는 `AuthSqlBuilder` 내부 메서드로 시작해도 되지만, 위 조건 중 하나라도 충족되면 `RolePatternFactory`로 분리한다.

## Phase 4. Repository + DB Dialect 전략

이 Phase에서는 DB 조회 책임과 DB 방언 문제를 분리한다.

### 4-1. Repository 분리

다음 클래스를 도입한다.

```text
MenuRepository
AuthRepository
```

Repository는 다음을 담당한다.

- 메뉴 조회
- 프로그램 조회
- max 값 조회
- 중복 조회

### 4-2. MenuTool 중복 검증 강화

#### upperMenuNo 존재 여부 확인

`generateMenuInsertSql()` 실행 전에 `COMTNMENUINFO`에서 상위 메뉴 존재 여부를 확인한다.

검증 SQL 예시는 다음과 같다.

```sql
SELECT COUNT(*) FROM COMTNMENUINFO WHERE MENU_NO = ?
```

상위 메뉴가 없으면 SQL을 생성하지 않고 안내 메시지를 반환한다.

#### PROGRM_FILE_NM 중복 확인

`COMTNPROGRMLIST.PROGRM_FILE_NM`은 PK 성격이므로 중복 확인이 필요하다.

검증 SQL 예시는 다음과 같다.

```sql
SELECT COUNT(*) FROM COMTNPROGRMLIST WHERE PROGRM_FILE_NM = ?
```

중복이면 신규 INSERT SQL을 만들지 않고 기존 프로그램 확인 안내를 반환한다.

#### URL 중복 확인

동일 URL이 이미 등록되어 있으면 중복 메뉴나 잘못된 프로그램 연결이 될 수 있다.

검증 SQL 예시는 다음과 같다.

```sql
SELECT COUNT(*) FROM COMTNPROGRMLIST WHERE URL = ?
```

중복이면 SQL 생성 전 경고한다.

### 4-3. DB Dialect 전략 수립

기존 계획의 "MySQL/MariaDB 기준임을 명시"만으로는 부족하다.

eGovFrame 공공 SI 환경에서는 Oracle 사용 가능성이 높으므로 DB 방언 문제를 명시적 구현 이슈로 올린다.

우선 대상 차이는 다음이다.

| 기능 | MySQL/MariaDB | Oracle 예시 |
|---|---|---|
| 목록 제한 | `LIMIT 50` | `FETCH FIRST 50 ROWS ONLY` 또는 `ROWNUM <= 50` |
| 현재 시각 | `NOW()` | `SYSDATE` 또는 `SYSTIMESTAMP` |
| ROLE_CODE 숫자 추출 | `CAST(SUBSTRING(ROLE_CODE, 5) AS UNSIGNED)` | `TO_NUMBER(REGEXP_SUBSTR(ROLE_CODE, '[0-9]+$'))` |

구현 옵션은 다음이다.

| 옵션 | 설명 | 판단 |
|---|---|---|
| A. Tool 파라미터에 `dbType` 추가 | `mysql`, `mariadb`, `oracle` 등을 명시 입력 | 가장 명확하지만 기존 Tool 시그니처 변경 |
| B. 설정값으로 DB 방언 결정 | `spring.datasource` 또는 application 설정에서 결정 | Tool 호출은 단순하지만 설정 의존 |
| C. DB metadata로 감지 | JDBC metadata에서 productName 감지 | 자동화 가능하지만 테스트와 예외 처리 필요 |
| D. SQL 2종 병기 | MySQL/MariaDB SQL과 Oracle SQL을 모두 출력 | 즉시 안전하지만 출력이 길어짐 |

초기 구현 권장안은 다음이다.

```text
Phase 4-1: 내부 DbDialect enum 도입
Phase 4-2: 기본값은 MYSQL_MARIADB
Phase 4-3: Oracle SQL 생성 메서드 병행 구현
Phase 4-4: Tool description에 dbType 확장 예정 명시
Phase 4-5: 후속 버전에서 dbType 파라미터 추가 또는 설정 기반 감지 선택
```

기존 Tool 시그니처를 유지해야 하므로, 이번 1차 리팩터링에서는 `DbDialect` 내부 기본값과 Oracle 대응 메서드/테스트를 먼저 둔다.

추후 실제 운영 적용 범위가 확정되면 `dbType` 파라미터 추가 여부를 결정한다.

## Phase 5. ResultBuilder 분리 + AuthTool 권한 생성 안정화

이 Phase에서는 사용자에게 반환하는 메시지 생성을 분리한다.

대상 클래스는 다음이다.

```text
MenuResultBuilder
AuthResultBuilder
```

ResultBuilder는 `SqlPlan`을 받아 문자열을 만든다.

### 5-1. ROLE_CODE 계산 결과 안내 강화

현재 `web-NNNNNN` 형식으로 다음 role code를 계산한다.

이 방식은 SQL 생성 시점과 실제 실행 시점 사이에 다른 사용자가 INSERT하면 중복될 수 있다.

따라서 출력에 다음 안내를 추가한다.

```text
※ ROLE_CODE는 SQL 생성 시점의 최대값 기준입니다.
※ SQL 실행 직전 COMTNROLEINFO 중복 여부를 다시 확인하세요.
```

### 5-2. ROLE_PTTRN 생성 규칙 명시

출력 SQL 상단에 생성된 URL 패턴 의미를 설명한다.

예시는 다음과 같다.

```text
URL prefix:
  /emp/employer

생성 ROLE_PTTRN:
  \A/emp/employer/.*\.do.*\Z

적용 대상 예:
  /emp/employer/EgovEmployerList.do
```

### 5-2-1. ROLE_PTTRN 실제 동작 검증

`ROLE_PTTRN`은 단순 안내 문자열이 아니라 Spring Security URL 접근 제어의 핵심 값이다.

현재 생성 방식은 다음과 같은 형태다.

```java
String rolePttrn = "\\\\A" + prefix + "/.*\\\\.do.*\\\\Z";
```

이 값은 다음 단계를 거친다.

```text
Java 문자열
  → SQL literal
  → DB 저장값
  → securityMapper 조회 결과
  → Security Filter / MetadataSource 패턴 해석
```

따라서 최종적으로 Security 쪽에서 `\A`, `\.`, `\Z`가 의도한 정규식으로 해석되는지 검증해야 한다.

검증 항목은 다음이다.

- Java 문자열에서 생성되는 실제 SQL 출력값
- DB에 저장될 문자열
- securityMapper가 조회한 문자열
- Security Filter 또는 MetadataSource가 해당 문자열을 regex로 해석하는 방식
- `/emp/employer/EgovEmployerList.do`가 매칭되는지
- `/emp/other/EgovEmployerList.do`가 매칭되지 않는지

이 검증은 Phase 5 또는 별도 테스트/문서화 항목으로 처리한다.

### 5-2-2. ROLE_SORT 타입 정리

현재 `ROLE_SORT`는 문자열 literal로 출력될 가능성이 있다.

예시는 다음과 같다.

```java
sb.append("    '").append(nextRoleNum).append("',\n");
```

`ROLE_SORT`가 숫자 컬럼이면 `'1'`이 아니라 `1`로 출력하는 것이 맞다.

따라서 다음을 확인한다.

- 실제 `COMTNROLEINFO.ROLE_SORT` 컬럼 타입
- MySQL/MariaDB와 Oracle에서 문자열 숫자 입력 허용 여부
- 숫자 컬럼이면 SQL 출력에서 quote 제거

권장 기본값은 숫자 출력이다.

### 5-3. securityMapper 의존성 안내 추가

`generateAuthInsertSql()` 반환값에 다음 안내를 추가한다.

```text
※ 이 SQL은 securityMapper가 COMTNROLEINFO / COMTNAUTHORROLERELATE를 조회할 때만 Security에 반영됩니다.
※ SecurityTemplateTool에서 securityMapper 포함 조합을 먼저 생성했는지 확인하세요.
```

## Phase 6. WorkflowDefinition 기반화

### 6-1. WorkflowGuideTool 확장 방식

Workflow 확장 방식은 두 가지가 있다.

| 방식 | 형태 | 장점 | 단점 |
|---|---|---|---|
| A. 범용 메서드 | `suggestNextStep(String workflowType, String currentContext)` | workflow가 늘어나도 메서드 수가 늘지 않음 | 기존 호출자 호환성 검토 필요 |
| B. 전용 메서드 추가 | `suggestSecurityMenuAuthWorkflow(String currentContext)` | 기존 CRUD 호출을 깨지 않음 | workflow마다 Tool 메서드가 늘어남 |

이번 구현에서는 방식 B를 우선 적용한다.

이유는 기존 `suggestNextStep(String currentContext)`가 CRUD workflow로 사용되고 있으므로, 1차 리팩터링에서 호출자 혼란을 줄이는 것이 더 중요하기 때문이다.

후보 메서드명은 다음이다.

```java
suggestSecurityMenuAuthWorkflow(String currentContext)
```

역할은 다음이다.

- 현재 상황을 입력받아 다음 단계 안내
- SecurityTemplateTool 적용 여부 확인
- `securityMapper` 포함 여부 확인
- MenuTool / AuthTool 호출 순서 안내
- SQL 실행 후 재기동/캐시 갱신 안내
- 접근 테스트 체크리스트 제공

### 6-2. 방식 A로 전환하는 조건

방식 B는 영구 구조가 아니라 과도기 구조로 본다.

다음 조건 중 하나 이상이 충족되면 방식 A로 전환한다.

- workflow 종류가 3개 이상으로 늘어난다.
- `suggest*Workflow()` 메서드가 2개 이상 추가된다.
- Tool description이 중복되어 유지보수 비용이 커진다.
- 클라이언트 또는 AI 호출부가 `workflowType` 파라미터를 안정적으로 전달할 수 있다.

전환 시 목표 형태는 다음이다.

```java
suggestNextStep(String workflowType, String currentContext)
```

기존 호환을 위해 `workflowType`이 null/blank이면 `"crud"`로 처리한다.

### 6-3. WorkflowGuideService 확장

`WorkflowGuideService`에 Security/Menu/Auth 전용 workflow를 추가한다.

권장 단계는 다음이다.

```text
1. SecurityTemplateTool로 Security 기반 생성
2. securityMapper 생성 여부 확인
3. MenuTool.getMenuStructure()로 상위 메뉴 확인
4. AuthTool.getProgramList()로 프로그램 중복 확인
5. MenuTool.generateMenuInsertSql()로 메뉴 SQL 생성
6. AuthTool.generateAuthInsertSql()로 권한 SQL 생성
7. SQL 실행 순서 안내
8. 서버 재기동 또는 Security 캐시 갱신
9. 메뉴 노출 / URL 접근 테스트
```

### 6-4. WorkflowDefinition 구조 도입

다음 구조를 도입한다.

```text
WorkflowDefinition
WorkflowStep
WorkflowDefinitionRegistry
WorkflowProgressDetector
WorkflowGuideRenderer
```

초기에는 기존 CRUD workflow와 Security/Menu/Auth workflow를 모두 registry에 등록한다.

상호 작용 흐름은 다음과 같다.

```text
currentContext
  → WorkflowProgressDetector
    → 현재 완료 단계 판단

workflowType
  → WorkflowDefinitionRegistry
    → 해당 WorkflowDefinition 조회

WorkflowDefinition + 완료 단계
  → WorkflowGuideRenderer
    → 진행률, 다음 단계, 남은 단계 안내 문자열 생성
```

각 구성 요소의 책임은 다음이다.

| 구성 요소 | 책임 |
|---|---|
| `WorkflowDefinition` | workflowType, 전체 단계 목록 보관 |
| `WorkflowStep` | 단계 번호, 이름, 추천 Tool, 설명, 감지 키워드 보관 |
| `WorkflowDefinitionRegistry` | workflowType으로 workflow 정의 조회 |
| `WorkflowProgressDetector` | currentContext와 감지 키워드로 완료 단계 판단 |
| `WorkflowGuideRenderer` | 완료 단계와 workflow 정의를 사용자 안내 문자열로 렌더링 |

## Phase 7. 테스트 / description / 문서 업데이트

### 7-1. MenuServiceTest 추가

테스트 대상은 다음이다.

- 정상 입력 시 `COMTNPROGRMLIST` SQL 생성
- 정상 입력 시 `COMTNMENUINFO` SQL 생성
- `MENU_NO`, `MENU_ORDR` 계산 검증
- `upperMenuNo` null / blank / 비숫자 입력 검증
- `urlPrefix` 형식 검증
- `menuNm` single quote escape 검증
- `PROGRM_FILE_NM` 중복 시 SQL 생성 차단
- URL 중복 시 SQL 생성 차단
- `storePath` 데드 코드 제거 후 SQL 출력 동일성 검증
- MySQL/MariaDB SQL과 Oracle SQL 차이 검증

### 7-2. AuthServiceTest 추가

테스트 대상은 다음이다.

- 정상 입력 시 `COMTNROLEINFO` SQL 생성
- 정상 입력 시 `COMTNAUTHORROLERELATE` SQL 생성
- `ROLE_CODE` 계산 검증
- `ROLE_PTTRN` 생성 검증
- `ROLE_PTTRN` SQL 출력값의 backslash 보존 검증
- `ROLE_PTTRN` positive/negative URL 매칭 검증
- `ROLE_SORT` 숫자 출력 검증
- `urlPrefix` 형식 검증
- `programNm`, `domain` single quote escape 검증
- securityMapper 의존성 안내 문구 포함 검증
- MySQL/MariaDB `ROLE_CODE` 계산 SQL과 Oracle 계산 SQL 차이 검증

### 7-3. WorkflowGuideServiceTest 추가

테스트 대상은 다음이다.

- 빈 context 입력 시 전체 Security/Menu/Auth 워크플로우 반환
- Security 완료 context 입력 시 다음 단계로 securityMapper 확인 안내
- 메뉴 SQL 생성 완료 context 입력 시 다음 단계로 AuthTool 안내
- 권한 SQL 생성 완료 context 입력 시 재기동/검증 안내

### 7-4. MenuTool description 업데이트

추가해야 할 내용은 다음이다.

- SQL 직접 실행하지 않음
- SQL 실행 전 중복 확인 필요
- URL과 Controller mapping 일치 필요
- DB 방언 기본값과 Oracle 대응 정책

### 7-5. AuthTool description 업데이트

추가해야 할 내용은 다음이다.

- `securityMapper` 선행 필요
- URL prefix와 실제 Controller URL 일치 필요
- SQL 실행 후 재기동 또는 캐시 갱신 필요
- ROLE_CODE는 생성 시점 기준이므로 실행 전 중복 재확인 필요
- DB 방언 기본값과 Oracle 대응 정책

### 7-6. WorkflowGuideTool description 업데이트

Security/Menu/Auth 워크플로우 안내 Tool임을 명시한다.

## 5. 파일별 구현 대상

| 파일 | 작업 |
|---|---|
| `src/main/java/com/krdevops/springai/model/SqlPlan.java` | SQL 본문/경고/후속조치 모델 추가 |
| `src/main/java/com/krdevops/springai/model/MenuRegistrationSpec.java` | MenuTool 입력값 정규화 모델 추가 |
| `src/main/java/com/krdevops/springai/model/AuthRegistrationSpec.java` | AuthTool 입력값 정규화 모델 추가 |
| `src/main/java/com/krdevops/springai/service/MenuService.java` | 조율자 역할로 축소 |
| `src/main/java/com/krdevops/springai/service/AuthService.java` | 조율자 역할로 축소 |
| `src/main/java/com/krdevops/springai/service/menu/MenuInputValidator.java` | 메뉴 입력 검증 |
| `src/main/java/com/krdevops/springai/service/menu/MenuSqlBuilder.java` | 메뉴 SQL/SqlPlan 생성 |
| `src/main/java/com/krdevops/springai/service/menu/MenuRepository.java` | 메뉴/프로그램 DB 조회 |
| `src/main/java/com/krdevops/springai/service/menu/MenuResultBuilder.java` | 메뉴 SQL 결과 문자열 생성 |
| `src/main/java/com/krdevops/springai/service/auth/AuthInputValidator.java` | 권한 입력 검증 |
| `src/main/java/com/krdevops/springai/service/auth/AuthSqlBuilder.java` | 권한 SQL/SqlPlan 생성 |
| `src/main/java/com/krdevops/springai/service/auth/AuthRepository.java` | 프로그램/권한 DB 조회 |
| `src/main/java/com/krdevops/springai/service/auth/AuthResultBuilder.java` | 권한 SQL 결과 문자열 생성 |
| `src/main/java/com/krdevops/springai/service/sql/DbDialect.java` | DB 방언 enum |
| `src/main/java/com/krdevops/springai/service/sql/SqlDialectRenderer.java` | MySQL/MariaDB 및 Oracle SQL 차이 처리 |
| `src/main/java/com/krdevops/springai/tools/MenuTool.java` | Tool description 보강 |
| `src/main/java/com/krdevops/springai/tools/AuthTool.java` | Tool description 보강 |
| `src/main/java/com/krdevops/springai/tools/WorkflowGuideTool.java` | Security/Menu/Auth 워크플로우 Tool 메서드 추가 |
| `src/main/java/com/krdevops/springai/service/WorkflowGuideService.java` | WorkflowDefinition 기반 조율 |
| `src/main/java/com/krdevops/springai/service/workflow/*` | WorkflowDefinition, Registry, Detector, Renderer |
| `src/test/java/com/krdevops/springai/service/MenuServiceTest.java` | 신규 테스트 |
| `src/test/java/com/krdevops/springai/service/AuthServiceTest.java` | 신규 테스트 |
| `src/test/java/com/krdevops/springai/service/WorkflowGuideServiceTest.java` | 신규 테스트 또는 기존 테스트 확장 |
| `docs/Security_Menu_Auth_구현순서.md` | 필요 시 구현 완료 내용 반영 |

## 6. 구현 우선순위

실제 작업은 다음 순서로 진행한다.

```text
1. SecurityTemplateTool 현재 구현 상태를 기준점으로 확정
2. 기존 Tool 메서드 시그니처 유지 확인
3. MenuService / AuthService 입력 검증과 SQL escape 1차 반영
4. MenuService storePath 데드 코드 제거
5. AuthService keyword null 처리 순서 정리
6. ROLE_SORT 숫자 출력 여부 확인 및 반영
7. ROLE_PTTRN 출력/매칭 검증 추가
8. MenuRegistrationSpec / AuthRegistrationSpec 도입
9. SqlPlan 도입
10. MenuInputValidator / AuthInputValidator 분리
11. MenuSqlBuilder / AuthSqlBuilder 분리
12. Phase 3 SqlBuilder는 MySQL/MariaDB 기준으로 우선 동작하게 정리
13. MenuRepository / AuthRepository 분리
14. DbDialect / SQL 방언 처리 전략 도입
15. SqlBuilder가 SqlDialectRenderer에 위임하도록 업데이트
16. MenuResultBuilder / AuthResultBuilder 분리
17. WorkflowGuideTool에 suggestSecurityMenuAuthWorkflow() 추가
18. WorkflowDefinition 기반 구조 도입
19. MenuServiceTest / AuthServiceTest / WorkflowGuideServiceTest 추가
20. Tool description 업데이트
21. 전체 테스트 실행
22. 구현 완료 검토 문서 작성
```

이 순서는 "검증·escape 먼저"와 "SqlPlan 기반 리팩터링"을 절충한 순서다.

최초 안전성 보강은 빠르게 수행하되, Phase 2에서 `Spec`과 `SqlPlan`을 바로 도입하여 이후 구조 추출의 방향을 고정한다.

## 7. 완료 기준

구현 완료 기준은 다음이다.

### 7.1 기능 완료 기준

- `MenuTool`이 잘못된 입력에 대해 명확한 오류 또는 안내를 반환한다.
- `MenuTool`이 중복 프로그램/URL에 대해 SQL 생성을 차단하거나 명확히 경고한다.
- `AuthTool`이 잘못된 URL prefix에 대해 명확한 오류 또는 안내를 반환한다.
- `AuthTool`이 생성한 SQL에 `securityMapper` 선행 조건과 재기동/캐시 갱신 안내가 포함된다.
- `WorkflowGuideTool`이 Security → Menu → Auth 순서를 안내한다.
- `SqlPlan`을 통해 SQL 본문, 경고, 후속 단계가 분리된다.
- `WorkflowDefinition`을 통해 CRUD workflow와 Security/Menu/Auth workflow가 같은 구조로 관리된다.

### 7.2 안전성 완료 기준

- 사용자 입력값에 single quote가 포함되어도 SQL이 깨지지 않는다.
- null / blank 입력으로 NPE가 발생하지 않는다.
- 숫자형 입력 오류가 `NumberFormatException`으로 그대로 노출되지 않는다.
- SQL 실행 전 재확인해야 할 중복 위험이 출력에 명시된다.
- MySQL/MariaDB와 Oracle SQL 차이가 구현 이슈로 분리되어 있다.
- 최소한 Oracle 대응 SQL 생성 또는 Oracle 대응 TODO가 테스트 가능한 구조에 존재한다.
- `ROLE_PTTRN`의 backslash 보존과 regex 매칭이 테스트로 확인된다.
- 숫자 컬럼 후보인 `ROLE_SORT`는 quote 없이 출력되거나, 문자열 출력이 필요한 근거가 문서화된다.
- 사용되지 않는 `storePath` 데드 코드가 제거된다.

### 7.3 테스트 완료 기준

- `MenuServiceTest` 추가
- `AuthServiceTest` 추가
- `WorkflowGuideServiceTest` 추가 또는 기존 테스트 확장
- 기존 `SecurityTemplateServiceTest` 통과
- 기존 `SecurityFilePlanFactoryTest` 통과
- 기존 `SecurityTemplateRendererIntegrationTest` 통과
- 전체 `./gradlew test` 통과

## 8. 리스크 및 주의사항

### 8.1 DB 방언 의존성

현재 일부 SQL은 MySQL/MariaDB 문법에 가깝다.

예를 들어 다음 요소는 DB별 차이가 있다.

- `LIMIT 50`
- `NOW()`
- `CAST(... AS UNSIGNED)`

기존 계획처럼 "MySQL/MariaDB 기준임을 명시"하는 것만으로는 부족하다.

이번 구현 계획에서는 DB 방언 문제를 Phase 4의 명시적 구현 대상으로 둔다.

초기 구현의 최소 완료 기준은 다음이다.

- `DbDialect` 모델을 둔다.
- 기본 방언을 MySQL/MariaDB로 유지한다.
- Oracle에서 달라지는 SQL 지점을 코드 또는 테스트에서 명확히 드러낸다.
- 후속 버전에서 `dbType` 파라미터 추가 또는 JDBC metadata 기반 감지 중 하나를 선택할 수 있게 한다.

장기적으로는 다음 중 하나를 선택해야 한다.

| 선택지 | 설명 |
|---|---|
| `dbType` 파라미터 추가 | Tool 호출 시 DB 종류를 명시한다. |
| 설정 기반 결정 | application 설정 또는 datasource 설정에서 DB 종류를 결정한다. |
| JDBC metadata 감지 | 현재 연결된 DB productName으로 자동 감지한다. |
| SQL 병기 | MySQL/MariaDB와 Oracle SQL을 함께 출력한다. |

### 8.2 SQL 생성 시점과 실행 시점 차이

`MENU_NO`, `MENU_ORDR`, `ROLE_CODE`는 생성 시점의 DB 최대값 기준으로 계산된다.

SQL 실행 전에 다른 데이터가 추가되면 중복될 수 있다.

따라서 출력 문구에 실행 직전 재확인 안내를 포함해야 한다.

### 8.3 ROLE_PTTRN 저장/해석 리스크

`ROLE_PTTRN`은 Java 문자열, SQL 문자열, DB 저장값, Security 런타임 해석을 모두 통과한다.

따라서 backslash가 어느 단계에서 하나 줄거나 과하게 남으면 접근 제어가 의도와 다르게 동작할 수 있다.

특히 다음 값이 실제로 어떻게 보존되는지 확인해야 한다.

```text
\A
\.do
\Z
```

검증은 단순 문자열 포함 검사가 아니라 positive/negative URL 매칭 테스트까지 포함한다.

### 8.4 Workflow Tool의 책임 범위

Workflow Tool은 실제 파일 생성이나 SQL 생성을 직접 수행하지 않는다.

Workflow Tool은 다음 역할만 담당한다.

- 다음 호출 Tool 안내
- 선행 조건 안내
- 검증 체크리스트 제공
- SQL 실행 순서 안내

### 8.5 Workflow 확장 방식 리스크

이번 구현은 기존 호환성을 위해 `suggestSecurityMenuAuthWorkflow(String currentContext)`를 추가하는 방식 B를 우선 선택한다.

하지만 workflow가 늘어나면 Tool 메서드가 계속 증가할 수 있다.

따라서 다음 조건이 발생하면 범용 방식 A로 전환한다.

```java
suggestNextStep(String workflowType, String currentContext)
```

전환 조건은 다음이다.

- workflow 종류가 3개 이상이 된다.
- 전용 workflow Tool 메서드가 2개 이상 추가된다.
- Tool description 중복이 커진다.
- AI 또는 클라이언트가 `workflowType`을 안정적으로 전달할 수 있다.

## 9. 최종 판단

이 구현 계획의 최종 방향은 다음이다.

> SecurityTemplateTool은 보안 기반을 만든다.  
> MenuTool은 메뉴/프로그램 SQL을 안전하게 만든다.  
> AuthTool은 URL 권한 SQL을 안전하게 만든다.  
> WorkflowGuideTool은 세 Tool의 순서를 통제한다.

따라서 이번 구현의 핵심은 Tool 추가가 아니라, 기존 Tool 간 관계를 안전하고 예측 가능하게 만드는 것이다.

최종 구현 전략은 다음으로 확정한다.

> `SqlPlan`을 처음부터 완전 구조로 강제하지는 않는다.  
> 그러나 Phase 2에서 `Spec`과 함께 `SqlPlan`을 정의해 이후 리팩터링 방향을 고정한다.  
> 먼저 검증·escape로 즉시 위험을 줄이고, 이후 Validator / SqlBuilder / Repository / ResultBuilder로 추출한다.

---

## 10. 구현 완료 기록

> 완료일: 2026-06-10  
> 커밋: `d64ebae` feat: Security_Menu_Auth Tool 안전성 강화 및 Workflow 구조화

### 10-1. Phase별 완료 상태

| Phase | 내용 | 상태 |
| --- | --- | --- |
| Phase 0 | SecurityTemplateTool 기준 확정 | 완료 (이전 세션) |
| Phase 1 | 시그니처 유지 + 안전성 보강 | 완료 |
| Phase 2 | Spec + SqlPlan 도입 | 완료 |
| Phase 3 | Validator / SqlBuilder 분리 | 완료 |
| Phase 4 | Repository + DB Dialect 전략 | 완료 |
| Phase 5 | ResultBuilder 분리 + AuthTool 안정화 | 완료 |
| Phase 6 | WorkflowDefinition 기반화 | 완료 |
| Phase 7 | 테스트 / description / 문서 | 완료 |

### 10-2. 생성된 주요 파일

| 파일 | 역할 |
| --- | --- |
| `model/SqlPlan.java` | SQL 본문 / 경고 / 후속조치 record |
| `model/MenuRegistrationSpec.java` | 메뉴 등록 입력 정규화 record |
| `model/AuthRegistrationSpec.java` | 권한 등록 입력 정규화 record |
| `service/sql/DbDialect.java` | DB 방언 enum (MYSQL_MARIADB, ORACLE) |
| `service/sql/SqlDialectRenderer.java` | 방언별 SQL 표현 렌더러 |
| `service/menu/MenuInputValidator.java` | 메뉴 입력 검증 |
| `service/menu/MenuRepository.java` | 메뉴/프로그램 DB 조회 |
| `service/menu/MenuSqlBuilder.java` | 메뉴 INSERT SQL 조립 + SqlPlan 생성 |
| `service/menu/MenuResultBuilder.java` | SqlPlan → 문자열 렌더링 |
| `service/auth/AuthInputValidator.java` | 권한 입력 검증 |
| `service/auth/AuthRepository.java` | 권한/프로그램 DB 조회 |
| `service/auth/AuthSqlBuilder.java` | 권한 INSERT SQL 조립 + ROLE_PTTRN 생성 |
| `service/auth/AuthResultBuilder.java` | SqlPlan → 문자열 렌더링 |
| `service/workflow/WorkflowDefinition.java` | 워크플로우 정의 record |
| `service/workflow/WorkflowStep.java` | 워크플로우 단계 record |
| `service/workflow/WorkflowDefinitionRegistry.java` | CRUD + Security-Menu-Auth 워크플로우 등록 |
| `service/workflow/WorkflowProgressDetector.java` | context 키워드로 완료 단계 감지 |
| `service/workflow/WorkflowGuideRenderer.java` | 워크플로우 진행 안내 렌더링 |

### 10-3. 수정된 파일

| 파일 | 주요 변경 |
| --- | --- |
| `service/MenuService.java` | JdbcTemplate 제거, 추출 클래스에 위임, storePath 데드코드 제거 |
| `service/AuthService.java` | JdbcTemplate 제거, keyword null 순서 정리 |
| `service/WorkflowGuideService.java` | WorkflowDefinitionRegistry 기반 재구성 |
| `tools/WorkflowGuideTool.java` | `suggestSecurityMenuAuthWorkflow()` 추가 |
| `tools/MenuTool.java` | description 보강 (중복 검증, Oracle 대응 예정) |
| `tools/AuthTool.java` | description 보강 (securityMapper 선행, ROLE_CODE 경고) |

### 10-4. 테스트 결과

| 테스트 클래스 | 개수 | 결과 |
| --- | --- | --- |
| `MenuServiceTest` | 11 | 통과 |
| `AuthServiceTest` | 12 | 통과 |
| `WorkflowGuideServiceTest` | 4 | 통과 |
| `SecurityFilePlanFactoryTest` | 기존 | 통과 |
| `SecurityTemplateRendererIntegrationTest` | 기존 | 통과 |
| `SecurityTemplateServiceTest` | 기존 | 통과 |
| **전체** `./gradlew test` | | **BUILD SUCCESSFUL** |

### 10-5. 잔여 후속 항목

| 항목 | 분류 | 기준 |
| --- | --- | --- |
| `dbType` 파라미터 추가 또는 JDBC metadata 자동 감지 | Phase 4 후속 | 실제 Oracle 운영 적용 범위 확정 후 결정 |
| `RolePatternFactory` 별도 추출 | Phase 5 후속 | ROLE_PTTRN 로직 확장 시 클래스 크기 기준으로 결정 |
| WorkflowGuideTool 방식 A 전환 | Phase 6 후속 | workflow 종류 3개 이상 또는 suggestXxx 메서드 2개 이상 시 전환 |
