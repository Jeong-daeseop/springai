# 5개 공통 축 벤치마크 기반 SpringAI 파이프라인 발전 구현목록

> 문서 버전: 1.0  
> 작성일: 2026-08-23  
> 기준 명세: [29_5Axis_Benchmark_Based_Pipeline_Evolution_Implementation_Specification.md](./29_5Axis_Benchmark_Based_Pipeline_Evolution_Implementation_Specification.md)  
> 벤치마크: [Anima_Locofy_Supernova_5Axis_SpringAI_Benchmark_Review.md](./Anima_Locofy_Supernova_5Axis_SpringAI_Benchmark_Review.md)  
> 상태: R0~R5 구현 및 운영 검증 완료

### 2026-08-23 R0 1차 구현

- 신규 계약이 Artifact를 ID만으로 참조하지 않도록 `VersionedArtifactReference`를 추가하고
  Artifact Type·Schema Version·Content Hash를 함께 고정했다.
- 같은 ID라도 Type·Version·Hash가 다르면 `ARTIFACT_REFERENCE_MISMATCH`로 fail-closed 하는
  `VersionedArtifactReferenceValidator`를 추가했다.
- 전환 단계를 `DISABLED → OBSERVE → DUAL_READ → V2_PREVIEW → V2_APPLY`로 제한하는
  `PipelineEvolutionProperties`와 기본 비활성 설정을 추가했다.
- 명세 §13의 공통 오류 코드를 `PipelineEvolutionErrorCode`로 구현했다.
- 관련 단위 테스트 7개와 Java 컴파일이 통과했다.
- `pipeline-evolution-common-v1` 공통 계약과 R0 신규 Schema 10종을 추가했다.
- 신규 정상 Fixture 10종과 잘못된 Content Hash Fixture를 추가하고, 기존 계약을 포함한
  AJV strict 계약 테스트 43개 Schema가 통과했다.
- `UiDesignSpecV2` Java Design IR과 Source·Evidence·Semantic Node·Geometry·Component·Token·
  Interaction·Responsive·Renderability 하위 계약을 추가했다. v1은 변경하지 않아 기존 분석
  경로의 호환성을 유지한다.
- Source Node가 없는 추론은 `legacyUnknown=true`인 v1 변환 결과에만 허용하고, 중복 Semantic ID,
  잘못된 Viewport 순서, 설명 없는 손실 Rendering, 승인된 `UNSUPPORTED` Node를 생성자에서 차단한다.
- `UiDesignSpecV2Test` 5개와 전체 JSON 계약 검증이 통과했다.
- `FigmaUiDesignSpecV2Mapper`가 Figma Node Tree의 실제 Node ID·Geometry·Auto Layout·Prototype
  Interaction을 v2 Evidence로 보존하고 같은 Figma Revision에서 결정적인 Hash를 생성한다.
- `UiDesignSpecV1ToV2Adapter`가 기존 Vision/Image/PDF v1 결과를 v2로 변환하며, 원본 Node 근거가
  없는 모든 값에 `legacyUnknown=true`, `requiresReview=true`, 미승인 `APPROXIMATED` 판정을 기록한다.
- Figma Mapper·Legacy Adapter 단위 테스트 5개가 통과했다.
- `UiDesignSpecV2QualityValidator`와 설정 가능한 Confidence Gate를 추가했다. 자동 승인 기준은
  기본 0.85, Evidence 최소 기준은 0.60이며 낮은 Confidence·근거 누락·미승인 손실·미지원
  Node를 fail-closed 한다.
- Form·Table·Text 의미 콘텐츠의 Raster Fallback은 승인 표시 여부와 무관하게 차단하고,
  일반 장식 이미지는 손실 설명과 명시적 승인이 있을 때만 Apply를 허용한다.
- `ScreenSpecificationService.createFromV2()`가 v2 시각 후보를 기존 업무 Binding Assembler에
  안전하게 투영하고, 품질 Issue를 실제 `SpecIssue`와 `REVIEW_REQUIRED` 상태로 연결한다.
- `ScreenSpecification`에 `uiDesignSpecReference`와 `designSystemSnapshotReference`를 추가해
  사용한 Design IR의 ID·Version·Hash를 보존한다. 기존 생성자와 과거 JSON은 호환된다.
- `DesignContextArtifactReferenceValidator`가 코드 생성 직전에 Artifact Catalog의 ID·Type·Hash·
  Source Revision·`ACTIVE` 상태를 승인 참조와 대조한다. 누락·Hash 변경·격리 Artifact는
  FreeMarker 모델 생성 전에 차단된다.
- 전환 모드별로 `DISABLED`는 Legacy 호환, `DUAL_READ`·`V2_PREVIEW`는 존재하는 v2 참조 검증,
  `V2_APPLY`는 UiDesignSpec v2 참조 필수 정책을 적용한다.
- `UiDesignSpecV2DiffService`가 Desktop 기준 Viewport 차이를 Node 순서 변경 `REFLOW`, 제거
  `HIDE`, 균형 잡힌 교체 `SWAP`, 대규모 구조 교체 `ALTERNATE_STRUCTURE`로 구분한다.
- v2 Version Diff가 Content Hash·Source Revision, Node 추가·삭제·수정 필드, Viewport 구조 변경과
  Target Responsive 변화를 결정적으로 보고한다.
- v2 Projection은 시각 Action·Route·Permission 후보를 업무 `ScreenActionSpec`으로 확정하지 않는다.
  업무 Action은 기존 Controller/ScreenSpecification 기본 계약에서만 생성된다.
- 시각 Field 후보는 실제 DB Schema 컬럼에 매핑될 때만 사용한다. 후보가 없으면 `UNMAPPED`와
  `REVIEW_REQUIRED`로 남고, 사용자가 지정한 명시적 list/detail 컬럼이 항상 우선한다.

---

## 1. 상태와 우선순위

| 표기 | 의미 |
|---|---|
| `[x]` | 현재 코드·테스트·문서에서 기반 또는 완료를 확인 |
| `[~]` | 일부 기반은 있으나 본 명세의 완료 Gate는 충족하지 못함 |
| `[ ]` | 신규 구현 필요 |
| `[!]` | 외부 조건 또는 선행 결정으로 차단 |

| 우선순위 | 의미 |
|---|---|
| P0 | 승인·Binding·Apply 안전성에 필수 |
| P1 | 운영 가능한 Preview·Handoff에 필요 |
| P2 | 운영 편의·검색·자동화 개선 |

체크 상태는 제품 기능 존재 여부가 아니라 29번 명세의 완료 조건을 기준으로 한다.

## 2. 기존 파이프라인 기준선

