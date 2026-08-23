# Anima·Locofy·Supernova 5개 공통 축 기반 SpringAI 벤치마크 검토

> 작성일: 2026-08-23  
> 검토 목적: 각 제품의 대표 강점만 분류하지 않고, Anima·Locofy·Supernova를 동일한 5개 축인 디자인 시스템, 시각 해석, 프런트엔드 코드, Prototype, Handoff에서 조사하여 SpringAI의 현재 방향과 구현 수준을 벤치마크한다.  
> 활용 목적: SpringAI가 제대로 된 방향으로 구현되고 있는지 확인하고, 제품 기능을 그대로 복제하지 않으면서 실제 구현 가능한 발전 계약과 우선순위를 도출한다.  
> 조사 원칙: 공식 사이트·공개 문서를 우선 근거로 사용하고, 생산성 배수·Pixel-perfect·Production-ready 같은 제품 홍보 표현은 구현 근거로 사용하지 않는다.

---

## 1. 종합 결론

SpringAI의 전체 방향은 올바르다.

특히 다음 책임 분리는 세 제품의 일반적인 Design-to-Code 흐름보다 SpringAI 목표에 더 적합하다.

```text
Figma
= 시각 구조·Layout·Component·Token·Interaction 후보

Controller·VO·DB Schema
= 업무 Field·Route·Validation·Permission 기준

APPROVED ScreenSpecification
= 시각 계약과 업무 계약의 결합·승인 경계

FreeMarker Renderer
= 결정론적 Thymeleaf 생성

Validation Gate
= Binding·Build·Render·A11y·Visual 기준의 운영 가능 여부 판정
```

다만 영역별 구현 성숙도에는 차이가 있다.

| 비교 영역 | SpringAI 판단 | 핵심 평가 |
|---|---|---|
| 디자인 시스템 | 강한 기반, 운영 연결은 부분적 | Registry·승인은 강하지만 문서·검색·Token 배포가 분산돼 있다. |
| 시각 해석 | 방향은 정확하나 중간 계약이 얕음 | `UiDesignSpec v1`이 실제 Figma 의미와 근거를 충분히 보존하지 못한다. |
| 프런트엔드 코드 | 차별점이 명확하고 방향도 적절 | 업무 Binding·보안·Atomic Apply는 강하지만 증분 생성·Fragment Mapping 보강이 필요하다. |
| Prototype | 상대적으로 가장 약함 | Preview와 검증은 존재하지만 실행·Flow·Evidence가 하나로 묶이지 않는다. |
| Handoff | 시스템 간 전달은 강하고 사람 대상 전달은 약함 | Operation·Artifact는 강하지만 검토자가 소비할 통합 Handoff Package가 부족하다. |

가장 먼저 보강할 항목은 다음 세 가지다.

1. `UiDesignSpec v2`와 명시적인 Design IR
2. 실행·검증 결과를 묶는 `PreviewEvidenceBundle`
3. 사람과 Agent가 함께 소비하는 `ScreenHandoffBundle`

---

## 2. 비교 기준과 해석 원칙

### 2.1 공통 비교축

세 제품과 SpringAI를 다음 축에서 모두 비교한다.

1. 디자인 시스템
2. 시각 해석
3. 프런트엔드 코드
4. Prototype
5. Handoff

대표 강점만으로 제품을 분류하지 않는다. 각 제품이 동일 영역을 어떤 입력, 산출물, 완료 기준, 운영 방식으로 처리하는지 확인한다.

### 2.2 비교 시 구분할 개념

| 용어 | 일반적인 Design-to-Code 의미 | SpringAI 의미 |
|---|---|---|
| Binding | Component Prop·Prototype Data 연결 | Controller Model·VO·Field·Validation 연결 |
| Component | React·Vue 등 UI Component | Registry Logical Type·Figma Component Set·Thymeleaf Fragment |
| Preview | 실행 화면과 시각 결과 확인 | 승인 전 계약 Preview와 Render Evidence |
| Production-ready | 실행 가능한 프런트엔드 코드 | 업무 Binding·보안·Build·Render Gate 통과 |
| Handoff | 문서·코드·URL 전달 | 승인 계약·Diff·Evidence·허용된 다음 작업 전달 |
| Sync | 디자인 변경을 코드에 반영 | Source Revision·Preview·승인·Conflict·Atomic Apply |

같은 용어를 사용하더라도 완료 기준이 다르므로 기능 이름만으로 동등성을 판단하지 않는다.

---

## 3. 전체 비교표

| 비교 영역 | Supernova | Locofy | Anima | SpringAI |
|---|---|---|---|---|
| 디자인 시스템 | Token·Component·Asset·문서·Version·Health 운영 | Figma Component와 Code Component·Prop 연결 | Design System 기반 화면·Storybook·Prototype 생성 | Profile·Registry·Token·승인·생성 Gate |
| 시각 해석 | Figma 디자인 데이터를 관리 모델로 동기화 | Layout·Responsive·Component·Props·Interaction 추론 | Auto Layout·Breakpoint·Fallback·다중 화면 통합 | Figma 분석→UiDesignSpec→ScreenSpecification |
| 프런트엔드 코드 | Token·Asset 중심 다중 Target Export | React·Vue·Angular·HTML 등 화면 코드 생성 | React·Vue·HTML과 다양한 Styling 출력 | FreeMarker 기반 서버 Thymeleaf·eGovFrame 생성 |
| Prototype | Component Playground·AI Prototype·PRD 연결 | Live Responsive·Interactive Prototype | 실행·수정·공유 가능한 Versioned Playground | Figma Preview·Thymeleaf Fixture·Screenshot·Browser Gate |
| Handoff | 문서·Version·Search·Pipeline·MCP | MCP·CLI·GitHub·VS Code·Smart Merge | MCP·Git Repository·Playground URL | APPROVED Specification·Operation·Artifact·Evidence·Apply |

