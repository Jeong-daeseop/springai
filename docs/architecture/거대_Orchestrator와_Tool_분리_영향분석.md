# 거대 Orchestrator와 Tool 분리 영향 분석

> 작성일: 2026-07-31  
> 대상 프로젝트: `springai`  
> 기준 문서: [거대 Orchestrator와 Tool 분리 방안](./거대_Orchestrator와_Tool_분리_방안.md)  
> 관련 문서: [SpringAI 프로젝트 전체 아키텍처 분석](./SpringAI_프로젝트_전체_아키텍처_분석.md)  
> 상태: 구현 전 영향 분석

## 1. 분석 목적

이 문서는 `CrudPromptBuilderTool`, `ThymeleafLayoutTool`과 세 Generation Orchestration Service를 분리할 때 발생하는 영향을 현재 코드 기준으로 분석한다.

주요 분석 관점은 다음과 같다.

1. 직접 Java 호출 관계
2. MCP 외부 계약
3. 생성 파일과 후처리 순서
4. 부분 실패와 이력 저장 정책
5. Design Reference와 ScreenSpecification 전달
6. 공통 서비스 재사용 관계
7. 테스트와 문서 변경 범위
8. 단계별 구현 위험과 적용 순서

분리의 목적은 클래스 수를 늘리는 것이 아니라 Tool, Application, Domain, Infrastructure 사이의 책임 경계를 복원하는 것이다.

## 2. 종합 판단

[거대 Orchestrator와 Tool 분리 방안](./거대_Orchestrator와_Tool_분리_방안.md)의 방향은 타당하다.

전체 영향도는 **높음**이다. 다만 운영 코드의 직접 호출 관계가 넓어서가 아니라 다음 외부 계약과 부작용을 정확히 보존해야 하기 때문이다.

- MCP Tool 이름과 입력 JSON Schema
- 생성 파일 경로와 파일 수
- CSS·XML·Controller 패치 순서
- 부분 실패 처리 방식
- 기존 문자열 응답
- ScreenSpecification과 Design Reference 전달
- 생성 이력 저장 시점

```text
직접 Java 결합도: 낮음
외부 MCP 계약 위험: 매우 높음
파일 생성 회귀 위험: 매우 높음
DB 영향: 낮음
테스트 변경량: 높음
문서 변경량: 중간 이상
```

## 3. 영역별 영향도

| 영역 | 영향도 | 판단 |
|---|---|---|
| MCP 외부 계약 | 매우 높음 | Tool 이름·파라미터 Optional 여부·설명 변경 시 Claude 호출 영향 |
| 생성 파일 | 매우 높음 | 파일 수, 경로, 생성 순서와 부분 실패 정책 보존 필요 |
| Spring Bean 등록 | 높음 | Tool 분리 시 `McpConfig` 등록 누락·중복 가능 |
| Application Service | 높음 | 신규 Use Case·Planner·Executor·Processor 도입 |
| 테스트 | 높음 | 기존 테스트가 구체 클래스와 문자열 결과에 강하게 결합 |
| REST API | 낮음 | 현재 Orchestrator를 직접 호출하는 REST Controller가 없음 |
| MySQL·Redis | 낮음 | 기존 생성 이력 저장 외 DB Schema 변경 없음 |
| 설정 파일 | 중간 | Bean 구성과 Component Scan 영향 가능 |
| 문서·워크플로우 | 중간 이상 | 대상 클래스 또는 Tool 이름을 언급하는 문서가 58개 |
| 운영 배포 | 중간 | MCP 등록 결과가 동일하면 배포 형식은 유지 가능 |
| 성능 | 중간 | GenerationPlan에서 모든 Source를 보유하면 메모리 증가 가능 |

## 4. 현재 직접 호출 관계

### 4.1 CrudPromptBuilderTool

대상:

```text
src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java
```

운영 코드에서 `CrudPromptBuilderTool`을 직접 참조하는 곳은 사실상 `McpConfig` 하나다.

```text
McpConfig
    ↓
CrudPromptBuilderTool
    ├── CrudOrchestrationService
    ├── BoardOrchestrationService
    ├── MasterDetailOrchestrationService
    ├── Prompt Builder Service
    ├── Schema Service
    ├── Model Factory
    └── Template Renderer
```

직접 영향을 받는 파일:

- `src/main/java/com/krdevops/springai/config/McpConfig.java`
- `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java`
- `src/test/java/com/krdevops/springai/tools/CrudPromptBuilderToolTest.java`
- 신규 MCP Tool·Facade·Use Case 클래스

