# 픽셀 재현(V2_APPLY) 뒷단 완성 — 필요 항목 정리

> 작성일: 2026-09-04
> 목적: Figma/캡처 디자인 참조가 CRUD 생성에 "픽셀 수준"으로 반영되게 하려면 무엇을 연결·채워야 하는지 정리
> 관련: `docs/tool-reference/V2_APPLY_디자인제시_구현요청_처리방안_검토.md`

---

## 0. 왜 이 문서가 필요한가

디자인 참조 입력 3종 — 로컬 이미지/PDF(`analyzeDesignReference`), Figma 프레임(`analyzeFigmaReference`), 실행 화면 캡처(`captureWebPage` → `analyzeCapturedDesign`) — 은 분석 단계에서 담는 정보량이 다르다(Figma·캡처는 좌표·컴포넌트·변수까지 결정론적으로, 로컬 이미지는 비전 LLM 추측). 그러나 **`buildFullCrudPrompt`가 실제로 읽는 지점에서는 셋 다 같은 다이제스트로 축소된다**:

- 읽음: `archetype`, `layoutDensity`, `formColumnLayout`, `actionPlacement`, `searchPanelPlacement`, `pages()`의 컬럼 선택
- 안 읽음: `ScreenSpecification.componentGeometry` / `componentStyles` / `tokens` (저장은 됨), Figma 컴포넌트 참조, 캡처 CSS

이 풍부한 정보가 흘러갈 소비 지점(픽셀 재현 = V2_APPLY의 컴포넌트 매핑 경로)이 **연결돼 있지 않기 때문**이다. 따라서 현재 상태에서는 입력 3종이 CRUD 생성 목적상 사실상 동급이고, 스키마 생성 + `listColumns`/`detailColumns` + 밀도 파라미터 대비 추가 가치가 작다.

---

## 1. "연결(배선)"이 무슨 뜻인가

부품(코드 모듈)은 대부분 만들어져 있는데 **서로를 호출하지 않는** 상태다. 창고에 엔진·바퀴·차체가 다 있는데 조립을 안 해서 차가 안 굴러가는 것과 같다. 아래는 "새로 만들기"보다 "조립 + 빈 표 채우기"에 가깝다.

**이미 존재하는 부품:**

| 부품 | 위치 | 상태 |
|---|---|---|
| 정밀 스펙 모델 `UiDesignSpecV2` | `model/design/UiDesignSpecV2.java` | 있음 |
| Figma → 정밀 스펙 변환기 `FigmaUiDesignSpecV2Mapper` | `service/FigmaUiDesignSpecV2Mapper.java` | 있음, **analyze→spec 흐름에서 미호출** |
| v1 → v2 어댑터 `UiDesignSpecV1ToV2Adapter` | `service/UiDesignSpecV1ToV2Adapter.java` | 있음 |
| 정밀 스펙 저장·조회 `UiDesignSpecArtifactReader` | `service/UiDesignSpecArtifactReader.java` | 있음 |
| 화면명세에 정밀 스펙 물리는 `createFromV2()` | `service/ScreenSpecificationService.java:82` | 있음, **프로덕션 호출자 0곳** |
| 컴포넌트 대응표 테이블 | `resources/db/migration/V14__design_code_component_mapping.sql` | 있음 |
| 대응표 저장·조회 `DesignCodeComponentMappingRepository` | `mapper/DesignCodeComponentMappingRepository.java` | 있음 |
| 대응표 승인 서비스 `DesignCodeComponentMappingApprovalService` | `service/designsystem/...` | 있음 |
| 대응표 스키마·샘플 | `website-figma-contract/design-code-component-mapping-v1.schema.json`, `.../fixtures/valid-design-code-component-mapping-v1.json` | 스키마·예시만, **실데이터 행 0** |
| 정밀 적용 게이트 `RequiredComponentMappingApplyGate` | `service/designsystem/RequiredComponentMappingApplyGate.java` | 있음, V2_APPLY에서만 동작 |
| 렌더러 프로파일 (컴포넌트 매핑 버전 pin) | `website-figma-contract/renderer-profile-thymeleaf-krds-v1.json` (`componentMappingVersion: "1.0"`) | 있음 |

