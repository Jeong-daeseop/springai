# Figma/Claude Design → DESIGN.md → initializeProject()/CRUD 반영 — 구현계획

> 2026-09-03, 코드 실측 기준 작성. **구현 여부는 결정되지 않았으며, 이 문서는 명세만 담는다.**
> 요청한 흐름: "project 시작 시 Figma 또는 Claude Design을 통해 DESIGN.md를 만들고,
> `initializeProject()` 단계 시작 시, `CrudPromptBuilderService` 등 화면 생성 시 DESIGN.md
> 기준으로 생성한다." 세 단계로 나눠 각각 실제 클래스·메서드에 정확히 어디를 손대야 하는지
> 정리한다.

---

## 0. 이 문서가 나온 배경 — 이전 결론의 정정

이전 검토(`Figma_ClaudeDesign_DESIGN_md_연계_영향검토.md` §6)는 "DESIGN.md는 화면마다 다른
Figma 디자인을 담을 수 없는 전역 정적 파일이라 부적합하다"고 결론 냈다. 이 결론은 **Figma
디자인 = 화면별 목업**을 전제로 한 것이었다.

이번 요청은 시점을 "project 시작 시 1회"로 고정한다 — 이는 화면별 목업이 아니라 **Figma
디자인 시스템/스타일 가이드 페이지**(회사 전역에 하나뿐인 성격)를 소스로 쓰는 것과 정확히
일치한다. 이 재구성으로 이전 §6의 "범위 불일치" 반론은 해소된다. 다만 실측 결과 아래 3단계
모두에 실제 신규 구현이 필요하다.

---

## 1. 전체 흐름

```
[1단계] Figma(디자인 시스템 페이지) 또는 Claude Design
            ↓ (신규) DESIGN.md 내보내기
        DESIGN.md (project 루트에 배치될 콘텐츠)
            ↓
[2단계] initializeProject(..., designSystemProfileId)
            ↓ (신규) DESIGN.md를 프로젝트 루트에 먼저 기록 → 정적 자산 patch
        생성된 프로젝트 (DESIGN.md + styles.css에 토큰 반영됨)
            ↓
[3단계] CRUD 생성 (auto: GenerationStageProcessor / claude: CrudPromptBuilderService)
            ↓ (신규) DesignMdRuleLoader.load(outputPath) → CompanyDesignTokenResolver.resolve(...)
        화면별 생성 결과에도 동일 토큰 참조 반영
```

---

## 2. 1단계 — Figma/Claude Design → DESIGN.md 내보내기

### 2-1. Figma 경로 (권장) — raw 값이 아니라 이미 검증된 Variable 이름을 재사용

Figma 프레임을 다시 분석(`analyzeFigmaReference()`)해 raw RGBA를 뽑는 방식은 쓰지 않는다.
이유: `FigmaDesignSpecMapper`는 `boundVariables`를 캡처하지 않으므로(영구 규칙,
`feedback_figma-must-capture-variable-reference` 메모리 참고), raw 값만으로는 "표준
Variable 참조"와 "디자이너가 우연히 비슷하게 칠한 값"을 구분할 수 없다.

대신 이미 신뢰도가 검증된 소스를 그대로 재사용한다 — Figma "Publish"로 게시된 Variable이
이미 DB에 있다(`ComponentRegistrySyncService` → `AI_COMPONENT_REGISTRY`).

**신규 클래스**: `ComponentRegistryToDesignMdExporter`

```java
package com.krdevops.springai.service.thymeleaf;

public class ComponentRegistryToDesignMdExporter {
    /**
     * @param profileId DesignSystemProfile 식별자
     * @return DESIGN.md 8개 카테고리 YAML frontmatter 문자열 (schemaVersion: "1.0" 고정)
     */
    public String export(String profileId) {
        List<VariableRegistryEntry> vars = designSystemQueryService.findVariables(profileId);
        // colors/typography/spacing/radius 카테고리별로 variableName → variableKey 매핑해 YAML 조립
        // DesignMdRuleLoader.SUPPORTED_CATEGORIES(8개)만 사용, FORBIDDEN_KEYWORDS는 원천적으로 안 생성
    }
}
```