Java 내부 호출 결합도는 낮지만 외부 MCP Client는 Tool 이름과 입력 Schema에 의존하므로 외부 영향은 크다.

### 4.2 ThymeleafLayoutTool

대상:

```text
src/main/java/com/krdevops/springai/tools/ThymeleafLayoutTool.java
```

운영 코드 직접 참조:

- `McpConfig`

테스트 직접 참조:

- `ThymeleafLayoutToolTest`

이 클래스는 다른 운영 Service에서 직접 호출하지 않으므로 Service 추출 자체의 Java 영향은 제한적이다. 하지만 생성 파일·XML 패치·응답 문자열을 동시에 검증하는 테스트가 많아 동작 회귀 위험은 높다.

### 4.3 Orchestration Service

| Orchestrator | 운영 호출자 | 직접 테스트 |
|---|---|---|
| `CrudOrchestrationService` | `CrudPromptBuilderTool` | `CrudOrchestrationServiceTest` |
| `BoardOrchestrationService` | `CrudPromptBuilderTool` | `BoardOrchestrationServiceTest` |
| `MasterDetailOrchestrationService` | `CrudPromptBuilderTool` | `MasterDetailOrchestrationServiceTest` |

세 Orchestrator의 운영 호출자가 하나이므로 Tool을 신규 Use Case에 먼저 연결하면 Orchestrator 내부를 단계적으로 교체할 수 있다.

## 5. MCP 계약 영향

### 5.1 Tool 객체 수

현재 `McpConfig`에는 Tool 객체 25개가 등록되어 있다.

기준 문서대로 `CrudPromptBuilderTool` 하나를 7개 Tool로 분리하면 예상 등록 객체 수는 다음과 같다.

```text
기존 Tool 객체: 25개
CrudPromptBuilderTool 제거: -1개
분리 Tool 추가: +7개
예상 Tool 객체: 31개
```

기존 `@Tool` 메서드 이름을 그대로 유지하면 전체 MCP 메서드 수는 기존 79개를 유지할 수 있다.

### 5.2 반드시 보존할 계약

- Tool 메서드 이름
- 파라미터 이름
- 파라미터 순서
- Java 타입
- `@Nullable` 적용 여부
- `@Tool` 설명
- 기본값 적용 방식
- 반환 문자열 구조

### 5.3 DTO 전환 위험

기존 MCP 메서드의 개별 파라미터를 DTO 하나로 바꾸면 MCP 입력 JSON Schema가 변경된다.

```java
// 금지: 기존 MCP 입력 Schema가 변경된다.
@Tool
public String buildFullCrudPrompt(BuildFullCrudRequest request) {
    // ...
}
```

Tool 외부 시그니처는 유지하고 내부에서 Application Command로 변환해야 한다.

```java
@Tool
public String buildFullCrudPrompt(
        String database,
        String tableName,
        String domain,
        String packageName,
        String outputPath,
        String llmProvider,
        @Nullable String egovVersion
) {
    return facade.execute(
            new BuildFullCrudCommand(
                    database,
                    tableName,
                    domain,
                    packageName,
                    outputPath,
                    llmProvider,
                    egovVersion));
}
```

### 5.4 중복 Tool 등록

기존 `CrudPromptBuilderTool`을 등록한 상태에서 신규 Tool에도 같은 이름의 `@Tool` 메서드를 추가하면 중복 Tool이 발생한다.

안전한 전환:

```text
신규 Use Case·Facade 추가
    ↓
기존 Tool이 신규 Facade 호출
    ↓
회귀 테스트
    ↓
신규 Tool Adapter 생성
    ↓
McpConfig에서 기존 Tool 제거와 신규 Tool 추가를 동시에 적용
```

기존 Bean과 신규 Bean을 동시에 MCP Provider에 등록하지 않는다.

## 6. 생성 처리 순서 영향

공통 Generation Pipeline을 도입할 때 가장 주의해야 하는 항목이다.

### 6.1 CRUD 현재 순서

```text
Schema·Metadata·Design Context
    ↓
Route·Layout 검증
    ↓
KRDS CSS 사전 패치
    ↓
파일 렌더링·저장
    ↓
기본 진입점 수정
    ↓
Thymeleaf Runtime 설정
    ↓
MyBatis 설정
    ↓
코드 검증
    ↓
계약 감사
    ↓
이력 저장
```

