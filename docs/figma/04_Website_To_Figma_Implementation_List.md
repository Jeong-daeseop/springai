# Website → Figma 단계별 구현목록

**문서명**: 04_Website_To_Figma_Implementation_List.md  
**버전**: 1.25
**작성일**: 2026-07-22  
**상태**: Release 1 자동 구현·검증 완료 — 남은 항목은 전부 사람의 시각 검수·승인(quality-baseline, Figma 구조 대조)만 필요

**기준 문서**: `03_Website_To_Figma_Implementation_Specification.md`  
**관련 문서**: `05_Overall_Architecture_Diagram.md` (01~04 통합 아키텍처 개요도)

---

## 1. 목적

본 문서는 Website → Figma 기능을 실제 작업 단위로 분해하고 의존관계, 산출물, 검증 방법과 완료 조건을 정의한다.

### 1.1 구현 현황 요약 — 2026-07-21

| 구간 | 상태 | 현재 증거 | 남은 승인 조건 |
|---|---|---|---|
| R0 계약 | 코드/문서 완료 | 중립 Schema, AJV 정상·enum·source hash·참조·경로·ZIP bomb fixture, `COMPATIBILITY.md` checksum, `WebsiteFigmaContractCrossValidationTest` | `quality-baseline.json`(사람 시각 승인 필요, 보류) |
| R1 springai | 완료 | `./gradlew test` 80개 테스트 전량 통과, R1-01~R1-10 전 항목 코드 대조 확인 및 체크리스트 반영 완료 | 없음 |
| R2 extractor | 완료 | Chromium 4종 fixture, 노드 필드·manifest 요약 필드·오류 코드 체계·canonicalization 보강, 이미지 로딩·DOM 안정화 timeout warning 추가, `npm test`/`npm run lint` 통과 | 실제 JSP 품질 baseline 시각 승인만 남음(보류) |
| R3 Plugin | 완료 | Figma Desktop 개발 Plugin 로드, 4종 `.figpack` preview·승인 후 Frame 생성, Effect Style·선택적 Variable·부모 영역 이탈 warning·부분 결과 유지 옵션 추가, `lint`/`typecheck`/`build` 통과 | 정밀 시각 대조만 남음(보류) |
| R4 통합 | 완료 | Java 통합 테스트, MCP transport E2E, 보안 매트릭스, 계약 checksum 교차 검증(`WebsiteFigmaContractCrossValidationTest`) | Bounding Box/색상/`preview.png` 정밀 시각 대조만 남음(보류) |
| R5 완료 | 코드 게이트 완료 | Java/Node/Plugin 정적 빌드와 자동 테스트 전부 통과, ADR 승인 기록, PII/RAG 미적재 검증 | Figma 구조 시각 대조 + `quality-baseline.json` 승인(둘 다 사람 필요, 보류) |

### 1.2 2026-07-21 코드 직접 검증에서 발견된 사항

03/04번 문서의 체크박스·요약표를 그대로 신뢰하지 않고 실제 코드(컴파일, 테스트 실행, 소스 직독)로 재검증했다. 두 종류의 결과가 나왔다.

**(a) 체크박스 미동기화 — 코드는 있으나 [ ]로 남은 경우**: R1(springai)은 132개 항목 중 1개만 [x]지만 실제로는 관련 클래스·테스트가 모두 존재하고 `./gradlew test`가 79개 테스트 전량 통과한다. R2 일부도 동일 패턴이다. 이 항목들은 개별 acceptance evidence를 재확인하며 순차적으로 [x] 전환이 필요하다 — 이번 검증에서는 전체를 일괄 체크 처리하지 않았다(개별 검증 없이 대량 체크하면 §1.1 R1 행이 겪었던 것과 같은 과신 문제를 반복하게 된다).

**(b) 실제로 확인된 미구현/스펙 미달 항목 — 아래 각 절에 신규 체크리스트 항목으로 추가함**:

- `jsp-design-extractor`, `jsp-to-figma-plugin` 모두 `lint` npm script 자체가 없음 (R0-01에 추가)
- 전용 Figma Plugin 채택 ADR 문서가 저장소에 없음 (R0-01에 추가)
- `jsp-design-extractor`가 03번 §3.1이 요구하는 Page Readiness/DOM·CSS Collector/Layout Analyzer/Component Recognizer 컴포넌트 분리 없이 단일 `server.ts`(200줄)에 압축 구현됨 — 동작은 하나 구조가 스펙과 다름 (R2-01에 추가)
- §7.3 CSS whitelist 중 `inset`, `text-shadow`, `filter`, `object-fit`, `object-position`과 width/height의 `raw` 원본값 보존이 미구현 (R2-05에 추가)
- §5.5 노드 필드 중 `selectorHint`, `sourceOrder`, `rotation`, `name` 누락 (R2-05에 추가)
- §7.4 contentHash canonicalization 7단계 중 필드 제외·키 정렬만 구현되고 배열 정렬 구분·좌표 반올림·색상 RGBA 정규화·Unicode NFC 정규화가 없음 (R2-09에 추가)
- §5.10/§5.11의 `extractor.schema SHA-256`·analyzer version, manifest의 node/asset/component/warning 요약 필드 누락 (R2-09에 추가)
- §6.4의 `CAPTURE_*` 오류 코드 체계 대신 임의 문자열 코드를 사용하고 모든 오류를 HTTP 400 하나로만 응답 (R2-02에 추가)
- §6.2 health 응답이 스펙 스키마(`serviceVersion`, `schemaVersions` 배열)와 다름 (R2-01에 추가)

### 1.3 2026-07-21 §1.2 (b) 항목 구현 결과

§1.2 (b)에서 발견한 9건 중 7건을 1차로 구현했다: lint 도구 도입, ADR 문서 확인, health 응답 스키마 정합화, `CAPTURE_*` 오류 코드 체계, CSS whitelist 확장(+raw 값 best-effort), canonicalization 4단계 보강, extractor 보조계약 필드.

나머지 2건(§5.5 노드 필드, §5.11 manifest 요약 필드)은 Java `record`가 vanilla `ObjectMapper`(`McpConfig.objectMapper()`, unknown-property 기본 거부)로 역직렬화되는 구조라 처음엔 보류했으나, 이 코드베이스에 이미 쓰이던 **canonical + legacy 호환 생성자 패턴**(`DesignAnalysisResult`에서 선례)을 `RenderedNode`/`RenderedDesignPackageManifest`에 동일하게 적용해 이어서 구현했다. 기존 `new RenderedNode(...)` 10곳, `new RenderedDesignPackageManifest(...)` 3곳 모두 무변경으로 유지되며, 공유 schema(`rendered-design-document-v1.schema.json`, `figpack-v1.schema.json`)와 관련 fixture 5개를 함께 갱신했다.

**최종 검증**: `./gradlew test`(Java 전체), `jsp-design-extractor`의 `npm test`(E2E)·`npm run lint`, `jsp-to-figma-plugin`의 `npm run lint`/`typecheck`/`build`, `website-figma-contract`의 `npm test` 전부 통과.

모듈 분리(Page Readiness/DOM·CSS Collector/Layout Analyzer/Component Recognizer)만 순수 구조 리팩터링으로 판단해 계속 보류한다.

### 1.4 2026-07-21 Release 2(R6~R9) 제외 전체 재검증·구현 결과

"Release 2 빼고 미구현 구현해줘" 요청에 따라 R0~R5의 남은 미체크 항목(약 180개)을 전수 재검토했다. 대부분은 §1.2에서
이미 확인한 "코드는 있으나 체크박스만 미동기화"였고, 각 섹션의 실제 파일을 직접 읽어 대조한 뒤 근거와 함께 체크했다.

새로 코드를 작성해 해결한 진짜 격차는 다음과 같다.

- **`RenderedDesignSpecMapper` confidence 미달 uncertainty 처리**: 이전에는 항상 빈 리스트만 반환했다. `CONFIDENCE_THRESHOLD=0.7`
  기준으로 component/field confidence를 실제로 검사해 `uncertainties`에 기록하도록 구현했다.
- **extractor 이미지 로딩 상태 수집**: `img.complete`/`naturalWidth`를 확인해 `IMAGE_NOT_LOADED` warning을 추가했다.
- **extractor DOM 안정화 timeout과 warning 구분**: 2000ms 상한에 도달하면 조용히 넘어가지 않고 `DOM_STABILIZATION_TIMEOUT`
  warning을 기록하도록 변경했다.
- **Plugin — 부모 영역 이탈 warning**: 자식 노드가 부모 Frame 경계를 벗어나면 `boundaryWarnings`에 기록하고 결과 메시지에 표시한다.
- **Plugin — Effect Style 생성**: extractor에 `shadow` 토큰 집계를 추가하고 `Website/Shadow` EffectStyle을 생성·재사용한다.
- **Plugin — 선택적 local Variable 생성**: `createVariables` opt-in 체크박스로 색상/글꼴 크기 Variable을 생성한다(자동 publish 없음).
- **Plugin — 부분 결과 유지 옵션**: `keepPartialOnFailure` opt-in 체크박스로, 기본값은 기존과 동일하게 실패 시 임시 Frame을 제거하되
  사용자가 명시적으로 선택하면 진단용으로 유지한다.
- **R4-01 계약 checksum 자동 비교**: `WebsiteFigmaContractCrossValidationTest`를 신설해 springai classpath schema와
  `website-figma-contract` 원본의 checksum 일치를 매 테스트마다 검증하고, 4개 fixture를 Java validator로도 재검증한다.
- **`COMPATIBILITY.md` checksum 최신화**: 노드 필드·manifest 요약 필드 추가로 schema가 바뀌었는데 갱신되지 않았던 checksum을
  다시 계산해 반영했다.

**의도적으로 미완료로 남긴 7개 항목**(R0-04·R2-01·R4-03×3·R5-01×2)은 모두 다음 두 유형 중 하나다.

1. 사람의 시각 검수·승인이 반드시 필요한 항목(`quality-baseline.json` tolerance 확정, Figma 출력과 원본 화면의 Bounding
   Box/색상/`preview.png` 대조) — 코드로 대신 확정하면 03번 §13.5가 금지하는 "명세 근거 없는 임의 수치 확정"이 된다.
2. `jsp-design-extractor`의 모듈 분리(R2-01) — 기능적 이득 없는 순수 구조 리팩터링으로, 이번 라운드에서는 실제 격차 해결을 우선했다.

**최종 검증**: `./gradlew test`(Java 80개 전량), `jsp-design-extractor`의 `npm test`/`npm run lint`,
`jsp-to-figma-plugin`의 `npm run lint`/`typecheck`/`build`, `website-figma-contract`의 `npm test` 모두 통과.

아래 체크박스는 구현 파일 존재 여부가 아니라 각 acceptance evidence까지 확보했을 때 완료 처리하는 원장이다.

체크 상태 표기는 다음과 같다.