- 입력: `DesignSystemQueryService`/`ComponentRegistryRepository`가 이미 갖고 있는
  `VariableRegistryEntry`(`variableKey`/`variableName`/`collectionKey`/`collectionName`/
  `resolvedType`/`publishStatus`) — 이미 사람이 Figma UI에서 명시적으로 게시한 값이라
  `boundVariables` 미캡처 문제를 원천적으로 피해간다.
- 출력: `DESIGN_md_KRDS_템플릿.md`와 동일한 YAML 구조(`colors`/`typography`/`spacing`/
  `radius`/`layout`/`voice`/`forbidden`), 값은 항상 `variableName`(CSS 변수 이름) — raw
  hex 절대 넣지 않음.
- **트레이드오프**: 이렇게 만든 DESIGN.md는 사실상 `ComponentRegistry`의 파생 캐시가 된다.
  두 값이 벌어지지 않도록 "DESIGN.md는 내보내기 시점의 스냅샷이며, `ComponentRegistry` 갱신 시
  재실행이 필요하다"는 점을 파일 상단 주석으로 명시해야 한다(동기화 어긋남 위험, §5).

### 2-2. Claude Design 경로 — 범위 제외

`.dc.html` 캔버스 전용 파서가 없어 PNG export 후 `analyzeDesignReference()`(LLM 추론,
`VisionAnalysisClient`)만 가능하다. 이 경로는 raw 값만 뽑고 재시도도 없어 Figma보다 신뢰도가
낮다. **1차 구현 범위에서 제외**하고, 필요해지면 별도 검토 문서로 분리한다(2-1과 동일하게
"이름 캡처" 문제부터 해결해야 하므로 난이도가 더 높음).

---

## 3. 2단계 — `initializeProject()`에 DESIGN.md 선(先)반영

### 3-1. 현재 상태 (실측 재확인)

`ProjectInitializrTool.initializeProject(projectName, groupId, artifactId, packageName,
buildTool, projectType, egovVersion, outputPath, viewType)`에는 DESIGN.md/프로필 관련
파라미터가 없다. `FilePlanFactory`의 KRDS 3파일(`styles.css`/`_ds_bundle.css`/`krds.min.js`)은
`ClassPathTemplateLoader.load(name)` 단일 인자 버전만 호출해 `${...}` 치환을 전혀 거치지 않는다
(실측 완료, `DefaultStaticTemplateRenderer.java:68-80`).

### 3-2. 필요한 변경

**① 신규 파라미터**: `initializeProject(..., @Nullable String viewType, @Nullable String designSystemProfileId)`

**② DESIGN.md 선배치** — `FilePlanFactory`의 파일 목록 맨 앞에 조건부 항목 추가:

```java
if (designSystemProfileId != null) {
    plans.add(0, FilePlan.of("DESIGN.md", RESOURCE,
            () -> componentRegistryToDesignMdExporter.export(designSystemProfileId)));
}
```

**③ 정적 자산 patch는 `_ds_bundle.css.tpl`/`krds.min.js.tpl` 자체를 건드리지 않는다** —
이 두 파일은 KRDS 원본 그대로 유지해야 업스트림 업데이트 추적이 쉽다. 대신 이미 있는
`KrdsStylesConfigurer`의 marker-patch 패턴을 그대로 재사용해 `styles.css`에만 새 블록을
추가한다:

```java
// KrdsStylesConfigurer에 신규 메서드 추가 (기존 ensureBoardCrudStyles/ensureTableDensityStyles와 동일 패턴)
public CssPatchResult ensureDesignMdTokenStyles(String outputPath, ResolvedDesignTokens tokens) {
    // DESIGN_MD_TOKEN_START_MARKER ~ END_MARKER 사이에
    // :root { --krds-button-bg-color: var(--krds-color-light-secondary-60); } 형태로
    // "재배정(alias)"만 patch — 항상 변수 참조, raw 값 금지
}
```

