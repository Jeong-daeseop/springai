# 거대 Orchestrator와 Tool 분리 방안

> 작성일: 2026-07-31  
> 대상 프로젝트: `springai`  
> 관련 문서: [SpringAI 프로젝트 전체 아키텍처 분석](./SpringAI_프로젝트_전체_아키텍처_분석.md)  
> 상태: 구현 전 설계안

## 1. 목적

현재 소스 생성 영역의 MCP Tool과 Orchestration Service는 기능 확장 과정에서 다음 책임을 동시에 담당하게 되었다.

- MCP 입력 수신과 응답 문자열 생성
- 입력 기본값과 실행 방식 결정
- DB Schema와 프로그램 Metadata 조회
- ScreenSpecification 조회
- Template Model 생성
- 파일 렌더링과 저장
- Thymeleaf·MyBatis·WAR 설정 보강
- CSS·XML·Controller 수정
- 정적 검증과 계약 감사
- 생성 이력 저장

이 문서의 목적은 거대 클래스를 단순히 여러 파일로 나누는 것이 아니라 다음 계층 경계를 명확하게 만드는 것이다.

```text
MCP Tool
    ↓
MCP Facade
    ↓
Use Case
    ↓
Feature Application Service
    ↓
Generation Planner
    ↓
Generation Plan Executor
    ↓
Project Post Processor
    ↓
Generation Verifier
    ↓
History Recorder
```

분리 과정에서는 다음 원칙을 지킨다.

1. 기존 MCP Tool 이름과 입력 스키마를 보존한다.
2. 기존 생성 파일과 응답 결과의 동작을 먼저 보존한다.
3. 패키지 이동과 로직 분리를 동시에 수행하지 않는다.
4. 공통 추상 부모 클래스보다 작은 컴포넌트 조합을 사용한다.
5. Gradle 멀티 모듈이나 마이크로서비스 분리는 이번 범위에서 제외한다.

## 2. 분석 범위

### 2.1 우선 분리 대상

| 클래스 | 줄 수 | 의존성 수 | 주요 문제 |
|---|---:|---:|---|
| `CrudPromptBuilderTool` | 1,015 | 16 | CRUD·게시판·Master/Detail과 세 가지 실행 방식을 모두 담당 |
| `ThymeleafLayoutTool` | 430 | 5 | Tool이 파일 생성·복사·XML 패치·런타임 설정까지 수행 |
| `CrudOrchestrationService` | 324 | 15 | Schema부터 생성 이력까지 한 실행 메서드에서 처리 |
| `BoardOrchestrationService` | 280 | 17 | CRUD와 유사한 공통 파이프라인 중복 |
| `MasterDetailOrchestrationService` | 300 | 10 | 생성·설정·검증과 XML·Controller 패치 혼재 |

### 2.2 이번 분리에서 제외할 클래스

다음 클래스는 줄 수만으로 분리하지 않는다.

| 클래스 | 판단 |
|---|---|
| `WebCaptureOrchestrationService` | 57줄, 단일 `capture()` 흐름으로 응집도가 높아 유지 |
| `FigmaExportTool` | Facade 하나에 위임하는 얇은 Tool이므로 유지 |
| `DesignSystemTool` | Facade 위임 구조이므로 유지 |
| `FigmaHybridExportService` | 규모는 크지만 Hybrid 변환 Use Case 경계가 명확하여 후순위 |
| `FigmaScreenExportService` | Figma Screen Export 단일 도메인에 집중되어 후순위 |

클래스 크기는 분리 판단의 보조 지표다. 직접적인 판단 기준은 책임 수, 의존성 방향, 부작용 범위와 변경 이유의 개수다.

## 3. 현재 Tool 계층 분석

### 3.1 CrudPromptBuilderTool

대상 파일:

```text
src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java
```

이 클래스는 16개의 서비스를 직접 의존한다.

```text
CrudOrchestrationService
CrudProgramMetadataService
CrudSchemaQueryService
CrudModelFactory
CrudTemplateRenderer
CrudPromptBuilderService
MasterDetailService
MasterDetailTemplateRenderer
MasterDetailOrchestrationService
BoardSchemaService
BoardModelFactory
BoardTemplateRenderer
BoardOrchestrationService
BoardTableSetResolver
BoardProgramMetadataService
GenerationDesignContextService
```

### MCP 기능 분류

| 기능 분류 | MCP 메서드 |
|---|---|
| CRUD 전체 생성 또는 Prompt | `buildFullCrudPrompt` |
| Master/Detail 전체 생성 또는 Prompt | `buildMasterDetailPrompt` |
| JOIN Prompt | `buildJoinSelectPrompt` |
| 게시판 전체 생성 | `buildBoardFeature` |
| CRUD 단일 화면 | `generateCrudList`, `generateCrudDetail`, `generateCrudRegist`, `generateCrudUpdt` |
| 게시판 단일 화면 | `generateBoardList`, `generateBoardDetail`, `generateBoardRegist`, `generateBoardUpdt` |
| Master/Detail 단일 화면 | `generateMasterList`, `generateMasterDetail`, `generateMasterRegist`, `generateMasterUpdt` |

