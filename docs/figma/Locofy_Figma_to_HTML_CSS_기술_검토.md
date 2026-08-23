# Locofy.ai Figma → HTML/CSS 변환 파이프라인 기술 검토

> 작성일: 2026-08-23  
> 검토 범위: Locofy.ai의 Figma 입력부터 HTML/CSS 생성·검토·내보내기까지의 기술 파이프라인과 SpringAI/eGovFrame 적용 적합성  
> 근거 기준: Locofy 공식 문서 및 본 저장소의 Figma·화면명세·Thymeleaf 생성 아키텍처

## 1. 요약

Locofy.ai의 Figma → HTML/CSS 변환은 단순한 레이어-태그 치환기가 아니라 다음 세 계층이 결합된 파이프라인으로 보는 것이 타당하다.

1. Figma 노드 그래프를 코드 생성용 중간 모델로 정규화
2. AI와 규칙 기반 휴리스틱으로 레이아웃·반응형 구조·컴포넌트·Props·시맨틱을 추론
3. 프레임워크별 HTML/CSS 또는 컴포넌트 코드를 생성하고 Builder에서 검토·교정·내보내기

공개 자료로 확인되는 범위는 입력 조건, 사용자 설정, 지원 기능과 산출물이다. LocoAI/LDM의 내부 모델 구조, 정확한 중간 표현(Intermediate Representation, IR), 레이아웃 및 컴포넌트 판정 알고리즘은 공개되지 않았다. 따라서 본 문서는 다음을 구분한다.

- **확인된 사실**: Locofy 공식 문서가 직접 명시한 기능과 계약
- **기술적 추론**: 공개된 입출력과 동작으로부터 합리적으로 역추론한 내부 처리
- **미확인 영역**: 공개 자료만으로 확정할 수 없는 구현 세부사항

### 1.1 종합 판단

| 순위 | 판단 | 신뢰도 | 근거 |
|---:|---|---|---|
| 1 | 시각적 프로토타입과 일반 프런트엔드 초안 생성에는 유효하다. | 높음 | Auto Layout, 컴포넌트, Props, 토큰, 반응형 미리보기, HTML/CSS 출력 지원 |
| 2 | 출력 품질은 Locofy 엔진보다 원본 Figma의 구조적 품질에 크게 좌우된다. | 높음 | 레이어명, Auto Layout, Variant 구조, Gap, Min/Max 크기 사용을 공식 권장 |
| 3 | eGovFrame·Thymeleaf 업무 화면의 완성 코드를 직접 생성하는 주 경로로는 부족하다. | 높음 | DB·VO·Controller Binding, Thymeleaf 속성, CSRF, 권한은 Figma에 존재하지 않음 |
| 4 | SpringAI에서는 완성 코드 생성기보다 시각 구조·CSS·컴포넌트 후보 생성기로 사용하는 편이 적합하다. | 높음 | 현재 저장소가 시각 명세와 업무 Binding 계약을 의도적으로 분리 |
| 5 | 디자인과 코드의 반복 동기화는 가능하지만 충돌 없는 완전한 왕복 변환은 아니다. | 중간 | GitHub Smart Merge가 파일별 충돌 검토와 사용자 선택을 요구 |

## 2. 전체 파이프라인

```text
Figma 선택 영역
  ↓
Figma 노드 그래프 수집
  ↓
정규화된 Design IR
  ↓
레이아웃·반응형 규칙 추론
  ↓
시맨틱·컴포넌트·Props 추론
  ↓
디자인 토큰·자산 추출
  ↓
프레임워크 독립 UI 모델
  ↓
HTML/CSS 또는 React·Vue 등 코드 생성
  ↓
Locofy Builder Preview
  ↓
수동 교정·Agent Mode
  ↓
ZIP / Clipboard / GitHub Sync
```

Locofy는 Figma 파일 전체를 일괄 변환하기보다 사용자가 선택한 Frame 또는 Section을 변환 대상으로 사용한다. 프로젝트 생성 시 Web 타깃으로 React, Next.js, HTML/CSS, Gatsby, Vue, Angular 등을 선택할 수 있다.

## 3. 단계별 기술 분석

### 3.1 Figma 노드 그래프 수집

