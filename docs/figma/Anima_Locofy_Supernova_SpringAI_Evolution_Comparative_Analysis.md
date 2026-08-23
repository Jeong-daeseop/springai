# Anima·Locofy·Supernova 검토 기반 SpringAI 발전 방향 비교분석

> 작성일: 2026-08-23  
> 검토 목적: Anima, Locofy, Supernova의 제품 우열이나 도입 여부를 비교하는 것이 아니라, 각 공식 자료에서 도출한 SpringAI 발전 제안을 동일한 아키텍처 축으로 재분류하고 중복·상호보완·충돌 가능성을 분석한다.  
> 기준 문서: `Anima_Based_SpringAI_Pipeline_Evolution_Review.md`, `Locofy_Flow_Based_SpringAI_Pipeline_Evolution_Review.md`, `Supernova_Based_SpringAI_DesignOps_Evolution_Review.md`

---

## 1. 종합 결론

세 검토 결과는 경쟁하거나 서로를 대체하는 관계가 아니다. SpringAI 파이프라인의 서로 다른 계층을 보완한다.

| 검토 대상 | 가장 강하게 보완하는 계층 | 핵심 질문 |
|---|---|---|
| Supernova | 디자인 시스템 운영·거버넌스 | 어떤 디자인 시스템 버전과 컴포넌트를 사용할 수 있는가 |
| Locofy | 디자인 해석·코드 생성 | Figma를 어떻게 구조화된 생성 계약으로 바꿀 것인가 |
| Anima | 실행·검증·변경 작업 | 생성 결과가 실제로 실행 가능하고 손실 없이 검증됐는가 |

세 관점을 통합하면 다음 구조가 된다.

```text
Supernova 관점
Design System Control Plane
Profile·Registry·Token·문서·Health·Version
                    │
                    ▼
Locofy 관점
Design Interpretation & Compilation
Quality Gate·Design IR·Mapping·Scope·Merge
                    │
                    ▼
Controller·VO·DB·권한 결합
                    │
                    ▼
APPROVED ScreenSpecification
                    │
                    ▼
Anima 관점
Generation Execution & Verification
Job·Fallback·Responsive Structure·Evidence·Review
```

이를 한 문장으로 정리하면 다음과 같다.

> Supernova는 무엇을 사용할 수 있는지 관리하고, Locofy는 디자인을 어떻게 생성 계약으로 해석할지 정의하며, Anima는 그 결과가 실제로 안전하게 실행되고 검증됐는지 증명한다.

---

## 2. 비교 기준

세 검토 결과를 다음 공통 축으로 비교한다.

1. 디자인 시스템 기준과 버전
2. Figma 입력 해석
3. Component·Token·Asset 처리
4. Responsive 처리
5. 업무 Binding 경계
6. 생성 범위와 코드 병합
7. 실행과 장시간 작업
8. 검증과 증거
9. 변경 이벤트와 운영 자동화
10. AI 권한과 사람 승인

비교 결과는 제품 기능의 존재 여부보다 SpringAI에 어떤 책임 계약이 필요한지를 기준으로 판단한다.

---

## 3. 검토 결과별 핵심 역할

### 3.1 Locofy: 디자인을 생성 계약으로 변환

Locofy 검토는 세 문서 중 생성 파이프라인 내부를 가장 상세하게 다룬다.

주요 발전 항목:

- Design Input Quality Gate
- Figma Node Graph와 Design IR
- Layout·Responsive Policy
- Component·Props·Semantic 후보 분리
- Token·Asset 정규화
- 코드 컴포넌트 우선 Mapping
- 증분 생성과 Dependency Closure
- Generated Region Ownership
- Base/Current/New Semantic Merge
- 다중 진입점의 단일 Command 수렴
- 제한된 Agent 후처리

책임 범위:

```text
Figma
→ 해석 가능한 입력인지 검사
→ 프레임워크 독립 Design IR
→ UiDesignSpec
→ 생성 범위와 코드 컴포넌트 결정
→ 안전한 증분 생성·병합
```

핵심 강점은 Figma Node를 바로 HTML로 변환하지 않고 명시적인 중간 계약을 둔다는 점이다.

### 3.2 Anima: 실행 가능성과 표현 손실 통제