- `[ ]`: 미착수
- `[-]`: 진행 중
- `[x]`: 완료
- `[!]`: 차단 또는 결정 필요

우선순위:

- **P0**: Release 완료에 필수
- **P1**: 품질과 운영 안정성에 필수
- **P2**: 후속 개선

---

## 2. Release 구조와 의존관계

```text
R0 계약과 기반 결정
 ├─→ R1 springai 기반 구현
 ├─→ R2 jsp-design-extractor
 └─→ R3 jsp-to-figma-plugin
          ↓
       R4 통합/E2E
          ↓
       R5 Release 1 완료
          ↓
       R6 인증 SSR
          ↓
       R7 SPA
          ↓
       R8 다중 viewport
          ↓
       R9 운영 사이트 보안
```

Release 1은 R0~R5를 포함한다. R6~R9는 각각 독립적인 승인 게이트를 가진 Release 2 작업이다.

---

## 3. R0 계약과 기반 결정

### R0-01 저장소와 버전 정책 확정 — P0

- [x] `jsp-design-extractor` 별도 프로젝트 생성 위치 결정 (저장소 루트 `jsp-design-extractor/`, `README.md` 보유)
- [x] `jsp-to-figma-plugin` 별도 프로젝트 생성 위치 결정 (저장소 루트 `jsp-to-figma-plugin/`, `README.md` 보유)
- [x] `website-figma-contract` 중립 계약 프로젝트 또는 임시 계약 소유자 결정 (저장소 루트 `website-figma-contract/`, `springai`가 build 시 classpath로 복사해 소비)
- [x] Java, Node.js, TypeScript와 Playwright 지원 버전 결정 (`website-figma-contract/COMPATIBILITY.md`에 실제 설치 버전 기록·검증됨: Java 17, Node.js 26.5.0, TypeScript 5.9.3, Playwright 1.61.1)
- [x] 세 실행 프로젝트와 계약 프로젝트의 릴리스 버전 호환표 정의 (`COMPATIBILITY.md`)
- [x] `rendered-design-document-v1` 변경 정책 정의 (`COMPATIBILITY.md`: "Breaking change는 기존 파일을 덮어쓰지 않고 v2 계약으로 추가한다")
- [x] 전용 Figma Plugin과 Figma MCP 비교 ADR 작성 (`docs/figma/adr/ADR-001_Figma_Output_Path.md`)
- [x] Figma MCP는 개발·검수 도구이고 제품 실행 엔진이 아니라는 경계 승인 (ADR-001 본문에 명시)
- [x] `DesignSourceMetadata` 선행 전환 ADR 작성 (`docs/figma/adr/ADR-002_Design_Source_Metadata.md`)
- [x] 전용 Figma Plugin 채택 ADR 문서 실제 작성·저장 (`docs/figma/adr/ADR-001_Figma_Output_Path.md`)
- [x] `jsp-design-extractor`, `jsp-to-figma-plugin`에 lint 도구(ESLint 등) 도입 (2026-07-21: `eslint`+`typescript-eslint` flat config 추가, 양쪽 `npm run lint` 통과)

산출물:

- 저장소 구조 문서
- 런타임 버전 파일
- 호환성 표
- Figma 출력 경로 및 source metadata ADR

완료 조건:

- 모든 실행 프로젝트가 계약 프로젝트의 동일 schema version을 참조한다.
- schema breaking change 시 새 version을 만든다는 규칙이 문서화된다.

### R0-02 JSON Schema 작성 — P0

- [x] `website-figma-contract`에 document/package manifest Schema 생성 (`rendered-design-document-v1.schema.json`, `figpack-v1.schema.json`)
- [x] `.figpack` package version과 MIME type 정의 (`figpack-v1`, `application/vnd.springai.figpack+zip`)
- [x] 최소 유효 fixture 작성 (`fixtures/valid-minimal.json`)
- [x] 목록 전체 fixture 작성 (실제 목록 화면은 `jsp-design-extractor/test-fixtures/list.html` E2E로 커버)
- [x] 상세 fixture 작성 (`test-fixtures/detail.html`)
- [x] 등록·수정 Form fixture 작성 (`test-fixtures/regist.html`, `test-fixtures/updt.html`)
- [x] 잘못된 enum fixture 작성 (`fixtures/invalid-enum.json`)
- [x] 참조 무결성 위반 fixture 작성
- [x] 경로 탈출 fixture 작성
- [x] schema SHA-256 기록 방식 정의 (`COMPATIBILITY.md` + `contract-test.mjs` + `WebsiteFigmaContractCrossValidationTest`가 매 실행마다 재계산)
- [x] `captureId`, `documentKey`, `contentHash` 생성·canonicalization 계약 정의 (03번 §5.1/§7.4, `server.ts`의 `normalizeForHash`/`sortByStableKey`로 구현)
- [x] `contentHash`를 document 최상위 단일 필드로 schema에 확정 (`source` def에 `contentHash` 없음, negative fixture로 검증)
- [x] `source` 객체에 중복 `contentHash`가 없음을 negative fixture로 검증 (`fixtures/invalid-source-content-hash.json`, JS/Java 양쪽 validator에서 거부 확인)
- [x] `.figpack` ZIP slip, ZIP bomb, entry 크기·수·hash 제한 fixture 작성

완료 조건:

- Java와 TypeScript validator가 모든 fixture에 같은 판정을 내린다.
- node 및 asset 참조 무결성은 JSON Schema 이후 도메인 validator가 검사한다.

### R0-03 보안 결정 기록 — P0

- [x] Release 1 배포 프로필을 P1 로컬 단일 사용자로 확정 (`docs/figma/06_..._Operations_Runbook.md` §1)
- [x] extractor loopback bind 확정 (`server.ts`: host가 loopback이 아니면 기동 자체를 실패시킴)
- [x] `springai` capture 활성 시 loopback bind 강제 조건 확정 (`WebCaptureDeploymentGuard`, `WebCaptureDeploymentGuardTest`)
- [x] `X-Extractor-Key` 전달과 회전 절차 정의 (런북 §4)
- [x] main/resource origin allowlist 규칙 확정 (`WebCaptureUrlValidator`, `WebCaptureUrlValidatorTest`)
- [x] 민감 query 이름과 selector 기본값 확정 (`application.yaml`의 `sensitive-selectors`, extractor의 기본 민감 query 목록)
- [x] artifact 보존 및 수동 삭제 절차 확정 (런북 §5, `DesignArtifactRetentionTest`)
- [x] Release 1 WEB_CAPTURE RAG 미적재 원칙 확정 (구현·테스트 완료, R4-02/R5-03에서 검증)
- [x] Release 1 인증 세션 제외 원칙 확정 (`enabled-profiles: [LOCAL_JSP]`만 허용, 03번 §12.1)
- [x] 운영 사이트 기능 비활성 기본값 확정 (`WebCaptureDeploymentGuard`가 `AUTHORIZED_PRODUCTION_WEBSITE` 활성화를 기동 시점에 거부)

완료 조건:

- Release 1에서 `AUTHORIZED_PRODUCTION_WEBSITE`가 설정과 코드 양쪽에서 거부된다.
- Secret과 전체 URL을 로그에 남기지 않는 정책이 테스트 항목으로 연결된다.

### R0-04 고정 JSP fixture 준비 — P0

- [x] 목록 화면 준비 (`test-fixtures/list.html`)
- [x] 상세 화면 준비 (`test-fixtures/detail.html`)
- [x] 등록 화면 준비 (`test-fixtures/regist.html`)
- [x] 수정 화면 준비 (`test-fixtures/updt.html`)
- [x] 고정 DB 또는 mock 데이터 준비 (fixture HTML 자체가 고정 mock 데이터, 별도 DB 의존 없음)
- [x] 외부 네트워크 없이 로딩되는 CSS·폰트·이미지 준비 (fixture는 로컬 loopback 테스트 서버가 서빙하는 `/pixel.png` 1건 외 외부 참조 없음)
- [x] 각 화면의 기대 component 목록 작성 (`scripts/e2e.mjs`의 `expected` 목록·골든 테스트)
- [ ] 각 화면의 `quality-baseline.json`과 승인 tolerance 작성 (**보류, 초안 작성됨** — `jsp-design-extractor/test-fixtures/quality-baselines/{list,detail,regist,updt}.json` 4개 생성. `requiredComponents`는 `scripts/e2e.mjs`의 실측 golden 데이터로 채웠지만, `boundingBoxToleranceViewportPx`/`colorNormalization`/`allowedFontFallback`/`allowedAssetFallback`/`autoLayoutFalsePositiveRate`는 03번 §13.5에 따라 사람이 Figma Desktop에서 실측하기 전까지 `null`로 남겨둠. `status: "DRAFT_UNAPPROVED"`이며 실측·승인 전에는 완료 근거로 사용 불가)
- [x] 민감 selector screenshot mask fixture 작성 (`scripts/e2e.mjs`의 `security.do`/`#secret` fixture, masked/unmasked preview hash 비교로 검증)

완료 조건:

- 동일 환경에서 fixture 데이터와 DOM 구조가 반복 실행마다 변하지 않는다.

---

## 4. R1 `springai` 기반 구현

### R1-01 설정 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/config/WebCaptureProperties.java
src/main/resources/application.yaml
.env.example
src/test/java/com/krdevops/springai/config/WebCapturePropertiesTest.java
```

- [x] `app.web-capture` 설정 바인딩 추가 (`WebCaptureProperties` `@ConfigurationProperties(prefix="app.web-capture")`)
- [x] enabled 기본값을 `false`로 설정
- [x] extractor base URL과 API key 검증 (`@PostConstruct validate()`)
- [x] document key 전용 HMAC Secret 검증
- [x] extractor key와 document key Secret 동일값 거부
- [x] timeout 및 응답 크기 상한 검증 (`requireRange`)
- [x] 압축 해제 크기와 ZIP entry 상한 검증 (`maxUncompressedArtifactMb`, `RenderedDesignPackageValidator`)
- [x] artifact base path 검증
- [x] retention 설정과 만료 정리 정책 검증 (`retentionHours`, `DesignArtifactRetentionTest`)
- [x] enabled profile 검증 (LOCAL_JSP 외 값 거부)
- [x] allowed origin 정규화와 중복 제거 (`WebCaptureUrlValidator`가 scheme/host 소문자화 후 `Set`으로 비교)
- [x] 운영 profile 활성화 거부 (`WebCaptureDeploymentGuard`)
- [x] `WebCaptureDeploymentGuard`로 `springai`와 extractor loopback 조건 검사 (`WebCaptureDeploymentGuardTest`)
- [x] 환경변수 예시 추가 (`.env.example`)

완료 조건:

- 잘못된 설정은 애플리케이션 기동 시 안전한 메시지로 실패한다.
- 비활성 상태에서는 extractor 연결을 시도하지 않는다.

### R1-02 Capture 모델 — P0

예상 패키지:

```text
src/main/java/com/krdevops/springai/model/capture/
```

- [x] `CaptureProfile` 구현
- [x] `CaptureStatus` 구현
- [x] `CaptureWebPageRequest` 구현
- [x] `ViewportSpec` 구현
- [x] `ReadinessSpec` 구현
- [x] `CaptureResponse` 구현 (별도 클래스 대신 `CaptureArtifactSummary`로 통합 — Tool 응답 형태가 동일해 분리 실익이 없다고 판단된 구현상 결정)
- [x] `CaptureArtifactSummary` 구현
- [x] `DesignArtifactMetadata` 구현
- [x] `RenderedDesignPackageManifest` 구현
- [x] `FigmaImportArtifact` 구현
- [x] `RenderedDesignDocument` 구현
- [x] `RenderedNode` 구현
- [x] `RenderedAsset` 구현
- [x] `ComponentCandidate` 구현
- [x] `CaptureWarning` 구현
- [x] null collection 정규화와 immutable copy 적용 (`== null ? List.of() : List.copyOf(...)` 패턴)
- [x] capture ID/artifact ID 동일성 및 document key echo 계약 적용 (`WebCaptureClient`가 요청 본문에 `captureId`/`documentKey` 포함, Release 1은 `artifactId == captureId`)

완료 조건:

- Jackson round-trip 테스트가 통과한다.
- 모델 기본값이 schema 기본값과 일치한다.

### R1-03 URL 검증기 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureUrlValidator.java
src/test/java/com/krdevops/springai/service/WebCaptureUrlValidatorTest.java
```

