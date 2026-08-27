# 화면 생성 Tool 3종 비교 분석 — CrudGenerationTool / BoardGenerationTool / MasterDetailGenerationTool

> 아키텍처 다이어그램(`docs/figma/artifacts/SpringAI_Architecture_Target_Pipeline.html`) 2.2 "코드 생성 Tool 오케스트레이션 flow" 중
> **"3단계 · 화면 생성 Tool (셋 중 하나 선택)"** 노드에 대한 상세 비교 문서. 코드를 직접 확인해 작성했으며, 추측 없이 파일 경로 · 라인 번호를 근거로 남긴다.
>
> **2026-08-27 정정:** 최초 작성 시 CRUD 쪽 진입점을 `CrudPromptBuilderTool`로 표기했으나, 이 클래스는 **MCP에 등록되지 않는 legacy 코드**임을
> 확인했다(§0 참고). 실제 등록된 CRUD 진입점은 `CrudGenerationTool`이며, 이번 갱신에서 문서 전체를 이 클래스 기준으로 정정했다.

---

## 0. CrudGenerationTool이 실제 CRUD 진입점이다 — `CrudPromptBuilderTool`은 죽은 코드

`tools/CrudPromptBuilderTool.java`에도 `buildFullCrudPrompt`/`buildMasterDetailPrompt`/`buildBoardFeature` 등
17개의 `@Tool` 메서드가 있어, 처음에는 이 클래스가 세 Tool의 "호환용 진입점"인 줄 알았다. 하지만 실제로는
**MCP에 전혀 등록되지 않는 클래스**다:

- `@Component`/`@Service` 등 스프링 빈 어노테이션이 없다.
- `McpConfig.allToolCallbacks(...)`(`config/McpConfig.java` L69-105)의 파라미터·`toolObjects(...)` 어디에도 등장하지 않는다.
- `@McpToolRisk`도 없다 — `McpConfig`는 등록되는 모든 Tool 메서드에 이 어노테이션이 없으면 기동 자체를 즉시 차단하도록 되어 있어, 애초에 등록될 수 없는 구조다.
- `CrudPromptBuilderToolTest.java`(L165)에서 `new CrudPromptBuilderTool(...)`로 직접 생성하는 순수 단위 테스트에서만 참조된다.

실제로 살아있는 CRUD 진입점은 **`tools/generation/CrudGenerationTool.java`**다(`@Component`, `@McpToolRisk(FILE_WRITE)` 있음, `McpConfig.java` L83에서 `crudGenerationTool`로 주입됨). `CrudPromptBuilderTool.buildFullCrudPrompt()`와 똑같이 `CrudGenerationMcpFacade`로 위임하므로 **동작 로직 자체는 동일**하지만, MCP 클라이언트가 실제로 호출할 수 있는 건 `CrudGenerationTool` 쪽뿐이다.

`MasterDetailGenerationTool`/`BoardGenerationTool`은 각자 자기 메서드 하나씩만 갖는 전용 클래스이고, `CrudPromptBuilderTool` 안에 있는 이들의 동명 메서드(`buildMasterDetailPrompt`/`buildBoardFeature`)는 **호출될 수 없는 사본**이다. 즉 애초에 우려했던 "같은 기능에 진입점이 두 갈래"라는 상황은 실제로는 존재하지 않는다 — 등록된 경로는 셋 다 각각 하나뿐이다.

아키텍처 다이어그램의 "셋 중 하나 선택"은 이 세 가지 **기능**(단일 CRUD / 게시판 / 마스터-디테일) 중 하나를 고른다는 뜻이며, 각 기능의 실제 호출 지점은 아래 표와 같다.

---

## 1. 한눈에 보는 비교표

