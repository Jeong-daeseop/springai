# Semantic Figma Design System 구현 목록

> 문서 버전: 3.9
> 작성일: 2026-07-30  
> 상태 재판정일: 2026-08-03
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
| I-4 디자인 기반 Thymeleaf HTML | `[~]` | Skeleton·디자인 규칙 기반은 있으나 Binding Composer와 전체 `th:*`·CSRF·route provenance Gate 미완료 |
| I-5 Responsive·Preview·Apply·검증 | `[~]` | Preview/Apply 프로토타입은 있으나 DB 재시작 복구, 영속 Report, render/build/a11y Gate 미완료 |
| I-6 Figma MCP Tool·Plugin Apply | `[~]` | callback과 일부 인증은 있으나 공통 deny-by-default 인증, 실제 Artifact parity, revision/editable scope E2E 미완료 |

따라서 I-4~I-6 전체는 기능 프로토타입으로 관리하며, 하위 항목 일부가 `[x]`여도 각 통합 작업의
완료 Gate가 모두 닫히기 전에는 상위 상태를 `[x]`로 올리지 않는다.

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
| BASE-10 | [~] | `jsp-to-figma-plugin` | 현재 신규 프레임 생성 방식에서 재사용 기반 방식으로 개편 |
| BASE-11 | [x] | `GenerationDesignContextService` | Screen Specification 기반 코드 생성 연계 유지 |
| BASE-12 | [x] | CRUD/Board/MasterDetail 생성 흐름 | Figma 전용 식별자와 분리하여 기존 생성 기능 보존 |
| BASE-13 | [x] | `DesignReferenceTool.analyzeFigmaReference`·`DesignReferenceAnalysisService.analyzeFigma` | `create_design_from_reference`의 Figma FRAME 분석 단계 재사용 |
| BASE-14 | [x] | `DesignReferenceTool.analyzeDesignReference`·`VisionAnalysisClient` | `create_design_from_image`의 PNG/JPEG/PDF 및 OpenAI/Ollama Vision 분석 단계 재사용 |
| BASE-15 | [x] | `FigmaApiClient`·`FigmaDesignSpecMapper` | Figma Node 조회/retry와 layout·token·component 의미 매핑 확장 |
| BASE-16 | [x] | `ScreenSpecificationService.revise`·Screen Plugin MERGE/REPLACE | `modify_existing_design`의 명세 버전 갱신과 `logicalNodeId` 동기화 기반 재사용 |
| BASE-17 | [x] | `ComponentRegistryResolver` | 지정 컴포넌트 요청의 alias·replacement·Published Registry 해석 재사용 |
| BASE-18 | [~] | `FigmaScreenExportRequest.viewport`·Layout annotation | 플랫폼 표시는 존재하지만 Layout 재계산·Component Swap은 추가 필요 |
| BASE-19 | [x] | `ScreenFieldBinding`·`GenerationQueryContractFactory` | Generator Binding Contract의 field/query 기반 재사용 |
| BASE-20 | [x] | `CrudTemplateRenderer`·`BoardTemplateRenderer`·`MasterDetailTemplateRenderer` | FreeMarker 기반 Thymeleaf HTML Skeleton 생성 재사용 |
| BASE-21 | [x] | `CrudModelFactory`·`BoardModelFactory`와 Controller/Thymeleaf template | Controller Model Binding 생성 기반 재사용 |
| BASE-22 | [x] | `ThymeleafRenderValidator`·`GeneratedProjectBuildValidator`·`CodeValidatorTool` | 생성 결과 parse/render 및 허용된 Maven/Gradle 빌드 검증 재사용 |
| BASE-23 | [~] | `jsp-design-extractor`·`ProjectScannerTool`·기존 CRUD metadata 서비스 | JSP 구조와 프로젝트 파일 탐색은 가능하나 Controller method·Model attribute·VO validation의 화면 단위 연결 필요 |

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
| DEC-04 | P0 | [x] | 속성 소유권 정책 | `CONTRACT_RULES.md` §3("사용자 직접 수정 속성은 USER_OVERRIDE, 컴포넌트 외형은 DESIGN_SYSTEM, 업무 값·구조는 SCREEN_SPEC") + Plugin `code.ts`의 `applyOwnedProperties()`가 실제로 사용자가 바꾼 값은 재동기화 때 덮어쓰지 않도록 구현·적용 |
| DEC-05 | P0 | [x] | 화면 갱신 기본 정책 | `CONTRACT_RULES.md` §3 변경 정책 표(CREATE/MERGE/REPLACE/SKIP) + Plugin UI의 동기화 모드 select가 MERGE를 기본값으로 제공하고 REPLACE는 사용자가 명시적으로 선택했을 때만 기존 화면을 Archive 후 재생성 |
| DEC-06 | P0 | [x] | Registry 버전 정책 | `CONTRACT_RULES.md` §1(`registryVersion`은 "Figma Library Publish 단위로 신규 발급") + §5(Screen/Profile/Registry/Bundle metadata 간 버전 불일치를 `*_MISMATCH` 오류로 차단) + `ComponentRegistryResolver`로 구현·검증 완료 |
| DEC-07 | P0 | [x] | API 및 산출물 보안 정책 | `X-API-Key`(R6-010) + 단기 토큰(R6-012, `FigmaRestTokenService`) + CORS(R6-012) + MCP 전용 인증(DEC-11) 구현·테스트 완료. 2026-07-28 redaction 감사([13번 문서](./13_Semantic_Figma_Operations_Runbook.md) §11) 결과 MCP 응답·로그·저장 산출물·메시지 문자열·Plugin 어디에서도 Component/Variable Key 노출 없음을 확인. REST 전용 Registry 검토 API(원문 Key 포함, 사람 승인용)와 MCP 채널(redaction됨)이 코드 수준에서 분리돼 있음도 확인 |
| DEC-08 | P1 | [ ] | 플러그인 배포 방식 | 개발용 manifest import 방식만 존재(`krds-design-system-author-plugin`/`figma-screen-spec-plugin` 둘 다). 조직 내부 Plugin 배포 채널(Figma Organization/Enterprise 여부, 사내 배포 절차)은 코드로 대신할 수 없는 순수 조직 IT 정책이라 여전히 미결. 선택지 비교와 권장안은 [13_Semantic_Figma_Operations_Runbook.md](./13_Semantic_Figma_Operations_Runbook.md) §7 참고 |
| DEC-09 | P0 | [x] | 초기 필수 Component·Pattern 목록 | `component-catalog-v1.json`에 필수 Component 5종(button/textField/select/checkbox/pagination)·Pattern 5종·Page Template 2종이 기술 기준안으로 이미 확정돼 Schema 검증까지 통과(R0-025와 동일 게이트). 조직 Library 담당자의 최종 Preview 승인이 완료되어 운영 기준으로 확정 — 승인 요청 문서: [14_DEC02_DEC09_Component_Catalog_Approval_Request.md](./14_DEC02_DEC09_Component_Catalog_Approval_Request.md) |
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
- [~] **R0-027 · P0** `designSystemProfileId`가 Token/Variable·Component Registry·Default Layout Policy 버전을 원자적으로 결합하는 계약 정의 — `DesignSystemProfile`과 `DesignSystemProfileSnapshot`에 `layoutPolicyVersion`과 `layoutPolicy` 필드로 구현되어 있으나 Default Layout Policy 정식 모델(`DefaultLayoutPolicy`) 미구현(I-1 후속)
- [~] **R0-028 · P1** FORM/LIST/DETAIL/DASHBOARD 기본 Layout과 Desktop/Tablet/Mobile 변환·Component Swap 정책 Schema 작성 — `figma-screen-spec-v1.schema.json`의 viewport/layout/navigation 필드로 Layout 정보는 저장되나 Platform 변환 정책 Schema와 Component Swap 정책은 I-1/2-A6 범위로 남김
- [~] **R0-029 · P1** 원본 명세서의 KRDS 색·타이포그래피·간격·radius 및 11개 예시 컴포넌트를 샘플 fixture로 변환하되 운영 fileKey/Node ID와 분리 — `component-catalog-v1.json`의 requiredComponents 5종·patterns 5종·pageTemplates 2종이 기술 기준선으로 존재하고 DEC-02/DEC-09 조직 승인 완료. 추가 11개 예시 컴포넌트와 색·타이포 완전 카탈로그는 Design System Library 정식 Publish 이후 자동 동기화로 진행(현재는 샘플 5종+5종 기준)

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
- [~] **R0-T08** Profile·Registry·Default Layout Policy 중 하나라도 버전이 다르면 교체/변환 Preflight가 실패한다. — `ComponentRegistryResolver`와 `FigmaScreenExportService.preflightComponentRegistry()`가 Registry 버전 검증을 구현했으나 layoutPolicyVersion 검증은 I-1 `DefaultLayoutPolicy` 모델 후속

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
- [ ] **R1-015 · P1** `DefaultLayoutPolicy`, `PlatformLayoutPolicy`, `ComponentSwapPolicy` 구현 — 미착수(후속 I-1 작업으로 남김)

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

