# SecurityTemplateTool 기준 MenuTool / AuthTool / WorkflowGuideTool 아키텍처 검토

## 1. 검토 목적

현재 `SecurityTemplateTool`은 이미 구현되어 있으며, 구조적으로 다음과 같은 분리된 아키텍처를 가진다.

```text
SecurityTemplateTool
  → SecurityTemplateService
    → SecuritySpec
    → SecurityFilePlanFactory
    → SecurityTemplateRenderer
    → FilePlanExecutor
    → SecurityResultBuilder
```

반면 현재 `MenuTool`, `AuthTool`, `WorkflowGuideTool`은 구현 방식이 다르다.

이 문서는 세 Tool을 `SecurityTemplateTool` 방식과 비교하고, 앞으로 어떤 방향으로 구현을 이어가야 하는지 검토한다.

## 2. 핵심 결론

현재 `MenuTool`, `AuthTool`, `WorkflowGuideTool`은 기능적으로는 동작할 수 있다.

하지만 앞으로 확장성, 검증 가능성, 안전성을 고려하면 `SecurityTemplateTool`의 아키텍처 원칙을 일부 가져오는 것이 좋다.

단, `SecurityTemplateTool` 구조를 그대로 복제하면 안 된다.

`SecurityTemplateTool`은 파일 생성 도구이고, `MenuTool` / `AuthTool`은 SQL 생성 도구이며, `WorkflowGuideTool`은 절차 안내 도구이기 때문이다.

따라서 최종 방향은 다음과 같다.

> `SecurityTemplateTool`을 재구현하지 않는다.  
> `MenuTool`, `AuthTool`, `WorkflowGuideTool`을 `SecurityTemplateTool`의 아키텍처 원칙에 맞게 점진적으로 리팩터링한다.  
> 단, 파일 생성 파이프라인이 아니라 SQL 생성 / 워크플로우 안내 파이프라인으로 맞춘다.

## 3. 현재 아키텍처 비교

### 3.1 SecurityTemplateTool

`SecurityTemplateTool`은 Tool 진입점과 실제 로직이 잘 분리되어 있다.

```text
SecurityTemplateTool
  → SecurityTemplateService
    → SecuritySpec
    → SecurityFilePlanFactory
    → SecurityTemplateRenderer
    → FilePlanExecutor
    → SecurityResultBuilder
```

각 구성 요소의 역할은 다음과 같다.

| 구성 요소 | 역할 |
|---|---|
| `SecurityTemplateTool` | MCP Tool 진입점 |
| `SecurityTemplateService` | 전체 흐름 조율 |
| `SecuritySpec` | 입력값 정규화 |
| `SecurityFilePlanFactory` | securityType을 파일 생성 계획으로 변환 |
| `SecurityTemplateRenderer` | 템플릿 렌더링 |
| `FilePlanExecutor` | 파일 저장 |
| `SecurityResultBuilder` | 사용자 반환 메시지 생성 |

이 구조의 장점은 다음이다.

- Tool이 얇다.
- Service가 조율자 역할에 집중한다.
- 입력값이 Spec으로 정리된다.
- 생성 계획과 렌더링이 분리된다.
- 결과 메시지 생성이 분리된다.
- 테스트 단위가 명확하다.

### 3.2 MenuTool / AuthTool

현재 `MenuTool`, `AuthTool`은 다음과 같은 단순 구조다.

```text
MenuTool
  → MenuService
    → DB 조회
    → 입력 처리
    → 번호 계산
    → SQL 문자열 조립

AuthTool
  → AuthService
    → DB 조회
    → ROLE_CODE 계산
    → ROLE_PTTRN 생성
    → SQL 문자열 조립
```

현재 구조에서는 Service 하나가 많은 책임을 가진다.

`MenuService`는 다음 책임을 동시에 가진다.

- 메뉴 트리 조회
- 하위 메뉴 조회
- 신규 `MENU_NO` 계산
- 신규 `MENU_ORDR` 계산
- URL 생성
- 프로그램 저장 경로 생성
- SQL 문자열 조립
- 사용자 안내 문구 생성

`AuthService`는 다음 책임을 동시에 가진다.

- 프로그램 목록 검색
- `ROLE_CODE` 최대값 조회
- 신규 `ROLE_CODE` 계산
- `ROLE_PTTRN` 생성
- 권한 SQL 문자열 조립
- 사용자 안내 문구 생성

### 3.3 WorkflowGuideTool

현재 `WorkflowGuideTool`은 CRUD 생성 워크플로우 안내에 집중되어 있다.

구조는 다음과 같다.

```text
WorkflowGuideTool
  → WorkflowGuideService
    → static WORKFLOW
    → 완료 단계 감지
    → 출력 문자열 생성
```

