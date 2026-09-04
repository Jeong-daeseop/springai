# 픽셀 재현(V2_APPLY) 뒷단 — 항목별 구현 계획

> 작성일: 2026-09-04
> 관련: `docs/tool-reference/픽셀재현_V2_APPLY_뒷단_완성_필요항목.md` (필요 항목 개요), `docs/tool-reference/V2_APPLY_디자인제시_구현요청_처리방안_검토.md`
> 전제: 서버 `app.pipeline-evolution.mode`는 현재 `V2_PREVIEW` (`application.yaml:122`, 2026-09-04 로컬 롤백)

---

## 0. 두 단계로 나눈다

작업을 **Phase A / Phase B**로 분리한다. 이유: 조사 결과 `UiDesignSpecV1ToV2Adapter`가 만드는 v2 스펙은 **`componentRef`가 전부 null**(`UiDesignSpecV1ToV2Adapter.java:37-57` — `SemanticNode`의 `logicalType` String만 채우고 `componentRef`는 안 세팅). 따라서:

- **Phase A** — v1 분석 → v1→v2 어댑터 → 아티팩트 저장 → `createFromV2` 연결. `RequiredComponentMappingApplyGate`가 순회할 `componentRef`가 0개라 **컴포넌트 대응표 없이도 V2_APPLY가 통과**한다. 결과: V2_APPLY에서 디자인 참조 CRUD 생성이 가능해지지만, **반영되는 신호는 여전히 archetype+enum+컬럼**(V2_PREVIEW와 동일 다이제스트). 픽셀 재현은 아직 아님. **도메인 데이터 불필요, 결정론적.** 배관을 검증한다.
- **Phase B** — Figma 분석을 `FigmaUiDesignSpecV2Mapper`로 태워 실제 `componentRef`가 있는 v2 스펙을 만들고, `thymeleaf-krds` 컴포넌트 대응표를 채우고, CRUD 템플릿이 그 결과를 실제로 렌더하게 한다. **Figma 디자인 라이브러리의 컴포넌트 세트 키 + eGov 마크업 대응 데이터가 필요.** 여기서부터 진짜 픽셀 재현.

Phase A를 먼저 끝내 배관을 확정하고, Phase B는 B2 데이터가 준비되는 대로 착수한다.

---

# Phase A — V2_APPLY 경로 정상화 (도메인 데이터 불필요)

## A1. v1 분석 → v2 스펙 생성 + 아티팩트 저장 + `createFromV2` 연결

**목표**: `designReferenceId`(로컬 이미지/캡처/Figma 무관)로 만든 화면명세에 `uiDesignSpecReference`가 붙고, 그 참조 아티팩트가 catalog에 active로 존재하게 한다.

**대상 파일**
| 파일 | 변경 |
|---|---|
| `service/GenerationDesignContextService.java:47-52` | `designReferenceId` 분기에서 `screenSpecificationService.create(...)`(v1) 대신: `analysis.uiSpec()`(v1) → `UiDesignSpecV1ToV2Adapter.adapt(specId, v1, source)` → v2 아티팩트 저장 → `screenSpecificationService.createFromV2(database, tableName, screenName, featureType, v2spec, listColumns, detailColumns)` |
| **신규** `service/UiDesignSpecV2ArtifactWriter.java` (또는 `DesignArtifactService`에 `saveUiDesignSpecV2(...)` 추가) | v2 스펙 JSON을 `ArtifactStorePort` + `ArtifactCatalogPort`에 `artifactType="UI_DESIGN_SPEC_V2"`, `artifactId=uiSpec.specId()`, `contentHash=uiSpec.contentHash()`, mediaType `application/json`으로 저장. `UiDesignSpecArtifactReader.read()`가 되읽을 수 있는 형태여야 함 (`UiDesignSpecArtifactReader.java:71` `readV2` 참고) |
| `service/GenerationDesignContextService.java` 생성자 | `UiDesignSpecV1ToV2Adapter`, `UiDesignSpecV2ArtifactWriter` 주입 추가 |
| `tools/DesignReferenceTool.java` `createScreenSpecification` (74-92행) | 동일하게 v2 경로로 전환 — 또는 `ScreenSpecificationService`에 "v1 분석 id → v2 저장 후 createFromV2" 헬퍼를 두고 두 호출자가 공용 |

