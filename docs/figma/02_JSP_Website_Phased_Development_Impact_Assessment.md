# JSP → Website 단계별 개발 범위 구현 영향평가

**문서명**: 02_JSP_Website_Phased_Development_Impact_Assessment.md  
**버전**: 1.2  
**작성일**: 2026-07-21  
**상태**: 검토안  
**관련 문서**: `05_Overall_Architecture_Diagram.md` (01~04 통합 아키텍처 개요도)

---

## 1. 목적

본 문서는 구축된 웹사이트 화면을 분석하여 Figma Design Template을 자동 생성하는 기능을 다음 두 단계로 개발할 때의 범위와 구현 영향을 평가한다.

- **1차 개발**: 기존 JSP 기반 화면 분석
- **2차 개발**: 개발 또는 운영 환경에서 접근 가능한 기존 웹사이트 화면 분석

1차에서는 지원 대상을 JSP로 제한하되, 데이터 모델과 분석 인터페이스는 특정 서버 기술에 종속되지 않도록 설계한다. 2차에서는 1차 분석 엔진을 교체하지 않고 입력 환경, 인증, 보안, 동적 화면 및 반응형 분석 기능을 확장하는 것을 기본 원칙으로 한다.

---

## 2. 결론

단계별 개발 방식은 적절하다. 다만 다음 두 원칙을 적용해야 한다.

1. 1차 지원 범위는 JSP로 제한하지만 구현 구조는 렌더링된 웹페이지를 공통 입력으로 사용한다.
2. 2차에서는 분석 로직을 재작성하지 않고 접근 범위, 인증, 보안, SPA, 동적 상태 및 다중 viewport 기능을 확장한다.

권장 발전 경로는 다음과 같다.

```text
1차 LOCAL_JSP
  └─ 단일 viewport, 서버 렌더링, 허용된 로컬·개발 URL

2차 DEVELOPMENT_WEBSITE
  └─ SPA, 인증 세션, 다중 viewport, 동적 상태

2차 확장 AUTHORIZED_PRODUCTION_WEBSITE
  └─ 운영 보안, 개인정보 보호, 감사, 사용자·tenant 격리
```

전체 구현 영향도는 다음과 같다.

| 영역 | 영향도 | 평가 |
|---|---|---|
| 전체 아키텍처 | 중간 | 기존 Spring AI MCP를 오케스트레이터로 유지하고 브라우저 분석기를 별도 실행 모듈로 분리 |
| 브라우저 수집 | 높음 | 렌더링 완료 조건, 동적 콘텐츠, 브라우저 프로세스 관리 필요 |
| URL·인증·보안 | 높음 | SSRF, 세션, 운영 사이트 접근 권한, 개인정보 통제 필요 |
| Design JSON | 높음 | 현재 `UiDesignSpec`보다 정밀한 렌더링 문서 모델 필요 |
| Figma Plugin | 낮음~중간 | 공통 JSON 계약을 유지하면 1·2차에서 동일 플러그인 재사용 가능 |
| 기존 화면명세·코드 생성 | 중간 | 신규 렌더링 문서를 기존 `UiDesignSpec`으로 변환하는 어댑터 필요 |

---

## 3. 권장 문서 목적 문구

상위 가이드의 목적 문장은 다음과 같이 변경하는 것을 권장한다.

> 본 문서는 구축된 웹사이트 화면을 분석하여 Figma Design Template을 자동 생성하는 방법을 정의한다. 1차 개발에서는 기존 JSP 기반 화면을 대상으로 하며, 2차 개발에서는 개발 또는 운영 환경에서 접근 가능한 기존 웹사이트 화면으로 분석 범위를 확장한다.

상위 문서의 장기적인 제목은 다음과 같이 기술 중립적으로 변경할 수 있다.

```text
Website → Figma Design Template 자동 생성 구현 가이드
```

---

## 4. 단계별 범위 비교

