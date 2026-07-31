# 거대 Orchestrator와 Tool 분리 구현명세서

> 작성일: 2026-07-31  
> 대상 프로젝트: `springai`  
> 기준 문서: [거대 Orchestrator와 Tool 분리 영향 분석](./거대_Orchestrator와_Tool_분리_영향분석.md)  
> 설계 문서: [거대 Orchestrator와 Tool 분리 방안](./거대_Orchestrator와_Tool_분리_방안.md)  
> 상태: 구현 대기  
> 문서 성격: 구현 시 준수해야 하는 규범적 명세

## 1. 목적

이 명세서는 `springai`의 거대 MCP Tool과 Generation Orchestration Service를 점진적으로 분리하기 위한 구현 계약을 정의한다.

분리 대상:

- `CrudPromptBuilderTool`
- `ThymeleafLayoutTool`
- `CrudOrchestrationService`
- `BoardOrchestrationService`
- `MasterDetailOrchestrationService`

목표 구조:

```text
MCP Tool
    ↓
MCP Facade
    ↓
Inbound Use Case
    ↓
Feature Application Service
    ↓
Generation Planner
    ↓
Generation Renderer
    ↓
Generation Executor
    ↓
Generation Stage Processor
    ↓
Generation Verifier
    ↓
Generation History Recorder
```

## 2. 범위

### 2.1 포함 범위

- MCP Tool 계약 Snapshot 도입
- `ThymeleafLayoutTool`의 비즈니스 로직 추출
- 단일 화면 Source 생성 Use Case 추출
- Prompt 생성과 자동 프로젝트 생성 Use Case 분리
- 공통 Generation Pipeline 도입
- CRUD·게시판·Master/Detail Pipeline 전환
- `CrudPromptBuilderTool`을 기능별 Tool Adapter로 분리
- 기존 Orchestrator Compatibility Facade 제공
- 기능 중심 패키지로 점진적 이동
- 관련 테스트와 문서 갱신

### 2.2 제외 범위

- Gradle 멀티 모듈 전환
- 마이크로서비스 분리
- MCP Transport 변경
- 기존 Tool 이름 제거
- 기존 MCP 응답을 JSON으로 전환
- 생성 파일 경로 정책 변경
- Template 내용 개선
- 생성 파일 전체 Rollback
- 원자적 프로젝트 디렉터리 교체
- MySQL·Redis Schema 변경
- Figma·RAG 기능 구조 변경

## 3. 구현 원칙

| ID | 원칙 |
|---|---|
| `ORT-PRN-001` | Tool은 MCP 입력, Command 변환, Facade 호출, 응답 반환만 수행한다. |
| `ORT-PRN-002` | Application Service는 구조화된 Command와 Result를 사용한다. |
| `ORT-PRN-003` | 파일·XML·CSS 조작은 Infrastructure 또는 Processor에서 수행한다. |
| `ORT-PRN-004` | 상속 기반 공통 Orchestrator를 만들지 않고 작은 컴포넌트를 조합한다. |
| `ORT-PRN-005` | 구조 분리 단계에서는 기존 동작을 변경하지 않는다. |
| `ORT-PRN-006` | Tool 분리와 Package 이동을 같은 변경 단위에서 수행하지 않는다. |
| `ORT-PRN-007` | 기존 MCP Tool 이름과 입력 Schema는 유지한다. |
| `ORT-PRN-008` | 기존 파일 수, 경로, 오류 문자열과 부분 실패 정책을 보존한다. |
| `ORT-PRN-009` | Design Reference와 ScreenSpecification 전달을 명시적 Command 필드로 보존한다. |
| `ORT-PRN-010` | 기존 공통 Service는 초기 단계에서 이동하거나 이름을 변경하지 않는다. |

## 4. 기준 상태

### 4.1 현재 규모

| 대상 | 줄 수 | 의존성 |
|---|---:|---:|
| `CrudPromptBuilderTool` | 1,015 | 16 |
| `ThymeleafLayoutTool` | 430 | 5 |
| `CrudOrchestrationService` | 324 | 15 |
| `BoardOrchestrationService` | 280 | 17 |
| `MasterDetailOrchestrationService` | 300 | 10 |

### 4.2 MCP 기준

```text
현재 Tool 객체 수: 25
현재 @Tool 메서드 수: 79
CrudPromptBuilderTool의 @Tool 메서드 수: 16
```

Tool 분리 완료 후 목표:

```text
Tool 객체 수: 31
@Tool 메서드 수: 79
기존 Tool 이름: 변경 없음
기존 입력 Schema: 변경 없음
```

### 4.3 기존 결과 타입

다음 타입은 Compatibility 기간 동안 유지한다.

- `CrudOrchestrationResult`
- `BoardOrchestrationResult`
- `MasterDetailOrchestrationResult`

신규 내부 Result를 외부에 직접 노출하지 않는다.

## 5. 용어

