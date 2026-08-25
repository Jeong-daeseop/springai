# CRUD 생성 경로에 명시적 승인 단계 도입 — 타당성 검토

> 문서 버전: 1.1
> 작성일: 2026-08-25
> 상태: 검토(결정 아님) — 구현 여부는 별도 승인 필요
> 관련 문서: [32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md](../figma/32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md),
> [ADR_Dual_Pipeline_Legacy_Storage_Adapter_Strategy.md](./ADR_Dual_Pipeline_Legacy_Storage_Adapter_Strategy.md)

## 1. 배경

Thymeleaf 마이그레이션 경로(`ThymeleafProjectWorkflowService`)는 `PREVIEW_READY → APPROVED → APPLIED`
3단계 상태기계로, 사람이 `previewHash`를 확인하고 명시적으로 `approve()`를 호출해야만 실제 파일
쓰기(`apply()`)가 진행된다. 반면 CRUD 생성 경로(`CodeServiceGenerationExecutor`)는
`llmProvider="auto"`(기본값)일 때 사람의 개입 없이 한 번의 Tool 호출로 렌더링과 파일 저장까지
즉시 완료된다. 이 문서는 CRUD 경로에도 Thymeleaf와 같은 명시적 승인 단계를 추가하는 것이
기술적으로 가능한지, 어떤 비용이 드는지, 그리고 실제로 그렇게 하는 것이 타당한지를 검토한다.

## 2. 현재 상태 요약

| 항목 | Thymeleaf 경로 | CRUD 경로(auto) |
|---|---|---|
| 실행 방식 | Preview → Approve → Apply 3단계, 각각 별도 Tool 호출 | Plan → Render → Write → Verify → History, 한 번의 Tool 호출로 전부 완료 |
| 승인 트리거 | `llmProvider` 없음. 사람이 hash를 보고 `approveThymeleafProject()` 명시 호출 | 없음. `llmProvider`를 생략하거나 `"auto"`로 지정하면 즉시 진행 |
| 승인 로직 위치 | `ThymeleafProjectWorkflowService.approve(operationId, expectedPreviewHash)` | 해당 없음 — 코드베이스에 CRUD용 `approve` 메서드 자체가 존재하지 않음 |
| 승인 판정 기준 | ① 상태가 `PREVIEW_READY`인지 ② `previewHash` 일치 여부 ③ 검증 에러 없음, 3개 모두 통과해야 `APPROVED`로 전이 | 해당 없음 |
| 인가(Authorization) | `sharedSecret` + `ThymeleafToolAuthorizationService.authorize()` | 해당 없음 |
| 상태값 정의 | `PREVIEW_READY`/`APPROVED`/`APPLIED`/`CONFLICT`/`REJECTED` 등을 실제로 사용 | `GenerationOperationStatus`에 `PREVIEW_READY`/`APPROVAL_REQUIRED`/`APPROVED`/`APPLYING` 값이 **정의는 되어 있으나**, `CodeServiceGenerationExecutor`는 `CONFLICT`/`FAILED`/`APPLIED` 3개만 실제로 기록 |

**핵심 발견**: 공통 상태 조회 모델(`GenerationOperationStatus`)에는 이미 승인 흐름을 표현할 수 있는
어휘(`PREVIEW_READY`, `APPROVAL_REQUIRED`, `APPROVED`, `APPLYING`)가 정의되어 있다. 다만 CRUD
실행기가 이 값들을 채우지 않을 뿐이다 — 완전히 새로운 개념을 도입하는 것이 아니라, 이미 있는
어휘를 실제로 사용하기 시작하는 작업에 가깝다.

## 3. 도입 옵션

### 옵션 A — Thymeleaf와 동일한 3단계 구조로 전환

`CrudGenerationApplicationService.execute()`를 Render 단계까지만 수행하고 멈추는 `preview()`와,
승인된 결과만 실제로 쓰는 `apply()`로 분리한다.

**필요 작업:**
- 렌더링된 결과(아직 파일로 안 쓴 상태)를 "승인 대기" 상태로 영속화할 저장소 신설
  (신규 테이블 또는 `AI_CRUD_GENERATION_SNAPSHOT` 확장)
- `previewCrudGeneration` / `approveCrudGeneration` / `applyCrudGeneration` 신규 MCP Tool 3종
  (`ThymeleafProjectWorkflowTool` 패턴을 그대로 준용)
