# JSP→Thymeleaf 레거시 전환(섹션 14~17)의 KRDS 디자인 시스템 반영 지점 검토

> 2026-09-02, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> 아키텍처 다이어그램 아티팩트 14~17번 섹션(승인 워크플로우 / 웹 캡처 가이드 / Figma 디자인 생성 /
> JSP→Thymeleaf Binding 생성)의 흐름에서 KRDS 디자인 시스템이 실제로 어디에 반영되는지 확인해달라는
> 요청에 대한 검토다.

---

## 1. 배경

CRUD/Board **자동 생성** 파이프라인(아키텍처 아티팩트 5~6번 섹션 계열)에는 `KrdsStylesConfigurer`가
`styles.css`에 KRDS CSS 계약을 patch하는 지점이 명확히 있다. 이 문서가 다루는 **JSP→Thymeleaf 레거시
전환** 파이프라인(14~17번 섹션)도 생성 결과 HTML에 `krds-*` 클래스를 쓰는데, 그 스타일이 실제로 어디서
채워지는지 확인이 필요했다.

---

## 2. 섹션별 확인 결과

| 섹션 | 내용 | KRDS 관여 여부 | 근거 |
|---|---|---|---|
| **14** | `ThymeleafProjectWorkflowService` 승인 워크플로우(`PREVIEW_READY`→`APPROVED`→`APPLIED`) | 없음 | `ThymeleafProjectWorkflowService.java`에서 `css`/`krds` 관련 코드 grep **0건** |
| **15** | 브라우저 캡처 실행 가이드 | 없음 | `WebCaptureProjectionPolicy`는 필드/라벨/텍스트만 추출, 디자인 시스템과 무관 |
| **16** | Figma 디자인 생성(Schema-first/Design-first) | 있으나 별개 소비처 | `DesignSystemQueryService`가 `DEFAULT_PROFILE_ID="krds"`로 `ComponentRegistry` 참조하지만, 산출물은 Figma Bundle/Artifact(`FigmaDesignOperationStatus.PREVIEW_READY`)에서 끝남 — 실제 Thymeleaf 코드 생성과 연결되는 지점 없음(grep으로 확인: `createDesignFromText`/`createDesignFromReference`/`createDesignFromImage`/`createDesignWithComponents` 호출부가 `FigmaDesignOrchestrationTool.java`에만 있고, `applyThymeleafProject`/`BindingComposer` 쪽에서 이 산출물을 참조하는 코드 없음) |
| **17** | JSP→Thymeleaf Binding Contract 생성 → `BindingComposer.compose()` | **클래스명만 하드코딩, 스타일 정의는 없음** | §3 참고 |

---

## 3. 섹션 17 상세 — 클래스명은 있지만 스타일 정의로 이어지지 않음

### 3-1. 템플릿에 KRDS 클래스명이 리터럴로 박혀 있음(확인됨)

`BindingComposer.compose()`가 `templates/legacy-thymeleaf/{list,form,detail}.html.ftl`을 렌더링한다.

```
list.html.ftl:19   class="krds-btn primary medium egov-btn egov-btn-register"
list.html.ftl:30   class="krds-input medium egov-control"
list.html.ftl:36   class="krds-table-wrap egov-density-${layoutDensity?lower_case}"
form.html.ftl:31,46  krds-table-wrap / krds-input
detail.html.ftl:20,37 krds-table-wrap
```

### 3-2. 이 클래스들의 실제 CSS 정의는 이 파이프라인에서 patch되지 않음

`KrdsStylesConfigurer`(`styles.css`에 `.krds-btn`/`.krds-input`/`.krds-table-wrap` 룰과
`--krds-button--size-height-medium` 등 CSS 변수를 멱등 patch하는 서비스)의 실제 호출처는:

```
BoardGeneratedCodeAuditor.java
BoardCssProcessor.java              (Board 자동 생성 파이프라인)
CrudFormColumnCssProcessor.java     (CRUD 자동 생성 파이프라인)
CrudTableDensityCssProcessor.java   (CRUD 자동 생성 파이프라인)
```

**전부 CRUD/Board 자동 생성 파이프라인(섹션 5~6 계열) 전용이며, JSP→Thymeleaf 마이그레이션
(섹션 17)과는 별개 코드 경로다.** `ThymeleafProjectWorkflowService`/`BindingComposer` 어디에서도
`KrdsStylesConfigurer`를 참조하지 않는다(grep 확인).

### 3-3. KRDS 원본 CSS/JS 자산은 완전히 다른 단계(프로젝트 초기화)에서만 배치됨