- [~] **R4-020 · P0** Registry Key 유효성 점검: 비어 있는 Key·중복 Key·`CURRENT`가 아닌 Publish 상태는 차단함. Figma에서 Key를 실제 import하는 원격 유효성 검증은 R5/R6에서 추가 필요
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

- [ ] **R5-T01** 모든 공통 컴포넌트가 Published Instance로 생성되는지 검증
- [~] **R5-T02** 순수 Reconciliation 테스트에서 동일 논리 노드 `REUSE` 판정 검증. Figma Desktop 런타임 재실행 검증 필요
- [~] **R5-T03** 순수 Reconciliation 테스트에서 신규 논리 노드만 `ADD` 판정 검증. Figma Desktop 런타임 검증 필요
- [ ] **R5-T04** 사용자 텍스트·위치 override 보존 검증
- [ ] **R5-T05** MERGE와 REPLACE 결과 비교 검증
- [x] **R5-T06** 필수 Component 누락 FATAL·적용 차단, 선택 Component 시각 fallback(Placeholder 생성·Registry 갱신 시 정식 Instance로 교체) `core.ts` 순수 로직 검증 완료. Figma Desktop 실제 렌더(Placeholder 시각 확인)는 수동 QA 필요

### 10.6 디자인 Operation 적용

- [ ] **R5-040 · P0** `FigmaDesignOperation`/Bundle의 `operationId`, request type, source revision을 읽고 Preview에 표시
- [ ] **R5-041 · P0** MCP 분석 완료와 Plugin Apply 완료 상태를 분리하고 실제 적용 후에만 `APPLIED` 보고서 생성
- [ ] **R5-042 · P0** `modify_existing_design`의 `editableNodeIds`가 현재 file/page/승인 범위와 일치하는지 재검증
- [ ] **R5-043 · P1** 멀티 스크린 Operation을 전체 Preview 성공 후 일괄 Apply하며 중간 실패 시 부분 적용 방지
- [ ] **R5-044 · P1** 플랫폼 변환 결과의 Grid·Navigation·Component Swap·annotation 적용
- [ ] **R5-045 · P1** 참조 Style Token 후보와 운영 Profile Token의 차이를 Preview에 표시하고 자동 Library 변경 금지
- [ ] **R5-T07** source revision 불일치·다른 파일 Node ID·미승인 Component가 Apply 전에 차단된다.
- [ ] **R5-T08** 7가지 요청 fixture의 Preview diff와 실제 Reconciliation 결과가 일치한다.
- [ ] **R5-T09** 다중 화면 Apply가 전부 성공하거나 전부 실패하고 재시도 시 중복 노드가 생기지 않는다.

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
- [~] **R6-T04** 전체 Spring Context와 기존 테스트 회귀 통과. 실제 Streamable HTTP JSON-RPC E2E 호출은 후속 운영 검증 필요
- [x] **R6-T05** MCP Registry 감사 응답에서 Component/Variable Key 미노출 검증 + 전체 로그·산출물 redaction 감사 완료(2026-07-28, [13번 문서](./13_Semantic_Figma_Operations_Runbook.md) §11) — 실제 노출 없음 확인
- [x] **R6-T06** DEC-10=FILE 기본 경로의 REST Bundle 다운로드와 R5 Plugin 파일 입력 테스트 통과. 선택 기능인 REST 직접 조회도 단기 토큰·CORS·재시도·파일 fallback 테스트로 검증
- [x] **R6-T07** 잘못된/누락 공유 비밀키를 Repository 접근 전에 거부하고 Registry 공개 Key가 MCP 응답에 포함되지 않는지 검증

