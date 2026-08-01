# FigmaScreenSpec과 `.figpack` 개념 및 역할 비교 분석

> 작성일: 2026-07-31  
> 대상 프로젝트: `springai`  
> 문서 목적: `FigmaScreenSpec`, `.figpack`, `FigmaExportBundle`의 책임과 사용 경계를 명확히 구분한다.

## 1. 결론

`FigmaScreenSpec`과 `.figpack`은 모두 Figma 화면 생성에 사용되지만, 같은 데이터를 다른 형식으로 표현한 것이 아니다.  
`가장 정확한 한국어 표현은 “Figma 변환용 렌더링 캡처 패키지” 또는 **“Figma 가져오기 패키지”**입니다.

- `FigmaScreenSpec`은 **업무 화면의 의미와 디자인 시스템 사용 의도**를 표현하는 JSON 계약이다.
- `.figpack`은 **브라우저에 실제 렌더링된 화면의 구조·좌표·스타일·이미지 자산**을 함께 운반하는 ZIP 패키지다.
- `FigmaExportBundle`은 `FigmaScreenSpec`을 오프라인으로 실행하는 데 필요한 **Design System Profile과 Component Registry 스냅샷까지 묶은 별도의 JSON 전달 계약**이다.

따라서 기본 용도는 다음처럼 나뉜다.

```text
기존 웹 화면과 최대한 비슷하게 복제
    → .figpack

KRDS 등 디자인 시스템 컴포넌트로 업무 화면 재구성
    → FigmaScreenSpec

FigmaScreenSpec을 Profile·Registry와 함께 파일 하나로 전달
    → FigmaExportBundle
```

## 2. 전체 관계

### 2.1 시각적 복제 경로

```text
실행 중인 웹 화면
    ↓ Chromium 렌더링
DOM + computed style + 좌표 + 이미지 수집
    ↓
RenderedDesignDocument
    ↓ ZIP 패키징
.figpack
    ↓ Figma Plugin
Frame · Text · Rectangle · Image 생성
```

이 경로의 중심 질문은 다음과 같다.

> “현재 브라우저에 보이는 화면을 Figma에서 얼마나 비슷하게 재현할 수 있는가?”

### 2.2 의미 기반 디자인 시스템 경로

```text
승인된 ScreenSpecification
    ↓ Projection
FigmaScreenSpec
    ↓ Component Registry 해석
KRDS Team Library Component 연결
    ↓ Figma Plugin
Component Instance + Auto Layout 기반 화면 생성
```

이 경로의 중심 질문은 다음과 같다.

> “이 업무 화면을 조직의 디자인 시스템 컴포넌트로 어떻게 구성해야 하는가?”

### 2.3 하이브리드 경로

```text
.figpack의 document.json
    ↓ 참조 화면 분석
ScreenSpecification 후보
    ↓ 사용자 검토·수정·승인
FigmaScreenSpec
    ↓