| | **CrudGenerationTool**<br>`.buildFullCrudPrompt()` | **MasterDetailGenerationTool**<br>`.buildMasterDetailPrompt()` | **BoardGenerationTool**<br>`.buildBoardFeature()` |
|---|---|---|---|
| 처리 대상 | 테이블 **1개** | 테이블 **2개**(master/detail) | 테이블 역할 슬롯 **5개**(게시글/마스터/사용권한/파일/파일상세) |
| 테이블명 지정 방식 | 필수 파라미터, 완전 자유 | 필수 파라미터 2개, 완전 자유 | `@Nullable` 파라미터 5개 — 미지정 시 `LETTN*` 기본값으로 대체 |
| 테이블명 하드코딩 여부 | 없음 | 없음 | 없음 (기본값 fallback만 존재) |
| `llmProvider`(auto/claude) 파라미터 | 있음 | 있음 | **없음** |
| 분기 서비스 | `CrudGenerationDispatchService` | `MasterDetailGenerationDispatchService`(CRUD와 별개 구현체) | 없음 — 항상 `BoardGenerationPipelineService` |
| 반환값 구조 | `CrudToolResult.Orchestrated` / `Prompted` | `MasterDetailToolResult.Orchestrated` / `Prompted` | `BoardOrchestrationResult` 단일 고정 |
| 생성 화면 수 | 4개(List/Detail/Regist/Updt) | **마스터만** 4개, 디테일은 화면 없음 | 4개 + 논리삭제/조회수 증가/마스터명 조회 로직 |
| 총 산출 파일 수(JSP 기준) | 11개 | 14개(공통 백엔드 10 + 마스터 화면 4) | 12개(reuse) / 17개(create) |

---

## 2. CrudGenerationTool — `buildFullCrudPrompt()`

- 파일: `tools/generation/CrudGenerationTool.java` L72(메서드 시작), `@Tool` description L19
- 파라미터 17개, `database`/`tableName`/`domain`/`packageName`/`outputPath`/`llmProvider` 필수
- 호출 체인: `CrudGenerationMcpFacade.buildFullCrudPrompt` → `DispatchCrudGenerationUseCase` → **`CrudGenerationDispatchService`**(`service/generation/crud/CrudGenerationDispatchService.java` L15-25)가 `command.isAuto()`로 분기
  - `auto`(기본값) → `GenerateCrudProjectUseCase` — Tool이 파일을 직접 디스크에 저장
  - `claude` → `BuildCrudPromptUseCase` — 프롬프트 텍스트만 반환, Claude가 후속 Tool 호출
- 반환: `CrudGenerationResultFormatter.format`이 `Orchestrated`/`Prompted` 중 하나로 포맷
- 세부 스펙(플레이스홀더 계산, 타입 변환표, 공통코드 자동 탐지 등)은 `CrudPromptBuilderTool_기능및역할_상세설명.md`에 정리돼 있다 — 파일명은 legacy 클래스명을 그대로 쓰지만, 내용은 `CrudGenerationTool`(실제 등록된 클래스)이 위임하는 `CrudGenerationMcpFacade`/`CrudPromptBuilderService` 등 공유 로직을 설명하므로 여전히 유효하다.

---

## 3. MasterDetailGenerationTool — `buildMasterDetailPrompt()`

- 파일: `tools/generation/MasterDetailGenerationTool.java` L15-69
- 시그니처(L58-64): `database`, `masterTable`, `detailTable`, `domain`, `packageName`, `outputPath` 필수 + `viewType`/`egovVersion`/`llmProvider`/`layoutMode`/`layoutView`/`breadcrumbView`/`designReferenceId`/`screenSpecificationId` 선택(`@Nullable`)
- description의 `masterTable`/`detailTable` 예시(L22-28)는 `LETTNEMPLYRINFO`/`LETTNEMPLYRATTRBINFO` — **예시일 뿐**, 실제로는 `MasterDetailGenerationPlanner.plan()`(L38-39)이 `schemaQueryService.fetchColumns(database, command.masterTable())` / `...detailTable()`을 호출해 사용자가 넘긴 이름 그대로 `INFORMATION_SCHEMA.COLUMNS`를 조회한다(`CrudSchemaQueryService.fetchColumns` L38-50). 하드코딩 없음.
- `llmProvider` 분기: **`MasterDetailGenerationDispatchService.execute()`**(L22-25)가 `isAuto()`로 분기 — CRUD의 `CrudGenerationDispatchService`와는 **별개의 구현체**(클래스 주석 L10-12: "llmProvider 분기가 존재하는 유일한 지점")
  - `auto` → `GenerateMasterDetailProjectUseCase` (결정론적, `Orchestrated`)
  - 그 외(`claude` 등) → `BuildMasterDetailPromptUseCase` (`Prompted`)
  - `isAuto()` 기본값: `MasterDetailGenerationCommand`(L28-30)에서 null/blank면 `"auto"`로 정규화
