# Supernova 분석 기반 SpringAI DesignOps 발전 검토

> 작성일: 2026-08-23  
> 검토 목적: Supernova 제품 도입 여부가 아니라, Supernova의 Documentation, Design System Management, DesignOps, Ask AI, Code Automation 흐름에서 확인한 운영 원리를 SpringAI 자체 생성 파이프라인에 추가 반영할 수 있는지 검토한다.  
> 기준 자료: [Documentation](https://www.supernova.io/documentation), [Design System Management](https://www.supernova.io/design-system-management), [DesignOps](https://www.supernova.io/designops), [Ask AI and Browse](https://www.supernova.io/ask-ai-browse-your-design-system), [Code Automation](https://www.supernova.io/code-automation)

---

## 1. 검토 결론

Supernova 분석에서 SpringAI에 추가 반영할 가치가 있는 부분은 Figma를 코드로 직접 변환하는 기능이 아니라, 디자인 시스템의 문서·토큰·컴포넌트·에셋·코드·변경 이력을 하나의 버전과 운영 흐름으로 연결하는 **Design System Control Plane**이다.

SpringAI는 이미 다음 기반을 갖추고 있다.

- 버전이 지정된 `DesignSystemProfile`과 Component Registry
- 컴포넌트 게시 상태, 수명주기, 대체 컴포넌트 관리
- Figma 인벤토리와 Registry 간 Drift 검증
- Preview → 사람 승인 → Apply
- Source Revision 충돌 감지와 멱등 처리
- `UiDesignSpec → ScreenSpecification → FreeMarker → 검증` 단계 분리

따라서 기존 기능을 다시 만드는 것보다 다음 운영 계층을 연결하는 방향이 적합하다.

1. 디자인 시스템 전체를 하나의 버전으로 묶는 Knowledge Snapshot
2. 기존 검증 결과를 컴포넌트 단위로 종합하는 Health 모델
3. 토큰과 에셋의 대상별 Code Export Pipeline
4. 승인된 디자인 시스템만 근거로 답변하는 검색/RAG
5. 변경을 Preview와 검증으로 연결하는 Event-driven DesignOps
6. Base·Brand·Theme·Application 간 상속 구조

Supernova의 기능을 그대로 복제하거나 외부 제품을 생성 파이프라인에 결합하는 것은 본 제안의 범위가 아니다.

---

## 2. 검토 범위와 판단 기준

### 2.1 검토한 Supernova 영역

| 영역 | 확인한 운영 원리 | SpringAI 관점의 의미 |
|---|---|---|
| Documentation | 토큰·컴포넌트·에셋·코드·가이드·릴리스 노트의 통합 문서화와 버전 관리 | 디자인 계약과 구현 계약을 동일한 게시 버전으로 연결 |
| Design System Management | 토큰 Alias, Metadata, Component Status, Code Link, Asset SSOT | Registry와 Token 정보를 운영 가능한 관리 모델로 확장 |
| DesignOps | 변경 Trigger, 외부 저장소·REST 전달, 다중 브랜드·테마, 권한 관리 | 변경 이벤트를 Preview·승인·Export·감사 로그로 연결 |
| Ask AI and Browse | 토큰·컴포넌트·에셋·가이드의 통합 검색과 근거 기반 답변 | APPROVED/PUBLISHED Snapshot 전용 RAG 구축 |
| Code Automation | 디자인 토큰과 에셋의 다중 대상 코드 생성 및 전달 | CSS·TypeScript 등 대상별 Export 계약과 Manifest 도입 |

### 2.2 판단 기준

추가 반영 항목은 다음 원칙을 만족해야 한다.

- Figma는 시각 구조·레이아웃·컴포넌트 선택만 통제한다.
- 업무 Binding의 기준은 Controller·VO·DB Schema·ScreenSpecification이다.
- AI의 설명과 추천은 승인 또는 게시 권한을 대신하지 않는다.
- 변경 감지는 자동화할 수 있지만 Apply와 배포 승인은 우회하지 않는다.
- 생성 결과는 버전, 근거, 상태, 검증 결과를 추적할 수 있어야 한다.
- 제품 종속적인 기능보다 SpringAI 내부 계약과 교환 가능한 Adapter를 우선한다.

---

## 3. 현재 목표 흐름과 확장 위치

### 3.1 현재 목표 흐름

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

이 책임 경계는 유지한다.

### 3.2 발전된 운영 구조

```text
Figma · Token · Registry · 문서 · Asset · Code Metadata
                          │
                          ▼
          Versioned Design System Knowledge Snapshot
                          │
             ┌────────────┼────────────┐
             ▼            ▼            ▼
       문서·검색·AI   Health·Governance   Export Pipeline
         읽기 전용       Drift·승인       CSS·TS·Asset
             │            │            │
             └────────────┼────────────┘
                          ▼
                 UiDesignSpec Preview
                          ↓
          DB·Controller·VO·권한 결합
                          ↓
          APPROVED ScreenSpecification
                          ↓
             FreeMarker Thymeleaf 생성
                          ↓
             Binding·Build·Render 검증
                          │
                          └── 결과 이벤트·Health 갱신
```

Knowledge Snapshot은 기존 생성 흐름을 대체하지 않는다. 생성에 사용한 디자인 시스템의 상태와 근거를 고정하고, 문서·검색·Export·검증이 같은 버전을 바라보게 하는 상위 운영 계약이다.

---

## 4. 현재 구현 근거

### 4.1 이미 구현된 기반

| 현재 기반 | 코드 근거 | 판단 |
|---|---|---|
| Profile·Registry 버전 및 상태 | `DesignSystemProfile` | Snapshot의 디자인 기준 버전으로 재사용 가능 |
| Component 게시·수명주기·대체 관계 | `ComponentRegistryEntry` | Component Health의 핵심 입력으로 재사용 가능 |
| Variable Collection과 Mode | `DesignSystemSpec` | Theme·Mode 및 Token Export의 기반 |
| Registry Preview·Apply·Rollback | `DesignSystemQueryService` | 운영 API의 기존 승인 경계 유지 가능 |
| Registry diff·인벤토리·접근성 상태 검증 | `ComponentRegistrySyncService` | Health 산정과 생성 차단 근거로 재사용 가능 |
| Source Revision 충돌·멱등 처리 | `FigmaDesignOperationRepository` | Event와 Export 작업의 중복·충돌 통제에 확장 가능 |

주요 코드 위치는 다음과 같다.

- `src/main/java/com/krdevops/springai/model/designsystem/ComponentRegistryEntry.java`
- `src/main/java/com/krdevops/springai/model/designsystem/DesignSystemProfile.java`
- `src/main/java/com/krdevops/springai/model/designsystem/DesignSystemSpec.java`
- `src/main/java/com/krdevops/springai/service/designsystem/DesignSystemQueryService.java`
- `src/main/java/com/krdevops/springai/service/designsystem/ComponentRegistrySyncService.java`
- `src/main/java/com/krdevops/springai/mapper/FigmaDesignOperationRepository.java`

### 4.2 추가로 필요한 연결 계층

현재 구현은 개별 계약과 검증에는 강하지만 다음 연결 정보는 명시적으로 통합돼 있지 않다.

- Profile·Registry·Token·문서·Asset·Code Artifact를 함께 게시하는 상위 버전
- Component별 검증 결과를 종합한 Health 상태
- Token의 대상별 Export 설정과 생성 Manifest
- 디자인 시스템에 한정된 검색 인덱스와 답변 근거
- 변경 발생부터 Preview·승인·Export까지 이어지는 Event 계약
- Base·Brand·Theme·Application 간 명시적인 상속 관계

---

## 5. 우선순위별 발전 항목

| 순위 | 발전 항목 | 기대 효과 | 판단 신뢰도 |
|---:|---|---|---|
| P1 | Design System Knowledge Snapshot | 문서·디자인·코드·산출물의 동일 버전 추적 | 높음 |
| P1 | Component Health·Governance | 기존 검증 결과를 생성 가능 여부로 통합 | 높음 |
| P1 | Token·Asset Export Pipeline | 대상별 산출물의 재현성과 추적성 확보 | 높음 |
| P2 | Design System 전용 Search/RAG | 승인된 계약에 근거한 설명과 탐색 | 높음 |
| P2 | Event-driven DesignOps | 변경 영향 분석과 후속 작업 자동 연결 | 높음 |
| P2 | Multi-brand·Theme 상속 | 공통 기반과 조직별 변형의 경계 명시 | 중간 |
| P3 | 문서·사용 분석 | 검색 실패와 문서 노후화 등 운영 문제 발견 | 중간 |
| P3 | Component Playground | Variant·State와 코드 구현의 사전 확인 | 중간 |

---

## 6. Design System Knowledge Snapshot

### 6.1 목적

Supernova Documentation은 토큰, 컴포넌트, 에셋, 코드 예제, Figma, 릴리스 노트를 한 문서 체계에 연결하고 버전을 관리한다. SpringAI에서는 이를 범용 문서 편집기로 구현하기보다, 생성과 검증의 근거를 고정하는 읽기 모델로 반영하는 편이 적합하다.

### 6.2 권장 계약

```text
DesignSystemKnowledgeSnapshot
├─ snapshotId
├─ designSystemId
├─ profileId
├─ profileVersion
├─ registryVersion
├─ tokenVersion
├─ documentationVersion
├─ assetManifestVersion
├─ codeArtifactVersion
├─ releaseNotes
├─ sourceRevisions
├─ checksums
├─ status: DRAFT | IN_REVIEW | APPROVED | PUBLISHED | RETIRED
├─ approvedBy
├─ approvedAt
└─ publishedAt
```

### 6.3 게시 불변식

- Snapshot에 포함된 모든 참조 버전이 존재해야 한다.
- Registry와 Profile의 호환성이 검증돼야 한다.
- Token Alias가 순환하거나 해석 불가능하면 게시할 수 없다.
- 필수 Component의 문서 또는 코드 연결이 누락되면 경고 또는 차단한다.
- `PUBLISHED` Snapshot은 불변이며 변경 시 새 버전을 생성한다.
- `ScreenSpecification`은 생성에 사용한 `snapshotId`를 기록한다.
- 생성 보고서는 Snapshot과 Renderer 버전을 함께 기록한다.

### 6.4 문서 산출물

초기에는 별도 WYSIWYG 편집기보다 다음 정적 산출물부터 지원하는 것이 적절하다.

- 디자인 시스템 개요
- Token 목록과 Alias 관계
- Component Catalog와 Health
- Lifecycle·Replacement 안내
- 지원 Variant·State·Platform
- Code Component·문서 링크
- 변경 내역과 Migration 안내
- JSON Snapshot과 Markdown Export

---

## 7. Component Health와 Governance

### 7.1 목적

현재 Registry 검증 결과를 개별 Issue로만 소비하지 않고, 컴포넌트별로 생성 가능 상태를 종합한다.

```text
ComponentHealthReport
├─ componentSetKey
├─ registryVersion
├─ snapshotId
├─ publishStatus
├─ lifecycleStatus
├─ inventorySyncStatus
├─ requiredStateCoverage
├─ tokenCompliance
├─ documentationCompleteness
├─ codeLinkStatus
├─ platformCoverage
├─ fixtureVerification
├─ renderVerification
├─ replacementReadiness
├─ issues[]
└─ decision
```

### 7.2 상태 모델

```text
HEALTHY
WARNING
BLOCKED_FOR_GENERATION
DEPRECATED
RETIRED
```

### 7.3 생성 Gate 연계

- `HEALTHY`: 자동 선택 가능
- `WARNING`: Preview에 사유를 노출하고 정책에 따라 진행
- `BLOCKED_FOR_GENERATION`: `UiDesignSpec` 또는 생성 단계 진입 차단
- `DEPRECATED`: 신규 화면 선택 금지, 기존 화면에는 Migration 안내
- `RETIRED`: Snapshot에서 참조 금지

단일 숫자 점수만 제공하면 차단 이유가 가려질 수 있으므로, 상태와 구조화된 Issue를 함께 제공해야 한다.

---

## 8. Token·Asset Code Export Pipeline

### 8.1 목적

Supernova Code Automation에서 참고할 핵심은 Figma Variable을 특정 코드 한 종류로 변환하는 것이 아니라, 동일한 Token Source에서 대상별 산출물을 재현 가능하게 만드는 구조다.

### 8.2 권장 계약

```text
TokenExportProfile
├─ exportProfileId
├─ target: CSS | SCSS | TYPESCRIPT | JSON
├─ themeMode
├─ namingStrategy
├─ aliasResolutionPolicy
├─ outputPath
├─ headerTemplate
├─ includeMetadata
└─ validationPolicy
```

```text
TokenExportManifest
├─ exportId
├─ snapshotId
├─ tokenVersion
├─ exportProfileId
├─ generatedFiles[]
├─ checksums
├─ warnings[]
├─ buildResult
└─ generatedAt
```

Asset도 별도 Manifest로 관리한다.

```text
AssetManifest
├─ assetId
├─ logicalName
├─ sourceNodeKey
├─ sourceRevision
├─ mediaType
├─ variants[]
├─ outputFiles[]
├─ checksums
├─ licenseMetadata
└─ optimizationMetadata
```

### 8.3 실행 흐름

```text
Token·Asset 변경 감지
  ↓
Export Preview
  ↓
Alias·누락·충돌·라이선스 검증
  ↓
사람 승인
  ↓
대상별 산출물 생성
  ↓
Lint·Build 검증
  ↓
Manifest와 감사 로그 저장
```

Trigger는 Preview 생성을 자동화할 수 있지만 Apply, Commit, 배포 승인을 대신하지 않는다.

---

## 9. Design System 전용 Search/RAG

### 9.1 인덱싱 범위

```text
APPROVED 또는 PUBLISHED Knowledge Snapshot
  + DesignSystemProfile
  + Component Registry
  + Token과 Alias
  + Component Documentation
  + Guidelines
  + Release Notes
  + Export Manifest
→ DesignSystemKnowledgeIndex
```

Draft, Rejected, Retired 상태는 기본 검색 대상에서 제외한다. Deprecated 항목은 검색할 수 있지만 상태와 Replacement를 반드시 함께 제시한다.

### 9.2 답변 근거

답변에는 가능한 범위에서 다음 식별자를 포함한다.

- `snapshotId`
- Profile ID와 Version
- Registry Version
- `componentSetKey`
- Token Path
- Documentation Version
- Publish/Lifecycle Status

### 9.3 AI 권한 경계

| 작업 | 허용 여부 |
|---|---|
| Token·Component·Guideline 검색 | 허용 |
| 사용 가능한 Variant와 State 설명 | 허용 |
| 적합한 Component 후보 추천 | 허용 |
| Deprecated 사용 경고와 Replacement 안내 | 허용 |
| 변경 영향도 요약 | 허용 |
| 업무 Binding 최종 확정 | 금지 |
| Registry Apply·승인·게시 | 금지 |
| ScreenSpecification 승인 | 금지 |
| 권한·보안 정책 변경 | 금지 |

Ask AI는 디자인 시스템의 탐색과 설명 계층이며 Controller·VO·ScreenSpecification의 업무 권한을 대체하지 않는다.

---

## 10. Event-driven DesignOps

### 10.1 권장 이벤트

```text
FIGMA_SOURCE_SYNCED
DESIGN_PROFILE_CHANGED
REGISTRY_PREVIEW_CREATED
REGISTRY_APPLIED
TOKEN_CHANGED
COMPONENT_DEPRECATED
DOCUMENTATION_PUBLISHED
EXPORT_PREVIEW_CREATED
EXPORT_SUCCEEDED
EXPORT_FAILED
SCREEN_SPEC_APPROVED
GENERATION_VERIFIED
```

### 10.2 이벤트 공통 Envelope

```text
DesignSystemEvent
├─ eventId
├─ eventType
├─ aggregateType
├─ aggregateId
├─ sourceRevision
├─ requestRevision
├─ correlationId
├─ causationId
├─ actor
├─ occurredAt
└─ payload
```

### 10.3 Event Consumer 책임

- Drift 분석 실행
- 영향받는 Snapshot과 Screen 목록 계산
- 문서 갱신 필요 경고
- Token·Asset Export Preview 생성
- Registry 재검증
- 승인 요청 생성
- 검색 인덱스 갱신
- Health Report 갱신
- 감사 로그 기록

이벤트 처리는 Outbox와 멱등 키를 사용해 중복 실행을 통제하고, Source Revision이 변경되면 기존 Preview를 충돌 상태로 전환한다.

---

## 11. Multi-brand·Theme 상속

### 11.1 권장 계층

```text
Base Design System
  └─ Organization Brand
       └─ Theme / Accessibility Mode
            └─ Application Profile
                 └─ Screen Override
```

### 11.2 권장 메타데이터

```text
baseProfileId
brandId
themeId
tenantId
supportedPlatforms
inheritancePolicy
overrideAllowlist
compatibilityRange
```

### 11.3 상속 규칙

- 하위 Profile은 상위 Token을 참조하거나 허용된 범위에서 Override한다.
- Component Contract와 접근성 필수 상태는 임의로 제거할 수 없다.
- Brand Override와 Application Override를 구분한다.
- Screen 단위 Override는 예외이며 근거와 승인자를 기록한다.
- Snapshot 생성 시 상속 결과를 완전히 해석해 재현 가능한 상태로 저장한다.

현재 Variable Mode와 Profile Override는 이 구조의 기반이 될 수 있지만, 실제 다중 브랜드 요구와 운영 주체가 확인된 이후 구체화하는 것이 적절하다.

---

## 12. 문서·사용 분석과 Component Playground

### 12.1 문서·사용 분석

Knowledge Portal이 실제 운영된 이후 다음 지표를 수집할 수 있다.

- 검색 결과가 없는 질의
- 답변 근거를 찾지 못한 AI 질의
- 오래된 문서와 마지막 검토일
- Deprecated Component의 사용 화면 수
- Replacement Migration 진행률
- Component별 생성·검증 실패 빈도
- 자주 발생하는 Registry Drift 유형

사용자 식별 정보나 원문 질의를 장기간 저장할 경우 보안·개인정보 정책을 별도로 정의해야 한다. 분석은 Portal과 검색이 안정된 이후의 후순위 항목이다.

### 12.2 Component Playground

Registry Entry에 Storybook 또는 내부 Fixture 정보를 연결하면 Variant·State·Responsive 동작을 생성 전에 확인할 수 있다.

```text
ComponentPreviewBinding
├─ componentSetKey
├─ codeComponent
├─ previewProvider
├─ previewUrl
├─ fixtureId
├─ supportedVariants
└─ verifiedRevision
```

SpringAI의 최종 결과는 Thymeleaf이므로 React Storybook에 종속시키기보다, Provider Adapter를 통해 Storybook과 Thymeleaf Fixture를 모두 연결할 수 있어야 한다.

---

## 13. 구현하지 않거나 경계해야 할 항목

### 13.1 Supernova 제품 기능의 직접 복제

범용 디자인 문서 편집기, 완전한 SaaS Portal, 모든 플랫폼용 Exporter를 한 번에 구현하는 것은 현재 생성 파이프라인의 핵심 목표와 거리가 있다.

### 13.2 Figma 또는 AI에 업무 의미 부여

Figma Layer Name이나 AI 추론을 근거로 Controller Binding, 필드 권한, Validation, 업무 Route를 확정해서는 안 된다.

### 13.3 변경 감지 후 자동 Apply

Figma 변경, Token 변경, 문서 변경은 Preview와 영향 분석을 시작할 수 있지만 Registry Apply, Snapshot Publish, 코드 배포를 자동 승인해서는 안 된다.

### 13.4 문서 최신성을 런타임 진실로 간주

문서는 설명과 탐색에 사용한다. 생성 가능 여부는 승인된 Profile·Registry·Token Contract와 검증 결과가 결정한다.

### 13.5 Health 점수 하나로 판단

단일 총점만으로 생성 허용 여부를 결정하면 보안·접근성·Binding 오류가 평균값에 가려질 수 있다. 필수 Gate와 정보성 지표를 구분해야 한다.

---

## 14. 권장 도입 순서

### 14.1 1단계: 운영 기준 통합

- `DesignSystemKnowledgeSnapshot` 계약 정의
- ScreenSpecification과 생성 보고서에 `snapshotId` 기록
- 기존 Registry 검증을 `ComponentHealthReport`로 집계
- Markdown·JSON 기반 정적 문서 Export

### 14.2 2단계: 산출물 자동화

- CSS·TypeScript 중심 `TokenExportProfile` 구현
- `TokenExportManifest`와 Checksum 저장
- Asset Manifest와 라이선스 Metadata 관리
- Export Preview → 승인 → Build 검증 연결

### 14.3 3단계: 검색과 변경 이벤트

- PUBLISHED Snapshot 전용 검색 인덱스 구축
- 근거 식별자를 포함하는 Design System RAG 제공
- Outbox 기반 `DesignSystemEvent` 도입
- Drift·Health·Export·Index 갱신 Consumer 연결

### 14.4 4단계: 확장 운영

- 실제 요구를 근거로 Multi-brand·Theme 상속 도입
- 문서·검색·Deprecated 사용 분석
- Storybook·Thymeleaf Fixture용 Preview Adapter

---

## 15. 완료 조건

다음 조건이 충족되면 Supernova 분석에서 도출한 핵심 운영 원리가 SpringAI에 반영됐다고 판단할 수 있다.

- 생성된 Screen이 사용한 디자인 시스템 Snapshot을 역추적할 수 있다.
- Snapshot에서 Profile·Registry·Token·문서·Asset·Code Artifact 버전을 확인할 수 있다.
- Component별 생성 가능 여부와 차단 사유가 구조화돼 있다.
- 동일한 Token Source로부터 대상별 산출물을 재현할 수 있다.
- AI 답변이 승인된 Snapshot을 근거로 삼고 정확한 출처 식별자를 제공한다.
- 변경 이벤트가 Preview와 영향 분석을 시작하지만 승인을 우회하지 않는다.
- Deprecated Component가 신규 생성에 사용되지 않고 Replacement가 안내된다.
- Figma의 시각 책임과 Controller·VO·ScreenSpecification의 업무 책임이 유지된다.

---

## 16. 근거·추론·미확정 사항

### 16.1 확인된 근거

- Supernova는 토큰·컴포넌트·에셋·코드·가이드를 연결한 문서와 버전 관리 기능을 제공한다.
- Supernova는 Figma Variable과 Token을 여러 코드 형식으로 출력하고 외부 저장소·REST 흐름과 연결한다.
- Supernova는 디자인 시스템 데이터를 통합 검색하고 AI가 이를 근거로 답변하는 탐색 기능을 제공한다.
- SpringAI에는 Profile·Registry 버전, Lifecycle, Preview·Apply, Drift 검증, Source Revision 충돌 통제가 이미 존재한다.

### 16.2 근거에서 도출한 판단

- SpringAI의 우선 과제는 새로운 Figma 변환기가 아니라 기존 계약을 연결하는 Control Plane이다.
- Knowledge Snapshot을 중심으로 문서, Health, Search, Export, Event를 연결하면 기존 승인 파이프라인을 훼손하지 않고 운영성을 강화할 수 있다.
- Design System RAG는 일반 RAG와 분리하고 PUBLISHED Snapshot만 검색해야 생성 기준의 혼입을 방지할 수 있다.
- Trigger는 자동 적용보다 Preview·영향 분석 자동화에 사용하는 것이 현재 거버넌스와 일치한다.

### 16.3 현재 근거로 확정할 수 없는 사항

- 실제로 필요한 Export 대상이 CSS·TypeScript 외에 Android·iOS까지 포함되는지
- 다중 브랜드·Tenant 운영이 가까운 시점에 필요한지
- 디자인 문서의 작성·승인·게시 책임자가 누구인지
- Storybook 또는 별도 Component Preview 인프라를 운영할 것인지
- 사용 분석을 위한 화면·Component 사용 Telemetry가 현재 수집되는지

이 항목들은 선행 구현 대상으로 확정하지 않고 실제 요구와 운영 책임이 확인된 후 범위를 결정한다.

---

## 17. 최종 제안

SpringAI의 기존 목표 흐름은 그대로 유지한다.

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

여기에 다음 운영 계층을 추가하는 것이 Supernova 분석을 SpringAI답게 반영하는 방향이다.

```text
Versioned Knowledge Snapshot
  + Component Health
  + Token·Asset Export Manifest
  + APPROVED/PUBLISHED 전용 Search/RAG
  + 승인 경계를 유지하는 DesignOps Event
```

핵심은 디자인 시스템을 단순한 생성 입력이 아니라 **문서화되고, 버전이 고정되며, 검증되고, 검색 가능하고, 산출물까지 추적되는 운영 계약**으로 발전시키는 것이다.

---

## 참고 자료

- [Supernova Documentation](https://www.supernova.io/documentation)
- [Supernova Design System Management](https://www.supernova.io/design-system-management)
- [Supernova DesignOps](https://www.supernova.io/designops)
- [Supernova Ask AI and Browse Your Design System](https://www.supernova.io/ask-ai-browse-your-design-system)
- [Supernova Code Automation](https://www.supernova.io/code-automation)
- `docs/figma/11_Semantic_Figma_Design_System_Implementation_Plan.md`
- `docs/figma/Locofy_Flow_Based_SpringAI_Pipeline_Evolution_Review.md`