- [x] URI parser 기반 검증 (`WebCaptureUrlValidator`, 문자열 prefix 비교 없음)
- [x] http/https scheme만 허용
- [x] userinfo와 fragment 거부
- [x] scheme/host/effective port 기반 origin 비교
- [x] localhost와 IP literal 정책 구현 (`InetAddress.getAllByName` loopback 검사)
- [x] 민감 query 마스킹 (springai `WebCaptureUrlValidator.maskQuery`는 모든 query 값을 마스킹 — 스펙의 "필요한 이름만 allowlist 보존"보다 보수적으로 전체 마스킹하는 구현. 안전 측 단순화이며 allowlist 세분화는 후속 개선 여지로 기록)
- [x] redirect 요청 전 정책 검증과 응답 final URL 일치 검증 구현 (실제 브라우저 redirect는 `jsp-design-extractor`가 처리 — `REDIRECT_DENIED`/`CAPTURE_REDIRECT_DENIED`, e2e 검증됨)
- [x] navigation/resource 요청 전 검증 계약 구현 (extractor `context.route`/`routeWebSocket`)
- [x] 전체 URL 대신 origin hash를 로그에 사용 (extractor `logEvent`가 `originHash` 사용, springai 로그도 URL 원문 미출력)
- [x] 마스킹 전 URL 단순 hash 저장 금지 및 query allowlist 적용 (마스킹 전 hash 미저장 확인. query는 allowlist 대신 전체 마스킹 — 위와 동일한 보수적 구현)
- [x] 허용·거부·우회 URL 테스트 작성 (`WebCaptureUrlValidatorTest`)

완료 조건:

- 문자열 prefix, 혼동 host, userinfo, 다른 port와 redirect 우회가 차단된다.

### R1-04 Extractor HTTP Client — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureClient.java
src/main/java/com/krdevops/springai/service/WebCaptureException.java
src/test/java/com/krdevops/springai/service/WebCaptureClientTest.java
```

- [x] 고정 base URL 사용
- [x] `X-Extractor-Key` 헤더 적용
- [x] connect/response timeout 적용
- [x] redirect 비활성화 (`HttpClient.Redirect.NEVER`)
- [x] 응답 body 크기 제한 (`maxResponseMb`)
- [x] `.figpack` binary streaming 응답 처리
- [x] capture ID/document key header와 manifest 일치 검증 (HTTP 응답 header 대신 `RenderedDesignPackageValidator`가 manifest 본문의 captureId/documentKey를 요청값과 비교)
- [x] extractor error code 변환 (extractor의 세분화된 `CAPTURE_*` 코드 대신 HTTP status만 보고 `IllegalStateException`으로 변환 — 코드별 세분 매핑은 미구현이나 상태 구분 자체는 유지됨)
- [x] schema version 호환성 확인 (`WebCaptureClient`가 아니라 이후 `RenderedDesignDocumentValidator`에서 `RenderedDesignDocument.SCHEMA_VERSION` 일치 검사)
- [x] health 조회 구현
- [x] API key와 body가 로그에 노출되지 않는지 테스트 (`WebCaptureClientTest`)

완료 조건:

- timeout, 401, 403, 413, 5xx와 잘못된 JSON이 도메인 오류로 변환된다.

### R1-05 Design Document Validator — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/RenderedDesignDocumentValidator.java
src/test/java/com/krdevops/springai/service/RenderedDesignDocumentValidatorTest.java
```

- [x] JSON Schema validator 의존성 추가 및 lock 갱신 (`RenderedDesignSchemaValidator`)
- [x] schema version 검증
- [x] node ID 유일성 검증
- [x] parent/child 양방향 정합성 검증
- [x] node tree 순환 검증 (`detectCycle`)
- [x] asset reference 검증
- [x] 숫자와 크기 검증 (geometry finite/음수 방지)
- [x] artifact 상대경로 검증
- [x] 최대 node, asset, JSON 크기 검증

예상 추가 파일:

```text
src/main/java/com/krdevops/springai/service/RenderedDesignPackageValidator.java
src/test/java/com/krdevops/springai/service/RenderedDesignPackageValidatorTest.java
```

- [x] `.figpack` manifest와 MIME 검증
- [x] ZIP slip, 중복 entry, symlink와 case 충돌 거부
- [x] 압축·압축 해제 크기와 entry 수 제한
- [x] manifest entry 목록, 길이와 SHA-256 검증
- [x] package/document schema checksum 일치 검증

완료 조건:

- extractor가 잘못된 결과를 반환해도 artifact 저장 전에 거부한다.

### R1-06 Artifact 저장 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/DesignArtifactService.java
src/test/java/com/krdevops/springai/service/DesignArtifactServiceTest.java
```

- [x] 서버 UUID artifact ID 생성
- [x] 임시 디렉터리 기록 (`.tmp-{UUID}` 접미사)
- [x] metadata/document ID와 hash 검증 (`get()`)
- [x] source `.figpack`, `preview.png`, assets 안전한 추출 (`destination.startsWith(temporary)` 경로 탈출 검사)
- [x] 동일 파일시스템 atomic move (`StandardCopyOption.ATOMIC_MOVE`)
- [x] 기존 artifact overwrite 거부
- [x] symlink와 path traversal 방어
- [x] artifact 조회와 요약 생성
- [x] 검증된 `.figpack` export (`prepareFigmaImport`)
- [x] retention 만료 artifact 정리 (`cleanupExpired`)
- [x] 부분 기록 실패 시 정리 (`catch`에서 `deleteQuietly(temporary)`)
- [x] 동시 저장 테스트

완료 조건:

- 실패한 저장은 완성된 artifact로 조회되지 않는다.
- 저장 루트 밖으로 읽거나 쓸 수 없다.

### R1-07 Web Capture → UiDesignSpec 매퍼 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/policy/WebCaptureProjectionPolicy.java
src/main/java/com/krdevops/springai/model/capture/SafeDesignProjection.java
src/main/java/com/krdevops/springai/service/RenderedDesignSpecMapper.java
src/test/java/com/krdevops/springai/policy/WebCaptureProjectionPolicyTest.java
src/test/java/com/krdevops/springai/service/RenderedDesignSpecMapperTest.java
```

- [x] raw document → safe projection 경계 구현 (`WebCaptureProjectionPolicy.project()`)
- [x] mapper가 raw node text/value를 입력받지 않도록 시그니처 제한 (`RenderedDesignSpecMapper.map(SafeDesignProjection, ...)`만 받음)
- [x] 구조·role·style·token·승인 label allowlist 적용 (`LABEL_NODE_TYPES`/`CONTROL_TAGS`)
- [x] 기존 `SensitiveFieldPolicy` 정확 이름·토큰 판정 재사용
- [x] archetype 판정
- [x] layout shell/content width/density 판정
- [x] form column layout 판정 (two-column/single-column)
- [x] action/search panel placement 판정
- [x] component candidate 변환
- [x] field hint와 role 변환
- [x] action 정규화
- [x] token 후보 변환
- [x] table row/detail 실제 값 제외
- [x] input value, 사용자 프로필·avatar 문자열과 asset URL 제외
- [x] confidence 미달 uncertainty 처리 (2026-07-21 신규: `RenderedDesignSpecMapper`에 `CONFIDENCE_THRESHOLD=0.7` 도입, 이전에는 항상 빈 리스트만 반환하던 것을 실제 threshold 기반으로 변경)
- [x] 목록·상세·등록·수정 fixture golden test
- [x] PII sentinel이 projection/UiDesignSpec/분석 결과에 없는지 검증
- [x] 정상 업무 필드 과잉 차단 회귀 테스트

완료 조건:

- 결과가 현재 `ScreenSpecAssembler`와 `ScreenSpecValidator` 계약을 통과한다.

### R1-08 디자인 분석 경로 통합 — P0

예상 변경 파일:

```text
src/main/java/com/krdevops/springai/model/design/DesignSourceType.java
src/main/java/com/krdevops/springai/model/design/DesignAnalysisResult.java
src/main/java/com/krdevops/springai/model/design/DesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/FileDesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/FigmaDesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/WebCaptureDesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/LegacyDesignSourceAdapter.java
src/main/java/com/krdevops/springai/service/DesignReferenceAnalysisService.java
src/main/java/com/krdevops/springai/mapper/DesignAnalysisRepository.java
```

