# SpringAI의 DESIGN.md와 Figma 처리 방식 검토

작성일: 2026-09-03  
검토 대상: SpringAI의 `DESIGN.md`, `ScreenSpecification`, Figma Design System Profile·Registry 연계 구조

## 1. 결론

현재 SpringAI의 Figma 생성 경로는 `DESIGN.md`를 직접 읽어 Figma 디자인을 만드는 구조가 아니다.

현재 동작은 다음과 같다.

```text
ScreenSpecification
  → 의미 기반 화면 구조(FigmaNodeSpec)
  → DesignSystemProfile + ComponentRegistry
  → Published Component와 Variant 결정
  → FigmaExportBundle
  → Figma 플러그인이 실제 Published Component Instance 생성
```

따라서 현재 Figma 경로의 디자인 기준은 다음 두 가지다.

1. 화면별 구조와 업무 의미: `ScreenSpecification`
2. 실제 Figma 컴포넌트 매핑: `DesignSystemProfile`과 `ComponentRegistry`

사용자가 의도한 것처럼 `DESIGN.md`를 “Sketch나 Claude Design 같은 디자인 도구가 만든 디자인 명세”로 사용하려면, `DESIGN.md`의 규칙을 Figma Profile·Registry·Variable·Variant로 변환하는 연결 단계가 추가로 필요하다.

## 2. SpringAI에서 DESIGN.md의 의미

이 프로젝트에서 `DESIGN.md`는 프로젝트별 임의 오버라이드 문서라기보다 다음 의미로 보는 것이 맞다.

- 디자인의 최상위 원본
- 색상, 글꼴, 간격, 모서리, 버튼, 테이블, 레이아웃 원칙을 정의하는 문서
- Sketch, Claude Design 또는 사람이 만든 디자인 명세를 코드 생성기가 이해할 수 있게 전달하는 문서
- 실제 CSS와 UI 컴포넌트로 변환할 때 참조하는 디자인 규칙의 원천

즉, 권장 개념은 다음과 같다.

```text
DESIGN.md
  ↓ 디자인 원칙과 토큰
공통 디자인 시스템 Profile·Registry
  ↓ 화면에 사용할 실제 컴포넌트와 Variant
ScreenSpecification / Figma 정보
  ↓ 화면별 구조와 데이터
Figma 또는 HTML/CSS 결과물
```

## 3. 현재 Figma 처리 방식

### 3.1 화면 구조 생성

`FigmaScreenExportService`는 승인된 `ScreenSpecification`을 가져와 화면 유형과 레이아웃 패턴을 결정하고, Builder를 통해 의미 기반 `FigmaNodeSpec` 트리를 생성한다.

근거:

- `FigmaScreenExportService.java:137-153`

### 3.2 Profile과 Registry 조회

화면 트리가 만들어지면 PUBLISHED 상태의 `DesignSystemProfile`과 버전이 일치하는 `ComponentRegistry`를 조회한다.

근거:

- `FigmaScreenExportService.java:154-168`

### 3.3 의미 역할을 실제 Figma 컴포넌트로 변환

`KrdsComponentResolutionService`는 각 노드의 `semanticRole`, 화면 패턴, 플랫폼, 밀도, 상태 등을 바탕으로 Registry의 논리 컴포넌트와 실제 Figma Component Set·Variant를 결정한다.

결과에는 다음 매핑 정보가 포함된다.

- 논리 컴포넌트 유형
- `componentSetKey`
- `variantKey`
- Variant 속성
- Text·Boolean·Instance Swap 등의 Component Property
- 계약 및 규칙 버전

근거:

- `KrdsComponentResolutionService.java:33-35`
- `KrdsComponentResolutionService.java:173-227`

### 3.4 재현 가능한 Bundle 생성

서버는 화면 명세와 Profile·Registry 스냅샷을 하나의 `FigmaExportBundle`로 묶는다. 확장 경로에서는 Screen Pattern, Variant Rule Set, Registry hash도 함께 고정한다.

이렇게 해야 같은 입력과 버전으로 같은 Figma 결과를 재생성하고, 디자인 시스템 버전 불일치를 차단할 수 있다.

근거:

- `FigmaExportBundleAssembler.java:20-50`
- `FigmaExportBundleAssembler.java:53-116`

### 3.5 Figma 플러그인의 실제 생성

Figma 플러그인은 서버가 결정한 `variantKey`를 사용해 `figma.importComponentByKeyAsync()`로 Published Component를 가져온다. Variant Key가 바뀐 경우 Component Set을 가져와 Variant Property로 다시 찾는 호환 경로도 있다.

