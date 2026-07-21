# 비전 디자인 참조 통합 — Tool 테스트 우선순위 상세

> **작성일:** 2026-07-18
> **근거 문서:** [`local-vision-design-reference-integration-review.md`](local-vision-design-reference-integration-review.md)
> **범위:** 위 검토안 반영 이후 실제 코드(`git status` 기준 tracked 수정분 + untracked 신규분)를 대조하여, 로컬에서 바로 테스트 가능한 항목과 외부 인프라가 있어야만 검증되는 항목을 구분하고 우선순위를 정한다.

---

## 1. 전체 그림

```text
                         ┌─────────────────────────────┐
                         │   analyzeDesignReference()   │  ← DesignReferenceTool
                         │   (PNG/JPEG/PDF → UiDesignSpec) │
                         └───────────────┬───────────────┘
                                         │ designAnalysisId
                                         ▼
                         ┌─────────────────────────────┐
                         │  createScreenSpecification()  │  ← DesignReferenceTool
                         │  (DB 스키마 + UiDesignSpec)    │
                         └───────────────┬───────────────┘
                        DRAFT / REVIEW_REQUIRED / APPROVED
                                         │ screenSpecificationId
                                         ▼
        ┌────────────────────────────────────────────────────┐
        │            CrudPromptBuilderTool                    │
        │  buildFullCrudPrompt / buildMasterDetailPrompt /     │
        │  buildBoardFeature  (auto ↔ claude 두 분기 모두 배선) │
        └───────────────────────────┬──────────────────────────┘
                                     │ 생성된 FTL/HTML
                                     ▼
        ┌────────────────────────────────────────────────────┐
        │              CodeValidatorTool                       │
        │  auditGeneratedQuality / validateThymeleafRendering / │
        │  validateGeneratedProjectBuild                        │
        └────────────────────────────────────────────────────┘
```

이 파이프라인에서 **로컬에서 지금 바로 돌릴 수 있는 구간**과 **외부 자원이 있어야만 실행되는 구간**이 섞여 있다. 아래에서 순서대로 다룬다.

> ⚠️ **주의**: 위 그림은 컴포넌트 간 의존 순서를 보여주는 것이지, "매번 4단계를 전부 별도 MCP Tool 호출로 순서대로 실행한다"는 뜻이 아니다. 실제로는 케이스에 따라 일부 단계가 **생략**되거나 **다른 단계 내부로 흡수**되거나 **자동으로 이미 실행**된다. 정확한 분기는 §5를 참고.

---

## 2. 로컬에서 바로 테스트 가능한 항목 (우선순위 순)

> **진행 현황 (2026-07-18 갱신)**
> | 우선순위 | 대상 | 상태 |
> |---|---|---|
> | 1 | `DesignReferenceTool` | ✅ 완료 — `DesignReferenceToolTest.java` 작성, Figma 위임 포함 9개 테스트 통과 |
> | 2 | `CrudPromptBuilderTool` (auto/claude 분기) | ✅ 완료 — `CrudPromptBuilderToolTest.java`에 5개 테스트 추가, 전체 9개 통과 |
> | 3 | `ThymeleafLayoutTool` | ✅ 확인 완료, 추가 작업 불필요 — 상세는 §2.3 |

### 우선순위 1 — `DesignReferenceTool` (테스트 파일 자체가 없음) ✅ 완료

```text
src/test/java/com/krdevops/springai/tools/
  ├─ CrudPromptBuilderToolTest.java   ✅ 존재 (수정됨)
  ├─ ThymeleafLayoutToolTest.java     ✅ 존재 (수정됨)
  └─ DesignReferenceToolTest.java     ❌ 없음  ← 신규 Tool인데 Tool 레벨 테스트 부재
```

`DesignReferenceTool.java`가 노출하는 7개 `@Tool` 메서드:

| 메서드 | 위임 대상 | 위험 포인트 |
|---|---|---|
| `analyzeDesignReference(referencePath, pageRange, featureType)` | `DesignReferenceAnalysisService.analyze()` | 경로 검증(§10.1 임의 파일 읽기), provider gating |
| `analyzeFigmaReference(figmaUrl, nodeId, featureType)` | `DesignReferenceAnalysisService.analyzeFigma()` | URL/allowlist 검증, PAT gating, 결정론 매핑, Figma 캐시 계약 |
| `findReusableDesignAnalyses(query, expectedArchetype, expectedFeatureType, topK)` | `.findReusableCandidates()` | RAG 후보가 source/schema/featureType/현재 계약 검증 후 후보로만 반환되는지 |
| `createScreenSpecification(database, tableName, screenName, featureType, designAnalysisId)` | `ScreenSpecificationService.create()` | `designAnalysisId`가 blank/null일 때 DB 스키마만으로 fallback 생성되는지 (18~57행 로직) |
| `approveScreenSpecification(id)` | `.approve()` | 미해결 이슈 있는 명세를 승인 처리하지 않는지 |
| `reviseScreenSpecification(spec)` | `.revise()` | 주 테이블 변경 차단, COLUMN 재검증 |
| `getScreenSpecification(id)` | `.get(id)` | 단순 조회 — 우선순위 낮음 |

