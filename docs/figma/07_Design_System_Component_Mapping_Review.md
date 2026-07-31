# 캡처 화면 → 외부 Design System Component 매핑 검토

**문서명**: 07_Design_System_Component_Mapping_Review.md
**버전**: 1.2
**작성일**: 2026-07-22
**상태**: 검토(구현 미착수) — §6 FTC 파일 실사 결과는 재검증 필요(§6.1 참고)
**관련 문서**: `03_Website_To_Figma_Implementation_Specification.md`, `04_Website_To_Figma_Implementation_List.md`

---

## 1. 배경

지금까지 구현된 `jsp-to-figma-plugin`의 import 방식은 캡처된 JSP 화면의 DOM과 스타일을 그대로 읽어 Figma
Frame/Text/Rectangle을 **그 자리에서 새로 생성**하는 방식이다(03번 §10 참고). 이 문서는 이와 별개로 제기된
요구사항, 즉 "캡처한 JSP 화면을 import할 때 별도 Figma 프로젝트(FTC 정부포털 Design System)에 이미 정의된
Component로 매핑해서 디자인하고 싶다"는 방향에 대한 영향 검토다. **코드 변경 없이 검토 결과만 기록한다.**

## 2. 두 가지 import 방식 비교

| | 지금 방식(Release 1) | 요청된 방식 |
|---|---|---|
| 생성 방법 | `figma.createFrame()`/`createText()`로 새로 생성 | 외부 라이브러리의 Instance로 교체(`figma.importComponentByKeyAsync`) |
| 외부 의존성 | 없음 | FTC 정부포털 Design System 파일(publish 필요) |
| 결정론 | 같은 `.figpack`은 항상 같은 결과 | 외부 라이브러리 상태에 따라 결과가 달라질 수 있음 |
| 컴포넌트화 시점 | 생성 후 `componentCandidates` 기준 로컬 Component로 승격(옵션) | 생성 시점에 외부 Component instance로 대체 |

## 3. 기술적으로 필요한 것

1. **FTC 파일이 Team Library로 publish**되어야 한다. 일반 파일 상태로는 다른 파일(Plugin 실행 컨텍스트)에서
   그 컴포넌트를 가져올 방법이 Figma Plugin API에 없다.
2. **컴포넌트 key 매핑 테이블**이 필요하다. "캡처 시 판정한 타입(BUTTON/SELECT/HEADER 등) → FTC 라이브러리의
   실제 컴포넌트 key" 대응표를 수동으로 만들고 유지해야 한다. 자동으로 알아낼 방법이 없고, FTC 쪽 컴포넌트가
   rename되거나 재생성되면 key가 바뀌어 매핑이 깨진다.
3. **속성/variant 매핑**이 필요하다. 캡처된 요소는 그 페이지에서 실제 쓰인 임의의 색상·크기·텍스트인데, FTC
   컴포넌트는 자체 정의된 variant 체계만 지원한다. 캡처값을 variant로 변환하는 로직과, 매칭되지 않는 값(라이브러리에
   없는 커스텀 값)에 대한 처리 정책이 필요하다.

## 4. 영향과 리스크

- **결정론 원칙 위반**: 03번 스펙 §2 핵심 원칙은 "같은 입력은 항상 같은 결과"를 전제로 한다. 외부 라이브러리
  파일에 의존하면, 그 파일이 나중에 수정·삭제되면 같은 `.figpack`을 다시 import해도 결과가 달라지거나 실패한다.
- **새 실패 모드**: 라이브러리 파일 접근 불가, 컴포넌트 key 소실, 팀 멤버십 없음 등 — fallback 정책(못 찾으면
  기존 방식으로 새로 생성?)이 필요하다.
- **UI/워크플로우 재설계**: 지금 `ui.html`은 candidateTypes 체크박스만 있는데, 타겟 라이브러리 선택과 타입별
  매핑 확인 UI가 추가로 필요하다.
- **범위 분류**: 지금까지 작업(Release 1 자체 생성, R6 Release 2A 인증 캡처)보다 큰 별도 Release급 작업이며,
  진행 시 03/04번 문서에 새 Release 섹션과 승인 게이트가 필요하다.

## 5. 대안(리스크가 더 낮은 절충안)

완전 자동 매칭 대신, **① 지금처럼 그대로 생성하고, ② 매칭 후보로 보이는 노드에는 pluginData/네이밍으로만
라벨을 남겨서, 사람이 Figma의 "Swap Instance" 기능으로 직접 바꿔치기**하는 반자동 방식을 고려할 수 있다.
결정론이 깨지지 않고, 매핑 오류 위험도 없고, 구현 부담도 훨씬 작다.

