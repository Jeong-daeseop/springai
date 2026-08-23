# 5개 공통 축 벤치마크 기반 SpringAI 파이프라인 발전 구현명세서

> 문서 버전: 1.0  
> 작성일: 2026-08-23  
> 기준 문서: [Anima_Locofy_Supernova_5Axis_SpringAI_Benchmark_Review.md](./Anima_Locofy_Supernova_5Axis_SpringAI_Benchmark_Review.md)  
> 대응 구현목록: [30_5Axis_Benchmark_Based_Pipeline_Evolution_Implementation_List.md](./30_5Axis_Benchmark_Based_Pipeline_Evolution_Implementation_List.md)  
> 상태: 제안 계약 및 단계별 구현 범위 정의

---

## 1. 목적

이 문서는 Anima·Locofy·Supernova를 디자인 시스템, 시각 해석, 프런트엔드 코드,
Prototype, Handoff의 동일한 5개 축으로 벤치마크한 결과를 SpringAI의 실제 구현 계약으로
전환한다.

목표는 세 제품의 기능을 복제하는 것이 아니다. SpringAI가 이미 채택한 다음 책임 경계를
유지하면서 부족한 중간 계약, 검증 증거, 인수인계 단위를 보강하는 것이다.

```text
Figma/디자인
→ 시각 구조와 디자인 토큰
→ UiDesignSpec
→ DB·Controller·VO·권한 결합
→ APPROVED ScreenSpecification
→ FreeMarker 기반 Thymeleaf 생성
→ Binding·Build·Render 검증
```

본 명세에서 추가하는 계약은 위 순서를 교체하지 않는다. 각 단계의 입력과 출력에 버전,
근거, 범위, 검증 결과를 부여한다.

## 2. 핵심 설계 원칙

### 2.1 책임 경계

| 책임 | 유일한 기준 | 금지 사항 |
|---|---|---|
| 시각 구조·Layout·Component 후보 | Figma와 `UiDesignSpec` | Figma Layer 이름으로 VO 필드 확정 |
| Token·Component 사용 가능성 | Published Design System Snapshot | Draft·Blocked Component 자동 사용 |
| 업무 Field·Route·Validation | DB·Controller·VO | 시각 추론으로 업무 계약 덮어쓰기 |
| 시각·업무 결합 승인 | `APPROVED ScreenSpecification` | Prototype 성공을 승인으로 간주 |
| 코드 생성 | 승인 계약을 소비하는 FreeMarker Renderer | 승인되지 않은 명세로 파일 저장 |
| 운영 가능 판정 | Binding·Security·Build·Render Gate | Screenshot 유사도만으로 완료 판정 |

### 2.2 기존 파이프라인 불변 조건

1. `ScreenSpecification` 승인 Gate를 제거하거나 우회하지 않는다.
2. `screenSpecificationId`가 있으면 `designReferenceId`보다 우선한다.
3. 생성 진입 시 최신 DB Schema로 승인 상태와 데이터 출처를 재검증한다.
4. Thymeleaf 공통 Layout은 프로젝트 단위로 생성하고 화면 생성은 기본적으로 재사용한다.
5. `auto` 경로는 FreeMarker 기반 결정론적 생성과 승인된 쓰기 Port를 사용한다.
6. Preview·Approve·Apply는 서로 다른 상태로 유지한다.
7. Prototype과 Handoff는 운영 자동 배포 권한을 갖지 않는다.

### 2.3 신규 계약의 역할

```text
DesignSystemKnowledgeSnapshot
        ↓
UiDesignSpec v2
        ↓
APPROVED ScreenSpecification
        ↓
RendererProfile + GenerationScopeManifest
        ↓
FreeMarker Thymeleaf 생성
        ↓
PreviewEvidenceBundle
        ↓
ScreenReviewSession
        ↓
ScreenHandoffBundle
```

## 3. 범위

### 3.1 포함 범위

- `UiDesignSpec v2` 및 v1 호환 변환
- 시각 추론의 Node·Evidence·Confidence·Fallback 보존
- Figma Component와 Thymeleaf Fragment Parameter의 승인 Mapping
- Responsive Policy와 실제 Viewport Structure의 분리
- Renderer Capability와 금지 Fallback 계약
- 화면·Section·Fragment 생성 범위와 Dependency Closure
- 생성 영역·업무 Binding 영역·보호 영역 Ownership
- 동일 Revision에 묶인 Preview·Binding·Build·Render Evidence
- 사람과 Agent가 함께 소비하는 Review·Handoff 계약
- 디자인 시스템 Version·Health·문서 검색용 Snapshot
- 장시간 검증을 위한 비동기 Generation Job
- 단계별 관찰 모드, 이중 읽기, fail-closed 전환