Locofy의 주 입력은 스크린샷 픽셀이 아니라 Figma의 구조화된 노드 그래프다. 다음 정보가 코드 생성의 주요 입력으로 사용될 수 있다.

- Frame, Group, Component, Instance, Text, Vector, Image 노드
- 부모·자식 계층과 노드 순서
- 절대 좌표와 Bounding Box
- Auto Layout 방향·정렬·Wrap
- Padding과 Gap
- Width/Height 및 Min/Max 제약
- Fill, Stroke, Shadow, Radius, Opacity
- Font Size, Line Height, Letter Spacing, Font Weight
- Figma Component Properties와 Variants
- Styles와 Variables
- Prototype Connection과 Interaction
- 레이어 이름과 노드 유형

Locofy 공식 문서는 Padding, Gap, Radius, Width/Height, Min/Max 크기, Stroke Width, Opacity, Shadow 및 Typography 속성 처리를 설명한다.

같은 화면처럼 보여도 Figma 노드 구조가 다르면 생성 코드도 달라질 수 있다.

```text
구조화된 입력
Frame (Auto Layout: vertical)
 ├─ Header (Auto Layout: horizontal)
 ├─ Main (Auto Layout: vertical)
 └─ Footer

비구조화된 입력
Frame
 ├─ Rectangle 41
 ├─ Text 82
 ├─ Group 19
 └─ 절대 좌표로 겹친 노드 다수
```

첫 번째 구조는 DOM 계층과 Flex Layout으로 비교적 직접 변환할 수 있다. 두 번째 구조는 개발자가 의도한 DOM 계층이 입력에 존재하지 않으므로 시각적 근접성에 기반한 추론 비중이 커진다.

### 3.2 Design IR 정규화

> **기술적 추론:** Locofy는 정확한 IR 스키마를 공개하지 않았다. 다만 동일한 Figma 입력을 HTML/CSS, React, Vue, Flutter 등 서로 다른 타깃으로 변환하려면 프레임워크 독립적인 UI 중간 모델이 존재해야 한다.

추정 가능한 최소 IR은 다음과 유사하다.

```json
{
  "type": "container",
  "semanticRole": "navigation",
  "layout": {
    "display": "flex",
    "direction": "row",
    "gap": 16,
    "align": "center",
    "responsivePolicy": "wrap-or-collapse"
  },
  "styleRefs": ["color.primary", "spacing.4"],
  "componentRef": "Header",
  "properties": {
    "showLogin": true,
    "variant": "desktop"
  },
  "children": []
}
```

이 중간 모델은 타깃별로 다음과 같이 투영될 수 있다.

```text
UI IR Container
 ├─ HTML/CSS       → <div class="...">
 ├─ React          → <Header variant="desktop" />
 ├─ Vue            → <Header :variant="..." />
 ├─ Flutter        → Row(...)
 └─ React Native   → <View style={...}>
```

### 3.3 레이아웃 추론

레이아웃 변환은 다음 우선순위를 사용할 가능성이 높다.

1. Auto Layout을 `display:flex` 또는 동등한 타깃 레이아웃으로 매핑
2. 반복되는 행·열 패턴을 Grid 후보로 승격
3. 겹친 노드를 Absolute Position 후보로 분류
4. 반복 간격을 `gap`으로 매핑
5. 부모 내부 여백을 `padding`으로 매핑
6. Fill Container를 `width:100%`, `flex-grow` 등으로 매핑
7. Hug Contents를 콘텐츠 기반 크기 또는 `fit-content`로 매핑
8. Fixed 크기를 고정값으로 매핑
9. Min/Max 제약을 `min-width`, `max-width` 등으로 매핑

예를 들어 다음 Figma Auto Layout은:

```text
Direction: horizontal
Gap: 16
Padding: 24
Child A: Fill container
Child B: Fixed 120
```

다음과 유사한 CSS로 변환될 수 있다.

```css
.container {
  display: flex;
  gap: 16px;
  padding: 24px;
}

.container__main {
  flex: 1 1 auto;
  min-width: 0;
}

.container__aside {
  flex: 0 0 120px;
}
```

Locofy가 Auto Layout과 Gap 사용을 권장하는 이유는 이러한 속성이 CSS Layout으로 비교적 결정론적으로 매핑되기 때문이다. 절대 좌표만 존재하면 Flex, Grid, Absolute Position 중 어떤 구현이 설계 의도인지 시스템이 추측해야 한다.