| 용어 | 정의 |
|---|---|
| Tool Adapter | Spring AI `@Tool`을 가진 MCP 진입 클래스 |
| MCP Facade | MCP 파라미터를 Command로 변환하고 결과를 문자열로 만드는 계층 |
| Use Case | 외부 Adapter가 호출하는 Application 경계 |
| Planner | Schema·Metadata·Design Context를 해석해 생성 계획을 만드는 컴포넌트 |
| Blueprint | 아직 렌더링하지 않은 Layer와 Target Path 계획 |
| Rendered Plan | 렌더링된 Source를 가진 실행 직전 계획 |
| Executor | 렌더링된 파일을 저장하고 성공·실패를 수집하는 컴포넌트 |
| Stage Processor | 파일 쓰기 전·후 또는 검증 전에 프로젝트를 보강하는 컴포넌트 |
| Verifier | 정적 검증, 공통 계약 감사, 기능별 감사를 수행하는 컴포넌트 |
| Compatibility Facade | 기존 Java 호출자를 임시로 유지하는 위임 클래스 |

## 6. 목표 패키지

### 6.1 1차 구현 패키지

Package 이동 영향을 줄이기 위해 초기 구현은 현재 `service`, `tools` 아래에서 수행한다.

```text
com.krdevops.springai
├── tools
│   ├── CrudPromptBuilderTool.java
│   ├── ThymeleafLayoutTool.java
│   └── generation
│       ├── CrudGenerationTool.java
│       ├── BoardGenerationTool.java
│       ├── MasterDetailGenerationTool.java
│       ├── JoinQueryTool.java
│       ├── CrudScreenSourceTool.java
│       ├── BoardScreenSourceTool.java
│       └── MasterDetailScreenSourceTool.java
└── service
    └── generation
        ├── api
        ├── model
        ├── pipeline
        ├── crud
        ├── board
        ├── masterdetail
        ├── layout
        └── mcp
```

### 6.2 최종 패키지

모든 동작 보존 검증 후 별도 변경으로 이동한다.

```text
com.krdevops.springai.feature.generation
├── adapter.in.mcp
├── application.port.in
├── application.service
├── application.pipeline
├── domain
└── infrastructure
```

## 7. MCP Tool 계약

### 7.1 Tool Definition Snapshot

`ORT-MCP-001`: 전체 MCP Tool Definition을 Snapshot으로 저장해야 한다.

Snapshot 항목:

- Tool 이름
- 설명
- Input JSON Schema
- 필수 파라미터 목록
- 선택 파라미터 목록

권장 Fixture:

```text
src/test/resources/mcp/tool-definitions-baseline.json
```

정렬 규칙:

- Tool 이름 오름차순
- JSON Object Key 오름차순
- 배열 순서는 실제 계약 순서를 유지

### 7.2 Tool 외부 시그니처

`ORT-MCP-002`: 기존 `@Tool` 메서드의 파라미터를 Request DTO 하나로 바꾸면 안 된다.

`ORT-MCP-003`: 파라미터명, Java 타입, 순서와 `@Nullable`을 유지해야 한다.

`ORT-MCP-004`: 기존 Tool 설명을 별도 상수로 이동할 수 있지만 문자열 값은 동일해야 한다.

`ORT-MCP-005`: Tool 클래스 이동 시 기존 Bean과 신규 Bean을 동시에 등록하면 안 된다.

`ORT-MCP-006`: `McpConfig.allToolCallbacks()`에서 기존 Tool 제거와 신규 Tool 추가는 같은 변경으로 수행해야 한다.

### 7.3 Tool 분리 매핑

| 기존 MCP 메서드 | 신규 Tool Adapter | 신규 Facade | Use Case |
|---|---|---|---|
| `buildFullCrudPrompt` | `CrudGenerationTool` | `CrudGenerationMcpFacade` | `DispatchCrudGenerationUseCase` |
| `buildBoardFeature` | `BoardGenerationTool` | `BoardGenerationMcpFacade` | `GenerateBoardProjectUseCase` |
| `buildMasterDetailPrompt` | `MasterDetailGenerationTool` | `MasterDetailGenerationMcpFacade` | `DispatchMasterDetailGenerationUseCase` |
| `buildJoinSelectPrompt` | `JoinQueryTool` | `JoinQueryMcpFacade` | `BuildJoinQueryPromptUseCase` |
| `generateCrudList/Detail/Regist/Updt` | `CrudScreenSourceTool` | `ScreenSourceMcpFacade` | `GenerateScreenSourceUseCase` |
| `generateBoardList/Detail/Regist/Updt` | `BoardScreenSourceTool` | `ScreenSourceMcpFacade` | `GenerateScreenSourceUseCase` |
| `generateMasterList/Detail/Regist/Updt` | `MasterDetailScreenSourceTool` | `ScreenSourceMcpFacade` | `GenerateScreenSourceUseCase` |
| `generateThymeleafLayout` | `ThymeleafLayoutTool` | `ThymeleafLayoutMcpFacade` | `GenerateThymeleafLayoutUseCase` |

### 7.4 MCP 응답

`ORT-MCP-007`: 다음 Formatter 결과를 Characterization Snapshot으로 보존해야 한다.

- CRUD 전체 생성 결과
- 게시판 전체 생성 결과
- Master/Detail 전체 생성 결과
- 단일 화면 Source 결과
- Thymeleaf Layout 결과
- Prompt 결과