| 구분 | 1차 개발 | 2차 개발 |
|---|---|---|
| 주요 대상 | 기존 JSP 기반 화면 | 개발·운영 환경의 기존 웹사이트 |
| 서버 기술 | JSP/eGovFrame 중심 | JSP, Thymeleaf, 정적 HTML, React, Vue, Angular 등 |
| 실행 환경 | 로컬 또는 지정 개발 서버 | 로컬·개발·승인된 운영 환경 |
| 입력 | 허용된 JSP 화면 URL | 허용 목록에 등록된 HTTP/HTTPS URL |
| 렌더링 | 전통적인 서버 렌더링 DOM | SSR, CSR, SPA, 비동기 데이터 |
| 인증 | 비인증 또는 사전 생성 세션 | SSO, MFA, 서비스 계정 등 별도 정책 필요 |
| Viewport | 데스크톱 단일 규격 | 데스크톱·태블릿·모바일 |
| 화면 상태 | 초기 로딩 화면 | 탭, 모달, 메뉴, 스크롤 등 지정 상태 |
| iframe | 원칙적으로 제외 | 동일 출처부터 제한 지원 |
| Canvas·Shadow DOM | 제외 | 별도 지원 여부 결정 |
| 보안 경계 | 로컬 단일 사용자 | 운영 환경 인증, 인가, 감사 및 격리 필요 |

---

## 5. 공통 아키텍처

### 5.1 권장 구성

```text
springai
  ├─ MCP Tool
  ├─ 작업 오케스트레이션
  ├─ URL 및 입력 정책 검증
  ├─ Design Artifact 메타데이터 관리
  ├─ RenderedDesignDocument 검증
  ├─ RenderedDesignDocument → UiDesignSpec 변환
  └─ 기존 ScreenSpecification 및 코드 생성 연동

jsp-design-extractor
  ├─ Playwright 브라우저 실행
  ├─ DOM 및 computed CSS 수집
  ├─ 좌표와 레이아웃 분석
  ├─ 자산 수집
  └─ RenderedDesignDocument 생성

jsp-to-figma-plugin
  ├─ RenderedDesignDocument 가져오기
  ├─ Frame 및 Auto Layout 생성
  ├─ Text, Image, Component 생성
  └─ Style 및 Variable 생성
```

브라우저 분석기와 Figma Plugin은 각각 별도 TypeScript 프로젝트로 관리하는 것을 권장한다. 현재 `springai` 프로젝트는 MCP 오케스트레이션, 결과 검증, 화면명세 및 코드 생성 책임을 유지한다.

### 5.2 처리 흐름

```text
허용된 웹페이지 URL
  → Playwright 렌더링
  → DOM·computed CSS·좌표·자산 수집
  → 컴포넌트·레이아웃 추론
  → RenderedDesignDocument 생성
  ├─→ Figma Plugin → Figma Design Template
  └─→ UiDesignSpec → ScreenSpecification → JSP/Thymeleaf 생성
```

---

## 6. 1차 개발 범위

### 6.1 지원 대상

1차에서는 다음 조건을 만족하는 JSP 화면을 지원한다.

- Tomcat 또는 eGovFrame 서버가 이미 실행 중이다.
- Playwright가 허용된 URL로 화면에 접근할 수 있다.
- 화면이 HTML DOM과 CSS를 중심으로 구성되어 있다.
- CSS, 이미지 및 웹 폰트가 정상적으로 로딩된다.
- 데스크톱 단일 viewport를 사용한다.
- 목록, 상세, 등록 및 수정 화면을 우선 지원한다.
- 비인증 화면 또는 사전에 준비된 로그인 세션을 사용한다.
- 동일 출처 자산을 우선 지원한다.

### 6.2 분석 기준

JSP 파일 자체를 정적으로 파싱하지 않고 실행 결과를 분석한다.

```text
JSP 소스 직접 분석
  → 지원하지 않음

JSP 실행 결과 URL
  → Playwright
  → 최종 DOM·computed CSS·좌표 분석
  → 지원
```

JSP 소스에는 include, tag library, JSTL 조건문, Tiles 및 서버 데이터 바인딩이 포함될 수 있으므로 소스만으로 실제 렌더링 결과를 확정하기 어렵다.

### 6.3 우선 인식 컴포넌트

- Header
- Footer
- Navigation
- Breadcrumb
- Search Panel
- Toolbar
- Form
- Input
- Select
- Checkbox
- Radio
- Button
- Data Grid/Table
- Pagination