### 3.4 반응형 규칙 추론

반응형 변환은 단순한 px→% 치환이 아니다. 다음 신호를 함께 분석해야 한다.

- 서로 다른 Viewport Frame 사이의 구조적 대응
- Auto Layout 방향과 Wrap
- Fill/Hug/Fixed 크기
- Min/Max Width
- 요소의 상대 정렬과 순서
- Component Variant
- 특정 Viewport에서의 표시·숨김
- 반복 레이아웃 패턴

그 결과는 다음과 같은 Media Query와 Layout 변환으로 표현될 수 있다.

```css
.cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
}

@media (max-width: 1024px) {
  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
```

다만 Desktop Frame과 Mobile Frame이 같은 화면의 반응형 상태인지 서로 다른 화면인지는 시각적 유사성만으로 항상 확정할 수 없다. 레이어 이름, Component Instance 관계, 내부 구조가 일관돼야 대응 관계를 안정적으로 찾을 수 있다.

따라서 반응형 결과에는 다음 방식이 혼합될 가능성이 높다.

- **직접 매핑**: Auto Layout, Min/Max Constraint
- **규칙 기반 추론**: 반복 노드→Grid, 인접 노드→Flex
- **AI 추론**: Viewport 간 구조 대응과 Component 전환

### 3.5 시맨틱 HTML 추론

Figma 노드는 기본적으로 HTML 의미를 갖지 않는다. 예를 들어 `Rectangle 23`이 Button인지 장식용 Container인지 원본 구조만으로 확정할 수 없다.

Locofy는 이를 보완하기 위해 Container와 Text Layer 등에 시맨틱 태그를 지정하는 기능을 제공한다.

```text
Figma Frame       → header / nav / main / section / footer
Figma Text        → h1 / h2 / p / strong
Interactive Node → button / a / input
Repeated Item    → ul / ol / article
```

접근성과 SEO 품질은 자동 생성 여부보다 다음 정보가 입력 또는 후처리 단계에 존재하는지에 좌우된다.

- 적절한 HTML 태그
- 아이콘 전용 버튼의 Accessible Name
- 제목 계층
- Link와 Button의 의미 구분
- Input과 Label 관계
- 키보드 Interaction과 Focus 상태
- Modal Focus Trap
- 오류 메시지와 입력 필드의 연결

Agent Mode가 접근성 보정을 지원하더라도 생성 결과에 대한 별도 접근성 검증은 필요하다.

### 3.6 컴포넌트와 Props 추론

Locofy는 화면을 평면 HTML로만 생성하지 않고 반복 구조를 재사용 컴포넌트와 Props로 분리할 수 있다.

주요 입력 신호는 다음과 같다.

- Figma Component와 Instance 관계
- Component Set과 Variant
- 반복되는 동일한 내부 계층
- 텍스트·이미지만 다른 반복 패턴
- Boolean Property에 따른 Visibility
- Text Property
- 레이어 이름
- 스타일 차이

지원되는 개념에는 Value Prop, Style Prop, Conditional Rendering Prop, Variant/Enum Prop, Event Function Prop, Array/Object Prop 등이 포함된다.

예를 들어 Figma의 `Button` 컴포넌트가 다음 속성을 갖는 경우:

```text
Variant: Primary | Ghost
Show icon: True | False
Label: "저장"
```

HTML/CSS에서는 다음과 유사하게 투영될 수 있다.

```html
<button class="button button--primary">
  <span class="button__icon" aria-hidden="true"></span>
  <span>저장</span>
</button>
```

React 타깃에서는 다음과 같은 Props 호출로 변환할 수 있다.

```tsx
<Button variant="primary" showIcon>
  저장
</Button>
```

기존 코드 컴포넌트가 있으면 `locofy.config.json`으로 Figma Component와 Code Component를 연결할 수 있다.

```json
{
  "path": "./src/components/Button.tsx",
  "name": "Button",
  "figmaComponentName": "Button",
  "props": [
    {
      "name": "variant",
      "figmaPropName": "Variant",
      "type": "enum",
      "valueMapping": {
        "primary": ["Primary"],
        "ghost": ["Ghost"]
      }
    }
  ]
}
```

이 매핑은 Component Name, Figma Property, Variant Label과 Layer Name의 안정성에 의존한다.

