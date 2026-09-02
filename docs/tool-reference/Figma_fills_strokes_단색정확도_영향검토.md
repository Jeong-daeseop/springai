# Figma `fills`/`strokes` 단색 정확도 개선 영향검토

> 작성일: 2026-09-02
> 대상: 기존 claude 경로용 `fills`/`strokes` 1차 구현의 정확도 보완
> 결론: `paint.opacity` 반영과 타입별 첫 유효 fill/stroke 선택은 우선 수정한다. 그라데이션과 이미지 fill은 이번 범위에서 제외한다.

## 1. 검토 목적

현재 `FigmaDesignSpecMapper`는 Figma 노드의 첫 번째 visible `SOLID` paint를 RGBA 문자열로 변환하고,
컴포넌트 의미 타입별 첫 매치 노드의 fill/stroke를 대표 색상으로 저장한다.

이 구조에는 다음 두 가지 단색 정확도 문제가 있다.

1. `paint.opacity`를 읽지 않아 반투명 paint가 불투명하게 변환될 수 있다.
2. 타입별 첫 매치 노드에 색상이 없으면 뒤에 등장하는 실제 유효 색상을 선택하지 못한다.

관련 코드:

- `src/main/java/com/krdevops/springai/service/FigmaDesignSpecMapper.java` L118-172
- `src/main/java/com/krdevops/springai/service/FigmaDesignSpecMapper.java` L455-460
- `src/test/java/com/krdevops/springai/service/FigmaDesignSpecMapperTest.java` L111-156, L356-376

## 2. 현재 동작과 문제

### 2.1 `paint.opacity` 누락

현재 `firstSolidPaint()`는 `paint.color`만 `rgba()`에 전달한다. `rgba()`는 `color.a`가 없으면 alpha를
`1`로 정한다. 그러나 Figma paint의 전체 투명도는 paint 자체의 `opacity` 속성에 별도로 존재할 수 있다.

예:

```json
{
  "type": "SOLID",
  "color": {"r": 1, "g": 0, "b": 0},
  "opacity": 0.5
}
```

현재 결과는 `rgba(255,0,0,1.00)`이 될 수 있지만 기대 결과는 `rgba(255,0,0,0.50)`이다.

### 2.2 첫 매치 노드의 null 고정

현재 `colorsByType.computeIfAbsent()`는 타입이 처음 매치되는 순간 `[fill, stroke]`를 고정한다.

```text
ACTION_GROUP 컨테이너: fill 없음, stroke 없음  ← 먼저 매치
└─ 실제 버튼: fill 파랑, stroke 회색          ← 현재는 무시
```

따라서 실제 색상이 존재해도 최종 `ComponentSpec`에는 `null`이 들어갈 수 있다. fill과 stroke 중 하나만
먼저 발견되는 경우에도 나머지 값은 이후 노드에서 보충되지 않는다.

## 3. 권장 해결 범위

### 3.1 이번에 반드시 해결

- visible `SOLID` paint의 `opacity`를 RGBA alpha에 반영한다.
- `opacity`가 없으면 Figma 기본값인 `1.0`으로 처리한다.
- alpha는 `0.0..1.0` 범위로 제한한다.
- 같은 의미 타입에서 fill과 stroke를 각각 독립적으로 최초 유효값이 나올 때까지 탐색한다.
- 유효값을 얻은 뒤에는 뒤 노드의 값으로 덮어쓰지 않아 현재의 “대표값 1건” 계약을 유지한다.

### 3.2 이번에는 제외

- 노드 자체 `opacity`와 부모 노드 opacity의 누적 합성
- `GRADIENT_LINEAR` 등 그라데이션 paint 변환
- `IMAGE` fill 다운로드 및 asset 경로 연결
- 타입별 대표 스타일을 노드별 스타일 모델로 변경
- `ComponentSpec`, `ScreenSpecification`, MCP 입력 스키마 변경

노드/부모 opacity까지 즉시 합성하지 않는 이유는 현재 `componentStyles`가 타입별 대표값이고, 서로 다른
노드에서 fill과 stroke를 보충할 수 있어 단일 노드의 opacity를 어느 값에 적용할지 계약을 먼저 정해야
하기 때문이다. 이번 수정은 paint 자체의 명시적 opacity만 정확히 반영한다.

## 4. 영향 범위

| 대상 | 영향 | 판단 |
|---|---|---|
| `FigmaDesignSpecMapper.firstSolidPaint()` | paint 전체를 받아 color와 opacity를 함께 변환 | 수정 필요 |
| `FigmaDesignSpecMapper.rgba()` | alpha 인자 또는 paint 전용 변환 헬퍼 필요 | 수정 필요 |
| `FigmaDesignSpecMapper.components()` | 타입별 fill/stroke null을 이후 유효값으로 보충 | 수정 필요 |
| `UiDesignSpec.ComponentSpec` | 기존 두 색상 문자열을 그대로 사용 | 변경 없음 |
| `ScreenSpecification` | 기존 `componentStyles` 구조 유지 | 변경 없음 |
| `ScreenSpecAssembler` | 기존 pass-through 유지 | 변경 없음 |
| `ScreenSpecificationPromptFormatter` | 기존 RGBA 문자열 출력 유지 | 변경 없음 |
| MCP tool baseline | 모델/Tool 스키마가 바뀌지 않음 | 갱신 불필요 |
| auto 생성 경로 | `componentStyles`를 소비하지 않음 | 동작 변경 없음 |

## 5. 호환성과 위험

### 호환성

- 메서드 내부 구현만 변경되므로 Java 생성자와 JSON/MCP 계약은 유지된다.
- 불투명 단색은 기존과 동일한 RGBA 문자열을 생성한다.
- claude 프롬프트 형식은 그대로이고 alpha 값만 실제 Figma paint에 가까워진다.

### 남는 위험

1. 같은 의미 타입의 서로 다른 노드에서 fill과 stroke가 각각 선택되면 하나의 실제 노드 스타일이 아니라
   합성 대표값이 될 수 있다. 이는 기존 타입별 대표값 모델의 한계다.
2. 노드/부모 opacity는 이번 범위에서 합성하지 않으므로 픽셀 단위 최종 alpha와 다를 수 있다.
3. 여러 SOLID paint가 겹친 경우 첫 visible SOLID만 선택하며 blend mode 합성은 하지 않는다.
4. 그라데이션과 이미지는 계속 의도적으로 무시된다.

## 6. 결론

이 수정은 단색 처리 결과가 명백히 틀리거나 누락되는 경우를 줄이는 선행 보완이다. 변경 범위가 매퍼 내부와
테스트에 한정되고 데이터 모델 및 MCP 계약을 바꾸지 않으므로 구현 위험은 낮다. 그라데이션·이미지 지원에
앞서 이 단색 정확도 개선을 먼저 완료하는 것이 적절하다.