주요 특성:

- 일부 CSS 패치는 파일 렌더링 전에 수행된다.
- Layout 검증 실패 시 파일 생성 전에 종료한다.
- 파일 저장 실패 후에도 나머지 Layer 생성을 계속한다.
- 이력 저장 실패는 이미 생성한 파일 결과를 취소하지 않는다.

### 6.2 게시판 현재 순서

```text
Schema·Metadata·Design Context
    ↓
Route·Layout 검증
    ↓
파일 렌더링·저장
    ↓
Thymeleaf Runtime·Board CSS
    ↓
MyBatis 설정
    ↓
기본 진입점 수정
    ↓
공통 검증·게시판 감사
    ↓
이력 저장
```

CRUD와 달리 게시판 CSS 보강은 파일 생성 뒤에 실행된다.

### 6.3 Master/Detail 현재 순서

```text
Master·Detail Schema
    ↓
FK·Domain·Model 생성
    ↓
Layout 검증
    ↓
파일 렌더링·저장
    ↓
Thymeleaf·MyBatis 설정
    ↓
MainController 수정
    ↓
Servlet Context 수정
    ↓
검증·감사
    ↓
이력 저장
```

Master/Detail은 MainController와 Servlet Context 수정이 고유 후처리로 포함된다.

### 6.4 Thymeleaf Layout 현재 순서

```text
Layout·GNB·Main·Logo 생성
    ↓
Layout 검증
    ↓
Servlet Context 패치
    ↓
MyBatis 설정
    ↓
Servlet 패치 성공 시 Thymeleaf Runtime 설정
```

Servlet Context 패치 실패 시 Thymeleaf Runtime 설정을 건너뛰는 종속 규칙이 존재한다.

### 6.5 Post Processor 정책

공통 Post Processor를 도입할 때 기능별 실행 순서와 실패 정책을 명시해야 한다.

```java
public record PostProcessStep(
        int order,
        String processorId,
        FailurePolicy failurePolicy
) {}
```

필수 실패 정책:

| 정책 | 의미 |
|---|---|
| `STOP` | 이후 모든 단계 중단 |
| `CONTINUE` | 실패를 기록하고 다음 단계 진행 |
| `SKIP_DEPENDENTS` | 이 Processor에 의존하는 단계만 건너뜀 |

단순한 Spring Bean 주입 순서에 Post Processor 실행 순서를 의존하면 안 된다.

## 7. 부분 실패 정책 영향

현재 Generation Orchestrator는 파일 하나가 실패해도 다음 파일 생성을 계속한다.

```java
for (LayerDefinition layer : layers) {
    try {
        renderAndSave(layer);
        succeeded.add(file);
    } catch (Exception e) {
        failed.add(file);
    }
}
```

첫 구조 리팩터링에서 보존해야 할 동작:

- 한 파일 실패 후 다음 파일 생성 계속
- Schema 미존재 시 파일 생성 전 종료
- Metadata 충돌 시 파일 생성 전 종료
- Route 충돌 시 파일 생성 전 종료
- Layout 미존재 시 파일 생성 전 종료
- 생성 이력 실패는 전체 생성 실패로 처리하지 않음
- 검증 실패는 이미 생성한 파일을 삭제하지 않음
- CSS 패치 실패는 기능별 기존 정책 유지

`GenerationPlanExecutor`를 도입하면서 원자적 저장이나 전체 Rollback을 동시에 적용하면 기존 동작이 변경된다.

다음 기능은 구조 리팩터링 후 별도 변경으로 진행한다.

- 임시 디렉터리 기반 프로젝트 생성
- 전체 성공 후 원자적 이동
- 실패 시 생성 파일 Rollback
- 이력과 파일 생성의 보상 트랜잭션

## 8. 생성 결과 영향

### 8.1 파일 개수

기존 테스트는 생성 파일 수를 직접 검증한다.

| 기능 | 대표 생성 파일 수 |
|---|---:|
| CRUD JSP | 11개 |
| CRUD Thymeleaf Create | 16개 |
| 게시판 | 조건에 따라 12개 또는 17개 |
| Master/Detail | 조건에 따라 15개 또는 20개 |

Pipeline 전환 후에도 View Type, Layout Mode별 파일 수를 유지해야 한다.

### 8.2 경로

보존 대상:

- Java Package 기반 경로
- JSP `/WEB-INF/jsp/**` 경로
- Thymeleaf `src/main/resources/templates/**` 경로
- Layout Base Path
- WAR·Boot 정적 Resource 경로
- Mapper XML 경로
- MainController 및 Servlet Context 경로

경로 계산을 Planner로 이동할 때 기존 `resolve*ScreenPath()` 결과를 Characterization Test로 고정해야 한다.

### 8.3 MCP 문자열 응답

현재 Tool 테스트는 다음 문자열을 직접 검증한다.

- `featureType`
- `screen`
- `layerKey`
- 권장 저장 경로
- 생성 Source
- `[auto]` 완료 제목
- `CLAUDE_PROMPT`
- 생성 파일 성공·실패 목록
- 검증 요약
- 이력 저장 요약

기존 Formatter 메서드:

- `formatResult`
- `formatBoardResult`
- `formatMasterDetailResult`
- `formatGeneratedScreen`

`McpResponseFormatter`를 추출하기 전에 기존 결과를 Snapshot으로 고정해야 한다.

## 9. Design Reference와 ScreenSpecification 영향

Tool 분리 과정에서 다음 파라미터가 누락될 위험이 있다.

- `designReferenceId`
- `screenSpecificationId`
- `programFileName`
- `programUrl`
- `programKoreanName`
- `programStorePath`
- `defaultBbsId`

신규 Application Command에는 같은 필드가 포함되어야 한다.

```java
public record GenerateCrudCommand(
        String database,
        String tableName,
        String domain,
        String packageName,
        Path outputPath,
        String egovVersion,
        CrudViewType viewType,
        CrudLayoutMode layoutMode,
        ProgramMetadataOverrides program,
        DesignContextReference designContext
) {}
```

Design Context:

```java
public record DesignContextReference(
        String designReferenceId,
        String screenSpecificationId
) {}
```

이 값이 누락되면 일반 CRUD 생성은 성공하더라도 Semantic Design 규칙이 적용되지 않는 조용한 회귀가 발생한다.

반드시 검증할 시나리오:

1. `screenSpecificationId`가 `designReferenceId`보다 우선
2. Approved ScreenSpecification 적용
3. Metadata 명시값이 DB 자동조회보다 우선
4. Metadata 충돌 시 생성 차단
5. JSP에서 지원하지 않는 Detail Subset 경고 유지

## 10. 공통 서비스 영향

Pipeline으로 묶으려는 기존 서비스는 다른 Tool 또는 Service에서도 사용한다.

주요 공통 서비스:

- `CodeService`
- `CodeValidatorService`
- `GenerationHistoryService`
- `ThymeleafRuntimeConfigurer`
- `ThymeleafLayoutValidator`
- `MyBatisRuntimeConfigurer`
- `KrdsStylesConfigurer`
- `GeneratedCodeContractAuditor`
- `WarEntryPointConfigurer`

### 10.1 직접 이동 위험

`CodeService`는 다음에서도 사용한다.

- `CodeSaverTool`
- `ToolApiController`
- 기존 Generation Service

`CodeValidatorService`와 `GenerationHistoryService`는 별도 MCP Tool에도 노출된다.

따라서 초기 단계에서는 기존 서비스를 이동하거나 이름을 바꾸지 않는다.

권장 위임:

```text
GenerationPlanExecutor
    ↓
CodeService

GenerationVerifier
    ├── CodeValidatorService
    └── GeneratedCodeContractAuditor

GenerationHistoryRecorder
    ↓
GenerationHistoryService
```

기능 중심 패키지 이동은 Port를 도입한 후 마지막 단계에서 수행한다.

### 10.2 기존 서비스 재사용

초기 Pipeline은 기존 서비스의 Adapter 역할을 한다.

```java
@Component
@RequiredArgsConstructor
public class ExistingCodeServiceGenerationExecutor
        implements GenerationPlanExecutor {

    private final CodeService codeService;

    @Override
    public GenerationExecution execute(GenerationPlan plan) {
        // 기존 saveGeneratedCode 결과와 실패 문자열 정책 보존
    }
}
```

동작 보존이 확인된 후 `CodeService`의 문자열 결과를 구조화 Result로 전환할 수 있다.

## 11. 테스트 영향

### 11.1 직접 영향 테스트 규모

| 테스트 | 줄 수 |
|---|---:|
| `CrudOrchestrationServiceTest` | 621 |
| `BoardOrchestrationServiceTest` | 421 |
| `MasterDetailOrchestrationServiceTest` | 381 |
| `CrudPromptBuilderToolTest` | 389 |
| `ThymeleafLayoutToolTest` | 516 |
| 합계 | 2,328 |

