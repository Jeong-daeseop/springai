# Figma MCP와 eGovFrame JSP→Thymeleaf 통합 구현계획서

- 기준 문서 1: `Figma_MCP_디자인_오케스트레이션_구현계획서.md` v1.0.0
- 기준 문서 2: `eGovFrame_JSP_to_Spring_Boot_Thymeleaf_전환_구현계획서.md` v1.0.0
- 대상 저장소: `springai`
- 계획서 버전: 1.1.0
- 작성일: 2026-08-02
- 최종 반영일: 2026-08-02
- 구현 원칙: Figma 공통 기반 우선, 업무 Binding 조기 검증, 생성·적용 단계 순차 통합

## 1. 목적

이 문서는 다음 두 구현계획을 하나의 실행 가능한 순서로 통합한다.

1. Figma MCP 디자인 오케스트레이션
2. eGovFrame JSP 화면의 Spring Boot·Thymeleaf 전환

통합 우선순위는 다음과 같다.

> 1순위: Figma MCP 디자인 오케스트레이션의 계약·정책·Operation 기반<br>
> 2순위: eGovFrame JSP→Thymeleaf의 Source 분석·Binding Contract<br>
> 이후 두 계획의 생성·적용 단계를 순차 통합

두 계획을 하나의 거대한 Orchestrator로 합치지 않는다. 공유 가능한 계약과 정책만 공통화하고,
Figma Canvas Operation과 Thymeleaf Project Conversion은 독립 Use Case와 상태 모델을 유지한다.

## 2. 통합 목표

최종 시스템은 다음 두 흐름을 제공한다.

### 2.1 Figma 디자인 흐름

```text
사용자 디자인 요청
→ FigmaDesignRequest
→ Context·Reference·Style 분석
→ 영속 ANALYZED 승인 후보
→ ScreenSpecification 후보·사람 승인
→ FigmaScreenSpec
→ Bundle·불변 Artifact·영속 PREVIEW_READY Operation
→ Plugin Preview
→ 사용자 승인
→ Plugin Apply
→ Figma 적용 보고서
```

### 2.2 JSP 전환 흐름

```text
JSP + Controller + VO + CSS + JavaScript + 선택적 DB Schema
→ LegacyScreenAnalysis
→ ThymeleafBindingContract
→ Component·DESIGN.md·Token 결정
→ Thymeleaf Skeleton
→ Model Binding
→ Responsive 변환
→ Project Preview
→ 사용자 승인
→ 파일 Apply
→ Render·Build·Parity 보고서
```

### 2.3 선택적 연결 흐름

```text
Figma MCP에서 승인된 ScreenSpecification
                  │
                  ▼
JSP 업무 분석 → ThymeleafBindingContract
                  │
                  ▼
디자인 구조와 업무 Binding 교차 검증
                  │
                  ▼
Thymeleaf 생성
```

Figma 결과는 화면 구조·Component·Token의 디자인 입력으로 사용할 수 있지만, route, field,
validation, 권한과 CSRF를 변경할 수 없다.

## 3. 핵심 설계 결정

### 3.1 공통화할 항목

다음 값 객체와 정책은 두 흐름에서 같은 의미와 버전 규칙을 사용한다.

- `DesignSystemSnapshotRef`
  - profileId
  - profileVersion
  - registryVersion
  - layoutPolicyVersion
- `PlatformLayoutPolicy`
- `ComponentSwapPolicy`
- `ArtifactRef`
- `GenerationIssue`
  - code
  - severity
  - stage
  - sourceLocation
  - message
  - remediation
- `SourceRevisionRef`
- content hash 정규화 규칙
- idempotency key 생성 규칙
- 민감정보 redaction 규칙
- Preview 승인 감사정보

### 3.2 분리할 항목

다음 모델은 도메인 의미가 다르므로 통합하지 않는다.

| Figma 도메인 | Thymeleaf 전환 도메인 | 분리 이유 |
|---|---|---|
| `FigmaDesignRequest` | `LegacyConversionRequest` | 디자인 요청과 소스 전환 요청의 입력이 다름 |
| `FigmaDesignOperation` | `ThymeleafProjectOperation` | Canvas 적용과 파일 적용의 트랜잭션 경계가 다름 |
| `FigmaScreenSpec` | `ThymeleafBindingContract` | 디자인 노드 계약과 업무 Binding 계약이 다름 |
| Plugin Preview/Apply | Project Preview/Apply | Figma revision과 파일 source revision 검증 방식이 다름 |
| Figma Apply Report | `ThymeleafGenerationReport` | 캔버스 결과와 빌드·렌더 결과가 다름 |

### 3.3 업무 계약 우선순위

규칙 충돌 시 다음 순서를 강제한다.

