# ARCH-WP0 계약·테스트 기준선

> 작성일: 2026-08-03
> 기준 commit: `ea56582` (`main`, `origin/main` 대비 26 커밋 ahead, 작업 트리 clean)
> 목적: 이후 WP1~WP13 리팩터링이 외부 계약·데이터·테스트 결과를 실수로 바꾸지 않았는지 비교할 기준점

이 문서는 [구현목록](../SpringAI_권장_목표_아키텍처_구현목록_2026-08-03.md) §4 `ARCH-WP0`의
증적입니다. 재현 가능한 항목은 자동 스냅샷 테스트로, 재현이 어렵거나 이번 세션에서 다루지 않은
항목은 그 이유와 함께 명시합니다.

## 완료 조건(M0-G1~G4) 판정

| Gate | 상태 | 근거 |
|---|---|---|
| `M0-G1` MCP snapshot 재현 가능 | ✅ | 기존 `McpToolDefinitionSnapshotTest` + `tool-definitions-baseline.json`(92 methods/34 objects) |
| `M0-G2` 대표 생성 결과 비교 가능 | ❌ 미착수 | CRUD/Board/Master-detail golden fixture(ARCH-0011) 및 Thymeleaf/Figma Bundle 대표 fixture(ARCH-0012)는 실제 생성 파이프라인 실행(Ollama/OpenAI/MySQL 의존)이 필요해 이번 세션에서 다루지 않음 |
| `M0-G3` DB·Artifact 기준선 재현 가능 | 🟡 부분 | DB schema(ARCH-0007)는 실제 조회로 확보. Artifact 저장 경로·metadata 구조(ARCH-0010)는 미착수 |
| `M0-G4` 사용자 기존 변경과 구현 변경 범위 분리 | ✅ 계속 실천 중 | 이 세션 전체에서 다른 동시 진행 세션의 미커밋 변경을 별도 커밋으로 분리(`162bb3c`) |

## ARCH-0001 Git 상태

```
branch: main
commit: ea56582 (refactor: WP1 위험 등급을 중앙 레지스트리에서 @McpToolRisk 어노테이션으로 전환)
origin/main 대비: 26 커밋 ahead
작업 트리: clean (git status --short 출력 없음)
```

## ARCH-0002/0003 MCP Tool 계약

기존 `McpToolDefinitionSnapshotTest`(`src/test/resources/mcp/tool-definitions-baseline.json`)가 이미
이 항목을 충족합니다 — 새로 만들지 않고 그대로 기준선으로 채택합니다.

- Tool 메서드 수: **92**
- Tool 객체(클래스) 수: **34**
- 위험 등급: 전 메서드 `@McpToolRisk` 부착 완료(WP1), `McpToolRiskAnnotationCoverageTest`로 커버리지 보증

## ARCH-0004 REST endpoint inventory (신규)

`RestEndpointSnapshotTest` + `src/test/resources/rest/endpoint-inventory-baseline.json`을 새로 만들었습니다.
`@RestController` classpath 스캔 기반이라 새 Controller가 추가돼도 자동으로 대상에 포함됩니다.

- 총 endpoint 수: **45** (Controller 8개: `DesignSystemController` 11, `FigmaExportController` 6,
  `FigmaHybridController` 6, `FigmaOperationsController` 6, `RagController` 4,
  `ThymeleafOperationsController` 5, `ToolApiController` 6, `FigmaDesignOrchestrationController` 1)
- 인증 방식(`SecurityConfig` 기준):
  - `/api/**` 전체: `X-API-Key` 헤더 필요(`apiKeyFilter`)
  - 예외: `/api/chat/**`, `/api/ollama/**`, `/api/documents/**`는 permitAll
  - `/api/figma/screens/**`의 GET만 단기 Bearer 토큰(`FigmaRestTokenService`) 추가 허용
  - `/mcp/**`, `/sse/**`는 Spring Security 상 permitAll이며 WP1의 `McpAuthenticationInterceptor`가 별도 경계를 담당(§ARCH-0002 참고)

## ARCH-0005 상태 전이 matrix (신규, 최초 버전에서 정정됨)

