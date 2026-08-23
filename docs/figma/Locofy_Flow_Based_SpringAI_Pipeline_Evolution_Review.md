# Locofy 변환 Flow 기반 SpringAI 생성 파이프라인 발전 검토

> 작성일: 2026-08-23  
> 검토 목적: Locofy 제품 도입 여부가 아니라, Locofy의 Figma → HTML/CSS 변환 Flow 분석에서 확인한 설계 원리를 SpringAI 자체 생성 파이프라인에 반영할 수 있는지 검토한다.  
> 기준 문서: `Locofy_Figma_to_HTML_CSS_기술_검토.md`, `11_Semantic_Figma_Design_System_Implementation_Plan.md`, `SpringAI_Architecture_Target_Pipeline.html`  
> 추가 조사: 2026-08-23 Locofy 공식 사이트와 MCP, Manual Prop Mapping, Smart Merge, VS Code Extension 공개 문서

---

## 1. 검토 결론

Locofy를 SpringAI에 통합하거나 Locofy 생성 HTML을 Thymeleaf 생성의 입력으로 사용하는 방안은 본 검토의 범위가 아니다.

참고할 핵심은 Figma를 바로 HTML로 변환하지 않고 다음 책임을 분리하는 처리 구조다.

1. 구조화된 Figma 입력의 품질을 먼저 판정한다.
2. Figma Node Graph를 프레임워크 독립적인 Design IR로 정규화한다.
3. Layout, Responsive, Semantic, Component, Props, Token, Asset을 개별 계약으로 해석한다.
4. 자동 추론에는 근거, confidence, Issue를 남긴다.
5. 시각 계약의 확인과 업무 계약의 승인을 분리한다.
6. 생성 결과를 구조, 접근성, 토큰, Binding, 보안, 반응형, 시각 회귀, Build 관점에서 검증한다.

이 방향은 SpringAI의 기존 원칙을 변경하지 않는다.

```text
Figma·DESIGN.md·Design Token
  = 시각 구조·Layout·Component 선택의 기준

Controller·VO·DB Schema·ScreenSpecification
  = 업무 Binding·Route·Validation·권한·보안의 기준
```

Locofy 분석에서 참고할 대상은 제품이나 생성 코드가 아니라, 시각 입력을 코드 생성 가능한 계약으로 정제하는 단계적 처리 방식이다.

---

## 2. 현재 목표 흐름과 발전된 목표 흐름

### 2.1 현재 목표 흐름

```text
Figma/디자인
  ↓
시각 구조와 디자인 토큰
  ↓
UiDesignSpec
  ↓
DB·Controller·VO·권한 결합
  ↓
APPROVED ScreenSpecification
  ↓
FreeMarker 기반 Thymeleaf 생성
  ↓
Binding·Build·Render 검증
```

현재 흐름은 책임 경계가 명확하지만, `Figma/디자인 → UiDesignSpec` 사이에서 수행할 분석과 정규화가 압축돼 있다.

### 2.2 발전된 목표 흐름

```text
Figma Frame/Section
  ↓
Design Input Quality Gate
  ↓
Figma Node Graph 수집
  ↓
Design IR 정규화
  ↓
Layout·Responsive Policy 해석
  ↓
Semantic·Component·Props 후보 추출
  ↓
Token·Asset 정규화
  ↓
UiDesignSpec Preview
  ↓
사람의 교정·확정
  ↓
DB·Controller·VO·권한 결합
  ↓
APPROVED ScreenSpecification
  ↓
Component Inventory 선택
  ↓
FreeMarker Thymeleaf Skeleton 생성
  ↓
Controller Model Binding
  ↓
Responsive Transformation
  ↓
Structure·A11y·Security·Build·Render 검증
```

이 흐름은 Figma 화면을 HTML로 그대로 복제하는 변환기가 아니다. Figma 입력에서 추출한 결과는 시각 계약 후보이며, 업무 의미는 이후 단계에서 별도로 결합한다.

---

## 3. 우선순위별 발전 항목

