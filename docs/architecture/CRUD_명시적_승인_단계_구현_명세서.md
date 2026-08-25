# CRUD 생성 경로 명시적 승인 단계 구현 명세서

> 문서 버전: 1.1
> 작성일: 2026-08-25
> 기준 검토: [CRUD_명시적_승인_단계_도입_타당성_검토.md](./CRUD_명시적_승인_단계_도입_타당성_검토.md)
> 상태: **옵션 B(조건부 승인 게이트) 채택 확정(2026-08-25 사용자 승인)** — 옵션 A는 비채택, §3은
> 검토 근거 보존을 위해 참고용으로만 남김

## 1. 문서의 성격

이 문서는 타당성 검토서(위 링크)가 제시한 옵션 A(Thymeleaf식 3단계 구조)와 옵션 B(조건부 승인
게이트)를 각각 실제로 구현 가능한 수준까지 구체화한 명세였다. **두 옵션을 동시에 지원할 수는
없다는 판단에 따라 옵션 B가 최종 채택됐다** — 이유는 §5 비교표와 32번 문서의 실행기 분리
원칙에 따른 것으로, 검토서 §6 검토 의견과 동일하다. 옵션 A(§3)는 구현하지 않으며, 왜
검토했고 왜 채택하지 않았는지 근거를 남기기 위해 문서에는 그대로 둔다.

## 2. 공통 전제

두 옵션 모두 다음 기존 자산을 그대로 재사용한다(신규 개념 도입 최소화).

- `GenerationOperationStatus`(이미 `PREVIEW_READY`/`APPROVAL_REQUIRED`/`APPROVED`/`APPLYING`
  값이 정의되어 있음) — `CodeServiceGenerationExecutor`가 이 값들을 실제로 채우기 시작한다.
- `AI_GENERATION_OPERATION_AUDIT`(V16) — 승인 이전/이후 모든 시도 기록은 계속 이 테이블에 남긴다.
- `GenerationControlPlaneService`/`GenerationOperationsController`(14.1절) — 승인 대기 중인
  Operation도 기존 조회 API로 볼 수 있어야 한다(`sourceType=CRUD`).
- 실행기 분리 원칙(32번 문서) — 어느 옵션도 `ThymeleafProjectWorkflowService`/
  `ThymeleafProjectWorkflowTool`을 CRUD와 공유하거나 내부에서 호출하지 않는다(검토서 §5.1 근거).

## 3. 옵션 A — Thymeleaf식 3단계 구조 (비채택 · 참고용)

### 3.1 신규 테이블

```sql
CREATE TABLE AI_CRUD_GENERATION_PREVIEW (
    OPERATION_ID       VARCHAR(64)  NOT NULL,
    PREVIEW_HASH       VARCHAR(128) NOT NULL,
    RENDERED_FILES_JSON LONGTEXT    NOT NULL,
    STATUS             VARCHAR(32)  NOT NULL,  -- PREVIEW_READY | APPROVED | REJECTED
    CREATED_AT         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    APPROVED_AT        DATETIME(6),
    PRIMARY KEY (OPERATION_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```
`AI_CRUD_GENERATION_SNAPSHOT`(승인·적용 이후 이력)과 역할을 분리한다 — 이 테이블은 "아직 승인
안 된 대기 상태"만 담고, 승인·적용이 끝나면 기존처럼 `AI_CRUD_GENERATION_SNAPSHOT`에 기록된다.

### 3.2 서비스 계층 분리

`CrudGenerationApplicationService.execute()`를 3개 메서드로 분리한다.

```java
public interface PreviewCrudGenerationUseCase {
    CrudPreviewResult preview(CrudGenerationCommand command);
}

public interface ApproveCrudGenerationUseCase {
    CrudApprovalResult approve(String operationId, String expectedPreviewHash);
}

public interface ApplyCrudGenerationUseCase {
    CrudOrchestrationResult apply(String operationId);
}
```