Anima 검토는 생성 과정에서 발생하는 표현 손실, 실제 실행, 장시간 작업, 검증 증거를 가장 구체적으로 다룬다.

주요 발전 항목:

- Node별 `RenderabilityAssessment`
- CSS·SVG·Raster·효과 제거·차단 구분
- `ResponsiveStructureSet`
- `PreviewEvidenceBundle`
- 장시간 실행용 `GenerationJob`
- 유형화된 `DesignChangeIntent`
- `GenerationObjective`
- Specification 기반 Reverse Projection
- Private Review Session

책임 범위:

```text
생성할 수 없는 Figma 표현은 어떻게 처리됐는가?
→ 여러 Viewport의 구조는 어떻게 통합됐는가?
→ 장시간 생성 작업은 어떤 상태인가?
→ Build·Render·A11y·Visual 검증을 통과했는가?
→ 승인에 사용한 Evidence를 재현할 수 있는가?
```

핵심 강점은 코드 생성 완료와 운영 가능한 화면 검증 완료를 구분하는 점이다.

### 3.3 Supernova: 디자인 시스템 운영 기준 통합

Supernova 검토는 개별 화면 생성보다 디자인 시스템 전체의 운영과 게시 기준에 집중한다.

주요 발전 항목:

- `DesignSystemKnowledgeSnapshot`
- Component Health·Governance
- Token·Asset Export Pipeline
- 디자인 시스템 전용 Search/RAG
- Event-driven DesignOps
- Multi-brand·Theme 상속
- 문서·사용 분석
- Component Playground

책임 범위:

```text
어떤 Profile·Registry·Token·문서 버전을 사용했는가?
→ 해당 Component는 생성 가능한 상태인가?
→ Deprecated Component가 사용되지 않았는가?
→ Token·Asset 산출물을 재현할 수 있는가?
→ 디자인 시스템 변경이 어떤 화면에 영향을 주는가?
```

핵심 강점은 디자인 시스템을 파일 모음이 아니라 버전형 운영 계약으로 보는 점이다.

---

## 4. 계층별 비교

| 아키텍처 계층 | Locofy 검토 | Anima 검토 | Supernova 검토 |
|---|---|---|---|
| 디자인 시스템 기준 | Registry Resolution | Snapshot 참조와 Generation Objective | Knowledge Snapshot·Health·Lifecycle |
| Figma 입력 | Quality Gate·Node Graph·Design IR | Renderability·Fallback | Figma Variable·Component 운영 동기화 |
| Component | Code Component·Property Mapping | Viewport Swap·표현 가능성 | 게시·Health·Replacement·문서 |
| Token | Raw Value→Semantic Token 정규화 | CSS·SVG·Raster 표현 전략 | 대상별 Token Export·Manifest |
| Asset | 중복 제거·Asset Contract | Fallback Asset와 손실 판정 | Asset SSOT·Export·Metadata |
| Responsive | Responsive Policy 추출 | 실제 Viewport 구조 통합 | Theme·Platform 지원 범위 |
| Interaction | 시각 후보와 업무 Action 분리 | Flow Evidence·Modal 검증 | Component State·Playground |
| 생성 | Scope·Dependency·Renderer | Job·Objective | 생성 입력 Snapshot 제공 |
| 병합 | Ownership·Base/Current/New Merge | Design Change Intent | Event와 영향도 전파 |
| 검증 | 다층 Gate | Evidence Bundle | Component Health와 게시 Gate |
| AI | 제한된 Refinement | Job 내 제한적 실행·Review | 승인 Snapshot 전용 Search/RAG |
| 운영 | Command 수렴·Revision | 비동기 Job·Review Session | Event·Analytics·Multi-brand |

---

## 5. 동일 주제의 책임 분리

### 5.1 Responsive

| 검토 | 책임 |
|---|---|
| Supernova | Brand·Theme·Platform별 Breakpoint 및 지원 기준 |
| Locofy | Figma Constraint에서 Responsive Policy 추출 |
| Anima | 여러 Viewport의 실제 구조 차이를 통합하고 검증 |

통합 흐름:

```text
Design System Breakpoint·Theme 기준
→ Figma에서 Responsive Policy 추출
→ Desktop·Tablet·Mobile 구조 비교
→ Responsive Structure 확정
→ Viewport별 Render Evidence 생성
```

