# eGovFrame JSP를 Spring Boot·Thymeleaf로 전환하는 구현계획서

- 기준 문서: `eGovFrame_JSP_to_Spring_Boot_Thymeleaf_전환_작업_명세서.md` v1.2
- 대상 저장소: `springai`
- 계획서 버전: 1.0.0
- 작성일: 2026-08-02
- 핵심 범위: 업무 Binding 보존, 디자인 시스템 적용, Preview 승인, 렌더·빌드 검증

## 1. 목적

기존 eGovFrame JSP 화면과 Controller·VO·CSS·JavaScript를 분석해 업무 동작을 보존한
Thymeleaf 화면으로 전환한다.

최종 결과는 다음을 만족해야 한다.

- 기존 route, HTTP method, request parameter, Model attribute를 보존한다.
- VO field, validation, 권한, CSRF와 action 의미를 변경하지 않는다.
- 승인된 Component Registry와 회사 Design Token을 사용한다.
- 프로젝트 `DESIGN.md`의 시각·Layout·Component 규칙을 적용한다.
- Desktop·Tablet·Mobile이 하나의 `ThymeleafBindingContract`를 공유한다.
- 생성된 HTML을 실제 Thymeleaf 엔진으로 렌더링한다.
- 허용된 환경에서 Maven 또는 Gradle 빌드를 통과한다.
- 단계별 입력 Hash, 계약 버전, Issue와 산출물을 보고서로 추적한다.

## 2. 구현 원칙

### 2.1 업무 계약 우선

규칙 충돌 시 다음 우선순위를 적용한다.

1. Controller·VO·DB Schema의 업무 Binding과 보안 제약
2. 승인된 `ScreenSpecification`과 `ThymeleafBindingContract`
3. 회사 `DesignSystemProfile`·Component Registry·Design Token
4. 프로젝트 `DESIGN.md`
5. 화면별 승인 Override
6. Generator 기본값

Figma, 디자인 시스템과 `DESIGN.md`는 route, field, validation, 권한, CSRF를 바꿀 수 없다.

### 2.2 단일 Binding 원본

HTML과 Controller가 서로 독립적으로 field 이름을 추론하지 않는다.

```text
JSP + Controller + VO + 선택적 DB Schema + ScreenSpecification
  → 교차 검증
  → ThymeleafBindingContract
  ├─→ HTML Binding
  └─→ 필요한 Controller Adapter
```

### 2.3 구조와 Binding 분리

HTML Skeleton 단계에서는 `th:field`, `th:object`, Controller Model attribute를 임의 생성하지
않는다. 구조 생성 후 검증된 Binding Contract만 적용한다.

### 2.4 Preview 후 적용

승인 전에는 대상 프로젝트를 덮어쓰지 않는다.

```text
ANALYZED
  → CONTRACT_READY
  → PREVIEW_READY
  → APPROVED
  → APPLIED
  → VALIDATED
```

FATAL이 발생하면 이후 단계는 `SKIPPED` 처리한다.

### 2.5 결정론

다음 입력이 같으면 생성 파일과 보고서의 content hash가 같아야 한다.

- source revision과 project fingerprint
- ScreenSpecification version
- Binding Contract version
- Component Inventory version
- `DESIGN.md` content hash
- Design System Profile·Token version
- Generator version

## 3. 범위와 대상 런타임

### 3.1 포함

- JSP taglib, form tag, JSTL, EL, include 분석
- Controller route, Model, validation, redirect 분석
- VO field, type, Bean Validation 분석
- CSS·SCSS·Bootstrap·Tailwind 정적 분석
- JavaScript selector, event, form submit, endpoint 분석
- 이미지·아이콘·폰트 Source Inventory
- LIST, FORM, DETAIL, DASHBOARD 화면 유형
- STANDARD, BOARD, MASTER_DETAIL, DASHBOARD Layout Pattern
- CRUD, Board, Master/Detail 화면
- Thymeleaf layout·fragment·화면 HTML 생성
- Responsive 변환과 Component Swap
- Thymeleaf parse/render, 접근성·overflow·parity·빌드 검증

### 3.2 제외