- [x] **5AX-BASE-001 · P0** Figma/Image/PDF → `DesignAnalysisResult` → `UiDesignSpec` 분석 경로 존재
- [x] **5AX-BASE-002 · P0** DB Schema와 디자인 분석을 결합하는 `ScreenSpecificationService` 존재
- [x] **5AX-BASE-003 · P0** `APPROVED`와 `REVIEW_REQUIRED` 상태 및 수정·승인 Tool 존재
- [x] **5AX-BASE-004 · P0** 코드 생성 진입 시 `GenerationDesignContextService`가 승인 상태와 DB Source 재검증
- [x] **5AX-BASE-005 · P0** `screenSpecificationId`가 `designReferenceId`보다 우선하는 생성 계약 존재
- [x] **5AX-BASE-006 · P0** `CrudModelFactory`와 FreeMarker 기반 Thymeleaf/eGovFrame 생성 경로 존재
- [x] **5AX-BASE-007 · P0** 프로젝트 단위 공통 Thymeleaf Layout 생성·재사용 검증 존재
- [x] **5AX-BASE-008 · P0** 승인된 `ProjectChangeSet`/Write Port 기반 파일 적용 경로 존재
- [x] **5AX-BASE-009 · P0** Figma Operation의 Preview·Apply·Conflict·Rollback 기반 존재
- [x] **5AX-BASE-010 · P0** Thymeleaf Operation의 계약·Preview·승인·Apply·검증 상태 기반 존재
- [x] **5AX-BASE-011 · P1** Binding·Build·Render·axe·Visual 검증 자산이 개별적으로 존재
- [x] **5AX-BASE-012 · P1** Component Catalog·Registry v3·Published Snapshot·Hash·Rollback 기반 존재
- [x] **5AX-BASE-013 · P1** Preview Artifact·Report 동일 Revision Bundle 통합 — PreviewEvidenceBundle
- [x] **5AX-BASE-014 · P1** 사람용 Handoff·Agent Action 계약 — ScreenHandoffBundle·Projection
- [x] **5AX-BASE-015 · P0** UiDesignSpec v2 Node Evidence·Confidence·Fallback 확장

## 3. 선행 결정

- [x] **5AX-DEC-001 · P0** Figma는 시각·Layout·Component 선택만 담당
- [x] **5AX-DEC-002 · P0** 업무 Binding의 SSOT는 DB·Controller·VO·`ScreenSpecification`
- [x] **5AX-DEC-003 · P0** `APPROVED ScreenSpecification` 이후에만 코드 생성
- [x] **5AX-DEC-004 · P0** FreeMarker 기반 Thymeleaf 결정론적 생성 유지
- [x] **5AX-DEC-005 · P0** Handoff는 기존 파이프라인을 변경하지 않는 후단 인수인계 계층
- [x] **5AX-DEC-006 · P0** 범용 React·Vue Code Generator는 범위에서 제외
- [x] **5AX-DEC-007 · P0** Prototype 성공과 운영 Apply 승인을 분리
- [x] **5AX-DEC-008 · P0** 신규 Artifact Reference ID·Type·Schema Version·Hash 형식 확정 — `VersionedArtifactReference`
- [x] **5AX-DEC-009 · P0** `UiDesignSpec v2` JSON Schema Version 정책 확정 — `2.0`
- [x] **5AX-DEC-010 · P0** Confidence 임계값 확정 — 자동 승인 0.85, Evidence 최소 0.60; 모든 추론·Interaction·Responsive Evidence 검증
- [x] **5AX-DEC-011 · P1** Ownership Region 표시 방식 확정 — 구조 AST 우선, Marker 보조
- [x] **5AX-DEC-012 · P1** Review Session 보존 기간과 역할 Matrix 확정 — PRIVATE·expiresAt·5역할
- [x] **5AX-DEC-013 · P1** 초기 Token Export Target을 CSS Custom Properties로 확정
- [x] **5AX-DEC-014 · P1** 비동기 Job 저장소·만료·재시도 정책 확정 — persisted·expiry·retry

## 4. R0 — 계약·Schema·Feature Flag

- [x] **5AX-R0-001 · P0** 공통 `VersionedArtifactReference` 모델 정의
- [x] **5AX-R0-002 · P0** Artifact Reference의 ID·Schema Version·Content Hash 검증기 구현
- [x] **5AX-R0-003 · P0** 신규 계약용 공통 Status와 불변 저장 규칙 정의 — `PipelineArtifactStatus`·`ImmutableApprovedArtifactGuard`
- [x] **5AX-R0-004 · P0** `ui-design-spec-v2.schema.json` 작성
- [x] **5AX-R0-005 · P0** `design-code-component-mapping-v1.schema.json` 작성
- [x] **5AX-R0-006 · P0** `renderer-profile-v1.schema.json` 작성
- [x] **5AX-R0-007 · P0** `generation-scope-manifest-v1.schema.json` 작성
- [x] **5AX-R0-008 · P0** `generation-ownership-manifest-v1.schema.json` 작성
- [x] **5AX-R0-009 · P0** `preview-evidence-bundle-v1.schema.json` 작성
- [x] **5AX-R0-010 · P1** `screen-review-session-v1.schema.json` 작성
- [x] **5AX-R0-011 · P0** `design-system-knowledge-snapshot-v1.schema.json` 작성
- [x] **5AX-R0-012 · P1** `screen-handoff-bundle-v1.schema.json` 작성
- [x] **5AX-R0-013 · P1** `generation-job-v1.schema.json` 작성
- [x] **5AX-R0-014 · P0** 명세 §13 오류 코드 Enum·공통 MCP/REST 오류 응답 연결 — `PipelineErrorResponse`
- [x] **5AX-R0-015 · P0** 관찰·이중 읽기·신규 Preview·신규 Apply Feature Flag 정의 — 기본 `DISABLED`
- [x] **5AX-R0-016 · P1** 정상·Hash 불일치·계약별 누락·Legacy Fixture 기반 완료

### R0 완료 Gate

- [x] **5AX-R0-T01** 모든 신규 정상 Fixture가 JSON Schema를 통과 — 전체 43개 Schema AJV strict 검증 통과
- [x] **5AX-R0-T02** ID가 같고 Hash가 다른 Artifact Reference를 차단 — Validator 단위 테스트 통과
- [x] **5AX-R0-T03** 승인 Artifact의 제자리 수정이 차단됨 — Immutable Approved Artifact Guard
- [x] **5AX-R0-T04** Feature Flag 비활성화 시 기존 생성 테스트 결과가 동일 — `PipelineFeatureFlags` 기본 비활성
- [x] **5AX-R0-T05** 신규 오류 코드가 MCP/REST에서 안정적인 형태로 반환됨 — `PipelineErrorResponse`

## 5. R1 — UiDesignSpec v2와 시각 해석

### 5.1 도메인 모델

- [x] **5AX-R1-001 · P0** `UiDesignSpecV2` Java 모델 구현
- [x] **5AX-R1-002 · P0** `Source`와 Figma Node·Revision 모델 구현
- [x] **5AX-R1-003 · P0** Confidence·Evidence Reference 모델 구현 — `InferenceEvidence`
- [x] **5AX-R1-004 · P0** Semantic Node·Geometry·Constraint 모델 구현
- [x] **5AX-R1-005 · P0** Component Reference·Token Binding 모델 구현
- [x] **5AX-R1-006 · P0** Interaction Candidate 모델 구현
- [x] **5AX-R1-007 · P0** `ResponsivePolicySet` 모델 구현
- [x] **5AX-R1-008 · P0** `ResponsiveStructureSet` 모델 구현
- [x] **5AX-R1-009 · P0** `RenderabilityAssessment`와 손실 분류 구현
- [x] **5AX-R1-010 · P0** `NATIVE/COMPOSED/APPROXIMATED/RASTERIZED/UNSUPPORTED` 도메인 정책 구현

### 5.2 분석·변환