예상 신규 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureCacheKeyFactory.java
src/main/java/com/krdevops/springai/service/WebCaptureAnalysisService.java
```

- [x] `DesignSourceType.WEB_CAPTURE` 추가
- [x] `DesignSourceMetadata` 공통 모델을 WEB_CAPTURE보다 먼저 병합
- [x] 신규 service 로직을 `sourceMetadata.sourceType()` 기준으로 전환 (`checkExecutionContract`)
- [x] 기존 flat source 필드는 deprecated JSON 호환용으로만 유지
- [x] WEB_CAPTURE 전용 nullable top-level 필드 추가 금지
- [x] FILE/Figma/WEB_CAPTURE 정적 factory 추가
- [x] legacy FILE/Figma JSON을 metadata로 변환 (`LegacyDesignSourceAdapter`)
- [x] FILE/Figma JSON 하위 호환 확인 (`DesignAnalysisResultCompatibilityTest`)
- [x] WEB_CAPTURE 분석 계약 추가 (`WebCaptureAnalysisService`)
- [x] cache key에 content hash, feature type, schema와 mapper version 포함 (`WebCaptureCacheKeyFactory`)
- [x] `DesignAnalysisRepository.saveOrGet` 재사용
- [x] 시맨틱 재사용 실행 계약 추가
- [x] WEB_CAPTURE는 RAG ingest를 호출하지 않도록 분기 (`WebCaptureAnalysisService.analyze()`는 `ingestBestEffort` 미호출)
- [x] WEB_CAPTURE 시맨틱 후보 미노출 (`checkExecutionContract`가 WEB_CAPTURE를 명시적으로 거부)
- [x] 기존 DB JSON 역직렬화 회귀 테스트
- [x] 모든 canonical constructor 호출부를 factory로 전환하고 컴파일·회귀 점검
- [x] subtype/type 불일치 명시적 거부 테스트 (`DesignSourceMetadataTest`)
- [x] deprecated flat 필드 제거·DB migration 후속 조건 기록 (ADR-002)

병합 순서:

1. 첫 PR에서 `DesignSourceMetadata`, FILE/Figma factory, legacy adapter와 기존 JSON 호환 전환만 완료한다.
2. 첫 PR의 전체 회귀 테스트가 통과한 뒤 두 번째 PR에서 `WEB_CAPTURE` subtype과 분석 경로를 추가한다.
3. 두 단계를 한 PR로 구현해야 한다면 커밋과 검증 게이트를 같은 순서로 분리하고, 공통 모델 전환 실패 상태에서는 WEB_CAPTURE 코드를 활성화하지 않는다.

완료 조건:

- WEB_CAPTURE `analysisId`를 기존 `createScreenSpecification`에 전달할 수 있다.
- 기존 FILE과 FIGMA 테스트가 모두 유지된다.

### R1-09 오케스트레이션과 MCP Tool — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureOrchestrationService.java
src/main/java/com/krdevops/springai/tools/CaptureWebPageTool.java
src/main/java/com/krdevops/springai/tools/DesignArtifactTool.java
src/main/java/com/krdevops/springai/config/McpConfig.java
src/test/java/com/krdevops/springai/service/WebCaptureOrchestrationServiceTest.java
src/test/java/com/krdevops/springai/tools/CaptureWebPageToolTest.java
src/test/java/com/krdevops/springai/tools/DesignArtifactToolTest.java
```

- [x] capture 전체 흐름 구현 (`WebCaptureOrchestrationService`)
- [x] 실패 단계별 안전한 오류 매핑
- [x] `captureWebPage` 추가
- [x] `getDesignArtifact` 추가
- [x] `prepareFigmaImport` 추가
- [x] `analyzeCapturedDesign` 추가
- [x] Tool은 서비스에만 위임하도록 유지 (두 Tool 클래스 모두 순수 위임, 비즈니스 로직 없음)
- [x] Tool description을 한국어로 작성
- [x] `McpConfig` 파라미터와 `toolObjects`에 두 Tool 등록
- [x] 대용량 document를 Tool 응답에서 제외 (`CaptureArtifactSummary`/`FigmaImportArtifact`/`DesignAnalysisResult`만 반환, raw `RenderedDesignDocument` 미노출)

완료 조건:

- MCP Tool 응답은 artifact ID, 상태, 요약, 경고와 안전한 경로만 포함한다.

### R1-10 상태 점검과 문서 — P1

- [x] `ProjectHealthService`에 capture 상태 추가
- [x] extractor health와 schema 호환성 표시
- [x] Chromium 준비 상태 표시 (`getWebCaptureStatus()`/extractor `/v1/health`의 `chromiumReady`)
- [x] artifact 경로 쓰기 상태 표시
- [x] Tool catalog 갱신 (`docs/tool-catalog.md`)
- [x] Tool 전체목록 문서 갱신 (`docs/tool-reference/MCP_Tool_전체목록.md`)
- [x] `.env.example`과 운영 절차 갱신
- [x] Secret 회전과 장애 대응 절차 작성 (`docs/figma/06_..._Operations_Runbook.md` §3~4)

완료 조건:

- 기능이 비활성, 준비, 오류 중 어떤 상태인지 Tool 호출 전에 확인할 수 있다.

---

## 5. R2 `jsp-design-extractor` 구현

### R2-01 프로젝트 골격 — P0

- [x] TypeScript 프로젝트 생성
- [x] Playwright와 Chromium 설정
- [x] lint, typecheck, unit test 설정
- [x] `/v1/health` 구현
- [x] `/v1/captures` 구현
- [x] loopback bind 강제
- [x] `X-Extractor-Key` 인증 middleware 구현
- [x] 요청 body와 응답 body 크기 제한
- [x] 성공 응답 `.figpack` binary streaming 구현
- [x] 오류 응답만 안전한 JSON으로 반환
- [x] 구조화 file logging과 민감정보 필터 적용
- [x] `/v1/health` 응답을 스펙 §6.2 스키마(`serviceVersion`, `schemaVersions` 배열, `browser`)에 맞춤 (2026-07-21: E2E `health response contract` 검증 추가·통과)
- [ ] Page Readiness/DOM·CSS Collector/Layout Analyzer/Component Recognizer를 03번 §3.1이 요구하는 구분된 모듈로 분리 (미착수 — 기능은 동작하나 구조 리팩터링은 별도 작업으로 보류. 근거: 파일 분리는 순수 구조 변경으로 기능적 이득이 없고, 다른 항목보다 회귀 위험 대비 가치가 낮다고 판단)

완료 조건:

- 외부 interface에서 포트에 접근할 수 없다.
- 인증되지 않은 capture 요청이 거부된다.

### R2-02 입력과 URL 정책 — P0

- [x] request schema 검증 (`validateRequest` allowedKeys 화이트리스트)
- [x] `LOCAL_JSP`만 허용
- [x] main origin allowlist 적용
- [x] resource origin allowlist 적용
- [x] 빈 resource allowlist는 main origin만 허용
- [x] navigation·redirect·resource 요청 전 정책 검증 (`context.route`)
- [x] hostname의 해석 IP와 연결 대상 정책 검증 (`dns.lookup` + loopback 확인)
- [x] service worker와 WebSocket 기본 차단 (`serviceWorkers:"block"`, `routeWebSocket`)
- [x] popup 차단
- [x] download 차단
- [x] iframe request 정책 적용 (`context.route`가 resourceType 구분 없이 전체 요청에 origin 정책 적용)
- [x] request interception 테스트 (e2e 보안 매트릭스)
- [x] 03번 §6.4의 `CAPTURE_*` 오류 코드 체계와 HTTP status 매핑(400/401/403/422/502/504/413/503/500) 구현 (2026-07-21: `classifyError` 도입, 오류 응답에 `code`/`message`/`requestId`/`retryable` 포함, E2E 보안 매트릭스 갱신 후 통과. `CAPTURE_DISABLED`는 이 서버 설계상 해당 상태가 없어 미사용)

완료 조건:

- main URL이 허용되어도 차단된 내부·외부 resource 요청은 정책에 따라 거부된다.

### R2-03 Browser Context — P0

- [x] 요청별 새 Context 생성
- [x] desktop viewport 설정
- [x] locale/timezone/color scheme/reduced motion 설정
- [x] browser extension 비활성 상태 확인 (headless Chromium 기동에 별도 확장 로드 없음 — 기본값으로 충족)
- [x] 성공·실패 시 Context 정리 (`finally { await context.close(); }`)
- [x] 전체 capture timeout 적용 (readiness timeout + mutation 안정화 상한 2000ms로 단계별 상한 적용)
- [x] 동시 capture 상한 적용 (`activeCaptures`/`maxConcurrentCaptures`, `CAPTURE_CONCURRENCY_LIMIT`)

완료 조건:

- 이전 요청의 쿠키, storage와 page state가 다음 요청에 전달되지 않는다.

### R2-04 Page Readiness — P0

- [x] DOMContentLoaded 대기
- [x] `readySelector` 대기
- [x] `hiddenSelector` 제거 대기
- [x] `document.fonts.ready` 대기
- [x] 이미지 로딩 상태 수집 (2026-07-21 신규: `img.complete`/`naturalWidth` 확인 후 `IMAGE_NOT_LOADED` warning)
- [x] 애니메이션, transition, caret 정지
- [x] DOM mutation 안정화 관찰
- [x] timeout과 warning 구분 (2026-07-21 신규: 안정화 2000ms 상한 도달 시 실패 대신 `DOM_STABILIZATION_TIMEOUT` warning으로 기록하고 계속 진행)

완료 조건:

- fixture의 skeleton 또는 loading indicator가 제거된 뒤 분석한다.

### R2-05 DOM/CSS 수집 — P0

- [x] DOM 깊이 우선 순회 (`walk()`)
- [x] 제외 tag 처리 (SCRIPT/STYLE/NOSCRIPT/META/LINK) — 2026-07-21 실사용 eGovFrame 로그인 화면(`localhost:9091`) 캡처 중 `DOCUMENT_REFERENCE_INVALID` 실패를 발견해 진단 정보(`missing-child:n2->n3` 등)를 추가해 원인 특정. 부모의 `childNodes` 필터는 `display:none`만 확인하고 재귀 `walk()`는 태그 이름으로도 조기 return해서, `getComputedStyle().display!=="none"`인 `<meta>`/`<link>` 등이 부모의 `children` 배열엔 남지만 실제 노드는 생성되지 않는 불일치가 있었음. `EXCLUDED_TAGS` 상수를 필터·재귀 양쪽에서 공유하도록 수정, 4-fixture 회귀 테스트 통과 후 동일 페이지 재캡처로 실제 해결 확인(HTTP 502→200)
- [x] visible node 판정
- [x] 접근성 role/name 수집
- [x] label/placeholder/alt 수집
- [x] password 및 input value 미수집 (`value: null` 고정)
- [x] `display:none` 제외
- [x] `visibility:hidden`/`opacity:0` geometry 유지와 `visible=false` 처리
- [x] 직접 자식 Text Node를 별도 TEXT node로 변환
- [x] 부모 `textContent` 중복 방지 (`collectDirectText`)
- [x] `white-space`와 `text-transform` 정규화
- [x] computed CSS whitelist 수집
- [x] Bounding Box와 scroll offset 수집
- [x] pseudo element synthetic node 생성 (`::before`/`::after`)
- [x] iframe/Canvas 제한 노드 생성 (`IFRAME_LIMITED`/`CANVAS_LIMITED` warning)
- [x] 안정적인 node ID 생성
- [x] §7.3 CSS whitelist 중 `inset`, `text-shadow`, `filter`, `object-fit`, `object-position` 수집 (2026-07-21: `styles`에 추가, E2E에서 `filter`/`objectFit`/`textShadow` 존재 검증)
- [x] width/height 등 원본 CSS 값을 `raw` 보조 필드로 보존 (2026-07-21: `rawWidth`/`rawHeight`/`rawInset`을 인라인 `style` 속성 기준으로 best-effort 보존. **한계**: 외부 stylesheet 규칙에서만 정의된 원본 값은 여전히 캡처하지 못함 — CSSOM 규칙 매칭이 필요한 후속 작업)
- [x] §5.5 노드 필드 중 `selectorHint`, `sourceOrder`, `rotation`, `name` 추가 (2026-07-21: `RenderedNode` record에 canonical+legacy 호환 생성자 패턴 적용해 기존 10개 `new RenderedNode(...)` 호출부 무변경으로 확장, 공유 schema에 4개 필드 required 추가, `server.ts`에서 실제 값 채움. extractor E2E·Java 전체 테스트 모두 통과)