1. Controller·VO·DB Schema의 업무 Binding과 보안
2. `ThymeleafBindingContract`
3. 승인된 `ScreenSpecification`
4. `DesignSystemProfile`·Component Registry·Layout Policy
5. 프로젝트 `DESIGN.md`
6. 화면별 승인 Override
7. Generator 기본값

Figma 디자인과 `DESIGN.md`는 route, HTTP method, field source, validation, 권한, CSRF를
변경할 수 없다.

### 3.4 Orchestrator 분리

```text
FigmaDesignOrchestrationService
└─ Figma 요청의 분석·Bundle·Plugin 상태만 조율

ThymeleafProjectWorkflowService
└─ Preview·승인·source/design revision·원자 파일 적용·rollback·재검증 조율

공통 정책 서비스
├─ DesignSystemSnapshotResolver
├─ PlatformPolicyResolver
├─ ComponentRegistryResolver
├─ OperationHashFactory
└─ GenerationIssueFactory
```

두 Orchestrator가 서로를 직접 호출하지 않는다. 필요한 경우 승인된 `ScreenSpecification`과
버전이 있는 Artifact를 통해 연결한다.

## 4. 현재 구현 기준선

### 4.1 Figma 재사용 자산

- `FigmaScreenExportService`
- `FigmaScreenBuilderRegistry`
- `FigmaScreenSpecValidator`
- `FigmaExportBundleAssembler`
- `FigmaMcpFacadeService`
- `FigmaApiClient`
- `DesignReferenceAnalysisService`
- `FigmaDesignSpecMapper`
- `ComponentRegistryResolver`
- `DesignSystemQueryService`
- `figma-screen-spec-plugin`
- `website-figma-contract`
- Preview·MERGE·REPLACE·Archive·생성 이력

### 4.2 Thymeleaf 재사용 자산

- `ScreenSpecAssembler`
- `ScreenFieldBinding`
- `GenerationQueryContractFactory`
- `CrudModelFactory`, `BoardModelFactory`
- `CrudTemplateRenderer`, `BoardTemplateRenderer`, `MasterDetailTemplateRenderer`
- `ThymeleafLayoutGenerationService`
- `ThymeleafLayoutGenerationPlanner`
- `ThymeleafRenderValidator`
- `GeneratedProjectBuildValidator`
- `ProjectScannerService`
- CRUD·Board·MasterDetail Pipeline과 생성 이력

### 4.3 Extractor 재사용 자산

- `jsp-design-extractor`의 Playwright 기반 `RENDERED_URL` 분석
- `RenderedDesignDocument`
- `.figpack`
- DOM·computed style·screenshot·asset 분석
- URL·origin·resource·response size 보안 정책
- LIST·DETAIL·REGIST·UPDT E2E fixture

### 4.4 2026-08-02 현재 구현 스냅샷

완료된 연결:

- `FigmaDesignRequest`·`FigmaDesignOperation` 공통 계약과 불변 revision 저장
- canonical SHA-256 멱등 처리와 source revision `CONFLICT`
- 승인 ScreenSpecification → `FigmaScreenExportService.exportBundle()` →
  `DesignArtifactService.saveFigmaExportBundle()` → 영속 `PREVIEW_READY`
- 승인 Bundle REST/MCP 진입점과 기존 7개 Figma MCP callback의 선행 공유키 인증
- Plugin 보고 전 `APPLIED` 전이 금지
- 안전한 Thymeleaf Project Preview → hash 승인 → source/`DESIGN.md` revision 재검증 →
  staging/backup → Apply/전체 rollback → 재검증
- Desktop 1440/12·Tablet 768/8·Mobile 390/4 변환과 Binding 수 동일성 검사
- Thymeleaf REST Preview와 MCP Approve/Apply/Revalidate 교차 E2E

부분 완료 또는 잔여 작업:

- 일반 Figma 7개 요청은 영속 `ANALYZED` 승인 후보까지만 공통화됨. 자연어 분석 결과로
  ScreenSpecification을 자동 생성하는 단계는 미연결
- Image Vision export, 멀티 화면 Canvas 원자 rollback, Figma Platform 실제 노드 변환은 미완료
- `DESIGN.md` 업무 계약 침범과 승인 후 drift는 차단하지만 회사 Token·Component Inventory를
  최종 HTML 생성에 병합하는 전체 우선순위는 미완료
- 현재 작업 트리에서 제거된 legacy Binding assembler/renderer를 대체할 새
  `ThymeleafBindingContract` → Bound View composer가 필요
- 정적 Thymeleaf parse·overflow Gate는 연결됐으나 실제 TemplateEngine render, 고정 offline build,
  Playwright 접근성·visual regression과 보고서 영속 저장은 미완료
- 실제 Figma Desktop과 대표 Maven/Gradle eGovFrame 프로젝트의 사람 승인 증적은 별도 실환경 Gate

