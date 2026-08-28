# Figma 픽셀 단위 재현 — claude 경로 확장 검토

> 2026-08-28, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> [`Figma_색상_CSS자동주입_auto경로_검토.md`](./Figma_색상_CSS자동주입_auto경로_검토.md)의 후속으로,
> "픽셀 단위로 Figma를 그대로 재현하려면 어떤 경로로 접근해야 하는가"를 검토한 결과다.

---

## 1. 배경 — "픽셀 재현"과 지금 파이프라인의 목적 차이

지금 `FigmaDesignSpecMapper` → `ScreenSpecAssembler` → `ScreenSpecification` 파이프라인은
Figma 노드 트리를 5개 시맨틱 타입(`TABLE`/`SEARCH_PANEL`/`FORM`/`PAGINATION`/`ACTION_GROUP`)으로
뭉치고, 좌표(`absoluteBoundingBox`)는 "2단 폼인가/버튼이 하단인가" 같은 대략적 판단에만 쓰고
버린다. 폰트·모서리radius·그림자·auto-layout(`padding`/`itemSpacing`) 등은 애초에 읽지도 않는다.
이건 "화면이 뭘 해야 하는지(필드/액션/구조)"를 뽑아 항상 같은 KRDS 골격 위에 얹는 **시맨틱
추출기**이지, Figma를 시각적으로 그대로 복제하는 **렌더러**가 아니다.

실무에서 "픽셀 단위로 똑같이 만든다"는 건 보통 개발자가 Figma **Dev Mode**를 열어 레이어를
하나씩 클릭하며 나오는 CSS 값(padding/color/font 등)을 손으로 옮기는 방식이다. **Code Connect**는
이 중 "이 부품이 코드에서 뭐냐(컴포넌트 identity)"만 사전에 매핑해줄 뿐, "이 화면 이 자리에 정확히
어떻게 배치되는가(geometry)"는 여전히 별도 문제로 남는다 — 즉 픽셀 재현에는 (a) 컴포넌트 정체성
매핑과 (b) 인스턴스별 좌표·간격 재현, 두 가지가 모두 필요하다.

---

## 2. auto/claude 라우팅 정정 — "디자인 참조 있으면 claude"가 아니다

`CrudGenerationTool.java`(L55-60)를 보면 지금은 오히려 반대에 가깝다:

> `viewType="thymeleaf"` + `llmProvider="auto"` 조합은 designReferenceId 또는
> screenSpecificationId가 **반드시 있어야** 한다(V2_APPLY 정책, 없으면 즉시 실패).
> `viewType="jsp"`는 이 제약과 무관.

디자인 참조를 넣는 목적은 두 가지로 나뉜다:

| 목적 | 필요한 경로 |
|---|---|
| ① 필드/액션/밀도/배치 추출 | auto로 충분 — 지금 이미 이렇게 동작(대부분의 실사용 조합) |
| ② 시각적으로 Figma에 가깝게(픽셀 근접) | claude 필요 |

"디자인 참조 존재 여부"를 자동 라우팅 기준으로 삼으면 ①번 목적(다수)까지 전부 claude로
끌려가 auto 경로의 장점(결정론적, 빠름, Claude 토큰 절감)을 잃는다. **새 결합 규칙을 만들
필요 없이, 이미 있는 `llmProvider` 파라미터가 이 스위치 역할을 한다** — 픽셀 재현이
필요하면 `llmProvider="claude"`를 명시적으로 선택하면 된다.

---

## 3. `llmProvider="claude"`로 픽셀 재현을 하려면 필요한 추가 작업

지금 `claude` 경로가 Claude에게 넘기는 건 원본 Figma 데이터가 아니라 **이미 여러 단계를 거쳐
축약된 `ScreenSpecification`**이다. 이 축약을 되돌리려면 파이프라인 전 계층에 손을 대야 한다.

### 3.1 원본에 가까운 데이터를 뽑는 새 추출 로직 (`FigmaDesignSpecMapper`)

지금 `map()`은 노드를 5개 타입으로 뭉치고 좌표는 판단에만 쓰고 버린다. 노드별로 아래 값을
보존하는 별도 추출 경로가 필요하다.

| 값 | 지금 상태 |
|---|---|
| `absoluteBoundingBox`(x/y/width/height) | 대략적 판단에만 사용, 값 자체는 버려짐 |
| `cornerRadius`, `strokeWeight`, `opacity`, `effects`(그림자) | 전혀 안 읽음 |
| auto-layout(`layoutMode`, `itemSpacing`, `padding*`) | 전혀 안 읽음 |
| 노드별 텍스트 스타일(`fontFamily`/`fontSize`/`fontWeight`/`lineHeight`) | 루트 노드 1건만(`tokens()` L226-234), 그마저 3.3에서 버려짐 |