디자인 시스템 기반 Figma 화면
```

`.figpack`은 기존 화면의 시각적 사실을 제공하고, `FigmaScreenSpec`은 그 사실을 업무 의미와 디자인 시스템 구조로 재해석한다. 두 결과는 경쟁 관계가 아니라 보완 관계다.

## 3. `FigmaScreenSpec` 개념

### 3.1 정의

`FigmaScreenSpec`은 승인된 `ScreenSpecification`을 Figma 업무 화면 생성에 적합한 **논리 컴포넌트 트리**로 변환한 출력 계약이다.

원본 `ScreenSpecification` 전체를 Figma에 직접 노출하지 않고, Figma 생성에 필요한 정보만 별도 DTO로 Projection한다.

구현 위치:

- `src/main/java/com/krdevops/springai/model/figma/FigmaScreenSpec.java`
- Schema version: `figma-screen-spec-v1`

### 3.2 주요 필드

| 필드 | 역할 |
|---|---|
| `screenId` | Figma 출력 화면의 안정적인 식별자 |
| `screenVersion` | 동일 화면 출력의 버전 |
| `screenSpecificationId` | 원본 화면 명세 추적 식별자 |
| `screenSpecificationVersion` | 어떤 원본 명세 버전에서 생성됐는지 표시 |
| `screenType` | LIST, FORM, DETAIL 등 화면의 업무 유형 |
| `layoutPattern` | STANDARD 등 화면 배치 패턴 |
| `name` | 화면 이름 |
| `route` | 화면과 연결되는 애플리케이션 URL |
| `viewport` | 생성 대상 Viewport |
| `status` | DRAFT, APPROVED 등 생성·승인 상태 |
| `designSystem` | 사용할 Profile과 Registry 버전 참조 |
| `content` | `FigmaNodeSpec` 기반 논리 컴포넌트 트리 |
| `issues` | 생성 차단·경고·참고 사항 |

개념적인 예시는 다음과 같다.

```json
{
  "screenId": "employee-list",
  "screenVersion": 3,
  "screenSpecificationId": "employee-management",
  "screenSpecificationVersion": 5,
  "screenType": "LIST",
  "layoutPattern": "STANDARD",
  "name": "사용자 목록",
  "route": "/employee/list.do",
  "viewport": "DESKTOP",
  "status": "APPROVED",
  "designSystem": {
    "profileId": "krds-admin",
    "profileVersion": "1.4.0",
    "registryVersion": "2026.07"
  },
  "content": {
    "logicalType": "screen.list",
    "children": [
      {
        "logicalType": "krds.search-panel"
      },
      {
        "logicalType": "krds.data-table"
      },
      {
        "logicalType": "krds.button",
        "properties": {
          "label": "등록",
          "variant": "primary"
        }
      }
    ]
  },
  "issues": []
}
```

이 예시는 개념 설명용이며 실제 직렬화 필드는 프로젝트 JSON Schema를 기준으로 해야 한다.

### 3.3 핵심 역할

#### 업무 의미 보존

`FigmaScreenSpec`은 단순 좌표보다 “이 노드가 무엇인가”를 중요하게 다룬다.

```text
x=120, y=80, width=96, height=40인 파란 사각형
    → 시각적 표현

logicalType=krds.button
label=등록
variant=primary
action=create
    → 업무·컴포넌트 의미
```

#### 디자인 시스템 연결

`designSystem` 참조와 각 노드의 `logicalType`을 통해 `Component Registry`에서 실제 Figma Component Key를 찾는다.

```text
logicalType: krds.button
    ↓ Component Registry
Figma Team Library의 Button Component Key
    ↓ Property Mapper
label=등록, variant=primary
    ↓
Figma Component Instance
```

#### 코드와 디자인의 추적성 제공

`screenSpecificationId`, `screenSpecificationVersion`, `screenId`, `screenVersion`을 통해 다음 관계를 추적할 수 있다.

```text
업무 화면 명세
    ↕
eGovFrame JSP/Thymeleaf 코드
    ↕
Figma 업무 화면
```

#### 검증 가능한 출력 계약 제공

Java Bean Validation, JSON Schema 검증, 의미 검증을 통해 다음 오류를 탐지할 수 있다.

- 필수 식별자 누락
- 지원하지 않는 논리 컴포넌트 타입
- Registry에 없는 컴포넌트
- 허용되지 않는 속성 또는 Variant
- 버전 불일치
- BLOCK 수준 Issue가 남은 화면의 Apply 시도

### 3.4 장점

- KRDS 등 조직 디자인 시스템을 일관되게 적용할 수 있다.
- 일반 Frame보다 Component Instance 중심으로 생성할 수 있다.
- 디자이너가 Team Library 변경사항을 반영하기 쉽다.
- 화면 의미가 유지되어 후속 수정과 자동 검증이 쉽다.
- 코드 생성 명세와 Figma 생성 명세의 추적 관계를 유지할 수 있다.
- 동일한 Spec과 Registry 버전을 사용하면 재현성이 높다.

### 3.5 한계

- Registry에 등록되지 않은 컴포넌트는 정확히 생성하기 어렵다.
- 기존 화면의 미세한 시각적 차이를 그대로 복제하는 용도에는 적합하지 않다.
- 잘못된 의미 추론이 들어가면 시각적으로 그럴듯해도 업무 구조가 틀릴 수 있다.
- `FigmaScreenSpec`만 파일로 전달하면 Profile과 Registry의 실제 내용이 포함되지 않아 별도 조회가 필요하다.

## 4. `.figpack` 개념

### 4.1 정의

`.figpack`은 실행 중인 웹 화면을 브라우저로 캡처한 결과를 프로젝트 사이에서 안전하게 전달하기 위한 **ZIP 기반 바이너리 패키지**다.

구현 계약:

- Package version: `figpack-v1`
- MIME type: `application/vnd.springai.figpack+zip`
- 서버 저장 원본명: `source.figpack`

`.figpack`은 확장자만 별도일 뿐 내부적으로는 여러 JSON·이미지 파일을 포함하는 ZIP 컨테이너다.

### 4.2 기본 구조

```text
{captureId}.figpack
├─ manifest.json
├─ document.json
├─ preview.png
└─ assets/
   ├─ {contentHash}.png
   ├─ {contentHash}.jpg
   └─ {contentHash}.svg