서비스 레벨 테스트(`DesignReferenceAnalysisServiceTest`, `ScreenSpecificationServiceTest`)는 이미 존재했지만, **Tool 진입점**(파라미터 null/blank 처리, `analysis == null` 분기, MCP 파라미터 바인딩)은 검증된 적이 없었다.

**✅ 갱신 (2026-07-19):** `src/test/java/com/krdevops/springai/tools/DesignReferenceToolTest.java`는 Mockito 기반으로 Tool 위임 로직을 검증한다. Figma 분석 위임 케이스를 추가해 9개 테스트가 통과한다(`./gradlew test --tests DesignReferenceToolTest`). URL 보안·API 재시도·매퍼·캐시 계약은 각각의 서비스 단위 테스트에서 별도로 검증한다.

- `analyzeDesignReference` / `analyzeFigmaReference` / `findReusableDesignAnalyses` / `approveScreenSpecification` / `reviseScreenSpecification` / `getScreenSpecification` — 서비스 위임 확인
- `createScreenSpecification` — 3가지 분기
  - `designAnalysisId=null` → 분석 조회 없이 `create(..., null)` 호출
  - `designAnalysisId="  "`(blank) → 동일하게 조회 생략
  - `designAnalysisId="analysis-1"` → `get()`으로 분석 조회 후 `uiSpec`을 그대로 `create()`에 전달

```java
// 실제 작성된 핵심 케이스 (전체는 DesignReferenceToolTest.java 참고)
@Test
void createScreenSpecification_withBlankDesignAnalysisId_skipsAnalysisLookup() {
    tool.createScreenSpecification("com", "LETTNEMPLYRINFO", "직원목록", "crud", "  ");
    verify(designReferenceAnalysisService, never()).get(any());
}
```

---

### 우선순위 2 — `CrudPromptBuilderTool`의 3개 provider 분기 메서드 ✅ 완료

`buildFullCrudPrompt` / `buildMasterDetailPrompt` / `buildBoardFeature`는 각각 내부적으로 다음처럼 **두 갈래**로 갈라진다 (검토 문서 §5가 지적한 중복 배선 지점):

```text
buildFullCrudPrompt(..., designReferenceId, screenSpecificationId)
  │
  ├─ llmProvider == "auto"
  │     → CrudGenerationOptions(..., designReferenceId, screenSpecificationId)
  │     → crudOrchestrationService.orchestrate(..., options)   ★확인 필요①
  │
  └─ llmProvider == "claude" 등
        → crudProgramMetadataService.resolve(...)
        → generationDesignContextService.resolve(
              database, tableName, ..., designReferenceId, screenSpecificationId)  ★확인 필요②
        → crudPromptBuilderService.buildFullCrudPrompt(..., screenSpecification)
```

소스를 직접 확인한 결과 **두 분기 모두 실제로 배선은 되어 있었다** (`CrudPromptBuilderTool.java` 117~150행). 문제는 `git diff` 상 `CrudPromptBuilderToolTest.java`에 `designReferenceId`/`screenSpecificationId`를 다루는 테스트 케이스가 **하나도 없었다**는 점(grep 결과 0건). 즉 배선 코드는 있지만 회귀 안전망이 없는 상태였다.

**✅ 완료 (2026-07-18):** 다음 5개 테스트를 `CrudPromptBuilderToolTest.java`에 추가했다(전체 9개 통과).

| 메서드 | 추가된 테스트 | 검증 내용 |
|---|---|---|
| `buildFullCrudPrompt` (auto) | `buildFullCrudPrompt_auto_passesDesignReferenceIdsThroughGenerationOptions` | `CrudGenerationOptions`에 두 ID가 담겨 `crudOrchestrationService.orchestrate()`에 전달되는지, `generationDesignContextService`/`crudProgramMetadataService`는 호출 안 되는지 |
| `buildFullCrudPrompt` (claude) | `buildFullCrudPrompt_claude_resolvesScreenSpecificationAndPassesToPromptBuilder` | `crudProgramMetadataService.resolve()` → `generationDesignContextService.resolve()` → 반환된 `ScreenSpecification`이 `crudPromptBuilderService`에 전달되는지, `crudOrchestrationService`는 호출 안 되는지 |
| `buildMasterDetailPrompt` (auto) | `buildMasterDetailPrompt_auto_passesResolvedScreenSpecificationToOrchestrator` | `masterDetailOrchestrationService.orchestrate()`로 전달되는지 |
| `buildMasterDetailPrompt` (claude) | `buildMasterDetailPrompt_claude_passesResolvedScreenSpecificationToPromptBuilder` | `masterDetailService.buildMasterDetailPrompt()`로 전달되는지 |
| `buildBoardFeature` | `buildBoardFeature_passesDesignReferenceIdsThroughGenerationOptions` | `BoardGenerationOptions`에 두 ID가 담겨 `boardOrchestrationService.orchestrate()`에 전달되는지 |