### 3.2 제외 범위

- React·Vue·Angular 범용 코드 생성
- Anima·Locofy·Supernova UI 또는 SaaS 기능 복제
- Figma Layer 이름을 통한 업무 Binding 자동 확정
- 실제 운영 DB Data를 Prototype에 복제
- Review URL을 통한 운영 배포
- 승인 없이 발생하는 자동 Commit·Apply·배포
- 모든 기존 생성 파일의 일괄 재작성
- 초기 단계에서 TypeScript·iOS·Android Token Export 지원

## 4. 목표 아키텍처

### 4.1 논리 흐름

```text
[Design System Plane — 디자인 시스템 운영 계층]
Profile + Registry + Token + Mapping + Asset + Documentation
                         ↓ approved snapshot
[Design Interpretation Plane — 디자인 해석 계층]
Figma/Image → UiDesignSpec v2 → RenderabilityAssessment
                         ↓
[Business Contract Plane — 업무 계약 결합·승인 계층]
DB Schema + Controller/VO + Permission
                         ↓
             ScreenSpecification
                         ↓ APPROVED
[Generation Plane — 코드 생성·변경 적용 계층]
RendererProfile + Scope + Ownership
                         ↓
CrudModelFactory → FreeMarker → ProjectChangeSet
                         ↓ preview/apply
[Evidence Plane — 실행 검증·증거·인수인계 계층]
Binding + Build + Render + A11y + Visual + Interaction
                         ↓
PreviewEvidenceBundle → ReviewSession → HandoffBundle
```

각 Plane의 한국어 의미는 다음과 같다.

| Plane | 한국어 명칭 | 담당 역할 |
|---|---|---|
| Design System Plane | 디자인 시스템 운영 계층 | 사용할 수 있는 Profile·Component·Token·Mapping·Asset·문서의 승인 버전과 상태를 관리한다. |
| Design Interpretation Plane | 디자인 해석 계층 | Figma·이미지의 시각 구조를 `UiDesignSpec v2`로 해석하고 각 요소의 실제 생성 가능성과 손실 여부를 판정한다. |
| Business Contract Plane | 업무 계약 결합·승인 계층 | 시각 해석 결과를 DB Schema·Controller·VO·권한과 결합하고, 코드 생성의 기준인 `APPROVED ScreenSpecification`을 확정한다. |
| Generation Plane | 코드 생성·변경 적용 계층 | 승인된 화면 계약을 Renderer·생성 범위·Ownership 규칙에 따라 FreeMarker 기반 코드와 적용 전 변경 계획으로 변환한다. |
| Evidence Plane | 실행 검증·증거·인수인계 계층 | 생성 결과의 Binding·Build·Render·접근성·시각·Interaction을 검증하고, 결과를 Review와 Handoff에 사용할 하나의 증거 묶음으로 만든다. |

### 4.2 저장 단위

신규 계약은 가능한 한 기존 Artifact 저장소와 불변 Version 패턴을 재사용한다. DB 테이블을
계약별로 즉시 분리하기보다 다음 공통 Envelope를 우선 도입한다.

```json
{
  "artifactType": "UI_DESIGN_SPEC_V2",
  "artifactId": "...",
  "schemaVersion": "2.0",
  "subjectId": "screen-or-operation-id",
  "sourceRevision": "...",
  "contentHash": "sha256:...",
  "status": "DRAFT|REVIEW_REQUIRED|APPROVED|SUPERSEDED",
  "createdBy": "actor",
  "createdAt": "timestamp",
  "payload": {}
}
```

Artifact 간 연결은 ID만 저장하지 않고 예상 `schemaVersion`과 `contentHash`를 함께 기록한다.
Apply 시점에는 ID·Version·Hash가 모두 일치해야 한다.

## 5. 디자인 시스템 운영 계약

### 5.1 `DesignSystemKnowledgeSnapshot`

화면 생성에 사용한 디자인 시스템 전체 상태를 하나의 승인 Snapshot으로 고정한다.

```text
DesignSystemKnowledgeSnapshot
├─ snapshotId
├─ schemaVersion
├─ profileId/profileVersion/profileHash
├─ catalogVersion/catalogHash
├─ registryVersion/registryHash
├─ tokenVersion/tokenHash
├─ componentMappingVersion/componentMappingHash
├─ assetManifestVersion/assetManifestHash
├─ documentationVersion/documentationHash
├─ sourceRevision
├─ publishStatus
├─ approvedBy/approvedAt
└─ contentHash
```

