# FTC 정부 포털 Design System — Figma 구축 개요

> **작성일:** 2026-07-20
> **성격:** 실제로 완성된 Figma 디자인 시스템의 결과 요약 문서(사후 문서화). 이 문서 자체는 설계/구현 계획 문서가 아니라, `docs/crud/design-vision-figma-mcp-adoption-impact-analysis.md`·`design-vision-figma-mcp-implementation-spec.md` 논의 이후 **Claude Design → Figma 핸드오프**를 실제로 실행한 결과를 정리한다.
> **Figma 파일:** https://www.figma.com/design/mVy5h1UbORVqQoBm8Wr1bT ("FTC 정부 포털 Design System")

---

## 1. 배경

Claude Design(claude.ai/design)에서 만든 "FTC 정부 포털" 목업을 "ready to build" 핸드오프로 받은 HTML(`~/Downloads/FTC 정부 포털 (Standalone).html`)을 소스로, 이 세션에 연결된 Figma MCP(`use_figma`, `figma-generate-library`, `figma-generate-design` 스킬)를 통해 **디자인 토큰 → Foundations 문서 → 컴포넌트** 순으로 Figma 디자인 시스템을 처음부터 구축했다. Claude Design에는 Figma로의 네이티브 내보내기가 없어(공식적으로 서드파티 플러그인 경유만 지원), 코드 핸드오프 → Claude Code + Figma MCP 경로를 대안으로 사용했다.

---

## 2. 디자인 토큰 (Foundations)

원본 HTML의 인라인 스타일(색상 hex, radius, shadow, 폰트)을 정규화해 Figma 변수·스타일로 구축했다.

### 2.1 색상 — Primitives(17개, 1 mode: Value)

| 그룹 | 변수 | 값 |
|---|---|---|
| Brand | `navy/900` | `#083891` |
| Brand | `blue/500` | `#256ef4` |
| Brand | `blue/50` | `#eef4fc` |
| Accent | `orange/500` | `#e8512a` |
| Status | `warning/100` | `#fff3b0` |
| Neutral | `gray/900`~`gray/25`(9단계) | `#1e2124` → `#f9fbff` |
| Neutral | `white/1000` | `#ffffff` |

Primitives는 `scopes: []`로 모든 피커에서 숨김 처리(원칙: 디자이너는 Semantic만 사용).

### 2.2 색상 — Semantic(15개, `Color` 컬렉션, 1 mode: Default)

| 변수 | 별칭 대상 | 용도(scope) |
|---|---|---|
| `color/bg/default` | white/1000 | FRAME_FILL, SHAPE_FILL |
| `color/bg/subtle` | gray/50 | FRAME_FILL, SHAPE_FILL |
| `color/bg/brand-tint` | blue/50 | FRAME_FILL, SHAPE_FILL |
| `color/bg/brand` | navy/900 | FRAME_FILL, SHAPE_FILL |
| `color/text/default` | gray/900 | TEXT_FILL |
| `color/text/secondary` | gray/800 | TEXT_FILL |
| `color/text/muted` | gray/500 | TEXT_FILL |
| `color/text/brand` | navy/900 | TEXT_FILL |
| `color/text/link` | blue/500 | TEXT_FILL |
| `color/text/on-brand` | white/1000 | TEXT_FILL |
| `color/border/default` | gray/200 | STROKE_COLOR |
| `color/border/strong` | gray/300 | STROKE_COLOR |
| `color/border/focus` | navy/900 | STROKE_COLOR |
| `color/accent/alert` | orange/500 | SHAPE_FILL, TEXT_FILL |
| `color/status/warning-bg` | warning/100 | FRAME_FILL, SHAPE_FILL |

> 원본 핸드오프에 다크모드 데이터가 없어 `Color` 컬렉션은 **단일 모드(Default)**로만 구성했다 — 존재하지 않는 다크모드 값을 임의로 지어내지 않았다.

### 2.3 Spacing(6개, `Spacing` 컬렉션)

`spacing/2xs=4` · `xs=8` · `sm=12` · `md=16` · `lg=24` · `xl=40` (scope: `GAP`)

### 2.4 Radius(8개, `Radius` 컬렉션)

`radius/xs=3` · `sm=4` · `md=6` · `lg=8` · `xl=12` · `2xl=14` · `pill=50` · `full=9999` (scope: `CORNER_RADIUS`)

### 2.5 Effect 스타일(2개)

| 스타일 | 원본 CSS | 용도 |
|---|---|---|
| `Shadow/Overlay` | `0 2px 10px rgba(0,0,0,.35)` | 모달/오버레이 |
| `Shadow/Brand-Card` | `0 4px 24px rgba(8,56,145,.18)` | 브랜드 틴트 카드 강조 |

### 2.6 서체