실제 KRDS 자산(`_ds_bundle.css`, `krds.min.js`, `styles.css` 뼈대)이 프로젝트에 놓이는 지점은
`FilePlanFactory`(`ProjectInitializrTool.initializeProject()` 도구, 프로젝트 골격 생성 단계) 하나뿐이다:

```
FilePlanFactory.java:110-115  WAR: styles.css / _ds_bundle.css / krds.min.js
FilePlanFactory.java:154-159  Boot: 동일 3종(static 경로)
FilePlanFactory.java:349-350  layout에 <link rel="stylesheet" ... styles.css> / <script ... krds.min.js> 삽입
```

이건 섹션 14~17보다 훨씬 앞선, **완전히 별개의 일회성 단계**다.

---

## 4. 실질적 위험

섹션 17은 정의상 "이미 존재하는 레거시 JSP 프로젝트"를 변환 대상으로 삼는다. 그 프로젝트가 이 도구의
`initializeProject()`를 거쳐 만들어진 게 아니라면(실제 마이그레이션 대상 레거시 프로젝트는 대부분
그렇지 않을 가능성이 높다), 생성된 화면에는 `krds-btn`/`krds-input`/`krds-table-wrap` **클래스명만
존재**하고 그 스타일 정의 자체가 대상 프로젝트에 없어서 **화면에 스타일이 전혀 반영되지 않는 상태**가
될 수 있다.

`ThymeleafProjectWorkflowService`나 `BindingComposer` 어디에도 "대상 프로젝트에 이 CSS/JS 자산이
이미 있는지" 검증하는 코드가 없다 — 이건 추정이 아니라 호출 그래프상 연결 자체가 없다는 사실로
확인된 gap이다.

---

## 5. 결론

1. 섹션 14(승인)·15(캡처)는 KRDS와 무관하다.
2. 섹션 16(Figma 디자인 생성)의 KRDS 참조는 Figma 목업 생성용이며 실제 Thymeleaf 코드 생성 결과와
   연결되지 않는다.
3. 섹션 17(JSP→Thymeleaf 변환)은 KRDS 클래스명을 HTML에 하드코딩하지만, 그 클래스의 실제 CSS
   정의를 채우는 `KrdsStylesConfigurer`를 호출하지 않으며, KRDS 원본 자산 배치는 훨씬 이전
   단계(`initializeProject()`)에서만 이루어진다.
4. 결과적으로 **섹션 17만 단독으로 실행되는 경로(초기화 없이 기존 레거시 프로젝트를 바로 변환하는
   경우)에서는 KRDS 스타일이 실제로 적용되지 않을 위험**이 코드 근거로 확인된다.

---

## 6. 대응 방안 (검토)

구현 여부는 결정되지 않았으며, 아래는 방안 비교만 담는다.

### 방안 1 — 사전조건 검증 게이트 추가 (권장)

**위치**: `BindingComposer.compose()` 진입부 또는 `ThymeleafProjectWorkflowService.preview()`

`contract.status() == REVIEW_REQUIRED`일 때 이미 쓰는 것과 같은 패턴
(`BINDING_REVIEW_REQUIRED_BLOCKS_COMPOSE`)을 재사용해서, 렌더링 전에 `layoutView`가 가리키는 파일과
`styles.css`/`_ds_bundle.css`/`krds.min.js`가 대상 프로젝트에 실제로 존재하는지 확인하고, 없으면
즉시 `FATAL` 이슈로 막는다.

- 장점: 코드 변경 범위가 작고(파일 존재 검사 몇 줄), 기존 실패 패턴을 그대로 재사용, 사람이 즉시
  원인을 알 수 있음
- 단점: 문제를 "막기"만 하고 "고쳐주지"는 않음 — 사용자가 직접 `initializeProject()`를 먼저
  돌리거나 자산을 수동 배치해야 함

### 방안 2 — 자동 배치(self-healing)로 확장

방안 1에서 자산이 없다고 판정되면, `FilePlanFactory`가 이미 하는 파일 배치(3종)를 재사용해
섹션 17 흐름 안에서 자동으로 채워 넣는 것.

- 장점: 사용자 개입 없이 한 번에 해결, `generateThymeleafLayout()`이 이미 "없으면 만들고 있으면
  skip" 방식을 쓰고 있어 같은 패턴 적용 가능
