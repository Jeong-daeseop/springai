# 픽셀 재현(V2_APPLY) 뒷단 — 항목별 구현 계획

> 작성일: 2026-09-04
> 관련: `docs/tool-reference/픽셀재현_V2_APPLY_뒷단_완성_필요항목.md` (필요 항목 개요), `docs/tool-reference/V2_APPLY_디자인제시_구현요청_처리방안_검토.md`
> 전제: 서버 `app.pipeline-evolution.mode` 기본값 `V2_APPLY` (`application.yaml`, 2026-09-04 B5 복원). `APP_PIPELINE_EVOLUTION_MODE=V2_PREVIEW`로 낮추면 디자인 참조 없는 순수 스키마 Thymeleaf CRUD 생성 가능.

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
- ~~KRDS Figma 라이브러리에서 각 컴포넌트 세트의 **실제 키**~~ → B1 pragmatic으로 논리 키 `"krds:" + logicalType` 사용
- 각 컴포넌트의 **Thymeleaf fragment 마크업** (파라미터·슬롯 포함) — B3에서 작성
- Figma variant 속성명 ↔ fragment 파라미터명 대응 — B2 시드에 최소 매핑 반영

### B2 시드 구현 완료 (2026-09-04)

| 파일 | 내용 |
|---|---|
| **신규** `service/designsystem/ThymeleafKrdsComponentMappingSeeder.java` | `ApplicationRunner`, `@ConditionalOnProperty("app.design-system.component-mapping-seed.enabled"=true)`. 6종 `DesignCodeComponentMapping`(button/text-input/select/date-input/data-table/pagination)을 **APPROVED로 직접 `saveImmutable`**(대화형 approve 우회). `figmaComponentSetKey = "krds:" + logicalType`(B1과 일치), `contentHash = hashService.compute(...)`, `supportedRendererProfiles = ["thymeleaf-krds"]`, `thymeleafFragment = "components/krds-<x> :: <name>"`. `repository.findVersion(id,"1.0")` 있으면 skip(멱등) |

**최소 계약**: `propertyMappings`는 전부 `required:false` + defaultValue(예: `Type→variant`(primary), `Size→size`(medium), `Label→label`). `fixtureModel`은 canonical envelope `{schemaVersion:"1.0", figmaProperties:{...}, figmaSlots:{}, contextVariables:{}}`. → `ComponentFixtureModelAdapter.adapt`·`DesignComponentRenderInputService.resolve`(속성/변형/슬롯 3 resolver) 전부 ERROR 없이 통과 확인.

**검증**: `ThymeleafKrdsComponentMappingSeederTest`(2 — 계약 자기정합, 6종 render input 해석). 전체 `./gradlew test` 통과.

**활성화**: `app.design-system.component-mapping-seed.enabled=true` (기본 비활성 — DB 있는 환경에서만).

### B2 인벤토리 (2026-09-04 조사, KRDS_v1.0.0 Community)

- `fileKey`: `6fcm04dwSEH2IUizZfaZCj`
- `libraryKey`: `lk-d23666ef86e768bedd5db3bae3fc7a19dfe377e321e758b0085d804d0a733281a3148081a64f16b42143690fdeb2c8f67cde44fd79ac8e707afb19bc03fd0dfe`
- 매핑 대상 라이브러리 결정: **KRDS_v1.0.0 (Community)** (대안 "FTC 정부 포털 Design System"은 미채택)

| eGov logicalType | KRDS 컴포넌트 | `figmaComponentSetKey` (componentKey) | assetType |
|---|---|---|---|
| `button` (primary/secondary/negative) | `button` | `104d078e1895f788a91cfc8c42073fe6bc85e77a` | component_set |
| `button-text` | `button_text` | `517f19a6f3981e46254deb24091b2240605dc74e` | component_set |
| `button-link` | `button_link` | `a92ad2622cafa6582137dc010d3b310ecc38c667` | component_set |
| `text-input` | `text_input` | `e2643d821fe50580f30fd5d0378ad7e486543da6` | component_set |
| `select` (공통코드) | `selectbox` | `69ac27b250e29b249f408e70a7f72f6422206d66` | component_set |
| `date-input` | `date_input` | `91e9276f499af0a7b06d94e514e40324fe66172a` | component_set |
| `checkbox` | `checkbox` | `68dac165ea6ccd639de85b07d6b49aadc0705d63` | component_set |
| `radio` | `radio_button` | `02eb1de395a6a48136b752469f3a9fceaf9fc7a4` | component_set |
| `data-table` (목록) | `table` | `194a582dc105671593355376a992d90ab42ca7ce` | component_set |
| `pagination` | `pagination__atomic` | `a786257623cc432e95c4dbaa80cd56741d2a753d` | component_set |
| `input-message` (에러/힌트) | `input_message__atomic` | `4804570b7748935a184c044fa669dff7099bb692` | component_set |