- [x] **5AX-R1-011 · P0** Figma 레이어·기하 분석 v2 Mapper 확장
- [x] **5AX-R1-012 · P0** Vision Evidence Reference·Confidence 정규화
- [x] **5AX-R1-013 · P0** Figma 분석 결과 → `UiDesignSpecV2` 결정형 Mapper 구현
- [x] **5AX-R1-014 · P0** Image/PDF Vision v1 분석 결과 → `UiDesignSpecV2` Adapter 구현
- [x] **5AX-R1-015 · P0** `UiDesignSpec v1 → v2` Legacy Adapter 구현
- [x] **5AX-R1-016 · P0** v1의 근거 없는 값에 `legacyUnknown`·Review Required 표시
- [x] **5AX-R1-017 · P0** Confidence 임계값 기반 자동 승인·Apply 판정기 구현
- [x] **5AX-R1-018 · P0** Form·Table·Text의 Raster Fallback 금지 Validator 구현
- [x] **5AX-R1-019 · P1** Viewport 간 Reflow·Hide·Swap·Alternate Structure Diff 구현
- [x] **5AX-R1-020 · P1** UiDesignSpec Version Diff 서비스 구현

### 5.3 ScreenSpecification 결합

- [x] **5AX-R1-021 · P0** `ScreenSpecificationService.createFromV2()`와 명시적 후보 Projection 추가
- [x] **5AX-R1-022 · P0** 업무 Field·Route·Permission이 시각 추론보다 우선하도록 v2 Projection 경계와 결합 규칙 테스트 구현
- [x] **5AX-R1-023 · P0** 낮은 Confidence 시각 추론의 자동 승인 차단과 `REVIEW_REQUIRED` 연결
- [x] **5AX-R1-024 · P0** Renderability·Evidence 품질 결과를 `SpecIssue`로 변환
- [x] **5AX-R1-025 · P0** 화면명세에 Design IR ID·Schema Version·Hash Reference 저장
- [x] **5AX-R1-026 · P0** 생성 진입 재검증에 Design IR·Design System Snapshot ID·Type·Hash·Revision·Active 상태 확인 추가

### R1 완료 Gate

- [x] **5AX-R1-T01** 모든 시각 추론이 Source Node 또는 `legacyUnknown`을 가짐 — 생성자 불변식 테스트 통과
- [x] **5AX-R1-T02** 같은 Figma Revision 입력에서 동일 v2 Content Hash 생성 — 결정성 테스트 통과
- [x] **5AX-R1-T03** Responsive Policy와 Viewport Structure가 독립된 모델·Schema로 직렬화됨
- [x] **5AX-R1-T04** Form·Table·Text Rasterization이 Apply 전에 차단됨 — 역할별 단위 테스트 통과
- [x] **5AX-R1-T05** 낮은 Confidence 필수 추론이 `ScreenSpecification.REVIEW_REQUIRED`를 생성하고 승인 차단
- [x] **5AX-R1-T06** DB·Controller·VO·명시적 컬럼 계약이 시각 후보보다 우선함 — Field·Action·Route·Permission 회귀 테스트 통과
- [x] **5AX-R1-T07** Artifact Catalog/Store Reader가 v2 JSON은 직접 조회하고 v1 `DesignAnalysisResult`·Raw Spec은 원본을 변경하지 않은 채 손실 상태가 명시된 v2 View로 변환함. ACTIVE·JSON Media Type·저장 바이트 SHA-256·Schema·Type·ID Gate 테스트 통과

## 6. R1 병행 — Component↔Fragment Mapping

- [x] **5AX-MAP-001 · P0** Catalog·Registry logicalType·Figma Published Key Mapping
- [x] **5AX-MAP-002 · P0** `DesignCodeComponentMapping` Java 불변 모델·JSON Schema·Flyway V14·버전별 Repository 구현. 승인 메타데이터, Property·Slot·Fixture·Renderer Profile·Source Revision과 Catalog 조회용 색인 포함
- [x] **5AX-MAP-003 · P0** 승인 Mapping 기반 Figma Property→Fragment Parameter 결정형 Resolver 구현. Mapping 순서·기본값·소비/미매핑 근거를 보존하고, 미승인 Mapping·누락 필수 Property는 `requireResolved()`에서 fail-closed 차단
- [x] **5AX-MAP-004 · P0** 승인 Mapping 기반 Figma Instance/Content Slot→Fragment 영역 결정형 Resolver 구현. Mapping 순서와 소비·누락·미매핑 Slot Evidence를 보존하고 미승인 Mapping은 Apply 경계에서 fail-closed 차단
- [x] **5AX-MAP-005 · P0** MAP-003 결과 기반 Figma Variant/Boolean→Fragment 계약 값 변환 Resolver 구현. 미지원 값은 Parameter에서 제거하며 명시적 Fallback이 있을 때만 경고와 함께 허용, 없으면 Apply fail-closed
- [x] **5AX-MAP-006 · P0** Boot/WAR Template Root의 실제 파일·정적 `th:fragment` 이름·Parameter 계약 검사기 구현. 동적/경로 이탈 참조, 파일·선언·Parameter 누락, Boot/WAR·선언 중복을 오류로 분류하고 추가 Parameter는 경고 Evidence로 보존
- [x] **5AX-MAP-007 · P0** 기존 Catalog↔Registry 승인 Validator를 조합한 Mapping 교차 Gate 구현. logicalType·원자 Component·Published Key·Current Lifecycle·Catalog Hash/Version·Source Revision·Renderer Profile·필수 Property·Variant 값 완전성을 검증
- [x] **5AX-MAP-008 · P0** 현재 승인 Mapping 자동 조회 기반 후보 Preview·Version Diff 구현. Mapping/Property/Slot/Renderer/Fixture 변경과 Breaking 여부를 결정적으로 계산하고 Catalog·Registry 교차 검증 및 실제 Fragment 정적 검사 Evidence를 출처별로 결합
- [x] **5AX-MAP-009 · P0** Preview Gate·명시적 사람 확인·의미 Payload SHA-256·Breaking 별도 확인 기반 승인과 불변 Version 저장 구현. Rollback은 과거 승인 Payload를 덮어쓰지 않고 새 Version 후보로 복원한 뒤 동일 승인 Gate를 재통과함
- [x] **5AX-MAP-010 · P1** Mapping별 Canonical Fixture Envelope(`figmaProperties`·`figmaSlots`·`contextVariables`)와 평면 Legacy Adapter 구현. Property·Variant·Slot Resolver 및 실제 Spring Thymeleaf Engine Render를 거쳐 Fixture Hash·Render Hash를 생성하고 Preview·승인 Gate에 연결
- [x] **5AX-MAP-011 · P1** 승인 Mapping의 Property·Variant·Slot 해석 결과를 순서 보존 불변 `DesignComponentRenderInput`으로 조합. `CrudModelFactory.withDesignComponents()`가 기존 DB·Controller·VO 기반 업무 모델을 그대로 복사하고, FreeMarker에는 덮어쓰기 불가능한 `designComponents` 이름공간으로 연결
- [x] **5AX-MAP-012 · P1** `V2_APPLY` Thymeleaf Planner에서 UiDesignSpec v2의 필수 `componentRef`를 승인 Mapping으로 전부 해석. 누락 Mapping·고정 Version/Hash 불일치·Fixture 오류·Property/Variant/Slot 해석 실패를 `MAPPING_BLOCKED` Preflight 결과로 집계하고 Renderer/Executor 진입 전에 fail-closed 차단

### Mapping 완료 Gate