**테스트 작성 중 바로잡은 점**: `buildMasterDetailPrompt`는 `buildFullCrudPrompt`와 달리 provider 분기 이전에 `generationDesignContextService.resolve()`를 **무조건** 한 번 호출한다(소스 238~240행). auto/claude 두 분기가 같은 `ScreenSpecification`을 공유하는 구조라서, "두 분기 모두 배선 필요"라는 원래 표현은 `buildFullCrudPrompt`에는 정확하지만 `buildMasterDetailPrompt`에는 다르게 적용된다. 또한 `buildBoardFeature`는 애초에 `llmProvider` 파라미터 자체가 없어 auto/claude 분기가 존재하지 않는다는 것도 확인했다(테스트 1개만 필요했던 이유).

추가로 남은 항목(이번 범위에서는 다루지 않음): `REVIEW_REQUIRED` 상태의 `ScreenSpecification`이 반환됐을 때 **auto 모드가 실제 파일 저장을 차단**하는지(§7 상태-생성정책 표)까지 확인하는 테스트는 아직 없다 — `GenerationDesignContextService`가 이 경우 `IllegalStateException`을 던진다는 것은 소스로 확인했지만(§5.2), `CrudPromptBuilderTool` 레벨에서 그 예외가 그대로 전파되는지(또는 잡혀서 에러 메시지로 변환되는지)는 아직 테스트되지 않았다.

---

### 우선순위 3 — `ThymeleafLayoutTool` ✅ 확인 완료, 추가 작업 불필요

원래 이 문서는 "298줄 규모 diff이니 회귀 위험이 있을 것"이라고 **diff 크기만 보고 추정**했다. 실제 `git diff`를 열어 확인한 결과는 추정과 달랐다.

**실제로 무슨 일이 있었는지 (git diff 기준):**

```text
1) 순수 리팩터
   context-common.xml / MyBatis 매퍼 스캐너 패치 로직(정규식 기반, 약 300줄)이
   ThymeleafLayoutTool에서 완전히 빠져나가 MyBatisRuntimeConfigurer 서비스로 이동.
   (KrdsStylesConfigurer/WarEntryPointConfigurer로 옮겨갔다는 이전 추정은 틀렸음 —
    ThymeleafLayoutTool은 이 두 서비스를 참조하지 않는다.)

2) 진짜 동작 변경 (리팩터가 아님)
   기존: WAR index.jsp → index.html 변환 + web.xml welcome-file 패치
        (convertWarWelcomeFileToIndexHtml, patchWebXmlWelcomeFile)
   변경 후: 정반대로 "WAR 진입점 파일(index.jsp/index.html/web.xml)은
        건드리지 않는다"는 동작으로 전환.
```

**테스트 파일도 이 변경에 맞춰 이미 정확히 갱신돼 있었다** (`ThymeleafLayoutToolTest.java` git diff 확인):

| 변경 | 내용 |
|---|---|
| 삭제 | `generateThymeleafLayout_convertsWarIndexJspToIndexHtml` (변환한다는 걸 검증하던 옛 테스트) |
| 추가 | `generateThymeleafLayout_preservesWarEntryPointFiles` (건드리지 않는다는 새 동작을 검증) |
| 추가 | `generateThymeleafLayout_writesGnbLogoImageFromClasspathAsset`, `generateThymeleafLayout_overwriteFalse_preservesExistingLogoImage` (로고 이미지 복사 신규 테스트) |
| 추가 | `generateThymeleafLayout_overwriteUnspecified_defaultsToOverwritingExistingLayoutFile` |

servlet-context.xml patch(등록/보존/이미 등록됨/component-scan 변형/`</beans>` 0개·2개 실패 케이스), context-common.xml MyBatis 위임, GNB 컴포넌트 4종, layout 5종, 메뉴 테이블명 커스터마이징까지 — 실제 `MyBatisRuntimeConfigurer`/`ThymeleafLayoutValidator`를 **mock 없이 진짜로 연결**해 파일 내용까지 검증하는 통합형 테스트로 이미 18개가 존재한다. `./gradlew test --tests ThymeleafLayoutToolTest` 실행 결과 전부 통과.

**결론**: `DesignReferenceTool`(테스트 부재)이나 `CrudPromptBuilderTool`(배선은 있으나 테스트 0건)과 달리, `ThymeleafLayoutTool`은 리팩터 시점에 테스트가 이미 함께 업데이트되어 추가로 작성할 것이 없다. diff 줄 수만으로 위험도를 판단하면 안 된다는 반례로 남겨둔다.

---

## 3. 외부 인프라가 있어야 검증되는 3가지 항목 (상세)