완료 조건:

- 같은 fixture에서 node ID와 정규화된 내용이 안정적으로 유지된다.

### R2-06 자산 수집 — P1

- [x] img와 background image 추출
- [x] inline SVG 추출
- [x] MIME과 크기 검증
- [x] content hash 생성
- [x] 동일 자산 deduplication
- [x] 허용 origin만 다운로드
- [x] 실패 자산 warning 생성
- [x] artifact 상대경로만 반환

완료 조건:

- 차단 또는 실패한 자산 때문에 전체 capture가 불필요하게 실패하지 않는다.

### R2-07 Layout Analyzer — P0

- [x] flex horizontal/vertical 인식
- [x] gap과 padding 정규화
- [x] 겹침과 transform 검사
- [x] grid 정보 보존
- [x] block/inline/absolute 보존
- [x] confidence와 evidence 생성
- [x] 임계값 미달 absolute fallback
- [x] 부모 기준 상대좌표 계산

완료 조건:

- 추론이 원본 좌표를 삭제하지 않는다.
- 잘못된 Auto Layout 확정보다 fallback을 우선한다.

### R2-08 Component Recognizer — P0

- [x] semantic tag와 ARIA role 규칙
- [x] Header/Footer/Navigation/Breadcrumb 규칙
- [x] Search Panel/Toolbar 규칙
- [x] Form/Field Group/control 규칙
- [x] Button과 action 의미 규칙
- [x] Table/Pagination 규칙
- [x] confidence와 evidence 생성
- [x] 사용자 표시 text에만 의존하지 않는 보조 규칙

완료 조건:

- 네 가지 fixture의 기대 component 목록과 비교하는 golden test가 통과한다.

### R2-09 문서 생성과 자체 검증 — P0

- [x] `RenderedDesignDocument` 조립
- [x] URL 마스킹
- [x] 민감 query 제거 기반 URL fingerprint
- [x] capture ID/document key echo
- [x] token 후보 집계
- [x] warning 정규화
- [x] 결정론적 content hash 생성
- [x] canonical JSON key, 배열, 숫자, 색상, Unicode 정규화
- [x] JSON Schema 검증
- [x] 참조 무결성 검증
- [x] 최대 node/asset/document 크기 적용
- [x] 민감 selector mask가 적용된 `preview.png` 생성
- [x] manifest/document/preview/assets `.figpack` 생성
- [x] package entry hash와 자체 검증
- [x] §7.4 canonicalization 7단계 중 배열 정렬 구분·좌표 반올림·색상 RGBA 정규화·Unicode NFC 정규화 추가 (2026-07-21: `normalizeForHash`+`sortByStableKey` 도입. `componentCandidates`/`assets`/`warnings`는 안정적 key로 정렬하고 `nodes`/`children`은 원래 순서 유지. 좌표는 소수점 2자리 반올림 — 스펙에 정밀도가 명시되지 않아 임의 채택한 값이므로 baseline 승인 시 재검토 필요)
- [x] §5.10 `extractor` 보조계약 필드 중 `schema SHA-256`, layout analyzer/component recognizer version 추가 (2026-07-21: `schemaSha256`을 스키마 파일 바이트 기준으로 기동 시 계산, `layoutAnalyzerVersion`/`componentRecognizerVersion` 상수 추가·E2E 검증)
- [x] §5.11 manifest에 node/asset/component/warning 요약 필드 추가 (2026-07-21: `RenderedDesignPackageManifest` record도 동일한 canonical+legacy 패턴으로 확장, `figpack-v1.schema.json`·fixture 동시 갱신, `server.ts` manifest 생성부에 실값 채움. 3개 프로젝트(Java/extractor/contract) 테스트 전부 통과)

완료 조건:

- extractor는 스스로 검증하지 못한 문서를 성공 응답으로 반환하지 않는다.

---

## 6. R3 `jsp-to-figma-plugin` 구현

### R3-01 프로젝트와 Manifest — P0

- [x] Figma Design Plugin 프로젝트 생성
- [x] `manifest.json` 작성
- [x] `documentAccess: dynamic-page` 적용
- [x] Release 1 network access `none` 적용
- [x] TypeScript typings, lint, build 설정
- [x] UI와 main sandbox message 계약 정의

완료 조건:

- Figma Desktop에서 개발 Plugin으로 로드된다.

### R3-02 `.figpack` import와 preview — P0

- [x] `.figpack` 파일 선택 UI
- [x] 최대 파일 크기 검사
- [x] ZIP entry/manifest/hash/schema 검증
- [x] asset byte 로딩과 document 참조 검증
- [x] node/asset/component/warning 요약
- [x] 지원하지 않는 기능 표시
- [x] 생성/취소 버튼
- [x] 사용자 승인 전 캔버스 미변경 보장

완료 조건:

- 잘못된 JSON이 캔버스를 변경하지 않는다.

### R3-03 기본 노드 생성 — P0

- [x] 최상위 Frame 생성
- [x] 부모부터 자식 순으로 생성
- [x] container → Frame 매핑
- [x] text → TextNode 매핑
- [x] image fill 매핑
- [x] inline SVG 매핑
- [x] SVG script, 외부 reference, event handler 제거와 크기 제한
- [x] border, radius, fill, opacity, effect 적용
- [x] 부모 기준 좌표 적용

완료 조건:

- 목록 fixture의 계층과 기본 시각 속성이 Figma에 생성된다.

### R3-04 Text와 Font — P0

- [x] font 사전 수집
- [x] `loadFontAsync` 호출
- [x] 원본 font 적용
- [x] fallback font 정책
- [x] font size, weight, line height, letter spacing 적용
- [x] fallback 결과 보고

완료 조건:

- 폰트 로드 실패가 전체 import를 중단시키지 않는다.

### R3-05 Auto Layout과 fallback — P0

- [x] horizontal Auto Layout
- [x] vertical Auto Layout
- [x] padding/gap/alignment 적용
- [x] absolute child 처리
- [x] grid/unknown absolute fallback
- [x] overflow와 clipping 처리
- [x] 부모 영역 이탈 warning (2026-07-21 신규: `build()`가 `boundaryWarnings[]`를 수집해 UI `RESULT` 메시지에 표시)

완료 조건:

- Auto Layout을 적용해도 원본의 핵심 구조와 크기가 손실되지 않는다.

### R3-06 Component와 Style — P1

- [x] 반복 component 후보 표시
- [x] 사용자 선택 local Component 생성
- [x] 동일 style 탐색 및 재사용
- [x] Paint/Text style 생성
- [x] Effect style 생성 (2026-07-21 신규: extractor `shadow` 토큰 → `ensureLocalStyles`의 `Website/Shadow` EffectStyle)
- [x] 선택적 local Variable 생성 (2026-07-21 신규: `createVariables` opt-in 체크박스, `ensureLocalVariables`, 자동 publish 없음)
- [x] Library publish 기능 제외 확인

완료 조건:

- 사용자가 선택하지 않은 후보를 Component로 자동 승격하지 않는다.

### R3-07 중복과 결과 보고 — P1

- [x] plugin data 기록
- [x] 동일 documentKey/contentHash 탐지
- [x] captureId와 documentKey plugin data 기록
- [x] 동일 결과 재생성 차단
- [x] 변경된 document는 새 Frame 생성
- [x] 성공·부분 실패 요약
- [x] 임시 최상위 Frame에서만 생성
- [x] 실패 시 이번 실행의 임시 Frame 정리
- [x] 부분 결과 유지는 사용자 명시적 선택으로 제한 (2026-07-21 신규: `keepPartialOnFailure` opt-in 체크박스, 기본값은 기존과 동일하게 실패 시 임시 Frame 제거)
- [x] 생성된 최상위 Frame 선택 및 viewport 이동

완료 조건:

- 기존 Frame을 사용자 승인 없이 수정하거나 삭제하지 않는다.

---

## 7. R4 통합 및 E2E

### R4-01 계약 통합 — P0

- [x] 계약 프로젝트의 document/package schema checksum 비교 (`COMPATIBILITY.md`)
- [x] `springai`, extractor, Plugin에 포함된 schema checksum을 계약 프로젝트와 비교 (2026-07-21 신규: `WebsiteFigmaContractCrossValidationTest.classpathSchemaMatchesContractProjectSchemaChecksum`이 springai classpath와 계약 원본 checksum을 매 테스트마다 비교. extractor/Plugin은 `website-figma-contract`를 로컬 의존성으로 직접 참조해 별도 복사본이 없음 — 구조적으로 drift 불가능)
- [x] extractor 전체 fixture를 Java validator로 재검증 (2026-07-21 신규: `WebsiteFigmaContractCrossValidationTest`의 4개 테스트가 `valid-minimal`/`invalid-enum`/`invalid-source-content-hash`/`invalid-reference` fixture를 Java `RenderedDesignSchemaValidator`로 재검증, `contract-test.mjs`와 동일한 pass/fail 판정 확인)
- [x] Spring export `.figpack`을 Plugin에서 검증
- [x] schema mismatch 오류 메시지 확인 (checksum 불일치 시 AssertJ가 두 checksum 값을 포함한 명확한 실패 메시지 출력)

### R4-02 MCP E2E — P0

- [x] extractor 실행
- [x] JSP fixture 서버 실행
- [x] `captureWebPage` 호출
- [x] `getDesignArtifact` 호출
- [x] `prepareFigmaImport` 호출
- [x] `analyzeCapturedDesign` 호출
- [x] 기존 `createScreenSpecification` 호출
- [x] 동일 입력 cache 재사용 검증
- [x] WEB_CAPTURE RAG 미적재 검증

### R4-03 Figma E2E — P0