Tool과 Use Case는 `StringBuilder`로 MCP 응답을 만들지 않는다. MCP 전용 문자열은 `service.generation.mcp`의 Formatter가 담당한다.

## 8. Command 명세

### 8.1 공통 Value Object

```java
public record ProgramMetadataOverrides(
        String programFileName,
        String programUrl,
        String programKoreanName,
        String programStorePath
) {}
```

```java
public record DesignContextReference(
        String designReferenceId,
        String screenSpecificationId
) {}
```

```java
public record LayoutOptions(
        String layoutMode,
        String layoutView,
        String breadcrumbView
) {}
```

### 8.2 CRUD Command

```java
public record CrudGenerationCommand(
        String database,
        String tableName,
        String domain,
        String packageName,
        Path outputPath,
        String llmProvider,
        String egovVersion,
        String viewType,
        LayoutOptions layout,
        ProgramMetadataOverrides program,
        DesignContextReference designContext
) {}
```

규칙:

- `llmProvider` 기본값은 `auto`
- `egovVersion` 기본값은 `5.0`
- `viewType` 기본값은 `jsp`
- 기존 명시값, DB 자동조회, Fallback 우선순위를 유지
- `screenSpecificationId`가 `designReferenceId`보다 우선

### 8.3 게시판 Command

```java
public record BoardGenerationCommand(
        String database,
        String domain,
        String packageName,
        Path outputPath,
        String mainTable,
        String masterTable,
        String useTable,
        String fileTable,
        String fileDetailTable,
        String egovVersion,
        String viewType,
        LayoutOptions layout,
        ProgramMetadataOverrides program,
        String defaultBbsId,
        DesignContextReference designContext
) {}
```

규칙:

- 기존 `BoardTableSetResolver` 기본 테이블 정책 유지
- `defaultBbsId` 전달 유지
- 게시판 Metadata 충돌 시 생성 차단

### 8.4 Master/Detail Command

```java
public record MasterDetailGenerationCommand(
        String database,
        String masterTable,
        String detailTable,
        String domain,
        String packageName,
        Path outputPath,
        String llmProvider,
        String egovVersion,
        String viewType,
        LayoutOptions layout,
        DesignContextReference designContext
) {}
```

### 8.5 단일 화면 Command

```java
public record GenerateScreenSourceCommand(
        FeatureType featureType,
        ScreenType screenType,
        String database,
        String primaryTable,
        String secondaryTable,
        String domain,
        String packageName,
        Path outputPath,
        String egovVersion,
        String viewType,
        BoardTableOptions boardTables,
        ProgramMetadataOverrides program,
        String defaultBbsId
) {}
```

### 8.6 Layout Command

```java
public record GenerateThymeleafLayoutCommand(
        Path outputPath,
        String layoutBasePath,
        boolean overwrite,
        String packageName,
        String menuTableName,
        String programTableName
) {}
```

기본값:

- `layoutBasePath`: `layout`
- `overwrite`: `true`
- `packageName`: 기존 기본값 유지
- `menuTableName`: `LETTNMENUINFO`
- `programTableName`: `LETTNPROGRMLIST`

## 9. Use Case 명세

### 9.1 CRUD

```java
public interface DispatchCrudGenerationUseCase {
    CrudToolResult execute(CrudGenerationCommand command);
}
```

분기:

```text
llmProvider=auto
    → GenerateCrudProjectUseCase

llmProvider=claude 또는 기타 기존 Prompt Provider
    → BuildCrudPromptUseCase
```

```java
public interface GenerateCrudProjectUseCase {
    CrudOrchestrationResult execute(CrudGenerationCommand command);
}
```

```java
public interface BuildCrudPromptUseCase {
    PromptGenerationResult execute(CrudGenerationCommand command);
}
```

### 9.2 게시판

```java
public interface GenerateBoardProjectUseCase {
    BoardOrchestrationResult execute(BoardGenerationCommand command);
}
```

### 9.3 Master/Detail

```java
public interface DispatchMasterDetailGenerationUseCase {
    MasterDetailToolResult execute(MasterDetailGenerationCommand command);
}
```

```java
public interface GenerateMasterDetailProjectUseCase {
    MasterDetailOrchestrationResult execute(
            MasterDetailGenerationCommand command);
}
```

```java
public interface BuildMasterDetailPromptUseCase {
    PromptGenerationResult execute(
            MasterDetailGenerationCommand command);
}
```

### 9.4 단일 화면

```java
public interface GenerateScreenSourceUseCase {
    GeneratedSource execute(GenerateScreenSourceCommand command);
}
```

### 9.5 Layout

```java
public interface GenerateThymeleafLayoutUseCase {
    LayoutGenerationResult execute(
            GenerateThymeleafLayoutCommand command);
}
```

## 10. Generation Pipeline 명세

### 10.1 단계

```java
public enum GenerationStage {
    PREFLIGHT,
    PLAN,
    RENDER,
    PRE_WRITE,
    WRITE,
    POST_WRITE,
    PRE_VERIFY,
    VERIFY,
    HISTORY
}
```

