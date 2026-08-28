# Figma 색상 → auto 경로 CSS 자동주입 확장 검토

> 2026-08-27, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> [`Figma_fills_strokes_반영_검토.md`](./Figma_fills_strokes_반영_검토.md) §5 "1차 구현 제외 범위"
> 및 [`Figma_fills_strokes_구현계획.md`](./Figma_fills_strokes_구현계획.md)에서 명시적으로 미룬
> "2차" 범위 — `claude` 경로(`ScreenSpecificationPromptFormatter`)에만 색상 텍스트를 반영한
> 구현(Phase 1~4, 커밋 `bbc8079`)과 별개로, `auto` 경로(FreeMarker 렌더링)에서 색상을 CSS로
> 자동 주입하는 것까지 하려면 무엇이 더 필요한지에 대한 후속 검토다.

---

## 1. 배경

`layoutDensity`는 이미 auto 경로에서 "값 → CSS 자동 반영"이 되고 있다
(`CrudTableDensityCssProcessor` → `KrdsStylesConfigurer.ensureTableDensityStyles()` →
`styles.css` patch). 지난 구현(Phase 1~4)에서 추가한 `ScreenSpecification.componentStyles`
(Figma `fills`/`strokes` → `rgba(...)`)도 같은 방식으로 auto 경로 CSS에 반영할 수 있는지,
즉 밀도 패턴을 색상에도 그대로 복제하면 되는지를 검토했다.

---

## 2. 밀도가 동작하는 전체 메커니즘 (그대로 따라 하면 되는 틀)

```
CrudTableDensityCssProcessor (GenerationStageProcessor 구현)
  stage()    = PRE_WRITE
  supports() = "layoutDensity != STANDARD"일 때만 (L42-44)
  process()  → KrdsStylesConfigurer.ensureTableDensityStyles(outputPath)
        │
        ▼
KrdsStylesConfigurer.ensureTableDensityStyles() (L155-188)
  marker(START/END) 사이에 고정 CSS 텍스트를 styles.css에 멱등 patch
  실제 저장은 ApprovedProjectWritePort(ProjectWritePolicy.ATOMIC_APPROVED)로 위임
        │
        ▼
CrudGenerationPlanner.processorSteps() (L358-371)
  new ProcessorStep(CrudTableDensityCssProcessor.ID, PRE_WRITE, order=100, FailurePolicy.STOP)
        │
        ▼
GenerationProcessorRunner.run() — stage 필터 → order 정렬 → 순차 실행, STOP이면 즉시 중단
```

이 등록·실행 틀 자체(프로세서 클래스 하나 + `processorSteps()`에 한 줄 추가)는 색상에도
그대로 복사해서 쓸 수 있다. **여기까지는 밀도 때와 기계적으로 동일하다.**

---

## 3. 문제 1 — "CSS 조립"은 밀도처럼 고정 텍스트 patch로 끝나지 않는다

밀도는 값이 `STANDARD`/`COMPACT`/`COMFORTABLE` 3가지뿐이라, `TableDensityCssContract`에
미리 다 써놓은 **고정 CSS 텍스트**를 그대로 patch하면 끝났다. 색상은 Figma에서
`rgba(255,87,51,1.00)`처럼 **매번 다른 값**이 나오므로:

- 어떤 CSS 셀렉터에 이 색을 적용할지(`.egov-btn`? `.krds-table-wrap`? 컴포넌트 `type`별로
  다른 셀렉터?) 결정하는 로직이 새로 필요하고,
- "고정 텍스트 하나를 patch"가 아니라 "값을 받아 CSS 규칙 문자열을 조립"하는 로직을
  새로 설계해야 한다.

밀도 프로세서를 복사하는 선에서 안 끝나고, **이 부분은 설계를 새로 해야 하는 영역**이다.

---

## 4. 문제 2 — 데이터가 auto 경로에 애초에 연결되어 있지 않다

`CrudTemplateRenderer.toDataModel()`(L216-252)이 FreeMarker에 넘기는 값은
`layoutDensity`/`formColumnLayout`/`actionPlacement`/`searchPanelPlacement`/`designComponents`
뿐이고, **`componentStyles`(Phase 1~4에서 추가한 그 필드)는 여기 전혀 들어가지 않는다.**
전수 확인 결과 `ScreenSpecification.componentStyles`와
`UiDesignSpec.ComponentSpec.backgroundColor`/`borderColor`는 **`claude` 경로
(`ScreenSpecificationPromptFormatter`)에서만 참조되고, CRUD/Board/MasterDetail
generation 패키지 어디에서도 쓰이지 않는다.**