#### 규칙

1. Apply와 Handoff는 `APPROVED` 또는 `PUBLISHED` Snapshot만 사용한다.
2. 구성 요소 중 하나라도 Hash가 달라지면 새 Snapshot을 생성한다.
3. 과거 Snapshot은 제자리 수정하지 않는다.
4. Runtime은 문서 검색 결과가 아니라 Snapshot의 구조화 계약을 사용한다.
5. Profile·Catalog·Registry의 기존 버전 검증을 중복 구현하지 않고 조합한다.

### 5.2 `DesignCodeComponentMapping`

Figma Component의 시각 Property를 Thymeleaf Fragment의 Parameter·Slot으로 연결한다.

```text
DesignCodeComponentMapping
├─ mappingId/version/status/contentHash
├─ logicalType
├─ figmaComponentSetKey
├─ thymeleafFragment
├─ propertyMappings[]
│  ├─ figmaProperty
│  ├─ fragmentParameter
│  ├─ valueMapping
│  ├─ required
│  └─ defaultValue
├─ slotMappings[]
├─ fixtureModel
├─ supportedRendererProfiles[]
├─ sourceRevision
└─ approvedBy/approvedAt
```

#### 검증

- `logicalType`이 Catalog에 존재해야 한다.
- Figma Key가 승인 Registry Binding과 일치해야 한다.
- Fragment 파일과 Parameter 계약이 실제 저장소에 존재해야 한다.
- 필수 Property가 빠지면 Apply를 차단한다.
- 지원하지 않는 Variant는 명시된 Fallback이 없으면 실패한다.

### 5.3 `ComponentHealthReport`

Health는 단일 점수가 아니라 근거가 있는 상태 집합으로 관리한다.

| 항목 | 예시 |
|---|---|
| Registry | Published Key 존재, Lifecycle 상태 |
| Mapping | Fragment·Property Mapping 완전성 |
| Token | 참조 Variable과 Alias 해석 성공 |
| Runtime | Fixture Render 성공 |
| A11y | axe 위반 수와 차단 등급 |
| Usage | 사용 Screen 수와 영향 범위 |
| Drift | Figma·Registry·Code 간 차이 |

`BLOCKED` 또는 필수 Mapping 미완료 Component는 신규 생성에 사용할 수 없다.

### 5.4 Token·Asset Export

초기 Target은 CSS와 Thymeleaf 정적 자산으로 제한한다.

```text
TokenExportManifest
├─ snapshotId
├─ targets[]: CSS_CUSTOM_PROPERTIES
├─ generatedFiles[]
├─ sourceTokenHash
├─ outputHashes[]
└─ validationResult
```

동일 입력은 동일한 파일 내용과 Hash를 생성해야 한다.

## 6. 시각 해석 계약

### 6.1 `UiDesignSpec v2`

v2는 단순 결과 목록이 아니라 추론 근거가 보존된 Design IR이다.

```text
UiDesignSpecV2
├─ specId/schemaVersion/contentHash
├─ source
│  ├─ sourceType: FIGMA|IMAGE|PDF|WEB_CAPTURE
│  ├─ fileKey/nodeId/sourceRevision
│  └─ viewport
├─ designSystemSnapshotRef
├─ nodes[]
│  ├─ semanticId
│  ├─ sourceNodeRefs[]
│  ├─ role/logicalType
│  ├─ geometry
│  ├─ layoutConstraints
│  ├─ componentRef
│  ├─ tokenBindings[]
│  ├─ interactionCandidates[]
│  └─ evidenceRefs[]
├─ responsivePolicySet
├─ responsiveStructureSet
├─ renderabilityAssessments[]
├─ issues[]
└─ confidenceSummary
```

#### 추론 필드 공통 형식

```text
InferredValue<T>
├─ value
├─ confidence: 0.0..1.0
├─ evidenceRefs[]
├─ inferenceMethod
├─ alternatives[]
└─ requiresReview
```

업무 Field 이름, Route, Permission은 `UiDesignSpec v2`에 확정값으로 저장하지 않는다.
화면에서 관찰된 label·control·action 후보만 보존한다.

### 6.2 Responsive 계약