**색상 토큰**: `mode` 컬렉션, `variableSetKey` `6c0577907fbad9e7f69d8714b3ef5b76d84279ee` — `color/input/border`, `color/input/surface`, `color/input/border-active`, `color/input/border-error`, `color/input/border-disabled`, `color/text/primary`, `color/text/danger`, `color/text/basic` 등

**미확인**: `search-form`(KRDS 전용 컴포넌트 없음 — 조합으로 구성), eGov `tbl col` 폼 테이블 대응(KRDS `table`은 데이터 테이블 지향 — 폼 테이블은 매핑 대상에서 제외하고 기존 마크업 유지 검토)

### B2 variant 명명 규칙 (Badge 컴포넌트에서 확인)

KRDS Figma 컴포넌트는 **영문 PascalCase variant 속성명 + 소문자 값**을 쓴다:
- 속성 축: `Type`, `Color`, `State`, `Size` (+ 컴포넌트별 추가 축, 예: Badge의 `Number`)
- 값 예: `Type` = `outline`/`solid`/`solid-pastel` · `Color` = `primary`/`secondary`/`tertiary`/`point`/`danger`/`warning`/`success`/`info` · `State` = `default`/`disabled`/`hover`/... · `Size` = `large`/`medium`/`small`
- variant 인스턴스 이름 형식: `Type=outline, Color=secondary, State=default, Size=large`

→ `DesignCodeComponentMapping.propertyMappings[].figmaProperty`에 이 속성명을 그대로 사용.

### B2 매핑표 (KRDS 공식 문서 krds.go.kr 확인, 2026-09-04)

| logicalType | figmaComponentSetKey | KRDS base 클래스 | Size | State | 변형(variant) | KRDS 문서 |
|---|---|---|---|---|---|---|
| `button` | `104d078e1895f788a91cfc8c42073fe6bc85e77a` | `.krds-btn` | `.xsmall .small .medium .large .xlarge` | (disabled) | 색상 `.primary .secondary .tertiary` / `.text` / `.icon` / `.icon.border` | component_05_02 |
| `text-input` | `e2643d821fe50580f30fd5d0378ad7e486543da6` | `.krds-input` | `.small .medium .large` | `.is-error .is-success` (+focus/disabled) | label·help·placeholder·아이콘버튼(pw토글/삭제) | component_09_03 |
| `select` | `69ac27b250e29b249f408e70a7f72f6422206d66` | **`.krds-form-select`** | `.small .medium .large` | `.completed .is-error` (+disabled) | — | component_06_03 |
| `date-input` | `91e9276f499af0a7b06d94e514e40324fe66172a` | `.form-group`/`.form-tit`/**`.calendar-input`**/`.form-btn-datepicker`/`.form-hint` | — | (disabled) | 단일 / 기간(입력필드 2개) / 연·월·일 분리 / 달력아이콘 유무 | component_09_01 |
| `data-table` | `194a582dc105671593355376a992d90ab42ca7ce` | `.krds-table-wrap` + `.tbl` | — | — | 기본형/스크롤형(헤더고정)/반응형, 정렬버튼, 빈값 `-` | component_04_11 |
| `pagination` | `a786257623cc432e95c4dbaa80cd56741d2a753d` | `.krds-pagination` | — | `.disabled` | PC/모바일, `.page-navi.prev/.next`, `.page-link`, `.link-dot` | component_03_06 |

**⚠️ 현재 eGov 생성 코드 ↔ KRDS 실제 클래스 불일치 (B3에서 정합 필요)**
| eGov 생성기가 내는 것 | KRDS 실제 | 조치 |
|---|---|---|
| `krds-btn negative` (삭제 버튼) | KRDS엔 `negative` 없음 — `primary/secondary/tertiary`뿐 | B3에서 `secondary` 또는 커스텀 확장 유지 결정 |
| `krds-select` | `krds-form-select` | 템플릿 클래스 교체 검토 |
| `krds-input medium egov-control` | `krds-input` + `.medium` ✓ (일치) | 유지 |
| 날짜 필드도 `<input class="krds-input">` | KRDS date-input은 `.calendar-input` + `.form-btn-datepicker` 별도 계열 | B3에서 date logicalType 감지 시 다른 fragment |

**variant 축(Figma) → propertyMappings**: `button`은 Figma `Color`(primary/…)·`Size`(large/…)·`State`, 나머지는 `Size`·`State` 중심. Figma 인스턴스명 규칙 `Type=…, Color=…, State=…, Size=…` (Badge에서 확인). 정확한 축은 각 컴포넌트 node-id URL로 `get_design_context` 시 최종 확정.

### Figma 조사 한계 (2026-09-04)

`get_metadata`(nodeId 없음)가 KRDS Community 파일의 페이지를 **5개만 반환**(Badge/Link/Design style/Getting Started/Main menu — 페이징 미지원, 잘림). button/text_input/selectbox/date_input/table/pagination은 각자 전용 페이지에 있으나 그 페이지 ID를 발견할 방법이 없다. `search_design_system`은 `componentKey`만 주고 `nodeId`를 안 준다. `get_design_context`/`get_metadata`는 nodeId 필수.