### 6.4 제외 범위

- 운영 사이트 직접 접근
- 임의 URL 입력
- SPA 라우팅 및 복잡한 비동기 상태
- SSO와 MFA 자동 로그인
- 반응형 다중 viewport
- cross-origin iframe
- Canvas 내부의 의미 구조 분석
- Shadow DOM 심층 분석
- 모든 페이지 자동 크롤링
- Figma Component Library 자동 publish
- React 코드 생성

### 6.5 완료 기준

- 동일 입력과 동일 캡처 조건에서 구조적으로 일관된 JSON을 생성한다.
- 목록, 상세, 등록 및 수정 JSP 화면을 식별한다.
- 주요 컴포넌트와 기본 디자인 토큰을 추출한다.
- Figma Frame, Text, Image 및 기본 Component를 생성한다.
- 확실한 flex 구조는 Auto Layout으로 변환한다.
- Auto Layout으로 확정할 수 없는 배치는 absolute layout으로 보존하고 경고를 기록한다.
- 폰트, 이미지 및 외부 자산 누락을 명시적으로 보고한다.
- Java와 TypeScript 구현이 동일한 JSON Schema 계약 테스트를 통과한다.
- 허용되지 않은 URL 접근을 차단한다.
- 세션, 쿠키, 토큰 및 개인정보를 로그에 출력하지 않는다.

---

## 7. 2차 개발 범위

### 7.1 지원 대상 확장

2차에서는 다음 웹사이트 유형을 단계적으로 지원한다.

- JSP 및 Thymeleaf 기반 서버 렌더링 사이트
- 정적 HTML 사이트
- React, Vue, Angular 등 SPA
- 비동기 API 데이터를 사용하는 화면
- lazy loading 및 infinite scroll 화면
- client-side routing을 사용하는 화면
- 사용자가 승인한 개발 또는 운영 환경의 화면

### 7.2 렌더링 완료 판정

일반 웹사이트는 `load` 이벤트만으로 렌더링 완료를 판단할 수 없다. 다음 조건을 조합한다.

```text
페이지 이동
  → DOMContentLoaded
  → 네트워크 안정화
  → 웹 폰트 로딩 완료
  → 지정 요소 표시 대기
  → lazy content 로딩
  → 애니메이션 정지
  → DOM·CSS·Layout 수집
```

사이트 특성에 따라 `readySelector`, 최대 대기시간, 스크롤 범위 및 사용자 정의 준비 스크립트를 옵션으로 제공할 수 있다. 사용자 정의 스크립트는 임의 코드 실행 위험이 있으므로 로컬 신뢰 프로필에서만 허용하거나 사전 등록된 동작으로 제한해야 한다.

### 7.3 인증 확장

인증 지원 우선순위는 다음과 같다.

1. 비인증 공개 페이지
2. Playwright `storageState` 기반 세션
3. 사용자가 로그인한 캡처 전용 브라우저 프로필
4. 캡처 전용 서비스 계정
5. 사용자 개입이 필요한 SSO/MFA 흐름

아이디, 비밀번호, 세션 쿠키 또는 접근 토큰을 MCP 인자로 직접 전달하지 않는다. 인증정보는 별도 Secret 저장소 또는 제한된 로컬 프로필에 보관하며 로그와 Design Artifact에서 제거한다.

### 7.4 반응형 분석

2차에서는 하나의 URL을 여러 viewport로 수집할 수 있다.

```json
{
  "viewports": [
    {"name": "desktop", "width": 1440, "height": 1200},
    {"name": "tablet", "width": 768, "height": 1024},
    {"name": "mobile", "width": 390, "height": 844}
  ]
}
```

분석 결과에서는 다음을 구분한다.

- 모든 viewport에서 공통인 컴포넌트
- 위치 또는 크기만 달라지는 컴포넌트
- 특정 breakpoint에서 숨겨지는 컴포넌트
- 모바일에서 다른 형태로 교체되는 Navigation
- 열 개수가 달라지는 Grid 및 Form
- Table에서 Card 형태로 변경되는 반응형 패턴

### 7.5 동적 화면 상태

초기 화면 외 상태는 명시적으로 지정한다.

