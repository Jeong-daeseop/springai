# Figma MCP 디자인 오케스트레이션 구현계획서

- 기준 문서: `Figma_MCP_디자인_오케스트레이션_아키텍처_및_구현_명세서.md` v1.0.0
- 대상 저장소: `springai`
- 계획서 버전: 1.0.0
- 작성일: 2026-08-02
- 구현 방식: 기존 Spring Boot Streamable HTTP MCP 서버와 Figma Plugin 확장

## 1. 목적

원본 명세의 7가지 디자인 요청을 현재 저장소의 다음 흐름에 통합한다.

```text
사용자 요청
  → Spring AI MCP Tool
  → 요청 분석·검증
  → ScreenSpecification 후보
  → FigmaScreenSpec
  → FigmaExportBundle / FigmaDesignOperation
  → Figma Plugin Preview
  → 사용자 승인
  → Plugin Apply
  → 생성 보고서·이력 저장
```

구현의 핵심은 LLM이 Figma 캔버스를 직접 수정하게 만드는 것이 아니다. 서버는 검증 가능한
의미 계약과 작업 계획을 만들고, 실제 캔버스 쓰기는 Figma Plugin이 Preview와 승인 절차를 거쳐
수행한다.

## 2. 원본 명세 적용 결정

원본 명세는 별도 Node.js/TypeScript MCP 서버를 제안하지만 현재 프로젝트에는 이미 Spring Boot
MCP 서버, Figma 계약, 디자인 시스템 저장소와 Plugin이 존재한다. 따라서 별도 MCP 서버를 신설하지
않고 다음과 같이 대응한다.

| 원본 명세 | 현재 프로젝트 적용 방식 |
|---|---|
| Node.js/TypeScript MCP Server | 기존 Spring Boot 4.1 Streamable HTTP MCP 서버 확장 |
| Claude API 직접 호출 | Spring AI의 `ChatModel`·Vision capability로 추상화 |
| `design-system.json` | 불변 `DesignSystemProfile` Snapshot |
| `component-catalog.json` | `ComponentRegistry`와 `component-catalog-v1.json` |
| `default-layouts.json` | 버전 관리되는 `DefaultLayoutPolicy` |
| Figma Writer의 스크립트 직접 실행 | `FigmaDesignOperation`을 Plugin이 Preview 후 Apply |
| 로컬 Node ID/assetId 장기 저장 | 논리 타입과 Published Component Key 사용 |
| `.env`의 API Key 전달 | 서버 환경변수로만 주입하고 Tool·Bundle·로그에는 미포함 |

원본 명세에 기록된 Figma fileKey, StateGroupId와 SymbolId는 예제 값으로만 취급한다. 운영의
단일 원본은 승인된 Profile·Registry·Layout Policy Snapshot이다.

## 3. 현재 구현 기준선

### 3.1 재사용할 구현

- `FigmaScreenExportService`: 승인된 `ScreenSpecification`을 `FigmaScreenSpec`과 Bundle로 변환
- `FigmaScreenBuilderRegistry`: LIST, FORM, DETAIL Builder 선택
- `FigmaScreenSpecValidator`: Schema·의미·logicalNodeId 검증
- `FigmaExportBundleAssembler`: Spec, Profile, Registry Snapshot 결합
- `FigmaMcpFacadeService`: MCP 인증, 서비스 위임, 민감정보 제거 응답
- `FigmaApiClient`: Figma Node 조회, timeout, 429/5xx retry와 오류 정규화
- `DesignReferenceAnalysisService`: 파일·Figma 참조·Vision 분석
- `FigmaDesignSpecMapper`: 참조 노드의 layout/token을 의미 모델로 변환
- `ComponentRegistryResolver`: 직접 ID, alias, replacement 해석
- `DesignSystemQueryService`: Profile·Registry 검증과 Preflight
- `figma-screen-spec-plugin`: Bundle 검증, Preview diff, MERGE/REPLACE, 사용자 변경 보존
- `website-figma-contract`: JSON Schema와 정상·오류 fixture