확인된 variant 명명 규칙(Badge, Main menu 페이지에서):
- 속성명: `Type`, `Color`, `Size`, `State` (일부 소문자 `state=`, `banner=`), 값은 소문자
- 예: `Type=outline, Color=secondary, State=default, Size=large` / `State=hover` / `More depth=Yes`

**진행 방식**: 정확한 `figmaProperty` 이름은 사용자가 Figma에서 컴포넌트 우클릭 → Copy link로 node-id URL을 주면 `get_design_context`로 확정. 그때까지는 위 규칙(`Type`/`Color`/`Size`/`State`)으로 매핑 작성하고 B3 구현 중 보정. `figmaProperty`가 틀려도 해당 variant가 해석 안 되어 default로 떨어질 뿐(멱등적 실패 아님)이라 후속 보정 가능.

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

---

### B3 fragment 라이브러리 (2026-09-04 작성 — KRDS 문서 기반)

#### `thymeleafFragment` 계약 (`ThymeleafFragmentContractValidator` 확인)

- 형식: **`"<templates 상대경로> :: <fragmentName>"`** — 정적만(`${}` `~{}` `@{}` 등 금지), 경로 `[A-Za-z0-9_./-]`(`.html` 생략 가능, `..` 금지), 이름 `[A-Za-z_][A-Za-z0-9_-]*`
- 대상 프로젝트의 `src/main/resources/templates/<경로>.html`(또는 WAR `src/main/webapp/WEB-INF/templates/...`)에 파일이 **정확히 1개** 존재해야 함
- 그 파일에 `th:fragment="<이름>(p1, p2, ...)"` 선언이 정확히 1개, **파라미터 목록 = `propertyMappings[].fragmentParameter` ∪ `slotMappings[].fragmentSlot` (완전 일치)**
- → fragment 파일은 대상 프로젝트에 **생성 산출물**로 넣어야 함. `generateThymeleafLayout`에 `templates/components/krds-*.html` 생성 단계 추가 (신규 B3 작업).

#### 파라미터 규약

fragment 파라미터는 3부류. 셋 다 `propertyMappings`에 항목이 있어야 검증 통과:
- **디자인 variant** (`variant`, `size`, `state`): `figmaProperty` = 실제 Figma 속성명(`Type`/`Color`/`Size`/`State` — 아래 표는 KRDS 문서 기준 추정, Figma node-id로 확정 필요), `valueMapping`으로 Figma 값→KRDS 클래스 토큰 변환
- **바인딩** (`path`, `id`): `figmaProperty` = 합성명(`@path` 등), `required:false` — Figma 소스 없음, CRUD FTL이 필드에서 채움
- **콘텐츠 슬롯** (`label`, `hint`): `slotMappings` 또는 `propertyMappings`(figmaProperty=`Label` 등 텍스트 속성)

#### 6종 fragment (초안)

**1. `components/krds-button.html :: button`** — params `(label, variant, size, buttonType)`
```html
<button th:fragment="button(label, variant, size, buttonType)"
        th:type="${buttonType}"
        th:class="'krds-btn ' + ${variant} + ' ' + ${size} + ' egov-btn'"
        th:text="${label}">버튼</button>
```
mappings: `Label`→`label`(slot, default "버튼") · `Type`→`variant`(valueMap: `solid→primary, outline→secondary, text→tertiary`) · `Size`→`size`(valueMap: `xsmall→xsmall … xlarge→xlarge`, default `medium`) · `@buttonType`→`buttonType`(required:false, default `button`)
비고: KRDS엔 `negative` 없음 → 삭제 버튼은 `variant=secondary` + 별도 확인 클래스(예: `egov-btn-danger`)는 CRUD FTL이 `class`에 추가.

**2. `components/krds-text-input.html :: textInput`** — params `(path, label, size, state, placeholder, maxlength, required)`
```html
<div th:fragment="textInput(path, label, size, state, placeholder, maxlength, required)" class="krds-form-group">
  <label th:for="${path}" class="krds-form-label" th:text="${label}"><span th:if="${required}" class="egov-required-mark">*</span></label>
  <input type="text" th:id="${path}" th:name="${path}" th:field="*{__${path}__}"
         th:class="'krds-input ' + ${size} + (${state} != null ? ' ' + ${state} : '') + ' egov-control'"
         th:placeholder="${placeholder}" th:maxlength="${maxlength}"/>
  <p th:if="${#fields.hasErrors('__${path}__')}" class="krds-form-hint is-error" th:errors="*{__${path}__}"></p>
</div>
```
mappings: `Size`→`size`(`small/medium/large`, default `medium`) · `State`→`state`(valueMap: `error→is-error, success→is-success`, default null) · `@path`→`path`(req:false) · `Label`→`label`(slot) · `@placeholder`/`@maxlength`/`@required`→동명(req:false)
비고: `th:field="*{__${path}__}"` = 사전 계산 표현식(preprocessing). CRUD FTL이 `path`에 자바 필드명 전달.