현재 구조에서는 workflow 정의, 진행률 판단, 출력 렌더링이 하나의 Service에 묶여 있다.

CRUD workflow만 있을 때는 문제가 작지만, 앞으로 다음과 같은 workflow가 늘어나면 복잡도가 빠르게 증가한다.

- CRUD 생성 workflow
- SecurityTemplate 적용 workflow
- Security → Menu → Auth workflow
- ProjectInitializr workflow
- 배포 전 검증 workflow

## 4. 현재 구조의 문제점

### 4.1 Service God Class화 위험

`MenuService`, `AuthService`, `WorkflowGuideService`는 앞으로 기능이 늘어날수록 God Class가 될 가능성이 크다.

예를 들어 `MenuService`에 다음 기능이 모두 추가되면 클래스 책임이 과도해진다.

- 입력 검증
- SQL escape
- 중복 검증
- DB 방언 분기
- SQL 출력 포맷
- 메뉴 트리 렌더링
- 테스트 시나리오별 안내

`AuthService`도 마찬가지다.

- URL prefix 검증
- `ROLE_CODE` 중복 확인
- `ROLE_PTTRN` 생성
- 권한 그룹 옵션 처리
- `securityMapper` 의존성 안내
- SQL escape
- DB 방언 분기

### 4.2 테스트 단위가 흐려짐

현재 구조에서는 `generateMenuInsertSql()` 하나를 테스트하려 해도 다음 로직이 한꺼번에 엮인다.

- DB 조회
- 번호 계산
- URL 조립
- SQL 문자열 생성
- 안내 문구 생성

이러면 단위 테스트가 어려워지고, 테스트 실패 시 원인 분리가 어려워진다.

### 4.3 SQL 생성 안정성 확장이 어려움

SQL 생성 도구는 다음 기능이 중요하다.

- 입력값 검증
- SQL literal escape
- DB 방언 처리
- 중복 위험 안내
- 실행 전 재확인 안내

현재처럼 Service에서 직접 문자열을 조립하면 이런 안정성 기능이 흩어지기 쉽다.

### 4.4 Workflow 확장이 어려움

현재 `WorkflowGuideService`는 CRUD 14단계 workflow가 static list로 박혀 있다.

여기에 Security/Menu/Auth workflow를 그대로 추가하면 한 클래스 안에 여러 workflow가 혼재된다.

장기적으로는 workflow 정의와 렌더링을 분리해야 한다.

## 5. 가져와야 할 SecurityTemplateTool의 원칙

`SecurityTemplateTool` 구조를 그대로 복제할 필요는 없다.

그러나 다음 원칙은 가져와야 한다.

```text
1. Tool은 얇게 유지한다.
2. Service는 조율자 역할에 집중한다.
3. 입력값은 Spec으로 정규화한다.
4. 계획/검증/렌더링을 분리한다.
5. 결과 출력은 Builder 또는 Renderer가 담당한다.
```

이 원칙을 적용하면 `MenuTool`, `AuthTool`, `WorkflowGuideTool`도 더 안정적으로 확장할 수 있다.

## 6. 권장 아키텍처 방향

## 6.1 MenuTool 권장 구조

`MenuTool`은 SQL 생성 도구이므로 `FilePlan`이 아니라 `SqlPlan` 성격의 구조가 맞다.

권장 구조는 다음과 같다.

```text
MenuTool
  → MenuRegistrationService
    → MenuRegistrationSpec
    → MenuInputValidator
    → MenuRepository
    → MenuSqlPlanFactory
    → MenuSqlRenderer
    → MenuResultBuilder
```

각 구성 요소의 역할은 다음과 같다.

| 구성 요소 | 역할 |
|---|---|
| `MenuTool` | MCP Tool 진입점 |
| `MenuRegistrationService` | 전체 흐름 조율 |
| `MenuRegistrationSpec` | 입력값 정규화 |
| `MenuInputValidator` | `upperMenuNo`, `urlPrefix`, `menuNm`, `progrmFileNm` 검증 |
| `MenuRepository` | 메뉴/프로그램 DB 조회 |
| `MenuSqlPlanFactory` | INSERT SQL 생성 계획 구성 |
| `MenuSqlRenderer` | SQL 문자열 렌더링 |
| `MenuResultBuilder` | 경고/후속 단계 포함 결과 메시지 생성 |

초기에는 너무 많은 클래스를 한 번에 만들기보다 다음 정도의 경량 구조로 시작해도 충분하다.

```text
service/menu/
  MenuRegistrationSpec.java
  MenuRegistrationService.java
  MenuInputValidator.java
  MenuSqlBuilder.java
```

## 6.2 AuthTool 권장 구조

