# Website → Figma 단계별 구현명세서

**문서명**: 03_Website_To_Figma_Implementation_Specification.md  
**버전**: 1.6  
**작성일**: 2026-07-22  
**상태**: 구현 제안  
**기준 문서**: `02_JSP_Website_Phased_Development_Impact_Assessment.md`  
**관련 문서**: `05_Overall_Architecture_Diagram.md` (01~04 통합 아키텍처 개요도)

---

## 1. 목적

본 문서는 실행 중인 웹페이지를 브라우저로 분석하여 정규화된 디자인 문서를 만들고, 이를 Figma Design Template과 기존 `UiDesignSpec` 기반 코드 생성 흐름에 연결하기 위한 구현 계약을 정의한다.

개발 단계는 다음과 같다.

- **Release 1**: 허용된 로컬·개발 JSP 화면, 서버 렌더링, 단일 viewport
- **Release 2A**: 인증된 서버 렌더링 화면
- **Release 2B**: SPA와 동적 화면 상태
- **Release 2C**: 다중 viewport와 반응형 비교
- **Release 2D**: 승인된 운영 사이트와 다중 사용자 보안

Release 1의 지원 대상은 JSP이지만, 분석 입력과 중간 산출물은 `RENDERED_WEB_PAGE`라는 기술 중립 계약을 사용한다.

---

## 2. 핵심 구현 원칙

1. JSP 파일을 정적으로 해석하지 않고 브라우저에 렌더링된 결과를 분석한다.
2. `springai`는 MCP와 작업 오케스트레이션을 담당하고 Chromium 프로세스를 직접 포함하지 않는다.
3. Playwright 분석기는 별도 Node.js/TypeScript 프로세스로 실행한다.
4. Figma 문서 생성은 별도 TypeScript Figma Plugin이 수행한다.
5. `RenderedDesignDocument`는 시각 재현용, `UiDesignSpec`은 화면명세와 코드 생성용으로 분리한다.
6. DOM, CSS, Layout 분석 단계를 개별 MCP Tool로 노출하지 않는다.
7. Release 1은 결정론적 규칙을 사용하며 LLM을 필수 경로에 포함하지 않는다.
8. 확실하지 않은 Auto Layout과 컴포넌트는 자동 확정하지 않고 원본 좌표와 경고를 보존한다.
9. URL 검증은 `springai`와 extractor 양쪽에서 수행한다.
10. MCP 응답에는 대용량 노드 문서 대신 artifact ID, 요약, 상태 및 경고를 반환한다.
11. 인증정보, 쿠키, 토큰과 민감한 URL query 값은 artifact와 로그에 저장하지 않는다.
12. Release 2D 승인 전에는 운영 사이트 프로필을 활성화하지 않는다.
13. 문서와 자산은 단일 `.figpack` 패키지로 전달하고 프로젝트 간 로컬 경로를 공유하지 않는다.
14. Release 1의 WEB_CAPTURE 분석 결과는 RAG에 적재하지 않는다.
15. `springai`와 extractor가 모두 loopback으로 제한된 경우에만 Release 1 capture 기능을 활성화한다.

---

## 3. 시스템 경계

### 3.1 구성 요소

```text
MCP Client
  → springai
      ├─ CaptureWebPageTool
      ├─ WebCaptureOrchestrationService
      ├─ WebCaptureUrlValidator
      ├─ WebCaptureClient
      ├─ RenderedDesignDocumentValidator
      ├─ DesignArtifactService
      ├─ WebCaptureProjectionPolicy
      │      → SafeDesignProjection
      └─ RenderedDesignSpecMapper
             → DesignAnalysisResult
             → UiDesignSpec
             → ScreenSpecification

springai
  → loopback HTTP + X-Extractor-Key
      → jsp-design-extractor
          ├─ Playwright/Chromium
          ├─ Page Readiness
          ├─ DOM/CSS Collector
          ├─ Layout Analyzer
          ├─ Component Recognizer
          └─ RenderedDesignDocument

RenderedDesignPackage `.figpack`
  → jsp-to-figma-plugin
      ├─ package/schema/asset 검증
      ├─ preview
      ├─ Frame/Text/Image 생성
      └─ Auto Layout/Component/Style 생성
```

### 3.2 저장소 구성

구현물은 런타임과 배포 책임에 따라 분리한다.

| 프로젝트 | 책임 | 기술 |
|---|---|---|
| `springai` | MCP, 보안 정책, artifact 관리, 기존 디자인·코드 생성 연결 | Java 17, Spring Boot, Spring AI |
| `jsp-design-extractor` | 브라우저 렌더링과 디자인 문서 생성 | Node.js, TypeScript, Playwright |
| `jsp-to-figma-plugin` | Figma 캔버스 생성 | TypeScript, Figma Plugin API |
| `website-figma-contract` | JSON Schema, package manifest, 공통 fixture와 변경 이력 | JSON Schema, test fixture |

`RenderedDesignDocument`와 `.figpack` manifest Schema의 원본은 기술 중립적인 `website-figma-contract` 프로젝트에 둔다. 세 실행 프로젝트는 계약 버전을 고정해 사용하고 계약 테스트에서 schema SHA-256과 schema version을 확인한다. 별도 계약 프로젝트 생성 전에는 `springai/contracts/`를 임시 원본으로 사용할 수 있지만, 이 경우 `springai`가 계약 소유자라는 사실과 외부 프로젝트 동기화 절차를 명시한다.

---

## 4. Release 1 범위

### 4.1 포함

- `LOCAL_WEB` capture profile
- `http://localhost`와 명시적으로 허용한 개발 origin
- 서버가 이미 실행 중인 JSP/Thymeleaf 등 서버 렌더링 화면
- Chromium 단일 브라우저 엔진
- 데스크톱 단일 viewport
- 비인증 페이지 또는 인증을 우회한 고정 테스트 fixture
- HTML DOM, computed CSS, Bounding Box 수집
- Header, Footer, Navigation, Breadcrumb, Search Panel, Toolbar 인식
- Form, Input, Select, Checkbox, Radio, Button 인식
- Table/Data Grid와 Pagination 인식
- `.figpack` 파일 기반 Figma Plugin import
- `RenderedDesignDocument → UiDesignSpec` 결정론적 매핑
- 기존 `ScreenSpecification` 생성 경로 연결

### 4.2 제외

- JSP/Tomcat 프로세스 자동 기동과 종료
- 임의 URL 또는 자동 크롤링
- 운영 사이트 접근
- 로그인과 인증 세션 사용
- SSO/MFA 자동 로그인
- SPA 라우트 및 복잡한 비동기 상태
- 다중 viewport와 breakpoint 추론
- cross-origin iframe 내부 분석
- Canvas 픽셀의 의미 분석
- Shadow DOM 심층 분석
- Figma Library publish
- React 코드 생성
- Figma REST API를 통한 캔버스 노드 쓰기

### 4.3 Figma 출력 경로 결정

Release 1은 개발 세션에서 사용할 수 있는 Figma MCP(`use_figma`, `figma-generate-library` 계열)를 애플리케이션 런타임 의존성으로 재사용하지 않고 전용 `jsp-to-figma-plugin`을 구현한다.

결정 근거:

1. Figma MCP 경로는 로그인된 대화형 AI 세션이 Tool 호출을 직접 조정하는 개발 지원 경로이며, `springai`가 독립적으로 시작하고 반복 실행하는 제품 런타임 계약이 아니다.
2. MCP/AI 세션의 가용성, 사용자 로그인 상태와 모델 판단을 필수 경로에 두면 동일 `.figpack` 입력의 결정론적 재생성과 버전 고정이 어렵다.
3. Release 1은 LLM을 필수 경로에서 제외하고 package/schema/hash 검증, 사용자 preview 승인과 Figma Node 생성을 코드로 재현해야 한다.
4. Figma REST API의 파일 조회 경로는 기존 Figma 분석에 계속 사용하지만, 일반 캔버스 Frame/Text/Image/Component 생성을 위한 출력 경로로 간주하지 않는다.
5. 전용 Plugin은 Figma 편집 권한을 가진 사용자의 명시적 실행과 승인 안에서만 현재 파일을 변경하며, 실패 시 이번 실행의 임시 Frame만 정리할 수 있다.