**3. `components/krds-select.html :: select`** — params `(path, label, size, state, options, required)`
```html
<div th:fragment="select(path, label, size, state, options, required)" class="krds-form-group">
  <label th:for="${path}" class="krds-form-label" th:text="${label}"></label>
  <select th:id="${path}" th:name="${path}" th:field="*{__${path}__}"
          th:class="'krds-form-select ' + ${size} + (${state} != null ? ' ' + ${state} : '')">
    <option value="">선택</option>
    <option th:each="opt : ${options}" th:value="${opt.code}" th:text="${opt.codeNm}"></option>
  </select>
</div>
```
mappings: `Size`→`size` · `State`→`state`(valueMap: `error→is-error`) · `@path`/`@options`/`@required`(req:false) · `Label`→`label`(slot)
비고: base 클래스 `krds-form-select` (eGov 생성기의 `krds-select` 아님). `options` = 공통코드 리스트.

**4. `components/krds-date-input.html :: dateInput`** — params `(path, label, mode, required)`
```html
<div th:fragment="dateInput(path, label, mode, required)" class="form-group">
  <span class="form-tit" th:text="${label}"></span>
  <div th:if="${mode == 'single'}">
    <input type="text" class="calendar-input" th:id="${path}" th:name="${path}" th:field="*{__${path}__}" placeholder="YYYY-MM-DD"/>
    <button type="button" class="form-btn-datepicker" aria-label="날짜 선택"></button>
  </div>
  <div th:if="${mode == 'period'}">
    <input type="text" class="calendar-input" th:name="${path} + 'From'" placeholder="시작일"/>
    <span> ~ </span>
    <input type="text" class="calendar-input" th:name="${path} + 'To'" placeholder="종료일"/>
  </div>
  <p class="form-hint">YYYY-MM-DD 형식</p>
</div>
```
mappings: `Type`→`mode`(valueMap: `single→single, period→period`, default `single`) · `@path`/`@label`/`@required`(req:false)
비고: KRDS date-input은 `.calendar-input`+`.form-btn-datepicker` 별도 계열(`krds-input` 아님). `mode` 감지는 CRUD FTL이 필드명/타입으로 결정.

**5. `components/krds-data-table.html :: dataTable`** — params `(columns, rows, emptyText)`
```html
<div th:fragment="dataTable(columns, rows, emptyText)" class="krds-table-wrap">
  <table class="tbl data">
    <thead><tr><th th:each="col : ${columns}" scope="col" th:text="${col.label}"></th></tr></thead>
    <tbody>
      <tr th:if="${#lists.isEmpty(rows)}"><td th:colspan="${#lists.size(columns)}" th:text="${emptyText} ?: '데이터가 없습니다.'"></td></tr>
      <tr th:each="row : ${rows}"><td th:each="col : ${columns}" th:text="${row[col.name]} ?: '-'"></td></tr>
    </tbody>
  </table>
</div>
```
mappings: `@columns`/`@rows`/`@emptyText`(req:false) — 전부 바인딩. variant 없음(목록 표는 구조 고정).
비고: 목록 화면 전용. 상세/등록/수정의 `tbl col` 폼 테이블은 매핑 대상 아님(2단 폼 로직이 담당).

**6. `components/krds-pagination.html :: pagination`** — params `(paginationInfo, linkUrl)`
```html
<div th:fragment="pagination(paginationInfo, linkUrl)" class="krds-pagination">
  <a class="page-navi prev" th:classappend="${paginationInfo.currentPageNo == 1} ? ' disabled'"
     th:href="@{${linkUrl}(pageIndex=${paginationInfo.currentPageNo - 1})}">이전</a>
  <a th:each="p : ${#numbers.sequence(paginationInfo.firstPageNoOnPageList, paginationInfo.lastPageNoOnPageList)}"
     class="page-link" th:classappend="${p == paginationInfo.currentPageNo} ? ' active'"
     th:href="@{${linkUrl}(pageIndex=${p})}" th:text="${p}"></a>
  <a class="page-navi next" th:classappend="${paginationInfo.currentPageNo == paginationInfo.totalPageCount} ? ' disabled'"
     th:href="@{${linkUrl}(pageIndex=${paginationInfo.currentPageNo + 1})}">다음</a>
</div>
```
mappings: `@paginationInfo`/`@linkUrl`(req:false) — 전부 바인딩. eGovFrame `PaginationInfo` 연동.

#### B3 열린 결정