이 테스트에는 파일 수, 경로, 문자열과 실패 메시지를 확인하는 Assertion이 약 155개 존재한다.

### 11.2 테스트 재배치

| 기존 테스트 책임 | 이동 위치 |
|---|---|
| Tool 업무 분기 검증 | MCP Facade 테스트 |
| Tool Schema·Model 생성 검증 | Planner 테스트 |
| 파일 저장 성공·실패 | Executor 테스트 |
| XML·CSS·Runtime 패치 | Post Processor 테스트 |
| 응답 문자열 | Response Formatter 테스트 |
| 전체 파일 수와 내용 | Integration·Golden File 테스트 |
| Tool 등록 | MCP Tool Definition Snapshot |

### 11.3 테스트 유지 원칙

기존 테스트를 먼저 삭제하면 안 된다.

```text
기존 테스트 유지
    ↓
Characterization Test 보강
    ↓
신규 계층 테스트 추가
    ↓
동작 동일성 확인
    ↓
중복 테스트 정리
```

### 11.4 신규 필수 테스트

#### MCP Tool Definition Snapshot

- 전체 Tool 이름
- 전체 Tool 수
- 입력 JSON Schema
- Optional 파라미터
- Tool 설명

#### Golden File

- CRUD JSP
- CRUD Thymeleaf Reuse
- CRUD Thymeleaf Create
- 게시판 JSP·Thymeleaf
- Master/Detail JSP·Thymeleaf
- Layout·GNB·Main 파일

#### 실패 정책

- Schema 미존재
- Metadata 충돌
- Route 충돌
- Layout 미존재
- 파일 일부 저장 실패
- Template Rendering 실패
- Runtime 설정 실패
- 계약 감사 실패
- 생성 이력 실패

#### Post Processor 순서

- CRUD CSS 선처리
- 게시판 CSS 후처리
- Layout Servlet 패치 실패 후 Thymeleaf Runtime Skip
- Master/Detail MainController와 Servlet Context 수정 순서

## 12. 문서와 Workflow 영향

다음 이름을 언급하는 문서가 현재 58개다.

- `CrudPromptBuilderTool`
- `ThymeleafLayoutTool`
- `CrudOrchestrationService`
- `BoardOrchestrationService`
- `MasterDetailOrchestrationService`

Tool 메서드 이름을 유지하면 사용자 가이드 영향은 제한적이다. 클래스 구조와 실행 흐름을 설명하는 문서는 갱신이 필요하다.

우선 갱신 대상:

- `docs/tool-reference/CrudPromptBuilderTool_기능및역할_상세설명.md`
- `docs/tool-reference/ThymeleafLayoutTool_기능및역할_상세설명.md`
- `docs/tool-reference/MCP_Tool_전체목록.md`
- `docs/tool-catalog.md`
- `docs/architecture/SpringAI_프로젝트_전체_아키텍처_분석.md`
- `docs/crud/CrudPromptBuilderTool_WorkflowDefinition_관계_검토.md`

다음 Production 클래스는 Tool 이름을 문자열로 안내한다.

- `WorkflowDefinitionRegistry`
- `ResultBuilder`
- `WorkflowGuideService`
- `OutputPathResolverService`
- `TableRelationService`

기존 Tool 이름을 유지하면 이 클래스들의 동작 변경은 없다. 신규 Tool 이름을 도입하거나 기존 이름을 Deprecated 처리할 때 별도 변경이 필요하다.

## 13. 단계별 영향도

| 단계 | 영향도 | 주요 영향 | 필수 게이트 |
|---|---|---|---|
| 단계 0: MCP 계약 고정 | 낮음 | 테스트 추가 | 전체 Tool Snapshot |
| 단계 1: Layout Tool 분리 | 중간 | 파일·XML·Runtime 설정 이동 | 기존 Layout 결과 동일 |
| 단계 2: 단일 화면 Source 분리 | 중간 | Tool 응답과 경로 계산 이동 | Source·경로 Snapshot |
| 단계 3: Prompt·자동 생성 분리 | 높음 | `auto/claude` 분기와 Design Context 전달 | 옵션 전달 테스트 |
| 단계 4: CRUD Pipeline | 매우 높음 | 전체 파일 생성 순서와 실패 정책 | CRUD Golden File |
| 단계 5: Board·Master/Detail | 높음 | 기능별 후처리와 감사 로직 | 기능별 통합 테스트 |
| 단계 6: Tool 클래스 분리 | 매우 높음 | MCP 등록과 외부 Schema | Tool Definition Snapshot |
| 단계 7: 패키지 이동 | 높음 | Import, 테스트, 문서, Component Scan | 전체 테스트·빌드 |