### 혼재된 실행 의미

현재 하나의 Tool에는 다음 세 가지 서로 다른 Use Case가 섞여 있다.

```text
1. Prompt 생성
   DB와 Metadata를 읽고 LLM 지시문만 반환

2. 전체 프로젝트 생성
   Template을 렌더링하고 실제 파일을 저장·검증

3. 단일 화면 소스 미리보기
   화면 코드와 권장 저장 경로만 반환
```

특히 `buildFullCrudPrompt()`는 `llmProvider` 값에 따라 동작 의미가 달라진다.

```text
llmProvider=auto
    → CrudOrchestrationService 실행
    → 파일 생성·저장
    → 검증과 이력 저장

llmProvider=claude
    → Metadata 조회
    → ScreenSpecification 조회
    → LLM Prompt 반환
```

메서드 이름은 Prompt 생성을 의미하지만 `auto`에서는 실제 파일 생성까지 수행한다. 이는 하위 호환을 위해 당장 변경하지 않되 내부 Use Case는 분리해야 한다.

### Tool 내부에 존재하는 비즈니스 로직

- 기본 eGovFrame 버전 결정
- 기본 View Type 결정
- LLM Provider 분기
- Metadata 우선순위 해석
- ScreenSpecification 선택
- Schema 미존재 처리
- FK 컬럼 추론
- Detail Domain명 생성
- Layer Key 선택
- 생성 파일 경로 계산
- MCP 응답 문자열 생성

이 로직은 MCP Adapter 책임이 아니므로 Application 계층으로 이동해야 한다.

### 3.2 ThymeleafLayoutTool

대상 파일:

```text
src/main/java/com/krdevops/springai/tools/ThymeleafLayoutTool.java
```

현재 단일 MCP 메서드가 다음을 모두 처리한다.

1. Layout HTML 5개 생성
2. GNB Java·Mapper 관련 파일 4개 생성
3. 로고 이미지 복사
4. Main Thymeleaf 화면 생성
5. Layout 파일 존재 검증
6. `servlet-context.xml` 패치
7. Component Scan Base Package 패치
8. MyBatis Mapper 설정
9. Thymeleaf Runtime과 ViewResolver 설정
10. MCP 결과 문자열 생성

Tool에 다음 저수준 작업이 직접 존재한다.

- `Files.exists`
- `Files.copy`
- `Paths.get`
- XML 문자열 검색과 치환
- Template Renderer 호출
- Generated Code 저장
- `StringBuilder` 응답 조립

이 클래스는 Tool Adapter가 아니라 Layout Generation Application Service 역할을 하고 있다.

## 4. 현재 Orchestrator 분석

### 4.1 CrudOrchestrationService

핵심 `orchestrate()` 메서드는 다음 단계를 모두 처리한다.

1. 옵션과 View 정책 결정
2. 테이블 Schema 조회
3. 패키지 규칙 검증
4. 프로그램 Metadata 조회
5. 생성 차단 정책 확인
6. ScreenSpecification 해석
7. Template Model 생성
8. Detail Subset 정책 확인
9. Controller URL Alias 충돌 확인
10. Thymeleaf Layout 존재 검증
11. KRDS Table Density CSS 보강
12. Form Column Layout CSS 보강
13. 전체 Layer 렌더링
14. 파일 저장
15. 기본 진입점 수정
16. Thymeleaf Runtime 보강
17. Controller Component Scan 보강
18. MyBatis Runtime 보강
19. 생성 코드 검증
20. 공통 생성 계약 감사
21. 생성 이력 저장
22. 최종 결과 조립

### 직접 분리해야 하는 책임

| 현재 책임 | 이동 대상 |
|---|---|
| 입력 기본값과 정책 결정 | `CrudGenerationCommandFactory` |
| Schema·Metadata·Design Context 해석 | `CrudGenerationPlanner` |
| Alias·Layout 사전 검증 | `GenerationPreflightService` |
| CSS 보강 | `KrdsCssPostProcessor` |
| Layer 렌더링과 저장 | `GenerationPlanExecutor` |
| Entry Point와 Runtime 설정 | `GeneratedProjectPostProcessor` |
| 코드 검증과 감사 | `GenerationVerifier` |
| 생성 이력 | `GenerationHistoryRecorder` |
| 결과 조립 | `CrudGenerationResultAssembler` |

### 4.2 BoardOrchestrationService

CRUD Orchestrator와 다음 처리를 중복한다.

- 패키지 검증
- Schema 조회
- Metadata·Design Context 해석
- View·Layout 정책 결정
- URL Alias 검증
- Layout 존재 검증
- Layer 반복 렌더링
- 파일 저장
- Thymeleaf Runtime 설정
- Controller Component Scan 설정
- MyBatis 설정
- 기본 진입점 수정
- 정적 코드 검증
- 공통 계약 감사
- 생성 이력 저장

