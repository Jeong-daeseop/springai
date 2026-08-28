# Figma 픽셀 재현 — claude 경로 확장 구현계획

> [`Figma_픽셀재현_claude경로_확장검토.md`](./Figma_픽셀재현_claude경로_확장검토.md)의 검토 결과를 바탕으로
> 작성한 구현명세서 + 구현목록. `fills`/`strokes`(색상) 반영 때보다 스코프가 명확히 크므로, 착수 전
> §3의 **핵심 설계 결정(옵션 A/B)** 을 먼저 확정해야 한다. 구현 승인 전까지는 이 문서에 따라 코드를
> 변경하지 않는다.

---

## 1. 배경 및 목적

`FigmaDesignSpecMapper`는 노드를 5개 시맨틱 타입(`TABLE`/`SEARCH_PANEL`/`FORM`/`PAGINATION`/
`ACTION_GROUP`)으로 뭉치고, 좌표(`absoluteBoundingBox`)는 대략적 배치 판단에만 쓰고 버린다.
이 문서는 `llmProvider="claude"` 경로에 한해, 노드별 좌표·간격·색상·텍스트 스타일을 지금보다
덜 축약된 형태로 넘겨 Claude가 더 픽셀에 가깝게 코드를 작성하도록 하는 설계와 작업 목록을
정리한다. **auto 경로(CSS 자동 주입, `.ftl` 재설계)는 이번 범위에서 제외**한다
(`Figma_색상_CSS자동주입_auto경로_검토.md` 참고).

### 설계 원칙
- **기존 계약 보존**: `UiDesignSpec`/`ScreenSpecification`은 필드를 "추가"만 하고, 기존 필드·
  생성자 시그니처는 그대로 둔다(레포의 compat 생성자 누적 패턴 — `Figma_fills_strokes_구현계획.md`
  §3.2 참고).
- **`withValidation()`/`withDesignContext()` 재발 방지**: `componentStyles` 도입 때 이 두
  메서드가 새 필드를 빠뜨려 값을 조용히 지울 뻔한 버그가 있었다. 새 필드(`componentGeometry`)도
  이 두 메서드에 명시적으로 전달하는지 **구현 시 반드시 재확인**한다.
- **claude 경로 전용**: `CrudModelFactory`(auto 경로)는 이 문서의 신규 필드를 참조하지 않는다.

---

## 2. 목표 아키텍처

```
FigmaDesignSpecMapper.buildGeometryTree()     [신규 메서드]
  └─ 노드 트리를 "부모-자식 구조를 보존한 채" 재귀 순회(collect()의 평탄화 방식과는 다름 — §3 참고)
  └─ 각 노드의 좌표/모서리/불투명도/색상/auto-layout/텍스트 스타일을 NodeGeometry로 구성
        ↓
UiDesignSpec.geometryTree: List<NodeGeometry>    [신규 필드]
        ↓
ScreenSpecAssembler.assemble()                    [수정]
  └─ resolvedUi.geometryTree() → ScreenSpecification.componentGeometry
        ↓
ScreenSpecification.componentGeometry: List<NodeGeometry>   [신규 필드]
        ↓ (auto 경로는 이 필드를 참조하지 않음 — CrudModelFactory 변경 없음)
ScreenSpecificationPromptFormatter.format()       [수정]
  └─ componentGeometry를 JSON 블록으로 직렬화 + KRDS 구조 유지 가드레일 문구 추가
        ↓
CrudPromptBuilderService/MasterDetailService 반환 프롬프트 안에 기하 정보 포함
  → Claude가 좌표·간격·색상·폰트를 참고해 코드 작성(결정론적 변환 아님 — 근사 재현)
```

---

## 3. 핵심 설계 결정 — 착수 전 확정 필요