```
claude 경로: ScreenSpecification → componentStyles → 텍스트 프롬프트   ✅ (Phase 1~4 완료)
auto 경로:   ScreenSpecification → CrudModelFactory → CrudTemplateModel → FreeMarker
                                    ↑
                          여기서 componentStyles를 아예 참조하지 않음(연결 자체가 없음)
```

즉 CSS 프로세서를 만들기 **전에**, `CrudTemplateModel`에 `componentStyles`를 담을 필드를
추가하고 `toDataModel()`에서 전달하는 선행 작업이 필요하다. 이건 이전 검토·구현계획
어디에도 없던, 이번에 새로 확인된 작업이다.

---

## 5. 문제 3 — `.ftl` 템플릿에 색상을 끼워 넣을 자리가 없다

- 표 밀도: `crud/thymeleaf-list-body.html.ftl` L68에
  `class="krds-table-wrap egov-density-${layoutDensity?lower_case}"` — **이미 보간 자리가 있음**
- 액션 버튼 영역(같은 파일 L100-104, `crud/jsp-list.jsp.ftl` L57, L144-151):
  `class="krds-btn secondary small egov-btn"` — **클래스가 완전히 하드코딩**,
  변수 보간 자리 자체가 없음

밀도는 이미 뚫려 있는 구멍에 값만 흘려보내면 됐지만, 색상은 그 구멍부터 새로 뚫어야 한다
(`Figma_fills_strokes_반영_검토.md` §4에서 이미 이 벽을 예상했고, 이번 확인으로 재확인됐다).

---

## 6. 문제 4 — Board/MasterDetail에는 이 패턴 자체가 없다

| 도메인 | CSS 자동주입 프로세서 현황 |
|---|---|
| CRUD | `CrudTableDensityCssProcessor` 있음(밀도 전용) |
| Board | `BoardCssProcessor` 있으나 밀도별 분기가 아닌 게시판 고정 CSS 계약 — 성격이 다름 |
| MasterDetail | CSS 자동주입 프로세서 **없음** |

색상을 auto로 확장한다면 **CRUD 도메인부터 시작**하는 것이 기존 구조와 일관되며,
Board/MasterDetail까지 확장하려면 이 패턴 자체를 해당 도메인에 새로 만들어야 한다.

---

## 7. 결론 — 필요한 작업 목록

| # | 작업 | 밀도 패턴 재사용 가능 여부 |
|---|---|---|
| 1 | `CrudTemplateModel`에 `componentStyles` 필드 추가 + `CrudTemplateRenderer.toDataModel()`에서 전달 | 불가 — 현재 연결 자체가 없어 신규 배선 |
| 2 | `.ftl` 액션/버튼 영역에 색상 보간 자리 신설 | 불가 — 밀도의 기존 슬롯 재사용 불가, 템플릿 구조 변경 |
| 3 | 색상값 → CSS 규칙 조립 로직 신규 설계 | 불가 — 밀도는 고정 텍스트 patch였지만 색상은 매번 다른 값 |
| 4 | `ColorCssContract`(marker+CSS) + `KrdsStylesConfigurer.ensureComponentColorStyles()` + `CrudColorCssProcessor`(`GenerationStageProcessor`) + `CrudGenerationPlanner.processorSteps()` 등록 | 가능 — 등록·실행 틀은 그대로 복제 |
| 5 | Board/MasterDetail 확장 여부 결정 | 해당 없음 — 별도 신규 패턴 구축 필요 |

**밀도 패턴을 그대로 복제하면 되는 부분(4번)은 작지만, 그 앞에 필요한 "데이터 연결"
(1번)과 "템플릿 구조 변경"(2·3번)은 새로 설계해야 하는 작업**이라, 밀도 대비 작업량이
확실히 크다. 1차 구현(claude 경로)에서 이 범위를 제외한 판단은 이번 코드 확인으로도
유효한 것으로 보인다.
