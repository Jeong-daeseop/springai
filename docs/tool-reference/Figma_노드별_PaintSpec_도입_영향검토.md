# Figma 노드별 `PaintSpec` 도입 영향검토

> 작성일: 2026-09-02
> 선행 완료: 단색 alpha 정확도, 노드·부모 opacity 계약, revise·JOIN 디자인 문맥 보존
> 결론: 기존 대표 색상은 호환 fallback으로 유지하고, `NodeGeometry`에 순서형 `fills`/`strokes`를 optional 추가한다. 기존 MCP baseline drift는 구현 전 별도로 해소했다.

## 1. 목적

현재 노드 스타일은 `backgroundColor`와 `borderColor` 문자열 하나씩만 저장한다. 이 구조는 같은 노드의
복수 paint, 비가시 paint, 미지원 gradient/image의 존재와 배열 순서를 잃는다. 향후 그라데이션과 이미지
fill을 지원하려면 물리 노드에 연결된 paint 배열이 필요하다.

## 2. 권장 모델

```java
public record PaintSpec(
        String type,
        boolean visible,
        double opacity,
        @Nullable String color) {}
```

```java
public record NodeGeometry(
        // 기존 필드 유지
        @Nullable String backgroundColor,
        @Nullable String borderColor,
        @Nullable AutoLayout autoLayout,
        @Nullable TextStyle textStyle,
        @Nullable List<PaintSpec> fills,
        @Nullable List<PaintSpec> strokes,
        List<NodeGeometry> children) { ... }
```

정규화 규칙:

- `fills`/`strokes` null → `List.of()`
- 배열 순서 유지
- `visible` 누락 → `true`
- `opacity` 누락 → `1.0`, `0.0..1.0` 제한
- `type`은 알려진 Figma paint 타입 또는 `UNKNOWN`으로 정규화
- `color`는 SOLID의 `color.a`까지만 포함한 원시 RGBA
- 기존 `backgroundColor`/`borderColor`는 `color.a × paint.opacity`가 반영된 호환값 유지

지원 타입 식별자:

```text
SOLID, GRADIENT_LINEAR, GRADIENT_RADIAL, GRADIENT_ANGULAR,
GRADIENT_DIAMOND, IMAGE, VIDEO, PATTERN, SHADER, UNKNOWN
```

## 3. 구조 선택 비교

| 선택지 | 장점 | 단점 | 판단 |
|---|---|---|---|
| `NodeGeometry` 내부 배열 | 부모 계층·nodeId와 자연스럽게 결합, 별도 조인 없음 | 중첩 MCP schema 변경 | 채택 |
| 최상위 `nodePaints` 리스트 | geometry와 독립 저장 가능 | nodeId 조인·orphan·중복 규칙 필요 | 제외 |
| 기존 색상 문자열만 확장 | 계약 변화가 작음 | 복수 paint·gradient/image 표현 불가 | 제외 |

## 4. 호환성 정책

기존 필드는 제거하거나 의미를 바꾸지 않는다.

```text
NodeGeometry.fills/strokes
→ NodeGeometry.backgroundColor/borderColor
→ ComponentSpec.backgroundColor/borderColor
```

- 기존 Java 호출자를 위해 구형 `NodeGeometry` 생성자를 compat 생성자로 유지한다.
- 과거 JSON에 `fills`/`strokes`가 없어도 빈 리스트로 읽는다.
- `UiDesignSpec.SCHEMA_VERSION`은 additive optional 변경으로 `v1`을 유지하되 과거 artifact 역직렬화 테스트로
  호환성을 증명한다.
- v2 Design IR은 현재 geometry를 보존하지 않으므로 이번 범위를 Figma v1 직접 분석 경로로 한정한다.

## 5. 파싱 및 정제 영향

### paint 파싱

- 모든 paint의 순서를 보존한다.
- 1차에서는 SOLID의 원시 색상만 구조화한다.
- gradient/image 등은 최소 `type`, `visible`, `opacity`를 보존해 “paint 없음”과 구분한다.
- 노드당 fills/strokes 각각 최대 16개를 보존하고 초과 시 uncertainty를 기록한다.

### 반복 형제 축약

현재 반복 형제 판정은 type·정규화 이름·크기만 비교한다. 색상이 다른 행도 같은 반복으로 축약될 수 있으므로
다음 값도 동일해야 축약하도록 변경한다.

- fills/strokes
- node opacity
- cornerRadius
- textStyle

## 6. 계약 및 저장 영향

| 지점 | 영향 |
|---|---|
| `UiDesignSpec.NodeGeometry` | optional fills/strokes 및 PaintSpec 추가 |
| `ScreenSpecification.componentGeometry` | 상위 배선 변경 없이 중첩 데이터 전달 |
| DB JSON 저장/조회 | 자동 직렬화되지만 round-trip 테스트 필요 |
| `reviseScreenSpecification` | 중첩 MCP input schema 변경; revise 정책은 현재 저장본 불변 보존 |
| JOIN resolver | 선행 수정으로 componentGeometry 전체 보존 |
| formatter | paint 배열 우선순위와 alpha 의미 보강 |
| auto 경로 | 계속 소비하지 않으므로 동작 변경 없음 |

## 7. 선행 차단 조건: MCP baseline drift

초기 검토에서 확인된 `McpToolDefinitionSnapshotTest` baseline 불일치는 두 검증 Tool 설명의 기존 확장으로
분류했다. 해당 두 설명만 baseline에 반영했으며, 현재 snapshot 테스트는 통과한다. PaintSpec 변경은 아직
baseline에 포함하지 않았다.

구현 전 반드시:

1. 현재 baseline과 런타임 schema 차이를 도구별로 추출했다.
2. 변경은 `validateGeneratedCode`, `validateGeneratedCodeDirectory` 설명 2건으로 확인했다.
3. `CodeValidatorTool`의 실제 설명과 대조해 기존 승인 변경으로 분류했다.
4. 승인 변경만 baseline에 반영하고 snapshot 테스트 green을 확인했다.
5. 그 다음 PaintSpec만 적용해 `NodeGeometry`의 optional fills/strokes와 PaintSpec 추가만 diff인지 검토한다.

## 8. 주요 위험

| 위험 | 대응 |
|---|---|
| 공개 MCP schema 확장 | optional 필드, 구형 JSON/생성자 호환 테스트, 제한된 baseline diff 검토 |
| payload 폭증 | 노드당 fills/strokes 각각 16개 제한과 uncertainty |
| paint와 기존 색상 불일치 | paint 배열 우선, 기존 문자열 fallback 계약 고정 |
| 반복 축약으로 스타일 유실 | paint·opacity·radius·textStyle 동일성 포함 |
| gradient/image를 지원한 것으로 오해 | 1차는 메타데이터 보존만 하고 상세 지원 상태를 명시 |
| revise에서 외부 paint 변조 | 선행 정책대로 현재 저장된 디자인 문맥 불변 보존 |

## 9. 결론

노드별 PaintSpec은 gradient/image 확장의 필수 기반이지만 공개 MCP 계약을 건드린다. 기존 필드와 v1 artifact를
호환 유지하고 `NodeGeometry` 내부 optional 배열로 도입해야 한다. baseline 선행 게이트가 green이 되었으므로
이제 PaintSpec 데이터 모델과 매퍼 구현으로 진행할 수 있다.