### 3.2 이미 완료된 기반

- FigmaScreenSpec LIST/FORM/DETAIL 생성
- 결정론적 `logicalNodeId`
- DesignSystemProfile·ComponentRegistry 버전 저장
- `.figma-export-bundle.json`과 `.figpack` 계약
- REST/MCP 인증과 Registry 공개 Key redaction
- Plugin Preview, MERGE/REPLACE, Archive 정책
- 생성 보고서·Review·Registry 동기화 이력
- GitHub Actions CI와 전체 Gradle 테스트

### 3.3 핵심 미구현 범위

- 7가지 요청의 공통 `FigmaDesignRequest`와 Operation 모델
- Operation 불변 저장, source revision 충돌, 멀티 화면 원자 상태 전이
- 자연어 요청 구조화 분석기와 명시적 요청 라우터
- 7개 MCP callback과 공통 오케스트레이션 서비스
- Figma files/images/styles/components 조회 확장
- Default Layout·Platform·Component Swap 정책
- Plugin의 Operation Preview·Apply 상태 보고
- 이미지·멀티 화면·플랫폼 변환 고급 시나리오

## 4. 범위

### 4.1 포함

1. 텍스트 설명 기반 생성
2. 기존 화면 참조 생성
3. 기존 화면 부분 수정
4. 이미지 Node 참조 생성
5. 멀티 스크린 플로우 생성
6. 논리 컴포넌트 지정 생성
7. Desktop·Tablet·Mobile 변환
8. Preview·승인·Apply·이력 저장
9. 디자인 시스템 Profile 교체와 Preflight
10. MCP·REST·Plugin 계약 및 보안 검증

### 4.2 제외

- Figma REST API를 이용한 직접 캔버스 쓰기
- Figma Library 자동 Publish·삭제·공개 Key 교체
- 사용자의 승인 없는 REPLACE 또는 파괴적 수정
- React, Slack, Teams 전용 클라이언트 구현
- 디자인 시스템 외부 SaaS에 API Key를 전달하는 기능
- 자연어 요청만으로 운영 Profile Token을 자동 변경하는 기능

## 5. 목표 아키텍처

```text
FigmaDesignOrchestrationTool (7 callbacks)
  └─ FigmaMcpFacadeService
      └─ FigmaDesignOrchestrationService
          ├─ FigmaDesignRequestRouter
          ├─ FigmaContextAnalyzer
          ├─ FigmaStyleExtractor
          ├─ FigmaPlatformConversionService
          ├─ FigmaMultiScreenFlowService
          ├─ DesignReferenceAnalysisService (기존)
          ├─ ComponentRegistryResolver (기존 확장)
          ├─ FigmaScreenExportService (기존)
          └─ FigmaDesignOperationRepository
                    │
                    ▼
        FigmaDesignOperation + FigmaExportBundle[]
                    │
                    ▼
           figma-screen-spec-plugin
          Preview → 승인 → Apply → Report
```

상태 전이는 다음으로 제한한다.

```text
ANALYZED
  → PREVIEW_READY
  → APPLY_REQUIRED
  → APPLIED

어느 단계에서든 검증 실패 → FAILED
source revision 불일치 → CONFLICT
사용자 반려 → REJECTED
```

서버는 Plugin의 적용 보고서를 받기 전에는 `APPLIED`를 반환하거나 저장할 수 없다.

## 6. 공통 계약

### 6.1 요청 모델

```text
FigmaDesignRequest
├─ requestType
├─ prompt
├─ designSystemProfileId
├─ fileKey
├─ referenceNodeIds[]
├─ editableNodeIds[]
├─ imageNodeIds[]
├─ targetPlatform: DESKTOP | TABLET | MOBILE
├─ componentLogicalTypes[]
├─ screens[{name, description}]
└─ syncMode: PREVIEW | MERGE | REPLACE
```