`fills`/`strokes` 작업 때는 `FigmaDesignSpecMapper.collect()`가 이미 만든 평탄화된
`List<NodeInfo>`를 그대로 재사용할 수 있었다. **이번엔 그럴 수 없다** — 좌표/색상은 "타입별
대표값 1개"로도 의미가 통했지만, 레이아웃 재현은 "이 버튼이 이 auto-layout 컨테이너의 자식"이라는
**부모-자식 관계**가 없으면 Claude가 배치 구조 자체를 알 수 없다. `collect()`는 이 관계를 버리고
1차원 리스트로 평탄화하므로(`FigmaDesignSpecMapper.java` L69-80), **재사용이 아니라 새 트리 순회
메서드가 필요하다** — 이건 검토 문서(§3.1)에 명시되지 않았던, 이번 코드 확인으로 새로 드러난 지점.

두 가지 범위 중 하나를 확정해야 한다:

| | **옵션 A — 저비용** | **옵션 B — 검토 취지에 부합** |
|---|---|---|
| 방식 | 기존 5개 타입 그룹(`ComponentSpec`)에 좌표·모서리·텍스트 스타일 필드만 추가(색상과 동일하게 "타입당 대표 노드 1개") | 노드 트리 구조를 그대로 보존하는 새 `NodeGeometry` 모델 신설(부모-자식 포함) |
| 재사용성 | `components()` 그룹핑 로직 그대로 재사용 | 새 재귀 순회 메서드 신설 필요 |
| 픽셀 재현 기여도 | 낮음 — "표 하나의 대표 위치/크기" 정도만 알 수 있고 배치 구조는 여전히 모름 | 검토 문서가 말한 "노드별 좌표·간격" 재현에 실제로 필요한 수준 |
| 개발 규모 | 색상 작업과 비슷(작음) | 색상 작업보다 명확히 큼(신규 모델+신규 순회+정제 로직) |

**이 문서 이후 절은 옵션 B를 기준으로 작성한다** — 옵션 A는 "타입 대표 좌표 1개"만 추가하는
훨씬 축소된 버전이며, 착수 전 이 표를 보고 어느 쪽으로 갈지 확정해야 한다.

---

## 4. 데이터 모델 설계 (옵션 B)

### 4.1 `UiDesignSpec.NodeGeometry` (신규 record, `model/design/UiDesignSpec.java`)

```java
public record NodeGeometry(
        String nodeId, String type, String name,
        double x, double y, double width, double height,
        @Nullable Integer cornerRadius, @Nullable Double opacity,
        @Nullable String backgroundColor, @Nullable String borderColor,
        @Nullable AutoLayout autoLayout, @Nullable TextStyle textStyle,
        List<NodeGeometry> children) {

    public NodeGeometry {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public record AutoLayout(
            String direction, double itemSpacing,
            double paddingTop, double paddingRight, double paddingBottom, double paddingLeft) {}

    public record TextStyle(
            @Nullable String fontFamily, @Nullable Double fontSize,
            @Nullable Double fontWeight, @Nullable Double lineHeightPx) {}
}
```

새 record이므로 compat 생성자가 필요 없다(기존 호출부가 없음).

### 4.2 `UiDesignSpec`에 필드 추가 (`model/design/UiDesignSpec.java` L8-17)

```java
public record UiDesignSpec(
        String archetype, LayoutSpec layout, List<ComponentSpec> components,
        List<ActionSpec> actions, List<FieldHint> fieldHints, Map<String, String> tokens,
        List<InteractionSpec> interactions, List<String> uncertainties,
        List<NodeGeometry> geometryTree   // ← 신규
) {
    public UiDesignSpec {
        // ...기존 정규화 로직...
        geometryTree = geometryTree == null ? List.of() : List.copyOf(geometryTree);
    }

    /** geometryTree 도입 전 호출자 호환(기존 8-인자 시그니처 그대로 유지). */
    public UiDesignSpec(
            String archetype, LayoutSpec layout, List<ComponentSpec> components,
            List<ActionSpec> actions, List<FieldHint> fieldHints, Map<String, String> tokens,
            List<InteractionSpec> interactions, List<String> uncertainties) {
        this(archetype, layout, components, actions, fieldHints, tokens, interactions,
                uncertainties, List.of());
    }
}
```
`empty()` 팩토리(L29-31)는 새 8-인자 compat 생성자를 그대로 호출하므로 무변경.

