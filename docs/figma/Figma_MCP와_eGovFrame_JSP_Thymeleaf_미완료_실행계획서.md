# Figma MCP와 eGovFrame JSP→Thymeleaf 미완료 실행계획서

- 기준 문서: `Figma_MCP와_eGovFrame_JSP_Thymeleaf_통합_구현계획서.md`
- 상태 원장: `12_Semantic_Figma_Design_System_Implementation_List.md`
- 코드 기준: `main` `24c8e75` (2026-08-02)
- 작성일: 2026-08-02
- 범위: 완료 게이트가 닫히지 않은 작업만 포함

## 1. 판정 요약

공통 계약 모델, Figma Operation 저장·상태 전이, JSP·Controller·VO 기본 분석, Binding Contract,
LIST·FORM·DETAIL 기본 HTML 생성, 7개 MCP callback 등록은 구현되어 있으므로 이 계획에서 제외한다.

다만 다음은 “클래스나 callback 존재”만으로 완료 처리할 수 없다.

- `FigmaDesignOrchestrationService`는 현재 요청 검증과 Operation 생성까지만 수행하며 실제
  Context 분석→ScreenSpecification→FigmaScreenSpec→Bundle 생성 흐름을 연결하지 않는다.
- 멀티 화면 callback은 요청 조립이 TODO이고, Plugin에는 Operation Preview·Apply 보고 계약이 없다.
- Thymeleaf Skeleton은 Component Registry와 `DESIGN.md`를 사용하지 않으며 반응형 처리는 annotation만 생성한다.
- Thymeleaf E2E 결과의 Binding Contract가 `null`이고, offline build·viewport·접근성·parity Gate가 연결되지 않았다.
- 상태 원장과 최근 구현 커밋의 체크 상태가 어긋나므로 아래 완료 게이트 통과 후 원장을 갱신해야 한다.

## 2. 미완료 구현목록

| 순서 | 작업 패키지 | 우선순위 | 완료 산출물 |
|---:|---|---|---|
| 1 | 계약·해시·상태 원장 정합화 | P0 | 누락 Schema/Report, canonical SHA-256, 최신 체크 상태 |
| 2 | Figma 실처리 오케스트레이션 완성 | P0 | 핵심 4개 요청의 실제 `PREVIEW_READY` Bundle |
| 3 | Figma Plugin Apply와 고급 요청 완성 | P0/P1 | revision 검증, 원자 Apply, Image·Multi-screen·Platform |
| 4 | Design-aware Thymeleaf 생성 완성 | P0 | `DESIGN.md`·Token·Component 기반 Bound View |
| 5 | Responsive·Project Apply·검증 Gate 완성 | P0 | 3 viewport, 원자 적용, render/build/parity 보고서 |
| 6 | REST·MCP 진입점과 교차 통합 E2E | P0 | 독립/결합/HYBRID 4개 시나리오 |
| 7 | 운영 안정화와 실제 환경 승인 | P1 | metrics, retention, runbook, 실제 Figma/대상 프로젝트 증적 |

## 3. 실행 상세

### 1) 계약·해시·상태 원장 정합화

대상:

- `website-figma-contract/`
- `model/contract`, `model/figma/contract`, `model/thymeleaf`
- `OperationHashFactory`, Figma·Thymeleaf Operation Repository
- `12_Semantic_Figma_Design_System_Implementation_List.md`

구현:

1. 누락된 `local-source-design-document-v1.schema.json`,
   `thymeleaf-generation-report-v1.schema.json`과 Java Report 모델을 추가한다.
2. Figma Orchestrator의 `String.hashCode()` 기반 request hash를 공통 canonical JSON + SHA-256로 교체한다.
3. Snapshot/Profile/Registry/Layout Policy 버전과 source revision을 Operation hash 입력에 포함한다.
4. Schema 정상·경계·오류 fixture와 Java/JSON enum 교차 검증을 추가한다.
5. 실제 코드와 테스트 근거로 R5-040~041, R6-030~039, R6-050~064 상태를 다시 판정한다.

완료 게이트:

- 같은 입력·버전은 Java와 Node에서 같은 hash를 만들고, 순서만 다른 JSON도 같은 hash를 만든다.
- 알 수 없는 Schema version·상태·Snapshot drift를 거부한다.
- 상태 원장의 `[x]`, `[~]`, `[ ]`가 코드와 테스트 증적에 일치한다.

### 2) Figma 실처리 오케스트레이션 완성

대상:

- `FigmaApiClient`
- `FigmaDesignOrchestrationService`
- 신규 `FigmaContextAnalyzer`, `FigmaStyleExtractor`
- `ComponentRegistryResolver`, `DesignReferenceAnalysisService`
- MCP 응답 redaction/facade

구현:

1. files/nodes/images/components/styles 조회, pagination, `429 Retry-After`, timeout, 응답 크기 제한을 구현한다.
2. fileKey allowlist와 node의 file/page 소속을 Repository 접근 전에 검증한다.
3. 구조화 분석 결과로 domain, screenType, layoutPattern, logical component, confidence/uncertainty를 생성한다.
4. Text·Reference·Modify·Component 요청을 실제
   ScreenSpecification→FigmaScreenSpec→Bundle→Operation 흐름에 연결한다.
5. Registry·승인 Catalog·Layout Policy 교집합만 허용하고 참조 스타일은 Profile 변경이 아닌 Token 후보로 저장한다.
6. MCP 응답·로그·artifact에서 access token, LLM key, Published key, 제한 식별자를 redaction한다.

완료 게이트:

- 핵심 4개 요청이 서로 다른 유효 Bundle과 `artifactId`를 만들며 `PREVIEW_READY`까지만 전이한다.
- 낮은 confidence, 잘못된 node 소속, 미승인 logical type, rate limit/timeout이 구조화 Issue로 반환된다.
- 같은 입력의 Spec·logicalNodeId·Operation hash가 결정적이다.

### 3) Figma Plugin Apply와 고급 요청 완성

대상:

- `figma-screen-spec-plugin/src/`
- `FigmaDesignOrchestrationTool`
- `FigmaDesignOperationService`, `FigmaOperationsController`
- 신규 `FigmaPlatformConversionService`

구현:

1. Plugin Preview에 `operationId`, request type, source revision, 서버 diff summary를 표시한다.
2. Apply 직전 Figma revision과 `editableNodeIds`의 file/page/승인 범위를 실제 API로 재검증한다.
3. Plugin 적용 보고서에 적용 node, hash, warning, revision을 포함하고 보고서 검증 후에만 `APPLIED`로 전이한다.
4. 멀티 화면 callback의 TODO를 제거하고 화면별 요청·artifact를 조립한다. 전체 Preview 성공 후 일괄 Apply하고 실패 시 생성 node를 롤백한다.
5. Image 요청에 Vision capability preflight와 Figma image export를 연결한다.
6. Desktop 1440/12, Tablet 768/8, Mobile 390/4의 Grid·Navigation·Component Swap·annotation 변환을 구현한다.

완료 게이트:

- revision 충돌과 editable 범위 위반 시 Canvas 변경이 없다.
- Plugin 보고 전에는 어떤 경로에서도 `APPLIED`가 반환되지 않는다.
- 멀티 화면 중간 실패 시 부분 node가 남지 않고 MERGE 사용자 Override가 보존된다.
- 7개 callback의 정상·오류·미지원 capability 계약 테스트와 Figma Desktop E2E가 통과한다.

### 4) Design-aware Thymeleaf 생성 완성

대상:

- 신규 `DesignMdRuleLoader`, `DesignRuleValidator`, `ThymeleafComponentInventoryResolver`
- `ThymeleafSkeletonPlanner`, `LegacyThymeleafViewComposer`, renderer/FTL
- `ThymeleafConversionOrchestrationService`

구현:

1. 대상 프로젝트 루트에서 `DESIGN.md`를 안전하게 탐색하고 version, source location, 우선순위를 파싱한다.
2. 화면 유형·layout pattern·field role을 근거와 confidence를 포함해 판정하고 Registry의 CURRENT Component만 선택한다.
3. 회사 Profile/Token을 CSS variable, class, Component property로 해석한다.
4. Skeleton 단계에는 Binding을 넣지 않고, Binding 단계의 모든 `th:*`에 Contract provenance를 연결한다.
5. 업무 Binding/보안→승인 ScreenSpecification→Profile/Token→`DESIGN.md`→Override→기본값 순서를 강제한다.
6. `DESIGN.md`가 route, method, field, validation, 권한, CSRF를 바꾸거나 승인 Token 밖 값을 하드코딩하면 FATAL로 중단한다.

완료 게이트:

- LIST·FORM·DETAIL 및 BOARD·MASTER_DETAIL fixture가 선택 근거·Registry 상태를 남긴다.
- 정상/알 수 없는 규칙/업무 계약 변경/Token 누락 fixture가 각각 기대 상태를 반환한다.
- 생성 HTML의 모든 `th:*`, action, CSRF, validation이 Binding Contract와 일치한다.

### 5) Responsive·Project Apply·검증 Gate 완성

대상:

- 신규 `ResponsiveThymeleafTransformer`, `ThymeleafGenerationReport`
- `ThymeleafConversionOrchestrationService`, `ProjectApplicationService`
- `ThymeleafRenderValidator`, `GeneratedProjectBuildValidator`
- Playwright 기반 viewport/a11y 검사