`AuthTool`도 `SecurityTemplateTool`의 원칙을 가져오되, SQL 생성 도구에 맞게 조정한다.

권장 구조는 다음과 같다.

```text
AuthTool
  → AuthRegistrationService
    → AuthRegistrationSpec
    → AuthInputValidator
    → AuthRepository
    → AuthSqlPlanFactory
    → AuthSqlRenderer
    → AuthResultBuilder
```

각 구성 요소의 역할은 다음과 같다.

| 구성 요소 | 역할 |
|---|---|
| `AuthTool` | MCP Tool 진입점 |
| `AuthRegistrationService` | 전체 흐름 조율 |
| `AuthRegistrationSpec` | 입력값 정규화 |
| `AuthInputValidator` | `urlPrefix`, `programNm`, `domain` 검증 |
| `AuthRepository` | 프로그램/권한 DB 조회 |
| `AuthSqlPlanFactory` | 권한 SQL 생성 계획 구성 |
| `AuthSqlRenderer` | SQL 문자열 렌더링 |
| `AuthResultBuilder` | `securityMapper` 의존성, 재기동 안내 포함 |

초기 경량 구조는 다음이 적절하다.

```text
service/auth/
  AuthRegistrationSpec.java
  AuthRegistrationService.java
  AuthInputValidator.java
  AuthSqlBuilder.java
```

특히 `ROLE_PTTRN` 생성은 Security 접근 제어의 핵심 규칙이므로 다음 중 하나로 분리하는 것이 좋다.

- `AuthSqlBuilder`
- `RolePatternFactory`
- `SecurityRolePatternBuilder`

## 6.3 WorkflowGuideTool 권장 구조

`WorkflowGuideTool`은 파일 생성이나 SQL 생성이 아니라 절차 안내 도구다.

따라서 정의 기반 workflow 구조가 어울린다.

권장 구조는 다음과 같다.

```text
WorkflowGuideTool
  → WorkflowGuideService
    → WorkflowDefinitionRegistry
    → WorkflowProgressDetector
    → WorkflowGuideRenderer
```

각 구성 요소의 역할은 다음과 같다.

| 구성 요소 | 역할 |
|---|---|
| `WorkflowGuideTool` | MCP Tool 진입점 |
| `WorkflowGuideService` | workflow 선택과 조율 |
| `WorkflowDefinitionRegistry` | workflowType별 단계 정의 제공 |
| `WorkflowProgressDetector` | currentContext 기반 완료 단계 판단 |
| `WorkflowGuideRenderer` | 진행률, 다음 단계, 남은 단계 출력 |

Tool 메서드는 두 가지 방식 중 하나를 선택할 수 있다.

### 방식 A. 범용 메서드로 통합

```java
suggestNextStep(String workflowType, String currentContext)
```

장점은 workflow 종류가 늘어나도 메서드 하나로 처리할 수 있다는 점이다.

단점은 기존 호출자와의 호환성이 깨질 수 있다.

### 방식 B. 기존 메서드 유지 + 전용 메서드 추가

```java
suggestNextStep(String currentContext)
suggestSecurityMenuAuthWorkflow(String currentContext)
```

장점은 기존 CRUD workflow 호출을 깨지 않는다는 점이다.

단점은 workflow가 많아질수록 Tool 메서드가 늘어날 수 있다.

현재는 방식 B가 더 안전하다.

## 7. SqlPlan 모델 제안

`SecurityTemplateTool`은 파일 생성 도구이므로 `FilePlan`이 자연스럽다.

하지만 `MenuTool`, `AuthTool`은 SQL 생성 도구이므로 `SqlPlan`이 더 적합하다.

제안 모델은 다음과 같다.

```java
public record SqlPlan(
    String title,
    List<String> statements,
    List<String> warnings,
    List<String> nextSteps
) {}
```

이 모델을 사용하면 다음을 분리해서 테스트할 수 있다.

- SQL 본문
- 경고 메시지
- 선행 조건
- 후속 조치

예를 들어 `AuthTool`의 결과는 다음 구조로 표현할 수 있다.

```text
title:
  권한·롤 등록 SQL

statements:
  INSERT INTO COMTNROLEINFO ...
  INSERT INTO COMTNAUTHORROLERELATE ...

warnings:
  securityMapper가 먼저 생성되어야 함
  ROLE_CODE는 생성 시점 기준이므로 실행 전 재확인 필요

nextSteps:
  SQL 실행
  서버 재기동 또는 Security 캐시 갱신
  관리자/일반 사용자 접근 테스트
```

## 8. 점진적 리팩터링 계획

한 번에 모든 구조를 바꾸면 리스크가 크다.

따라서 다음 순서로 점진적으로 진행하는 것이 좋다.