### 4.3 `ScreenSpecification`에 필드 추가 (`model/design/ScreenSpecification.java` L8-28)

`componentStyles` 때와 동일한 패턴 — 현재의 19-인자 canonical 생성자를 compat으로 남기고
20-인자 canonical을 새로 추가:

```java
public record ScreenSpecification(
        /* ...기존 18개 필드..., */
        List<UiDesignSpec.ComponentSpec> componentStyles,
        List<UiDesignSpec.NodeGeometry> componentGeometry   // ← 신규
) {
    public ScreenSpecification {
        // ...기존 정규화 로직...
        componentGeometry = componentGeometry == null ? List.of() : List.copyOf(componentGeometry);
    }

    /** componentGeometry 도입 전 호출자 호환(기존 19-인자 시그니처 그대로 유지). */
    public ScreenSpecification(/* ...기존 19개 파라미터... */) {
        this(/* ...기존 19개 인자..., */ List.of());
    }
}
```

**주의(§1 설계 원칙 재확인)**: `withValidation()`(L103-108)과 `withDesignContext()`(L114-124)는
반드시 `componentGeometry`를 명시적으로 새 20-인자 canonical에 전달해야 한다 — `componentStyles`
도입 때 이 두 메서드가 새 필드를 빠뜨려 값을 조용히 지울 뻔한 버그가 있었다(구현 중 발견·수정됨).
같은 실수가 재발하지 않도록 이번엔 필드 추가와 동시에 이 두 메서드부터 고친다.

---

## 5. 핵심 로직 설계

### 5.1 `FigmaDesignSpecMapper.buildGeometryTree()` — 신규 재귀 메서드

`collect()`(L69-80)는 트리를 평탄화하므로 재사용하지 않고, 부모-자식을 보존하는 새 순회를 만든다.
기존 저수준 헬퍼(`firstSolidPaint(JsonNode)`, `rgba(JsonNode)`)는 이미 `JsonNode`를 인자로
받으므로 그대로 재사용 가능하다.

```java
private UiDesignSpec.NodeGeometry buildGeometryTree(JsonNode node) {
    JsonNode box = node.path("absoluteBoundingBox");
    List<UiDesignSpec.NodeGeometry> children = new ArrayList<>();
    for (JsonNode child : node.path("children")) {
        if (!child.path("visible").asBoolean(true)) continue;   // 비가시 노드 제외
        children.add(buildGeometryTree(child));
    }
    children = collapseRepeatedSiblings(children);              // §5.3 정제 규칙

    return new UiDesignSpec.NodeGeometry(
            node.path("id").asText(""), node.path("type").asText(""), node.path("name").asText(""),
            box.path("x").asDouble(0), box.path("y").asDouble(0),
            box.path("width").asDouble(0), box.path("height").asDouble(0),
            node.path("cornerRadius").isNumber() ? node.path("cornerRadius").asInt() : null,
            node.path("opacity").isNumber() ? node.path("opacity").asDouble() : null,
            firstSolidPaint(node.path("fills")), firstSolidPaint(node.path("strokes")),
            autoLayoutOf(node), textStyleOf(node), children);
}

private @Nullable UiDesignSpec.NodeGeometry.AutoLayout autoLayoutOf(JsonNode node) {
    String mode = node.path("layoutMode").asText("NONE");
    if ("NONE".equals(mode)) return null;
    return new UiDesignSpec.NodeGeometry.AutoLayout(
            mode, node.path("itemSpacing").asDouble(0),
            node.path("paddingTop").asDouble(0), node.path("paddingRight").asDouble(0),
            node.path("paddingBottom").asDouble(0), node.path("paddingLeft").asDouble(0));
}

private @Nullable UiDesignSpec.NodeGeometry.TextStyle textStyleOf(JsonNode node) {
    if (!"TEXT".equals(node.path("type").asText())) return null;
    JsonNode style = node.path("style");
    if (!style.isObject()) return null;
    return new UiDesignSpec.NodeGeometry.TextStyle(
            style.path("fontFamily").isTextual() ? style.path("fontFamily").asText() : null,
            style.path("fontSize").isNumber() ? style.path("fontSize").asDouble() : null,
            style.path("fontWeight").isNumber() ? style.path("fontWeight").asDouble() : null,
            style.path("lineHeightPx").isNumber() ? style.path("lineHeightPx").asDouble() : null);
}
```