```text
ResponsivePolicySet
├─ breakpoints[]
├─ rules[]
│  ├─ semanticId
│  ├─ behavior: REFLOW|WRAP|RESIZE|HIDE|SWAP|FIXED
│  └─ evidenceRefs[]
└─ confidence

ResponsiveStructureSet
├─ viewportId
├─ visibleSemanticIds[]
├─ order[]
├─ alternateStructures[]
└─ sourceEvidence
```

Policy는 일반화된 동작이고 Structure는 특정 Viewport에서 관찰된 실제 결과다. 두 정보를
서로 덮어쓰지 않는다.

### 6.3 `RenderabilityAssessment`

모든 주요 Node는 생성 가능성을 명시한다.

| 판정 | 의미 | Apply 정책 |
|---|---|---|
| `NATIVE` | 승인 Component/HTML로 손실 없이 생성 | 허용 |
| `COMPOSED` | 승인 Component 조합으로 생성 | 허용 |
| `APPROXIMATED` | 명시적 근사와 손실 존재 | 사람 승인 필요 |
| `RASTERIZED` | 이미지 대체 | Form·Table·Text에는 금지 |
| `UNSUPPORTED` | 생성 불가 | 차단 |

### 6.4 v1 호환

`UiDesignSpec v1`은 즉시 제거하지 않는다.

1. v1 Reader를 유지한다.
2. 결정 가능한 필드는 v2로 변환한다.
3. 원본 Node·Evidence가 없는 값은 `legacyUnknown`으로 표시한다.
4. 낮은 Confidence 또는 근거 누락은 자동 승인하지 않는다.
5. 신규 쓰기는 전환 Gate 이후 v2만 허용한다.

## 7. ScreenSpecification 결합 규칙

`UiDesignSpec v2` 도입 후에도 업무 결합의 SSOT는 `ScreenSpecification`이다.

### 7.1 결합 우선순위

```text
DB Schema·Controller·VO의 실제 계약
  > 사용자가 승인한 명시적 Mapping
  > UiDesignSpec의 시각 후보
  > 일반 CRUD 기본값
```

### 7.2 승인 차단 조건

- DB에 없는 COLUMN을 업무 Field로 확정
- JOIN 출처가 명시되지 않음
- Controller Route와 Action 불일치
- 권한이 필요한 Action에 Permission 계약 없음
- 낮은 Confidence의 시각 추론이 필수 Layout을 결정
- `APPROXIMATED` 또는 `RASTERIZED` 결과가 승인되지 않음
- 참조한 Design System Snapshot이 미승인 또는 변경됨

### 7.3 승인 후 재검증

코드 생성 진입 시 다음을 다시 검사한다.

- `ScreenSpecification.status == APPROVED`
- 요청 Database·Table과 명세의 Primary Source 일치
- 최신 DB Schema와 Column Mapping 일치
- Design System Snapshot ID·Version·Hash 일치
- Renderer Profile이 요구 Component·Feature를 지원

## 8. 프런트엔드 생성 계약

### 8.1 `RendererProfile`

```text
RendererProfile
├─ profileId/version/contentHash/status
├─ rendererType: THYMELEAF
├─ templateEngine: FREEMARKER
├─ templateSetVersion/templateSetHash
├─ componentMappingVersion
├─ supportedFeatures[]
├─ forbiddenFallbacks[]
├─ outputConventionVersion
├─ validatorProfile
└─ supportedViewTypes[]
```

현재 Target은 Thymeleaf로 고정한다. 향후 Renderer가 추가되더라도 같은 Profile Interface를
사용하되 기존 FreeMarker 경로의 동작을 변경하지 않는다.

### 8.2 `GenerationScopeManifest`

```text
GenerationScopeManifest
├─ manifestId/contentHash
├─ rootArtifacts[]
├─ dependencyArtifacts[]
├─ validationOnlyArtifacts[]
├─ preservedArtifacts[]
├─ affectedScreens[]
├─ selectionReason
└─ unresolvedDependencies[]
```

#### Dependency Closure

화면 또는 Fragment 단위 생성 요청 시 다음 의존성을 계산한다.

1. 대상 Screen
2. 직접 참조 Fragment
3. Fragment가 참조하는 하위 Fragment
4. Token·Asset Manifest
5. Binding에 필요한 Controller·VO·Mapper
6. 검증에 필요하지만 변경하지 않을 파일

의존성이 누락되거나 순환하면 Apply를 차단한다.

### 8.3 `GenerationOwnershipManifest`

```text
GenerationOwnershipManifest
├─ artifactPath
├─ generatedRegions[]
├─ bindingRegions[]
├─ protectedRegions[]
├─ regionHashes[]
├─ mergePolicy
└─ owner
```

