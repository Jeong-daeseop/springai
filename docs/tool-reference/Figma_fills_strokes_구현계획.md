# Figma `fills`/`strokes`(색상) 반영 구현계획 — claude 경로 전용 1차 범위

> [`Figma_fills_strokes_반영_검토.md`](./Figma_fills_strokes_반영_검토.md)의 검토 결과를 바탕으로 작성한
> 구현명세서 + 구현목록. 검토 결론대로 **claude 경로(텍스트 프롬프트)에만 반영**하는 저비용 범위로
> 스코프를 좁혔다. auto 경로 CSS 자동주입 + `.ftl` 템플릿 재설계는 2차(제외 범위)로 명시적으로 미룬다.
>
> **상태(2026-09-02 갱신): 구현 완료.** 커밋 `bbc8079`(feat: capture Figma component fills/strokes for
> claude-path prompts)로 §8 Phase 1~4가 전부 반영됐다. 이 문서는 완료된 구현의 설계 기록으로 유지한다.

---

## 1. 배경 및 목적

`FigmaDesignSpecMapper`는 컴포넌트별 `fills`/`strokes`(색상)를 전혀 읽지 않는다. 이 문서는 그 색상을
읽어서 `claude` 경로(`ScreenSpecificationPromptFormatter`가 만드는 텍스트 프롬프트)에만 반영하는
구체적 설계와 작업 목록을 정리한다. auto 경로(CSS 자동 주입, `.ftl` 템플릿 재설계)는 정부 표준
디자인 시스템(KRDS)과의 일관성 트레이드오프를 먼저 결정해야 하므로 이번 범위에서 제외한다.

### 설계 원칙
- **기존 계약 보존**: `ComponentSpec`/`ScreenSpecification`은 필드를 "추가"만 하고, 기존 필드·생성자
  시그니처는 그대로 둔다(레포에 이미 있는 compat 생성자 누적 패턴을 그대로 따른다 — 아래 §3 참고).
- **재귀 순회 로직 재사용**: `FigmaDesignSpecMapper.collect()`가 이미 모든 노드(루트+자손)를
  `NodeInfo.raw()`(원본 `JsonNode`)와 함께 순회하고 있으므로, 새로운 트리 순회 코드는 필요 없다.
  기존 `components()` 메서드 안에서 이미 순회된 노드의 `raw()`를 한 번 더 들여다보기만 하면 된다.
- **SOLID만 처리**: `fills`/`strokes` 배열에 `GRADIENT_LINEAR`/`IMAGE` 등이 있으면 무시한다
  (§6 리스크 참고).

---

## 2. 목표 아키텍처

```
FigmaDesignSpecMapper.components()          [수정]
  └─ 이미 순회된 NodeInfo.raw()에서
     fills[0](SOLID)/strokes[0](SOLID)만 rgba() 문자열로 추출
  └─ UiDesignSpec.ComponentSpec(type, semanticFields,
                                 backgroundColor, borderColor)  [필드 2개 추가]
        ↓
ScreenSpecAssembler.assemble()               [수정]
  └─ resolvedUi.components() → ScreenSpecification.componentStyles  [매핑 라인 추가]
        ↓
ScreenSpecification                          [필드 1개 추가: componentStyles]
        ↓ (auto 경로는 이 필드를 아예 참조하지 않음 — CrudModelFactory 변경 없음)
ScreenSpecificationPromptFormatter.format()  [수정]
  └─ componentStyles를 텍스트 블록으로 출력 (claude 경로 전용 소비)
        ↓
CrudPromptBuilderService 반환 프롬프트 안에 색상 정보 포함 → Claude가 직접 코드에 반영
```

---

## 3. 데이터 모델 설계

### 3.1 `UiDesignSpec.ComponentSpec` (`model/design/UiDesignSpec.java` L44)

현재:
```java
public record ComponentSpec(String type, List<String> semanticFields) {}
```

변경 후 — 레포에 이미 있는 compat 생성자 패턴(`LayoutSpec` L35-42, `ScreenSpecification` L40-83)을
그대로 따른다:
```java
public record ComponentSpec(
        String type, List<String> semanticFields,
        @Nullable String backgroundColor, @Nullable String borderColor) {

    /** 색상 필드 도입 전 호출자 호환. */
    public ComponentSpec(String type, List<String> semanticFields) {
        this(type, semanticFields, null, null);
    }
}
```
2-인자 생성자를 쓰는 기존 호출부는 **전부 그대로 컴파일된다.**

### 3.2 `ScreenSpecification` (`model/design/ScreenSpecification.java` L8-27)

`componentStyles` 필드 1개 추가. 이 record는 이미 5개의 compat 생성자가 누적돼 있으므로(L40-83),
같은 방식으로 **현재의 18-인자 canonical 생성자를 그대로 compat 생성자로 남기고** 19-인자
canonical 생성자를 새로 추가한다:

```java
public record ScreenSpecification(
        String id, int version, ScreenSpecStatus status, String screenName, String featureType,
        String archetype, String database, String primaryTable, List<DataSourceSpec> dataSources,
        List<PageSpec> pages, List<SpecIssue> issues, LayoutDensity layoutDensity,
        FormColumnLayout formColumnLayout, ActionPlacement actionPlacement,
        SearchPanelPlacement searchPanelPlacement, LocalDateTime createdAt,
        @Nullable VersionedArtifactReference uiDesignSpecReference,
        @Nullable VersionedArtifactReference designSystemSnapshotReference,
        List<UiDesignSpec.ComponentSpec> componentStyles   // ← 신규
) {
    public ScreenSpecification {
        // ...기존 정규화 로직...
        componentStyles = componentStyles == null ? List.of() : List.copyOf(componentStyles);
    }

    /** componentStyles 도입 전 호출자 호환(기존 18-인자 시그니처 그대로 유지). */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName, String featureType,
            String archetype, String database, String primaryTable, List<DataSourceSpec> dataSources,
            List<PageSpec> pages, List<SpecIssue> issues, LayoutDensity layoutDensity,
            FormColumnLayout formColumnLayout, ActionPlacement actionPlacement,
            SearchPanelPlacement searchPanelPlacement, LocalDateTime createdAt,
            @Nullable VersionedArtifactReference uiDesignSpecReference,
            @Nullable VersionedArtifactReference designSystemSnapshotReference) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, layoutDensity, formColumnLayout, actionPlacement,
                searchPanelPlacement, createdAt, uiDesignSpecReference, designSystemSnapshotReference,
                List.of());
    }
}
```
기존 5개 compat 생성자(L40-83)는 전부 18-인자 생성자를 `this(...)`로 호출하므로, **이 compat 생성자
하나만 추가하면 production 5개 파일·test 27개 파일의 기존 호출부는 그대로 컴파일된다** — 검토
문서(§3)가 "전부 컴파일 영향"이라고 썼던 것보다 실제 파급력은 훨씬 작다(이 부분은 검토 문서 대비
정정).

> **설계 판단**: `ScreenSpecification`이 `UiDesignSpec.ComponentSpec` 타입을 그대로 재사용하도록
> 했다. 두 모델 패키지가 이미 같은 `model.design` 패키지라 결합도 증가가 미미하다고 판단했지만,
> "화면명세는 UiDesignSpec을 몰라야 한다"는 계층 분리를 더 중시한다면 `ComponentStyleSpec`이라는
> 별도 record를 `model/design/`에 신설하는 대안도 있다 — 구현 착수 전 확정 필요.

---

## 4. 핵심 로직 설계

### 4.1 `FigmaDesignSpecMapper.components()` 확장 (`FigmaDesignSpecMapper.java` L94-116)

현재 로직은 컴포넌트 후보 노드를 감지해 **의미 타입(TABLE/SEARCH_PANEL/FORM/PAGINATION/
ACTION_GROUP)별로 이름만 모으는 그룹핑**이다 — 물리적 노드 1개당 1개가 아니라 "타입당 1개"로
합쳐진다. 색상도 이 타입 단위로 붙이려면, **그 타입으로 처음 매치된 노드의 색상만 대표값으로
채택**하는 방식이 가장 단순하다(§6 리스크에 한계 명시):

```java
private List<UiDesignSpec.ComponentSpec> components(
        List<NodeInfo> nodes, List<String> uncertainties) {
    Map<String, LinkedHashSet<String>> values = new LinkedHashMap<>();
    Map<String, String[]> colorsByType = new LinkedHashMap<>(); // [backgroundColor, borderColor]
    for (NodeInfo node : nodes) {
        String normalized = text(node);
        String componentType = /* 기존 판별 로직 그대로 */;
        if (componentType != null) {
            values.computeIfAbsent(componentType, ignored -> new LinkedHashSet<>())
                    .add(node.name().isBlank() ? node.type() : node.name());
            colorsByType.computeIfAbsent(componentType,
                    ignored -> new String[]{solidFillColor(node), solidStrokeColor(node)});
        } else if (/* 기존 uncertainty 로직 그대로 */) { ... }
    }
    return values.entrySet().stream()
            .map(entry -> {
                String[] colors = colorsByType.get(entry.getKey());
                return new UiDesignSpec.ComponentSpec(
                        entry.getKey(), List.copyOf(entry.getValue()), colors[0], colors[1]);
            })
            .toList();
}

/** fills 배열에서 첫 번째 SOLID·visible 페인트만 rgba 문자열로 반환. 그 외 타입(GRADIENT/IMAGE)은 무시. */
private @Nullable String solidFillColor(NodeInfo node) {
    return firstSolidPaint(node.raw().path("fills"));
}

/** strokes 배열에서 첫 번째 SOLID·visible 페인트만 rgba 문자열로 반환. */
private @Nullable String solidStrokeColor(NodeInfo node) {
    return firstSolidPaint(node.raw().path("strokes"));
}

private @Nullable String firstSolidPaint(JsonNode paints) {
    if (!paints.isArray()) return null;
    for (JsonNode paint : paints) {
        if ("SOLID".equals(paint.path("type").asText())
                && paint.path("visible").asBoolean(true)
                && paint.path("color").isObject()) {
            return rgba(paint.path("color")); // 기존 rgba() 헬퍼(L300-306) 그대로 재사용
        }
    }
    return null;
}
```