### 11.4 7가지 디자인 요청 오케스트레이션 (2-A 범위: 2-A1~6)

**2-A1: FigmaApiClient 확장 (3시간)**
- [~] **R6-040 · P0** 기존 `FigmaApiClient` 확장: 현재 단일 Node 조회·timeout·retry/backoff를 재사용하고 files/images/styles/components·pagination·오류 정규화 추가 — `FigmaApiQuery`, `FigmaStylesResponse`, `FigmaComponentsResponse`, `queryStyles()`, `queryComponents()`, 제네릭 `callApi<T>()` 추가. `queryNodesPaginated()` 실제 구현·retry/backoff 통합 테스트·응답 크기 제한 검증 필요

**2-A2: Allowlist 검증 (2시간)**
- [ ] **R6-041 · P0** Figma access token·LLM key·image URL·node/file 식별자의 로그/응답/산출물 redaction 및 allowlist 검증

**2-A3: FigmaContextAnalyzer (3시간)**
- [ ] **R6-042 · P0** `FigmaContextAnalyzer` 구현: Spring AI 구조화 출력으로 domain, screenType, layoutPattern, required logical types, uncertainty 반환

**2-A4: FigmaStyleExtractor (2시간)**
- [~] **R6-043 · P1** `FigmaStyleExtractor` 구현: 기존 `FigmaDesignSpecMapper`의 layout/token 추출을 재사용해 공통 color/typography/spacing/layout을 Profile 변경이 아닌 Token 후보로 확장

