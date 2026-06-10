# Security Menu Auth DB Dialect 선택 설계

## 1. 목적

현재 `MenuTool`, `AuthTool`은 SQL 생성 시 `SqlDialectRenderer`를 사용한다.

하지만 실제 서비스에서는 다음처럼 MySQL/MariaDB로 고정되어 있다.

```java
private final SqlDialectRenderer renderer =
    new SqlDialectRenderer(DbDialect.MYSQL_MARIADB);
```

따라서 `DbDialect.ORACLE` 분기와 `SqlDialectRenderer`의 Oracle SQL 생성 로직이 있어도, 실제 Tool 호출에서는 Oracle SQL을 선택할 수 없다.

이 문서는 DB Dialect 선택을 다음 방식 중 하나로 연결하기 위한 설계를 정리한다.

- Tool 파라미터 방식
- 설정값 방식
- JDBC metadata 자동 감지 방식

## 2. 핵심 결론

권장 설계는 다음이다.

```text
1. application.yaml 설정값으로 DB Dialect 결정
2. 설정값이 auto이면 JDBC metadata로 자동 감지
3. MenuService/AuthService는 SqlDialectRenderer를 직접 생성하지 않고 Bean 주입
4. Tool 파라미터 dbType 추가는 하지 않음
```

즉, 설정값 기반을 기본으로 하고 JDBC metadata 자동 감지를 보조 옵션으로 둔다.

Tool 파라미터 방식은 가장 명시적이지만 MCP Tool 시그니처를 바꾸므로 후순위로 둔다.

## 3. 선택지 비교

| 방식 | 설명 | 장점 | 단점 | 판단 |
|---|---|---|---|---|
| Tool 파라미터 | `generateMenuInsertSql(..., dbType)`처럼 Tool 인자 추가 | 호출 시 명확함 | 기존 MCP Tool 시그니처 변경, 문서/예시 수정 필요, 사용자가 매번 입력해야 함 | 후순위 |
| 설정값 | `application.yaml`에서 DB 방언 지정 | 기존 Tool 시그니처 유지, 운영 환경별 고정값과 잘 맞음 | 설정 누락 시 기본값 필요 | 1순위 |
| JDBC metadata 감지 | `DataSource.getConnection().getMetaData()`로 DB 종류 감지 | 사용자가 설정하지 않아도 자동 결정 | 연결 실패/권한 문제/테스트 복잡도 있음 | 보조 옵션 |

최종 선택:

```text
설정값 기반 + auto 감지 지원
```

## 4. 설정 구조

`application.yaml`에 다음 설정을 추가한다.

```yaml
app:
  sql:
    dialect: mysql_mariadb
```

지원값은 다음이다.

```text
mysql_mariadb
oracle
auto
```

의미는 다음과 같다.

| 값 | 의미 |
|---|---|
| `mysql_mariadb` | MySQL/MariaDB SQL 생성 |
| `oracle` | Oracle SQL 생성 |
| `auto` | JDBC metadata로 DB 종류 자동 감지 |

기본값은 `mysql_mariadb`로 둔다.

## 5. 추가 클래스 설계

추가 또는 수정할 클래스는 다음이다.

```text
service/sql/
  DbDialect.java
  SqlDialectRenderer.java
  SqlDialectProperties.java
  SqlDialectConfig.java
  DbDialectResolver.java
```

각 클래스의 역할은 다음과 같다.

| 클래스 | 역할 |
|---|---|
| `DbDialect` | `MYSQL_MARIADB`, `ORACLE` 등 실제 SQL 방언 정의 |
| `SqlDialectRenderer` | 방언별 SQL 조각 생성 |
| `SqlDialectProperties` | `app.sql.dialect` 설정 바인딩 |
| `SqlDialectConfig` | `SqlDialectRenderer` Spring Bean 생성 |
| `DbDialectResolver` | 설정값 또는 JDBC metadata로 최종 dialect 결정 |

## 6. Bean 생성 흐름

전체 흐름은 다음과 같다.

```text
application.yaml
  → SqlDialectProperties
  → DbDialectResolver
  → SqlDialectRenderer Bean
  → MenuService / AuthService 주입
```

현재 구조:

```java
private final SqlDialectRenderer renderer =
    new SqlDialectRenderer(DbDialect.MYSQL_MARIADB);
```

개선 구조:

```java
private final SqlDialectRenderer renderer;
```

더 나아가 `MenuSqlBuilder`, `AuthSqlBuilder`도 Bean으로 관리한다.

```text
SqlDialectRenderer Bean
  → MenuSqlBuilder Bean
  → MenuService

SqlDialectRenderer Bean
  → AuthSqlBuilder Bean
  → AuthService
```

## 7. DbDialectResolver 설계

`DbDialectResolver`는 최종 DB 방언을 결정한다.

동작 규칙은 다음이다.

```text
설정값이 mysql_mariadb
  → MYSQL_MARIADB 반환

설정값이 oracle
  → ORACLE 반환

설정값이 auto
  → JDBC metadata로 productName 확인
  → productName 기준으로 MYSQL_MARIADB / ORACLE 판별

감지 실패 또는 알 수 없는 DB
  → MYSQL_MARIADB 기본값 사용
  → warn log 출력
```

JDBC metadata 판별 예시는 다음이다.

| productName | 판정 |
|---|---|
| `MySQL` | `MYSQL_MARIADB` |
| `MariaDB` | `MYSQL_MARIADB` |
| `Oracle` | `ORACLE` |
| 기타 | `MYSQL_MARIADB` fallback |

## 8. DbDialect enum 설계

현재 enum은 다음과 같다.

```java
public enum DbDialect {
    MYSQL_MARIADB,
    ORACLE
}
```

설정값 파싱을 위해 다음 중 하나를 추가한다.

### 방식 A. enum에 parser 추가

```java
public static DbDialect from(String value) {
    if (value == null || value.isBlank()) {
        return MYSQL_MARIADB;
    }
    return switch (value.trim().toLowerCase()) {
        case "mysql", "mariadb", "mysql_mariadb" -> MYSQL_MARIADB;
        case "oracle" -> ORACLE;
        default -> MYSQL_MARIADB;
    };
}
```

### 방식 B. resolver에서 parser 처리

`DbDialect`는 순수 enum으로 두고, `DbDialectResolver`가 문자열 파싱을 담당한다.

권장안은 방식 B다.

이유는 `auto`는 실제 SQL 방언이 아니라 해결 전략이기 때문이다.

## 9. SqlDialectProperties 설계

예시:

```java
@ConfigurationProperties(prefix = "app.sql")
public class SqlDialectProperties {

    /**
     * mysql_mariadb, oracle, auto
     */
    private String dialect = "mysql_mariadb";

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }
}
```

`auto`는 `DbDialect` enum 값이 아니라 properties의 선택값으로만 다룬다.

## 10. SqlDialectConfig 설계

예시:

```java
@Configuration
@EnableConfigurationProperties(SqlDialectProperties.class)
public class SqlDialectConfig {

    @Bean
    public SqlDialectRenderer sqlDialectRenderer(DbDialectResolver resolver) {
        return new SqlDialectRenderer(resolver.resolve());
    }
}
```

`DbDialectResolver`는 `SqlDialectProperties`와 `DataSource`를 주입받는다.

```java
@Component
@RequiredArgsConstructor
public class DbDialectResolver {

    private final SqlDialectProperties properties;
    private final DataSource dataSource;

    public DbDialect resolve() {
        String configured = properties.getDialect();

        if ("auto".equalsIgnoreCase(configured)) {
            return detectFromMetadata();
        }

        return parseConfigured(configured);
    }
}
```

## 11. Service 변경 설계

현재 `MenuService`, `AuthService`는 다음처럼 renderer를 직접 생성한다.

```java
private final SqlDialectRenderer renderer =
    new SqlDialectRenderer(DbDialect.MYSQL_MARIADB);
```

이를 제거하고 Bean 주입으로 변경한다.

### 11.1 최소 변경안

```java
private final MenuRepository menuRepository;
private final SqlDialectRenderer renderer;

private final MenuInputValidator validator = new MenuInputValidator();
private final MenuResultBuilder resultBuilder = new MenuResultBuilder();

private MenuSqlBuilder sqlBuilder() {
    return new MenuSqlBuilder(renderer);
}
```