```

### 4.3 구성 파일별 역할

#### `manifest.json`

패키지 전체의 무결성과 호환성을 판단하는 인덱스다.

주요 정보:

- Package version과 MIME type
- Capture ID와 Document Key
- 전체 Content Hash
- Document Schema version과 Schema SHA-256
- 각 Entry의 상대경로
- 각 Entry의 MIME type
- 각 Entry의 바이트 크기와 SHA-256
- Node·Asset·Component·Warning 요약
- Extractor와 Browser 버전

#### `document.json`

브라우저에서 수집한 렌더링 구조의 중심 문서다.

대표 정보:

- DOM에서 유도한 노드 계층
- 각 노드의 좌표와 크기
- computed style
- 텍스트와 이미지 참조
- 레이아웃 분석 결과
- 컴포넌트 인식 후보
- 디자인 토큰 후보
- 정적으로 확인 가능한 Interaction
- Warning과 출처 메타데이터

#### `preview.png`

캡처 결과를 사람이 빠르게 확인하기 위한 기준 이미지다.

- 민감 Selector가 마스킹된 QA 이미지다.
- Figma 노드를 만드는 필수 데이터는 아니다.
- Plugin 결과와 원본 화면의 시각적 차이를 비교할 때 유용하다.

#### `assets/`

JSON에 직접 포함하기 부적절한 이미지와 SVG 바이너리를 담는다.

- 파일명은 Content Hash를 기반으로 한다.
- `document.json`에서는 패키지 내부 상대경로로 참조한다.
- 외부 로컬 절대경로를 프로젝트 사이에 공유하지 않는다.

### 4.4 핵심 역할

#### 렌더링 사실 보존

`.figpack`은 “업무적으로 무엇을 의미하는가”보다 “브라우저가 실제로 무엇을 그렸는가”를 보존한다.

```text
DOM 구조
+ computed CSS
+ 실제 좌표
+ 실제 텍스트
+ 이미지 자산
+ 캡처 환경 버전
```

#### 프로젝트 간 이식 가능한 전달

Extractor, Spring Boot 서버, Figma Plugin이 동일한 로컬 디렉터리를 공유하지 않아도 하나의 파일로 결과를 이동할 수 있다.

#### 무결성 및 공급망 검증

현재 `RenderedDesignPackageValidator`는 다음 항목을 검증한다.

- 압축 파일 최대 크기
- Package version과 MIME type
- Capture ID와 Document Key 일치
- Manifest Entry 중복
- 필수 Entry 누락
- 선언되지 않은 Entry 존재
- Entry 바이트 크기와 SHA-256
- Asset 메타데이터와 실제 파일 일치
- Manifest와 Document의 Content Hash 일치
- 절대경로와 `..` 경로 차단
- Case-folding 충돌
- Entry 수와 압축 해제 총크기 제한

이 검증은 ZIP Slip, ZIP Bomb, 파일 바꿔치기, 손상된 다운로드 같은 위험을 줄인다.

#### 시각적 복제 입력

Figma Plugin은 `document.json`과 `assets/`를 사용해 일반 Figma 노드를 만든다.

```text
HTML div      → Frame 또는 Rectangle
텍스트 노드  → Figma Text
이미지       → Figma Image Fill
CSS 배치     → 좌표 또는 Auto Layout 후보
```

### 4.5 장점

- 기존 JSP·Thymeleaf 화면과 시각적으로 가까운 결과를 만들기 쉽다.
- 캡처 시점의 좌표, 스타일, 텍스트, 자산을 함께 보존한다.
- 이미지가 포함된 복합 화면을 파일 하나로 전달할 수 있다.
- Hash와 Manifest를 이용해 무결성을 강하게 검증할 수 있다.
- Preview를 이용한 원본 대비 시각 QA가 가능하다.
- 디자인 시스템에 없는 레거시 화면도 캡처할 수 있다.

### 4.6 한계

- 생성 결과가 일반 Frame·Text·Rectangle 중심이 될 수 있다.
- 시각적으로 비슷해도 KRDS Component Instance와 연결되지 않을 수 있다.
- 원본의 비정상적인 CSS와 임시 스타일도 함께 복제할 수 있다.
- 반응형 의미나 업무 의미가 좌표 정보에 묻힐 수 있다.
- 캡처 시점과 Viewport에 종속된다.
- 화면 텍스트와 이미지에 개인정보 또는 민감정보가 포함될 수 있어 마스킹과 보관 정책이 필요하다.

## 5. 상세 비교

| 비교 항목 | `FigmaScreenSpec` | `.figpack` |
|---|---|---|
| 본질 | 의미 기반 JSON 명세 | 렌더링 캡처 ZIP 패키지 |
| 핵심 목적 | 디자인 시스템 기반 재구성 | 현재 화면의 시각적 복제 |
| 출발점 | 승인된 `ScreenSpecification` | 실행 중인 웹 URL |
| 생성 주체 | Spring의 Figma Projection·Export Service | Playwright/Chromium 기반 Extractor와 서버 |
| 중심 정보 | 화면 유형, 논리 노드, 컴포넌트 속성, Design System 참조 | DOM, computed style, 좌표, 텍스트, 이미지 |
| 계층 표현 | 논리 컴포넌트 트리 | 렌더링 노드 트리 |
| 위치 표현 | Layout Pattern·Auto Layout 의도 중심 | 캡처 시점의 실제 좌표 중심 |
| 디자인 시스템 | 핵심 전제 | 선택적 후처리 대상 |
| Figma 결과 | Component Instance 중심 | Frame·Text·Rectangle·Image 중심 |
| 자산 포함 | 기본적으로 외부 참조 또는 별도 전달 | `assets/`에 바이너리 포함 |
| 미리보기 | 필수 구성 아님 | `preview.png` 포함 |
| 파일 형식 | JSON DTO | ZIP 바이너리 |
| 버전 | `figma-screen-spec-v1` | `figpack-v1` |
| 무결성 | Schema·의미·버전 검증 | Manifest·크기·SHA-256·경로 검증 |
| 재현성 기준 | 같은 Spec + Profile + Registry | 같은 Package + Plugin version |
| 주요 변경 단위 | 논리 컴포넌트와 속성 | 캡처된 노드·스타일·자산 |
| 편집성 | 디자인 시스템 편집에 유리 | 일반 노드 정리에 추가 작업 필요 |
| 원본 유사도 | 의도적으로 달라질 수 있음 | 상대적으로 높음 |
| 민감정보 위험 | Projection 정책으로 최소화 가능 | 화면 텍스트·이미지가 포함될 가능성이 큼 |
| 권장 대상 | 신규 CRUD, 표준 업무 화면 | 레거시 JSP 화면, 시각 참조·복제 |

## 6. `FigmaExportBundle`과 `.figpack`을 혼동하면 안 되는 이유

`FigmaScreenSpec`의 `designSystem` 필드는 다음과 같은 참조만 가진다.

```json
{
  "profileId": "krds-admin",
  "profileVersion": "1.4.0",
  "registryVersion": "2026.07"
}
```

이 정보만으로는 오프라인 Plugin이 Profile과 Registry의 실제 내용을 알 수 없다. 이 문제를 해결하는 계약이 `FigmaExportBundle`이다.

```text
FigmaExportBundle
├─ figmaScreenSpec
├─ designSystemProfile snapshot
├─ componentRegistry snapshot
└─ metadata
```

`metadata`에는 다음 버전이 기록된다.

- `figmaScreenSpecSchemaVersion`
- `screenSpecificationVersion`
- `designSystemProfileVersion`
- `registryVersion`
- `exportedAt`

비교하면 다음과 같다.

| 구분 | `FigmaExportBundle` | `.figpack` |
|---|---|---|
| 중심 데이터 | `FigmaScreenSpec` | `RenderedDesignDocument` |
| 목적 | 의미 기반 화면의 독립 실행 | 렌더링 캡처의 독립 전달 |
| Profile 포함 | Snapshot 포함 | 기본적으로 포함하지 않음 |
| Registry 포함 | Snapshot 포함 | 기본적으로 포함하지 않음 |
| 이미지 자산 | 기본 계약에 포함하지 않음 | `assets/`에 포함 |
| Preview | 기본 계약에 포함하지 않음 | `preview.png` 포함 |
| 형식 | 단일 JSON 객체 | 여러 파일을 담은 ZIP |

즉 다음 등식은 잘못됐다.

```text
FigmaScreenSpec을 ZIP으로 압축한 것 = .figpack   // 잘못된 이해
```

정확한 이해는 다음과 같다.

```text
FigmaScreenSpec + Profile Snapshot + Registry Snapshot
    = FigmaExportBundle