- 탭 선택
- 모달 열기
- 드롭다운 펼치기
- Accordion 펼치기
- 페이지 스크롤
- hover, focus, disabled 상태
- 특정 라우트 또는 query parameter

자동으로 모든 조합을 탐색하면 상태 폭발이 발생하므로, 2차에서도 사용자가 지정한 상태와 사전 정의된 시나리오를 우선한다.

---

## 8. 공통 데이터 모델

### 8.1 모델 분리 원칙

기존 `UiDesignSpec`은 화면명세와 코드 생성을 위한 의미 중심 모델이다. Figma 화면을 재현하기 위한 노드 좌표, 상세 스타일, 자산 및 viewport 정보를 모두 표현하기에는 부족하다.

따라서 다음 두 모델을 분리한다.

```text
RenderedDesignDocument
  ├─ 시각 재현용 정밀 모델
  ├─ Figma Plugin 입력
  └─ DOM, 좌표, 스타일, 자산, viewport 포함

UiDesignSpec
  ├─ 의미 기반 화면 요약
  ├─ ScreenSpecification 입력
  └─ 기존 JSP/Thymeleaf 코드 생성에 사용
```

### 8.2 권장 RenderedDesignDocument 구조

```json
{
  "schemaVersion": "rendered-design-document-v1",
  "contentHash": "sha256:...",
  "source": {
    "type": "RENDERED_WEB_PAGE",
    "applicationKind": "JSP",
    "requestedUrl": "http://localhost:8080/sample.do",
    "finalUrl": "http://localhost:8080/sample.do",
    "capturedAt": "2026-07-20T12:00:00+09:00"
  },
  "environment": {
    "viewportWidth": 1440,
    "viewportHeight": 1200,
    "deviceScaleFactor": 1,
    "locale": "ko-KR",
    "timezone": "Asia/Seoul",
    "colorScheme": "light",
    "reducedMotion": true
  },
  "nodes": [],
  "assets": [],
  "tokens": {},
  "componentCandidates": [],
  "interactions": [],
  "warnings": []
}
```

`applicationKind`는 참고용이며 분석기의 핵심 동작을 결정하지 않는다.

```text
JSP
THYMELEAF
STATIC_HTML
SPA
UNKNOWN
```

분석기가 서버 기술을 확인할 수 없으면 `UNKNOWN`으로 기록한다. 서버 기술을 확정하지 못해도 렌더링된 화면 분석은 계속 수행한다.

### 8.3 필수 노드 정보

- 노드 ID 및 부모·자식 관계
- DOM tag와 제한된 selector
- 텍스트와 접근성 역할
- `x`, `y`, `width`, `height`
- display, position, flex 및 grid 속성
- margin, padding, gap
- border, radius, background
- font family, size, weight, line height
- shadow, opacity, overflow, z-index
- 이미지, SVG 및 폰트 자산 참조
- 컴포넌트 추론 결과와 confidence
- Auto Layout 추론 결과와 confidence
- 분석 불확실성 및 누락 경고

---

## 9. MCP Tool 설계 영향

### 9.1 Tool 이름

1차 범위가 JSP이더라도 `RenderJspTool`처럼 특정 기술을 포함한 이름은 사용하지 않는 것이 좋다.

권장 Tool은 다음과 같다.

```text
CaptureWebPageTool
DesignArtifactTool
```

`FigmaImportPreparationTool`을 별도 클래스로 분리하는 대신 `prepareFigmaImport`를 `DesignArtifactTool`에 통합하는 것으로 03번 문서에서 최종 확정했다. Tool 구성의 최종 기준은 `03_Website_To_Figma_Implementation_Specification.md` §8.4를 따른다.

### 9.2 외부 Tool과 내부 분석기 분리

MCP에는 상위 작업만 노출한다.

```java
captureWebPage(targetUrl, captureProfile, options)
getDesignArtifact(artifactId)
prepareFigmaImport(artifactId)
```

다음 분석기는 extractor 내부 서비스로 둔다.

- DOM Analyzer
- CSS Analyzer
- Layout Analyzer
- Component Recognizer
- Pattern Analyzer
- Design Document Generator