세부 체크 상태의 단일 추적 원본은
[`12_Semantic_Figma_Design_System_Implementation_List.md`](./12_Semantic_Figma_Design_System_Implementation_List.md)다.

## 5. 통합 작업 순서

```text
I-0 통합 경계·공통 계약
  ↓
I-1 Figma 계약·정책·Operation 기반
  ↓
I-2 JSP Source 분석·Binding Contract
  ├──────────────────────────────┐
  ▼                              ▼
I-3 Figma 핵심 4개 요청 MVP   I-4 Thymeleaf HTML 생성
  │                              │
  └──────────────┬───────────────┘
                 ▼
I-5 Thymeleaf Responsive·Preview·검증
                 ↓
I-6 Figma MCP Tool·Plugin Apply
                 ↓
I-7 교차 통합·E2E
                 ↓
I-8 고급 기능·운영 안정화
```

## 6. 통합 작업 패키지

### I-0. 통합 경계와 공통 계약 확정

목표: 두 계획이 동일 개념을 서로 다른 이름과 규칙으로 중복 구현하지 않도록 경계를 확정한다.

작업:

- 두 도메인의 Request·Operation·Artifact·Report 관계 문서화
- `DesignSystemSnapshotRef` 계약 정의
- `PlatformLayoutPolicy`와 `ComponentSwapPolicy` 소유 패키지 확정
- `GenerationIssue`, `ArtifactRef`, `SourceRevisionRef` 공통 계약 정의
- content hash·idempotency·redaction 공통 규칙 확정
- Figma와 Project Apply의 독립 상태 전이 확정
- JSON Schema `$id`, version, 호환성 정책 확정
- 정상·경계·오류 fixture 디렉터리 규칙 확정

완료 게이트:

- 공통 값 객체에 Figma 또는 Thymeleaf 전용 필드가 없음
- 두 Operation 상태가 서로 잘못 전이되지 않음
- 같은 Snapshot 조합이 같은 canonical hash를 생성함
- 알 수 없는 계약 버전과 상태를 거부함

선행 작업: 없음

### I-1. Figma 계약·정책·Operation 기반

원본 매핑: Figma WP-0~WP-2, R6-030~031의 기반, Operation 관련 R5-040~043

목표: 디자인 요청을 안전하고 멱등적인 Preview Operation으로 저장할 수 있게 한다.

작업:

1. `figma-design-request-v1.schema.json`
2. `figma-design-operation-v1.schema.json`
3. `FigmaDesignRequest`, `FigmaDesignScreenRequest`
4. `FigmaDesignOperation`, `FigmaDesignOperationStatus`
5. `DefaultLayoutPolicy`, `PlatformLayoutPolicy`, `ComponentSwapPolicy`
6. `FigmaDesignOperationRepository`
7. `FigmaDesignOperationStateService`
8. request hash 기반 멱등 처리
9. source revision optimistic conflict
10. 멀티 화면 Operation 원자 상태

상태:

```text
ANALYZED
→ PREVIEW_READY
→ APPLY_REQUIRED
→ APPLIED

분기: FAILED | CONFLICT | REJECTED
```

완료 게이트:

- 7개 요청 fixture Schema 통과
- Profile·Registry·Layout Policy 불일치 거부
- 동일 요청 재시도 시 Operation 중복 없음
- Plugin 보고서 전 `APPLIED` 전이 불가
- source revision 불일치 시 `CONFLICT`

선행 작업: I-0

### I-2. JSP Source 분석과 Binding Contract

원본 매핑: Thymeleaf WP-0~WP-4, R6-050~054, R6-T14~15

목표: HTML 생성 전에 기존 업무 동작을 추적 가능한 계약으로 확정한다.

#### I-2A. 전환 계약

- `legacy-conversion-request-v1.schema.json`
- `local-source-design-document-v1.schema.json`
- `legacy-screen-analysis-v1.schema.json`
- `thymeleaf-binding-contract-v1.schema.json`
- `thymeleaf-generation-report-v1.schema.json`
- `ThymeleafGenerationStageResult<T>`

#### I-2B. Source 보안·Inventory

- allowed real path 검증
- traversal·symlink 우회 차단
- `.env`, key, pem, certificate, credential 제외
- `.git`, build, upload, temp, `node_modules` 제외
- 파일 수·개별 크기·전체 크기 제한
- Source hash와 project fingerprint
- 설정·build script 실행 금지

#### I-2C. 업무 Source Reader

- JSP taglib, include, form tag, JSTL, EL, hidden, errors
- Controller mapping, Model, request/path parameter, redirect
- VO field, type, Bean Validation, format
- form action과 Controller route 연결
- 반환 View와 JSP 연결
- Security Annotation, CSRF, 권한 증거

#### I-2D. Frontend Source Graph

`jsp-design-extractor`를 모듈화하고 다음 정적 Reader를 추가한다.