**2-A5: 요청 흐름 연결 (3시간)**
- [~] **R6-030 · P0** `FigmaDesignRequestRouter`의 명시/컨텍스트 판정은 구현. 7개 전용 Tool이 명시 타입을 조립하므로 저장소에는 유효 타입만 들어가지만 자유 텍스트 router와 구조화 분류의 단일 경로 통합은 미완료
- [~] **R6-031 · P0** `FigmaDesignOrchestrationService` 구현 — 7개 요청은 canonical hash 기반 영속 `ANALYZED` 승인 후보로 저장되고, APPROVED ScreenSpecification은 FigmaScreenSpec→Bundle→불변 Artifact→영속 `PREVIEW_READY`로 연결된다. 자연어 후보 ScreenSpecification 자동 생성은 미완료
- [~] **R6-032 · P0** `create_design_from_text(prompt, platform)` MCP callback 구현 — DB Schema→ScreenSpecification→FigmaScreenSpec 기반 흐름은 존재하며 자연어 구조화 분석과 Operation 조립 필요
- [~] **R6-033 · P1** `create_design_from_reference(prompt, referenceNodeIds)` MCP callback 구현 — 기존 `analyzeFigmaReference`/`DesignReferenceAnalysisService.analyzeFigma`/`FigmaApiClient`를 재사용하고 prompt 반영과 새 Bundle 생성 연결
- [~] **R6-034 · P1** `modify_existing_design(prompt, editableNodeIds)` MCP callback 구현 — 기존 `reviseScreenSpecification`과 Plugin MERGE/REPLACE를 재사용하고 자연어 diff·대상 Node·source revision 검증 추가
- [~] **R6-035 · P2** `create_design_from_image(prompt, imageNodeIds)` MCP callback 구현 — 기존 PNG/JPEG/PDF Vision 분석을 재사용하고 Figma 이미지 Node export 입력과 Operation 연결 추가
- [~] **R6-036 · P2** `create_multi_screen_flow(prompt, screens[])` MCP callback 구현 — `screenNames`의 결정적 `FigmaScreenRequest` 조립과 null type TODO 제거 완료. 화면별 Bundle 일괄 Preview·부분 실패 rollback은 미완료
- [~] **R6-037 · P1** `create_design_with_components(prompt, componentLogicalTypes[])` MCP callback 구현 — 기존 `ComponentRegistryResolver`를 재사용하고 요청 logical type allowlist 제약 추가
- [~] **R6-038 · P2** `convert_platform(sourceNodeIds, targetPlatform)` MCP callback 구현 — viewport 저장과 Layout annotation은 존재하며 Layout 재계산·Navigation/Component Swap 추가