가져온 Component는 Instance로 생성되고, 서버가 전달한 Component Property가 적용된다.

근거:

- `figma-screen-spec-plugin/src/code.ts:2240-2289`
- `figma-screen-spec-plugin/src/code.ts:2588-2645`

## 4. DESIGN.md 연결 상태

### 4.1 현재 직접 연결된 곳

`DESIGN.md`를 직접 탐색하고 YAML frontmatter를 파싱하는 `DesignMdRuleLoader`는 현재 Thymeleaf 생성 경로에 있다.

지원하는 규칙 범주는 다음과 같다.

- typography
- colors
- spacing
- radius
- layout
- components
- voice
- forbidden

근거:

- `DesignMdRuleLoader.java:23-57`
- `DesignMdRuleLoader.java:74-98`

### 4.2 Figma 경로의 누락 지점

현재 Figma 서비스와 Figma 플러그인에서는 다음 연결이 확인되지 않는다.

- Figma Export 시 `DESIGN.md` 로드
- `DESIGN.md` 규칙을 Profile 또는 Registry로 컴파일
- `DESIGN.md` 토큰을 Figma Variable Key로 변환
- 플러그인에서 `importVariableByKeyAsync` 또는 Variable binding API로 노드 속성에 직접 바인딩

따라서 `DESIGN.md`의 기본 색상이나 간격을 바꾸는 것만으로는 현재 Figma 결과가 자동으로 바뀌지 않는다.

현재 결과의 시각 스타일은 주로 이미 디자인과 Variable이 적용된 Published Figma Component를 Instance로 가져오면서 상속되는 것으로 판단된다. 이것은 코드 흐름에 근거한 해석이며, 외부 Figma Library 내부 설정까지 이 저장소만으로 확인한 것은 아니다.

## 5. 기능별 상태

| 기능 | 현재 상태 | 처리 주체 |
|---|---|---|
| 화면 구조 정의 | 구현됨 | `ScreenSpecification` |
| 의미 역할 정의 | 구현됨 | `FigmaNodeSpec.semanticRole` |
| 실제 컴포넌트 선택 | 구현됨 | `DesignSystemProfile` + `ComponentRegistry` |
| Variant 선택 | 구현됨 | `VariantRuleSet` |
| Published Component Instance 생성 | 구현됨 | Figma 플러그인 |
| Component Property 적용 | 구현됨 | Figma 플러그인 |
| DESIGN.md 파싱 | Thymeleaf 경로에만 구현됨 | `DesignMdRuleLoader` |
| DESIGN.md → Figma Registry 변환 | 미연결 | 추가 구현 필요 |
| DESIGN.md → Figma Variable 변환·바인딩 | 미연결 | 추가 구현 필요 |

## 6. 사용자가 의도한 방식으로 처리하려면

Figma에서도 `DESIGN.md`를 디자인의 최상위 원본으로 사용하려면 다음 흐름이 적합하다.

```text
DESIGN.md
  → DesignMdRuleLoader
  → 공통 디자인 규칙/Token 모델
  → DesignSystemProfile + ComponentRegistry + VariableRegistryEntry
  → ScreenSpecification의 semanticRole과 결합
  → Component·Variant·Variable 결정
  → FigmaExportBundle에 결정 결과와 DESIGN.md hash 기록
  → Figma 플러그인이 Published Instance 생성 및 Variable binding
```

핵심은 `DESIGN.md`를 Figma 플러그인이 즉석에서 해석하게 하는 것이 아니라, 서버가 먼저 검증하고 결정한 매핑 결과를 Bundle로 전달하는 것이다. 이 방식이면 버전 고정, 재현성, 규칙 위반 차단과 결과 추적이 가능하다.

## 7. 최종 판단

현재 SpringAI의 Figma 연동은 “디자인 시스템을 사용하지 않는 구조”가 아니다. Profile·Registry·Published Component를 통해 디자인 시스템을 사용하고 있다.

다만 사용자가 정의한 `DESIGN.md` 중심 관점에서는 연결이 완성되지 않았다.

정확한 표현은 다음과 같다.

> 현재 Figma 생성은 `ScreenSpecification + DesignSystemProfile + ComponentRegistry` 기반이다. `DESIGN.md`는 Thymeleaf 생성에는 연결되어 있지만, Figma 컴포넌트·Variant·Variable 매핑의 입력으로는 아직 직접 연결되어 있지 않다.

검토 확신도: 높음. 저장소의 Figma 서버 경로, Export Bundle 조립 경로, Figma 플러그인 Materialization 경로와 `DESIGN.md` 참조 여부를 교차 확인했다.
