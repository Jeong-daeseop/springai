# Figma Variable/Style 이름·의미 반영 검토

> 2026-09-02, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> [`Figma_fills_strokes_구현계획.md`](./Figma_fills_strokes_구현계획.md)로 이미 반영된 "RGBA 원시값"
> 위에, Figma의 Variable/Style **이름·의미**까지 추가로 반영할지에 대한 후속 검토다.

---

## 1. 배경

`FigmaDesignSpecMapper`(468줄)는 Figma 노드 JSON에서 `backgroundColor`/`fills`/`strokes`/
`style.fontFamily`/`fontSize`를 읽어 `UiDesignSpec.tokens`/`ComponentSpec`에 담는다. 이때 읽는 값은
전부 **최종 해석된 RGBA·숫자값**이며, 그 옆에 나란히 존재하는 "이 색이 무슨 이름의 토큰에서 왔는지"
알려주는 참조 메타데이터(`boundVariables`, `fillStyleId`, `textStyleId`)는 **코드 어디에도 등장하지
않는다**(`grep -ni "variable" FigmaDesignSpecMapper.java` = 0건, 파일 전체 468줄 기준 확인됨).

바이브코딩(Claude가 프롬프트를 읽고 직접 코드를 작성하는 워크플로우) 관점에서, 이름·의미가 있으면
생성 코드의 가독성·유지보수성·일관성이 올라간다는 실질적 이득이 있어, 이걸 반영할지 검토를
요청받았다.

---

## 2. 이름이 실제로 끊기는 지점 (코드로 확인됨)

| 메서드 | 파일:라인 | 읽는 값 | 안 읽는 값 |
|---|---|---|---|
| `tokens()` | `FigmaDesignSpecMapper.java:348-356` | `root.path("backgroundColor")`(RGBA), `style.fontFamily`/`fontSize`(리터럴) | 없음(애초에 이름 필드 자체를 참조 안 함) |
| `solidFillColor()`/`solidStrokeColor()` | `FigmaDesignSpecMapper.java:153-159` | `firstSolidPaint()` 위임 | — |
| `firstSolidPaint()` | `FigmaDesignSpecMapper.java:162-172` | `fills`/`strokes` 배열의 첫 SOLID+visible `paint.color`(RGBA) | 같은 paint 객체 옆의 `boundVariables.color`, `fillStyleId` |
| `rgba()` | `FigmaDesignSpecMapper.java:455-461` | `{r,g,b,a}` 0~1 float → 0~255 정수 변환 | 이름 관련 필드 자체가 입력에 없음 |

`UiDesignSpec.tokens`(`Map<String,String>`)와 `ComponentSpec.backgroundColor/borderColor`(`String`)도
값만 담는 구조라 이름을 넣을 자리가 애초에 없다.

---

## 3. 이름을 쓰면 좋은 점 (이득)

1. **가독성/유지보수성**: `rgba(37,99,235,1.00)` 대신 `brand-primary` 같은 이름이 있으면 생성 코드에
   의도가 남고, 색 변경 시 숫자값 전체 검색 대신 이름 하나만 바꾸면 됨.
2. **Claude의 선택 일관성**: "이 버튼은 danger 색이다"라는 의미 정보가 있으면 RGBA만 볼 때보다
   올바른 CSS 클래스/시맨틱 마크업을 선택할 가능성이 높아짐(일반적 관찰, 코드로 검증된 사실은 아님).
3. **디자이너-개발자 공통 언어**: "그 파란색" 대신 "brand-primary"로 소통 가능.
4. **추적 가능성**: 어떤 코드가 어떤 디자인 토큰에서 왔는지 흔적이 남아, 나중에 "뭘 바꿔야 하는지"
   추적하기 쉬워짐(단, 아래 §4-5의 이름 최신성 문제와 표리관계).

---

## 4. 이름을 쓰면 생기는 위험 (코드로 확인됨)