## 14. 단계별 상세 영향

### 14.1 단계 0: MCP 계약 고정

변경 대상:

- 신규 MCP Registration Test
- Tool Definition Snapshot Fixture

운영 영향:

- 없음

위험:

- 낮음

완료 조건:

- 기존 전체 Tool 이름과 Schema를 추출할 수 있음
- Figma Tool 일부뿐 아니라 전체 Tool 계약을 검증

### 14.2 단계 1: ThymeleafLayoutTool 시험 분리

변경 대상:

- `ThymeleafLayoutTool`
- 신규 `GenerateThymeleafLayoutUseCase`
- 신규 `ThymeleafLayoutGenerationService`
- 신규 XML Configurer
- 신규 Asset Copier
- 신규 Response Formatter
- `ThymeleafLayoutToolTest`

직접 동작 위험:

- Layout 파일 덮어쓰기 정책
- GNB 파일 보존 정책
- 로고 파일 보존 정책
- 잘못된 XML 무수정 보장
- Component Scan 상위 Package 계산
- Servlet Patch 실패 후 Runtime 설정 Skip

이 단계는 단일 Tool이지만 동작 검증 범위가 넓으므로 중간 영향으로 평가한다.

### 14.3 단계 2: 단일 화면 Source 생성

변경 대상:

- `CrudPromptBuilderTool`
- 신규 `ScreenSourceGenerationService`
- 신규 Command·Result
- 신규 경로 Resolver 또는 기존 Resolver 재사용
- `CrudPromptBuilderToolTest`

위험:

- 권장 저장 경로 변경
- JSP·Thymeleaf Layer Key 변경
- 게시판 기본 테이블 해석 누락
- Master/Detail FK 추론 변경
- 응답 Source 구분자 변경

파일을 실제 저장하지 않는 흐름이므로 전체 생성 Pipeline보다 운영 위험은 낮다.

### 14.4 단계 3: Prompt와 자동 생성 분리

변경 대상:

- `CrudPromptBuilderTool`
- `CrudPromptBuilderService`
- `MasterDetailService`
- 세 Orchestration Service 호출부
- 신규 Dispatch Use Case
- 신규 Prompt Use Case
- 신규 Generation Use Case

주요 위험:

- `llmProvider` 기본값 변경
- `auto`와 `claude` 분기 역전
- Design Context 옵션 유실
- Metadata 명시값 우선순위 변경
- 기존 Prompt 문자열 변경

이 단계부터 업무 의미가 이동하므로 높은 영향으로 평가한다.

### 14.5 단계 4: CRUD Generation Pipeline

변경 대상:

- `CrudOrchestrationService`
- 신규 `CrudGenerationPlanner`
- 신규 `GenerationPlanExecutor`
- 신규 Post Processor
- 신규 Verifier
- 신규 History Recorder
- 신규 Result Assembler
- `CrudOrchestrationServiceTest`

주요 위험:

- CSS 처리 시점 변경
- 파일 생성 순서 변경
- 파일 일부 실패 정책 변경
- Runtime 설정 실행 조건 변경
- 검증과 감사 결과 문자열 변경
- 이력 저장 시점 변경

전체 단계 중 가장 큰 생성 결과 회귀 위험을 가진다.

### 14.6 단계 5: Board와 Master/Detail Pipeline

게시판 변경:

- `BoardOrchestrationService`
- `BoardGenerationPlanner`
- `BoardCssPostProcessor`
- `BoardContractVerifier`
- `BoardGenerationResultAssembler`

Master/Detail 변경:

- `MasterDetailOrchestrationService`
- `MasterDetailGenerationPlanner`
- `MasterDetailRelationshipResolver`
- `MasterDetailNamingPolicy`
- `MainControllerPostProcessor`
- `ServletContextPostProcessor`
- `MasterDetailGenerationResultAssembler`

주요 위험:

- 게시판 기본 테이블 Resolver 누락
- `bbsId`와 등록 URL 누락
- 게시판 감사 결과 누락
- FK 추론 변경
- Detail Domain명 변경
- MainController와 Servlet Context 패치 변경

