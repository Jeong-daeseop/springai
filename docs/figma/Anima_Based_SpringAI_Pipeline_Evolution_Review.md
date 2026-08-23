# Anima 분석 기반 SpringAI 생성 파이프라인 발전 검토

> 작성일: 2026-08-23  
> 검토 목적: Anima 제품 도입 여부나 다른 Design-to-Code 제품과의 비교가 아니라, Anima 공식 사이트와 공개 문서에서 확인한 처리 방식을 SpringAI 현재 구현에 직접 대조하여 추가 발전 항목을 도출한다.  
> 검토 원칙: Figma는 시각 구조·레이아웃·컴포넌트 선택의 기준이고, Controller·VO·DB Schema·ScreenSpecification은 업무 Binding·Route·Validation·권한의 기준이다.

---

## 1. 검토 결론

Anima 분석에서 SpringAI에 추가 반영할 가치가 가장 큰 부분은 새로운 Figma 변환 엔진이 아니라 다음 운영 계약이다.

1. Figma 표현을 코드로 옮길 수 없을 때 손실과 허용 여부를 기록하는 Rendering Fallback 계약
2. Desktop·Tablet·Mobile의 서로 다른 구조를 하나의 화면 계약으로 통합하는 Responsive Structure 계약
3. 실행 화면·Screenshot·DOM·접근성·Binding·Build 증거를 묶는 Preview Evidence Bundle
4. 장시간 MCP 생성의 진행률·대기·취소·재시도를 관리하는 비동기 Job Protocol
5. 기존 프로젝트에 화면·Breakpoint·Component·Modal 등을 추가하는 유형화된 Design Change Intent
6. 시각 Prototype과 운영 Semantic 화면의 완료 기준을 구분하는 Generation Objective
7. 임의 HTML이 아니라 승인된 Specification을 Figma로 되돌리는 제한된 Reverse Projection
8. Fixture 기반 실행 결과를 안전하게 공유하는 Review Session

SpringAI에는 이미 다음 기반이 존재한다.

- `FigmaGenerationReport`의 Fallback Count, Issue, Quality Gate, Refinement, SSOT Evidence
- Multi-viewport Component Matching과 Breakpoint Observation
- Source Revision 충돌과 멱등 Operation
- Content Hash 기반 Artifact Catalog
- Figma·Thymeleaf Artifact 존재와 Hash를 확인하는 Parity 검증
- Figma Plugin 적용 보고서와 Visual Regression Gate

따라서 기존 기능을 다시 구현하지 않고, 현재 분산된 관찰과 증거를 명시적인 중간 계약과 완료 조건으로 묶는 것이 적합하다.

---

## 2. 검토 범위

### 2.1 검토한 Anima 영역

| 영역 | 확인한 특징 | SpringAI 관점의 의미 |
|---|---|---|
| Figma Plugin | Framework·Styling·Breakpoint·AI Personalization·다중 화면 Flow | 생성 목적과 Target 설정을 분리하고 다중 화면 입력을 계약화 |
| Figma→Code 변환 | CSS 미지원 표현에 대한 근사·제거·SVG·이미지 Fallback | Fallback을 개수보다 Node별 손실과 Gate로 관리 |
| Anima API | 다중 화면, Prototype Interaction, Breakpoint, Preview Image | 실행 및 시각 증거를 하나의 Bundle로 관리 |
| Anima MCP | 비동기 Playground 생성, 완료 대기, Git 기반 후속 작업 | 장시간 생성의 영속 Job·상태 조회·재시도 모델 필요 |
| Mid-project Import | Screen·Breakpoint·Component·Modal·Reference·Replace 유형 | 변경 의도를 자유 텍스트가 아닌 명시적 Command로 변환 |
| Copy to Figma | 실행 코드를 Auto Layout 기반 Figma로 재투영 | HTML 역변환이 아닌 Specification 기반 재투영으로 제한 |
| Playground | 실행·공유·버전 관리 가능한 Preview | 운영 배포와 분리된 Private Review Session 후보 |

### 2.2 범위 밖

