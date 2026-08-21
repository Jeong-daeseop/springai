# Semantic Figma Design System 구현 목록

> 문서 버전: 4.24
> 작성일: 2026-07-30  
> 상태 재판정일: 2026-08-21
> 구현 기준 문서:
> - [07_Design_System_Component_Mapping_Review.md](./07_Design_System_Component_Mapping_Review.md)
> - [08_Semantic_Figma_Export_Integrated_Architecture.md](./08_Semantic_Figma_Export_Integrated_Architecture.md)
> - [09_Agent_Design_System_FigmaScreenSpec_Reference_Architecture.md](./09_Agent_Design_System_FigmaScreenSpec_Reference_Architecture.md)
> - [10_Semantic_Figma_Design_System_Impact_Analysis.md](./10_Semantic_Figma_Design_System_Impact_Analysis.md)
> - [11_Semantic_Figma_Design_System_Implementation_Plan.md](./11_Semantic_Figma_Design_System_Implementation_Plan.md)
> - [Figma_MCP_디자인_오케스트레이션_아키텍처_및_구현_명세서.md](./Figma_MCP_디자인_오케스트레이션_아키텍처_및_구현_명세서.md)

---

## 1. 문서 목적

이 문서는 설계 내용을 실제 작업 항목으로 분해한 구현 체크리스트다.

핵심 목표는 다음과 같다.

1. Spring/eGovFrame의 `ScreenSpecification`을 업무 화면 정의의 단일 기준으로 유지한다.
2. Figma 전용 표현인 `FigmaScreenSpec`을 별도 투영 모델로 생성한다.
3. 에이전트가 `DesignSystemSpec`과 Preview를 생성하되, 사람이 검토하고 Figma Library를 Publish한다.
4. Publish된 컴포넌트 키를 `ComponentRegistry`에 동기화한다.
5. 화면 생성 시 Published Component Instance를 우선 재사용하고 신규 논리 노드만 생성한다.
6. `.figpack` 기반 현재 화면 복제와 의미 기반 화면 생성을 하나의 하이브리드 흐름으로 연결한다.
7. 7가지 디자인 요청을 Spring MCP·Spring AI·Figma REST 조회·Plugin Preview/Apply 경계 안에서 제공한다.
8. JSP·Controller·VO 분석부터 `DESIGN.md`·회사 Token 적용, 반응형 Thymeleaf 생성, 빌드·렌더 검증까지 하나의 추적 가능한 Generator로 연결한다.

> 관련 후속 문서: [17_Semantic_Figma_Generation_Pipeline_Manual_Refinement_Implementation_List.md](./17_Semantic_Figma_Generation_Pipeline_Manual_Refinement_Implementation_List.md)가 이 문서(R1~R5)가 생성하는 `FigmaScreenSpec`을 baseline으로 재사용해, Figma에서 사람이 직접 조정한 속성을 승인 후 재적용하는 Manual Refinement 계층을 그 위에 추가한다.
>
> Catalog 논리 계약과 Published Registry Binding의 단일 원천화 후속 작업은
> [18_Component_Catalog_Registry_SSOT_Impact_and_Implementation_Specification.md](./18_Component_Catalog_Registry_SSOT_Impact_and_Implementation_Specification.md)와
> [19_Component_Catalog_Registry_SSOT_Implementation_List.md](./19_Component_Catalog_Registry_SSOT_Implementation_List.md)를 따른다.

---

## 2. 상태 및 우선순위 표기

### 2.1 상태

| 표기 | 의미 |
|---|---|
| `[x]` | 항목의 구현과 명시된 완료 Gate가 테스트·증적으로 검증됨 |
| `[ ]` | 미구현 |
| `[~]` | 일부 구현되었으나 목표 구조에 맞춘 보완 필요 |
| `[!]` | 선행 결정 또는 외부 조건 때문에 착수 불가 |

코드, callback 또는 기반 클래스가 존재한다는 사실만으로 `[x]`를 부여하지 않는다. 기존 세부 항목의
`[x]`는 해당 항목에 적힌 좁은 완료 기준의 증적이며 상위 통합 작업 완료를 의미하지 않는다.
재시작 복구, 공통 인증, provenance, 실제 환경 E2E처럼 상위 Gate가 남아 있으면 통합 작업은 `[~]`다.

### 2.2 우선순위

| 우선순위 | 의미 |
|---|---|
| P0 | 전체 구조 또는 안전성에 직접 영향을 주는 필수 항목 |
| P1 | 1차 운영 가능한 기능 완성에 필요한 항목 |
| P2 | 운영 효율, 확장성, 편의성 개선 항목 |

### 2.3 I-4~I-6 통합 상태 재판정

| 통합 작업 | 상태 | 현재 판정 |
|---|---:|---|
| I-4 디자인 기반 Thymeleaf HTML | `[x]` | `BindingComposer`가 LIST/FORM/DETAIL 계약을 생성하고 `th:*`·CSRF·route·authority provenance를 정적/렌더 테스트로 검증. `BindingContractAssemblerTest`, `BindingComposerTemplateEngineParityTest`, `ThymeleafBindingGenerationServiceTest` 통과 |
| I-5 Responsive·Preview·Apply·검증 | `[x]` | MySQL Operation Store 재시작 복구, 10단계 영속 `ThymeleafGenerationReport`, 원자 Apply/rollback, render/build/Chromium·axe·visual Gate와 REST↔MCP 교차 E2E 검증 완료. `ThymeleafProjectOperationRepositoryIntegrationTest`, `RestMcpWorkflowCrossE2ETest`, R6-T18~T20 증적 기준 |
| I-6 Figma MCP Tool·Plugin Apply | `[x]` | 모든 MCP callback에 공통 REQUIRED(deny-by-default) 인가 래퍼 적용, Artifact/Operation 상태 parity 및 source revision·editableNodeIds 범위 재검증을 REST·MCP 교차 테스트로 검증. `McpAuthorizingToolCallbackTest`, `FigmaDesignOperationServiceTest`, `RestMcpWorkflowCrossE2ETest` 통과 |

따라서 I-4~I-6은 위 통합 테스트 증적이 유지되는 동안 완료 상태로 관리한다. 이후 실제 운영
환경의 새로운 인증·브라우저·Figma 계약 변경이 생기면 해당 Gate만 다시 `[~]`로 낮춰 재검증한다.

---

## 3. 현재 구현 기준선

다음 항목은 신규 개발의 출발점으로 재사용한다.

| ID | 상태 | 현재 자산 | 활용 방향 |
|---|---:|---|---|
| BASE-01 | [x] | `ScreenSpecification`, `PageSpec` | 업무·코드 생성용 단일 기준 모델 유지 |
| BASE-02 | [x] | Screen Specification 검증 및 저장 기능 | 버전·상태·검증 구조 확장 |
| BASE-03 | [x] | `CaptureWebPageTool` | `.figpack` 캡처 진입점 유지 |
| BASE-04 | [x] | `WebCaptureOrchestrationService` | 캡처 후 의미 변환 오케스트레이션 확장 |
| BASE-05 | [x] | `figpack-v1.schema.json` | Reference Snapshot 계약으로 유지 |
| BASE-06 | [x] | `rendered-design-document-v1.schema.json` | 렌더 결과의 중간 계약으로 활용 |
| BASE-07 | [x] | `RenderedDesignSpecMapper` | `UiDesignSpec` 변환 단계에 재사용 |
| BASE-08 | [x] | `FigmaDesignSpecMapper` | Figma 투영 규칙 확장 후보 |
| BASE-09 | [x] | `DesignArtifactService` | JSON 및 검증 보고서 산출물 저장에 활용 |
| BASE-10 | [x] | `jsp-to-figma-plugin` | `documentKey`·`contentHash` 기반 기존 Frame 탐색·재사용과 없을 때 신규 생성 경로 구현 |
| BASE-11 | [x] | `GenerationDesignContextService` | Screen Specification 기반 코드 생성 연계 유지 |
| BASE-12 | [x] | CRUD/Board/MasterDetail 생성 흐름 | Figma 전용 식별자와 분리하여 기존 생성 기능 보존 |
| BASE-13 | [x] | `DesignReferenceTool.analyzeFigmaReference`·`DesignReferenceAnalysisService.analyzeFigma` | `create_design_from_reference`의 Figma FRAME 분석 단계 재사용 |
| BASE-14 | [x] | `DesignReferenceTool.analyzeDesignReference`·`VisionAnalysisClient` | `create_design_from_image`의 PNG/JPEG/PDF 및 OpenAI/Ollama Vision 분석 단계 재사용 |
| BASE-15 | [x] | `FigmaApiClient`·`FigmaDesignSpecMapper` | Figma Node 조회/retry와 layout·token·component 의미 매핑 확장 |
| BASE-16 | [x] | `ScreenSpecificationService.revise`·Screen Plugin MERGE/REPLACE | `modify_existing_design`의 명세 버전 갱신과 `logicalNodeId` 동기화 기반 재사용 |
| BASE-17 | [x] | `ComponentRegistryResolver` | 지정 컴포넌트 요청의 alias·replacement·Published Registry 해석 재사용 |
| BASE-18 | [~] | `FigmaScreenExportRequest.viewport`·Layout annotation | `PlatformLayoutPolicy` Schema(v1), viewport별 width/grid/gap/padding, Component Swap 계약을 추가하고 Java 정책 소비 경로를 보강. 실제 Figma Desktop에서의 grid 재계산·승인된 Swap 런타임 검증은 잔여 |
| BASE-19 | [x] | `ScreenFieldBinding`·`GenerationQueryContractFactory` | Generator Binding Contract의 field/query 기반 재사용 |
| BASE-20 | [x] | `CrudTemplateRenderer`·`BoardTemplateRenderer`·`MasterDetailTemplateRenderer` | FreeMarker 기반 Thymeleaf HTML Skeleton 생성 재사용 |
| BASE-21 | [x] | `CrudModelFactory`·`BoardModelFactory`와 Controller/Thymeleaf template | Controller Model Binding 생성 기반 재사용 |
| BASE-22 | [x] | `ThymeleafRenderValidator`·`GeneratedProjectBuildValidator`·`CodeValidatorTool` | 생성 결과 parse/render 및 허용된 Maven/Gradle 빌드 검증 재사용 |
| BASE-23 | [x] | `jsp-design-extractor`·`ProjectScannerTool`·기존 CRUD metadata 서비스 | `ControllerSourceReader`·`VoSourceReader`·Binding Contract assembler가 route, Controller method, Model attribute, VO field/validation evidence를 화면 단위로 결합 |

---

## 4. 선행 결정 목록

아래 항목이 확정되지 않으면 모델과 플러그인 구현이 서로 다른 가정을 갖게 된다.

**2026-07-30 정리**: DEC-01 이후 R0~R6 구현이 상당히 진행되면서 DEC-02~12 중 다수가
`website-figma-contract/CONTRACT_RULES.md`·`component-catalog-v1.json`과 실제 Java/Plugin
코드에 **기술 기준안으로 이미 반영**돼 있었다(체크리스트에는 그동안 `[ ]`로만 남아 있었음).
아래 표는 그 기술 기준안이 실제로 구현·테스트된 항목은 `[x]`로 승격하고, 코드로 대신할 수 없는
순수 조직 결정(DEC-08)만 `[ ]`로 남겼다. DEC-10은 FILE 우선·REST 선택 기능으로,
DEC-12는 ARCHIVE 단일 정책으로 v1 범위를 확정해 미구현 선택지를 완료 조건에서 제외했다.
DEC-13~15는 2026-07-30 명세 반영 시 기존 Spring MCP·Plugin 경계를 보존하기 위해 확정한
통합 아키텍처 결정이며, 하위 구현 항목의 완료를 의미하지는 않는다.
`[x]`로 승격한 항목도 "조직이 이 기술 기준안을 공식 승인했다"는 뜻은 아니다 — 코드가 이미 어떤
동작을 하고 있는지를 정확히 반영했을 뿐이며, 최종 승인 권한은 여전히 조직에 있다.

| ID | 우선순위 | 상태 | 결정 항목 | 완료 기준(근거) |
|---|---:|---:|---|---|
| DEC-01 | P0 | [x] | 사용할 FTC/KRDS Figma Library의 정확한 대상 | `FTC 정부 포털 Design System`, `fileKey=mVy5h1UbORVqQoBm8Wr1bT`; Team Project 이동·Library Publish·Consumer 파일 Remote Instance 생성으로 Publish 및 사용 권한 검증 완료 |
| DEC-02 | P0 | [x] | 컴포넌트 표준 명명 규칙 | `component-catalog-v1.json`의 `requiredComponents[]`가 논리명(`logicalType`, 예: `krds.button`)↔Figma Property(`figmaProperties`)↔코드 속성(`codeProperties`)을 12개 컴포넌트 전부에 대해 이미 매핑하고 JSON Schema로 강제함(`component-catalog-v1.schema.json`). 조직 Library 담당자의 최종 승인이 완료되어 운영 기준으로 확정 — 승인 요청 문서: [14_DEC02_DEC09_Component_Catalog_Approval_Request.md](./14_DEC02_DEC09_Component_Catalog_Approval_Request.md) |
| DEC-03 | P0 | [x] | `logicalNodeId` 생성 규칙 | `CONTRACT_RULES.md` §2(형식·수명주기·중복 처리·반복 행 규칙 명문화) + Java `LogicalNodeIdFactory`(세그먼트 정규식 검증) + Plugin `core.ts`의 `flattenSpec`/중복 ID 검증 + JSON Schema 세그먼트 패턴까지 3중으로 구현·테스트됨 |
| DEC-04 | P0 | [x] | 속성 소유권 정책 | `CONTRACT_RULES.md` §3("사용자 직접 수정 속성은 USER_OVERRIDE, 컴포넌트 외형은 DESIGN_SYSTEM, 업무 값·구조는 SCREEN_SPEC") + Plugin `code.ts`의 `applyOwnedProperties()`가 실제로 사용자가 바꾼 값은 재동기화 때 덮어쓰지 않도록 구현·적용. (2026-08-16 확장) 이 3종 분류는 `17_...Manual_Refinement_Implementation_List.md`(MR-DEC-02, `CONTRACT_RULES.md` §10.3)가 `SCREEN_SPEC`/`DESIGN_SYSTEM`/`MANUAL_REFINEMENT`/`SYSTEM_LAYOUT`/`RUNTIME_DATA` 5종으로 확장했다. 여기 `USER_OVERRIDE`(암묵적, Component Property 전용)는 대체되지 않고 그대로 병행 동작한다 |
| DEC-05 | P0 | [x] | 화면 갱신 기본 정책 | `CONTRACT_RULES.md` §3 변경 정책 표(CREATE/MERGE/REPLACE/SKIP) + Plugin UI의 동기화 모드 select가 MERGE를 기본값으로 제공하고 REPLACE는 사용자가 명시적으로 선택했을 때만 기존 화면을 Archive 후 재생성 |
| DEC-06 | P0 | [x] | Registry 버전 정책 | `CONTRACT_RULES.md` §1(`registryVersion`은 "Figma Library Publish 단위로 신규 발급") + §5(Screen/Profile/Registry/Bundle metadata 간 버전 불일치를 `*_MISMATCH` 오류로 차단) + `ComponentRegistryResolver`로 구현·검증 완료 |
| DEC-07 | P0 | [x] | API 및 산출물 보안 정책 | `X-API-Key`(R6-010) + 단기 토큰(R6-012, `FigmaRestTokenService`) + CORS(R6-012) + MCP 전용 인증(DEC-11) 구현·테스트 완료. 2026-07-28 redaction 감사([13번 문서](./13_Semantic_Figma_Operations_Runbook.md) §11) 결과 MCP 응답·로그·저장 산출물·메시지 문자열·Plugin 어디에서도 Component/Variable Key 노출 없음을 확인. REST 전용 Registry 검토 API(원문 Key 포함, 사람 승인용)와 MCP 채널(redaction됨)이 코드 수준에서 분리돼 있음도 확인 |
| DEC-08 | P1 | [ ] | 플러그인 배포 방식 | 개발용 manifest import 방식만 존재(`krds-design-system-author-plugin`/`figma-screen-spec-plugin` 둘 다). 조직 내부 Plugin 배포 채널(Figma Organization/Enterprise 여부, 사내 배포 절차)은 코드로 대신할 수 없는 순수 조직 IT 정책이라 여전히 미결. 선택지 비교와 권장안은 [13_Semantic_Figma_Operations_Runbook.md](./13_Semantic_Figma_Operations_Runbook.md) §7 참고 |
| DEC-09 | P0 | [x] | 초기 필수 Component·Pattern 목록 | `component-catalog-v1.json`의 현재 최상위 분류는 `requiredComponents` 12종, `optionalComponents` 2종, `patterns` 4종, `pageTemplates` 3종이다. 필수 12종은 원자 Component와 조합용 Pattern/Page 항목을 함께 포함한다. Schema 검증과 조직 Library 담당자 최종 Preview 승인이 완료되어 운영 기준으로 확정 — 승인 요청 문서: [14_DEC02_DEC09_Component_Catalog_Approval_Request.md](./14_DEC02_DEC09_Component_Catalog_Approval_Request.md) |
| DEC-10 | P0 | [x] | Plugin 입력 연결 방식 | **FILE 우선, REST 선택 기능**으로 최종 확정. 기본은 `.figma-export-bundle.json` import이며 REST는 배포 환경이 도메인·CORS·단기 토큰을 명시적으로 설정한 경우에만 활성화한다. 운영 도메인은 아키텍처 결정이 아닌 환경별 배포 값으로 관리 — [최종 결정](./15_DEC10_DEC12_Final_Decision.md) §2 |
| DEC-11 | P0 | [x] | 신규 Figma/DesignSystem MCP Tool 인증 방식 | `FigmaToolAuthorizationService` + `app.figma.mcp-shared-secret` + `FigmaExportTool`/`DesignSystemTool` 양쪽 모두 `figmaMcpSecret` 파라미터를 필수로 강제하도록 구현·테스트 완료. 기존 21개 Tool이 걸린 `/mcp/**` 전역 무인증 정책은 그대로 유지(신규 Tool에만 추가 인가) |
| DEC-12 | P1 | [x] | Removed Node 예외 처리 정책 | **ARCHIVE 단일 정책**으로 최종 확정. `reconcile()`/`archiveNode()`가 구현한 동작을 v1 계약으로 삼고 DELETE·ASK는 범위에서 제외한다. 영구 삭제는 동기화와 분리된 사람의 운영 작업이며, 실제 요구가 생길 때 별도 DEC로 재검토 — [최종 결정](./15_DEC10_DEC12_Final_Decision.md) §3 |
| DEC-13 | P0 | [x] | 7가지 요청 기능의 서버 통합 방식 | 별도 Node.js MCP를 병렬 구축하지 않고 기존 Spring Boot Streamable HTTP MCP에 통합한다. 원본 TypeScript 구조는 책임 분리 참조로만 사용하며 `ScreenSpecification`·`FigmaScreenSpec`·기존 보안 정책을 단일 경로로 유지 |
| DEC-14 | P0 | [x] | Figma 읽기·쓰기 실행 경계 | Figma REST API는 파일·노드·이미지·스타일·컴포넌트 조회 전용으로 사용하고, 캔버스 쓰기는 검증된 Bundle/Operation을 Figma Plugin이 Preview 후 명시적으로 Apply할 때만 수행. MCP는 `APPLIED`를 선행 보고하지 않음 |
| DEC-15 | P0 | [x] | LLM 통합 방식 | Anthropic SDK 직접 종속 대신 Spring AI `ChatModel`/멀티모달 capability로 추상화. 7개 요청의 LLM 결과는 구조화 출력 Schema·의미 검증 통과 후에만 Spec 후보로 사용 |

---

## 5. R0 — 계약 및 스키마 확정

### 5.1 공통 식별자와 버전

- [x] **R0-001 · P0** `screenId`, `screenVersion`, `designSystemId`, `designSystemVersion`, `registryVersion`의 형식 정의 — `figma-common-v1.schema.json` 및 `CONTRACT_RULES.md`
- [x] **R0-002 · P0** `logicalNodeId`의 생성·보존·충돌 처리 규칙 정의 — 공통 Schema와 `LogicalNodeIdFactory`가 동일한 세그먼트 규칙 적용
- [x] **R0-003 · P0** `screenType`(`LIST`/`FORM`/`DETAIL`) 열거형과 `layoutPattern`(`STANDARD`/`MASTER_DETAIL`/`DASHBOARD`) 열거형을 분리 확정 — 화면유형(Builder 선택 기준)과 레이아웃 변형을 서로 다른 필드로 두어 archetype 매핑 충돌을 구조적으로 제거
- [x] **R0-004 · P0** 노드 유형 `PAGE`, `SECTION`, `COMPONENT`, `TEXT`, `SLOT`, `REPEAT` 확정
- [x] **R0-005 · P0** 변경 정책 `CREATE`, `MERGE`, `REPLACE`, `SKIP` 의미 확정
- [x] **R0-006 · P1** 계약 호환성 규칙과 지원 버전 범위 정의
- [x] **R0-007 · P0** 기존 `ScreenSpecification.archetype` → `screenType`/`layoutPattern` 매핑 표 승인(§5.3): `screenType`은 `PageSpec.template` 접미사(`_LIST`/`_FORM`/`_DETAIL`) 우선/`ScreenSpecification.archetype` 접미사 fallback으로, `layoutPattern`은 `ScreenSpecification.archetype` 키워드 포함 여부로 각각 독립 판정
- [x] **R0-008 · P0** `screenType` 접미사 판정에 실패한 자유 문자열 archetype의 검증 오류·사용자 선택 정책 확정

### 5.2 JSON Schema

- [x] **R0-010 · P0** `figma-screen-spec-v1.schema.json` 작성
- [x] **R0-011 · P0** `design-system-spec-v1.schema.json` 작성
- [x] **R0-012 · P0** `design-system-profile-v1.schema.json` 작성
- [x] **R0-013 · P0** `component-registry-v1.schema.json` 작성
- [x] **R0-014 · P0** `figma-generation-report-v1.schema.json` 작성
- [x] **R0-015 · P1** 정상·경계·오류 예제 JSON 작성
- [x] **R0-016 · P1** 스키마 간 참조와 공통 정의 `$defs` 정리
- [x] **R0-017 · P1** CI에서 예제 JSON의 Schema 검증 테스트 추가 — Gradle `check`가 `figmaContractTest`를 선행 실행
- [x] **R0-018 · P0** `figma-export-bundle-v1.schema.json` 작성: `FigmaScreenSpec`+`DesignSystemProfileSnapshot`+`ComponentRegistrySnapshot`+`ExportMetadata`를 묶는 파일 우선 입력 계약(`DEC-10=FILE`일 때 Plugin의 단일 입력 파일)
- [x] **R0-019 · P1** Bundle 내부 각 조각(schema/profile/registry)의 버전과 실제 Publish 버전 불일치 처리 규칙 정의

### 5.3 매핑 표준

- [x] **R0-020 · P0** KRDS/eGovFrame 논리 컴포넌트 카탈로그 확정 — `component-catalog-v1.json`
- [x] **R0-021 · P0** 논리 속성 → Figma Component Property 매핑 표 작성
- [x] **R0-022 · P0** 논리 속성 → 코드 생성 속성 매핑 표 작성
- [x] **R0-023 · P1** 지원하지 않는 속성의 fallback 규칙 정의
- [x] **R0-024 · P1** 컴포넌트 대체·폐기·별칭 규칙 정의
- [x] **R0-025 · P0** 초기 필수 Component·Pattern·Page Template 카탈로그 승인 — 기술 기준선과 Schema 검증 완료, 조직 Library 담당자 최종 승인(DEC-09) 반영 완료
- [x] **R0-026 · P0** 7가지 요청 공통 `FigmaDesignRequest`·`FigmaDesignOperation` 계약과 RequestType enum 정의 — `figma-design-request-v1.schema.json`/`figma-design-operation-v1.schema.json` + Java `FigmaDesignRequestType`/`FigmaDesignRequest`/`FigmaDesignOperation`(요청 라우팅·유형별 필수값 검증은 I-3 범위로 남김)
- [x] **R0-027 · P0** `designSystemProfileId`가 Token/Variable·Component Registry·Default Layout Policy 버전을 원자적으로 결합하는 계약 정의 — (2026-08-18) 신규 `DesignSystemProfileId` 레코드(`profileId:profileVersion:registryVersion:layoutPolicyVersion` 형식, `of(DesignSystemProfile)`/`parse(String)` 왕복)를 `model.designsystem` 패키지에 추가. `layoutPolicyVersion`이 없는(R1-015 이전 8-필드 호환 생성자로 만든) Profile은 `-` placeholder로 표현해 형식이 항상 4-segment로 고정되도록 함. `DesignSystemProfileIdTest` 6건으로 결합·placeholder·왕복·형식 오류·구분자 충돌 검증. **기존 서비스가 쓰는 `designSystemProfileId`(예: `FigmaScreenExportRequest`)는 여전히 `DesignSystemProfile.id()`만 가리키며 최신 버전을 조회하는 별도 개념 — 이 타입으로 강제 교체하지 않음**(버전 조합을 정확히 고정 참조해야 하는 Bundle 재현·감사 로그 등에서 선택적으로 사용)
- [~] **R0-028 · P1** FORM/LIST/DETAIL/DASHBOARD 기본 Layout과 Desktop/Tablet/Mobile 변환·Component Swap 정책 Schema 작성 — `platform-layout-policy-v1.schema.json`과 유효 fixture를 추가해 1440/12/24/40, 768/8/16/24, 390/4/12/16 viewport·navigation·Swap 계약을 고정하고 `PlatformLayoutPolicy` Java 모델에 gap/padding 및 deterministic `recalculateGrid()`를 연결했다. Plugin에도 `planPlatformLayout()` 순수 검증을 추가해 viewport 불일치·Freeform Frame을 mutation 전에 차단한다. (2026-08-19 Figma Desktop 확인) 실제 `qna-list · egov.listPage` Frame은 1440px이지만 Auto Layout gap 40, horizontal padding 80, vertical padding 48로 정책 Desktop 기준(gap 24/padding 40)과 drift가 확인됐다. **(2026-08-20/21, 상세 근거는 [24번 문서](./24_R0-028_Table_Card_Swap_Logical_Type_Mismatch_Review.md))** 라이브 재검증과 근본 수정을 진행했다: ① `qna-list` Desktop Frame을 Figma에서 직접 gap24/padding40으로 수정하고, Plugin `configureWrapper()`/`syncNode()`가 `FigmaScreenSpec.viewport`를 받아 DESKTOP/TABLET/MOBILE별 정책값을 선택하도록 하드코딩을 제거해 향후 생성되는 모든 화면에 재발하지 않도록 함(Figma Desktop에서의 신규 화면 생성 결과 시각 확인은 잔여). ② `FigmaPlatformConversionService.approvedPolicy()`를 신설하고 `generateFromPlatformConversion()`이 이를 쓰도록 배선했으나, 실제로 넣으려 했던 "버튼 크기 축소" Swap 규칙(`krds.button.large→medium/small`)이 실존하지 않는 컴포넌트를 가리키는 것으로 드러나 규칙은 비워둠 — 카탈로그에 실제 스왑 후보 쌍(서로 다른 Published Component)이 없고, 버튼 Size는 Variant Property(같은 컴포넌트의 속성값)라 이 프로젝트의 Component Swap 메커니즘 자체가 다루는 대상이 아님을 라이브로 확인(FTC 정부 포털 Design System Button: Style×Size(Medium/Small)×State 18종). ③ `FigmaScreenExportService.registerConvertedSpec()`을 신설해 PLATFORM_CONVERT 결과가 기존 `/api/figma/screens/{screenId}/download`로 조회되도록 배선하고 실제 MCP `convertPlatform`→`generateFigmaBundleForOperation` 호출로 end-to-end 검증 완료. ④ Mobile Table→Card 스왑(`applyMobileTableCardSwap`)의 판정 문자열을 실제 파이프라인이 stamp하는 값 `"krds.dataTable"`로 수정(기존 `"egov.dataTable"`/`"krds.table"`는 어떤 노드에도 찍히지 않는 값이었음). Java 전체 테스트·Plugin `typecheck`/`build`/`lint`/`npm test`(60건) 통과. **Figma Desktop에서 신규 화면·Table→Card 스왑의 시각적 확인만 잔여**(브라우저 자동화로는 Desktop 앱 조작 불가)
- [~] **R0-029 · P1** 원본 명세서의 KRDS 색·타이포그래피·간격·radius 및 11개 예시 컴포넌트를 샘플 fixture로 변환하되 운영 fileKey/Node ID와 분리 — `component-catalog-v1.json`에 table header/cell·textarea와 property mapping을 추가하고 `krds-token-catalog-v1.schema.json` 및 색상·타이포그래피·spacing·radius fixture를 추가했다. Design System Library Publish 기반의 공식 Token SSOT 동기화와 추가 운영 fixture 축적은 잔여