CRUD Pipeline을 먼저 검증한 후 적용하면 공통 실행 위험을 줄일 수 있다.

### 14.7 단계 6: MCP Tool 클래스 분리

변경 대상:

- `CrudPromptBuilderTool`
- 신규 MCP Tool 7개
- `McpConfig`
- Tool 등록 테스트
- Tool 단위 테스트

주요 위험:

- Tool 등록 누락
- 중복 Tool 이름
- 선택 파라미터가 필수로 변경
- 입력 JSON Schema 변경
- Tool 설명 변경으로 Claude 선택 결과 변화

내부 동작은 이미 Use Case에 있어야 하므로 이 단계에서는 Adapter 이동만 수행한다.

### 14.8 단계 7: 기능 중심 패키지 이동

영향 대상:

- 신규 Generation 클래스 전체
- Import를 가진 테스트
- `McpConfig`
- Component Scan
- 문서 58개 중 구조 설명 문서

이 단계에서는 로직을 변경하지 않는다.

## 15. 분리 문서 보완 사항

### 15.1 Post Processor 실행 순서

기준 문서에 기능별 Processor 순서와 실패 정책을 추가해야 한다.

```java
public record GenerationPipelinePolicy(
        FeatureType featureType,
        List<PostProcessStep> steps
) {}
```

### 15.2 기존 Result 타입 보존

첫 리팩터링에서는 다음 타입을 유지한다.

- `CrudOrchestrationResult`
- `BoardOrchestrationResult`
- `MasterDetailOrchestrationResult`

통합 `GenerationResult`는 내부 결과로만 사용하고 기존 타입으로 변환한다.

### 15.3 GenerationPlan 메모리 정책

기준 문서의 `GeneratedFilePlan`은 전체 Source 문자열을 보유한다. 현재 파일별 순차 렌더링보다 메모리 사용량이 증가할 수 있다.

필요한 제한:

- 파일별 최대 Source 크기
- 전체 Plan 최대 크기
- Binary Asset은 Source 문자열에 포함하지 않음
- 로고 등 Binary는 Asset Post Processor에서 처리

대안:

```text
GenerationBlueprint
    ↓
GenerationRenderer
    ↓
RenderedGenerationPlan
    ↓
GenerationPlanExecutor
```

이 구조는 경로와 Layer 계획, Rendering, 파일 저장을 각각 분리한다.

### 15.4 행위 개선과 구조 개선 분리

구조 리팩터링 PR에 포함하지 않을 항목:

- 파일 생성 전체 Rollback
- 원자적 프로젝트 생성
- 신규 Tool 이름 추가
- 기존 Tool 이름 제거
- 응답 문자열을 JSON으로 전환
- 공통 Result 타입 외부 노출
- 파일 수 또는 경로 정책 변경
- Template 내용 개선

### 15.5 Compatibility Facade 수명

Deprecated Compatibility Facade는 영구 유지하지 않는다.

권장 정책:

```text
리팩터링 릴리스 N
    기존 Facade 유지

릴리스 N+1
    내부 호출자 제거 확인

릴리스 N+2
    Compatibility Facade 삭제
```

외부 MCP Tool 이름은 Facade 클래스 삭제와 관계없이 유지한다.

## 16. 주요 위험과 대응

| 위험 | 가능성 | 영향 | 대응 |
|---|---|---|---|
| MCP Tool Schema 변경 | 중간 | 매우 높음 | 전체 Tool Definition Snapshot |
| Tool 중복 등록 | 중간 | 높음 | 기존·신규 등록 원자적 교체 |
| 생성 파일 수·경로 변경 | 중간 | 매우 높음 | Golden File·파일 트리 Snapshot |
| Processor 순서 변경 | 높음 | 높음 | 기능별 Pipeline Policy 테스트 |
| 부분 실패 정책 변경 | 중간 | 높음 | Failure Policy Characterization |
| Design Context 옵션 누락 | 중간 | 높음 | Command 필드와 전달 테스트 |
| Prompt 문자열 변경 | 중간 | 중간 | Prompt Snapshot |
| Plan 메모리 증가 | 중간 | 중간 | 크기 제한과 Binary 분리 |
| 패키지 이동 시 Import 누락 | 높음 | 중간 | 마지막 별도 PR에서 이동 |
| 문서 불일치 | 높음 | 낮음 | 릴리스 체크리스트에 문서 갱신 포함 |

## 17. 권장 PR 적용 순서

