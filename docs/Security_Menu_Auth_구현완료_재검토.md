# Security Menu Auth 구현완료 재검토

## 1. 검토 대상

기준 문서:

- `docs/Security_Menu_Auth_구현계획서.md`
- `docs/Security_Menu_Auth_구현완료_검토.md`

검토 대상 구현:

- `MenuTool`
- `AuthTool`
- `WorkflowGuideTool`
- `MenuService`
- `AuthService`
- `WorkflowGuideService`
- `Menu/Auth/Workflow` 하위 구조화 클래스
- 관련 테스트

## 2. 전체 판단

현재 구현은 계획서 기준으로 거의 완료 상태로 볼 수 있다.

이전 검토에서 P1으로 지적했던 `MenuSqlBuilder`의 eGovFrame 스키마 컬럼 불일치는 수정되었다.

또한 Workflow 진행 감지의 비연속 입력 문제도 보완되었다.

남은 항목은 P2/P3 수준의 후속 정리다.

```text
1. DB Dialect 선택 경로를 실제 Tool/설정/JDBC 감지 중 하나로 연결
2. COMTNAUTHORROLERELATE INSERT에 CREAT_DT 포함 여부 결정
```

## 3. 이전 P1 이슈 재검토

## [해소] MenuSqlBuilder 스키마 컬럼 불일치

### 이전 문제

이전 구현에서는 `MenuSqlBuilder`가 실제 eGovFrame 스키마와 다른 컬럼명을 사용했다.

문제였던 부분은 다음이다.

```text
COMTNPROGRMLIST.STRE_PATH
COMTNMENUINFO.URL
```

하지만 스키마 기준 올바른 컬럼은 다음이다.

```text
COMTNPROGRMLIST.PROGRM_STRE_PATH
COMTNMENUINFO.PROGRM_FILE_NM
```

### 현재 상태

현재 구현은 다음처럼 수정되었다.

`COMTNPROGRMLIST` INSERT:

```java
INSERT INTO COMTNPROGRMLIST (
    PROGRM_FILE_NM,
    PROGRM_STRE_PATH,
    PROGRM_KOREAN_NM,
    PROGRM_DC,
    URL
)
```

`COMTNMENUINFO` INSERT:

```java
INSERT INTO COMTNMENUINFO (
    MENU_NO,
    UPPER_MENU_NO,
    MENU_NM,
    PROGRM_FILE_NM,
    MENU_ORDR
)
```

### 판단

해소되었다.

이제 메뉴 SQL은 실제 eGovFrame 메뉴/프로그램 연결 방식에 맞게 생성된다.

## [해소] Workflow 비연속 완료 문맥 처리

### 이전 문제

이전 `WorkflowProgressDetector`는 1단계부터 연속으로 감지된 단계만 완료로 인정했다.

따라서 사용자가 다음처럼 현재 완료 작업만 입력하면 진행률이 0으로 판단될 수 있었다.

```text
메뉴 SQL 생성 완료
```

### 현재 상태

현재 구현은 연속 완료 단계가 없을 때 감지된 최대 단계를 완료 단계로 추정한다.

```java
if (maxContinuous > 0) {
    return maxContinuous;
}

return completed.stream().max(Integer::compareTo).orElse(0);
```

예:

```text
메뉴 SQL 생성 완료
  → 5단계 감지
  → 다음 단계로 권한 SQL 생성 안내
```

### 판단

해소되었다.

실사용자가 전체 히스토리를 입력하지 않고 현재 완료 작업만 입력해도 다음 단계 안내가 가능하다.

## 4. 남은 Findings

## [P2] DB Dialect가 실제 Tool 호출에서 MySQL/MariaDB로 고정됨

### 위치

- `src/main/java/com/krdevops/springai/service/MenuService.java`
- `src/main/java/com/krdevops/springai/service/AuthService.java`
- `src/main/java/com/krdevops/springai/service/sql/DbDialect.java`
- `src/main/java/com/krdevops/springai/service/sql/SqlDialectRenderer.java`

### 현재 상태

`DbDialect`와 `SqlDialectRenderer`는 구현되어 있다.

```java
public enum DbDialect {
    MYSQL_MARIADB,
    ORACLE
}
```

`SqlDialectRenderer`도 다음 차이를 처리한다.

- `LIMIT 50`
- `FETCH FIRST 50 ROWS ONLY`
- `NOW()`
- `SYSDATE`
- `CAST(SUBSTRING(...))`
- `TO_NUMBER(REGEXP_SUBSTR(...))`

하지만 `MenuService`, `AuthService`는 renderer를 MySQL/MariaDB로 고정 생성한다.

```java
private final SqlDialectRenderer renderer =
    new SqlDialectRenderer(DbDialect.MYSQL_MARIADB);
```

### 영향

현재 Tool 호출 경로에서는 Oracle SQL을 선택할 수 없다.

즉, Oracle 대응 코드는 존재하지만 실제 사용자 입력이나 설정으로 연결되어 있지 않다.

다만 Tool description에 다음 취지가 명시되어 있어, 운영자가 현재 기본값을 오해할 가능성은 줄어들었다.