| 순위 | 발전 항목 | 기대 효과 | 판단 신뢰도 |
|---:|---|---|---|
| 1 | Design Input Quality Gate | 부적합한 Figma 구조의 자동 생성 진입 차단 | 높음 |
| 2 | 명시적인 Design IR | 시각 분석과 프레임워크별 코드 생성을 분리 | 높음 |
| 3 | Responsive Policy 계약 | Viewport 변환의 근거와 재현성 확보 | 높음 |
| 4 | Component·Props·Semantic 후보 분리 | Registry 해석과 업무 Binding 오염 방지 | 높음 |
| 5 | Token·Asset 정규화 | Raw Value와 중복 Asset의 운영 코드 유입 차단 | 높음 |
| 6 | UiDesignSpec Preview·교정 | 시각 계약 확인과 업무 계약 승인을 분리 | 높음 |
| 7 | 다층 검증 Gate | 접근성·보안·유지보수성까지 완료 조건에 포함 | 높음 |
| 8 | 코드 컴포넌트 우선 Mapping Contract | Figma Component·Property와 실제 Fragment Parameter 간 명시적 연결 | 높음 |
| 9 | 증분 생성과 Dependency Closure | 화면·컴포넌트 변경 시 필요한 산출물만 안전하게 재생성 | 높음 |
| 10 | Generated Region Ownership·Semantic Merge | 디자인 변경 재적용 시 기존 업무 코드 보호 | 높음 |
| 11 | 다중 진입점의 단일 Command 수렴 | Plugin·REST·MCP·Batch 간 실행 계약 불일치 방지 | 높음 |
| 12 | 제한된 Agent 후처리 | 접근성·반응형 등 허용 영역만 Patch하고 검증 | 중간 |

---

## 4. Design Input Quality Gate

### 4.1 목적

Figma 입력의 시각적 완성도와 코드 생성 적합성은 동일하지 않다. 같은 화면처럼 보여도 Node 구조, Auto Layout, Component 구성에 따라 Layout과 DOM 해석 결과가 달라진다.

따라서 시각 분석 전에 코드 생성 준비 상태를 판정해야 한다.

### 4.2 판정 항목

- Auto Layout 적용 여부
- Fill, Hug, Fixed 크기 정책
- Min/Max Width·Height 제약
- Component/Instance 사용 여부
- Variant 내부 구조 일관성
- 의미 있는 Layer 이름
- Figma Variable 연결 여부
- Desktop·Tablet·Mobile Frame 대응 관계
- 절대 배치와 Node 겹침 비율
- 과도한 Group 중첩
- Text의 Vector 변환 여부
- 동일 요소의 화면별 중복 여부
- 상태가 여러 Page에 분산됐는지 여부

### 4.3 권장 산출물

```text
DesignInputQualityReport
├─ layoutReadiness
├─ componentReadiness
├─ responsiveReadiness
├─ semanticReadiness
├─ tokenReadiness
├─ assetReadiness
├─ warnings[]
├─ issues[]
└─ decision: PASS | REVIEW_REQUIRED | REJECTED
```

### 4.4 Gate 규칙

- `PASS`: 시각 계약 자동 생성 단계로 진행한다.
- `REVIEW_REQUIRED`: Issue와 대상 Node를 표시하고 사람 교정을 요구한다.
- `REJECTED`: Figma 구조 개선 없이는 시각 계약을 확정하지 않는다.

절대 배치 과다, 서로 다른 Variant 내부 구조, Viewport 대응 불명확, Text Vector화 등은 `REVIEW_REQUIRED` 또는 `REJECTED` 후보가 된다.

---

## 5. Design IR과 UiDesignSpec 강화

### 5.1 Design IR의 역할

Figma Node를 직접 Thymeleaf 또는 HTML Element로 변환하지 않는다. 먼저 프레임워크 독립적인 Design IR로 정규화한다.

```text
Figma Node Graph
→ Normalized Design IR
→ UiDesignSpec
→ ScreenSpecification
→ Target Renderer
```

Design IR은 Figma 고유 속성과 최종 HTML 구현 사이의 중간 계약이다. Layout과 Component 의미를 보존하면서도 특정 프레임워크 코드에 종속되지 않아야 한다.

### 5.2 권장 UiDesignSpec 구조