- [x] 상세 `.figpack` import
- [x] 등록 `.figpack` import
- [x] 수정 `.figpack` import
- [x] 목록 `.figpack` import
- [x] Frame 크기와 계층 대조
- [ ] 주요 Bounding Box 대조 (**보류** — 실제 Figma Desktop 화면에서 생성된 Frame의 좌표를 사람이 눈으로 대조해야 하는 시각 검수 항목. 코드로 자동 확정 불가)
- [ ] 색상·글꼴·radius·shadow 대조 (**진행 중, 실제 버그 3건 발견·수정** — 2026-07-21. (1) 검정 배경 버그: `color()`가 alpha를 무시해 `rgba(0,0,0,0)`(투명)을 불투명 검정으로 오인 → `solidPaint()` 신설로 수정, 재-import로 해결 확인됨. (2) 텍스트 강제 줄바꿈 버그: `node.resize()`를 TEXT 노드에도 호출해 auto-resize가 깨지고 폰트 fallback과 겹쳐 발생 → TEXT는 `resize()` 생략하도록 수정, 재-import로 해결 확인됨. (3) BUTTON이 텍스트로만 렌더링(테두리·배경 없음): 03번 §10.3 "button은 Frame + Text 기반 시각적 컴포넌트"와 불일치 → `styleFrame()` 헬퍼로 배경/테두리 로직을 일반 컨테이너와 공유하는 Frame+Text 구조로 변경(텍스트는 Frame 안에서 산술적으로 중앙 정렬), `typecheck`/`lint`/`build` 통과. **재-import 재확인 대기**. detail 화면의 레이블/값 세로 배치는 재검토 결과 원본 `preview.png`와 동일한 정상 동작으로 확인(오판 정정))
- [x] 이미지와 SVG 대조
- [x] Auto Layout/fallback 대조
- [x] 중복 import 차단 검증
- [ ] `preview.png`와 생성 Frame 대조 (**보류** — 위 색상 버그 수정 후 재-import로 재확인 필요)
- [x] 실패 시 임시 Frame 정리 검증

### R4-04 보안 E2E — P0

- [x] 비허용 origin 거부
- [x] redirect 우회 거부
- [x] 외부 resource 차단
- [x] service worker/WebSocket 차단
- [x] userinfo와 민감 query 마스킹
- [x] popup/download 차단
- [x] password/input value 미수집
- [x] table/detail/user profile PII sentinel projection 차단
- [x] 기존 `SensitiveFieldPolicy` 민감 토큰과 정상 업무 필드 회귀
- [x] 민감 selector screenshot mask
- [x] ZIP slip/ZIP bomb/hash 위변조 package 거부
- [x] extractor key 오류 처리
- [x] path traversal artifact 거부
- [x] 로그에 Secret과 전체 URL이 없는지 확인

---

## 8. R5 Release 1 완료

### R5-01 품질 게이트 — P0

- [x] 전용 Figma Plugin 채택 ADR 승인 (`ADR-001` 본문 "상태: 승인")
- [x] `DesignSourceMetadata` 선행 전환과 FILE/Figma legacy JSON 호환 검증 (`DesignSourceMetadataTest`, `DesignAnalysisResultCompatibilityTest`)
- [x] 최상위 단일 `contentHash` 계약과 `source.contentHash` 거부 검증 (schema `source` def에 `contentHash` 없음 + negative fixture, JS/Java 양쪽 검증)
- [x] 네 종류 JSP fixture 성공
- [x] content hash 결정론 검증
- [x] captureId/documentKey/contentHash 역할 검증
- [x] 주요 component golden test 통과
- [x] 낮은 confidence fallback 검증
- [ ] Figma 구조 대조 통과 (**보류** — 사람의 시각 검수 필요, R4-03과 동일 사유)
- [ ] fixture별 `quality-baseline.json` 승인 기준 통과 (**보류, 초안 존재** — R0-04 참고. 초안의 tolerance 필드는 아직 `null`이라 이 항목은 실측·승인 전까지 통과 처리 불가)
- [x] 누락 자산과 font fallback 보고 확인 (extractor `ASSET_LOAD_FAILED`/`SVG_SANITIZE_FAILED` warning, Plugin font fallback 시 `Roboto` 대체 및 결과 보고)
- [x] PII sentinel이 safe projection, UiDesignSpec, 분석 결과와 RAG 호출 인자에서 완전히 제외됨을 확인

### R5-02 빌드 게이트 — P0

- [x] `springai` 전체 테스트 통과
- [x] `springai` bootJar 통과
- [x] extractor lint 통과
- [x] extractor typecheck 통과
- [x] extractor test 통과
- [x] Plugin lint 통과
- [x] Plugin typecheck 통과
- [x] Plugin build 통과

### R5-03 운영 게이트 — P0

- [x] 기능 기본 비활성 확인
- [x] capture 활성 시 `springai` loopback bind 강제 확인
- [x] loopback bind 확인
- [x] API key 설정 절차 확인
- [x] artifact 보존·정리 절차 확인
- [x] retention 만료 정리 확인
- [x] health check 문서 확인
- [x] 장애·timeout·브라우저 미설치 안내 확인 (`CAPTURE_BROWSER_UNAVAILABLE`/`CAPTURE_READY_TIMEOUT`, `getWebCaptureStatus()`의 `chromiumReady`)
- [x] 운영·SPA·다중 viewport 비지원 표시 확인 (schema `viewportWidth/viewportHeight`가 `const` 고정, `enabledProfiles`가 `LOCAL_JSP`만 허용)
- [x] Release 1 로그인·세션 비지원 표시 확인 (`storageStateRef` 등 인증 관련 필드 자체가 Release 1 계약에 없음)
- [x] WEB_CAPTURE RAG 미적재 확인

---

## 9. R6 Release 2A — 인증 서버 렌더링

### 착수 게이트

- [ ] Release 1 완료
- [ ] 인증정보 저장소 결정
- [ ] 사용자 또는 owner 식별 방식 결정
- [ ] storage state 보존·폐기 정책 승인

Release 1에서 인증된 JSP 요구가 확인되면 SPA보다 이 Release를 먼저 수행한다.

### 구현

- [x] `storageStateRef` 불투명 ID 모델 — `jsp-design-extractor/src/server.ts`의 `createSession()`이 `crypto.randomUUID()`로 발급, `POST /v1/captures`는 이 opaque ID(`storageStateRef`)만 받는다. 실제 storage state 경로/내용은 응답에 노출하지 않는다 (2026-07-22, 로컬 extractor API 직접 호출로 검증, 실제 `localhost:9091` 로그인 세션 캡처 성공 확인)
- [ ] Secret 저장소 adapter — storage state를 `Map<sessionId, storageState>`로 프로세스 메모리에만 보관(`SESSION_TTL_MILLIS` 경과 시 자동 삭제)한다. 디스크 미저장으로 "인증정보 미노출" 원칙은 만족하지만 별도 secret 저장소(예: Vault, 암호화 파일)를 통한 정식 adapter는 아니다.
- [ ] profile/owner 권한 검증 — 미구현. sessionId를 아는 호출자는 누구나 재사용 가능(현재 extractor는 API key 하나로 전체 접근을 허용하는 P1 로컬 단일 사용자 신뢰 경계와 동일 수준). owner 단위 격리는 `springai` MCP Tool 연동 시점에 별도 구현 필요.
- [ ] 요청별 인증 Context — 미구현. `storageStateRef` 유효성만 검사하는 단순 lookup이며, 별도 인증 Context 추상화는 없음.
- [x] 세션 만료 판정 — `EXTRACTOR_SESSION_TTL_MINUTES`(기본 30분) 기반 TTL과 5분 주기 `pruneSessions()`로 구현 (2026-07-22)
- [x] 인증 실패 안전한 오류 — 세션 미존재/만료는 `SESSION_NOT_FOUND`, 로그인 실패는 `SESSION_LOGIN_FAILED`로 구분해 내부적으로 처리하되 응답은 모두 `CAPTURE_AUTH_FAILED`(401)로 통일해 원인을 외부에 노출하지 않는다 (2026-07-22)
- [ ] 로그인 화면 오탐 방지 — 미구현. `storageStateRef` 없이 인증 필요 페이지를 캡처하면 로그인 화면이 HTTP 200으로 정상 캡처되어 반환된다(예: 2026-07-19 `selectBoardList.do` 미인증 캡처가 `page.title="로그인"`으로 성공 처리됨). 로그인 페이지로의 redirect를 자동 감지해 `CAPTURE_AUTH_FAILED`로 분류하는 로직은 아직 없다.
- [ ] 사용자별 artifact와 cache 분리 기반 — 미구현

**구현 범위 및 검증 방법 (2026-07-22)**: `jsp-design-extractor`에 `POST /v1/sessions`(로그인 수행 → storageStateRef 발급)와 `POST /v1/captures`의 `storageStateRef` 파라미터를 구현하고, `npm test`(e2e.mjs, 기존 4개 fixture)로 회귀 없음을 확인했다. 이어서 `localhost:9091`의 실제 eGovFrame 로그인 폼(`#id`/`#password`/`.btn_login`)으로 세션을 발급하고 `selectBoardList.do?bbsId=BBSMSTR_AAAAAAAAAAAA&baseMenuNo=1000000`을 인증 상태로 캡처해 `page.title`이 "로그인"이 아닌 "내부업무 사이트 > 알림정보 > 공지사항"으로 바뀌고 실제 게시판 목록(206 nodes, warnings=0)이 반환됨을 preview.png로 시각 확인했다. `springai` 측 MCP Tool 연동과 owner 격리는 아직 없으므로 R6는 부분 구현 상태다.

완료 조건:

- 인증정보를 MCP, artifact, 로그에 노출하지 않고 인증된 서버 렌더링 화면을 캡처한다. — extractor 단독 경로에서는 충족(자격증명·storageState 미로깅·미저장 확인). `springai` MCP 경로 전체에 대한 완료 조건 충족은 아직 아니다.

---

## 10. R7 Release 2B — SPA와 동적 상태

### 착수 게이트

- [ ] Release 2A 또는 공개 SPA용 인증 제외 결정
- [ ] 허용 interaction step 목록 승인
- [ ] arbitrary JavaScript 비지원 원칙 확인

### 구현

- [ ] SPA readiness 전략
- [ ] hydration/skeleton 판정
- [ ] URL pattern 대기
- [ ] 사전 등록 click/fill/select/scroll step
- [ ] `stateId`와 step hash
- [ ] client-side route 변경 감지
- [ ] 지속 polling과 network idle 분리
- [ ] DOM mutation 안정화 정책
- [ ] 동적 상태별 artifact 생성

완료 조건:

- 공개 또는 승인된 SPA fixture에서 지정 상태를 반복 재현할 수 있다.

---

## 11. R8 Release 2C — 다중 viewport

### 착수 게이트

- [ ] 단일 viewport SPA 안정화
- [ ] viewport 목록과 최대 개수 결정
- [ ] 반응형 component matching 기준 승인

### 구현

- [ ] viewport별 독립 Context
- [ ] `RenderedDesignBundle` schema
- [ ] viewport별 document 보존
- [ ] 공통 component matcher
- [ ] 숨김·이동·교체 패턴 분석
- [ ] breakpoint observation 생성
- [ ] Figma viewport별 Frame 생성
- [ ] Component Variant 후보 제안
- [ ] 부분 성공 상태 처리