`map()`(L20-31)의 마지막에 `geometryTree = List.of(buildGeometryTree(root))`를 추가해
`UiDesignSpec` 생성자에 전달한다.

### 5.2 `ScreenSpecAssembler.assemble()` 매핑 추가 (`ScreenSpecAssembler.java` L91-98)

```java
ScreenSpecification draft = new ScreenSpecification(
        /* ...기존 인자..., */
        resolvedUi.components(),
        resolvedUi.geometryTree());   // ← 추가되는 한 줄
```

### 5.3 정제(가지치기) 규칙 — 신규

원본 그대로 넘기면 너무 방대하므로(검토 §3.5), 1차 구현 규칙을 명확히 고정한다:

- **비가시 노드 제외**: `visible:false`는 트리에서 아예 뺀다(§5.1에 이미 반영).
- **반복 형제 축약**: 같은 부모 아래 `type`+`name`이 동일한 형제가 3개 이상 연속되면(표의
  반복 행 등), **첫 번째만 대표로 남기고 나머지는 제거**한다(`collapseRepeatedSiblings()` 신규
  private 메서드). 제거했다는 사실은 `uncertainties`에 "반복 패턴 N개 중 1개만 대표로 반영"
  형태로 기록한다.
- **깊이 상한은 API 호출 단계 그대로**: `FigmaApiClient`의 기존 `depth`(최대 10) 제한을 그대로
  따른다 — 이번 작업에서 깊이 자체를 늘리지 않는다(§8 리스크 참고).

### 5.4 `ScreenSpecificationPromptFormatter.format()` 확장

`componentStyles` 블록 뒤에 `componentGeometry`를 JSON으로 직렬화해 추가한다. 트리 구조라
들여쓰기 텍스트 줄보다 JSON 블록이 Claude가 파싱하기 안전하다(검토 §3.4).

```java
if (!specification.componentGeometry().isEmpty()) {
    result.append("  componentGeometry(JSON, 참고용 — 정확한 좌표/간격/색상/폰트는 이 값을 따르되 ")
          .append("krds-*/egov-* 클래스 구조와 컴포넌트 트리는 유지):\n");
    try {
        result.append(objectMapper.writeValueAsString(specification.componentGeometry())).append('\n');
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("componentGeometry 직렬화 실패", e);
    }
}
```

`ScreenSpecificationPromptFormatter`는 현재 `ObjectMapper` 의존성이 없으므로 생성자에 새로
주입해야 한다(신규 의존성 추가).

**KRDS 가드레일 문구는 위 안내문 한 줄로는 부족할 수 있다** — 검토 §3.6에서 짚었듯, Claude가
원본 좌표를 보고 자유롭게 커스텀 마크업으로 이탈할 위험이 있으므로, `CrudPromptBuilderService`가
조립하는 전체 프롬프트 상단(지시문 영역)에도 "componentGeometry는 참고 수치일 뿐, 화면 구조와
클래스 체계는 기존 KRDS 템플릿 규칙을 따르라"는 문구를 별도로 강조해야 한다 — 이 부분은
`ScreenSpecificationPromptFormatter` 밖의 프롬프트 조립부(`CrudPromptBuilderService`) 확인이
추가로 필요하다(이번 문서에서 코드 확인은 안 함).

---

## 6. 신규/수정 파일 목록

