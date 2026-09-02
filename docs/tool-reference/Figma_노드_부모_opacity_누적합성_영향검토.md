# Figma 노드·부모 `opacity` 누적 합성 영향검토

> 작성일: 2026-09-02
> 선행 작업: [`Figma_fills_strokes_단색정확도_구현계획.md`](./Figma_fills_strokes_단색정확도_구현계획.md)
> 결론: 부모 opacity를 `componentStyles` RGBA에 직접 합성하지 않고, `componentGeometry`의 계층과 로컬 opacity를 이용하도록 프롬프트 계약을 명확히 한다.

## 1. 검토 목적

선행 구현은 paint 단위 최종 alpha를 다음과 같이 계산한다.

```text
paintAlpha = color.a × paint.opacity
```

다음 단계에서는 Figma 노드 자체의 `opacity`와 부모 노드 opacity를 어떻게 반영할지 검토한다. Figma REST
API에서 node opacity는 기본값 `1`인 노드 전체 opacity이고, paint 내부 opacity와는 별도 값이다.

관련 코드:

- `FigmaDesignSpecMapper.components()` L118-150: 타입별 대표 `componentStyles`
- `FigmaDesignSpecMapper.buildGeometryTree()` L179-195: 노드 계층과 로컬 opacity 보존
- `ScreenSpecificationPromptFormatter.format()` L46-71: 대표 스타일과 geometry JSON을 함께 출력

## 2. 핵심 문제

### 2.1 `componentStyles`에는 실제 노드·부모 정보가 없다

`componentStyles`는 물리 노드별 구조가 아니라 `TABLE`, `ACTION_GROUP` 같은 의미 타입별 대표값이다. fill과
stroke도 서로 다른 물리 노드에서 최초 유효값을 선택할 수 있다. 따라서 대표 RGBA에 특정 노드나 부모의
opacity를 곱하면 잘못된 부모 관계를 적용할 수 있다.

### 2.2 geometry에는 이미 계층과 로컬 opacity가 있다

`NodeGeometry`는 자식 목록과 각 노드의 로컬 `opacity`를 보존한다. 따라서 생성 주체는 트리를 따라 다음
값을 계산할 수 있다.

```text
cumulativeNodeOpacity = 모든 조상 opacity × 현재 노드 opacity
effectivePaintAlpha = paintAlpha × cumulativeNodeOpacity
```

### 2.3 두 경로에서 합성하면 opacity가 중복 적용된다

매퍼가 `componentStyles.backgroundColor`에 부모 opacity를 미리 곱하고, Claude가 geometry의 opacity도
적용하면 동일 값이 두 번 곱해진다. 예를 들어 부모 `0.5`, 노드 `0.8`이면 올바른 누적값은 `0.4`지만
중복 적용 시 `0.16`이 될 수 있다.

### 2.4 그룹 opacity는 픽셀 합성과 완전히 같지 않을 수 있다

부모 그룹 opacity는 자식들이 합성된 결과 전체에 적용된다. 겹치는 자식의 opacity를 각각 미리 낮추는 것은
픽셀 단위 결과와 다를 수 있다. 따라서 대표 색상 문자열에 부모 opacity를 평탄화하는 방식은 정밀 재현
계약으로 부적절하다.

## 3. 권장 계약

### 데이터 의미

- `componentStyles.backgroundColor`/`borderColor`: `color.a × paint.opacity`만 반영한 대표 paint 색상
- `componentGeometry[].opacity`: 해당 노드의 로컬 opacity, 누락 시 `1.0`
- 부모 opacity: `children` 계층을 따라 소비자가 누적
- effective alpha: geometry가 실제 노드를 식별할 수 있을 때만 계산
- 같은 opacity를 RGBA와 CSS `opacity`에 중복 적용하지 않음

### 생성 경로별 적용

| 경로 | 적용 방식 |
|---|---|
| claude | 프롬프트에 위 의미와 누적 공식, 중복 적용 금지를 명시 |
| auto | 현재 geometry/componentStyles를 소비하지 않으므로 변경 없음 |
| 향후 결정론적 렌더러 | 노드별 PaintSpec 도입 후 트리 합성 단계에서 처리 |

## 4. 영향 범위

| 대상 | 영향 |
|---|---|
| `FigmaDesignSpecMapper` | 현재 paint alpha와 로컬 node opacity 보존 동작 유지 |
| `UiDesignSpec.NodeGeometry` | 필드 추가 없이 기존 `opacity` 의미를 문서화 |
| `ScreenSpecificationPromptFormatter` | geometry 안내 문구에 opacity 계약 추가 |
| formatter 테스트 | 누적 공식과 중복 적용 금지 문구 검증 |
| MCP baseline | Java 모델을 바꾸지 않으므로 변경 없음 |
| auto 경로 | 변경 없음 |

## 5. 제외 범위

- `effectiveOpacity` 신규 필드 추가
- `NodeGeometry.opacity`를 로컬값에서 누적값으로 변경
- 타입별 `componentStyles`에 부모 opacity 합성
- 그룹 blend mode, mask, effect를 포함한 픽셀 합성
- 노드별 `PaintSpec` 모델 도입

## 6. 리스크와 대응

| 리스크 | 대응 |
|---|---|
| Claude가 공식을 무시하거나 중복 적용 | 프롬프트에 MUST 수준의 명시적 규칙과 예시 추가, formatter 테스트 고정 |
| opacity 누락을 null로 전달 | 계약상 null은 `1.0`이라고 명시 |
| 대표 스타일과 geometry 스타일 충돌 | 실제 노드를 생성할 때 geometry 값을 우선하고 componentStyles는 의미 타입 fallback으로 규정 |
| 정확한 그룹 합성이 필요한 디자인 | 향후 결정론적 렌더러/노드별 PaintSpec 범위로 이관 |

## 7. 결론

이번 단계의 안전한 해법은 부모 opacity를 대표 RGBA에 미리 곱하는 것이 아니라, 이미 보존된 geometry 트리를
정확히 소비하도록 프롬프트 계약을 보강하는 것이다. 이 방식은 모델·MCP 계약을 유지하면서 opacity 중복
적용과 잘못된 부모 연결을 방지한다.
