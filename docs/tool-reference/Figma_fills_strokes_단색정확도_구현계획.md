# Figma `fills`/`strokes` 단색 정확도 개선 구현명세서 및 구현목록

> 기반 문서: [`Figma_fills_strokes_단색정확도_영향검토.md`](./Figma_fills_strokes_단색정확도_영향검토.md)
> 상태: 구현 완료(2026-09-02)
> 범위: `paint.opacity` 반영, 타입별 첫 유효 fill/stroke 선택
> 제외: 노드/부모 누적 opacity, 그라데이션, 이미지 fill, 데이터 모델 및 MCP 계약 변경

## 1. 요구사항 요약

1. visible `SOLID` paint의 RGB와 `opacity`를 RGBA 문자열로 변환한다.
2. paint에 `opacity`가 없으면 `1.0`을 사용한다.
3. 같은 컴포넌트 타입에서 첫 노드의 fill/stroke가 null이면 이후 노드의 최초 유효값으로 보충한다.
4. fill과 stroke는 서로 독립적으로 선택한다.
5. 이미 선택된 유효값은 뒤 노드가 덮어쓰지 않는다.
6. 그라데이션과 이미지 paint는 기존처럼 무시한다.
7. 기존 모델, MCP 스키마, 프롬프트 형식을 변경하지 않는다.

## 2. 상세 구현명세

### 2.1 paint opacity 변환

대상: `FigmaDesignSpecMapper.firstSolidPaint()`와 RGBA 변환 헬퍼

권장 시그니처:

```java
private @Nullable String firstSolidPaint(JsonNode paints) {
    if (!paints.isArray()) return null;
    for (JsonNode paint : paints) {
        if ("SOLID".equals(paint.path("type").asText())
                && paint.path("visible").asBoolean(true)
                && paint.path("color").isObject()) {
            double paintOpacity = clampAlpha(paint.path("opacity").asDouble(1.0));
            return rgba(paint.path("color"), paintOpacity);
        }
    }
    return null;
}

private String rgba(JsonNode color, double paintOpacity) {
    int r = channel(color, "r");
    int g = channel(color, "g");
    int b = channel(color, "b");
    double colorAlpha = clampAlpha(color.path("a").asDouble(1.0));
    double finalAlpha = clampAlpha(colorAlpha * paintOpacity);
    return "rgba(%d,%d,%d,%.2f)".formatted(r, g, b, finalAlpha);
}
```

규칙:

- `paint.opacity` 기본값: `1.0`
- `color.a`가 실제 응답이나 기존 fixture에 있으면 호환을 위해 함께 곱한다.
- 최종 alpha: `clamp(color.a 기본 1.0 × paint.opacity 기본 1.0)`
- alpha 범위: `0.0..1.0`
- 기존 `tokens()`가 `rgba(JsonNode color)`를 호출하므로 기존 overload를 유지하거나 해당 호출을 명시적으로
  `rgba(color, 1.0)`으로 변경한다.
- RGB 채널의 기존 반올림 동작은 유지한다.

### 2.2 타입별 첫 유효 색상 선택

대상: `FigmaDesignSpecMapper.components()`

배열 인덱스를 그대로 유지할 경우의 갱신 규칙:

```java
String[] colors = colorsByType.computeIfAbsent(
        componentType, ignored -> new String[] {null, null});

if (colors[0] == null) {
    colors[0] = solidFillColor(node);
}
if (colors[1] == null) {
    colors[1] = solidStrokeColor(node);
}
```

필수 의미:

- 첫 매치 노드가 무색이어도 탐색을 계속한다.
- fill을 찾았지만 stroke를 못 찾았으면 이후 노드에서는 stroke만 계속 찾는다.
- fill과 stroke를 모두 찾은 후에는 뒤 노드가 값을 덮어쓰지 않는다.
- semantic field 이름 수집은 기존 동작을 유지한다.

가독성을 높이려면 mutable 배열 대신 매퍼 내부 전용 `MutableComponentColors` 클래스를 사용할 수 있다.
단, 공개 모델을 추가하거나 MCP 스키마를 변경해서는 안 된다.

### 2.3 제외 paint 처리

- `visible=false`: 무시하고 다음 paint 탐색
- `GRADIENT_*`: 무시하고 다음 paint 탐색
- `IMAGE`, `VIDEO`, `PATTERN`, `SHADER`: 무시하고 다음 paint 탐색
- 앞 paint가 미지원 타입이어도 뒤에 visible SOLID가 있으면 그 SOLID를 선택
- 모든 paint가 미지원 또는 비가시이면 null 반환

## 3. 수정 파일

| 파일 | 변경 내용 |
|---|---|
| `src/main/java/com/krdevops/springai/service/FigmaDesignSpecMapper.java` | paint opacity 반영, alpha clamp, 타입별 첫 유효 fill/stroke 보충 |
| `src/test/java/com/krdevops/springai/service/FigmaDesignSpecMapperTest.java` | opacity·선택 순서·비가시/미지원 paint 회귀 테스트 추가 및 기존 fixture 현실화 |

다음 파일은 수정하지 않는다.

- `UiDesignSpec.java`
- `ScreenSpecification.java`
- `ScreenSpecAssembler.java`
- `ScreenSpecificationPromptFormatter.java`
- `src/test/resources/mcp/tool-definitions-baseline.json`