| 파일 | 변경 유형 | 내용 |
|---|---|---|
| `model/design/UiDesignSpec.java` | 수정 | `NodeGeometry`/`AutoLayout`/`TextStyle` record 신설, `geometryTree` 필드 + compat 생성자 |
| `model/design/ScreenSpecification.java` | 수정 | `componentGeometry` 필드 + compat 생성자, `withValidation()`/`withDesignContext()`에 명시적 전달 |
| `service/FigmaDesignSpecMapper.java` | 수정 | `buildGeometryTree()`/`autoLayoutOf()`/`textStyleOf()`/`collapseRepeatedSiblings()` 신규 private 메서드, `map()`에서 호출 |
| `service/ScreenSpecAssembler.java` | 수정 | `assemble()`에 `resolvedUi.geometryTree()` 매핑 한 줄 |
| `service/ScreenSpecificationPromptFormatter.java` | 수정 | `ObjectMapper` 의존성 주입, `componentGeometry` JSON 블록 + 가드레일 문구 |
| `service/CrudPromptBuilderService.java`(추정) | 확인·수정 | 프롬프트 상단에 KRDS 구조 유지 가드레일 문구 추가(§5.4 — 코드 확인 필요) |
| `src/test/resources/mcp/tool-definitions-baseline.json` | 재생성 | `reviseScreenSpecification` 입력 스키마 변경으로 baseline 재생성 |

---

## 7. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 트리 크기가 화면 복잡도에 비례해 커짐 | 프롬프트 컨텍스트 예산 초과, Claude가 노이즈에 묻힐 위험 | §5.3 정제 규칙(비가시 제외·반복 축약)을 1차부터 필수 적용. 그래도 복잡한 화면은 여전히 클 수 있음(완전 해결 아님, 인지된 한계) |
| Claude가 KRDS 클래스 체계를 이탈 | 정부 표준 UI 일관성 저하 | §5.4 가드레일 문구. 문구만으로 100% 방지는 안 됨(LLM 지시는 강제가 아니라 강한 요청) |
| `buildGeometryTree()`가 `collect()`와 별개 순회라 성능·일관성 이슈 가능 | 노드 수 많은 화면에서 두 번 순회(색상용 `collect()` + 기하용 `buildGeometryTree()`) | 1차는 별개 유지(단순함 우선). 필요시 이후 리팩터링에서 통합 검토 |
| `ScreenSpecification` 필드 추가로 MCP 계약 변경 | `reviseScreenSpecification` 스키마 불일치로 테스트 실패 | baseline 삭제 → `McpToolDefinitionSnapshotTest` 재실행(기존 절차 그대로) |
| auto 경로는 무영향(의도됨) | 없음 | `CrudModelFactory`가 `componentGeometry` 미참조 확인만 하면 됨 |
| Board 경로에 `ScreenSpecificationPromptFormatter` 미적용 확인됨(§검토 문서 3.9) | Board는 이번 확장 혜택 없음 | 이번 범위에서 Board는 제외, 별도 확인·작업 필요 |

---

## 8. 1차 구현 제외 범위 (2차 이후)

- 옵션 A(타입 대표 좌표 1개) — §3에서 옵션 B로 확정할 경우 불필요
- 픽셀 일치도를 실제로 측정하는 도구(비교 diff, 유사도 %) — 이번 범위는 "정보를 더 넘긴다"까지이고
  "얼마나 가까워졌는지 측정"은 포함하지 않음
- 아이콘/이미지 asset 실제 다운로드·삽입(`FigmaApiClient.queryImages()` 연결) — 좌표·색상·폰트
  텍스트 정보만 다루고 실제 바이너리 자산 처리는 제외
- 반응형/브레이크포인트 대응
- Board 경로로의 확장(§7 리스크 참고)
- `collapseRepeatedSiblings()`보다 정교한 dedup(예: 유사도 기반 판단) — 1차는 "타입+이름 완전
  일치·3개 이상 연속"이라는 단순 휴리스틱만 적용

---

## 9. 단계별 구현목록

