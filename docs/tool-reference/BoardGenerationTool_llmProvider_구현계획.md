# BoardGenerationTool `llmProvider` 도입 구현계획

> [`BoardGenerationTool_llmProvider_도입_검토.md`](./BoardGenerationTool_llmProvider_도입_검토.md)(최초 검토 + 2026-08-28
> 재확인)를 바탕으로 작성한 구현명세서 + 구현목록. CRUD의 `CrudGenerationCommand`/`CrudToolResult`/
> `CrudGenerationDispatchService`/`CrudPromptGenerationService`/`CrudGenerationMcpFacade` 구조를
> 코드로 직접 확인해 Board판을 1:1로 대응시키는 방식으로 설계했다.
>
> **현재 상태**: Phase 1~4 전부 완료. Phase 3 재확인 중 `PipelineGenerationMcpFacadeTest`의
> 인자 개수 오류(21개 → 22개 필요) 1건을 발견해 수정했다. `./gradlew test` 전체 통과(0 failures)
> 확인 완료.

---

## 1. 배경 및 목적

`BoardGenerationTool.buildBoardFeature()`는 지금 `llmProvider` 파라미터가 없어 항상 결정론적
파이프라인(`BoardGenerationPipelineService`)만 실행한다. CRUD/MasterDetail처럼 `llmProvider="claude"`를
선택해 Claude에게 프롬프트만 넘기고 직접 소스를 작성하게 하는 경로를 게시판에도 추가한다.

### 설계 원칙
- **기존 계약 보존**: `BoardGenerationCommand`는 필드를 "추가"만 하고, 기존 15-인자 생성자는
  compat 생성자로 남긴다(레포의 compat 생성자 누적 패턴).
- **CRUD와 동일 아키텍처**: `DispatchXxxUseCase`(분기 전담) / `BuildXxxPromptUseCase`(claude 경로) /
  `GenerateXxxProjectUseCase`(auto 경로, 기존 그대로) 3분할 구조를 그대로 따른다. Tool/Facade는
  분기 로직을 갖지 않는다(`CrudGenerationDispatchService`의 "ORT-PRN-001" 원칙과 동일).
- **auto 경로 무변경**: `GenerateBoardProjectUseCase`(`BoardProjectGenerationService`)와
  `BoardGenerationPipelineService`는 이번 변경에서 건드리지 않는다.

---

## 2. 목표 아키텍처

```
BoardGenerationTool.buildBoardFeature(..., llmProvider, ...)        [수정: 파라미터 추가]
        ↓
BoardGenerationMcpFacade.buildBoardFeature(...)                     [수정: Dispatch로 교체]
  └─ BoardGenerationCommand로 변환(llmProvider 포함)
        ↓
BoardGenerationDispatchService.execute(command)                     [신규 — 분기 전담]
  └─ command.isAuto()
       ├─ true  → GenerateBoardProjectUseCase.execute(command)       [기존 그대로, 무변경]
       │            → BoardToolResult.Orchestrated
       └─ false → BuildBoardPromptUseCase.execute(command)           [신규]
                    → BoardToolResult.Prompted
        ↓
BoardGenerationResultFormatter.format(BoardToolResult)               [수정: sealed 타입 분기]
  ├─ Orchestrated → 기존 auto 응답 포맷 그대로
  └─ Prompted     → Prompt 문자열 그대로 반환
```

`BuildBoardPromptUseCase`의 실제 구현체 `BoardPromptGenerationService`는 auto 경로의
`BoardGenerationPlanner`와 동일하게 테이블 세트 해석 → 스키마 조회 → 프로그램 메타데이터 해석 →
화면명세(design context) 해석까지 수행한 뒤, 그 정보를 텍스트 프롬프트로 조립한다(파일 저장은
하지 않음).

---

## 3. 데이터 모델 설계

### 3.1 `BoardGenerationCommand` (`service/generation/board/BoardGenerationCommand.java`)

`llmProvider` 필드 1개 추가. 기존 15-인자 생성자는 compat으로 남기고 `"auto"`로 기본값 채움,
새 16-인자 생성자가 canonical이 된다. `isAuto()` 메서드 추가(`CrudGenerationCommand`와 동일 패턴).