### 3.1 `validateGeneratedProjectBuild` — 실제 생성 대상 프로젝트 + 로컬 빌드 의존성 필요

**구현 위치:** `CodeValidatorTool.validateGeneratedProjectBuild(projectRootPath)` → `GeneratedProjectBuildValidator.validate()`

```text
호출자(Claude Desktop / MCP client)
        │ projectRootPath
        ▼
┌───────────────────────────────────────────────────────────┐
│ GeneratedProjectBuildValidator.validate()                   │
│                                                              │
│  1) allowBuildExecution == false ?                          │
│       └─▶ YES → BuildValidationReport.blocked(...)  ★기본값│
│                  "EGOV_ALLOW_BUILD_EXECUTION=true로 활성화"  │
│  2) 프로젝트 경로가 egov.output 허용 루트 하위인가?           │
│       └─▶ NO  → blocked("허용 경로 밖 프로젝트 빌드를 차단")  │
│  3) pom.xml / build.gradle(.kts) 탐지                        │
│       └─▶ 없음 → blocked("지원하는 빌드 파일이 없습니다")     │
│  4) mvn -o -DskipTests compile                                │
│     또는 gradle --offline compileJava 를 별도 프로세스로 실행 │
│       └─▶ timeout(기본 180s) 시 강제 종료                     │
└───────────────────────────────────────────────────────────┘
```

**왜 로컬 유닛 테스트로 끝낼 수 없는가**

1. `application.yaml`의 `egov.validation.allow-build-execution: ${EGOV_ALLOW_BUILD_EXECUTION:false}` — **기본값이 꺼져 있다.** 운영자가 명시적으로 환경 변수를 켜야만 실제 빌드 프로세스가 실행된다(생성된 코드가 임의 Gradle/Maven 플러그인을 실행할 수 있는 위험 때문 — 소스 주석 "pom.xml/build.gradle은 플러그인 코드를 실행할 수 있으므로 운영자 승인 전 비활성화").
2. `-o`(offline) 옵션을 쓰므로 **로컬 Maven/Gradle 캐시에 의존성이 이미 받아져 있어야** 한다. CI/신규 환경에서는 오프라인 컴파일 자체가 실패할 수 있다.
3. `mvn`/`gradle` 실행 파일이 PATH에 있어야 하고, 검증 대상은 **`generateCrudList` 등으로 실제 생성된 완성 프로젝트**여야 한다 — 즉 이 Tool 하나만 단독으로 테스트할 수 없고 "프로젝트 초기화 → CRUD 생성 → 이 Tool 실행"까지 앞단이 모두 성립해야 한다.

**로컬에서 부분적으로 검증 가능한 것**: `allowBuildExecution=false`일 때 `blocked` 리포트가 정확히 반환되는지, 허용 경로 밖 요청이 차단되는지, `pom.xml`/`build.gradle` 미탐지 시 blocked 처리되는지는 **실제 빌드 없이도** 단위 테스트 가능하다(`GeneratedProjectBuildValidatorTest.java`가 이미 존재 — 이 부분은 이미 커버된 것으로 추정, 실제 컴파일 성공/실패 판정만 외부 의존).

```bash
# 실제 빌드까지 검증하려면 (운영자 승인 후에만):
export EGOV_ALLOW_BUILD_EXECUTION=true
# projectRootPath는 initializeProject()+generateCrudList()로 실제 생성된 프로젝트 경로여야 함
```

---

### 3.2 `analyzeDesignReference` 실제 이미지 품질 평가 — 승인된 API 키/모델 필요

**구현 위치:** `DesignReferenceTool.analyzeDesignReference()` → `DesignReferenceAnalysisService.analyze()` → `VisionAnalysisClient` 구현체(`OpenAiVisionAnalysisClient` / `OllamaVisionAnalysisClient` / `DisabledVisionAnalysisClient`)

```text
app.design-vision.provider 설정값에 따라 활성 구현체가 갈림
                    │
        ┌───────────┼────────────┐
        ▼           ▼            ▼
   "disabled"    "openai"     "ollama"
        │           │            │
        ▼           ▼            ▼
 DisabledVisionAnalysisClient   OpenAiVisionAnalysisClient   OllamaVisionAnalysisClient
   (항상 오류/빈 결과)           (OPENAI_API_KEY 필요,        (로컬 멀티모달 모델 +
                                  실제 과금 발생)               GPU/메모리 필요)
```

**⚠️ 확인된 불일치 — 문서 vs 실제 설정값**

검토 문서 §12.1 표는 "`app.design-vision.provider=disabled` 기본값"이라고 적었지만, 실제 `application.yaml` 102~104행은 다음과 같다.

```yaml
design-vision:
  provider: ${DESIGN_VISION_PROVIDER:openai}   # 기본값이 disabled가 아니라 openai
```

