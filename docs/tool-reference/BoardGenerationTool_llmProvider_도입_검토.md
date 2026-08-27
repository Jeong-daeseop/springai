# BoardGenerationTool `llmProvider` 도입 검토

> 2026-08-27, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> 조사 과정에서 별도로 확인된 `CrudPromptBuilderTool` 미등록 문제도 함께 기록한다.

---

## 1. 배경

`CrudPromptBuilderTool.buildFullCrudPrompt()`와 `MasterDetailGenerationTool.buildMasterDetailPrompt()`는
`llmProvider`(`auto`/`claude`) 파라미터로 "서버가 직접 파일을 저장할지" vs "Claude에게 프롬프트만 넘길지"를
분기한다. 반면 `BoardGenerationTool.buildBoardFeature()`는 이 파라미터 자체가 없고 항상 결정론적
파이프라인(`BoardGenerationPipelineService`)만 실행한다. "BoardGenerationTool도 같은 스위치를 받게 하면
어떻게 될지" 검토를 요청받아 코드를 확인했다.

---

## 2. 필요한 변경 범위 (코드 확인 기준)

| 변경 대상 | 현재 상태 | 필요한 작업 |
|---|---|---|
| `BoardGenerationCommand.java`(L17-40) | 15개 필드, `llmProvider` 없음. Javadoc에 "분기가 없다"고 명시 | `llmProvider` 필드 추가 + compact constructor에서 `null/blank → "auto"` 정규화 + `isAuto()` 메서드 |
| 결과 타입 | `BoardOrchestrationResult` 단일 반환 | `BoardToolResult` sealed interface(`Orchestrated`/`Prompted` record 2종) 신설 — `CrudToolResult`/`MasterDetailToolResult`와 동일 패턴 |
| `BoardGenerationResultFormatter`(L10) | `format(BoardOrchestrationResult)` 단일 시그니처 | `Prompted` 케이스용 포맷터 추가 신설 |
| **claude 경로용 "프롬프트 조립" 서비스** | **존재하지 않음** | `BuildBoardPromptUseCase` + 구현 서비스를 **완전히 새로 개발**해야 함 |
| `BoardGenerationMcpFacade.buildBoardFeature`(L27-61) | 무조건 `GenerateBoardProjectUseCase` 직행 | `DispatchBoardGenerationUseCase`(신규 인터페이스) → `BoardGenerationDispatchService`(신규, `isAuto()` 분기)로 교체 |
| `BoardGenerationTool.buildBoardFeature` | `llmProvider` 파라미터 없음, 오버로드 없음 | 파라미터 추가(`@Nullable`, 기본 `"auto"`로 하위호환 유지) |
| MCP 계약 스냅샷 테스트 | `McpToolDefinitionSnapshotTest`가 `tool-definitions-baseline.json`과 완전 일치 비교(`assertThat(current).isEqualTo(stored)`) | 파라미터 추가만으로도 스키마가 달라져 **반드시 깨짐** — baseline 의도적 삭제 후 재생성 필요 |

---

## 3. 가장 큰 리스크 — "프롬프트 조립 로직이 아예 없다"

CRUD/MasterDetail의 `claude` 경로가 존재할 수 있었던 이유는 애초에 `CrudPromptBuilderService`(claude 경로
프롬프트 조립)가 auto/claude 분기 도입 **이전부터 이미 있었고**, 그걸 그대로 재활용했기 때문이다. Board는
처음부터 `BoardGenerationPipelineService` 결정론적 경로만 존재해서 대응하는 서비스가 없다. 즉 이건
"파라미터 하나 추가"가 아니라 **게시판용 프롬프트 빌더를 처음부터 설계·개발**하는 작업이다.