### 3.2 `BoardToolResult` (신규 sealed interface, `service/generation/board/BoardToolResult.java`)

```java
public sealed interface BoardToolResult {
    record Orchestrated(BoardOrchestrationResult result) implements BoardToolResult {}
    record Prompted(PromptGenerationResult result) implements BoardToolResult {}
}
```
`CrudToolResult`와 완전히 동일한 모양. 새 타입이므로 기존 호출부 영향 없음.

### 3.3 신규 API 인터페이스 (`service/generation/api/`)

- `BuildBoardPromptUseCase`: `PromptGenerationResult execute(BoardGenerationCommand)`
- `DispatchBoardGenerationUseCase`: `BoardToolResult execute(BoardGenerationCommand)`

---

## 4. 핵심 로직 설계

### 4.1 `BoardGenerationDispatchService` (신규)

```java
@Service
@RequiredArgsConstructor
public class BoardGenerationDispatchService implements DispatchBoardGenerationUseCase {
    private final GenerateBoardProjectUseCase generateBoardProjectUseCase;
    private final BuildBoardPromptUseCase buildBoardPromptUseCase;

    public BoardToolResult execute(BoardGenerationCommand command) {
        return command.isAuto()
                ? new BoardToolResult.Orchestrated(generateBoardProjectUseCase.execute(command))
                : new BoardToolResult.Prompted(buildBoardPromptUseCase.execute(command));
    }
}
```

### 4.2 `BoardPromptGenerationService` (신규 — 가장 큰 리스크였던 지점)

`BoardGenerationPlanner.plan()`이 하는 것과 동일한 순서로 정보를 모은다:

1. `BoardTableSetResolver.resolve()` — main/master/use/file/fileDetail 5개 테이블명 확정
2. `BoardSchemaService.fetchBoardSchemas()` — 5개 테이블 컬럼 스키마 조회(main/master 없으면 예외)
3. `BoardProgramMetadataService.resolve()` — 프로그램 메타데이터 해석. `metadata.blocksGeneration()`이면
   `IllegalArgumentException`으로 즉시 실패(CRUD claude 경로처럼 구조화된 Plan/PlanFailure 없이
   예외로 신호 — claude 경로는 원래 단순 실패 방식을 씀)
4. `GenerationDesignContextService.resolve()` — 화면명세(`ScreenSpecification`) 해석(없으면 null)

그 다음 텍스트 프롬프트를 조립한다:

- 화면명세가 있으면 `ScreenSpecificationPromptFormatter.format()` + `componentGeometry` KRDS
  가드레일 문구(CRUD/MasterDetail과 동일 문구 재사용)
- **테이블 구성**: main/master/use/file/fileDetail 5개 역할과 실제 테이블명
- **게시판 업무 규칙**(검토 문서가 지목한 리스크 지점, §6에서 다시 다룸):
  - 복합 PK: `BBS_ID + NTT_ID`(고정 사실 — `BoardGenerationResultFormatter`의 기존
    "PK 방어: BBS_ID + NTT_ID 적용" 문구와 동일 근거)
  - 논리삭제/조회수 증가: **컬럼명을 하드코딩하지 않는다.** 대신 아래 스키마 덤프의
    `COLUMN_COMMENT`를 보고 Claude가 직접 식별하도록 지시만 준다(사람이 실제 DB를 보고 판단하는
    것과 동일한 방식 — 테이블마다 컬럼명이 다를 수 있어 하드코딩은 오히려 틀릴 위험이 큼)
  - 마스터명 조회: master 테이블 조인 지시
- **프로그램 메타데이터**: 파일명/URL/한글명/기본 bbsId
- **기본 정보**: DB/도메인/패키지/출력경로/egovVersion/viewType/layoutMode
- **스키마 덤프**: 5개 테이블 각각의 컬럼(이름/타입/NULL/KEY/COMMENT)
- **생성 대상 안내**: 목록/상세/등록/수정 + 레이어 전체를 지정 viewType으로 작성하라는 지시

### 4.3 `BoardGenerationResultFormatter` 수정