즉 **환경변수 `DESIGN_VISION_PROVIDER`를 명시적으로 `disabled`로 설정하지 않는 한, `analyzeDesignReference()`는 기본적으로 OpenAI 클라이언트를 활성화하려고 시도한다.** `OPENAI_API_KEY`가 없으면 호출 시점에 실패한다. 이 부분은 문서(§10.5 망분리 게이트 취지)와 실제 설정 기본값이 어긋나 있으므로, 배포 전 재확인이 필요하다 — 별도 이슈로 다룰 만하다.

**왜 로컬 유닛 테스트로 끝낼 수 없는가**

1. `OpenAiVisionAnalysisClient`가 실제로 이미지를 얼마나 정확히 분석하는지(archetype 분류 정확도, 컴포넌트 탐지 precision/recall, 필드 역할 정확도 등 문서 §9.4 평가 항목)는 **실제 OpenAI API 호출 결과를 사람이 채점**해야 알 수 있다 — 이건 코드 정확성이 아니라 모델 품질 평가이므로 단위 테스트 대상이 아니다.
2. API 키 발급·과금 승인은 조직의 결정 사항(문서 §10.5 망분리 하드 게이트)이라 코드만으로 해결 불가.
3. 평가에는 "실제 디자인 참조 20~30개" 데이터셋(§9.4)이 필요 — 저장소에 그런 픽스처가 없다.

**로컬에서 검증 가능한 것**: `VisionAnalysisClient` 인터페이스를 mock하여 `DesignReferenceAnalysisService`가 응답을 올바르게 `DesignAnalysisResult`로 감싸는지, SHA-256 캐시 hit/miss 로직, `ReferencePathValidator`의 경로/MIME 검증, `DesignSpecValidator`의 enum/confidence 검증 — 이런 것들은 `DesignReferenceAnalysisServiceTest.java`가 이미 커버 중일 가능성이 높다(모델 자체의 "정확도"가 아니라 "파이프라인 배관"만 검증).

---

### 3.3 360/768/1280px 시각 회귀 — 브라우저 인프라 필요

```text
기준 이미지(baseline screenshot)
        │
        ▼
┌───────────────────────────────┐
│ 실행 대상 서버 기동             │  ← docker start egov-mysql,
│ (Spring Boot / 생성된 War)     │     redis-server, ollama 등 사전조건 충족
└───────────────┬─────────────────┘
                │ http://localhost:8080/...
                ▼
┌───────────────────────────────┐
│ 브라우저 자동화(Playwright/     │
│ Claude-in-Chrome 등)로          │
│ 360 / 768 / 1280px 각각 캡처    │
└───────────────┬─────────────────┘
                ▼
┌───────────────────────────────┐
│ 기준 이미지와 픽셀/구조 diff     │  ← 이 프로젝트에는 아직 없는 인프라
└───────────────────────────────┘
```

**왜 로컬 유닛 테스트로 끝낼 수 없는가**

1. **기준 이미지(baseline)가 아직 없다** — 검토 문서 §0차 완료 기준에 "주요 화면 시각 회귀 기준 이미지 확보"가 명시돼 있지만, §12.1 표의 0차 항목은 "부분 완료"로 표시돼 있고 "실제 브라우저 시각 비교"가 남은 작업으로 남아 있다.
2. 캡처하려면 **실행 중인 서버**(위 `CLAUDE.md`의 로컬 실행 사전조건 — MySQL, Redis, Ollama)가 필요하고, 생성된 CRUD/Board/MasterDetail 화면이 실제로 배포돼 라우팅까지 동작해야 한다.
3. 뷰포트별 반응형 breakpoint 비교는 headless 브라우저 자동화 도구(Playwright, `claude-in-chrome` 등)가 있어야 하며, 이 저장소의 `build.gradle`/테스트 스택에는 아직 그런 시각 회귀 프레임워크가 붙어 있지 않다(JUnit 5 + Thymeleaf 렌더링 검증 수준까지만 존재).

**로컬에서 검증 가능한 것(대체 수단)**: `validateThymeleafRendering`(`ThymeleafRenderValidator`)로 템플릿 구문·렌더링 오류는 브라우저 없이도 잡을 수 있다. 하지만 이는 "렌더링이 되는가"만 확인할 뿐 "디자인 기준과 시각적으로 일치하는가"는 확인하지 못한다 — 두 검사의 책임이 다르다는 점을 문서 §6.5도 명시하고 있다(`DesignSpecValidator` vs `ScreenSpecValidator` 책임 분리와 같은 맥락).

---

## 4. 종합 우선순위 요약