### PR 1: MCP 계약 고정

```text
전체 Tool Definition Snapshot
전체 Tool 이름·Schema·설명 검증
```

변경 위험: 낮음

### PR 2: ThymeleafLayoutTool 서비스 추출

```text
Tool 외부 계약 유지
파일·XML·Runtime 로직을 Use Case로 이동
```

변경 위험: 중간

### PR 3: 단일 화면 Source 생성 분리

```text
ScreenSourceGenerationService
GeneratedSource
Response Formatter
```

변경 위험: 중간

### PR 4: Prompt와 자동 생성 Use Case 분리

```text
CrudGenerationDispatchUseCase
BuildCrudPromptUseCase
GenerateCrudProjectUseCase
```

변경 위험: 높음

### PR 5: CRUD Pipeline

```text
Planner
Executor
Post Processor
Verifier
History Recorder
Result Assembler
```

변경 위험: 매우 높음

### PR 6: Board Pipeline

```text
BoardGenerationPlanner
BoardCssPostProcessor
BoardContractVerifier
```

변경 위험: 높음

### PR 7: Master/Detail Pipeline

```text
MasterDetailGenerationPlanner
RelationshipResolver
NamingPolicy
MainControllerPostProcessor
ServletContextPostProcessor
```

변경 위험: 높음

### PR 8: MCP Tool 클래스 분리

```text
CrudPromptBuilderTool 제거
신규 Tool Adapter 7개 등록
McpConfig 원자적 교체
```

변경 위험: 매우 높음

### PR 9: 기능 중심 패키지 이동

```text
Import와 Package 이동
문서와 다이어그램 갱신
로직 변경 없음
```

변경 위험: 높음

## 18. 단계별 테스트 게이트

모든 PR 공통:

```bash
./gradlew test
```

PR 1 이후 공통:

```text
MCP Tool Definition Snapshot 일치
```

PR 2:

```text
ThymeleafLayoutToolTest
Layout Golden File
XML 무수정·멱등 테스트
```

PR 3:

```text
CRUD·Board·Master/Detail Source 경로 Snapshot
```

PR 4:

```text
auto·claude 분기
Design Reference·ScreenSpecification 전달
Prompt Snapshot
```

PR 5~7:

```text
기능별 Orchestration Test
Generated File Tree
Golden File
Post Processor 순서
부분 실패 정책
생성 프로젝트 빌드
```

PR 8:

```text
전체 Tool 이름·Schema·설명 동일
중복 Tool 없음
Claude Desktop MCP 호출 확인
```

PR 9:

```text
전체 컴파일
전체 테스트
Component Scan 확인
문서 링크 확인
```

## 19. Go/No-Go 기준

### Go

- 전체 MCP Tool 계약 Snapshot이 존재함
- 대표 생성 프로젝트 Golden File이 존재함
- Post Processor 현재 순서가 문서화됨
- Design Context 옵션 전달 테스트가 존재함
- 각 단계가 독립 PR로 분리됨

### No-Go

- Tool 시그니처를 DTO 하나로 변경함
- 기존 Tool과 신규 Tool을 동시에 등록함
- Planner 도입과 파일 Rollback을 동시에 구현함
- Tool 분리와 패키지 이동을 같은 PR에서 수행함
- 기존 Orchestration Test를 먼저 삭제함
- 파일 수·경로 변경을 구조 리팩터링에 포함함

## 20. 최종 권고

분리 작업은 진행하는 것이 적절하다.

기대 효과:

- Tool 계층이 프로젝트 규칙에 맞는 얇은 Adapter로 복원
- 세 Orchestrator의 생성·검증·이력 중복 제거
- 단일 화면, 전체 생성, Prompt 생성의 Use Case 경계 명확화
- JSP·Thymeleaf·향후 Figma Generator 확장 용이
- 파일 생성과 후처리 단위 테스트 가능

다만 이 작업은 **구조적 영향은 크고 직접 호출 영향은 작은 리팩터링**이다.

가장 안전한 시작점:

```text
1. MCP 계약 Snapshot
2. ThymeleafLayoutTool 시험 분리
3. 단일 화면 Source 분리
4. Prompt·자동 생성 Use Case 분리
5. CRUD Pipeline
6. Board·Master/Detail Pipeline
7. Tool Adapter 클래스 분리
8. 패키지 이동과 문서 갱신
```

MCP 계약 Snapshot 없이 Tool 클래스부터 분리하는 방식은 권장하지 않는다.