요청 유형은 Tool 이름이 우선한다. 사용자가 특정 Tool을 호출한 경우 LLM 분류기가 다른 유형으로
바꿀 수 없다. 자유 텍스트 통합 진입점에서만 Router를 사용하며 confidence가 기준보다 낮으면
추측하지 않고 입력 보완 오류를 반환한다.

### 6.2 Operation 모델

```text
FigmaDesignOperation
├─ operationId
├─ idempotencyKey
├─ requestType
├─ sourceFileKeyHash
├─ sourceRevision
├─ profileId/profileVersion/registryVersion/layoutPolicyVersion
├─ screens[]
├─ bundles[] 또는 artifactRef[]
├─ previewSummary
├─ issues[]
├─ status
├─ createdAt/updatedAt
└─ appliedReportRef
```

`fileKey`, node ID와 image export URL은 응답에 꼭 필요한 범위로만 노출한다. 저장과 로그에서는
해시·축약·redaction 정책을 적용한다.

### 6.3 디자인 시스템 결합

다음 세 항목은 하나의 버전 조합으로 원자 결합한다.

```text
DesignSystemProfile
  + ComponentRegistry
  + DefaultLayoutPolicy
```

셋 중 하나라도 Profile ID 또는 호환 버전이 다르면 생성·변환 Preflight를 실패시킨다.

## 7. 작업 패키지

### WP-0. 계약과 fixture 확정

목표: Java, MCP, Plugin이 공유할 요청·Operation 계약을 먼저 고정한다.

작업:

- `figma-design-request-v1.schema.json` 추가
- `figma-design-operation-v1.schema.json` 추가
- 7가지 `requestType` enum과 유형별 조건부 필수값 정의
- Operation 상태와 허용 상태 전이 정의
- source revision, idempotency key, artifact reference 정의
- 정상·경계·오류 fixture 추가
- `website-figma-contract/test/contract-test.mjs`에 교차 계약 검증 추가

완료 게이트:

- 7가지 요청의 정상 fixture가 Schema를 통과한다.
- 유형별 누락 입력, 알 수 없는 logical type, Profile 버전 불일치 fixture가 실패한다.
- Spring과 Plugin이 같은 enum·필수값을 사용한다.

선행 작업: 없음

### WP-1. Java 도메인과 정책 모델

목표: 오케스트레이션에 필요한 타입과 교체 가능한 Layout 정책을 구현한다.

예상 파일:

- `model/figma/orchestration/FigmaDesignRequest.java`
- `model/figma/orchestration/FigmaDesignScreenRequest.java`
- `model/figma/orchestration/FigmaDesignOperation.java`
- `model/figma/orchestration/FigmaDesignOperationStatus.java`
- `model/figma/policy/DefaultLayoutPolicy.java`
- `model/figma/policy/PlatformLayoutPolicy.java`
- `model/figma/policy/ComponentSwapPolicy.java`

정책 초기값:

| Platform | 폭 | Grid | Navigation | spacingScale | fontScale |
|---|---:|---:|---|---:|---:|
| DESKTOP | 1440 | 12 | side navigation | 1.0 | 1.0 |
| TABLET | 768 | 8 | drawer | 0.875 | 0.9375 |
| MOBILE | 390 | 4 | bottom navigation | 0.75 | 0.875 |

완료 게이트:

- Bean Validation과 Jackson round-trip 테스트 통과
- 잘못된 요청 유형·플랫폼·상태 전이 거부
- Profile·Registry·Layout Policy 호환성 검사 통과

선행 작업: WP-0

### WP-2. Operation 저장과 동시성

목표: 분석과 실제 Figma 적용 상태를 분리하고 멱등 재시도를 보장한다.

예상 파일:

- `mapper/FigmaDesignOperationRepository.java`
- `service/figma/orchestration/FigmaDesignOperationStateService.java`
- Operation 저장 테이블과 조회 인덱스 초기화 코드

작업:

- Operation 생성 시 request hash 기반 멱등 처리
- 같은 operationId의 다른 내용 덮어쓰기 금지
- source revision optimistic conflict 처리
- 멀티 스크린을 하나의 Operation으로 저장
- 모든 Preview 성공 전 `APPLY_REQUIRED` 전이 금지
- Plugin 보고서 수신 전 `APPLIED` 전이 금지
- 실패·반려·충돌 이력 불변 저장

완료 게이트:

- 동일 요청 재시도 시 Operation 중복 생성 없음
- source revision 변경 시 `CONFLICT`
- 멀티 스크린 일부 성공만으로 `APPLIED`가 되지 않음

선행 작업: WP-1

### WP-3. Figma REST 조회 확장

목표: 기존 `FigmaApiClient`의 안전장치를 유지하면서 7개 요청에 필요한 읽기 기능을 제공한다.

추가 메서드:

- `getFile(fileKey)`
- `getFileNodes(fileKey, nodeIds)`
- `getFileComponents(fileKey)`
- `getFileStyles(fileKey)`
- `exportNodeImages(fileKey, nodeIds, format, scale)`
- pagination helper와 응답 크기 제한

보안 요구:

- fileKey allowlist와 Profile 연결 확인
- 요청 node가 fileKey에 실제 소속되는지 검증
- image export URL 저장 금지 또는 짧은 TTL metadata만 저장
- 토큰·URL·원문 식별자 로그 redaction
- 401, 403, 404, 429, 5xx 오류 코드 정규화

완료 게이트:

- pagination, 429 Retry-After, timeout, 최대 응답 크기 테스트 통과
- 허용되지 않은 fileKey와 다른 파일의 node ID 거부
- API Token과 export URL이 오류 메시지·로그에 없음

선행 작업: WP-0

### WP-4. Router·Context·Style 분석

목표: 자연어와 참조 디자인을 결정론적 후보 계약으로 변환한다.

예상 파일:

- `service/figma/orchestration/FigmaDesignRequestRouter.java`
- `service/figma/orchestration/FigmaContextAnalyzer.java`
- `service/figma/orchestration/FigmaStyleExtractor.java`

`FigmaContextAnalyzer` 구조화 출력:

- domain
- screenType
- layoutPattern
- requiredLogicalTypes
- optionalLogicalTypes
- field/action 후보
- uncertainty와 Issue

`FigmaStyleExtractor` 출력:

- color token 후보
- typography token 후보
- spacing·padding·section gap
- grid·sidebar·header·footer 구조
- Profile Token과의 차이

운영 Profile은 자동 수정하지 않는다. 참조 스타일은 Preview 후보와 Issue로만 제공한다.

완료 게이트:

- 명시 Tool 유형 우선 테스트
- 낮은 confidence와 구조화 출력 오류 거부
- 기존 `FigmaDesignSpecMapper` fixture 회귀 통과
- 민감한 사용자 텍스트와 Key redaction 통과

선행 작업: WP-1, WP-3

### WP-5. 공통 오케스트레이션

목표: 7가지 요청의 공통 분석·검증·Bundle 조립 흐름을 하나의 서비스로 구현한다.

예상 파일:

- `service/figma/orchestration/FigmaDesignOrchestrationService.java`
- `service/figma/orchestration/FigmaMultiScreenFlowService.java`
- `service/figma/orchestration/FigmaPlatformConversionService.java`

공통 처리 순서:

1. 인증과 입력 검증
2. file/node/Profile allowlist 검증
3. Profile·Registry·Layout Policy Preflight
4. Context·Reference·Vision 분석
5. `ScreenSpecification` 후보 생성 또는 기존 Spec revision 생성
6. 사람 승인 필요 Issue 판정
7. `FigmaScreenSpec` 생성
8. Bundle과 Operation 조립
9. Schema·의미 검증
10. Operation 저장과 `PREVIEW_READY` 반환