1. **fragment 파일을 대상 프로젝트에 넣는 방법** — `generateThymeleafLayout` 확장(`templates/components/` 6종 생성) vs 신규 `generateComponentFragments` 도구 vs 공유 classpath fragment.
2. **`figmaProperty` 실제 이름 확정** — 위 mapping의 `Type`/`Color`/`Size`/`State`는 KRDS 문서 기반 추정. 각 컴포넌트 node-id URL로 `get_design_context` 시 확정 필요. 특히 button이 `Type`인지 `Color`인지(Badge는 `Type`+`Color` 둘 다 있었음).
3. **바인딩 파라미터 처리** — `ComponentPropertyParameterResolver` 확인 결과: `required:false` + `defaultValue:null`이면 값이 null → **결과 `fragmentParameters`에 아예 안 담김**(에러 아님). 검증기(`ThymeleafFragmentContractValidator`)는 propertyMappings에 `fragmentParameter` 항목만 있으면 통과. → **바인딩 파라미터는 propertyMappings에 `required:false` 항목으로 등록(검증 통과용)하고, 실제 값은 CRUD FTL의 `th:replace` 호출부가 채운다.** 디자인 variant(`variant/size/state`)만 Figma에서 해석되어 render input으로 전달.
4. **`th:field` preprocessing** (`*{__${path}__}`) 가 대상 Thymeleaf 버전에서 동작하는지 검증.

## B4. 검증 관문 갱신

- B2 대응표 확정 후 `website-figma-contract/renderer-profile-thymeleaf-krds-v1.json`의 `componentMappingVersion` 갱신. `contentHash`는 JSON 필드와 `model/renderer/RendererProfileReference.java:14` `DEFAULT_CONTENT_HASH` 두 곳 동기화.
- B3 템플릿 변경 시 `templateSetHash` 재계산 — `TemplateSetFingerprintService`가 `templates/crud/*.ftl` 28종 SHA-256; `renderer-profile-thymeleaf-krds-v1.json` + `TemplateSetFingerprintServiceTest` golden 상수 동시 갱신. (2단 폼 구현 때와 동일 절차)
- `RequiredComponentMappingApplyGate` 통합 테스트 — 승인 매핑 존재 시 통과, 누락 시 `승인 Mapping 누락`.

## B5. 모드 전환

- `application.yaml` `${APP_PIPELINE_EVOLUTION_MODE:V2_PREVIEW}` → `:V2_APPLY` 복원. Phase A·B 전부 완료 + 전체 테스트 통과 후.

### B5 구현 완료 (2026-09-04)

**변경**: `application.yaml`의 `pipeline-evolution.mode` 기본값 `V2_PREVIEW` → `V2_APPLY`. 롤백 주석(`c884107`)을 "B5 복원" 주석으로 교체 — 이 모드에서 Thymeleaf CRUD 생성은 `designReferenceId`/`screenSpecificationId`(승인 화면명세)가 필요하고, 순수 스키마 생성만 하려면 `APP_PIPELINE_EVOLUTION_MODE=V2_PREVIEW`로 낮춘다는 안내 포함.

**V2_APPLY 시맨틱(의도된 정책, 버그 아님)**:
- 디자인 참조 없는 Thymeleaf CRUD 생성 → `CrudGenerationPlanner`가 DB 조회 전 `MAPPING_BLOCKED`로 reject + `analyzeFigmaReference`→`createScreenSpecification`→retry 안내(`CrudGenerationPlannerMigrationGuardTest.v2ApplyModeBlocksThymeleafGenerationWithoutDesignReference`).
- `screenSpecificationId`(APPROVED) 있으면 게이트 통과 → 이후 A1(아티팩트 참조 검증) → B4(`RequiredComponentMappingApplyGate`) → B3(fragment 소비) 경로.
- **JSP CRUD 생성은 영향 없음**(`RequiredComponentMappingApplyGate` 대상 아님, `v2ApplyModeDoesNotAffectJspGenerationWithoutDesignReference`).
- 디자인 참조 **없이** Thymeleaf를 쓰려면 env로 `V2_PREVIEW` 지정.

**검증**: 전체 `./gradlew test` — 2062건 통과, 0 실패(17 skipped). V2_APPLY 민감 경로(`CrudGenerationPlannerMigrationGuardTest`, `RequiredComponentMappingApplyGate*Test`, `GenerationDesignContextArtifactGateTest` 등)는 모두 모드를 명시 설정하므로 기본값 전환 영향 없음. `PipelineEvolutionPropertiesTest`는 Java 기본값(`DISABLED`)만 검사(yaml 무관).

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

## 진행 상태

### A1 구현 완료 (2026-09-04)

**Phase A(A1) 구현·검증 완료.** 보류했던 결정을 뒤집고 A1을 먼저 진행했다.

**변경 파일**
| 파일 | 내용 |
|---|---|
| **신규** `service/UiDesignSpecV2ArtifactWriter.java` | `UiDesignSpecV2` → `UI_DESIGN_SPEC_V2` Artifact 영속화. `artifactId = spec.specId()`(readV2 ID 검사 만족), `contentHash = sha256(JSON 바이트)`(content-addressed). stage/commit/save 멱등. 반환: content-addressed `VersionedArtifactReference` |
| `service/ScreenSpecificationService.java` | `createFromV2(..., VersionedArtifactReference designRef, listCols, detailCols)` 오버로드 추가 — 파생 대신 **영속화된 참조를 그대로 고정**. 기존 7-arg 오버로드는 파생 참조로 새 오버로드에 위임(하위 호환, `ScreenSpecificationV2IntegrationTest` 3건 유지) |
| `service/GenerationDesignContextService.java` | `UiDesignSpecV1ToV2Adapter` + `UiDesignSpecV2ArtifactWriter` 주입(nullable). `resolve()`의 `designReferenceId` 분기에서 **V2_PREVIEW/V2_APPLY일 때만** v1 분석 → v1→v2 어댑터 → writer 영속화 → `createFromV2(designRef)` 경로. DISABLED/OBSERVE/DUAL_READ는 기존 v1 `create()` 유지(마이그레이션 가드 충돌 회피) |