### 4-1. 프롬프트 인젝션 표면이 새로 열림
Style/Variable 이름은 해당 Figma 파일 편집 권한이 있는 누구나 붙일 수 있는 임의 문자열이다.
레포에는 이미 이런 "외부 자유 텍스트 → LLM 프롬프트" 경로에 대한 방어 패턴이 존재한다
(`WebCaptureProjectionPolicy.sanitizeLabel()` — 공백 정규화, 길이 100자 제한, 이메일/전화번호 패턴
차단). 하지만 이 방어는 웹 캡처 라벨에만 적용되고, Figma 토큰 경로에는 적용된 적이 없다 —
지금까지 토큰이 순수 RGBA/숫자값이라 인젝션 표면 자체가 없었기 때문이다.

### 4-2. 노출 지점이 예상보다 앞에 있음 — "2단계까지만"도 안전하지 않음
`ScreenSpecAssembler.assemble()`은 `resolvedUi.tokens()`/`components()`를 가공 없이 그대로
`ScreenSpecification`에 통과시킨다(`ScreenSpecAssembler.java` 해당 생성자 호출부 확인). 그리고
`DesignReferenceTool`의 `getScreenSpecification()`(121행), `reviseScreenSpecification()`(115행),
`approveScreenSpecification()`(105행) 세 개 MCP `@Tool` 메서드가 **`ScreenSpecification`을 그대로
반환**한다. 즉 프롬프트 조립(`ScreenSpecificationPromptFormatter`)을 건드리지 않아도, 데이터가
`ScreenSpecification`에 들어가는 순간 이미 이 세 툴의 응답을 통해 MCP client(Claude Desktop, 곧
LLM)에게 노출된다.

### 4-3. 검증 게이트가 이 데이터를 보지 않음
`ScreenSpecValidator`에서 `tokens`/`componentStyles`/`componentGeometry`를 참조하는 코드는
**0건**(grep 확인). `createScreenSpecification()`의 `REVIEW_REQUIRED` 사람 확인 게이트도 필드 매핑
이슈(`NO_COLUMN_CANDIDATE`, `COMMON_CODE_GROUP_REQUIRED`)만 검사 대상이라, 디자인 토큰 이름의
내용은 애초에 사람이 확인하는 항목에 없다.

### 4-4. 실사용 단계에서 XSS류 위험으로 격상
claude 경로에서 `ScreenSpecificationPromptFormatter`가 만든 프롬프트를 Claude가 읽고 실제
JSP/Thymeleaf 코드를 직접 작성한다(auto 경로 `CrudModelFactory.fromSchema()`는 tokens를 전혀
참조하지 않음 — 확인됨). `GeneratedCodeContractAuditor.audit()`/`auditAccessibility()`를 확인했는데
검사 대상은 FreeMarker 잔존 태그·Mapper `${}` 치환·외부 URL·html lang·img alt·이름 없는 button뿐이고,
**디자인 유래 텍스트가 이스케이프 없이 생성 소스에 삽입되는지 검사하는 항목은 없다.** 프로젝트
보안 규칙(`egov-security.md`)의 "출력 시 이스케이프" 요구는 런타임 출력 기준(`<c:out>`,
`escapeXml()`)이지, 생성 시점에 소스 코드 자체에 박히는 문자열은 사정거리 밖이다.

### 4-5. 실제로 해석하려면 새 서브시스템이 필요하고, 그마저 불완전함
- `FigmaDesignSpecMapper`는 `FigmaApiClient`를 아예 호출하지 않는 순수 파서다(grep 0건) — 이름
  해석 로직을 넣으려면 노드 조회 → ID 추출 → 별도 API 왕복 → 매핑 테이블 구성이라는 새 계층이
  필요하다.
- Variable 이름 해석에 필요한 Figma Variables REST API(`variables/local`/`variables/published`)는
  코드베이스에 **전혀 구현돼 있지 않다**(grep 0건). 이 API는 Figma **Enterprise 플랜 전용**이라는
  게 Figma 공식 정책이다(일반적으로 알려진 플랫폼 제약이며, 이 저장소 코드로 검증된 사실은 아님).
