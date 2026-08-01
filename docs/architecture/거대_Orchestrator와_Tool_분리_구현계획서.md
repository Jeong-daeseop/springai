# 거대 Orchestrator와 Tool 분리 구현계획서

> 작성일: 2026-07-31
> 대상 프로젝트: `springai`
> 구현명세서: [거대 Orchestrator와 Tool 분리 구현명세서](./거대_Orchestrator와_Tool_분리_구현명세서.md)
> 영향 분석: [거대 Orchestrator와 Tool 분리 영향 분석](./거대_Orchestrator와_Tool_분리_영향분석.md)
> 설계 문서: [거대 Orchestrator와 Tool 분리 방안](./거대_Orchestrator와_Tool_분리_방안.md)
> 패키지 이동 체크리스트: [Generation Package Migration Checklist](./Generation_Package_Migration_Checklist.md)
> 상태: Pipeline 운영 경로 및 구형 Orchestrator 제거 완료 — WP-0~WP-8 완료

### 현재 구현 추적 (2026-08-01)

이번 작업에서 확인·반영한 범위:

- WP-0~WP-4: 계약 Snapshot, Layout 분리, 단일 화면 Source 분리, Prompt/자동 생성 Use Case 분리, CRUD 공통 Pipeline이 코드와 테스트에 이미 반영되어 있음
- WP-7A: `CrudPromptBuilderTool`의 MCP 노출 메서드 16개를 7개 Adapter로 분리하고 `McpConfig` 등록을 교체함
- MCP 계약: Tool 메서드 79개, 중복 Tool 0개, 등록 Tool 객체 31개 기준으로 Snapshot 테스트 통과
- 검증: `./gradlew test`, `./gradlew bootJar` 통과

계획서 기준 구현 완료 범위:

- WP-5: Board 전용 Planner·Renderer·POST_WRITE Processor·Verifier·History·Result Assembler 및 운영 Pipeline 연결 완료
- WP-6: Master/Detail Planner·Renderer·Pipeline·Processor·Verifier·관계 Resolver·Result Assembler 연결 완료
- WP-7B: Board·Master/Detail 전용 Pipeline 호출 연결 및 MCP Adapter 경계 테스트 완료
- WP-8: 기능 중심 Package 이동, 문서 동기화, 구형 Orchestrator·Compatibility Facade·직접 호출 테스트 제거 완료

WP-5는 테이블·스키마·메타데이터·Design Context·Layout·URL 충돌을 계산하는 `BoardGenerationPlanner`, 레이어별 Source를 `RenderedGenerationPlan`으로 바꾸는 `BoardGenerationRenderer`, Planner→Renderer→공통 Executor→POST_WRITE Processor→Verifier→History 연결 서비스와 Result Assembler를 추가했다. WP-6은 `MasterDetailGenerationPlanner`·`MasterDetailGenerationRenderer`·전용 Processor·Verifier·FK Resolver와 공통 Executor를 연결했다. Board·Master/Detail Use Case는 Pipeline만 호출하며, 구형 Orchestrator와 Compatibility Facade 및 직접 호출 테스트는 제거했다.

## 1. 구현 목표

다음 거대 클래스의 책임을 MCP Adapter, Application Use Case, Generation Pipeline과 Infrastructure로 분리한다.

- `CrudPromptBuilderTool`
- `ThymeleafLayoutTool`
- `CrudOrchestrationService`
- `BoardOrchestrationService`
- `MasterDetailOrchestrationService`

최종 구조:

```text
MCP Tool
    ↓
MCP Facade
    ↓
Use Case
    ↓
Feature Application Service
    ↓
Planner → Renderer → Executor
    ↓
Stage Processor
    ↓
Verifier
    ↓
History Recorder
```

## 2. 구현 전략

구현은 기능 동작을 바꾸지 않는 점진적 리팩터링으로 수행한다.

```text
계약 고정
    ↓
독립 Tool 시험 분리
    ↓
순수 Source 생성 분리
    ↓
Prompt·자동 생성 Use Case 분리
    ↓
CRUD Pipeline
    ↓
Board Pipeline
    ↓
Master/Detail Pipeline
    ↓
MCP Tool Adapter 분리
    ↓
Package·문서 정리
```

핵심 규칙:

1. 각 단계는 독립 PR 또는 독립 변경 단위로 구현한다.
2. 각 단계 완료 후 전체 테스트를 통과해야 다음 단계로 진행한다.
3. Tool 외부 시그니처를 변경하지 않는다.
4. 기존 결과 파일과 문자열을 먼저 보존한다.
5. 신규 기능은 이번 구현과 분리한다.
6. Package 이동은 마지막에 수행한다.

## 3. 사전 조건

### 3.1 코드 기준선

- [ ] 현재 `./gradlew test` 성공
- [ ] 현재 `./gradlew bootJar` 성공
- [ ] 현재 MCP Tool Definition 추출 가능
- [ ] 대표 CRUD·게시판·Master/Detail 생성 Fixture 확보
- [ ] 기존 생성 파일 수와 경로 기록

### 3.2 변경 범위 격리

- [ ] 사용자 작업과 리팩터링 변경을 구분
- [ ] 구조 변경 PR에 Template 개선을 포함하지 않음
- [ ] 구조 변경 PR에 파일 경로 정책 변경을 포함하지 않음
- [ ] 구조 변경 PR에 DB Migration을 포함하지 않음
- [ ] 구조 변경 PR에 신규 Tool 이름을 포함하지 않음

### 3.3 기준 지표

```text
Tool 객체: 25
@Tool 메서드: 79
CrudPromptBuilderTool: 1,015줄 / 의존성 16개
ThymeleafLayoutTool: 430줄 / 의존성 5개
CrudOrchestrationService: 324줄 / 의존성 15개
BoardOrchestrationService: 280줄 / 의존성 17개
MasterDetailOrchestrationService: 300줄 / 의존성 10개
```