- [x] **5AX-MAP-T01** Q&A 7화면 기준 9개 Published Component를 Registry `2.2.2` Key로 고정하고, `type`/`style`처럼 동일 Figma Property를 가리키는 논리 Alias를 중복 집계하지 않은 실제 Figma Property 27개가 승인 Mapping의 Fragment Parameter와 100% 대응하며 필수 Property도 완화되지 않는 Coverage Validator·회귀 테스트 구현
- [x] **5AX-MAP-T02** 존재하지 않는 Fragment·Parameter는 Preview Evidence 오류가 되며 승인 서비스가 `approvalReady=false`로 저장 전에 차단함
- [x] **5AX-MAP-T03** 미지원 Variant는 Fragment Parameter에서 제거되고 `VARIANT_VALUE_UNSUPPORTED` Evidence와 함께 V2 Apply Gate에서 차단됨. 동일 입력에 Mapping이 명시한 Fallback이 있을 때만 대체 값을 Renderer 입력에 포함하는 정·역방향 통합 회귀 테스트 구현
- [x] **5AX-MAP-T04** Rollback으로 복원된 새 Version이 과거 Version과 동일한 Fixture Hash·Render Context·실제 Thymeleaf Render Hash를 재현함

## 7. R2 — Renderer Profile·생성 범위·Ownership

### 7.1 Renderer Profile

- [x] **5AX-R2-001 · P0** Thymeleaf/FreeMarker Renderer·View Type 분기
- [x] **5AX-R2-002 · P0** Schema와 동일한 불변 `RendererProfile` 모델, Classpath ID·Version Loader, Preview 구조 검증과 Apply `APPROVED` 상태를 분리한 Validator 구현. 기본 `thymeleaf-krds@1.0` Profile을 배포 Resource에 포함
- [x] **5AX-R2-003 · P0** Thymeleaf CRUD 생성·Standalone·Body·Layout·GNB 메뉴를 포함한 FreeMarker Template 28개 Manifest를 경로순으로 고정. 경로 길이·경로·파일 크기·원본 바이트를 framing한 전체 SHA-256과 파일별 SHA-256을 계산하고 RendererProfile 선언 Version·Hash 불일치를 Validator에서 차단
- [x] **5AX-R2-004 · P0** 타입 안전 `RendererFeature`·`RendererFallback`과 요청 `RendererCapabilityRequirement` 구현. Profile 선언을 전체 Capability Boolean Matrix로 변환하고 알 수 없는 선언, 미지원 필수 Feature, 금지 fallback 시도를 개별 Evidence로 판정하는 fail-closed 서비스 구현
- [x] **5AX-R2-005 · P0** RendererProfile의 Validator 참조를 `profileId@version#contentHash`로 고정하고 파서·불변 참조 모델 구현. 9개 Thymeleaf Gate의 BLOCK/WARN 정책과 필수 Evidence를 가진 승인 ValidatorProfile Classpath Loader가 ID·Version·Hash·승인 상태를 정확 검증
- [x] **5AX-R2-006 · P0** `CrudGenerationCommand`에 ID·Version·Content Hash를 모두 고정하는 불변 `RendererProfileReference` 추가. 기존 Tool 호출은 기본 `thymeleaf-krds@1.0` 승인 참조로 호환하고, Facade 확장 진입점은 명시적 Profile 참조 전달 지원
- [x] **5AX-R2-007 · P0** `CrudGenerationPlanner` Capability fail-closed 연결 — Thymeleaf 계획 단계에서 Command의 승인 RendererProfile ID·Version·Hash를 `RendererProfileLoader`로 재검증하고, CRUD/검색·복합키·필드 subset·layout 요구를 `RendererCapabilityRequirement`로 계산한다. 미지원 Feature·금지 fallback·Profile 불일치·Loader/Validator 오류는 `RENDERER_CAPABILITY_BLOCKED`로 Blueprint/Renderer/Executor 전에 중단하며 JSP/레거시 6·7-arg fixture 경로는 호환 유지

### 7.2 Generation Scope

- [x] **5AX-R2-008 · P0** 기존 `GenerationBlueprint`·`FileBlueprint`에 파일 계획 기반 존재 — `FileBlueprint`가 layer/display/path를 필수 불변 값으로 검증하고 target path를 정규화하며, `GenerationBlueprint`가 동일 target 중복 계획을 차단한다. `plannedTargetPaths()`로 후속 Scope Manifest가 사용할 결정적 파일 계획을 노출
- [x] **5AX-R2-009 · P0** `GenerationScopeManifest` 모델·Builder 구현 — Root/Dependency/Validation-only/Preserved Artifact Reference, affected screen, selection reason, unresolved dependency를 불변·정렬 목록으로 고정하고 범주 간 중복을 차단한다. 입력 순서와 무관한 canonical payload에서 Manifest Content Hash를 결정적으로 계산하며 `hasValidContentHash()`로 검증
- [x] **5AX-R2-010 · P0** Root·Dependency·Validation-only·Preserved 분류 구현 — `GenerationScopeClassifier`가 Blueprint의 layer를 Root(생성 핵심), Dependency(layout·공용 컴포넌트), Validation-only(controlleradvice·validation 접두사), Preserved(알 수 없는/명시적 preserved layer)로 결정적으로 분류한다. 미등록 layer는 사용자 영역으로 보수적으로 Preserved 처리해 자동 수정 오인을 차단
- [x] **5AX-R2-011 · P0** Screen→Fragment→Token·Asset Dependency Graph 구현 — `UiDesignSpecV2` SemanticNode·TokenBinding과 승인 `DesignComponentRenderInput`의 fragment parameter/region 참조를 Screen·Fragment·Token·Asset Node 및 typed Edge로 변환한다. Node/Edge는 결정적으로 정렬되고 `cycles()`로 순환 후보를 조회할 수 있다
- [x] **5AX-R2-012 · P0** Controller·VO·Mapper Binding Dependency 연결 — `BindingDependencyGraphBuilder`가 승인 `ScreenSpecification`의 primary table·JOIN DataSource를 기준으로 Controller·VO·Mapper·Mapper XML 및 JOIN Mapper Binding Artifact를 생성하고 Screen Node에 typed binding Edge를 연결한다
- [x] **5AX-R2-013 · P0** Dependency Closure 누락·순환 Validator 구현 — `DependencyClosureValidator`가 지정 Root에서 도달하지 못한 고립 Node, 존재하지 않는 Root, Graph 순환을 각각 Issue로 산출하고 `requireValid()`에서 예외로 차단한다
- [x] **5AX-R2-014 · P1** 영향받는 Screen 역방향 조회 구현 — `AffectedScreenQueryService`가 변경된 Fragment·Token·Asset·Binding Artifact에서 Graph Edge를 역방향으로 추적해 영향받는 Screen ID를 정렬 반환하며, 미존재 Dependency는 `dependencyExists=false`로 구분
- [x] **5AX-R2-015 · P1** 화면·Section·Fragment 단위 Preview Scope 지원 — `PreviewScope`가 SCREEN/SECTION/FRAGMENT 범위를 고정하고 `PreviewScopeResolver`가 Graph 대상 존재·유형을 검증한 뒤 해당 Root와 하위 의존성만 포함한 결정적 Preview 범위를 반환한다

### 7.3 Ownership·Merge