**2-A6: Redaction 정책 (1.5시간)**
- [~] **R6-044 · P1** 기존 `ComponentRegistryResolver` 확장: Registry·승인 Catalog·Default Layout Policy 교집합과 요청 allowlist·필수/선택 정책 적용
- [~] **R6-045 · P2** 기존 `DesignReferenceAnalysisService`·`VisionAnalysisClient` 확장: Vision capability 사전 점검, Figma 이미지 export 분석, 불확실성·접근성 Issue 반환
- [ ] **R6-046 · P2** `FigmaPlatformConversionService` 구현: Desktop 1440/12열, Tablet 768/8열, Mobile 390/4열 초기 정책과 Profile 기반 swap 적용
- [ ] **R6-047 · P1** 모든 Tool 응답에 `operationId`, `artifactId`, preview summary, issues, `PREVIEW_READY`/`APPLY_REQUIRED` 상태를 포함하고 캔버스 적용 전 `APPLIED` 반환 금지
- [x] **R6-039 · P0** 7개 callback과 승인 ScreenSpecification Bundle callback 등록 완료. 8개 모두 `figmaMcpSecret`을 Repository/오케스트레이션 접근 전에 상수시간 비교로 검증하며 MCP snapshot(97 methods/35 objects) 갱신 완료
- [ ] **R6-048 · P1** 웹 UI·CLI·Webhook 클라이언트가 동일 MCP/REST 계약을 사용하도록 transport-neutral facade 유지. 별도 React/Slack/Teams 클라이언트 구현은 후속 범위
- [~] **R6-T08** 7개 callback 입력 Schema snapshot, 인증 선행, 고급 요청 필수 목록·미지원 platform의 Repository 접근 전 거부 테스트 완료. 실제 file/page node 소속과 Vision capability 오류 계약은 미완료
- [ ] **R6-T09** Spring AI 구조화 출력 오류·timeout·rate limit·Vision 미지원 모델 fallback 테스트
- [ ] **R6-T10** Figma REST pagination·429 retry/backoff·권한 오류·만료 이미지 URL 테스트
- [x] **R6-T11** 분석 요청은 `ANALYZED`, 승인 Bundle은 `PREVIEW_READY`까지만 반환하며 Repository 상태 테스트에서 `APPLY_REQUIRED`와 유효 Plugin 보고 없이는 `APPLIED` 전이가 거부됨을 검증
- [ ] **R6-T12** 지정 컴포넌트 요청이 승인되지 않은 logical type과 로컬 Node ID 직접 지정을 거부하는지 검증
- [ ] **R6-T13** 플랫폼 변환 golden fixture에서 폭·Grid·Navigation·componentSwaps가 Profile 정책과 일치하는지 검증

### 11.5 Design-aware Thymeleaf Generator

상세 단계·계약·보안·완료 기준은
[eGovFrame_JSP_to_Spring_Boot_Thymeleaf_전환_작업_명세서.md](./eGovFrame_JSP_to_Spring_Boot_Thymeleaf_전환_작업_명세서.md)를
따른다.