## 4. 품질 게이트

| Gate | 검증 |
|---|---|
| `G0` | 전체 MCP Tool Definition Snapshot 일치 |
| `G1` | 대상 단위 테스트 성공 |
| `G2` | 전체 `./gradlew test` 성공 |
| `G3` | `./gradlew bootJar` 성공 |
| `G4` | 생성 파일 트리 Snapshot 일치 |
| `G5` | Golden File 일치 |
| `G6` | Processor 순서·실패 정책 테스트 성공 |
| `G7` | Design Context 전달 테스트 성공 |
| `G8` | 문서와 Tool 목록 동기화 |

모든 구현 단계는 최소 `G0`, `G1`, `G2`를 통과해야 한다.

## 5. 전체 작업 패키지

| 단계 | 작업 패키지 | 영향도 | 선행 단계 |
|---|---|---|---|
| `WP-0` | MCP 계약과 생성 결과 기준선 고정 | 낮음 | 없음 |
| `WP-1` | ThymeleafLayoutTool 시험 분리 | 중간 | `WP-0` |
| `WP-2` | 단일 화면 Source 생성 분리 | 중간 | `WP-0` |
| `WP-3` | Prompt와 자동 생성 Use Case 분리 | 높음 | `WP-2` |
| `WP-4` | 공통 Pipeline과 CRUD 전환 | 매우 높음 | `WP-3` |
| `WP-5` | Board Pipeline 전환 | 높음 | `WP-4` |
| `WP-6` | Master/Detail Pipeline 전환 | 높음 | `WP-4` |
| `WP-7` | MCP Tool Adapter 실제 분리 | 매우 높음 | `WP-5`, `WP-6` |
| `WP-8` | Package 이동·Compatibility 정리·문서 갱신 | 높음 | `WP-7` |

## 6. WP-0: 계약 기준선 고정

### 6.1 목표

Tool과 Generation Pipeline을 변경하기 전에 외부 계약과 생성 결과를 비교할 기준을 만든다.

명세 추적:

- `ORT-PRN-005`
- `ORT-PRN-007`
- `ORT-PRN-008`
- `ORT-MCP-001`~`ORT-MCP-007`

### 6.2 신규 파일

```text
src/test/java/com/krdevops/springai/config/McpToolDefinitionSnapshotTest.java
src/test/resources/mcp/tool-definitions-baseline.json

src/test/resources/generation/baseline/crud-jsp/
src/test/resources/generation/baseline/crud-thymeleaf-reuse/
src/test/resources/generation/baseline/crud-thymeleaf-create/
src/test/resources/generation/baseline/board-jsp/
src/test/resources/generation/baseline/board-thymeleaf/
src/test/resources/generation/baseline/master-detail-jsp/
src/test/resources/generation/baseline/master-detail-thymeleaf/
src/test/resources/generation/baseline/layout/
```

### 6.3 작업

- [ ] `ORT-P0-001` `ToolCallbackProvider`에서 전체 Tool Definition 추출
- [ ] `ORT-P0-002` Tool 이름 기준 정렬
- [ ] `ORT-P0-003` Input Schema Key 정규화
- [ ] `ORT-P0-004` Tool 설명 Snapshot 저장
- [ ] `ORT-P0-005` 중복 Tool 이름 검증
- [ ] `ORT-P0-006` `@Tool` 메서드 수 79 검증
- [ ] `ORT-P0-007` 대표 생성 파일 트리 저장
- [ ] `ORT-P0-008` 대표 파일 Golden Fixture 저장
- [ ] `ORT-P0-009` 기존 MCP 응답 문자열 Snapshot 저장
- [ ] `ORT-P0-010` Processor 현재 실행 순서를 테스트 이름으로 문서화

### 6.4 테스트

```bash
./gradlew test --tests "com.krdevops.springai.config.McpToolDefinitionSnapshotTest"
./gradlew test
```

### 6.5 완료 조건

- [ ] `G0` 통과
- [ ] 기존 테스트 전체 성공
- [ ] Tool 이름·Schema·설명 기준선 생성
- [ ] 대표 생성 파일 수·경로·내용 기준선 생성

### 6.6 롤백

운영 코드를 변경하지 않으므로 신규 테스트와 Fixture만 제거하면 된다.

## 7. WP-1: ThymeleafLayoutTool 시험 분리

### 7.1 목표

한 개 Tool을 대상으로 Tool → Facade → Use Case → Planner·Processor 패턴을 먼저 검증한다.

명세 추적:

- `ORT-PRN-001`~`ORT-PRN-003`
- §8.6
- §9.5
- §11.4
- §14

### 7.2 신규 파일

```text
service/generation/api/GenerateThymeleafLayoutUseCase.java
service/generation/layout/GenerateThymeleafLayoutCommand.java
service/generation/layout/LayoutGenerationResult.java
service/generation/layout/ThymeleafLayoutGenerationService.java
service/generation/layout/ThymeleafLayoutGenerationPlanner.java
service/generation/layout/MainPageRenderer.java
service/generation/layout/ClasspathAssetCopier.java
service/generation/layout/ServletContextConfigurer.java
service/generation/layout/ComponentScanConfigurer.java
service/generation/mcp/ThymeleafLayoutMcpFacade.java
service/generation/mcp/ThymeleafLayoutResultFormatter.java
```

### 7.3 수정 파일

```text
tools/ThymeleafLayoutTool.java
src/test/java/com/krdevops/springai/tools/ThymeleafLayoutToolTest.java
```

### 7.4 작업