RenderedDesignDocument + Preview + Assets + Manifest
    = .figpack
```

## 7. 동일한 “회원 목록” 화면의 표현 차이

### 7.1 `.figpack` 관점

```text
Frame: x=40, y=120, width=1120, height=620
Text: "사용자 목록", font-size=28px
Rectangle: background=#FFFFFF
Input: x=64, y=184, width=240, height=40
Button-like node: x=980, y=184, width=96, height=40
Table-like DOM structure: 실제 행·열 좌표
```

이 정보는 원본과 유사하게 그리는 데 유리하지만, 버튼이 KRDS의 어떤 Variant인지 확정하지 못할 수 있다.

### 7.2 `FigmaScreenSpec` 관점

```text
ScreenType: LIST
LayoutPattern: STANDARD
Children:
  - krds.page-header
  - krds.search-panel
    - krds.text-field(field=userName)
    - krds.button(action=search, variant=secondary)
  - krds.data-table(columns=[...])
  - krds.button(action=create, variant=primary)
```

이 정보는 원본 픽셀을 그대로 복제하지는 않지만, 화면의 의도와 디자인 시스템 규칙을 유지한다.

## 8. 변경과 재생성 시 영향

### 8.1 화면 CSS가 변경된 경우

- `.figpack`: 브라우저를 다시 캡처해야 변경된 좌표와 스타일이 반영된다.
- `FigmaScreenSpec`: 업무 의미가 같다면 변경하지 않을 수 있다. 디자인 시스템 Profile 또는 Plugin Layout 정책 변경으로 대응할 수 있다.

### 8.2 디자인 시스템 Button이 변경된 경우

- `.figpack`: 기존 캡처의 버튼 모양은 그대로 남는다. 자동 연동을 보장하지 않는다.
- `FigmaScreenSpec`: 동일한 `logicalType`이 Team Library Component Instance에 연결되면 Library 업데이트를 활용할 수 있다.

### 8.3 필드가 추가된 경우

- `.figpack`: 변경된 실행 화면을 다시 캡처한다.
- `FigmaScreenSpec`: 원본 `ScreenSpecification`을 갱신하고 새 버전으로 Projection한다.

### 8.4 원본 화면이 없어도 되는가

- `.figpack`: 캡처할 수 있는 실행 화면이 필요하다.
- `FigmaScreenSpec`: DB Schema와 승인된 화면 명세가 있으면 원본 웹 화면 없이도 생성할 수 있다.

## 9. 보안 및 개인정보 관점

### 9.1 `.figpack`

실제 렌더링된 텍스트와 이미지가 포함될 수 있으므로 위험도가 상대적으로 높다.

필요한 통제:

- 로그인 세션과 권한 범위 검토
- 개인정보 Selector 마스킹
- 화면 텍스트 Allowlist 또는 Redaction
- 외부 이미지 다운로드 정책
- 로컬 Artifact 보존 기간 제한
- RAG 자동 적재 금지
- ZIP Entry와 Hash 검증
- Artifact ID 기반 접근 통제

### 9.2 `FigmaScreenSpec`

Projection 과정에서 실제 개인정보 대신 필드 의미와 샘플 값을 사용할 수 있다.

```text
실제 값: 홍길동 / 010-1234-5678
    ↓ Projection