- 업무 로직 자동 재설계
- JSP scriptlet 핵심 업무 로직의 무인 변환
- Service·DAO를 임의로 재작성하는 기능
- 승인되지 않은 외부 자산 자동 다운로드
- Tailwind, PostCSS, SCSS, JavaScript 설정의 임의 실행
- 실패한 Binding을 추측한 field 이름으로 보정
- 승인 전 운영 프로젝트 파일 덮어쓰기

### 3.3 Target Runtime Profile

기존 Renderer와 Layout Tool은 eGovFrame WAR+Thymeleaf 지원을 이미 제공한다. “Spring Boot
전환”을 모호하게 처리하지 않도록 대상 런타임을 명시한다.

```text
TargetRuntimeProfile
├─ EGOVFRAME_5_WAR_THYMELEAF
└─ SPRING_BOOT_THYMELEAF
```

1차 구현은 기존 자산을 최대 재사용할 수 있는 `EGOVFRAME_5_WAR_THYMELEAF`를 기준으로 한다.
2차에서 Boot 전용 ViewResolver, Security, resource path와 실행 모델을 Adapter로 분리한다.
업무 Binding Contract는 두 런타임이 공유한다.

## 4. 현재 구현 기준선

### 4.1 재사용할 Backend 자산

- `ScreenSpecAssembler`: 화면 명세 조립
- `ScreenFieldBinding`: field binding 기반
- `GenerationQueryContractFactory`: query·field source 계약
- `CrudModelFactory`, `BoardModelFactory`: Controller/View 모델 생성 기반
- `CrudTemplateRenderer`, `BoardTemplateRenderer`, `MasterDetailTemplateRenderer`: FreeMarker 기반 HTML 생성
- `ThymeleafLayoutGenerationService`: 공통 layout·GNB·runtime 생성 Use Case
- `ThymeleafLayoutGenerationPlanner`: 생성 대상 파일 사전 계획
- `ThymeleafRenderValidator`: Thymeleaf 정적·렌더 검증 기반
- `GeneratedProjectBuildValidator`: 허용 경로와 실행 Gate가 적용된 빌드 검증
- `ProjectScannerService`: 프로젝트 구조와 파일 탐색
- `ComponentRegistryResolver`: 승인 Component 해석
- `DesignSystemProfile`, `DesignSystemSpec`, `VariableBinding`: 회사 Token 기반
- Board·CRUD·MasterDetail Pipeline과 생성 이력

### 4.2 재사용할 Extractor 자산

- `jsp-design-extractor/src/server.ts`
- Playwright 기반 Rendered URL 분석
- `RenderedDesignDocument` 계약
- URL/origin/response size 보안 정책
- LIST·REGIST·UPDT·DETAIL E2E fixture

### 4.3 핵심 미구현

- `LOCAL_SOURCE`와 `HYBRID` 분석 모드
- 프로젝트 Source Inventory와 안전한 Reader
- JSP·Controller·VO의 화면 단위 연결
- 상위 `ThymeleafBindingContract`
- `DESIGN.md` Loader·Validator
- Component Inventory 선택 결과 계약
- 회사 Token→CSS Variable·class 매핑
- Skeleton과 Binding 적용 분리
- Responsive Transformer
- 10단계 Orchestration, Preview·Approve·Apply 상태 저장
- parity, viewport, 접근성 검증 자동 Gate
- REST·MCP 진입점과 보고서 조회

## 5. 목표 아키텍처

### 5.1 책임 분리

```text
jsp-design-extractor (Node.js/TypeScript)
├─ RENDERED_URL: DOM/computed style/screenshot
└─ LOCAL_SOURCE frontend readers
   ├─ CSS/SCSS
   ├─ Bootstrap/Tailwind
   ├─ JavaScript
   └─ Asset
          │
          ▼
LocalSourceDesignDocument / RenderedDesignDocument
          │
          ▼
Spring Boot
├─ Project path 보안과 Source Inventory
├─ JSP·Controller·VO·DB 업무 분석
├─ LegacyScreenAnalysis 조립
├─ ThymeleafBindingContract 생성
├─ 디자인 규칙·Token·Component 결정
├─ Skeleton·Binding·Responsive 생성
├─ Preview/Approve/Apply
└─ Render/Build/Parity 검증
```

Frontend AST 분석을 Java와 Node에 중복 구현하지 않는다. Node Extractor는 frontend source와
rendered evidence를 정규화하고, Spring은 업무 계약과 생성 상태를 소유한다.

### 5.2 10단계 Pipeline