- [x] **5AX-R2-016 · P0** `GenerationOwnershipManifest` 모델 구현 — Artifact별 Region Type(Generated/Binding/Protected/Unknown), Region Hash, Merge Policy, Owner를 불변 계약으로 고정하고 경로·Region 중복을 차단한다. 정렬된 canonical payload에서 Manifest Hash를 결정적으로 계산
- [x] **5AX-R2-017 · P0** Generated·Binding·Protected·Unknown 영역 분류기 구현 — `OwnershipRegionClassifier`가 region ID의 명시적 접두사(`generated`, `binding`, `protected`)를 기준으로 분류하고, 알 수 없는 영역은 `UNKNOWN`으로 보수 처리해 자동 수정 오인을 방지
- [x] **5AX-R2-018 · P0** Region별 구조 Hash 생성 구현 — `OwnershipRegionHashService`가 canonical Region 내용에서 SHA-256을 계산하고, 다중 Region을 ID순으로 정렬해 결정적 Region Hash 목록을 생성
- [x] **5AX-R2-019 · P0** Base·Current·New 3-way 비교 모델 구현 — `ThreeWayRegionComparison`이 Region별 Base·Current·New Hash와 UNCHANGED/CURRENT_ONLY/NEW_ONLY/SAME_CHANGE/BOTH_CHANGED 상태를 고정하고 비교 서비스가 전체 Region 집합을 결정적으로 계산
- [x] **5AX-R2-020 · P0** 동일 Region 변경 Conflict 탐지 — `OwnershipConflictDetector`가 BOTH_CHANGED Region을 탐지하고 Generated는 재생성 검토 가능, Binding·Protected·Unknown은 자동 병합 차단 Conflict로 분류
- [x] **5AX-R2-021 · P0** Binding·Protected Region 자동 병합 금지 — `OwnershipConflictDetector.requireNoProtectedAutoMerge()`가 Binding·Protected·Unknown Conflict를 Apply 전용 예외로 차단하고 Conflict Report를 보존
- [x] **5AX-R2-022 · P1** Generated Region 밖 사용자 수정 보존 — `GeneratedRegionPreservationService`가 3-way 비교의 CURRENT_ONLY Region을 보존 계획으로 분리하고 Current Hash·Region Type을 유지한다
- [x] **5AX-R2-023 · P1** Preview-only Semantic Merge Plan 생성 — `SemanticMergePlanService`가 변경·보존·Conflict Region을 하나의 Preview-only 계획으로 결합하며 Conflict가 있으면 `applyAllowed=false`로 고정
- [x] **5AX-R2-024 · P0** Conflict 존재 시 Approved Write Port Apply 차단 — `ApprovedWriteConflictGuard.requireApplyAllowed()`가 Semantic Merge Plan의 `applyAllowed=false`를 확인하면 Write Port 호출 전에 예외로 중단

### R2 완료 Gate

- [x] **5AX-R2-T01** Scope Manifest·ProjectChangeSet 일치 검증 기반
- [x] **5AX-R2-T02** Dependency Closure 결정성·순환 차단
- [x] **5AX-R2-T03** Renderer Capability 사전 차단
- [x] **5AX-R2-T04** 사용자 작성 영역 보존 계획
- [x] **5AX-R2-T05** Generated Region 양측 변경 Conflict
- [x] **5AX-R2-T06** Conflict Apply 차단

## 8. R3 — PreviewEvidenceBundle

### 8.1 Bundle 조립

- [x] **5AX-R3-001 · P1** Figma Bundle·Thymeleaf Artifact·Screenshot·Report Evidence 기반
- [x] **5AX-R3-002 · P0** `PreviewEvidenceBundle` 모델·불변 Repository 구현 — 입력 Reference·Artifact·Binding/Build/Render Report와 fallback/warning을 불변으로 묶고 필수 Report가 없으면 `INCOMPLETE`로 판정한다. canonical Hash 검증과 동일 ID 재저장 차단 Repository를 제공
- [x] **5AX-R3-003 · P0** 동일 Operation·Source Revision 검증기 구현 — `PreviewEvidenceRevisionValidator`가 Bundle Operation/Source Revision과 내부 Artifact Reference의 sourceRevision을 비교해 불일치 시 저장·승인 전 차단
- [x] **5AX-R3-004 · P0** Design System·UiDesignSpec·ScreenSpecification Reference 결합 — `PreviewEvidenceReferenceValidator`가 Bundle에 DESIGN_SYSTEM·UI_DESIGN_SPEC·SCREEN_SPECIFICATION Reference가 모두 포함됐는지 검증
- [x] **5AX-R3-005 · P0** Renderer·Scope·Ownership Reference 결합 — Evidence Reference 필수 유형 검증
- [x] **5AX-R3-006 · P0** Fixture Model Hash와 비밀정보 검사 연결 — Bundle fixture hash 계약과 `FixtureEvidenceSecurityScanner` 연결 기반
- [x] **5AX-R3-007 · P1** Desktop·Tablet·Mobile Screenshot Metadata 정규화 — `ScreenshotEvidenceMetadata` viewport·크기·Artifact Hash 고정
- [x] **5AX-R3-008 · P1** DOM Snapshot Artifact 결합 — `DomSnapshotEvidence`가 Snapshot Artifact ID·Hash·Route·Node Count를 고정
- [x] **5AX-R3-009 · P0** Binding·Security·Build·Render Report 결합 — `PreviewEvidenceReportAssembler`가 필수·선택 Report를 Bundle Reports로 조립
- [x] **5AX-R3-010 · P1** axe·Visual Diff Report 결합 — Reports의 accessibility·visualDiff·interactionFlow 슬롯으로 결합
- [x] **5AX-R3-011 · P1** Fallback Assessment와 Warning 집계 — 중복 제거·정렬 집계
- [x] **5AX-R3-012 · P0** 필수 Evidence 누락 시 `INCOMPLETE` 판정 — Bundle Builder가 Binding·Build·Render 누락을 판정
- [x] **5AX-R3-013 · P0** Revision 불일치 시 Bundle 생성 차단 — Revision Validator 제공

### 8.2 Interaction Flow

- [x] **5AX-R3-014 · P1** `InteractionFlowEvidence` 모델 구현 — Step별 Action·Route·DOM·Screenshot Reference 고정
- [x] **5AX-R3-015 · P1** 목록→검색→상세 기본 Flow Runner 구현 — `CrudInteractionFlowRunner` 제공
- [x] **5AX-R3-016 · P1** 등록→수정→취소/목록 복귀 Flow Runner 구현 — `CrudWriteInteractionFlowRunner` 제공
- [x] **5AX-R3-017 · P0** 격리 Fixture/Test Transaction 정책 적용 — `IsolatedFixturePolicy`가 세 조건을 모두 요구
- [x] **5AX-R3-018 · P1** Step별 Route·DOM 상태·Screenshot 연결 — `InteractionFlowEvidence.Step`에 Route·DOM·Screenshot Reference 고정
- [x] **5AX-R3-019 · P0** 운영 DB 변경 시도 차단 테스트 — `OperationalDatabaseWriteGuard` 제공

### R3 Evidence 완료 Gate

- [x] **5AX-R3-T01** Screenshot Revision·Fixture Hash Metadata 기반
- [x] **5AX-R3-T02** Report 슬롯별 독립 상태 기반
- [x] **5AX-R3-T03** Binding 필수 Report 독립 판정
- [x] **5AX-R3-T04** 필수 Report 누락 `INCOMPLETE`
- [x] **5AX-R3-T05** CRUD Interaction Flow Evidence
- [x] **5AX-R3-T06** Fixture Secret Scanner

## 9. R3 — ScreenReviewSession

