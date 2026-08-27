# Figma `fills`/`strokes`(색상) 반영 확장 검토

> 2026-08-27, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> 배경은 [`화면생성Tool_3종_비교분석.md`](./화면생성Tool_3종_비교분석.md) 및 아키텍처 다이어그램
> 아티팩트 5번 섹션(DesignReferenceTool)에서 확인한 "색상·토큰이 두 단계에 걸쳐 사라진다"는
> 발견의 후속 검토다.

---

## 1. 배경

현재 `FigmaDesignSpecMapper`는 Figma 노드 JSON에서 컴포넌트별 `fills`(채우기색)/`strokes`(테두리색)를
전혀 읽지 않는다(`FigmaDesignSpecMapper.java` 전체 313줄에 `fills`/`strokes` 언급 0건). 이걸 실제로
읽어서 화면 생성 결과에 반영하도록 확장하면 뭐가 필요한지 검토를 요청받았다.

---

## 2. 좋은 소식 — API를 더 호출할 필요는 없어 보인다

`FigmaApiClient.fetchNodeOnce()`(`FigmaApiClient.java` L159-161)가 이미 `GET /v1/files/{fileKey}/nodes
?ids=...&depth=N`을 호출하고 있다. `depth`(현재 최대 10, L297)는 "노드 트리를 몇 단계까지 내려가며
자식을 포함할지"를 결정할 뿐, 개별 노드 안에 `fills`/`strokes` 필드가 있는지 여부와는 무관하다
(일반적인 Figma REST API 지식이며, 코드에서 depth와 fills 유무의 상관관계를 검증하는 테스트나
주석은 없어 이 부분 자체는 **미확인**). 즉 API 응답 안에는 이미 색상 정보가 와 있을 가능성이 높고,
문제는 "안 받아온다"가 아니라 **"받았는데 파싱 코드가 안 읽는다"**다.

---

## 3. 필요한 변경 범위 — 생각보다 파급력이 크다

| 변경 대상 | 현재 상태 | 필요한 작업 |
|---|---|---|
| `FigmaDesignSpecMapper` | 루트 노드 배경색 1건만 추출(`tokens()` L193-201) | 노드 트리 **재귀 순회**하며 각 컴포넌트의 `fills`/`strokes` 읽는 로직 신설 |
| `UiDesignSpec.ComponentSpec` | `record ComponentSpec(String type, List<String> semanticFields)`(L44) — 2필드뿐 | 색상 필드 추가(모델 변경) |
| `ScreenSpecAssembler.assemble()`(L91-97) | `resolvedUi.tokens()`/`.components()` 아예 참조 안 함 | 매핑 라인 추가 |
| `ScreenSpecification`(16개 필드, `model/design/ScreenSpecification.java` L107) | color/token 필드 없음 | 필드 추가 — 생성자 호출부(`new ScreenSpecification(...)`)가 **production 5개 파일 + test 27개 파일**에 있어 전부 컴파일 영향 |
| MCP 계약 | `DesignReferenceTool.reviseScreenSpecification(ScreenSpecification)`이 이 타입을 **입력 파라미터**로 받아 `tool-definitions-baseline.json`에 전체 필드 스키마가 이미 펼쳐져 있음 | 필드 추가 시 **MCP 계약 baseline 갱신 필요** |

> `getScreenSpecification()`은 `ScreenSpecification`을 단순 반환값으로만 쓰므로 계약에 안 잡히지만,
> `reviseScreenSpecification()`은 입력 스키마라 baseline이 반드시 깨진다는 점이 확인됐다.

---

## 4. 실제로 "반영"까지 가려면 — 여기가 진짜 큰 벽

밀도(`layoutDensity`)는 `CrudTableDensityCssProcessor`가 파이프라인 특정 단계(`PRE_WRITE`,
`GenerationProcessorRunner`가 `List<GenerationStageProcessor>`를 Blueprint의 `ProcessorStep` 선언
순서대로 실행)에 등록되어 `KrdsStylesConfigurer.ensureTableDensityStyles()` → `styles.css`에
`TableDensityCssContract.CSS`(marker `egov-table-density:start/end`)를 멱등 patch하는 구조다. 색상도
같은 패턴을 쓰려면 `ColorCssContract`(marker+CSS) + `CrudColorCssProcessor`(`GenerationStageProcessor`
구현체, `@Component`) + Blueprint에 `ProcessorStep` 등록이 새로 필요하다.