구현:

1. annotation에 그친 현재 정책을 실제 table→card/list, form 재배치, drawer/bottom navigation, component swap CSS/구조 변환으로 구현한다.
2. Preview artifact와 대상 경로·diff를 만들고 명시 승인 전 쓰기를 금지한다.
3. Apply 직전 source revision을 다시 읽고, 모든 대상 파일을 임시 영역에서 준비한 뒤 원자 교체한다. 실패 시 backup으로 전체 롤백한다.
4. E2E 결과가 Operation의 실제 Binding Contract를 반환하도록 현재 `null` 반환을 제거한다.
5. Thymeleaf parse/render, 정적 Binding, route·field·action·validation parity, 1440/768/390 overflow, 접근성 검사를 Gate로 묶는다.
6. 허용 경로에서만 Maven/Gradle 고정 offline 명령을 timeout·출력 제한과 함께 실행한다.

완료 게이트:

- 세 viewport가 동일 Binding hash를 사용하고 overflow·navigation·table/form 변환 검사를 통과한다.
- 승인 전 파일 변경 0건, 충돌 시 변경 0건, 적용 실패 시 부분 파일 0건이다.
- 단계별 input/output hash, Issue, artifact, build/render/parity 결과가 하나의 Report로 추적된다.

### 6) REST·MCP 진입점과 교차 통합 E2E

대상:

- Thymeleaf Preview/Approve/Apply/Report/Revalidate REST Controller와 MCP Tool
- 승인 Artifact 결합 Validator
- 통합 fixture와 E2E 테스트

구현:

1. Thymeleaf 분석·Preview·승인·Apply·보고서 조회·재검증을 분리한 REST/MCP 계약을 제공한다.
2. REST는 X-API-Key, MCP는 공유 비밀키를 Repository 접근 전에 검증한다.
3. 승인된 Figma ScreenSpecification과 Thymeleaf Binding Contract만 결합하고 Snapshot/Profile/Registry/Policy drift를 검사한다.
4. `.figpack`의 `document.json` 후보를 승인 Artifact로 승격한 뒤 같은 결합 Validator를 사용한다.
5. 두 Operation은 독립 상태를 유지하고 `correlationId`로만 감사 추적을 연결한다.

완료 게이트:

- 독립 Figma, 독립 JSP 전환, 승인 Figma 결합, `.figpack` HYBRID의 4개 E2E가 통과한다.
- 승인되지 않은 Artifact와 version drift를 거부한다.
- Figma Apply 실패와 Thymeleaf build 실패가 상대 Operation 상태를 변경하지 않는다.

### 7) 운영 안정화와 실제 환경 승인

대상:

- 구조화 로그·metrics·artifact retention
- 운영 Runbook/rollback 문서
- 실제 Figma Desktop 및 대표 eGovFrame 프로젝트

구현:

1. operationId/conversionId/correlationId 기반 로그와 Preview 반려율, 충돌률, build 실패율, fallback 비율을 수집한다.
2. Artifact 보존 기간·cleanup·복구 절차를 구현하고 release artifact secret scan을 CI에 연결한다.
3. Maven/Gradle 대상 프로젝트 각 1개와 LIST·FORM·DETAIL을 실제 전환한다.
4. Figma Desktop에서 7개 요청 Preview·Apply, 원격 Component Instance, 시각 비교 기준을 사람이 승인한다.
5. 현재 전체 테스트의 기존 실패군을 분리·해소하고 최종 `test`, `bootJar`, Node/Plugin CI를 모두 녹색으로 만든다.

완료 게이트:

- Backend·Contract·Extractor·Plugin CI가 모두 성공한다.
- 민감정보 scan 결과가 0건이고 rollback 훈련과 실제 환경 증적이 Runbook에 기록된다.

## 4. 권장 실행 순서

```text
1 계약·해시 정합화
  ├─ 2 Figma 실처리
  └─ 4 Design-aware Thymeleaf
       ↓
3 Plugin/고급 요청 + 5 Responsive/Apply/Gate
       ↓
6 REST·MCP·교차 E2E
       ↓
7 운영 안정화·실환경 승인
```

각 패키지는 해당 완료 게이트와 자동 테스트가 함께 병합되기 전 다음 단계의 “완료” 근거로 사용하지 않는다.

## 5. 검증 명령

```bash
cd website-figma-contract && npm test
cd ../jsp-design-extractor && npm run typecheck && npm run lint && npm test
cd ../figma-screen-spec-plugin && npm run typecheck && npm run lint && npm test && npm run build
cd .. && ./gradlew test && ./gradlew bootJar
```

실제 환경 Gate는 위 자동 검증 후 Figma Desktop과 Maven/Gradle 대표 프로젝트에서 별도로 수행한다.