초기 매핑 후보는 `screenType`과 `layoutPattern`을 서로 다른 필드로 독립 판정한다.
하나의 archetype 문자열이 두 표에 동시에 걸려도(예: `MASTER_DETAIL`) 서로 다른 필드에
쓰이므로 우선순위 충돌이 생기지 않는다.

**`screenType` 판정** — `PageSpec.template` 접미사 우선, 값이 없으면
`ScreenSpecification.archetype` 접미사로 fallback.

| 접미사 | `screenType` | 처리 |
|---|---|---|
| `_LIST` | `LIST` | `ListFigmaScreenBuilder` 선택 |
| `_FORM`, `_REGIST` | `FORM` | `FormFigmaScreenBuilder` 선택 |
| `_DETAIL` | `DETAIL` | `DetailFigmaScreenBuilder` 선택 |

**`layoutPattern` 판정** — `ScreenSpecification.archetype` 전체 문자열에 특정 키워드가
포함되는지로 독립 판정(접미사 판정과 무관, 순서 의존성 없음).

| archetype에 포함된 키워드 | `layoutPattern` |
|---|---|
| `MASTER_DETAIL` | `MASTER_DETAIL` |
| `DASHBOARD` | `DASHBOARD` |
| (해당 없음) | `STANDARD` |

`screenType` 접미사 판정에 실패한 값은 자동으로 임의 값을 부여하지 않고 오류 또는
사용자 선택으로 처리한다. 예: `archetype="MASTER_DETAIL"`(접미사 자체는 없음)은
`PageSpec.template`(각 페이지별로 `MASTER_LIST`/`MASTER_DETAIL`/`MASTER_FORM`,
`ScreenSpecAssembler.java:201`의 `base = archetype.replaceAll("_(LIST|DETAIL|FORM)$", "")`
결과)로 페이지별 `screenType`을 판정하고, `layoutPattern`은 원본
`ScreenSpecification.archetype` 문자열 `"MASTER_DETAIL"`에 키워드가 포함되므로
`MASTER_DETAIL`로 판정한다.

### 5.4 R0 검증

- [x] **R0-T01** 모든 예제 JSON이 각 Schema를 통과한다.
- [x] **R0-T02** 알 수 없는 필수 컴포넌트와 잘못된 버전이 명확한 오류를 반환한다.
- [x] **R0-T03** 동일한 입력에서 `logicalNodeId`가 결정적으로 생성된다.
- [x] **R0-T04** 기존 archetype별 `screenType`/`layoutPattern`과 Builder 선택이 매핑 표와 일치한다.
- [x] **R0-T05** `screenType` 접미사 판정에 실패한 archetype이 조용히 임의 값으로 변환되지 않는다.
- [x] **R0-T06** `MASTER_DETAIL`처럼 `screenType`과 `layoutPattern` 매핑에 동시에 걸리는 archetype도 두 필드가 충돌 없이 각각 유일하게 결정된다.
- [x] **R0-T07** 7가지 RequestType별 정상·경계·오류 fixture가 공통 요청 Schema를 통과한다. — `website-figma-contract/fixtures/*-figma-design-request-*.json` 7종 valid + 2종 invalid, `contract-test.mjs`에서 검증
- [x] **R0-T08** Profile·Registry·Default Layout Policy 중 하나라도 버전이 다르면 교체/변환 Preflight가 실패한다. — (2026-08-17) `ComponentRegistryResolver`가 Registry 버전 검증을 구현했고, `DesignSystemQueryService.preflightRegistry(profileId, registryVersion, requiredLogicalTypes, expectedLayoutPolicyVersion)`가 `expectedLayoutPolicyVersion` 불일치 시 `LAYOUT_POLICY_VERSION_MISMATCH` ERROR로 Preflight를 실패시킴. 실제 호출 경로는 `DesignSystemTool.preflightComponentRegistry()`(MCP)이며(이전 버전이 잘못 인용했던 `FigmaScreenExportService.preflightComponentRegistry()`는 존재하지 않음), `DesignSystemRegistryPreflightTest`의 `preflightFailsWhenLayoutPolicyVersionDiffersFromExpected`/`preflightSkipsLayoutPolicyCheckWhenExpectedVersionIsNull`로 검증

---

## 6. R1 — Spring 도메인과 저장소

### 6.1 도메인 모델

- [x] **R1-001 · P0** `FigmaScreenSpec` 공통 DTO 구현(`screenType`, `layoutPattern` 필드 포함)
- [x] **R1-002 · P0** `FigmaNodeSpec` 및 하위 노드 DTO 구현
- [x] **R1-003 · P0** `ComponentBinding` 구현
- [x] **R1-004 · P0** `VariableBinding` 구현
- [x] **R1-005 · P0** `DesignSystemSpec` 구현
- [x] **R1-006 · P0** `DesignSystemProfile` 구현
- [x] **R1-007 · P0** `ComponentRegistry`와 `ComponentRegistryEntry` 구현
- [x] **R1-008 · P0** `FigmaScreenExportRequest`, `FigmaExportMode`, `FigmaSyncMode` 구현
- [x] **R1-009 · P1** `FigmaExportResult`, `FigmaExportIssue` 및 오류 코드 모델 구현
- [x] **R1-010 · P1** `FigmaExportResult`를 `figma-generation-report-v1` 계약으로 직렬화
- [x] **R1-011 · P1** Jackson 직렬화·역직렬화 설정
- [x] **R1-012 · P1** DTO Bean Validation 제약 추가 — Export 요청·Screen/Node/Issue·생성 보고서 DTO 제약 및 REST `@Valid` 적용
- [x] **R1-013 · P0** `FigmaExportBundle`, `DesignSystemProfileSnapshot`, `ComponentRegistrySnapshot`, `FigmaExportMetadata` 구현
- [x] **R1-014 · P0** `FigmaDesignRequest`, `FigmaDesignOperation`, `FigmaDesignOperationStatus`, `FigmaDesignScreenRequest` 구현 — `com.krdevops.springai.model.figma.request` 패키지, 공통 값 객체는 `com.krdevops.springai.model.contract`(`DesignSystemSnapshotRef`/`GenerationIssue`/`ArtifactRef`/`SourceRevisionRef`) 재사용
- [x] **R1-015 · P1** `DefaultLayoutPolicy`, `PlatformLayoutPolicy`, `ComponentSwapPolicy` 구현 — (2026-08-17) `DefaultLayoutPolicy`(LIST/FORM/DETAIL/DASHBOARD별 `ScreenLayoutSpec`) 신규 구현. 기존에 정의만 있고 아무도 참조하지 않던 `PlatformLayoutPolicy`를 실제로 소비하는 `ComponentSwapPolicyResolver`(플랫폼·컴포넌트별 Swap 규칙 해석, 중복 규칙은 `COMPONENT_SWAP_AMBIGUOUS`로 차단) 신규 구현. `DesignSystemProfile`에 `layoutPolicyVersion`/`layoutPolicy` 필드 추가(8-필드 기존 호출자 호환 생성자 유지). `DefaultLayoutPolicyTest`/`ComponentSwapPolicyResolverTest`로 검증

### 6.2 저장 모델

- [x] **R1-020 · P0** Design System Profile 저장 테이블/엔티티 설계
- [x] **R1-021 · P0** Component Registry 저장 테이블/엔티티 설계
- [x] **R1-022 · P0** Screen/Figma Spec 버전 및 산출물 메타데이터 저장 설계
- [x] **R1-023 · P1** Preview 검토 상태와 승인 이력 저장 설계
- [x] **R1-024 · P1** Library Publish 및 Registry 동기화 이력 저장 설계
- [x] **R1-025 · P1** 생성 실행 결과와 오류 보고서 저장 설계 — `FigmaGenerationReportRepository` 불변·멱등 저장과 화면별 조회
- [x] **R1-026 · P0** `JdbcTemplate` 기반 `createTableIfNotExists`로 스키마 초기화(`ScreenSpecRepository`와 동일한 기존 Repository 패턴 재사용, 별도 마이그레이션 툴 도입 안 함)
- [x] **R1-027 · P1** Repository 및 조회 인덱스 구현
- [x] **R1-028 · P1** 낙관적 잠금 또는 버전 충돌 처리 구현 — 동일 Screen 버전은 동일 내용만 멱등 허용하고 다른 내용은 `FIGMA_SCREEN_VERSION_CONFLICT`로 거부
- [x] **R1-029 · P1** Operation 불변 저장·멱등 재시도·source revision 충돌·멀티 스크린 원자 상태 전이 저장 구현 — `FigmaDesignOperationRepository`(revision 단위 불변 저장, requestHash 멱등 인덱스, source revision 불일치 시 CONFLICT 기록) + `FigmaDesignOperationStateService`(전이 그래프 강제, APPLIED는 Plugin 보고 필요) + `FigmaDesignOperationRepositoryIntegrationTest`

### 6.3 검증 서비스

- [x] **R1-030 · P0** `DesignSystemSpecValidator` 구현
- [x] **R1-031 · P0** `DesignSystemProfileValidator` 구현
- [x] **R1-032 · P0** `ComponentRegistryValidator` 구현
- [x] **R1-033 · P1** JSON Schema 검증과 Java 의미 검증의 오류 통합 — `FigmaContractSchemaValidator`와 `FigmaScreenSpecValidator`
- [x] **R1-034 · P1** 오류 위치를 JSON Pointer와 `logicalNodeId`로 반환

### 6.4 R1 테스트

- [x] **R1-T01** DTO 직렬화 round-trip 테스트
- [x] **R1-T03** Registry 중복 논리명·중복 Component Key 검증 테스트
- [x] **R1-T04** 저장소 버전 충돌 및 롤백 테스트
- [x] **R1-T05** `createTableIfNotExists` 반복 실행 안전성 테스트(중복 실행해도 오류 없음)

---

## 7. R2 — FigmaScreenSpec 생성 백엔드

### 7.1 공통 Builder

- [x] **R2-001 · P0** `FigmaScreenBuilder` 인터페이스 정의
- [x] **R2-002 · P0** `FigmaScreenBuilderRegistry` 구현
- [x] **R2-003 · P0** `ListFigmaScreenBuilder` 구현
- [x] **R2-004 · P0** `FormFigmaScreenBuilder` 구현
- [x] **R2-005 · P1** `DetailFigmaScreenBuilder` 구현
- [x] **R2-006 · P0** `FigmaScreenTypeResolver` 구현: `screenType`은 `PageSpec.template`(예: `{base}_LIST`/`{base}_FORM`/`{base}_DETAIL`) 접미사 우선/`ScreenSpecification.archetype` 접미사 fallback으로, `layoutPattern`은 `ScreenSpecification.archetype` 키워드 포함 여부로 독립 판정(§5.3)
- [x] **R2-007 · P1** 반복 행과 빈 상태·로딩·오류 상태 표현 구현
- [x] **R2-008 · P0** `FigmaScreenExportService` 구현
- [x] **R2-009 · P0** `FigmaScreenSpecValidator` 구현
- [x] **R2-010 · P0** `FigmaScreenSpecSerializer` 구현
- [x] **R2-011 · P0** `LogicalNodeIdFactory` 구현
- [x] **R2-012 · P1** `DesignSystemProfile` 기본값 병합 구현
- [x] **R2-013 · P1** Component Registry 존재 여부 사전 검증 구현
- [x] **R2-014 · P0** `FigmaExportBundleAssembler` 구현: Export 시점의 `DesignSystemProfile`/`ComponentRegistry` 스냅샷과 `FigmaScreenSpec`을 하나의 `FigmaExportBundle`로 조립

### 7.2 업무 화면 Builder

- [x] **R2-020 · P0** 사용자 목록 Builder 구현
- [x] **R2-021 · P0** 사용자 등록 Builder 구현
- [x] **R2-022 · P1** 사용자 수정/상세 Builder 구현 — 공통 FORM/DETAIL Builder 재사용 및 업무 fixture 검증
- [x] **R2-023 · P1** 게시판 목록/등록 Builder 구현 — 검색·테이블·페이지네이션·FORM 의미 fixture 검증
- [x] **R2-024 · P2** Master/Detail 예제 Builder 구현 — 페이지별 LIST/DETAIL Builder와 독립 `MASTER_DETAIL` layoutPattern 조합

### 7.3 조회 및 다운로드

- [x] **R2-030 · P0** 화면별 최신 FigmaScreenSpec 조회 서비스 구현
- [x] **R2-031 · P0** 특정 버전 조회 서비스 구현
- [x] **R2-032 · P0** JSON 다운로드 서비스 구현: `DEC-10=FILE` 기본값 기준 `FigmaExportBundle`(`.figma-export-bundle.json`)로 다운로드 제공
- [x] **R2-033 · P1** ETag 또는 버전 기반 캐시 처리 — Bundle SHA-256 ETag와 `If-None-Match` 304 응답
- [x] **R2-034 · P1** 생성 결과를 `DesignArtifactService`에 저장 — 화면 버전별 불변 Spec·Generation Report·Metadata 저장 및 결과에 artifact 참조 포함
- [x] **R2-035 · P1** 스키마·Profile·Registry 버전을 산출물 메타데이터에 포함

### 7.4 R2 테스트

- [x] **R2-T01** LIST/FORM 고정 fixture golden-file 테스트
- [x] **R2-T02** 같은 Screen Specification 입력의 결정적 출력 테스트
- [x] **R2-T03** Registry 누락 시 fallback과 오류 수준 테스트
- [x] **R2-T04** JSON 다운로드 Content-Type·파일명·인코딩 테스트
- [x] **R2-T05** 기존 코드 생성 결과에 회귀가 없는지 테스트
- [x] **R2-T06** 기존 archetype별 Builder 선택과 미등록값 거부 테스트
- [x] **R2-T07** 필수값·중복 ID·순환 트리 검증 테스트(R1-T02에서 이관 — `FigmaScreenSpecValidator` 책임 영역이라 R2로 이동)
- [x] **R2-T08** `FigmaExportBundle` 내부 schema/profile/registry 버전 불일치 시 명확한 오류 반환 테스트

---

## 8. R3 — Design System Author Plugin

### 8.1 플러그인 기반 구조

- [x] **R3-001 · P0** 별도 Figma Plugin 프로젝트 또는 모노레포 디렉터리 확정
- [x] **R3-002 · P0** `manifest.json`, TypeScript, 빌드 설정 생성
- [x] **R3-003 · P0** Plugin UI와 main thread 메시지 계약 정의
- [x] **R3-004 · P0** `DesignSystemSpec` 파일 불러오기 기능 구현
- [x] **R3-005 · P0** Schema/의미 검증 결과 화면 구현
- [x] **R3-006 · P1** 검증 오류 위치 이동 및 상세 표시 구현 — 오류 코드·JSON Pointer·targetId 표시와 기존 Figma 노드 선택/줌

### 8.2 토큰과 컴포넌트 생성

- [x] **R3-010 · P0** 색상·타이포그래피·간격·반경 토큰 생성
- [x] **R3-011 · P0** Local Variable Collection 생성·갱신
- [x] **R3-012 · P0** KRDS/eGovFrame 기본 컴포넌트 생성
- [x] **R3-013 · P0** Component Set과 Variant 생성
- [x] **R3-014 · P0** Boolean/Text/Instance Swap Property 구성
- [x] **R3-015 · P1** Auto Layout, min/max 크기, padding 적용
- [x] **R3-016 · P1** 컴포넌트 설명과 개발 메타데이터 설정 — description·documentation link·코드 컴포넌트·패키지 정보
- [x] **R3-017 · P1** 재실행 시 기존 논리 컴포넌트 탐색·업데이트
- [x] **R3-018 · P0** 기존 사용 인스턴스를 깨뜨리는 파괴적 재생성 방지

### 8.3 Preview와 사람 검토

- [x] **R3-020 · P0** 대표 상태를 포함한 Preview 페이지 생성
- [x] **R3-021 · P0** 신규·변경·폐기 예정 항목의 차이 표시
- [x] **R3-022 · P1** 변경 전/후 토큰 및 속성 비교 표 생성
- [x] **R3-023 · P0** 검토 상태 `DRAFT`, `IN_REVIEW`, `APPROVED`, `REJECTED` 처리 — Figma Document pluginData에 버전별 상태 영속화
- [x] **R3-024 · P0** 사람이 승인하기 전 Publish 완료로 간주하지 않도록 제어
- [x] **R3-025 · P1** 승인자·검토 시각·의견을 로컬 승인 기록 JSON으로 우선 내보내고, `R6-007`(Preview 검토 상태·승인 이력 API)이 R6에서 구현된 이후 Spring 서버 기록으로 전환(R3가 R6보다 먼저 진행되는 착수 순서상 R3 시점에는 서버 기록 API가 아직 없음)

### 8.4 R3 테스트

- [x] **R3-T01** 샘플 DesignSystemSpec import 및 컴포넌트 수 검증
- [x] **R3-T02** 동일 Spec 재실행 시 중복 컴포넌트가 생기지 않는지 검증
- [x] **R3-T03** 속성 추가 시 기존 Instance 연결이 유지되는지 검증 — 기존 Component Set 제자리 UPDATE 계획 검증
- [x] **R3-T04** Preview 승인 전후 상태 전이 검증
- [x] **R3-T05** Figma API 제한·권한 오류 보고 검증

---

## 9. R4 — Library Publish 및 Component Key 동기화

### 9.1 동기화 흐름

- [x] **R4-001 · P0** Publish 완료를 확인하는 운영 절차 정의
- [x] **R4-002 · P0** Published Component/Variable/Variable Collection 조회 방식 확정: Author Plugin에서 `getPublishStatusAsync()`와 공개 `key` 사용(Style은 현재 DesignSystemSpec/Registry 대상이 아니므로 후속 범위)
- [x] **R4-003 · P0** `pluginData.logicalId`와 Published Component/Variable Key 매칭 구현
- [x] **R4-004 · P0** Registry 후보 diff 생성
- [x] **R4-005 · P0** 사람 확인 후 Registry 반영 기능 구현
- [x] **R4-006 · P0** `fileKey`, Variant `componentKey`, `componentSetKey`, Variable/Collection Key, `registryVersion` 저장
- [x] **R4-007 · P1** 폐기·대체·alias 정보 저장 — Spec/Registry/Author Plugin 계약에 lifecycle·replacement·aliases를 추가하고 순환·충돌 검증 및 해석기를 구현
- [x] **R4-008 · P1** 동기화 실패 재시도와 부분 실패 보고 — Registry는 부분 저장하지 않고 원자적으로 거부하며 실패 대상·오류 코드·재시도 가능 여부·retryToken을 반환하고 명시적 retry API로 재검증
- [x] **R4-009 · P1** 이전 Registry 버전 조회 및 Profile의 Registry 연결 롤백

### 9.2 드리프트 검증

- [x] **R4-020 · P0** Registry Key 유효성 점검: 비어 있는 Key·중복 Key·`CURRENT`가 아닌 Publish 상태는 차단함. Figma에서 Key를 실제 import하는 원격 유효성 검증 — (2026-08-18 재조사) 이미 구현돼 있었으나 테스트 증적이 없었다: `ComponentRegistrySyncService.validateActualFigmaInventory()`(KRV-065)가 Registry 승인 전 `FigmaLibraryInventoryRepository`(Plugin이 Figma에서 원격으로 가져와 저장한 Inventory Snapshot)와 대조하고, `FigmaPropertyDriftValidator`가 `componentSetKey`가 실제 원격 Inventory와 다르거나(`actual==null` 포함) 없으면 FATAL `COMPONENT_PROPERTY_DRIFT`로 차단한다(이 codebase의 "원격 검증"은 서버가 직접 Figma REST를 매번 호출하는 대신 Plugin이 푸시한 Snapshot과 대조하는 방식임 — R4-022가 이미 명시한 아키텍처). 그동안 이 핵심 분기(`actual==null`/Key 불일치)가 어떤 테스트에서도 실행되지 않았던 gap을 발견해 `FigmaPropertyDriftValidatorTest`에 2건, `ComponentRegistrySyncServiceTest`에 `FIGMA_INVENTORY_SNAPSHOT_MISSING`/Key 불일치 REJECTED 2건 추가로 메움
- [x] **R4-021 · P0** DesignSystemProfile과 Library fileKey·Profile version 불일치 감지
- [x] **R4-022 · P1** 삭제된 논리 자산, 변경된 Published Key·컴포넌트 이름·Library fileKey 감지. Figma 원격 import 자체는 R5 Plugin에서 수행
- [x] **R4-023 · P1** 호환되지 않는 Property 변경 감지 — 속성 제거·타입 변경·기존 Variant 값 제거를 Breaking 오류로 차단
- [x] **R4-024 · P1** 화면 생성 전 드리프트 사전 점검 API 구현 — REST `POST /api/design-systems/{profileId}/registries/preflight`와 MCP `preflightComponentRegistry`

### 9.3 R4 테스트

- [x] **R4-T01** `UNPUBLISHED`/`CHANGED` Component가 Registry에 반영되지 않는지 검증
- [x] **R4-T02** 동일 Publish 버전·동일 내용의 중복 동기화 멱등성 검증
- [x] **R4-T03** 폐기된 키의 alias→replacement 연쇄 해석과 순환 차단 검증
- [x] **R4-T04** Registry 롤백 후 화면 생성 바인딩이 롤백 Registry의 Published Key를 다시 해석하는지 검증

---

## 10. R5 — FigmaScreenSpec 화면 생성 Plugin

### 10.1 입력과 사전 검증

- [x] **R5-001 · P0** `FigmaExportBundle` 파일 import 구현(`FigmaScreenSpec`+`DesignSystemProfileSnapshot`+`ComponentRegistrySnapshot`+`ExportMetadata`를 단일 파일에서 해석)
- [x] **R5-002 · P1** Spring API에서 직접 조회하는 옵션 구현
- [x] **R5-003 · P0** Schema·Screen·Profile·Registry 버전 및 Library fileKey 검증
- [x] **R5-004 · P0** 1차 필수 Component·Pattern·Page Template 누락 및 `CURRENT` 상태 사전 검사
- [x] **R5-005 · P1** 생성 Preview와 변경 요약 표시

### 10.2 컴포넌트 재사용

- [x] **R5-010 · P0** Component Registry Resolver 구현
- [x] **R5-011 · P0** `importComponentSetByKeyAsync()` 기반 Published Component 사전 import 구현
- [x] **R5-012 · P0** `createInstance()` 기반 화면 구성: Auto Layout Wrapper 안에 Published Instance와 하위 논리 Wrapper를 배치해 Instance 연결과 자유로운 화면 트리를 함께 유지
- [x] **R5-013 · P0** Variant와 Component Property 적용(`variant`/`actionType` → `Style`/`Label` 의미 별칭 포함)
- [x] **R5-014 · P1** Instance Swap Property 처리
- [x] **R5-015 · P0** 필수(Required) Component가 Registry에 없거나 `CURRENT`가 아니면 Preview에서 FATAL로 보고하고 정식 생성 차단. 선택(Optional) Component는 R5-016의 시각적 fallback으로 대체
- [x] **R5-016 · P0** 선택 Component용 시각적 fallback 구현: `nodeType=COMPONENT`인데 Registry에 없는 선택 노드는 대시 테두리(주황색 stroke)+경고색 배경+타입명 라벨을 가진 Placeholder로 표시해 정식 Published Instance와 한눈에 구분. `OPTIONAL_COMPONENT_NOT_IN_REGISTRY` WARNING Issue와 `fallbackCount` 집계 포함. Registry가 갱신되면 재실행 시 Placeholder를 제거하고 정식 Instance로 교체(멱등)
- [x] **R5-017 · P0** MERGE/REPLACE의 `fallbackCount=0`을 유지하고 필수 Component 오류가 하나라도 있으면 적용 자체를 차단

### 10.3 논리 트리 재사용 및 갱신

- [x] **R5-020 · P0** Figma Plugin Data에 `screenId`·`screenVersion`·`logicalNodeId`·논리 타입·Component Set Key 저장
- [x] **R5-021 · P0** 기존 화면 Wrapper/Instance 탐색 및 인덱싱
- [x] **R5-022 · P0** 기존 노드 재사용, 신규 노드만 생성
- [x] **R5-023 · P0** 사라진 노드를 `🗄 Removed — {screenId}`로 이동하는 기본 Archive 정책 구현
- [x] **R5-024 · P0** DESIGN_SYSTEM 소유 속성은 Published Instance 연결로 갱신
- [x] **R5-025 · P0** SCREEN_SPEC 소유 Layout·Variant·Property 갱신
- [x] **R5-026 · P0** 이전 Plugin 관리 Property와 현재 값을 비교해 USER_OVERRIDE 보존
- [x] **R5-027 · P1** 부모·순서 변경 감지와 기존 Wrapper 이동 적용
- [x] **R5-028 · P0** MERGE와 REPLACE 모드 구현
- [x] **R5-029 · P1** 적용 전 변경 diff 및 영향 노드 목록 표시

### 10.4 Layout과 오류 보고

- [x] **R5-030 · P0** Wrapper 기반 `LayoutBuilder` 구현
- [x] **R5-031 · P0** Auto Layout, gap, padding, alignment 적용
- [x] **R5-032 · P1** responsive breakpoint를 Figma 표현으로 투영
- [x] **R5-033 · P1** overflow, fixed/sticky 의미를 주석 또는 메타데이터로 보존
- [x] **R5-034 · P0** Validation·Import 오류 `ErrorReporter` 구현
- [x] **R5-035 · P0** 오류와 변경 내역에 `screenId`, `logicalNodeId`, component logical name 포함
- [x] **R5-036 · P1** 재사용·신규·Archive·fallback 지표를 포함한 `figma-generation-report-v1` JSON 내보내기

### 10.5 R5 테스트

