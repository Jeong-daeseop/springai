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

## [해소] DB Dialect가 실제 Tool 호출에서 MySQL/MariaDB로 고정됨

### 위치

- `src/main/java/com/krdevops/springai/service/sql/SqlDialectProperties.java`
- `src/main/java/com/krdevops/springai/service/sql/DbDialectResolver.java`
- `src/main/java/com/krdevops/springai/service/sql/SqlDialectConfig.java`
- `src/main/java/com/krdevops/springai/service/MenuService.java`
- `src/main/java/com/krdevops/springai/service/AuthService.java`

### 수정 내용

`application.yaml`에 `app.sql.dialect` 설정을 추가하고, `DbDialectResolver` → `SqlDialectRenderer` Bean 주입 구조로 전환했다.

```yaml
app:
  sql:
    dialect: mysql_mariadb  # oracle / auto 지원
```

`MenuService`, `AuthService`의 직접 `new SqlDialectRenderer(DbDialect.MYSQL_MARIADB)` 생성을 제거하고 생성자 주입으로 전환했다.

`MenuInputValidator`, `MenuSqlBuilder`, `MenuResultBuilder`, `AuthInputValidator`, `AuthSqlBuilder`, `AuthResultBuilder`를 `@Component`로 Bean화했다.

Tool description도 `app.sql.dialect` 기준으로 갱신했다.

### 판단

해소되었다.

커밋: `b341c32` feat: P2 DB Dialect 설정 기반 선택 구현 (app.sql.dialect)

관련 설계 문서: `docs/Security_Menu_Auth_DB_Dialect_설계.md` 섹션 19

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
- `SqlDialectProperties` 도입 (`app.sql.dialect` 설정 바인딩)
- `DbDialectResolver` 도입 (설정값/auto JDBC metadata 분기, 감지 실패 시 MYSQL_MARIADB fallback)
- `SqlDialectConfig` 도입 (`SqlDialectRenderer` Spring Bean 등록)
- `MenuInputValidator` / `MenuSqlBuilder` / `MenuResultBuilder` Bean화 (`@Component`)
- `AuthInputValidator` / `AuthSqlBuilder` / `AuthResultBuilder` Bean화 (`@Component`)
- `MenuService` / `AuthService` 직접 `new SqlDialectRenderer(...)` 제거, 생성자 주입 전환
- `application.yaml` `app.sql.dialect: mysql_mariadb` 기본값 추가
- `MenuTool` / `AuthTool` description `app.sql.dialect` 기준으로 갱신
- `DbDialectResolverTest` 추가 (auto/oracle/fallback 7개)
- `SqlDialectRendererTest` 추가 (MySQL·Oracle 출력 8개)
- `AuthServiceTest` Oracle renderer 회귀 테스트 추가 (2개)

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
남은 이슈: 없음
```

후속 정리 우선순위는 다음이다.

```text
1. DB Dialect 선택 경로 연결  ← 해소 완료 (2026-06-10, b341c32)
2. COMTNAUTHORROLERELATE.CREAT_DT 포함 여부 결정  ← 해소 완료 (2026-06-10, 6614806)
```

모든 P2/P3 항목이 완료되었다.