```text
로컬 유닛 테스트 (완료)
  1순위) DesignReferenceTool           ✅ DesignReferenceToolTest.java 신규 작성 (8개 통과)
  2순위) CrudPromptBuilderTool         ✅ 5개 테스트 추가 (전체 9개 통과)
         (buildFullCrudPrompt/
          buildMasterDetailPrompt/
          buildBoardFeature)
  3순위) ThymeleafLayoutTool            ✅ 기존 18개 테스트로 이미 충분 — 추가 작업 불필요

외부 자원 필요 (이번 커밋 범위에서 유닛 테스트로 종결 불가, 미착수)
  A) validateGeneratedProjectBuild  — EGOV_ALLOW_BUILD_EXECUTION=true +
                                       실제 생성 프로젝트 + 로컬 mvn/gradle 캐시
  B) analyzeDesignReference 품질 평가 — OPENAI_API_KEY 승인 +
                                       실제 디자인 참조 20~30개 데이터셋
                                       (+ provider 기본값이 문서 기술과 다름, 확인 필요)
  C) 360/768/1280px 시각 회귀        — 기준 이미지 미확보 + 브라우저 자동화 인프라 부재
```

A/B/C는 **"코드가 틀렸다"가 아니라 "검증에 필요한 외부 조건(권한, 자격 증명, 인프라)이 아직 이 저장소 밖에 있다"**는 점에서 1~3순위와 성격이 다르다.

**완료된 작업:**

1. ✅ `DesignReferenceToolTest.java` 신규 작성 (우선순위 1)
2. ✅ `CrudPromptBuilderToolTest.java`에 `designReferenceId`/`screenSpecificationId` 케이스 추가 — auto/claude 두 분기 모두 (우선순위 2)
3. ✅ `ThymeleafLayoutToolTest.java` 리팩터 후 회귀 재확인 — 이미 커버돼 있어 추가 작업 없음 (우선순위 3)
4. ✅ `REVIEW_REQUIRED`/미승인 `ScreenSpecification` 실패 경로 테스트 (아래 §2.4)

**남은 작업:**

1. `application.yaml`의 `design-vision.provider` 기본값(`openai`)이 의도된 것인지 확인 — 문서(§12.1, §10.5)가 말하는 "기본 비활성" 취지와 다르면 기본값을 `disabled`로 되돌리는 별도 논의 필요
2. A/B/C는 운영자 승인·API 키·브라우저 인프라 준비 후 별도 세션에서 진행
3. §2.4에서 발견한 **비대칭 예외 처리**(아래) — 실제로 고칠지는 별도 논의 필요, 이번 세션에서는 테스트로 현재 동작만 문서화했다

### 2.4 `REVIEW_REQUIRED`/미승인 화면명세 실패 경로 ✅ 완료 — 그리고 발견한 비대칭

`generationDesignContextService.resolve()`는 화면명세가 `APPROVED`가 아니면 `IllegalStateException`을 던진다(§5.2). 이 호출부가 **두 곳**에 있다는 걸 소스에서 확인했다.

```text
1) CrudOrchestrationService.orchestrate()  (auto 경로 — CrudPromptBuilderTool이 아니라 여기서 호출됨)
2) CrudPromptBuilderTool.buildFullCrudPrompt()의 claude 분기
```

두 곳 다 `try/catch` 없이 그대로 노출돼 있다. 이 메서드 안의 **다른** 검증 실패들 — 프로그램 메타데이터 `blocksGeneration()`, Controller URL alias 충돌, layout 미존재 — 은 전부 예외를 던지지 않고 `CrudOrchestrationResult`에 실패 메시지를 담아 **정상적으로 반환**한다(`formatResult()`가 그대로 사람이 읽을 수 있는 결과로 포맷). 그런데 화면명세 해석 실패만 유독 raw `IllegalStateException`으로 튄다 — 나머지 검증과 처리 방식이 다르다는 **비대칭**을 발견했다.

**✅ 완료 (2026-07-18):** 이 현재 동작(수정이 아니라 "지금 이렇게 동작한다"는 사실)을 테스트로 고정했다.

| 위치 | 추가된 테스트 | 검증 내용 |
|---|---|---|
| `CrudOrchestrationServiceTest.java` | `orchestrate_designSpecResolutionThrows_propagatesUncaughtWithoutPartialGeneration` | auto 경로에서 예외가 그대로 전파되고, `crudModelFactory`/`crudTemplateRenderer`/`codeService`가 전혀 호출되지 않아 부분 생성물이 남지 않는지 |
| `CrudOrchestrationServiceTest.java` | `orchestrate_approvedScreenSpecification_passedToSevenArgModelFactoryOverload` | `APPROVED` 명세가 정상 경로일 때 `crudModelFactory.fromSchema()`의 7-arg(ScreenSpecification 포함) 오버로드로 전달되는지 — 이전까지 이 브랜치 자체가 전혀 테스트되지 않았음 |
| `CrudPromptBuilderToolTest.java` | `buildFullCrudPrompt_claude_designSpecResolutionThrows_propagatesUncaught` | claude 분기에서도 동일하게 예외가 그대로 전파되고 `crudPromptBuilderService`가 호출되지 않는지 |

전체 테스트 스위트(`./gradlew test`) 통과 확인.