- Style 이름은 `FigmaApiClient.queryStyles(fileKey)`(`GET /v1/files/{fileKey}/styles`)로 조회
  가능하지만, 실제 호출처는 `FigmaMcpFacadeService.java:68` **한 곳뿐**이고 CRUD 디자인 분석 흐름과는
  무관한 기능이다. 게다가 파일 단위 조회라 팀 라이브러리(다른 파일) 소스 스타일은 여전히 못 찾는다.
- 결과적으로 "Enterprise 플랜 여부", "라이브러리 스타일 여부"에 따라 **조용히 부분적으로만
  해석되는 데이터**가 생기고, 소비 측(3단계 프롬프트/생성)은 지금 그 불완전성을 구분할 방법이 없다.

### 4-6. 설계 원칙과의 충돌
아키텍처 아티팩트 5번 섹션에서 정리한 "업무 계약(semantic, DB/Controller/VO 기반) vs 시각
스타일(raw, 의미 없는 값)" 구분을 `ScreenSpecificationPromptFormatter`가 실제로 지키고 있다. 색상
필드에 이름을 붙이면 원래 "의미 없는 raw 값"이었던 시각 스타일 카테고리에 의미론적 신뢰를
부여하게 되어 이 경계가 흐려진다.

### 4-7. 이름 최신성 드리프트 (신규 문제)
RGBA 값과 달리 Variable/Style 이름은 Figma에서 언제든 리네임될 수 있는 가변 값이다.
`ScreenSpecification`은 버전·리비전이 있는 승인 워크플로우(`reviseScreenSpecification`/
`approveScreenSpecification`)를 거치므로, 분석 시점에 캡처한 이름이 그 뒤 Figma에서 바뀌면 생성된
코드의 주석·변수명에 남은 "의미"가 원본과 어긋나는 문서 드리프트가 생긴다.

---

## 5. 종합 판단

"위험하니 하지 말라"가 아니라 **이득과 위험의 트레이드오프**다. §3의 이득은 실질적이지만,
§4의 위험 중 4-1(프롬프트 인젝션)·4-3(검증 공백)·4-4(XSS류 격상)는 **방어선 없이 도입하면 실제
운영 애플리케이션 소스에 미검증 외부 텍스트가 삽입되는 구체적 경로**를 연다는 점에서 가볍지 않다.
도입한다면 방어선을 먼저 갖추는 것을 전제로 해야 하며, 구체적 방어선과 구현 순서는
[`Figma_Variable_Style_이름_구현계획.md`](./Figma_Variable_Style_이름_구현계획.md)에 정리했다.

---

## 6. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `service/FigmaDesignSpecMapper.java` | Figma 노드 JSON → `UiDesignSpec` 파싱, RGBA/숫자값만 추출(이름 미추출) |
| `service/FigmaApiClient.java` | Figma REST API 호출. `queryStyles()` 존재하나 이 흐름에서 미사용, Variables API 엔드포인트 없음 |
| `service/figma/FigmaMcpFacadeService.java` | `queryStyles()`의 유일한 호출처(CRUD 분석과 무관) |
| `model/design/UiDesignSpec.java` | `tokens: Map<String,String>`, `ComponentSpec.backgroundColor/borderColor: String` — 이름 필드 없음 |
| `service/ScreenSpecAssembler.java` | `UiDesignSpec` → `ScreenSpecification` pass-through, 토큰 가공/검증 없음 |
| `service/ScreenSpecValidator.java` | tokens/componentStyles/componentGeometry 검증 로직 0건 |
| `tools/DesignReferenceTool.java` | `getScreenSpecification`/`reviseScreenSpecification`/`approveScreenSpecification` — `ScreenSpecification`을 그대로 MCP 반환 |
| `service/GeneratedCodeContractAuditor.java` | 생성 코드 사후 검사, 디자인 유래 리터럴 이스케이프 검사 없음 |
| `policy/WebCaptureProjectionPolicy.java` | 참고할 기존 sanitize 패턴(`sanitizeLabel()`) — 이 경로엔 미적용 |
| `service/ScreenSpecificationPromptFormatter.java` | claude 경로 프롬프트 조립 — componentStyles/tokens/componentGeometry를 raw 텍스트로 emit |