| 영역 | 기본 정책 |
|---|---|
| Generated | 동일 계약으로 재생성 가능 |
| Binding | ScreenSpecification 변경을 통해서만 갱신 |
| Protected | 자동 수정 금지 |
| Unknown | 사용자 작성 영역으로 간주하고 보존 |

### 8.4 Semantic Merge

증분 생성은 `Base / Current / New` 세 입력을 사용한다.

```text
Base    = 직전 승인·Apply 산출물
Current = 현재 저장소 파일
New     = 새 계약으로 생성한 결과
```

- Base와 Current가 같으면 New 적용 가능
- Current만 변경됐고 Generated Region 밖이면 보존
- 같은 Region을 Current와 New가 모두 변경하면 Conflict
- Binding·Protected Region Conflict는 자동 병합 금지
- Conflict가 있으면 Preview는 가능하지만 Apply는 차단

### 8.5 생성 결과

기존 `ProjectChangeSet`과 승인된 Write Port를 재사용한다. 생성 결과에는 다음 Metadata를
추가한다.

- 사용한 `screenSpecificationId/version/hash`
- `designSystemSnapshotId/hash`
- `rendererProfileId/version/hash`
- `generationScopeManifestId/hash`
- `ownershipManifestId/hash`
- 생성·보존·충돌 파일 목록

## 9. Prototype·Evidence 계약

### 9.1 `PreviewEvidenceBundle`

동일 Revision과 Fixture로 생성한 증거만 하나의 Bundle로 묶는다.

```text
PreviewEvidenceBundle
├─ bundleId/schemaVersion/contentHash/status
├─ operationId/sourceRevision
├─ designSystemSnapshotRef
├─ uiDesignSpecRef
├─ screenSpecificationRef
├─ rendererProfileRef
├─ fixtureModelHash
├─ artifacts
│  ├─ figmaBundle
│  ├─ generatedThymeleaf
│  ├─ domSnapshot
│  └─ screenshots[]
├─ reports
│  ├─ binding
│  ├─ security
│  ├─ build
│  ├─ render
│  ├─ accessibility
│  ├─ visualDiff
│  └─ interactionFlow
├─ fallbackAssessments[]
├─ warnings[]
└─ finalDecision
```

### 9.2 정합성 규칙

1. 모든 Artifact와 Report는 동일 `sourceRevision`을 가져야 한다.
2. Screenshot은 Viewport, Browser, Device Scale, Locale을 기록한다.
3. Fixture Model의 내용 Hash를 기록하고 실제 운영 Data를 포함하지 않는다.
4. 필수 Report가 없으면 `COMPLETE`가 될 수 없다.
5. Build 성공만으로 Render 성공을 대신하지 않는다.
6. Visual Diff 성공만으로 Binding·A11y 성공을 대신하지 않는다.

### 9.3 `InteractionFlowEvidence`

초기 지원 범위는 서버 Thymeleaf CRUD의 핵심 Flow로 제한한다.

```text
목록 조회 → 검색 → 상세 → 등록 → 수정 → 취소/목록 복귀
```

각 Step은 Route, 사용자 Action, 예상 DOM 상태, Screenshot, 결과 상태를 기록한다. 실제 데이터
변경은 격리된 Fixture 또는 Test Transaction만 사용한다.

### 9.4 `ScreenReviewSession`

```text
ScreenReviewSession
├─ sessionId
├─ evidenceBundleId/contentHash
├─ reviewerRoles[]
├─ comments[]
├─ requestedChanges[]
├─ approvalDecision
├─ visibility: PRIVATE
├─ expiresAt
└─ auditTrail[]
```

#### 보안 규칙

- 기본 Visibility는 `PRIVATE`
- 만료 시간 필수
- 조회·Comment·승인 권한 분리
- Session Cookie·API Key·운영 Token 저장 금지
- Evidence Bundle 변경 시 기존 승인은 무효
- Review 승인과 운영 Apply 권한 분리

## 10. Handoff 계약

### 10.1 `ScreenHandoffBundle`

Handoff는 새 생성 단계가 아니라 검증이 끝난 결과를 전달하는 인수인계 Package다.