- CSS·SCSS dependency graph
- Bootstrap·Tailwind profile
- JavaScript selector·event·submit·fetch/XHR graph
- Local asset inventory
- source location·confidence·runtime verification 표시

#### I-2E. Binding Contract

```text
JSP + Controller + VO + 선택적 DB Schema
→ LegacyScreenAnalysis
→ ThymeleafBindingContract
```

충돌 정책:

- Controller·VO·DB Schema가 JSP hint보다 우선
- VO field 없는 form path는 FATAL
- Model attribute 없는 EL은 미해결
- writable=false field에 입력 Binding 금지
- route와 action method 불일치는 FATAL
- 증거 부족은 `REVIEW_REQUIRED`

완료 게이트:

- LIST·FORM·DETAIL fixture의 JSP·Controller·VO 연결 성공
- 모든 입력 후보가 실제 writable VO field에 연결됨
- 모든 조회 표현식이 Model attribute에 연결됨
- route·method·validation provenance 보존
- FATAL 이후 후속 단계가 실행되지 않음

선행 작업: I-1의 공통 정책 계약 완료

### I-3. Figma 핵심 4개 요청 MVP

원본 매핑: Figma WP-3~WP-5, R6-032~034, R6-037, R6-040~044

목표: Text·Reference·Modify·Component 지정 요청을 `PREVIEW_READY`까지 제공한다.

작업:

- `FigmaApiClient` files/nodes/components/styles 조회 확장
- pagination·429 Retry-After·timeout·응답 크기 제한
- fileKey allowlist와 node 소속 검증
- `FigmaDesignRequestRouter`
- `FigmaContextAnalyzer`
- `FigmaStyleExtractor`
- `FigmaDesignOrchestrationService`
- 기존 `FigmaScreenExportService`와 Bundle 연결
- Operation 저장과 Preview summary 생성

처리 순서:

```text
인증·입력 검증
→ file/node/Profile allowlist
→ Snapshot Preflight
→ Context·Reference 분석
→ ScreenSpecification 후보
→ FigmaScreenSpec
→ Bundle·Operation
→ Schema·의미 검증
→ PREVIEW_READY
```

완료 게이트:

- Text·Reference·Modify·Component 요청 서비스 E2E 통과
- 명시 Tool 유형이 LLM 분류보다 우선함
- 낮은 confidence는 추측 실행하지 않음
- 동일 입력의 Spec·logicalNodeId·Operation hash 결정성
- 분석 결과만으로 `APPLIED`를 반환하지 않음

선행 작업: I-1

현재 판정: **부분 완료**. 승인된 ScreenSpecification 경로는 실제 Bundle·Artifact·Operation까지
연결됐고 일반 요청은 영속 `ANALYZED`로 저장된다. 핵심 4개 요청의 후보 ScreenSpecification 자동
생성과 서로 다른 Bundle 생성 E2E는 남아 있다.

### I-4. 디자인 기반 Thymeleaf HTML 생성

원본 매핑: Thymeleaf WP-5~WP-6, R6-055~058, R6-062~063

목표: 검증된 Binding Contract와 승인된 디자인 정책으로 Thymeleaf 화면을 생성한다.

#### I-4A. 화면·Component 결정

- LIST, FORM, DETAIL, DASHBOARD 판정
- STANDARD, BOARD, MASTER_DETAIL, DASHBOARD Layout 판정
- Component Inventory 선택과 Registry 상태 확인
- 선택 근거·confidence·fallback 기록

#### I-4B. 디자인 규칙

- 프로젝트 루트 `DESIGN.md` 탐색
- 규칙 version·source location·우선순위 파싱
- 회사 `DesignSystemProfile`·Token 매핑
- CSS Variable·class·Component Property 해석
- Token 누락과 하드코딩 차단

#### I-4C. Skeleton과 Binding 분리

```text
Screen Structure + Component Inventory + Token
→ ThymeleafSkeleton

ThymeleafSkeleton + ThymeleafBindingContract
→ BoundThymeleafView
```

생성 대상:

- layout·fragment·slot
- LIST·FORM·DETAIL HTML
- `th:object`, `th:field`, `th:text`, `th:each`
- action·HTTP method·CSRF
- validation error
- 필요한 범위의 Controller Adapter

완료 게이트:

- Skeleton에 임의 `th:field`가 없음
- 모든 `th:*`가 Binding Contract provenance를 가짐
- `DESIGN.md`가 업무 계약을 변경하면 FATAL
- 승인 Token 밖 CSS 값 하드코딩 없음
- 기존 CRUD·Board·MasterDetail Renderer 회귀 없음

선행 작업: I-2, I-1의 Platform·Component 정책