- `CodeServiceGenerationExecutor.ownershipAwareExecute()`를 승인 이후에만 호출되도록 재구성

**비용/리스크:**
- 기존 `buildFullCrudPrompt` 등 "한 번 호출로 완결"되는 Tool 계약을 깨는 **호환성 파괴 변경**.
  기존 자동화·문서·사용 패턴이 전부 새 3단계 흐름에 맞게 바뀌어야 한다.
- CRUD 생성의 핵심 사용 시나리오인 "스키마 변경 시 빠른 반복 재생성"이 매번 사람 승인을
  거쳐야 하는 흐름으로 바뀌면서 속도 이점이 크게 줄어든다.

### 옵션 B — 조건부 승인 게이트

기존처럼 한 번의 호출로 완결되는 흐름은 유지하되, 특정 조건(운영 DB 테이블, 민감 테이블,
특정 프로젝트 등)에서만 사람의 사전 승인 산출물(예: 승인된 화면명세)이 있어야 진행되도록 하는
선택적 게이트를 추가한다.

이미 이번 세션에서 구현한 `CrudGenerationPlanner`의 가드(`V2_APPLY` 모드 + `viewType=THYMELEAF` +
`llmProvider=auto` 조합일 때 `designReferenceId`/`screenSpecificationId` 중 하나가 없으면 차단)가
이 방식의 선례다.

**비용/리스크:**
- 옵션 A보다 구현 범위가 훨씬 작다 — 기존 Tool 계약을 유지한 채 특정 조건에서만 사전 조건을
  추가하는 방식.
- "전면적인 사람 승인"이 아니라 "특정 위험군에 한정된 사전 조건부 승인"이므로, Thymeleaf 수준의
  보장(모든 diff를 사람이 직접 확인)은 제공하지 않는다.

## 4. 신중해야 하는 이유 — 기존 설계 결정과의 충돌

32번 문서(`32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md`)는 두 실행기를 하나로
통합하는 안을 이미 검토했고 **비권장**으로 결론 내렸다. 그 근거는 다음과 같다.

> "CRUD 생성과 Thymeleaf 마이그레이션은 동일한 파일 쓰기 Port를 공유하지만, 위험 모델과 승인
> 방식이 다르므로 실행기는 분리한다. CRUD는 반복 재생성을 위해 Region 3-way(Base·Current·New)
> 비교 기반 자동 병합을 사용하고, Thymeleaf 마이그레이션은 레거시 화면 변환의 위험도를 고려해
> Preview Hash·Source Revision·명시적 승인·Revalidate를 강제한다."

즉 CRUD 경로에 승인 단계가 없는 것은 단순한 누락이 아니라, "반복 재생성이라는 사용 시나리오에
맞춰 의도적으로 다르게 설계한 결과"라는 근거가 문서화되어 있다. 옵션 A를 그대로 도입하면 이
설계 결정과 정면으로 충돌한다.

## 5. 기존 Thymeleaf 승인 Tool과의 관계·충돌 가능성 검토

CRUD에 명시적 승인 단계를 추가할 때, 이미 존재하는 Thymeleaf 승인 경로
(`ThymeleafProjectWorkflowTool.approveThymeleafProject()`)와 얽히거나 충돌할 가능성을 별도로
검토했다.

### 5.1 `approveThymeleafProject()` 내부에서 `GenerateCrudProjectUseCase`를 호출할 수 있는가

기술적으로 Spring Bean 주입 자체는 막을 게 없지만, **입력값 자체가 성립하지 않아 실질적으로
불가능하다.**

```java
// approveThymeleafProject의 실제 파라미터
public String approveThymeleafProject(String sharedSecret, String operationId, String previewHash)

// GenerateCrudProjectUseCase.execute()가 요구하는 것
CrudOrchestrationResult execute(CrudGenerationCommand command)
// CrudGenerationCommand는 database, tableName, domain, packageName, outputPath,
// viewType, layout, program, designContext, rendererProfileReference 등을 요구
```

`approveThymeleafProject()`가 받는 `operationId`·`previewHash`만으로는 `CrudGenerationCommand`가
요구하는 DB 스키마 기반 정보(`database`/`tableName`/`domain`/`packageName` 등)를 만들어낼 근거가
없다. 또한 개념적으로도 `approveThymeleafProject()`는 "이미 존재하는 레거시 JSP 화면을
Thymeleaf로 변환하는 작업의 승인"이고, `GenerateCrudProjectUseCase`는 "DB 스키마 기반 신규 CRUD
화면 생성 실행"이라 트리거·대상·라이프사이클이 전혀 다르다. **이 방향은 권장하지 않는다** — 4절의
32번 문서 근거(실행기 통합 비권장)와도 같은 이유로 충돌한다.