- [ ] `ORT-P1-001` Layout Tool 현재 결과 Snapshot 추가
- [ ] `ORT-P1-002` `GenerateThymeleafLayoutCommand` 도입
- [ ] `ORT-P1-003` 기본값 변환 Mapper 구현
- [ ] `ORT-P1-004` Layout·GNB 파일 계획 로직 이동
- [ ] `ORT-P1-005` Main HTML 생성 로직 이동
- [ ] `ORT-P1-006` Logo 복사 로직 이동
- [ ] `ORT-P1-007` Servlet Context 패치 로직 이동
- [ ] `ORT-P1-008` Component Scan 패치 로직 이동
- [ ] `ORT-P1-009` MyBatis·Thymeleaf Runtime 위임 연결
- [ ] `ORT-P1-010` Layout 결과 Formatter 추출
- [ ] `ORT-P1-011` Tool 의존성을 Facade 하나로 축소
- [ ] `ORT-P1-012` Tool 내부 File API 제거

### 7.5 보존 시나리오

- [ ] Custom Layout Base Path
- [ ] `overwrite=false` Layout 보존
- [ ] 기본 `overwrite=true`
- [ ] GNB Java·Mapper 4개 생성
- [ ] Main Thymeleaf 화면 생성
- [ ] WAR Entry Point 미변경
- [ ] 기존 GNB 파일 보존
- [ ] 기본 Package 경고
- [ ] 메뉴·프로그램 테이블명 전달
- [ ] MyBatis 설정 보강
- [ ] Component Scan 상위 Package 계산
- [ ] Servlet XML 오류 시 무수정
- [ ] Interceptor 중복 방지
- [ ] Logo 생성과 보존
- [ ] Servlet 패치 실패 시 Thymeleaf Runtime Skip

### 7.6 테스트

```bash
./gradlew test --tests "com.krdevops.springai.tools.ThymeleafLayoutToolTest"
./gradlew test --tests "com.krdevops.springai.service.generation.layout.*"
./gradlew test
```

### 7.7 완료 조건

- [ ] Tool 120줄 이하
- [ ] Tool 의존성 1개
- [ ] Tool File API 사용 0건
- [ ] `G0`, `G1`, `G2`, `G4`, `G5`, `G6` 통과

### 7.8 롤백

Tool이 기존 구현을 유지한 커밋으로 되돌리고 신규 Layout Service Bean을 제거한다. MCP Tool 등록은 변경하지 않으므로 외부 계약 롤백은 필요 없다.

## 8. WP-2: 단일 화면 Source 생성 분리

### 8.1 목표

`CrudPromptBuilderTool` 내부의 CRUD·게시판·Master/Detail 단일 화면 Source 생성을 순수 Use Case로 이동한다.

명세 추적:

- §8.5
- §9.4
- §13

### 8.2 신규 파일

```text
service/generation/api/GenerateScreenSourceUseCase.java
service/generation/model/GeneratedSource.java
service/generation/model/GenerateScreenSourceCommand.java
service/generation/source/ScreenSourceGenerationService.java
service/generation/source/ScreenSourceGenerator.java
service/generation/source/CrudScreenSourceGenerator.java
service/generation/source/BoardScreenSourceGenerator.java
service/generation/source/MasterDetailScreenSourceGenerator.java
service/generation/mcp/ScreenSourceMcpFacade.java
service/generation/mcp/ScreenSourceResultFormatter.java
```

### 8.3 수정 파일

```text
tools/CrudPromptBuilderTool.java
src/test/java/com/krdevops/springai/tools/CrudPromptBuilderToolTest.java
```

### 8.4 작업

- [ ] `ORT-P2-001` 기존 단일 화면 응답 Snapshot 추가
- [ ] `ORT-P2-002` `GeneratedSource` 도입
- [ ] `ORT-P2-003` 화면 Command 도입
- [ ] `ORT-P2-004` CRUD Source Generator 추출
- [ ] `ORT-P2-005` Board Source Generator 추출
- [ ] `ORT-P2-006` Master/Detail Source Generator 추출
- [ ] `ORT-P2-007` 기존 경로 계산 로직 이동
- [ ] `ORT-P2-008` 기존 FK·Detail Domain 정책 이동
- [ ] `ORT-P2-009` Screen Source Formatter 추출
- [ ] `ORT-P2-010` 기존 Tool 메서드를 Facade 위임으로 변경

### 8.5 보존 시나리오

- [ ] CRUD List·Detail·Regist·Updt
- [ ] Board List·Detail·Regist·Updt
- [ ] Master List·Detail·Regist·Updt
- [ ] JSP·Thymeleaf Layer Key
- [ ] 권장 저장 경로
- [ ] 게시판 기본 테이블 Resolver
- [ ] Master/Detail FK 추론
- [ ] 파일 미저장

### 8.6 테스트

```bash
./gradlew test --tests "com.krdevops.springai.tools.CrudPromptBuilderToolTest"
./gradlew test --tests "com.krdevops.springai.service.generation.source.*"
./gradlew test
```

### 8.7 완료 조건

- [ ] Tool이 단일 화면 생성에서 Schema·Factory·Renderer를 직접 사용하지 않음
- [ ] 단일 화면 Source·경로 Snapshot 일치
- [ ] `G0`, `G1`, `G2`, `G5` 통과

### 8.8 롤백

기존 `generate*Screen()` private 메서드를 복구하고 신규 Source Service 호출을 제거한다.

## 9. WP-3: Prompt와 자동 생성 Use Case 분리

### 9.1 목표

Tool에서 `llmProvider` 분기, Metadata 조회와 Design Context 해석을 제거한다.

명세 추적:

- §8.2~§8.4
- §9.1~§9.3
- `ORT-PRN-009`