### 11.2 권장 변경안

`MenuInputValidator`, `MenuSqlBuilder`, `MenuResultBuilder`도 Bean으로 등록한다.

```java
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuInputValidator validator;
    private final MenuSqlBuilder sqlBuilder;
    private final MenuResultBuilder resultBuilder;
}
```

`AuthService`도 같은 방식으로 변경한다.

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final AuthInputValidator validator;
    private final AuthSqlBuilder sqlBuilder;
    private final AuthResultBuilder resultBuilder;
}
```

## 12. Builder Bean 설계

`MenuSqlBuilder`, `AuthSqlBuilder`는 `SqlDialectRenderer`를 생성자로 받는다.

현재 생성자 구조는 이미 Bean화에 적합하다.

```java
public MenuSqlBuilder(SqlDialectRenderer renderer) {
    this.renderer = renderer;
}
```

따라서 클래스에 `@Component`를 붙이면 된다.

```java
@Component
public class MenuSqlBuilder {
    ...
}
```

동일하게 다음 클래스도 Bean화한다.

```text
MenuInputValidator
MenuResultBuilder
AuthInputValidator
AuthSqlBuilder
AuthResultBuilder
```

## 13. Tool description 변경

현재 Tool description은 다음 취지를 담고 있다.

```text
현재 MySQL/MariaDB 방언 기준.
Oracle 전환 시 DbDialect 설정 변경 필요.
```

설정 기반이 구현되면 다음처럼 변경한다.

```text
DB 방언은 app.sql.dialect 설정을 따릅니다.
지원값: mysql_mariadb, oracle, auto
auto는 JDBC metadata로 DB 종류를 감지합니다.
```

`MenuTool`, `AuthTool` 둘 다 수정한다.

## 14. 테스트 설계

추가 테스트는 다음이 필요하다.

### 14.1 MySQL/MariaDB 설정 테스트

검증 항목:

```text
app.sql.dialect=mysql_mariadb
→ NOW()
→ LIMIT 50
→ CAST(SUBSTRING(... AS UNSIGNED))
```

### 14.2 Oracle 설정 테스트

검증 항목:

```text
app.sql.dialect=oracle
→ SYSDATE
→ FETCH FIRST 50 ROWS ONLY 또는 ROWNUM
→ TO_NUMBER(REGEXP_SUBSTR(...))
```

### 14.3 auto 감지 테스트

Mock `DataSource`, `Connection`, `DatabaseMetaData`를 사용한다.

검증 항목:

```text
DatabaseProductName = "Oracle"
→ ORACLE

DatabaseProductName = "MySQL"
→ MYSQL_MARIADB

DatabaseProductName = "MariaDB"
→ MYSQL_MARIADB

감지 실패
→ MYSQL_MARIADB fallback
```

### 14.4 Service 주입 테스트

`MenuService`, `AuthService`가 직접 `new SqlDialectRenderer(DbDialect.MYSQL_MARIADB)`를 하지 않는지 확인한다.

가능하면 구조 테스트보다 동작 테스트로 확인한다.

예:

```text
Oracle renderer 주입
→ AuthSqlBuilder 결과에 SYSDATE 포함
```

## 15. 구현 순서

권장 구현 순서는 다음이다.

```text
1. SqlDialectProperties 추가
2. DbDialectResolver 추가
3. SqlDialectConfig 추가
4. SqlDialectRenderer를 Spring Bean으로 등록
5. MenuInputValidator / MenuSqlBuilder / MenuResultBuilder Bean화
6. AuthInputValidator / AuthSqlBuilder / AuthResultBuilder Bean화
7. MenuService / AuthService의 직접 new 생성 제거
8. application.yaml에 app.sql.dialect 기본값 추가
9. MenuTool / AuthTool description 업데이트
10. MySQL/MariaDB 설정 테스트 추가
11. Oracle 설정 테스트 추가
12. auto 감지 테스트 추가
13. 기존 Menu/Auth/Workflow/Security 테스트 재실행
```

## 16. Tool 파라미터 방식 보류 이유

Tool 파라미터 방식은 다음처럼 명시적이다.

```java
generateAuthInsertSql(urlPrefix, programNm, domain, dbType)
```

하지만 지금 단계에서는 보류한다.

이유는 다음이다.

- 기존 MCP Tool 시그니처가 변경된다.
- 문서/예시/프롬프트를 모두 수정해야 한다.
- 사용자가 매번 `dbType`을 입력해야 한다.
- AI가 `dbType`을 누락하면 기본값 혼동 가능성이 있다.
- 운영 환경에서는 DB 종류가 보통 고정되어 있어 설정값과 더 잘 맞는다.

따라서 1차 구현에서는 설정값 기반을 선택한다.

## 17. 확장 방향

향후 다음과 같이 확장할 수 있다.

```text
ProjectInitializrTool
  → 프로젝트 생성 시 DB 종류를 함께 설정
  → application.yaml에 app.sql.dialect 자동 기록