**결정 필요**
1. `UiDesignSpecV2.Source` 채우기 — `DesignAnalysisResult`의 출처(`FIGMA`/`IMAGE`/`WEB_CAPTURE`)와 `sourceRevision`(Figma는 node revision, 이미지는 sourceHash)을 어떻게 매핑할지. `UiDesignSpecArtifactReader.java:113-139`의 `Source` 생성 로직을 역참조.
2. `specId` — `DesignAnalysisResult.analysisId`를 재사용할지, 새 UUID를 발급할지. 재사용 시 재분석 캐시와 충돌 여부 확인.
3. `createFromV2`가 내부에서 `v2Projection.project(uiSpec, featureType)`로 v1로 되투영해 assembler에 넘김 — v1→v2→v1 왕복이 손실 없이 되는지 (`UiDesignSpecV2ToV1ProjectionTest` 확인, 필요 시 라운드트립 테스트 추가).

**테스트**
- `GenerationDesignContextServiceTest` — `designReferenceId` 지정 시: 반환 spec의 `uiDesignSpecReference() != null`, 그 아티팩트가 catalog에서 `findById`로 조회되고 `requireActiveExact` 통과.
- `UiDesignSpecV2ArtifactWriter` 단위 테스트 — 저장→`UiDesignSpecArtifactReader.read()` 왕복.

## A2. V2_PREVIEW 회귀 방지

**리스크**: 지금 V2_PREVIEW에서도 `validateArtifactReferences()`가 `readsV2Artifacts()==true`라 `uiDesignSpecReference`가 있으면 **검증한다**(`GenerationDesignContextService.java:78-90`). A1이 아티팩트를 실제로 active 상태로 안 만들면 **지금 잘 도는 V2_PREVIEW 디자인 참조 경로가 깨진다.**

**확인 항목**
- A1 아티팩트 저장이 `DesignContextArtifactReferenceValidator.requireActiveExact`의 active 판정(상태·해시·revision)을 만족하는지 — `DesignContextArtifactReferenceValidator.java:25` 정독.
- `RequiredComponentMappingApplyGate`는 V2_PREVIEW에서 `if (!properties.usesV2Apply()) return List.of()`로 조기 종료 → 영향 없음 (`RequiredComponentMappingApplyGate.java:45`).
- 디자인 참조 **없이** 하는 순수 스키마 생성은 `resolve()`가 `null` 반환 → 경로 미변경.

**테스트**
- 기존 `CrudModelFactoryTest`, `RestMcpWorkflowCrossE2ETest`, `GenerationBaselineFixtureTest` 그대로 통과.
- V2_PREVIEW + `designReferenceId`로 `buildFullCrudPrompt` 성공(이 세션에서 미검증이던 경로) 통합 테스트 추가.

## A3. V2_APPLY 통과 확인

**테스트**
- `@TestPropertySource("app.pipeline-evolution.mode=V2_APPLY")` + `designReferenceId` → `buildFullCrudPrompt` 성공, 11개 파일 생성.
- `RequiredComponentMappingApplyGate.requireForApply` 호출 시 `spec.nodes()`의 `componentRef`가 0개 → `required` 맵 empty → `List.of()` 반환 → 통과.
- 승인된 `screenSpecificationId` 경로도 동일 확인.

**Phase A 완료 정의**: V2_APPLY에서 디자인 참조 CRUD 생성이 예외 없이 완료되고, V2_PREVIEW 기존 동작 회귀 없음. (픽셀 재현은 아직 없음 — Phase B.)

---

# Phase B — 실제 픽셀 재현 (Figma 라이브러리 데이터 필요)

## B1. Figma 분석 → 진짜 `componentRef` 있는 v2 스펙

**목표**: 출처가 Figma일 때 v1→v2 어댑터 대신 `FigmaUiDesignSpecV2Mapper`를 태워 `SemanticNode.componentRef`(logicalType + componentSetKey + mappingRef)가 채워진 v2 스펙을 만든다.

**대상 파일**
| 파일 | 변경 |
|---|---|
| `service/DesignReferenceAnalysisService.java:174` `analyzeFigmaAndSave` | 현재 `figmaApiClient.fetchNode` → v1 UiDesignSpec 생성. 여기서 `FigmaNodeDocument`를 `FigmaUiDesignSpecV2Mapper.map(...)`에도 태워 v2 스펙을 함께 만들고 `DesignAnalysisResult`에 실어두거나 별도 저장 |
| `service/GenerationDesignContextService.java` (A1 분기) | 출처가 `FIGMA`면 Figma v2 스펙 사용, 아니면 A1의 v1→v2 어댑터 |