**`Pretendard GOV`**(Regular/Medium/SemiBold/Bold) — 대한민국 공공 서비스 환경을 위한 [Pretendard](https://github.com/orioncactus/pretendard) 공식 GOV 패키지. `~/Library/Fonts/PretendardGOV-*.otf`에 정적(static) 폰트로 설치돼 있다.

---

## 3. 컴포넌트 목록 — 총 17개 ComponentSet, 46개 variant

| # | 컴포넌트 | Variant 축 | Variant 수 | TEXT 프로퍼티 | 조합 예시(Sample) |
|---|---|---|---|---|---|
| 1 | **Button** | Size(Small/Medium) × Style(Primary/Secondary/Ghost) × State(Default/Disabled) | 12 | Label | — |
| 2 | **Input** | State(Default/Focus/Disabled) | 3 | Value | — |
| 3 | **Tag** | Style(Default/Alert) | 2 | Label | — |
| 4 | **Nav Item**(GNB) | State(Default/Active) | 2 | Label | GNB Bar(실제 메뉴 7개) |
| 5 | **Breadcrumb Item** | Type(Link/Current/Separator) | 3 | Label | Trail("홈 > 소식·뉴스 > 보도자료") |
| 6 | **Card** | Style(Default/Featured) | 2 | Title, Meta | — |
| 7 | **Board List Row** | Style(Default/Notice) | 2 | Title, Author, Date, No, Views | 전체 목록(헤더+공지 2행+일반 5행) |
| 8 | **Carousel Dot** | State(Active/Inactive) | 2 | — | 5-dot 인디케이터 |
| 9 | **Pagination Item** | State(Default/Current) | 2 | Label | Pagination Bar("‹ 1 2 3 4 5 ›") |
| 10 | **Modal** | Type(Info/Confirm) | 2 | Title, Body | 반투명 백드롭 위의 Confirm 모달 |
| 11 | **Search Filter Panel** | Type(Simple/Advanced) | 2 | — | — |
| 12 | **Detail Page Layout** | Type(Default/Notice) | 2 | Title, Body, Author, Date, Views | — |
| 13 | **Footer** | Style(Default/Compact) | 2 | — | — |
| 14 | **Table Row** | Style(Header/Body) | 2 | Col1, Col2, Col3, Col4 | 4행 데이터 그리드("연도별 예산 집행 현황") |
| 15 | **Radio** | State(Unselected/Selected) | 2 | Label | — |
| 16 | **Checkbox** | State(Unchecked/Checked) | 2 | Label | — |
| 17 | **Form Field** | State(Default/Error) | 2 | Label, Helper | — |

- Button은 Primary=navy 배경/흰 텍스트, Secondary=흰 배경/navy 아웃라인, Ghost=배경 없는 텍스트형으로 설계.
- Board List Row의 Notice variant는 번호 칸을 **Tag 컴포넌트(Style=Alert) 인스턴스**로 대체해 컴포넌트 간 재사용을 보여준다.
- Pagination Item은 이전/다음 화살표(‹›)도 별도 컴포넌트 없이 동일 컴포넌트의 Label 프로퍼티만 바꿔 재사용한다(Breadcrumb Separator와 같은 패턴).
- Modal의 하단 액션 버튼은 **Button 컴포넌트 인스턴스를 그대로 중첩**해서 구성한다(Info=Primary 1개, Confirm=Secondary+Primary 2개) — `Shadow/Overlay` 이펙트 스타일 적용.
- Search Filter Panel은 **Input(Default) + Button(Primary/Secondary) 인스턴스를 조합**한 패널이다. Simple=검색창+검색 버튼, Advanced=검색 행 + 기간(시작일~종료일 Input 2개) + 필터 적용 버튼 행 추가.
- Detail Page Layout은 제목+메타(작성자·작성일·조회수)+구분선+본문+첨부파일 목록(파일명+**Button(Ghost, Small) 다운로드 인스턴스**)+**Button(Secondary) 목록 인스턴스**로 구성. Notice variant는 제목 앞에 **Tag(Alert) 인스턴스**를 재사용.
- Footer의 바로가기 링크 행은 **Breadcrumb Item(Type=Link) 인스턴스**를 재사용해 구성한다.
- Table Row는 Board List Row와 별개로 **범용 4열 데이터 그리드**용이다(예: 예산 집행 현황, 통계표 등 게시판 목록이 아닌 표 형식 데이터).
- Form Field는 **Input(Default) 인스턴스**를 Label/Helper 텍스트와 함께 조합한다. Radio/Checkbox는 원본 민원신청 페이지(`civil-type`)의 선택 컨트롤을 기반으로 설계.
- 모든 시각 속성(배경/텍스트/보더 색상, padding, radius)은 §2의 변수에 바인딩되어 있으며, 하드코딩된 hex/px 값이 없다(전수 검증 완료, §5 참고).

---

## 4. 페이지 구조

```
Cover → Foundations → --- → Button → Input → Tag → GNB → Breadcrumb → Card → Board List → Carousel → Pagination → Modal → Search Filter Panel → Detail Page Layout
```

각 컴포넌트 페이지는 좌측에 `{Component} / Documentation` 프레임(제목+설명), 우측 또는 하단에 ComponentSet 그리드, 필요 시 추가로 실사용 예시(Sample) 조합을 배치하는 동일한 레이아웃 컨벤션을 따른다.

---

## 5. 품질 검증

- **변수 바인딩 전수 감사**: 12개 ComponentSet(fills/strokes, 총 161건)을 스크립트로 순회해 각 paint의 `boundVariables.color`가 실제 변수 현재값과 raw fallback 색상이 일치하는지 검증 — **최종 0건 불일치**.
- **5개 추가 컴포넌트 재검증(2026-07-21)**: §3의 Footer/Table Row/Radio/Checkbox/Form Field(총 105건 fills/strokes)를 Figma MCP로 다시 조회해 동일 방식으로 감사했다.
  - 이미 바인딩된 paint의 resolve 값 불일치: **0건** (기존 12개와 동일한 결과).
  - unbound(미바인딩) 2종 발견: ① 각 ComponentSet 루트 프레임 자체의 배경 fill(raw `#f5f5fa` 계열)이 unbound — Button(`8:29`) 등 기존 12개에도 동일하게 존재하는, Figma가 variant 그룹 생성 시 자동 부여하는 캔버스 배경으로 실제 인스턴스에는 노출되지 않아 결함이 아님. ② Footer(Style=Default variant, node `80:94` `links-row`)의 배경이 raw white(`#ffffff`)로 하드코딩되어 `color/bg/default`에 바인딩돼 있지 않음 — §3의 "하드코딩된 hex/px 값이 없다"는 서술과 배치되는 실제 결함.
  - **수정 완료**: `80:94` `links-row`의 fill을 `color/bg/default`(`VariableID:1:21`)에 `setBoundVariableForPaint`로 바인딩. 재감사 결과 Footer 페이지의 남은 unbound 항목은 ComponentSet 루트 배경 1건(위 ①, 결함 아님)뿐이며, `get_screenshot`으로 시각적 회귀 없음을 확인했다.
- **시각 검증**: 매 컴포넌트 완성 직후 `get_screenshot`으로 렌더링 확인(그리드 배치, 색상, 텍스트 가독성, state 구분).
- **폰트 검증**: 전체 페이지 텍스트 노드(61개+) 전수 확인 — 100% `Pretendard GOV` 적용, 임시 폰트 잔여 없음.

### 발견·수정한 버그(재발 방지용 기록)

| 버그 | 원인 | 교훈 |
|---|---|---|
| Foundations wrapper 세로 고정(100px) | `resize()` 이후 sizing mode를 AUTO로 재설정하지 않음 | 최상위 auto-layout 프레임은 `resize()` 후 `primaryAxisSizingMode='AUTO'` 명시 필요 |
| 스크립트 전체 실패(**Detail Page Layout 빌드 중 재발**) | `layoutSizingHorizontal='FILL'`/`'HUG'`를 `appendChild` **이전**에 호출 — 헬퍼 함수 안에 sizing 설정을 넣으면 특히 재발하기 쉬움 | FILL/HUG는 반드시 부모에 append한 **이후**에 설정. 텍스트 생성 헬퍼는 sizing을 건드리지 말고 노드만 반환하게 만들 것 |
| Button 첫 variant 색상 깨짐(raw fallback stale) | `setBoundVariableForPaint` 결과가 간헐적으로 resolve 안 됨 | 전수 감사 스크립트로 사후 검증 필수, 발견 시 재바인딩으로 즉시 수정 |
| Board List Notice variant 노드 소실 | 스크립트에 실수로 `node.remove()` 삽입 | atomic 실행이라도 로직 버그는 남을 수 있음 — 생성 직후 존재 여부 재확인 |
| `setProperties()` 오류 | 프로퍼티 표시 이름("Date")과 실제 키("Date#59:6") 혼동 | 항상 `componentPropertyDefinitions`에서 실제 키를 조회해 사용 |
| 인스턴스 variant 필터링 실패 | `instance.name`이 아니라 `instance.mainComponent.name`으로 variant 식별해야 함 | 인스턴스 `.name`은 기본적으로 ComponentSet 이름과 같음 |

### 폰트 관련 운영 노트(중요)

이 Figma MCP 세션의 `listAvailableFontsAsync()`는 로컬에 새로 설치된 폰트를 반영하지 않는 구조적 제약이 있었다(앱 재시작·재설치 시도 7회 모두 동일 결과). 다만 **사용자가 Figma 앱 UI에서 텍스트에 한 번 직접 적용**하면, 그 이후로는 같은 세션의 `loadFontAsync()`가 해당 family/style을 정상적으로 로드한다. 향후 새 폰트를 도입할 때도 이 절차(UI에서 수동 1회 적용 → 이후 스크립트로 일괄 적용)를 따라야 한다.

---

## 6. 향후 확장 후보 (미착수)

이번 세션에서 다루었던 후보(Modal/Dialog, Pagination, 검색/필터 패널, Detail 페이지 레이아웃, Footer, Table(데이터 그리드), Form 필드 그룹/Radio/Checkbox)는 모두 완성되어 §3으로 이동했다. 현재 시점 기준 식별된 미착수 확장 후보는 없다.

---

이 문서는 완성된 결과물의 요약이며, §6에 남은 미착수 확장 후보는 현재 없다.