`ResponsivePolicySet`과 `ResponsiveStructureSet`은 중복 모델이 아니다.

- `ResponsivePolicySet`: 변환 의도와 규칙
- `ResponsiveStructureSet`: Viewport별 실제 구조와 통합 결과

### 5.2 Component

| 검토 | 책임 |
|---|---|
| Supernova | 사용할 수 있는 Component와 Lifecycle·Health·Replacement |
| Locofy | Figma Component·Property를 실제 코드 Component에 매핑 |
| Anima | Target Renderer에서 표현 가능한지와 Viewport Swap을 판단 |

통합 흐름:

```text
Component Registry Health
→ Figma Component 후보
→ Code Component·Property Mapping
→ Viewport별 Component Swap
→ Renderability Assessment
→ 생성·검증
```

권장 계약 경계:

```text
ComponentRegistryEntry
  = 어떤 컴포넌트를 사용할 수 있는가

DesignCodeComponentMapping
  = Figma Component·Property를 코드에 어떻게 연결하는가

RenderabilityAssessment
  = 해당 연결을 Target에서 안전하게 표현할 수 있는가
```

### 5.3 Token·Asset

| 검토 | 책임 |
|---|---|
| Locofy | Figma Raw Value를 Semantic Token과 Asset으로 정규화 |
| Anima | 표현 불가능한 Node의 SVG·Raster Fallback과 손실 관리 |
| Supernova | 승인된 Token·Asset을 대상별 코드로 Export |

통합 흐름:

```text
Figma Variable·Raw Value
→ Semantic Token Resolution
→ Renderability·Asset Fallback 판정
→ APPROVED Token Snapshot
→ CSS·TypeScript·Asset Manifest 생성
```

Manifest 책임:

- `AssetManifest`: Asset 자체의 출처·Checksum·License
- `TokenExportManifest`: Token 코드 산출물과 대상 형식
- `PreviewEvidenceBundle`: 생성 화면에서 실제 사용·검증된 증거

### 5.4 변경 관리

| 검토 | 책임 |
|---|---|
| Supernova | 변경 Event와 영향 전파 |
| Anima | 변경 의도·비동기 실행·완료 증거 |
| Locofy | 변경 범위·Dependency·Ownership·Semantic Merge |

통합 흐름:

```text
DesignSystemEvent 또는 Figma Revision 변경
→ DesignChangeIntent
→ 영향 Screen·Artifact 계산
→ GenerationScopeManifest
→ GenerationJob 실행
→ Base/Current/New Merge Preview
→ PreviewEvidenceBundle
→ 승인·Apply
```

### 5.5 Preview와 Playground

| 검토 | 책임 |
|---|---|
| Supernova Component Playground | Design System Component·Variant·State 탐색 |
| Anima Review Session | 생성된 Screen Flow의 실행·검토·승인 보조 |
| Locofy UiDesignSpec Preview | 시각 계약 후보의 교정·확정 |

세 Preview Surface를 하나로 합치지 않는다. 각각 대상과 승인 의미가 다르다.

---

## 6. 통합 Aggregate 제안

세 문서의 모든 제안 모델을 독립적으로 구현하면 ID·Version·Status가 지나치게 많아질 수 있다. 다음 다섯 Aggregate로 책임을 묶는 것이 적합하다.

### 6.1 Design System Control Plane

```text
DesignSystemKnowledgeSnapshot
├─ DesignSystemProfile
├─ Component Registry
├─ Component Health
├─ Token Version
├─ Asset Manifest
├─ Documentation Version
├─ Compatibility Metadata
└─ Publish Status
```

책임:

- 생성에 사용할 수 있는 디자인 시스템 기준 제공
- Component Lifecycle과 Replacement 관리
- Token·Asset·문서·코드 산출물 버전 고정
- APPROVED/PUBLISHED 정보만 생성과 Search에 제공

### 6.2 Design Interpretation

```text
UiDesignSpec
├─ Design Input Quality
├─ Design IR
├─ Layout Policy
├─ Responsive Policy
├─ Responsive Structure
├─ Component Mapping
├─ Renderability Assessment
├─ Token·Asset Resolution
├─ Interaction Candidates
├─ confidence
└─ issues[]
```

책임:

- Figma의 시각 정보를 프레임워크 독립 계약으로 정규화
- 자동 추론과 명시 계약을 구분
- 업무 Binding 이전의 시각 Preview 제공

### 6.3 Approved Screen Contract

```text
ScreenSpecification
├─ UiDesignSpec Reference
├─ DesignSystemSnapshotId
├─ Controller·VO·DB Binding
├─ Route·Validation·Permission
├─ Approved Interaction Binding
├─ Generation Objective
├─ approvedBy
└─ approvedAt
```

책임:

- 시각 계약과 업무 계약의 최종 결합
- FreeMarker Renderer가 사용할 유일한 승인 입력
- Figma나 AI가 업무 의미를 우회 확정하지 못하도록 차단

### 6.4 Generation Operation

```text
GenerationOperation
├─ DesignChangeIntent
├─ GenerationScopeManifest
├─ Dependency Closure
├─ Generation Jobs[]
├─ Ownership Manifest
├─ Merge Plan
├─ Source Revisions
└─ Result Artifact References
```

책임:

- 사용자 요청과 실행 시도를 분리
- 영향 범위와 의존 산출물 계산
- Base/Current/New 기반 병합
- 장시간 실행과 재시도·취소 관리

### 6.5 Verification Evidence

```text
PreviewEvidenceBundle
├─ Generated Artifacts
├─ Token·Asset Manifest Reference
├─ Binding Evidence
├─ Build Evidence
├─ Viewport Render Evidence
├─ Accessibility Evidence
├─ Visual Diff Evidence
├─ Interaction Flow Evidence
├─ Renderability Assessments
└─ Final Decision
```

책임:

- 코드 생성과 검증 완료를 구분
- 승인에 사용한 증거를 동일 버전으로 고정
- 과거 결과의 재현 가능 여부 제공

---

## 7. Source of Truth 계층

통합 후 기준 우선순위는 다음과 같다.

1. Controller·VO·DB Schema의 실제 업무 Binding과 보안 제약
2. APPROVED `ScreenSpecification`
3. PUBLISHED `DesignSystemKnowledgeSnapshot`
4. 승인된 `DesignCodeComponentMapping`
5. 프로젝트 `DESIGN.md`
6. `UiDesignSpec`의 시각 후보와 명시 Override
7. Generator 기본값

다음은 Source of Truth가 아니다.

- Figma Layer Name만으로 추론한 업무 의미
- AI 답변과 추천
- WYSIWYG Preview
- 문서의 설명 문구
- 실행 성공한 Generation Job
- 단일 Screenshot

---

## 8. 상태 모델 분리

세 검토 문서는 서로 다른 상태 축을 제안한다. 상태를 하나의 Enum으로 합치지 않는다.

| 상태 축 | 대표 상태 | 소유 Aggregate |
|---|---|---|
| 디자인 시스템 게시 | DRAFT, APPROVED, PUBLISHED, RETIRED | Knowledge Snapshot |
| 화면 명세 승인 | DRAFT, REVIEW_REQUIRED, APPROVED, REJECTED | ScreenSpecification |
| 작업 실행 | QUEUED, RUNNING, FAILED, SUCCEEDED | GenerationJob |
| Artifact 수명주기 | ACTIVE, QUARANTINED, RETIRED | Artifact Catalog |
| Component Health | HEALTHY, WARNING, BLOCKED, DEPRECATED | Component Health |
| 검증 결과 | PASSED, FAILED, BASELINE_CREATED | Evidence Bundle |
| 변경 Operation | ANALYZED, PREVIEW_READY, APPLY_REQUIRED, APPLIED, CONFLICT | GenerationOperation |

금지할 상태 전이:

- Job 성공 → ScreenSpecification 자동 승인
- Component `HEALTHY` → Registry 자동 게시
- Evidence 통과 → Artifact 자동 배포
- Snapshot 게시 → 기존 Screen 자동 재생성
- Event 발생 → Apply 자동 실행
- Review Session 승인 → 권한·Binding 자동 변경

---

## 9. Event·Operation·Job·Artifact·Evidence 관계

각 용어의 책임을 명확히 구분한다.

```text
Operation
  = 사용자가 요청한 업무 단위

Event
  = 이미 발생한 변경 사실

Job
  = 실행할 비동기 작업 또는 실행 시도

Artifact
  = 생성된 불변 결과물

Evidence
  = 특정 계약 버전의 검증 근거 묶음
```