## 6. FTC 정부포털 Design System 파일 실사 결과 (2026-07-22)

Figma MCP(`get_libraries`, `search_design_system`, `get_metadata`)로 대상 파일(`mVy5h1UbORVqQoBm8Wr1bT`)을
직접 확인했다.

- `get_libraries`: 이 파일이 **구독 중인** 라이브러리는 Material 3 Design Kit, Simple Design System,
  iOS/iPadOS/macOS/watchOS/visionOS 커뮤니티 UI kit뿐이었다. **이 파일 자신이 발행한 라이브러리는 없다.**
- `search_design_system`으로 "button", "버튼"을 검색했으나 **결과 0건**.
- `get_metadata`로 구조를 확인한 결과, **최상위 페이지가 "Cover" 하나뿐**이며, 그 캔버스도 `width=0, height=0`에
  자식 노드가 없는 **완전히 빈 상태**였다.

**결론**: FTC 정부포털 Design System 파일은 현재 라이브러리로 publish되어 있지 않으며, 그 이전에 **실질적인
컴포넌트가 하나도 없는 빈 파일**이다. 매핑을 시작하려면 먼저 그 파일에 실제 컴포넌트를 구축하고 Team Library로
publish하는 선행 작업이 필요하다. 사용자 플랜은 Team/Pro이며, Team Library publish 자체는 이 플랜에서 가능해
보인다(Code Connect만 Organization/Enterprise 플랜이 필요했다).

### 6.1 실사 결과 재확인 필요 (2026-07-22 추가)

사용자가 Figma Desktop에서 실제로 열려 있는 파일의 좌측 Pages 목록 스크린샷을 제공했는데, 여기에는
**Button, Input, Tag, GNB, Breadcrumb, Card, Board List, Carousel, Pagination, Modal, Search Filter Panel,
Footer, Table, Form Field Group** 등 14개 페이지가 실제로 존재했다. 이는 §6에서 "Cover 페이지 하나뿐인 빈
파일"이라고 확인한 것과 **정면으로 모순된다.**

원인으로 추정되는 것: §6에서 조회한 fileKey(`mVy5h1UbORVqQoBm8Wr1bT`)는 `/proto/`(프로토타입 공유 링크)에서
추출한 값인데, 이게 실제 Figma Desktop에서 편집 중인 파일과 다른 파일을 가리키고 있을 가능성이 높다. **§6의
"빈 파일" 결론은 잘못된 fileKey로 조회했을 가능성이 있어 재검증이 필요하며, 현재 신뢰할 수 없는 상태다.**
Figma Desktop 주소창의 정확한 URL(`https://www.figma.com/design/:fileKey/...` 형식)을 다시 받아 올바른
fileKey로 §6을 재조사해야 한다.

## 7. Import된 화면에서 Component를 만들 수 있는가 (2026-07-22 추가)

FTC 파일 매핑과는 별개로, "우리 Plugin으로 여러 화면을 import한 뒤, 그 import 결과들을 가지고 재사용 가능한
Component를 만들 수 있는가"를 검토했다.

### 7.1 지금도 되는 것

- Plugin은 import 시점에 `componentCandidates`(§`04_Website_To_Figma_Implementation_List.md`의 16종) 중
  사용자가 UI에서 체크한 타입을, confidence 0.8 이상 조건으로 `figma.createComponentFromNode()`를 호출해
  **자동으로 Figma Component 승격**한다(이미 구현·검증됨).
- 이와 별개로 **Figma 자체 기능**으로, import 직후든 나중이든 임의의 Frame을 선택해 우클릭 → "Create
  Component"(또는 Shift+Option/Alt+K)로 변환할 수 있다. 우리 Plugin이 만든 결과물이라는 사실과 무관하게 항상
  가능한 Figma 기본 기능이다.

### 7.2 안 되는 것 — 여러 import 간 자동 통합

- extractor/Plugin은 **매 import마다 새 Frame 트리를 처음부터 다시 생성**하며, 이전 import와 중복 제거·연결을
  하지 않는다. 즉 화면 4개를 각각 import하면 GNB/Footer/버튼 등 공통 요소가 **매번 독립된 별도 Frame**으로
  4벌 생긴다.
- Figma는 **서로 다른 페이지/다른 import에 흩어진 노드를 한 번에 선택해 하나의 Component로 자동 통합하는
  기능이 없다.** "Create Component"는 항상 동일 페이지 내 현재 선택 범위에만 적용된다.
