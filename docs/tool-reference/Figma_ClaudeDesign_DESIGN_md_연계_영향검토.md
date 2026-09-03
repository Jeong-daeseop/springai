# Figma/Claude Design → DESIGN.md → 코드 생성기 연계 — 영향검토

> 2026-09-03, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> "Figma/Claude Design으로 디자인을 만들면 그 결과가 DESIGN.md로 표현되어 코드 생성기가 이해하게
> 하고 싶다"는 요청에 대한 영향검토다. 후속 재검토(§7)로 결론이 확정됐다 — **DESIGN.md는 이 목적에
> 맞지 않으며, 이미 있는 다른 경로를 쓰는 게 유일하게 구조적으로 맞는 방향이다.**

---

## 1. 배경

요청한 파이프라인:

```
Figma / Claude Design
    ↓ 디자인 생성
DESIGN.md
    ↓ 디자인 결과를 코드 생성기가 이해할 수 있게 표현
코드 생성기
```

이 중 "디자인 분석 결과를 DESIGN.md로 자동 생성/역변환"하는 코드는 존재하지 않는다(grep 0건).

---

## 2. 두 디자인 도구의 현재 지원 수준이 완전히 다르다

### 2-1. Figma — 이미 전용 파이프라인이 있음(입력 B)

```
Figma 프레임 → FigmaApiClient(GET, 429/5xx 최대 5회 자동 재시도)
            → FigmaDesignSpecMapper(노드 트리 결정론적 파싱, LLM 호출 없음)
            → UiDesignSpec → ScreenSpecification
```

`analyzeFigmaReference()`로 이미 완전히 동작한다 — 노드 구조를 그대로 읽는 **결정론적 파싱**이라
LLM 추론에 의존하지 않고, 실패해도 자동 재시도가 있다.

### 2-2. Claude Design — 전용 지원이 전혀 없음

`.dc.html` 캔버스를 직접 읽는 파서가 없다. 유일한 경로는 캔버스의 **PNG export**(툴바 Export)로
뽑은 이미지를 `analyzeDesignReference()`(입력 A, `VisionAnalysisClient`의 LLM 추론)에 넣는
것이다 — 이건 생성형 LLM 호출이라 자동 재시도가 없고 하드 타임아웃으로 실패할 수 있다(코드로 확인된
기존 특성). Figma 경로보다 신뢰도가 낮다.

---

## 3. 기존 유사 경로와의 방향 차이 — 둘 다 DESIGN.md를 거치지 않는다

```
Figma:         analyzeFigmaReference() → FigmaDesignSpecMapper → UiDesignSpec → ScreenSpecification
Claude Design: (PNG export 후) analyzeDesignReference() → VisionAnalysisClient → UiDesignSpec → ScreenSpecification
                                                                          ↓
                                                          claude 경로 프롬프트(componentStyles/tokens)
```

DESIGN.md는 이 흐름과 완전히 별개로, 오직 17.1(JSP→Thymeleaf 마이그레이션)에서만
`DesignMdRuleLoader`가 소비한다(`CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md`에서 이미
확인됨).

---

## 4. 지금 당장 되는 것 (기존 경로 재사용, 신규 코드 불필요)

- **Figma**: `analyzeFigmaReference(figmaUrl, ...)` 그대로 호출하면 된다 — 이미 완전히 동작한다.
- **Claude Design**: 캔버스를 PNG로 export한 뒤 `analyzeDesignReference(referencePath)`에 넘기면
  된다.

두 경우 모두 결과는 DESIGN.md가 아니라 `UiDesignSpec`/`ScreenSpecification`으로 가고, 최종적으로
CRUD **claude** 경로 프롬프트에만 반영된다(auto 경로·17.1은 여전히 미참조).

---

## 5. 애초에 "요청한 그림대로(→ DESIGN.md)" 만들려면 필요했던 것

(참고용 — §7에서 이 방향 자체를 기각했으므로 실제 구현 대상은 아니다.)

- 신규 변환기(`UiDesignSpecToDesignMdConverter`): `UiDesignSpec.tokens`/`components()`를 DESIGN.md
  8개 카테고리 YAML로 직렬화
- `CompanyDesignTokenResolver` 수정: DESIGN.md 값이 "CSS 변수 이름"이 아니라 raw 값(hex/RGBA)일
  수도 있다는 분기 추가 — 현재는 값을 무조건 변수 참조로 취급