### 3.7 디자인 토큰과 CSS 생성

Locofy는 다음 두 토큰 변환 경로를 제공한다.

1. Figma Styles/Variables를 코드 변수로 변환
2. 반복되는 스타일 값을 찾아 공통 변수로 추출

예상되는 직접 변환은 다음과 같다.

```text
Figma Variable: color/brand/primary
                    ↓
CSS Variable: --color-brand-primary
                    ↓
.button--primary {
  background: var(--color-brand-primary);
}
```

두 방식은 의미적으로 다르다.

| 방식 | 장점 | 한계 |
|---|---|---|
| Figma Variable 직접 변환 | 설계자가 정의한 의미를 보존할 가능성이 높음 | 원본 Variable 체계의 품질과 명명에 의존 |
| 반복값 자동 추출 | Raw Value 중복을 줄일 수 있음 | 동일한 값이 Primary, Link, Focus 등 서로 다른 의미인지 판별하기 어려움 |

운영 코드에는 값 중복 제거보다 의미 기반 토큰 매핑이 중요하다. 현재 값이 우연히 같아도 역할이 다른 토큰은 향후 독립적으로 변경될 수 있기 때문이다.

### 3.8 자산 처리

이미지·아이콘·벡터에는 일반적으로 다음 전략이 필요하다.

- Figma Image Fill → PNG/JPEG/WebP Export
- 단순 Vector → SVG
- 반복 아이콘 → 공통 Asset 또는 Component
- Background Image → CSS URL
- 로컬 파일 또는 Public Asset 경로 생성
- 고해상도 이미지의 배율·압축 정책 적용

SVG 내부의 Fill/Stroke가 고정 색상으로 Export되면 Theme Token 적용이 어려워진다. 아이콘은 가능하면 `currentColor` 기반 SVG나 코드 컴포넌트로 정규화하는 것이 유지보수에 유리하다.

### 3.9 코드 생성과 Builder

HTML/CSS 타깃은 다음과 같은 산출물을 만들 수 있다.

- 화면별 HTML
- Section 또는 Component HTML
- 전역 CSS
- 화면·컴포넌트별 CSS
- CSS Variables
- Image/SVG Asset
- Script와 Meta 설정
- Navigation과 Interaction Wiring

Builder에서는 다음 작업을 수행할 수 있다.

- 생성 코드와 Live Preview 확인
- Component와 Props 검토·수정
- 파일·폴더 이름 및 구조 조정
- Framework 설정 변경
- Agent Mode를 통한 코드 보완
- Clipboard 복사 또는 ZIP 다운로드
- GitHub Repository/Branch/Folder로 동기화
- 디자인 변경과 기존 코드 변경 사이의 충돌 검토

## 4. 입력 품질별 예상 결과

| Figma 입력 특성 | 예상 결과 |
|---|---|
| Auto Layout 중심 | Flex/Grid 변환 안정성이 높음 |
| Component/Instance 적극 사용 | 재사용 컴포넌트 생성 용이 |
| Variant 내부 구조 일관 | Enum/Conditional Prop 추론 용이 |
| 의미 있는 레이어명 | CSS Class와 Prop 이름 개선 |
| Figma Variables 사용 | CSS Token 보존 가능 |
| Desktop/Mobile 구조 대응 | Breakpoint 추론 가능 |
| 절대 배치와 겹침 다수 | `position:absolute` 증가 가능 |
| Group 중첩 과다 | 불필요한 Wrapper `<div>` 증가 가능 |
| Variant마다 내부 구조 상이 | Props 대신 별도 컴포넌트로 분리될 수 있음 |
| 텍스트를 Vector로 변환 | 시맨틱·접근성·동적 데이터 가능성 상실 |
| 같은 요소를 화면마다 복제 | 공통 컴포넌트 탐지 불안정 |
| 상태가 여러 Figma Page에 분산 | 상태 전이와 State Machine 복원 어려움 |

Locofy는 임의의 Figma를 자동으로 깨끗한 코드로 변환하는 기술이라기보다, 코드 생성 친화적으로 구조화된 Figma를 프런트엔드 코드로 컴파일하는 기술에 가깝다.

## 5. HTML/CSS 출력의 구조적 한계

### 5.1 업무 의미를 복원할 수 없음