게시판에만 필요한 책임은 다음과 같다.

- 게시판 관련 5개 테이블 해석
- 게시판 Metadata와 `bbsId` 처리
- 게시판 Template Model 생성
- 게시판 전용 CSS 보강
- 게시판 생성 코드 감사

공통 실행 코드는 Pipeline으로 이동하고 게시판 고유 코드는 `BoardGenerationPlanner`, `BoardContractVerifier`, `BoardCssPostProcessor`에 남긴다.

### 4.3 MasterDetailOrchestrationService

공통 Pipeline 외에 다음 고유 책임이 있다.

- Master·Detail Schema 동시 조회
- FK 컬럼 추론
- Detail Domain명 생성
- MasterDetail Template Model 조립
- 기본 MainController 수정
- `servlet-context.xml` Component Scan 수정

다음과 같이 이동해야 한다.

| 현재 책임 | 이동 대상 |
|---|---|
| FK 추론 | `MasterDetailRelationshipResolver` |
| Detail Domain명 결정 | `MasterDetailNamingPolicy` |
| 두 Model 조립 | `MasterDetailGenerationPlanner` |
| MainController 수정 | `MainControllerPostProcessor` |
| Servlet XML 수정 | `ServletContextPostProcessor` |

## 5. 목표 책임 모델

### 5.1 MCP Tool

Tool은 다음 네 가지 작업만 수행한다.

1. MCP 파라미터 수신
2. Command 변환
3. Facade 또는 Use Case 호출
4. MCP 응답 변환

Tool에서 금지할 작업:

- DB 조회
- Template Renderer 호출
- 파일 저장
- XML·CSS·Controller 수정
- ScreenSpecification 조회
- 업무 실행 방식 분기
- 생성 경로 계산
- 업무 성공·실패 결정

### 목표 예시

```java
@Component
@RequiredArgsConstructor
public class CrudGenerationTool {

    private final CrudGenerationMcpFacade facade;

    @Tool(description = CrudToolDescriptions.BUILD_FULL_CRUD)
    public String buildFullCrudPrompt(
            String database,
            String tableName,
            String domain,
            String packageName,
            String outputPath,
            String llmProvider,
            String egovVersion,
            String viewType
    ) {
        return facade.buildFullCrud(
                new BuildFullCrudMcpRequest(
                        database,
                        tableName,
                        domain,
                        packageName,
                        outputPath,
                        llmProvider,
                        egovVersion,
                        viewType));
    }
}
```

### 5.2 MCP Facade

Facade는 MCP 전용 동작을 담당한다.

- MCP Request를 Application Command로 변환
- 하위 호환 분기
- 구조화된 결과를 문자열로 직렬화
- 민감 데이터 제거
- MCP 예외 형식 변환

Facade는 DB, Renderer, FileSystem을 직접 사용하지 않는다.

### 5.3 Use Case

Use Case는 외부 Adapter가 호출할 명확한 업무 경계다.

```java
public interface GenerateCrudProjectUseCase {
    CrudGenerationResult execute(GenerateCrudCommand command);
}

public interface BuildCrudPromptUseCase {
    PromptGenerationResult execute(BuildCrudPromptCommand command);
}

public interface GenerateBoardProjectUseCase {
    BoardGenerationResult execute(GenerateBoardCommand command);
}

public interface GenerateMasterDetailProjectUseCase {
    MasterDetailGenerationResult execute(GenerateMasterDetailCommand command);
}

public interface GenerateScreenSourceUseCase {
    GeneratedSource execute(GenerateScreenSourceCommand command);
}

public interface GenerateThymeleafLayoutUseCase {
    LayoutGenerationResult execute(GenerateThymeleafLayoutCommand command);
}
```

## 6. Tool 클래스 분리안

`CrudPromptBuilderTool`은 다음 Tool Adapter로 분리한다.

| 신규 Tool | 이전할 MCP 메서드 |
|---|---|
| `CrudGenerationTool` | `buildFullCrudPrompt` |
| `BoardGenerationTool` | `buildBoardFeature` |
| `MasterDetailGenerationTool` | `buildMasterDetailPrompt` |
| `JoinQueryTool` | `buildJoinSelectPrompt` |
| `CrudScreenSourceTool` | `generateCrudList`, `generateCrudDetail`, `generateCrudRegist`, `generateCrudUpdt` |
| `BoardScreenSourceTool` | `generateBoardList`, `generateBoardDetail`, `generateBoardRegist`, `generateBoardUpdt` |
| `MasterDetailScreenSourceTool` | `generateMasterList`, `generateMasterDetail`, `generateMasterRegist`, `generateMasterUpdt` |

### 목표 의존성