- `preview()`: 기존 `CrudGenerationApplicationService.execute()`에서 Planner→PRE_WRITE→Renderer
  까지만 수행(`Executor.execute()` 호출 제외), 렌더링 결과의 SHA-256 hash를 계산해
  `AI_CRUD_GENERATION_PREVIEW`에 `PREVIEW_READY`로 저장. 파일 시스템에는 아무것도 쓰지 않는다.
- `approve()`: `ThymeleafProjectWorkflowService.approve()`와 동일한 판정(① 상태가
  `PREVIEW_READY`인지 ② hash 일치 ③ 렌더링 실패가 없는지) 후 상태를 `APPROVED`로 전이.
- `apply()`: `APPROVED` 상태인 Preview를 읽어 기존 `CodeServiceGenerationExecutor`의
  Write→POST_WRITE→VERIFY→HISTORY 단계를 그대로 실행하고, 성공 시 `AI_CRUD_GENERATION_SNAPSHOT`
  에 최종 기록.

### 3.3 신규 MCP Tool

`ThymeleafProjectWorkflowTool` 패턴을 그대로 준용해 신규 `CrudApprovalWorkflowTool` 추가.

```java
@Component
@RequiredArgsConstructor
public class CrudApprovalWorkflowTool {
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "CRUD 생성 결과를 파일로 쓰지 않고 미리보기와 hash만 생성합니다.")
    public String previewCrudGeneration(/* CrudGenerationCommand와 동일한 파라미터 */) { ... }

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "Preview hash가 일치하는 CRUD 생성 Operation을 명시적으로 승인합니다.")
    public String approveCrudGeneration(String operationId, String previewHash) { ... }

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "승인된 CRUD 생성 Operation을 실제로 파일에 적용합니다.")
    public String applyCrudGeneration(String operationId) { ... }
}
```
`McpConfig.allToolCallbacks`에 등록 필요(`MethodToolCallbackProvider.builder().toolObjects(...)`에
추가).

### 3.4 기존 Tool과의 관계 — 반드시 결정해야 하는 지점

`buildFullCrudPrompt`(및 `buildBoardFeature`, `buildMasterDetailPrompt`) 는 지금 `llmProvider="auto"`
일 때 **한 번 호출로 파일까지 저장**한다. 옵션 A를 그대로 넣으면 이 동작 자체가 사라지고
"auto"의 의미가 "즉시 저장"에서 "미리보기 생성"으로 바뀐다 — **기존 Tool 계약을 깨는 변경**이다.
아래 두 가지 중 하나를 반드시 선택해야 한다(이 문서는 선택하지 않는다).

- (A-1) 기존 `llmProvider="auto"` 자체의 의미를 바꾼다 — 기존 자동화·문서 전부 갱신 필요.
- (A-2) 새 `llmProvider` 값(예: `"auto-approved"`)을 추가해 기존 `"auto"` 동작은 그대로 두고,
  승인이 필요한 경우에만 새 값을 쓰게 한다 — 호환성은 지키지만 선택 부담이 호출자에게 넘어간다.

### 3.5 테스트 관점

- `preview()`가 파일 시스템에 아무것도 쓰지 않는지 검증(기존 `CodeServiceGenerationExecutorTest`류
  픽스처 재사용)
- `approve()`의 3개 판정 조건(상태·hash·실패없음) 각각의 실패 케이스
- `apply()`가 `APPROVED`가 아닌 Operation에 대해 거부하는지
- 동시에 같은 `operationId`로 `approve()` 두 번 호출 시 두 번째는 상태 불일치로 거부되는지
  (`ThymeleafProjectWorkflowService.approve()`의 `THYMELEAF_APPROVAL_REQUIRES_PREVIEW_READY`와
  동일한 방어)

## 4. 옵션 B — 조건부 승인 게이트 (채택 확정)

### 4.1 조건 정의

이번 세션에서 이미 구현한 `CrudGenerationPlanner`의 가드(V2_APPLY + `viewType=THYMELEAF` +
`llmProvider=auto` 조합일 때 `designReferenceId`/`screenSpecificationId` 필수)를 확장한다.