`ProjectOperationStatusTransitionSnapshotTest` +
`src/test/resources/state-machine/thymeleaf-project-operation-transitions-baseline.json`,
`FigmaDesignOperationStateTransitionSnapshotTest` +
`src/test/resources/state-machine/figma-design-operation-transitions-baseline.json`.

- Thymeleaf `ProjectOperationStatus`: 9개 상태, 81개 조합 중 **10개** 전이만 허용
  (`ANALYZED→CONTRACT_READY→PREVIEW_READY→{APPROVED|CONFLICT|REJECTED}→...→{APPLIED|FAILED}→{VALIDATED|FAILED}`)
- Figma `FigmaDesignOperationStatus`: 7개 상태, 49개 조합 중 **11개** 전이만 허용
  (`ANALYZED→PREVIEW_READY→APPLY_REQUIRED→APPLIED`, 각 단계에서 `FAILED`/`REJECTED`/`CONFLICT`로 분기)
- 두 쪽 모두 최종 상태에서 나가는 전이 0건을 별도 테스트로 확인

**최초 관찰의 정정**: 이 baseline 최초 버전은 "`FigmaDesignOperation`이 `withNextRevision`으로
임의의 다음 상태를 받아들일 뿐 전이 검증이 없다"고 기록했으나, 이는 `FigmaDesignOperation`
record(모델) 하나만 보고 내린 부정확한 결론이었습니다. 실제 쓰기 경로인
`FigmaDesignOperationRepository`는 저장 직전 매번 별도 서비스
`FigmaDesignOperationStateService.assertTransitionAllowed(...)`로 검증하며, 이는 Thymeleaf의
`ProjectOperationStateService.transitionState`와 같은 계층(모델이 아니라 Service)에서 동등한
역할을 합니다. `APPLIED` 전이는 `assertTransitionToAppliedAllowed(current, pluginReportReceived)`
전용 API로만 가능해 Plugin 적용 보고 없이는 절대 `APPLIED`로 못 갑니다(계획서 §14 `ARCH-0821`
요구사항과 정확히 일치).

**실제로 남아있는 차이**: 두 Service의 위반 시 동작이 다릅니다.

| | 위반 시 동작 |
|---|---|
| `ProjectOperationStateService.transitionState` | `log.warn` 후 원본 Operation을 조용히 그대로 반환(no-op, 예외 없음) |
| `FigmaDesignOperationStateService.assertTransitionAllowed` | `IllegalStateException`을 던짐 |