```text
UiDesignSpec
├─ sourceNodeGraph
├─ semanticTree
├─ layoutTree
├─ responsivePolicies
├─ componentCandidates
├─ componentProperties
├─ tokenBindings
├─ assetReferences
├─ interactionCandidates
├─ stateCandidates
├─ provenance
├─ confidence
└─ issues
```

### 5.3 의미 확정 금지 규칙

시각 분석 결과는 후보이며 업무 의미가 아니다.

```text
시각적 Button 후보
  ≠ Controller Action 확정

시각적 Input 후보
  ≠ VO Field 확정

반복 List 후보
  ≠ 서버 Paging 방식 확정

숨겨진 Action 후보
  ≠ 권한 정책 확정
```

업무 의미는 Controller, VO, DB Schema, 보안 정책과 결합하는 단계에서만 확정한다.

---

## 6. Layout 해석 계약

### 6.1 권장 매핑

```text
Auto Layout
→ Flex 후보

반복 행·열
→ Grid 후보

겹침·Overlay
→ Absolute/Overlay 후보

Fill Container
→ width: 100% / flex-grow 후보

Hug Contents
→ intrinsic / fit-content 후보

Fixed Size
→ 고정 크기 후보
```

### 6.2 근거와 신뢰도

모든 추론 결과는 근거와 confidence를 함께 기록한다.

```json
{
  "layoutType": "GRID",
  "evidence": [
    "REPEATED_COLUMNS",
    "UNIFORM_GAP",
    "CONSISTENT_CHILD_WIDTH"
  ],
  "confidence": 0.94
}
```

낮은 confidence 결과를 곧바로 Renderer에 전달하지 않는다. 명시한 임계값 미만은 `REVIEW_REQUIRED` Issue로 전환한다.

---

## 7. Responsive Policy 계약

### 7.1 목적

반응형 변환은 px 값을 비율로 바꾸는 작업이 아니다. 서로 다른 Viewport Frame이 동일 화면의 상태인지 판정하고, 구조 변화 규칙을 계약화해야 한다.

### 7.2 권장 산출물

```text
ResponsivePolicySet
├─ viewportCorrespondence
├─ breakpointRules
├─ layoutTransitions
├─ visibilityRules
├─ navigationSwap
├─ componentSwap
├─ tableToCardSwap
├─ orderChanges
└─ unresolvedMappings
```

### 7.3 변환 예

```text
Desktop 4-column Grid
→ Tablet 2-column Grid
→ Mobile 1-column List

Desktop GNB
→ Tablet Compact GNB
→ Mobile Drawer

Desktop Table
→ Mobile Summary Card
```

Desktop·Tablet·Mobile은 동일한 업무 Binding을 공유해야 하며, Viewport별로 별도 VO 또는 별도 Controller 계약을 생성하지 않는다.

---

## 8. Component·Props·Semantic 후보 추출

### 8.1 Component 해석 흐름

```text
Figma Component/Instance/Variant
→ Logical Component Candidate
→ Prop Candidate
→ Component Registry Resolution
→ SelectedComponentInventory
```

### 8.2 Prop 후보

- Variant/Enum
- Boolean Visibility
- Text Value
- Icon Placement
- Size
- State
- Repeated Collection
- Event 후보

### 8.3 Semantic 후보

- `header`, `nav`, `main`, `section`, `footer`
- `h1`~`h6`, `p`, `strong`
- `button`, `a`
- `input`, `select`, `textarea`
- `table`, `ul`, `ol`, `article`

시맨틱 후보는 접근성 검증의 입력이지만 자동 생성만으로 접근성을 보장하지 않는다. Label/Input 연결, ARIA State, Focus, Keyboard, Modal Focus Trap, 오류 메시지 연결은 별도 검증한다.

---

## 9. Token·Asset 정규화

### 9.1 Token 처리 흐름

```text
Figma Variable/Style
→ Semantic Role 확인
→ Company Design Token 매핑
→ CSS Variable/Component Property 연결
```

### 9.2 차단 규칙

- 승인 Token 없이 Raw Color 사용
- 반복값을 의미 확인 없이 하나의 Token으로 병합
- 승인되지 않은 임의 px 간격 생성
- 의미가 다른 Token을 현재 값이 같다는 이유로 병합
- Theme Mode와 Variable Alias 손실
- 생성 CSS의 중복 Media Query
- 미세한 소수점 차이마다 별도 Class 생성