---

## 4. 디자인 시스템 벤치마크

### 4.1 Supernova

Supernova는 디자인 시스템을 조직 전체의 운영 데이터로 관리한다.

- Figma Variable·Component·Asset Import
- Token Alias·Theme·Brand
- Component Health·Status·Custom Metadata
- Storybook 연결
- Token·Component·Asset과 동적으로 연결된 문서
- 불변 Version Snapshot
- Token·Asset Code Pipeline
- Git Repository·Branch·PR 전달
- 디자인 시스템 검색과 AI 질의
- 사용·문서 Analytics

과거 Version의 Token·Component·Asset은 읽기 전용 Snapshot으로 유지된다. Version 생성은 비동기로 수행되고 현재 Draft와 게시 Version을 구분한다.

근거:

- [Supernova Design Systems 101](https://learn.supernova.io/latest/design-systems/design-systems-101-udW7KfEC)
- [Supernova Versioning](https://learn.supernova.io/latest/design-systems/features/versioning-oIUhGTVL)
- [Supernova Browse Design System](https://learn.supernova.io/latest/design-systems/features/ask/browse-design-system-PDZESPZj)
- [Supernova Code Automation](https://www.supernova.io/guides/scaling-your-product-development-with-supernova/5-scaling-with-code-automation/automating-design-token-and-asset-delivery)

### 4.2 Locofy

Locofy는 디자인 시스템을 화면 코드 생성 과정에서 실제 코드 컴포넌트를 재사용하는 기준으로 활용한다.

- 저장소의 Code Component 등록
- Figma Component↔Code Component Mapping
- Figma Property↔Code Prop Mapping
- Variant·Boolean·Text·Instance Swap 처리
- Figma Label과 Code Enum 값 변환
- Layer Path 기반 Prop 추출
- Preview Fixture 값
- Mapping 설정 파일의 Git 버전 관리
- Designer와 Developer의 공동 Mapping 검토

근거:

- [Locofy Manual Prop Mapping](https://www.locofy.ai/docs/cli/design-system/manual-prop-mapping/)
- [Locofy Custom Code Components](https://www.locofy.ai/docs/mcp/design-system/overview/)

### 4.3 Anima

Anima는 Figma Design System을 팀이 Prompt 기반 화면 생성에 사용할 수 있는 실행 가능한 Component Catalog로 변환한다.

- 선택한 Component별 Storybook Entry
- Variant·State·Prop·Control 유지
- Figma Variable과 Style 반영
- 팀 공유 Design System Playground
- Design System을 선택해 새 화면과 Flow 생성

근거:

- [Anima Add Figma Design System](https://docs.animaapp.com/docs/add-figma-design-system)

### 4.4 SpringAI 현재 구현

`ComponentRegistryEntry`는 다음 정보를 관리한다.

- Figma `componentSetKey`
- 게시 상태와 Lifecycle
- Replacement와 Alias
- Variant와 Property
- Semantic Role
- 지원 Platform
- Code Component
- Documentation URL
- Contract Version

`DesignSystemProfile`은 다음을 관리한다.

- Profile ID·Version
- Registry Version
- Figma Library File Key
- Component·Variable Binding
- Layout Policy
- 제한적 Override
- Draft·Review·Approved·Published 상태

그 밖에 다음 기반이 존재한다.

- Registry Sync·Diff
- Figma Inventory Drift
- Breaking Change 분석
- Rollback
- Property Drift
- 승인되지 않은 Registry 차단
- Component Swap
- Variant Rule
- Published Snapshot V3

코드 근거:

- `src/main/java/com/krdevops/springai/model/designsystem/ComponentRegistryEntry.java`
- `src/main/java/com/krdevops/springai/model/designsystem/DesignSystemProfile.java`
- `src/main/java/com/krdevops/springai/service/designsystem/ComponentRegistrySyncService.java`
- `src/main/java/com/krdevops/springai/service/designsystem/ComponentRegistryBreakingChangeAnalyzer.java`
- `src/main/java/com/krdevops/springai/service/designsystem/ComponentRegistryDriftReporter.java`
- `src/main/java/com/krdevops/springai/service/designsystem/ComponentRegistryRollbackService.java`

### 4.5 방향성 판정

#### 제대로 구현 중인 부분

- Figma Component를 그대로 신뢰하지 않고 Published Registry를 통과시킨다.
- Lifecycle과 Replacement를 생성 Gate에 사용한다.
- 사람 승인과 Rollback을 지원한다.
- Component·Variable Override 범위를 제한한다.
- Source Revision과 Registry Version을 추적한다.
- 실제 Figma Inventory와 승인 Registry의 차이를 검사한다.

#### 발전이 필요한 부분

- Profile·Registry·Token·문서·Asset·Code Artifact를 묶는 상위 Snapshot
- Component Health 종합 View
- 실제 Thymeleaf Fragment Parameter와 Figma Property의 명시적 Mapping
- Token·Asset 대상별 Export Manifest
- 디자인 시스템 문서와 Search/RAG
- Component Variant를 실행해 보는 내부 Playground

### 4.6 구현 제안

```text
DesignSystemKnowledgeSnapshot
├─ profileVersion
├─ registryVersion
├─ tokenVersion
├─ componentMappingVersion
├─ assetManifestVersion
├─ documentationVersion
├─ codeArtifactVersion
└─ publishStatus
```

```text
DesignCodeComponentMapping
├─ logicalType
├─ figmaComponentSetKey
├─ thymeleafFragment
├─ propertyMappings[]
├─ slotMappings[]
├─ fixtureModel
├─ sourceRevision
└─ status
```

초기 우선순위는 Knowledge Portal보다 `DesignCodeComponentMapping`이 높다. SpringAI의 최종 산출물은 문서가 아니라 동작하는 Thymeleaf 화면이기 때문이다.

---

## 5. 시각 해석 벤치마크

### 5.1 Supernova

Supernova는 화면 Layout을 DOM으로 변환하기보다 다음 디자인 시스템 정보를 구조화하고 동기화한다.

- Variable·Token
- Component·Property
- Asset
- Theme·Brand
- Figma Component Preview
- Storybook 구현

따라서 시각 해석 엔진보다 디자인 데이터의 Source of Truth 관리 계층에 가깝다.

### 5.2 Locofy

Locofy는 Figma 입력을 다음 관점에서 분석한다.

- Code-ready Design 사전 검사
- Auto Layout·Group 구조 최적화
- Button·Input·Image Tagging
- Layout·Spacing·Responsive 해석
- Breakpoint Frame 자동 연결
- Figma Prototype Interaction 확인
- 반복 Component·Props 분리
- Layer Name 개선
- Code-ready Structure 생성

결과 품질은 Figma Auto Layout, Layer 구조, Component Property, Breakpoint Frame 이름과 일관성에 영향을 받는다.

근거:

- [Locofy Lightning Flow](https://www.dev.locofy.ai/docs/plugin/lightning/)
- [Locofy Multiple Breakpoints](https://www.dev.locofy.ai/docs/plugin/lightning/multiple-breakpoints/)
- [Locofy Optimizing Designs](https://www.dev.locofy.ai/docs/plugin/optimising-designs/)

### 5.3 Anima

Anima 공개 문서는 다음 대응을 설명한다.

| Figma | Code 출력 |
|---|---|
| Frame·Auto Layout | HTML Structure·Flexbox·Grid |
| Variable | CSS Custom Property |
| Component | React Component |
| Variant | Component Prop |
| Constraint | Responsive Behavior |
| Image·Icon | 최적화 Asset |

CSS로 동일하게 표현할 수 없는 효과에는 CSS 근사, SVG, Raster Image, 효과 제거 등의 Fallback을 적용한다.

근거:

- [Anima Starting from Figma](https://docs.animaapp.com/docs/starting-from-figma)
- [Anima Figma Translation and Fallback](https://support.animaapp.com/en/articles/6228806-how-anima-translates-figma-design-settings-into-code)
- [Anima Responsiveness and Breakpoints](https://docs.animaapp.com/docs/1-responsive-pages)

### 5.4 SpringAI 현재 구현

현재 `UiDesignSpec v1`은 다음 구조다.

```text
archetype
layout
components
actions
fieldHints
tokens
interactions
uncertainties
```

내부 타입은 비교적 단순하다.

```text
ComponentSpec(type, semanticFields)
ActionSpec(type, importance)
FieldHint(id, label, role, control, confidence)
InteractionSpec(trigger, result)
Map<String, String> tokens
List<String> uncertainties
```

`FigmaUiDesignSpecQualityEvaluator`는 현재 다음 다섯 항목의 존재 여부를 평가한다.

- Archetype
- Layout
- Component
- Field Hint
- Uncertainty 또는 Interaction Evidence

5개 중 3개가 존재하면 통과한다.

그 밖에 다음 구현이 있다.

- `FigmaContextAnalyzer`
- `FigmaDesignSpecMapper`
- `FigmaUiDesignSpecQualityEvaluator`
- `MultiViewportComponentMatcher`
- `FigmaVisualComparisonService`
- `FigmaStyleExtractor`
- `ScreenSemanticNormalizer`
- `ComponentRegistryResolver`

코드 근거:

- `src/main/java/com/krdevops/springai/model/design/UiDesignSpec.java`
- `src/main/java/com/krdevops/springai/service/FigmaDesignSpecMapper.java`
- `src/main/java/com/krdevops/springai/service/FigmaUiDesignSpecQualityEvaluator.java`
- `src/main/java/com/krdevops/springai/service/MultiViewportComponentMatcher.java`

### 5.5 방향성 판정

#### 올바른 부분

- Figma를 업무 Binding 기준으로 사용하지 않는다.
- Figma 분석 결과를 `UiDesignSpec`으로 분리한다.
- Field 후보에 Confidence를 둔다.
- Uncertainty를 모델에 포함한다.
- Multi-viewport Matching과 Visual Comparison 기반이 있다.
- Registry Resolution을 시각 후보와 분리한다.

#### 가장 큰 부족

`UiDesignSpec v1`은 Design IR 역할을 하기에는 정보가 부족하다.

현재 구조만으로는 다음을 충분히 보존하기 어렵다.

- 원본 Node와 해석 결과의 연결
- Auto Layout·Constraint 해석 근거
- Component Registry Resolution
- Variant·Property Mapping
- Token Alias와 Source Variable
- Viewport별 구조 차이
- Fallback Strategy
- Interaction 대상 Node
- 추론에 사용한 Evidence
- Issue Severity
- 전체 및 항목별 Confidence

Quality Evaluator 역시 정보의 존재 여부를 검사할 뿐 계약의 상호 일관성을 깊게 검사하지 않는다.

### 5.6 구현 제안: `UiDesignSpec v2`

```text
UiDesignSpecV2
├─ source
│  ├─ fileKey
│  ├─ nodeIds[]
│  └─ sourceRevision
├─ qualityReport
├─ designTree
├─ layoutPolicies[]
├─ responsivePolicy
├─ responsiveStructure
├─ componentCandidates[]
├─ componentMappings[]
├─ tokenBindings[]
├─ assetBindings[]
├─ renderabilityAssessments[]
├─ interactionCandidates[]
├─ evidence[]
├─ issues[]
└─ confidenceSummary
```

Quality Gate도 존재 여부가 아니라 계약 일관성을 검사해야 한다.

```text
SOURCE_TRACEABILITY
LAYOUT_RESOLVABILITY
COMPONENT_RESOLUTION
TOKEN_RESOLUTION
RESPONSIVE_CONSISTENCY
RENDERABILITY
INTERACTION_TRACEABILITY
UNCERTAINTY_COMPLETENESS
```

이 개선이 전체 벤치마크에서 가장 높은 우선순위다.

---

## 6. 프런트엔드 코드 벤치마크

### 6.1 Supernova

Supernova의 Code Automation은 전체 화면보다 디자인 시스템 산출물에 집중한다.

- CSS·SCSS·JSON
- iOS·Android Token
- React Web·React Native·Flutter용 데이터
- Asset Set
- Custom Exporter
- Git Branch·Folder·PR
- Brand·Platform별 Pipeline

### 6.2 Locofy

Locofy는 Figma에서 화면과 Component 코드를 생성한다.

- React·Next.js·Vue·Angular·HTML/CSS 등
- Component·Props
- Responsive Layout
- Interaction
- Custom Code Component 재사용
- 파일·Component 단위 Export
- Figma 변경분 Smart Regenerate
- GitHub Smart Merge
- Agent Mode 후처리

근거:

- [Locofy Quickstart](https://www.locofy.ai/docs/plugin/quickstart/)
- [Locofy Smart Regenerate](https://www.dev.locofy.ai/docs/plugin/lightning/smart-code-flow/)
- [Locofy GitHub Smart Merge](https://www.locofy.ai/docs/plugin/export-and-deployment/sync-with-github/)
- [Locofy Agent Mode](https://www.locofy.ai/docs/lightning/agent-mode/)

### 6.3 Anima

Anima는 Framework와 Styling Target을 명시적으로 설정한다.

- React·Vue·HTML
- JavaScript·TypeScript
- CSS·Tailwind·Styled Components·CSS Modules·SASS·SCSS
- MUI·AntD·Radix·shadcn
- Component Auto Split
- Compact Structure
- Fast·High Quality
- Code Sample과 Custom Instruction 기반 Personalization

근거:

- [Anima Figma Plugin](https://support.animaapp.com/en/articles/11721866-anima-figma-plugin-design-to-code-in-figma)
- [Anima API](https://docs.animaapp.com/docs/anima-api)
- [Anima AI Personalization](https://support.animaapp.com/en/articles/8937099-figma-to-code-with-ai-personalization)

### 6.4 SpringAI 현재 구현

SpringAI는 범용 클라이언트 프런트엔드 생성기와 목적이 다르다.

```text
APPROVED ScreenSpecification
→ ThymeleafBindingContract
→ FreeMarker Template
→ Thymeleaf
→ eGovFrame Layout·Fragment
→ Controller Model Binding
→ Build·Render Validation
```

다음 구현이 확인된다.

- APPROVED ScreenSpecification만 Binding 생성에 사용
- Thymeleaf Parse·Overflow·Binding Gate
- Design Hardcoding 검사
- Preview Artifact와 Preview Hash
- Preview Hash 일치와 Validation Error 부재 후 승인
- `DESIGN.md` Revision 재검사
- 기존 Source Fingerprint 재검사
- 대상 파일 Hash 충돌 검사
- Atomic Apply
- Rollback과 Rollback Failure 분리

코드 근거:

- `src/main/java/com/krdevops/springai/service/thymeleaf/ThymeleafBindingGenerationService.java`
- `src/main/java/com/krdevops/springai/service/thymeleaf/ThymeleafProjectWorkflowService.java`
- `src/main/java/com/krdevops/springai/model/thymeleaf/ThymeleafBindingContract.java`
- `src/main/java/com/krdevops/springai/model/write/ProjectWritePolicy.java`

### 6.5 방향성 판정

#### SpringAI 목적에 더 적합한 부분

- 실제 Controller·VO Binding
- Route·Validation·Permission·CSRF 고려
- 승인 명세 기반 결정론적 생성
- Preview Hash 승인
- Atomic Apply
- Drift·Conflict·Rollback
- 서버 Thymeleaf Parse·Render

React·Vue를 생성하지 않는 것은 부족함이 아니라 Target 차이다.

#### 발전할 부분

- Figma Component↔Thymeleaf Fragment Parameter Mapping
- 화면·Section·Fragment 단위 증분 생성
- Fragment Dependency Closure
- 생성 영역과 사용자 작성 영역 Ownership
- Base/Current/New Semantic Merge
- Renderer Profile과 Target Capability
- Token·Asset Manifest와 생성 보고서 연결

### 6.6 구현 제안

```text
RendererProfile
├─ rendererType: THYMELEAF
├─ templateEngine: FREEMARKER
├─ componentMappingVersion
├─ supportedFeatures[]
├─ forbiddenFallbacks[]
├─ outputConventionVersion
└─ validatorProfile
```

```text
GenerationScopeManifest
├─ rootArtifacts[]
├─ dependencyArtifacts[]
├─ validationOnlyArtifacts[]
├─ preservedArtifacts[]
└─ affectedScreens[]
```

```text
GenerationOwnershipManifest
├─ generatedRegions[]
├─ bindingRegions[]
├─ protectedRegions[]
└─ mergePolicy
```

---

## 7. Prototype 벤치마크

### 7.1 Supernova

Supernova Portal은 다음을 연결한다.

- Figma·Storybook Component 탐색
- Design System 기반 AI Prototype
- PRD·Specification·Release Note
- 팀 협업
- MCP Export

Component Browser에서는 Variant·Behavior·Token·Asset·Storybook Code를 탐색한다.

근거:

- [Supernova Portal](https://learn.supernova.io/)
- [Supernova Browse Design System](https://learn.supernova.io/latest/design-systems/features/ask/browse-design-system-PDZESPZj)

### 7.2 Locofy

Locofy Prototype은 생성 코드 기반이다.

- Responsive Live Preview
- Hover·Pressed State
- Button·Input·Drawer·Popup
- Figma Prototype 연결
- 연결 Frame 자동 탐색
- Agent Mode 변경의 즉시 Preview
- Builder 협업과 공유

### 7.3 Anima

Anima Playground는 실행 가능한 Versioned Project다.

- 실행 가능한 Code Project
- Figma 화면·Component 중간 추가
- Prompt·Code 수정
- 변경마다 자동 Version 저장
- 과거 Version Preview·Restore·Fork
- Public·Private Visibility
- Git Repository
- MCP를 통한 Agent Handoff

근거:

- [Anima Version History](https://docs.animaapp.com/docs/version-history)
- [Anima Project Visibility](https://docs.animaapp.com/docs/project-visibility)
- [Anima Import from Figma](https://docs.animaapp.com/docs/import-from-figma)

### 7.4 SpringAI 현재 구현

SpringAI에는 다음 Preview·검증 기반이 있다.

- Figma Bundle Preview
- Plugin Preview·Apply Report
- Thymeleaf Preview Artifact
- Browser Render
- Desktop·Mobile Screenshot
- Visual Regression
- axe
- Fixture Model
- Operation History

`FigmaGenerationReport`는 다음 정보를 기록한다.

- 재사용·생성·보관 Node 수
- Fallback 수
- 변경 목록
- Issue
- Layout·Accessibility·Visual Regression Gate
- Baseline·Evidence Hash
- Refinement Summary
- Registry·Catalog SSOT Evidence

`DesignParityValidationUseCase`는 Figma Artifact와 Thymeleaf Artifact의 존재와 Hash를 확인한다. 실제 Component·Token·Layout 시각 일치는 별도 Render·Screenshot·axe Gate의 책임으로 명시한다.

코드 근거:

- `src/main/java/com/krdevops/springai/model/figma/ops/FigmaGenerationReport.java`
- `src/main/java/com/krdevops/springai/service/parity/DesignParityValidationUseCase.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaVisualComparisonService.java`

### 7.5 방향성 판정

SpringAI에 Prototype 기능이 없는 것이 아니다. Preview와 Evidence가 여러 Operation과 Artifact에 분산돼 있어 사용자가 하나의 실행 결과로 보기 어렵다는 것이 문제다.

| 대상 | 사용자가 보는 단위 |
|---|---|
| Supernova | Design System Portal과 Prototype |
| Locofy | Live Responsive Prototype |
| Anima | 실행 가능한 Versioned Playground |
| SpringAI | Figma Operation·Thymeleaf Operation·Artifact·개별 Gate |

범용 Playground를 새로 만들기보다 기존 Evidence를 조합하는 Review Surface가 적합하다.

### 7.6 구현 제안

```text
PreviewEvidenceBundle
├─ Figma Bundle
├─ UiDesignSpec Preview
├─ ScreenSpecification
├─ Generated Thymeleaf
├─ Fixture Model Hash
├─ Desktop·Tablet·Mobile Screenshots
├─ Visual Diff
├─ DOM Snapshot
├─ axe Report
├─ Binding Report
├─ Build Report
├─ Interaction Flow Report
├─ Fallback Assessments[]
└─ Final Decision
```

```text
ScreenReviewSession
├─ evidenceBundleId
├─ reviewerRoles[]
├─ comments[]
├─ requestedChanges[]
├─ approvalDecision
├─ visibility: PRIVATE
└─ expiresAt
```

Prototype 안전 원칙:

- 실제 DB 대신 Fixture Model 사용
- 운영 Token·Session Cookie 전달 금지
- 기본 Visibility는 Private
- 만료 시간 필수
- 조회·Comment·승인 권한 분리
- Prototype 결과의 운영 자동 승격 금지

---

## 8. Handoff 벤치마크

### 8.1 Supernova

Supernova Handoff의 중심은 디자인 시스템 지식과 배포 가능한 데이터다.

- Token·Component·Asset
- 문서와 Code Example
- Storybook
- Version·Release Note
- Component Health
- Search·AI Ask
- Export Pipeline·PR
- Platform·Brand별 Context

### 8.2 Locofy

Locofy Handoff의 중심은 생성 코드와 기존 저장소의 결합이다.

- CLI·MCP·VS Code
- Component·파일 단위 Pull
- Dependency 포함 Pull
- GitHub Sync
- Smart Merge
- Conflict Resolution
- Agent Mode 결과 전달

### 8.3 Anima

Anima Handoff의 중심은 실행 가능한 Project다.

- Playground URL
- Git Repository
- Version History
- Short-lived Git Access
- MCP
- Private Team Project
- Code와 실행 결과 동시 전달

근거:

- [Anima MCP](https://docs.animaapp.com/docs/anima-mcp)

### 8.4 SpringAI 현재 구현

SpringAI의 Figma Operation은 다음 상태를 구분한다.

```text
ANALYZED
→ AWAITING_TABLE_BINDING
→ PREVIEW_READY
→ APPLY_REQUIRED
→ APPLIED
```

실패·충돌·거부는 별도 상태다.

Thymeleaf Operation도 다음을 구분한다.

```text
CONTRACT_READY
→ PREVIEW_READY
→ APPROVED
→ APPLIED
→ VALIDATED
```

SpringAI가 이미 강한 부분:

- 계약 기반 Handoff
- Source Revision
- 승인 상태
- Artifact Hash
- MCP Tool
- Apply 전 Preview
- Figma Generation Report
- Build·Render Gate
- Atomic Apply와 Conflict

코드 근거:

- `src/main/java/com/krdevops/springai/model/figma/contract/FigmaDesignOperationStatus.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaDesignOperationStateService.java`
- `src/main/java/com/krdevops/springai/service/thymeleaf/ThymeleafProjectWorkflowService.java`
- `src/main/java/com/krdevops/springai/service/artifact/ArtifactService.java`

### 8.5 방향성 판정

시스템 간 Handoff 안전성은 강하지만 사람이나 Agent가 다음 정보를 한 번에 소비할 Package가 부족하다.

- 어떤 Figma Revision인가
- 어떤 Registry·Token Version인가
- 어떤 Component가 선택됐는가
- 어떤 Field가 VO와 연결됐는가
- 어떤 파일이 생성·변경되는가
- 어떤 검증을 통과했는가
- 어떤 Fallback과 Warning이 남았는가
- 승인 후 무엇이 Apply되는가
- 다음에 실행할 수 있는 Tool은 무엇인가

### 8.6 구현 제안: `ScreenHandoffBundle`

```text
ScreenHandoffBundle
├─ operationId
├─ sourceRevision
├─ designSystemSnapshotId
├─ uiDesignSpecId
├─ screenSpecificationId
├─ bindingContractId
├─ generationScopeManifest
├─ ownershipManifest
├─ changedFiles[]
├─ componentMappings[]
├─ tokenBindings[]
├─ unresolvedIssues[]
├─ previewEvidenceBundleId
├─ migrationNotes
└─ nextAllowedActions[]
```

사용자별 소비 정보:

| 대상 | 확인할 내용 |
|---|---|
| Designer | Layout·Component·Token·Responsive·시각 Diff |
| 업무 담당자 | Field·Action·Route·Permission |
| Developer | 생성 파일·Dependency·Ownership·Conflict |
| QA | Evidence·실패 Gate·Fixture·Interaction Flow |
| 승인자 | Apply 범위·잔여 위험·Rollback 기준 |
| Agent | `nextAllowedActions` 범위의 후속 Tool |

Handoff는 파일 전달이 아니라 다음 단계가 안전하게 판단 가능한 상태를 전달해야 한다.

---

## 9. SpringAI 방향성 최종 판정

### 9.1 올바르게 구현하고 있는 부분

#### Figma와 업무 Binding 분리

가장 중요한 아키텍처 결정이며 유지해야 한다.

#### APPROVED ScreenSpecification

단순 Design-to-Code 도구에서 약한 업무 계약을 명시적으로 통제한다. SpringAI의 핵심 차별점이다.

#### Preview·Approve·Apply 분리

Preview나 Tool 실행 성공이 실제 Apply를 의미하지 않는다.

#### Registry·Lifecycle·Drift

디자인 시스템 변경이 생성 코드로 유입되는 경로를 엄격하게 통제한다.

#### Artifact·Hash·Revision·Atomic Apply

Handoff와 변경 재적용의 안전성을 높인다.

#### Binding·Build·Render 검증

시각적 유사도보다 실제 서버 화면의 동작 가능성을 우선한다.

### 9.2 부분적으로 올바르지만 보강이 필요한 부분

| 영역 | 현재 판단 | 필요한 보강 |
|---|---|---|
| `UiDesignSpec` | 책임 위치는 정확함 | Design IR·Evidence·Resolution·Fallback을 포함하는 v2 필요 |
| Responsive | 관찰과 변환 기반 존재 | Policy와 실제 Structure 통합 결과 분리 |
| Component Mapping | Registry가 강함 | Thymeleaf Fragment Parameter Mapping 추가 |
| Preview | 개별 증거가 강함 | Evidence Bundle과 Review Surface로 통합 |
| Handoff | 상태 머신·Artifact가 강함 | 사람이 읽을 `ScreenHandoffBundle` 필요 |

### 9.3 현재 약하거나 확인되지 않은 부분

- Design System Knowledge Snapshot
- Component Health 통합 View
- Token·Asset Code Export Pipeline
- Design System Search/RAG
- 화면·Fragment Dependency Graph
- Generated Region Ownership
- 비동기 Generation Job
- Screen Review Session
- Handoff Bundle
- 문서·사용 Analytics

---

## 10. 구현 가능한 발전 로드맵

### 10.1 1단계: 생성 계약 강화

```text
UiDesignSpec v2
DesignCodeComponentMapping
ResponsivePolicySet
ResponsiveStructureSet
RenderabilityAssessment
```

완료 조건:

- 모든 시각 추론이 원본 Node와 Evidence를 가진다.
- Component와 Token이 Registry·Variable에 연결된다.
- Viewport 차이를 Reflow·Visibility·Swap·Alternate Structure로 구분한다.
- Raster·Approximation·Unsupported를 명시한다.
- 낮은 Confidence 결과가 자동 승인되지 않는다.

### 10.2 2단계: Prototype·Evidence 통합

```text
PreviewEvidenceBundle
ScreenReviewSession
InteractionFlowEvidence
```

완료 조건:

- Desktop·Tablet·Mobile Screenshot을 제공한다.
- DOM·axe·Visual Diff를 같은 버전으로 묶는다.
- Fixture Model Hash를 기록한다.
- Binding·Build 결과를 연결한다.
- Fallback·Warning·Comment·승인 결과를 한 Surface에서 확인한다.

### 10.3 3단계: 생성·병합 안전성

```text
GenerationScopeManifest
Dependency Closure
GenerationOwnershipManifest
Semantic MergePlan
```

완료 조건:

- 화면·Section·Fragment 단위 생성이 가능하다.
- 변경 영향을 받는 Screen을 계산한다.
- 업무 코드와 생성 영역을 분리한다.
- Base/Current/New 충돌을 표시한다.
- 승인 전 실제 파일 변경이 없다.

### 10.4 4단계: 디자인 시스템 운영

```text
DesignSystemKnowledgeSnapshot
ComponentHealthReport
TokenExportManifest
DesignSystemKnowledgeIndex
```

완료 조건:

- Screen이 사용한 디자인 시스템 전체 버전을 역추적한다.
- Component별 생성 가능 여부를 조회한다.
- CSS·TypeScript Token 산출물을 재현한다.
- AI가 APPROVED/PUBLISHED Snapshot만 검색한다.

### 10.5 5단계: Handoff·Automation

```text
ScreenHandoffBundle
GenerationJob
DesignSystemEvent
nextAllowedActions
```

완료 조건:

- 사람과 Agent가 같은 Bundle을 소비한다.
- 장시간 작업의 진행률·취소·재시도를 지원한다.
- Event가 Preview와 영향 분석을 시작한다.
- 승인·Apply를 자동화가 우회하지 못한다.

---

## 11. 도입하지 않아야 할 방식

- 범용 Figma→React 또는 Vibe Coding 제품을 그대로 복제
- Figma Layer Name이나 AI 추론으로 VO Field를 확정
- Prototype 성공을 운영 화면 승인으로 자동 승격
- Screenshot 유사도만으로 Semantic HTML·A11y 완료 판단
- Text·Form·Table을 이미지로 변환해 시각적 충실도 확보
- Design System Event 발생 직후 자동 Commit·Apply·배포
- Agent가 Ownership과 Protected Region을 무시하고 전체 파일 수정
- Product별 기능을 각각 독립 Subsystem으로 구현해 계약 중복 발생
- 문서 또는 Search/RAG 답변을 Runtime Source of Truth로 사용
- 실제 DB Data와 인증 Token을 Review Session에 포함

---

## 12. 완료 상태 판단 기준

SpringAI가 다섯 영역에서 충분히 발전했다고 판단하려면 다음 조건을 만족해야 한다.

### 디자인 시스템

- Screen이 사용한 Profile·Registry·Token·Mapping Version을 역추적할 수 있다.
- Deprecated·Blocked Component가 신규 생성에 사용되지 않는다.
- Figma Component와 Thymeleaf Fragment Parameter Mapping이 승인돼 있다.

### 시각 해석

- `UiDesignSpec`이 원본 Node·Evidence·Confidence·Issue를 보존한다.
- Responsive Policy와 Viewport Structure를 구분한다.
- 모든 Fallback이 손실·전략·허용 여부를 기록한다.

### 프런트엔드 코드

- APPROVED ScreenSpecification만 생성 입력으로 사용한다.
- 생성 범위·Dependency·Ownership을 알 수 있다.
- Binding·Security·Build·Render Gate를 통과한다.

### Prototype

- 실행 결과와 Desktop·Tablet·Mobile Evidence를 한 Bundle로 조회한다.
- Fixture 기반 Flow와 Interaction을 검증한다.
- Prototype과 운영 배포 상태를 구분한다.

### Handoff

- Designer·업무 담당자·Developer·QA·승인자·Agent가 동일 Bundle을 소비한다.
- 남은 Issue와 허용된 다음 작업이 명확하다.
- Preview·Approve·Apply를 우회할 수 없다.

---

## 13. 근거·추론·미확정 사항

### 13.1 확인된 근거

- 세 제품은 모두 디자인 시스템, 시각 해석, 프런트엔드 코드, Prototype, Handoff를 일정 수준 다룬다.
- Supernova는 Versioned Design System Data, Documentation, Code Pipeline, Portal을 제공한다.
- Locofy는 Figma 구조 분석, Custom Component Mapping, Responsive Prototype, 증분 재생성, Smart Merge를 제공한다.
- Anima는 Figma Design System Storybook, Breakpoint, Fallback, Versioned Playground, MCP를 제공한다.
- SpringAI에는 Profile·Registry·UiDesignSpec·ScreenSpecification·Binding Contract·Operation·Artifact·Quality Gate·Atomic Apply 기반이 존재한다.

### 13.2 근거에서 도출한 판단

- SpringAI의 업무 계약·승인·보안 중심 방향은 적절하다.
- 현재 가장 큰 구조적 병목은 `UiDesignSpec v1`의 표현력 부족이다.
- Prototype 기능 자체보다 분산된 Preview와 Evidence를 통합하는 것이 우선이다.
- Handoff는 Machine State뿐 아니라 사람이 이해할 Package가 필요하다.
- 범용 Frontend Framework 지원보다 Thymeleaf Fragment Mapping·증분 생성·Semantic Merge가 프로젝트 목표에 더 중요하다.

### 13.3 현재 확정할 수 없는 사항

- 실제 Figma 입력에서 `UiDesignSpec v1` 정보 손실이 발생하는 빈도
- 이미지·SVG Fallback이 필요한 Node의 실제 수
- 장시간 생성 작업이 MCP Timeout을 초과하는 빈도
- 조직이 Private Review Session을 필요로 하는지
- Multi-brand·Theme가 현재 실제 업무 요구인지
- Token Export Target이 CSS 외 TypeScript·Mobile까지 필요한지
- Design System 문서 작성·게시 책임자가 누구인지

이 항목은 구현 범위로 단정하지 않고 Fixture 분석, Runtime Telemetry, 운영 책임 확인 후 결정한다.

---

## 14. 최종 권고

SpringAI는 세 제품을 따라 범용 Design-to-Code 플랫폼이 될 필요가 없다.

벤치마크를 통해 확인되는 SpringAI의 적합한 목표는 다음과 같다.

```text
디자인 시스템
= 버전과 사용 가능성을 통제

시각 해석
= 근거가 있는 Design IR과 UiDesignSpec 생성

프런트엔드 코드
= 승인된 업무 계약으로 결정론적 Thymeleaf 생성

Prototype
= Fixture 기반 실행 화면과 검증 Evidence 제공

Handoff
= 사람과 Agent가 같은 승인 계약·Diff·Evidence를 전달받음
```

현재 SpringAI는 업무 계약, 승인, 보안, Apply 안전성에서는 방향이 정확하고 기반도 강하다. 다음 발전의 중심은 생성 기능의 수를 늘리는 것이 아니라 다음 세 계약을 강화하는 것이다.

```text
UiDesignSpec v2
→ PreviewEvidenceBundle
→ ScreenHandoffBundle
```

이 세 계약이 자리 잡으면 디자인 시스템 정보, 시각 해석 근거, Thymeleaf 생성 결과, 실행 검증, 사람 승인, Agent 후속 작업이 같은 Version과 Evidence를 기준으로 연결될 수 있다.

---

## 참고 자료

### Supernova

- [Supernova 공식 학습 문서](https://learn.supernova.io/)
- [Design Systems 101](https://learn.supernova.io/latest/design-systems/design-systems-101-udW7KfEC)
- [Versioning](https://learn.supernova.io/latest/design-systems/features/versioning-oIUhGTVL)
- [Browse Design System](https://learn.supernova.io/latest/design-systems/features/ask/browse-design-system-PDZESPZj)
- [Code Automation](https://www.supernova.io/guides/scaling-your-product-development-with-supernova/5-scaling-with-code-automation/automating-design-token-and-asset-delivery)

### Locofy

- [Locofy Documentation](https://www.locofy.ai/docs/ui-libraries-design-systems/locofy-components/)
- [Lightning Flow](https://www.dev.locofy.ai/docs/plugin/lightning/)
- [Manual Prop Mapping](https://www.locofy.ai/docs/cli/design-system/manual-prop-mapping/)
- [Custom Code Components](https://www.locofy.ai/docs/mcp/design-system/overview/)
- [Multiple Breakpoints](https://www.dev.locofy.ai/docs/plugin/lightning/multiple-breakpoints/)
- [Smart Regenerate](https://www.dev.locofy.ai/docs/plugin/lightning/smart-code-flow/)
- [GitHub Smart Merge](https://www.locofy.ai/docs/plugin/export-and-deployment/sync-with-github/)
- [Agent Mode](https://www.locofy.ai/docs/lightning/agent-mode/)

### Anima

- [Anima Figma Plugin](https://support.animaapp.com/en/articles/11721866-anima-figma-plugin-design-to-code-in-figma)
- [Add Figma Design System](https://docs.animaapp.com/docs/add-figma-design-system)
- [Starting from Figma](https://docs.animaapp.com/docs/starting-from-figma)
- [Figma Translation and Fallback](https://support.animaapp.com/en/articles/6228806-how-anima-translates-figma-design-settings-into-code)
- [Responsiveness and Breakpoints](https://docs.animaapp.com/docs/1-responsive-pages)
- [Anima API](https://docs.animaapp.com/docs/anima-api)
- [Version History](https://docs.animaapp.com/docs/version-history)
- [Project Visibility](https://docs.animaapp.com/docs/project-visibility)
- [Anima MCP](https://docs.animaapp.com/docs/anima-mcp)

### SpringAI 내부 문서

- [`Anima_Based_SpringAI_Pipeline_Evolution_Review.md`](./Anima_Based_SpringAI_Pipeline_Evolution_Review.md)
- [`Locofy_Flow_Based_SpringAI_Pipeline_Evolution_Review.md`](./Locofy_Flow_Based_SpringAI_Pipeline_Evolution_Review.md)
- [`Supernova_Based_SpringAI_DesignOps_Evolution_Review.md`](./Supernova_Based_SpringAI_DesignOps_Evolution_Review.md)
- [`Anima_Locofy_Supernova_SpringAI_Evolution_Comparative_Analysis.md`](./Anima_Locofy_Supernova_SpringAI_Evolution_Comparative_Analysis.md)
- [`11_Semantic_Figma_Design_System_Implementation_Plan.md`](./11_Semantic_Figma_Design_System_Implementation_Plan.md)