- 산출물(`MasterDetailLayerDefinition.java`):
  - 공통 백엔드 10개 — masterVo/detailVo/masterMapper/detailMapper/masterMapperXml/detailMapperXml/service/serviceImpl/controller/validationHandler
  - **마스터 전용** 화면 4개(List/Detail/Regist/Updt) — 총 14개
  - **디테일 테이블은 자체 화면이 없다** — VO/Mapper/MapperXml 3개 파일만 생성
  - 1:N 관계는 `jsp-detail.jsp.ftl`(L20-38)에서 마스터 상세 화면 하단에 "디테일 목록" 테이블을 임베드하는 방식으로 표현
- 반환: `MasterDetailToolResult`(sealed interface, L11-16) — `Orchestrated`/`Prompted` 2종, CrudGenerationTool과 동일한 패턴

**결론: MasterDetailGenerationTool은 "테이블 슬롯이 1개→2개로 늘어난 CrudGenerationTool의 확장판"에 가깝다** — llmProvider 분기와 Orchestrated/Prompted 반환 구조를 그대로 계승.

---

## 4. BoardGenerationTool — `buildBoardFeature()`

- 파일: `tools/generation/BoardGenerationTool.java` L19, `@McpToolRisk(McpToolRiskLevel.FILE_WRITE)`(L18) 위험도 태그 명시
- 시그니처(L56-65): `database`/`domain`/`packageName`/`outputPath` 필수 + `mainTable`/`masterTable`/`useTable`/`fileTable`/`fileDetailTable`(전부 `@Nullable`, L57-59) 등 20개 파라미터
- description(L29-33)에 "게시글 테이블 (**기본값**: LETTNBBS)" 식으로 **기본값**이라 명시 — 하드코딩된 고정 테이블이 아니다.
- 실제 흐름:
  1. `BoardGenerationMcpFacade.buildBoardFeature`(L27-61) → `BoardGenerationCommand` 생성 — compact constructor(L34-40)는 mainTable~fileDetailTable에 별도 기본값 로직을 두지 않고 null 그대로 다음 단계로 전달
  2. `BoardGenerationPlanner.plan()`(L56-57) → **`BoardTableSetResolver.resolve()`**(`service/BoardTableSetResolver.java` L16-33)가 "명시 테이블 우선, 미지정 시 `LETTN*`으로 대체"(`PREFIX="LETTN"` + `"BBS"`/`"BBSMASTER"`/...) — 사용자가 값을 넘기면 그 값을 그대로 채택(`blank(explicit) ? fallback : explicit.trim()`), `optional()`은 `schemaQueryService.tableExists()`로 실제 DB 존재까지 검증
  3. 해석된 테이블명은 `BoardGenerationPlanner`(L60-61) → `BoardSchemaService.fetchColumns` → `CrudSchemaQueryService.fetchColumns`(L38)를 거쳐 실제 JDBC로 컬럼 조회 — 사용자가 `MY_POST` 등 임의 이름을 넘기면 그 테이블 기준으로 생성된다.