```text
CrudGenerationTool
    └── CrudGenerationMcpFacade

BoardGenerationTool
    └── BoardGenerationMcpFacade

MasterDetailGenerationTool
    └── MasterDetailGenerationMcpFacade

CrudScreenSourceTool
    └── ScreenSourceMcpFacade

BoardScreenSourceTool
    └── ScreenSourceMcpFacade

MasterDetailScreenSourceTool
    └── ScreenSourceMcpFacade
```

### MCP 하위 호환

Spring AI MCP Tool 이름은 기본적으로 `@Tool`이 붙은 메서드 이름을 사용한다. Tool 클래스가 나뉘어도 기존 메서드 이름을 보존하면 MCP Tool 이름을 유지할 수 있다.

유지 대상:

```text
buildFullCrudPrompt
buildMasterDetailPrompt
buildJoinSelectPrompt
buildBoardFeature
generateCrudList
generateCrudDetail
generateCrudRegist
generateCrudUpdt
generateBoardList
generateBoardDetail
generateBoardRegist
generateBoardUpdt
generateMasterList
generateMasterDetail
generateMasterRegist
generateMasterUpdt
```

수동 등록 방식이므로 `McpConfig.allToolCallbacks()`에 분리된 Tool 객체를 모두 추가해야 한다.

## 7. 실행 의미별 Use Case 분리

### 7.1 Prompt 생성

```java
public interface BuildCrudPromptUseCase {
    PromptGenerationResult execute(BuildCrudPromptCommand command);
}
```

특성:

- DB Schema와 Metadata를 읽는다.
- ScreenSpecification을 해석할 수 있다.
- LLM에 전달할 Prompt만 반환한다.
- 프로젝트 파일을 생성하거나 저장하지 않는다.

### 7.2 전체 프로젝트 생성

```java
public interface GenerateCrudProjectUseCase {
    CrudGenerationResult execute(GenerateCrudCommand command);
}
```

특성:

- 결정적 Template Rendering을 사용한다.
- 파일을 저장한다.
- 프로젝트 Runtime 설정을 보강한다.
- 정적 검증과 계약 감사를 수행한다.
- 생성 이력을 기록한다.

### 7.3 단일 화면 소스 생성

```java
public interface GenerateScreenSourceUseCase {
    GeneratedSource execute(GenerateScreenSourceCommand command);
}
```

특성:

- 파일을 저장하지 않는다.
- 화면 Source와 권장 경로를 구조화하여 반환한다.
- CRUD·게시판·Master/Detail Strategy를 선택한다.

### 구조화된 결과

```java
public record GeneratedSource(
        FeatureType featureType,
        String domain,
        ScreenType screenType,
        CrudViewType viewType,
        String layerKey,
        Path recommendedPath,
        String source
) {}
```

Application Service가 문자열을 직접 반환하지 않고 MCP Facade가 최종 응답을 만든다.

## 8. 공통 Generation Pipeline

### 8.1 GenerationPlanner

실제 파일을 쓰지 않고 전체 생성 계획을 만든다.

```java
public interface GenerationPlanner<C> {
    GenerationPlan plan(C command);
}
```

```java
public record GenerationPlan(
        GenerationContext context,
        List<GeneratedFilePlan> files,
        List<PostProcessStep> postProcessSteps,
        List<GenerationWarning> warnings
) {}
```

```java
public record GeneratedFilePlan(
        String layerKey,
        Path targetPath,
        String content
) {}
```

기능별 Planner:

- `CrudGenerationPlanner`
- `BoardGenerationPlanner`
- `MasterDetailGenerationPlanner`
- `ThymeleafLayoutGenerationPlanner`

Planner 책임:

- Schema와 Metadata 해석
- ScreenSpecification 해석
- Template Model 생성
- Layer 선택
- Target Path 결정
- Rendering
- 사전 경고 수집

Planner는 파일을 저장하지 않는다.

### 8.2 GenerationPlanExecutor

```java
public interface GenerationPlanExecutor {
    GenerationExecution execute(GenerationPlan plan);
}
```

책임:

- 생성 계획의 파일 저장
- 성공 파일과 실패 파일 수집
- 파일별 예외 격리
- 경로 정책 적용
- 추후 임시 디렉터리 기반 원자적 저장 지원

```java
public record GenerationExecution(
        GenerationPlan plan,
        List<Path> succeeded,
        List<GenerationFailure> failed
) {}
```

### 8.3 GenerationPostProcessor

```java
public interface GenerationPostProcessor {
    boolean supports(GenerationContext context);
    PostProcessResult process(GenerationContext context);
}
```

권장 Processor:

| Processor | 역할 |
|---|---|
| `ThymeleafRuntimePostProcessor` | Thymeleaf 의존성과 ViewResolver 설정 |
| `MyBatisPostProcessor` | Mapper Location과 Scanner 설정 |
| `ControllerScanPostProcessor` | Controller Component Scan 보강 |
| `WarEntryPointPostProcessor` | WAR 기본 진입점 수정 |
| `KrdsCssPostProcessor` | 기능별 KRDS CSS 보강 |
| `ServletContextPostProcessor` | Interceptor와 XML 설정 |
| `MainControllerPostProcessor` | 기본 MainController 수정 |
| `ClasspathAssetPostProcessor` | 로고 등 Classpath Asset 복사 |

공통 Processor는 `supports()`를 통해 다음 조건을 판단한다.

- Feature Type
- JSP 또는 Thymeleaf
- WAR 또는 Boot
- Layout Mode
- eGovFrame 버전

### 8.4 GenerationVerifier

```java
public interface GenerationVerifier {
    VerificationResult verify(GenerationContext context);
}
```

내부 Verifier:

- `CodeDirectoryValidator`
- `CommonContractVerifier`
- `BoardContractVerifier`
- `ThymeleafRenderVerifier`
- `GeneratedProjectBuildVerifier`

기능별 검증은 공통 Verifier에 조건문으로 추가하지 않고 별도 구현으로 조합한다.

### 8.5 GenerationHistoryRecorder

```java
public interface GenerationHistoryRecorder {
    HistoryRecordResult record(GenerationExecution execution);
}
```

생성 이력 저장 실패는 파일 생성 결과와 분리한다. 이력 저장소 장애로 이미 생성된 파일을 생성 실패로 표시하지 않도록 한다.

## 9. 분리된 Application Service

```java
@Service
@RequiredArgsConstructor
public class CrudGenerationApplicationService
        implements GenerateCrudProjectUseCase {

    private final CrudGenerationPlanner planner;
    private final GenerationPlanExecutor executor;
    private final GeneratedProjectPostProcessor postProcessor;
    private final GenerationVerifier verifier;
    private final GenerationHistoryRecorder historyRecorder;

    @Override
    public CrudGenerationResult execute(GenerateCrudCommand command) {
        GenerationPlan plan = planner.plan(command);
        GenerationExecution execution = executor.execute(plan);
        PostProcessResult postProcess =
                postProcessor.process(plan.context());
        VerificationResult verification =
                verifier.verify(plan.context());
        HistoryRecordResult history =
                historyRecorder.record(execution);

        return CrudGenerationResultAssembler.assemble(
                plan,
                execution,
                postProcess,
                verification,
                history);
    }
}
```

Application Service에는 다음 코드가 없어야 한다.

- 파일 경로 문자열 결합
- Layer 반복 렌더링
- XML 문자열 검색·치환
- CSS 파일 직접 수정
- `StringBuilder` 기반 MCP 응답
- 개별 Template 파일명 분기
- `Files.readString` 또는 `Files.writeString`

## 10. ThymeleafLayoutTool 분리안

| 현재 책임 | 신규 컴포넌트 |
|---|---|
| Layout·GNB 파일 계획 | `ThymeleafLayoutGenerationPlanner` |
| Main HTML 생성 | FreeMarker Template 또는 `MainPageRenderer` |
| 로고 복사 | `ClasspathAssetCopier` |
| Servlet XML 패치 | `ServletContextConfigurer` |
| Component Scan 패치 | `ComponentScanConfigurer` |
| MyBatis 설정 | 기존 `MyBatisRuntimeConfigurer` 재사용 |
| Thymeleaf 설정 | 기존 `ThymeleafRuntimeConfigurer` 재사용 |
| 결과 문자열 | `ThymeleafLayoutMcpResponseFormatter` |
| 전체 실행 | `GenerateThymeleafLayoutUseCase` |

목표 Tool:

```java
@Component
@RequiredArgsConstructor
public class ThymeleafLayoutTool {

    private final ThymeleafLayoutMcpFacade facade;

    @Tool(description = ThymeleafToolDescriptions.GENERATE_LAYOUT)
    public String generateThymeleafLayout(
            String outputPath,
            String layoutBasePath,
            Boolean overwriteLayout,
            String packageName,
            String menuTableName,
            String programTableName
    ) {
        return facade.generate(
                new GenerateThymeleafLayoutMcpRequest(
                        outputPath,
                        layoutBasePath,
                        overwriteLayout,
                        packageName,
                        menuTableName,
                        programTableName));
    }
}
```

## 11. 목표 패키지 구조

최종적으로 기능 중심 패키지 구조로 이동한다.