- [ ] **R6-050 · P0** 10단계 Generator 입출력 계약과 단계별 FATAL/WARNING·중단·재시도·입력 Hash 정책 정의
- [x] **R6-051 · P0** JSP·Controller·VO 화면 단위 분석 구현 — `LegacySourceInventoryService`(안전한 경로·예산) + `JspSourceReader`(taglib/form/EL/forEach/표시필드, 정규식 기반) + `ControllerSourceReader`(매핑/모델/반환뷰/redirect/보안, JavaParser AST) + `VoSourceReader`(필드/Lombok 접근자/Bean Validation, JavaParser AST)로 신규 구현. CSS/JS Frontend Source Graph(I-2D, `jsp-design-extractor` 모듈화)는 범위 밖으로 남김
- [~] **R6-052 · P0** `ThymeleafBindingContract` 모델과 JSP·Controller·VO reader는 유지. 기존 `LegacyBindingContractAssembler` 구현/테스트가 현재 작업 트리에서 제거되어 새 Workflow와의 재연결 필요
- [~] **R6-053 · P0** 화면 유형 판단 — `FigmaScreenTypeResolver`·`ScreenSpecAssembler`·CRUD/Board/MasterDetail 판정을 재사용하고 근거·confidence 포함
- [~] **R6-054 · P0** Component Inventory 선택 — `ComponentCandidate`·`ComponentRegistryResolver`를 재사용하고 field role별 선택 근거·fallback·Published 상태 검증 추가
- [x] **R6-055 · P0** 프로젝트 루트 `DESIGN.md` 탐색·파싱·버전·규칙 우선순위·위반 위치를 제공하는 `DesignMdRuleLoader` 구현 및 정상/경계/오류 fixture 테스트 완료
- [~] **R6-056 · P0** 회사 표준 Design Token 로드·매핑 — `DesignSystemProfile`·`DesignSystemSpec`·`VariableBinding`을 CSS Variable/Thymeleaf class/Component Property로 해석
- [~] **R6-057 · P0** `ScreenHtmlSkeletonGenerator`로 LIST/FORM/DETAIL 구조와 Binding 없는 slot을 생성. 제거된 구형 `ThymeleafSkeletonPlanner`/FreeMarker legacy renderer 대신 새 Workflow에 Component Registry/Token을 주입하는 단계는 미완료
- [ ] **R6-058 · P0** 제거된 `LegacyThymeleafViewComposer`/`LegacyThymeleafRenderer`를 대체해 `ThymeleafBindingContract`를 새 Skeleton에 결합하는 구현 필요
- [x] **R6-059 · P1** Desktop·Tablet·Mobile 변환 — 1440/768/390 grid, navigation swap, table→card, form/detail 재배치와 Binding 수 동일성 검증 테스트 완료
- [~] **R6-060 · P0** Preview/재검증에 Thymeleaf 정적 parse와 1440px overflow Gate 연결. 실제 TemplateEngine render, 고정 offline build, Playwright 접근성·visual regression은 미완료
- [~] **R6-061 · P0** `ThymeleafProjectWorkflowService`로 Preview→DESIGN.md 포함 canonical hash 승인→source/design revision 재검증→staging/backup→Apply/전체 rollback→재검증 상태 흐름과 실제 중간 실패 rollback 테스트 완료. 10단계 전체 `ThymeleafGenerationReport` 영속 저장은 미완료
- [~] **R6-062 · P0** 업무 계약을 침범하는 DESIGN.md 규칙은 FATAL, DESIGN.md revision은 Preview hash/source drift보다 함께 강제. Profile/Token→DESIGN.md→화면 Override의 생성 단계 전체 병합은 미완료
- [~] **R6-063 · P0** `route`·`field`·`validation`·`authority`·`csrf` 등 DESIGN.md 업무 계약 변경은 Preview 전에 차단하고 승인 후 DESIGN.md 변경은 CONFLICT/쓰기 0건으로 검증. 승인 Token 밖 값 하드코딩 검사는 미완료
- [x] **R6-064 · P1** Generator REST/MCP 진입점 구현 — `/api/thymeleaf/operations` Preview/Approve/Apply/Report/Revalidate 분리, REST X-API-Key와 MCP 공유 비밀키 선검증, Preview 전 파일 변경 0건·hash 불일치·source drift CONFLICT E2E 완료
- [ ] **R6-T14** 기존 `LegacyBindingContractAssemblerTest`가 현재 작업 트리에서 제거되어 새 Binding assembler 기준 골든 LIST/FORM/DETAIL 테스트 재구현 필요
- [ ] **R6-T15** LIST/FORM/DETAIL 화면 유형과 Component Inventory 선택이 근거·confidence·Registry 상태를 포함하는지 검증
- [~] **R6-T16** DESIGN.md 정상/미존재/알 수 없는 규칙/버전/구문/업무 Binding 변경 시도와 승인 후 drift 테스트 완료. 회사 Token 누락·금지 하드코딩 테스트는 미완료
- [ ] **R6-T17** 제거된 legacy renderer 테스트를 대체해 새 Binding composer의 `th:*`, CSRF, validation, route, iteration 정적·렌더 테스트 필요
- [~] **R6-T18** Desktop 1440/12·Tablet 768/8·Mobile 390/4 grid, navigation swap, table→card, mobile form 단일열, 세 viewport Binding 수 동일성과 정적 overflow Gate 검증 완료. 실제 브라우저 viewport overflow는 미완료
- [ ] **R6-T19** `EGOV_ALLOW_BUILD_EXECUTION`·허용 경로 정책, Maven/Gradle 성공·실패·timeout과 Thymeleaf parse/render Gate 검증
- [ ] **R6-T20** 동일 입력 재실행 결정성, 중간 FATAL 이후 단계 미실행, 단계별 산출물·버전·Issue 추적 검증

---

## 12. R7 — `.figpack` 하이브리드 흐름

### 12.1 Reference Snapshot 처리