반환값에는 `operationId`, `artifactId`, status, preview summary와 issues를 포함한다.

완료 게이트:

- Text·Reference·Modify·Component 지정 요청 E2E 서비스 테스트 통과
- 분석 결과만으로 `APPLIED`를 반환하지 않음
- 동일 입력의 Spec·logicalNodeId·Operation hash 결정성 보장

선행 작업: WP-2, WP-4

### WP-6. 7개 MCP Tool 노출

목표: 공통 서비스를 얇은 Tool로 노출한다.

예상 파일:

- `tools/FigmaDesignOrchestrationTool.java`
- `service/figma/FigmaMcpFacadeService.java` 확장
- `config/McpConfig.java` 등록

Tool:

- `create_design_from_text`
- `create_design_from_reference`
- `modify_existing_design`
- `create_design_from_image`
- `create_multi_screen_flow`
- `create_design_with_components`
- `convert_platform`

모든 Tool은 `FIGMA_MCP_SHARED_SECRET` 인증을 사용하고 비즈니스 로직을 포함하지 않는다.

계약 변경 시 함께 갱신할 항목:

- `McpToolDefinitionSnapshotTest`
- MCP Tool 개수 상수와 Tool 객체 개수 상수
- `src/test/resources/mcp/tool-definitions-baseline.json`
- `FigmaMcpRegistrationTest`

완료 게이트:

- 7개 callback 등록·중복 이름 없음
- 필수 입력 누락과 미지원 capability 오류 계약 통과
- MCP 응답에 Figma access token, LLM key, Published Key 원문 없음

선행 작업: WP-5

### WP-7. Figma Plugin Operation 적용

목표: 기존 Bundle Preview·MERGE/REPLACE 기능을 Operation 단위로 확장한다.

대상 파일:

- `figma-screen-spec-plugin/src/types.ts`
- `figma-screen-spec-plugin/src/core.ts`
- `figma-screen-spec-plugin/src/code.ts`
- `figma-screen-spec-plugin/src/ui.html`
- `figma-screen-spec-plugin/test/core.test.mjs`

작업:

- operationId, requestType, sourceRevision 표시
- Preview diff와 서버 preview summary 일치 검증
- `editableNodeIds`의 file/page/승인 범위 재검증
- Apply 직전 source revision 재확인
- 멀티 스크린 전체 Preview 후 일괄 Apply
- 중간 실패 시 root 숨김 Backup 또는 전체 복구
- 실제 Apply 완료 후 생성 보고서 전송
- 운영 Profile Token과 참조 Token 후보의 차이 표시

완료 게이트:

- 분석 완료와 Apply 완료 상태가 분리됨
- revision 충돌 시 캔버스 변경 없음
- 허용되지 않은 node 수정 없음
- MERGE 사용자 override 보존 회귀 통과

선행 작업: WP-2, WP-5

### WP-8. 고급 요청 구현

#### WP-8A. 이미지 참조

- Figma image Node export
- 기존 `VisionAnalysisClient` capability 사전 점검
- 구조·텍스트·접근성·불확실성 Issue 생성
- 이미지 URL과 원본 바이너리의 보존 기간 제한

#### WP-8B. 멀티 스크린

- 공유 flowId, Profile, Token, Navigation 규칙 고정
- 화면별 Spec·Bundle 생성
- 부분 실패 시 Operation 전체 실패
- Plugin 전체 Preview 후 일괄 Apply

#### WP-8C. 플랫폼 변환

- Desktop·Tablet·Mobile Grid 재계산
- side navigation → drawer/bottom navigation
- `footer__pc ↔ footer__mo` Profile 기반 swap
- sticky/fixed/breakpoint annotation 보존
- 대체 컴포넌트 미게시 시 변환 중단

완료 게이트:

- 이미지 capability 미지원 시 명확한 오류
- 멀티 화면 부분 적용 없음
- 플랫폼별 golden fixture가 폭·Grid·Navigation·Swap 정책과 일치

선행 작업: WP-3, WP-5, WP-7

### WP-9. 운영·문서·릴리스 검증

작업:

- Operation 조회·재시도·실패 원인 REST API
- 단계별 pipelineId/operationId 구조화 로그
- 성공률, Preview 반려율, Apply 충돌률, fallback 비율 집계
- API Token·file/node ID·image URL redaction 감사
- 운영 Runbook과 장애 복구 절차 갱신
- 디자인 시스템 교체 체크리스트와 Rollback 절차 갱신

완료 게이트:

- 전체 Gradle 테스트와 `bootJar` 성공
- 계약 테스트 성공
- Plugin typecheck, lint, unit test, build 성공
- 실제 Figma Desktop에서 대표 7개 요청 Preview/Apply 검증
- MCP 초기화와 7개 Tool 목록·호출 확인

선행 작업: WP-6, WP-7, WP-8

## 8. 구현 순서와 의존성

```text
WP-0 계약
 ├─→ WP-1 도메인·정책 ─→ WP-2 Operation 저장 ─┐
 └─→ WP-3 Figma REST ─→ WP-4 분석기·Router ───┤
                                                ▼
                                      WP-5 오케스트레이션
                                       ├─→ WP-6 MCP Tool
                                       └─→ WP-7 Plugin Apply
                                                │
                                                ▼
                                         WP-8 고급 요청
                                                │
                                                ▼
                                         WP-9 운영·릴리스
```

WP-6을 WP-5보다 먼저 구현하지 않는다. Tool부터 만들면 요청별 로직이 Tool 클래스에 흩어지고
기존 Orchestrator 분리 원칙을 다시 위반하게 된다. WP-7은 Operation 계약과 상태 저장이 확정된
후 착수한다.

## 9. 릴리스 단계

### Release A — 계약과 안전한 Operation 기반

- WP-0, WP-1, WP-2
- 목표: 요청/Operation Schema, 상태 전이, 멱등 저장

### Release B — 핵심 4개 요청 MVP

- WP-3, WP-4, WP-5
- Text, Reference, Modify, Component 지정
- 목표: `PREVIEW_READY` Bundle 생성까지

### Release C — MCP와 Plugin Apply

- WP-6, WP-7
- 목표: 7개 Tool 등록, Preview·승인·Apply 보고 연결

### Release D — 고급 요청

- WP-8
- Image, Multi-screen, Platform Convert

### Release E — 운영 안정화

- WP-9
- 메트릭, Runbook, 실제 Figma E2E, RC 배포

## 10. 테스트 계획

### 10.1 Backend

- 요청 DTO Bean Validation·직렬화
- Router 명시 유형 우선·낮은 confidence 거부
- Operation 상태 전이·멱등성·source revision 충돌
- Text/Reference/Modify/Image/Multi/Component/Platform fixture
- Profile·Registry·Layout Policy 버전 불일치
- Figma REST pagination·429·401/403/404·timeout
- Spring AI 구조화 출력 오류·rate limit·Vision 미지원
- Tool 인증·callback 등록·redaction·snapshot
- 기존 Builder·Bundle·Artifact·Registry 회귀

### 10.2 Contract

```bash
cd website-figma-contract
npm test
```

### 10.3 Plugin

```bash
cd figma-screen-spec-plugin
npm run typecheck
npm run lint
npm test
npm run build
```

### 10.4 전체 검증

```bash
./gradlew clean test bootJar
```

실제 Figma Desktop E2E는 자동 테스트와 별도로 다음을 확인한다.

- Published Component import 성공
- Preview와 실제 Apply diff 일치
- 사용자 override 보존
- source revision 충돌 차단
- 멀티 화면 부분 적용 방지
- 플랫폼 Swap과 Auto Layout 결과