필드 의미: 사용자명 / 연락처
샘플 값: 사용자 1 / 010-0000-0000
```

하지만 다음 정보도 민감할 수 있다.

- 내부 Route
- 비공개 화면명
- 권한 전용 Action
- Registry의 비공개 Component Key
- 내부 Design System Profile 구조

따라서 MCP 응답과 공개 REST 응답에서는 Registry Key Redaction 및 별도 인증이 필요하다.

## 10. 검증 전략

### 10.1 `FigmaScreenSpec`

```text
Java Bean Validation
    ↓
JSON Schema Validation
    ↓
논리 컴포넌트·속성 의미 검증
    ↓
Profile·Registry 버전 정합성 검증
    ↓
BLOCK Issue 확인
    ↓
Plugin Preview
```

주요 실패 예:

- `screenId` 누락
- `screenVersion`이 1보다 작음
- `content` 누락
- Registry에 없는 `logicalType`
- 폐기된 Component의 대체 Component 누락
- Property 또는 Variant 값 불일치

### 10.2 `.figpack`

```text
압축 크기 확인
    ↓
안전한 ZIP Entry 경로 확인
    ↓
Manifest 계약 버전 확인
    ↓
필수 Entry와 미선언 Entry 확인
    ↓
크기·SHA-256 검증
    ↓