ProjectScannerTool
  → 기존 프로젝트 datasource URL 분석
  → DB Dialect 후보 추천

WorkflowGuideTool
  → DB Dialect 설정 여부 점검 단계 추가
```

이렇게 하면 Tool 간 연결성이 더 좋아진다.

## 18. 최종 결론

최종 설계는 다음으로 확정한다.

```text
설정값 기반 + auto 감지 지원
```

구현 방향은 다음이다.

```text
application.yaml
  → SqlDialectProperties
  → DbDialectResolver
  → SqlDialectRenderer Bean
  → MenuSqlBuilder / AuthSqlBuilder
  → MenuService / AuthService
```

이 방식은 기존 Tool API를 깨지 않으면서도 운영 환경별 SQL 생성을 안정적으로 지원한다.

## 19. 구현완료 (2026-06-10)

### 19.1 구현 상태

```text
완료
```

### 19.2 구현 커밋

| 커밋 | 내용 |
| --- | --- |
| `b341c32` | feat: P2 DB Dialect 설정 기반 선택 구현 (app.sql.dialect) |
| `955b426` | test: P3 Oracle SQL 생성 회귀 테스트 추가 |

### 19.3 구현된 클래스

| 클래스 | 상태 |
| --- | --- |
| `SqlDialectProperties` | 신규 — `app.sql.dialect` 설정 바인딩 |
| `DbDialectResolver` | 신규 — 설정값/auto(JDBC metadata) 분기 |
| `SqlDialectConfig` | 신규 — `SqlDialectRenderer` Spring Bean 등록 |
| `MenuInputValidator` | `@Component` 추가 |
| `MenuSqlBuilder` | `@Component` + `@RequiredArgsConstructor` 추가 |
| `MenuResultBuilder` | `@Component` 추가 |
| `AuthInputValidator` | `@Component` 추가 |
| `AuthSqlBuilder` | `@Component` + `@RequiredArgsConstructor` 추가 |
| `AuthResultBuilder` | `@Component` 추가 |
| `MenuService` | 직접 `new SqlDialectRenderer(...)` 제거, 생성자 주입으로 전환 |
| `AuthService` | 동일 |
| `application.yaml` | `app.sql.dialect: mysql_mariadb` 기본값 추가 |
| `MenuTool` / `AuthTool` | description → `app.sql.dialect` 설명으로 갱신 |

### 19.4 추가된 테스트

| 테스트 클래스 | 내용 |
| --- | --- |
| `DbDialectResolverTest` | mysql/oracle/auto/fallback 판정 7개 |
| `SqlDialectRendererTest` | now/limit/roleCodeMaxExpr/wrapLimit MySQL·Oracle 출력 8개 |
| `AuthServiceTest` | Oracle renderer 주입 시 SYSDATE, MySQL 시 NOW() 검증 2개 |

### 19.5 설계 대비 변경 사항

없음. 15절 구현 순서대로 전체 완료.

단, `MenuSqlBuilder`는 현재 `renderer`를 실제 호출하지 않으므로 Oracle SQL 분기가 없다.
Oracle 전환 시 메뉴 SQL에 방언 차이가 없으면 이는 정상이다.

### 19.6 최종 판단

```text
P2 DB Dialect 선택 경로 연결 — 완료
P3 Oracle SQL 생성 회귀 테스트 — 완료
```