**더 근본적인 문제**: `.ftl` 템플릿(`crud/thymeleaf-list-body.html.ftl` 등, 1-152줄 전체 확인)을
보면 `krds-btn primary`, `krds-input`, `egov-list-table`, `egov-density-${layoutDensity}` 같은
**고정 클래스만** 쓰고 있고, 인라인 style이나 색상을 끼워 넣을 자리가 **구조적으로 없다.** 밀도는
클래스 이름 뒤에 값 하나(`${layoutDensity}`)만 붙이면 되는 단순한 케이스였지만, 색상은 그런 슬롯
자체가 준비돼 있지 않아 템플릿을 다시 설계해야 한다.

---

## 5. 설계적 긴장 — 이게 우연이 아닐 수 있다

eGovFrame/KRDS는 **정부 표준 디자인 시스템**이다. 지금 구조는 어떤 Figma 목업을 넣든 결과 화면이
항상 같은 KRDS 룩앤필로 나오도록 고정 클래스만 쓰게 만들어져 있다. 여기에 임의 Figma 색상을
주입하면, 프로젝트마다 색이 제각각인 화면이 나와서 **정부 표준 UI 일관성이 깨질 수 있다.** 템플릿에
색상 슬롯이 없는 게 "안 만들어서"가 아니라 "일부러 안 만든 것"일 가능성이 있다(코드 주석으로 확인된
근거는 아니라 추정이다).

추가로 Figma의 `fills`는 단색(`SOLID`)만 있는 게 아니라 그라데이션(`GRADIENT_LINEAR`)·이미지
(`IMAGE`)일 수도 있어서, "대표색 하나만 뽑는다"는 단순화 자체가 실제 디자인과 다르게 보일 위험도
있다(코드로 확인할 수 없는 일반적 리스크).

---

## 6. 결론

기술적으로 불가능하진 않지만 다음 이유로 **중~대규모 작업**이다:

1. 모델 변경(`ScreenSpecification`)이 test 27개 + main 5개 파일에 파급
2. MCP 계약(baseline) 갱신 필요
3. auto 경로는 CSS 자동주입 신규 개발 + 그보다 먼저 **`.ftl` 템플릿 구조 자체를 색상 슬롯이 있게
   다시 설계**해야 함(밀도보다 몇 단계 위 작업)
4. 정부 표준 디자인 시스템과의 일관성 트레이드오프를 먼저 결정해야 함

우선순위를 낮추고 싶다면, **claude 경로(`ScreenSpecificationPromptFormatter`)에만 색상 정보를
텍스트로 추가**하는 게 훨씬 저비용 대안이다 — 이미 텍스트 프롬프트 방식이라 CSS/템플릿 재설계
없이 "이 버튼은 #FF5733입니다" 정도만 알려주고 Claude가 알아서 반영하게 할 수 있다.

---

## 7. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `service/FigmaApiClient.java` | Figma REST API 호출(`GET /v1/files/{fileKey}/nodes`), `depth` 파라미터 |
| `service/FigmaDesignSpecMapper.java` | Figma 노드 JSON → `UiDesignSpec` 파싱(현재 `fills`/`strokes` 미읽음) |
| `model/design/UiDesignSpec.java` | `ComponentSpec(type, semanticFields)` — 색상 필드 없음 |
| `service/ScreenSpecAssembler.java` | `UiDesignSpec` → `ScreenSpecification` 매핑(`tokens`/`components` 미매핑) |
| `model/design/ScreenSpecification.java` | 최종 화면명세 — color/token 필드 없음 |
| `tools/DesignReferenceTool.java` | `reviseScreenSpecification()` — `ScreenSpecification`을 입력으로 받는 MCP 계약 지점 |
| `service/generation/crud/CrudTableDensityCssProcessor.java` | 밀도→CSS 자동 주입 패턴의 실제 예시 |
| `templates/crud/thymeleaf-list-body.html.ftl` 등 | 고정 KRDS 클래스만 사용, 색상 슬롯 없음 |