중간 DOM과 CSS 결과는 크기가 크므로 각 분석 단계를 개별 MCP Tool로 노출하지 않는다. MCP Tool은 중간 전체 JSON 대신 `artifactId`, 상태, 요약 및 경고를 반환한다.

### 9.3 Capture Profile

```text
LOCAL_JSP
DEVELOPMENT_WEBSITE
AUTHORIZED_PRODUCTION_WEBSITE
```

프로필별로 URL 정책, 인증, 보존기간, 브라우저 옵션 및 허용 기능을 분리한다.

---

## 10. 보안 영향

### 10.1 공통 URL 통제

- `http`, `https` scheme만 허용한다.
- 허용 domain 또는 origin 목록을 적용한다.
- redirect 이후 최종 URL을 다시 검증한다.
- 파일 URL과 브라우저 내부 URL을 거부한다.
- DNS 재해석과 사설망 접근 정책을 적용한다.
- localhost 접근은 명시적인 로컬 개발 프로필에서만 허용한다.
- 다운로드와 새 창 생성을 기본 차단한다.
- 페이지별 실행시간과 브라우저 메모리를 제한한다.

### 10.2 운영 사이트 추가 통제

2차에서 운영 사이트를 지원하기 전에 다음 조건을 충족해야 한다.

- 대상 사이트 소유권 또는 분석 권한 확인
- 운영망 접근 승인
- 사용자별 인증 및 객체 단위 인가
- 캡처 전용 실행 에이전트 검토
- 스크린샷, HTML 및 Design JSON 보존기간 정의
- 개인정보 및 민감정보 마스킹
- 작업 주체와 대상 URL에 대한 감사 로그
- 외부 이미지, 폰트 및 디자인 자산 사용 권한 확인
- tenant별 작업 결과, 캐시 및 저장소 격리

운영망 내부 사이트는 중앙 `springai` 서버가 직접 방문하기보다 해당 네트워크 안에서 실행되는 capture agent가 분석하고 정제된 산출물만 전달하는 구조를 우선 검토한다.

---

## 11. 자산 처리 영향

일반 웹사이트는 다음 외부 자산을 포함할 수 있다.

- CDN 이미지
- CSS background image
- SVG sprite
- icon font
- web font
- CSS `@import`
- blob 및 data URL
- 인증이 필요한 이미지
- JavaScript로 그린 Canvas

자산별로 다음 처리 정책을 정의한다.

```text
직접 다운로드
Figma Image Fill로 변환
제한된 로컬 캐시
원본 URL 참조만 유지
권한 문제로 제외
placeholder로 대체
```

사이트 열람 권한과 디자인 또는 자산의 복제·재배포 권한은 다를 수 있다. 운영 사이트를 분석할 때에는 사용 권한 확인을 입력 계약과 운영 절차에 포함한다.

---

## 12. 테스트 전략

### 12.1 1차 테스트

- 대표 JSP 목록·상세·등록·수정 fixture
- 고정 DB 데이터
- 고정 viewport, locale, timezone 및 color scheme
- DOM 및 computed CSS 수집 단위 테스트
- RenderedDesignDocument JSON Schema 테스트
- Java·TypeScript 교차 계약 테스트
- 컴포넌트 인식 golden test
- Figma Plugin import 테스트
- URL allowlist 및 redirect 차단 테스트
- 민감정보 로그 노출 테스트

### 12.2 2차 추가 테스트

- SSR 및 SPA 비교 fixture
- 비동기 렌더링 완료 테스트
- 다중 viewport 및 breakpoint 테스트
- storageState 인증 테스트
- 세션 만료 및 재인증 실패 테스트
- iframe, Shadow DOM 및 Canvas 제한 동작 테스트
- redirect, DNS 변경 및 내부망 접근 방어 테스트
- tenant별 artifact 격리 테스트
- 개인정보 마스킹 테스트

### 12.3 품질 평가 원칙

전체 픽셀 일치율 하나만 사용하지 않고 다음 지표를 분리한다.

- 노드 계층 보존
- 주요 컴포넌트 인식 정확도
- Bounding Box 오차
- typography 및 색상 토큰 일치
- Auto Layout 추론 정확도
- 이미지 및 폰트 누락률
- 잘못된 자동 확정 비율
- Figma import 성공률