### 9.3 Asset 계약

```text
ResolvedAsset
├─ sourceNodeId
├─ type: SVG | PNG | JPEG | WEBP | FONT
├─ outputPath
├─ density
├─ themePolicy
├─ colorPolicy
├─ contentHash
└─ license/attribution
```

### 9.4 Asset 규칙

- 아이콘 SVG는 가능하면 `currentColor` 또는 승인 Token을 사용한다.
- Figma Image Fill은 용도와 압축 정책에 따라 PNG/JPEG/WebP로 결정한다.
- 동일 Asset은 content hash로 중복을 방지한다.
- Background Image와 콘텐츠 이미지를 구분한다.
- 고해상도 원본을 그대로 배포하지 않고 density와 압축 정책을 적용한다.

---

## 10. Preview·교정·승인 분리

### 10.1 시각 계약 확인

```text
UiDesignSpec DRAFT
→ Visual Structure Preview
→ Issue·Confidence Review
→ reviseUiDesignSpec
→ UiDesignSpec CONFIRMED
```

이 단계는 Layout, Component, Token, Responsive 해석이 디자인 의도와 맞는지 확인한다.

### 10.2 업무 계약 승인

```text
UiDesignSpec CONFIRMED
+ Controller·VO·DB Schema·권한
→ ScreenSpecification REVIEW_REQUIRED
→ reviseScreenSpecification
→ approveScreenSpecification
→ APPROVED ScreenSpecification
```

시각 계약 확인과 업무 계약 승인을 분리해야 한다. 시각 교정 때문에 DB Binding을 다시 승인하거나, 업무 Mapping 변경 때문에 Figma 분석을 다시 수행하지 않도록 한다.

---

## 11. 검증 Gate 발전

### 11.1 전체 검증 흐름

```text
Generated Thymeleaf View
→ HTML Structure Audit
→ Semantic Audit
→ Accessibility Audit
→ Token Audit
→ Binding Audit
→ Security Audit
→ Responsive Audit
→ Visual Regression
→ Thymeleaf Parse/Render
→ Project Build
```

### 11.2 검증 항목

| 검증 계층 | 검증 내용 |
|---|---|
| 구조 | DOM 깊이, Wrapper 과다, Component 중복, HTML 문법 |
| 시맨틱 | Heading 계층, Landmark, Button/Link/Input 의미 |
| 접근성 | axe-core, Keyboard, Focus, Contrast, ARIA, Reduced Motion |
| Token | Raw Color/Spacing/Radius 금지, 승인 Token 연결 |
| Binding | `th:object`, `th:field`, Route, Model Attribute, Validation 일치 |
| 보안 | CSRF, XSS, URL, 권한 조건, 민감정보 노출 |
| 반응형 | 1440/768/390 Screenshot, Overflow, Component Swap |
| 시각 회귀 | Figma Reference와 Pixel/Perceptual Diff |
| 렌더링 | Thymeleaf Parse와 Fixture Model Render |
| 빌드 | 허용 환경의 Maven/Gradle Build |

Preview가 정상이라는 사실은 Binding, 접근성, 보안, 실제 서버 렌더링이 정상이라는 증거가 아니다.

---

## 12. Tool 오케스트레이션 발전 방향

### 12.1 목표 책임 흐름

다음 이름은 현재 Tool 구현 사실이 아니라 목표 책임을 설명하기 위한 가칭이다.

```text
DesignReferenceTool
→ analyzeDesignInputQuality()
→ analyzeFigmaNodeGraph()
→ createUiDesignSpec()
→ previewUiDesignSpec()
→ revise/confirmUiDesignSpec()
→ createScreenSpecification()
→ revise/approveScreenSpecification()
→ calculateGenerationScopeAndDependencies()
→ buildFullCrudPrompt()
→ generate()
→ createMergePreview()
→ refineWithinApprovedRegions()
→ auditGeneratedView()
```

### 12.2 다중 진입점의 단일 Command 수렴

Locofy의 최신 공개 흐름은 Figma Plugin, URL Import, CLI, MCP, Builder, VS Code, GitHub Sync 등 여러 진입점을 제공하지만 디자인 분석과 코드 생성 책임은 같은 파이프라인으로 수렴시킨다. SpringAI도 진입점별 Service 구현을 만들지 않고 공통 Command를 생성해야 한다.