**결정 필요**
1. `FigmaUiDesignSpecV2Mapper`가 `componentSetKey`를 Figma node의 무엇에서 뽑는지 (COMPONENT_SET node id / `componentId` / mainComponent name) — `FigmaUiDesignSpecV2Mapper.java` `node(...)` 메서드(42행 부근) 정독 필요.
2. `logicalType` 부여 규칙 — Figma 컴포넌트 이름/타입 → `button` / `text-input` / `select` / `date-input` / `table` / `pagination` 등 표준 logicalType 매핑. 이 매핑도 어딘가 정의 필요(설정 파일 또는 mapper 내부 규칙).
3. Figma 파일 키 화이트리스트(`FIGMA_ALLOWED_FILE_KEYS`) — 실제 KRDS 컴포넌트 라이브러리 파일 키 추가.

## B2. `thymeleaf-krds` 컴포넌트 대응표 시드 + 승인  ← 도메인 데이터 필요

**목표**: `RequiredComponentMappingApplyGate.repository.findApproved(logicalType, componentSetKey, "thymeleaf-krds")`가 조회할 승인된 `DesignCodeComponentMapping` 행을 채운다.

**`DesignCodeComponentMapping` 필드** (`model/designsystem/DesignCodeComponentMapping.java:12-28`)
```
mappingId, version, status(APPROVED), contentHash,
logicalType,                 // "button" 등
figmaComponentSetKey,        // 실제 Figma 라이브러리 컴포넌트 세트 키
thymeleafFragment,           // 렌더할 마크업 (fragment 정의)
propertyMappings[]           // {figmaProperty, fragmentParameter} — Figma variant → fragment 파라미터
slotMappings[]               // {figmaSlot, fragmentSlot}
supportedRendererProfiles[]  // ["thymeleaf-krds"]
sourceRevision, approvedBy, approvedAt
```

**대상 컴포넌트 종류(logicalType)** — 최소 세트
| logicalType | eGov 대응 (`styles.css` 클래스 기준) |
|---|---|
| `button-primary` / `button-secondary` / `button-negative` | `krds-btn primary/secondary/negative medium egov-btn` |
| `text-input` | `krds-input medium egov-control` |
| `select` (공통코드) | `krds-select` (검토) |
| `date-input` | 날짜 input (검토) |
| `checkbox` / `radio` | (검토) |
| `data-table` | `tbl data` |
| `form-table` | `tbl col egov-form-table` |
| `pagination` | 페이징 프래그먼트 |
| `search-form` | `egov-search-form` |

**수단 (택1)**
- (a) DB 마이그레이션 시드 `resources/db/migration/V__seed_thymeleaf_krds_component_mappings.sql` — 정적. 스키마: `V14__design_code_component_mapping.sql` 참고.
- (b) 신규 MCP 도구 `registerComponentMapping(...)` + `approveComponentMapping(mappingId)` — 세션에서 등록. `DesignCodeComponentMappingRepository.saveImmutable` + `DesignCodeComponentMappingApprovalService.approve`(46행) 위임.

**결정/입력 필요 (사용자·디자이너)**
- KRDS Figma 라이브러리에서 각 컴포넌트 세트의 **실제 키**
- 각 컴포넌트의 **Thymeleaf fragment 마크업** (파라미터·슬롯 포함)
- Figma variant 속성명 ↔ fragment 파라미터명 대응

## B3. CRUD 템플릿이 `designComponents` 소비

**목표**: `CrudTemplateModel.designComponents`(현재 `CrudTemplateRenderer.java:244`가 모델에 넣지만 템플릿 미사용)를 실제 렌더에 반영.

**`DesignComponentRenderInput` 필드** (`model/designsystem/DesignComponentRenderInput.java:8-18`)
```
mappingId, mappingVersion, logicalType, figmaComponentSetKey,
thymeleafFragment,   // 삽입할 마크업
rendererProfile, sourceRevision, contentHash
```

**대상 파일**
| 파일 | 변경 |
|---|---|
| `templates/crud/thymeleaf-detail-body.html.ftl` / `thymeleaf-regist-body.html.ftl` / `thymeleaf-updt-body.html.ftl` / `thymeleaf-list-body.html.ftl` | 필드/버튼 렌더 지점에서, 매칭되는 `designComponents` 항목이 있으면 그 `thymeleafFragment`를 출력, 없으면 현행 기본 마크업(fallback) |
| `service/CrudTemplateRenderer.java` | 필드 ↔ renderInput 매칭 인덱스(`logicalType` 기준 Map)를 모델에 추가로 넣어 템플릿이 O(1) 조회 |