**해시 규약 불일치 해결**: `UiDesignSpecV2.contentHash()` 레코드 필드(원본 입력 해시)는 그대로 두고, 화면명세 참조에는 **저장 아티팩트의 실제 contentHash**(=sha256(JSON))를 쓴다. `createFromV2`가 더 이상 `uiSpec.contentHash()`에서 참조를 파생하지 않음 → content-addressed 저장소와 정합.

**활성화 범위**: v1→v2 어댑터는 `componentRef`를 안 채우므로(§0), V2_APPLY에서 `RequiredComponentMappingApplyGate`가 순회할 componentRef가 0개 → 컴포넌트 대응표 없이 통과. 즉 **A1만으로 V2_APPLY에서 디자인 참조 CRUD 생성이 가능해지지만, 반영 신호는 여전히 archetype+enum+컬럼**(V2_PREVIEW와 동일 다이제스트). 픽셀 재현은 Phase B(B1~B3).

**검증**: `UiDesignSpecV2ArtifactWriterTest`(2, 신규 — specId=artifactId, content-addressed hash, 멱등), `GenerationDesignContextServiceTest`(4, +2 — V2_PREVIEW v2 경로 / DISABLED v1 경로 유지), `GenerationDesignContextArtifactGateTest`(3), `ScreenSpecificationV2IntegrationTest`(3), `ScreenSpecificationServiceTest`(5), `generation.crud.*` / `CrudOrchestrationServiceTest` / `GenerationBaselineFixtureTest` / `RestMcpWorkflowCrossE2ETest` / `designsystem.*` — 총 332건 통과. 전체 `./gradlew test`도 실행.

**남음**: B1(Figma→componentRef), B2 variant 정밀, B3(fragment 파일 생성 + FTL 소비), B4(프로파일 버전), B5(모드 전환).

### B1 구현 완료 (pragmatic, 2026-09-04)

**변경**
| 파일 | 내용 |
|---|---|
| `FigmaUiDesignSpecV2Mapper` | `node()`가 `componentRef` 자리에 `null` 대신 `componentReference(raw)` 전달. COMPONENT/INSTANCE 노드 이름(정규화)을 알려진 eGov logicalType 접두사(`button`/`textinput`/`selectbox`/`dateinput`/`pagination`/`checkbox`/`radiobutton`/`table`)에 매칭 → `ComponentReference(logicalType, "krds:"+logicalType, null)`. 미매칭(레이아웃 컨테이너·텍스트)은 null → 게이트 순회 제외 |
| `GenerationDesignContextService` | `FigmaApiClient` + `FigmaUiDesignSpecV2Mapper` 주입(nullable). `toV2Spec(analysis)` 신설 — 출처가 FIGMA + Figma 재조회 가능 시 `figmaApiClient.fetchNode` → `figmaMapper.map`(정밀 v2, componentRef 있음), 아니면/실패 시 v1→v2 어댑터로 폴백(non-fatal, WARN 로그) |

**pragmatic 결정**: `figmaComponentSetKey`를 실제 Figma Component Set 키가 아니라 **`"krds:" + logicalType`** 논리 키로 사용. B2 시드가 같은 값을 쓰면 `RequiredComponentMappingApplyGate.findApproved(logicalType, componentSetKey, "thymeleaf-krds")`가 매칭된다. 더 정밀한 해석(ComponentRegistry 통합, Figma `/components` API로 실제 세트 키 해석)은 후속.

**한계**: variant(`Type`/`Size`/`State`)는 아직 안 뽑음 — `SemanticNode.componentRef`는 `logicalType`+`componentSetKey`만. Figma `componentProperties`(인스턴스 variant 값) 추출은 B3 `propertyMappings` 해석 시 필요 → 별도 확장.

**검증**: `FigmaUiDesignSpecV2MapperTest`(+1 — INSTANCE 이름별 componentRef, 미매칭 null), `GenerationDesignContextServiceTest`/`GenerationDesignContextArtifactGateTest`(생성자 오버로드 유지) 통과. 전체 `./gradlew test` 실행.

---

### B3 구현 완료 (2026-09-04)