## 11. 보안 체크리스트

- [ ] `.env`가 Git과 Release artifact에 포함되지 않는다.
- [ ] Figma access token과 LLM API key가 Tool 인자·응답·로그에 없다.
- [ ] fileKey는 승인 Profile allowlist를 통과한다.
- [ ] node ID가 요청 file/page에 실제 소속된다.
- [ ] image export URL은 저장하지 않거나 TTL 이후 제거한다.
- [ ] Published Component/Variable Key 원문은 MCP 응답에서 제거한다.
- [ ] LLM 원문을 Plugin 명령으로 직접 실행하지 않는다.
- [ ] Schema와 의미 검증을 통과한 Operation만 Preview할 수 있다.
- [ ] REPLACE, Library Publish, Registry Breaking 변경은 사람 승인이 필요하다.
- [ ] Apply 직전에 source revision을 다시 검사한다.

## 12. 주요 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 별도 MCP 서버 중복 구현 | 계약·인증·운영 이원화 | 기존 Spring Boot MCP에 통합 |
| 로컬 Node ID 장기 저장 | Library Publish 후 매핑 파손 | 논리 타입과 Published Key 사용 |
| LLM 자유 출력 직접 실행 | 비결정적·파괴적 캔버스 변경 | 구조화 출력→Schema→의미 검증→Preview |
| 분석과 Apply 상태 혼동 | 실제 미적용인데 성공 보고 | Plugin 보고서 전에는 `APPLIED` 금지 |
| 멀티 화면 부분 성공 | 플로우 불일치 | Operation 원자 상태와 전체 Preview 적용 |
| 참조 스타일이 운영 Token 오염 | 디자인 시스템 무단 변경 | Token 후보 diff만 표시, 자동 변경 금지 |
| API Key·식별자 노출 | 보안 사고 | 환경변수 주입, 응답·로그 redaction 감사 |
| 기존 Tool 계약 변경 | MCP 클라이언트 회귀 | Snapshot과 callback 등록 테스트 갱신 |

## 13. 최종 완료 정의

다음 조건을 모두 만족하면 구현 완료로 판단한다.

- 7개 MCP Tool이 동일 인증·응답·Operation 계약을 사용한다.
- 7개 요청이 `FigmaScreenSpec`과 Bundle을 생성한다.
- Plugin Preview 승인 전에는 캔버스가 변경되지 않는다.
- Plugin 보고서 전에는 Operation이 `APPLIED`가 되지 않는다.
- Text·Reference·Modify·Component 요청이 자동화 테스트를 통과한다.
- Image·Multi-screen·Platform 요청이 fixture와 실제 Figma E2E를 통과한다.
- 디자인 시스템 교체가 Profile·Registry·Layout Policy Snapshot 교체로 가능하다.
- 기존 LIST/FORM/DETAIL Builder와 MERGE/REPLACE 동작에 회귀가 없다.
- 전체 계약·Backend·Plugin·CI 검증이 성공한다.
- API Key, `.env`, Published Key 원문과 단기 image URL이 공개 산출물에 포함되지 않는다.

## 14. 기존 문서와의 관계

이 문서는 원본 명세를 현재 코드베이스에 적용하기 위한 실행 순서 문서다. 세부 설계와 전체
체크리스트의 단일 원본은 다음 문서를 함께 사용한다.

- `11_Semantic_Figma_Design_System_Implementation_Plan.md`의 R6A
- `12_Semantic_Figma_Design_System_Implementation_List.md`의 R0-T07~08, R1-014~015/029,
  R5-040~045, R6-030~048, R6-T08~13
- `13_Semantic_Figma_Operations_Runbook.md`
- `website-figma-contract/CONTRACT_RULES.md`

구현 완료 여부는 본 문서에 중복 체크하지 않고 12번 구현 목록의 작업 ID를 갱신해 관리한다.