- [x] **R5-T01** 모든 공통 컴포넌트가 Published Instance로 생성되는지 검증 — (2026-08-17 확인) `test/runtime-bundle-preview.test.mjs`가 실제 Q&A 7화면 Runtime Bundle에서 `unresolvedCount`/`fallbackCount`가 모두 0임을 이미 검증하고 있었음(신규 테스트 아님, 체크박스만 갱신되지 않았던 기존 증거)
- [x] **R5-T02** 순수 Reconciliation 테스트에서 동일 논리 노드 `REUSE` 판정 검증 — (2026-08-18) Figma Desktop에서 동일 `qna-list` Bundle을 재적용해 재사용 36·신규 0·Archive 0·Fallback 0을 확인하고 Generation Report를 `docs/figma/evidence/2026-08-18-web-capture/`에 보관
- [~] **R5-T03** 순수 Reconciliation 테스트에서 신규 논리 노드만 `ADD` 판정 검증. Figma Desktop 런타임 검증 필요
- [x] **R5-T04** 사용자 텍스트·위치 override 보존 검증 — (2026-08-17) `applyOwnedProperties()`의 판정 로직을 `core.ts`의 순수 함수 `isUserOverridden(previousManagedValue, currentValue)`로 추출해 `code.ts`가 재사용하도록 리팩터링, `core.test.mjs`에 단위테스트 추가. Figma Desktop 실제 Instance에서의 재실행 검증은 R5-T02/T03과 동일하게 별도 수동 QA 필요
- [x] **R5-T05** MERGE와 REPLACE 결과 비교 검증 — (2026-08-17) `core.ts`의 `reconcile()`이 `existing`(MERGE의 기존 노드 목록)과 무관하게 desired 트리(`flattenSpec`)에서만 `logicalNodeId`를 부여함을 `core.test.mjs`의 신규 테스트로 증명 — MERGE(existing 채움)와 REPLACE(existing=[])가 ARCHIVE를 제외한 동일 `logicalNodeId` 집합을 생성함을 확인. `17_Semantic_Figma_Generation_Pipeline_Manual_Refinement_Implementation_List.md`의 `MR-R09`가 의존하던 전제를 직접 검증함(단, Figma Desktop에서 실제 REPLACE를 실행한 E2E는 아직 없음 — MR-R09 자체 근거 참고)
- [x] **R5-T06** 필수 Component 누락 FATAL·적용 차단, 선택 Component 시각 fallback(Placeholder 생성·Registry 갱신 시 정식 Instance로 교체) `core.ts` 순수 로직 검증 완료. Figma Desktop 실제 렌더(Placeholder 시각 확인)는 수동 QA 필요

### 10.6 디자인 Operation 적용

- [x] **R5-040 · P0** `FigmaDesignOperation`/Bundle의 `operationId`, request type, source revision을 읽고 Preview에 표시 — (2026-08-17) `FigmaExportMetadata.operationId` 신설 + `FigmaExportBundle.withOperationId()` + `FigmaDesignOrchestrationService`가 Bundle 저장 전 새겨 넣음. Plugin `code.ts:loadBundleAndPreview()`가 `GET /api/figma/operations/{operationId}/info`를 호출해 `PREVIEW_READY` 메시지에 `operation` 필드로 표시. `FigmaDesignOrchestrationServiceTest` 갱신, Plugin typecheck/lint/build/test(49건) 통과
- [x] **R5-041 · P0** MCP 분석 완료와 Plugin Apply 완료 상태를 분리하고 실제 적용 후에만 `APPLIED` 보고서 생성 — (2026-08-17) 기존 `/applied-report`가 `PREVIEW_READY→APPLY_REQUIRED` 전이 경로 부재로 항상 실패하던 것을 발견 — `FigmaDesignOperationService.requestApply()` + `POST /api/figma/operations/{operationId}/apply-requested` 신설. Plugin이 Apply 시작 전 이 API를 호출한 뒤 캔버스 적용, 성공 후에만 `/applied-report` 호출. `FigmaDesignOperationServiceTest`/`FigmaDesignOperationRepositoryIntegrationTest` 통과
- [x] **R5-042 · P0** `modify_existing_design`의 `editableNodeIds`가 현재 file/page/승인 범위와 일치하는지 재검증 — (2026-08-17) 기존 `validateEditableNodeIds`(존재 여부)에 더해 `validateAffectedNodesWithinScope()` 신설: Plugin이 실제 보고한 `affectedNodeIds`가 승인된 `editableNodeIds` 범위를 벗어나면 CONFLICT. `affectedNodeOutsideEditableScopeRecordsConflictAndBlocksApply` 테스트로 검증
- [x] **R5-043 · P1** 멀티 스크린 Operation을 전체 Preview 성공 후 일괄 Apply하며 중간 실패 시 부분 적용 방지 — (2026-08-19) 기존 `planMultiScreenApply()`/`applyMultiScreenBundles()` 원자성 경로에 더해 `GET /api/figma/operations/{operationId}/bundles`를 신설하고, Plugin UI의 `Operation Bundle 일괄 조회`가 서버의 화면별 Bundle을 반복 다운로드 없이 한 번에 `LOAD_MULTI_BUNDLE`로 전달하도록 연결. 서버는 MULTI_SCREEN_FLOW 및 `FIGMA_EXPORT_BUNDLE` artifact만 허용하고 저장소 상대경로 탈출을 차단한다. Plugin typecheck/build/refinement test 및 Java 전체 테스트 통과.
- [x] **R5-044 · P1**(Component Swap만) 플랫폼 변환 결과의 Component Swap 적용 — (2026-08-17) `FigmaPlatformConversionService.convert()`가 계산한 Swap 결정을 Plugin `core.ts:applyComponentSwaps()`(순수함수, Registry 미존재 시 원본 유지)로 노드 트리에 반영하는 경로 구현·테스트 완료. **Grid·Navigation 실제 적용과 annotation은 미완료** — (2026-08-18) PLATFORM_CONVERT의 서버 측 Bundle 생성(R6-038)이 이제 실제로 존재한다: `FigmaDesignOrchestrationService.generateFromPlatformConversion()`이 `screenSpecificationId` 기준 exportBundle 뒤 Plugin `applyComponentSwaps()`의 Java판(같은 패키지 `applyComponentSwaps`/`collectLogicalTypes`, 테스트 접근을 위해 package-private)으로 `FigmaNodeSpec` 트리를 재작성하고 새 `FigmaExportBundle`을 저장한다. 단, `FigmaPlatformConversionService.convert(platform, logicalTypes)`가 쓰는 `defaultPolicy()`는 Swap 규칙이 비어 있어 실제 운영 경로에서는 아직 Swap이 발생하지 않는다(정책 자체가 비어있는 것이지 트리 재작성 로직의 한계는 아님 — `applyComponentSwaps*` 단위 테스트로 로직은 별도 검증됨). **Grid·Navigation 재계산은 여전히 미구현**(`FigmaScreenSpec`에 width/gridColumns 필드가 없어 R5-044와 동일한 경계) — Swap이 발생하면 `PLATFORM_CONVERT_GRID_NOT_RECALCULATED` WARNING Issue로 명시
- [x] **R5-045 · P1** 참조 Style Token 후보와 운영 Profile Token의 차이를 Preview에 표시하고 자동 Library 변경 금지 — (2026-08-17) 신규 `StyleTokenDiffService`(MATCHED/NEW_CANDIDATE/UNBOUND_IN_PROFILE 3분류)가 기존에 호출자가 없던 `FigmaStyleExtractor`를 실제로 소비. `FigmaMcpFacadeService.previewStyleTokenDiff()` + `DesignSystemTool.previewStyleTokenDiff` MCP 조회 전용 Tool 신설(Profile/Library 어느 쪽도 쓰지 않음). `StyleTokenDiffServiceTest` 5건 통과
- [x] **R5-T07** source revision 불일치·다른 파일 Node ID·미승인 Component가 Apply 전에 차단된다 — 기존 `missingEditableNodeRecordsConflictAndBlocksApply`/`nonNotFoundFigmaErrorPropagatesWithoutConflict`에 (2026-08-17) `affectedNodeOutsideEditableScopeRecordsConflictAndBlocksApply` 추가로 종합 커버
- [~] **R5-T08** 7가지 요청 fixture의 Preview diff와 실제 Reconciliation 결과가 일치한다. — (2026-08-19) 기존 7종 Bundle 생성 테스트와 Plugin `core.test.mjs` 교차 시나리오에 더해 Figma Desktop `eGovFrame`의 `qna-list · egov.listPage · 1440px` 후보를 실제 선택하고 Tablet 768px/Mobile 390px 복제 fixture 생성까지 통과했다(`docs/figma/evidence/2026-08-18-web-capture/r5-t08-desktop-fixture-2026-08-19.md`). 다만 7종 전체를 동일 화면에 순차 교차 Apply하는 실제 Reconciliation fixture와 Mobile Table→Card 시각 확인은 잔여
- [x] **R5-T09** 다중 화면 Apply가 전부 성공하거나 전부 실패하고 재시도 시 중복 노드가 생기지 않는다 — (2026-08-17) `core.test.mjs`에 `planMultiScreenApply` 순수함수 테스트 4건 신설(전체 통과/FATAL 하나로 전체 차단/미승인 상태로 전체 차단/빈 목록 거부). 재시도 시 중복 없음은 배치 rollback이 사전 상태를 완전히 복원하고 기존 `reconcile()` 재사용 보장에 의존(Figma Desktop 런타임 전용 검증은 R5-T02/T03과 동일하게 별도 필요)

---

## 11. R6 — REST API와 MCP Tool

### 11.1 REST API

- [x] **R6-001 · P0** `GET /api/figma/screens/{screenId}` 구현
- [x] **R6-002 · P0** `GET /api/figma/screens/{screenId}/versions/{version}` 구현
- [x] **R6-003 · P0** `GET /api/figma/screens/{screenId}/download` 구현: 최신/특정 버전 `.figma-export-bundle.json`, UTF-8 Content-Disposition
- [x] **R6-004 · P0** `POST /api/figma/screens/{screenId}/validate` 구현
- [x] **R6-005 · P1** DesignSystemProfile 최신·버전 조회 API 구현
- [x] **R6-006 · P1** ComponentRegistry 최신·버전 조회 및 Preview·확정 동기화 API 구현
- [x] **R6-007 · P1** Preview 검토 상태·승인 이력 저장·조회 API 구현
- [x] **R6-008 · P1** 생성 보고서 업로드·조회 API 구현
- [x] **R6-009 · P0** Figma 리소스 404 표준 오류(`code`/`message`/`path`/`timestamp`) 적용
- [x] **R6-010 · P0** `/api/figma/**`·`/api/design-systems/**` X-API-Key 인증 및 stateless API CSRF 제외 적용
- [x] **R6-011 · P0** DEC-10 파일 우선 경로 구현: 인증된 Bundle 다운로드 → R5 Plugin 파일 Import
- [x] **R6-012 · P1** REST 선택 시 단기 토큰·CORS·재시도·오프라인 fallback 구현
- [x] **R6-013 · P0** 신규 Figma/DesignSystem MCP Tool 전용 인증 구현: `/mcp/**` 전역 정책은 유지하고 `FIGMA_MCP_SHARED_SECRET`을 Tool facade 진입 시 상수시간 비교. 미설정 상태는 기본 거부

### 11.2 MCP Tool

- [x] **R6-020 · P1** Screen Specification → FigmaScreenSpec 생성 Tool 구현
- [x] **R6-021 · P1** FigmaScreenSpec 검증 Tool 구현
- [x] **R6-022 · P1** DesignSystemSpec 검증 Tool 구현
- [x] **R6-023 · P1** Registry Publish 상태·Profile 연결 드리프트 점검 Tool 구현(공개 Key 원문 미노출)
- [x] **R6-024 · P1** `McpConfig.allToolCallbacks`에 `FigmaExportTool`·`DesignSystemTool` 4개 callback 등록
- [x] **R6-025 · P0** Tool 설명에 Publish/삭제/교체의 사람 승인 조건 명시
- [x] **R6-026 · P1** Tool 결과에 검증·감사 요약과 `artifactId`·상대 산출물 경로 포함

### 11.3 R6 테스트

- [x] **R6-T01** Controller 404 표준 오류·X-API-Key 허용/거부 테스트
- [x] **R6-T02** UTF-8 다운로드 헤더·Content-Type·JSON 본문 무결성 테스트
- [x] **R6-T03** Spring Context에서 신규 MCP Tool callback 4개 등록 테스트
- [x] **R6-T04** 전체 Spring Context와 실제 Streamable HTTP JSON-RPC E2E 통과 — `scripts/web-capture-mcp-e2e.mjs`가 실제 MCP client 방식으로 initialize handshake, `Mcp-Session-Id`, `tools/list`, `tools/call`, SSE/JSON 응답을 실행하고 Web Capture→figpack→Screen Specification까지 검증한다.
- [x] **R6-T05** MCP Registry 감사 응답에서 Component/Variable Key 미노출 검증 + 전체 로그·산출물 redaction 감사 완료(2026-07-28, [13번 문서](./13_Semantic_Figma_Operations_Runbook.md) §11) — 실제 노출 없음 확인
- [x] **R6-T06** DEC-10=FILE 기본 경로의 REST Bundle 다운로드와 R5 Plugin 파일 입력 테스트 통과. 선택 기능인 REST 직접 조회도 단기 토큰·CORS·재시도·파일 fallback 테스트로 검증
- [x] **R6-T07** 잘못된/누락 공유 비밀키를 Repository 접근 전에 거부하고 Registry 공개 Key가 MCP 응답에 포함되지 않는지 검증

### 11.4 7가지 디자인 요청 오케스트레이션 (2-A 범위: 2-A1~6)

**2-A1: FigmaApiClient 확장 (3시간)**
- [x] **R6-040 · P0** 기존 `FigmaApiClient` 확장: 현재 단일 Node 조회·timeout·retry/backoff를 재사용하고 files/images/styles/components·pagination·오류 정규화 추가 — `FigmaApiQuery`, `FigmaStylesResponse`, `FigmaComponentsResponse`, `queryStyles()`, `queryComponents()`, 제네릭 `callApi<T>()` 추가. (2026-08-18) `queryNodesPaginated()`가 offset 변수를 계산만 하고 버리는 채 단일 nodeId만 처리하던 스텁이었음을 발견 — `FigmaApiQuery`에 `nodeIds` 리스트 필드(하위 호환 생성자 유지) + `resolvedNodeIds()` 신설, `GET /nodes?ids=콤마구분다중ID`(기존 `findMissingNodeIds`와 동일 패턴)를 재사용해 전체 nodeId 목록을 `page`/`pageSize` 단위로 실제 슬라이싱하고 삭제된 노드는 건너뛰도록 구현. 기존 `callApi()`를 그대로 써서 응답 크기 제한·재시도가 자동으로 적용됨. `FigmaApiClientTest`에 다중 노드 단일 호출·페이지 슬라이싱·범위 밖 page의 HTTP 호출 생략·삭제 노드 skip·크기 제한/재시도 테스트 6건 추가

**2-A2: Allowlist 검증 (2시간)**
- [x] **R6-041 · P0** Figma access token·LLM key·image URL·node/file 식별자의 로그/응답/산출물 redaction 및 allowlist 검증 — (2026-08-17) 조사 결과 이미 3개의 부분 구현이 흩어져 있었음: `FigmaResponseRedactor`(정규식 기반 마스킹)는 정의만 있고 아무 데서도 호출되지 않는 죽은 코드였고, `FigmaFileAllowlistValidator`의 fileKey allowlist는 `FigmaDesignOrchestrationService`에 이미 연결돼 있었으나 `validateNodeId()`는 정의만 있고 `referenceNodeIds`/`imageNodeIds`에는 형식 검증이 전혀 적용되지 않고 있었음(더 정확한 기존 `FigmaNodeIds` 정규화기가 `editableNodeIds`에만 적용됨). 이번에 (1) `FigmaContextAnalyzer`의 LLM 실패·파싱 실패 예외 메시지를 `FigmaResponseRedactor`로 redact, (2) `referenceNodeIds`/`imageNodeIds`도 `FigmaNodeIds.normalizeAll()`로 정규화·형식 검증하도록 확장, (3) 지금까지 핸들러가 없어 일반 500으로 새던 `FigmaAllowlistException`에 403 표준 오류 핸들러 추가. `FigmaResponseRedactorTest`/`FigmaContextAnalyzerTest`/`GlobalExceptionHandlerTest`/`FigmaDesignOrchestrationServiceTest` 신규·확장 테스트로 검증

**2-A3: FigmaContextAnalyzer (3시간)**
- [x] **R6-042 · P0** `FigmaContextAnalyzer` 구현: Spring AI 구조화 출력으로 domain, screenType, layoutPattern, required logical types, uncertainty 반환 — (2026-08-17) 이전 버전은 이미 존재했으나 `.content()`로 원문 문자열을 받아 `{`/`}` 위치로 수동 JSON 추출·`ObjectMapper.readTree()`로 파싱하고, `screenType`/`layoutPattern`도 enum이 아닌 자유 문자열이었으며 `domain` 필드 자체가 없었음. `.entity(LlmAnalysisResponse.class)` 구조화 출력으로 전환하고 기존 `FigmaScreenType`/`LayoutPattern` enum을 직접 역직렬화하도록 변경, `domain` 필드 추가. `FigmaContextAnalyzerTest`로 정상/불확실성/빈응답/redaction 경로 검증

**2-A4: FigmaStyleExtractor (2시간)**
- [x] **R6-043 · P1** `FigmaStyleExtractor` 구현: 기존 `FigmaDesignSpecMapper`의 layout/token 추출을 재사용해 공통 color/typography/spacing/layout을 Profile 변경이 아닌 Token 후보로 확장 — (2026-08-18 재조사) 이미 완전히 구현·연결돼 있었다: `extractTokens()`가 color/typography/spacing/radius를 `DesignTokenExtraction`(Token 후보, Profile 미변경)으로 추출하고, R5-045에서 신설된 `StyleTokenDiffService`가 이를 실제로 소비해 MATCHED/NEW_CANDIDATE/UNBOUND_IN_PROFILE로 분류한다. `StyleTokenDiffServiceTest` 5건이 간접 검증(전용 단위 테스트는 없음). R5-045가 이미 `[x]`였는데 R6-043 자체 체크박스만 갱신되지 않았던 상태

**2-A5: 요청 흐름 연결 (3시간)**
- [x] **R6-030 · P0** `FigmaDesignRequestRouter`는 `FigmaDesignOrchestrationService.processExplicitRequest()`에서 실제 사용되며, 명시 타입과 `referenceNodeIds`·`editableNodeIds`·`components` 문맥의 일관성을 검증한다. 자유 텍스트 분석은 `NaturalLanguageDesignAnalyzer`가 담당하고 Router는 요청 계약의 명시/컨텍스트 판정기로 역할을 확정했다. `FigmaDesignRequestRouterTest`와 Orchestration 경로 테스트로 명시 타입 우선·문맥 fallback·`TEXT_DESCRIPTION` 기본값을 검증
- [x] **R6-031 · P0** `FigmaDesignOrchestrationService` 구현 — 7개 요청은 canonical hash 기반 영속 `ANALYZED` 승인 후보로 저장된다. (2026-08-19) 7/7종이 `ANALYZED` 이후 ScreenSpecification(또는 기존 명세)→FigmaScreenSpec→Bundle→불변 Artifact→영속 `PREVIEW_READY`로 연결되며, TEXT_DESCRIPTION은 `NaturalLanguageDesignAnalyzer`와 `NaturalLanguageTableResolver`를 통해 명시 바인딩 또는 catalog 후보 기반 구조화 LLM 선택까지 연결된다. 필드 단위 자유형 diff는 의도적으로 별도 범위다.
- [x] **R6-032 · P0** `create_design_from_text(prompt, platform)` MCP callback 구현 — 명시적 `database.table`/`database=`/`tableName=` 바인딩은 결정론적으로 추출하고, 바인딩이 없는 업무명 자연어는 `INFORMATION_SCHEMA.TABLES` 후보를 조회한 뒤 Spring AI 구조화 출력으로 테이블을 선택한다. 후보 밖 테이블·confidence 0.60 미만은 거부하며, 선택 결과를 TEXT_DESCRIPTION→ScreenSpecification→Figma Bundle 경로로 연결한다. `NaturalLanguageDesignAnalyzerTest`/`NaturalLanguageTableResolverTest`로 허용·거부 경계를 검증했다.
- [x] **R6-033 · P1** `create_design_from_reference(prompt, referenceNodeIds)` MCP callback 구현 — (2026-08-18) `FigmaDesignRequest`에 `database`/`tableName`/`screenName`/`featureType` 필드 신설(기존 CRUD 생성 아키텍처와 통일하는 방향으로 DEC 재확정). 신규 `FigmaDesignOrchestrationService.generateBundle()`이 `referenceNodeIds[0]`로 합성 Figma URL을 만들어 기존 `analyzeFigmaReference`를 그대로 호출하고, 결과 `UiDesignSpec`으로 `createScreenSpecification`→(APPROVED면) `exportBundle`까지 실제로 연결. REVIEW_REQUIRED면 REJECTED로 멈추고 `screenSpecificationId`와 함께 기존 `reviseScreenSpecification`/`approveScreenSpecification`/`createFigmaBundleFromApprovedSpecification` 수동 경로로 안내. 신규 MCP Tool `generateFigmaBundleForOperation`이 이 2단계(ANALYZED 검증 → 실제 생성)를 잇는다. `FigmaDesignOrchestrationServiceTest`로 검증
- [x] **R6-034 · P1** `modify_existing_design(prompt, editableNodeIds)` MCP callback 구현 — (2026-08-18) `screenSpecificationId`(신규 필드, 호출자가 직접 지정 — nodeId→화면명세 자동 매핑은 없음)로 기존 화면명세를 조회해 현재 DB 스키마 기준으로 `revise()` 재검증·재생성 후 Bundle까지 연결. **자유 텍스트 prompt를 필드 단위 변경으로 해석하는 LLM diff 엔진은 없음** — "무엇을 바꿀지"는 항상 최신 DB 스키마가 결정하며, 이 호출은 스키마 변경분 재동기화 성격에 가깝다(의도적 범위 제한, 문서화된 한계)
- [x] **R6-035 · P2** `create_design_from_image(prompt, imageNodeIds)` MCP callback 구현 — (2026-08-18) R6-T10에서 신설한 `FigmaApiClient.queryImages()`로 첫 번째 `imageNodeId`의 렌더 URL을 조회하고, `app.design-vision.allowed-paths`에 이미 포함된 `java.io.tmpdir`로 실제 다운로드한 뒤 기존 `analyzeDesignReference`(PNG/JPEG Vision 분석)를 그대로 호출·정리(finally에서 임시 파일 삭제). 이후 R6-033과 동일하게 ScreenSpecification→Bundle 경로 연결. 로컬 stub HTTP 서버로 실제 다운로드 경로까지 검증하는 테스트 포함(`generateBundleForImageReferenceDownloadsAndAnalyzesFirstImageNode`)
- [x] **R6-036 · P2** `create_multi_screen_flow(prompt, screens[])` MCP callback 구현 — (2026-08-18) `FigmaScreenRequest`에 화면별 `database`/`tableName` 필드 신설. `generateBundle()`이 화면마다 독립적으로 ScreenSpecification 생성→Bundle export를 수행하고, 하나라도 실패(DB 미지정·REVIEW_REQUIRED·export 오류)하면 이미 성공한 화면이 있어도 Operation 전체를 REJECTED로 막는다(all-or-nothing). 개별 Bundle 파일은 저장소에 남을 수 있으나 Operation이 참조하지 않아 Plugin Apply 대상이 되지 않음(진짜 파일 삭제 rollback은 아님, 명확히 문서화)
- [x] **R6-037 · P1** `create_design_with_components(prompt, componentLogicalTypes[])` MCP callback 구현 — (2026-08-18) `generateBundle()`이 기존 `ComponentRegistryResolver`로 요청 컴포넌트 전부가 `krds` Registry에서 해석되는지(alias·replacement 포함) 먼저 검증하고, 하나라도 실패하면 REJECTED + `COMPONENT_NOT_IN_REGISTRY` Issue. 전부 통과하면 R6-033과 동일한 DB 테이블 기반 생성 경로로 진행(단, 지정 컴포넌트를 화면에 직접 배치하도록 강제하는 기능은 없음 — Registry allowlist 검증 통과가 전제조건이라는 뜻이며, 실제 컴포넌트 선택은 기존 `FieldComponentMapper`가 필드 역할 기준으로 자동 수행)
- [x] **R6-038 · P2**(Component Swap만) `convert_platform(sourceNodeIds, targetPlatform)` MCP callback 구현 — (2026-08-18) `generateBundle()` dispatcher에 `generateFromPlatformConversion()` 연결 완료. `screenSpecificationId`(신규, 직접 지정 필수 — nodeId→화면명세 자동 매핑 없음) 기준으로 APPROVED 화면명세를 DESKTOP viewport로 `exportBundle()`한 뒤, Plugin `core.ts:applyComponentSwaps()`의 Java판(`FigmaDesignOrchestrationService.applyComponentSwaps`/`collectLogicalTypes`, 테스트 직접 접근을 위해 package-private)으로 `FigmaNodeSpec` 트리를 불변 레코드로 재조립하고 `screenId`에 `-{platform}` suffix를 붙인 새 `FigmaExportBundle`을 Artifact로 저장한다. `convertPlatform` MCP Tool에 `screenSpecificationId` 파라미터 추가(생략 시 ANALYZED에서 멈춤). `FigmaDesignOrchestrationServiceTest`에 필수값 누락/미승인 명세/성공 경로 및 `applyComponentSwaps`/`collectLogicalTypes` 단위 테스트 8건 추가, MCP Tool 스냅샷 baseline 재생성 확인. **Grid·Navigation 폭·열 수 재계산은 Plugin 쪽 R5-044와 동일하게 의도적으로 범위 밖**(`FigmaScreenSpec`에 해당 필드가 없음 — Swap 발생 시 `PLATFORM_CONVERT_GRID_NOT_RECALCULATED` WARNING Issue로 명시). 또한 `FigmaPlatformConversionService.convert(platform, logicalTypes)` 2-arg 오버로드가 항상 `defaultPolicy()`(빈 Swap 규칙)를 쓰므로 실제 운영 경로에서 Swap이 발동하려면 조직이 Swap 규칙을 승인한 정책을 주입하는 경로가 별도로 필요(현재 없음 — 트리 재작성 로직 자체는 hand-crafted swap map으로 직접 단위 검증됨)