**커밋 1 — fragment 인프라**
| 파일 | 내용 |
|---|---|
| `templates/components/krds-{button,text-input,select,date-input,data-table,pagination}.html` | 6종 정본 fragment. `ThymeleafFragmentContractValidator` 계약 통과(B2 시드 6개 Mapping과 파라미터 정합 — data-table/pagination에 `variant` 파라미터 추가) |
| `service/designsystem/KrdsComponentFragmentWriter.java` | 클래스패스 정본을 대상 프로젝트 `src/main/resources/templates/components/`(Boot) 또는 `.../webapp/WEB-INF/templates/components/`(WAR)에 멱등 복사(`ApprovedProjectWritePort` ATOMIC_APPROVED 배치) |
| `service/generation/crud/CrudComponentFragmentProcessor.java` | PRE_WRITE(priority 115, STOP). `supports` = viewType THYMELEAF && `!model.designComponents().isEmpty()` — plan 없으면 조용히 제외 |
| `CrudGenerationPlanner.processorSteps()` | 위 processor 등록 |

**커밋 2 — FTL 소비 + 공통코드 select 풀스택**
| 파일 | 내용 |
|---|---|
| `model/crud/CrudDesignComponentPlan.java` (신규) | 렌더 전용. `byLogicalType`(logicalType→render input) + `commonCodeFields`(자바명+CODE_ID). VO/Mapper/스키마 계약 무영향 |
| `model/crud/CrudTemplateModel.java` | 24번째 컴포넌트 `designComponentPlan` 추가(+호환 생성자). null이면 산출물 바이트 동일 |
| `service/CrudModelFactory.java` | `withDesignComponents(model, screenSpec, components)` — 화면명세 binding 중 `COMMON_CODE` source이면서 formFields에 남은 것만 `CommonCodeField`로. CODE_ID는 `FieldSource.codeGroup()` 있을 때만, 없으면 null(생성기가 `CHANGE_ME` 자리표시자). 테스트용 `withDesignComponents(model, components, commonCodeFields)` 오버로드 추가 |
| `service/CrudTemplateRenderer.java` | plan 있으면 `designComponentPlan`을 FTL 모델에 추가 |
| `templates/crud/thymeleaf-regist-body.html.ftl` / `thymeleaf-updt-body.html.ftl` | `<#if designComponentPlan??>` KRDS div-stack 폼(`krds-form-group` + `th:replace` fragment) / `<#else>` 기존 table 폼(바이트 동일). 필드→fragment: 공통코드+`has('select')`→select, 시간형(`LocalDate`/`LocalDateTime`/`Date`)+`has('date-input')`→date-input, `has('text-input')`→text-input, 그 외 기본 마크업. 버튼→`has('button')`→krds-button. 2단(`form-row-two-col`) 유지 |
| `templates/crud/controller.java.ftl` | plan+공통코드 있으면 `populateCommonCodes(ModelMap)` 헬퍼 + registView/updtView/오류 재렌더 경로에서 호출. `service.select<Jn>CodeList("<CODE_ID 또는 CHANGE_ME>")` → `model.addAttribute("<jn>CodeList", ...)` |
| `templates/crud/service.java.ftl` / `service-impl.java.ftl` / `mapper.java.ftl` / `mapper.xml.ftl` | `select<Jn>CodeList(String codeId)` — `SELECT CODE AS \`code\`, CODE_NM AS \`codeNm\` FROM LETTCCMMNDETAILCODE WHERE CODE_ID = #{codeId} AND USE_AT = 'Y'`(MyBatis `#{}`, 하드코딩 CODE_ID 없음 — 인자 전달). plan 없으면 미출력 |
| `renderer-profile-thymeleaf-krds-v1.json` + `TemplateSetFingerprintServiceTest` | `templateSetHash` `e70a93e3…` → `7ea765532ca55e9ea9d9f7b76b135b6b79ab32fbb058abc2c24577ba36eeea84` |

**B3 열린 결정 처리**
1. fragment 파일 배치 → `CrudComponentFragmentProcessor`(생성 파이프라인 PRE_WRITE)가 대상 프로젝트에 복사. `generateThymeleafLayout` 확장 아님(생성 시점에만 필요).
2. `figmaProperty` 실제 이름 → 미확정. variant 값은 아직 render input에 안 담기므로(B1 한계) fragment 기본값(`primary`/`medium`/…)에 의존. 후속 보정.
3. 바인딩 파라미터 → `th:replace` 호출부(FTL)가 `path`/`options`/`required` 등 채움. 계약 검증은 propertyMappings 항목만 확인.
4. `th:field` preprocessing(`*{__${path}__}`) → fragment는 `th:field="*{__${path}__}"` 사용. 대상 Thymeleaf 3.1(Spring6)에서 동작. 실기동 검증은 대상 프로젝트 생성 후 필요.

**의도적 미구현(후속)**
- **list 화면 data-table/pagination fragment 소비 안 함** — 기존 eGov 목록 마크업(행별 상세 링크, 검색 파라미터 보존 페이지네이션, 체크박스 컬럼)이 generic fragment보다 기능이 많아 교체 시 회귀. fragment 파일·시드는 유지(계약 완결성/타 화면용).
- **detail 화면** — 2단 폼 로직이 담당(계획서 §309).
- variant(`Type`/`Size`/`State`) Figma 추출 — B1 한계 그대로.