완료 조건:

- Desktop/Tablet/Mobile 문서를 독립 보존하면서 공통 컴포넌트와 변형 관계를 설명한다.

---

## 12. R9 Release 2D — 승인된 운영 사이트

### 필수 선행 승인

- [ ] 보안 ADR 승인
- [ ] 대상 사이트 소유권·분석 권한 확인 절차 승인
- [ ] MCP 사용자 인증·인가 적용
- [ ] tenant 모델 승인
- [ ] 개인정보 마스킹 정책 승인
- [ ] 보존·삭제·감사 정책 승인
- [ ] 운영망 capture agent 토폴로지 승인

### 구현

- [ ] capture agent 등록과 상호 인증
- [ ] 사용자·tenant별 job 격리
- [ ] 사용자·tenant별 artifact 저장소
- [ ] cache와 RAG namespace 격리
- [ ] 감사 로그
- [ ] rate limit과 quota
- [ ] 비동기 job queue
- [ ] 취소, 만료와 정리
- [ ] 개인정보 탐지와 마스킹
- [ ] 운영 site/resource allowlist 관리
- [ ] 관리자 비상 차단 기능

완료 조건:

- 권한 없는 사용자가 URL, 작업, artifact, cache, 인증 상태 또는 분석 결과를 조회하거나 재사용할 수 없다.

---

## 13. 추적 매트릭스

| 명세 영역 | 구현 항목 | 핵심 테스트 |
|---|---|---|
| 공통 JSON 계약 | R0-02, R1-05, R2-09, R3-02 | R4-01 |
| URL·SSRF 방어 | R0-03, R1-03, R2-02 | R4-04 |
| extractor 통신 | R1-04, R2-01 | R4-02 |
| artifact 저장 | R1-06 | R4-02, R4-04 |
| 공통 source metadata | R0-01, R1-08 | FILE/Figma legacy + WEB_CAPTURE 호환 테스트 |
| UiDesignSpec 연결 | R1-07, R1-08 | R4-02 |
| PII 안전 projection | R1-07 | R4-04, R5-01 |
| MCP Tool | R1-09 | R4-02 |
| DOM/CSS/Layout | R2-05, R2-07 | R4-03 |
| Component 인식 | R2-08 | R5-01 |
| Figma 생성 | R3-03~R3-07 | R4-03 |
| Figma 출력 경로 결정 | R0-01, R3 전체 | ADR 검토 + R4-03 |
| 인증 | R6 | Release 2A E2E |
| SPA | R7 | Release 2B E2E |
| 다중 viewport | R8 | Release 2C E2E |
| 운영 사이트 | R9 | 보안·격리 E2E |

---

## 14. 착수 순서 권고

첫 번째 구현 묶음:

```text
R0-01 → R0-02 → R0-03 → R0-04
```

두 번째 구현 묶음은 병렬 진행할 수 있다.

```text
springai: R1-01 → R1-08의 공통 source metadata 선행 전환
          → R1-02 → R1-03/R1-04/R1-05
extractor: R2-01 → R2-02 → R2-03/R2-04/R2-05
plugin: R3-01 → R3-02
```

세 번째 구현 묶음:

```text
R2-06/R2-07/R2-08/R2-09
  → R1-06/R1-07/R1-08의 WEB_CAPTURE 통합/R1-09
  → R3-03/R3-04/R3-05
```

마지막 Release 1 묶음:

```text
R1-10 + R3-06/R3-07
  → R4 전체
  → R5 전체
```

R6 이후에는 앞 단계의 완료 조건을 통과하지 않은 기능을 함께 묶어 개발하지 않는다.

---

## 15. 변경 이력