**결정 필요**
1. 필드-컴포넌트 매칭 키 — 필드의 무엇(추론된 위젯 타입? `UiFieldRole`?)이 `renderInput.logicalType`과 매칭되는가.
2. fragment 삽입 방식 — `thymeleafFragment`가 Thymeleaf `th:fragment` 정의면 생성 HTML에서 `th:replace`로 부르고, 그 fragment 파일도 프로젝트에 생성해야 함. 아니면 FTL이 fragment 문자열을 그대로 인라인 출력.
3. `propertyMappings` 적용 — Figma variant 값(예: size=medium)을 fragment 파라미터로 어떻게 전달.

## B4. 검증 관문 갱신

- B2 대응표 확정 후 `website-figma-contract/renderer-profile-thymeleaf-krds-v1.json`의 `componentMappingVersion` 갱신. `contentHash`는 JSON 필드와 `model/renderer/RendererProfileReference.java:14` `DEFAULT_CONTENT_HASH` 두 곳 동기화.
- B3 템플릿 변경 시 `templateSetHash` 재계산 — `TemplateSetFingerprintService`가 `templates/crud/*.ftl` 28종 SHA-256; `renderer-profile-thymeleaf-krds-v1.json` + `TemplateSetFingerprintServiceTest` golden 상수 동시 갱신. (2단 폼 구현 때와 동일 절차)
- `RequiredComponentMappingApplyGate` 통합 테스트 — 승인 매핑 존재 시 통과, 누락 시 `승인 Mapping 누락`.

## B5. 모드 전환

- `application.yaml:122` `${APP_PIPELINE_EVOLUTION_MODE:V2_PREVIEW}` → `:V2_APPLY` 복원. Phase A·B 전부 완료 + 전체 테스트 통과 후.

---

## 규모·순서 요약

| 단계 | 성격 | 공수 | 도메인 데이터 |
|---|---|---|---|
| A1 v2 생성·저장·연결 | 조립 + 신규 writer | 중 | 불필요 |
| A2 V2_PREVIEW 회귀 방지 | 검증·테스트 | 소~중 | 불필요 |
| A3 V2_APPLY 통과 확인 | 테스트 | 소 | 불필요 |
| B1 Figma → 진짜 componentRef | 조립 + mapper 규칙 | 중 | Figma 파일 키 |
| B2 컴포넌트 대응표 | **데이터 입력 + 도구/시드** | **대** | **KRDS Figma 라이브러리 + fragment 마크업** |
| B3 템플릿 소비 | FTL 신규 작성 | **대** | B2 결과 |
| B4 검증 갱신 | 해시·버전 동기화 | 중 | — |
| B5 모드 전환 | 설정 1줄 | 소 | — |

**권장 실행 순서**: A1 → A2 → A3 (Phase A 완료·검증) → B2 데이터 확보 → B1 → B3 → B4 → B5.

**착수 가능**: ~~Phase A는 지금 바로.~~ 아래 "진행 상태" 참조. Phase B는 B2 데이터(사용자/디자이너 제공) 대기.

---

## 진행 상태 (2026-09-04)

**결정: Phase A 보류. Phase B 데이터 확보 후 A+B를 함께 진행.**

### A1 착수 중 발견 — 계획 대비 규모 증가

A1 구현에 들어가 실제 아티팩트 영속화·참조 검증 계약을 추적한 결과, **공유 V2 인프라의 contentHash 규약 불일치**를 고쳐야 함이 드러났다:

| 요구 | 근거 |
|---|---|
| 저장소는 content-addressed — `artifact.contentHash == sha256(저장 JSON 바이트)` | `UiDesignSpecArtifactReader.readAndVerify` (`:147`) |
| 참조 검증은 `참조.contentHash(= v2.contentHash() 레코드 필드) == artifact.contentHash` | `DesignContextArtifactReferenceValidator.requireActiveExact` (`:38-47`) + `VersionedArtifactReferenceValidator.requireExact` |
| 그런데 v2 producer 둘 다 contentHash를 **v2 JSON이 아니라 원본 입력**에 대해 계산 | `UiDesignSpecV1ToV2Adapter.java:81` (`sha256([specId, source, legacy])`), `FigmaUiDesignSpecV2Mapper.java:213` (`sha256([fileKey, nodeId, fileVersion, featureType, document])`) |