권장 관계:

```text
Operation
→ Event
→ Job
→ Artifact
→ Evidence
→ 사람 승인
→ Apply
```

예시:

```text
UPDATE_VISUAL_STRUCTURE Operation
→ FIGMA_SOURCE_CHANGED Event
→ Thymeleaf Generation Job
→ HTML·CSS·Screenshot Artifact
→ PreviewEvidenceBundle
→ 승인
→ Repository Apply
```

Event는 명령이 아니며 Job은 승인 결과가 아니다.

---

## 10. AI와 Tool 권한 경계

### 10.1 허용

- Figma 구조와 Layout 후보 추출
- Component·Property Mapping 초안
- Responsive Structure 후보 생성
- 변경 영향도 분석
- Deprecated Component와 Replacement 안내
- Token·Component·Guideline 검색
- 접근성·반응형·Token 정규화 Patch 제안
- Issue 분류와 설명

### 10.2 금지

- ScreenSpecification 최종 승인
- Registry Apply·게시
- DB·VO·Controller Binding 확정
- Route·HTTP Method·권한 변경
- 충돌 코드 무조건 덮어쓰기
- Preview 성공을 운영 배포로 승격
- Agent 반복 실행으로 Gate 우회
- Design System Event만으로 자동 Apply

### 10.3 실행 원칙

```text
Analyze
→ Preview
→ Review
→ Approve
→ Generate
→ Validate
→ Apply
```

Plugin, REST, MCP, Batch, Event, Agent 중 어느 진입점도 이 경계를 우회할 수 없다.

---

## 11. 버전과 추적성

모든 생성과 검증 결과는 다음 식별자를 추적해야 한다.

```text
sourceRevision
uiDesignSpecVersion
screenSpecificationVersion
designSystemSnapshotId
registryVersion
componentMappingVersion
rendererVersion
generationOperationId
generationJobId
artifactHash
evidenceBundleId
```

최소 불변식:

- `ScreenSpecification`은 생성에 사용한 `designSystemSnapshotId`를 기록한다.
- `GenerationJob`은 Operation과 실행 시도를 모두 식별한다.
- `Artifact`는 Source Revision과 Content Hash를 가진다.
- `PreviewEvidenceBundle`은 Artifact와 Specification Version을 고정한다.
- Source Revision이 바뀌면 기존 Preview를 자동 Apply하지 않는다.
- PUBLISHED Snapshot은 변경하지 않고 새 버전을 생성한다.

---

## 12. 통합 우선순위

### 12.1 P0: 공통 기준

1. `DesignSystemKnowledgeSnapshot`
2. `UiDesignSpec`의 Design IR·Responsive·Renderability 확장
3. `ScreenSpecification`에 Snapshot과 Generation Objective 연결
4. `PreviewEvidenceBundle`

이 네 계약이 상위 기준이 되지 않으면 이후 Manifest와 Job이 서로 다른 버전을 참조할 수 있다.

### 12.2 P1: 생성 정확성과 안전성

5. `DesignCodeComponentMapping`
6. `ResponsiveStructureSet`
7. Component Health Gate
8. `GenerationScopeManifest`와 Dependency Closure
9. `GenerationOwnershipManifest`와 Semantic Merge

### 12.3 P2: 실행과 자동화

10. `DesignChangeIntent`
11. `GenerationJob`
12. Event-driven DesignOps
13. Token·Asset Export Pipeline
14. 제한된 `RefinementTask`

### 12.4 P3: 운영 확장

15. Design System 전용 Search/RAG
16. Private Review Session
17. Multi-brand·Theme
18. Component Playground
19. 문서·사용 분석
20. Specification 기반 Reverse Projection

---

## 13. 통합 위험과 대응

### 13.1 계약 과잉

세 문서의 모든 제안 모델을 독립 클래스로 만들면 ID, Version, Status, Repository가 폭증한다.

대응:

- 상위 다섯 Aggregate를 먼저 확정한다.
- 작은 개념은 Value Object로 포함한다.
- 독립 조회·수명주기·승인 상태가 필요한 경우에만 별도 Aggregate로 분리한다.

### 13.2 Manifest 중복