- Anima 제품 또는 API 도입
- Anima 생성 코드를 Thymeleaf 생성 입력으로 사용
- React·Vue Renderer 추가
- 범용 Vibe Coding 환경 구축
- Anima와 Locofy·Supernova 등 다른 제품 비교
- 제품이 주장하는 생산성 배수와 Pixel-perfect 성능 평가

---

## 3. 현재 SpringAI 목표 흐름과 확장 위치

### 3.1 유지할 목표 흐름

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

### 3.2 발전된 흐름

```text
Figma Frame·Prototype·Viewport Set
  ↓
Design Input Quality Gate
  ↓
Renderability Assessment
  ├─ Native CSS
  ├─ 허용된 Approximation
  ├─ SVG·Raster Asset
  └─ Blocked Fallback
  ↓
Responsive Structure Reconciliation
  ↓
UiDesignSpec Preview
  ↓
DesignChangeIntent·GenerationObjective 확정
  ↓
DB·Controller·VO·권한 결합
  ↓
APPROVED ScreenSpecification
  ↓
비동기 Generation Job
  ↓
FreeMarker Thymeleaf 생성
  ↓
Binding·Build·Render·A11y·Visual 검증
  ↓
Preview Evidence Bundle
  ↓
검토·승인·Apply
  └─ 선택적으로 Specification 기반 Figma 재투영
```

---

## 4. 우선순위별 발전 항목

| 순위 | 발전 항목 | 기대 효과 | 판단 신뢰도 |
|---:|---|---|---|
| P1 | Renderability Assessment·Fallback Policy | 시각 보존 과정의 의미·접근성 손실 차단 | 높음 |
| P1 | Responsive Structure Set | 서로 다른 Viewport 구조의 통합 근거 확보 | 높음 |
| P1 | Preview Evidence Bundle | 생성 완료를 실제 실행 증거로 판단 | 높음 |
| P2 | Generation Job Protocol | 장시간 MCP 작업의 상태·재시도·취소 관리 | 높음 |
| P2 | Design Change Intent | 중간 프로젝트 변경의 범위·보존 정책 명시 | 높음 |
| P2 | Generation Objective | Prototype과 운영 화면의 Gate 분리 | 높음 |
| P3 | Specification 기반 Reverse Projection | 디자인과 승인 계약 사이의 추적 가능한 재투영 | 중간 |
| P3 | Private Review Session | 실행 결과의 안전한 협업 검토 | 중간 |

---

## 5. Rendering Fallback 계약

### 5.1 Anima에서 확인한 처리 방식

Figma의 모든 표현을 HTML과 CSS로 동일하게 구현할 수 없기 때문에 Anima는 상황에 따라 다음 처리를 사용한다.

- CSS로 직접 표현
- 유사한 CSS 효과로 근사
- 미지원 효과 제거
- 복잡한 Vector를 SVG로 변환
- Font·Mask·일부 Shape 문제를 이미지로 변환
- 명시적으로 Export Image가 설정된 Node를 Asset으로 생성

이 방식에서 참고할 핵심은 Fallback 자체가 아니라, Figma 기능별 지원 여부와 결과 전략을 구분한다는 점이다.

### 5.2 현재 SpringAI와의 차이

`FigmaGenerationReport`는 `fallbackCount`와 Issue를 기록하지만 개수만으로는 다음 질문에 답하기 어렵다.

- 어떤 Node가 Fallback됐는가
- CSS 근사인지 SVG·Raster 변환인지
- 시맨틱과 접근성이 손실됐는가
- 운영 Thymeleaf에서 허용 가능한가
- 생성 후 어떤 Artifact로 대체됐는가

### 5.3 권장 계약

```text
RenderabilityAssessment
├─ assessmentId
├─ sourceNodeId
├─ logicalNodeId
├─ featureType
├─ supportLevel
├─ selectedStrategy
├─ reasonCode
├─ visualLoss
├─ semanticLoss
├─ accessibilityImpact
├─ generatedArtifactId
├─ confidence
├─ issues[]
└─ decision
```