```text
Figma Plugin ─┐
REST API ─────┤
MCP Tool ─────┼→ FigmaGenerationCommand
Batch/Event ──┤           ↓
CLI Adapter ──┘   Canonical Orchestration Pipeline
```

```text
FigmaGenerationCommand
├─ requestType
├─ sourceReference
├─ sourceRevision
├─ editableNodeIds
├─ referenceNodeIds
├─ generationScope
├─ screenSpecificationId
├─ designSystemSnapshotId
├─ requestedArtifacts
├─ interactionMode
└─ requesterContext
```

모든 Adapter는 인증·인가, 입력 정규화, Source Revision 확인, Preview, 승인, 생성, Merge Preview, 검증, Apply라는 동일한 상태 전이를 사용한다. MCP가 호출됐다는 이유만으로 Preview나 승인 Gate를 건너뛰지 않는다.

### 12.3 현재와 목표의 문서 분리

아키텍처 다이어그램에서는 현재 구현과 목표 상태를 같은 실행 경로처럼 섞지 않는다.

권장 구성:

```text
2.1 목표 화면 생성 파이프라인
2.2 현재 코드 생성 Tool 오케스트레이션
2.3 목표 Design-aware 생성 오케스트레이션
2.4 RAG / Chat 흐름
```

현재 Tool 이름과 호출 순서는 `현재`로 표시하고, 신규 책임은 `목표 Service/Tool` 또는 `제안 단계`로 표시한다.

---

## 13. Revision과 재적용 관리

Figma와 생성 코드를 완전한 양방향 변환으로 취급하지 않는다. 디자인 변경을 다시 적용할 때는 다음 식별자와 충돌 Gate가 필요하다.

```text
sourceFileKey
sourceNodeId
sourceRevision
sourceHash
uiDesignSpecVersion
screenSpecificationVersion
generatedArtifactHash
```

재적용 규칙:

- Source Revision이 다르면 자동 덮어쓰지 않는다.
- 사용자가 수정한 업무 Binding 영역은 디자인 변경으로 교체하지 않는다.
- Skeleton/Style 변경과 Binding 변경의 Diff를 분리한다.
- 충돌 시 Preview와 Issue Report를 먼저 생성한다.
- 다중 화면은 전부 Preview 성공 후 Apply하거나 전부 중단한다.
- 동일 입력과 동일 계약 버전의 재실행은 멱등성을 유지한다.

---

## 14. 코드 컴포넌트 우선 Mapping Contract

### 14.1 목적

Locofy는 코드 저장소의 컴포넌트를 Figma Component와 연결하고 Figma Property, Layer, Value를 실제 Code Prop으로 매핑한다. SpringAI에서도 Component 후보 선택을 넘어서 Figma Property와 Thymeleaf Fragment Parameter 사이의 명시적 계약이 필요하다.

```text
DesignCodeComponentMapping
├─ mappingId
├─ registryVersion
├─ logicalType
├─ figmaComponentSetKey
├─ figmaComponentNames[]
├─ codeComponent
├─ rendererType
├─ matchingStrategy
├─ propertyMappings[]
├─ slotMappings[]
├─ previewFixture
├─ confidence
├─ issues[]
└─ status: DRAFT | REVIEW_REQUIRED | APPROVED | RETIRED
```

```text
PropertyMapping
├─ figmaPropertyName
├─ codePropertyName
├─ propertyType
├─ required
├─ valueMappings
├─ defaultValue
├─ allowedValues
├─ extractionSource
└─ transformationId
```

### 14.2 Mapping 우선순위

```text
1. Published Registry의 componentSetKey
2. 승인된 Figma Component Name Alias
3. 명시적인 Figma Property ↔ Fragment Parameter Mapping
4. 승인된 Layer Path Mapping
5. 규칙 기반 후보
6. AI 후보
```