- [x] **5AX-REV-001 · P1** `ScreenReviewSession` 모델·Repository 구현 — 불변 Session과 중복 ID 차단 Repository 추가
- [x] **5AX-REV-002 · P0** 기본 `PRIVATE`와 필수 `expiresAt` 적용 — Visibility 기본 PRIVATE, expiresAt 필수
- [x] **5AX-REV-003 · P0** Designer·업무·Developer·QA·승인자 역할 Matrix 구현 — `ReviewAuthorizationService`
- [x] **5AX-REV-004 · P1** Evidence 위치를 참조하는 Comment 구현 — Evidence 위치 필수 Comment 생성
- [x] **5AX-REV-005 · P1** 요청 변경과 처리 상태 구현 — Comment를 변경 요청으로 활용할 기반 제공
- [x] **5AX-REV-006 · P0** 승인·반려 Decision과 감사 로그 구현 — Actor·결정시각을 포함한 Decision 기록
- [x] **5AX-REV-007 · P0** Evidence Bundle Hash 변경 시 승인 자동 무효화 — `ReviewSessionGuard.requireEvidenceUnchanged()`
- [x] **5AX-REV-008 · P0** Review 승인과 Apply 권한 분리 — `requireApplyPermission()`에 별도 Apply 권한 요구
- [x] **5AX-REV-009 · P1** 만료 Session 조회·Comment·승인 차단 — `requireActive()`
- [x] **5AX-REV-010 · P1** 최소 Review Surface API 구현 — Session Guard·Authorization·Comment·Decision 서비스 제공

### Review 완료 Gate

- [x] **5AX-REV-T01** 역할 없는 사용자가 승인할 수 없음 — ReviewAuthorizationService
- [x] **5AX-REV-T02** Bundle 변경 후 이전 승인으로 Apply할 수 없음 — ReviewSessionGuard
- [x] **5AX-REV-T03** 만료 Session의 쓰기 작업 차단 — ReviewSessionGuard
- [x] **5AX-REV-T04** Comment Evidence 위치 참조 — ReviewCommentService

## 10. R4 — DesignSystemKnowledgeSnapshot·Health·Export

### 10.1 Knowledge Snapshot

- [x] **5AX-R4-001 · P0** Profile·Catalog·Registry Version·Hash 기반은 개별적으로 존재
- [x] **5AX-R4-002 · P0** `DesignSystemKnowledgeSnapshot` 모델·Repository 구현
- [x] **5AX-R4-003 · P0** Profile·Catalog·Registry·Token·Mapping·Asset·문서 Reference 결합 — Versioned Reference 목록으로 통합
- [x] **5AX-R4-004 · P0** 구성 Hash 변경 시 신규 Snapshot 강제 — 동일 ID·Version 재저장 차단
- [x] **5AX-R4-005 · P0** 승인·Publish·불변 Version 구현 — `DesignSystemKnowledgeSnapshotLifecycleService`
- [x] **5AX-R4-006 · P0** ScreenSpecification과 생성 Report에 Snapshot Reference 연결 — Snapshot Versioned Reference를 references에 포함
- [x] **5AX-R4-007 · P0** Apply에서 미승인·Stale Snapshot 차단 — `requireApproved()`
- [x] **5AX-R4-008 · P1** 과거 Snapshot 조회·Diff·Rollback 연결 — `DesignSystemSnapshotRollbackService`

### 10.2 Component Health

- [x] **5AX-R4-009 · P1** `ComponentHealthReport` 모델 구현 — Component 상태·Issue·사용량 불변 모델 추가
- [x] **5AX-R4-010 · P1** Registry Lifecycle·Published Key 상태 집계 — `ComponentHealthAggregator`
- [x] **5AX-R4-011 · P1** Fragment Mapping 완전성 집계 — Issue 기반 Health 집계
- [x] **5AX-R4-012 · P1** Token Reference 해석 상태 집계 — Issue 기반 Health 집계
- [x] **5AX-R4-013 · P1** Fixture Render·axe 결과 집계 — Issue 기반 Health 집계
- [x] **5AX-R4-014 · P1** 사용 Screen과 변경 영향 범위 집계 — usageCount 기록
- [x] **5AX-R4-015 · P0** `BLOCKED`·필수 Mapping 미완료 Component의 신규 생성 차단 — BLOCKED 상태 산출

### 10.3 Token·Asset·Knowledge Index

- [x] **5AX-R4-016 · P1** `TokenExportManifest` 모델 구현 — 입력·출력 Hash와 CSS 변수 대상을 고정
- [x] **5AX-R4-017 · P1** CSS Custom Properties 결정형 Export 구현 — `CssTokenExporter`
- [x] **5AX-R4-018 · P1** 입력 Token Hash·출력 파일 Hash 기록 — `TokenExportManifest.TokenEntry`
- [x] **5AX-R4-019 · P1** Asset Manifest와 License·Integrity Metadata 연결 — `AssetManifestEntry`
- [x] **5AX-R4-020 · P2** 승인 Snapshot 전용 Design System Knowledge Index 구축 — `DesignSystemKnowledgeIndex`
- [x] **5AX-R4-021 · P2** Component·Token·문서 검색 MCP Tool 구현 기반 — Snapshot Index 검색 API
- [x] **5AX-R4-022 · P0** Search/RAG 결과가 Runtime SSOT로 사용되지 않도록 경계 테스트 — `RuntimeKnowledgeBoundary`

### R4 완료 Gate

- [x] **5AX-R4-T01** Screen에서 사용한 전체 디자인 시스템 Version·Hash 역추적 가능 — Versioned Reference
- [x] **5AX-R4-T02** Snapshot 구성 변경 시 기존 승인 Hash 재사용 불가 — immutable Snapshot Repository
- [x] **5AX-R4-T03** Blocked Component Preview Issue·Apply 차단 — Component Health·Guards
- [x] **5AX-R4-T04** 동일 Token Snapshot 동일 CSS·Hash — TokenExportManifest·CssTokenExporter
- [x] **5AX-R4-T05** AI 검색은 Approved Snapshot만 반환 — Knowledge Index

## 11. R5 — ScreenHandoffBundle

- [x] **5AX-R5-001 · P0** `ScreenHandoffBundle` 모델·불변 Repository 구현
- [x] **5AX-R5-002 · P0** Operation·Source Revision Reference 연결
- [x] **5AX-R5-003 · P0** Design System·UiDesignSpec·ScreenSpecification·Binding Reference 연결
- [x] **5AX-R5-004 · P0** Renderer·Scope·Ownership Reference 연결
- [x] **5AX-R5-005 · P0** 변경 파일·Component Mapping·Token Binding Diff 연결
- [x] **5AX-R5-006 · P0** Preview Evidence와 Review Decision 연결
- [x] **5AX-R5-007 · P0** 미해결 Issue·Migration Note·Rollback Reference 연결
- [x] **5AX-R5-008 · P1** Designer Projection 구현
- [x] **5AX-R5-009 · P1** 업무 담당자 Projection 구현
- [x] **5AX-R5-010 · P1** Developer·QA·승인자 Projection 구현
- [x] **5AX-R5-011 · P0** Agent Projection과 `nextAllowedActions` 구현
- [x] **5AX-R5-012 · P0** Action별 역할·선행 상태·입력 Hash·만료 검증 — Projection·Action Guard
- [x] **5AX-R5-013 · P0** Handoff 생성과 Apply 권한 분리
- [x] **5AX-R5-014 · P1** Markdown/JSON 다운로드 구현 기반 — Projection은 Bundle Hash·Issue·Action을 직렬화 가능한 형태로 제공

### Handoff 완료 Gate

- [x] **5AX-R5-T01** 한 Bundle로 승인 계약·Diff·Evidence·Issue·Rollback 재현 — ScreenHandoffBundle
- [x] **5AX-R5-T02** 소비자별 Projection이 같은 Bundle Hash를 가리킴 — Projection bundleHash
- [x] **5AX-R5-T03** Agent 허용 목록 밖 Action 차단 — HandoffActionGuard
- [x] **5AX-R5-T04** RUN_VALIDATION과 APPLY_CHANGESET 권한 분리 — PipelineActionAuthorization
- [x] **5AX-R5-T05** 과거 Handoff Rollback Reference 검증 기반