확신할 수 없는 레이아웃은 잘못된 Auto Layout으로 확정하지 않고 absolute layout과 경고로 보존한다.

---

## 13. 2차 착수 게이트

다음 조건을 모두 충족한 후 2차 개발에 착수한다.

- `RenderedDesignDocument v1` 규격 안정화
- 대표 JSP 화면의 반복 가능한 변환 성공
- Figma Plugin import 안정화
- Auto Layout 추론 실패 시 fallback 검증
- 브라우저 프로세스 격리와 타임아웃 검증
- URL allowlist 및 redirect 검증 완료
- 인증정보 로그 노출 방지 검증
- artifact 저장, 보존 및 삭제 정책 확정
- 개발·운영 사이트 분석 권한 정책 확정
- 운영 사이트 보안 ADR 승인
- 다중 사용자 운영 시 인증·인가 및 tenant 격리 완료

---

## 14. 예상 변경 대상

### 14.1 현재 `springai` 프로젝트

| 구분 | 예상 변경 |
|---|---|
| MCP Tool | `CaptureWebPageTool`, `DesignArtifactTool` 추가 |
| 설정 | capture profile, URL allowlist, timeout, artifact 보존 설정 |
| 모델 | artifact 메타데이터 및 공통 요청·응답 모델 추가 |
| 서비스 | extractor client, schema validator, artifact service 추가 |
| 변환 | `RenderedDesignDocument → UiDesignSpec` 어댑터 추가 |
| 보안 | URL 검증, 민감정보 제거, 사용자·tenant 정책 확장 |
| 테스트 | Tool, 서비스, JSON 계약 및 보안 테스트 추가 |

### 14.2 별도 `jsp-design-extractor` 프로젝트

| 단계 | 예상 구현 |
|---|---|
| 1차 | JSP URL, 단일 viewport, DOM/CSS/Layout, 기본 자산과 컴포넌트 분석 |
| 2차 | SPA, 인증, 다중 viewport, 동적 상태, 운영 capture agent 및 강화된 격리 |

### 14.3 별도 `jsp-to-figma-plugin` 프로젝트

| 구분 | 예상 구현 |
|---|---|
| 입력 | JSON 파일 또는 허용된 localhost artifact endpoint |
| 생성 | Frame, Text, Image, Component, Auto Layout |
| 디자인 시스템 | Style, Variable, 로컬 Component 생성 |
| 안전장치 | preview, 사용자 승인, 중복 생성 및 갱신 정책 |

---

## 15. 최종 권고

1차와 2차를 별도 Release로 정의하되 공통 렌더링 문서 계약을 사용한다.

1차에서는 JSP 화면의 안정적인 수집과 Figma 변환 품질에 집중한다. JSP 서버 기동, 임의 사이트 크롤링, 복잡한 인증 및 반응형 분석은 범위에서 제외한다.

2차에서는 검증된 1차 파이프라인 위에 일반 웹사이트 지원을 추가한다. 특히 운영 사이트 지원은 단순 기능 확장이 아니라 인증, 인가, SSRF 방어, 개인정보, 감사 및 데이터 격리가 포함된 별도 보안 Release로 취급한다.

이 구조를 적용하면 1차 결과물을 폐기하지 않고 다음과 같이 확장할 수 있다.

```text
1차 JSP 화면 분석
  → 공통 RenderedDesignDocument 확립
  → Figma Plugin 검증
  → 기존 UiDesignSpec·ScreenSpecification 연동

2차 일반 웹사이트 분석
  → 입력·인증·보안 범위 확장
  → SPA·반응형·동적 상태 추가
  → 운영 환경 통제 적용
```

---

## 16. 문서 변경 이력

| 버전 | 작성일 | 변경 내용 |
|---|---|---|
| 1.2 | 2026-07-21 | `05_Overall_Architecture_Diagram.md` 링크 추가 |
| 1.1 | 2026-07-21 | 03번 문서 v1.2와 일치하도록 §8.2 `contentHash` 최상위 위치 수정, §9.1 Tool 구성을 2개 클래스로 정정 |
| 1.0 | 2026-07-20 | JSP 1차, 개발·운영 웹사이트 2차 개발 범위 및 구현 영향평가 작성 |