```text
ScreenHandoffBundle
├─ handoffId/schemaVersion/contentHash
├─ operationId/sourceRevision
├─ designSystemSnapshotRef
├─ uiDesignSpecRef
├─ screenSpecificationRef
├─ bindingContractRef
├─ rendererProfileRef
├─ generationScopeManifestRef
├─ ownershipManifestRef
├─ changedFiles[]
├─ componentMappings[]
├─ tokenBindings[]
├─ unresolvedIssues[]
├─ previewEvidenceBundleRef
├─ reviewDecision
├─ migrationNotes
├─ rollbackReference
└─ nextAllowedActions[]
```

### 10.2 소비자별 Projection

Bundle 원문은 하나지만 소비자별 View를 제공한다.

| 대상 | 기본 Projection |
|---|---|
| Designer | Layout·Component·Token·Responsive·Visual Diff |
| 업무 담당자 | Field·Action·Route·Permission·미해결 문제 |
| Developer | 변경 파일·Dependency·Ownership·Conflict |
| QA | Fixture·Gate·Interaction Flow·실패 Evidence |
| 승인자 | Apply 범위·잔여 위험·Rollback |
| Agent | 입력 Hash·상태·`nextAllowedActions` |

### 10.3 `nextAllowedActions`

Agent는 Bundle의 상태가 허용한 Tool만 실행할 수 있다.

```text
REQUEST_REVISION
REBUILD_PREVIEW
RUN_VALIDATION
APPROVE_REVIEW
PREVIEW_APPLY
APPLY_CHANGESET
ROLLBACK
```

각 Action은 필요한 역할, 선행 상태, 예상 입력 Hash, 만료 시각을 포함한다. Bundle이
`RUN_VALIDATION`을 허용하더라도 `APPLY_CHANGESET` 권한까지 자동으로 얻지 않는다.

## 11. Generation Job과 Event

### 11.1 `GenerationJob`

Build·Browser·Screenshot처럼 MCP 요청 시간을 초과할 수 있는 작업은 비동기 Job으로 실행한다.

```text
GenerationJob
├─ jobId/type/status
├─ operationId
├─ requestedBy/requestedAt
├─ inputRefs[]/inputHashes[]
├─ progress/currentStage
├─ attempt/maxAttempts
├─ resultArtifactRefs[]
├─ errorCode/errorMessage
├─ cancellable
└─ expiresAt
```

상태는 `QUEUED → RUNNING → SUCCEEDED|FAILED|CANCELLED`로 제한한다. 재시도는 같은 입력
Hash에서만 허용하고 결과 Artifact는 멱등 저장한다.

### 11.2 `DesignSystemEvent`

Design System 변경 Event는 Preview와 영향 분석을 시작할 수 있지만 Apply를 수행하지 않는다.

```text
SNAPSHOT_PUBLISHED
COMPONENT_DEPRECATED
COMPONENT_BLOCKED
TOKEN_CHANGED
MAPPING_CHANGED
```

Event 소비 결과는 영향받는 Screen 목록과 재검증 필요 여부를 기록한다.

## 12. API·MCP Tool 설계

정확한 Tool 이름은 구현 시 기존 Catalog 충돌 검사를 통과해야 하며 다음 책임으로 구분한다.

| 책임 | 제안 Tool/Endpoint |
|---|---|
| Design IR | `getUiDesignSpecV2`, `compareUiDesignSpecVersions` |
| Mapping | `previewDesignCodeComponentMapping`, `approveDesignCodeComponentMapping` |
| Snapshot | `getDesignSystemKnowledgeSnapshot`, `publishDesignSystemKnowledgeSnapshot` |
| Scope | `previewGenerationScope`, `getGenerationOwnershipManifest` |
| Evidence | `buildPreviewEvidenceBundle`, `getPreviewEvidenceBundle` |
| Review | `createScreenReviewSession`, `commentScreenReview`, `decideScreenReview` |
| Handoff | `createScreenHandoffBundle`, `getScreenHandoffBundle` |
| Job | `getGenerationJob`, `cancelGenerationJob`, `retryGenerationJob` |

### 12.1 위험 등급

- 조회·Diff·Preview: `READ_ONLY` 또는 기존 동급
- 외부 Figma 조회·Browser 실행: `EXTERNAL`
- 승인·Publish·Review Decision: 승인형 위험 등급
- 파일 Apply·Rollback: 기존 쓰기·파괴적 정책 재사용

Tool 추가 시 `McpConfig` 등록, Tool Snapshot 기준선, 위험 등급 인가 테스트를 함께 갱신한다.

## 13. 오류 코드