document.json Schema·식별자 검증
    ↓
Asset 메타데이터 검증
    ↓
Plugin Preview와 preview.png 비교
```

주요 실패 예:

- ZIP Slip 경로
- 중복 또는 대소문자 충돌 Entry
- ZIP Bomb
- Asset 누락
- Manifest Hash 불일치
- Capture ID 불일치
- 지원하지 않는 Package version

## 11. 선택 기준

### `.figpack`을 선택해야 하는 경우

- 기존 JSP/eGovFrame 화면을 빠르게 Figma로 옮겨야 한다.
- 현재 화면의 좌표와 시각적 인상을 기준으로 검토해야 한다.
- 디자인 시스템에 등록되지 않은 레거시 UI가 많다.
- 이미지·SVG를 포함한 완결된 캡처 파일이 필요하다.
- 원본과 Plugin 결과의 시각 비교가 중요하다.

### `FigmaScreenSpec`을 선택해야 하는 경우

- 신규 CRUD·게시판·관리자 업무 화면을 만든다.
- KRDS 또는 조직 Team Library를 강제해야 한다.
- Component Instance 기반 편집성이 중요하다.
- 코드와 Figma 화면 간 추적성을 유지해야 한다.
- 동일한 화면 명세에서 여러 플랫폼 출력을 만들고 싶다.
- 향후 Design System 변경을 화면에 반영해야 한다.

### 하이브리드를 선택해야 하는 경우

- 레거시 화면의 시각적 특징은 참고하되 신규 디자인 시스템으로 재구성해야 한다.
- 자동 추론 결과를 사용자가 검토·승인해야 한다.
- 원본 참조와 최종 의미 기반 결과를 같은 Artifact에서 추적해야 한다.

권장 순서는 다음과 같다.

```text
1. .figpack으로 기존 화면의 사실 수집
2. document.json에서 의미 후보 추출
3. 사용자 Preview·수정
4. ScreenSpecification 승인
5. FigmaScreenSpec 생성
6. Component Registry로 KRDS Component 매핑
7. Figma Plugin에서 명시적 Apply
```

## 12. 역할과 책임 경계

| 구성요소 | 책임 |
|---|---|
| Web Capture Extractor | 브라우저 렌더링, DOM·CSS·좌표·자산 수집, `.figpack` 생성 |
| `RenderedDesignPackageValidator` | `.figpack` 압축·경로·Manifest·Hash·Document 검증 |
| `DesignArtifactService` | `source.figpack`, `document.json`, Preview, Asset 저장과 Import Artifact 준비 |
| `FigmaHybridExportService` | `.figpack` Reference와 의미 기반 후보·승인 결과 연결 |
| `FigmaScreenExportService` | `ScreenSpecification`에서 `FigmaScreenSpec` 생성·저장·조회 |
| `FigmaScreenSpecValidator` | 논리 구조와 디자인 시스템 정합성 검증 |
| `FigmaExportBundleAssembler` | Spec, Profile Snapshot, Registry Snapshot, Metadata 조립 |
| Figma Plugin | 입력 검증, Preview, Component 매핑, 캔버스 생성·갱신 |
| MCP Client | 자연어 요청 해석, Tool 선택과 전체 작업 오케스트레이션 |

## 13. 프로젝트 구현 기준 용어 정리

| 용어 | 정확한 의미 |
|---|---|
| `ScreenSpecification` | 코드와 Figma 출력이 공동으로 사용하는 승인된 업무 화면 원본 명세 |
| `FigmaScreenSpec` | Figma 디자인 시스템 생성을 위한 의미 기반 Projection |
| `FigmaExportBundle` | Spec과 Profile·Registry Snapshot을 묶은 파일 우선 JSON 계약 |
| `RenderedDesignDocument` | 웹 캡처 결과를 구조화한 렌더링 문서 |
| `.figpack` | RenderedDesignDocument, Preview, Asset, Manifest를 담는 ZIP 패키지 |
| `Component Registry` | 논리 타입과 실제 Figma Component Key의 매핑 |
| `Design System Profile` | 사용할 디자인 시스템, Token, Registry 버전 등의 정책 |
| Figma Plugin | Spec 또는 Package를 읽고 Figma 캔버스에 실제 노드를 만드는 실행기 |

## 14. 최종 판단

`FigmaScreenSpec`은 **설계 의도와 업무 의미를 보존하는 계약**이고, `.figpack`은 **렌더링 결과와 자산을 보존하는 운반 패키지**다.

```text
.figpack
    = 지금 보이는 화면이 어떻게 렌더링됐는가