**주의**: 실제 CRUD Pipeline 실행 순서는 `PRE_WRITE`가 `RENDER`보다 먼저이며, 이는 enum 선언
순서와 반대다. `GenerationProcessorRunner`는 stage를 호출자가 넘기는 필터 인자로만 사용할 뿐
enum ordinal을 읽지 않으므로, 실제 stage 실행 순서는 각 처리 단계 호출자
(`CrudGenerationApplicationService` 등)가 명시한 순서로 결정된다(§10.7의
`GenerationVerifierRunner`만 ordinal 기반 정렬을 사용한다). WP-0 `CrudOrchestrationProcessorOrderTest`가
실측한 기존 동작(`ORT-PRN-005`)을 보존하기 위한 결과다.

### 10.2 Preflight

```java
public interface GenerationPreflight<C> {
    PreflightResult validate(C command);
}
```

검증 항목:

- 필수 입력
- Package Name 규칙
- Schema 존재
- Metadata 충돌
- Route 충돌
- Layout 존재
- Output Path 정책

Preflight 실패 시 파일을 쓰지 않는다.

### 10.3 Blueprint

```java
public record GenerationBlueprint(
        GenerationContext context,
        List<FileBlueprint> files,
        List<ProcessorStep> processors,
        List<GenerationWarning> warnings
) {}
```

```java
public record FileBlueprint(
        String layerKey,
        String displayName,
        Path targetPath,
        RenderRequest renderRequest
) {}
```

Blueprint는 Source 문자열을 보유하지 않는다. `displayName`은 결과 성공/실패 목록에 노출되는
이름이며 파일 시스템 경로의 마지막 조각과 항상 같지는 않다 — 예: Thymeleaf layout 레이어는
경로가 `.../templates/layout/default.html`이지만 표시 이름은 `layout/default.html`이다.

### 10.4 Renderer

```java
public interface GenerationRenderer {
    RenderedGenerationPlan render(GenerationBlueprint blueprint);
}
```

```java
public record RenderedFilePlan(
        String layerKey,
        String displayName,
        Path targetPath,
        String source,
        GenerationFailure renderFailure
) {}
```

`displayName`은 §10.3 `FileBlueprint.displayName`을 그대로 이어받는다. 렌더링에 실패한
레이어도 목록에서 빠지지 않고 `renderFailure`가 채워진 채로 남는다 — Executor가 레이어 순서
그대로 순회하면서 실패를 누적해야 기존의 성공/실패 목록 순서가 보존되기 때문이다.

```java
public record RenderedGenerationPlan(
        GenerationContext context,
        List<RenderedFilePlan> files,
        List<ProcessorStep> processors,
        List<GenerationWarning> warnings
) {}
```

Rendering 실패는 기존 기능별 정책에 따라 파일 실패로 수집하거나 실행 전 실패로 처리한다. 1차 구현은 현재 Layer별 예외 격리 방식을 보존한다.

### 10.5 Executor

```java
public interface GenerationExecutor {
    GenerationExecution execute(RenderedGenerationPlan plan);
}
```

```java
public record GenerationExecution(
        RenderedGenerationPlan plan,
        List<RenderedFilePlan> succeededFiles,
        List<GenerationFailure> failedFiles
) {
    public List<String> succeededNames();
}
```

`succeededFiles`는 `Path` 목록이 아니라 `RenderedFilePlan` 목록이다 — `displayName`을 포함한
전체 정보를 결과 조립 단계까지 그대로 전달하기 위함이다. `succeededNames()`는 각 항목의
`displayName`만 뽑아 반환하는 편의 메서드다.

규칙:

- 기존 `CodeService.saveGeneratedCode()`를 사용
- 파일 하나 실패 후 다음 파일 계속
- 파일 저장 결과 문자열을 기존 Result 형식으로 변환
- 생성 파일을 자동 삭제하지 않음
- Binary Asset은 Executor가 아니라 Asset Processor가 처리

### 10.6 Processor

```java
public interface GenerationStageProcessor {
    String id();
    GenerationStage stage();
    boolean supports(GenerationContext context);
    ProcessorResult process(GenerationProcessingContext context);
}
```

```java
public enum FailurePolicy {
    STOP,
    CONTINUE,
    SKIP_DEPENDENTS
}
```

```java
public record ProcessorStep(
        String processorId,
        GenerationStage stage,
        int order,
        FailurePolicy failurePolicy,
        List<String> dependsOn
) {}
```

Processor 실행 순서는 `stage`, `order`, `processorId` 순으로 결정한다. Spring Bean 주입 순서에 의존하지 않는다.
단, 이 정렬은 `GenerationProcessorRunner` 호출 시점에 특정 `stage` 값이 이미 필터로 주어졌을 때
그 안에서의 순서만 정한다 — `GenerationProcessorRunner` 자신은 `GenerationStage` enum의 ordinal을
읽지 않으며, 어떤 stage들을 어떤 순서로 호출할지는 전적으로 호출자가 결정한다(§10.1 참고).
이 점에서 아래 §10.7 `GenerationVerifierRunner`와 다르다.

### 10.7 Verifier