## 4. 단계별 구현목록

### Phase 1 — opacity 정확도

1. `rgba(JsonNode, double)` 또는 동등한 paint-aware 변환 헬퍼를 추가한다.
2. `paint.opacity` 기본값과 alpha clamp를 구현한다.
3. 기존 `rgba(JsonNode)` 호출 경로의 호환성을 유지한다.
4. 기존 SOLID 테스트 fixture에서 `color.a` 의존을 제거하고 paint 구조를 실제 응답 형태에 가깝게 수정한다.

### Phase 2 — 첫 유효 색상 선택

5. `colorsByType.computeIfAbsent()`가 null 배열만 초기화하도록 변경한다.
6. fill과 stroke가 null인 경우에만 현재 노드에서 각각 보충한다.
7. 이미 선택된 색상을 뒤 노드가 덮어쓰지 않는지 확인한다.

### Phase 3 — 회귀 테스트

8. `paint.opacity=0.5`가 alpha `0.50`으로 출력되는 테스트를 추가한다.
9. `color.a=0.8`, `paint.opacity=0.5`가 alpha `0.40`으로 출력되는 호환 테스트를 추가한다.
10. opacity 누락 시 `1.00`이 되는 테스트를 추가한다.
11. 첫 매치 노드는 무색이고 두 번째 노드에 fill/stroke가 있는 테스트를 추가한다.
12. 첫 노드에는 fill만, 두 번째 노드에는 stroke만 있는 독립 선택 테스트를 추가한다.
13. 뒤 노드의 다른 색상이 최초 유효값을 덮어쓰지 않는 테스트를 추가한다.
14. 비가시 SOLID와 그라데이션 뒤의 visible SOLID를 올바르게 선택하는 테스트를 추가한다.
15. 그라데이션·이미지만 있는 경우 null을 유지하는 기존 동작을 검증한다.

### Phase 4 — 검증 및 문서 상태 갱신

16. 대상 매퍼 테스트를 실행한다.
17. 전체 테스트를 실행해 모델·MCP 계약 무변경을 확인한다.
18. MCP snapshot 실패 시 baseline을 자동 재생성하지 말고 이번 diff에 스키마 변경이 없는지 먼저 비교한다.
19. 검증 완료 후 이 문서 상태를 `구현 완료`로 바꾸고 구현 커밋과 테스트 결과를 기록한다.

## 5. 인수 조건

- [x] opacity 없는 불투명 빨강이 `rgba(255,0,0,1.00)`으로 출력된다.
- [x] `paint.opacity=0.5`인 빨강이 `rgba(255,0,0,0.50)`으로 출력된다.
- [x] `color.a=0.8`과 `paint.opacity=0.5`가 함께 있으면 alpha가 `0.40`이다.
- [x] 첫 매치 노드가 무색이어도 같은 타입의 뒤 노드 색상을 선택한다.
- [x] fill과 stroke를 서로 다른 순서의 노드에서 독립적으로 찾는다.
- [x] 최초 유효 fill/stroke는 뒤 노드의 값으로 덮어쓰지 않는다.
- [x] `visible=false` paint는 선택하지 않는다.
- [x] 미지원 paint 다음의 visible SOLID는 선택한다.
- [x] 그라데이션·이미지만 있으면 기존대로 null을 반환한다.
- [x] `ComponentSpec`, `ScreenSpecification`, MCP input schema가 변경되지 않는다.
- [x] claude 프롬프트의 `componentStyles` 텍스트 형식이 변경되지 않는다.
- [x] auto 경로 생성 결과에 변화가 없다.

## 6. 검증 명령

```bash
./gradlew test --tests "com.krdevops.springai.service.FigmaDesignSpecMapperTest" --console=plain
./gradlew test --console=plain
git diff -- src/test/resources/mcp/tool-definitions-baseline.json
```

마지막 명령의 기대 결과는 diff 없음이다. 현재 저장소에 이미 다른 MCP 계약 불일치가 존재한다면 이번 변경과
분리해 보고하며, baseline 삭제·재생성으로 덮어쓰지 않는다.

## 7. 후속 과제

단색 정확도 개선 완료 후 별도 승인과 명세를 거쳐 다음 순서로 확장한다.

1. 노드 및 부모 opacity 누적 합성 계약
2. 노드별 `PaintSpec` 모델
3. 그라데이션 stop/transform 보존 및 CSS 변환
4. IMAGE fill과 기존 Figma asset 다운로드 경로 연결

## 8. 구현 및 검증 결과

- `paint.opacity × color.a` 계산과 `0.0..1.0` alpha 제한을 구현했다.
- 타입별 fill/stroke를 각각 최초 유효값으로 보충하고 이후 값으로 덮어쓰지 않도록 구현했다.
- opacity 기본값·곱셈·범위 제한, 첫 유효값 보충, fill/stroke 독립 선택, 비가시·미지원 paint 회귀 테스트를 추가했다.
- `FigmaDesignSpecMapperTest`, `ScreenSpecAssemblerTest`, `ScreenSpecificationPromptFormatterTest`: 통과
- 전체 테스트: 1,980개 중 1개 실패, 14개 skipped. 실패 1건은 변경 전부터 존재한
  `McpToolDefinitionSnapshotTest` baseline 불일치이며 이번 변경은 모델·MCP baseline을 수정하지 않았다.
- 아키텍처 검증: 승인