```text
com.krdevops.springai.feature.generation
├── adapter
│   └── in
│       └── mcp
│           ├── CrudGenerationTool.java
│           ├── BoardGenerationTool.java
│           ├── MasterDetailGenerationTool.java
│           ├── JoinQueryTool.java
│           ├── CrudScreenSourceTool.java
│           ├── BoardScreenSourceTool.java
│           ├── MasterDetailScreenSourceTool.java
│           └── ThymeleafLayoutTool.java
├── application
│   ├── port
│   │   └── in
│   │       ├── GenerateCrudProjectUseCase.java
│   │       ├── BuildCrudPromptUseCase.java
│   │       ├── GenerateBoardProjectUseCase.java
│   │       ├── GenerateMasterDetailProjectUseCase.java
│   │       ├── GenerateScreenSourceUseCase.java
│   │       └── GenerateThymeleafLayoutUseCase.java
│   ├── service
│   │   ├── CrudGenerationApplicationService.java
│   │   ├── BoardGenerationApplicationService.java
│   │   ├── MasterDetailGenerationApplicationService.java
│   │   ├── ScreenSourceGenerationService.java
│   │   └── ThymeleafLayoutGenerationService.java
│   └── pipeline
│       ├── GenerationPlanner.java
│       ├── GenerationPlanExecutor.java
│       ├── GenerationPostProcessor.java
│       ├── GenerationVerifier.java
│       └── GenerationHistoryRecorder.java
├── domain
│   ├── GenerationPlan.java
│   ├── GenerationContext.java
│   ├── GeneratedFilePlan.java
│   ├── GeneratedSource.java
│   └── GenerationResult.java
└── infrastructure
    ├── schema
    ├── template
    ├── filesystem
    ├── projectconfig
    └── persistence
```

### 점진적 이동 원칙

1. 현재 패키지에서 신규 인터페이스와 서비스를 먼저 추출한다.
2. 기존 Tool과 Orchestrator가 신규 서비스를 호출하게 한다.
3. 동작 보존 검증 후 Tool 클래스를 분리한다.
4. 마지막 단계에서 기능 중심 패키지로 이동한다.

로직 분리, Tool 분리, 패키지 이동을 하나의 변경으로 처리하지 않는다.

## 12. 구현 단계

### 12.1 단계 0: MCP 계약 고정

현재 MCP 등록은 `McpConfig.allToolCallbacks()`에서 수동 처리한다. Tool 클래스를 분리하면 등록 누락이나 입력 Schema 변경이 발생할 수 있다.

먼저 다음 Snapshot 테스트를 추가한다.

- 전체 MCP Tool 이름
- Tool 총 개수
- Tool별 입력 JSON Schema
- 필수·선택 파라미터
- Tool 설명
- 주요 응답 문자열

완료 기준:

- 현재 전체 Tool 계약 Snapshot 저장
- 이후 Tool 분리 시 Snapshot 변경 없음
- 의도된 신규 Tool만 별도 승인 후 Snapshot 갱신

### 12.2 단계 1: ThymeleafLayoutTool 시험 분리

단일 MCP 메서드이고 기존 테스트가 충분하므로 첫 적용 대상으로 사용한다.

작업:

1. `GenerateThymeleafLayoutCommand` 생성
2. `GenerateThymeleafLayoutUseCase` 생성
3. `ThymeleafLayoutGenerationService` 생성
4. XML 패치 로직을 Configurer로 이동
5. Asset 복사 로직 분리
6. MCP 결과 Formatter 분리
7. Tool을 Facade 위임 형태로 축소

완료 기준:

- Tool 의존성 1개
- Tool 내부 File API 사용 0건
- 기존 Layout 생성 결과와 동일
- 기존 `ThymeleafLayoutToolTest` 시나리오 유지

### 12.3 단계 2: 단일 화면 Source 생성 분리

작업:

1. `GeneratedSource` 도입
2. `GenerateScreenSourceCommand` 도입
3. `ScreenSourceGenerationService` 생성
4. CRUD 화면 생성 로직 이동
5. 게시판 화면 생성 로직 이동
6. Master/Detail 화면 생성 로직 이동
7. 경로 Resolver 이동
8. MCP Response Formatter 이동

완료 기준:

- `CrudPromptBuilderTool`이 Schema·ModelFactory·Renderer를 직접 의존하지 않음
- 화면 Source와 권장 경로가 기존 응답과 동일
- 단일 화면 생성 시 파일 저장이 발생하지 않음

### 12.4 단계 3: Prompt와 자동 생성 분리

작업:

1. `BuildCrudPromptUseCase` 생성
2. `GenerateCrudProjectUseCase` 생성
3. `CrudGenerationDispatchUseCase` 생성
4. `llmProvider` 분기를 Dispatch Use Case로 이동
5. Master/Detail Prompt와 자동 생성 분리
6. 게시판 전체 생성 Use Case 분리

기존 `buildFullCrudPrompt` Tool 이름은 유지한다.

```text
buildFullCrudPrompt
    ↓
CrudGenerationMcpFacade
    ↓
CrudGenerationDispatchUseCase
    ├── auto   → GenerateCrudProjectUseCase
    └── claude → BuildCrudPromptUseCase
```

장기적으로 의미가 명확한 다음 Tool을 추가할 수 있다.

- `generateCrudProject`
- `buildCrudPrompt`

기존 `buildFullCrudPrompt` 제거는 별도 하위 호환 정책으로 처리한다.