```text
supportLevel:
  NATIVE
  APPROXIMATED
  RASTERIZED
  OMITTED
  UNSUPPORTED

selectedStrategy:
  CSS
  CSS_APPROXIMATION
  SVG
  RASTER_IMAGE
  REMOVE_EFFECT
  BLOCK_GENERATION

decision:
  ACCEPTED
  REVIEW_REQUIRED
  REJECTED
```

### 5.4 Fallback 분류

| 대상 | 허용 정책 |
|---|---|
| 장식용 Blur·Shadow 근사 | 경고 후 허용 가능 |
| 복잡한 장식 Illustration의 SVG 변환 | Asset 검증 후 허용 가능 |
| 장식용 배경의 Raster 변환 | 해상도·용량·라이선스 검증 후 허용 가능 |
| Text의 이미지 변환 | 운영 화면에서 차단 |
| Button·Input·Link의 이미지 변환 | 차단 |
| Form Label·Error Message 제거 | 차단 |
| Table·List의 평면 이미지 변환 | 차단 |
| Focus·Disabled·Error 상태 손실 | 차단 |
| 업무 데이터 Node의 장식용 Asset 변환 | 차단 |

### 5.5 검증 규칙

- `fallbackCount > 0`만으로 실패시키지 않고 각 Assessment의 Severity로 판정한다.
- Semantic Loss 또는 Accessibility Impact가 `HIGH`면 생성 승인을 차단한다.
- Raster Asset에는 Source Revision, Checksum, DPI, 크기, 최적화 정보를 기록한다.
- Unsupported 표현을 조용히 제거하지 않고 명시적인 Issue로 남긴다.
- Preview가 비슷해 보여도 Text·Control Rasterization은 허용하지 않는다.

---

## 6. 다중 Viewport 구조 통합

### 6.1 목적

Desktop, Tablet, Mobile Frame이 항상 동일한 DOM의 크기 변화로 표현되는 것은 아니다. Navigation 교체, Table→Card 변환, Search Panel 이동, 보조 정보 숨김처럼 구조 자체가 바뀔 수 있다.

SpringAI의 `MultiViewportComponentMatcher`는 `selectorHint`와 Parent 관계를 사용하여 표시·숨김·이동을 관찰한다. 다음 단계로 이 관찰을 최종 Responsive Structure 계약으로 확정해야 한다.

### 6.2 권장 계약

```text
ResponsiveStructureSet
├─ structureSetId
├─ canonicalStructure
├─ viewportVariants[]
├─ componentIdentityMappings[]
├─ breakpointTransitions[]
├─ unresolvedStructures[]
├─ confidence
└─ mergeDecision
```

```text
ViewportStructureVariant
├─ viewport
├─ sourceFrameId
├─ rootStructureId
├─ visibleComponents[]
├─ hiddenComponents[]
├─ movedComponents[]
├─ replacedComponents[]
├─ alternateStructures[]
└─ evidence[]
```

```text
ResponsiveMergeStrategy:
  SAME_DOM_REFLOW
  SAME_DOM_VISIBILITY
  COMPONENT_SWAP
  ALTERNATE_NAVIGATION
  ALTERNATE_STRUCTURE
  UNRESOLVED
```

### 6.3 통합 규칙

- 동일한 `logicalNodeId` 또는 Registry Component는 Viewport가 달라도 같은 의미 단위로 취급한다.
- Parent만 변경되면 `MOVED`로 기록하고 DOM Reparent 필요성을 검토한다.
- Desktop Table과 Mobile Card는 단순 Reflow가 아니라 `COMPONENT_SWAP` 또는 `ALTERNATE_STRUCTURE`로 표현한다.
- Viewport별 Component가 달라도 업무 Field Binding은 동일한 ScreenSpecification에서 파생한다.
- 구조가 서로 달라 Identity Mapping을 확정하지 못하면 `UNRESOLVED`로 중단한다.
- Desktop 구조를 Mobile에 강제 축소하거나 Mobile 구조를 Desktop에 확대해 추론하지 않는다.

### 6.4 완료 조건