`GenerationScopeManifest`, `GenerationOwnershipManifest`, `TokenExportManifest`, `AssetManifest`, `PreviewEvidenceBundle`이 같은 Artifact 목록과 Hash를 반복 저장할 수 있다.

책임 기준:

| 질문 | 계약 |
|---|---|
| 무엇을 생성하는가 | `GenerationScopeManifest` |
| 누가 어느 영역을 수정할 수 있는가 | `GenerationOwnershipManifest` |
| 무엇이 생성됐는가 | Artifact Catalog |
| Token이 어떤 대상 코드로 생성됐는가 | `TokenExportManifest` |
| 무엇을 어떤 기준으로 검증했는가 | `PreviewEvidenceBundle` |

Manifest는 Artifact 본문을 복제하지 않고 Artifact ID와 Hash를 참조한다.

### 13.3 Event와 Job 혼동

Event Consumer가 직접 Apply까지 수행하면 승인 경계를 우회할 수 있다.

대응:

- Event는 Preview 또는 Job 생성까지만 수행한다.
- Job 완료는 `APPLY_REQUIRED` 후보를 만들 수 있지만 Apply하지 않는다.
- Operation, Event, Job의 Correlation과 Causation ID를 기록한다.

### 13.4 Visual Fidelity의 과대평가

시각적으로 동일해도 Text나 Control이 이미지로 바뀌면 운영 화면으로 승인할 수 없다.

대응:

- `PRODUCTION_SEMANTIC` Objective를 운영 Thymeleaf의 필수값으로 사용한다.
- Semantic Loss와 Accessibility Impact를 Visual Diff보다 우선한다.
- Text·Form·Table Rasterization을 차단한다.

### 13.5 문서와 Runtime 진실 혼동

디자인 시스템 문서와 Search/RAG는 설명·탐색 계층이다.

대응:

- 실제 생성 가능 여부는 Registry, Snapshot, ScreenSpecification, Validator가 결정한다.
- AI 답변은 승인 근거가 아니라 출처를 포함한 안내로 취급한다.
- 문서가 최신이어도 Runtime 검증을 생략하지 않는다.

### 13.6 Preview Surface 혼동

UiDesignSpec Preview, Component Playground, Review Session은 승인 의미가 다르다.

대응:

- UiDesignSpec Preview는 시각 계약 교정용이다.
- Component Playground는 디자인 시스템 상태 탐색용이다.
- Review Session은 생성된 화면과 Evidence 검토용이다.
- 어느 Preview도 단독으로 운영 Apply 권한을 갖지 않는다.

---

## 14. 구현하지 않아야 할 통합 방식

- 세 제품의 기능을 각각 별도 Subsystem으로 복제
- Figma·AI·문서 중 하나를 업무 Binding의 기준으로 사용
- `UiDesignSpec`, `ScreenSpecification`, Knowledge Snapshot을 하나의 거대 모델로 병합
- Component Registry 상태와 Generation Job 상태를 하나의 Enum으로 관리
- Screenshot 유사도만으로 화면 승인
- Design System Event 발생 즉시 코드 생성·Commit·배포
- Agent가 Ownership과 Protected Region을 무시하고 전체 파일 수정
- Prototype용 결과를 운영 Thymeleaf로 자동 승격
- Artifact 본문과 Hash를 여러 Manifest에 중복 저장
- 코드→Figma 결과를 승인된 ScreenSpecification에 자동 역병합

---

## 15. 완료 조건

세 검토 결과가 통합됐다고 판단하려면 다음 조건을 충족해야 한다.

- 생성 Screen이 사용한 Design System Snapshot을 역추적할 수 있다.
- Figma 입력이 Design IR과 UiDesignSpec으로 정규화된다.
- Component 선택이 Registry Health와 Code Mapping을 모두 통과한다.
- 여러 Viewport가 Responsive Policy와 Responsive Structure를 모두 가진다.
- 표현 불가능한 Node가 Renderability Assessment와 손실 정보를 가진다.
- ScreenSpecification이 Controller·VO·DB·권한을 최종 확정한다.
- 변경 범위와 Dependency Closure를 계산한 후 생성한다.
- 기존 코드와 새 생성 코드를 Ownership과 Base/Current/New로 병합한다.
- 장시간 생성 작업을 Job으로 조회·취소·재시도할 수 있다.
- 생성 결과의 Binding·Build·Render·A11y·Visual·Interaction Evidence를 조회할 수 있다.
- Event, Job, Artifact, Evidence가 서로 다른 책임과 상태를 유지한다.
- AI와 Tool이 사람 승인과 보안 경계를 우회하지 않는다.