Figma MCP는 구현·검수 단계의 비교 도구로 사용할 수 있지만 Release 1 제품 파이프라인의 실행 엔진이나 자동 fallback으로 사용하지 않는다. 이 결정은 Plugin 신규 구축 비용을 수용하는 아키텍처 결정이며 변경하려면 결정론, 인증 주체, 무인 실행, 오류 복구와 감사 가능성을 다시 평가한다.

---

## 5. 공통 데이터 계약

### 5.1 최상위 구조

```json
{
  "schemaVersion": "rendered-design-document-v1",
  "captureId": "UUID",
  "documentKey": "HMAC-SHA-256",
  "contentHash": "SHA-256",
  "source": {},
  "environment": {},
  "page": {},
  "nodes": [],
  "assets": [],
  "tokens": {},
  "componentCandidates": [],
  "interactions": [],
  "warnings": [],
  "extractor": {}
}
```

식별자의 의미는 다음과 같다.

| 필드 | 생성 주체 | 의미 |
|---|---|---|
| `captureId` | `springai` | 실행마다 새로 생성하는 추적 ID이며 extractor가 그대로 echo |
| `documentKey` | `springai` | 정규화된 origin/path, profile, viewport, state를 서버 Secret으로 HMAC한 논리 화면 ID |
| `contentHash` | extractor 생성, `springai` 재검증 | 실행 가변 필드를 제외한 정규화 디자인 내용의 SHA-256 |

`documentKey`에는 URL query 값과 인증정보를 포함하지 않는다. query가 화면 식별에 반드시 필요한 경우 사전 승인된 query 이름과 정규화된 값만 포함한다.

Release 1에서는 `artifactId`를 `captureId`와 동일한 값으로 사용한다. extractor HTTP 재시도와 로그 추적에는 별도 `requestId`를 사용하되 artifact와 디자인 문서의 논리 식별자로 사용하지 않는다.

### 5.2 `source`

| 필드 | 형식 | 필수 | 계약 |
|---|---|---:|---|
| `type` | enum | Y | Release 1은 `RENDERED_WEB_PAGE` |
| `applicationKind` | enum | Y | `JSP`, `THYMELEAF`, `STATIC_HTML`, `SPA`, `UNKNOWN` |
| `requestedUrl` | string | Y | userinfo·fragment 제거, 민감 query 값 마스킹 |
| `finalUrl` | string | Y | redirect 검증을 통과한 URL의 마스킹 값 |
| `urlFingerprint` | SHA-256 | Y | 민감 query 값을 제거한 canonical URL의 식별용 해시 |
| `capturedAt` | RFC 3339 | Y | timezone 포함 |

민감 query 이름의 기본 목록은 `token`, `access_token`, `code`, `session`, `sid`, `key`, `password`이며 설정으로 확장할 수 있다. 기본 정책은 query 값을 모두 마스킹하고, 화면 식별에 필요한 이름만 allowlist로 보존하는 방식이다. URL 원문과 query 값은 로그에 출력하지 않는다. 마스킹 전 URL의 단순 SHA-256은 저장하지 않는다.

### 5.3 `environment`

| 필드 | 형식 | Release 1 기본값 |
|---|---|---|
| `viewportName` | string | `desktop` |
| `viewportWidth` | integer | 1440 |
| `viewportHeight` | integer | 1200 |
| `deviceScaleFactor` | number | 1 |
| `locale` | string | `ko-KR` |
| `timezone` | string | `Asia/Seoul` |
| `colorScheme` | enum | `light` |
| `reducedMotion` | boolean | `true` |
| `browserEngine` | enum | `chromium` |

### 5.4 `page`

| 필드 | 설명 |
|---|---|
| `title` | 문서 제목 |
| `rootNodeId` | 최상위 디자인 노드 ID |
| `documentWidth` | 문서 전체 폭 |
| `documentHeight` | 문서 전체 높이 |
| `scrollX`, `scrollY` | 캡처 시점 스크롤 위치 |
| `backgroundColor` | 정규화된 배경색 |

### 5.5 `nodes[]`

각 노드는 다음 필드를 갖는다.

| 그룹 | 필드 |
|---|---|
| 식별 | `id`, `parentId`, `childIds`, `tagName`, `role`, `name` |
| 출처 | `selectorHint`, `sourceOrder` |
| 내용 | `text`, `valueKind`, `placeholder`, `altText` |
| 가시성 | `visible`, `opacity`, `clipped`, `overflow` |
| 기하 | `x`, `y`, `width`, `height`, `rotation`, `zIndex` |
| 박스 | `display`, `position`, `margin`, `padding`, `border`, `radius` |
| 레이아웃 | `flex`, `grid`, `gap`, `alignment`, `layoutInference` |
| 색상 | `background`, `fills`, `strokes` |
| 글꼴 | `fontFamily`, `fontSize`, `fontWeight`, `lineHeight`, `letterSpacing`, `textAlign` |
| 효과 | `boxShadow`, `textShadow`, `filter` |
| 자산 | `assetRefs` |
| 시맨틱 | `componentType`, `componentConfidence`, `fieldRole` |

`selectorHint`는 재식별을 위한 제한된 힌트이며 비밀번호, 사용자 입력값 또는 동적 토큰을 포함하지 않는다. `text`는 `input[type=password]`와 설정된 민감 selector에서 수집하지 않는다.

`value`는 기본적으로 `null`이며 유일한 예외로 `<select>`의 현재 선택된 `<option>` 텍스트만 채운다(2026-07-22 확장). `<input>`/`<textarea>` 등 사용자가 직접 입력할 수 있는 컨트롤의 값은 계속 `null`로 고정한다 — §11.3 민감정보 원칙(입력값을 문서에 포함하지 않음)이 select의 사전 정의된 선택지 라벨에는 적용되지 않는다고 판단했기 때문이다. select 표시값도 `text`와 마찬가지로 아직 `sensitiveSelectors` 기반 마스킹 대상은 아니다(기존에도 `text` 필드가 JSON 레벨에서 마스킹되지 않는 것과 동일한 기존 한계).

### 5.6 `layoutInference`

```json
{
  "mode": "AUTO_LAYOUT_HORIZONTAL",
  "confidence": 0.94,
  "evidence": ["display:flex", "flex-direction:row", "stable-gap"],
  "fallback": "ABSOLUTE"
}
```

허용값:

- `AUTO_LAYOUT_HORIZONTAL`
- `AUTO_LAYOUT_VERTICAL`
- `GRID`
- `ABSOLUTE`
- `UNKNOWN`

Release 1에서는 실제 `display:flex`이며 겹침과 비정상 transform이 없는 경우만 Auto Layout 확정 후보로 삼는다. Grid와 Block 기반 배치의 Auto Layout 변환은 confidence와 경고를 남기며, 임계값 미만은 `ABSOLUTE`로 보존한다.

### 5.7 `assets[]`

| 필드 | 설명 |
|---|---|
| `id` | 문서 내 자산 ID |
| `kind` | `IMAGE`, `SVG`, `FONT`, `BACKGROUND_IMAGE`, `ICON` |
| `sourceUrl` | 마스킹된 URL 또는 null |
| `contentHash` | 확보한 바이트의 SHA-256 |
| `mimeType` | MIME type |
| `width`, `height` | 자산 크기 |
| `storage` | `EMBEDDED`, `ARTIFACT_FILE`, `URL_REFERENCE`, `OMITTED` |
| `artifactPath` | artifact 디렉터리 기준 상대경로 |
| `warning` | 누락 또는 권한 경고 |

Release 1은 동일 출처 이미지와 인라인 SVG를 우선 수집한다. 외부 자산은 명시된 resource origin allowlist를 통과한 경우에만 다운로드한다.

`ARTIFACT_FILE`은 extractor 로컬 경로가 아니라 `.figpack` 내부 `assets/` 기준 상대경로를 의미한다. `EMBEDDED`는 작은 인라인 SVG 등 schema가 허용한 제한된 데이터에만 사용하고, 일반 raster image를 base64로 JSON에 포함하지 않는다.

### 5.8 `componentCandidates[]`