즉 "공통 상태 전이 정책 부재"가 아니라, **두 도메인이 각자 상태 검증을 갖고 있지만 서로 다른
실패 시맨틱(silent no-op vs throw)을 쓰고, 코드도 공유하지 않는다**는 게 정확한 관찰입니다.
후자(코드 미공유)는 여전히 `ARCH-WP4`(`ARCH-0404` "기능 공통 상태와 Figma/Thymeleaf 확장 상태를
구분한다")가 풀어야 할 실제 문제입니다.

## ARCH-0006 Schema version 목록

```
ui-design-spec-v1              UiDesignSpec
design-system-spec-v1          DesignSystemSpec
component-registry-v1          ComponentRegistry
rendered-design-document-v1    RenderedDesignDocument
figma-export-bundle-v1         FigmaExportBundle
1                               FigmaScreenSpec (숫자 문자열 — 다른 5개와 명명 규칙 불일치)
```

`ScreenSpecification`, Validation Report류는 별도 `SCHEMA_VERSION` 상수가 없습니다(DB
`AI_SCREEN_SPECIFICATION.SPEC_VERSION`, `AI_FIGMA_SCREEN_SPEC.SCREEN_SPEC_VERSION` 등 정수 revision
컬럼으로만 버전 관리). 전부 아직 v1/초기 revision 단계라 버전 진화 이력은 없습니다.

## ARCH-0007 MySQL 애플리케이션 테이블·index inventory

`docker exec egov-mysql`로 실제 조회(운영 DB 아님, 로컬 `egov-mysql` 컨테이너).

`AI_*` 접두사 애플리케이션 관리 테이블 **10개** (최초 조사 시 12개였으나, 아래 ARCH-0008에서
발견한 고아 테이블 2개를 이 WP0 세션 중 실제로 DROP했습니다 — 이 목록은 삭제 후 현재 상태입니다):

```
AI_COMPONENT_REGISTRY                          PK(PROFILE_ID, REGISTRY_VERSION)
AI_DESIGN_ANALYSIS                             PK(ANALYSIS_ID) + UK(SOURCE_HASH,PROVIDER_ID,MODEL_ID,PROMPT_VERSION)
AI_DESIGN_SYSTEM_PROFILE                       PK(PROFILE_ID, PROFILE_VERSION)
AI_FIGMA_DESIGN_OPERATION                      PK(OPERATION_ID, REVISION)
AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY          PK(REQUEST_HASH)
AI_FIGMA_GENERATION_REPORT                     PK(REPORT_ID) + IDX(SCREEN_ID,SCREEN_VERSION)
AI_FIGMA_REVIEW_HISTORY                        PK(EVENT_ID) + IDX(TARGET_TYPE,TARGET_ID,TARGET_VERSION)
AI_FIGMA_SCREEN_SPEC                           PK(SCREEN_ID, SCREEN_VERSION) + IDX(SCREEN_SPEC_ID,SCREEN_SPEC_VERSION)
AI_GENERATION_HISTORY                          PK(ID)
AI_SCREEN_SPECIFICATION                        PK(SPEC_ID, SPEC_VERSION)
```

eGovFrame 기본 스키마(`LETTN*`, `COMVN*`, `IDS`)는 이 프로젝트가 관리하지 않는 별도 영역이라
목록에서 제외했습니다. `LETTNMENUINFO_BACKUP_20260706`, `LETTNMENUINFO_BEFORE_RESTORE_20260707`,
`LETTNPROGRMLIST_BACKUP_20260706`, `LETTNPROGRMLIST_BEFORE_RESTORE_20260707`는 과거 수동 복구
작업의 흔적으로 보이는 백업 테이블입니다(이번 세션에서 만든 것 아님, 원인 미상).

## ARCH-0008 `@PostConstruct` DDL 목록과 고아 테이블 정리(완료)

`@PostConstruct`에서 `CREATE TABLE IF NOT EXISTS`를 실행하는 Repository **9개**:

```
DesignAnalysisRepository, FigmaReviewHistoryRepository, ComponentRegistryRepository,
DesignSystemProfileRepository, GenerationHistoryRepository, FigmaScreenSpecRepository,
ScreenSpecRepository, FigmaDesignOperationRepository, FigmaGenerationReportRepository
```

`FigmaDesignOperationRepository`는 `AI_FIGMA_DESIGN_OPERATION`과
`AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY` 두 테이블을 함께 생성합니다.

**고아 테이블 발견 및 삭제**: `AI_THYMELEAF_CONVERSION_OPERATION`,
`AI_THYMELEAF_CONVERSION_OPERATION_IDEMPOTENCY` 두 테이블을 만들던
`ThymeleafConversionOperationRepository`는 커밋 `162bb3c`(다른 세션의 레거시 Thymeleaf 파이프라인
제거)에서 이미 삭제되었고, 코드베이스 전체에서 `AI_THYMELEAF_CONVERSION_OPERATION`을 참조하는
곳이 0건임을 확인했습니다. 두 테이블에는 각각 546건/127건의 과거 이력 데이터가 남아 있었지만,
사용자 확인 후 이 WP0 세션 중 `DROP TABLE`로 실제 삭제했습니다. 삭제 후
`SpringaiApplicationTests`를 포함한 전체 테스트가 그대로 통과해 애플리케이션에 영향이 없음을
확인했습니다.

## ARCH-0009 Redis key·TTL

`docker exec redis-stack redis-cli`로 조회. dbsize **2729**.

관찰된 key 패턴:
```
egov:<uuid>              — 벡터 스토어 문서 chunk (spring-ai-starter-vector-store-redis)
chat:chunk-ids:<key>     — RAG 청크 ID 인덱스
sample_restaurant:<id>, sample_bicycle:<id> — 샘플/테스트성 데이터로 추정
```

TTL 설정 여부는 이번 조사에서 개별 key 단위로 확인하지 않았습니다(2700개 이상 key를 순회하는
비용 문제로 이번 세션 범위 밖으로 미룸) — `ARCH-WP5`(Artifact) 또는 `ARCH-WP10`(관측성) 착수 시
TTL 정책 유무를 별도로 확인해야 합니다.

## ARCH-0010~0012 Artifact·golden fixture

미착수. `DesignArtifactService`, Thymeleaf Preview, Figma Bundle 대표 산출물을 fixture로 고정하려면
실제 생성 파이프라인을 한 번 돌려야 하는데(Ollama/OpenAI API, MySQL 데이터 필요), 이번 세션에서는
시간·부작용(실제 파일/DB 레코드 생성) 문제로 다루지 않았습니다. `M0-G2`/`M0-G3`가 부분 미충족인
직접적 이유입니다.

## ARCH-0013/0014 테스트 결과

```
전체(-Pci 없음): 169 test classes, 983 tests, 0 failures, 0 errors
-Pci 모드:        150 test classes, 787 tests, 0 failures, 0 errors
차이: 19 classes / 196 tests가 -Pci에서 제외됨
```

`-Pci` 제외 목록(`build.gradle`):
```
SpringaiApplicationTests, FigmaMcpRegistrationTest, McpToolDefinitionSnapshotTest,
mapper/**/*IntegrationTest, service/**/*IntegrationTest,
BoardTemplateRendererTest, CrudTemplateRendererTest, MasterDetailTemplateRendererTest
```
즉 CI는 MySQL/Redis 실제 연결이 필요한 통합 테스트와 컨텍스트 기동 테스트를 제외한 빠른
회귀 세트만 돈다는 뜻이며, MCP 계약·Spring 컨텍스트 정합성은 로컬(또는 별도 integration job)에서만
검증됩니다.

## ARCH-0015 bootJar

```
build/libs/springai-0.0.1-SNAPSHOT.jar: 241M
```

로컬 ONNX 임베딩 모델(약 400MB, CLAUDE.md 언급) 등이 포함되어 상당히 큽니다. 임의
working directory에서의 prod profile smoke test(`ARCH-0905`, `ARCH-WP9` 영역)는 이번
세션에서 실행하지 않았습니다.

## ARCH-0016 Contract/Extractor/Plugin 테스트 결과

```
website-figma-contract        : npm test → contract OK (schemas=16), Gradle `figmaContractTest`로 이미 CI 연결됨
jsp-design-extractor           : typecheck ✅, build ✅. test 스크립트는 "build + e2e"라 서버 기동이
                                  필요해 이번 세션에서는 실행하지 않음
jsp-to-figma-plugin             : typecheck ✅. package.json에 test 스크립트 자체가 없음(build/lint만 존재)
figma-screen-spec-plugin        : typecheck ✅, test ✅ 17/17
krds-design-system-author-plugin: typecheck ✅, test ✅ 7/7
```

`jsp-to-figma-plugin`에 `test` 스크립트가 없다는 사실은 계획서 §19(`ARCH-1307` "Plugin
typecheck/lint/test/build를 통과한다")를 이 프로젝트가 아직 충족하지 못한다는 뜻이므로
`ARCH-WP13`(Release Gate) 이전에 반드시 채워야 할 항목으로 별도 기록합니다.

## 신규 회귀 감지 테스트 요약

이번 WP0 작업으로 추가된, 이후 회귀를 자동으로 잡아주는 테스트:

| 테스트 | Baseline 파일 |
|---|---|
| `RestEndpointSnapshotTest` | `src/test/resources/rest/endpoint-inventory-baseline.json` |
| `ProjectOperationStatusTransitionSnapshotTest` | `src/test/resources/state-machine/thymeleaf-project-operation-transitions-baseline.json` |
| `FigmaDesignOperationStateTransitionSnapshotTest` | `src/test/resources/state-machine/figma-design-operation-transitions-baseline.json` |

기존에 이미 있던 것(재확인만 함):

| 테스트 | Baseline 파일 |
|---|---|
| `McpToolDefinitionSnapshotTest` | `src/test/resources/mcp/tool-definitions-baseline.json` |