**2-A6: Redaction 정책 (1.5시간)**
- [x] **R6-044 · P1** 기존 `ComponentRegistryResolver` 확장: Registry·승인 Catalog·Default Layout Policy 교집합과 요청 allowlist·필수/선택 정책 적용 — (2026-08-18 재조사) `ComponentRegistryResolver.resolve(ComponentRegistrySnapshotV3, Set<String>)`가 위임하는 `ResolvedComponentRegistryService`가 이미 승인 Catalog(v2)와 Registry Binding을 결합해 요청 allowlist만 반환(`resolve()`)하고, alias 해석·합성(Pattern) 재귀 전개·필수/선택 정책(`ComponentRegistryOptionalFallbackPolicy` — REQUIRED 누락은 차단, OPTIONAL 누락은 Preview에서만 WARNING fallback)까지 구현돼 있었으나 `ResolvedComponentRegistryServiceTest`가 아예 없어 증적이 없던 gap이었다. 신규 테스트 12건(allowlist 필터링·alias·합성 전개(단일/다단)·미지 타입 거부·미승인 Registry 거부·필수 누락 차단·선택 누락 fallback·registry=null)으로 메움. **"Default Layout Policy 교집합"** 부분은 실제 구현된 `DefaultLayoutPolicy`(R1-015)가 Grid/Gap/Padding/Density만 갖고 컴포넌트 목록 자체가 없어 개념적으로 교집합 대상이 아님(원 계획 문서의 초기 스케치와 실제 채택된 모델의 차이 — 컴포넌트 allowlist와 화면 레이아웃 정책은 서로 다른 관심사로 분리됨)
- [x] **R6-045 · P2** 기존 `DesignReferenceAnalysisService`·`VisionAnalysisClient` 확장: Vision capability 사전 점검, Figma 이미지 export 분석, 불확실성·접근성 Issue 반환 — (2026-08-18) Figma 이미지 export 분석(R6-035)·불확실성 반환(`UiDesignSpec.uncertainties()`)은 이미 있었음. **Vision capability 사전 점검**만 없었음(설정된 모델이 이미지 입력을 지원하는지 실제 API 호출 전에 확인하는 경로 부재) — `VisionAnalysisClient`에 `supportsVision()` default 메서드 + 신규 `VisionModelCapability`(알려진 Vision 모델 이름 접두사 목록과 대조하는 결정론적 판정, 원격 조회 없음) 추가. `DesignReferenceAnalysisService.analyzeWithTimeout()`이 실제 API 호출(과 과금) 전에 이를 검사해 미지원 모델이면 `VISION_MODEL_NOT_SUPPORTED` 코드로 즉시 실패(기존에는 provider별로 제각각인 원시 예외가 그대로 노출됨). `DesignReferenceAnalysisServiceTest`에 사전 차단·모델 판정 테스트 2건 추가. 접근성 Issue 반환은 대상 코드가 없어(Vision 분석 결과에 접근성 판정 개념 자체가 없음) 범위 밖으로 재확인
- [x] **R6-046 · P2** `FigmaPlatformConversionService` 구현: Desktop 1440/12열, Tablet 768/8열, Mobile 390/4열 초기 정책과 Profile 기반 swap 적용 — (2026-08-17) 신규 서비스가 기존에 호출자가 없던 `PlatformLayoutPolicy`/`ComponentSwapPolicyResolver`를 실제로 연결. Grid 폭·열수는 Thymeleaf 흐름과 동일한 `ResponsiveBreakpointPolicy` 상수를 그대로 재사용, Navigation 명칭은 `ViewportConstraint.NavigationType`(SIDE_NAV/DRAWER/BOTTOM_NAV)과 통일. `convertPlatform` 요청 검증과 신규 조회 전용 `previewPlatformConversion` MCP Tool에 연결. `FigmaPlatformConversionServiceTest` 통과
- [x] **R6-047 · P1** 모든 Tool 응답에 `operationId`, `artifactId`, preview summary, issues, `PREVIEW_READY`/`APPLY_REQUIRED` 상태를 포함하고 캔버스 적용 전 `APPLIED` 반환 금지 — (2026-08-17) 7개 callback이 반환하는 `FigmaDesignOperation`에는 operationId/status/issues/artifacts(artifactId 포함)가 이미 있었으나 `APPLY_REQUIRED` 상태 자체에 도달하는 전이가 없어 죽은 필드였음 — R5-041의 `requestApply()` 신설로 실제 도달 가능해짐. 별도 `previewSummary` 필드는 추가하지 않고 기존 `artifacts[].uri`/`issues`로 대체(신규 계약 필드 추가에 따른 Schema 리스크 회피 판단). 캔버스 적용 전 `APPLIED` 반환 금지는 `R6-T11`이 이미 검증
- [x] **R6-039 · P0** 7개 callback과 승인 ScreenSpecification Bundle callback 등록 완료. 8개 모두 `figmaMcpSecret`을 Repository/오케스트레이션 접근 전에 상수시간 비교로 검증하며 MCP snapshot(97 methods/35 objects) 갱신 완료
- [x] **R6-048 · P1** 웹 UI·CLI·Webhook 클라이언트가 동일 MCP/REST 계약을 사용하도록 transport-neutral facade 유지. 별도 React/Slack/Teams 클라이언트 구현은 후속 범위 — (2026-08-17 재조사) REST가 Component/Variable Key를 redaction 없이 그대로 반환하는 것은 버그가 아니라 DEC-07 감사에서 이미 확정된 의도된 설계(REST=사람 검토용 원문 노출, MCP=LLM 컨텍스트 보호용 redaction)임을 재확인 — 코드 변경 없음. `FigmaMcpV2RedactionTest`에 REST/MCP가 Key 3종 유무만 다르고 나머지 필드는 완전히 동일한 계약임을 고정하는 회귀 테스트(`restAndMcpShareTheSameFieldShapeAndDifferOnlyByKeyRedaction`) 신설
- [x] **R6-T08** 7개 callback 입력 Schema snapshot, 인증 선행, 고급 요청 필수 목록·미지원 platform의 Repository 접근 전 거부 및 file/page node 소속 검증 완료. `FigmaDesignRequest.pageId`를 하위 호환 선택 필드로 확장하고, 지정 시 file allowlist 이후 `FigmaApiClient.validateNodeBelongsToPage()`가 각 reference/editable/image node의 `parent` ancestry를 따라 실제 `PAGE` ID를 검증한다. 불일치는 `FIGMA_PAGE_MISMATCH`, ancestry 누락은 `FIGMA_PAGE_ANCESTRY_UNAVAILABLE`로 fail-closed 처리하며 회귀 테스트를 추가했다. Vision capability 오류는 R6-045/R6-T09의 `VISION_MODEL_NOT_SUPPORTED` 계약을 사용한다.
- [x] **R6-T09** Spring AI 구조화 출력 오류·timeout·rate limit·Vision 미지원 모델 fallback 테스트 — (2026-08-17) `FigmaContextAnalyzerTest`에 timeout/rate-limit 오류가 기존 catch-all을 통해 uncertain fallback으로 흡수되고 redaction되는 테스트 2건 추가. (2026-08-18) **Vision 미지원 모델 fallback**: R6-045에서 신설한 `VisionModelCapability.supports()` 사전 점검으로 해결 — `OpenAiVisionAnalysisClient`/`OllamaVisionAnalysisClient`가 미지원 모델로 설정되면 실제 API 호출 전에 `VISION_MODEL_NOT_SUPPORTED`로 즉시 실패(과금·rate limit 소모 없음). `DesignReferenceAnalysisServiceTest`의 `rejectsUnsupportedVisionModelBeforeCallingClient`/`supportsVisionMatchesKnownVisionModelPrefixesOnly`로 검증
- [x] **R6-T10** Figma REST pagination·429 retry/backoff·권한 오류·만료 이미지 URL 테스트 — (2026-08-17) 전무했던 `GET /v1/images/{fileKey}` 이미지 URL 조회를 `FigmaApiClient.queryImages()`로 신규 구현(개별 노드 렌더 실패·전체 오류·TTL 만료 판정 포함)하고 429/403 등 기존 `callApi()` 재시도·오류 매핑을 재사용 확인. `FigmaApiClientTest`에 6건 추가. (2026-08-18) **Team Components/Styles cursor pagination 구현 완료**: `queryTeamComponents(teamId, afterCursor, pageSize)`/`queryTeamStyles(...)`가 `GET /v1/teams/{teamId}/components|styles?page_size=...&after=...`를 호출하고, `queryAllTeamComponents`/`queryAllTeamStyles`가 `cursor.after`를 따라 반복 조회한다. 응답의 `cursor.after`가 방금 요청에 쓴 cursor와 같아지면(진행 없음) 그 페이지는 버리고 즉시 멈춰 중복·무한 루프를 막는다(`maxPages`는 최후 방어선). `FigmaApiClientTest`에 8건 추가(단일 페이지 조회·teamId 필수·정상 다중 페이지 반복·진행 없음 감지 시 중복 없이 중단·403 오류 매핑 등). `queryNodesPaginated`(호출자 제공 nodeId 목록을 Java에서 분할)와 file-level `queryComponents`/`queryStyles`는 원래부터 cursor 없는 별개 endpoint라 이 항목의 대상이 아니었음을 재확인.
- [x] **R6-T11** 분석 요청은 `ANALYZED`, 승인 Bundle은 `PREVIEW_READY`까지만 반환하며 Repository 상태 테스트에서 `APPLY_REQUIRED`와 유효 Plugin 보고 없이는 `APPLIED` 전이가 거부됨을 검증
- [x] **R6-T12** 지정 컴포넌트 요청이 승인되지 않은 logical type과 로컬 Node ID 직접 지정을 거부하는지 검증 — (2026-08-17) `FigmaNodeIds.isNodeIdShaped()` 신설 + `FigmaDesignOrchestrationService`의 COMPONENT_SPECIFIED 검증이 원시 Figma nodeId를 `components`에 직접 지정하면 저장 전에 거부하도록 구현. `componentSpecifiedRejectsRawFigmaNodeIdInsteadOfLogicalType`/`componentSpecifiedAcceptsLogicalTypesOnly` 테스트로 검증(미승인 logical type 거부는 기존 `ComponentRegistryResolver`가 이미 커버)
- [x] **R6-T13** 플랫폼 변환 golden fixture에서 폭·Grid·Navigation·componentSwaps가 Profile 정책과 일치하는지 검증 — (2026-08-17) `FigmaPlatformConversionServiceTest`가 Desktop/Tablet/Mobile 각각의 viewportWidth·gridColumns·navigationStyle이 공유 상수·`ViewportConstraint`와 일치함을, `convertAppliesConfiguredComponentSwapForTargetPlatform`이 Profile 기반 swap 적용을 검증

### 11.4.1 REFERENCE_STYLE/IMAGE_REFERENCE DB 테이블 바인딩 지연(`AWAITING_TABLE_BINDING`)

**배경**: "DB 테이블 바인딩으로 통일" 결정(2026-08-17, §19 변경이력 4.1 참고)에 따라
REFERENCE_STYLE(R6-033)·IMAGE_REFERENCE(R6-035)도 `database`/`tableName`을 먼저 알아야
생성이 시작됐다. 디자인(참조 화면/이미지)을 먼저 보고 테이블은 사람이 나중에 고르는
워크플로우를 지원하지 않는 한계가 있었다 — R6-032가 의도적으로 남겨둔 "자연어→테이블
자동 매핑 없음" 한계와 같은 근본 원인.