- 모든 Viewport Node가 Canonical Component 또는 명시적인 Alternate Structure에 연결된다.
- 숨김과 제거를 구분한다.
- Navigation 교체 시 동일 Route·권한 계약이 유지된다.
- Table→Card 변환 시 표시 Field와 Action Binding의 동등성을 검증한다.
- 각 Breakpoint의 Overflow와 Keyboard Order를 검증한다.

---

## 7. Preview Evidence Bundle

### 7.1 목적

코드 생성과 Artifact Hash 일치는 실제 화면의 시각, 상호작용, 접근성, Binding이 정상이라는 충분한 증거가 아니다. 실행 가능한 Preview와 모든 검증 결과를 동일 버전의 Evidence Bundle로 묶어야 한다.

### 7.2 권장 계약

```text
PreviewEvidenceBundle
├─ evidenceBundleId
├─ operationId
├─ screenSpecificationId
├─ screenSpecificationVersion
├─ uiDesignSpecVersion
├─ designSystemSnapshotId
├─ rendererVersion
├─ fixtureModelHash
├─ executablePreviewArtifact
├─ viewportEvidence[]
├─ interactionFlowEvidence[]
├─ bindingEvidence
├─ accessibilityEvidence
├─ securityEvidence
├─ buildEvidence
├─ renderabilityAssessments[]
├─ issues[]
└─ decision
```

```text
ViewportEvidence
├─ viewport
├─ renderedHtmlArtifactId
├─ screenshotArtifactId
├─ domSnapshotArtifactId
├─ accessibilityReportArtifactId
├─ baselineArtifactId
├─ visualDiffArtifactId
├─ diffRatio
├─ threshold
└─ passed
```

### 7.3 Interaction Flow Evidence

```text
InteractionFlowEvidence
├─ flowId
├─ startScreenId
├─ steps[]
├─ routeAssertions[]
├─ permissionFixture
├─ screenshotArtifacts[]
├─ failedStep
└─ passed
```

Figma Prototype 연결은 Flow 후보를 제공하지만 실제 Route, HTTP Method, 권한, Validation은 ScreenSpecification에서 확정한다.

### 7.4 승인 규칙

- Bundle에 포함된 모든 Artifact는 같은 Operation과 Specification Version을 참조해야 한다.
- Build 성공만으로 Bundle을 `VERIFIED`로 전이하지 않는다.
- 필수 Viewport 하나라도 Screenshot 또는 Render Evidence가 누락되면 승인하지 않는다.
- Fixture Model Hash를 기록해 동일 화면을 재현할 수 있어야 한다.
- 시각 Diff와 접근성 실패를 별개 Gate로 유지한다.
- Evidence Artifact가 보존되지 않으면 과거 승인 결과를 재현 가능한 것으로 표시하지 않는다.

---

## 8. 비동기 Generation Job Protocol

### 8.1 필요성

Figma 분석, Asset 처리, Thymeleaf 생성, Build, Browser Render, axe, Screenshot, Visual Diff는 단일 MCP 요청의 응답 시간보다 길어질 수 있다. MCP 연결 상태와 실제 작업 수명을 분리해야 한다.

### 8.2 권장 계약

```text
GenerationJob
├─ jobId
├─ operationId
├─ jobType
├─ status
├─ currentStage
├─ progress
├─ startedAt
├─ heartbeatAt
├─ leaseOwner
├─ leaseExpiresAt
├─ retryCount
├─ cancellationRequested
├─ resultArtifacts[]
├─ issues[]
└─ completedAt
```

```text
JobStatus:
  QUEUED
  RUNNING
  AWAITING_APPROVAL
  AWAITING_EXTERNAL_REPORT
  RETRYABLE_FAILED
  FAILED
  CANCELLED
  SUCCEEDED
```

### 8.3 Tool 계약

```text
startGeneration(...)
getGenerationStatus(jobId)
getGenerationResult(jobId)
cancelGeneration(jobId)
retryGeneration(jobId)
```

### 8.4 운영 규칙