### 9.2 신규 파일

```text
service/generation/api/DispatchCrudGenerationUseCase.java
service/generation/api/GenerateCrudProjectUseCase.java
service/generation/api/BuildCrudPromptUseCase.java
service/generation/api/DispatchMasterDetailGenerationUseCase.java
service/generation/api/GenerateMasterDetailProjectUseCase.java
service/generation/api/BuildMasterDetailPromptUseCase.java
service/generation/api/GenerateBoardProjectUseCase.java
service/generation/crud/CrudGenerationCommand.java
service/generation/board/BoardGenerationCommand.java
service/generation/masterdetail/MasterDetailGenerationCommand.java
service/generation/crud/CrudGenerationDispatchService.java
service/generation/masterdetail/MasterDetailGenerationDispatchService.java
service/generation/mcp/CrudGenerationMcpFacade.java
service/generation/mcp/BoardGenerationMcpFacade.java
service/generation/mcp/MasterDetailGenerationMcpFacade.java
```

### 9.3 수정 파일

```text
tools/CrudPromptBuilderTool.java
service/CrudPromptBuilderService.java
service/MasterDetailService.java
```

### 9.4 작업

- [ ] `ORT-P3-001` CRUD Command 전체 필드 정의
- [ ] `ORT-P3-002` 게시판 Command 전체 필드 정의
- [ ] `ORT-P3-003` Master/Detail Command 전체 필드 정의
- [ ] `ORT-P3-004` `ProgramMetadataOverrides` 도입
- [ ] `ORT-P3-005` `DesignContextReference` 도입
- [ ] `ORT-P3-006` `LayoutOptions` 도입
- [ ] `ORT-P3-007` CRUD `auto/claude` Dispatch 이동
- [ ] `ORT-P3-008` Master/Detail `auto/claude` Dispatch 이동
- [ ] `ORT-P3-009` 게시판 전체 생성 Facade 연결
- [ ] `ORT-P3-010` 기존 Prompt 결과 Snapshot 검증
- [ ] `ORT-P3-011` 기존 Orchestrator 호출 Adapter 연결
- [ ] `ORT-P3-012` Tool의 Metadata·Design Service 의존 제거

### 9.5 필수 옵션 전달 테스트

- [ ] `designReferenceId`
- [ ] `screenSpecificationId`
- [ ] `programFileName`
- [ ] `programUrl`
- [ ] `programKoreanName`
- [ ] `programStorePath`
- [ ] `defaultBbsId`
- [ ] `layoutMode`
- [ ] `layoutView`
- [ ] `breadcrumbView`

### 9.6 테스트

```bash
./gradlew test --tests "com.krdevops.springai.tools.CrudPromptBuilderToolTest"
./gradlew test --tests "com.krdevops.springai.service.CrudPromptBuilderServiceTest"
./gradlew test --tests "com.krdevops.springai.service.MasterDetailServiceTest"
./gradlew test --tests "com.krdevops.springai.service.generation.*"
./gradlew test
```

### 9.7 완료 조건

- [ ] Tool 내부 `llmProvider` 업무 분기 없음
- [ ] Design Context 전달 유지
- [ ] Prompt 결과 동일
- [ ] 기존 `auto` 결과 동일
- [ ] `G0`, `G1`, `G2`, `G7` 통과

### 9.8 롤백

Tool의 기존 분기 로직을 복구한다. 신규 Use Case는 등록 호출자가 없어지면 런타임에 영향을 주지 않는다.

## 10. WP-4: 공통 Pipeline과 CRUD 전환

### 10.1 목표

공통 Pipeline 계약을 구현하고 CRUD Generation을 첫 적용 대상으로 전환한다.

명세 추적:

- §10
- §11.1
- §12.1
- §16
- §17

### 10.2 신규 공통 파일

```text
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

service/generation/pipeline/GenerationPreflight.java
service/generation/pipeline/GenerationRenderer.java
service/generation/pipeline/GenerationExecutor.java
service/generation/pipeline/GenerationStageProcessor.java
service/generation/pipeline/GenerationProcessorRunner.java
service/generation/pipeline/GenerationVerifier.java
service/generation/pipeline/GenerationHistoryRecorder.java
```

### 10.3 신규 CRUD 파일

```text
service/generation/crud/CrudGenerationApplicationService.java
service/generation/crud/CrudGenerationPlanner.java
service/generation/crud/CrudGenerationRenderer.java
service/generation/crud/CrudGenerationResultAssembler.java
service/generation/crud/CrudTableDensityCssProcessor.java
service/generation/crud/CrudFormColumnCssProcessor.java
service/generation/crud/CrudEntryPointProcessor.java
```

### 10.4 수정 파일

```text
service/CrudOrchestrationService.java
src/test/java/com/krdevops/springai/service/CrudOrchestrationServiceTest.java
```

### 10.5 작업

- [ ] `ORT-P4-001` 공통 Stage와 Failure Policy 구현
- [ ] `ORT-P4-002` Blueprint와 Rendered Plan 구현
- [ ] `ORT-P4-003` 기존 `CodeService` Adapter Executor 구현
- [ ] `ORT-P4-004` Processor Runner 구현
- [ ] `ORT-P4-005` 의존 Processor Skip 처리 구현
- [ ] `ORT-P4-006` 공통 Verifier Adapter 구현
- [ ] `ORT-P4-007` History Recorder Adapter 구현
- [ ] `ORT-P4-008` CRUD Preflight 구현
- [ ] `ORT-P4-009` CRUD Planner 구현
- [ ] `ORT-P4-010` CRUD Renderer 구현
- [ ] `ORT-P4-011` CRUD Processor 목록 구현
- [ ] `ORT-P4-012` CRUD Result Assembler 구현
- [ ] `ORT-P4-013` 기존 Orchestrator를 Compatibility Facade로 변경
- [ ] `ORT-P4-014` 파일 수·경로·내용 비교
- [ ] `ORT-P4-015` 부분 실패 정책 비교
- [ ] `ORT-P4-016` Processor 시간·파일 크기 관측 로그 추가