```java
public interface GenerationVerifier {
    String id();
    GenerationStage stage();
    int order();
    boolean supports(GenerationContext context);
    VerificationResult verify(GenerationProcessingContext context);
}
```

Verifier 구성:

- `CodeDirectoryVerifier` (`PRE_VERIFY`)
- `CommonGeneratedContractVerifier` (`VERIFY`)
- `BoardGeneratedContractVerifier`
- 선택적 `ThymeleafRenderVerifier`
- 선택적 `GeneratedProjectBuildVerifier`

**`GenerationVerifierRunner`**: 등록된 `GenerationVerifier`를 `stage().ordinal()` → `order()` →
`id()` 순으로 정렬해 실행하고 요약 조각을 이어붙인다. `GenerationStageProcessor`/
`GenerationProcessorRunner`(§10.6)와 달리 이 Runner는 `GenerationStage` enum의 ordinal을 1차
정렬 키로 실제로 사용한다 — 그래서 "Directory 검증이 항상 Common Contract 감사보다 먼저"라는
순서가 배선이 아니라 stage 배정(`CodeDirectoryVerifier`=`PRE_VERIFY`,
`CommonGeneratedContractVerifier`=`VERIFY`) 자체로 보장된다(§11.1~§11.3 표 참고).

### 10.8 History Recorder

```java
public interface GenerationHistoryRecorder {
    HistoryRecordResult record(GenerationProcessingContext context);
}
```

규칙:

- 기존 `GenerationHistoryService` 위임
- History 실패는 생성 파일 성공을 취소하지 않음
- 기존 History Summary 문자열 유지

## 11. 기능별 Pipeline 정책

### 11.1 CRUD

| 순서 | Stage | Processor | 실패 정책 |
|---:|---|---|---|
| 100 | `PRE_WRITE` | `CrudTableDensityCssProcessor` | `STOP` |
| 110 | `PRE_WRITE` | `CrudFormColumnCssProcessor` | `STOP` |
| 100 | `POST_WRITE` | `CrudEntryPointProcessor` | `CONTINUE` |
| 200 | `POST_WRITE` | `ThymeleafRuntimeProcessor` | `CONTINUE` |
| 210 | `POST_WRITE` | `ControllerScanProcessor` | `CONTINUE` |
| 300 | `POST_WRITE` | `MyBatisRuntimeProcessor` | `CONTINUE` |
| 100 | `PRE_VERIFY` | `CodeDirectoryVerifier`* | - |
| 100 | `VERIFY` | `CommonGeneratedContractVerifier`* | - |

JSP에서는 Thymeleaf 관련 Processor가 `supports()`에서 제외된다.

\* `PRE_VERIFY`/`VERIFY` 행은 `GenerationStageProcessor`가 아니라 `GenerationVerifier`(§10.7) 등록이다.
`GenerationVerifier`에는 `실패 정책`(`FailurePolicy`) 개념이 없다 — `GenerationVerifierRunner`는
Verifier를 항상 끝까지 순회하며 실패를 누적할 뿐 중단하지 않는다. 실행 순서는 `stage().ordinal()`이
1차 키이므로 `CodeDirectoryVerifier`(`PRE_VERIFY`)가 `CommonGeneratedContractVerifier`(`VERIFY`)보다
항상 먼저 실행된다.

### 11.2 게시판

| 순서 | Stage | Processor | 실패 정책 |
|---:|---|---|---|
| 100 | `POST_WRITE` | `ThymeleafRuntimeProcessor` | `CONTINUE` |
| 110 | `POST_WRITE` | `ControllerScanProcessor` | `CONTINUE` |
| 120 | `POST_WRITE` | `BoardCrudCssProcessor` | `CONTINUE` |
| 200 | `POST_WRITE` | `MyBatisRuntimeProcessor` | `CONTINUE` |
| 300 | `POST_WRITE` | `BoardEntryPointProcessor` | `CONTINUE` |
| 100 | `PRE_VERIFY` | `CodeDirectoryVerifier`* | - |
| 100 | `VERIFY` | `CommonGeneratedContractVerifier`* | - |
| 110 | `PRE_VERIFY` | `BoardGeneratedContractProcessor` | `CONTINUE` |

\* `CodeDirectoryVerifier`/`CommonGeneratedContractVerifier`는 `GenerationVerifier`(§10.7) 등록이며
`실패 정책` 개념이 없다 — 자세한 내용은 §11.1의 각주 참고. `BoardGeneratedContractProcessor`는
Board Pipeline이 공통 Pipeline으로 전환되지 않은 현재 시점 기준으로 아직 미구현이다(Board/Master-Detail
Pipeline 전환은 이 문서 범위 밖의 후속 WP).

### 11.3 Master/Detail

| 순서 | Stage | Processor | 실패 정책 |
|---:|---|---|---|
| 100 | `POST_WRITE` | `ThymeleafRuntimeProcessor` | `CONTINUE` |
| 200 | `POST_WRITE` | `MyBatisRuntimeProcessor` | `CONTINUE` |
| 300 | `POST_WRITE` | `MainControllerProcessor` | `CONTINUE` |
| 400 | `POST_WRITE` | `ServletContextScanProcessor` | `CONTINUE` |
| 100 | `PRE_VERIFY` | `CodeDirectoryVerifier`* | - |
| 100 | `VERIFY` | `CommonGeneratedContractVerifier`* | - |