- `operationId`는 업무 작업의 동일성을, `jobId`는 실행 시도를 식별한다.
- 동일 Operation에 활성 Job이 있으면 중복 Job 대신 기존 Job을 반환한다.
- Heartbeat가 만료되면 Lease를 회수하되 Source Revision을 재확인한 후 재시도한다.
- 승인 대기는 실패나 Timeout으로 처리하지 않는다.
- MCP Client 연결 종료가 Job 취소를 의미하지 않는다.
- 재시도는 새 Job ID를 발급하고 이전 시도와 인과관계를 기록한다.
- 취소해도 이미 승인·게시된 Artifact를 삭제하지 않는다.
- 최대 실행 시간, 최대 재시도, Stage별 Timeout을 명시한다.

---

## 9. 유형화된 Mid-project Design Change

### 9.1 목적

기존 화면에 디자인을 추가하거나 교체할 때 자유 텍스트를 곧바로 파일 Diff로 변환하지 않는다. 먼저 변경 의도와 보존 대상을 구조화한다.

```text
DesignChangeIntent
├─ changeIntentId
├─ intentType
├─ targetScreenSpecificationId
├─ targetComponentId
├─ targetViewport
├─ sourceReference
├─ sourceRevision
├─ affectedRegions[]
├─ preservationPolicy
├─ bindingImpact
├─ permissionImpact
├─ requiredGates[]
└─ approvalPolicy
```

```text
intentType:
  ADD_SCREEN
  ADD_VIEWPORT
  ADD_COMPONENT
  ADD_MODAL
  APPLY_VISUAL_REFERENCE
  REPLACE_COMPONENT
  UPDATE_VISUAL_STRUCTURE
```

### 9.2 유형별 Gate

| 변경 유형 | 필수 검증 |
|---|---|
| `ADD_SCREEN` | Route·Controller·Binding·권한·Build·Render |
| `ADD_VIEWPORT` | 기존 Binding 유지·Responsive·Visual·Keyboard Order |
| `ADD_COMPONENT` | Registry·Property Mapping·Fragment Contract |
| `ADD_MODAL` | Focus Trap·Keyboard·ARIA·Action·권한 |
| `APPLY_VISUAL_REFERENCE` | Token·Layout만 변경하고 Binding 보호 |
| `REPLACE_COMPONENT` | Contract 호환성·Migration·Replacement 상태 |
| `UPDATE_VISUAL_STRUCTURE` | Ownership·Merge·Binding·전체 Viewport Render |

### 9.3 보존 규칙

- `ADD_VIEWPORT`는 기존 Controller와 Binding을 변경하지 않는다.
- `APPLY_VISUAL_REFERENCE`는 Route, Action, Permission을 변경할 수 없다.
- `REPLACE_COMPONENT`는 Registry의 승인된 Replacement만 선택한다.
- `ADD_MODAL`은 배경 Focus 차단과 Escape 복귀를 포함한다.
- 영향 범위를 확정할 수 없으면 자동 Apply하지 않는다.

---

## 10. 생성 목적별 Fidelity Profile

### 10.1 목적

시각 Prototype, 디자인 검토 Fixture, 운영 Thymeleaf, Email HTML은 동일한 품질 기준을 사용할 수 없다. Framework보다 먼저 생성 목적과 허용 손실을 고정한다.

```text
GenerationObjective
├─ objectiveType
├─ renderer
├─ semanticRequirement
├─ visualTolerance
├─ accessibilityLevel
├─ rawValuePolicy
├─ fallbackPolicy
├─ fixturePolicy
├─ buildRequired
└─ approvalRequired
```

```text
objectiveType:
  PRODUCTION_SEMANTIC
  VISUAL_PROTOTYPE
  DESIGN_REVIEW_FIXTURE
  EMAIL_DOCUMENT
```

### 10.2 목적별 정책

| Objective | 허용 범위 |
|---|---|
| `PRODUCTION_SEMANTIC` | Semantic HTML·A11y·Binding·Security·Build·Render 필수 |
| `VISUAL_PROTOTYPE` | 업무 Binding 없이 Fixture Data 사용 가능, 운영 배포 금지 |
| `DESIGN_REVIEW_FIXTURE` | 정적·제한적 Interaction Preview만 허용 |
| `EMAIL_DOCUMENT` | Inline CSS와 별도 Compatibility Validator 사용 |