- 사람 승인 게이트, CRUD 경로 확장 등 추가 작업

---

## 6. DESIGN.md 자체의 근본적 부적합 사유

- **범위 불일치**: DESIGN.md는 프로젝트 전체에 하나뿐인 정적 정책 파일인데, Figma/Claude Design은
  화면마다 다른 디자인을 만든다 — "화면별 디자인"을 "전역 정책 파일 1개"에 담는 구조 자체가
  맞지 않는다.
- **소비 경로 불일치**: DESIGN.md는 17.1에만 반영되고 **CRUD 생성(auto/claude 둘 다)은 아예 안
  읽는다** — 사용자가 만들려는 화면이 CRUD 화면이면 DESIGN.md에 넣어도 도달하지 않는다.
- **의미 불일치**: DESIGN.md는 "CSS 변수 **이름**"을 기대하는데(`CompanyDesignTokenResolver`),
  디자인 분석은 "raw **값**"을 뽑아낸다(`FigmaDesignSpecMapper`/`VisionAnalysisClient`) — 억지로
  이으면 기존 클래스의 계약을 깨야 한다.
- **이미 더 적합한 경로가 있음**: `analyzeFigmaReference()`/`analyzeDesignReference()` →
  `UiDesignSpec` → `ScreenSpecification` → `ScreenSpecificationPromptFormatter`(claude 경로)가
  정확히 이 목적(특정 디자인을 특정 화면 생성에 반영)을 위해 이미 설계돼 있다. `screenSpecificationId`
  로 화면마다 다른 디자인을 개별 지정할 수 있고, `createScreenSpecification()`의
  `REVIEW_REQUIRED` 승인 게이트도 이미 있다.

---

## 7. 결론 (확정)

**DESIGN.md를 쓰지 않는다.** §6의 네 가지 근거는 대안 중 하나를 고르는 문제가 아니라, DESIGN.md가
구조적으로 이 목적에 맞지 않는다는 확정 근거다.

대신 이미 있는 경로를 그대로 쓴다:

```
Figma          → analyzeFigmaReference()   → UiDesignSpec → ScreenSpecification
Claude Design  → (PNG export) analyzeDesignReference() → UiDesignSpec → ScreenSpecification
                                                              ↓
                                        createScreenSpecification() → REVIEW_REQUIRED(필요 시) → approveScreenSpecification()
                                                              ↓
                                        buildFullCrudPrompt(..., screenSpecificationId=...) → claude 경로 프롬프트 반영
```

이 경로는 이미 존재하고, 화면별 개별 지정과 사람 승인 게이트까지 갖추고 있어 별도 구현 없이
지금 바로 쓸 수 있다. 다만 이 경로도 **CRUD auto 경로와 17.1에는 반영되지 않는다**는 기존 한계는
그대로 남는다 — 그 확장이 필요하면 `CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md`가 아니라
`ScreenSpecificationPromptFormatter`/`CrudModelFactory` 쪽 확장을 별도로 검토해야 한다(DESIGN.md
경로가 아니라 이 경로의 확장이므로 성격이 다르다).

---

## 8. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `tools/DesignReferenceTool.java` | `analyzeFigmaReference()`/`analyzeDesignReference()`/`createScreenSpecification()` 등 진입점 |
| `service/FigmaApiClient.java` | Figma 노드 조회(GET, 자동 재시도) |
| `service/FigmaDesignSpecMapper.java` | Figma 노드 트리 → `UiDesignSpec`(결정론적 파싱) |
| `service/VisionAnalysisClient.java` | Claude Design 등 이미지 입력 → `UiDesignSpec`(LLM 추론, 재시도 없음) |
| `model/design/UiDesignSpec.java` | 디자인 분석 결과 구조 |
| `service/ScreenSpecificationPromptFormatter.java` | claude 경로 프롬프트에 실제로 반영되는 지점 |
| `service/thymeleaf/DesignMdRuleLoader.java` | DESIGN.md 파싱 — 17.1 전용, 이번 목적과 무관함이 확정됨 |
| `service/thymeleaf/CompanyDesignTokenResolver.java` | DESIGN.md 값을 CSS 변수 이름으로 취급(이번 목적에는 해당 없음) |
| `docs/tool-reference/CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md` | DESIGN.md의 CRUD 경로 미반영 확인(별개 주제) |