- **단점(중요)**: 섹션 17이 대상으로 삼는 프로젝트는 정의상 이미 존재하는 레거시 프로젝트다. 그
  프로젝트가 이미 자기만의 CSS 체계(부트스트랩, 자체 사내 프레임워크 등)를 갖고 있을 수 있는데,
  거기에 KRDS `_ds_bundle.css`(전체 KRDS 컴포넌트 라이브러리)를 강제로 얹으면 클래스명 충돌이나
  기존 스타일 오염이 생길 수 있다. 이건 `Figma_fills_strokes_반영_검토.md` §5에서 짚었던 "정부
  표준 디자인 시스템과의 일관성 트레이드오프"와 같은 종류의 결정 사항이라, 자동화하기 전에 별도
  의사결정이 필요하다.
- 참고: `KrdsStylesConfigurer`의 CRUD_CSS 블록(`.krds-btn { --krds-button--size-height-medium: ... }`)은
  **변수 오버라이드만** 정의한다 — 실제 `.krds-btn`의 배경색·테두리 같은 시각 규칙은 `_ds_bundle.css`
  쪽에 있으므로, `KrdsStylesConfigurer`만 연결하는 건 불충분하고 `_ds_bundle.css`/`krds.min.js`
  자체가 있어야 의미가 있다.

### 방안 3 — 브라우저 게이트를 기본 흐름에 강제 포함

이미 존재하는 `BROWSER_RENDER`/`VISUAL_PARITY` 게이트(현재 opt-in
`revalidateThymeleafProjectWithBrowserGate`로만 실행됨)를 `preview()`/`approve()`의 기본 흐름에
필수로 넣는 것.

- 장점: CSS/JS 누락뿐 아니라 다른 렌더링 이상까지 일반적으로 잡아냄
- 단점: 매 preview마다 실제 브라우저 렌더링이 필요해 비용·지연이 커짐. 첫 생성 시점엔 비교할
  baseline 스크린샷이 없어 효과가 제한적(최초 생성 건은 여전히 못 잡음)

### 권장 순서 (정정: 아래 §6-1 참고)

~~방안 1을 우선 적용하는 게 위험 대비 효과가 가장 좋다~~ — 이 판단은 §6-1에서 정정한다. 방안 1과
방안 3은 둘 다 **"감지"**(막기)일 뿐 **"해결"**(고치기)이 아니며, 게다가 방안 1을 readiness()와
연동하는 구체안([`Thymeleaf_레거시전환_Readiness_Apply_연동_검토.md`](./Thymeleaf_레거시전환_Readiness_Apply_연동_검토.md))은
추가 조사 결과 **구조적으로 실행 불가능**하다는 게 드러났다(§6-1).

---

## 6-1. 정정 — "감지"와 "해결"은 다른 문제다

### readiness() 기반 사전 차단이 불가능한 이유 (신규 발견)

`persistValidationReport()`(`BINDING`/`BUILD`/`RENDER` 증적을 실제로 남기는 유일한 코드 경로)는
`ThymeleafProjectWorkflowService.revalidate(operationId, browserOptions)` 안에서만 호출되는데, 이
메서드 자체가 `snapshot.operation().status() != APPLIED`면 `THYMELEAF_VALIDATION_REQUIRES_APPLIED`로
즉시 예외를 던진다. 즉 **검증 증적은 `apply()`가 끝난 뒤에만 생길 수 있다.** `readiness()`를
`approve()`/`apply()` 앞에 게이트로 연결하자는 제안은 "적용하기 전에, 적용한 뒤에만 생기는 증거를
요구"하는 순환 논리라 그대로는 구현할 수 없다. `Thymeleaf_레거시전환_Readiness_Apply_연동_검토.md`가
제안한 방향은 이 전제가 무너지므로 재검토가 필요하다.

### "감지"(방안 1·3)와 "해결"(방안 2)을 구분해야 한다

방안 1(사전조건 검증)과 방안 3(브라우저 게이트 필수화)은 문제를 **막을 뿐** 고쳐주지 않는다 —
사용자가 별도로 `initializeProject()`를 돌리거나 자산을 수동 배치해야 한다. 실제로 CSS/JS를
채워 넣어 **문제를 고치는** 방안은 방안 2(자동 배치)뿐이다. 그런데 방안 2는 그대로 쓰기엔 레거시
프로젝트의 기존 CSS를 오염시킬 위험이 있었다(§방안 2 참고). 그 위험을 줄이면서 "실제로 고치는"
효과는 유지하는 구체안 세 가지를 아래에 추가한다.

### 해결안 A — 충돌 검사 후 자동 배치 (방안 2의 안전판)