\* `GenerationVerifier`(§10.7) 등록이며 `실패 정책` 개념이 없다 — 자세한 내용은 §11.1의 각주 참고.

### 11.4 Thymeleaf Layout

| 순서 | Stage | Processor | 실패 정책 | 종속성 |
|---:|---|---|---|---|
| 100 | `POST_WRITE` | `LayoutValidationProcessor` | `CONTINUE` | 없음 |
| 200 | `POST_WRITE` | `ServletContextInterceptorProcessor` | `SKIP_DEPENDENTS` | 없음 |
| 300 | `POST_WRITE` | `LayoutMyBatisProcessor` | `CONTINUE` | 없음 |
| 400 | `POST_WRITE` | `LayoutThymeleafRuntimeProcessor` | `CONTINUE` | `ServletContextInterceptorProcessor` |

Servlet Context 패치 실패 시 `LayoutThymeleafRuntimeProcessor`는 실행하지 않는다.

## 12. Feature Planner 명세

### 12.1 CrudGenerationPlanner

의존성:

- `CrudSchemaQueryService`
- `CrudProgramMetadataService`
- `GenerationDesignContextService`
- `CrudModelFactory`
- `ThymeleafLayoutValidator`
- `BoardRouteCollisionDetector`

책임:

- Schema 조회
- Package 검증
- Metadata 해석
- Design Context 해석
- Template Model 생성
- Route·Layout 검증
- Layer와 Target Path 계획
- 기능별 Processor 계획

금지:

- 파일 저장
- 이력 저장
- MCP 문자열 생성

### 12.2 BoardGenerationPlanner

의존성:

- `BoardTableSetResolver`
- `BoardSchemaService`
- `BoardProgramMetadataService`
- `GenerationDesignContextService`
- `BoardModelFactory`
- `BoardRouteCollisionDetector`
- `ThymeleafLayoutValidator`

### 12.3 MasterDetailGenerationPlanner

의존성:

- `CrudSchemaQueryService`
- `CrudModelFactory`
- `MasterDetailRelationshipResolver`
- `MasterDetailNamingPolicy`
- `ThymeleafLayoutValidator`

### 12.4 ThymeleafLayoutGenerationPlanner

책임:

- Layout 5개 계획
- GNB Java·Mapper 4개 계획
- Main HTML 계획
- Logo Asset Processor 계획
- Servlet·MyBatis·Runtime Processor 계획

## 13. 단일 화면 Source 명세

### 13.1 결과

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

### 13.2 Strategy

```java
public interface ScreenSourceGenerator {
    boolean supports(FeatureType featureType);
    GeneratedSource generate(GenerateScreenSourceCommand command);
}
```

구현:

- `CrudScreenSourceGenerator`
- `BoardScreenSourceGenerator`
- `MasterDetailScreenSourceGenerator`

규칙:

- 파일을 저장하지 않음
- 기존 Layer Key 유지
- 기존 권장 경로 유지
- 게시판 기본 테이블 Resolver 재사용
- Master/Detail FK 추론 정책 유지

## 14. Thymeleaf Layout 세부 컴포넌트

| 컴포넌트 | 책임 |
|---|---|
| `ThymeleafLayoutGenerationService` | Layout Use Case 조율 |
| `ThymeleafLayoutGenerationPlanner` | Layout·GNB·Main 파일 계획 |
| `MainPageRenderer` | Main Thymeleaf Source 생성 |
| `ClasspathAssetCopier` | 로고 등 Binary 복사 |
| `ServletContextConfigurer` | Interceptor XML 패치 |
| `ComponentScanConfigurer` | Component Scan 범위 보강 |
| `ThymeleafLayoutResultFormatter` | 기존 MCP 문자열 생성 |

XML 수정 규칙:

- 닫는 `</beans>`가 정확히 1개일 때만 수정
- 이미 등록된 Interceptor는 중복 생성하지 않음
- 더 넓은 Component Scan은 유지
- 잘못된 XML은 수정하지 않음
- 실패 메시지와 기존 파일 내용을 보존

Asset 규칙:

- `overwrite=false`이면 기존 Logo 보존
- Binary는 `RenderedFilePlan.source`에 포함하지 않음

## 15. Compatibility 명세

### 15.1 기존 Orchestrator

기존 클래스는 신규 Use Case를 호출하는 Compatibility Facade로 축소할 수 있다.

```java
@Deprecated
@Service
@RequiredArgsConstructor
public class CrudOrchestrationService {

    private final GenerateCrudProjectUseCase useCase;
    private final LegacyCrudCommandMapper mapper;

    public CrudOrchestrationResult orchestrate(...) {
        return useCase.execute(mapper.map(...));
    }
}
```

### 15.2 유지 기간

```text
리팩터링 릴리스 N
    Compatibility Facade 유지

릴리스 N+1
    내부 직접 호출자 제거 확인

릴리스 N+2
    Compatibility Facade 삭제 가능
```