→ `v2.contentHash()` ≠ `sha256(serialize(v2))` **구조적으로**. `createFromV2`가 참조를 `v2.contentHash()`에서 파생하므로(`ScreenSpecificationService.java:96-99`) content-addressed 저장소와 호환 불가. `createFromV2` 프로덕션 호출자가 0이라 이 불일치가 통합된 적이 없다. `ScreenSpecificationV2IntegrationTest`는 repository mock + `contentHash="a".repeat(64)` 가짜라 드러나지 않았다.

**해결에 필요한 변경**: `ScreenSpecificationService.createFromV2`가 `v2.contentHash()` 파생 대신 **영속화된 아티팩트의 실제 `VersionedArtifactReference`를 인자로 받도록** 변경 + `ScreenSpecificationV2IntegrationTest` 3개 기대값 수정(`contentHash().isEqualTo("a".repeat(64))` 포함) + 아티팩트 writer 신규 + `GenerationDesignContextService` 연결. = 공유 V2 인프라 변경. 계획서 A1 "중" 추정보다 큼.

### 왜 보류했나

- Phase A 단독은 사용자 가치 0 — V2_APPLY가 안 죽을 뿐 출력은 V2_PREVIEW와 동일(픽셀 재현 없음)
- 그 대가로 공유 해시 인프라를 고치고, 방금 2043 테스트로 검증한 V2_PREVIEW 경로에 회귀 리스크를 얹어야 함
- `createFromV2` 변경은 **실제 Figma 컴포넌트 데이터(Phase B)로 함께 검증**하는 게 안전

### 재개 조건 (트리거)

**B2 데이터가 준비되면 A1(해시 규약 수정 포함) + B 전체를 한 묶음으로 진행.** 필요한 B2 데이터:

1. KRDS Figma 디자인 라이브러리의 **컴포넌트 세트 키** (버튼 primary/secondary/negative, 텍스트 입력, 셀렉트, 날짜 입력, 체크박스/라디오, 데이터 테이블, 폼 테이블, 페이지네이션, 검색폼 등)
2. 각 컴포넌트의 **Thymeleaf fragment 마크업** (파라미터·슬롯 포함)
3. Figma variant 속성명 ↔ fragment 파라미터명 대응
4. 해당 Figma 파일 키를 `FIGMA_ALLOWED_FILE_KEYS`에 추가

---

## 부록 — 조사 근거

| 사실 | 위치 |
|---|---|
| `resolve()` 주입 지점 | `GenerationDesignContextService.java:38-66` |
| `createFromV2` 존재·미호출, v2→v1 되투영 | `ScreenSpecificationService.java:82-110` |
| v1→v2 어댑터가 `componentRef` 안 채움 | `UiDesignSpecV1ToV2Adapter.java:37-57` (`SemanticNode` 3번째 인자는 `logicalType` String) |
| `SemanticNode.componentRef` / `ComponentReference` 구조 | `UiDesignSpecV2.java:83-159` |
| Figma → v2 mapper | `FigmaUiDesignSpecV2Mapper.java:27` `map(reference, source, featureType)` |
| 아티팩트 읽기 (writer 없음) | `UiDesignSpecArtifactReader.java:28-80` (`ArtifactCatalogPort`+`ArtifactStorePort`) |
| V2 아티팩트 저장 코드 부재 | `grep 'UI_DESIGN_SPEC_V2' src/main` → 참조 생성만(`ScreenSpecificationService.java:98`), 저장 0건 |
| 컴포넌트 대응표 모델·승인 | `DesignCodeComponentMapping.java:12-63`, `DesignCodeComponentMappingApprovalService.java:46`, `DesignCodeComponentMappingRepository.java:23,58` |
| 대응표 실데이터 부재 | `website-figma-contract/fixtures/valid-design-code-component-mapping-v1.json` (예시만), `tools/`에 등록 도구 없음 |
| 게이트 | `RequiredComponentMappingApplyGate.java:43-96`, `GenerationDesignContextService.java:69-92` |
| 템플릿 미소비 | `grep 'designComponents' src/main/resources/templates/` → 0건, 모델은 `CrudTemplateRenderer.java:244` |
| 렌더러 프로파일 pin | `renderer-profile-thymeleaf-krds-v1.json` (`componentMappingVersion`, `templateSetHash`), `TemplateSetFingerprintService.java` |