**검증**: `CrudTemplateRendererTest`(+7 — plan 유무별 regist/updt fragment 치환, 공통코드 select, controller/mapper/service 공통코드 코드젠, plan 없을 때 바이트 동일), `KrdsComponentFragmentWriterTest`(신규 — 멱등 기록 + 6종 계약 통과), `TemplateSetFingerprintServiceTest`(golden 갱신) 통과. 전체 `./gradlew test`.

---

### B4 구현 완료 (2026-09-04)

**`componentMappingVersion` — 유지(`"1.0"`), bump 안 함**
- `RendererProfile.componentMappingVersion`은 선언 필드일 뿐 **소비자 0** — `RendererProfileValidator`는 rendererType·templateEngine·viewType·status·templateSetVersion·**templateSetHash**·capability matrix·validatorProfile만 검증. `componentMappingVersion`을 읽거나 실제 시드 Mapping과 대조하는 게이트 없음(`grep componentMappingVersion` → 모델 필드·스키마·픽스처뿐).
- B2 시더가 만드는 6개 `DesignCodeComponentMapping`의 `version`이 `"1.0"`. 프로파일의 `componentMappingVersion`을 `"1.1"`로 올리면 **시드 Mapping version과 desync**되고, 그걸 잡아줄 검증도 없으므로 오히려 혼동만 는다. → `"1.0"` 유지가 정합.
- `contentHash`(JSON `8e2801b4…` ↔ `RendererProfileReference.DEFAULT_CONTENT_HASH`)는 프로파일 JSON 내용이 B4에서 안 바뀌므로(templateSetHash는 B3에서 이미 갱신) 그대로.

**`RequiredComponentMappingApplyGate` 통합 테스트 (신규)**
- `RequiredComponentMappingApplyGateSeedIntegrationTest` — `ThymeleafKrdsComponentMappingSeeder.mappings()`의 **실제 6종 시드**를 in-memory stub repository로 Gate에 태운다.
- v2 스펙 nodes 6개가 각각 `ComponentReference(logicalType, "krds:"+logicalType, null)` (B1 pragmatic 키 규약) 보유.
- ① 6종 모두 승인 → `requireForApply`가 6개 `DesignComponentRenderInput` 확정(각 `thymeleafFragment` = `components/krds-<x> :: <name>`, `rendererProfile` = `thymeleaf-krds`).
- ② `select` 시드 제외 → `RequiredComponentMappingException("승인 Mapping 누락: select/krds:select (thymeleaf-krds)")`.
- ③ `date-input`+`pagination` 제외 → 두 누락 모두 수집 후 차단.
- Mapping 내부 계약(Fixture Adapter·RenderInput 해석)은 `ThymeleafKrdsComponentMappingSeederTest`가, Gate 단위 동작(pinned ref 불일치·미지원 variant·V2_PREVIEW no-op)은 기존 `RequiredComponentMappingApplyGateTest`가 커버 — 이 테스트는 시드↔Gate end-to-end만.

**검증**: `RequiredComponentMappingApplyGateSeedIntegrationTest`(신규 3), `RequiredComponentMappingApplyGateTest`·`ThymeleafKrdsComponentMappingSeederTest`(기존 유지) 통과.

---

### (이전 기록) B1 착수 중 발견 (2026-09-04) — mapper 확장 필요

`FigmaUiDesignSpecV2Mapper`를 `resolve()`에 연결하는 것만으로는 픽셀 재현이 안 된다:

- `FigmaUiDesignSpecV2Mapper.node()` (`:83`)가 `SemanticNode`의 `componentRef` 자리에 **`null`을 전달**. `logicalType(raw)`(문자열)만 세팅하고 `ComponentReference(logicalType, componentSetKey, mappingRef)`는 안 만든다.
- → Figma mapper를 배선해도 `RequiredComponentMappingApplyGate`가 순회할 `componentRef` = 0개 → A1과 동일(다이제스트만).

**진짜 B1 작업**:
1. `FigmaUiDesignSpecV2Mapper.node()` 확장 — INSTANCE 노드에서 `raw.path("componentId")` 읽고, 컴포넌트 세트 키로 해석해 `ComponentReference` 생성 후 `SemanticNode`에 전달
2. `componentId → componentSetKey` 해석 — NODES 응답에 `componentSets` 맵이 없으면 `FigmaApiClient.getFileComponents(fileKey)`(`:368`, `GET /v1/files/{key}/components`, 이미 존재) 별도 호출
3. `FigmaApiClient.fetchNode`가 instance의 `componentId`/`componentProperties`를 응답에 포함하는지 확인(depth/필드)
4. 실제 Figma INSTANCE 노드 JSON 픽스처 + 테스트 (현재 없음 — Figma 데이터 확보 필요)

**결론**: B1은 "배선"이 아니라 Figma 노드 → 컴포넌트 참조 추출 로직 신규 구현. 실제 Figma 데이터(KRDS 컴포넌트/화면 node-id) 확보 후 진행.

---

### (이전 기록) A1 착수 중 발견 — 계획 대비 규모 증가

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