### 10.6 CRUD Processor 순서

```text
PRE_WRITE
  100 Table Density CSS
  110 Form Column CSS

WRITE
  Layer별 Rendering·저장

POST_WRITE
  100 Entry Point
  200 Thymeleaf Runtime
  210 Controller Scan
  300 MyBatis

PRE_VERIFY
  100 Common Contract

VERIFY
  Directory Validation

HISTORY
  Generation History
```

### 10.7 테스트

```bash
./gradlew test --tests "com.krdevops.springai.service.CrudOrchestrationServiceTest"
./gradlew test --tests "com.krdevops.springai.service.generation.pipeline.*"
./gradlew test --tests "com.krdevops.springai.service.generation.crud.*"
./gradlew test
./gradlew bootJar
```

### 10.8 완료 조건

- [ ] CRUD JSP 11개 유지
- [ ] CRUD Thymeleaf Create 16개 유지
- [ ] Reuse Layout 실패 정책 유지
- [ ] 파일 일부 실패 후 계속 실행
- [ ] History 실패 비치명 처리
- [ ] 기존 Result 타입 유지
- [ ] 기존 Orchestrator 100줄 이하
- [ ] `G0`~`G7` 통과

### 10.9 롤백

`CrudOrchestrationService`의 기존 구현으로 복구한다. 신규 공통 Pipeline Bean은 호출자가 없으면 운영 동작에 영향을 주지 않는다.

## 11. WP-5: Board Pipeline 전환

### 11.1 목표

CRUD에서 검증한 Pipeline을 게시판 생성에 적용한다.

명세 추적:

- §11.2
- §12.2

### 11.2 신규 파일

```text
service/generation/board/BoardGenerationApplicationService.java
service/generation/board/BoardGenerationPlanner.java
service/generation/board/BoardGenerationRenderer.java
service/generation/board/BoardGenerationResultAssembler.java
service/generation/board/BoardCrudCssProcessor.java
service/generation/board/BoardEntryPointProcessor.java
service/generation/board/BoardGeneratedContractVerifier.java
```

### 11.3 수정 파일

```text
service/BoardOrchestrationService.java
src/test/java/com/krdevops/springai/service/BoardOrchestrationServiceTest.java
```

### 11.4 작업

- [~] `ORT-P5-001` Board Table Set 해석 이동 — `BoardGenerationPlanner`에 1차 추출
- [~] `ORT-P5-002` Board Schema·Metadata Preflight 이동 — 실패 분류 포함
- [~] `ORT-P5-003` Design Context 전달 이동 — Planner 입력 경계 고정
- [~] `ORT-P5-005` Board Renderer 구현 — `RenderedGenerationPlan` 생성까지 완료
- [~] `ORT-P5-004` Board Planner 연결 — `BoardGenerationPipelineService`에서 Renderer·공통 Executor 호출
- [x] `ORT-P5-004` Board Planner 구현
- [x] `ORT-P5-005` Board Renderer 구현
- [x] `ORT-P5-006` Board CSS Processor 구현
- [x] `ORT-P5-007` Board Entry Point Processor 구현
- [x] `ORT-P5-008` Board 전용 감사 Verifier 구현
- [x] `ORT-P5-009` Result Assembler 구현
- [~] `ORT-P5-010` Board Use Case를 Pipeline 호출 경로로 전환 — 기존 Orchestrator 생성자 호환 경로는 이행 기간 유지

### 11.5 보존 항목

- [ ] 게시판 기본 테이블명
- [ ] `defaultBbsId`
- [ ] Program URL
- [ ] 게시판 Display Name
- [ ] Board CSS Status
- [ ] Board Audit Summary
- [ ] JSP·Thymeleaf 파일 수
- [ ] Layout Create·Reuse 정책

### 11.6 테스트

```bash
./gradlew test --tests "com.krdevops.springai.service.BoardOrchestrationServiceTest"
./gradlew test --tests "com.krdevops.springai.service.generation.board.*"
./gradlew test
./gradlew bootJar
```

### 11.7 완료 조건

- [ ] Board Golden File 일치
- [ ] Board Processor 순서 일치
- [ ] 게시판 감사 실패 처리 일치
- [ ] 기존 Orchestrator 100줄 이하
- [ ] `G0`~`G7` 통과

### 11.8 롤백

기존 `BoardOrchestrationService` 구현을 복구하고 신규 Board Application Service 호출을 제거한다.

## 12. WP-6: Master/Detail Pipeline 전환

### 12.1 목표

Master/Detail 고유 관계·Naming·프로젝트 패치 로직을 분리하고 공통 Pipeline에 적용한다.

명세 추적:

- §11.3
- §12.3

### 12.2 신규 파일

```text
service/generation/masterdetail/MasterDetailGenerationApplicationService.java
service/generation/masterdetail/MasterDetailGenerationPlanner.java
service/generation/masterdetail/MasterDetailGenerationRenderer.java
service/generation/masterdetail/MasterDetailRelationshipResolver.java
service/generation/masterdetail/MasterDetailNamingPolicy.java
service/generation/masterdetail/MasterDetailGenerationResultAssembler.java
service/generation/masterdetail/MainControllerProcessor.java
service/generation/masterdetail/ServletContextScanProcessor.java
```

### 12.3 수정 파일