```json
{
  "nodeId": "node-42",
  "type": "SEARCH_PANEL",
  "confidence": 0.91,
  "evidence": ["role=search", "contains-input", "contains-search-button"]
}
```

Release 1 지원 타입:

```text
HEADER, FOOTER, NAVIGATION, BREADCRUMB,
SEARCH_PANEL, TOOLBAR, FORM, FIELD_GROUP,
TEXT_INPUT, SELECT, CHECKBOX, RADIO, BUTTON,
TABLE, PAGINATION, IMAGE, GENERIC_CONTAINER
```

### 5.9 무결성 계약

- `schemaVersion`은 정확히 `rendered-design-document-v1`이어야 한다.
- 모든 node ID와 asset ID는 문서 내에서 유일해야 한다.
- `parentId`, `childIds`, `assetRefs`는 존재하는 대상을 가리켜야 한다.
- 노드 트리에 순환이 없어야 한다.
- 숫자는 유한값이어야 하고 크기는 음수가 될 수 없다.
- artifact 상대경로는 `..`, 절대경로 및 symbolic link 탈출을 허용하지 않는다.
- JSON 본문과 각 자산은 설정된 최대 크기를 넘을 수 없다.
- 알 수 없는 enum 값은 조용히 기본값으로 바꾸지 않고 명시적으로 거부한다.

### 5.10 보조 계약

`extractor`는 다음 정보를 포함한다.

- extractor service version
- browser engine과 browser version
- schema version과 schema SHA-256
- layout analyzer와 component recognizer version

`warnings[]`의 각 항목은 `code`, `severity`, `nodeId`, 안전한 `message`를 포함한다. `severity`는 `BLOCK`, `WARN`, `FYI` 중 하나다.

`interactions[]`는 Release 1에서 정적으로 확인되는 anchor, button, form submit 관계만 표현하며 자동 실행 step은 포함하지 않는다. `tokens`는 색상, 글꼴, 크기, radius, shadow, spacing 후보와 사용 빈도를 표현한다. 각 구조의 상세 필드와 `additionalProperties` 정책은 공통 JSON Schema에서 확정한다.

### 5.11 RenderedDesignPackage `.figpack`

Release 1의 프로젝트 간 전달 단위는 ZIP 형식의 `.figpack` 파일이다.

```text
{captureId}.figpack
├─ manifest.json
├─ document.json
├─ preview.png
└─ assets/
   ├─ {contentHash}.png
   └─ {contentHash}.svg
```

`manifest.json`은 다음 정보를 포함한다.

- package version
- capture ID, document key, content hash
- document schema version과 schema SHA-256
- 각 entry의 상대경로, MIME type, byte length와 SHA-256
- node, asset, component, warning 요약
- extractor와 browser version

패키지 검증 규칙:

- ZIP entry 이름은 UTF-8 정규화 상대경로만 허용한다.
- 절대경로, `..`, 중복 entry, symbolic link와 case-folding 충돌을 거부한다.
- 압축 파일 크기, 압축 해제 총크기, entry 수와 개별 entry 크기를 제한한다.
- manifest에 없는 entry와 누락된 entry를 거부한다.
- 모든 entry의 크기와 SHA-256을 검증한다.
- `preview.png`는 민감 selector를 마스킹한 QA 기준 이미지이며 Figma 생성 필수 입력은 아니다.

---

## 6. Capture API 계약

### 6.1 통신

- extractor는 `127.0.0.1`에만 bind한다.
- `springai`는 고정 설정의 extractor base URL만 호출한다.
- 모든 요청에 `X-Extractor-Key`를 보낸다.
- extractor는 요청의 `targetUrl`을 검증하지만 다른 임의 목적지 URL을 서버 API로 받지 않는다.
- 성공 응답은 `.figpack` binary이며 오류 응답만 안전한 JSON을 사용한다.
- 압축 응답 크기, 압축 해제 크기 제한과 요청 timeout을 양쪽에서 적용한다.

### 6.2 Health API

```http
GET /v1/health
```

```json
{
  "status": "UP",
  "serviceVersion": "1.0.0",
  "schemaVersions": ["rendered-design-document-v1"],
  "browser": "chromium"
}
```

### 6.3 Capture API

```http
POST /v1/captures
Content-Type: application/json
X-Extractor-Key: ********
```

```json
{
  "requestId": "UUID",
  "captureId": "UUID",
  "documentKey": "HMAC-SHA-256",
  "targetUrl": "http://localhost:8080/sample.do",
  "profile": "LOCAL_WEB",
  "featureType": "crud",
  "viewport": {
    "name": "desktop",
    "width": 1440,
    "height": 1200,
    "deviceScaleFactor": 1
  },
  "readiness": {
    "readySelector": "main",
    "hiddenSelector": ".loading",
    "timeoutSeconds": 30
  }
}
```

Release 1은 동기 응답을 사용한다.

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.springai.figpack+zip
X-Capture-Id: UUID
X-Capture-Request-Id: UUID
Content-Disposition: attachment; filename="capture.figpack"

<binary figpack body>
```

`springai`는 응답의 capture ID와 document key가 요청값 및 package manifest와 일치하는지 검증한다. 성공 상태와 요약은 package의 `manifest.json`에 포함한다. 브라우저 분석 시간이 운영 timeout을 초과하거나 비동기 큐가 필요해지는 시점에 Release 2에서 job API로 확장한다. `captureId/requestId`와 manifest 상태 모델을 미리 사용해 비동기 확장 시 의미를 유지한다.

### 6.4 오류 코드

| 코드 | HTTP | 의미 |
|---|---:|---|
| `CAPTURE_DISABLED` | 503 | 기능이 비활성화됨 |
| `CAPTURE_PROFILE_NOT_ALLOWED` | 403 | 허용되지 않은 profile |
| `CAPTURE_URL_INVALID` | 400 | URL 문법 또는 scheme 오류 |
| `CAPTURE_ORIGIN_DENIED` | 403 | origin allowlist 위반 |
| `CAPTURE_REDIRECT_DENIED` | 403 | redirect 목적지 위반 |
| `CAPTURE_RESOURCE_DENIED` | 422 | 차단된 하위 자원이 핵심 렌더링을 방해함 |
| `CAPTURE_AUTH_FAILED` | 401 | 인증 세션 실패 |
| `CAPTURE_NAVIGATION_FAILED` | 502 | 페이지 이동 실패 |
| `CAPTURE_READY_TIMEOUT` | 504 | 화면 준비 조건 timeout |
| `CAPTURE_DOCUMENT_TOO_LARGE` | 413 | 문서 크기 제한 초과 |
| `CAPTURE_SCHEMA_INVALID` | 502 | extractor 출력 계약 위반 |
| `CAPTURE_BROWSER_UNAVAILABLE` | 503 | Chromium 실행 불가 |
| `CAPTURE_INTERNAL_ERROR` | 500 | 분류되지 않은 내부 오류 |

오류 응답에는 `code`, 안전한 `message`, `requestId`, `retryable`만 포함한다. URL 원문, 쿠키, DOM 원문과 stack trace는 MCP 응답에 포함하지 않는다.

---

## 7. 브라우저 수집 명세

### 7.1 수집 순서

```text
입력 정규화 및 main origin 정책 검증
  → 새 Browser Context 생성
  → viewport/locale/timezone/colorScheme 고정
  → service worker/WebSocket/download/popup 정책 적용
  → 모든 navigation/resource request interception 적용
  → 요청 전 origin/IP/resource 정책 검증
  → 허용된 경우에만 target URL 이동
  → redirect 요청도 전송 전에 동일 검증
  → DOMContentLoaded 대기
  → readySelector 표시 대기
  → hiddenSelector 제거 대기
  → document.fonts.ready 대기
  → 애니메이션과 caret 정지
  → DOM/CSS/Layout 수집
  → 자산 수집
  → 민감 selector가 마스킹된 preview screenshot 생성
  → 정규화 및 시맨틱 인식
  → schema 자체 검증
  → `.figpack` 생성과 package 자체 검증
  → binary 응답
  → Context 폐기