Figma만으로는 다음을 확정할 수 없다.

- 입력 필드가 어느 VO Property에 매핑되는지
- Form Action이 어느 Controller Method인지
- HTTP Method가 GET, POST, PUT 중 무엇인지
- CSRF Token이 필요한지
- Validation Message가 어디서 오는지
- 권한에 따라 어떤 Action을 숨겨야 하는지
- 목록이 서버 페이징인지 클라이언트 페이징인지
- 날짜·금액·개인정보 Masking 정책
- Error, Empty, Loading 상태의 실제 발생 조건

따라서 Locofy가 생성하는 기능적 코드는 주로 프런트엔드 Interaction과 Visual Behavior 범위다. 서버 업무 계약까지 생성한다는 의미로 해석하면 안 된다.

### 5.2 접근성의 완전 자동 보장 불가

시맨틱 태그가 생성되더라도 다음 항목은 별도 감사가 필요하다.

- Label/Input 연결
- ARIA State와 Live Region
- Focus Order와 Focus Restoration
- Keyboard Interaction
- Modal Focus Trap
- 오류 메시지 연결
- Color Contrast
- Reduced Motion 대응
- Table Caption과 Header Scope

### 5.3 CSS 유지보수성의 입력 의존성

자동 생성 CSS에는 다음 문제가 나타날 수 있다.

- 과도한 고정 px 값
- 깊은 Class 계층
- 작은 차이마다 별도 Class 생성
- Semantic Token 대신 Raw Color 사용
- Media Query 중복
- 디자인 Wrapper가 DOM Wrapper로 그대로 생성
- 시각적 정밀도를 위한 Absolute Positioning
- 동일 스타일의 미세한 소수점 차이

공통 스타일 추출은 값 중복을 줄일 수 있지만 회사 표준 Token과의 의미적 정합성을 자동으로 보장하지는 않는다.

### 5.4 완전한 Design↔Code Round Trip의 한계

GitHub Smart Merge는 디자인 변경과 개발자가 추가한 로직 사이의 충돌을 표시하고 선택적으로 병합한다. 이는 유용하지만 다음 한계를 내포한다.

- 생성 코드를 개발자가 크게 재구성하면 이후 동기화가 어려워질 수 있음
- Figma Node와 Code AST 사이의 안정적인 Identity 유지가 중요
- 충돌 시 GitHub 버전, Locofy 버전 또는 양쪽 변경을 수동 선택
- 업무 로직이 삽입된 파일을 재생성하면 별도 검토 필요

따라서 반복 동기화는 가능하지만 의미 보존이 보장되는 완전한 양방향 컴파일러로 보기는 어렵다.

## 6. SpringAI 현재 아키텍처와의 비교

현재 저장소는 다음 모델을 분리한다.

| 모델 | 책임 |
|---|---|
| `UiDesignSpec` | 레이아웃, 컴포넌트, 시맨틱 필드, 디자인 토큰 |
| `ScreenSpecification` | DB Table/Column/JOIN, URL, Action, 권한을 포함한 코드 생성 계약 |
| Renderer 산출물 | 승인된 화면명세를 기준으로 결정론적으로 생성된 Thymeleaf/KRDS 코드 |

현재 설계는 이미지 또는 시각 자료에서 전체 HTML을 직접 생성하는 방식을 빠른 프로토타입 용도로 제한한다. 운영 코드에는 재현성, 보안, Binding 정확성 및 회귀 검증이 필요하므로 승인된 `ScreenSpecification`을 코드 생성의 기준으로 사용한다.

```text
Figma/디자인
  ↓
시각 구조와 디자인 토큰
  ↓
UiDesignSpec
  ↓
DB·Controller·VO·권한 결합
  ↓
APPROVED ScreenSpecification
  ↓
FreeMarker 기반 Thymeleaf 생성
  ↓
Binding·Build·Render 검증
```

SpringAI의 목표 파이프라인에서 Figma와 디자인 시스템은 시각, Layout, Component 선택을 제어한다. 업무 Binding의 단일 기준은 Controller, VO, DB Schema 및 승인된 `ScreenSpecification`이다.

### 6.1 적용 방식별 적합성