`format(BoardOrchestrationResult)` → `format(BoardToolResult)`로 시그니처 변경, 기존 auto 포맷
로직은 `formatOrchestrated()`로 이름만 바꿔 그대로 보존. `Prompted`는 프롬프트 문자열 그대로 반환
(CRUD와 동일).

### 4.4 `BoardGenerationMcpFacade`/`BoardGenerationTool` 수정

- Facade 생성자 의존성을 `GenerateBoardProjectUseCase` → `DispatchBoardGenerationUseCase`로 교체
- `buildBoardFeature(...)` 파라미터 목록에 `llmProvider`를 `outputPath` 바로 뒤에 추가(CRUD 관례와
  동일 위치)
- `BoardGenerationTool`의 `@Tool` 설명에 `llmProvider` 옵션 설명 추가

---

## 5. 신규/수정 파일 목록

| 파일 | 변경 유형 | 완료 여부 |
|---|---|---|
| `service/generation/board/BoardGenerationCommand.java` | 수정 | 완료 |
| `service/generation/board/BoardToolResult.java` | 신규 | 완료 |
| `service/generation/api/BuildBoardPromptUseCase.java` | 신규 | 완료 |
| `service/generation/api/DispatchBoardGenerationUseCase.java` | 신규 | 완료 |
| `service/generation/board/BoardGenerationDispatchService.java` | 신규 | 완료 |
| `service/generation/board/BoardPromptGenerationService.java` | 신규 | 완료 |
| `service/generation/mcp/BoardGenerationResultFormatter.java` | 수정 | 완료 |
| `service/generation/mcp/BoardGenerationMcpFacade.java` | 수정 | 완료 |
| `tools/generation/BoardGenerationTool.java` | 수정 | 완료 |
| `test/.../tools/generation/BoardGenerationToolTest.java` | 수정 | 완료 |
| `test/.../service/generation/mcp/PipelineGenerationMcpFacadeTest.java` | 수정 | 완료(재확인 중 인자 개수 오류 1건 발견·수정) |
| `src/test/resources/mcp/tool-definitions-baseline.json` | 재생성 | 완료 |
| `test/.../service/generation/board/BoardPromptGenerationServiceTest.java` | 신규 | 완료(3건) |
| `test/.../service/generation/board/BoardGenerationDispatchServiceTest.java` | 신규 | 완료(2건) |

---

## 6. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 논리삭제/조회수 컬럼명을 하드코딩하면 실제 스키마와 다를 때 틀림 | Claude가 잘못된 컬럼에 UPDATE를 씀 | §4.2처럼 컬럼명을 하드코딩하지 않고 스키마 덤프 + 식별 지시로 대체(원 검토가 지적한 리스크를 줄이는 설계 선택, 완전 제거는 아님) |
| `BoardProgramMetadata.blocksGeneration()`(AMBIGUOUS/INVALID_BBS_ID) 처리 | auto는 구조화된 실패로 처리하는데 claude 경로는 예외만 던짐 | 의도된 단순화 — CRUD claude 경로도 구조화된 Plan/PlanFailure 없이 예외 방식을 씀(선례 확인됨). 사용자 경험 차이는 있으나 기존 CRUD 패턴과 일관성 유지 |
| 5개 테이블 조인·복합 PK를 Claude가 프롬프트만 보고 정확히 구현 못할 가능성 | 생성 코드 품질 저하 | 1차 구현 범위에서는 텍스트 지시로 최대한 명시(§4.2). 검증은 사람이 실제 결과물을 확인하는 것으로 대체(자동 검증 도구 없음, §7 제외범위) |
| MCP 계약(`buildBoardFeature` 입력 스키마) 변경 | `tool-definitions-baseline.json` 불일치로 테스트 실패 | baseline 삭제 → `McpToolDefinitionSnapshotTest` 재실행으로 재생성(기존 절차) |
| `ScreenSpecificationPromptFormatter`/`componentGeometry` 재사용 | 없음(이미 공용 서비스로 검증됨) | 그대로 재사용, 신규 리스크 없음 |
| auto 경로 회귀 | `GenerateBoardProjectUseCase`/`BoardGenerationPipelineService` 무변경이므로 낮음 | `BoardGenerationDispatchService.execute()`가 `isAuto()`일 때 기존 흐름을 그대로 위임하는지 테스트로 확인 |