자동 배치 전에 대상 프로젝트를 먼저 스캔한다: `.krds-*` 클래스나 다른 CSS 프레임워크(부트스트랩
등)가 이미 쓰이고 있는지 확인 → 충돌 없으면 자동 배치, 충돌 있으면 사람에게 확인 요청(이미 있는
`REVIEW_REQUIRED` 패턴 재사용). §5의 "덮어쓰기 위험"을 자동화 이전에 걸러내면서도, 안전한 경우엔
실제로 문제가 고쳐진다.

### 해결안 B — 안내형 실패 (기존 패턴 재사용, 권장)

이 코드베이스에는 이미 같은 패턴이 있다 — layout 없이 화면 생성 Tool을 호출하면 그냥 에러만
던지지 않고 `generateThymeleafLayout()`을 먼저 실행하라고 구체적으로 안내한다(`CLAUDE.md` "layout
파일 없이 viewType="thymeleaf"로 화면 생성 Tool을 먼저 호출하면 ... generateThymeleafLayout() 선행
실행을 안내하는 메시지가 반환됩니다" 참고). 이걸 그대로 재사용해, CSS/JS가 없으면 에러 메시지에
"이 문제를 고치려면 [자산 배치 Tool]을 실행하세요"라고 다음 행동을 안내한다. 자동은 아니지만
사람이 안내를 따라 **실제로 문제를 해결하는 행동**을 하게 만든다는 점에서 방안 1(그냥 막고 끝)과
다르다.

### 해결안 C — KRDS를 격리해서 배치 (충돌 위험 자체를 제거)

KRDS CSS/JS를 레거시 프로젝트의 기존 경로에 섞지 않고 별도 네임스페이스(예:
`/resources/krds-migration/` 전용 경로)에 배치하고 CSS를 스코프 처리해 기존 스타일과 물리적으로
겹치지 않게 한다. §5의 "오염 위험" 자체가 사라지므로 자동 배치를 안전하게 상시 적용할 수 있지만,
CSS 스코프 설계가 추가로 필요해 셋 중 구현 범위가 가장 크다.

| 방안 | 실제로 고치나 | 위험 | 구현 비용 |
|---|---|---|---|
| A. 충돌검사+자동배치 | ✅(안전한 경우만) | 낮음 | 중간 |
| B. 안내형 실패 | 사람이 실행해야 고쳐짐(반자동) | 없음 | 낮음(기존 패턴 재사용) |
| C. 격리 배치 | ✅(항상) | 매우 낮음 | 높음 |

### 최종 권장 순서

**해결안 B를 가장 먼저 적용**하는 게 비용 대비 효과가 좋다 — 기존 패턴을 그대로 재사용하고
부작용이 없다. 이후 여력이 되면 해결안 A(충돌 검사 후 자동 배치)나 C(격리 배치)로 자동화 수준을
높이는 순서를 권한다. 방안 3(브라우저 게이트 필수화)은 "감지"에 머무는 데다 비용까지 커서 이
문제 전용 해법으로는 적합하지 않다.

---

## 7. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `service/thymeleaf/BindingComposer.java` | 섹션 17 핵심 — `legacy-thymeleaf/*.html.ftl` 렌더링, KRDS/CSS 관련 로직 없음 |
| `templates/legacy-thymeleaf/list.html.ftl`, `form.html.ftl`, `detail.html.ftl` | `krds-btn`/`krds-input`/`krds-table-wrap` 클래스명 하드코딩 |
| `service/KrdsStylesConfigurer.java` | KRDS CSS 계약을 `styles.css`에 patch — 섹션 17에서 미호출 |
| `service/generation/board/BoardCssProcessor.java`, `service/generation/crud/CrudFormColumnCssProcessor.java`, `service/generation/crud/CrudTableDensityCssProcessor.java` | `KrdsStylesConfigurer`의 실제 호출처(전부 CRUD/Board 자동 생성 파이프라인) |
| `service/initializr/FilePlanFactory.java` | KRDS 원본 자산(`_ds_bundle.css`/`krds.min.js`/`styles.css`) 배치 — 프로젝트 초기화 단계, 섹션 14~17과 별개 |
| `service/thymeleaf/ThymeleafProjectWorkflowService.java` | 섹션 14 승인 워크플로우 — CSS/KRDS 관련 코드 0건 |
| `service/figma/FigmaDesignOrchestrationService.java`, `tools/FigmaDesignOrchestrationTool.java` | 섹션 16 — KRDS `ComponentRegistry` 참조는 Figma Bundle 생성에서 끝남 |
| `service/designsystem/DesignSystemQueryService.java` | `DEFAULT_PROFILE_ID = "krds"`로 `ComponentRegistry` 조회 |
