# Security Menu Auth 구현완료 검토

## 1. 검토 대상

기준 문서:

- `docs/Security_Menu_Auth_구현계획서.md`

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

구현 아키텍처는 계획서 방향을 대체로 잘 따라갔다.

다음 계층은 실제로 추가되었다.

```text
Spec
SqlPlan
Validator
SqlBuilder
Repository
ResultBuilder
WorkflowDefinition
WorkflowProgressDetector
WorkflowGuideRenderer
```

입력 검증, SQL escape, `securityMapper` 안내, `ROLE_PTTRN` 검증, `ROLE_SORT` 숫자 출력 등 계획서의 주요 보완 항목도 상당 부분 반영되었다.

다만 현재 상태를 완전한 구현 완료로 보기에는 P1 이슈가 남아 있다.

가장 큰 문제는 `MenuSqlBuilder`가 실제 eGovFrame 메뉴/프로그램 테이블 스키마와 다른 컬럼명으로 INSERT SQL을 생성한다는 점이다.

## 3. 주요 Findings

## [P1] MenuTool 생성 SQL이 실제 eGovFrame 스키마와 맞지 않음

### 위치

- `src/main/java/com/krdevops/springai/service/menu/MenuSqlBuilder.java`
- `src/test/java/com/krdevops/springai/service/MenuServiceTest.java`

### 문제

`MenuSqlBuilder`는 `COMTNPROGRMLIST` INSERT SQL에서 `STRE_PATH` 컬럼을 사용한다.

```java
"PROGRM_FILE_NM, PROGRM_KOREAN_NM, PROGRM_DC, URL, STRE_PATH, "
```

하지만 스키마 문서 기준 `COMTNPROGRMLIST`의 저장 경로 컬럼은 `PROGRM_STRE_PATH`다.

근거:

```text
docs/com_columns.tsv
COMTNPROGRMLIST  PROGRM_STRE_PATH  varchar(100)  프로그램저장경로
```

또한 `MenuSqlBuilder`는 `COMTNMENUINFO` INSERT SQL에서 `URL` 컬럼을 사용한다.

```java
"MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR, URL, "
```

하지만 스키마 문서 기준 `COMTNMENUINFO`는 `URL` 컬럼이 아니라 `PROGRM_FILE_NM` 컬럼을 가진다.

근거:

```text
docs/com_columns.tsv
COMTNMENUINFO  PROGRM_FILE_NM  varchar(60)  프로그램파일명
```

### 영향

현재 생성 SQL을 실제 eGovFrame DB에 실행하면 다음과 같은 문제가 발생할 수 있다.

- `COMTNPROGRMLIST.STRE_PATH` 컬럼 없음 오류
- `COMTNMENUINFO.URL` 컬럼 없음 오류
- 메뉴 등록 실패
- 메뉴와 프로그램 연결 실패

### 테스트 문제

현재 테스트도 잘못된 컬럼명을 기대한다.

예:

```java
assertThat(result).contains("STRE_PATH");
```

따라서 테스트는 통과하지만 실제 스키마 오류를 잡지 못한다.

### 권장 수정

`COMTNPROGRMLIST` INSERT SQL은 다음 형태로 수정해야 한다.

```sql
INSERT INTO COMTNPROGRMLIST (
    PROGRM_FILE_NM,
    PROGRM_STRE_PATH,
    PROGRM_KOREAN_NM,
    URL
) VALUES (
    ...
);
```

`COMTNMENUINFO` INSERT SQL은 다음 형태로 수정해야 한다.

```sql
INSERT INTO COMTNMENUINFO (
    MENU_NO,
    UPPER_MENU_NO,
    MENU_NM,
    PROGRM_FILE_NM,
    MENU_ORDR
) VALUES (
    ...
);
```

테스트도 다음을 검증하도록 수정해야 한다.

- `PROGRM_STRE_PATH` 포함
- `COMTNMENUINFO`에 `PROGRM_FILE_NM` 포함
- `COMTNMENUINFO`에 `URL` 컬럼이 포함되지 않음
- `COMTNPROGRMLIST`에 `STRE_PATH` 컬럼이 포함되지 않음

## [P2] DB Dialect 구조는 있지만 실제 Tool 호출에서는 MySQL/MariaDB로 고정됨

### 위치

- `src/main/java/com/krdevops/springai/service/MenuService.java`
- `src/main/java/com/krdevops/springai/service/AuthService.java`
- `src/main/java/com/krdevops/springai/service/sql/DbDialect.java`
- `src/main/java/com/krdevops/springai/service/sql/SqlDialectRenderer.java`

### 문제

`DbDialect`와 `SqlDialectRenderer`는 구현되어 있다.

예:

```java
public enum DbDialect {
    MYSQL_MARIADB,
    ORACLE
}
```

하지만 `MenuService`, `AuthService`는 renderer를 MySQL/MariaDB로 고정 생성한다.

```java
private final SqlDialectRenderer renderer =
    new SqlDialectRenderer(DbDialect.MYSQL_MARIADB);
```

따라서 실제 Tool 호출에서는 Oracle SQL을 생성할 방법이 없다.

### 영향

계획서의 DB 방언 대응 전략은 구조 일부만 구현된 상태다.

현재 상태에서는 다음 문제가 남는다.

- Oracle 환경에서 Tool 결과를 그대로 사용하기 어려움
- `DbDialect.ORACLE` 분기가 테스트 또는 내부 직접 호출 외에는 활용되지 않음
- 사용자가 DB 방언을 선택할 수 없음

### 권장 수정

다음 중 하나를 후속 구현으로 결정해야 한다.