---

## 7. 1차 구현 제외 범위 (2차 이후)

- 생성된 게시판 소스의 업무 로직(복합 PK/논리삭제/조회수) 정확도를 자동 검증하는 도구
- Board 전용 `.ftl` 템플릿이나 CSS 자동주입(이번 범위와 무관, `Figma_색상_CSS자동주입_auto경로_검토.md` 참고)
- `BoardProgramMetadata.blocksGeneration()`을 CRUD처럼 구조화된 결과 타입으로 표현하는 리팩터링

---

## 8. 단계별 구현목록

### Phase 1 — 데이터 모델·인터페이스 (완료)
| 순서 | 작업 | 상태 |
|---|---|---|
| 1 | `BoardGenerationCommand`에 `llmProvider` + compat 생성자 + `isAuto()` | 완료 |
| 2 | `BoardToolResult` sealed interface 신설 | 완료 |
| 3 | `BuildBoardPromptUseCase`/`DispatchBoardGenerationUseCase` 인터페이스 신설 | 완료 |

### Phase 2 — 분기·프롬프트 로직 (완료)
| 순서 | 작업 | 상태 |
|---|---|---|
| 4 | `BoardGenerationDispatchService` 신설 | 완료 |
| 5 | `BoardPromptGenerationService` 신설(§4.2) | 완료 |

### Phase 3 — Formatter·Facade·Tool (완료)
| 순서 | 작업 | 상태 |
|---|---|---|
| 6 | `BoardGenerationResultFormatter`를 `BoardToolResult` 기준으로 수정 | 완료 |
| 7 | `BoardGenerationMcpFacade`를 `DispatchBoardGenerationUseCase` 사용하도록 수정 | 완료 |
| 8 | `BoardGenerationTool`에 `llmProvider` 파라미터 + 설명 추가 | 완료 |
| 9 | 기존 테스트(`BoardGenerationToolTest`, `PipelineGenerationMcpFacadeTest`) 시그니처 보정 | 완료(재확인 필요) |

### Phase 4 — 계약·신규 테스트 (완료)
| 순서 | 작업 | 상태 |
|---|---|---|
| 10 | `./gradlew compileTestJava`로 나머지 컴파일 오류 확인 | 완료 |
| 11 | `tool-definitions-baseline.json` 삭제 → `McpToolDefinitionSnapshotTest` 재실행으로 재생성 | 완료 |
| 12 | `BoardPromptGenerationService` 신규 테스트: 정상 케이스(테이블 5개 스키마 반영), `blocksGeneration()` 시 예외, 화면명세 있을 때 `componentGeometry` 블록 포함 여부 | 완료(3건) |
| 13 | `BoardGenerationDispatchService` 신규 테스트: auto/claude 분기 각각 올바른 UseCase 호출 확인 | 완료(2건) |
| 14 | `./gradlew test` 전체 통과 확인 | 완료(0 failures) |

---

## 9. 검증 방법

1. `./gradlew build` — 전체 테스트 통과 확인
2. `llmProvider` 생략(또는 `"auto"`) 호출 → 기존과 동일한 파일 생성·응답 포맷 확인(회귀 없음)
3. `llmProvider="claude"` 호출 → 프롬프트 문자열에 5개 테이블 스키마, 복합 PK 규칙, 프로그램
   메타데이터, (화면명세 있으면) `componentGeometry` 블록이 포함되는지 확인
4. `BoardProgramMetadata`가 `AMBIGUOUS`/`INVALID_BBS_ID`인 케이스로 claude 경로 호출 →
   `IllegalArgumentException`이 명확한 메시지와 함께 발생하는지 확인

---

## 10. 관련 문서

- [BoardGenerationTool_llmProvider_도입_검토.md](./BoardGenerationTool_llmProvider_도입_검토.md) — 이 구현계획의 기반이 된 검토 원문(최초 + 재확인)
- [Figma_fills_strokes_구현계획.md](./Figma_fills_strokes_구현계획.md) — compat 생성자 패턴 근거
- [Figma_픽셀재현_claude경로_구현계획.md](./Figma_픽셀재현_claude경로_구현계획.md) — `componentGeometry`/KRDS 가드레일 재사용 근거