- [x] **R7-001 · P0** `.figpack`을 Reference Snapshot으로 명시하는 메타데이터 추가
- [~] **R7-002 · P0** `document.json` → `UiDesignSpec` 변환 품질 보완 — 기존 `WebCaptureAnalysisService`/`RenderedDesignSpecMapper`의 confidence·uncertainty 변환을 재사용하며, 추가 휴리스틱 품질 평가는 운영 fixture로 남김
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
- [~] **R7-014 · P0** Published Component Instance로 화면 재생성 — R5 Plugin 출력으로 연결 완료, Figma Desktop 런타임 검증은 미실시
- [~] **R7-015 · P1** 원본 복제 프레임과 의미 기반 프레임의 시각 비교 — 노드·텍스트·컴포넌트·필드·Action·Viewport 구조 비교 보고서 구현, 픽셀/이미지 비교는 미구현
- [~] **R7-016 · P1** 허용 시각 오차와 의미 일치 기준 정의 — Viewport 일치와 FieldHint 매핑 누락 기준 구현, 이미지 기반 허용 오차 기준은 미정

### 12.3 R7 테스트

- [ ] **R7-T01** 공개 URL 캡처 → 후보 Spec → FigmaScreenSpec E2E
- [ ] **R7-T02** 로그인 화면 캡처 fixture 기반 변환 테스트
- [ ] **R7-T03** 캡처 실패 시 기존 WEB_CAPTURE 오류 보고 회귀 테스트
- [~] **R7-T04** 원본과 생성 화면의 텍스트·컴포넌트·레이아웃 비교 — 구조 비교 단위 테스트 통과, Figma 렌더 이미지 비교는 미실시
- [x] **R7-T05** `.figpack`을 FigmaScreenSpec으로 잘못 해석하지 않는지 검증 — 후보 생성은 `document.json` 기반 분석만 사용하고 `.figpack`은 Reference 다운로드에만 사용

### 12.4 구현된 Hybrid API와 저장 계약

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
- [~] **R8-T04** Figma Plugin 샘플 파일 E2E 통과 — 순수 core fixture 8건과 typecheck/lint/build 통과, Figma Desktop 실제 노드 Migration은 수동 QA 잔여
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

- [ ] 확정된 Schema와 예제 JSON이 CI 검증을 통과한다.
- [ ] 초기 필수 Component·Pattern·Page Template 목록이 선행 결정 게이트에서 승인된다. Plugin 입력 방식(`DEC-10`)과 신규 MCP Tool 인증 방식(`DEC-11`)은 확정 완료
- [ ] 사용자 목록·등록 Screen Specification에서 FigmaScreenSpec이 생성된다.
- [ ] FigmaScreenSpec이 Published KRDS/eGovFrame Component Instance로 생성된다.
- [ ] 같은 Spec을 다시 적용해도 동일 논리 노드와 컴포넌트가 중복 생성되지 않는다.
- [ ] 신규 항목만 생성되고 기존 사용자 수정 보호 대상은 유지된다.
- [ ] 필수 Component 누락 시 생성이 실패하고, 선택 Component의 fallback 노드는 Preview 단계에만 남으며 정식 생성 완료 판정에는 남아 있지 않다.
- [ ] Design System 변경 Preview를 사람이 검토한 후 Publish할 수 있다.
- [ ] Publish 후 Component Key가 Registry에 동기화된다.
- [ ] REST API, JSON 다운로드, MCP Tool의 인증과 오류 처리가 검증되고, 신규 MCP Tool은 전용 인증 없이는 호출되지 않는다.
- [ ] P1 요청 유형 4종이 동일 `FigmaDesignOperation` 계약으로 Preview Bundle을 만들고 Plugin Apply 전에는 `APPLIED`를 반환하지 않는다.
- [ ] Figma REST 조회·Spring AI 분석·Plugin 쓰기의 실행 경계와 민감정보 redaction이 검증된다.
- [ ] CRUD LIST·FORM·DETAIL이 10단계 Generator를 통과하고 Binding·DESIGN.md·Token·반응형·검증 결과를 하나의 보고서로 추적할 수 있다.
- [ ] 기존 WEB_CAPTURE와 eGovFrame 코드 생성 회귀 테스트가 통과한다.

### 17.1 확장 릴리스(R7~R8) 완료 조건

- [ ] `.figpack` 캡처를 후보 Screen Specification으로 변환하고 승인 후 의미 흐름을 탈 수 있다.
- [ ] 운영·롤백·드리프트 대응 문서가 준비된다.

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