`rgba()` 헬퍼는 이미 있고(L300-306), `NodeInfo.raw()`도 이미 `collect()`(L69-80)가 채워주므로
**신규 재귀 순회 코드는 없다** — 검토 문서에 있던 "재귀 순회 로직 신설"은 이미 존재하는 `collect()`를
그대로 쓰면 되는 것으로 확인해 스코프가 줄었다(검토 문서 대비 정정).

### 4.2 `ScreenSpecAssembler.assemble()` 매핑 추가 (`ScreenSpecAssembler.java` L91-97)

```java
ScreenSpecification draft = new ScreenSpecification(
        UUID.randomUUID().toString(), 1, ScreenSpecStatus.DRAFT,
        blank(screenName) ? tableName : screenName,
        blank(featureType) ? "crud" : featureType,
        archetype, database, tableName,
        List.of(DataSourceSpec.primary(database, tableName)), pages, issues,
        density, formColumnLayout, actionPlacement, searchPanelPlacement, LocalDateTime.now(),
        null, null,
        resolvedUi.components());   // ← 추가되는 한 줄
```

### 4.3 `ScreenSpecificationPromptFormatter.format()` 확장 (`ScreenSpecificationPromptFormatter.java` L12-52)

`dataSources` 블록 출력 뒤, `pages` 루프 앞에 추가:
```java
if (!specification.componentStyles().isEmpty()) {
    result.append("  componentStyles:\n");
    for (UiDesignSpec.ComponentSpec component : specification.componentStyles()) {
        result.append("    - ").append(component.type());
        if (component.backgroundColor() != null) {
            result.append(" backgroundColor=").append(component.backgroundColor());
        }
        if (component.borderColor() != null) {
            result.append(" borderColor=").append(component.borderColor());
        }
        result.append('\n');
    }
}
```

---

## 5. 신규/수정 파일 목록

| 파일 | 변경 유형 | 내용 |
|---|---|---|
| `model/design/UiDesignSpec.java` | 수정 | `ComponentSpec`에 `backgroundColor`/`borderColor` 필드 + compat 생성자 |
| `model/design/ScreenSpecification.java` | 수정 | `componentStyles` 필드 + compat 생성자(§3.2) |
| `service/FigmaDesignSpecMapper.java` | 수정 | `components()` 확장, `solidFillColor()`/`solidStrokeColor()`/`firstSolidPaint()` 신규 private 메서드 |
| `service/ScreenSpecAssembler.java` | 수정 | `assemble()`에 `resolvedUi.components()` 매핑 한 줄 |
| `service/ScreenSpecificationPromptFormatter.java` | 수정 | `format()`에 `componentStyles` 텍스트 블록 |
| `src/test/resources/mcp/tool-definitions-baseline.json` | 재생성 | `reviseScreenSpecification` 입력 스키마 변경으로 baseline 삭제 후 `McpToolDefinitionSnapshotTest` 재실행 |

신규 파일은 없다(기존 파일 확장만).

---

## 6. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| `components()`가 **타입 단위**로 그룹핑됨(노드 1개당 1개가 아님) | 같은 타입(예: `ACTION_GROUP`)에 색이 다른 버튼이 여러 개면 **첫 번째 매치 색상만** 대표로 채택 | 1차 구현에서는 이 단순화를 그대로 받아들이고 프롬프트 텍스트에 "대표값" 취지를 명시. 노드별 개별 색상이 꼭 필요하면 `ComponentSpec`을 타입 그룹핑이 아니라 노드별 구조로 바꾸는 훨씬 큰 리팩터링이 필요(2차 이후 검토) |
| Figma `fills`가 `SOLID` 외 `GRADIENT_LINEAR`/`IMAGE`일 수 있음 | 그런 컴포넌트는 색상이 `null`로 빠짐(무시) | 의도된 동작. 향후 필요 시 `uncertainties`에 "그라데이션/이미지 채우기라 색상 생략" 항목 추가 검토 |
| MCP 계약(`reviseScreenSpecification` 입력 스키마) 변경 | `tool-definitions-baseline.json` 불일치로 테스트 실패 | `ThymeleafLayoutTool` 설명 문구 수정 때와 동일한 절차: baseline 삭제 → `McpToolDefinitionSnapshotTest` 재실행으로 재생성 |
| `ScreenSpecification`이 `UiDesignSpec.ComponentSpec` 타입을 직접 재사용 | 두 모델 패키지 간 결합도 약간 증가 | §3.2 설계 판단 참고 — 구현 착수 전 별도 record 신설 여부 확정 필요 |
| auto 경로는 이 변경으로 아무 영향 없음(의도됨) | `CrudModelFactory`가 `componentStyles`를 참조하지 않으므로 생성 파일·CSS는 그대로 | 의도된 범위 제한. auto 반영은 별도 문서/승인 필요(§7) |