### Phase 0 — 설계 결정 확정 (필수, 코드 작업 아님)
| 순서 | 작업 |
|---|---|
| 1 | §3 옵션 A/B 중 확정 (이 문서는 옵션 B 기준) |

### Phase 1 — 데이터 모델 확장
| 순서 | 작업 |
|---|---|
| 2 | `UiDesignSpec.NodeGeometry`/`AutoLayout`/`TextStyle` record 신설 |
| 3 | `UiDesignSpec`에 `geometryTree` 필드 + compat 생성자 추가 |
| 4 | `ScreenSpecification`에 `componentGeometry` 필드 + compat 생성자 추가 |
| 5 | `withValidation()`/`withDesignContext()`에 `componentGeometry` 명시적 전달(누락 시 조용한 데이터 소실 재발) |

### Phase 2 — 파싱 로직
| 순서 | 작업 |
|---|---|
| 6 | `buildGeometryTree()` 신규 재귀 메서드 추가 |
| 7 | `autoLayoutOf()`/`textStyleOf()` 헬퍼 추가 |
| 8 | `collapseRepeatedSiblings()` 정제 로직 추가(§5.3) |
| 9 | `map()`에서 `geometryTree` 채워 `UiDesignSpec`에 전달 |

### Phase 3 — 매핑·소비
| 순서 | 작업 |
|---|---|
| 10 | `ScreenSpecAssembler.assemble()`에 `geometryTree` → `componentGeometry` 매핑 한 줄 추가 |
| 11 | `ScreenSpecificationPromptFormatter`에 `ObjectMapper` 주입, `componentGeometry` JSON 블록 추가 |
| 12 | `CrudPromptBuilderService`(및 필요 시 `MasterDetailService`) 프롬프트 상단에 KRDS 구조 유지 가드레일 문구 추가(§5.4 — 코드 확인 선행 필요) |

### Phase 4 — 계약·테스트
| 순서 | 작업 |
|---|---|
| 13 | `tool-definitions-baseline.json` 삭제 → `McpToolDefinitionSnapshotTest` 재실행으로 재생성 |
| 14 | `FigmaDesignSpecMapperTest`에 트리 구조/auto-layout/텍스트스타일/반복축약 케이스 추가 |
| 15 | `ScreenSpecAssemblerTest`에 `componentGeometry` 매핑 케이스 추가 |
| 16 | `ScreenSpecificationPromptFormatterTest`에 `componentGeometry` JSON 출력 케이스 추가 |
| 17 | `./gradlew build` 전체 통과 확인 |

---

## 10. 검증 방법

1. `./gradlew build` — 전체 테스트 통과 확인
2. 중첩 auto-layout이 있는 실제 Figma 프레임으로 `analyzeFigmaReference()` →
   `createScreenSpecification()` 호출 → `componentGeometry`에 부모-자식 구조가 보존됐는지 확인
3. `buildFullCrudPrompt(..., llmProvider="claude", screenSpecificationId=...)` 호출 → 반환
   프롬프트에 `componentGeometry` JSON 블록과 KRDS 가드레일 문구가 포함되는지 확인
4. `auto` 경로로 같은 명세 호출 → 생성 파일이 이전과 **동일**한지 확인(auto 무변경 보장)
5. 표처럼 반복 요소가 있는 프레임으로 테스트 → `collapseRepeatedSiblings()`가 실제로 축약하고
   `uncertainties`에 기록되는지 확인

---

## 11. 관련 문서

- [Figma_픽셀재현_claude경로_확장검토.md](./Figma_픽셀재현_claude경로_확장검토.md) — 이 구현계획의 기반이 된 검토 원문
- [Figma_fills_strokes_구현계획.md](./Figma_fills_strokes_구현계획.md) — compat 생성자 패턴, `withValidation`/`withDesignContext` 재발 방지 근거가 된 선행 작업
- [Figma_색상_CSS자동주입_auto경로_검토.md](./Figma_색상_CSS자동주입_auto경로_검토.md) — auto 경로 제외 근거