운영 Thymeleaf 생성은 반드시 `PRODUCTION_SEMANTIC`을 사용한다. `VISUAL_PROTOTYPE`의 시각적 성공을 운영 화면 승인 근거로 승격하지 않는다.

---

## 11. 제한된 Reverse Projection

### 11.1 허용 흐름

Anima의 Copy to Figma 흐름에서 참고할 부분은 생성 후 디자인 도구로 되돌아갈 수 있다는 점이다. SpringAI에서는 임의 HTML을 Figma로 변환하지 않고 다음 승인 계약을 재투영한다.

```text
APPROVED ScreenSpecification
+ UiDesignSpec
+ DesignSystem Snapshot
+ ResponsiveStructureSet
→ FigmaExportBundle
```

### 11.2 재투영 대상

- Layout 구조
- Component Instance
- Design Token
- Variant와 시각 상태
- Viewport별 구조
- 시각적 Interaction 후보
- Specification·Registry·Source Revision Metadata

### 11.3 재투영 금지 대상

- Thymeleaf 조건문과 반복문
- Controller Binding
- 권한 표현식
- CSRF
- Validation Logic
- DB Data
- 서버 Route 구현
- 사용자 작성 JavaScript

### 11.4 원칙

- Figma 재투영의 기준은 생성 HTML이 아니라 승인된 Specification이다.
- Figma Export Bundle에 원본 Specification Version과 Checksum을 포함한다.
- Figma에서 재수정되면 새 Source Revision으로 다시 Preview한다.
- 코드에서 발생한 시각 변경을 자동으로 승인된 디자인 계약에 역병합하지 않는다.

---

## 12. Private Review Session

### 12.1 권장 계약

```text
DesignReviewSession
├─ sessionId
├─ operationId
├─ previewEvidenceBundleId
├─ visibility
├─ allowedReviewers[]
├─ expiresAt
├─ reviewStatus
├─ comments[]
└─ approvalReference
```

### 12.2 보안 규칙

- 기본 Visibility는 `PRIVATE`이다.
- 만료 시간을 필수로 지정한다.
- 운영 DB Data를 사용하지 않고 검증된 Fixture만 사용한다.
- API Key, MCP Token, Session Cookie를 Preview에 전달하지 않는다.
- 조회, Comment, 승인 권한을 분리한다.
- 외부 Resource는 Allowlist와 CSP를 적용한다.
- Session 만료와 Evidence Artifact 보존 기간을 분리한다.
- 공개 URL 발급은 별도 권한과 확인을 요구한다.

Review Session은 운영 배포 수단이 아니라 Evidence Bundle을 사람이 확인하는 임시 검토 Surface다.

---

## 13. 기존 구현과의 연결

| 현재 구현 | 발전 연결점 |
|---|---|
| `FigmaGenerationReport.fallbackCount` | Node별 `RenderabilityAssessment`로 확장 |
| `FigmaGenerationReport.qualityGates` | `PreviewEvidenceBundle`의 Viewport·A11y·Visual Evidence로 통합 |
| `MultiViewportComponentMatcher` | 관찰 결과를 `ResponsiveStructureSet`으로 확정 |
| `FigmaDesignOperation` | 업무 Operation과 실행 `GenerationJob`을 분리 |
| `SourceRevisionRef` | Job 재시도와 DesignChangeIntent 충돌 검증에 사용 |
| `ArtifactService`·Artifact Catalog | Evidence Bundle의 Content-addressed Artifact 저장에 사용 |
| `DesignParityValidationUseCase` | Hash 존재 검증에서 실제 Render Evidence 연결로 확장 |
| `FigmaDesignOrchestrationService` | 유형화된 DesignChangeIntent Dispatcher로 확장 |
| `FigmaExportBundle` | Specification 기반 Reverse Projection 산출물로 유지 |

주요 코드 위치:

- `src/main/java/com/krdevops/springai/model/figma/ops/FigmaGenerationReport.java`
- `src/main/java/com/krdevops/springai/service/MultiViewportComponentMatcher.java`
- `src/main/java/com/krdevops/springai/service/parity/DesignParityValidationUseCase.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaDesignOrchestrationService.java`
- `src/main/java/com/krdevops/springai/mapper/FigmaDesignOperationRepository.java`
- `src/main/java/com/krdevops/springai/service/artifact/ArtifactService.java`

---

## 14. 반영하지 않아야 할 내용

- Anima 생성 코드를 운영 Thymeleaf로 직접 사용
- 시각적으로 비슷하다는 이유로 Text·Control을 이미지로 변환
- 서로 다른 Viewport 구조를 Desktop DOM 하나로 강제 통합
- Preview URL이 열렸다는 사실을 최종 검증으로 간주
- MCP 연결 종료 시 영속 Job과 Artifact를 자동 삭제
- 자유 텍스트 변경 요청으로 업무 Binding과 권한을 수정
- `VISUAL_PROTOTYPE` 결과를 `PRODUCTION_SEMANTIC`으로 자동 승격
- 임의 HTML·JavaScript·서버 Template을 Figma로 역변환
- 실제 DB Data와 인증 Token을 Review Session에 포함
- Screenshot만으로 상호작용과 접근성을 검증했다고 판단

---

## 15. 권장 도입 순서

### 15.1 1단계: 손실과 구조의 명시화

- `RenderabilityAssessment` 도입
- Fallback Severity와 차단 정책 정의
- `ResponsiveStructureSet` 도입
- Desktop·Tablet·Mobile 구조 통합 Fixture 구축

### 15.2 2단계: 완료 증거 통합

- `PreviewEvidenceBundle` 도입
- Screenshot·DOM·axe·Binding·Build Artifact 연결
- Fixture Model Hash와 Renderer Version 기록
- Evidence 누락 시 `VERIFIED` 전이 차단

### 15.3 3단계: 장시간 실행과 변경 의도

- `GenerationJob`과 Stage별 상태·Heartbeat·Lease 도입
- MCP Tool을 Start·Status·Result·Cancel·Retry로 분리
- `DesignChangeIntent`와 유형별 필수 Gate 도입
- `GenerationObjective`에 따른 Renderer·Fallback·검증 정책 분기

### 15.4 4단계: 협업 확장

- Specification 기반 Reverse Projection 강화
- Private·만료형 Review Session
- 실제 운영 요구가 있을 때 Email 전용 Objective와 Validator 검토

---

## 16. 완료 조건

- 모든 Fallback이 대상 Node, 전략, 손실, Artifact, 결정 상태를 가진다.
- Text·Control Rasterization이 운영 생성에서 차단된다.
- 모든 Viewport 구조가 Canonical Structure 또는 명시적 Alternate Structure에 연결된다.
- Screen 승인에 사용된 실행·Screenshot·DOM·A11y·Binding·Build 증거를 하나의 Bundle로 조회할 수 있다.
- MCP Client 연결과 무관하게 Generation Job의 상태를 조회·취소·재시도할 수 있다.
- 중간 디자인 변경이 유형화되고 각 유형의 보존 정책과 필수 Gate를 가진다.
- 운영 Thymeleaf가 `PRODUCTION_SEMANTIC` 목적과 전체 Gate를 통과한다.
- Figma 재투영이 승인된 Specification과 Registry Version을 기준으로 수행된다.
- Review Session이 Fixture만 사용하고 권한·만료 정책을 준수한다.

---

## 17. 근거·추론·미확정 사항

### 17.1 확인된 근거

- Anima 공개 문서는 Figma 표현의 지원·근사·효과 제거·SVG·이미지 Fallback을 구분한다.
- Anima API는 다중 화면, Prototype Interaction, Breakpoint, Preview Image, 서로 다른 Layout의 통합을 지원 대상으로 설명한다.
- Anima MCP는 비동기 Playground 생성과 완료 대기, Git 기반 후속 작업을 제공한다.
- Anima는 기존 프로젝트에 Screen, Breakpoint, Component, Modal, Visual Reference, Replacement를 유형별로 추가하는 흐름을 제공한다.
- SpringAI에는 Fallback Count, Multi-viewport Observation, Artifact Hash, Generation Report, Visual Gate의 기반이 존재한다.