```text
현재 MySQL/MariaDB 방언 기준.
Oracle 전환 시 DbDialect 설정 변경 필요.
```

### 판단

P2 후속 개선 항목이다.

현재 기본 동작이 MySQL/MariaDB로 명확히 고정되어 있고 Tool 설명에도 노출되어 있으므로 P1은 아니다.

### 권장 후속 방향

다음 중 하나를 선택해야 한다.

| 방식 | 설명 |
|---|---|
| Tool 파라미터 추가 | `dbType`을 입력받아 `mysql`, `mariadb`, `oracle` 중 선택 |
| 설정 기반 결정 | application 설정에서 DB 방언 결정 |
| JDBC metadata 감지 | datasource productName으로 DB 방언 자동 감지 |
| SQL 병기 | MySQL/MariaDB SQL과 Oracle SQL을 함께 출력 |

기존 Tool 시그니처를 유지하려면 설정 기반 또는 JDBC metadata 감지가 적절하다.

## [해소] COMTNAUTHORROLERELATE INSERT에 CREAT_DT가 생략됨

### 위치

- `src/main/java/com/krdevops/springai/service/auth/AuthSqlBuilder.java`

### 수정 내용

`buildAuthorRoleRelateSql()`에 `CREAT_DT` 컬럼 추가.
`SqlDialectRenderer.now()`를 사용하여 MySQL/Oracle 방언 대응.

```java
// 수정 후
INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT)
VALUES ('ROLE_ADMIN', 'web-000001', NOW());
```

ROLE_USER 주석 예시도 동일하게 반영.

### 판단

해소되었다.

커밋: `6614806` fix: COMTNAUTHORROLERELATE INSERT에 CREAT_DT 추가 (P3)

## 5. 잘 구현된 부분

다음 항목은 계획서와 이전 검토 기준을 충족한다.

- 기존 Tool 메서드 시그니처 유지
- `SqlPlan` 도입
- `MenuRegistrationSpec` 도입
- `AuthRegistrationSpec` 도입
- `MenuInputValidator` 도입
- `AuthInputValidator` 도입
- `MenuSqlBuilder` 도입
- `AuthSqlBuilder` 도입
- `MenuRepository` 도입
- `AuthRepository` 도입
- `MenuResultBuilder` 도입
- `AuthResultBuilder` 도입
- `DbDialect` 도입
- `SqlDialectRenderer` 도입
- `WorkflowDefinition` 도입
- `WorkflowStep` 도입
- `WorkflowDefinitionRegistry` 도입
- `WorkflowProgressDetector` 도입
- `WorkflowGuideRenderer` 도입
- `MenuSqlBuilder` 스키마 컬럼 수정
- Workflow 비연속 완료 문맥 처리
- 입력값 null / blank 검증
- 숫자형 입력 검증
- SQL single quote escape
- `storePath` 데드 코드 제거
- `keyword null` 처리 순서 정리
- `ROLE_PTTRN` positive / negative URL 매칭 검증
- `ROLE_SORT` 숫자 출력
- `securityMapper` 선행 안내

## 6. 테스트 실행 결과

다음 테스트를 실행했다.

```bash
./gradlew test --rerun-tasks \
  --tests 'com.krdevops.springai.service.MenuServiceTest' \
  --tests 'com.krdevops.springai.service.AuthServiceTest' \
  --tests 'com.krdevops.springai.service.WorkflowGuideServiceTest' \
  --tests 'com.krdevops.springai.service.SecurityTemplateServiceTest' \
  --tests 'com.krdevops.springai.service.security.SecurityFilePlanFactoryTest' \
  --tests 'com.krdevops.springai.service.security.SecurityTemplateRendererIntegrationTest'
```

결과:

```text
BUILD SUCCESSFUL
```

컴파일 경고:

```text
GenericJackson2JsonRedisSerializer deprecated/removal warning
```

해당 경고는 이번 `Security_Menu_Auth` 구현과 직접 관련 없는 기존 Redis 설정 경고다.

## 7. 최종 결론

현재 구현은 계획서 기준으로 사용 가능한 수준이다.

이전 P1 이슈였던 `MenuSqlBuilder` 스키마 불일치는 해소되었고, Workflow 진행 감지도 보완되었다.

따라서 현재 상태는 다음과 같이 판단한다.

```text
구현 아키텍처: 양호
MenuTool: 사용 가능
AuthTool: 사용 가능
WorkflowGuideTool: 사용 가능
테스트: 통과
남은 이슈: P2/P3 후속 개선
```

후속 정리 우선순위는 다음이다.

```text
1. DB Dialect 선택 경로 연결  ← 미해소 (P2)
2. COMTNAUTHORROLERELATE.CREAT_DT 포함 여부 결정  ← 해소 완료 (2026-06-10)
```

위 두 항목 중 P3(CREAT_DT)는 `6614806` 커밋에서 완료되었다.

P2(DB Dialect 선택 경로)는 Oracle 운영 환경 적용 범위 확정 후 별도 구현 예정이다.