```text
1. Source Analysis
2. Binding Contract
3. Screen Type Decision
4. Component Inventory
5. DESIGN.md Rules
6. Company Token Mapping
7. HTML Skeleton
8. Model Binding
9. Responsive Transformation
10. Build/Render/Parity Validation
```

각 단계는 공통 `ThymeleafGenerationStageResult<T>`를 반환한다.

```text
stage
status
inputHash
contractVersion
startedAt/completedAt
artifactRefs[]
issues[]
outputHash
```

## 6. 공통 계약

### 6.1 LegacyConversionRequest

```text
LegacyConversionRequest
├─ analysisMode: RENDERED_URL | LOCAL_SOURCE | HYBRID
├─ targetRuntimeProfile
├─ projectRoot
├─ entryViews[]
├─ renderedUrls[]
├─ controllerPaths[]
├─ voPaths[]
├─ includeGlobs[]
├─ excludeGlobs[]
├─ designSystemProfileId
├─ targetPlatforms[]
├─ overwritePolicy
└─ idempotencyKey
```

`LOCAL_SOURCE`는 로컬 서버의 존재 여부와 무관하게 크롤링하지 않는다. `HYBRID`는 소스 분석을
기준으로 삼고 허용된 환경에서 렌더 결과를 추가 증거로 사용한다.

### 6.2 LegacyScreenAnalysis

```text
LegacyScreenAnalysis
├─ sourceRevision
├─ projectFingerprint
├─ provenance[]
├─ views[]
├─ controllers[]
├─ valueObjects[]
├─ routes[]
├─ fields[]
├─ actions[]
├─ modelAttributes[]
├─ validations[]
├─ securityConstraints[]
├─ frontendEvidence
└─ issues[]
```

### 6.3 ThymeleafBindingContract

```text
ThymeleafBindingContract
├─ screenId
├─ contractVersion
├─ sourceRevision
├─ formObjectName
├─ commandType
├─ fields[]
│  ├─ logicalFieldId
│  ├─ voField
│  ├─ requestName
│  ├─ modelPath
│  ├─ sourceColumn
│  ├─ javaType
│  ├─ validation
│  ├─ readable/writable
│  └─ provenance[]
├─ routes[]
├─ actions[]
├─ iterations[]
├─ queryContract
└─ securityContract
```

동일 Binding에 대해 Controller, VO, JSP, DB Schema 증거가 충돌하면 자동 선택하지 않고 Issue를
생성한다.

### 6.4 Generation Report

```text
ThymeleafGenerationReport
├─ generationId
├─ requestHash
├─ projectFingerprint
├─ sourceRevision
├─ targetRuntimeProfile
├─ contractVersions
├─ stages[]
├─ generatedFiles[]
├─ bindingAudit
├─ tokenAudit
├─ responsiveAudit
├─ renderReport
├─ buildReport
├─ parityReport
└─ finalStatus
```

## 7. 작업 패키지

### WP-0. 계약·상태·Schema 확정

목표: 10단계가 공유할 입력·출력·Issue·보고서 계약을 먼저 고정한다.

작업:

- `legacy-conversion-request-v1.schema.json`
- `local-source-design-document-v1.schema.json`
- `legacy-screen-analysis-v1.schema.json`
- `thymeleaf-binding-contract-v1.schema.json`
- `thymeleaf-generation-report-v1.schema.json`
- 분석 모드·런타임 Profile·단계 상태 enum
- 단계별 FATAL/WARNING/REVIEW_REQUIRED 정책
- 동일 입력 재시도와 idempotency 정책
- 정상·경계·오류 fixture

완료 게이트:

- 3개 분석 모드의 Request fixture 통과
- 알 수 없는 단계 상태·잘못된 상태 전이 거부
- Binding provenance와 source location 필수 검증
- 단계 output hash와 보고서 교차 참조 검증

선행 작업: 없음

### WP-1. Local Source 보안과 Project Inventory

목표: 프로젝트 외부와 보안 파일을 읽지 않는 결정론적 Source Inventory를 만든다.

예상 Backend 파일:

- `service/thymeleaf/generator/source/LocalSourceSecurityPolicy.java`
- `service/thymeleaf/generator/source/ProjectSourceInventory.java`
- `service/thymeleaf/generator/source/SourceReferenceResolver.java`

보안 정책:

- `projectRoot` real path와 allowed root 검증
- `..`, 절대경로 우회, Unicode·대소문자 혼동 차단
- symlink가 root 밖을 가리키면 거부
- `.git`, `node_modules`, build, upload, temp 기본 제외
- `.env`, key, pem, certificate, credential 파일 읽기 금지
- 파일 개수·개별 크기·전체 읽기 크기 제한
- 분석 로그에는 상대 경로·hash·Issue만 기록
- 설정 파일과 build script 실행 금지

완료 게이트:

- path traversal, symlink, secret, 대용량, 파일 수 제한 테스트 통과
- 같은 Source Tree의 project fingerprint 결정성 보장
- 허용 범위 밖 파일 내용이 산출물·로그에 없음

선행 작업: WP-0

### WP-2. View·Controller·VO 업무 분석

목표: 한 화면에 연결된 JSP·Controller·VO의 업무 계약을 정규화한다.

예상 파일:

- `service/thymeleaf/generator/source/JspSourceReader.java`
- `service/thymeleaf/generator/source/ThymeleafSourceReader.java`
- `service/thymeleaf/generator/source/ControllerSourceReader.java`
- `service/thymeleaf/generator/source/ValueObjectSourceReader.java`
- `service/thymeleaf/generator/LegacyScreenSourceAnalyzer.java`

분석 항목:

- JSP taglib, include, form tag, JSTL, EL, hidden, errors
- Controller mapping, Model attribute, request/path parameter, redirect
- VO field, type, validation, format, enum/common code 후보
- form action과 Controller route 연결
- 반환 View 이름과 JSP 연결
- Security Annotation, CSRF, 권한 검사 증거

scriptlet에 핵심 업무 로직이 있으면 `REVIEW_REQUIRED` 또는 FATAL로 중단한다.

완료 게이트:

- LIST·FORM·DETAIL fixture의 view/controller/VO 연결 성공
- form action·HTTP method·route 불일치 탐지
- 동일 field type 충돌 탐지
- 반환 View를 찾을 수 없는 경우 추측하지 않음

선행 작업: WP-1

### WP-3. Frontend Source Graph 확장

목표: `jsp-design-extractor`에 LOCAL_SOURCE용 frontend Reader를 추가한다.

대상:

- `jsp-design-extractor/src/` 모듈 분리
- CSS/SCSS dependency graph
- Bootstrap·Tailwind profile
- JavaScript behavior graph
- Local asset inventory

Reader:

- CSS: selector, custom property, media/container/supports, asset URL
- SCSS: `@use`, `@forward`, variable, map, mixin 호출
- Bootstrap: dependency/version 증거, class, data attribute, breakpoint
- Tailwind: config 경로, theme, utility, arbitrary value, dynamic class
- JavaScript: selector, event, form submit, fetch/XHR, class mutation
- Asset: MIME/signature, dimensions, hash, usage, SVG 위험 요소

설정 JavaScript, SCSS compiler, PostCSS와 build script는 기본적으로 실행하지 않는다.

완료 게이트:

- import 순환과 unresolved reference 보고
- framework version 충돌 탐지
- dynamic class와 arbitrary value를 자동 확정하지 않음
- 악성 SVG와 외부 URL 자산 차단
- 모든 후보에 source location·confidence·runtime verification 표시

선행 작업: WP-1

### WP-4. LegacyScreenAnalysis와 Binding Contract

목표: 업무 분석과 frontend evidence를 하나의 화면 분석·Binding 계약으로 결합한다.

예상 파일:

- `model/generator/LegacyScreenAnalysis.java`
- `model/generator/ThymeleafBindingContract.java`
- `service/thymeleaf/generator/ThymeleafBindingContractFactory.java`

재사용:

- `ScreenFieldBinding`
- `GenerationQueryContract`
- `GenerationQueryContractFactory`
- 선택적 DB Schema metadata

충돌 정책:

- Controller·VO·DB Schema가 JSP hint보다 우선
- Controller Model attribute 없는 EL은 미해결
- VO field 없는 form path는 FATAL
- writable=false field에 입력 Binding 금지
- route와 action method 불일치 시 FATAL
- 증거 부족은 임의 보정 대신 REVIEW_REQUIRED

완료 게이트:

- 모든 `th:field` 후보가 실제 VO field에 연결
- 모든 조회 표현식이 Model attribute에 연결
- 모든 action이 route와 HTTP method에 연결
- provenance와 conflict가 보고서에 포함

선행 작업: WP-2, WP-3

### WP-5. 화면 유형·Component·디자인 규칙·Token

목표: 업무 계약을 변경하지 않고 시각·Layout 계약을 결정한다.

예상 파일:

- `GeneratorScreenTypeResolver.java`
- `ComponentInventorySelector.java`
- `DesignMdRuleLoader.java`
- `CompanyDesignTokenResolver.java`
- `model/generator/ScreenTypeDecision.java`
- `model/generator/SelectedComponentInventory.java`
- `model/generator/AppliedDesignRules.java`
- `model/generator/ResolvedDesignTokens.java`

화면 유형 근거 우선순위:

1. 승인 ScreenSpecification
2. Controller route/action
3. JSP form/table/detail 구조
4. VO 사용 방식
5. 파일명·화면명

Component 선택은 field role, 화면 유형, Registry Publish 상태와 회사 Catalog의 교집합으로
제한한다.

`DESIGN.md` Loader는 선택 경로, content hash, schema version, 적용·무시·위반 규칙을 기록한다.
알 수 없는 규칙이나 업무 Binding 변경 시도는 Issue로 반환한다.

Token 매핑:

```text
semantic token
  → company token key
  → CSS custom property
  → Thymeleaf class/property
```

회사 Token에 없는 값을 임의 CSS literal로 만들지 않는다.

완료 게이트:

- LIST·FORM·DETAIL 판정에 근거·confidence 포함
- 필수 미게시 Component 생성 차단
- `DESIGN.md` 업무 계약 변경 시도 차단
- CSS literal이 Token 또는 승인 예외로 역추적됨

선행 작업: WP-4

### WP-6. HTML Skeleton과 Binding 적용 분리

목표: 기존 Renderer를 재사용하면서 구조 생성과 업무 Binding 적용을 명확히 분리한다.

예상 파일:

- `service/thymeleaf/generator/ThymeleafSkeletonGenerator.java`
- `service/thymeleaf/generator/ThymeleafModelBindingApplicator.java`
- 기존 FreeMarker template의 slot 계약

Skeleton:

- `layout:decorate`
- title, breadcrumb, GNB/LNB
- form/table/detail container
- action, pagination
- empty/loading/error/success state
- footer
- binding slot

Binding 적용:

- `th:object`, `th:field`, `th:text`
- `th:if`, `th:unless`, `th:each`
- `th:href`, `th:action`
- validation error와 CSRF
- pagination/search state
- common code options

기존 `CrudTemplateRenderer`, `BoardTemplateRenderer`, `MasterDetailTemplateRenderer`를 폐기하지
않고 내부 Renderer로 사용한다.

완료 게이트:

- Skeleton에 임의 `th:*` 업무 Binding 없음
- 적용된 모든 `th:*`가 Binding Contract에 존재
- form object와 BindingResult 이름 일치
- route parameter와 HTTP method 일치
- 기존 CRUD·Board·MasterDetail Renderer 회귀 없음

선행 작업: WP-5

### WP-7. Responsive Transformer

목표: 동일 Binding Contract를 유지한 반응형 HTML/CSS를 생성한다.

예상 파일:

- `service/thymeleaf/generator/ResponsiveThymeleafTransformer.java`
- `model/generator/ResponsiveThymeleafViewSet.java`

초기 기준:

| Platform | 폭 | Grid | Navigation |
|---|---:|---:|---|
| Desktop | 1440 | 12 | side navigation |
| Tablet | 768 | 8 | drawer |
| Mobile | 390 | 4 | drawer 또는 bottom navigation |

변환:

- table horizontal scroll·우선 열·card 후보
- multi-column form의 single/dual column
- side navigation의 drawer/bottom navigation swap
- action group의 stack/full-width
- modal의 full-screen sheet 후보
- fixed width의 container Token 교체

route, form object, field, validation, action, 권한, CSRF는 플랫폼에 따라 바뀌지 않는다.

완료 게이트:

- 세 viewport가 동일 Binding Contract hash 사용
- overflow와 navigation 정책 golden test 통과
- 별도 mobile template이 필요한 경우 field/action parity 통과
- 회사 breakpoint·spacing·font Token 외 하드코딩 없음