그리고 그 프롬프트가 담아야 할 내용이 CRUD보다 훨씬 복잡하다 — 5개 테이블 역할 슬롯 조인,
`BBS_ID + NTT_ID` 복합 PK 방어, 논리삭제, 조회수 증가, 마스터명 조회 같은 업무 로직을 Claude가 프롬프트만
보고 직접 작성해야 하는데, 단일/이중 테이블 CRUD보다 실수 여지가 훨씬 크다.

---

## 4. 설계 의도 — 오버사이트가 아니라 의도된 제약으로 보임

`BoardGenerationCommand` Javadoc에 "llmProvider 분기가 없다 — 항상 결정론적 오케스트레이션"이라고
**명시적으로** 적혀 있다. 이건 누락이 아니라 "게시판처럼 다중 테이블 업무 로직이 얽힌 기능은 자유 생성에
맡기지 않겠다"는 의도된 설계 결정으로 읽힌다.

---

## 5. 결론

기술적으로는 CRUD/MasterDetail과 동일한 패턴(Command 필드 추가 + sealed Result + Dispatch 서비스)으로
구현 가능하지만, **신규 프롬프트 빌더 개발 + MCP 계약 테스트 재생성**이 필요한 중간 규모 작업이고,
무엇보다 "왜 지금까지 의도적으로 막아뒀는지"에 대한 답 없이 진행하면 설계 이탈이 될 수 있다. 진행하려면
먼저 "claude 경로가 실제로 필요한 시나리오(토큰 절감/커스터마이징 요구)가 뭔지"부터 확인을 권한다.

---

## 6. ⚠ 별도 발견 — `CrudPromptBuilderTool`은 실제로 MCP에 등록되지 않는다

이번 조사 중에 `CrudPromptBuilderTool`(`tools/CrudPromptBuilderTool.java`)이 **실제로는 MCP에 등록되지
않은 클래스**라는 걸 확인했다.

- `@Component`/`@Service` 등 스프링 빈 어노테이션이 없다(`CrudPromptBuilderTool.java` L14-16: `@Slf4j`,
  `@RequiredArgsConstructor`만 존재).
- `McpConfig.allToolCallbacks(...)`(`config/McpConfig.java` L69-105)의 파라미터 목록·`toolObjects(...)`
  호출 어디에도 `CrudPromptBuilderTool`이 등장하지 않는다(직접 grep 확인, hit 없음).
- src/main 전체에서 `new CrudPromptBuilderTool` 또는 이를 빈으로 주입받는 코드가 전혀 없다.
- 실제 등록된 CRUD 진입점은 `tools/generation/CrudGenerationTool.java`(`@Component`, `McpConfig.java`
  L83에서 주입)이며, `CrudPromptBuilderTool`과 **동일한** `CrudGenerationMcpFacade`로 위임하는 별도
  클래스다 — 즉 동작 로직(auto/claude 분기, 반환 구조 등)은 같지만, MCP 클라이언트가 실제로 호출할 수
  있는 건 `CrudGenerationTool` 쪽이다.
- `CrudPromptBuilderTool`은 순수 단위 테스트(`CrudPromptBuilderToolTest.java` L165, `new
  CrudPromptBuilderTool(...)`로 직접 생성, `@SpringBootTest` 아님)에서만 참조되며, 실행 중인 서버에서는
  호출될 수 없는 legacy 클래스로 보인다.

### 영향받는 기존 산출물

이 발견은 이번 세션에서 이미 작성·발행한 아래 문서/아티팩트의 전제를 정정해야 함을 의미한다(동작 로직
서술 자체는 맞지만, "이 클래스가 실제 MCP Tool"이라는 전제가 틀림):

- `docs/tool-reference/화면생성Tool_3종_비교분석.md`
- `docs/tool-reference/CrudPromptBuilderTool_기능및역할_상세설명.md`
- 아키텍처 다이어그램 아티팩트(`docs/figma/artifacts/SpringAI_Architecture_Target_Pipeline.html`) 2.2절

정정이 필요하면 `CrudPromptBuilderTool`을 `CrudGenerationTool` 기준으로 바꿔 다시 작성해야 한다.