**④ 호출 순서** — `ProjectInitializrService.initializeProject()`에서 `FilePlanFactory` 실행
(DESIGN.md 포함 전체 파일 생성) 완료 **직후**, `designSystemProfileId`가 있을 때만:

```
DesignMdRuleLoader.load(생성된 프로젝트 루트)      // 방금 ①에서 쓴 DESIGN.md를 다시 읽음
  → CompanyDesignTokenResolver.resolve(profileId, appliedDesignRules)
  → KrdsStylesConfigurer.ensureDesignMdTokenStyles(outputPath, resolvedTokens)
```

DESIGN.md를 쓰자마자 다시 읽는 게 비효율적으로 보일 수 있으나, `DesignMdRuleLoader`가 이미
파일 기반 계약(파싱·검증·`forbidden` 키 차단)을 갖고 있으므로 이 계약을 그대로 재사용하는 게
중복 로직을 만드는 것보다 안전하다.

---

## 4. 3단계 — CRUD 생성 반영 (auto + claude)

### 4-1. claude 경로 — `CrudPromptBuilderService`

이전 턴에서 확정한 삽입 지점 그대로다.

- **파라미터**: 가장 깊은 오버로드(`buildFullCrudPrompt(..., ScreenSpecification screenSpecification)`,
  현재 line 207-212)에 `String designSystemProfileId` 추가 + 상위 오버로드 체인에 기본값
  `null` 전달 오버로드 유지(하위호환).
- **호출 위치**: line 236 `KRDS_ASSET_WARNING` 체크 직후.
- **출력**: line 242-250(`[디자인 기하 정보 사용 규칙]`)과 같은 스타일의 신규 블록
  `[KRDS 디자인 토큰 — DESIGN.md 기준]`, 값은 항상 변수 이름.
- DESIGN.md/profileId가 없으면 `KRDS_ASSET_WARNING`과 동일하게 조용히 블록을 생략(non-fatal).

### 4-2. auto 경로 — 신규 `GenerationStageProcessor`

```java
package com.krdevops.springai.service.generation.crud;

@Component
public class CrudDesignMdCssProcessor implements GenerationStageProcessor {
    static final String ID = "crudDesignMdCssProcessor";
    // stage() = PRE_WRITE, order = 95 (KrdsAssetVerificationProcessor=90 다음, density=100 이전)
    // supports(): designSystemProfileId 컨텍스트가 있을 때만 true
    // process(): DesignMdRuleLoader.load(outputPath) → CompanyDesignTokenResolver.resolve(...)
    //            → KrdsStylesConfigurer.ensureDesignMdTokenStyles(outputPath, tokens)
    //            실패해도 FailurePolicy.CONTINUE (자산 검증과 달리 이건 스타일 개선이지 차단 사유가 아님)
}
```

`CrudGenerationPlanner.processorSteps()`에 `new ProcessorStep(CrudDesignMdCssProcessor.ID,
GenerationStage.PRE_WRITE, 95, FailurePolicy.CONTINUE)` 추가.

**2단계와 로직 공유**: `KrdsStylesConfigurer.ensureDesignMdTokenStyles()`는 marker 기반
멱등 patch이므로 `initializeProject()` 때 이미 한 번 patch됐어도 CRUD 생성 때 다시 실행해
안전하다(DESIGN.md가 그사이 갱신됐다면 최신값으로 재patch됨). 즉 하나의 메서드를 2단계·3단계
양쪽에서 공유한다 — 로직 중복 없음.

---

## 5. 위험 및 제약 (기존 계약에서 못 벗어나는 부분)