---

## 2. 완성에 필요한 5가지

### 항목 1 — Figma 분석을 "정밀 버전"으로 만들어 저장하고, 화면명세에 물리기

- **지금**: `analyzeFigmaReference` → `DesignAnalysisResult.uiSpec()` = **v1**(필드 역할·액션·밀도만). `createScreenSpecification`도 v1 `create()`만 호출.
- **필요**:
  1. Figma 분석 시 `FigmaUiDesignSpecV2Mapper`로 정밀 스펙(v2)도 생성 — 레이어 좌표, 컴포넌트 종류(`componentRef`: logicalType + componentSetKey), 색상/치수 변수 포함
  2. 그 v2 스펙을 아티팩트로 영속화 (`UiDesignSpecArtifactReader`가 읽어들일 수 있는 형태)
  3. `createScreenSpecification`(또는 신규 도구)이 `ScreenSpecificationService.createFromV2(...)`를 타서 `ScreenSpecification.uiDesignSpecReference`가 그 아티팩트를 가리키게 함
- **막는 지점**: `GenerationDesignContextService.validateArtifactReferences()` — V2_APPLY에서 `uiDesignSpecReference == null`이면 `DESIGN_EVIDENCE_MISSING`으로 항상 차단

### 항목 2 — "Figma 컴포넌트 = eGov 컴포넌트" 대응표 채우기

- **지금**: 대응표 테이블·저장소·승인 서비스는 있으나 **`thymeleaf-krds` 프로파일용 실데이터 행이 0**. 세션에서 행을 등록할 MCP 도구도 없음(`tools/`에 없음).
- **필요**:
  1. `thymeleaf-krds` 렌더러가 쓰는 컴포넌트 종류마다(버튼 primary/secondary/negative, 텍스트 입력, 셀렉트, 날짜 입력, 테이블, 페이징 등 대략 10~20종) 매핑 행 등록:
     - Figma 컴포넌트 세트 키 ↔ eGov 마크업/클래스(`krds-btn primary`, `krds-input medium`, …) ↔ variant 값 매핑
  2. 각 행을 `DesignCodeComponentMappingApprovalService`로 승인 상태로 확정
  3. (권장) 세션에서 등록·승인할 MCP 도구 신설 또는 시드 마이그레이션/픽스처 추가
- **막는 지점**: `RequiredComponentMappingApplyGate.requireForApply()` — 스펙에 등장하는 각 `componentRef`마다 `(logicalType, componentSetKey, "thymeleaf-krds")` 승인 행이 없으면 `승인 Mapping 누락`으로 차단

### 항목 3 — 화면 생성기가 정밀 정보를 실제 마크업에 반영

- **지금**: `CrudTemplateRenderer.java:244`가 `designComponents`를 템플릿 모델에 넣지만, **CRUD FTL 템플릿(`templates/crud/*.ftl`)에서 `designComponents` 사용 0회**. 받기만 하고 안 씀.
- **필요**: `RequiredComponentMappingApplyGate`가 돌려준 `List<DesignComponentRenderInput>`를 템플릿이 실제로 소비하도록 수정 —
  - 필드 위젯 타입 결정("Figma에서 date-picker → 날짜 input", "select → 공통코드 셀렉트")
  - 버튼 variant 반영("secondary였으니 secondary 클래스")
  - 필요 시 컴포넌트별 부분 템플릿 분리
- `CrudModelFactory.withDesignComponents(model, inputs)` 경로는 이미 있으므로, 소비 측(FTL)만 작성

### 항목 4 — "정밀 모드" 전환

- **지금**: `application.yaml:122` `app.pipeline-evolution.mode` = `V2_PREVIEW`(관찰만). 2026-09-04 로컬 롤백으로 낮춰둔 상태.
- **필요**: 항목 1~3 완료 후 `V2_APPLY`로 되돌림. 이전에 올리면 게이트에서 무조건 실패.

### 항목 5 — 검증 관문 갱신