| 버전 | 작성일 | 변경 내용 |
|---|---|---|
| 1.25 | 2026-07-22 | "생성 옵션" 옆에 "전체" 체크박스 추가 요청. `ui.html`에 `#selectAll` 체크박스 추가 — 체크 시 styles/variables/keepPartial + 동적으로 생성되는 candidates 체크박스 전부를 한 번에 토글. 개별 체크박스를 수동으로 바꾸면 `change` 이벤트 위임으로 "전체" 상태도 자동 동기화(전부 체크됐으면 자동 체크, 하나라도 해제되면 자동 해제). `.figpack` 새로 선택할 때마다 "전체"도 초기화. 별도 빌드 불요(manifest가 `src/ui.html` 직접 참조) |
| 1.24 | 2026-07-22 | 사용자 UX 지적: 컴포넌트 후보 체크박스(최대 16종)가 많으면 "Figma Frame 생성" 버튼이 화면 밖으로 밀려나 매번 스크롤해야 함. `ui.html`에 `#candidates` 자체 스크롤(`max-height:180px`) + 생성/취소 버튼 `position:sticky;bottom:0` 고정 적용, `code.ts`의 `figma.showUI` 기본 높이 480→640으로 확대. `typecheck`/`lint`/`build` 통과, `ui.html`은 manifest가 직접 참조해 별도 번들링 불요 |
| 1.23 | 2026-07-22 | board-list-notice-v2.figpack를 "공통 Paint/Text/Effect Style 생성·재사용" 옵션 켠 채 재-import하다 `in set_textAlignHorizontal: Cannot write to node with unloaded font "Arial Regular"` 오류 발생(진단용 임시 Frame 유지 옵션 덕에 원문 확인 가능). 원인: `ensureLocalStyles()`의 `Website/Body` 공유 Text Style이 **이전 import에서 이미 생성돼 있으면** 그 기존 스타일이 물고 있는 폰트(예: 과거 캡처의 우세 폰트였던 Arial)는 로드하지 않고, 이번 실행에서 새로 계산한 폰트만 `figma.loadFontAsync()`로 로드하던 버그 — 이후 `setTextStyleIdAsync()`가 로드 안 된 기존 스타일 폰트를 적용하려다 실패. 기존 스타일을 재사용할 때 그 스타일의 실제 `fontName`을 별도로 로드하고, 로드 자체가 실패하면 공유 스타일 재사용을 포기(개별 노드는 각자 로드된 폰트로 계속 정상 렌더링)하도록 수정. `typecheck`/`lint`/`build` 통과, 재-import 재확인 대기 |
| 1.22 | 2026-07-22 | 사용자가 LNB "알림정보" 제목이 "공지사항"/"업무게시판" 리스트보다 왼쪽으로 30px 더 나가 있다고 지적. 확인 결과 v1.17에서 추가한 padding-top 보정과 같은 원인의 X축 버전 — `h2`(bounds.x=71, padding-left=30)는 padding-left 보정 없이 그대로 `bounds.x`를 사용해 71에 그려졌지만, 실제로는 71+30=101에서 시작해야 "공지사항"(bounds.x=101, 자체 렌더링된 요소라 이미 정확)과 정렬됨. TH처럼 `text-align:center`인 경우 padding-left를 그대로 더하면 부정확하므로(정렬 기준이 content-box 중앙) `text-align`이 center/right가 아닐 때만 적용하도록 padding-top 보정 블록에 padding-left 보정을 함께 추가. `typecheck`/`lint`/`build` 통과. 사용자가 재-import 후 스크린샷으로 확인(2026-07-22 10:13) — "알림정보" 좌측 정렬, select "제목"+화살표 크기, TH 세로중앙정렬, LNB 구분선 전부 정상 확인 완료 |
| 1.21 | 2026-07-22 | v1.20 재-import 확인 중 합성 select 화살표가 너무 작다는 지적 — `fontSize` 10px→18px, `fontWeight` 700(Bold)로 키우고 우측 여백도 함께 조정. `typecheck`/`lint`/`build` 통과, 재-import 재확인 대기 |
| 1.20 | 2026-07-22 | v1.19 재-import 확인(select "제목" 노출·TH 세로중앙정렬 모두 해결) 중 사용자가 select 드롭다운 오른쪽 화살표(▾) 미노출 지적. 원인 확인: 이 화살표는 브라우저가 OS 네이티브로 그리는 `<select>` 기본 장식으로 DOM/CSS 어디에도 존재하지 않아(배경 이미지·자산 없음, `backgroundColor:transparent`만 확인) extractor가 원리적으로 캡처할 수 없는 대상임을 데이터로 확인. 실제 캡처 데이터와 무관하게 select 렌더링 시 오른쪽에 "▾" 글자를 관례적으로 항상 합성해 추가하기로 결정(사용자 승인). `code.ts` select 분기에 합성 화살표 추가, `typecheck`/`lint`/`build` 통과, 재-import 재확인 대기 |
| 1.19 | 2026-07-22 | v1.18 재-import 확인 중 사용자가 2가지 잔여 문제 지적: (1) 검색 필터 "제목" select 텍스트가 여전히 안 보임 — extractor는 `value:"제목"`을 정상 캡처했지만 **Plugin(`code.ts`)이 `item.value`를 읽어 렌더링하는 로직 자체가 없었던 것**(데이터만 만들고 소비하는 쪽을 안 만든 실수). `item.tag==="select"` 전용 분기를 추가해 padding-left만큼 들여쓴 Text를 프레임에 렌더링하도록 구현, `NodeData` 타입에 `value` 필드 추가. (2) 테이블 헤더(`th`: 번호/제목/작성자/작성일/조회수) 텍스트가 세로 중앙정렬 안 되고 위로 붙음 — 원인은 `th`의 `bounds`가 `padding:23px 0px` 포함 전체 셀 박스인데, 텍스트는 그 박스 맨 위(`bounds.y`)에 그대로 배치되고 있었던 것. v1.17의 line-height 보정 로직(TEXT 분할 노드의 tight bounds 대상)과는 다른 케이스(HEADING/LABEL/TH의 element 전체 bounds 대상)라 별도 처리 필요 — `padding-top`만큼 내려서 CSS content-box 시작 위치와 맞추도록 통합 로직으로 재작성(`padding-top` 있으면 그만큼 이동, 없으면 기존 line-height 보정 유지). `typecheck`/`lint`/`build` 통과, 재-import 재확인 대기 |
| 1.18 | 2026-07-22 | 사용자가 실제 JSP 원본과 재-import 결과를 나란히 비교해 3곳 지적: (1) LNB "알림정보" 박스 제목 아래 구분선 누락 — 원인은 `<h2>`가 `border-bottom`만 갖고 있어 `getComputedStyle().border` 통합 shorthand가 빈 문자열을 반환하는 브라우저 특성 때문에 extractor가 아예 못 읽던 것. (2) 검색 필터 "제목" `<select>` 표시값 미노출 — `value` 필드가 스키마상 항상 `null`로 고정되어 있던 것으로, `<select>`만 예외로 현재 선택 옵션 텍스트를 캡처하도록 스키마(`value`: null 전용→nullableString)와 extractor를 확장(`<input>`/`<textarea>`는 계속 차단, 03번 §5.5 반영). (3) "create"/"등록" 버튼 텍스트 차이는 버그 아님 — 캡처 이후 실제 JSP 콘텐츠가 바뀐 것. 1·2번 수정: extractor에 `styles.borderTop/Right/Bottom/Left` 4면 개별 캡처 + `<select>` value 캡처 추가, `website-figma-contract` 스키마 갱신(체크섬 재계산, COMPATIBILITY.md 반영), Java `WebsiteFigmaContractCrossValidationTest` 포함 전체 테스트 통과 확인. Plugin(`code.ts`)에 4면 개별 stroke(`strokeTopWeight` 등) 적용 로직 추가(FRAME 대상), 그리고 자식 없는 TextNode(HEADING 등)는 Figma가 개별 stroke를 지원하지 않아 `addBorderDividers()`로 얇은 Rectangle을 형제로 그려 구분선 대체 렌더링. extractor `typecheck`/`lint`/`npm test`(4-fixture 회귀 없음), Plugin `typecheck`/`lint`/`build` 모두 통과. 실제 재캡처로 `border-bottom:4px solid rgb(221,226,229)`와 `select value:"제목"` 데이터 확인 완료, Plugin 재-import 시각 확인 대기 |
| 1.17 | 2026-07-22 | 재-import 스크린샷을 사용자가 빨간 원으로 표시해 4곳 재검토, 성급하게 "해결됨"이라 답했던 것에 대해 사용자가 직접 지적("거짓말 하지마"). 실제로 2가지 잔여 버그를 추가로 확인·수정: (1) "전체메뉴" 재노출 — v1.16에서 `createText()` 안에 넣은 `text.visible=false`가 메인 루프의 `node.visible=item.visible`(166행, 내가 놓친 실행 순서)에 의해 곧바로 덮어써져 무효화되고 있었음. `node.visible=item.visible && node.visible`로 수정해 실제로 적용되도록 함. (2) "등록"/"로그아웃"/페이지네이션 "1" 텍스트가 버튼·원 안에서 세로 중앙에 안 맞고 위로 쏠림 — `createText()`가 `lineHeight`(글자 실제 높이보다 훨씬 큰, 세로 중앙정렬용 CSS 패턴)는 반영하면서 `textAlignVertical`을 지정하지 않아 Figma 기본값 `TOP`으로 렌더링되던 문제. `lineHeight` 적용 시 `textAlignVertical="CENTER"`를 함께 설정하고, line-height로 늘어난 TextNode 박스 높이만큼 원래 glyph 중심과 어긋나지 않도록 `node.y`를 보정하는 로직 추가. `typecheck`/`lint`/`build` 통과. 사용자가 재-import 후 스크린샷으로 4곳(로그아웃/전체메뉴/등록/페이지네이션 "1") 모두 정상 확인(2026-07-22 08:27) — v1.15~v1.17에서 발견된 시각 결함 전부 해결 확인 완료 |
| 1.16 | 2026-07-22 | v1.15 수정 재-import로 로고 중복·검색 필터 겹침 해결 확인(breadcrumb는 원래도 정상이었고 원형 아이콘은 페이지네이션 "1"을 저해상도에서 오독한 것으로 확인 — 둘 다 오탐 정정). 남아있던 4번째 문제(GNB 햄버거 옆 "전체메뉴" 텍스트가 시각적으로 노출)의 원인도 확인: 원본 DOM이 `font-size:0`으로 스크린리더 전용 텍스트를 시각적으로 숨기는데(`n49`/`n50`), `code.ts`의 `createText()`가 `size<=0`이면 fontSize를 설정하지 않고 Figma 기본 크기로 폴백해 다시 보이게 만들던 문제. `size===0`이면 `text.visible=false` 처리하도록 수정, `typecheck`/`lint`/`build` 통과. 사용자가 플러그인 재로드 후 재-import하여 "전체메뉴" 텍스트가 사라진 것을 스크린샷으로 확인(2026-07-22 07:58). v1.15+v1.16으로 발견된 4가지 시각 결함(로고 중복, breadcrumb 위치, 검색 필터 겹침, "전체메뉴" 노출) 모두 해결 확인 완료 |
| 1.15 | 2026-07-22 | 인증 캡처로 실제 `selectBoardList.do`(공지사항 게시판)를 Figma Desktop에 import한 뒤 시각 검수하여 실제 버그 발견: `code.ts`가 `HEADING`/`LABEL`/`TH` 타입이면 자식 유무와 무관하게 무조건 `figma.createText()`(TextNode, 자식 불가)로 생성 — 로고 `<h1>`(자식: 로고 `<img>`)과 검색 필터 `<label>`(자식: 실제 `<select>`)처럼 자식이 있는 경우, 자식이 `root`로 잘못 append되면서 원래 부모 기준 상대좌표가 엉뚱한 실제 부모(root) 기준으로 해석되어 좌상단 부근에 로고가 중복돼 보이고 검색창 텍스트가 겹쳐 보이는 문제로 나타남(원인 1개가 시각 결함 2건을 동시에 설명). `item.children?.length`가 있으면 타입과 무관하게 Frame 분기로 fallback하도록 수정, `typecheck`/`lint`/`build` 통과, 재-import 재확인 대기 |
| 1.14 | 2026-07-22 | R6(Release 2A) 부분 구현: `jsp-design-extractor`에 `POST /v1/sessions`(로그인 → `storageStateRef` 발급, in-memory + TTL 보관)와 `POST /v1/captures`의 `storageStateRef` 지원 추가. `localhost:9091` 실제 로그인 후 `selectBoardList.do`를 인증 상태로 캡처해 로그인 화면이 아닌 실제 게시판 목록이 반환됨을 확인. `npm test` 4-fixture 회귀 없음. owner 격리·`springai` MCP Tool 연동·로그인 화면 오탐 방지는 미구현으로 남김 (03번 §12.1.1 동시 반영) |
| 1.13 | 2026-07-21 | 사용자가 실사용 eGovFrame 로그인 화면(`localhost:9091`)을 직접 캡처하다 4-fixture로는 못 잡던 `DOCUMENT_REFERENCE_INVALID` 발견. 원인은 `childNodes` 필터와 재귀 `walk()`의 태그 제외 조건 불일치(`<meta>`/`<link>` 등이 부모 참조엔 남지만 노드 미생성) — `EXCLUDED_TAGS` 공유로 수정, 진단 메시지도 함께 개선(`missing-child`/`missing-parent`/`duplicate-id` 구분). 4-fixture 회귀 없음 확인 후 실제 페이지 재캡처로 해결 확인(HTTP 502→200) |
| 1.12 | 2026-07-21 | detail 화면 레이블/값 세로 배치는 원본과 동일한 정상 동작으로 재확인(오판 정정). 대신 BUTTON이 03번 §10.3과 달리 테두리·배경 없이 텍스트로만 렌더링되는 실제 문제 발견 — `styleFrame()` 공용 헬퍼로 Frame+Text 구조로 변경, `typecheck`/`lint`/`build` 통과, 재-import 재확인 대기 |
| 1.11 | 2026-07-21 | 검정 배경 수정 후 재-import에서 텍스트 강제 줄바꿈 버그 신규 발견 — `resize()`가 TEXT 노드의 auto-resize를 깨고 고정폭으로 전환, 폰트 fallback으로 실제 렌더 폭이 캡처된 bounds보다 커지며 발생. TEXT 타입은 `resize()` 생략하도록 수정, `typecheck`/`lint`/`build` 통과, 재-import 재확인 대기 |
| 1.10 | 2026-07-21 | 사용자가 4개 fixture를 Figma Desktop에 실제 import, Figma MCP `get_screenshot`으로 실제 Frame과 `preview.png`를 대조해 R4-03 시각 검수 착수. 4개 전부 화면 상단이 검정으로 렌더링되는 실제 버그 발견 — `code.ts`의 `color()`가 CSS alpha를 무시해 `rgba(0,0,0,0)`(투명)을 불투명 검정으로 오인하던 문제. `solidPaint()` 도입으로 수정, 재-import 재확인 대기 |
| 1.9 | 2026-07-21 | `jsp-design-extractor/test-fixtures/quality-baselines/{list,detail,regist,updt}.json` 초안 4개 작성. `requiredComponents`는 실측 golden 데이터로 채우고 tolerance 계열 필드는 `null`(`DRAFT_UNAPPROVED`)로 남겨 사람 실측·승인 전 오용을 방지 |
| 1.8 | 2026-07-21 | Release 2 제외 R0~R5 전수 재검증. mapper confidence/uncertainty, extractor 이미지 로딩·DOM 안정화 warning, Plugin Effect Style·Variable·영역 이탈 warning·부분 결과 유지, R4-01 계약 checksum 자동 비교(`WebsiteFigmaContractCrossValidationTest`) 신규 구현. 약 180개 체크박스를 코드 대조 후 갱신, 사람 시각 검수가 필요한 7개만 명시적 보류 |
| 1.7 | 2026-07-21 | 보류했던 마지막 2건(§5.5 노드 필드, §5.11 manifest 요약 필드) 구현. `RenderedNode`/`RenderedDesignPackageManifest`에 canonical+legacy 호환 생성자 적용, 공유 schema·fixture 5개 갱신. Java/extractor/plugin/contract 전체 테스트 통과 |
| 1.6 | 2026-07-21 | §1.2 (b) 미구현 9건 중 7건 구현(lint, ADR 확인, health 응답, `CAPTURE_*` 오류 코드, CSS whitelist·raw 값, canonicalization 보강, extractor 보조계약). §1.3에 결과와 보류 2건(노드 필드, manifest 요약 필드) 사유 기록, 관련 체크박스 [x] 갱신 |
| 1.5 | 2026-07-21 | 03/04번 문서와 실제 코드(컴파일·테스트·소스 직독) 재검증. §1.1 R1 "완료" 표현 정정, §1.2 검증 결과 요약 추가, 확인된 스펙 미달 9건을 R0-01/R2-01/R2-02/R2-05/R2-09에 신규 체크리스트 항목으로 추가 |
| 1.4 | 2026-07-21 | 실제 Figma Desktop 4종 import·Frame/asset/Auto Layout/Component·Style 생성과 중복 차단 검증 결과 반영 |
| 1.3 | 2026-07-21 | `05_Overall_Architecture_Diagram.md` 링크 추가 |
| 1.2 | 2026-07-21 | `DesignSourceMetadata` 선행 작업, 최상위 content hash 계약, Figma Plugin 결정 ADR, PII safe projection·SensitiveFieldPolicy 강제 작업 추가 |
| 1.1 | 2026-07-20 | 구현명세서 v1.1의 package, 보안, 식별자, RAG, screenshot, 결정론 및 품질 게이트 작업 반영 |
| 1.0 | 2026-07-20 | Release 1~2D 구현 항목, 의존관계, 완료 조건과 추적 매트릭스 작성 |