선행 작업: WP-6

### WP-8. 10단계 Orchestration과 Preview/Apply

목표: 개별 서비스를 고정 순서 Pipeline으로 연결하고 승인된 결과만 적용한다.

예상 파일:

- `service/thymeleaf/generator/ThymeleafGenerationOrchestrationService.java`
- `service/thymeleaf/generator/ThymeleafGenerationReportService.java`
- `mapper/ThymeleafGenerationRepository.java`
- Preview 작업 디렉터리 관리 서비스

작업:

- 10단계 고정 순서 실행
- stage별 input/output hash와 contract version 기록
- FATAL 이후 단계 SKIPPED
- idempotency key와 source revision 충돌 처리
- Preview 파일을 대상 프로젝트 밖 관리 디렉터리에 저장
- 승인 시 대상 경로·revision 재검증
- Apply 전 변경 파일 목록과 diff 확정
- 원본 Backup 또는 recoverable patch 생성
- Apply 후 검증 단계 실행

상태:

```text
ANALYZED → CONTRACT_READY → PREVIEW_READY
→ APPROVED → APPLIED → VALIDATED
```

완료 게이트:

- 승인 전 대상 파일 변경 없음
- source revision 변경 시 Apply 중단
- 같은 입력 재시도 시 산출물 hash 동일
- 중간 FATAL 이후 Renderer·빌드 미실행
- 단계별 Issue와 artifact 경로 추적 가능

선행 작업: WP-4, WP-5, WP-6, WP-7

### WP-9. 렌더·빌드·Parity 검증

목표: 생성 결과가 구문상 유효한 수준을 넘어 실제 업무 계약을 보존하는지 검증한다.

재사용:

- `ThymeleafRenderValidator`
- `GeneratedProjectBuildValidator`
- `CodeValidatorTool`
- 기존 Generated Code Auditor

검증 순서:

1. HTML/Thymeleaf 정적 검사
2. Binding Contract 감사
3. Thymeleaf parse
4. fixture Model render
5. route/action/CSRF 감사
6. CSS Token literal 감사
7. Desktop·Tablet·Mobile screenshot
8. overflow·layout·접근성 검사
9. Maven/Gradle build
10. JSP 대비 field/action/route parity

빌드 실행 조건:

- `EGOV_ALLOW_BUILD_EXECUTION=true`
- 허용된 real path 내부
- 고정 Maven/Gradle 명령
- offline 우선
- timeout과 출력 크기 제한
- shell 문자열 조립 금지

완료 게이트:

- Thymeleaf parse/render 성공
- Controller가 제공하지 않는 Model 참조 없음
- field/action/route parity 100% 또는 승인 예외
- 세 viewport 접근성·overflow Gate 통과
- 허용 환경 빌드 성공

선행 작업: WP-8

### WP-10. REST·MCP·운영

목표: 분석, Preview, 승인, Apply, 검증과 보고서 조회를 분리해 노출한다.

REST 후보:

```text
POST /api/thymeleaf-generations/analyze
POST /api/thymeleaf-generations/{id}/preview
POST /api/thymeleaf-generations/{id}/approve
POST /api/thymeleaf-generations/{id}/apply
POST /api/thymeleaf-generations/{id}/validate
GET  /api/thymeleaf-generations/{id}
GET  /api/thymeleaf-generations/{id}/report
```

MCP 후보:

```text
analyzeLegacyThymeleafConversion
previewThymeleafConversion
applyApprovedThymeleafConversion
validateGeneratedThymeleaf
getThymeleafGenerationReport
```

예상 파일:

- `controller/ThymeleafGenerationController.java`
- `tools/ThymeleafGenerationTool.java`
- MCP·REST 공통 Facade
- `config/McpConfig.java` callback 등록

보안:

- REST X-API-Key 유지
- MCP 공유 비밀키 검증
- 분석·Preview·Apply 경로 allowlist
- 승인되지 않은 Apply 거부
- source body, 실제 사용자 값, token, secret 로그 금지

완료 게이트:

- REST와 MCP가 같은 Use Case를 호출
- Tool 클래스에 비즈니스 로직 없음
- analyze와 apply가 분리됨
- 보고서로 각 단계와 생성 파일을 조회 가능
- MCP Tool snapshot과 callback 등록 테스트 갱신

선행 작업: WP-8, WP-9