```text
1. 기존 Tool 메서드 시그니처 유지
2. Menu/Auth 입력값을 Spec으로 정규화
3. Validator 분리
4. SQL Builder 분리
5. 결과 메시지 Builder 분리
6. Repository 분리
7. WorkflowGuideService를 definition 기반으로 분리
8. 테스트 추가
```

### Phase 1. 시그니처 유지

기존 MCP Tool 호출을 깨지 않기 위해 다음 메서드는 유지한다.

```java
MenuTool.getMenuStructure(String menuNo)
MenuTool.generateMenuInsertSql(String upperMenuNo, String urlPrefix, String menuNm, String progrmFileNm)
AuthTool.getProgramList(String keyword)
AuthTool.generateAuthInsertSql(String urlPrefix, String programNm, String domain)
WorkflowGuideTool.suggestNextStep(String currentContext)
```

새 구조는 내부 구현에서만 적용한다.

### Phase 2. Spec 도입

입력값 정규화를 위해 다음 record를 도입한다.

```java
MenuRegistrationSpec
AuthRegistrationSpec
```

Spec에서는 다음을 담당한다.

- null / blank 처리
- urlPrefix 정규화
- package나 domain 같은 텍스트 값 trim
- `.do` 포함 여부 같은 입력 관례 정리

### Phase 3. Validator 분리

검증 로직을 Service에서 분리한다.

```text
MenuInputValidator
AuthInputValidator
```

검증 대상은 다음이다.

- null / blank
- 숫자형 입력
- URL prefix 형식
- 프로그램 파일명 형식

### Phase 4. SQL Builder 분리

SQL 문자열 생성은 Builder로 분리한다.

```text
MenuSqlBuilder
AuthSqlBuilder
```

Builder는 다음을 담당한다.

- SQL literal escape
- INSERT SQL 조립
- ROLE_PTTRN 생성
- URL 생성
- 저장 경로 생성

### Phase 5. Result Builder 분리

사용자에게 반환하는 메시지는 Result Builder로 분리한다.

```text
MenuResultBuilder
AuthResultBuilder
```

Result Builder는 다음을 담당한다.

- 제목
- SQL 본문
- 경고
- 선행 조건
- 후속 단계

### Phase 6. Repository 분리

DB 조회를 Repository 성격의 클래스로 분리한다.

```text
MenuRepository
AuthRepository
```

Repository는 다음을 담당한다.

- 메뉴 조회
- 프로그램 조회
- max 값 조회
- 중복 조회

### Phase 7. Workflow 정의 기반화

`WorkflowGuideService`는 workflow 정의와 렌더링을 분리한다.

```text
WorkflowDefinition
WorkflowStep
WorkflowDefinitionRegistry
WorkflowProgressDetector
WorkflowGuideRenderer
```

우선 기존 CRUD workflow를 유지한 뒤, Security/Menu/Auth workflow를 추가한다.

## 9. 구현 우선순위

현실적인 구현 우선순위는 다음과 같다.

```text
1. MenuRegistrationSpec / AuthRegistrationSpec 도입
2. MenuInputValidator / AuthInputValidator 도입
3. MenuSqlBuilder / AuthSqlBuilder 도입
4. MenuService / AuthService를 조율자 역할로 축소
5. MenuServiceTest / AuthServiceTest 추가
6. WorkflowGuideTool에 suggestSecurityMenuAuthWorkflow() 추가
7. WorkflowGuideService 구조 분리
8. WorkflowGuideServiceTest 추가
```

## 10. 최종 판단

현재 `MenuTool`, `AuthTool`, `WorkflowGuideTool`의 구조는 빠른 기능 구현에는 적합하다.

하지만 `SecurityTemplateTool`처럼 계속 기능이 늘어나고 검증 기준이 정교해질 경우, 현재 구조는 장기적으로 유지보수성이 떨어진다.

최종 판단은 다음과 같다.

```text
SecurityTemplateTool
  = 이미 좋은 기준 구조를 가짐
  = Spec / Plan / Renderer / Result 분리

MenuTool / AuthTool
  = 현재는 Service에 로직 집중
  = 앞으로 Spec / Validator / Repository / SqlBuilder / ResultBuilder로 분리 권장

WorkflowGuideTool
  = 현재는 CRUD workflow 고정
  = 앞으로 WorkflowDefinition 기반 구조 권장
```

따라서 앞으로의 구현 방향은 다음이다.

> SecurityTemplateTool은 재구현하지 않는다.  
> MenuTool, AuthTool, WorkflowGuideTool을 SecurityTemplateTool의 아키텍처 원칙에 맞게 점진적으로 리팩터링한다.  
> 단, 파일 생성 파이프라인이 아니라 SQL 생성 / 워크플로우 안내 파이프라인으로 맞춘다.