### 5.2 CRUD에 새 승인 단계를 추가하면 기존 Thymeleaf 승인 체계와 충돌(operationId 충돌 등)할 수 있는가

코드로 확인한 결과 **충돌 위험은 없다.**

- **ID 공간이 원천적으로 분리됨**: CRUD는 `CrudGenerationOperationIdFactory.forScreen(outputPath,
  tableName, viewType)`로 ID를 만들어 `AI_CRUD_GENERATION_SNAPSHOT`에 저장하고, Thymeleaf는 별도
  로직으로 ID를 만들어 `AI_THYMELEAF_PROJECT_OPERATION`에 저장한다. 서로 다른 입력값 기반 해시가
  서로 다른 테이블에 들어가므로 물리적으로 같은 자리를 놓고 경합할 수 없다.
- **우연히 같은 문자열이 나와도 이미 방어 코드가 있음**: `GenerationControlPlaneService.find()`가
  이렇게 되어 있다.

  ```java
  public Optional<GenerationOperation> find(String operationId, GenerationSourceType sourceType) {
      List<GenerationOperation> matches = adapters.stream()
              .filter(adapter -> sourceType == null || adapter.sourceType() == sourceType)
              .map(adapter -> adapter.find(operationId)).flatMap(Optional::stream).toList();
      if (matches.size() > 1) {
          throw new IllegalArgumentException("GENERATION_OPERATION_SOURCE_TYPE_REQUIRED: " + operationId);
      }
      return matches.stream().findFirst();
  }
  ```

  `sourceType`을 생략하면 CRUD·Thymeleaf 두 Adapter를 모두 조회하는데, 만약 두 곳에서 동시에
  같은 `operationId`가 발견되면 조용히 아무거나 반환하지 않고 즉시 예외를
  던진다(`GENERATION_OPERATION_SOURCE_TYPE_REQUIRED`). 이미 이 상황을 예상하고 fail-loud하게
  막아놓은 상태다.
- **MCP Tool 진입점도 서로 다른 메서드**: `approveThymeleafProject()`는 독립된 `@Tool` 메서드이고,
  CRUD 전용 승인 Tool(예: `approveCrudGeneration`, 3절 옵션 A 참고)은 완전히 다른 이름의 별도
  메서드가 되므로 Tool 등록(`McpConfig`) 단계에서도 이름이 겹치지 않는다.

**결론**: 각자 독립된 Tool·테이블·ID 체계를 유지하는 한(3절 옵션 A/B처럼 CRUD 전용 별도 경로로
설계하는 한) 기존 Thymeleaf 승인 체계와 충돌할 위험은 없다. 유일하게 문제가 되는 경우는 5.1처럼
두 흐름을 억지로 한 진입점에 뒤섞을 때뿐이다.

## 6. 검토 의견

- 옵션 A는 기술적으로 가능하나, 기존 Tool 계약을 깨는 큰 변경이고 32번 문서의 설계 근거와
  충돌한다. 전면 도입은 권장하지 않는다.
- 옵션 B는 이미 있는 패턴(V2_APPLY + 화면명세 필수화 가드)의 연장선이라 일관성이 있고, 반복
  재생성 시나리오를 깨지 않으면서 고위험 케이스만 선별적으로 보호할 수 있다.
- 어느 경우든, "언제·어떤 조건에서 승인을 요구할 것인가"는 기술 문제가 아니라 운영 정책
  결정이므로, 실제 도입 여부와 범위는 반드시 사용자 승인을 거쳐야 한다.

## 7. 미확인 사항

1. 옵션 B를 도입한다면 "고위험"을 구분하는 기준(테이블명 패턴, 환경, 프로젝트 등)을 무엇으로
   할지는 별도 정책 논의가 필요하다.
2. 옵션 A/B 어느 쪽이든, 승인 이력을 `AI_GENERATION_OPERATION_AUDIT`(V16, 14.1절)에 어떻게
   연결할지는 설계 단계에서 추가로 정해야 한다.
3. 실제 도입 결정 시 `writing-plans` 절차를 거쳐 상세 구현 계획을 별도로 작성해야 한다 — 이
   문서는 타당성 검토이며 구현 계획이 아니다.