## 8. 구현 순서와 의존성

```text
WP-0 계약
  ↓
WP-1 Source 보안·Inventory
  ├─→ WP-2 View·Controller·VO 분석 ─┐
  └─→ WP-3 Frontend Source Graph ───┤
                                     ▼
                          WP-4 Binding Contract
                                     ↓
                   WP-5 Component·DESIGN.md·Token
                                     ↓
                         WP-6 Skeleton·Binding
                                     ↓
                         WP-7 Responsive
                                     ↓
                    WP-8 Orchestration·Preview/Apply
                                     ↓
                       WP-9 Render·Build·Parity
                                     ↓
                         WP-10 REST·MCP·운영
```

WP-8 Orchestration을 먼저 만들지 않는다. 각 단계의 계약과 실패 조건이 정해지기 전에 거대한
Orchestrator를 작성하면 이전 분리 작업과 같은 결합도가 다시 생긴다. Orchestrator는 Use Case
순서와 상태만 조정하고 각 단계의 분석·생성·검증 로직은 독립 서비스에 둔다.

## 9. 릴리스 단계

### Release A — Source와 Binding 기반

- WP-0~WP-4
- LOCAL_SOURCE LIST·FORM·DETAIL 분석
- Binding Contract와 provenance

### Release B — 디자인 시스템 적용과 HTML 생성

- WP-5~WP-6
- Component Inventory, `DESIGN.md`, Token, Skeleton, Binding

### Release C — Responsive와 Preview Workflow

- WP-7~WP-8
- 세 viewport와 승인 전 적용 차단

### Release D — 검증과 외부 진입점

- WP-9~WP-10
- 렌더·빌드·parity, REST·MCP

### Release E — HYBRID·고급 분석

- SCSS/Tailwind 승인 compile 비교
- Source와 computed style 차이 보고서
- 시각 회귀 기준 이미지 승인
- Batch 화면 전환

## 10. 테스트 계획

### 10.1 Source 보안

- path traversal, symlink, Unicode 경로
- `.env`, key, certificate, credential 제외
- 파일 개수·크기·전체 용량 제한
- 외부 CSS·asset URL 다운로드 차단
- 설정·build script 미실행

### 10.2 분석기

- JSP taglib/include/form/JSTL/EL
- Thymeleaf fragment/layout/field/action
- Controller route/model/request/path/redirect
- VO field/type/validation/format
- CSS import/custom property/media query
- SCSS use/forward/variable/map
- Bootstrap version/class/data attribute
- Tailwind theme/content/dynamic class/arbitrary value
- JavaScript selector/event/fetch/class mutation
- PNG/JPEG/WebP/SVG/sprite/icon font

### 10.3 계약

- Controller·VO·View·DB 충돌
- field read/write 정책
- action route·HTTP method
- form object·BindingResult
- Model attribute·iteration
- Security·CSRF
- provenance와 confidence

### 10.4 생성

- CRUD LIST·FORM·DETAIL
- Board LIST·DETAIL·FORM
- MasterDetail LIST·DETAIL·FORM
- Skeleton과 Binding 단계 분리
- Component Registry Publish 상태
- `DESIGN.md` 정상·알 수 없는 규칙·금지 Override
- Token 누락·literal 하드코딩 차단
- Responsive golden fixture

### 10.5 Pipeline

- 10단계 순서
- FATAL 이후 SKIPPED
- 동일 입력 결정성
- Preview 전 대상 파일 불변
- 승인·revision 충돌
- Apply 후 render/build 자동 Gate
- 단계별 보고서와 artifact 추적

### 10.6 E2E

```text
실제 JSP·Controller·VO fixture
→ LOCAL_SOURCE 분석
→ Binding Contract
→ Preview
→ 승인
→ Thymeleaf 적용
→ 1440/768/390 render
→ Maven/Gradle build
→ JSP parity report
```

## 11. 검증 명령

Backend:

```bash
./gradlew test
./gradlew check
./gradlew bootJar
```

Extractor:

```bash
cd jsp-design-extractor
npm run typecheck
npm run lint
npm test
```

대상 프로젝트 빌드는 보안 Gate와 허용 경로가 승인된 경우에만
`GeneratedProjectBuildValidator`를 통해 실행한다.

## 12. 보안 체크리스트