```yaml
# application.yaml (신규)
app:
  crud-generation:
    approval-required-tables: []   # 예: ["LETTNEMPLYRINFO", "LETTNBBSMASTER"]
    approval-required-for-all: false  # true면 viewType 무관하게 전체 강제
```

### 4.2 승인 산출물 재사용

새 "승인 클릭" 개념을 추가하지 않고, **이미 존재하는 `ScreenSpecification` 승인 흐름**
(`DesignReferenceTool.approveScreenSpecification()`, `AI_SCREEN_SPECIFICATION.SPEC_STATUS=APPROVED`)
을 승인 증거로 재사용한다 — 즉 "고위험으로 지정된 테이블은 승인된 화면명세 없이는 auto 생성을
거부한다"는 규칙이다.

### 4.3 구현 지점

`CrudGenerationPlanner`(기존 V2_APPLY 가드 바로 아래)에 조건 분기 추가:

```java
if (approvalPolicy.requiresApproval(tableName, viewType)
        && isBlank(options.designReferenceId()) && isBlank(options.screenSpecificationId())) {
    return CrudGenerationPlan.rejected(new CrudPlanFailure(
            CrudPlanFailure.Kind.MAPPING_BLOCKED,
            "이 테이블은 승인된 화면명세 없이 auto 생성이 차단됩니다(고위험 테이블 정책)",
            List.of(/* 기존 가드와 동일한 안내 메시지 */),
            null, null, null, null, List.of()));
}
```
신규 `CrudGenerationApprovalPolicy`(또는 `PipelineEvolutionProperties` 확장) 컴포넌트가
`approval-required-tables`/`approval-required-for-all` 설정을 읽어 판정한다.

### 4.4 감사 기록

거부/통과 여부는 기존 `recordAudit()` 경로에 `failureStage="approval-policy"`로 남긴다 — 신규
테이블 없이 `AI_GENERATION_OPERATION_AUDIT`를 그대로 사용.

### 4.5 테스트 관점

- 고위험 테이블 + 화면명세 없음 → 차단
- 고위험 테이블 + 승인된 화면명세 있음 → 통과
- 고위험 목록에 없는 테이블 → 기존 동작 그대로(회귀 없음)
- `approval-required-for-all=true`일 때 CRUD 뷰타입에도 적용되는지

## 5. 옵션 비교

| 항목 | 옵션 A | 옵션 B |
|---|---|---|
| 기존 Tool 계약 변경 | 있음(breaking) | 없음 |
| 신규 테이블 | 1개(`AI_CRUD_GENERATION_PREVIEW`) | 0개 |
| 신규 MCP Tool | 3개 | 0개 |
| 반복 재생성 워크플로우 영향 | 큼(매번 승인 필요) | 없음(고위험 지정 테이블만) |
| 보장 수준 | 모든 diff를 사람이 직접 확인(Thymeleaf 동급) | "승인된 화면명세가 있어야 함"이라는 사전 조건부(Thymeleaf보다 약함) |
| 구현 규모 | 큼 | 작음 |
| 32번 문서 설계 원칙과의 정합성 | 낮음(실행기 통합 비권장 근거와 긴장) | 높음(기존 V2_APPLY 가드의 연장선) |

## 6. 미확정 사항 (구현 착수 전 반드시 결정)

1. ~~옵션 A/B 중 최종 채택안~~ — **확정됨(2026-08-25): 옵션 B.** 두 옵션을 동시에 지원할 수
   없다는 판단에 따라 옵션 A(§3)는 구현하지 않는다.
2. ~~옵션 A 채택 시 §3.4의 (A-1)/(A-2) 중 선택~~ — 옵션 A 비채택으로 해당 없음.
3. **(남은 결정 사항)** "고위험 테이블" 목록을 코드/설정 중 어디서 관리할지, 초기 목록을
   무엇으로 할지(비어 있는 채로 시작해 운영 중 추가하는 안 포함) — 아직 미정.