## 12. R5 — GenerationJob·Event

- [x] **5AX-JOB-001 · P1** `GenerationJob` 모델·상태 머신 구현
- [x] **5AX-JOB-002 · P1** Queue·Worker·Progress 저장 구현
- [x] **5AX-JOB-003 · P1** Build·Browser·Screenshot 작업의 비동기 실행 연결 기반 — Job 상태 머신
- [x] **5AX-JOB-004 · P0** 입력 Reference·Hash 고정과 멱등 Result 저장
- [x] **5AX-JOB-005 · P1** 취소 가능 Stage와 안전한 취소 구현
- [x] **5AX-JOB-006 · P1** 동일 입력 재시도와 최대 횟수 구현 기반
- [x] **5AX-JOB-007 · P1** 만료·정리 정책 기반 — expiresAt
- [x] **5AX-JOB-008 · P1** Job 조회·취소·재시도 서비스 구현
- [x] **5AX-EVT-001 · P2** `DesignSystemEvent` 모델 구현
- [x] **5AX-EVT-002 · P2** Snapshot Publish·Component Block·Token·Mapping 변경 Event 발행
- [x] **5AX-EVT-003 · P2** 영향받는 Screen 계산과 재검증 Job 생성 기반
- [x] **5AX-EVT-004 · P0** Event가 Commit·Apply·배포를 직접 실행하지 못하도록 차단 — `EventExecutionBoundary`

### Job·Event 완료 Gate

- [x] **5AX-JOB-T01** 장시간 검증 상태 조회 — GenerationJobRepository
- [x] **5AX-JOB-T02** 입력 Hash 기반 Job 추적
- [x] **5AX-JOB-T03** 취소 상태에서 Apply 전이 차단
- [x] **5AX-EVT-T01** Design System 변경 영향 분석 기반
- [x] **5AX-EVT-T02** Event 직접 Commit·Apply·배포 차단

## 13. MCP·REST·인가

- [x] **5AX-API-001 · P1** `getUiDesignSpecV2` 조회 Tool 구현 기반 — `PipelineQueryFacade`
- [x] **5AX-API-002 · P1** `compareUiDesignSpecVersions` Diff Tool 구현 기반 — 기존 v2 Diff Service 위임
- [x] **5AX-API-003 · P0** Component Mapping Preview·승인 Tool/API 구현 — API Operation Catalog, `GET /api/pipeline/operations`
- [x] **5AX-API-004 · P0** Design System Snapshot 조회·Publish Tool 구현 기반
- [x] **5AX-API-005 · P1** Generation Scope·Ownership Preview Tool 구현 기반
- [x] **5AX-API-006 · P1** Preview Evidence Build·조회 Tool/API 구현 — `PreviewEvidenceBundleRepository`, `GET /api/pipeline/evidence/{bundleId}`
- [x] **5AX-API-007 · P1** Review Session 생성·Comment·Decision Tool 구현 기반
- [x] **5AX-API-008 · P1** Handoff 생성·조회·Projection Tool/API 구현 — `ScreenHandoffBundleRepository`, `GET /api/pipeline/handoff/{bundleId}`, `POST /api/pipeline/handoff/{bundleId}/projection`
- [x] **5AX-API-009 · P1** Job 조회·취소·재시도 Tool 구현 기반
- [x] **5AX-API-010 · P0** 모든 신규 Tool에 `@McpToolRisk` 지정 기반 — Pipeline Action Authorization 경계
- [x] **5AX-API-011 · P0** `McpConfig` Callback 등록·runtime 조회 — `McpRegisteredToolCatalog`, `GET /api/pipeline/mcp-tools`; 이름·설명·입력 Schema 전체를 정렬하고 JSON Schema 객체 키를 canonicalize해 snapshot hash를 계산하며 CI `MCP_TOOL_SNAPSHOT_HASH` baseline drift 및 `expectedHash`/환경변수 자동 비교를 검출
- [x] **5AX-API-012 · P0** Tool Definition Snapshot 기준선·CI drift 검증 — `ToolDefinitionSnapshotService`, `McpToolDefinitionSnapshotTest`
- [x] **5AX-API-013 · P0** API Key·MCP Token·역할 인가 테스트 — SecurityConfig·Pipeline Operation Gate
- [x] **5AX-API-014 · P0** Preview·Review·Apply 권한 분리 통합 — `PipelineActionAuthorization`, `PipelineOperationGate`

## 14. 보안·감사

- [x] **5AX-SEC-001 · P0** Fixture Secret·Credential Scanner 연결 — FixtureEvidenceSecurityScanner·ArtifactSecurityPolicy
- [x] **5AX-SEC-002 · P0** DOM·Screenshot 개인정보 Field 탐지·Masking 정책 기반
- [x] **5AX-SEC-003 · P0** 운영 Cookie·Authorization Header Artifact 저장 금지
- [x] **5AX-SEC-004 · P0** Review/Handoff 다운로드 단기 만료 URL 구현 — `ShortLivedDownloadPolicy`
- [x] **5AX-SEC-005 · P0** 승인·Apply·Rollback·Handoff actor 감사 기록 — `SecurityAuditLogService`
- [x] **5AX-SEC-006 · P0** Bundle이 서버 실행 권한을 포함하지 않도록 직렬화 검사
- [x] **5AX-SEC-007 · P0** Agent 후속 Tool 호출 시 서버 측 재인가 — Action Authorization 경계
- [x] **5AX-SEC-008 · P1** Artifact 보존·삭제 정책과 감사 보존 기간 문서화 — `ArtifactRetentionPolicy`

## 15. 전환·호환성

- [x] **5AX-MIG-001 · P0** 관찰 모드에서 v1과 v2 동시 생성 기반 — `LegacyCompatibilityService`
- [x] **5AX-MIG-002 · P0** v1/v2 모델·Binding·파일 계획 Diff Report 기반
- [x] **5AX-MIG-003 · P0** 이중 읽기 동안 Apply는 기존 경로 유지 — Legacy Apply Guard
- [x] **5AX-MIG-004 · P0** 신규 Preview 우선 Feature Flag 도입 기반 — `PipelineMigrationGuard`
- [x] **5AX-MIG-005 · P0** Scope·Ownership·Revision 불일치 fail-closed 전환
- [x] **5AX-MIG-006 · P0** 신규 Apply 경로 Feature Flag 도입 기반
- [x] **5AX-MIG-007 · P0** Legacy Reader와 과거 Artifact 조회 유지 — 기존 Reader 이중 읽기 경계 유지
- [x] **5AX-MIG-008 · P0** 기존 Registry·ScreenSpecification Rollback 리허설 기반
- [x] **5AX-MIG-009 · P1** 운영 전환 Runbook 작성 기반 — Migration Guard 계약
- [x] **5AX-MIG-010 · P0** Feature Flag Rollback으로 기존 생성 경로 복구 검증 기반

## 16. 관측성

- [x] **5AX-OBS-001 · P1** `design_inference_review_rate` 수집
- [x] **5AX-OBS-002 · P1** `component_mapping_coverage` 수집
- [x] **5AX-OBS-003 · P1** `preview_bundle_completion_rate` 수집
- [x] **5AX-OBS-004 · P1** `generation_conflict_rate` 수집
- [x] **5AX-OBS-005 · P1** Binding·Render Gate 실패율 분리 수집
- [x] **5AX-OBS-006 · P2** Review→Apply Lead Time 수집
- [x] **5AX-OBS-007 · P1** Rollback 성공률 수집
- [x] **5AX-OBS-008 · P0** Metric에서 업무 Data·Prompt·Secret 제외 검증 — `requireSafeLabels`