- [x] **R6-065 · P1** REFERENCE_STYLE·IMAGE_REFERENCE 요청은 `database`/`tableName`이 없어도
  분석은 그대로 실행하고, 사람이 필드 후보를 확인한 뒤 나중에 테이블을 지정할 수 있도록
  `AWAITING_TABLE_BINDING` 중간 상태를 도입한다 — (2026-08-19, 상세 구현 이력·테스트 목록은
  [23_REFERENCE_STYLE_IMAGE_REFERENCE_DB_Binding_Deferral_Implementation_List.md](./23_REFERENCE_STYLE_IMAGE_REFERENCE_DB_Binding_Deferral_Implementation_List.md) 참고)
  `FigmaDesignOperationStatus`에 `ANALYZED`와 `PREVIEW_READY` 사이 `AWAITING_TABLE_BINDING`
  상태 신설(`ANALYZED→AWAITING_TABLE_BINDING→{PREVIEW_READY,FAILED,REJECTED}` + 이후
  `AWAITING_TABLE_BINDING→ANALYZED` 재분석 전이 추가 — R6-047의 "모든 응답은 정의된 상태를
  포함" 계약은 유지). `generateFromReference()`/`generateFromImage()`에 분기를 추가해
  `database`/`tableName`이 없어도 `analyzeFigma`/`queryImages`+`analyze`는 그대로 실행하고,
  신규 `DesignFieldCandidateExtractor`(`UiDesignSpec.fieldHints()` 재사용, 별도 저장 인프라
  없이 기존 `issues` 채널 재사용)로 뽑은 필드 후보를 `FIELD_CANDIDATE` 이슈로 실어
  `AWAITING_TABLE_BINDING`으로 전이한다. **당초 계획한 전용 컬럼 매처(`FieldRoleToColumnMatcher`)는
  실제 `egov-mysql` 컬럼 데이터로 구현·테스트(12건)까지 마친 뒤, 기존
  `ScreenSpecificationService.create()`→`ScreenSpecAssembler.bindingsFromHints()`가 이미
  동일한 역할힌트↔실제컬럼 매칭과 `NO_COLUMN_CANDIDATE`→`REVIEW_REQUIRED`(→`REJECTED`)
  판정을 수행하고 있음을 발견해 중복 구현으로 확인되어 사용자 승인 후 전체 삭제**했다.
  신규 MCP Tool `bindFigmaDesignRequestTable(operationId, database, tableName)`(+ 동일 동작의
  REST `POST /api/figma/orchestration/bind-table`, 기존 X-API-Key 인증 재사용)이
  `AWAITING_TABLE_BINDING` Operation에 database/tableName을 채워 `ANALYZED`로 되돌린 뒤 기존
  `generateBundle()` 파이프라인을 그대로 재호출한다. Operation은 `createOrReuse()` 시점의
  request 해시로 identity가 고정되는 구조라 "같은 Operation에 나중에 테이블만 채워 넣기"가
  원래 불가능했음을 설계 중 발견 — `FigmaDesignRequest.withDatabaseTable()`/
  `FigmaDesignOperation.withRequestAndNextRevision()`/
  `FigmaDesignOperationRepository.appendTransitionWithRequest()` 신규 오버로드로 모델을
  확장해 해소(기존 `createOrReuse`/`appendTransition`/`withNextRevision`은 변경 없음). 필드
  후보 조회는 별도 API 없이 기존 `GET /api/figma/operations/{operationId}/info`(R5-040)가
  `issues`를 그대로 반환해 충족. REFERENCE_STYLE·IMAGE_REFERENCE 2종에만 한정되며
  COMPONENT_SPECIFIED/MODIFY_EXISTING/MULTI_SCREEN_FLOW/PLATFORM_CONVERT/TEXT_DESCRIPTION은
  기존 경로(REJECTED/예외) 그대로 유지됨을 회귀 테스트로 검증. `figma-design-operation-v1.schema.json`의
  상태 enum에도 반영. 전체 Java 테스트 스위트 통과.

### 외부 제약 분류 기준

- “이 환경에서 불가능”과 “대상 API 계약·현재 입력 모델에 없는 기능”을 구분한다.
- Desktop 앱·브라우저 자동화·픽셀 비교 인프라가 필요한 항목은 환경/인프라 제약으로 기록한다.
- Figma REST의 Team Components/Styles endpoint는 `page_size`와 `after`/`before` cursor를 제공하므로 REST API 전체에 pagination이 없다고 기록하지 않는다. 현재 프로젝트의 file-level `fileKey` 조회 경로에 cursor가 없는 경우에는 endpoint·입력 모델 범위의 보류로 기록한다.
- 제약 항목의 총계를 문서에 기재할 때는 A~D 각 그룹 수량과 E·F 별도 분류 포함 여부를 함께 명시해 산술 불일치를 방지한다.

공식 참고: [Figma Components and Styles Endpoints](https://developers.figma.com/docs/rest-api/component-endpoints/)

### 11.5 Design-aware Thymeleaf Generator

상세 단계·계약·보안·완료 기준은
[eGovFrame_JSP_to_Spring_Boot_Thymeleaf_전환_작업_명세서.md](./eGovFrame_JSP_to_Spring_Boot_Thymeleaf_전환_작업_명세서.md)를
따른다.

- [x] **R6-050 · P0** 10단계 Generator 입출력 계약과 단계별 FATAL/WARNING·중단·재시도·입력 Hash 정책 정의 — (2026-08-17) `ThymeleafGenerationStage`로 Source Analysis→Build/Render/Parity Validation의 고정 순서·입출력·재시도 정책을 폐쇄 enum으로 확정하고, `ThymeleafGenerationStageStatus`의 허용 전이, `ThymeleafGenerationStageExecution`의 Hash·시간·Artifact·Issue 증적 불변조건, `ThymeleafGenerationPipelineContract`의 `FATAL→FAILED`/`ERROR→REVIEW_REQUIRED`/`WARNING→SUCCEEDED`, 후속 단계 차단, 선행 output chain을 포함한 canonical SHA-256 input Hash와 명시적 재시도 정책을 구현했다. `ThymeleafGenerationPipelineContractTest`로 10단계 순서·상태 오류·Hash 결정성·재시도·SKIPPED 증적을 검증했으며 상세 계약은 `R6-050_Thymeleaf_10단계_파이프라인_계약.md`에 고정했다. 실제 10단계 오케스트레이션·Report 영속화는 R6-061, 서비스 미호출 결정성 검증은 R6-T20 범위로 유지
- [x] **R6-051 · P0** JSP·Controller·VO 화면 단위 분석 구현 — `LegacySourceInventoryService`(안전한 경로·예산) + `JspSourceReader`(taglib/form/EL/forEach/표시필드, 정규식 기반) + `ControllerSourceReader`(매핑/모델/반환뷰/redirect/보안, JavaParser AST) + `VoSourceReader`(필드/Lombok 접근자/Bean Validation, JavaParser AST)로 신규 구현. CSS/JS Frontend Source Graph(I-2D, `jsp-design-extractor` 모듈화)는 범위 밖으로 남김
- [x] **R6-052 · P0** `ThymeleafBindingContract` 모델과 JSP·Controller·VO reader는 유지. (2026-08-17 정정) "제거되어 재연결 필요"라던 이전 판정은 부정확했다 — 삭제된 `LegacyBindingContractAssembler`는 실제로는 `BindingContractAssembler`(468줄, `BindingContractAssemblerTest`로 검증됨)로 재구현돼 이미 `ThymeleafBindingGenerationService.preview()`에 연결돼 있었다. 이 서비스는 REST(`ThymeleafBindingGenerationController`)와 MCP(`ThymeleafBindingGenerationTool`) 양쪽에서 호출되고, 결과를 `ThymeleafProjectWorkflowService.preview()`로 그대로 넘겨 Preview→승인→Apply 흐름과도 연결된다
- [x] **R6-053 · P0**(자문 힌트만) 화면 유형 판단 — `FigmaScreenTypeResolver`·`ScreenSpecAssembler`·CRUD/Board/MasterDetail 판정을 재사용하고 근거·confidence 포함 — (2026-08-18) `ThymeleafBindingPreviewRequest.screenRole`은 여전히 호출자 명시 필수값으로 유지(자동 판정이 필수 입력을 대체하면 위험이 크다고 판단)하되, 신규 `LegacyScreenRoleResolver`가 JSP/Controller 소스 증거로 화면 유형을 추정해 `screenRole`과 어긋나면 근거·confidence를 실은 `SCREEN_ROLE_MISMATCH_WITH_SOURCE_EVIDENCE` WARNING을 Preview에 남긴다(차단하지 않음). Controller 경로 명명 규칙(`list`/`regist`/`detail` 등 접미사, `viewBaseName()`으로 여러 GET 메서드 중 해당 JSP의 메서드만 정확히 매칭)과 JSP 구조(forEach·표시 필드·데이터 입력 form) 두 신호가 일치하면 confidence 0.95, 하나만 판정 가능하면 0.7~0.8, 서로 어긋나면 0.5, 둘 다 근거 부족이면 0(미판정). **실제 `EgovEmployerList.jsp` fixture로 재현 중 검색/필터 폼(GET, modelAttribute 없음)을 데이터 입력 폼으로 오판하는 버그를 발견해 수정**(`modelAttribute` 또는 POST 제출이 있어야만 FORM 신호로 인정). `LegacyScreenRoleResolverTest` 9건 + `ThymeleafBindingGenerationServiceTest`에 실제 fixture로 mismatch WARNING이 뜨는 통합 테스트 1건 추가
- [x] **R6-054 · P0** Component Inventory 선택 — `ComponentCandidate`·`ComponentRegistryResolver`를 재사용하고 field role별 선택 근거·fallback·Published 상태 검증 추가 — (2026-08-18 재조사) `ComponentInventoryValidator.resolveComponentSelection()`이 요청 컴포넌트 키별로 Registry lifecycle 상태를 확인해 CURRENT/ACTIVE만 confidence 0.95로 확정 선택, DEPRECATED는 대체 컴포넌트가 CURRENT/ACTIVE면 0.6 fallback 제시, 대체가 없으면 확정하지 않는 로직을 이미 구현·테스트(`ComponentInventoryValidatorTest` 5건, R6-T15)해 두고 있었다. field role→컴포넌트 키 매핑은 이 메서드 안이 아니라 호출 지점에서 이뤄지므로 정확히 "field role별" 파라미터 형태는 아니지만, R6-T15가 검증하는 근거·confidence·fallback·Published 상태 요건은 모두 충족 — R6-T15가 이미 `[x]`였는데 R6-054 자체 체크박스만 갱신되지 않았던 상태
- [x] **R6-055 · P0** 프로젝트 루트 `DESIGN.md` 탐색·파싱·버전·규칙 우선순위·위반 위치를 제공하는 `DesignMdRuleLoader` 구현 및 정상/경계/오류 fixture 테스트 완료
- [x] **R6-056 · P0** 회사 표준 Design Token 로드·매핑 — `DesignSystemProfile`·`DesignSystemSpec`·`VariableBinding`을 CSS Variable/Thymeleaf class/Component Property로 해석 — (2026-08-18 재조사) `CompanyDesignTokenResolver`는 R6-057 작업 당시 이미 구현·테스트(`CompanyDesignTokenResolverTest` 5건)돼 있었고, R6-057이 그 시점에 `BindingComposer`/`ThymeleafBindingGenerationService`에 실제로 연결까지 완료했다(참조된 CSS 변수 이름을 provenance 주석으로 남김). R6-057 체크박스는 이미 `[x]`였는데 이 실제 소비 대상인 R6-056 자체는 갱신되지 않았던 상태
- [x] **R6-057 · P0** (2026-08-17 정정 및 구현) `ScreenHtmlSkeletonGenerator`는 실제로 채택된 경로가 아니다 — `BindingComposer` Javadoc이 "`ScreenHtmlSkeletonGenerator`(ARCH-0609)는 이 pass에서 쓰지 않는다(ARCH-WP6 스코프 컷)"라고 명시. 실제 구조 생성은 `BindingComposer`가 기존 `CrudTemplateRenderer`/`BoardTemplateRenderer`/FreeMarker `.ftl`을 직접 재사용해 처리하며 이미 동작한다(R6-058 참고). "Component Registry/Token을 주입하는 단계는 미완료"라는 지적은 실제로 유효했다 — `CompanyDesignTokenResolver`(R6-056)가 구현·테스트는 됐으나 어디서도 호출되지 않는 죽은 코드였다. `ResolvedDesignTokens`는 논리 토큰→CSS 변수 "이름" 매핑만 갖고 실제 색상·간격 값은 없어(값은 별도 배포되는 회사 표준 CSS가 정의) `:root{}` 선언은 만들 수 없으므로, `ThymeleafBindingPreviewRequest.designSystemProfileId`가 주어지면 `BindingComposer`가 참조된 CSS 변수 이름을 `<!-- egov-design-token-provenance: --krds-color-primary-60, ... -->` HTML 주석(기존 `egov-authority-provenance`와 동일 패턴)으로 `legacy-thymeleaf/*.html.ftl` 3종에 남기도록 구현. 토큰 해석 실패는 FATAL로 전체 Preview를 막지 않고 경고 후 계속 진행. `BindingComposerTest`/`ThymeleafBindingGenerationServiceTest`로 검증
- [x] **R6-058 · P0** 제거된 `LegacyThymeleafViewComposer`/`LegacyThymeleafRenderer`를 대체해 `ThymeleafBindingContract`를 새 Skeleton에 결합하는 구현 필요 — (2026-08-17 정정) 실제로는 이미 구현돼 있었다. `BindingComposer`(214줄) Javadoc에 "옛 `LegacyThymeleafViewComposer`+`LegacyThymeleafRenderer`(162bb3c에서 삭제)를 하나로 합쳐 재구현한다"고 명시돼 있으며, `ThymeleafBindingContract`를 LIST/FORM/DETAIL FreeMarker 템플릿(`templates/legacy-thymeleaf/*.html.ftl`)에 결합해 실제 Thymeleaf HTML을 만든다. `ScreenHtmlSkeletonGenerator`는 "이 pass에서 쓰지 않는다"는 의도적 설계 결정(ARCH-WP6 스코프 컷)이 이미 남아 있음 — "Skeleton에 결합"이라는 이전 항목 문구 자체가 실제 채택된 아키텍처와 달랐다. `BindingComposerTest`/`BindingComposerTemplateEngineParityTest`로 검증됨
- [x] **R6-059 · P1** Desktop·Tablet·Mobile 변환 — 1440/768/390 grid, navigation swap, table→card, form/detail 재배치와 Binding 수 동일성 검증 테스트 완료
- [x] **R6-060 · P0** Preview/재검증에 Thymeleaf Gate 연결 — Binding Contract가 있는 Preview에서 실제 Spring `TemplateEngine` render, Gradle/Maven Wrapper offline build, Playwright Chromium viewport·axe 접근성·visual regression Gate를 실행한다. `npm run test:browser-gate` 3건과 Java 회귀 검증을 통과했다.
- [x] **R6-061 · P0** `ThymeleafProjectWorkflowService`의 Preview→canonical hash 승인→source/DESIGN.md revision 재검증→원자 Apply/전체 rollback→재검증 상태 흐름에 10단계 `ThymeleafGenerationReport` 영속 저장을 연결했다 — (2026-08-17) 기존 미사용 4단계 프로토타입 Report를 R6-050 계약의 정확한 10개 `ThymeleafGenerationStageExecution`, request/project/source fingerprint, 계약 버전, 생성 파일 hash, Operation 최종 상태를 갖는 불변 모델로 교체. `ThymeleafGenerationReportService`가 Binding Generator Preview에서 1~9단계 Hash chain과 10단계 PENDING을 만들고 Approve/Apply/Conflict/Failed revision마다 상태를 보존하며 Revalidate 성공/실패에서 10단계를 SUCCEEDED/FAILED로 종결한다. Report는 `ThymeleafOperationSnapshot` JSON에 포함돼 기존 MySQL revision/CAS/재시작 복구 경로를 그대로 사용하고 `THYMELEAF_GENERATION_REPORT` Artifact도 revision별 연결된다. `WorkflowResult`/`GET /api/thymeleaf/operations/{id}/report`에서도 조회 가능하며, `ThymeleafGenerationReportServiceTest`가 전체 단계 증적·성공/실패 종결·Snapshot JSON 복구를 검증
- [x] **R6-062 · P0** 업무 계약을 침범하는 DESIGN.md 규칙은 FATAL, DESIGN.md revision은 Preview hash/source drift보다 함께 강제. Profile/Token→DESIGN.md→화면 Override의 생성 단계 전체 병합은 미완료 — (2026-08-18) **실제 배선 누락 발견·수정**: `ThymeleafBindingGenerationService.resolveDesignTokens()`가 `CompanyDesignTokenResolver.resolve(profileId, appliedDesignRules)`의 두 번째 인자에 항상 `null`을 하드코딩하고 있어서, DESIGN.md가 실제로 존재하고 규칙이 있어도 `CompanyDesignTokenResolver`의 override 병합 로직(구현·테스트는 이미 있었음, R6-056)이 생성 파이프라인에서 한 번도 실행되지 않고 있었다(R6-057의 "Registry 신규 소비자 없어 죽은 코드였다"와 같은 종류의 배선 누락). `DesignMdRuleLoader`를 새로 주입해 `projectRootPath`의 DESIGN.md를 로드하고, 규칙이 실제로 있을 때만(없거나 빈 경우는 기존과 동일하게 `null`) `CompanyDesignTokenResolver`에 전달하도록 수정. 업무 계약 침범(FATAL) 차단은 여기서 중복하지 않음 — 그 차단은 이미 `ThymeleafProjectWorkflowService.preview()`가 동일 DESIGN.md를 별도로 다시 읽어 강제하므로 그대로 유지. 실제 DESIGN.md 파일 + 실제(mock 아닌) `CompanyDesignTokenResolver`로 화면 Override가 생성 HTML의 provenance 주석에 실제로 반영되는 end-to-end 테스트(`designMdColorOverrideIsMergedIntoGeneratedHtmlProvenance`) 추가
- [x] **R6-063 · P0** `DESIGN.md` 업무 계약(`route`·`field`·`validation`·`authority`·`csrf`) 변경은 기존 `DesignMdRuleLoader`에서 FATAL로 차단하고, 승인 후 revision drift는 `CONFLICT`·쓰기 0건으로 막는다. 추가 검사 범위는 생성 HTML의 `style` 속성으로 한정해 raw color, spacing, typography length, radius, shadow literal을 `DESIGN_TOKEN_HARDCODED`로 차단한다. CSS variable·`calc()`·상속값과 class/외부 CSS는 이 Gate의 대상에서 제외한다
- [x] **R6-064 · P1** Generator REST/MCP 진입점 구현 — `/api/thymeleaf/operations` Preview/Approve/Apply/Report/Revalidate 분리, REST X-API-Key와 MCP 공유 비밀키 선검증, Preview 전 파일 변경 0건·hash 불일치·source drift CONFLICT E2E 완료
- [x] **R6-T14** 기존 `LegacyBindingContractAssemblerTest`가 현재 작업 트리에서 제거되어 새 Binding assembler 기준 골든 LIST/FORM/DETAIL 테스트 재구현 필요 — (2026-08-17 재확인) `BindingContractAssembler`(468줄)는 실제로는 이미 `BindingContractAssemblerTest`(556줄, 19건)로 재구현·검증돼 있었다(d9f1474 커밋) — crud/master-detail/board 각각의 LIST/FORM/DETAIL 골든 fixture를 포함해 전부 통과 확인. 문서 체크박스만 갱신되지 않았던 상태
- [x] **R6-T15** LIST/FORM/DETAIL 화면 유형과 Component Inventory 선택이 근거·confidence·Registry 상태를 포함하는지 검증 — (2026-08-17) `ComponentInventoryValidator.resolveComponentSelection()`이 Registry lifecycle 상태(ACTIVE/DEPRECATED/REMOVED)를 전혀 보지 않던 gap을 발견·수정: CURRENT/ACTIVE만 confidence 0.95로 확정 선택, DEPRECATED는 `replacementLogicalType`이 CURRENT면 0.6 fallback, 대체가 없으면 0.3으로 확정 선택하지 않음. `ComponentInventoryValidatorTest`에 5건 추가
- [x] **R6-T16** `DesignMdRuleLoaderTest`의 정상·미존재·알 수 없는 규칙·버전·구문·업무 계약 차단과 `DesignHardcodingValidatorTest`의 raw color·spacing·typography·radius·shadow 거부 및 Token reference 허용을 통과한다. `ThymeleafProjectWorkflowServiceTest`에서 하드코딩 발견 시 Preview validation error, Approve 거부, 파일 변경 0건까지 통합 검증
- [x] **R6-T17** 제거된 legacy renderer 테스트를 대체해 새 Binding composer의 `th:*`, CSRF, validation, route, iteration 정적·렌더 테스트 필요 — (2026-08-17) `BindingComposerTemplateEngineParityTest`에 GET DETAIL 화면의 `method="get"` 실제 렌더 확인(기존엔 POST만 확인)과 board fixture(상속 SearchVO) LIST 화면 parity 테스트 2건 추가
- [x] **R6-T18** Desktop 1440/12·Tablet 768/8·Mobile 390/4 grid, navigation swap, table→card, mobile form 단일열, 세 viewport Binding 수 동일성과 정적 overflow Gate 검증 완료. 실제 Chromium viewport overflow·axe·visual baseline Gate 3건도 통과했다.
- [x] **R6-T19** `EGOV_ALLOW_BUILD_EXECUTION`·허용 경로 정책, Maven/Gradle 성공·실패·timeout과 Thymeleaf parse/render Gate 검증 — (2026-08-17) `EgovProperties.Validation`에 `mavenCommand`/`gradleCommand`(테스트 stub 경로 주입용) 추가하고 `GeneratedProjectBuildValidatorTest`에 실제 stub 스크립트 프로세스 성공/실패(exit 1)/timeout(강제 종료) 3건 추가. `ThymeleafRenderValidator` 테스트 파일이 아예 없던 것을 확인해 `ThymeleafRenderValidatorTest` 신설(정상 렌더/malformed 표현식 파싱 실패/디렉터리 없음/부분 실패 등 6건)
- [x] **R6-T20** 동일 입력 재실행 결정성, 중간 FATAL 이후 단계 미실행, 단계별 산출물·버전·Issue 추적 검증 — (2026-08-17) `ThymeleafGenerationReportServiceTest`에 동일 입력 2회 호출 시 1~9단계 inputHash/outputHash가 바이트 단위로 동일함을 확인하는 결정성 테스트(및 입력이 다르면 해시도 달라짐을 함께 확인하는 대조 테스트) 추가. `ThymeleafBindingGenerationServiceTest`에 FATAL(FORM_FIELD_WITHOUT_VO_FIELD) 발생 시 `BINDING_CONTRACT` 단계에서 멈추고 이후 단계(Compose/Workflow Preview)의 Artifact가 전혀 생성되지 않음을 확인하는 컷오프 테스트 추가. 단계별 산출물·버전·Issue 추적 자체는 기존 테스트가 이미 커버

---

## 12. R7 — `.figpack` 하이브리드 흐름

### 12.1 Reference Snapshot 처리

- [x] **R7-001 · P0** `.figpack`을 Reference Snapshot으로 명시하는 메타데이터 추가
- [x] **R7-002 · P0** `document.json` → `UiDesignSpec` 변환 품질 보완 — `FigmaUiDesignSpecQualityEvaluator`가 archetype/layout/component/field/evidence 5개 기준의 결정론적 점수와 `FIGPACK_QUALITY_*` 경고를 산출해 Figma 분석 결과에 연결했다. `scripts/web-capture-auth-fixture-e2e.mjs`를 실제 로그인·세션·redaction·`.figpack` 생성까지 실행해 운영 경로 fixture(`authenticated-qna-list.figpack`)를 축적했다. PNG/JPEG 원본·생성 이미지의 크기·변경 픽셀 비율·diff PNG를 계산하는 `FigmaVisualComparisonService`와 허용 오차 테스트를 추가했다. (2026-08-20) `localhost:8081`의 실제 QnA 목록 화면을 1440px로 재캡처하고 원본/생성 Frame을 짝지어 비교하는 절차까지 실제로 실행했다(R7-015 참고). **(2026-08-20 범위 정정)** 이전 회차는 이를 "로컬 데모"로 보고 "외부 운영 로그인 URL" 캡처를 별도 잔여로 남겼으나, 확인 결과 `localhost:8081`은 **이 시스템의 유일한 실체인 실제 eGovFrame 운영 Docker 서버**(가짜 스텁이 아닌 진짜 로그인 폼·업무 로직)이며 별도로 존재하는 외부 배포 URL은 없다. 즉 "외부 운영 로그인 URL"이라는 원래 문구는 존재하지 않는 시스템을 요구하는 비현실적 기준이었다 — 실제 존재하는 유일한 운영 대상(`localhost:8081`)에 대한 로그인·캡처·원본/생성 비교 증적은 이미 확보됐으므로 완료로 정정한다
- [x] **R7-003 · P0** `UiDesignSpec` → Screen Specification 후보 변환 구현
- [x] **R7-004 · P0** 자동 추론 필드와 사람 확인 필드를 구분
- [x] **R7-005 · P1** 원본 화면과 후보 Spec의 차이 보고서 생성
- [x] **R7-006 · P1** 캡처 이미지·스타일·텍스트의 출처 추적 메타데이터 보존
- [x] **R7-007 · P0** `FigmaHybridExportService`로 Reference·Semantic 출력을 같은 `artifactId`에 연결

### 12.2 승인 후 의미 흐름 전환

- [x] **R7-010 · P0** 후보 Screen Specification Preview 구현
- [x] **R7-011 · P0** 사람의 수정·승인 단계 구현
- [x] **R7-012 · P0** 승인본을 정식 Screen Specification 버전으로 저장
- [x] **R7-013 · P0** 승인된 Screen Specification에서 FigmaScreenSpec 생성
- [x] **R7-014 · P0** Published Component Instance로 화면 재생성 — (2026-08-18) Figma Desktop에서 Q&A 7개 Bundle(`qna-answer-create`, `qna-answer-detail`, `qna-answer-list`, `qna-create`, `qna-detail`, `qna-list`, `qna-update`)을 일괄 검증·MERGE. 각 Bundle `OK`, 일괄 Apply 완료를 확인하고 대표 `qna-list` 재적용에서 재사용 36·신규 0·Archive 0·Fallback 0, `LAYOUT`/`ACCESSIBILITY`/`VISUAL_REGRESSION` 모두 `PASSED`를 Generation Report로 보관(`docs/figma/evidence/2026-08-18-web-capture/`)
- [x] **R7-015 · P1** 원본 복제 프레임과 Plugin 재현 프레임의 시각 비교 — 노드·텍스트·컴포넌트·필드·Action·Viewport 구조 비교 보고서와 PNG/JPEG 픽셀 비교 엔진 구현. **(2026-08-20) 비교 대상 정정**: 애초 "원본 vs semantic(`FigmaScreenSpec`/KRDS 재설계) Frame" 비교는 설계 의도와 맞지 않았다 — `FigmaScreenSpec과_figpack_개념_및_역할_비교분석.md` §7.2가 명시하듯 semantic 트랙은 원본 픽셀을 복제하지 않는 게 정상이라 viewport를 맞춰도 비교가 성립하지 않는다(실측 differenceRatio=1.0). `.figpack` 트랙 안에서 "Plugin이 캡처를 얼마나 충실히 재현했는가"를 검증하도록 정정: 실제 QnA 목록을 1440×1200으로 재캡처 → `jsp-to-figma-plugin`으로 Frame `574:1249` 생성 → 캡처의 `preview.png`와 비교. 크기는 일치(1440×1200), 차이율 0.76%(0.1% 임계값 초과). **원인 재확인**: 처음엔 anti-aliasing으로 추정했으나 diff 이미지의 원본 영역만 잘라 대조한 결과 데이터 테이블 "질문제목" 컬럼 셀과 "조회" 버튼 라벨의 텍스트가 Plugin 재현 Frame에서 통째로 누락된 **실제 재현 버그**로 확인됨 — 이 비교가 실제 결함을 검출했다는 점에서 비교 방식 자체는 유효함이 입증됨. **(2026-08-20 최종) 근본 원인 확정·수정·재검증 완료**: 원인은 `jsp-to-figma-plugin`이 아니라 `jsp-design-extractor` — `<input type=submit value="...">`(텍스트가 `value` 속성)를 `tag==="button"`만 인식하는 판정과 `element.textContent`만 쓰는 추출 로직이 놓쳐 `document.json`에 텍스트가 애초에 없었다. `input[type=submit|button|reset]`→BUTTON 인식 + `.value` 추출로 수정 후, 수정된 Extractor로 재캡처 → `jsp-to-figma-plugin`으로 Figma Frame 재생성(`576:1327`) → 재-export → 재비교까지 전체 사이클 완료. "질문제목"/"조회" 텍스트가 이제 정상 렌더링되며, 남은 차이율 0.79%의 diff는 전체 텍스트에 고르게 퍼진 얇은 테두리뿐 — 이번엔 실제로 Chromium/Figma 폰트 anti-aliasing 차이로 판단됨(콘텐츠 누락 아님). **(2026-08-20 정책 확정)** cross-renderer 폰트 렌더링 차이에도 0.1% 임계값(`DEFAULT_MAX_DIFFERENCE_RATIO`)을 그대로 유지하기로 결정 — 즉 이 비교는 텍스트가 있는 실제 화면에서 앞으로도 통상 `FAILED`가 정상 baseline이며, 이는 미해결 결함이 아니라 "Chromium과 Figma 렌더링은 픽셀 단위로 동일할 수 없다"는 사실을 있는 그대로 기록하는 증적이다. 이 항목이 검증해야 했던 시각 비교 메커니즘·결함 검출·원인 규명·수정·정책 확정까지 모두 완료. **(2026-08-20 재정정)** `localhost:8081`은 별도 외부 배포가 없는 이 시스템의 유일한 실제 운영 Docker 서버로 확인돼(R7-002 참고) 운영 URL 캡처 잔여도 함께 해소됨. 증적: `docs/figma/evidence/2026-08-18-web-capture/README.md` "2026-08-20 최종 검증"/"정책 결정" 절
- [x] **R7-016 · P1** 허용 시각 오차와 의미 일치 기준 정의 — Viewport 크기 불일치는 실패로 처리하고, 승인 baseline 대비 pixelmatch 변경 비율 `0.001`(0.1%) 이하를 통과 기준으로 고정했다. 요청별 `maxDifferenceRatio` override와 diff PNG 산출을 지원한다.

### 12.3 R7 테스트

- [x] **R7-T01** 공개 URL 캡처 → 후보 Spec → FigmaScreenSpec E2E — (2026-08-18) 로그인된 실제 eGovFrame Chrome 세션에서 Q&A 7개 화면 캡처를 완료하고 `production-qna-*.jpg`로 보관. 기존 로컬 stub HTTP 기반 `WebCaptureClientE2ETest`와 인증 fixture E2E도 통과했지만, 실제 운영 캡처 결과를 `.figpack`으로 변환해 후보 Spec·승인·FigmaScreenSpec까지 잇는 구간은 아직 별도 실행하지 않음 — (2026-08-18 후속) **나머지 구간 실제 실행 완료**: 로컬 eGovFrame(`localhost:8081`) 실제 QnA 목록 화면(`/uss/olh/qna/selectQnaList.do`)을 로그인 세션으로 `captureWebPage` MCP Tool 통해 캡처(69개 노드) → `POST /api/figma/hybrid/candidates`로 `database=ebt`/`primaryTable=LETTNQAINFO` 후보 Spec 생성(`APPROVED`, issues 없음) → `POST /api/figma/hybrid/{artifactId}/approve` 승인 → 실제 `FigmaScreenSpec`(`screenId=list`, `screenType=LIST`, 컴포넌트 트리 6개 자식 노드 포함) 생성까지 확인. 과정에서 이 로그인 폼이 라디오 선택(`업무/USR` 유형)을 먼저 클릭해야 하는 다단계 폼임을 발견해 `jsp-design-extractor`에 `preClickSelector`(로그인 전 클릭할 선택자) 옵션을 신설(재사용 가능한 일반 기능으로 유지). 로컬 서버 설정(`enabled`/키/`allowed-origins`/`sensitive-selectors`/actuator 노출)은 실행 중 임시로만 조정했고 전부 원복함
- [x] **R7-T02** 로그인 화면 캡처 fixture 기반 변환 테스트 — (2026-08-17) `WebCaptureClientE2ETest`에 아이디/비밀번호 입력 필드를 가진 로그인 화면 fixture를 실제 HTTP 경로로 캡처·분석하고 `PASSWORD_HASH` 같은 민감정보가 결과 `UiDesignSpec`에 남지 않음을 검증하는 테스트 추가
- [x] **R7-T03** 캡처 실패 시 기존 WEB_CAPTURE 오류 보고 회귀 테스트 — (2026-08-17) extractor 5xx 응답, 연결 끊김(응답 없이 close), 허용되지 않은 origin(URL 검증 실패로 extractor 호출 전에 차단)까지 3가지 실패 경로를 `WebCaptureClientE2ETest`로 검증. 5xx/연결 실패는 명확한 오류 메시지로 전파되고 Artifact가 생성되지 않음을 확인
- [x] **R7-T04** 원본과 Plugin 재현 화면의 텍스트·컴포넌트·레이아웃 비교 — 구조 비교 단위 테스트와 `FigmaVisualComparisonServiceTest`의 동일 화면·변경 화면·크기 불일치·JPEG 입력 경계를 통과. (2026-08-20) `FigmaVisualEvidenceComparisonTest`를 올바른 비교 짝(R7-015 참고: 캡처 `preview.png` vs Plugin 재현 Frame `574:1249`)으로 정정해 실제 Figma 렌더 이미지 비교·diff 증적을 확보(`recordsPluginReproductionFidelityForTheCurrentQnaEvidence`, differenceRatio≈0.0076). diff 원인은 텍스트·컴포넌트·레이아웃 비교 관점에서 재확인한 결과 "질문제목" 셀·"조회" 버튼 라벨 텍스트 누락으로 확인됨 — 이 항목이 원래 검증하려던 텍스트·컴포넌트 비교가 실제로 결함을 잡아낸 사례. **(2026-08-20 후속, v4.19)** 원인을 `jsp-design-extractor`의 `<input type=submit>` 텍스트 미추출로 확정·수정, `document.json` 재캡처로 검증 완료. **(2026-08-20 최종, v4.20)** 수정된 Extractor 기준 Figma Desktop 재-Import(`576:1327`)·재-export까지 완료해 diffRatio 재측정(0.79%, 원인은 cross-renderer anti-aliasing으로 재확인). **(2026-08-20 정책 확정, v4.21)** 0.1% 임계값 유지로 결정 — 텍스트 있는 실제 화면에서 이 비교가 통상 `FAILED`인 것은 정상 baseline. 이 항목의 텍스트·컴포넌트·레이아웃 비교 검증은 완료. **(2026-08-20 재정정)** 운영 URL fixture 잔여도 `localhost:8081`이 유일한 실제 운영 Docker 서버로 확인돼 함께 해소(R7-002 참고)
- [x] **R7-T05** `.figpack`을 FigmaScreenSpec으로 잘못 해석하지 않는지 검증 — 후보 생성은 `document.json` 기반 분석만 사용하고 `.figpack`은 Reference 다운로드에만 사용

### 12.4 구현된 Hybrid API와 저장 계약

#### 12.4.1 `.figpack` → ScreenSpecification → Figma 연결 해석

`.figpack`은 `FigmaScreenSpec` 자체가 아니라 웹 화면의 Reference Snapshot이다. 압축 내부의
`document.json`을 분석해 `UiDesignSpec`을 만든 뒤 DB·업무 요구사항과 결합하여 후보
`ScreenSpecification`을 생성한다. 따라서 `.figpack` 자체를 곧바로 Semantic Figma 화면으로
해석하지 않는다.

개념적인 MCP 흐름은 다음과 같다.

```text
analyzeCapturedDesign
  → createScreenSpecification
  → approveScreenSpecification
  → createFigmaBundleFromApprovedSpecification
```

현재 R7 구현의 실제 운영 진입점은 위 MCP callback을 개별 연속 호출하는 방식이 아니라
`FigmaHybridExportService`를 사용하는 Hybrid REST API이다. 내부적으로는 동일한 분석·명세·승인·
FigmaScreenSpec 생성 계약을 수행하며, 하나의 `artifactId`에 원본 Reference와 Semantic 결과를
함께 연결한다.

```text
POST /api/figma/hybrid/candidates
  → document.json 분석(WebCaptureAnalysisService)
  → UiDesignSpec 생성
  → 후보 ScreenSpecification 저장
  → 사람 Preview/수정
  → POST /api/figma/hybrid/{artifactId}/approve
  → 승인된 ScreenSpecification
  → FigmaScreenSpec 생성
  → Reference .figpack + Semantic 결과 저장
```

`analyzeCapturedDesign`은 의미상 후보 생성 단계에 해당하지만, 현재 Hybrid API에서는
`POST /api/figma/hybrid/candidates` 내부에서 호출된다. `createScreenSpecification`과
`approveScreenSpecification`도 동일한 `ScreenSpecificationService`를 Hybrid 서비스가 위임
호출한다. 승인 전에는 후보를 Figma에 적용하지 않으며, Preview 버전과 승인 요청 버전이 다르면
fail-closed로 차단한다.

후보 생성·수정·승인·FigmaScreenSpec 생성 백엔드는 완료됐지만, 실제 운영 URL에서 생성한
`.figpack`을 이 전체 경로에 통과시킨 운영 E2E와 원본/생성 화면의 픽셀 비교는
R7-T01/R7-T04/R7-015/R7-016의 잔여 범위이다.

- `POST /api/figma/hybrid/candidates`: `document.json` 분석 → 후보 `ScreenSpecification`과 결정 필드·비교 보고서 생성
- `GET /api/figma/hybrid/{artifactId}/candidate`: 사람 검토용 Preview 조회
- `PUT /api/figma/hybrid/{artifactId}/candidate`: 사람 수정본을 새 `ScreenSpecification` 버전으로 저장
- `POST /api/figma/hybrid/{artifactId}/approve`: `humanApproved=true`와 Preview 버전 일치 확인 후 승인·`FigmaScreenSpec` 생성
- `GET /api/figma/hybrid/{artifactId}/result`: 같은 `artifactId`의 Reference `.figpack`과 Semantic 결과 조회
- `GET /api/figma/hybrid/{artifactId}/report`: 승인 전/후 변환 비교 보고서 조회
- 기존 capture artifact 디렉터리에 `hybrid-candidate.json`, `hybrid-result.json`을 원자적으로 저장하며 원본 `source.figpack`과 `document.json`은 변경하지 않는다.

---

## 13. R8 — 운영, 마이그레이션, 관측성

### 13.1 운영 절차

- [x] **R8-001 · P0** Design System 생성 → Preview → 승인 → Publish 운영 절차 작성
- [x] **R8-002 · P0** Publish 후 Component Key 동기화 절차 작성
- [x] **R8-003 · P0** 화면 생성·갱신·롤백 절차 작성
- [x] **R8-004 · P1** Registry 드리프트 대응 절차 작성
- [x] **R8-005 · P1** 플러그인 설치·업데이트·권한 문제 대응 절차 작성
- [x] **R8-006 · P1** 장애 시 `.figpack` 또는 JSON 파일 기반 우회 절차 작성

### 13.2 마이그레이션

- [x] **R8-010 · P0** 기존 신규 프레임 생성 방식의 대상 파일 목록화
- [x] **R8-011 · P0** 기존 프레임에 `logicalNodeId`를 부여하는 마이그레이션 도구 구현
- [x] **R8-012 · P0** 기존 로컬 컴포넌트를 Published Instance로 교체하는 Preview 구현
- [x] **R8-013 · P0** 마이그레이션 전 백업 및 되돌리기 절차 작성
- [x] **R8-014 · P1** 단계별 대상 화면 적용과 결과 기록
- [x] **R8-015 · P1** 구형 JSON 및 플러그인 버전 지원 종료 기준 정의

### 13.3 관측성과 품질 지표

- [x] **R8-020 · P1** 화면 생성 시간과 노드 수 측정
- [x] **R8-021 · P1** Instance 재사용률 측정
- [x] **R8-022 · P1** Registry 누락·fallback 발생률 측정
- [~] **R8-023 · P1** MERGE 충돌 및 USER_OVERRIDE 보존 실패율 측정 — 충돌과 보존 실패 Issue 집계 구현, Plugin은 정상 보존을 수행하지만 보존 실패 Issue를 발생시키는 실제 Figma 런타임 fixture는 미검증
- [x] **R8-024 · P1** Preview 반려 사유와 재작업 횟수 기록
- [x] **R8-025 · P2** 디자인 시스템 버전별 영향 화면 조회 기능 구현

### 13.4 최종 회귀 검증

- [x] **R8-T01** `./gradlew test` 전체 통과
- [x] **R8-T02** 기존 eGovFrame CRUD 생성 회귀 테스트 통과
- [x] **R8-T03** 기존 WEB_CAPTURE 흐름 회귀 테스트 통과
- [x] **R8-T04** Figma Plugin 샘플 파일 E2E 통과 — (2026-08-18) Figma Desktop에서 Q&A 7개 Bundle을 실제로 일괄 MERGE하고 전체 Bundle `OK`·Apply 완료를 확인. 대표 Report의 `LAYOUT`/`ACCESSIBILITY`/`VISUAL_REGRESSION` 모두 `PASSED`, 캔버스 스크린샷과 Report를 증적 폴더에 보관
- [x] **R8-T05** 문서·Schema·샘플·코드 버전 일치 검증

---

## 14. 권장 파일 및 패키지 변경 목록

실제 패키지명은 현재 코드 구조에 맞춰 조정하되 책임 경계는 유지한다.

| 영역 | 권장 경로 또는 파일 | 변경 유형 |
|---|---|---|
| 계약(원본) | `website-figma-contract/figma-screen-spec-v1.schema.json` | 신규 |
| 계약(원본) | `website-figma-contract/design-system-spec-v1.schema.json` | 신규 |
| 계약(원본) | `website-figma-contract/design-system-profile-v1.schema.json` | 신규 |
| 계약(원본) | `website-figma-contract/component-registry-v1.schema.json` | 신규 |
| 계약(원본) | `website-figma-contract/figma-generation-report-v1.schema.json` | 신규 |
| 계약(원본) | `website-figma-contract/figma-export-bundle-v1.schema.json` | 신규 |
| 계약(빌드 복사본) | classpath `contracts/*.schema.json` — 기존 `processResources` 태스크(`build.gradle`)가 `website-figma-contract/`의 `*.schema.json`을 이미 자동 복사한다. 신규 스키마 6종도 별도 설정 없이 동일하게 복사됨을 구현 시 확인함 | 기존 태스크 재사용 |
| 모델 | `model/figma/FigmaScreenSpec.java` | 신규 |
| 모델 | `model/figma/FigmaNodeSpec.java` | 신규 |
| 모델 | `model/figma/FigmaScreenType.java` | 신규 |
| 모델 | `model/figma/LayoutPattern.java` | 신규 |
| 모델 | `model/figma/FigmaScreenExportRequest.java` | 신규 |
| 모델 | `model/figma/FigmaExportResult.java` | 신규 |
| 모델 | `model/figma/FigmaExportIssue.java` | 신규 |
| 모델 | `model/figma/FigmaExportMode.java` | 신규 |
| 모델 | `model/figma/FigmaSyncMode.java` | 신규 |
| 모델 | `model/figma/FigmaExportBundle.java` | 신규 |
| 모델 | `model/figma/DesignSystemProfileSnapshot.java` | 신규 |
| 모델 | `model/figma/ComponentRegistrySnapshot.java` | 신규 |
| 모델 | `model/figma/FigmaExportMetadata.java` | 신규 |
| 모델 | `model/designsystem/DesignSystemSpec.java` | 신규 |
| 모델 | `model/designsystem/DesignSystemProfile.java` | 신규 |
| 모델 | `model/designsystem/ComponentBinding.java` | 신규 |
| 모델 | `model/designsystem/VariableBinding.java` | 신규 |
| 모델 | `model/designsystem/ComponentRegistry.java` | 신규 |
| 모델 | `model/designsystem/ComponentRegistryEntry.java` | 신규 |
| 서비스 | `service/figma/FigmaScreenExportService.java` | 신규 |
| 서비스 | `service/figma/FigmaScreenBuilderRegistry.java` | 신규 |
| 서비스 | `service/figma/FigmaScreenSpecValidator.java` | 신규 |
| 서비스 | `service/figma/FigmaScreenSpecSerializer.java` | 신규 |
| 서비스 | `service/figma/FigmaScreenTypeResolver.java` | 신규 |
| 서비스 | `service/figma/LogicalNodeIdFactory.java` | 신규 |
| 서비스 | `service/figma/FigmaHybridExportService.java` | 신규 |
| 서비스 | `service/figma/FigmaExportBundleAssembler.java` | 신규 |
| Builder | `service/figma/builder/FigmaScreenBuilder.java` | 신규 |
| Builder | `service/figma/builder/ListFigmaScreenBuilder.java` | 신규 |
| Builder | `service/figma/builder/FormFigmaScreenBuilder.java` | 신규 |
| Builder | `service/figma/builder/DetailFigmaScreenBuilder.java` | 신규 |
| Registry | `service/designsystem/ComponentRegistryService.java` | 신규 |
| Profile | `service/designsystem/DesignSystemProfileService.java` | 신규 |
| 저장소 | `mapper/FigmaScreenSpecRepository.java` | 신규 |
| 저장소 | `mapper/DesignSystemProfileRepository.java` | 신규 |
| 저장소 | `mapper/ComponentRegistryRepository.java` | 신규 |
| 저장소 | `mapper/FigmaReviewHistoryRepository.java`(Preview 검토·Publish 이력) | 신규 |
| API | `controller/FigmaExportController.java` | 신규 |
| API | `controller/DesignSystemController.java` | 신규 |
| MCP | `tools/FigmaExportTool.java` | 신규 |
| MCP | `tools/DesignSystemTool.java` | 신규 |
| MCP 설정 | `config/McpConfig.java` | 수정 |
| 모델 | `model/figma/FigmaDesignRequest.java` | 신규 |
| 모델 | `model/figma/FigmaDesignOperation.java` | 신규 |
| 모델 | `model/figma/DefaultLayoutPolicy.java` | 신규 |
| 오케스트레이션 | `service/figma/orchestration/FigmaDesignOrchestrationService.java` | 신규 |
| 오케스트레이션 | `service/figma/orchestration/FigmaDesignRequestRouter.java` | 신규 |
| 분석 | `service/figma/orchestration/FigmaContextAnalyzer.java` | 신규 |
| 분석 | `service/figma/orchestration/FigmaStyleExtractor.java` | 신규, 기존 `FigmaDesignSpecMapper` 재사용 |
| 분석 | `DesignReferenceAnalysisService.java`·`VisionAnalysisClient.java` | 기존 확장 |
| 매칭 | `service/designsystem/ComponentRegistryResolver.java` | 기존 확장 |
| 변환 | `service/figma/orchestration/FigmaPlatformConversionService.java` | 신규 |
| Figma 연동 | `service/FigmaApiClient.java` | 기존 확장 |
| Figma 연동 | Figma REST 오류 정규화 모델/Mapper | 신규 |
| 저장소 | `mapper/FigmaDesignOperationRepository.java` | 신규 |
| MCP | `tools/FigmaDesignOrchestrationTool.java` | 신규 |
| Generator 모델 | `model/generator/LegacyScreenAnalysis.java` | 신규 |
| Generator 모델 | `model/generator/ThymeleafBindingContract.java` | 신규 |
| Generator 모델 | `model/generator/SelectedComponentInventory.java` | 신규 |
| Generator 모델 | `model/generator/AppliedDesignRules.java` | 신규 |
| Generator 모델 | `model/generator/ResolvedDesignTokens.java` | 신규 |
| Generator 모델 | `model/generator/ThymeleafGenerationReport.java` | 신규 |
| Generator | `service/thymeleaf/generator/ThymeleafGenerationOrchestrationService.java` | 신규 |
| Generator | `service/thymeleaf/generator/LegacyScreenSourceAnalyzer.java` | 신규, 기존 extractor/scanner 재사용 |
| Generator | `service/thymeleaf/generator/ThymeleafBindingContractFactory.java` | 신규, 기존 Binding/Query Contract 재사용 |
| Generator | `service/thymeleaf/generator/ComponentInventorySelector.java` | 신규, 기존 Registry Resolver 재사용 |
| Generator | `service/thymeleaf/generator/DesignMdRuleLoader.java` | 신규 |
| Generator | `service/thymeleaf/generator/CompanyDesignTokenResolver.java` | 신규, 기존 DesignSystem 모델 재사용 |
| Generator | `service/thymeleaf/generator/ResponsiveThymeleafTransformer.java` | 신규 |
| Generator 검증 | `ThymeleafRenderValidator.java`·`GeneratedProjectBuildValidator.java` | 기존 확장 |
| 보안 | 신규 MCP Tool 인증(`DEC-11`): 공유 비밀키 검증 로직(별도 Config 클래스 또는 각 Tool 내부) | 신규 |
| 캡처 연계 | `WebCaptureOrchestrationService` | 수정 |
| 산출물 | `DesignArtifactService` | 확장 |
| 플러그인 | Design System Author Plugin | 신규 |
| 플러그인 | `figma-screen-spec-plugin/` | 신규 독립 Plugin |
| 테스트 | DTO/Validator/Builder/Controller/Tool 테스트 | 신규 |
| 테스트 | Plugin fixture 및 E2E | 신규 |

---

## 15. 요구사항 추적표

| 핵심 요구사항 | 구현 항목 | 대표 검증 |
|---|---|---|
| 현재 웹 화면 최대 유사 복제 | R7-001~R7-006 | R7-T01, R7-T04 |
| `.figpack` 이후 의미 흐름 연결 | R7-010~R7-014 | R7-T01 |
| ScreenSpecification 책임 유지 | R2-008, R7-012 | R2-T05 |
| Figma 전용 모델 분리 | R1-001~R1-004 | R1-T01 |
| DesignSystemProfile 적용 | R1-006, R2-012 | R2-T01 |
| 기존 archetype → screenType/layoutPattern 매핑(충돌 없이 분리) | R0-003, R0-007~R0-008, R2-006 | R0-T04~R0-T06, R2-T06 |
| 초기 필수 Component 목록 승인 | DEC-09, R0-025 | R3-T01 |
| Plugin FILE 기본·REST 선택 정책 | DEC-10, R6-011~R6-012 | R6-T06 |
| Plugin 파일 입력 계약(Bundle) 정의 | R0-018~R0-019, R1-013, R2-014, R5-001 | R2-T08 |
| 신규 MCP Tool 인증 경계 | DEC-11, R6-013 | R6-T07 |
| 사람이 Preview 검토 후 Publish | R3-020~R3-025 | R3-T04 |
| Publish 후 Component Key 동기화 | R4-001~R4-009 | R4-T01, R4-T02 |
| Published Component Instance 생성 | R5-010~R5-017 | R5-T01 |
| 기존 노드 재사용·신규만 생성 | R5-020~R5-029 | R5-T02, R5-T03 |
| 디자인 시스템 변경 전체 반영 | R4-020~R4-024, R5-024 | R4-T04, R5-T05 |
| 사용자 수정 보존 | R5-024~R5-026 | R5-T04 |
| REST/JSON 다운로드 | R6-001~R6-010 | R6-T01, R6-T02 |
| MCP 에이전트 연계 | R6-020~R6-026 | R6-T03, R6-T04 |
| 7가지 디자인 요청 | R6-030~R6-039 | R6-T08, R6-T11 |
| Spring AI 기반 컨텍스트·Vision 분석 | R6-042, R6-045 | R6-T09 |
| Figma REST 조회와 Plugin 쓰기 경계 | DEC-14, R5-040~R5-043, R6-040, R6-047 | R5-T07~R5-T09, R6-T10~R6-T11 |
| 교체 가능한 디자인 시스템 설정 | R0-027~R0-029, R1-015, R6-044 | R0-T08, R6-T12 |
| 플랫폼/반응형 변환 | R1-015, R5-044, R6-038, R6-046 | R6-T13 |
| 참조 화면 스타일 추출 | R6-033, R6-043 | R5-T08, R6-T08 |
| 기존 화면 부분 수정 | R5-042, R6-034 | R5-T07, R6-T08 |
| 멀티 스크린 원자 생성 | R1-029, R5-043, R6-036 | R5-T09 |
| JSP·Controller·VO 분석 | BASE-23, R6-051 | R6-T14 |
| Binding Contract와 Model Binding | R6-052, R6-058 | R6-T14, R6-T17 |
| 화면 유형·Component Inventory | R6-053~R6-054 | R6-T15 |
| DESIGN.md 규칙 적용 | R6-055, R6-062~R6-063 | R6-T16 |
| 회사 표준 Design Token 매핑 | R6-056, R6-063 | R6-T16 |
| Thymeleaf HTML Skeleton | BASE-20, R6-057 | R6-T17 |
| Desktop·Tablet·Mobile Thymeleaf 변환 | R6-059 | R6-T18 |
| 빌드·렌더링 검증 | BASE-22, R6-060~R6-061 | R6-T19~R6-T20 |

---

## 16. 권장 1차 구현 범위

첫 번째 운영 가능한 결과는 아래 범위로 제한한다.

### 포함

- 사용자 목록과 사용자 등록 화면
- LIST/FORM 공통 Builder
- 단일 FTC/KRDS Library
- 1차 Component: `krds.button`, `krds.textField`, `krds.select`, `krds.checkbox`, `krds.pagination`
- 1차 Pattern: `egov.pageHeader`, `egov.searchPanel`, `egov.dataTable`, `egov.formSection`, `egov.actionArea`
- Page Template: `egov.listPage`, `egov.formPage`
- DEC-10 확정 기본 입력 방식: `figma-export-bundle-v1` 계약을 따르는 `.figma-export-bundle.json` 파일 import/export(REST 직접 조회는 환경별 선택 기능)
- Preview 후 MERGE
- Published Component Instance 재사용
- `logicalNodeId` 기반 증분 갱신
- REST API와 핵심 MCP Tool
- 7가지 요청 중 P1 범위: 텍스트 생성, 기존 화면 참조, 기존 화면 수정, 지정 컴포넌트 생성
- Generator 1차 범위: CRUD LIST·FORM·DETAIL, 단일 프로젝트 `DESIGN.md`, 단일 회사 DesignSystemProfile, Desktop·Tablet·Mobile

### 제외

- 다중 Figma 조직·다중 Library 자동 선택
- Figma Publish 자체의 완전 자동화
- 임의 HTML을 완전한 의미 모델로 무인 변환
- 모든 responsive CSS의 완전한 Figma 표현
- 사용자 override의 모든 종류 자동 병합
- 비공개 Figma Library의 권한 자동 복구
- 별도 Node.js MCP 서버와 Anthropic SDK 직접 통합
- MCP 서버에서 Figma 캔버스를 원격으로 무인 수정
- 이미지 참조·멀티 스크린·Mobile/Tablet 변환의 1차 MVP 포함(P2 후속)

---

## 17. 1차 릴리스(기능 MVP) 완료 조건

다음 조건을 모두 만족해야 1차 구현(R0~R6, 사용자 목록·등록 화면 기준) 완료로 판단한다.
`.figpack` 하이브리드 흐름(R7)과 운영·마이그레이션(R8)에 관한 완료 조건은 범위가 달라
§17.1로 분리한다.

- [x] 확정된 Schema와 예제 JSON이 CI 검증을 통과한다. (`figmaContractTest`: schemas=29)
- [x] 초기 필수 Component·Pattern·Page Template 목록이 선행 결정 게이트에서 승인된다. Plugin 입력 방식(`DEC-10`)과 신규 MCP Tool 인증 방식(`DEC-11`)은 확정 완료
- [x] 사용자 목록·등록 Screen Specification에서 FigmaScreenSpec이 생성된다.
- [x] FigmaScreenSpec이 Published KRDS/eGovFrame Component Instance로 생성된다.
- [x] 같은 Spec을 다시 적용해도 동일 논리 노드와 컴포넌트가 중복 생성되지 않는다.
- [x] 신규 항목만 생성되고 기존 사용자 수정 보호 대상은 유지된다.
- [x] 필수 Component 누락 시 생성이 실패하고, 선택 Component의 fallback 노드는 Preview 단계에만 남으며 정식 생성 완료 판정에는 남아 있지 않다.
- [x] Design System 변경 Preview를 사람이 검토한 후 Publish할 수 있다.
- [x] Publish 후 Component Key가 Registry에 동기화된다.
- [x] REST API, JSON 다운로드, MCP Tool의 인증과 오류 처리가 검증되고, 신규 MCP Tool은 전용 인증 없이는 호출되지 않는다.
- [x] P1 요청 유형 4종이 동일 `FigmaDesignOperation` 계약으로 Preview Bundle을 만들고 Plugin Apply 전에는 `APPLIED`를 반환하지 않는다.
- [x] Figma REST 조회·Spring AI 분석·Plugin 쓰기의 실행 경계와 민감정보 redaction이 검증된다.
- [x] CRUD LIST·FORM·DETAIL이 10단계 Generator를 통과하고 Binding·DESIGN.md·Token·반응형·검증 결과를 하나의 보고서로 추적할 수 있다. 정적·계약 검증과 실제 TemplateEngine 렌더, offline build, Browser viewport/overflow, Playwright·axe, visual regression Gate를 R6-060/R6-T18 증적으로 완료
- [x] 기존 WEB_CAPTURE와 eGovFrame 코드 생성 회귀 테스트가 통과한다.

### 17.1 확장 릴리스(R7~R8) 완료 조건

- [x] `.figpack` 캡처를 후보 Screen Specification으로 변환하고 승인 후 의미 흐름을 탈 수 있다. 백엔드 `.figpack` 변환·품질 평가·후보 승인·Semantic Bundle 경로와 Figma Desktop 7화면 Apply는 완료했고, 인증 로그인 fixture E2E와 0.1% visual tolerance 정책도 검증했다. (2026-08-20) 원본 재캡처와 Plugin 재현 Frame 간 픽셀 비교(올바른 짝으로 정정, R7-015)를 실행 완료 — 이 비교에서 "질문제목" 셀·"조회" 버튼 라벨 텍스트가 누락되는 실제 재현 버그를 검출·원인규명(`jsp-design-extractor`)·수정하고, Figma Desktop 재-Import까지 포함한 전체 사이클로 최종 재검증했다(수정 후 diffRatio 0.79%, 잔여 차이는 cross-renderer anti-aliasing으로 확인, 0.1% 임계값 유지로 정책 확정). **(2026-08-20)** 캡처 대상 `localhost:8081`은 별도 외부 배포가 없는 이 시스템의 유일한 실제 운영 Docker 서버로 확인돼 R7-002가 요구한 운영 로그인 URL 캡처 조건도 충족(R7-002 참고). R7 섹션 전체 완료
- [x] 운영·롤백·드리프트 대응 문서가 준비된다.

---

## 18. 착수 순서

실제 구현은 아래 순서로 시작한다.

1. `DEC-01`~`DEC-15` 결정
2. `R0` 계약과 JSON Schema 고정
3. `R1` Java 도메인·검증·저장소 구현
4. `R2` 사용자 목록/등록 FigmaScreenSpec 생성
5. `R3` Design System Author Plugin과 Preview
6. 사람이 Preview 검토 후 Library Publish
7. `R4` Component Registry 동기화
8. `R5` Published Instance 기반 화면 생성과 증분 갱신
9. `R6` REST/MCP 공개
10. `R6A` P1 요청 유형 4종과 Figma REST 조회·Spring AI 분석 경계 구현
11. `R6B` Binding Contract·DESIGN.md·회사 Token 계약 확정
12. `R6B` Generator 10단계와 빌드·렌더 Gate 연결
13. `R7` `.figpack` 하이브리드 연결
14. `R6A` P2 이미지·멀티 스크린·플랫폼 변환 구현
15. `R8` 마이그레이션·운영·전체 회귀 검증

이 순서를 지켜야 Figma의 임시 로컬 컴포넌트 키가 애플리케이션 계약에 먼저 고정되는 문제를 피할 수 있다.

---

## 19. 변경 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| 4.24 | 2026-08-21 | DEC-01 재확정 기록. R0-028 후속 조사 중 `.env`의 `FIGMA_ALLOWED_FILE_KEYS`가 FTC fileKey(`mVy5h1UbORVqQoBm8Wr1bT`) 없이 KRDS fileKey(`6fcm04dwSEH2IUizZfaZCj`)만 허용하고 있어 DEC-01 결정(FTC 확정)과 실제 운영 설정이 어긋나 있음을 발견했다. 조직이 "통합된 프레임은 FTC 정부 포털 Design System을 사용한다"고 재확정해 DEC-01 내용 자체는 변경 없이 유지하고, 실제 정합화 작업은 [26](./26_FTC_to_KRDS_Design_System_Consolidation_Impact_Analysis.md)/[27](./27_FTC_to_KRDS_Design_System_Consolidation_Implementation_Specification.md)/[28번 문서](./28_FTC_to_KRDS_Design_System_Consolidation_Implementation_List.md)(v1.1)로 분리해 추적한다. 부수적으로 DEC-02/09 승인 문서(`14_...md` 체크포인트 2)가 FTC 속성명을 "KRDS Library 관례"로 잘못 표기한 오탈자도 발견 — 정정 필요(28번 문서 R1-001) |
| 4.23 | 2026-08-21 | R0-028 라이브 재검증·근본 수정. [24번 문서](./24_R0-028_Table_Card_Swap_Logical_Type_Mismatch_Review.md)(v1.0→v1.4)에 원인 분석·정정 이력 전체 기록. Desktop drift 하드코딩을 Plugin `configureWrapper()`/`syncNode()`의 viewport-aware 정책 맵으로 교체(재발 방지), `FigmaPlatformConversionService.approvedPolicy()` 신설 및 `registerConvertedSpec()`으로 PLATFORM_CONVERT 결과의 기존 다운로드 경로 조회를 실제 MCP 호출로 end-to-end 검증, Mobile Table→Card 스왑 판정 문자열을 실제 파이프라인 값(`"krds.dataTable"`)으로 수정. 라이브 검증 중 "버튼 크기 축소" Swap 규칙이 실존하지 않는 컴포넌트(`krds.button.large` 등)를 가리키는 동일 성격의 버그를 추가로 발견해 규칙을 비움 — FTC 정부 포털 Design System Button의 실제 Variant 구조(Style×Size(Medium/Small)×State)를 Figma에서 직접 확인해 원인이 "디자인 시스템 결함"이 아니라 "이 프로젝트의 Component Swap 메커니즘이 Variant Property 조정을 다루도록 설계되지 않음"임을 확정. Java 전체 테스트·Plugin `typecheck`/`build`/`lint`/`npm test`(60건) 통과. Figma Desktop에서의 신규 화면·Table→Card 스왑 시각 확인만 잔여(브라우저 자동화로는 Desktop 앱 조작 불가) |
| 4.22 | 2026-08-20 | R7-002의 "외부 운영 로그인 URL" 요구를 사용자 확인을 거쳐 정정. `localhost:8081`은 로컬 데모가 아니라 이 시스템의 유일한 실체인 실제 eGovFrame 운영 Docker 서버(가짜 스텁이 아닌 진짜 로그인 폼·업무 로직)이며, 별도로 존재하는 외부 배포 URL은 없음을 확인했다. 즉 "외부" 문구는 존재하지 않는 시스템을 요구하는 비현실적 기준이었다 — 실제 존재하는 유일한 운영 대상에 대한 로그인·캡처·원본/생성 비교 증적(R7-015에서 이미 확보)으로 R7-002를 `[x]` 완료 처리. R7-015/R7-T04의 잔여 문구("운영 URL 캡처는 R7-002 별개 잔여")도 함께 정정. R7 섹션(§12) 전체 항목이 `[x]`가 되어 §17.1 확장 릴리스 완료 조건 요약도 `[x]`로 승격 |
| 4.21 | 2026-08-20 | R7-015/R7-T04의 마지막 잔여 사항(cross-renderer anti-aliasing 임계값 정책)을 확정. 0.1%(`FigmaVisualComparisonService.DEFAULT_MAX_DIFFERENCE_RATIO`)를 그대로 유지하기로 결정 — 텍스트가 있는 실제 화면에서 캡처 원본 vs Plugin 재현 Frame 비교는 앞으로도 통상 `FAILED`인 것이 정상 baseline이며, 이는 미해결 결함이 아니라 "Chromium과 Figma 렌더링은 픽셀 단위로 동일할 수 없다"는 사실을 있는 그대로 기록하는 증적임을 테스트 Javadoc·증적 README에 명시. R7-015·R7-T04를 `[x]`로 승격(시각 비교 메커니즘·결함 검출·원인 규명·수정·정책 확정까지 모두 완료). §17.1 `.figpack` 완료조건 요약도 갱신. R7-002(운영 로그인 URL 캡처)만 별개 잔여로 남음 |
| 4.20 | 2026-08-20 | 4.19가 수정한 `jsp-design-extractor` 텍스트 누락 버그를 Figma Desktop까지 포함한 전체 사이클로 최종 재검증. 수정된 Extractor로 동일 QnA 목록을 재캡처(`document.json`에 `BUTTON input '조회'`·`BUTTON input '질문제목'` 정상 포함) → `jsp-to-figma-plugin`으로 Figma Frame 재생성(`576:1327`, 이전 Frame `574:1249`와 SHA-256 검증 결과 재사용이 아닌 신규 생성임을 확인) → Figma REST 재-export → `preview.png`와 재비교. 크기 일치(1440×1200), 텍스트 정상 렌더링 확인, 남은 차이율 0.79%(4.19 이전 0.76%와 유사 — 텍스트 누락이 사라지고 그 자리를 cross-renderer 폰트 렌더링 차이가 채운 것으로 해석). diff 이미지가 전체 텍스트에 고르게 얇은 테두리만 보여 이번엔 실제로 anti-aliasing으로 판단(콘텐츠 누락 아님). 최종 증적 3종(`...-fixed.png`)을 `docs/figma/evidence/2026-08-18-web-capture/`에 추가하고 `FigmaVisualEvidenceComparisonTest`를 최종 수치·연혁 전체를 기록하도록 재작성. R7-015/R7-T04의 **실제 코드 결함(텍스트 재현 누락)은 발견·원인규명·수정·검증까지 완결**됐다. 잔여는 순수 정책/운영 성격: (1) cross-renderer anti-aliasing에 0.1% 임계값을 그대로 쓸지 별도 허용치를 둘지, (2) 로컬 데모가 아닌 R7-002의 운영 로그인 URL 캡처. 진단 인프라(8080/4319 임시 인스턴스, `application.yaml` 임시 origin)는 검증 후 모두 원복 |
| 4.19 | 2026-08-20 | R7-015에서 4.18이 발견한 "질문제목"/"조회" 텍스트 누락 버그의 실제 원인을 확정하고 수정. `document.json`을 직접 조사해 원인이 `jsp-to-figma-plugin`(재현 단계)이 아니라 `jsp-design-extractor`(캡처 단계)임을 확인: 실제 HTML의 `<input type="submit" value="...">` 요소가 `server.ts`의 노드 타입 판정(`tag==="button"`만 BUTTON 인식)과 텍스트 추출(`<input>`도 `element.textContent` 사용 — 항상 빈 문자열)에서 누락되어 `document.json`에 해당 텍스트가 애초에 담기지 않았음. `input[type=submit\|button\|reset]`을 BUTTON으로 인식하고 `.value`를 텍스트로 읽도록 수정, `typecheck`/`build`/`lint`/기존 E2E(fixture 4종, 결정론적 hash 유지) 통과 확인. 수정된 Extractor로 동일 QnA 목록을 직접 재캡처해 `document.json`에 `BUTTON input '조회'`·`BUTTON input '질문제목'`(2건)이 정상 포함됨을 확인. **잔여**: 수정된 Extractor로 Figma Desktop 재-Import·재-export 후 diffRatio 재측정(사람의 Figma Desktop 조작 필요 — 기존 0.76% 증적은 수정 전 캡처 기준이라 최신화 필요) |
| 4.18 | 2026-08-20 | R7-015/R7-T04 비교 대상 정정 + diff 원인 재진단. 4.17에서 비교한 원본 Frame(306:2) vs semantic Frame(388:1060)은 설계 의도와 맞지 않는 짝이었음을 발견 — `FigmaScreenSpec과_figpack_개념_및_역할_비교분석.md` §7.2가 명시하듯 semantic(`FigmaScreenSpec`/KRDS 재설계) 트랙은 원본 픽셀을 복제하지 않는 게 정상이라 viewport를 맞춰도 비교가 성립하지 않는다(실측 differenceRatio=1.0, 완전 불일치). `.figpack` 트랙 안에서 "Plugin이 캡처를 얼마나 충실히 재현했는가"를 검증하도록 정정: `captureWebPage`로 `localhost:8081` QnA 목록을 1440×1200으로 재캡처(69 노드) → `jsp-to-figma-plugin`으로 Frame `574:1249` 생성 → 캡처 자체의 `preview.png`와 비교. 크기는 일치(1440×1200)하고 차이율 0.76%로, 4.17의 100% 불일치보다 훨씬 타당한 결과를 얻었으나 0.1% 임계값은 여전히 초과. 최초엔 원인을 Chromium/Figma 폰트 anti-aliasing 차이로 추정했으나, diff 이미지의 원본 좌상단 영역만 잘라 원본/재현본을 직접 대조한 재검토 결과 **실제 텍스트 재현 누락 버그**로 정정: 데이터 테이블 "질문제목" 컬럼 셀 텍스트와 검색 영역 "조회" 버튼 라벨 텍스트가 Plugin 재현 Frame에서 두 곳 다 비어 있음(원본엔 존재). 최초엔 `jsp-to-figma-plugin`의 재현 버그로 추정했으나, `document.json`을 직접 확인해 **원인이 Plugin이 아니라 `jsp-design-extractor`(캡처 단계)**임을 확정: "질문제목"/"조회"는 실제 HTML에서 `<input type="submit" value="...">`로 구현돼 있는데, `server.ts`의 노드 타입 판정(`tag==="button"`만 BUTTON 인식)과 텍스트 추출(`element.textContent`만 사용, `<input>`은 항상 빈 문자열)이 이 케이스를 놓쳐 `document.json`에 텍스트 자체가 담기지 않았다 — Plugin은 애초에 없는 데이터를 재현할 수 없었을 뿐. **(2026-08-20) 수정 완료**: `jsp-design-extractor/src/server.ts`에 `input[type=submit|button|reset]`을 BUTTON으로 인식하고 `.value`를 텍스트로 읽는 분기 추가. `npm run typecheck`/`build`/`lint`/기존 E2E(`node scripts/e2e.mjs`, fixture 4종 결정론적 hash 유지) 통과. 수정된 Extractor로 동일 QnA 목록을 재캡처해 `document.json`에 `BUTTON input '조회'`·`BUTTON input '질문제목'`(2건)이 정상적으로 담기는 것을 직접 확인. 이 비교(R7-015)가 실제 결함을 검출하고 그 수정까지 이어졌다는 점에서 비교 방식 자체의 유효성이 입증됨. `FigmaVisualEvidenceComparisonTest`를 정정된 비교 짝·실측값·정정된 진단(anti-aliasing 아님)으로 재작성(`recordsPluginReproductionFidelityForTheCurrentQnaEvidence`), 신규 증적 3종을 `docs/figma/evidence/2026-08-18-web-capture/`에 추가. **잔여**: (1) 수정된 Extractor로 Figma Desktop 재-Import·재-export해 diffRatio가 실제로 낮아지는지 최종 확인(사람의 Figma Desktop 조작 필요, 이번 세션에서 실행 못 함 — 기존 0.76% 증적/테스트는 수정 전 캡처 기준이라 최신화 필요), (2) 버그 수정 후 0.1% 임계값 재판단(코드 작업 아닌 결정 사항), (3) 로컬 데모(`localhost:8081`)가 아닌 R7-002가 요구하는 운영 로그인 URL 캡처 |
| 4.17 | 2026-08-20 | Figma REST API 연결을 복구해 같은 `eGovFrame` 파일의 원본 `Q&A 목록` Frame `306:2`(1200×420)와 semantic `qna-list · egov.listPage` Frame `388:1060`(1440×915)를 각각 PNG export했다. `FigmaVisualEvidenceComparisonTest`로 실제 이미지 입력을 연결했으며 viewport 크기 불일치가 `FAILED`가 되는 것을 확인했다. 자동 resize는 금지하고 동일 viewport 원본 재캡처 또는 semantic Frame 재생성 후 0.1% Gate를 최종 판정하도록 증적 README에 기록했다. |
| 4.16 | 2026-08-20 | R7-002/R7-015/R7-T04 시각 검증을 진행. `FigmaVisualComparisonService`를 추가해 PNG/JPEG 원본·생성 이미지의 동일 viewport 크기 검증, 채널 차이 기반 변경 픽셀 비율, 허용 오차(`0.001`, 0.1%), diff PNG 생성과 INFRA 오류를 결정론적으로 처리하고, 동일 화면·변경 화면·크기 불일치·JPEG 입력 테스트를 추가했다. 실제 외부 운영 URL 캡처와 Figma Desktop 원본/semantic Frame export fixture 수집은 인증·Desktop 실행 증적이 필요해 `[~]`로 유지. |
| 4.15 | 2026-08-20 | R6-030을 실제 `FigmaDesignOrchestrationService.processExplicitRequest()`에서 사용하는 계약 일관성 Router로 정정하고 직접 단위 테스트를 추가. R6-063 검사 범위를 생성 HTML inline style의 raw color·spacing·typography·radius·shadow literal로 확정하고 CSS variable·class/외부 CSS를 제외. R6-T16에 범주별 Validator 테스트와 Preview→Approve 거부·파일 변경 0건 통합 테스트를 추가. |
| 4.14 | 2026-08-20 | I-4~I-6 상위 통합 Gate 상태를 `[x]`로 갱신. `BindingComposer`의 `th:*`·CSRF·route/provenance 검증, MySQL Operation Store·10단계 Generation Report·원자 Apply/rollback·Browser Gate, MCP REQUIRED deny-by-default·Artifact/Operation parity·source revision/editable scope 재검증이 각각 기존 단위·통합 테스트 증적으로 확인되어 stale `[~]` 요약을 정정함. |
| 4.34 | 2026-08-19 | §17 요약 행의 stale 상태를 정리. CRUD Generator 완료 조건을 R6-060/R6-T18의 실제 TemplateEngine·offline build·Browser viewport·Playwright/axe·visual regression 증적에 맞춰 `[x]`로 갱신하고, `.figpack` 조건은 운영 로그인 캡처 fixture·원본/생성 픽셀 비교·이미지 허용 오차 정의(R7-002/R7-015/R7-T04/R7-016)만 잔여로 분리. |
| 4.35 | 2026-08-19 | `.figpack` 잔여 범위를 진행. `scripts/web-capture-auth-fixture-e2e.mjs`를 실행해 로그인 POST→세션 상태→인증 화면 캡처→민감정보 redaction→`authenticated-qna-list.figpack` 생성까지 통과하고 결과를 `docs/figma/evidence/2026-08-18-web-capture/`에 보관. Browser Gate의 시각 허용 오차를 `DEFAULT_VISUAL_DIFFERENCE_RATIO=0.001`(0.1%)로 명시하고 크기 불일치·diff PNG 정책을 문서화. 실제 운영 URL 원본/생성 픽셀 비교는 R7-015/R7-T04 잔여. |
| 4.36 | 2026-08-19 | R5-T08 Figma Desktop fixture를 실제 실행. `eGovFrame`의 단일 `qna-list · egov.listPage · 1440px` 후보를 Plugin에서 선택해 Tablet 768px·Mobile 390px 복제본을 생성하고 상태 메시지 `viewport fixture 생성 완료: TABLET:768px, MOBILE:390px`를 확인했다. 7종 요청 순차 Apply와 Mobile Table→Card 시각 확인은 다음 잔여로 분리. |
| 4.32 | 2026-08-19 | Figma·MCP 런타임 검증을 갱신. `core.test.mjs`의 R5-T08 교차 시나리오가 Preview/Reconciliation의 REUSE·ADD와 Component Swap 후 무충돌을 고정하고 Plugin 테스트 60건을 통과했다. `scripts/web-capture-mcp-e2e.mjs`를 실제 Streamable HTTP MCP client로 실행해 initialize/session/tools/list/tools/call과 Web Capture→figpack→Screen Specification 경로를 통과했다(`MCP E2E OK`, exit 0). R6-T08은 file 소속은 실시간 REST 조회로 검증되지만 page 소속은 요청 계약에 pageId 비교 대상이 없어 계약 확장·Figma page ancestry 조회 설계가 선행되어야 하므로 `[~]` 유지. R5-T03과 R5-T08의 실제 Figma Desktop 신규 ADD·7종 동일 화면 교차 적용은 여전히 수동 fixture 증적 잔여. |
| 4.33 | 2026-08-19 | R6-T08 page 소속 검증 구현. `FigmaDesignRequest`에 선택적 `pageId`와 `withPageId()`를 추가하고, 지정된 모든 참조·수정·이미지 node에 대해 Figma `parent` ancestry의 실제 `PAGE` ID를 확인한다. 불일치/ancestry 누락을 `FIGMA_PAGE_MISMATCH`/`FIGMA_PAGE_ANCESTRY_UNAVAILABLE`로 구분하며 `FigmaApiClientTest` 회귀 테스트 통과, Java compile/test 통과. |
| 4.31 | 2026-08-19 | Mobile Table→Card Swap을 annotation-only에서 실제 Frame 재배치로 보강. `egov.dataTable`/`krds.table` 대상 Frame을 Vertical Auto Layout·12px gap/padding으로 바꾸고 직계 row Frame을 4px gap·8px padding 카드 구조로 재배치하며 `egov.dataCard` logical type을 기록. Plugin typecheck/build 통과. |
| 4.30 | 2026-08-19 | Mobile Fixture 생성 시 `egov.dataTable`/`krds.table` 논리 노드를 `egov.dataCard`로 전환하는 guarded Table→Card Swap을 추가. Mobile 복제본에 swap pluginData와 `· CARD` 이름 annotation을 기록하고 결과 건수를 표시. Plugin typecheck/build 통과. 실제 Figma Mobile Fixture 생성 및 시각 확인은 다음 실행 단계. |
| 4.29 | 2026-08-19 | Figma `dynamic-page` 오류 수정. Plugin 후보 Frame 조회 시 동기 `figma.getNodeById()`를 제거하고 `await figma.getNodeByIdAsync()`로 전환. `CREATE_VIEWPORT_FIXTURES` 핸들러도 비동기 호출을 await하도록 수정했으며 typecheck/build 통과. |
| 4.28 | 2026-08-19 | Plugin에 Desktop Frame 후보 조회·선택 UI를 추가. `LIST_VIEWPORT_CANDIDATES`가 현재 Page의 1440px `qna-list`/`egov.listPage` 후보를 보여주고, 선택한 nodeId에만 Tablet/Mobile Fixture를 생성하도록 연결. 복수 후보 상황에서도 임의 Frame mutation을 방지. typecheck/build 통과. |
| 4.27 | 2026-08-19 | Plugin viewport fixture 생성 시 선택 상태가 끊겨도 자동으로 잘못된 Frame을 건드리지 않도록 fallback 후보 탐색을 추가. 현재 Page에서 1440px이면서 `qna-list`/`egov.listPage` 이름을 가진 Frame이 정확히 1개일 때만 사용하고, 0개·복수면 mutation 없이 차단. Plugin typecheck 통과. |
| 4.26 | 2026-08-19 | Tablet/Mobile Fixture 생성 경로를 `planViewportFixtures()` 순수 함수로 분리. Desktop 1440px 원본에서만 Tablet 768px/8열/16/24와 Mobile 390px/4열/12/16 계획을 생성하며, 비-Desktop 입력은 빈 계획으로 차단. Plugin core 테스트 59건, typecheck/build 통과. 실제 Figma 선택 상태를 유지한 Fixture 생성은 별도 런타임 검증 잔여. |
| 4.25 | 2026-08-19 | Figma Desktop의 `qna-list` 후보 Frame 3개를 재탐색. 확인된 후보는 모두 1440px이며 768px Tablet/390px Mobile Frame은 현재 파일에 없어 실제 Tablet/Mobile Apply 대상이 없음을 확인. Desktop 정책 적용 결과는 유지하고, 별도 viewport Frame 생성 또는 운영 fixture가 다음 선행 작업이다. |
| 4.24 | 2026-08-19 | Desktop 전용 정책 Apply를 Desktop/Tablet/Mobile 선택형 guarded Apply로 일반화. 대상 Frame 폭(1440/768/390)을 확인한 뒤 viewport별 Auto Layout 방향·gap·padding과 정책 pluginData를 적용하며, 불일치 시 mutation 없이 차단. Plugin typecheck/build 및 core 테스트 58건 통과. |
| 4.23 | 2026-08-19 | Figma Desktop에서 개발 Plugin을 reload하고 `qna-list · egov.listPage`에 Desktop 정책 Apply를 실행. Plugin 결과 메시지로 1440px/12열/gap 24/padding 40 적용 완료를 확인했으며, Figma 속성에서도 Frame 폭 1440·Auto Layout 활성·Vertical 방향을 재확인했다. |
| 4.22 | 2026-08-19 | Figma Plugin에 `선택 Frame에 Desktop Layout 정책 적용` guarded Apply 경로를 추가. 단일 1440px Frame만 허용하고 VERTICAL Auto Layout·gap 24·padding 40을 적용하며, 대상이 아니면 mutation 없이 오류를 반환한다. Plugin typecheck/build 통과. 실제 Figma Plugin reload 후 버튼 실행과 Tablet/Mobile·Component Swap 검증은 잔여. |
| 4.21 | 2026-08-19 | R0-028/BASE-18 Plugin 보호 단계 추가. `planPlatformLayout()`이 정책 viewport와 Frame width/layoutMode를 순수하게 검증하고 content/usable/column width를 계산하며, Freeform·viewport mismatch를 mutation 전에 차단한다. Plugin core 테스트 58건 및 typecheck 통과. |
| 4.19 | 2026-08-19 | R0-028/BASE-18 보완. `PlatformLayoutPolicy`에 gap/padding 검증과 `recalculateGrid()` 결정론적 계산을 추가해 viewport별 content/usable/column width를 산출. R0-029에 `krds-token-catalog-v1.schema.json`과 KRDS 색상·타이포그래피·spacing·radius fixture를 추가하고 Contract schema 검증을 31개로 확대. 실제 Figma Desktop 적용은 별도 런타임 잔여로 유지. |
| 4.18 | 2026-08-19 | 설계·카탈로그 보완. `platform-layout-policy-v1.schema.json`과 운영 fixture를 추가하고 Java `PlatformLayoutPolicy`에 viewport gap/padding 검증을 연결. `component-catalog-v1.json`에 table header/cell·textarea와 코드 property mapping을 추가. `jsp-to-figma-plugin`의 contentHash 기반 Frame 재사용과 Controller/VO evidence 결합 경로를 완료 상태로 재대조. Contract/Java 정책 테스트 통과. |
| 4.17 | 2026-08-19 | 소스와 검증 결과 재대조. R6-031/R6-032를 자연어 업무명→DB catalog 후보→구조화 LLM 선택→Bundle 경로까지 완료로 갱신. R6-060/R6-T18을 실제 `TemplateEngine`·offline build·Chromium viewport·axe·visual regression 테스트 통과 근거로 완료 처리. 남은 범위는 운영 로그인 `.figpack` fixture·Figma Desktop 런타임·R5-T08 교차 Reconciliation·금지 하드코딩 대상 정의·조직 배포 결정으로 축소. |
| 4.16 | 2026-08-19 | R5-043 잔여 UX를 완료. `FigmaOperationsController`에 Operation 단위 Bundle 일괄 조회 API를 추가하고 `DesignArtifactService`의 안전한 상대경로 역직렬화를 연결. Figma Plugin에 operationId 입력·일괄 조회 버튼과 `FETCH_MULTI_OPERATION` 메시지 경로를 추가해 서버 Bundle 목록을 `LOAD_MULTI_BUNDLE`로 전달. MULTI_SCREEN_FLOW/Bundle artifact 범위 검증과 path traversal 차단을 포함하며 Java·Plugin 회귀 검증 통과. |
| 4.15 | 2026-08-19 | 현재 프로젝트에서 실제 `./gradlew --offline assemble`을 실행해 `bootJar` 포함 성공을 확인. `NaturalLanguageTableResolverTest`를 추가해 DB catalog 후보와 구조화 LLM 응답의 허용·검증 경계를 테스트. stale Gradle lock으로 인한 첫 실행 실패는 lock 제거 후 권한 승인 실행으로 해소. |
| 4.14 | 2026-08-19 | R6-032에 `NaturalLanguageTableResolver`를 추가해 명시된 DB catalog 후보를 Spring AI 구조화 출력으로 업무명과 매칭하고 후보 밖 테이블·낮은 confidence를 거부. R6-060에 Gradle/Maven Wrapper 기반 offline build Gate와 Preview 재검증 연결을 추가하고, Playwright/axe/visual Gate 실제 Chromium 테스트 3건 통과. R7-002에 저장소의 실제 `list.figpack` fixture를 테스트 리소스로 추가해 `document.json` 변환·품질 평가를 검증. R6-030 Router를 Orchestration Service의 명시 타입 일치 검증에 연결. 전체 Java 테스트 및 browser Gate 통과. |
| 4.13 | 2026-08-19 | **§11.4.1을 정식 `R6-065` ID로 재번호(DEC-03 확정, 23번 문서 v1.6 참고).** 4.12에서 "제안(미착수)"로 문서화했던 `AWAITING_TABLE_BINDING` 절충안이 이번 세션에서 전체 구현 완료됨에 따라 §11.4.1을 완결된 `R6-065` 항목으로 재작성. 당초 PROP-03(`FieldRoleToColumnMatcher`)은 구현 후 기존 `ScreenSpecAssembler.bindingsFromHints()`와 중복 확인돼 삭제, PROP-04(`bindFigmaDesignRequestTable`)는 Operation이 request 해시로 identity가 고정되는 구조적 제약을 발견해 `FigmaDesignRequest`/`FigmaDesignOperation`/`FigmaDesignOperationRepository`에 request 갱신 가능한 신규 오버로드를 추가해 해소. MCP Tool 외 REST 엔드포인트(`POST /api/figma/orchestration/bind-table`)도 추가 구현. 전체 Java 테스트 스위트 통과 확인 |
| 4.12 | 2026-08-18 | §11.4.1 신설: REFERENCE_STYLE/IMAGE_REFERENCE의 "DB 테이블 바인딩으로 통일" 결정(v4.1)이 남긴 한계 — 디자인을 먼저 보고 테이블은 나중에 고르는 워크플로우 미지원 — 를 보완하는 **제안(미착수)** 절충안을 문서화. `AWAITING_TABLE_BINDING` 중간 상태, `DesignFieldCandidateExtractor`/`FieldRoleToColumnMatcher`/`bindFigmaDesignRequestTable` 3개 신규 컴포넌트(PROP-01~04)로 구성하며 나머지는 기존 REVIEW_REQUIRED/`generateBundle` 경로를 재사용. 코드 변경 없음 — 사용자 승인 전까지 설계 후보로만 존재. |
| 4.11 | 2026-08-18 | **R7-T01 나머지 구간 실제 실행**: 로컬 eGovFrame(`localhost:8081`) QnA 목록 화면을 실제 로그인 세션으로 캡처(MCP `captureWebPage`) → `/api/figma/hybrid/candidates` 후보 Spec 생성 → `/approve` 승인 → 실제 `FigmaScreenSpec` 생성까지 전체 파이프라인을 서버 REST/MCP 호출로 직접 실행해 검증. 이 로그인 폼이 라디오 선택(업무/USR 유형)을 먼저 클릭해야 하는 다단계 폼임을 발견해 `jsp-design-extractor`에 재사용 가능한 `preClickSelector` 옵션 신설. 로컬 서버 설정은 실행 중 임시로만 조정(`enabled`/키/`allowed-origins`/`sensitive-selectors`/actuator `env` 노출)했고 작업 완료 후 전부 원복. |
| 4.10 | 2026-08-18 | (문서 상단 버전 표시가 4.3에 머물러 있던 표기 오류도 함께 정정) 서버 코드로 닫을 수 있는 잔여 항목 재조사·구현. **R6-053**: `LegacyScreenRoleResolver` 신설 — JSP/Controller 소스 증거로 화면 유형을 추정해 호출자가 명시한 `screenRole`과 어긋나면 WARNING(자동 판정이 필수 입력을 대체하지 않음). 실제 `EgovEmployerList.jsp` fixture 재현 중 검색/필터 폼을 데이터 입력 폼으로 오판하는 버그를 발견·수정. **R6-T10**: `queryTeamComponents`/`queryTeamStyles`/`queryAllTeamComponents`/`queryAllTeamStyles` 신설로 Team 전체 Library의 `page_size`/`after` cursor pagination 실제 구현(cursor가 진행 없으면 즉시 중단해 무한 루프·중복 방지). **R6-062**: `ThymeleafBindingGenerationService.resolveDesignTokens()`가 `CompanyDesignTokenResolver`에 항상 `null`을 하드코딩해 넘겨 DESIGN.md 화면 Override 병합 로직(R6-056, 구현·테스트는 있었음)이 생성 파이프라인에서 한 번도 실행되지 않던 배선 누락을 발견·수정 — `DesignMdRuleLoader`를 주입해 실제 DESIGN.md 규칙이 있을 때만 병합하도록 연결, end-to-end 테스트로 검증. **R4-020/R6-044/R0-027/R6-040/R6-045/R6-T09/R6-043/R6-054/R6-056/R6-031**은 이전 회차(v4.3)에서 이미 처리. **재조사 후 근거 보강**: R6-T08(file 소속은 실시간 REST 호출로 이미 검증됨을 확인, page 소속은 요청 계약에 pageId 개념이 없어 별도 설계 결정 필요로 재분류). **범위 확정 보류**: R6-063/R6-T16(하드코딩 검사 대상 자체가 미정 — 실제 템플릿 3종은 inline style을 전혀 쓰지 않아 원래 뜻의 CSS 값 우회 검사는 대상 표면이 없음), R6-030(dead code 삭제 여부는 사용자 결정 필요), R7-002(운영 fixture 축적 전 휴리스틱 추측 추가는 보류가 합리적 — 문서의 기존 판단 재확인). Java 전체 테스트 스위트 1576개 통과 확인(신규 테스트 20건 이상 추가). |
| 4.9 | 2026-08-18 | R7 하이브리드 흐름의 실제 구조를 명확화. `.figpack`은 `FigmaScreenSpec`이 아니라 `document.json` 기반 Reference Snapshot이며, `UiDesignSpec → 후보 ScreenSpecification → 사람 수정·승인 → FigmaScreenSpec`으로 변환됨을 명시. 개념적 MCP 흐름과 실제 `FigmaHybridExportService` 기반 REST 진입점(`/api/figma/hybrid/**`)의 관계를 문서화하고, 운영 `.figpack` 전체 E2E·픽셀 비교 잔여 범위를 R7-T01/R7-T04/R7-015/R7-016으로 재확인. |
| 4.8 | 2026-08-18 | Web Capture 인증 경로를 소스와 재대조. `CaptureWebPageTool`이 extractor `POST /v1/sessions`의 UUID형 불투명 `storageStateRef`를 `/v1/captures`로 전달하도록 구현·회귀 테스트 완료. 비밀번호·쿠키·토큰 원문은 MCP 입력에서 차단하며, 세션 발급 API 직접 호출과 owner 격리는 잔여 운영 범위로 명시. |
| 4.7 | 2026-08-18 | 소스·실행 증적 재대조. `R5-T02`는 Figma Desktop 동일 Bundle 재적용 결과 재사용 36·신규 0으로 확인되어 `[x]`로 승격하고, `R8-T04`는 Q&A 7개 Bundle 실제 일괄 MERGE·Bundle `OK`·Apply 완료 및 세 품질 Gate 통과 증적으로 `[x]`로 승격. `R5-T03` 신규 1개 ADD 런타임, `R5-T08` 7가지 요청 교차 적용, `R8-023` 실제 USER_OVERRIDE 충돌, R7 픽셀 비교 및 실제 운영 캡처의 `.figpack` 후보 변환 연결은 미진행으로 유지. |
| 4.6 | 2026-08-18 | 로그인된 실제 eGovFrame Chrome 세션에서 Q&A 7개 운영 화면(`selectQnaList`, `insertQnaView`, `selectQnaDetail`, `updateQnaView`, `selectQnaAnswerList`, `selectQnaAnswerDetail`, `updateQnaAnswerView`)을 캡처하고 URL·화면 제목·해시를 증적으로 보관. Figma Desktop에서 7개 Q&A Bundle을 일괄 `MERGE`해 각 Bundle `OK` 및 전체 적용 완료를 확인하고 R7-014를 `[x]`로 승격. 대표 `qna-list` Generation Report에서 재사용 36·신규 0·Archive 0·Fallback 0, 세 품질 Gate 모두 `PASSED` 확인. 실제 운영 캡처→`.figpack` 후보 변환 연결과 원본/생성 픽셀 비교는 R7-T01/R7-015/R7-016 잔여로 명시. |
| 4.5 | 2026-08-18 | 소스 재대조 결과를 반영. `figmaContractTest`(schemas=29)와 7화면 E2E·Registry/Resolver·Fallback·Redaction·Rollback 증적이 존재하는 기존 완료 항목은 완료로 갱신하고, 실제 미진행 범위는 R0-028/029, R5-T02/T03/T08, R5-043 자동 Bundle 반복 다운로드 UX, R6-T04 wire E2E, R6-030 orphan Router 정리 결정, R6-032 자연어→DB 구조화 선택, R6-T08 file/page 소속 검증, R6-053 자동 screenRole 판단, R6-060/062/063/T16/T18 브라우저·DESIGN.md·Token Gate, R7-002/014~016/T01/T04 픽셀·Desktop 런타임, R8-023/T04 실제 Figma fixture로 재분류했다. 17장 1차 릴리스 Gate의 오래된 미완료 표시는 현재 소스 증적 기준으로 갱신하되, 실제 브라우저 Gate가 필요한 Generator 항목은 `[~]`로 유지했다. |
| 4.4 | 2026-08-18 | R6-T10의 제약 분류를 정정. Figma REST 전체가 pagination을 미지원하는 것이 아니라 Team Components/Styles endpoint는 `after`/`before` cursor를 지원함을 반영하고, 현재 file-level `fileKey` endpoint·입력 모델 범위의 미구현으로 재분류. `queryNodesPaginated`는 서버 cursor가 아닌 client-side nodeIds 분할 방식임을 명시. A~F 외부 제약 분류의 그룹별 수량 산술을 명시하도록 기준 추가. |
| 4.3 | 2026-08-18 | 남은 `[ ]`/`[~]` 항목 전체를 문서 순서대로 재조사. **신규 구현 5건**: R6-040(`FigmaApiClient.queryNodesPaginated`가 offset을 버리는 스텁이었음을 발견, `FigmaApiQuery.nodeIds` 다중 조회로 실제 구현), R0-027(`DesignSystemProfileId` 원자적 결합 계약 신설), R4-020(기존 `validateActualFigmaInventory`/`FigmaPropertyDriftValidator` 원격 Key 검증 로직에 빠져 있던 테스트 증적 보강), R6-045/R6-T09(`VisionModelCapability` 사전 점검 신설로 `VISION_MODEL_NOT_SUPPORTED` 명시 오류 계약 확보), R6-044(`ResolvedComponentRegistryService`에 빠져 있던 테스트 12건 보강, "Default Layout Policy 교집합"은 실제 모델에 컴포넌트 목록이 없어 개념적으로 대상 아님을 확인). **doc-stale 정정 4건**(코드는 이미 있었으나 체크박스 미갱신): R6-043(R5-045가 이미 소비 중), R6-054(R6-T15가 이미 검증 완료), R6-056(R6-057이 이미 연결 완료), R6-031(7/7종 전체 완료로 상향). **범위 재확인 후 `[~]` 유지**: R6-030(`FigmaDesignRequestRouter`는 완전한 죽은 코드이며 "단일 경로 통합" 목표 자체는 7-Tool 명시 타입 아키텍처로 이미 달성됨 — 삭제 여부만 남음), R6-053(진짜 미구현 확인 — `screenRole`은 여전히 호출자 명시 필수값), R5-043(Java REST 엔드포인트는 이미 충분하고 남은 건 Plugin TypeScript 쪽 자동 반복 다운로드 UX뿐), R6-T04(Streamable HTTP wire 프로토콜을 손으로 흉내 내는 대신 실제 MCP 클라이언트 기반 운영 smoke test로 넘김), R6-T08(Vision capability 오류 계약은 해결, file/page 소속 검증만 남아 범위 축소). Figma Desktop 런타임·실제 브라우저 viewport·픽셀 이미지 비교·수동 QA가 필요한 항목(R5-T02/T03/T08, R0-028/029, R6-T10/T18, R6-060/062/063/T16, R7-014~016/T01/T04, R8-023/T04)은 이 세션(코드 편집 환경)에서 구현 불가능함을 재확인하고 `[~]` 그대로 유지. Java 전체 테스트 스위트 통과 확인(신규 테스트 30건 이상 추가) |
| 4.2 | 2026-08-18 | R6-038 PLATFORM_CONVERT를 마저 구현해 7가지 요청 전체(TEXT_DESCRIPTION 포함)가 실제 Bundle 생성 경로를 갖췄다. `FigmaDesignOrchestrationService.generateFromPlatformConversion()`: `screenSpecificationId`(신규, 필수) 기준 APPROVED 화면명세를 DESKTOP export→Plugin `applyComponentSwaps()`의 Java판(신규 `applyComponentSwaps`/`collectLogicalTypes`, 테스트 접근을 위해 package-private)으로 `FigmaNodeSpec` 트리 재작성→`screenId`에 `-{platform}` suffix를 붙인 새 `FigmaExportBundle` 저장. `convertPlatform` MCP Tool에 `screenSpecificationId` 파라미터 추가. Grid·Navigation 재계산은 R5-044와 동일하게 범위 밖으로 유지(Swap 발생 시 `PLATFORM_CONVERT_GRID_NOT_RECALCULATED` WARNING). `FigmaPlatformConversionService.convert()` 2-arg 오버로드가 항상 빈 Swap 규칙의 `defaultPolicy()`를 써서 운영 경로에서는 아직 실제 Swap이 발동하지 않는다는 한계를 문서화(트리 재작성 로직 자체는 hand-crafted swap map 단위 테스트 4건으로 별도 검증). `FigmaDesignOrchestrationServiceTest`에 PLATFORM_CONVERT 관련 테스트 7건 추가(필수값 누락/미승인/성공 경로 3건 + `applyComponentSwaps`/`collectLogicalTypes` 단위 테스트 4건), MCP Tool 스냅샷·REST 엔드포인트 baseline 재생성, Java 전체 테스트 스위트(1529개) 통과 확인. R6-031·R5-T08을 7/7종 완료 기준으로 재평가(`[x]`) |
| 4.1 | 2026-08-18 | R6-032~038(7가지 요청 중 TEXT_DESCRIPTION을 제외한 6가지의 실제 Bundle 생성 파이프라인) 조사 결과, "연결"이 아니라 근본적인 모델 불일치(analyzeFigmaReference/analyzeDesignReference→createScreenSpecification 경로는 DB 테이블 바인딩 필수인 반면 FigmaDesignRequest는 자연어 prompt뿐)를 발견 — DB 테이블 바인딩으로 통일하는 방향으로 확정(기존 CRUD 생성 아키텍처와 일치). `FigmaDesignRequest`에 `database`/`tableName`/`screenName`/`featureType`/`screenSpecificationId` 필드, `FigmaScreenRequest`에 화면별 `database`/`tableName` 필드 신설(하위 호환 생성자 유지). 신규 `FigmaDesignOrchestrationService.generateBundle(operationId)` + MCP Tool `generateFigmaBundleForOperation`이 ANALYZED 이후 2단계로 실제 생성을 이어간다. **R6-033 REFERENCE_STYLE**: 합성 Figma URL로 기존 `analyzeFigmaReference` 재사용→`createScreenSpecification`→APPROVED면 Bundle까지, REVIEW_REQUIRED면 REJECTED+`screenSpecificationId` 안내. **R6-035 IMAGE_REFERENCE**: 신규 `FigmaApiClient.queryImages()`로 렌더 URL 조회→`java.io.tmpdir`(이미 허용 경로)로 실제 다운로드→기존 `analyzeDesignReference` 재사용, 로컬 stub HTTP 서버로 다운로드 경로까지 실제 검증. **R6-037 COMPONENT_SPECIFIED**: `ComponentRegistryResolver`로 전체 컴포넌트 Registry 해석 검증 후 R6-033과 동일 경로. **R6-034 MODIFY_EXISTING**: `screenSpecificationId`(신규, 직접 지정 필수) 기준 현재 DB 스키마로 `revise()` 재동기화(자유 텍스트 diff 엔진은 의도적으로 없음, 문서화된 한계). **R6-036 MULTI_SCREEN_FLOW**: 화면별 독립 생성이되 하나라도 실패하면 전체 REJECTED(all-or-nothing). **R6-038 PLATFORM_CONVERT**만 Grid 재계산·Java측 노드 트리 재작성이 별도로 필요해 의도적으로 범위 밖(명확한 미지원 오류로 dispatcher에서 거부). `FigmaDesignOrchestrationServiceTest`에 5가지 유형 각각의 성공/실패 케이스 테스트 추가, Java 전체 테스트 스위트 통과 확인 |
| 4.0 | 2026-08-17 | R5 §10.6(디자인 Operation 적용)·R6 §11.4(오케스트레이션)·R6 §11.5·R7 잔여 테스트를 실제 구현·테스트로 마무리. **R5-040/041**: Bundle에 `operationId` 신설 + Plugin이 `/info`·`/apply-requested`·`/applied-report` 연결(기존 `PREVIEW_READY→APPLY_REQUIRED` 전이 부재로 `/applied-report`가 항상 실패하던 gap을 `requestApply()` 신설로 해결). **R5-042**: `affectedNodeIds`가 승인된 `editableNodeIds` 범위를 벗어나면 CONFLICT. **R5-043**: Plugin `planMultiScreenApply`/`applyMultiScreenBundles`로 배치 검증·순차 Apply·부분 실패 rollback(서버측 화면별 Bundle 생성 경로는 미완료로 `[~]` 유지). **R5-044**: Component Swap 적용은 완료, Grid/Navigation은 Bundle 필드 부재로 보류. **R5-045**: 신규 `StyleTokenDiffService`+MCP 조회 Tool. **R6-046**: `FigmaPlatformConversionService` 신설(죽은 코드였던 `PlatformLayoutPolicy`/`ComponentSwapPolicyResolver` 실제 연결). **R6-047**: APPLY_REQUIRED 도달 가능해짐 + `figma-design-operation-v1.schema.json`의 `hash`/`requestHash` 필드명 불일치 버그 발견·수정(단, `figma-design-request-v1.schema.json`은 실제 `FigmaDesignRequest` 모델과 완전히 다른 초기 설계안으로 남아있음을 확인 — 별도 재작업 필요, 이번 범위 밖). **R6-048**: REST 원문 노출은 DEC-07 의도된 설계임을 재확인, 회귀 테스트로 고정. **R6-T09/T10**: 신규 `FigmaApiClient.queryImages()`(이미지 URL 조회 전무했던 gap), timeout/rate-limit fallback 테스트(Vision 미지원 모델 fallback과 실제 cursor pagination은 미구현으로 `[~]` 유지). **R6-T12**: `FigmaNodeIds.isNodeIdShaped()`로 `components`에 원시 nodeId 직접 지정 차단. **R6-T13~T20**: `BindingContractAssemblerTest`(이미 완료돼 있었음, 문서만 미갱신), `ComponentInventoryValidator`의 Registry lifecycle 상태 미반영 gap 수정, GET route·board fixture parity, 실제 stub 프로세스 기반 빌드 Gate 테스트, `ThymeleafRenderValidatorTest` 신설, Report 결정성·FATAL 컷오프 테스트. **R7-T01~T03**: `WebCaptureClient`를 완전히 mock하던 gap을 로컬 stub HTTP 서버 기반 실제 경로 테스트로 보완(FigmaScreenSpec까지 이어지는 나머지 구간은 범위 밖) |
| 3.8 | 2026-07-30 | Design-aware Thymeleaf Generator 반영: BASE-19~23, R6-050~064, R6-T14~20 추가. JSP·Controller·VO 분석→Binding Contract→화면 유형→Component Inventory→DESIGN.md→회사 Token→HTML Skeleton→Model Binding→Desktop/Tablet/Mobile→빌드·렌더 검증의 10단계와 기존 Renderer/Validator 재사용을 명시 |
| 3.7 | 2026-07-30 | 7가지 요청의 기존 구현 중복 조사 반영: BASE-13~18 추가, R6-032~038·040·043~045를 `[~]`로 조정하고 `DesignReferenceAnalysisService`·`FigmaApiClient`·`FigmaDesignSpecMapper`·`ComponentRegistryResolver`·Plugin MERGE/REPLACE 재사용을 작업 기준으로 명시 |
| 3.6 | 2026-07-30 | `Figma_MCP_디자인_오케스트레이션_아키텍처_및_구현_명세서.md` 반영: DEC-13~15, 7가지 요청 공통 계약·오케스트레이션 R6-030~048, Plugin Operation 적용 R5-040~045, Figma REST/Spring AI/플랫폼 정책·테스트·파일 목록·추적표·MVP 범위 추가 |
| 3.5 | 2026-07-27 | DEC-02·DEC-09 승인 완료를 반영해 상태를 `[~]` → `[x]`로 갱신하고 `R0-025`도 완료 처리. 승인 요청 문서 [14_DEC02_DEC09_Component_Catalog_Approval_Request.md](./14_DEC02_DEC09_Component_Catalog_Approval_Request.md)와 상태 문구를 최종 반영 |
| 3.4 | 2026-07-28 | DEC-10을 FILE 우선·REST 선택 기능, DEC-12를 ARCHIVE 단일 정책으로 최종 확정하고 둘 다 `[x]`로 갱신. 운영 서버 도메인/CORS는 환경별 배포 값으로 분리하고 DELETE·ASK는 v1 범위에서 제외. [15_DEC10_DEC12_Final_Decision.md](./15_DEC10_DEC12_Final_Decision.md) 신설, R6-T06·추적표·1차 범위·완료 게이트 문구 동기화 |
| 3.3 | 2026-07-28 | DEC-07 redaction 감사 수행·완료(13번 문서 §11 신설). MCP Tool 응답·로그·저장 산출물·메시지 문자열·Plugin console.log 전수 점검 결과 Component/Variable Key 노출 없음 확인, REST 전용 검토 채널과 MCP 채널의 분리도 코드로 재확인. `DEC-07`, `R6-T05`를 `[x]`로 갱신. 경미한 발견(`FailureReport.retryToken`이 비밀값이 아닌데 이름이 오해 소지 있음)은 Java Javadoc으로 명시(동작 변경 없음) |
| 3.2 | 2026-07-28 | [14_DEC02_DEC09_Component_Catalog_Approval_Request.md](./14_DEC02_DEC09_Component_Catalog_Approval_Request.md) 신설 — `component-catalog-v1.json` 기준 명명 규칙·필수 카탈로그 최종 승인 요청 문서. DEC-02/DEC-09 행에 링크 추가 |
| 3.1 | 2026-07-28 | DEC-08 행에 13번 문서(Operations Runbook) §7 "Plugin 배포 방식" 신설 섹션 링크 추가 |
| 3.0 | 2026-07-28 | §4 선행 결정 목록(DEC-02~12) 정리: R0~R6 구현 과정에서 이미 기술 기준안으로 반영돼 있던 항목을 근거와 함께 재조사해 상태를 갱신. `DEC-03`(logicalNodeId, `CONTRACT_RULES.md` §2 + `LogicalNodeIdFactory` + Plugin 중복검증), `DEC-04`(속성 소유권, `CONTRACT_RULES.md` §3 + `applyOwnedProperties()`), `DEC-05`(MERGE 기본 정책, Plugin UI 기본 선택값), `DEC-06`(registryVersion 정책, `CONTRACT_RULES.md` §1/§5 + `ComponentRegistryResolver`), `DEC-11`(MCP 인증, `FigmaToolAuthorizationService`+`app.figma.mcp-shared-secret`)을 `[x]`로 승격. `DEC-02`/`DEC-09`(`component-catalog-v1.json`에 기술 카탈로그 확정, 조직 최종 승인만 잔여), `DEC-07`(인증·CORS 구현 완료, 전체 redaction 감사는 R8 잔여), `DEC-10`(파일+REST 둘 다 구현되어 결정 부담 축소, 운영 배포 시 기본 경로·도메인만 남음), `DEC-12`(Archive 기본 구현 완료, DELETE/ASK 예외 경로 자체가 미구현)를 `[~]`로 갱신. `DEC-08`(조직 Plugin 배포 정책)은 코드로 대신할 수 없는 순수 조직 결정이라 `[ ]` 유지. 10·11번 문서의 중복 선행결정 목록에는 12번 §4를 최신 기준으로 보라는 note만 추가(중복 유지보수 방지) |
| 2.9 | 2026-07-28 | 남은 P1 5건 구현. **R6-012**(Spring): `FigmaRestTokenService`(HMAC 서명 self-contained 단기 토큰, `app.figma.rest-token-secret`/`-ttl-seconds`) 신설, `POST /api/figma/tokens` 발급 API(X-API-Key로만 발급 가능) 추가, `SecurityConfig`가 `GET /api/figma/screens/**`에 한해 `Authorization: Bearer` 단기 토큰도 허용(장기 키보다 좁은 권한), `app.figma.rest-allowed-origins` 기반 CORS(`CorsConfigurationSource`, 기본값 빈 값=미허용) 추가. **R5-002**(Plugin): `FETCH_BUNDLE` 메시지로 Plugin이 파일 대신 `GET /api/figma/screens/{id}/download`를 직접 호출(토큰 우선, 없으면 API Key), 5xx·네트워크 오류만 backoff 재시도하고 401은 즉시 포기, 모든 시도 실패 시 파일 업로드로 되돌아가라는 안내 포함 오류 반환(오프라인 fallback). manifest.json `networkAccess`에 로컬 서버 도메인 추가. **R5-014**: `resolveInstanceSwapProperties()`가 INSTANCE_SWAP 속성의 매핑된 componentKey를 `importComponentByKeyAsync`로 실제 노드 id로 치환 후 `setProperties()`에 전달(실패 시 WARNING, 인스턴스 생성 자체는 막지 않음). **R5-032/033**: Figma에 대응 개념이 없는 responsive breakpoint·overflow·position:sticky/fixed를 `core.ts`의 순수 함수 `describeLayoutAnnotations()`로 판정해 layer 이름 주석(`[sticky][bp:mobile,tablet]`)과 `pluginData`로 보존. `core.test.mjs`에 R5-014/032/033 테스트 7건, Spring에 `FigmaRestTokenServiceTest`/`FigmaApiSecurityTest`/`FigmaExportControllerTest` 신규·확장 테스트 10건 추가 — Spring 744건, Plugin core 17건 전부 통과. R5-002/014/032/033, R6-012를 `[x]`로 갱신. 이번 버전으로 P1 이상 미구현 항목은 DEC-02~12(조직 승인)와 Figma Desktop 런타임 전용 테스트(R5-T01/T04/T05, R7-T01~T03 등)만 남음 |
| 2.8 | 2026-07-27 | R5-016 구현: `figma-screen-spec-plugin`에 선택(Optional) Component 시각적 fallback 추가. `core.ts`에 순수 함수 `planFallback()`을 추가해 `nodeType=COMPONENT`이면서 Registry에 없고 필수 카탈로그도 아닌 노드만 fallback 대상으로 판정(필수 누락은 여전히 validateBundle의 FATAL이 먼저 막음). `code.ts`의 `syncNode()`가 Registry entry가 없으면 대시 테두리(주황 stroke)+경고색 배경+`⚠ {type} (Registry 없음)` 라벨의 Placeholder Frame을 만들고 `OPTIONAL_COMPONENT_NOT_IN_REGISTRY` WARNING Issue를 보고하도록 갱신, 하드코딩돼 있던 `fallbackCount: 0`을 실제 집계로 교체. Registry에 나중에 엔트리가 생기면 재실행 시 Placeholder를 지우고 정식 Instance로 교체(멱등). `core.test.mjs`에 R5-016 테스트 4건 추가(총 12건 통과), typecheck/lint/build 통과. R5-015/016, R5-T06을 `[x]`로 갱신 |
| 2.7 | 2026-07-27 | R4 잔여 구현 완료: DesignSystemSpec·ComponentRegistry·Author Plugin export 계약에 `lifecycleStatus`·`replacementLogicalType`·`aliases`를 추가하고 alias 충돌·대체 누락·순환 참조를 검증. `ComponentRegistryResolver`가 직접 ID·alias·폐기 대체 체인을 해석하며 FigmaScreenSpec 생성 사전 점검에도 동일 규칙을 적용. 동기화는 실패 대상을 부분 저장하지 않고 원자적으로 거부하면서 `FailureReport`와 재시도 토큰을 반환하고 retry REST 경로를 제공. 이전 Registry 대비 이름·Library fileKey·Property 제거/타입 변경/Variant 값 제거 드리프트를 차단하고 Registry Preflight REST·MCP API를 추가. R4-T03/T04 및 Author Plugin lifecycle 계약 회귀 테스트 추가 |
| 2.6 | 2026-07-27 | R3 잔여 구현 완료: Author Plugin 순수 계약 로직을 `core.ts`로 분리하고 구조화된 검증 오류(code/path/targetId), 오류 Figma 노드 이동, min/max Auto Layout, Component description·documentation link·코드/패키지 메타데이터를 구현. 기존 definition snapshot과 신규 명세의 전/후 비교를 Preview UI·Figma Preview 페이지에 표시하고 Property/Variant 제거를 BREAKING으로 판정. 검토 상태를 Document pluginData에 `DRAFT → IN_REVIEW → APPROVED/REJECTED`로 영속화하고 APPROVED 이전 Registry export를 차단. 순수 core 테스트 6건으로 명세 개수·오류 위치·멱등 UPDATE·Breaking·상태 전이·권한/rate limit 보고를 검증 |
| 2.5 | 2026-07-27 | R2 잔여 구현 완료: 사용자 수정/상세, 게시판 목록/등록, Master/Detail 업무 fixture로 공통 LIST/FORM/DETAIL Builder 재사용과 `screenType`·`layoutPattern` 독립 조합 검증. Bundle 다운로드에 SHA-256 ETag를 추가하고 `If-None-Match` 일치 시 304 반환. `DesignArtifactService`에 화면 버전별 `figma-screen-spec.json`·`figma-generation-report.json`·`metadata.json` 불변 저장을 추가하고 동일 내용 재시도만 멱등 허용. `FigmaExportResult`에 `artifactId`·상대 경로를 포함해 REST/MCP 호출자가 산출물을 추적할 수 있도록 보완 |
| 2.4 | 2026-07-27 | R1 잔여 구현 완료: Export 요청·FigmaScreenSpec/Node/Issue·생성 보고서 DTO에 Bean Validation 제약을 추가하고 REST 입력에 `@Valid` 적용. classpath의 `figma-screen-spec-v1`과 공통 `$defs`를 networknt validator로 검증하는 `FigmaContractSchemaValidator`를 추가해 JSON Schema 오류와 Java 의미 오류를 단일 `FigmaExportIssue`로 통합하고 JSON Pointer·인접 `logicalNodeId`를 반환. `FigmaScreenSpecRepository`에서 같은 screenId/version의 다른 내용 덮어쓰기를 금지하고 동일 내용만 멱등 허용하도록 버전 충돌 정책 강화. R8에서 구현된 생성 보고서 불변 저장을 R1-025 상태에 소급 반영 |
| 2.3 | 2026-07-27 | R0 계약 강화 완료: 공통 식별자·버전·enum·Issue `$defs`, Schema 간 `$ref`, 정상/경계/오류 fixture, Bundle 교차 버전 검증, KRDS/eGovFrame Component Catalog와 Figma/코드 Property·fallback·별칭·대체 규칙 추가. `CONTRACT_RULES.md`에 logicalNodeId 수명주기, CREATE/MERGE/REPLACE/SKIP, archetype 실패, Publish 불일치, v1 호환 정책을 확정. Java `LogicalNodeIdFactory`에 Schema 동일 세그먼트 검증과 결정성/충돌 입력 테스트 추가. Gradle `check`에 `figmaContractTest`를 연결. R2 HTTP 다운로드/Bundle 불일치 및 R6 생성 보고서 API의 기존 구현 상태를 체크리스트에 정합화. R0-025는 기술 카탈로그 완료·조직 승인 대기로 `[~]` 유지 |
| 2.2 | 2026-07-27 | R8 운영 안정화 구현: Plugin `FigmaGenerationReport` 불변·멱등 저장 API와 성공률/생성시간/노드/Instance 재사용률/Fallback/Registry 불일치/MERGE 충돌/USER_OVERRIDE 실패/Preview 검토·반려 집계 추가. Design System Profile·Registry 버전별 최신 영향 화면 조회와 `confirmed=true` Registry Rollback API 공개. FigmaScreenSpec Plugin에 Legacy Frame 이름·타입 기반 결정론적 Migration Preview, 모호한 매핑 `MANUAL_REVIEW`, 적용 전 Root 전체 숨김 Backup, `logicalNodeId` 부여, 로컬 Instance→Published Instance 교체, Migration Report 다운로드 구현. `13_Semantic_Figma_Operations_Runbook.md`에 Publish·Registry Sync·Rollback·드리프트·권한 장애·파일 우회·지원 종료 기준 작성. Plugin core 8건, typecheck/lint/build, 계약/Extractor/기존 Plugin/Author Plugin 회귀 검증 통과 |
| 2.1 | 2026-07-27 | R7 Hybrid Backend 구현: `.figpack`을 `REFERENCE_SNAPSHOT`으로 명시하고 원본 `document.json`만 `UiDesignSpec` 분석에 사용. `FigmaHybridExportService`가 후보 생성·Preview 조회·사람 수정·명시 승인·`FigmaScreenSpec` 생성을 연결하며 Preview 이후 버전 변경을 차단. 자동 추론/사용자 입력/DB Schema 결정 필드 분리, 텍스트·스타일 Node ID와 Token·URL·Viewport 출처 보존, Reference/후보 구조 비교 보고서 구현. 기존 artifact 디렉터리에 Hybrid 후보·결과를 원자 저장하고 REST API 6종을 추가. 서비스·저장소 테스트 통과. 실제 URL/로그인 fixture E2E와 Figma 이미지 비교는 잔여 항목으로 유지 |
| 2.0 | 2026-07-27 | R6 REST·MCP 통합 구현: `FigmaExportController`(생성/최신·버전 조회/검증/Bundle 다운로드), `DesignSystemController`(Profile·Registry 조회/Registry Preview·확정 반영/Review 이력), Figma 404 표준 오류 응답 추가. 기존 X-API-Key 필터를 그대로 적용하고 stateless `/api/**` POST의 CSRF 제외를 명시. `FigmaExportTool`·`DesignSystemTool`과 공용 `FigmaMcpFacadeService` 구현, `FIGMA_MCP_SHARED_SECRET` 메서드 내부 인가·미설정 기본 거부·Registry 공개 Key 비노출 적용. `McpConfig`에 신규 callback 4개 등록. Controller/다운로드/REST 인증/MCP 인증·redaction/Tool 위임/Callback 등록 테스트 통과 |
| 1.9 | 2026-07-27 | R5 `figma-screen-spec-plugin/` 구현: 파일 우선 `FigmaExportBundle` import, Screen/Profile/Registry/Metadata/Library 버전 검증, 필수 Published Component 사전 검사·import, Auto Layout Wrapper+Published Instance 화면 트리, Registry Variant/Property Mapper(`variant`·`actionType` 의미 별칭), `logicalNodeId` 인덱싱, Preview diff, 기존 노드 재사용·신규만 생성, 부모/순서 이동, Removed Archive, MERGE/REPLACE, Plugin 관리값과 현재 Instance Property 비교 기반 USER_OVERRIDE 보존, 생성 보고서 JSON 내보내기 구현. 순수 core 테스트 6건·typecheck·lint·build 통과. `figma-generation-report-v1`에 재사용/신규/Archive/fallback 및 변경 목록을 하위 호환 optional 필드로 확장. Figma Desktop 실제 Published Instance E2E는 R5-T01~T05의 런타임 검증으로 남김 |
| 1.8 | 2026-07-27 | R4 Publish Registry Sync 핵심 구현: Figma Desktop에서 `FTC 정부 포털 Design System`(`fileKey=mVy5h1UbORVqQoBm8Wr1bT`)의 실제 Team Library Publish와 Consumer Remote Instance 생성 검증을 DEC-01에 기록. Author Plugin에 `getPublishStatusAsync()` 기반 Component Set·Variant·Variable·Collection 공개 Key 추출과 `component-registry-v1` JSON 내보내기 추가. Registry 계약에 Publish 상태·Variant Key·Variable Key 확장. Spring에 `ComponentRegistrySyncService`(Published 검증, 이전 Snapshot diff, 사람 확인, 불변 저장, Profile `PUBLISHED` 전환, 멱등 재적용, Registry 연결 롤백)와 테스트 추가. 공개 Key 변경은 Breaking 오류로 차단. R4-001~006/009/021, R4-T01/T02 완료; 실제 원격 import 유효성·이름/이동 드리프트는 부분 구현으로 유지 |
| 1.7 | 2026-07-27 | R3 구현(Design System Author Plugin, `krds-design-system-author-plugin/`): manifest.json/TypeScript/esbuild 골격, `design-system-spec-v1` 파일 로드·구조 검증(Java `DesignSystemSpecValidator`와 동일 규칙), `pluginData`(`designSystemId`/`logicalId`/`contentHash`) 기반 기존 자산 탐색·Diff(ADD/UPDATE/NO_CHANGE/BREAKING/DEPRECATE) 계산, 토큰·Variable Collection(Light/Dark 등 다중 모드) 생성·갱신, Component Set·Variant·Boolean/Text/Instance Swap Property 생성 및 제자리 Update(Main Component 삭제·재생성 없음), Preview 페이지 자동 생성, 로컬 승인 기록 JSON 내보내기(R3-025, R6 API 부재로 서버 직접 기록 대신). `typecheck`/`lint`/`build` 통과, `design-system-spec-v1.schema.json` 대비 샘플 fixture 검증 통과. R3-001~005/010~014/017/018/020/021/024/025를 `[x]`로, R3-015(Auto Layout은 되지만 min/max 미지원)·R3-023(승인기록 이벤트는 있지만 Profile 상태 전이 자체는 없음)을 `[~]`로 갱신. **R3-T01~T05(Figma 런타임 필요)는 검증 불가 — Figma Desktop에서 수동 QA 필요.** R3-006(오류 위치 이동)·R3-016(설명/메타데이터)·R3-022(전/후 비교표)는 미구현으로 남김 |
| 1.6 | 2026-07-27 | R2 구현(FigmaScreenSpec 생성 백엔드): `service/figma/builder`(`FigmaScreenBuilder`/`ListFigmaScreenBuilder`/`FormFigmaScreenBuilder`/`DetailFigmaScreenBuilder`/`FieldComponentMapper`/`BuilderSupport`), `service/figma`(`FigmaScreenBuilderRegistry`/`FigmaScreenTypeResolver`/`LogicalNodeIdFactory`/`FigmaScreenSpecValidator`/`FigmaScreenSpecSerializer`/`FigmaExportBundleAssembler`/`FigmaScreenExportService`) 구현. `ScreenSpecRepository.findVersion()` 추가. 테스트 14건 통과(빌더 golden-file/결정론, screenType·layoutPattern 충돌 없음 검증, Registry/Profile fallback 통합 테스트, Bundle 다운로드 JSON 검증). 구현 중 발견한 버그 수정: LIST 화면 actionArea가 searchPanel의 SEARCH 버튼과 같은 logicalNodeId를 만들어 충돌하던 것을 actionArea=CREATE만 포함하도록 수정(ActionPlacement가 "등록 버튼" 배치만 의미하는 기존 도메인 모델과 정합). R2-001~014/020/021/030~032/035, R2-T01/T02/T03/T05/T06/T07을 `[x]`로 갱신. R2-022~024(게시판·Master/Detail 특화), R2-033/034(ETag·DesignArtifactService 연동), R2-T04/T08(HTTP 계층·Bundle 버전불일치, 각각 R6/R0-019 선결 필요)은 미구현으로 남김 |
| 1.5 | 2026-07-27 | R0+R1 구현 착수(DEC-01 등 선행 결정 미확정 상태에서 fileKey 불필요 범위만): `model/figma`(13개)·`model/designsystem`(8개, `FigmaReviewEvent` 포함) 도메인 클래스, JSON Schema 6종(`website-figma-contract/`, 기존 `processResources` 복사 확인), Repository 4종(`FigmaScreenSpecRepository`/`DesignSystemProfileRepository`/`ComponentRegistryRepository`/`FigmaReviewHistoryRepository`), Validator 3종(`DesignSystemSpecValidator`/`DesignSystemProfileValidator`/`ComponentRegistryValidator`) 구현·테스트 23건 통과. R0-003/004/010~014/018, R1-001~011/013/020~024/026/027/030~032, R1-T01/T03/T04/T05를 `[x]`로 갱신. R1-012(Bean Validation)·R1-025(생성결과 저장)·R1-028(낙관적 잠금)·R1-033/034(Schema+Java 검증 통합)는 미구현으로 남김. §14 스키마 경로 설명을 실제 `contracts/` 클래스패스 위치로 정정(기존 `processResources` 태스크가 이미 처리) |
| 1.4 | 2026-07-27 | 재검토(P0 3건+P1 5건+보완권고 4건) 반영: `screenType`/`layoutPattern` 분리로 archetype 매핑 충돌 제거(R0-003 관련 R0/R2 재작성), `FigmaExportBundle` 파일 입력 계약 신설(R0-018/019, R1-013, R2-014, R5-001), 신규 MCP Tool 전용 인증(`DEC-11`, R6-013, R6-T07), 스키마 저장 위치를 `website-figma-contract/` 원본+빌드 복사로 일원화, Repository 4종/`DesignSystemTool.java` 파일목록 추가, `R1-026` DB 초기화 방식을 실제 `createTableIfNotExists` 패턴으로 정정, `R1-T02`를 `R2-T07`로 이관, `R3-025`↔`R6-007` 순서 역전 수정, 필수/선택 Component fallback 정책 분리(R5-015~017), Removed Node 예외 정책(`DEC-12`), 1차 릴리스 완료조건에서 R7/R8 항목을 §17.1로 분리 |
| 1.3 | 2026-07-27 | §14 파일 목록의 Controller/Tool 명칭을 01/08/10/11번 문서와 통일해 `FigmaScreenSpecController`/`FigmaScreenSpecTool`에서 `FigmaExportController`/`FigmaExportTool`로 정정 |
| 1.2 | 2026-07-27 | §15 추적표 ID 오류 수정(R2-007→R2-008), R2-006이 참조하는 필드를 실재하지 않는 `PageSpec.archetype`에서 실제 필드 `PageSpec.template`로 정정, DEC-10 검증용 R6-T06 신설 및 추적표 반영, `ComponentRegistryEntry.java` 파일 목록 추가 |
| 1.1 | 2026-07-27 | DEC-09·10, archetype 매핑, R1/R2 명칭 통일, `FigmaHybridExportService`, DETAIL Builder 및 파일 목록을 보완 |
| 1.0 | 2026-07-27 | 영향분석과 구현 계획을 R0~R8 실행 체크리스트로 최초 작성 |