| 적용 방식 | 적합성 | 판단 근거 |
|---|---|---|
| Locofy HTML을 완성된 Thymeleaf로 직접 사용 | 낮음 | `th:*`, VO Binding, Route, CSRF, 권한 계약 부재 |
| Locofy CSS와 Asset만 가져오기 | 중간~높음 | 시각 자산 재사용 가능, Token 정규화 필요 |
| Locofy HTML을 Skeleton으로 사용 | 중간 | DOM을 Thymeleaf/KRDS 컴포넌트로 다시 투영해야 함 |
| Locofy 결과를 `UiDesignSpec` 입력으로 변환 | 높음 | 시각 의미와 업무 계약 분리 유지 |
| KRDS 컴포넌트를 Locofy Custom Component로 연결 | 개념상 높음 | 기존 컴포넌트 재사용 가능, HTML/CSS 타깃 지원 범위는 별도 확인 필요 |
| Locofy를 독립 프로토타이핑 트랙으로 운영 | 높음 | 빠른 검증 후 승인된 구조만 운영 생성기로 전달 가능 |

## 7. 권장 통합 경계

Locofy를 다음 영역에 제한하는 구성이 안전하다.

```text
Locofy 책임
 ├─ Figma 노드 해석
 ├─ 반응형 레이아웃 후보
 ├─ 시맨틱 HTML 후보
 ├─ 컴포넌트·Props 후보
 ├─ CSS와 디자인 토큰 후보
 └─ Asset Export

SpringAI 책임
 ├─ ScreenSpecification 작성·승인
 ├─ DB/VO/Controller Binding
 ├─ Thymeleaf 속성 적용
 ├─ 권한·CSRF·보안
 ├─ KRDS 컴포넌트 계약
 ├─ FreeMarker 결정론적 렌더링
 └─ Build/Render/A11y/Visual 검증
```

핵심은 Locofy의 HTML을 최종 산출물로 간주하지 않고 시각적으로 풍부한 초안 또는 구조화된 디자인 입력으로 취급하는 것이다.

### 7.1 권장 변환 계약

Locofy 결과를 SpringAI에 연결한다면 직접 HTML 복사보다 다음과 같은 중간 계약이 적합하다.

```json
{
  "source": "LOCOFY",
  "sourceArtifactId": "...",
  "layout": {},
  "components": [],
  "responsivePolicies": [],
  "tokens": {},
  "assets": [],
  "semanticCandidates": [],
  "issues": [
    {
      "severity": "REVIEW_REQUIRED",
      "code": "UNBOUND_FORM_FIELD",
      "nodeRef": "..."
    }
  ]
}
```

이 계약은 Locofy가 추출한 시각 정보를 보존하되 DB·VO·Controller Binding을 임의 확정하지 않아야 한다. 이후 `ScreenSpecification` 작성·검증·승인 단계에서 업무 의미를 결합한다.

## 8. 검증 관점

Locofy 결과를 실제 프로젝트에 사용할 경우 최소한 다음 검증 계층이 필요하다.

| 검증 계층 | 검증 내용 |
|---|---|
| 구조 검증 | DOM 깊이, Wrapper 과다, Component 중복, HTML 문법 |
| 시맨틱 검증 | Heading 계층, Landmark, Button/Link/Input 의미 |
| 접근성 검증 | axe-core, Keyboard, Focus, Contrast, ARIA |
| 반응형 검증 | Desktop/Tablet/Mobile Screenshot 및 Overflow |
| 시각 회귀 | Figma Reference와 Rendered HTML의 Pixel/Perceptual Diff |
| 토큰 검증 | Raw Color/Spacing/Radius 금지 및 승인 Token 사용 |
| Binding 검증 | `th:object`, `th:field`, Route, Model Attribute 일치 |
| 보안 검증 | CSRF, XSS, URL, 권한 조건, 민감정보 노출 |
| 빌드 검증 | Thymeleaf Parse/Render와 Spring Boot Build |

Locofy Preview가 정상적으로 보인다는 사실은 업무 Binding, 접근성, 보안 및 실제 서버 렌더링이 정상이라는 증거가 아니다.

## 9. 미확인 영역과 한계

다음 항목은 공개 자료만으로 확정할 수 없다.