| 오류 코드 | 의미 | 처리 |
|---|---|---|
| `DESIGN_EVIDENCE_MISSING` | 시각 추론 근거 누락 | Review 필요 |
| `DESIGN_CONFIDENCE_TOO_LOW` | 필수 추론 Confidence 미달 | 자동 승인 차단 |
| `DESIGN_SYSTEM_SNAPSHOT_STALE` | 구성 Version·Hash 변경 | Preview 재생성 |
| `COMPONENT_MAPPING_MISSING` | 필수 Fragment Mapping 없음 | Apply 차단 |
| `RENDERER_CAPABILITY_UNSUPPORTED` | Renderer가 기능 미지원 | Apply 차단 |
| `GENERATION_DEPENDENCY_UNRESOLVED` | Scope Closure 미완성 | Apply 차단 |
| `OWNERSHIP_CONFLICT` | 보호·Binding 영역 충돌 | 사람 해결 필요 |
| `EVIDENCE_REVISION_MISMATCH` | Bundle 내 Revision 불일치 | Bundle 무효 |
| `REVIEW_EVIDENCE_CHANGED` | Review 중 Evidence Hash 변경 | 승인 무효 |
| `HANDOFF_ACTION_NOT_ALLOWED` | 상태·역할에 없는 후속 Action | 실행 거부 |
| `PROTOTYPE_SECRET_DETECTED` | Fixture·Artifact에 비밀정보 발견 | 저장·공유 차단 |

## 14. 보안·개인정보·감사

1. Fixture 생성 시 Secret Scanner와 개인정보 Field 정책을 적용한다.
2. 실제 Session Cookie, Authorization Header, API Key는 Artifact에 저장하지 않는다.
3. Screenshot과 DOM에 개인정보가 포함되면 Masking하거나 저장을 차단한다.
4. 승인, Apply, Rollback, Handoff 생성은 actor·시각·입력 Hash를 감사 로그에 기록한다.
5. Review Session과 다운로드 URL은 짧은 만료 시간을 사용한다.
6. Handoff Bundle은 실행 권한 자체가 아니라 허용 가능한 다음 Action 설명만 제공한다.
7. Agent가 Tool을 호출할 때 서버 인가를 다시 수행한다.

## 15. 호환성 및 마이그레이션

### 15.1 단계 A — 관찰 모드

- v2 Design IR과 신규 Snapshot을 기존 흐름 옆에서 생성한다.
- 기존 생성 결과를 변경하지 않는다.
- v1/v2 차이와 Evidence 누락을 Report로 수집한다.

### 15.2 단계 B — 이중 읽기

- Preview에서 기존 계약과 신규 계약을 모두 해석한다.
- 모델·파일 목록·Binding 차이를 비교한다.
- Apply는 기존 경로를 유지한다.

### 15.3 단계 C — 신규 Preview 우선

- 신규 Preview와 Evidence Bundle을 기본 검토 화면으로 사용한다.
- Scope·Ownership·Revision 불일치를 fail-closed 한다.
- Apply는 명시적 Feature Flag 아래 신규 Manifest를 소비한다.

### 15.4 단계 D — 신규 Apply 기본

- 신규 쓰기는 v2·Snapshot·Manifest를 필수로 한다.
- Legacy Reader는 Rollback과 과거 Artifact 조회용으로 유지한다.
- 운영 리허설과 Rollback 검증 후 Feature Flag를 기본 활성화한다.

## 16. 테스트 전략

### 16.1 계약 테스트

- 모든 신규 JSON Schema 정상·오류 Fixture 검증
- Version·Hash 결정성 및 불변성
- v1→v2 변환과 `legacyUnknown` 보존
- Artifact 참조 ID·Version·Hash 교차 검증
- `nextAllowedActions` 상태·역할 Matrix 검증

### 16.2 단위 테스트

- Component↔Fragment Property Mapping
- Responsive Policy/Structure 분리
- Renderability 금지 Fallback
- Dependency Closure와 순환 검출
- Ownership Region Hash와 3-way Conflict
- Evidence Revision 일치
- Review 승인 무효화
- Job 멱등 재시도·취소

### 16.3 통합 테스트

- `UiDesignSpec v2 → ScreenSpecification → APPROVED` 결합
- 승인 명세의 생성 진입 재검증
- Renderer Profile과 `CrudModelFactory` 결합
- FreeMarker 생성 파일과 Scope Manifest 일치
- Binding·Build·Render Report의 Bundle 결합
- Handoff Projection과 후속 Tool 인가

### 16.4 Browser·Figma Desktop E2E