### 3.2 이 데이터를 담을 모델 확장

`UiDesignSpec.ComponentSpec`(현재 `type`/`semanticFields`/`backgroundColor`/`borderColor`
4필드)로는 노드별 좌표·스타일을 못 담는다. 새 필드(또는 새 record)가 필요하고,
`ScreenSpecification`에도 이를 실어 나를 필드가 신규로 필요하다 — `componentStyles` 추가 때
확인한 **compat 생성자 패턴**을 그대로 따르면 기존 호출부는 안 깨지지만, 필드 자체는 신규다.

### 3.3 매핑 배선 (`ScreenSpecAssembler.assemble()`)

`tokens()`(루트 배경색/폰트)조차 **파싱은 되는데 매핑이 안 돼** 지금도 버려지고 있다
(L98에서 `componentStyles`만 넘기고 `tokens()`는 여전히 미참조). 새 필드도 여기서 명시적으로
연결해야 하며, 안 하면 똑같은 "파싱 O, 매핑 X" 소실이 반복된다.

### 3.4 프롬프트 포맷 확장 (`ScreenSpecificationPromptFormatter`)

지금은 `"    - ACTION_GROUP backgroundColor=..."` 같은 들여쓰기 텍스트 줄로 직렬화한다.
노드별 좌표/스타일은 트리 구조라 이런 평문보다 **JSON 블록을 프롬프트에 통째로 넣는 방식**이
Claude가 파싱하기 더 안전하다 — 포맷터 구조를 상당 부분 새로 짜야 한다.

### 3.5 데이터 정제(가지치기) 로직 — 신규

노드 트리를 있는 그대로 넘기면 너무 방대하다. 다음이 새로 필요하다:
- 숨겨진/비가시 노드(`visible:false`) 제외
- 표의 반복 행(row) 같은 동일 패턴 인스턴스는 대표 1개만 남기고 축약
- 벡터 패스·플러그인 데이터 등 코드 생성과 무관한 필드 제거

### 3.6 KRDS 이탈 방지 가드레일 — 프롬프트 신규 문구

원본 데이터를 그대로 주면 Claude가 KRDS 클래스 대신 자유롭게 커스텀 스타일을 쓸 위험이 크다.
"기존 `krds-*`/`egov-*` 클래스 구조는 유지하고 이 값들(좌표/색상/폰트)만 그 안에서 반영하라"는
제약 문구를 프롬프트에 명시적으로 새로 넣어야 한다.

### 3.7 `FigmaApiClient`의 `depth` 파라미터 재검토

지금 최대 `depth=10`이 아이콘처럼 깊게 중첩된 노드까지 충분히 내려가는지 확인이 필요하다.
깊이를 늘리면 3.5(정제)에서 걸러야 할 노이즈도 같이 늘어나는 트레이드오프가 있다.

### 3.8 MCP 계약(baseline) 갱신

`ScreenSpecification`에 필드를 추가하면 `reviseScreenSpecification()`의 입력 스키마가 바뀌어
`tool-definitions-baseline.json`을 다시 생성해야 한다(`componentStyles` 작업 때와 동일 절차).

### 3.9 적용 범위 확인

`ScreenSpecificationPromptFormatter`는 `CrudPromptBuilderService`와 `MasterDetailService`
**양쪽에서 공유**되므로 CRUD/MasterDetail은 한 번 확장하면 같이 적용된다. **Board 쪽은 이
포매터를 쓰는 코드가 확인되지 않아, Board의 claude 경로에도 적용하려면 별도 확인·작업이
필요하다**(이번 검토에서 미확인).

---

## 4. 결론

- Java로 결정론적 "노드 1:1 트랜스파일러"를 만드는 것보다는 **`llmProvider="claude"` 경로에
  더 풍부한 데이터를 넘기고 Claude가 해석하게 하는 쪽이 개발 비용 면에서 현실적**이다.
- 다만 "그대로 읽어와서 그대로 쓴다"는 아니다 — 파싱(3.1) → 모델(3.2) → 매핑(3.3) →
  포맷팅(3.4)의 4계층을 전부 손대야 하고, 그 위에 정제(3.5)와 KRDS 가드레일(3.6)이라는
  신규 설계가 추가로 필요하다.
- 이렇게 확장해도 이는 **결정론적 변환이 아니라 LLM의 해석**이라, "보장된 픽셀 일치"가 아닌
  "높은 확률의 근사 재현" 수준에 머문다(이미 있는 이미지 기반 vision 분석 경로와 성격이 비슷).
- 규모는 지난 세션에 완료한 색상 반영(Phase 1~4, claude 경로 텍스트 추가)보다 명확히 크지만,
  auto 경로 CSS 자동주입이나 Java 트랜스파일러보다는 작다.