```text
service/MasterDetailOrchestrationService.java
src/test/java/com/krdevops/springai/service/MasterDetailOrchestrationServiceTest.java
```

### 12.4 작업

- [~] `ORT-P6-001` FK 추론 로직 추출 — Planner에 1차 적용, Resolver 분리 잔여
- [~] `ORT-P6-002` Detail Domain Naming 추출 — Planner에 1차 적용
- [x] `ORT-P6-003` Master·Detail Schema Preflight 구현
- [x] `ORT-P6-004` MasterDetail Planner 구현
- [x] `ORT-P6-005` MasterDetail Renderer 구현
- [~] `ORT-P6-005a` MasterDetail Planner→Renderer→공통 Executor 연결 — 기존 Use Case 연결 잔여
- [x] `ORT-P6-006` MainController Processor 구현
- [x] `ORT-P6-007` Servlet Context Scan Processor 구현
- [x] `ORT-P6-008` Result Assembler 구현
- [~] `ORT-P6-009` 기존 Orchestrator를 Compatibility Facade로 변경 — Pipeline 기본 경로 전환 및 Deprecated 호환 경로 유지

### 12.5 보존 항목

- [ ] FK 후보 우선순위
- [ ] Detail Domain명
- [ ] Master·Detail Layer 이름
- [ ] MainController Source
- [ ] Component Scan 범위
- [ ] 잘못된 Servlet XML 무수정
- [ ] 파일 수와 경로
- [ ] 부분 실패 정책

### 12.6 테스트

```bash
./gradlew test --tests "com.krdevops.springai.service.MasterDetailOrchestrationServiceTest"
./gradlew test --tests "com.krdevops.springai.service.generation.masterdetail.*"
./gradlew test
./gradlew bootJar
```

### 12.7 완료 조건

- [ ] Master/Detail Golden File 일치
- [ ] MainController·Servlet 패치 일치
- [ ] 기존 Orchestrator 100줄 이하
- [ ] `G0`~`G7` 통과

### 12.8 롤백

기존 `MasterDetailOrchestrationService` 구현을 복구한다. 추출된 Resolver와 Processor는 호출자가 없으면 영향이 없다.

## 13. WP-7: MCP Tool Adapter 실제 분리

### 13.1 목표

Use Case 분리가 끝난 뒤 `CrudPromptBuilderTool`의 16개 MCP 메서드를 7개 Adapter로 이동한다.

명세 추적:

- §7
- `ORT-MCP-002`~`ORT-MCP-006`

### 13.2 신규 파일

```text
tools/generation/CrudGenerationTool.java
tools/generation/BoardGenerationTool.java
tools/generation/MasterDetailGenerationTool.java
tools/generation/JoinQueryTool.java
tools/generation/CrudScreenSourceTool.java
tools/generation/BoardScreenSourceTool.java
tools/generation/MasterDetailScreenSourceTool.java
```

### 13.3 수정 파일

```text
config/McpConfig.java
tools/CrudPromptBuilderTool.java
src/test/java/com/krdevops/springai/tools/CrudPromptBuilderToolTest.java
src/test/java/com/krdevops/springai/config/McpToolDefinitionSnapshotTest.java
```

### 13.4 작업

- [x] `ORT-P7-001` 기존 Tool 메서드 Signature 복사
- [x] `ORT-P7-002` 기존 `@Tool` 설명 값 복사
- [x] `ORT-P7-003` 각 Tool을 Facade 하나에 연결
- [x] `ORT-P7-004` Java 하위 호환 Overload 처리
- [x] `ORT-P7-005` `McpConfig`에서 기존 Tool 제거
- [x] `ORT-P7-006` 신규 Tool 7개 등록
- [x] `ORT-P7-007` 중복 Tool 검사
- [x] `ORT-P7-008` Tool Definition Snapshot 비교
- [x] `ORT-P7-009` Tool 객체 수 31 확인
- [x] `ORT-P7-010` Tool 메서드 수 79 확인
- [x] `ORT-P7-011` 기존 `CrudPromptBuilderTool` Bean 제거
- [x] `ORT-P7-012` 필요 시 비등록 Compatibility Facade 유지

> `ORT-P7-001`~`ORT-P7-012`는 MCP 계약 Adapter 분리(WP-7A) 기준으로 완료했다. Board·Master/Detail 모두 Pipeline 호출 경로로 연결되었고, `CrudPromptBuilderToolTest`의 생성 시나리오도 Pipeline 실행·Result Assembler Mock 경계로 전환했다. 구형 Orchestrator 직접 동작 테스트는 삭제 직전까지 Compatibility 회귀군으로 별도 실행한다.

### 13.5 금지 사항

- [ ] Request DTO 단일 파라미터로 변경하지 않음
- [ ] Tool 이름 변경하지 않음
- [ ] Optional 파라미터를 필수로 변경하지 않음
- [ ] 신규 Tool 이름 추가하지 않음
- [ ] 기존 Tool과 신규 Tool 동시 등록하지 않음

### 13.6 테스트

```bash
./gradlew test --tests "com.krdevops.springai.config.McpToolDefinitionSnapshotTest"
./gradlew test --tests "com.krdevops.springai.tools.generation.*"
./gradlew test
./gradlew bootJar
```

가능한 경우 실제 MCP Client로 다음 Tool을 호출한다.

```text
buildFullCrudPrompt
buildBoardFeature
buildMasterDetailPrompt
generateCrudList
generateBoardList
generateMasterList
generateThymeleafLayout
```

### 13.7 완료 조건

- [x] Tool 이름·Schema·설명 Snapshot 동일
- [x] Tool 메서드 수 79
- [x] 중복 Tool 0
- [x] Tool 클래스당 120줄 이하
- [x] Tool 의존성 1~2개
- [x] `G0`, `G1`, `G2`, `G3` 통과