### 15.3 Tool 이름

Compatibility Facade 제거 여부와 관계없이 기존 MCP Tool 이름은 유지한다.

## 16. 오류 처리

### 16.1 구조화 오류

```java
public record GenerationFailure(
        String code,
        GenerationStage stage,
        String target,
        String message,
        boolean retryable
) {}
```

내부에서는 구조화 오류를 사용하되 MCP Formatter는 기존 문자열로 변환한다.

### 16.2 필수 오류 코드

| 코드 | 의미 |
|---|---|
| `SCHEMA_NOT_FOUND` | 대상 Schema 없음 |
| `PACKAGE_INVALID` | Package 규칙 위반 |
| `METADATA_AMBIGUOUS` | Metadata 자동 선택 불가 |
| `ROUTE_COLLISION` | Controller URL 충돌 |
| `LAYOUT_MISSING` | Reuse Layout 없음 |
| `RENDER_FAILED` | Template Rendering 실패 |
| `WRITE_FAILED` | 파일 저장 실패 |
| `PROCESSOR_FAILED` | 프로젝트 후처리 실패 |
| `VERIFY_FAILED` | 정적 검증 실패 |
| `CONTRACT_AUDIT_FAILED` | 생성 계약 감사 실패 |
| `HISTORY_FAILED` | 이력 저장 실패 |

오류 코드 도입으로 기존 MCP 문자열을 변경하면 안 된다.

## 17. 성능과 메모리

### 17.1 Plan 크기

1차 구현은 기존 생성 규모를 수용하되 다음 값을 기록한다.

- 파일 수
- 파일별 문자 수
- 전체 Source 문자 수
- Rendering 시간
- 파일 저장 시간
- Processor 시간
- 검증 시간

새로운 크기 제한은 구조 리팩터링과 동시에 도입하지 않는다.

### 17.2 Binary

이미지와 기타 Binary는 Source 문자열 Plan에 포함하지 않는다.

### 17.3 동시성

- 현재 동기 실행 정책 유지
- 동일 Output Path 동시 생성에 대한 신규 Lock은 도입하지 않음
- 동시성 개선은 별도 설계로 분리

## 18. 보안

- 기존 `CodeService` 경로 검증을 우회하지 않는다.
- Planner가 계산한 Path는 Executor에서 다시 검증한다.
- Tool 분리로 `/mcp/**` 인증 정책을 변경하지 않는다.
- Tool 설명 또는 오류 응답에 Secret을 추가하지 않는다.
- Output Path와 생성 파일 목록의 기존 노출 수준을 유지한다.

## 19. 테스트 명세

### 19.1 MCP 계약 테스트

신규 테스트:

```text
src/test/java/com/krdevops/springai/config/McpToolDefinitionSnapshotTest.java
```

검증:

- `@Tool` 메서드 이름 79개
- Input JSON Schema
- Tool 설명
- 필수·선택 파라미터
- 중복 Tool 이름 없음

### 19.2 Tool Adapter 테스트

검증:

- Command 변환
- Facade 1회 호출
- Tool 내부에서 DB·Renderer·File Service를 사용하지 않음

### 19.3 Planner 테스트

검증:

- 올바른 Layer 목록
- Target Path
- Processor 목록과 순서
- Schema·Metadata·Design Context 전달
- 사전 실패 시 Blueprint 미생성

### 19.4 Renderer 테스트

검증:

- 기존 Template Renderer와 동일한 Source
- Layer별 Rendering 실패 격리

### 19.5 Executor 테스트

검증:

- 파일 저장 성공·실패 수집
- 한 파일 실패 후 계속 실행
- Path 검증 재적용

### 19.6 Processor 테스트

검증:

- Stage·Order
- `STOP`
- `CONTINUE`
- `SKIP_DEPENDENTS`
- 기능별 `supports()`

### 19.7 Golden File

필수 Fixture:

- CRUD JSP
- CRUD Thymeleaf Reuse
- CRUD Thymeleaf Create
- 게시판 JSP
- 게시판 Thymeleaf
- Master/Detail JSP
- Master/Detail Thymeleaf
- Layout·GNB·Main

### 19.8 전체 회귀

```bash
./gradlew test
./gradlew bootJar
```

필요하면 기존 생성 프로젝트 검증 명령을 별도로 실행한다.

## 20. 파일 변경 명세

### 20.1 신규 공통 파일

```text
service/generation/api/GenerateCrudProjectUseCase.java
service/generation/api/BuildCrudPromptUseCase.java
service/generation/api/GenerateBoardProjectUseCase.java
service/generation/api/GenerateMasterDetailProjectUseCase.java
service/generation/api/BuildMasterDetailPromptUseCase.java
service/generation/api/GenerateScreenSourceUseCase.java
service/generation/api/GenerateThymeleafLayoutUseCase.java

service/generation/model/GenerationStage.java
service/generation/model/FailurePolicy.java
service/generation/model/GenerationContext.java
service/generation/model/GenerationBlueprint.java
service/generation/model/FileBlueprint.java
service/generation/model/RenderedGenerationPlan.java
service/generation/model/RenderedFilePlan.java
service/generation/model/GenerationExecution.java
service/generation/model/GenerationFailure.java
service/generation/model/ProcessorStep.java
service/generation/model/GeneratedSource.java

service/generation/pipeline/GenerationPreflight.java
service/generation/pipeline/GenerationRenderer.java
service/generation/pipeline/GenerationExecutor.java
service/generation/pipeline/GenerationStageProcessor.java
service/generation/pipeline/GenerationVerifier.java
service/generation/pipeline/GenerationHistoryRecorder.java
```