- [ ] `projectRoot`가 allowed real path 안에 있다.
- [ ] symlink가 프로젝트 외부를 가리키지 않는다.
- [ ] `.env`, key, certificate, credential 파일을 읽지 않는다.
- [ ] source 본문과 실제 사용자 데이터가 로그에 기록되지 않는다.
- [ ] 외부 CSS·JavaScript·이미지를 자동 다운로드하지 않는다.
- [ ] JavaScript·Tailwind·PostCSS·SCSS 설정을 임의 실행하지 않는다.
- [ ] 승인 전 대상 프로젝트 파일이 변경되지 않는다.
- [ ] Apply 직전에 source revision과 대상 경로를 다시 검증한다.
- [ ] build는 명시적 Gate, 고정 명령, timeout과 출력 제한을 사용한다.
- [ ] `DESIGN.md`가 route·validation·권한·CSRF를 변경하지 못한다.

## 13. 주요 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| JSP·Controller·VO 연결 오판 | 동작하지 않는 Binding 생성 | provenance 교차 검증, 충돌 시 FATAL |
| frontend Reader 중복 구현 | Java/Node 결과 불일치 | frontend AST는 Extractor, 업무 계약은 Spring 담당 |
| 거대한 Orchestrator 재발 | 수정 영향도 증가 | 단계별 서비스와 공통 Stage Result, Orchestrator는 순서만 조정 |
| `DESIGN.md`가 업무 계약 변경 | route·권한 회귀 | 규칙 우선순위 Validator로 차단 |
| Token 누락 시 하드코딩 | 회사 디자인 시스템 이탈 | `TOKEN_MAPPING_MISSING`, 생성 중단 또는 승인 예외 |
| 정적 분석의 responsive 오판 | 모바일 overflow | runtimeVerificationRequired와 생성 후 viewport render |
| 설정 파일 임의 실행 | 공급망·RCE 위험 | 정적 AST만 사용, 실행은 별도 승인 Gate |
| Preview 없이 덮어쓰기 | 사용자 코드 손실 | Preview/Approve/Apply 분리, revision 검사, Backup |
| 빌드 실행 악용 | 임의 명령 실행 | allowlist·고정 명령·offline·timeout |

## 14. 완료 정의

다음 조건을 모두 충족하면 1차 구현을 완료한다.

- LOCAL_SOURCE만으로 CRUD LIST·FORM·DETAIL 분석이 가능하다.
- JSP·Controller·VO가 하나의 `LegacyScreenAnalysis`로 연결된다.
- 모든 field·route·action이 `ThymeleafBindingContract`에서 추적된다.
- 화면 유형 판정에 evidence와 confidence가 포함된다.
- 선택 Component가 승인 Registry와 일치한다.
- `DESIGN.md` 적용·무시·위반 규칙이 보고된다.
- 모든 생성 CSS 값이 회사 Token 또는 승인 예외에 연결된다.
- Skeleton과 Model Binding 단계가 분리된다.
- Desktop·Tablet·Mobile이 같은 Binding Contract를 사용한다.
- Preview 승인 전 대상 프로젝트가 변경되지 않는다.
- Thymeleaf parse/render와 허용 환경 빌드가 통과한다.
- 기존 JSP 대비 field/action/route parity가 유지된다.
- 단계별 input/output hash, 계약 버전, Issue와 artifact가 보고서에 남는다.
- 같은 입력 재실행 시 동일 산출물이 생성된다.
- 기존 CRUD·Board·MasterDetail·Thymeleaf Layout 테스트가 통과한다.

## 15. 기존 문서와의 관계

이 문서는 기준 문서를 현재 코드베이스에서 실행하기 위한 작업 순서 문서다. 구현 상태의 단일
체크리스트는 다음 작업 ID를 사용한다.

- `12_Semantic_Figma_Design_System_Implementation_List.md`
- BASE-19~23
- R6-050~064
- R6-T14~20

세부 원칙은 다음 문서를 함께 따른다.

- `eGovFrame_JSP_to_Spring_Boot_Thymeleaf_전환_작업_명세서.md`
- `11_Semantic_Figma_Design_System_Implementation_Plan.md`의 R6B
- `website-figma-contract/CONTRACT_RULES.md`

구현 완료 여부는 본 문서에 중복 기록하지 않고 12번 구현 목록의 작업 ID를 갱신한다.