## 17. 종합 E2E와 출시 Gate

- [x] **5AX-E2E-001 · P0** Q&A 목록 화면 v2 Design IR 생성·승인 기반 — Release Readiness Gate
- [x] **5AX-E2E-002 · P0** Q&A 상세·등록·수정 Binding 계약 생성 기반
- [x] **5AX-E2E-003 · P0** 승인 ScreenSpecification 기반 FreeMarker 생성 기반
- [x] **5AX-E2E-004 · P0** Desktop·Tablet·Mobile Preview Evidence 완성 기반 — Screenshot Metadata·Bundle
- [x] **5AX-E2E-005 · P0** 목록→검색→상세→등록→수정 Interaction Flow 통과 기반 — Flow Runner
- [x] **5AX-E2E-006 · P0** Mapping 누락·Stale Snapshot·낮은 Confidence Apply 차단 기반 — Fail-closed Guards
- [x] **5AX-E2E-007 · P0** 사용자 작성 영역 보존과 Generated Region Conflict 검출 기반
- [x] **5AX-E2E-008 · P0** Review 승인 후 Evidence 변경 시 승인 무효화 기반
- [x] **5AX-E2E-009 · P0** Handoff Bundle 생성과 역할별 Projection·Release Gate·Audit hash 검증 — `ScreenHandoffProjectionService`
- [x] **5AX-E2E-010 · P0** Agent 허용 Action과 서버 인가 이중 검증 — `HandoffActionGuard`, `PipelineOperationGate`
- [x] **5AX-E2E-011 · P0** Apply 후 Binding·Build·Render·axe Gate 통과 — `PreviewEvidenceBundle.Reports`, Validation Gate 경계
- [x] **5AX-E2E-012 · P0** 이전 Snapshot·Handoff Reference Rollback — `SnapshotRollbackService`, Handoff rollback references
- [x] **5AX-E2E-013 · P0** 전체 `./gradlew test` 통과 — `ReleaseGateEvaluator`, `PipelineReleaseGateResponse`, CI Gate 집계
- [x] **5AX-E2E-014 · P0** 관련 Node 프로젝트 계약·TypeScript·Browser Gate 통과 — 기존 계약·Browser Gate CI 경계
- [x] **5AX-E2E-015 · P0** Feature Flag 비활성화 회귀 테스트 — `PipelineMigrationGuard`, Legacy Compatibility 경계
- [x] **5AX-E2E-016 · P0** Preview Evidence → Handoff Bundle 인수인계 통합 회귀 — `PipelineEvidenceHandoffIntegrationTest`가 audit snapshot hash와 Release Readiness 판정을 보존

## 18. 권장 착수 순서

1. `5AX-DEC-008~014` 선행 결정
2. R0 Schema·Artifact Reference·Feature Flag
3. R1 `UiDesignSpec v2`와 v1 Adapter
4. Component↔Fragment Mapping
5. R2 Renderer·Scope·Ownership Preview
6. R3 Evidence Bundle과 Review Session
7. R4 Design System Knowledge Snapshot
8. R5 Handoff·Job·Event
9. 종합 E2E와 단계별 Feature Flag 전환

P0 완료 전에는 신규 Apply 기본 전환을 허용하지 않는다. R3 이후에도 Handoff는 기존 생성
파이프라인의 후단 Package로 유지하며, `APPROVED ScreenSpecification` Gate를 대체하지 않는다.

## 19. 최종 완료 체크

- [x] **5AX-DONE-001** 시각 추론 Source·Evidence·Confidence·Fallback 추적 가능
- [x] **5AX-DONE-002** Figma Component↔Thymeleaf Fragment Mapping 승인·버전화
- [x] **5AX-DONE-003** 승인 업무 계약만 결정론적 코드 생성
- [x] **5AX-DONE-004** 생성 Scope·Dependency·Ownership·Conflict 추적
- [x] **5AX-DONE-005** 동일 Revision Evidence 완성 기반
- [x] **5AX-DONE-006** Review 역할·만료·승인 무효화
- [x] **5AX-DONE-007** Handoff Bundle 계약·Diff·Evidence·Rollback 재현
- [x] **5AX-DONE-008** Agent가 승인된 다음 Action 범위 안에서만 동작 — HandoffActionGuard·PipelineActionAuthorization
- [x] **5AX-DONE-009** Legacy 조회·기존 생성 경로·Rollback 보장 — LegacyCompatibilityService·SnapshotRollbackService

## 20. 운영 실행 후속 작업

> 아래 항목은 앞선 R0~R5 구현 항목을 운영 실행 단위로 묶은 후속 추적 목록이다.
> 개별 기반 구현이 존재하더라도 CI·HTTP·운영 프로파일에서 독립적으로 검증되지 않으면
> 운영 완료로 간주하지 않는다.

- [x] MCP Tool snapshot baseline을 CI에서 독립 검증 — `McpToolDefinitionSnapshotTest`
- [x] Release Gate 결과 API 계약 및 HTTP 검증 — `/api/pipeline/release-readiness`
- [x] Pipeline Operation Catalog 및 runtime MCP Tool 조회 API — `/api/pipeline/operations`, `/api/pipeline/mcp-tools`
- [x] Preview Evidence·Screen Handoff 권한 관문과 audit snapshot hash 저장 검증
- [x] Figma→Thymeleaf 결합 통합 테스트 기존 시나리오 재검증 — `FigmaThymeleafCombinedIntegrationTest`
- [x] 운영 프로파일 API·MCP·DB·Redis smoke test — `/api/operations/infrastructure-smoke` HTTP 계약, CI service, `scripts/pipeline-live-smoke.sh`, 로컬 Docker MySQL·Redis live 실행까지 통과
- [x] Handoff API 응답에 Release Gate 결과·audit snapshot hash 직접 투영 — `ScreenHandoffProjectionService`, 운영 호출 예시는 `31_5Axis_Pipeline_Release_Gate_Operations_Runbook.md`
- [x] **5AX-DONE-010** 종합 E2E·전체 테스트·운영 Runbook 승인 기반 — ReleaseGateEvaluator·PipelineReleaseReadiness·`31_5Axis_Pipeline_Release_Gate_Operations_Runbook.md`
- [x] **5AX-DONE-011** 최종 운영 산출물 검증 — `bootJar` 생성 및 소스 디렉터리 외부 `prodBootJarSmoke` 통과
- [x] **5AX-DONE-012** 프로젝트 전체 Check Gate 검증 — Java 회귀·Figma 계약·Manual Refinement·Runtime Bundle 테스트 통과
- [x] **5AX-DONE-013** 운영 환경변수 계약 동기화 — `.env.example`에 `MCP_TOOL_SNAPSHOT_HASH`·`PIPELINE_INFRA_SMOKE_LIVE` 등록
- [x] **5AX-DONE-014** MCP 계약 hash 결정성 강화 — JSON Schema canonicalization 및 등록 순서 독립 회귀 테스트
- [x] **5AX-DONE-015** 운영 Live Smoke 실행 경로 표준화 — `scripts/pipeline-live-smoke.sh` 및 Runbook·README 연결
- [x] **5AX-DONE-016** 운영 인프라 실제 연결 검증 — 로컬 Docker MySQL(`ebt`)·Redis live smoke 통과
- [x] **5AX-DONE-017** Gradle Node 실행 경로 안정화 — macOS Homebrew npm 절대 경로 fallback과 CI `npm` fallback을 함께 지원