---

## 16. 근거·추론·미확정 사항

### 16.1 확인된 근거

- 세 검토 문서는 모두 Figma와 업무 Binding의 책임 분리를 유지한다.
- Locofy 검토는 Design IR, Component Mapping, Generation Scope, Semantic Merge를 구체화한다.
- Anima 검토는 Renderability, Multi-viewport Structure, Generation Job, Evidence를 구체화한다.
- Supernova 검토는 Knowledge Snapshot, Component Health, Token Export, Search/RAG, Event를 구체화한다.
- SpringAI에는 Profile·Registry·Operation·Artifact·Quality Gate·Source Revision 기반이 이미 존재한다.

### 16.2 근거에서 도출한 판단

- 세 검토 결과는 중복 제품 도입안이 아니라 Control Plane, Compilation, Execution·Verification의 상호보완 구조다.
- 통합의 핵심은 기능 수를 늘리는 것이 아니라 Aggregate와 상태 경계를 먼저 확정하는 것이다.
- `DesignSystemKnowledgeSnapshot`, `UiDesignSpec`, `ScreenSpecification`, `GenerationOperation`, `PreviewEvidenceBundle`이 통합 구조의 중심이 돼야 한다.
- Event와 AI는 Preview와 영향 분석을 자동화할 수 있지만 승인·Apply를 대신해서는 안 된다.

### 16.3 현재 확정할 수 없는 사항

- 각 신규 계약을 별도 DB Table로 저장할지 JSON Snapshot 내부에 포함할지
- 장시간 Job을 내부 Executor로 처리할지 외부 Queue를 도입할지
- Multi-brand·Theme가 현재 실제 업무 요구인지
- Private Review Session을 외부에 공개할 수 있는지
- Design System Search/RAG의 문서 작성·게시 책임자가 누구인지
- Token Export Target이 CSS·TypeScript 외 플랫폼까지 필요한지

이 사항들은 구현 범위로 단정하지 않고 사용 사례, Runtime 측정, 운영 책임을 확인한 후 결정한다.

---

## 17. 최종 통합 목표 흐름

```text
PUBLISHED Design System Knowledge Snapshot
  ↓
Design Input Quality Gate
  ↓
Design IR·Component Mapping·Responsive Policy·Renderability
  ↓
UiDesignSpec Preview·Confirm
  ↓
Controller·VO·DB·Permission Binding
  ↓
APPROVED ScreenSpecification
  ↓
DesignChangeIntent·Generation Scope·Ownership
  ↓
Generation Job
  ↓
FreeMarker Thymeleaf 생성·Semantic Merge
  ↓
Binding·Build·Render·A11y·Visual·Interaction 검증
  ↓
Preview Evidence Bundle
  ↓
사람 승인·Apply
  ↓
DesignOps Event·Component Health·문서·Search Index 갱신
```

이 구조에서는 어느 제품도 SpringAI 생성 파이프라인에 직접 도입할 필요가 없다. 각 검토에서 확인한 운영 원리를 SpringAI의 승인, 보안, 버전, Evidence 중심 아키텍처에 맞춰 선택적으로 흡수한다.

---

## 참고 문서

- [`Anima_Based_SpringAI_Pipeline_Evolution_Review.md`](./Anima_Based_SpringAI_Pipeline_Evolution_Review.md)
- [`Locofy_Flow_Based_SpringAI_Pipeline_Evolution_Review.md`](./Locofy_Flow_Based_SpringAI_Pipeline_Evolution_Review.md)
- [`Supernova_Based_SpringAI_DesignOps_Evolution_Review.md`](./Supernova_Based_SpringAI_DesignOps_Evolution_Review.md)
- [`11_Semantic_Figma_Design_System_Implementation_Plan.md`](./11_Semantic_Figma_Design_System_Implementation_Plan.md)
- [Anima 공식 사이트](https://www.animaapp.com/)
- [Locofy 공식 사이트](https://www.locofy.ai/)
- [Supernova 공식 사이트](https://www.supernova.io/)