---

## 7. 1차 구현 제외 범위 (2차 이후)

- `ColorCssContract` + `CrudColorCssProcessor`(auto 경로 CSS 자동 주입 — `CrudTableDensityCssProcessor`와 동일 패턴)
- `.ftl` 템플릿에 색상 슬롯을 넣는 구조 재설계(`krds-btn`/`egov-list-table` 등 고정 클래스 체계와의 정합성 확보 포함)
- 정부 표준 디자인 시스템(KRDS)과의 색상 일관성 트레이드오프에 대한 별도 의사결정
- `fills`/`strokes`의 `GRADIENT_LINEAR`/`IMAGE` 타입 지원
- `ComponentSpec`을 노드별(타입 그룹핑이 아닌) 구조로 재설계하는 것

---

## 8. 단계별 구현목록

### Phase 1 — 데이터 모델 확장 (필수)

| 순서 | 작업 |
|---|---|
| 1 | `UiDesignSpec.ComponentSpec`에 `backgroundColor`/`borderColor` 필드 + compat 생성자 추가 |
| 2 | `ScreenSpecification`에 `componentStyles` 필드 + compat 생성자 추가(§3.2 설계 판단 먼저 확정) |

### Phase 2 — 파싱 로직 (필수)

| 순서 | 작업 |
|---|---|
| 3 | `FigmaDesignSpecMapper`에 `firstSolidPaint()`/`solidFillColor()`/`solidStrokeColor()` private 메서드 추가 |
| 4 | `components()` 메서드에서 타입별 첫 매치 노드의 색상을 `ComponentSpec`에 반영하도록 수정 |

### Phase 3 — 매핑·소비 (필수)

| 순서 | 작업 |
|---|---|
| 5 | `ScreenSpecAssembler.assemble()`에 `resolvedUi.components()` → `componentStyles` 매핑 한 줄 추가 |
| 6 | `ScreenSpecificationPromptFormatter.format()`에 `componentStyles` 텍스트 블록 추가 |

### Phase 4 — 계약·테스트 (필수)

| 순서 | 작업 |
|---|---|
| 7 | `src/test/resources/mcp/tool-definitions-baseline.json` 삭제 → `McpToolDefinitionSnapshotTest` 재실행으로 재생성 |
| 8 | `FigmaDesignSpecMapper` 관련 기존 테스트에 색상 있는 Figma fixture 케이스 추가(SOLID 1건, GRADIENT 무시 1건, 색상 없는 노드 1건) |
| 9 | `ScreenSpecAssembler` 관련 기존 테스트에 `componentStyles` 매핑 케이스 추가 |
| 10 | `ScreenSpecificationPromptFormatter` 관련 기존 테스트에 `componentStyles` 출력 케이스 추가 |
| 11 | `./gradlew build` 전체 통과 확인(기존 27+5개 호출부가 compat 생성자로 실제 안 깨지는지 컴파일로 최종 확인) |

---

## 9. 검증 방법

1. `./gradlew build` — 전체 테스트 통과 확인
2. 색상이 있는 실제 Figma 프레임으로 `analyzeFigmaReference()` → `createScreenSpecification()` 호출
   → 반환된 명세(또는 `getScreenSpecification()`)에 `componentStyles`가 채워졌는지 확인
3. `buildFullCrudPrompt(..., llmProvider="claude", screenSpecificationId=...)` 호출 → 반환 프롬프트
   텍스트 안에 `componentStyles:` 블록과 실제 rgba 색상값이 포함되는지 확인
4. `auto` 경로(`llmProvider="auto"`)로 같은 화면명세 호출 → 생성 파일·`styles.css`가 이전과 **동일**한지
   확인(auto 경로 무변경 보장)

---

## 10. 관련 문서

- [Figma_fills_strokes_반영_검토.md](./Figma_fills_strokes_반영_검토.md) — 이 구현계획의 기반이 된 검토 원문
- [화면생성Tool_3종_비교분석.md](./화면생성Tool_3종_비교분석.md) — auto/claude 소비 구조 배경