현재 판정: **`[~]` 기능 프로토타입**. `DESIGN.md` 로드·버전·금지 업무 규칙 Gate와 Skeleton/Responsive 기반은
존재한다. 제거된 legacy Binding composer/renderer를 대체하는 새 Bound View 생성 경로와
Profile/Token/Registry 전체 병합은 남아 있다.

### I-5. Thymeleaf Responsive·Preview·Apply·검증

원본 매핑: Thymeleaf WP-7~WP-10, R6-059~064, R6-T18~20

목표: 생성 결과를 세 Viewport에서 검증하고 승인 후 안전하게 프로젝트에 적용한다.

#### I-5A. Responsive

| Platform | 기준 폭 | Grid | Navigation |
|---|---:|---:|---|
| Desktop | 1440 | 12 | side navigation |
| Tablet | 768 | 8 | drawer |
| Mobile | 390 | 4 | bottom navigation |

- table→card/list 정책
- form column 재배치
- footer·navigation Component Swap
- sticky/fixed/breakpoint annotation
- 세 Viewport에서 동일 Binding Contract 유지

#### I-5B. Project Operation

```text
ANALYZED
→ CONTRACT_READY
→ PREVIEW_READY
→ APPROVED
→ APPLIED
→ VALIDATED

분기: FAILED | CONFLICT | REJECTED
```

- Preview artifact와 대상 경로 목록 생성
- 승인 전 대상 프로젝트 쓰기 금지
- Apply 직전 source revision 재검사
- 기존 파일 Backup과 충돌 보고
- 부분 적용 방지
- Apply 후 재검증

#### I-5C. 검증 Gate

- Thymeleaf parse/render
- Binding 정적 검증
- route·field·action·validation parity
- 1440·768·390 overflow 검증
- 접근성 검사
- 선택적 visual regression
- 허용 경로의 Maven·Gradle offline build
- timeout과 출력 크기 제한

완료 게이트:

- 승인 전 파일 변경 없음
- source revision 변경 시 `CONFLICT`
- 세 Viewport의 Binding hash 동일
- Thymeleaf render 통과
- JSP 대비 route·field·action parity 통과
- 허용 환경 빌드 성공
- 단계별 hash·Issue·artifact 추적 가능

선행 작업: I-4

현재 판정: **`[~]` 기능 프로토타입**. 승인 전 쓰기 0건, Preview hash, source/`DESIGN.md` drift 충돌,
경로 탈출·중간 symlink 차단, staging/backup, 실제 중간 실패 전체 rollback, 재검증 및 REST/MCP
교차 흐름까지 구현됐다. 실제 render/offline build/a11y/visual Gate와 Report 영속화는 남아 있다.

### I-6. Figma MCP Tool과 Plugin Apply

원본 매핑: Figma WP-6~WP-7, R6-039·047, R5-040~045, R6-T08·11

목표: 핵심 디자인 요청을 MCP로 노출하고 실제 Plugin Apply 결과를 Operation 상태에 연결한다.

작업:

- `FigmaDesignOrchestrationTool` 7개 callback 등록
- `FigmaMcpFacadeService` 인증·redaction 적용
- Tool은 검증과 서비스 위임만 수행
- Plugin에 operationId·requestType·sourceRevision 표시
- Preview diff와 서버 summary 교차 검증
- Apply 직전 Figma source revision 확인
- 멀티 화면 전체 Preview와 원자 Apply 기반 마련
- 실제 Plugin 보고서 수신 후에만 `APPLIED`

Tool:

- `create_design_from_text`
- `create_design_from_reference`
- `modify_existing_design`
- `create_design_from_image`
- `create_multi_screen_flow`
- `create_design_with_components`
- `convert_platform`

고급 기능이 아직 준비되지 않은 Tool은 등록하더라도 명확한 capability 오류를 반환하며 fallback으로
임의 적용하지 않는다.

완료 게이트:

- callback 이름 중복 없음
- 인증 실패가 Repository 접근 전에 차단됨
- MCP 응답에 access token·LLM key·Published Key 원문 없음
- revision 충돌 시 캔버스 변경 없음
- Plugin 보고서 전 `APPLIED` 상태 없음
- MERGE 사용자 Override 보존

선행 작업: I-3, I-1

현재 판정: **`[~]` 기능 프로토타입**. 7개 callback과 승인 Bundle callback은 모두 등록됐고 8개 Tool이
`figmaMcpSecret`을 서비스/Repository 접근 전에 검증한다. MCP 계약 기준선은 97 methods/35 objects다.
실제 Figma API revision·editable node 재검증과 Desktop Canvas 원자 Apply 증적은 남아 있다.

### I-7. 교차 통합과 E2E

목표: Figma 디자인 흐름과 JSP 전환 흐름이 독립 실행되고, 승인된 Artifact를 통해 선택적으로
연결되는지 검증한다.

시나리오 A — 독립 Figma 생성:

```text
Text 요청
→ ScreenSpecification
→ FigmaScreenSpec
→ Plugin Preview·Apply
```

시나리오 B — 독립 JSP 전환:

```text
JSP·Controller·VO
→ Binding Contract
→ Thymeleaf 생성
→ Preview·Apply·Build
```

시나리오 C — 디자인 결합 전환:

```text
승인 Figma ScreenSpecification
          +
JSP 업무 Binding Contract
          ↓
디자인 구조·업무 계약 교차 검증
          ↓
Thymeleaf 생성
```

시나리오 D — `.figpack` HYBRID:

```text
실행 중 JSP
→ jsp-design-extractor
→ .figpack / UiDesignSpec
→ ScreenSpecification 후보 승인
→ Binding Contract 결합
→ Thymeleaf 생성
```

교차 검증:

- 같은 SnapshotRef 사용
- Component logical type 일치
- Figma와 Thymeleaf Platform Policy version 일치
- Figma 디자인 field 후보가 Binding Contract를 덮어쓰지 않음
- Figma Apply 실패가 Thymeleaf Operation 상태를 변경하지 않음
- Thymeleaf Build 실패가 Figma Operation을 `FAILED`로 변경하지 않음
- 공통 correlationId로만 감사 추적 연결

완료 게이트:

- 네 시나리오 E2E 통과
- 독립 실패 격리
- 승인되지 않은 Artifact 연결 거부
- Profile·Registry·Policy version drift 탐지
- 동일 입력 재실행 결정성

선행 작업: I-5, I-6

현재 판정: **부분 완료**. 동일 `ThymeleafProjectWorkflowService` 상태 원장을 사용해 REST Preview 후
MCP 승인·Apply·Revalidate를 수행하는 교차 E2E는 통과한다. 승인 Figma Artifact 결합과 `.figpack`
HYBRID를 포함한 네 시나리오 전체 E2E 및 `correlationId` 감사 연결은 남아 있다.

### I-8. 고급 기능과 운영 안정화

Figma:

- Image Node 참조
- 멀티 스크린 원자 생성
- Desktop·Tablet·Mobile Figma 변환
- 실제 Figma Desktop 7개 요청 E2E

Thymeleaf:

- `HYBRID` 분석
- Source와 computed style 차이 보고
- 승인된 SCSS·Tailwind compile 비교
- 시각 회귀 기준 이미지
- Batch 화면 전환

운영:

- operationId·conversionId·correlationId 구조화 로그
- Preview 반려율·충돌률·빌드 실패율·fallback 비율
- Artifact retention과 cleanup
- Runbook·Rollback·장애 복구
- Release artifact secret scan

선행 작업: I-7

## 7. 릴리스 계획

현재 판정:

| Release | 상태 | 현재 근거/잔여 Gate |
|---|---|---|
| 1 공통 기반 | 완료 | 공통 계약, canonical hash, 불변 Operation, 멱등·충돌 테스트 완료 |
| 2 업무 Binding 기반 | 부분 완료 | Source Reader와 계약 모델은 존재하나 새 Binding assembler 재연결 필요 |
| 3 두 MVP 생성기 | 부분 완료 | 승인 Figma Bundle 경로 완료, 자동 ScreenSpecification·새 Bound View 생성 미완료 |
| 4 안전한 적용 | 부분 완료 | Thymeleaf 원자 Apply/rollback과 MCP 인증 완료, 실제 build 및 Figma Canvas Gate 미완료 |
| 5 교차 통합 | 부분 완료 | REST→MCP Project E2E 완료, 승인 Figma/`.figpack` 결합 E2E 미완료 |
| 6 고급·운영 | 미완료 | 실제 Desktop, Image/Multi-screen/Platform, a11y/visual, 운영 증적 필요 |

### Release 1 — 공통 기반

범위: I-0~I-1

완료 결과:

- 공통 Snapshot·Policy·Issue 계약
- Figma Request·Operation Schema
- 멱등 저장과 상태 전이

### Release 2 — 업무 Binding 기반

범위: I-2

완료 결과:

- LOCAL_SOURCE LIST·FORM·DETAIL 분석
- JSP·Controller·VO 연결
- `ThymeleafBindingContract`

### Release 3 — 두 MVP 생성기

범위: I-3~I-4

완료 결과:

- Figma 핵심 4개 요청 `PREVIEW_READY`
- 디자인 시스템 기반 Thymeleaf HTML 생성
- Skeleton과 Binding 분리

### Release 4 — 안전한 적용

범위: I-5~I-6

완료 결과:

- Thymeleaf Responsive·Project Preview·Apply·Build
- Figma 7개 MCP Tool·Plugin Apply 상태 연결

### Release 5 — 교차 통합

범위: I-7

완료 결과:

- 승인된 ScreenSpecification과 Binding Contract 결합
- `.figpack` 선택적 HYBRID 연결
- 독립 실패 격리

### Release 6 — 고급 기능·운영

범위: I-8

완료 결과:

- Image·Multi-screen·Platform
- Batch·visual regression
- 실제 Figma·대상 프로젝트 운영 검증

## 8. 테스트 전략

### 8.0 최신 자동 검증 증적

2026-08-02 현재 다음 검증이 통과했다.

- Backend: `./gradlew test bootJar` — 926 tests, failures 0, errors 0
- Contract: 16 schemas, SHA-256 `83ea58779e416889ef95805781880a0a1e3cf692a1f7dfd7de00e14fcdc3a5da`
- Extractor: typecheck·lint·4 fixture E2E·결정적 hash 통과
- Plugin: typecheck·lint·17 tests·build 통과
- MCP Tool snapshot: 97 methods, 35 tool objects, callback 이름 중복 없음
- `git diff --check` 통과

이 결과는 자동 검증 증적이며 §8.5의 실제 Figma Desktop·대표 프로젝트 사람 승인을 대체하지 않는다.

### 8.1 공통 계약

```bash
cd website-figma-contract
npm test
```

- JSON Schema 정상·오류 fixture
- Java·Plugin enum과 필수값 교차 검증
- canonical hash와 idempotency
- Snapshot version drift
- redaction

### 8.2 Extractor

```bash
cd jsp-design-extractor
npm run typecheck
npm run lint
npm test
```

- RENDERED_URL 회귀
- LOCAL_SOURCE Reader
- traversal·symlink·secret 제외
- CSS·JS·Asset source graph
- 결정론적 output hash

### 8.3 Figma Plugin

```bash
cd figma-screen-spec-plugin
npm run typecheck
npm run lint
npm test
npm run build
```

- Preview·Apply 상태 분리
- revision conflict
- MERGE Override 보존
- Operation report
- 멀티 화면 부분 적용 방지

### 8.4 Backend

```bash
./gradlew test
./gradlew bootJar
```

- Figma Request·Operation·Router·Orchestrator
- JSP·Controller·VO Source Reader
- Binding Contract
- Skeleton·Binding·Responsive
- REST·MCP 등록과 인증
- Render·Build·Parity Gate
- 실패 격리와 결정론

### 8.5 실제 환경 E2E

- Figma Desktop에서 핵심 4개 요청 Preview·Apply
- 대표 eGovFrame JSP LIST·FORM·DETAIL 전환
- Maven과 Gradle 대상 프로젝트 각 1개 빌드
- Desktop 1440·Tablet 768·Mobile 390 검증
- Figma Artifact를 사용한 Thymeleaf 결합 시나리오

## 9. 보안 원칙

- `.env`, key, certificate, credential을 Source Inventory에 포함하지 않는다.
- Figma access token과 LLM API key를 Tool 인자·응답·로그에 포함하지 않는다.
- fileKey와 node ID는 승인 Profile과 소속 관계를 검증한다.
- Published Component·Variable Key 원문은 공개 응답에서 redaction한다.
- 외부 CSS·JavaScript·이미지를 자동 다운로드하지 않는다.
- JavaScript·SCSS·PostCSS·Tailwind 설정을 임의 실행하지 않는다.
- LLM 원문을 Plugin 명령이나 파일 쓰기 명령으로 직접 실행하지 않는다.
- 승인 전 Figma Canvas와 대상 프로젝트를 변경하지 않는다.
- Apply 직전에 source revision과 허용 범위를 다시 검사한다.
- Maven·Gradle 실행은 allowlist·고정 명령·offline·timeout·출력 제한을 적용한다.
- Release artifact와 Git staging 전에 secret scan을 수행한다.

## 10. 주요 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 두 Orchestrator 통합 | 거대 결합도와 장애 전파 | 독립 Use Case·상태·Repository 유지 |
| 공통 모델 과도한 추상화 | 도메인 의미 손실 | 값 객체만 공통화하고 Operation은 분리 |
| Figma field 후보가 업무 Binding 변경 | 동작·보안 회귀 | Binding 우선순위 Validator로 차단 |
| Plugin Apply와 Project Apply 상태 혼동 | 잘못된 성공 보고 | 독립 상태와 report type 유지 |
| Snapshot version drift | Figma와 HTML 디자인 불일치 | 원자 SnapshotRef와 Preflight |
| JSP·Controller·VO 연결 오판 | 잘못된 Thymeleaf Binding | provenance 필수, 불확실성은 중단 |
| Figma API·LLM 장애 | 전체 Pipeline 중단 | Figma 흐름 실패 격리, 기존 승인 Artifact 사용 |
| 대상 프로젝트 빌드 실패 | 적용 후 불완전 상태 | Preview·Backup·Apply 후 자동 Gate |
| 민감정보 노출 | 보안 사고 | Source 제외·redaction·artifact scan |
| 고급 기능 조기 착수 | 핵심 계약 불안정 | I-0~I-7 완료 전 I-8 금지 |