- 1~4단계는 명시적 계약으로 취급한다.
- 5~6단계는 Preview 후보만 만들 수 있다.
- AI 후보에는 confidence, 근거 Node, 선택 이유를 기록한다.
- 임의 표현식이나 스크립트를 실행하지 않고 승인된 `transformationId`만 사용한다.
- Figma Property 매핑은 Fragment의 시각·표현 Parameter를 연결할 뿐 VO Field나 Controller Action을 확정하지 않는다.
- Mapping 변경은 Registry Version 또는 별도 Mapping Version으로 추적하고 Diff 승인 후 적용한다.

---

## 15. 증분 생성과 Dependency Closure

### 15.1 생성 범위

전체 프로젝트 재생성을 기본값으로 삼지 않는다. 요청과 변경 영향에 따라 다음 범위를 선택한다.

```text
PROJECT
SCREEN
SECTION
COMPONENT
FRAGMENT
STYLE_ARTIFACT
ASSET
```

### 15.2 Dependency Closure

특정 화면이나 Fragment만 생성하더라도 실행과 검증에 필요한 의존 산출물을 함께 계산한다.

```text
Q&A 목록 화면
├─ qna/list.html
├─ layout fragment
├─ search-form fragment
├─ data-table fragment
├─ pagination fragment
├─ token CSS
├─ icon assets
├─ message bundle
└─ Controller Model Contract
```

```text
GenerationScopeManifest
├─ scopeType
├─ scopeId
├─ snapshotId
├─ rootArtifacts[]
├─ dependencyArtifacts[]
├─ preservedArtifacts[]
├─ affectedScreens[]
├─ sourceRevisions
└─ checksums
```

Dependency Closure는 무조건 재생성할 파일 목록이 아니다. `rootArtifacts`는 변경 대상, `dependencyArtifacts`는 빌드·렌더·검증에 필요한 대상, `preservedArtifacts`는 참조하지만 변경하지 않을 대상을 의미한다.

### 15.3 증분 검증

- 변경 Fragment를 사용하는 모든 Screen을 Render Fixture 대상으로 포함한다.
- Token 변경은 해당 Token을 참조하는 Component와 Screen으로 영향 범위를 확장한다.
- Layout Fragment 변경은 전체 Route의 최소 Smoke Render를 요구한다.
- Controller Model Contract가 변경되면 시각 산출물만 변경됐더라도 Binding Gate를 다시 수행한다.
- Dependency Graph를 완전히 계산하지 못하면 전체 프로젝트 재생성보다 `REVIEW_REQUIRED`로 중단한다.

---

## 16. Generated Region Ownership과 Semantic Merge

### 16.1 세 버전 비교

Source Revision 충돌 감지에서 한 단계 더 나아가 파일 내부 병합은 다음 세 버전을 비교한다.

```text
Base Generated Version
Current Repository Version
New Generated Version
```

```text
GenerationOwnershipManifest
├─ artifactPath
├─ generatedRegions[]
├─ protectedRegions[]
├─ bindingRegions[]
├─ previousGeneratedHash
├─ currentRepositoryHash
├─ newGeneratedHash
└─ mergePolicy
```

### 16.2 영역별 정책

| 영역 | 정책 |
|---|---|
| 생성 전용 Fragment | 새 산출물로 교체 가능 |
| 개발자 업무 로직 | 항상 보존 |
| Controller Binding | ScreenSpecification 기반 병합 |
| 수동 CSS | 충돌 시 검토 |
| Token 생성 파일 | Manifest 단위 교체 |
| 권한·보안 코드 | 자동 덮어쓰기 금지 |
| 사용자 작성 JavaScript | 자동 병합 금지 또는 승인된 Hook만 허용 |

### 16.3 Merge 흐름

```text
새 Figma Revision
→ 새 UiDesignSpec Preview
→ 영향 Artifact 계산
→ 새 코드 임시 생성
→ Base/Current/New 비교
→ 안전 영역 자동 병합
→ 충돌 영역 MergePlan 생성
→ 사람 확인
→ Build·Binding·Render 재검증
```

단순 행 단위 Diff만 사용하지 않는다. ScreenSpecification ID, Fragment ID, Binding ID, Component Mapping ID와 같은 안정적인 식별자를 사용해 Semantic Merge 후보를 만든다. 식별자가 유실되거나 중복되면 자동 병합하지 않는다.

---

## 17. 제한된 Agent 후처리