### 12.5 단계 4: CRUD Generation Pipeline 추출

CRUD에 먼저 공통 Pipeline을 적용한다.

작업:

1. `GenerationPlan`과 `GeneratedFilePlan` 도입
2. `CrudGenerationPlanner` 생성
3. `GenerationPlanExecutor` 생성
4. 공통 Post Processor 추출
5. `GenerationVerifier` 추출
6. `GenerationHistoryRecorder` 추출
7. `CrudGenerationResultAssembler` 생성
8. 기존 `CrudOrchestrationService`를 하위 호환 Facade로 변경

```java
@Deprecated
public CrudOrchestrationResult orchestrate(...) {
    return generateCrudProjectUseCase.execute(
            commandMapper.map(...));
}
```

완료 기준:

- 기존 CRUD JSP·Thymeleaf 생성 파일 동일
- 기존 응답 문자열 동일
- `CrudOrchestrationService` 100줄 이하
- 기존 Java 호출자와 테스트 호환

### 12.6 단계 5: Board와 Master/Detail 전환

CRUD에서 검증한 Pipeline을 재사용한다.

게시판:

- `BoardGenerationPlanner`
- `BoardCssPostProcessor`
- `BoardContractVerifier`
- `BoardGenerationResultAssembler`

Master/Detail:

- `MasterDetailGenerationPlanner`
- `MasterDetailRelationshipResolver`
- `MasterDetailNamingPolicy`
- `MainControllerPostProcessor`
- `ServletContextPostProcessor`
- `MasterDetailGenerationResultAssembler`

완료 기준:

- 공통 파일 실행·검증·이력 코드 중복 제거
- 기능별 Model과 정책은 독립 유지
- 공통 추상 부모 클래스 없음

### 12.7 단계 6: MCP Tool 클래스 실제 분리

Use Case 분리와 회귀 검증이 끝난 뒤 `CrudPromptBuilderTool`을 실제 Tool 클래스들로 나눈다.

작업:

1. 신규 Tool Adapter 생성
2. 기존 `@Tool` 메서드를 신규 클래스에 이동
3. `McpConfig` 등록 객체 교체
4. 전체 MCP 계약 Snapshot 비교
5. 기존 `CrudPromptBuilderTool` Spring Bean 제거
6. Java 하위 호환이 필요하면 비등록 Deprecated Facade 유지

완료 기준:

- 중복 Tool 이름 없음
- 등록 누락 없음
- MCP 입력 Schema 변경 없음
- Claude Desktop 기존 호출 정상

### 12.8 단계 7: 기능 중심 패키지 이동

마지막으로 생성 기능을 `feature.generation` 아래로 이동한다.

이 단계에서는 로직을 변경하지 않고 패키지와 Import만 변경한다.

## 13. 테스트 전략

| 테스트 계층 | 검증 내용 |
|---|---|
| MCP 계약 Snapshot | Tool 이름과 입력 Schema 보존 |
| Tool Adapter 테스트 | Command 변환과 Facade 호출 |
| Facade 테스트 | 하위 호환 분기와 응답 포맷 |
| Planner 단위 테스트 | 파일을 쓰지 않고 올바른 GenerationPlan 생성 |
| Executor 테스트 | 파일별 성공·실패와 경로 정책 |
| Post Processor 테스트 | Thymeleaf·MyBatis·CSS·XML 보강 |
| Verifier 테스트 | 공통·기능별 감사 |
| Golden File 테스트 | 생성 파일 내용 보존 |
| 통합 테스트 | CRUD·게시판·Master/Detail JSP·Thymeleaf 생성 |
| 생성 프로젝트 빌드 | 생성 결과 컴파일과 렌더링 |
| 전체 회귀 테스트 | 기존 Spring 테스트 전체 통과 |

### 기존 테스트 활용

주요 기존 테스트:

```text
src/test/java/com/krdevops/springai/tools/CrudPromptBuilderToolTest.java
src/test/java/com/krdevops/springai/tools/ThymeleafLayoutToolTest.java
src/test/java/com/krdevops/springai/service/CrudOrchestrationServiceTest.java
src/test/java/com/krdevops/springai/service/BoardOrchestrationServiceTest.java
src/test/java/com/krdevops/springai/service/MasterDetailOrchestrationServiceTest.java
```

기존 테스트는 내부 의존성을 Mock하는 방식이 많으므로 리팩터링 전에 다음 행동 보존 테스트를 보강해야 한다.

- MCP Tool Definition Snapshot
- 결과 파일 트리 Snapshot
- 대표 생성 파일 Golden File
- MCP 응답 구조 Snapshot
- Post Processor 실행 순서
- 부분 실패 시 성공·실패 파일 집계

## 14. 주요 위험과 대응