- KRDS Q&A 기준 화면 Desktop·Tablet·Mobile Evidence 생성
- 목록→검색→상세→등록·수정 Flow
- Figma Revision 변경 후 기존 Review 승인 무효화
- Component Mapping 누락 시 Preview Issue와 Apply 차단
- Generated Region 외 사용자 수정 보존
- 같은 Region 충돌 시 Atomic Apply 차단
- 이전 Handoff의 Rollback Reference로 복구

## 17. 관측성과 운영 지표

초기에는 제품 홍보식 생산성 지표를 사용하지 않는다. 다음 운영 지표를 수집한다.

| 지표 | 목적 |
|---|---|
| `design_inference_review_rate` | 낮은 Confidence와 표현력 부족 파악 |
| `component_mapping_coverage` | Fragment Mapping 완전성 |
| `preview_bundle_completion_rate` | Evidence 수집 안정성 |
| `generation_conflict_rate` | Ownership·Merge 정책 보정 |
| `binding_gate_failure_rate` | 업무 계약 품질 |
| `render_gate_failure_rate` | Runtime 생성 품질 |
| `review_to_apply_lead_time` | 검토 병목 파악 |
| `rollback_success_rate` | Handoff 복구 가능성 |

Metric에는 실제 업무 데이터나 Prompt 본문을 넣지 않는다.

## 18. 단계별 구현 순서

### R0 — 기준선과 계약

- 기존 Artifact·Operation·Registry·ScreenSpecification 재사용 경계 확정
- 신규 Schema와 공통 Artifact Reference 정의
- Feature Flag와 오류 코드 정의

### R1 — Design IR·Mapping

- `UiDesignSpec v2`
- v1 Adapter
- Responsive·Renderability
- `DesignCodeComponentMapping`

### R2 — Renderer·Scope·Ownership

- `RendererProfile`
- `GenerationScopeManifest`
- Dependency Closure
- `GenerationOwnershipManifest`
- Preview-only Semantic Merge

### R3 — Evidence·Review

- `PreviewEvidenceBundle`
- Interaction Flow
- Private `ScreenReviewSession`
- Revision·Hash Gate

### R4 — Design System 운영

- `DesignSystemKnowledgeSnapshot`
- Component Health
- CSS Token Export
- 승인 Snapshot 전용 Knowledge Index

### R5 — Handoff·Automation

- `ScreenHandoffBundle`
- 소비자별 Projection
- `nextAllowedActions`
- `GenerationJob`
- Design System Event 영향 분석

## 19. 완료 판정 기준

다음 조건을 모두 만족해야 본 명세의 구현을 완료로 판정한다.

1. 모든 시각 추론이 원본 Node 또는 명시적인 Legacy 누락 상태를 가진다.
2. Figma Component와 Thymeleaf Fragment Parameter Mapping이 승인·버전화된다.
3. `APPROVED ScreenSpecification`만 생성 입력으로 사용된다.
4. 생성 범위와 Dependency Closure가 실제 변경 파일과 일치한다.
5. 보호·Binding 영역 Conflict가 승인 없이 덮어써지지 않는다.
6. Desktop·Tablet·Mobile Evidence와 Binding·Build·Render 결과가 같은 Revision Bundle에 있다.
7. Review 승인 후 Evidence 변경 시 승인이 자동 무효화된다.
8. Handoff Bundle로 승인 계약, Diff, Evidence, 잔여 Issue, Rollback을 재현할 수 있다.
9. Agent는 `nextAllowedActions` 밖의 Tool을 실행할 수 없다.
10. 기존 v1 Artifact와 운영 Snapshot을 읽고 Rollback할 수 있다.
11. KRDS 기준 화면 E2E와 전체 Gradle 테스트가 통과한다.
12. 신규 계약을 비활성화했을 때 기존 생성 경로가 동일하게 동작한다.

## 20. 최종 목표 상태

```text
디자인 시스템
= 어떤 버전과 Component를 사용할 수 있는지 통제

시각 해석
= 원본 근거와 손실을 보존한 Design IR 생성

프런트엔드 코드
= 승인된 업무 계약으로 결정론적 Thymeleaf 생성

Prototype
= 같은 Revision의 실행 화면과 검증 Evidence 제공

Handoff
= 사람과 Agent가 같은 승인 계약·Diff·Evidence로 후속 작업
```

이 목표 상태에서도 SpringAI의 핵심 파이프라인은 바뀌지 않는다. 신규 계약은 기존 단계의
판단 근거, 재현성, 변경 안전성, 인수인계 품질을 강화한다.