- **`llmProvider` 파라미터 자체가 없다**: `BoardGenerationCommand`(L13-15) 주석 — "llmProvider 분기가 없다 — 항상 결정론적 오케스트레이션". `GenerateBoardProjectUseCase` → `BoardProjectGenerationService`(L23-28)는 분기 없이 항상 `BoardGenerationPipelineService`만 실행(L9 주석: "모든 실행은 Board Pipeline을 통해 수행"). (`llmProvider` 도입 검토는 `BoardGenerationTool_llmProvider_도입_검토.md` 참고)
- 기능: 목록/상세/등록/수정 + **논리삭제 + 조회수 증가 + 마스터명 조회**까지 포함(단순 CRUD보다 넓음)
- 규모: `LETTNBBS`/`LETTNBBSMASTER`/`LETTNBBSUSE`/`LETTNFILE`/`LETTNFILEDETAIL` 최대 5개 테이블 조인 — **단, 이 5개는 테이블 "이름"이 아니라 "역할(role) 구조"가 고정된 것**. 실제 테이블명은 사용자가 자유롭게 지정 가능.
- 반환: `BoardGenerationResultFormatter.format`(L10) — 분기 없이 항상 `BoardOrchestrationResult`(PK 방어: `BBS_ID` + `NTT_ID` 등 게시판 특화 정보 포함) 하나만 반환

**결론: BoardGenerationTool은 세 Tool 중 유일하게 llmProvider 분기가 없는, 처음부터 결정론적 파이프라인으로 설계된 이질적 구조다.**

---

## 5. 자주 헷갈리는 포인트 — "테이블명이 고정이다" 오해 정정

`LETTNBBS`, `LETTNBBSMASTER`, `LETTNBBSUSE`, `LETTNFILE`, `LETTNFILEDETAIL`, `LETTNEMPLYRINFO`, `LETTNEMPLYRATTRBINFO` 등은
**전부 이 프로젝트가 다루는 eGovFrame 샘플 DB의 예시/기본값**이며, Tool 로직에 하드코딩된 제약이 아니다.

- 실제 Java 로직(조건문·쿼리 바인딩)에 리터럴로 박혀 있는 곳은 `BoardTableSetResolver`의 `PREFIX="LETTN"` 조합뿐이고, 이는 **파라미터 미지정 시에만** 쓰이는 fallback이다.
- 그 외 "LETTNBBS" 류 문자열은 전부 `@Tool` description의 JavaDoc 예시 문구, 모델 필드 주석, 테스트 리소스에만 등장한다.
- 고정되어 있는 것은 **테이블 이름이 아니라 구조**다:
  - CrudGenerationTool = 역할 슬롯 1개(자유)
  - MasterDetailGenerationTool = 역할 슬롯 2개(자유, master/detail)
  - BoardGenerationTool = 역할 슬롯 5개(자유, 게시글/마스터/사용권한/파일/파일상세)

---

## 6. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `tools/generation/CrudGenerationTool.java` | **실제 등록된** CRUD 전용 진입점(`@Component`, MCP 노출) |
| `tools/generation/MasterDetailGenerationTool.java` | 마스터-디테일 전용 진입점 |
| `tools/generation/BoardGenerationTool.java` | 게시판 전용 진입점 |
| `tools/CrudPromptBuilderTool.java` | ⚠ **MCP에 등록되지 않는 legacy 코드**. `buildFullCrudPrompt`/`buildMasterDetailPrompt`/`buildBoardFeature` 등 17개 메서드가 있지만 전부 호출 불가. 삭제는 별도 작업으로 보류됨 |
| `service/generation/crud/CrudGenerationDispatchService.java` | CRUD auto/claude 분기 |
| `.../MasterDetailGenerationDispatchService.java` | 마스터-디테일 auto/claude 분기(CRUD와 별개) |
| `.../BoardProjectGenerationService.java` | 게시판 — 분기 없이 항상 파이프라인 실행 |
| `service/BoardTableSetResolver.java` | 게시판 테이블 역할 슬롯 → 실제 테이블명 해석(명시값 우선, 미지정 시 `LETTN*`) |
| `service/CrudSchemaQueryService.java` | 공통 스키마 조회(`INFORMATION_SCHEMA.COLUMNS`) — 세 Tool 모두 이 서비스 경유 |