**판단은 보류한다**: 이 비대칭이 버그인지 의도된 설계(화면명세 문제는 "생성기 사용법 오류"에 가까워 다른 검증 실패와 성격이 다르다고 볼 수도 있음)인지는 이 세션에서 결정하지 않았다. 고칠지 여부는 별도 논의가 필요하며, CLAUDE.md 원칙("사용자가 명시적으로 수정을 요청하기 전까지 코드를 변경하지 않는다")에 따라 이번에는 현재 동작을 테스트로 문서화하는 데 그쳤다.

---

## 5. 실행 흐름 분기 — §1 다이어그램이 "항상 4단계 순서 호출"을 뜻하지 않는 이유

§1의 전체 그림은 **컴포넌트 의존 관계**(무엇이 무엇을 필요로 하는가)를 보여줄 뿐, "매 CRUD 생성마다 `analyzeDesignReference` → `createScreenSpecification` → `CrudPromptBuilderTool` → `CodeValidatorTool` 4개를 항상 이 순서로 4번 호출한다"는 뜻이 아니다. 실제로는 다음 세 가지 이유로 흐름이 갈라진다.

1. **`createScreenSpecification`은 별도 호출일 수도, `buildFullCrudPrompt` 내부에 흡수될 수도 있다** — 표준 매핑이 명확하면 별도 승인 절차 없이 한 번에 통과한다.
2. **화면명세가 `REVIEW_REQUIRED`면 중간에 사람 확인·수정·재승인 루프가 추가로 끼어든다.**
3. **`CodeValidatorTool`의 일부 기능은 이미 생성 파이프라인 안에서 자동 실행되고 있고, 일부는 별도로 수동 호출해야 한다** — 4개 Tool이 항상 사슬처럼 이어지는 게 아니다.

### 5.1 케이스 A — 단순 매핑 (자동 승인, 명시적 승인 단계 없음)

표준 단일 테이블 CRUD처럼 컬럼 타입까지 후보가 하나로 명확히 떨어지는 경우, `createScreenSpecification`을 **별도 MCP Tool로 호출하지 않아도** `buildFullCrudPrompt`가 내부에서 즉석으로 만들어 통과시킨다 (`GenerationDesignContextService.resolve()` 로직 — `screenSpecificationId`가 없고 `designReferenceId`만 있으면 그 자리에서 `screenSpecificationService.create()`를 호출하고, 결과가 바로 `APPROVED`면 그대로 진행).

```text
[사용자/Claude가 실제로 호출하는 Tool은 2개뿐]

  ① analyzeDesignReference(referencePath)
         │
         │ designAnalysisId
         ▼
  ② buildFullCrudPrompt(..., designReferenceId = designAnalysisId)
         │
         │  (Tool 내부, 별도 호출 아님)
         │  GenerationDesignContextService.resolve()
         │    └─ screenSpecificationService.create(...)  → 상태 = APPROVED  ✅ 즉시 통과
         │
         ▼
    FTL/HTML 생성 실행
         │
         │ (orchestrate() 내부, 별도 호출 아님 — §5.4 참고)
         ▼
    GeneratedCodeContractAuditor.audit() 자동 실행  ✅
```

이 케이스에서는 `createScreenSpecification`/`approveScreenSpecification`이라는 이름의 Tool을 사용자가 직접 부를 필요가 없다 — 다만 **내부적으로 동일한 서비스 로직이 실행되는 것**이지 그 단계 자체가 생략되는 건 아니다.

### 5.2 케이스 B — 애매한 매핑 (REVIEW_REQUIRED, 사람 확인·승인 루프 필요)

JOIN, 공통코드, 미매핑 필드, 파일 처리 등이 걸려 자동으로 확정할 수 없으면 `create()`가 `REVIEW_REQUIRED`를 반환한다. 이 상태로 `buildFullCrudPrompt`에 `designReferenceId`만 넘기면 `GenerationDesignContextService.resolve()`가 `IllegalStateException`을 던지며 **생성 자체가 실패**한다. 따라서 이 케이스에서는 4~5개 Tool을 실제로 순서대로 불러야 한다.

```text
  ① analyzeDesignReference(referencePath)
         │ designAnalysisId
         ▼
  ② createScreenSpecification(database, tableName, ..., designAnalysisId)   ← 반드시 별도 호출
         │
         │ 상태 = REVIEW_REQUIRED
         │ (미매핑 필드 / JOIN 후보 다수 / 공통코드 CODE_ID 미확정 등 이슈 목록 반환)
         ▼
  ③ (사람이 이슈 검토)
         │
         ▼
  ④ reviseScreenSpecification(spec)        ← 수정된 전체 명세를 새 버전으로 저장
         │ 상태 재검증
         ▼
  ⑤ approveScreenSpecification(screenSpecificationId)   ← 명시적 승인
         │ 상태 = APPROVED
         ▼
  ⑥ buildFullCrudPrompt(..., screenSpecificationId = ...)   ← designReferenceId 아님, screenSpecificationId 사용
         │
         ▼
    FTL/HTML 생성 실행
         │
         ▼
    GeneratedCodeContractAuditor.audit() 자동 실행  ✅
```