- LocoAI/LDM 내부 모델 구조와 버전 정책
- Figma Node를 내부 IR로 변환하는 정확한 Schema
- Flex와 Grid 선택 알고리즘
- Breakpoint 추론의 정확한 기준
- Component 유사도와 분할 임계값
- 동일 입력에 대한 완전한 결정론 보장 여부
- 생성 코드 AST Identity의 동기화 방식
- HTML/CSS 출력에서 Custom Component Mapping이 React 계열과 동일한 수준인지 여부
- 생성 전후 자동 접근성 및 시각 회귀 검증의 실제 범위
- Figma Variable Alias와 Mode가 CSS Theme으로 변환되는 정확한 규칙
- 생성 코드에 대한 장기 버전 호환성과 마이그레이션 정책

이 영역을 확인하려면 동일한 Figma Fixture를 고정하고 Locofy HTML/CSS 결과물을 실제로 생성하여 다음을 비교해야 한다.

1. 동일 입력 반복 생성의 Diff
2. Auto Layout 유무에 따른 DOM/CSS 차이
3. Desktop/Mobile Frame 구조 변화에 따른 Breakpoint 생성
4. Component/Variant/Property Mapping 결과
5. Figma Variable Alias와 Mode 변환 결과
6. GitHub Sync 후 개발자 수정 코드의 보존 범위
7. axe-core와 Visual Diff 결과

## 10. 최종 결론

Locofy.ai는 구조가 잘 정리된 Figma를 반응형 프런트엔드 초안으로 컴파일하는 데 강하다. 그러나 Figma에 존재하지 않는 업무·데이터·보안 계약까지 복원하는 시스템은 아니다.

SpringAI에서는 Locofy 결과를 `UiDesignSpec`, HTML Skeleton 또는 CSS/Asset 후보로 받아들이고 다음 경계를 유지하는 것이 타당하다.

- 업무 Binding은 Controller·VO·DB Schema로 검증
- 코드 생성 기준은 승인된 `ScreenSpecification`
- Thymeleaf/KRDS 코드는 결정론적 Renderer가 생성
- Build, Render, Accessibility, Visual Regression을 별도 Gate로 수행

따라서 Locofy를 기존 생성기의 대체재로 보기보다 Figma 해석과 프런트엔드 시각 구조 생성을 보강하는 선택적 상위 계층으로 평가하는 것이 가장 적절하다.

## 11. 참고 자료

### 11.1 Locofy 공식 자료

- [Quickstart - Plugin](https://www.dev.locofy.ai/docs/plugin/quickstart/)
- [Lightning Flow](https://www.dev.locofy.ai/docs/plugin/lightning/)
- [Figma Components](https://www.locofy.ai/docs/cli/design-system/figma-components/)
- [Design Best Practices](https://www.locofy.ai/docs/plugin/examples/tutorials/design-best-practices/)
- [Components & Props](https://www.locofy.ai/docs/plugin/lightning/components/)
- [Semantic Tags](https://www.locofy.ai/docs/classic/tagging/code-with-semantic-tags/)
- [Manual Prop Mapping](https://www.locofy.ai/docs/cli/design-system/manual-prop-mapping/)
- [Custom Components](https://www.dev.locofy.ai/docs/plugin/design-system/overview/)
- [Locofy Builder Tour](https://www.dev.locofy.ai/docs/plugin/builder-tour/)
- [Download Code](https://www.dev.locofy.ai/docs/url/export-and-deployment/download-code/)
- [GitHub Integration and Smart Merge](https://www.locofy.ai/docs/plugin/export-and-deployment/sync-with-github/)

### 11.2 저장소 내부 자료

- [`docs/crud/local-vision-design-reference-integration-review.md`](../crud/local-vision-design-reference-integration-review.md)
- [`docs/crud/design-reference-screen-specification-mapping-flow.md`](../crud/design-reference-screen-specification-mapping-flow.md)
- [`docs/figma/03_Website_To_Figma_Implementation_Specification.md`](03_Website_To_Figma_Implementation_Specification.md)
- [`docs/figma/05_Overall_Architecture_Diagram.md`](05_Overall_Architecture_Diagram.md)
- [`docs/figma/08_Semantic_Figma_Export_Integrated_Architecture.md`](08_Semantic_Figma_Export_Integrated_Architecture.md)
- [`docs/figma/11_Semantic_Figma_Design_System_Implementation_Plan.md`](11_Semantic_Figma_Design_System_Implementation_Plan.md)