### 13.8 롤백

`McpConfig`에서 신규 7개 Tool을 제거하고 기존 `CrudPromptBuilderTool` Bean을 다시 등록한다. Use Case와 Pipeline은 그대로 유지할 수 있다.

## 14. WP-8: Package 이동과 정리

### 14.1 목표

구조 분리가 안정화된 후 Generation 기능을 기능 중심 패키지로 이동하고 Compatibility 코드를 정리한다.

### 14.2 작업

- [ ] `ORT-P8-001` `feature.generation` Package 생성
- [ ] `ORT-P8-002` MCP Adapter 이동
- [ ] `ORT-P8-003` Application Port·Service 이동
- [ ] `ORT-P8-004` Pipeline 이동
- [ ] `ORT-P8-005` Domain Model 이동
- [ ] `ORT-P8-006` Infrastructure Adapter 이동
- [ ] `ORT-P8-007` Import 갱신
- [ ] `ORT-P8-008` Component Scan 확인
- [~] `ORT-P8-009` 불필요 Compatibility Facade 제거 여부 결정 — 외부 호환 생성자·기존 테스트 사용으로 현 단계 유지
- [ ] `ORT-P8-010` 사용하지 않는 기존 Helper 제거
- [ ] `ORT-P8-011` 문서와 다이어그램 갱신

### 14.3 문서 갱신

우선 대상:

```text
docs/tool-reference/CrudPromptBuilderTool_기능및역할_상세설명.md
docs/tool-reference/ThymeleafLayoutTool_기능및역할_상세설명.md
docs/tool-reference/MCP_Tool_전체목록.md
docs/tool-catalog.md
docs/architecture/SpringAI_프로젝트_전체_아키텍처_분석.md
docs/crud/CrudPromptBuilderTool_WorkflowDefinition_관계_검토.md
```

검토 대상:

- 대상 클래스 또는 Tool을 언급하는 문서 58개

Tool 이름만 언급하는 사용자 가이드는 외부 이름이 유지되면 수정하지 않아도 된다. 내부 클래스 구조를 설명하는 문서는 갱신해야 한다.

### 14.4 테스트

```bash
./gradlew compileJava
./gradlew test
./gradlew bootJar
```

### 14.5 완료 조건

- [ ] 로직 변경 없이 Package만 이동
- [ ] Component Scan 정상
- [ ] 전체 테스트 성공
- [ ] MCP Snapshot 일치
- [ ] 문서 링크 정상
- [ ] `G0`~`G8` 통과

### 14.6 롤백

Package 이동 커밋만 되돌린다. 로직 분리 커밋과 섞지 않아야 이 방식으로 롤백할 수 있다.

## 15. 테스트 매트릭스

| 기능 | Unit | Contract | Golden | Integration | Build |
|---|---|---|---|---|---|
| MCP Tool 등록 |  | 필수 |  | Spring Context | `bootJar` |
| Layout | 필수 | MCP | 필수 | 파일·XML | `test` |
| 단일 화면 Source | 필수 | MCP | Source | Facade | `test` |
| CRUD Prompt | 필수 | MCP | Prompt | Dispatch | `test` |
| CRUD 자동 생성 | 필수 | MCP | 필수 | 파일 생성 | 생성 프로젝트 |
| 게시판 생성 | 필수 | MCP | 필수 | 파일 생성 | 생성 프로젝트 |
| Master/Detail | 필수 | MCP | 필수 | 파일 생성 | 생성 프로젝트 |
| Package 이동 |  | MCP | 회귀 | Spring Context | `bootJar` |

## 16. 핵심 회귀 시나리오

### 16.1 MCP

- [ ] Tool 79개
- [ ] 중복 이름 없음
- [ ] Tool 설명 동일
- [ ] Optional 파라미터 동일
- [ ] Claude Tool 선택에 필요한 설명 유지

### 16.2 CRUD

- [ ] Schema 없음
- [ ] 잘못된 Package
- [ ] Metadata 충돌
- [ ] Route 충돌
- [ ] Layout 없음
- [ ] JSP 11개
- [ ] Thymeleaf Create 16개
- [ ] 파일 일부 실패
- [ ] Rendering 실패
- [ ] Validation 실패
- [ ] History 실패
- [ ] Design Context 적용

### 16.3 게시판

- [ ] 기본 Table Set
- [ ] 명시 Table Set
- [ ] Metadata 충돌
- [ ] `bbsId`
- [ ] Board CSS
- [ ] Board Audit
- [ ] Layout Create·Reuse
- [ ] 파일 일부 실패

### 16.4 Master/Detail

- [ ] Master Schema 없음
- [ ] Detail Schema 없음
- [ ] FK 자동 추론
- [ ] Detail Domain명
- [ ] MainController
- [ ] Servlet Component Scan
- [ ] 잘못된 XML 무수정
- [ ] 파일 일부 실패

### 16.5 Layout

- [ ] Layout 5개
- [ ] GNB 4개
- [ ] Main HTML
- [ ] Logo
- [ ] Overwrite 정책
- [ ] Component Scan
- [ ] Interceptor 중복 방지
- [ ] Servlet 실패 시 Runtime Skip

## 17. 위험 관리

| 위험 | 감지 | 대응 | 롤백 단위 |
|---|---|---|---|
| MCP Schema 변경 | `G0` | Signature 복원 | 해당 Tool PR |
| 중복 Tool | Spring Context 실패 | `McpConfig` 원자적 교체 | `WP-7` |
| 파일 수 변경 | `G4` | Planner Layer 목록 수정 | 기능별 WP |
| Source 변경 | `G5` | Renderer 위임 경로 수정 | 기능별 WP |
| Processor 순서 변경 | `G6` | Pipeline Policy 수정 | 기능별 WP |
| Design Context 누락 | `G7` | Command Mapper 수정 | `WP-3` |
| 메모리 증가 | 계측 로그 | Blueprint·Renderer 분리 | `WP-4` |
| 문서 Drift | `G8` | 문서 목록 갱신 | `WP-8` |