```

### 7.2 DOM 수집

- `document.documentElement`부터 깊이 우선 순회한다.
- `script`, `style`, `link`, `meta`, `noscript`는 디자인 노드에서 제외한다.
- `display:none` 노드는 시각 트리에서 제외한다.
- `visibility:hidden`, `opacity:0` 노드는 레이아웃 공간과 좌표를 유지하고 `visible=false`로 기록한다.
- `input[type=password]` 값은 절대 수집하지 않는다.
- 일반 input의 현재 값은 Release 1에서 기본 수집하지 않고 placeholder와 label만 수집한다.
- 사용자 표시 문자열은 요소의 전체 `textContent`를 복제하지 않고 직접 자식 DOM Text Node를 별도 디자인 TEXT node로 변환한다.
- 연속 공백과 줄바꿈은 computed `white-space`를 고려해 정규화하고 CSS `text-transform` 적용 결과와 원문을 구분한다.
- `::before`, `::after`의 텍스트 또는 이미지가 실제 표시되는 경우 별도 synthetic child node로 표현한다.
- cross-origin iframe은 경계 노드와 크기만 기록하고 내부는 분석하지 않는다.
- Canvas는 이미지형 노드로만 기록하고 내부 의미를 분석하지 않는다.

### 7.3 CSS 수집

전체 CSS 속성을 무차별 저장하지 않고 Figma 변환에 필요한 whitelist만 수집한다.

- display, position, inset
- width, height, min/max size
- margin, padding, gap
- flex 및 grid 관련 속성
- overflow, opacity, z-index, transform
- background, border, radius
- color, font, line-height, letter-spacing, text-align
- box-shadow, text-shadow, filter
- object-fit, object-position

색상은 RGBA로, 길이는 가능한 경우 CSS pixel 숫자로 정규화한다. `auto`, `%`, `calc()` 같은 원본 값은 `raw` 보조 필드로 유지하고 최종 Bounding Box를 별도로 저장한다.

### 7.4 결정론과 안정화

- `capturedAt`, capture/request ID 등 실행마다 달라지는 값은 `contentHash` 계산에서 제외한다.
- 노드 ID는 DOM 순서와 안정적인 selector 힌트를 조합하여 생성하되 충돌 시 순번을 추가한다.
- 애니메이션, transition, caret과 smooth scroll을 캡처 전에 비활성화한다.
- locale, timezone, color scheme과 reduced motion을 고정한다.
- readiness 완료 후 DOM 변이가 계속되면 안정화 제한시간까지 관찰하고 경고를 남긴다.

`contentHash`는 다음 canonicalization 이후 계산한다.

1. 실행 가변 필드와 최상위 `contentHash` 자체를 제외한다.
2. JSON object key를 Unicode code point 순서로 정렬한다.
3. 집합 의미 배열은 명세된 stable key로 정렬하고 DOM/node 순서 배열은 원래 순서를 유지한다.
4. 좌표와 길이는 schema에 정의된 소수점 정밀도로 반올림한다.
5. 색상은 정규화된 RGBA로 변환한다.
6. 문자열은 UTF-8과 Unicode NFC로 정규화한다.
7. canonical JSON byte에 SHA-256을 적용한다.

browser version, schema version, analyzer version은 문서에 기록하지만 `contentHash` 포함 여부를 schema에서 고정한다. 같은 버전과 fixture의 반복 결정론은 별도 테스트하고, 다른 browser/analyzer version 사이의 hash 동일성을 보장하지 않는다.

---

## 8. `springai` 구현 명세

### 8.1 설정

신규 `WebCaptureProperties`를 `app.web-capture`에 바인딩한다.

```yaml
app:
  web-capture:
    enabled: ${WEB_CAPTURE_ENABLED:false}
    extractor-base-url: ${WEB_CAPTURE_EXTRACTOR_BASE_URL:http://127.0.0.1:4319}
    extractor-api-key: ${WEB_CAPTURE_EXTRACTOR_API_KEY:}
    connect-timeout-seconds: ${WEB_CAPTURE_CONNECT_TIMEOUT_SECONDS:3}
    response-timeout-seconds: ${WEB_CAPTURE_RESPONSE_TIMEOUT_SECONDS:60}
    max-response-mb: ${WEB_CAPTURE_MAX_RESPONSE_MB:50}
    max-uncompressed-artifact-mb: ${WEB_CAPTURE_MAX_UNCOMPRESSED_ARTIFACT_MB:100}
    artifact-base-path: ${WEB_CAPTURE_ARTIFACT_PATH:${java.io.tmpdir}/springai-design-artifacts}
    max-artifact-mb: ${WEB_CAPTURE_MAX_ARTIFACT_MB:50}
    retention-hours: ${WEB_CAPTURE_RETENTION_HOURS:24}
    mapper-version: ${WEB_CAPTURE_MAPPER_VERSION:rendered-design-mapper-v2}
    document-key-secret: ${WEB_CAPTURE_DOCUMENT_KEY_SECRET:}
    enabled-profiles:
      - LOCAL_WEB
    allowed-origins:
      - http://127.0.0.1:8080
      - http://localhost:8080
    allowed-resource-origins: []
    sensitive-selectors:
      - input[type=password]
```

검증 규칙:

- `enabled=true`이면 extractor API key와 document key HMAC 전용 Secret이 필요하다. 두 Secret은 같은 값을 사용할 수 없다.
- extractor base URL은 Release 1에서 loopback HTTP만 허용한다.
- `springai`의 실제 bind 주소도 loopback이어야 하며 `SERVER_ADDRESS` override 결과까지 검사한다.
- allowed origin은 scheme, host, port까지 정확히 비교한다.
- `allowed-resource-origins`가 비어 있으면 main document origin만 허용한다.
- artifact 경로는 기동 시 실경로를 확인하고 허용된 루트 밖이면 실패한다.
- `AUTHORIZED_PRODUCTION_WEBSITE`는 Release 2D gate 완료 전 설정할 수 없다.
- retention은 Release 1 허용 범위 안에서 검증하고 만료 artifact를 안전하게 정리한다.

`WebCaptureDeploymentGuard`는 `enabled=true`일 때 `springai` bind 주소, extractor 주소, 활성 profile과 MCP 노출 조건을 함께 검사한다. 하나라도 Release 1 신뢰 경계를 벗어나면 기동을 실패시킨다.

### 8.2 Java 모델

신규 패키지:

```text
com.krdevops.springai.model.capture
```

신규 모델:

- `CaptureProfile`
- `CaptureStatus`
- `CaptureWebPageRequest`
- `ViewportSpec`
- `ReadinessSpec`
- `CaptureResponse`
- `CaptureArtifactSummary`
- `DesignArtifactMetadata`
- `RenderedDesignDocument`
- `RenderedNode`
- `RenderedAsset`
- `ComponentCandidate`
- `CaptureWarning`
- `FigmaImportArtifact`
- `RenderedDesignPackageManifest`
- `SafeDesignProjection`

JSON 계약을 정확하게 제어하기 위해 외부 Tool 요청과 저장 모델은 immutable record를 우선 사용한다. 대규모 노드 데이터가 mapper에 불필요한 경로에서는 `JsonNode`를 사용하되 schema 검증을 먼저 통과해야 한다.

### 8.3 Java 서비스

| 클래스 | 책임 |
|---|---|
| `WebCaptureUrlValidator` | URL, profile, origin, redirect 대상 정규화 및 검증 |
| `WebCaptureDeploymentGuard` | Release 1 loopback, MCP 노출과 profile 기동 조건 검증 |
| `WebCaptureClient` | 고정 extractor endpoint 호출, API key, timeout, 크기 제한 |
| `RenderedDesignPackageValidator` | ZIP entry, manifest, 크기, hash와 경로 검증 |
| `RenderedDesignDocumentValidator` | JSON Schema와 참조 무결성 검증 |
| `DesignArtifactService` | artifact 원자적 저장, 조회, export, 경로 탈출 방지 |
| `WebCaptureProjectionPolicy` | raw document에서 UiDesignSpec에 허용된 구조·label·style만 안전한 projection으로 추출 |
| `RenderedDesignSpecMapper` | `SafeDesignProjection`을 기존 `UiDesignSpec`으로 요약 |
| `WebCaptureCacheKeyFactory` | content hash, feature type, schema와 mapper version 기반 키 생성 |
| `WebCaptureAnalysisService` | `DesignAnalysisResult` 저장과 기존 분석 경로 연결 |
| `WebCaptureOrchestrationService` | 전체 capture 트랜잭션과 안전한 오류 변환 |

### 8.4 MCP Tool

```java
@Tool(description = """
    허용된 로컬 또는 개발 화면 URL을 Chromium으로 분석하여 Figma import와
    화면명세 생성에 사용할 Design Artifact를 만듭니다. JSP/Thymeleaf 등 서버 템플릿
    엔진을 구분하지 않고 렌더링된 최종 HTML을 캡처합니다. Release 1은 LOCAL_WEB,
    단일 desktop viewport와 비인증 화면만 지원하며 서버는 미리 실행되어 있어야 합니다.
    인증정보, 쿠키 또는 토큰을 인자로 전달하지 마세요.
    """)
public CaptureArtifactSummary captureWebPage(CaptureWebPageRequest request)
```

```java
@Tool(description = "저장된 Design Artifact의 메타데이터, 요약과 경고를 조회합니다.")
public CaptureArtifactSummary getDesignArtifact(String artifactId)
```

```java
@Tool(description = """
    검증된 Design Artifact를 Figma Plugin에서 가져올 수 있는 .figpack 파일로 내보냅니다.
    반환 경로는 설정된 artifact export 디렉터리 안에만 생성됩니다.
    """)
public FigmaImportArtifact prepareFigmaImport(String artifactId)
```

```java
@Tool(description = """
    Design Artifact를 기존 UiDesignSpec 분석 결과로 변환합니다.
    반환된 analysisId는 createScreenSpecification에 전달할 수 있습니다.
    """)
public DesignAnalysisResult analyzeCapturedDesign(String artifactId, String featureType)
```

`CaptureWebPageTool`은 첫 번째 메서드를, `DesignArtifactTool`은 나머지 세 메서드를 제공한다. 두 Tool을 `McpConfig.allToolCallbacks`에 등록한다.

### 8.5 기존 디자인 분석 모델 연결

WEB_CAPTURE는 FILE, FIGMA에 이은 세 번째 source이므로 공통 source 모델 전환 트리거가 이미 충족되었다. 따라서 nullable `WebCaptureSource`를 flat record에 추가하지 않고, WEB_CAPTURE 병합 전에 `DesignSourceMetadata` 전환 ADR과 호환 구현을 먼저 완료한다.

권장 모델:

```java
public sealed interface DesignSourceMetadata
        permits FileDesignSourceMetadata,
                FigmaDesignSourceMetadata,
                WebCaptureDesignSourceMetadata {
    DesignSourceType sourceType();
}
```

```text
FileDesignSourceMetadata
├─ sourcePath
└─ pageRange

FigmaDesignSourceMetadata
├─ fileKey
├─ nodeId
└─ fileVersion

WebCaptureDesignSourceMetadata
├─ artifactId
├─ documentKey
├─ contentHash
└─ renderedDocumentSchemaVersion
```

`DesignSourceType`에는 다음 값을 추가한다.

```java
WEB_CAPTURE
```

`DesignAnalysisResult`에는 `DesignSourceMetadata sourceMetadata`를 추가한다. 기존 `sourceType`, `sourcePath`, `pageRange`, `figmaSource`는 운영 DB의 이전 JSON을 읽기 위한 deprecated 호환 필드로 한 schema 세대 동안 유지하되 신규 서비스 로직에서는 사용하지 않는다. WEB_CAPTURE 전용 nullable top-level 필드는 추가하지 않는다.

호환 절차:

1. 신규 정적 factory가 FILE/FIGMA/WEB_CAPTURE별 유효한 metadata 조합만 생성한다.
2. 이전 JSON에서 `sourceMetadata == null`이면 `LegacyDesignSourceAdapter`가 기존 flat 필드로 FILE 또는 FIGMA metadata를 구성한다.
3. repository 저장, cache key, 실행 계약과 RAG 정책은 `sourceMetadata.sourceType()`만 사용한다.
4. 잘못된 metadata subtype과 `DesignSourceType` 조합은 명시적으로 거부한다.
5. 모든 `new DesignAnalysisResult(...)` canonical constructor 호출부를 factory 호출로 전환한다.
6. 이전 FILE/Figma JSON read, 신규 JSON write, save-or-get 동시성 및 재직렬화 회귀 테스트를 통과한다.
7. deprecated flat 필드 제거 시점과 DB JSON migration은 ADR에 별도 기록한다.

WEB_CAPTURE 분석 계약:

| 필드 | 값 |
|---|---|
| `sourceMetadata.sourceType` | `WEB_CAPTURE` |
| `sourceMetadata.artifactId` | `{artifactId}` |
| `sourceMetadata.documentKey` | 최상위 `documentKey` |
| `sourceMetadata.contentHash` | 최상위 `contentHash` |
| `provider` | `web-capture` |
| `model` | `deterministic-mapper` |
| `promptVersion` | `rendered-design-mapper-v2` |
| `analysisContractVersion` | 설정의 mapper version |
| `uiSpecSchemaVersion` | `UiDesignSpec.SCHEMA_VERSION` |

`DesignReferenceAnalysisService.checkExecutionContract`와 직접 ID 조회는 `WEB_CAPTURE` 계약을 별도 분기로 검증한다. 기존 DB 테이블은 전체 결과 JSON을 저장하므로 Release 1에서 컬럼 추가는 필요하지 않지만, 운영 데이터가 있는 환경에서는 호환성 역직렬화 테스트를 반드시 수행한다.

Release 1의 WEB_CAPTURE 결과는 RAG에 적재하지 않으며 시맨틱 후보 검색에도 노출하지 않는다. 화면의 label과 table/detail text에도 개인정보가 포함될 수 있으므로 마스킹만으로 RAG 저장을 정당화하지 않는다. Release 2D에서 owner/tenant 격리, 최소 데이터 projection과 명시적 opt-in이 완료된 이후 별도 활성화한다.

### 8.6 artifact 저장

Release 1은 로컬 파일 저장소를 사용한다.

```text
{artifact-base-path}/
└─ {artifactId}/
   ├─ metadata.json
   ├─ source.figpack
   ├─ document.json
   ├─ preview.png
   ├─ assets/
   │  └─ {contentHash}.{extension}
   └─ export/
      └─ {artifactId}.figpack
```

저장 계약:

- artifact ID는 서버가 생성한 UUID만 허용한다.
- 임시 디렉터리에 기록 후 같은 파일시스템에서 원자적으로 이동한다.
- 기존 artifact를 덮어쓰지 않는다.
- metadata와 document의 ID 및 hash를 재조회 시 검증한다.
- 외부에서 전달된 파일명과 상대경로를 직접 사용하지 않는다.
- 만료된 artifact는 `retention-hours`에 따라 정리하고 현재 작업 중인 임시 디렉터리는 제외한다.
- 삭제 기능은 Release 1에서 MCP에 노출하지 않으며 운영자가 명시적 artifact ID로 실행하는 관리 절차만 제공한다.
- Release 2D에서는 owner/tenant를 포함한 저장소 추상화로 교체한다.

---

## 9. `RenderedDesignDocument → UiDesignSpec` 매핑

### 9.1 화면 유형

다음 증거를 순서대로 적용한다.

1. 명시된 `featureType`
2. 인식된 TABLE/FORM/DETAIL 패턴
3. 페이지 title, heading, action 의미
4. 불명확하면 `CRUD_LIST`와 uncertainty 기록

`applicationKind`는 HTML에서 신뢰성 있게 탐지하지 않는다. `LOCAL_WEB` profile은 JSP/Thymeleaf 등 서버 템플릿 엔진을 구분하지 않으므로 Release 1은 profile과 무관하게 `UNKNOWN`을 기록한다.

### 9.2 레이아웃

| RenderedDesignDocument | UiDesignSpec |
|---|---|
| 최상위 Navigation/Header/Footer | `layout.shell` |
| viewport와 주요 content 폭 | `layout.contentWidth` |
| 요소 밀도와 행 높이 | `layout.density` |
| form field의 열 배치 | `layout.formColumnLayout` |
| primary action 좌표 | `layout.actionPlacement` |
| 검색 영역과 표의 상대 위치 | `layout.searchPanelPlacement` |

### 9.3 컴포넌트와 필드

`RenderedDesignSpecMapper`는 raw `RenderedDesignDocument`를 직접 입력받지 않는다.

```text
RenderedDesignDocument
  → WebCaptureProjectionPolicy
  → SafeDesignProjection
  → RenderedDesignSpecMapper
  → UiDesignSpec
```

`WebCaptureProjectionPolicy`는 읽을 수 있는 필드를 allowlist로 강제한다.

- component type, confidence와 값이 아닌 evidence code
- tag, role, control kind, field role
- layout, geometry와 style/token 후보
- `LABEL`, `BUTTON`, `TH`, `HEADING`으로 분류된 semantic label
- label/`for`, control name과 table header의 구조 관계

다음 raw 필드는 `SafeDesignProjection`에 존재하지 않으므로 mapper가 읽을 수 없다.

- input/current value
- 일반 node의 raw `text`
- table body cell 값
- detail value 영역의 값
- 사용자 프로필, avatar alt와 세션별 표시 문자열
- asset 원본 URL과 인증 관련 속성

semantic label, field ID 또는 control name은 기존 `SensitiveFieldPolicy.isSensitiveDisplayField(...)`의 토큰 단위 정확 일치 정책을 통과해야 한다. `PASSWORD`, `PWD`, `IHID`, `CERT`, `SECRET`, `HASH`, `KEY` 등 기존 인증·식별 토큰과 정확 이름은 projection에서 제외한다. Web capture 전용 규칙이 필요하면 기존 정책을 우회하지 않고 `WebCaptureProjectionPolicy`에서 추가 제한한다.

- 안전한 `componentCandidates`를 `UiDesignSpec.ComponentSpec`으로 변환한다.
- 승인된 label 관계를 조합하여 field hint를 만든다.
- 비밀번호와 마스킹된 값은 field hint의 실제 값으로 사용하지 않는다.
- 인식 confidence가 설정 임계값 미만이면 자동 확정하지 않고 `uncertainties`에 추가한다. 임계값은 analyzer version 계약에 포함하고 fixture baseline 승인 없이 변경하지 않는다.
- 버튼 텍스트와 role을 기존 CREATE, SAVE, UPDATE, DELETE, SEARCH, BACK, CANCEL action으로 정규화한다.

### 9.4 토큰

반복 빈도가 높은 색상, 글꼴, font size, radius, shadow, spacing을 후보로 집계한다. Release 1에서는 후보를 `UiDesignSpec.tokens`에 평탄화해 저장하되 Figma Variable로 자동 승격하는 판단은 Plugin preview 단계에서 사용자에게 표시한다.

---

## 10. Figma Plugin 구현 명세

### 10.1 실행 모델

- Figma Design용 Plugin으로 구현한다.
- `manifest.json`, `code.ts`, `ui.html` 또는 UI bundle을 별도 프로젝트로 관리한다.
- Release 1은 로컬 `.figpack` 파일 선택 방식만 지원한다.
- Release 1 `networkAccess.allowedDomains`는 `none`으로 설정한다.
- Plugin UI가 package를 읽고 ZIP bomb/path/hash/manifest/schema를 검증한 뒤 main sandbox에 검증된 document와 asset byte를 message로 전달한다.
- main sandbox에서도 전체 document 참조 무결성과 schema version을 다시 확인한 후 Figma 노드를 생성한다.

### 10.2 생성 순서

```text
`.figpack` 선택
  → package/manifest/hash/schema 검증
  → 요약과 경고 preview
  → 사용자 생성 승인
  → 임시 최상위 Frame 생성
  → assets 등록
  → 부모부터 node tree 생성
  → text font 로딩
  → style 적용
  → Auto Layout 적용 또는 absolute fallback
  → component 후보 생성
  → 성공 시 plugin data와 최종 이름 확정
  → 화면 배치 및 선택
  → 결과 요약 표시
```

### 10.3 노드 매핑

| 웹 노드 | Figma 노드 |
|---|---|
| container/section/div | `FrameNode` |
| text/heading/label | `TextNode` |
| img/background image | Rectangle의 image fill |
| inline SVG | `createNodeFromSvg` 결과 |
| button/input/select | Frame + Text 기반 시각 컴포넌트 |
| table | 행/셀 Frame 트리 |
| separator/border | Rectangle 또는 stroke |

HTML input을 Figma의 실제 동작 입력 컨트롤로 만들 수는 없으므로 시각적 Component로 표현한다.

SVG는 허용 요소·속성, 최대 byte, 최대 node와 재귀 깊이를 검사하고 script, 외부 reference 및 event handler를 제거한 뒤 사용한다.

### 10.4 Auto Layout

- `AUTO_LAYOUT_HORIZONTAL`은 `layoutMode=HORIZONTAL`로 변환한다.
- `AUTO_LAYOUT_VERTICAL`은 `layoutMode=VERTICAL`로 변환한다.
- padding, gap, alignment를 가능한 범위에서 적용한다.
- absolute fallback 노드는 원본 좌표를 부모 기준 상대좌표로 변환한다.
- 혼합 배치에서 자식의 원래 Bounding Box를 잃지 않는다.
- 변환 결과가 부모 크기를 벗어나면 warning annotation 또는 Plugin 결과 목록에 기록한다.

### 10.5 폰트

- TextNode의 문자를 설정하기 전에 `loadFontAsync`를 호출한다.
- 원본 폰트를 로드할 수 없으면 설정된 fallback font를 사용한다.
- fallback 발생 노드와 원본 font family를 결과에 기록한다.
- 여러 font style이 섞인 text는 range style 적용이 가능한 경우에만 유지한다.

### 10.6 컴포넌트와 스타일

- 높은 confidence의 반복 후보만 local Component 후보로 제안한다.
- Component 생성 여부는 preview에서 사용자가 선택한다.
- 색상, 글꼴, 효과 Style 생성 시 기존 동일 이름과 값의 local style을 재사용한다.
- Variable 생성은 Release 1 선택 기능이며 자동 publish하지 않는다.
- 팀 라이브러리 publish는 범위에서 제외한다.

### 10.7 중복 생성

생성된 최상위 Frame에 plugin data를 기록한다.

```text
schemaVersion
captureId
documentKey
contentHash
generatedAt
```

같은 `documentKey + contentHash`가 이미 있으면 기본 동작은 생성을 중단하고 사용자에게 알린다. 내용이 변경된 같은 document key는 Release 1에서 새 Frame으로 생성하며 기존 Frame을 자동 수정하지 않는다.

Plugin 생성 작업은 다음 실패 계약을 따른다.

- 이번 실행의 모든 노드는 임시 최상위 Frame 아래에서만 생성한다.
- 성공 전에는 기존 사용자 노드를 수정하거나 삭제하지 않는다.
- 실패 시 이번 실행에서 만든 임시 Frame만 제거하는 것을 기본값으로 한다.
- 진단을 위해 부분 결과를 유지하려면 사용자의 명시적 선택을 받는다.

---

## 11. 보안 명세

### 11.1 URL 검증

- URI parser를 사용하고 문자열 prefix 비교만 사용하지 않는다.
- Release 1은 `http`, `https`만 허용한다.
- origin은 scheme, canonical host, effective port의 정확한 조합으로 비교한다.
- userinfo, fragment, 비정상 host, 잘못된 port를 거부한다.
- navigation과 redirect는 요청이 전송되기 전에 interception 계층에서 주소 정책을 검사한다.
- hostname을 사용하는 경우 해석된 모든 IP를 검사하고 연결 시점의 대상이 정책 범위 안인지 확인한다.
- main document뿐 아니라 iframe, script, stylesheet, XHR, fetch, font, image 요청에도 resource origin 정책을 적용한다.
- `allowed-resource-origins`가 비어 있으면 main origin만 허용한다.
- Release 1에서는 service worker와 WebSocket을 기본 차단하고 필요한 경우 허용 origin과 용도를 별도 승인한다.

차단된 script, stylesheet, XHR/fetch가 readiness 또는 핵심 DOM 생성을 방해하면 `CAPTURE_RESOURCE_DENIED`로 실패한다. 이미지와 폰트처럼 fallback 가능한 자원은 기본적으로 warning과 placeholder/fallback으로 처리한다. 어떤 요청이 핵심 자원인지 결과만 보고 임의 추정하지 않고 자원 유형, readiness 실패 및 사용자가 승인한 resource origin 정책을 함께 사용한다.

### 11.2 브라우저 제한

- download를 취소한다.
- popup과 새 창을 닫고 경고한다.
- 브라우저 확장을 로드하지 않는다.
- 임의 사용자 script는 Release 1에서 지원하지 않는다.
- 요청별 새 Browser Context를 사용한다.
- Context와 임시 파일은 성공·실패와 무관하게 정리한다.
- extractor의 stdout에 페이지 데이터나 인증정보를 출력하지 않는다.

### 11.3 민감정보

- password value, cookie, localStorage, sessionStorage를 문서에 포함하지 않는다.
- 기본 민감 selector와 사용자가 설정한 민감 selector의 text/value를 마스킹한다.
- `preview.png`는 Playwright screenshot mask를 적용해 같은 민감 selector를 가린다.
- URL query 값은 allowlist 방식으로 보존하거나 기본 마스킹한다.
- 오류 메시지와 로그는 request ID, 안전한 error code와 origin 식별 해시만 남긴다.
- WEB_CAPTURE의 `UiDesignSpec`에는 sample table row와 detail value를 포함하지 않고 구조, 승인된 label, role, action과 token만 포함한다.
- 이 제한은 구현 관례가 아니라 `WebCaptureProjectionPolicy → SafeDesignProjection` 경계로 강제한다. `RenderedDesignSpecMapper`에는 raw node text/value 접근 API를 제공하지 않는다.
- 기존 `SensitiveFieldPolicy`의 정확 이름·토큰 판정을 semantic label과 field identifier에 재사용한다.
- projection allowlist 변경은 보안 테스트와 fixture 승인을 동반해야 하며 알 수 없는 필드는 기본 거부한다.

### 11.4 Release 2D 게이트

운영 사이트 지원 전에 다음을 별도 ADR로 승인한다.

- MCP 사용자 인증과 요청 주체 식별
- 사용자별 Browser Context와 Secret 저장소
- owner/tenant별 artifact와 캐시 격리
- 운영망 capture agent 배치
- 개인정보 마스킹과 보존기간
- 감사 로그와 삭제 절차
- 사이트 및 자산 사용 권한 확인
- rate limit, quota, 작업 큐와 취소

---

## 12. Release 2 확장 계약

### 12.1 인증 서버 렌더링

- `storageStateRef`는 서버가 발급한 불투명 ID만 Tool 입력으로 받는다.
- 실제 storage state 경로와 내용은 MCP에 노출하지 않는다.
- owner와 profile이 일치하는 상태만 사용할 수 있다.
- 세션 만료는 `CAPTURE_AUTH_FAILED`로 분류한다.

Release 1에는 session 참조를 제공하지 않는다. 인증된 JSP가 1차 업무상 필수로 확인되면 일반 SPA 기능과 묶지 않고 Release 2A를 먼저 구현한다.

#### 12.1.1 extractor Session API (R6 부분 구현)

`jsp-design-extractor`에 다음 계약으로 세션 발급 endpoint를 구현했다:

```http
POST /v1/sessions
Content-Type: application/json
X-Extractor-Key: ********
```

```json
{
  "requestId": "UUID",
  "loginUrl": "http://localhost:9091/uat/uia/egovLoginUsr.do",
  "allowedOrigins": ["http://localhost:9091"],
  "usernameSelector": "#id",
  "username": "...",
  "passwordSelector": "#password",
  "password": "...",
  "submitSelector": ".btn_login",
  "successSelector": null,
  "timeoutMillis": 15000
}
```

```json
{
  "sessionId": "UUID",
  "expiresAt": "2026-07-21T22:36:42.441Z"
}
```

- `loginUrl`은 Capture API와 동일한 origin/loopback 검증(`validateOrigin`)을 통과해야 하며, `EXTRACTOR_ALLOWED_ORIGINS`에 등록된 origin만 허용한다.
- 로그인은 extractor가 Playwright로 직접 수행한다(`usernameSelector`/`passwordSelector`에 값 입력 → `submitSelector` 클릭 → `successSelector` 또는 navigation 대기). extractor는 `username`/`password`를 로그·artifact에 남기지 않으며, 발급 로그에는 `sha256(sessionId)`만 기록한다.
- 로그인 성공 시 `context.storageState()` 결과를 **메모리 내부**(`Map<sessionId, storageState>`)에만 보관한다. 디스크에 파일로 저장하지 않아 §2 "인증정보·쿠키·토큰을 artifact와 로그에 저장하지 않는다" 원칙을 파일 기반 방식보다 더 엄격하게 만족한다.
- `EXTRACTOR_SESSION_TTL_MINUTES`(기본 30분) 경과 시 세션이 자동 만료·삭제된다(5분 주기 정리).
- `POST /v1/captures` 요청에 `storageStateRef`(발급받은 `sessionId`)를 추가하면 해당 storage state로 `browser.newContext()`를 생성해 인증 상태로 캡처한다. `storageStateRef`가 없거나 만료된 세션을 가리키면 `SESSION_NOT_FOUND` → `CAPTURE_AUTH_FAILED`(401)로 응답한다. 로그인 자체 실패는 `SESSION_LOGIN_FAILED` → `CAPTURE_AUTH_FAILED`(401)로 응답한다.
- **잔여 범위(§12.1 원 계약과의 차이)**: `springai`의 `CaptureWebPageTool`은 이제 extractor가 발급한 UUID형 불투명 `storageStateRef`를 캡처 요청에 전달한다(원문 비밀번호·쿠키·토큰 입력은 받지 않음). 세션 발급 자체는 extractor의 `POST /v1/sessions`를 호출하는 운영 경계로 유지한다. owner 단위 격리(현재는 API key 하나로 extractor 전체에 접근하는 P1 로컬 단일 사용자 모델과 동일 신뢰 경계이므로 sessionId를 아는 호출자는 누구나 재사용 가능)는 아직 남아 있다.

### 12.2 SPA

- `readySelector`, `hiddenSelector`, URL pattern과 사전 등록 interaction step을 지원한다.
- arbitrary JavaScript 문자열은 받지 않는다.
- skeleton, hydration 완료 및 지속적인 DOM mutation을 별도로 판정한다.
- 화면 상태는 `stateId`와 정규화된 step 목록으로 식별한다.

### 12.3 다중 viewport

각 viewport는 깨끗한 Browser Context에서 독립 캡처한다.

```text
RenderedDesignBundle
├─ documents[]
├─ responsiveComponents[]
├─ breakpointObservations[]
└─ warnings[]
```

원본 `RenderedDesignDocument`를 하나로 병합하지 않는다. viewport별 문서를 보존하고 별도 matcher가 공통 컴포넌트와 변형 관계를 생성한다.

### 12.4 비동기 작업

Release 2에서 실행시간과 동시성이 증가하면 다음 API로 확장한다.

```text
POST /v1/capture-jobs
GET  /v1/capture-jobs/{jobId}
POST /v1/capture-jobs/{jobId}/cancel
GET  /v1/capture-jobs/{jobId}/result
```

상태:

```text
QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, CANCELLED, EXPIRED
```

---

## 13. 테스트 명세

### 13.1 계약 테스트

- 유효한 최소/전체 JSON fixture
- 필수 필드 누락
- 알 수 없는 enum
- node/asset 참조 무결성 위반
- node tree 순환
- NaN/Infinity와 음수 크기
- 경로 탈출과 절대경로
- Java validator와 TypeScript validator의 동일 결과
- schema 파일 SHA-256 drift 검출
- `.figpack` ZIP slip, ZIP bomb, 중복 entry, 누락 entry와 hash 불일치
- `captureId`, `documentKey`, `contentHash` 의미와 중복 판정

### 13.2 extractor 단위 테스트

- DOM 순회와 제외 노드
- computed CSS 정규화
- pseudo element
- Bounding Box와 부모 상대좌표
- flex layout 추론과 absolute fallback
- component recognition evidence
- password/input value 미수집
- 직접 Text Node 변환과 부모 text 중복 방지
- `visibility:hidden`/`opacity:0` geometry 보존
- URL/redirect/resource allowlist
- service worker/WebSocket 차단
- timeout, popup, download 차단
- content hash 결정론
- 민감 selector screenshot mask

### 13.3 `springai` 단위 테스트

- `WebCapturePropertiesTest`
- `WebCaptureUrlValidatorTest`
- `WebCaptureClientTest`
- `RenderedDesignDocumentValidatorTest`
- `RenderedDesignPackageValidatorTest`
- `DesignArtifactServiceTest`
- `WebCaptureCacheKeyFactoryTest`
- `WebCaptureProjectionPolicyTest`
- `RenderedDesignSpecMapperTest`
- `WebCaptureAnalysisServiceTest`
- `WebCaptureOrchestrationServiceTest`
- `CaptureWebPageToolTest`
- `DesignArtifactToolTest`
- `DesignAnalysisResultCompatibilityTest`의 WEB_CAPTURE 호환 시나리오
- `DesignSourceMetadataTest`의 subtype/type 조합과 legacy adapter 시나리오
- WEB_CAPTURE RAG 미적재 검증

PII projection 테스트에는 table row, detail value, input value, 사용자명, 이메일, 전화번호와 `PASSWORD_HASH`, `SECRET_KEY`, `CERTDN`, `UNIQID` 같은 민감 field sentinel을 넣는다. 결과 `SafeDesignProjection`, `UiDesignSpec`, `DesignAnalysisResult`와 RAG 호출 인자 어디에도 sentinel이 존재하지 않아야 한다. 반대로 `CLOCK_TM`, `UNIQUE_NM`, `CERTIFICATE_TITLE` 같은 기존 정상 업무 필드가 과잉 차단되지 않는지도 기존 `SensitiveFieldPolicyTest`와 함께 검증한다.

### 13.4 Plugin 테스트

- package/manifest/hash/schema 거부
- preview와 사용자 취소
- 부모·자식 생성 순서
- Text font 성공과 fallback
- image/SVG 생성
- Auto Layout과 absolute fallback
- 중복 document 감지
- 생성 실패 시 임시 Frame 정리와 선택적 부분 결과 보고

### 13.5 통합 및 E2E

고정 JSP fixture를 다음 네 유형으로 준비한다.

- 목록 + 검색 + Table + Pagination
- 상세 화면
- 등록 Form
- 수정 Form

검증 흐름:

```text
fixture 서버 실행
  → extractor capture
  → springai artifact 저장
  → schema 및 hash 검증
  → UiDesignSpec 매핑
  → ScreenSpecification 생성
  → Figma Plugin import
  → `preview.png` 기준 구조·좌표·폰트·색상 수동/자동 대조
```

각 fixture에는 `quality-baseline.json`을 두고 필수 component 목록, 주요 Bounding Box 허용 오차, 색상 정규화 규칙, 허용 가능한 font/asset fallback과 Auto Layout 오탐 기준을 기록한다. 초기 tolerance는 기준 화면 실측 후 승인하며 명세 근거 없이 임의 수치로 확정하지 않는다.

---

## 14. Release 1 완료 기준

- Figma 출력 경로 ADR에서 전용 Plugin의 결정론·실행 주체·복구 경계를 승인한다.
- `DesignSourceMetadata` 공통 모델과 legacy adapter를 WEB_CAPTURE 코드보다 먼저 병합하고 기존 FILE/Figma JSON 호환 테스트를 통과한다.
- document schema는 `contentHash`를 최상위에 한 번만 가지며 `source.contentHash`를 거부한다.
- `LOCAL_WEB` 외 profile이 거부된다.
- `springai` 또는 extractor가 loopback이 아니면 enabled 기동이 거부된다.
- 허용된 JSP URL 네 유형을 반복 캡처할 수 있다.
- 동일 fixture, browser/analyzer version과 환경에서 baseline이 정한 반복 횟수만큼 `contentHash`가 유지된다.
- JSON Schema와 참조 무결성 검증을 통과한다.
- `.figpack` manifest, entry 크기, hash와 경로 검증을 통과한다.
- Header/Search/Form/Table/Pagination 등 우선 컴포넌트를 인식한다.
- 확신이 낮은 layout은 absolute fallback과 warning으로 보존된다.
- artifact를 원자적으로 저장하고 안전하게 재조회할 수 있다.
- WEB_CAPTURE 분석 결과를 기존 `createScreenSpecification` 경로에 전달할 수 있다.
- Figma Plugin이 `.figpack`으로 Desktop Frame과 자산을 생성한다.
- 폰트와 자산 fallback을 사용자에게 보고한다.
- URL, redirect, resource allowlist 및 민감정보 테스트가 통과한다.
- PII sentinel이 `SafeDesignProjection`, `UiDesignSpec`, `DesignAnalysisResult`와 RAG 호출 인자에 포함되지 않는다.
- `./gradlew test`와 `./gradlew bootJar`가 통과한다.
- extractor와 Plugin의 lint, typecheck 및 test가 통과한다.
- 운영 사이트, SPA, 다중 viewport 및 Library publish가 활성화되지 않는다.
- WEB_CAPTURE 결과가 RAG에 적재되지 않는다.
- fixture별 `quality-baseline.json`의 승인 기준을 통과한다.

---

## 15. 관측성과 운영

안전한 구조화 로그 필드:

```text
requestId
artifactId
profile
originHash
viewportName
status
durationMs
nodeCount
assetCount
warningCount
errorCode
```

금지 로그:

- 전체 URL과 query 값
- Cookie/Authorization 헤더
- storage state
- DOM 원문과 사용자 입력값
- extractor API key
- 전체 artifact JSON

Release 1은 다음 상태 점검을 제공한다.

- extractor health와 schema version 호환성
- Chromium 설치 여부
- artifact 경로 쓰기 가능 여부
- 설정된 allowed origin 수
- 기능 enabled/disabled 상태

---

## 16. 변경 이력

| 버전 | 작성일 | 변경 내용 |
|---|---|---|
| 1.7 | 2026-08-18 | `CaptureWebPageTool`/`CaptureWebPageRequest`에 extractor 발급 UUID형 `storageStateRef` 전달 지원을 추가하고, 인증 원문을 MCP 입력으로 받지 않는 경계를 명시. 세션 발급과 owner 격리는 잔여 범위로 분리 |
| 1.6 | 2026-07-22 | §5.5에 `value` 필드 확장 반영: `<select>` 현재 선택 옵션 텍스트만 예외로 캡처(스키마 `value`를 `null` 전용에서 nullableString으로 완화), `<input>`/`<textarea>`는 계속 차단. 실사용 화면(`selectBoardList.do`) 시각 검수로 발견된 `border-bottom` 전용 구분선 미캡처 문제(단일 면만 있으면 `getComputedStyle().border` 통합 shorthand가 빈 문자열을 반환하는 브라우저 특성) 대응으로 `styles.borderTop/Right/Bottom/Left` 4면 개별 캡처 추가(스키마 변경 없음, 기존 열린 string map 활용) |
| 1.5 | 2026-07-22 | §12.1.1 신설: `jsp-design-extractor`에 구현된 Session API(`POST /v1/sessions`, `storageStateRef`) 계약과 미구현 범위(owner 격리, `springai` MCP Tool 연동) 명시 — R6(Release 2A) 부분 구현 |
| 1.4 | 2026-07-21 | `05_Overall_Architecture_Diagram.md` 링크 추가 |
| 1.3 | 2026-07-21 | §3.1 시스템 경계 다이어그램에 `WebCaptureProjectionPolicy → SafeDesignProjection` 반영, §9.3 데이터 흐름과 정합화 |
| 1.2 | 2026-07-21 | `DesignSourceMetadata` 선행 전환 결정, 최상위 `contentHash` 단일화, 전용 Figma Plugin 채택 근거, PII allowlist projection 강제 반영 |
| 1.1 | 2026-07-20 | 검토 결과 반영: `.figpack`, 요청 전 네트워크 검증, loopback 배포 게이트, 식별자 분리, Release 1 인증 제외, WEB_CAPTURE RAG 미적재, 수집·QA 계약 보강 |
| 1.0 | 2026-07-20 | Release 1 JSP 단일 viewport와 Release 2 단계별 확장 구현 계약 정의 |