FigmaScreenSpec
    = 이 업무 화면을 디자인 시스템으로 어떻게 구성할 것인가
```

기존 화면 복제만 필요하면 `.figpack`이 적합하다. 장기적으로 관리 가능한 KRDS 기반 업무 화면을 만들려면 `FigmaScreenSpec`이 중심이 되어야 한다. 레거시 현대화에서는 `.figpack`을 참조 자료로 사용하고, 승인 과정을 거쳐 `FigmaScreenSpec`으로 전환하는 하이브리드 방식이 가장 적합하다.

## 15. 관련 구현 및 문서

- `src/main/java/com/krdevops/springai/model/figma/FigmaScreenSpec.java`
- `src/main/java/com/krdevops/springai/model/figma/FigmaExportBundle.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaExportBundleAssembler.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaScreenExportService.java`
- `src/main/java/com/krdevops/springai/service/RenderedDesignPackageValidator.java`
- `src/main/java/com/krdevops/springai/service/DesignArtifactService.java`
- `src/main/java/com/krdevops/springai/service/figma/FigmaHybridExportService.java`
- `docs/figma/03_Website_To_Figma_Implementation_Specification.md`
- `docs/figma/08_Semantic_Figma_Export_Integrated_Architecture.md`
- `docs/figma/09_Agent_Design_System_FigmaScreenSpec_Reference_Architecture.md`
- `docs/figma/15_DEC10_DEC12_Final_Decision.md`