## 18. 구현 중지 조건

다음 조건이 발생하면 해당 단계 구현을 중지하고 원인을 먼저 해결한다.

- MCP Tool Definition Snapshot 불일치
- 기존 Tool 이름 누락
- Optional 파라미터가 Required로 변경
- Golden File 비의도 변경
- 파일 수 또는 경로 변경
- Processor 순서가 기존 정책과 불일치
- Design Context 옵션 누락
- 기존 부분 실패 정책 변경
- 기존 전체 테스트 실패

## 19. 코드 리뷰 체크리스트

### Tool

- [ ] Facade 외 Service를 직접 의존하지 않는가
- [ ] 파일·DB·Renderer를 직접 호출하지 않는가
- [ ] 기존 `@Tool` 설명과 Signature가 같은가
- [ ] 업무 분기가 없는가

### Application

- [ ] Command와 Result가 구조화되어 있는가
- [ ] MCP 문자열을 생성하지 않는가
- [ ] Infrastructure API를 직접 조작하지 않는가
- [ ] 의존성 6개 이하인가

### Pipeline

- [ ] Stage와 Order가 명시적인가
- [ ] Failure Policy가 명시적인가
- [ ] Spring Bean 순서에 의존하지 않는가
- [ ] 파일 일부 실패 후 계속 실행하는가
- [ ] History 실패가 생성 성공을 취소하지 않는가

### Compatibility

- [ ] 기존 Result 타입을 유지하는가
- [ ] Tool Definition Snapshot이 같은가
- [ ] 기존 Java Overload 처리 방안이 있는가
- [ ] Compatibility Facade에 제거 계획이 있는가

## 20. 완료 정의

### 구조

- [ ] `CrudPromptBuilderTool`의 16개 MCP 메서드가 7개 Tool Adapter로 분리됨
- [ ] `ThymeleafLayoutTool`이 Facade 하나에 위임
- [ ] Prompt·전체 생성·단일 화면 Source Use Case 분리
- [ ] CRUD·게시판·Master/Detail 공통 Pipeline 사용
- [ ] Orchestrator가 Compatibility Facade 또는 100줄 이하 Application 조율자

### 품질

- [ ] 전체 Spring 테스트 성공
- [ ] `bootJar` 성공
- [ ] MCP Tool Definition Snapshot 일치
- [ ] Tool 메서드 79개 유지
- [ ] Golden File 일치
- [ ] Processor 순서 테스트 성공
- [ ] Design Context 전달 테스트 성공

### 문서

- [ ] 구현명세서 상태 갱신
- [ ] 구현계획서 체크리스트 갱신
- [ ] Tool Reference 갱신
- [ ] MCP Tool 목록 갱신
- [ ] 전체 아키텍처 문서 갱신

## 21. 요구사항 추적표

| 구현명세 요구사항 | 작업 패키지 |
|---|---|
| `ORT-PRN-001`~`ORT-PRN-010` | 전체 |
| `ORT-MCP-001`~`ORT-MCP-007` | `WP-0`, `WP-7` |
| Command 명세 §8 | `WP-2`, `WP-3` |
| Use Case 명세 §9 | `WP-1`, `WP-2`, `WP-3` |
| Pipeline 명세 §10 | `WP-4` |
| CRUD 정책 §11.1 | `WP-4` |
| 게시판 정책 §11.2 | `WP-5` |
| Master/Detail 정책 §11.3 | `WP-6` |
| Layout 정책 §11.4 | `WP-1` |
| Feature Planner §12 | `WP-1`, `WP-4`, `WP-5`, `WP-6` |
| 단일 화면 §13 | `WP-2` |
| Layout 컴포넌트 §14 | `WP-1` |
| Compatibility §15 | `WP-4`~`WP-8` |
| 오류 처리 §16 | `WP-4`~`WP-6` |
| 성능 §17 | `WP-4` |
| 보안 §18 | 전체 |
| 테스트 §19 | 전체 |

## 22. 최종 실행 순서

```text
WP-0 MCP 계약 기준선
    ↓
WP-1 Thymeleaf Layout 시험 분리
    ↓
WP-2 단일 화면 Source 분리
    ↓
WP-3 Prompt·자동 생성 Use Case 분리
    ↓
WP-4 공통 Pipeline·CRUD
    ↓
WP-5 Board
    ↓
WP-6 Master/Detail
    ↓
WP-7 MCP Tool Adapter 분리
    ↓
WP-8 Package·Compatibility·문서 정리
```

`WP-5`와 `WP-6`은 `WP-4` 완료 후 내부적으로 독립적이지만 공통 Pipeline 파일 충돌을 피하기 위해 순차 적용을 권장한다.

## 23. 최종 권고

첫 구현은 `WP-0`과 `WP-1`만 수행한다.

이 두 단계에서 다음을 검증한 후 나머지 작업을 진행한다.

1. MCP 계약 Snapshot이 실제 회귀를 감지하는가
2. Tool을 Facade 하나로 축소해도 기존 응답이 유지되는가
3. XML·파일·Runtime 로직을 Service로 이동해도 결과가 같은가
4. 기존 테스트를 신규 계층 테스트로 안전하게 재배치할 수 있는가

이 패턴이 검증되지 않은 상태에서 `CrudOrchestrationService` Pipeline 전환이나 MCP Tool 클래스 분리를 먼저 수행하지 않는다.