## 11. 작업 추적 매핑

| 통합 작업 | Figma 계획 | Thymeleaf 계획 | 구현 목록 |
|---|---|---|---|
| I-0 | 공통 경계 | 공통 경계 | DEC·공통 Contract |
| I-1 | WP-0~2 | 정책 참조 | R6-030~031, Operation 관련 R5-040~043 |
| I-2 | frontend evidence 참조 | WP-0~4 | R6-050~054, R6-T14~15 |
| I-3 | WP-3~5 | ScreenSpecification 참조 | R6-032~034, 037, 040~044 |
| I-4 | Registry·Profile 제공 | WP-5~6 | R6-055~058, 062~063, R6-T16~17 |
| I-5 | Platform Policy 제공 | WP-7~10 | R6-059~064, R6-T18~20 |
| I-6 | WP-6~7 | 없음 | R6-039·047, R5-040~045, R6-T08·11 |
| I-7 | Artifact·ScreenSpec | Binding·Generator | R7 Hybrid, 교차 E2E |
| I-8 | WP-8~9 | Release E | R6-035~038·045~046, 운영 항목 |

구현 완료 상태는 이 문서에 중복 체크하지 않고
`12_Semantic_Figma_Design_System_Implementation_List.md`를 단일 추적 원본으로 사용한다.

## 12. 최종 완료 정의

다음 조건을 모두 만족하면 통합 구현을 완료한다.

- Figma와 Thymeleaf 흐름이 독립 Request·Operation·Report를 가진다.
- 공통 Snapshot·Platform·Component 정책의 version과 hash가 일치한다.
- Figma 핵심 4개 요청이 Preview·승인·Apply를 통과한다.
- JSP·Controller·VO가 하나의 `ThymeleafBindingContract`로 연결된다.
- 생성된 모든 `th:*` 표현식이 Binding provenance를 가진다.
- Figma 디자인이 업무 route·field·validation·권한·CSRF를 변경하지 못한다.
- Desktop·Tablet·Mobile이 동일 Binding Contract를 공유한다.
- Preview 승인 전 Canvas와 대상 프로젝트에 변경이 없다.
- 실제 적용 보고 전 Operation을 `APPLIED`로 저장하지 않는다.
- Thymeleaf parse/render, parity와 허용 환경 build가 통과한다.
- 두 흐름 중 하나의 실패가 다른 흐름의 상태를 변경하지 않는다.
- 같은 입력과 계약 버전의 재실행 결과 hash가 동일하다.
- API Key, `.env`, Published Key 원문과 민감한 Source 데이터가 산출물에 없다.
- 전체 Backend·Contract·Extractor·Plugin CI가 성공한다.

## 13. 다음 착수 작업

공통 계약과 Operation 기반은 완료됐으므로 다음 순서로 잔여 Gate를 닫는다.

1. Text·Reference·Modify·Component 요청에서 후보 ScreenSpecification을 생성하고 사람 승인 후
   현재 Bundle/Artifact 오케스트레이션으로 연결한다.
2. 새 `ThymeleafBindingContract` assembler와 Bound View composer를 구현해 JSP·Controller·VO
   provenance를 Skeleton의 모든 `th:*`·route·CSRF·validation에 연결한다.
3. Profile/Registry/Token → `DESIGN.md` → 승인 Override 우선순위를 최종 HTML에 적용하고 승인 Token
   밖 하드코딩을 FATAL로 차단한다.
4. 실제 TemplateEngine render, 고정 offline Maven/Gradle build, Playwright 1440/768/390·접근성 Gate와
   `ThymeleafGenerationReport` 영속 저장을 연결한다.
5. 승인 Figma Artifact와 `.figpack` HYBRID 결합 Validator, 독립 실패 격리, `correlationId` 기반
   네 시나리오 교차 E2E를 완성한다.
6. Figma Desktop에서 revision/editable 범위 재검증, Plugin 보고, 멀티 화면 rollback을 실제 검증하고
   대표 Maven/Gradle eGovFrame 프로젝트 승인 증적을 Runbook에 기록한다.

## 14. 변경 이력

| 버전 | 날짜 | 변경 내용 |
|---|---|---|
| 1.1.0 | 2026-08-02 | 승인 ScreenSpecification→Bundle→Artifact 영속 연결, 8개 Figma MCP 인증, Design-aware Thymeleaf 원자 Apply/rollback, REST→MCP E2E, 자동 검증 증적과 잔여 실환경 Gate 반영 |
| 1.0.0 | 2026-08-02 | Figma MCP와 JSP→Thymeleaf 초기 통합 실행 순서 정의 |