- **지금**: 정밀 모드에서 `RendererProfileValidator` 등 게이트가 추가로 엄격해짐. 렌더러 프로파일이 `componentMappingVersion`을 pin.
- **필요**:
  1. 항목 2 대응표 확정 후 `renderer-profile-thymeleaf-krds-v1.json`의 `componentMappingVersion` 갱신(+ `contentHash` 두 참조 지점 동기화: JSON 필드와 `RendererProfileReference.DEFAULT_CONTENT_HASH`)
  2. 템플릿 변경 시 `templateSetHash` 재계산 — `TemplateSetFingerprintService`가 `templates/crud/*.ftl` 28종을 SHA-256; `renderer-profile-thymeleaf-krds-v1.json` + `TemplateSetFingerprintServiceTest` golden 상수 동시 갱신
  3. `RequiredComponentMappingApplyGate` 관련 통합 테스트 추가

---

## 3. 규모·순서

| 항목 | 성격 | 손 |
|---|---|---|
| 1. 정밀 스펙 생성·저장 연결 | 조립(기존 부품 호출) | 중 |
| 2. 대응표 채우기 + 등록 도구 | 데이터 입력 + 도구 신설 | **대** |
| 3. 템플릿 반영 | FTL 신규 작성 | **대** |
| 4. 모드 전환 | 설정 1줄 | 소 |
| 5. 검증 관문 갱신 | 해시·버전 동기화 + 테스트 | 중 |

- 전부 **springai 서버 개발 작업**. eGov 프로젝트 쪽에서 할 수 있는 것 아님.
- **하나만 빠져도 현재처럼 게이트에서 막힌다** — 항목 1~3은 전부 있어야 첫 픽셀 재현 화면이 나옴.
- 권장 순서: 2(대응표) 착수와 병행해 1(정밀 스펙 연결) → 3(템플릿) → 5(검증) → 4(모드 전환).

---

## 4. 그때까지의 현실적 지침

픽셀 재현 뒷단이 완성되기 전에는:

- 디자인 참조 입력 3종은 CRUD 생성 목적상 동급 (결과 동일)
- "목록 화면 참조로 CRUD 생성"은 어느 입력이든 결과가 같고, **스키마 생성 + `listColumns`/`detailColumns` + 밀도 파라미터**로 대체 가능
- 디자인 참조 경로를 쓸 이유는 (a) 목업에서 컬럼·밀도 손 전사 대신 자동 추출 (b) `REVIEW_REQUIRED` 매핑 확인 관문 (c) 디자인→명세→코드 감사 추적 — 이 3가지가 필요할 때
- 완성 후에는 Figma가 유일하게 의미 있는 입력(컴포넌트 참조·기하 필요), 로컬 이미지는 fallback, URL 캡처는 사후 충실도 검증(`DesignFidelityTool`)용으로 역할이 갈림

---

## 부록 — 근거 (파일:라인)

| 사실 | 위치 |
|---|---|
| `createFromV2` 프로덕션 호출자 0 | `grep -rn 'createFromV2' src/main` → 정의 3건, 호출 0건 |
| `FigmaUiDesignSpecV2Mapper` analyze→spec 미호출 | `grep -rn 'FigmaUiDesignSpecV2Mapper' src/main` → 정의만 |
| CRUD FTL `designComponents` 미사용 | `grep -rn 'designComponents' src/main/resources/templates/` → 0건 |
| 모델은 전달함 | `CrudTemplateRenderer.java:244` |
| 컴포넌트 대응표 실데이터 없음 | `website-figma-contract/fixtures/valid-design-code-component-mapping-v1.json` (예시만), `tools/`에 등록 도구 없음 |
| V2_APPLY 아티팩트 필수 게이트 | `GenerationDesignContextService.java:69-92` |
| 컴포넌트 매핑 게이트 | `RequiredComponentMappingApplyGate.java:43-96` |
| 서버 모드 | `application.yaml:122` (현재 `V2_PREVIEW`) |
| 템플릿 세트 해시 pin | `TemplateSetFingerprintService.java` + `renderer-profile-thymeleaf-krds-v1.json` |