- 여러 import에서 나온 동일 패턴을 하나의 재사용 가능한 라이브러리로 정리하려면, 사람이 (a) 가장 좋은 버전
  하나를 골라 Component로 승격하고 (b) 나머지 import의 중복 Frame을 지운 뒤 그 master Component의 Instance로
  교체하는 **수작업**이 필요하다. 이 "여러 import 간 중복 감지·자동 통합" 기능은 지금 코드에 없으며, 만들려면
  §2~4에서 검토한 외부 라이브러리 매핑과 사실상 같은 성격의 작업(대상이 로컬로 만든 Component냐 외부 FTC
  Component냐 차이)이라 별도 개발이 필요하다.

## 8. 다음 단계 결정 필요 사항

1. §6.1 — Figma Desktop에서 정확한 파일 URL을 다시 확인해 FTC 파일 실사(§6)를 재검증
2. FTC 파일에 컴포넌트를 먼저 채워 넣을지, 다른 기존 라이브러리(예: Simple Design System)를 대신 쓸지
3. 완전 자동 매칭(§3~4의 리스크 감수)과 반자동 라벨링(§5 대안) 중 어느 쪽으로 갈지
4. §7.2의 "여러 import 간 중복 통합" 기능을 만들지, 계속 수작업으로 둘지
5. 진행하기로 하면 03/04번 문서에 별도 Release로 정식 편입할지

### 8.1 이후 문서에서의 진행 상황 (2026-07-27 기준)

이 문서(2026-07-22) 이후 08~12번 문서가 위 결정 사항 중 일부를 구체화했다.

1. **§6.1 fileKey 재검증** — 아직 해결되지 않았다. 12번 문서의
   `DEC-01`(사용할 FTC/KRDS Figma Library의 정확한 대상)로 그대로 이월되어
   `[!]`(선행 결정 미확정) 상태로 남아 있다. 착수 전 정확한 fileKey 확인이 여전히
   선행 조건이다.
2. **FTC 파일을 채울지 대체 라이브러리를 쓸지** — 09~11번 문서가 "사람이 수작업으로
   FTC 파일에 채워 넣는" 방향 대신, **Design System Author Plugin(R3)이
   `DesignSystemSpec`으로부터 KRDS/eGovFrame 컴포넌트를 프로그래밍적으로 생성·갱신**하는
   방향으로 구체화했다. 다만 이 Plugin이 채우는 대상 파일이 §6.1에서 문제된 그 fileKey와
   같은 파일인지는 `DEC-01` 승인 시 함께 확정해야 한다.
3. **완전 자동 매칭 vs 반자동 라벨링(§5)** — 09~12번은 **완전 자동 매칭** 경로를
   채택했다. 다만 §5가 우려한 "외부 라이브러리 상태 변화에 따른 결과 불안정성"은
   그대로 두지 않고, 사람의 Preview 검토 후 Publish(R3), Publish 후 Registry
   동기화(R4), Registry 드리프트 사전 점검(R4-024)이라는 승인·검증 게이트로
   완화했다. §5의 "Swap Instance 반자동" 대안 자체는 채택되지 않았다.
4. **컴포넌트 key 매핑 테이블(§3-2)** — `ComponentRegistry`/`ComponentRegistryEntry`
   모델과 Publish Registry Sync(R4)로 공식화됐다. 수동으로 만들고 유지해야 한다는
   우려는 유지되지만, Publish 결과에서 반자동으로 후보 diff를 생성(R4-004)하고
   사람이 확인 후 반영(R4-005)하는 절차로 구체화됐다.
5. **여러 import 간 중복 통합(§7.2)** — 08~12번 어디에도 이 기능에 대한 결정이나
   작업 항목이 없다. 여전히 미결 상태다.

---

## 9. 변경 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| 1.2 | 2026-07-27 | §8.1 신설: 08~12번 문서와 대조해 §8의 미결 사항 중 무엇이 해결됐고(Author Plugin 방향, 완전 자동 매칭 채택, ComponentRegistry 공식화) 무엇이 여전히 미결인지(§6.1 fileKey 재검증=DEC-01, §7.2 중복 통합 기능) 정리 |
| 1.1 | 2026-07-22 | §6.1 신설: 사용자가 제공한 Figma Desktop 스크린샷(Button/Input/.../Form Field Group 등 14개 페이지 존재)이 §6의 "빈 파일" 결론과 모순됨을 확인 — 잘못된 fileKey 조회 가능성으로 재검증 필요 상태로 표시. §7 신설: import 결과로 Component를 만드는 것 자체는 이미 가능(Plugin 자동 승격 + Figma 기본 "Create Component")하지만, 여러 번의 import 사이에서 중복을 자동으로 통합하는 기능은 없고 현재는 수작업이 필요함을 정리 |
| 1.0 | 2026-07-22 | 최초 작성 — 외부 Design System 매핑 방향 영향 검토, FTC 파일 실사 결과(라이브러리 미발행, 빈 파일) 기록 |