### 17.2 근거에서 도출한 판단

- SpringAI에는 변환 불가능한 표현을 조용히 이미지로 만드는 것보다 의미 손실을 판정하는 계약이 우선 필요하다.
- Multi-viewport 관찰 결과와 최종 Renderer 사이에 구조 통합 계약이 필요하다.
- Artifact 존재·Hash 검증과 실제 실행·시각·접근성 증거를 동일 Bundle로 묶어야 승인 재현성이 높아진다.
- MCP 장시간 작업은 동기 요청보다 기존 Operation·Artifact에 연결된 영속 Job으로 관리하는 편이 적합하다.
- Figma로 되돌리는 기준은 런타임 HTML이 아니라 승인된 시각·화면 Specification이어야 한다.

### 17.3 현재 확인할 수 없는 사항

- 실제 생성 작업이 MCP Timeout을 초과하는 빈도
- 이미지 Fallback이 현재 화면에서 발생하는 수와 유형
- 서로 다른 Desktop·Mobile 구조를 통합해야 하는 실제 화면 수
- 외부 공유형 Preview가 조직 보안 정책상 허용되는지
- Email 전용 Renderer가 프로젝트 범위에 필요한지
- Job Worker를 단일 서버 내부 Executor로 운영할지 별도 Queue로 분리할지

이 항목들은 구현 범위로 확정하지 않고 Runtime 측정과 운영 정책 확인 후 결정한다.

---

## 18. 최종 제안

SpringAI의 책임 흐름은 유지한다.

```text
Figma = 시각 구조·Layout·Component 선택 후보
Controller·VO·DB Schema = 실제 업무 계약
ScreenSpecification = 시각 계약과 업무 계약의 승인 경계
FreeMarker Renderer = 결정론적 코드 생성
Validation Gate = 운영 가능 여부 판정
```

Anima 분석에서 추가할 핵심은 다음과 같다.

```text
표현 불가능성을 Renderability Assessment로 명시한다.
→ 여러 Viewport의 구조를 Responsive Structure Set으로 통합한다.
→ 생성 작업을 영속 Generation Job으로 실행한다.
→ 실행·시각·접근성·Binding 증거를 Preview Evidence Bundle로 묶는다.
→ 중간 변경은 Design Change Intent와 목적별 Gate를 거친다.
→ Figma 재투영은 승인된 Specification만 기준으로 삼는다.
```

이 구조를 적용하면 시각적으로 유사한 결과를 만드는 데서 멈추지 않고, 어떤 손실과 변환이 발생했는지 설명할 수 있고, 여러 Viewport와 장시간 생성 작업을 재현 가능하게 관리하며, 최종 운영 Thymeleaf의 승인 근거를 하나의 계약으로 추적할 수 있다.

---

## 참고 자료

- [Anima 공식 사이트](https://www.animaapp.com/)
- [Anima Figma Plugin: Design to Code in Figma](https://support.animaapp.com/en/articles/11721866-anima-figma-plugin-design-to-code-in-figma)
- [How Anima Translates Figma Design Settings into Code](https://support.animaapp.com/en/articles/6228806-how-anima-translates-figma-design-settings-into-code)
- [Anima API: Figma to Code and Clone Any Site](https://support.animaapp.com/en/articles/11722262-anima-api-figma-to-code-clone-any-site)
- [Anima MCP](https://docs.animaapp.com/docs/anima-mcp)
- [Starting from Figma](https://docs.animaapp.com/docs/starting-from-figma)
- [Anima MCP: Connect Your AI Coding Agent](https://www.animaapp.com/blog/code/connect-your-ai-coding-agent-to-anima-playground-and-figma-with-mcp/)
- [Mid-project Import and Copy to Figma](https://www.animaapp.com/blog/product-updates/two-major-features-for-the-figma-workflow-mid-project-import-copy-to-figma/)