| 방식 | 설명 |
|---|---|
| Tool 파라미터 추가 | `generateMenuInsertSql(..., dbType)`, `generateAuthInsertSql(..., dbType)` |
| 설정 기반 결정 | application 설정에서 DB 방언 결정 |
| JDBC metadata 감지 | datasource productName으로 DB 방언 자동 감지 |
| SQL 병기 | MySQL/MariaDB SQL과 Oracle SQL을 함께 출력 |

기존 Tool 시그니처 유지가 중요하다면 우선 설정 기반 또는 JDBC metadata 감지가 적절하다.

## [P2] Workflow 진행 감지가 단독 완료 문맥을 제대로 처리하지 못함

### 위치

- `src/main/java/com/krdevops/springai/service/workflow/WorkflowProgressDetector.java`
- `src/test/java/com/krdevops/springai/service/WorkflowGuideServiceTest.java`

### 문제

`WorkflowProgressDetector`는 1단계부터 연속으로 감지된 단계만 완료로 인정한다.

현재 로직은 다음과 같다.

```java
int maxContinuous = 0;
for (WorkflowStep step : definition.steps()) {
    if (completed.contains(step.no())) {
        maxContinuous = step.no();
    } else {
        break;
    }
}
return maxContinuous;
```

이 방식은 사용자가 앞 단계 전체를 모두 언급하면 잘 동작한다.

예:

```text
security, securitymapper, 메뉴구조, 프로그램목록, 메뉴등록 완료
```

하지만 사용자가 다음처럼 현재 완료 작업만 말하면 문제가 생긴다.

```text
메뉴 SQL 생성 완료
```

이 경우 5단계 키워드는 감지되지만 1~4단계가 없으므로 `completedStep`은 0으로 남고, 다음 단계가 다시 1단계로 안내될 수 있다.

### 영향

계획서의 의도는 `currentContext`를 보고 다음 단계를 안내하는 것이다.

실사용에서는 사용자가 전체 히스토리를 모두 입력하기보다 현재 완료 작업만 입력할 가능성이 높다.

따라서 현재 진행 감지 방식은 실사용 UX에서 부정확한 안내를 만들 수 있다.

### 권장 수정

다음 중 하나를 선택한다.

| 방식 | 설명 |
|---|---|
| 현재 방식 유지 + 안내 강화 | currentContext에는 이전 단계 전체를 포함하라고 Tool description에 명시 |
| 비연속 감지 허용 | 감지된 단계 중 가장 큰 단계 기준으로 다음 단계 안내 |
| 혼합 방식 | 연속 단계가 있으면 연속 기준, 없으면 감지된 최소/최대 단계 기준으로 추정 |

기존 `WorkflowGuideService`의 이전 구현은 비연속 입력을 일부 보정하는 로직을 갖고 있었다.

따라서 혼합 방식이 가장 현실적이다.

테스트도 다음 케이스를 추가해야 한다.

```text
currentContext = "메뉴 SQL 생성 완료"
expected next = "권한 SQL 생성"
```

## 4. 잘 구현된 부분

다음 항목은 계획서 방향대로 구현되었다.

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

## 5. 계획서 항목별 구현 평가

| 계획 항목 | 구현 상태 | 평가 |
|---|---|---|
| 기존 Tool 메서드 시그니처 유지 | 구현 | 양호 |
| 입력 검증 | 구현 | 양호 |
| SQL single quote escape | 구현 | 양호 |
| `storePath` 데드 코드 제거 | 구현 | 양호 |
| `keyword null` 처리 순서 정리 | 구현 | 양호 |
| `ROLE_SORT` 숫자 출력 | 구현 | 양호 |
| `ROLE_PTTRN` 출력/매칭 검증 | 구현 | 양호 |
| `Spec` 도입 | 구현 | 양호 |
| `SqlPlan` 도입 | 구현 | 양호 |
| `Validator` 분리 | 구현 | 양호 |
| `SqlBuilder` 분리 | 구현 | 구조는 양호, Menu SQL 컬럼 오류 있음 |
| `Repository` 분리 | 구현 | 양호 |
| `DbDialect` 도입 | 부분 구현 | 외부 선택 경로 없음 |
| `ResultBuilder` 분리 | 구현 | 양호 |
| `WorkflowGuideTool` 확장 | 구현 | 양호 |
| `WorkflowDefinition` 기반 구조 | 구현 | 진행 감지 보완 필요 |
| 테스트 추가 | 구현 | 스키마 정확성/비연속 Workflow 테스트 보완 필요 |

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

해당 경고는 이번 `MenuTool/AuthTool/WorkflowGuideTool` 구현과 직접 관련 없는 기존 Redis 설정 경고다.

## 7. 최종 결론

구현 구조는 계획서의 큰 방향을 잘 따랐다.

특히 `SecurityTemplateTool` 방식에서 가져오려던 다음 원칙은 대체로 반영되었다.

```text
Tool은 얇게 유지
Service는 조율자 역할
Spec으로 입력 정규화
SqlPlan으로 결과 구조화
Validator / Builder / Repository / ResultBuilder 분리
WorkflowDefinition 기반 안내 구조
```

그러나 현재 상태를 최종 완료로 보기에는 P1 이슈가 남아 있다.

가장 먼저 수정해야 할 항목은 다음이다.

```text
MenuSqlBuilder의 eGovFrame 스키마 컬럼 불일치 수정
```

수정 후 다음을 다시 검증해야 한다.

- `COMTNPROGRMLIST.PROGRM_STRE_PATH` 사용
- `COMTNMENUINFO.PROGRM_FILE_NM` 사용
- 잘못된 `STRE_PATH`, `COMTNMENUINFO.URL` 제거
- 관련 테스트 수정
- 대상 테스트 재실행