### 20.2 신규 기능 파일

```text
service/generation/crud/CrudGenerationApplicationService.java
service/generation/crud/CrudGenerationPlanner.java
service/generation/crud/CrudGenerationResultAssembler.java
service/generation/crud/CrudGenerationCommand.java

service/generation/board/BoardGenerationApplicationService.java
service/generation/board/BoardGenerationPlanner.java
service/generation/board/BoardGenerationResultAssembler.java
service/generation/board/BoardGenerationCommand.java

service/generation/masterdetail/MasterDetailGenerationApplicationService.java
service/generation/masterdetail/MasterDetailGenerationPlanner.java
service/generation/masterdetail/MasterDetailRelationshipResolver.java
service/generation/masterdetail/MasterDetailNamingPolicy.java
service/generation/masterdetail/MasterDetailGenerationResultAssembler.java

service/generation/layout/ThymeleafLayoutGenerationService.java
service/generation/layout/ThymeleafLayoutGenerationPlanner.java
service/generation/layout/ServletContextConfigurer.java
service/generation/layout/ComponentScanConfigurer.java
service/generation/layout/ClasspathAssetCopier.java

service/generation/mcp/CrudGenerationMcpFacade.java
service/generation/mcp/BoardGenerationMcpFacade.java
service/generation/mcp/MasterDetailGenerationMcpFacade.java
service/generation/mcp/ScreenSourceMcpFacade.java
service/generation/mcp/ThymeleafLayoutMcpFacade.java
```

실제 구현 시 과도한 파일 분리를 피하기 위해 관련 Value Object는 응집도에 따라 하나의 파일로 조정할 수 있다. 단, 명세된 책임 경계는 유지해야 한다.

### 20.3 수정 파일

```text
config/McpConfig.java
tools/CrudPromptBuilderTool.java
tools/ThymeleafLayoutTool.java
service/CrudOrchestrationService.java
service/BoardOrchestrationService.java
service/MasterDetailOrchestrationService.java
```

## 21. 완료 기준

### 21.1 Tool

- Tool 클래스당 120줄 이하
- Tool 의존성 1~2개
- Tool 내부 DB·파일·Renderer 접근 없음
- Tool 내부 업무 분기 없음
- 기존 MCP Tool 이름·Schema 유지

### 21.2 Application Service

- `execute(Command)` 중심
- 의존성 6개 이하
- 파일·XML 직접 조작 없음
- 구조화 Result 반환
- MCP 전용 문자열 미반환

### 21.3 Pipeline

- Blueprint 단계에서 전체 Target Path 확인 가능
- Processor 순서가 명시적
- 부분 실패 정책 보존
- 기능별 Processor와 Verifier 조합 가능
- History 실패와 파일 생성 실패 분리

### 21.4 회귀

- 기존 Spring 테스트 전체 통과
- MCP Tool Definition Snapshot 일치
- Golden File 일치
- 생성 파일 수·경로 일치
- `auto`·`claude` 분기 일치
- Design Context 전달 일치

## 22. 추적성 표

| 영향 분석 항목 | 구현 요구사항 |
|---|---|
| MCP Schema 위험 | `ORT-MCP-001`~`ORT-MCP-007` |
| 생성 순서 위험 | §10, §11 |
| 부분 실패 위험 | §10.5, §16 |
| Design Context 유실 | §8, §9 |
| 공통 Service 이동 위험 | `ORT-PRN-010`, §20 |
| 테스트 결합 | §19 |
| Plan 메모리 증가 | §10.3, §17 |
| Tool 중복 등록 | `ORT-MCP-005`, `ORT-MCP-006` |
| Compatibility 수명 | §15 |
| Package 이동 영향 | §6 |

## 23. 최종 구현 규칙

다음 조건을 모두 만족해야 구현 완료로 판단한다.

1. Tool은 Facade에만 위임한다.
2. Prompt, 전체 생성, 단일 화면 Source Use Case가 분리된다.
3. CRUD·게시판·Master/Detail이 공통 Pipeline 계약을 사용한다.
4. 기능별 실행 순서와 실패 정책이 보존된다.
5. 기존 Result와 MCP 응답이 유지된다.
6. 전체 MCP Tool Definition이 변경되지 않는다.
7. 기존 파일 수·경로·내용이 Golden File과 일치한다.
8. Design Reference와 ScreenSpecification이 신규 Command에서 보존된다.
9. Package 이동은 마지막 별도 변경으로 수행한다.
10. 구조 리팩터링과 기능 개선을 같은 변경에 포함하지 않는다.