### 17.1 역할

결정론적 Renderer가 먼저 승인된 계약을 생성하고, Agent는 생성 후 발견된 Issue 중 허용된 표현 영역만 수정한다.

```text
Deterministic Generator
  ↓
Generation Issue 분석
  ↓
허용된 Refinement Skill 선택
  ↓
Patch Preview
  ↓
Validator 실행
  ↓
개선된 경우만 후보 유지
  ↓
사람 승인 또는 정책 기반 Apply
```

```text
RefinementTask
├─ taskId
├─ artifactId
├─ issueIds[]
├─ allowedSkills[]
├─ editableRegions[]
├─ protectedRegions[]
├─ maximumSteps
├─ tokenBudget
├─ requiredValidators[]
└─ stopConditions[]
```

### 17.2 허용 범위

- 접근성 Label, ARIA, Keyboard Focus 보완
- 승인된 Responsive Policy 범위의 Overflow·Reflow 보정
- Semantic Token으로 대체 가능한 Raw Value 제거
- 중복 Markup의 승인된 Fragment 추출
- 다국어 적용을 위한 하드코딩 문자열 탐지
- DOM 구조와 CSS 유지보수성 개선

### 17.3 금지 범위

- Controller 업무 처리
- VO Field 의미
- DB Query
- Route와 HTTP Method
- 권한·CSRF·Security 설정
- 승인된 Binding
- ScreenSpecification의 확정 내용

Agent 결과는 독립된 Patch, 사용한 Skill, 변경 근거, Validator 결과를 기록한다. `maximumSteps` 또는 Token Budget 내에서 개선되지 않으면 중단하고 Issue를 반환한다.

---

## 18. Interaction 후보와 업무 Action 분리

Figma의 Prototype 연결과 시각 상태는 상호작용 후보를 제공하지만 업무 Route 또는 권한을 확정하지 않는다.

```text
VisualInteractionCandidate
├─ sourceNodeId
├─ trigger
├─ visualState
├─ transition
├─ targetNode
├─ confidence
└─ evidence
```

```text
ApprovedInteractionBinding
├─ interactionId
├─ actionType
├─ route
├─ httpMethod
├─ permission
├─ confirmationPolicy
├─ validationGroup
└─ screenSpecificationId
```

예를 들어 Figma의 버튼 연결은 클릭 가능한 시각 요소와 이동 후보의 근거다. `DELETE /qna/{id}`, 관리자 권한, 확인 창 정책은 Controller·권한 설정·ScreenSpecification에서 승인해야 한다.

---

## 19. 반영하지 않아야 할 내용

다음 방식은 SpringAI의 책임 경계와 맞지 않는다.

- 생성 HTML을 운영 Thymeleaf로 직접 사용
- Figma Layer를 VO Field로 자동 확정
- 프런트엔드 Interaction을 Controller Action으로 확정
- 반복 Raw Value를 회사 Semantic Token으로 자동 승격
- Preview 성공을 최종 완료로 간주
- 디자인과 업무 코드가 섞인 파일을 무조건 재생성
- Figma 변경으로 Controller Route, Validation, CSRF, 권한을 덮어쓰기
- 자동 추론의 근거와 confidence 없이 Component 또는 Responsive 정책 확정
- MCP 또는 Agent 호출을 승인 근거로 사용
- Agent가 생성 파일 전체를 제한 없이 반복 수정
- Dependency Closure 없이 개별 파일만 교체
- Base/Current/New 비교 없이 생성 코드를 기존 파일에 덮어쓰기
- 임의 Mapping Script를 서버에서 실행

---

## 20. 기존 계획과의 관계

본 검토는 `11_Semantic_Figma_Design_System_Implementation_Plan.md`의 Design-aware Thymeleaf Generator를 대체하지 않는다. 다음 부분을 구체화한다.