| 위험 | 대응 |
|---|---|
| Tool 클래스 이동 후 MCP 등록 누락 | 전체 Tool 이름·Schema Snapshot 테스트 |
| 같은 Tool 이름이 두 Bean에 중복 등록 | 기존 Bean 등록 해제 후 신규 Bean 등록 |
| 선택 파라미터가 필수로 변경 | Tool JSON Schema 비교 |
| 기존 응답 문자열 변경 | Response Formatter 추출 후 Snapshot 보존 |
| Overload 제거로 Java 테스트 실패 | Deprecated Compatibility Facade 유지 |
| Post Processor 순서 변경 | 실행 순서 계약 테스트 |
| 파일이 일부만 생성된 상태 변경 | 1차는 기존 부분 성공 정책 보존 |
| 기능별 차이를 공통 Pipeline이 흡수 | Strategy·Processor 조합, 공통 부모 클래스 금지 |
| 패키지 이동과 로직 변경 충돌 | 단계 7에서 패키지만 별도 이동 |
| 생성 이력 실패가 전체 실패로 처리 | History Result를 파일 생성 결과와 분리 |

## 15. 완료 기준

### 15.1 Tool

- 클래스당 120줄 이하
- 의존성 1~2개
- DB·파일·Renderer 직접 접근 없음
- 업무 실행 분기 없음
- MCP 이름과 입력 Schema 변경 없음
- MCP 결과 포맷은 전용 Formatter가 담당

### 15.2 Application Service

- 공개 실행 메서드 `execute(Command)` 중심
- 의존성 6개 이하
- 150줄 내외
- 파일과 XML 직접 조작 없음
- 구조화된 Result 반환
- MCP 전용 문자열 반환 없음

### 15.3 Planner

- 파일 저장 부작용 없음
- 입력 Command에서 전체 파일 계획 생성
- 기능별 Schema와 Template Model 처리
- 경로와 Layer 결정 로직을 단위 테스트 가능

### 15.4 Pipeline

- 실행 전에 전체 파일 계획 확인 가능
- 공통 Post Processor 재사용
- 기능별 감사 확장 가능
- 파일 저장 실패와 이력 실패 구분
- JSP와 Thymeleaf 정책 분리 가능

## 16. 구현 우선순위

| 우선순위 | 작업 |
|---|---|
| P0 | 전체 MCP Tool 계약 Snapshot 테스트 |
| P0 | `ThymeleafLayoutTool` 서비스 추출 |
| P0 | `CrudPromptBuilderTool` 단일 화면 Use Case 추출 |
| P0 | Prompt와 자동 생성 Use Case 분리 |
| P0 | CRUD 공통 Generation Pipeline 추출 |
| P1 | Board Generation Pipeline 전환 |
| P1 | Master/Detail Generation Pipeline 전환 |
| P1 | MCP Tool 클래스 실제 분리 |
| P1 | `CrudPromptBuilderService`, `MasterDetailService` Prompt Section 분리 |
| P2 | 기능 중심 패키지 이동 |
| 제외 | Gradle 멀티 모듈 또는 마이크로서비스 전환 |

## 17. 권장 첫 번째 구현 단위

첫 번째 변경은 다음 범위로 제한하는 것이 안전하다.

```text
1. MCP 전체 Tool Definition Snapshot 테스트 추가
2. GenerateThymeleafLayoutCommand 추가
3. GenerateThymeleafLayoutUseCase 추가
4. ThymeleafLayoutGenerationService 추가
5. ThymeleafLayoutMcpResponseFormatter 추가
6. ThymeleafLayoutTool을 Facade 위임 구조로 축소
7. 기존 Layout 생성 회귀 테스트 실행
```

이 변경으로 MCP 계약을 유지하면서 Tool 내부에서 파일·XML·Runtime 설정 책임을 제거하는 전체 분리 패턴을 먼저 검증할 수 있다.

두 번째 구현 단위는 단일 화면 Source 생성 분리, 세 번째 구현 단위는 CRUD Generation Pipeline 추출로 진행한다.

## 18. 결론

현재 가장 큰 문제는 클래스 크기 자체가 아니라 계층 간 책임 역전이다.

`CrudPromptBuilderTool`과 `ThymeleafLayoutTool`은 MCP Adapter가 Application Service와 Infrastructure 역할까지 수행한다. 세 Generation Orchestrator는 기능별 차이보다 공통 실행 흐름을 더 많이 중복한다.

권장 방향은 다음과 같다.

```text
Tool은 얇은 Adapter로 제한
        ↓
Prompt·전체 생성·단일 화면 Use Case 분리
        ↓
기능별 Generation Planner 도입
        ↓
공통 Executor·Post Processor·Verifier·History 조합
        ↓
동작 보존 후 Tool 클래스와 패키지 분리
```

이 방식은 기존 MCP Tool 계약과 생성 결과를 유지하면서 변경 영향 범위를 줄이고 향후 JSP·Thymeleaf·Figma Generator를 같은 Application Pipeline에 안전하게 연결할 수 있다.