| 위험 | 내용 | 완화 |
|---|---|---|
| 이름만, 값 아님 | `CompanyDesignTokenResolver`는 CSS 변수 **이름**만 다룬다. KRDS에 없는 신규 브랜드 hex 색은 이 흐름으로 반영 불가 | 범위 밖으로 명시. 필요 시 별도 검토(리졸버 계약 자체 확장) |
| DESIGN.md·ComponentRegistry 동기화 어긋남 | 1단계 내보내기 이후 `ComponentRegistry`가 갱신되면 DESIGN.md는 스냅샷인 채로 남는다 | DESIGN.md 파일 상단에 내보내기 시각·profileId 기록, "재실행 필요" 안내 문구 포함 |
| Claude Design 경로 미지원 | PNG 기반 LLM 추론은 신뢰도 낮고 `boundVariables` 캡처 불가 | 1차 구현 범위에서 명시적 제외 |
| auto/claude 반영 시점 차이 | auto는 CRUD 생성마다 patch 재실행(최신), claude는 프롬프트에 텍스트로만 안내(실제 반영은 Claude의 코드 작성에 의존) | claude 경로 결과물은 "안내를 따랐는지"에 대한 사후 검증 수단이 없다는 기존 한계 그대로 문서화 |
| `initializeProject()` 파라미터 증가 | `designSystemProfileId` 추가로 기존 호출자 영향 없어야 함 | `@Nullable`, 기본값 없으면 기존 동작과 완전히 동일 |

---

## 6. 범위 외 (이 문서에서 다루지 않음)

- Claude Design(PNG export) 기반 DESIGN.md 자동 생성 — §2-2에서 명시적 제외
- raw hex 값 직접 주입(신규 브랜드 색) — `CompanyDesignTokenResolver` 계약 자체 변경 필요
- DESIGN.md 수동 작성 경로(`DESIGN_md_KRDS_템플릿.md`) — 이미 존재, 본 문서와 병행 가능(1단계를 건너뛰고 바로 2단계부터 시작하는 경우)

---

## 7. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `tools/ProjectInitializrTool.java` | `initializeProject()` — 신규 `designSystemProfileId` 파라미터 대상 |
| `service/ProjectInitializrService.java` | `FilePlanFactory` 실행 후 DESIGN.md 재로드·patch 호출 지점 |
| `service/initializr/FilePlanFactory.java` | DESIGN.md를 파일 목록 맨 앞에 조건부 추가할 지점 |
| `service/initializr/template/DefaultStaticTemplateRenderer.java` | `stylesCss()`/`dsBundleCss()`/`krdsJs()` — 원본 그대로 유지, 손대지 않음 |
| `service/KrdsStylesConfigurer.java` | `ensureDesignMdTokenStyles()` 신규 메서드 추가 위치, 기존 marker-patch 패턴 재사용 |
| `service/CrudPromptBuilderService.java` | `buildFullCrudPrompt()` line 236 부근 — claude 경로 반영 지점 |
| `service/generation/crud/CrudGenerationPlanner.java` | `CrudDesignMdCssProcessor` 등록 지점(order=95) |
| `service/thymeleaf/DesignMdRuleLoader.java` | DESIGN.md 파싱 — 8개 카테고리·금지 키워드 계약 재사용 |
| `service/thymeleaf/CompanyDesignTokenResolver.java` | `resolve(profileId, appliedDesignRules)` — 이름만 반환하는 계약 |
| `docs/tool-reference/DESIGN_md_KRDS_템플릿.md` | DESIGN.md 수동 작성 템플릿 — 2단계부터 시작할 때 참고 |
| `docs/tool-reference/CRUD_생성_KRDS_자산_검증_구현계획.md` | `GenerationStageProcessor`/marker-patch 패턴의 선행 구현 사례 |
| `docs/figma/artifacts/SpringAI_Architecture_Target_Pipeline.html` §17 | 전체 아키텍처 상 이 흐름이 들어갈 위치 |