`buildFullCrudPrompt(..., screenSpecificationId=...)`에서 `screenSpecificationId`가 있으면 `GenerationDesignContextService.resolve()`는 재생성하지 않고 **조회만** 하며, 그 상태가 `APPROVED`가 아니면 즉시 실패한다(재확인 목적).

### 5.3 두 케이스를 가르는 기준

| 조건 | 결과 상태 | 필요한 실제 Tool 호출 수 |
|---|---|---|
| 단일 테이블, PK/타입까지 후보 1개, JOIN·공통코드·미매핑 없음 | `APPROVED` (즉시) | 2개 (`analyzeDesignReference` + `buildFullCrudPrompt`) |
| JOIN 후보 복수, 공통코드 CODE_ID 미확정, 미매핑 필드, 파일 처리 존재 | `REVIEW_REQUIRED` | 4~5개 (`analyzeDesignReference` → `createScreenSpecification` → [`reviseScreenSpecification`] → `approveScreenSpecification` → `buildFullCrudPrompt`) |
| 비전 분석 자체를 쓰지 않음(순수 DB 스키마 기반 CRUD) | 해당 없음 | `analyzeDesignReference`/`createScreenSpecification` 전부 생략, 기존처럼 `buildFullCrudPrompt`만 호출 |

### 5.4 `CodeValidatorTool` — 이미 자동 실행되는 부분과 수동 호출이 필요한 부분

§1 그림은 `CodeValidatorTool`을 생성 다음 단계에 뒀지만, 실제로는 **일부만 자동**이다. `CrudOrchestrationService`, `BoardOrchestrationService`, `MasterDetailOrchestrationService` 세 orchestrate() 모두 내부 4단계("코드 검증")에서 다음을 **이미 자동으로** 실행한다.

```text
// CrudOrchestrationService.java 199~211행 발췌 (설명용 요약, 원본 그대로가 아님)
validationSummary = codeValidatorService.validateDirectory(outputPath);   // validateGeneratedCodeDirectory와 동일 로직
List<String> contractFailures = generatedCodeContractAuditor.audit(outputPath);  // auditGeneratedQuality의 계약 감사 부분
if (!contractFailures.isEmpty()) {
    failed.addAll(...);   // 실패로 기록됨 — 생성 결과에 바로 반영
}
```

즉:

| CodeValidatorTool의 기능 | auto 생성 파이프라인에 자동 포함? |
|---|---|
| `validateGeneratedCodeDirectory` 로직(레이어별 규칙 검사) | ✅ 자동 (`codeValidatorService.validateDirectory`) |
| `auditGeneratedQuality`의 **계약 감사**(FreeMarker 잔존, Mapper `${}` 등) | ✅ 자동 (`generatedCodeContractAuditor.audit()`) — 실패 시 생성 결과에 실패로 반영됨 |
| `auditGeneratedQuality`의 **접근성 감사**(`auditAccessibility()`) | ❌ 수동 — orchestrate()는 `.audit()`만 호출, `.auditAccessibility()`는 호출하지 않음 |
| `validateThymeleafRendering` | ❌ 수동 — orchestrate()에서 호출되지 않음 |
| `validateGeneratedProjectBuild` | ❌ 수동 — 기본 비활성(`EGOV_ALLOW_BUILD_EXECUTION=false`), §3.1 참고 |

따라서 실무 흐름을 정확히 그리면:

```text
buildFullCrudPrompt(auto 모드) 한 번의 호출 안에
  ├─ (내부) 화면명세 승인 확인
  ├─ (내부) FTL 렌더링·파일 저장
  ├─ (내부) 레이어 규칙 검증          ← CodeValidatorTool.validateGeneratedCodeDirectory와 동일 로직, 자동
  └─ (내부) 생성 계약 감사            ← CodeValidatorTool.auditGeneratedQuality의 절반, 자동
                │
                ▼ (여기서부터는 사용자가 추가로 명시 호출해야 함)
  auditGeneratedQuality(...)         ← 접근성 감사까지 포함해 다시 보고 싶을 때
  validateThymeleafRendering(...)    ← 실제 템플릿 엔진 렌더링까지 확인하고 싶을 때
  validateGeneratedProjectBuild(...) ← 운영자 승인 후 실제 컴파일까지 확인하고 싶을 때
```

**결론**: `auto` 생성 한 번만으로도 기본적인 계약 검증까지는 이미 끝나 있다. `CodeValidatorTool`을 "생성 후 항상 별도로 불러야 하는 4번째 단계"로 이해하면 틀리다 — 이미 자동으로 실행된 부분(레이어 규칙, 계약 감사)과, 필요할 때만 추가로 부르는 부분(접근성 감사, 실제 렌더링, 실제 컴파일)을 구분해야 한다.