| 기존 단계 | 본 검토에서 구체화하는 내용 |
|---|---|
| Figma/디자인 분석 | Input Quality Gate, Node Graph, Design IR |
| 화면 유형 판단 | 시각 근거와 confidence 기록 |
| Component Inventory | Component·Props 후보와 Registry Resolution을 분리하고, 코드 컴포넌트 우선 Mapping과 Property 계약 추가 |
| 회사 Token 매핑 | Semantic Role, Alias, Mode, Raw Value 차단 |
| HTML Skeleton | Design IR과 Binding Contract를 입력으로 명시 |
| Responsive Transformation | ResponsivePolicySet 계약화 |
| Artifact 생성 | Generation Scope·Dependency Closure·Ownership Manifest 추가 |
| Revision 관리 | Base/Current/New Semantic Merge와 MergePlan 추가 |
| Tool 오케스트레이션 | Plugin·REST·MCP·Batch를 공통 Command로 수렴 |
| 생성 후 보정 | 제한된 RefinementTask와 Validator Loop 추가 |
| Build and Render Validation | 구조·시맨틱·A11y·Token·Security·Visual Gate 확장 |

규칙 우선순위는 기존 계획을 유지한다.

1. Controller·VO·DB Schema의 실제 업무 Binding과 보안 제약
2. 승인된 `ScreenSpecification`과 `ThymeleafBindingContract`
3. 회사 `DesignSystemProfile`·Component Registry·Design Token
4. 프로젝트 `DESIGN.md`
5. 화면별 명시 Override
6. Generator 기본값

---

## 21. 최종 권고

SpringAI가 Locofy 분석에서 가져올 것은 외부 생성기가 아니라 다음 설계 원리다.

```text
좋은 Figma 입력을 판정한다.
→ 시각 정보를 명시적 Design IR로 정규화한다.
→ Layout·Responsive·Semantic·Component·Token을 개별 계약으로 관리한다.
→ 자동 추론의 근거·confidence·Issue를 보존한다.
→ 시각 계약과 업무 계약을 서로 다른 Gate에서 확인한다.
→ 결정론적 Renderer가 업무 Binding을 적용한다.
→ 승인된 코드 컴포넌트와 Property Mapping을 우선 사용한다.
→ 변경 범위와 Dependency Closure를 계산해 증분 생성한다.
→ Base/Current/New와 Ownership을 기준으로 병합한다.
→ Agent 후처리는 허용 영역·Step·Validator로 제한한다.
→ 구조·접근성·보안·시각 회귀까지 검증한다.
```

가장 먼저 아키텍처 문서에 반영할 항목은 다음 여섯 가지다.

1. `Design Input Quality Gate → Design IR → UiDesignSpec` 구간
2. `UiDesignSpec Preview/Confirm`과 `ScreenSpecification Approve`의 분리
3. 최종 검증 Gate의 구조·접근성·Token·Security·Visual 세분화
4. `DesignCodeComponentMapping`과 명시적 Property Mapping 우선순위
5. `GenerationScopeManifest`·Dependency Closure·Ownership Manifest
6. 공통 `FigmaGenerationCommand`와 제한된 `RefinementTask`

이를 통해 SpringAI의 기존 원칙인 “Figma는 시각 계약, Controller·VO·ScreenSpecification은 업무 계약”을 유지하면서도 Figma 분석부터 운영 Thymeleaf 검증까지의 흐름을 더 구체적이고 재현 가능한 계약으로 발전시킬 수 있다.

---

## 22. 근거 문서

- [`Locofy_Figma_to_HTML_CSS_기술_검토.md`](./Locofy_Figma_to_HTML_CSS_기술_검토.md)
- [`11_Semantic_Figma_Design_System_Implementation_Plan.md`](./11_Semantic_Figma_Design_System_Implementation_Plan.md)
- [`artifacts/SpringAI_Architecture_Target_Pipeline.html`](./artifacts/SpringAI_Architecture_Target_Pipeline.html)
- [Locofy 공식 사이트](https://www.locofy.ai/)
- [Locofy MCP Quickstart](https://www.locofy.ai/docs/mcp/quickstart/)
- [Locofy Manual Prop Mapping](https://www.dev.locofy.ai/docs/cli/design-system/manual-prop-mapping/)
- [Locofy GitHub Integration and Smart Merge](https://www.locofy.ai/docs/plugin/export-and-deployment/sync-with-github/)
- [Locofy VS Code Extension](https://www.locofy.ai/docs/plugin/export-and-deployment/vs-code-extension/)
- [Locofy Design to Code](https://www.locofy.ai/convert/design-to-code)
