# layoutMode=none 도입 — 2차 구현 계획 (검토)

## 상태

**검토 단계 — 미구현.** 아래 계획은 코드 대조까지 마친 설계안이며, 실제 코드 변경은 별도 승인 후 진행한다.
(관련: [[crudpromptbuildertool-layoutmode-reuse-plan]] 1차 구현 — `reuse`/`create` 완료, `none`은 1차에서 명시적으로 2차로 미룸)

## 요청 범위 (3개 항목)

1. `layoutMode=none` 지원 — 현재 3개 OrchestrationService(`CrudOrchestrationService`·`BoardOrchestrationService`·`MasterDetailOrchestrationService`) 모두 `layoutMode=none`이면 `IllegalArgumentException("layoutMode=none은 아직 지원하지 않습니다.")`을 던진다.
2. layout 없는 독립 화면 템플릿 — `layout:decorate` 없이 그 자체로 완결된 HTML을 생성.
3. breadcrumb partial 제거 — 독립 화면에는 `th:replace="~{${breadcrumbView} :: breadcrumb}"` 참조가 없어야 한다.

## 코드 대조 검증 결과

### 1. 현재 화면 템플릿 구조 (11개 전부 동일 패턴)

`crud/thymeleaf-{list,detail,regist,updt}.html.ftl`, `board/thymeleaf-{list,detail,regist,updt}.html.ftl`,
`masterdetail/thymeleaf-{list,detail,regist}.html.ftl` 11개 전부 아래 골격을 공유한다(`<title>` 텍스트만 다름).

```html
<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${domainKr} 목록</title>
</head>
<section layout:fragment="content">   <!-- ⚠️ MasterDetail 3종(list/detail/regist)은 <section>이 아니라 <th:block>을 씀. 아래 정정 참조 -->
    <th:block th:replace="~{${breadcrumbView} :: breadcrumb}"></th:block>
    ... (화면 본문 — 테이블/폼/페이지네이션 등) ...
</section>
<th:block layout:fragment="scripts">...</th:block>  <!-- list 3종(crud/board/masterdetail)만 존재, 나머지 8종은 없음. </section>의 형제 요소이지 내부 요소가 아님 -->
</html>
```

**정정 — CRUD/Board와 MasterDetail은 content 래퍼 태그가 다르다(코드 재확인 결과):**
- CRUD 4종·Board 4종(8개): `<section layout:fragment="content">` ... `</section>`
- MasterDetail 3종(list/detail/regist, 3개): `<th:block layout:fragment="content">` ... `</th:block>` (`<section>`이 아님)

11개 전부가 완전히 동일한 것은 아니고, **content 래퍼 태그만 도메인에 따라 다르며 나머지(breadcrumb 위치, scripts fragment 유무)는 동일**하다. §2 구현 시 이 차이를 그대로 보존해야 한다(MasterDetail standalone/decorate wrapper에 실수로 `<section>`을 쓰지 않도록 주의).

`layoutMode=none`이 되려면 이 골격에서 다음이 모두 제거·대체되어야 한다.

- `xmlns:layout` 선언과 `layout:decorate` 속성
- `layout:fragment="content"` / `layout:fragment="scripts"` (Layout Dialect 없이도 무해하게 렌더되지만, "layout 없음"의 취지상 완전히 제거하는 것이 맞다)
- breadcrumb `th:replace` 줄
- **현재 `default.html.ftl`에만 있는 head 자산**: `<meta charset>`, `<meta viewport>`, `<meta http-equiv=X-UA-Compatible>`, `<link rel="stylesheet" th:href="@{/resources/css/styles.css}">`, 공통 리셋 `<style>` 블록, `<script th:src="@{/resources/js/krds.min.js}">`. 독립 화면은 이 자산을 직접 포함해야 브라우저에서 정상 렌더된다. (참조: `crud/layout/default.html.ftl`, `board/masterdetail`도 내용 동일 — diff 없음 확인됨)
- `<main data-layout-shell>` 래퍼(플렉스 2단 gnb/lnb 배치용)는 독립 화면에 없으므로, 본문을 감싸는 최소 컨테이너(예: `max-width:1200px; margin:0 auto; padding:32px 24px 60px;`)로 대체해야 한다.

### 2. body(테이블/폼) 자체는 layoutMode와 무관

본문에서 사용하는 `${'$'}{message}`, `${'$'}{searchVO...}`, `${'$'}{resultList}` 등은 FreeMarker의 `$` 리터럴 escape 트릭으로, Thymeleaf 런타임 표현식을 그대로 통과시킨다. `layoutView`/`breadcrumbView`/`layoutBasePath` 같은 layout 전용 변수는 본문에서 전혀 참조되지 않는다. 즉 **본문(테이블/폼/페이지네이션/JS)은 decorate형과 standalone형 사이에서 100% 동일하게 재사용 가능**하다. 차이는 상단 5줄(html/head)과 breadcrumb 1줄뿐이다.

### 3. Controller가 breadcrumbs/lnbTitle/lnbMenus를 layoutMode 무관하게 항상 채움

`crud/board/masterdetail`의 `controller.java.ftl` 3개 전부, 모든 action 메서드가 `populateLayoutModel(model, currentMenuId, currentPageLabel)`을 호출하고, 이 메서드는 `currentMenuId`/`lnbTitle`/`lnbMenus`/`breadcrumbs`를 `ModelMap`에 무조건 채운다(라인 157-174 부근, 3개 도메인 동일 패턴). `layoutMode`는 현재 Controller 코드생성 경로에 전달되지 않는다.

standalone 화면은 이 데이터를 전혀 참조하지 않으므로 **기능상 문제는 없다** — 다만 도달 불가능한 모델 채우기 코드가 남는다.

### 4. LAYER_TEMPLATE_MAP은 layerKey당 템플릿 파일 1개만 고정 매핑

`CrudTemplateRenderer`/`BoardTemplateRenderer`/`MasterDetailTemplateRenderer`의 `LAYER_TEMPLATE_MAP`은 `"thymeleafList" → "thymeleaf-list.html.ftl"` 식 1:1 고정 매핑이다. layerKey는 `CrudLayerDefinition`/`BoardLayerDefinition`/`MasterDetailLayerDefinition`에도 동일하게 고정되어 있고, 저장 파일명(`List.html` 등)도 layerKey에서 결정된다. **layoutMode에 따라 다른 소스 템플릿을 선택하는 로직은 지금 존재하지 않는다.**

### 5. OrchestrationService의 NONE 분기 — 부분적으로 이미 호환됨

`isLayoutLayer(key) && layoutMode != CREATE`로 layout 레이어를 건너뛰는 필터는 REUSE/NONE 모두에 이미 적용되어 있다(레이어 5종 제외는 그대로 재사용 가능). `layoutMode == REUSE`일 때만 부재 검증을 수행하는 분기도 이미 NONE을 자동으로 건너뛴다(검증 불필요와 일치). **유일한 하드 블로커는 `if (... == NONE) throw ...` 가드 한 줄과, 화면 렌더링 시 여전히 decorate형 템플릿(`renderByLayerKey(..., layoutView, breadcrumbView, layoutBasePath)`)을 호출한다는 점이다.**

### 6. NONE 구현은 REUSE/CREATE/기본값과 같은 소스 라인을 공유한다 — **분리된 추가 작업이 아니라 함께 검토 필수**

`layoutMode`는 `CrudLayoutMode.from(String)` 한 곳에서 REUSE/CREATE/NONE(그리고 null→REUSE 기본값)으로 정규화된 뒤, 3개 OrchestrationService의 **동일한 조건식**을 타고 내려간다(예: `CrudOrchestrationService.java:64-72`).

```java
CrudLayoutMode resolvedLayoutMode = resolvedViewType == CrudViewType.THYMELEAF
        ? CrudLayoutMode.from(layoutMode)      // null → REUSE
        : CrudLayoutMode.CREATE;
if (resolvedViewType == CrudViewType.THYMELEAF && resolvedLayoutMode == CrudLayoutMode.NONE) {
    throw new IllegalArgumentException("layoutMode=none은 아직 지원하지 않습니다.");   // ← 제거 대상
}
ThymeleafLayoutValidator.LayoutReference layoutReference = resolvedViewType == CrudViewType.THYMELEAF
        ? thymeleafLayoutValidator.resolve(layoutView, breadcrumbView)
        : thymeleafLayoutValidator.resolve(null, null);
```

즉 "NONE만 새로 추가"가 아니라 **"REUSE/CREATE/기본값이 이미 지나가는 3갈래 분기가 4갈래로 바뀐다"**는 관점으로 봐야 한다. 구체적 공유 지점:

- **렌더 호출부** — 3개 서비스 저장 루프의 `renderByLayerKey(5-arg) : renderByLayerKey(2-arg)` 삼항식은 REUSE/CREATE가 지금 매번 지나가는 라인이다. NONE 라우팅을 추가하려면 이 라인 자체를 고쳐야 한다.
- **화면 FTL 11종 body 추출** — "결정 1"의 권장안(`-body.html.ftl` 분리)은 새 파일 추가가 아니라, REUSE/CREATE가 지금 렌더링하는 11개 파일의 **내용을 직접 수정**하는 리팩터링이다. 공백·escape 하나만 어긋나도 REUSE/CREATE 결과물이 조용히 달라질 수 있다.
- **`LAYER_TEMPLATE_MAP`/`toDataModel`** — static 공유 상태. NONE 전용 `STANDALONE_TEMPLATE_MAP` 분기 코드가 기존 map 조회·데이터 모델 조립 로직과 뒤섞인다.

**정정(재검토에서 확인) — NONE 가드는 3곳이 아니라 5곳이다.** `CrudOrchestrationService`·`BoardOrchestrationService`·`MasterDetailOrchestrationService`(auto 모드) 외에, Claude 프롬프트 모드 빌더인 **`CrudPromptBuilderService.java:179-180`**과 **`MasterDetailService.java:76-77`**도 동일한 `if (thymeleaf && resolvedLayoutMode == CrudLayoutMode.NONE) throw ...` 가드를 독립적으로(코드 공유 없이 각자 재구현) 갖고 있다. 두 파일 모두 layoutMode 해석(`CrudLayoutMode.from(layoutMode)`)과 레이어 필터링(`isLayoutLayer`)까지 Orchestration 쪽과 동일한 로직을 별도로 구현해뒀다(`CrudPromptBuilderService.java:176-178, 236-241`). 아래 §3(구현 계획)의 "OrchestrationService 3개"는 **정확히는 5개 파일**로 읽어야 하며, `CrudPromptBuilderTool.buildFullCrudPrompt`/`buildMasterDetailPrompt`가 `llmProvider=claude`일 때 호출하는 게 바로 이 두 서비스이므로, 여기를 빠뜨리면 `auto`는 NONE을 지원하는데 `claude` 프롬프트 모드는 여전히 예외를 던지는 상태가 된다(Board는 claude 모드 자체가 없으므로 해당 없음).

**결론: NONE 구현 PR에는 REUSE/CREATE/기본값(미입력) 3가지 경로의 회귀 테스트가 반드시 함께 포함되어야 한다.** 이는 "혹시 모르니 다시 테스트"가 아니라, 코드 구조상 같은 함수·같은 파일을 수정하기 때문에 **필연적으로 요구되는 검증**이다. 다행히 1차 구현에서 이미 REUSE 기본값(`orchestrate_thymeleafReuseWithoutLayout_returnsFailureBeforeSave` 등, layoutMode 미전달로 REUSE 기본값 경로 이미 검증됨)·CREATE(`orchestrate_thymeleafViewType_savesHtmlUnderResourcesTemplates`, 16/17/18개 검증)·커스텀 layoutView(2차 검토 세션에서 추가한 `layout/admin` 테스트) 테스트가 준비되어 있으므로, **이 기존 테스트 스위트가 수정 없이 그대로 통과하는 것 자체를 NONE 구현의 완료 조건(gate)으로 삼는다.**

## 설계 결정 (권장안 적용 — 확정 전 조정 가능)

사용자 확인을 시도했으나 응답이 없어(60초 타임아웃), 아래는 **권장안을 기본값으로 반영**한 결과다. 구현 착수 전 언제든 조정 가능하다.

### 결정 1 — 템플릿 분리 방식: 공통 body를 partial로 추출 (권장안 채택)

11개 화면 각각을 아래 2개 파일로 분리한다.

```
crud/thymeleaf-list-body.html.ftl       ← 본문만 (breadcrumb 제외, <section> 내부 컨텐츠)
crud/thymeleaf-list.html.ftl            ← decorate형 wrapper. breadcrumb + <#include "thymeleaf-list-body.html.ftl">
crud/thymeleaf-list-standalone.html.ftl ← standalone형 wrapper. head 자산 인라인 + <#include "thymeleaf-list-body.html.ftl"> (breadcrumb 없음)
```

**대안(미채택, 필요 시 전환 가능):** 화면마다 `-standalone.html.ftl` 하나만 완전히 새로 작성(본문 복사). 파일 단위 완결성은 높지만 본문 로직 변경 시 2곳(decorate형·standalone형)을 함께 고쳐야 하는 드리프트 위험이 있다. 11개 화면 × 100행 이상의 실질 로직이라 3줄짜리 사소한 중복이 아니므로, 이번 계획은 partial 추출을 기본으로 한다.

영향: 기존 11개 `-body`로 리네임/분리, decorate형 wrapper 11개 축소, standalone wrapper 11개 신규 → **최종 파일 수: 기존 11개 + body 11개 + standalone 11개 = 33개**(순증 22개).

### 결정 2 — breadcrumb 제거 범위: 화면 템플릿(HTML)만 (권장안 채택)

standalone 화면에서 breadcrumb `th:replace` 줄만 제거한다. Controller의 `populateLayoutModel`(`breadcrumbs`/`lnbTitle`/`lnbMenus` 채우기)은 layoutMode와 무관하게 그대로 유지한다.

**대안(미채택):** `controller.java.ftl`도 layoutMode를 데이터 모델로 받아 NONE일 때 `populateLayoutModel` 호출/생성 자체를 생략. Controller 코드생성 3개 파일(`crud/board/masterdetail controller.java.ftl`) 모두 변경이 필요하고, mode별로 다른 Controller 코드가 나오는 더 큰 변경 범위라 이번 2차 범위에서는 제외한다. 사용자가 "완전히 죽은 코드도 남기지 않겠다"는 원칙을 원하면 3차로 넘길 수 있다.

## 구현 계획

### 0. 회귀 안전망 확보 (착수 전 선행 — 위 §6 근거)

- 착수 전 `./gradlew test`로 현재 REUSE/CREATE/커스텀 layoutView 테스트가 모두 통과하는 기준선(baseline)을 확인해둔다.
- **`CrudPromptBuilderService`/`MasterDetailService`의 reuse/create 안전망을 먼저 보강한다** — `CrudPromptBuilderService` 전용 테스트가 아예 없고 `MasterDetailServiceTest`엔 create 모드 테스트가 없음을 확인했다(테스트 계획 참조). §3에서 이 두 파일의 NONE 가드를 제거하기 전에, 최소한 `MasterDetailServiceTest`에 create 모드 케이스를 추가하고 `CrudPromptBuilderService`에 대한 최소 단위 테스트(reuse/create 각 1개)를 신설해 회귀를 감지할 수 있는 상태로 만든다.
- §2(화면 FTL 11종 → 33종 분리) 작업은 **본문 내용을 한 글자도 바꾸지 않는 순수 이동**으로 진행한다(로직 개선은 별도 커밋으로 분리).
- §2 완료 직후, NONE 관련 코드(§1·§3)를 아직 건드리지 않은 상태에서 기존 테스트 스위트를 먼저 재실행해 REUSE/CREATE 회귀가 없는지 확인한 뒤 §1·§3·§4·§5로 진행한다(문제가 생기면 NONE 코드와 뒤섞이기 전에 원인을 좁힐 수 있다).

### 1. `renderByLayerKey`에 layoutMode 인지 추가 (renderer 3개)

`CrudTemplateRenderer` · `BoardTemplateRenderer` · `MasterDetailTemplateRenderer`

- `LAYER_TEMPLATE_MAP`과 별도로 `STANDALONE_TEMPLATE_MAP`(layerKey → `-standalone.html.ftl` 파일명)을 추가한다. **레이어 정의(`CrudLayerDefinition` 등)나 저장 파일명은 변경하지 않는다** — 저장 파일명은 여전히 `List.html`이고, "어떤 FTL 소스로 렌더링할지"만 layoutMode로 갈린다.
- `renderByLayerKey(layerKey, model, layoutView, breadcrumbView, layoutBasePath)`에 `layoutMode` 파라미터를 추가한 오버로드를 만든다(기존 시그니처는 유지 — 기본값 REUSE/CREATE 경로 하위 호환).
  - `layoutMode == NONE`이면 `STANDALONE_TEMPLATE_MAP`에서 템플릿을 선택하고, `layoutView`/`breadcrumbView`/`layoutBasePath`는 데이터 모델에 넣지 않는다(사용되지 않으므로).
  - 그 외에는 기존 `LAYER_TEMPLATE_MAP` 경로 그대로.

### 2. 화면 FTL 11종 → 33종 분리

- 각 도메인×화면(11개)에 대해 본문을 `-body.html.ftl`로 추출한다.
- 기존 `crud/thymeleaf-list.html.ftl` 등(CRUD·Board 8종)은 `<section layout:fragment="content"><th:block th:replace="~{${breadcrumbView} :: breadcrumb}"></th:block><#include "thymeleaf-list-body.html.ftl"></section>` 형태로 축약한다. **MasterDetail 3종은 `<section>` 대신 `<th:block layout:fragment="content">...</th:block>`로 축약**(§1 정정 참조 — 태그만 다르고 나머지 구조는 동일).
- 신규 `crud/thymeleaf-list-standalone.html.ftl`을 아래 골격으로 작성한다(`default.html.ftl`에서 head 자산 발췌, gnb/lnb 전용 CSS 셀렉터는 제외).

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>${domainKr} 목록</title>
    <link rel="stylesheet" th:href="@{/resources/css/styles.css}">
    <style>/* default.html.ftl의 공통 리셋 중 gnb/lnb 전용 셀렉터 제외한 부분 */</style>
</head>
<body>
<div style="max-width:1200px;margin:0 auto;padding:32px 24px 60px;">
<#include "thymeleaf-list-body.html.ftl">
</div>
<script th:src="@{/resources/js/krds.min.js}"></script>
</body>
</html>
```

**정정(재검토) — scripts fragment는 body partial에 넣지 않는다.** `</section>`(또는 MasterDetail의 `</th:block>`) 뒤에 오는 `<th:block layout:fragment="scripts">`는 **`<section>`/`<th:block>`의 형제 요소**이지 내부 요소가 아니다(코드 확인: `crud/board/masterdetail`의 `thymeleaf-list.html.ftl` 전부 동일 구조). body partial 하나에 content+script를 합쳐 `<section>` 안에서 `<#include>`하면 script가 section 내부로 중첩되어 현재 DOM 구조와 달라진다. 이 스크립트는 3개 파일(각 도메인의 list 화면) 모두 완전히 동일한 3줄짜리 내용이므로, **partial로 추출하지 않고 각 wrapper(decorate형·standalone형)에 그대로 직접 둔다**(사소한 중복은 허용 — 진짜 중복 위험은 100행 이상인 본문 쪽).
  - decorate형 wrapper: 기존처럼 `<th:block layout:fragment="scripts"><script>...</script></th:block>` 유지.
  - standalone형 wrapper: `layout:fragment` 래퍼 없이 `<script>...</script>`만 직접 포함.
  - 대상은 `list` 3종(crud/board/masterdetail)뿐이며, 나머지 8종(detail/regist/updt)은 애초에 scripts fragment가 없으므로 해당 없음.
- 대상: `crud`(4) · `board`(4) · `masterdetail`(3) = 11개 화면 각각 body+standalone 2종 신규. **MasterDetail 3종은 body partial과 두 wrapper 모두 `<section>`이 아니라 `<th:block>`으로 감싼다**(§1 정정 참조).

### 3. 5개 파일 — NONE 분기 활성화 (OrchestrationService 3개 + Claude 프롬프트 빌더 2개)

`CrudOrchestrationService` · `BoardOrchestrationService` · `MasterDetailOrchestrationService` (auto 모드)
`CrudPromptBuilderService` · `MasterDetailService` (claude 프롬프트 모드 — §6 정정 참조. Board는 claude 모드가 없으므로 해당 없음)

- 5개 파일 전부에서 `if (... == CrudLayoutMode.NONE) throw ...` 가드를 제거한다. **3개만 고치면 `auto`/`claude` 모드 간 지원 여부가 어긋난다.**
- (auto 3개) 저장 루프의 렌더링 호출에 `resolvedLayoutMode`를 전달하도록 변경(`renderByLayerKey(..., layoutMode)` 신규 오버로드 사용).
- (claude 2개) `CrudPromptBuilderService`/`MasterDetailService`는 실제 파일을 렌더링하지 않고 프롬프트 텍스트만 만들므로, `renderByLayerKey` 변경과는 무관하다 — §5(Claude 프롬프트 모드) 작업과 함께 처리한다.
- `layoutMode == REUSE`일 때만 수행하던 부재 검증은 **그대로 유지**(NONE은 검증 불필요 — 이미 통과하는 분기).
- `isLayoutLayer(key) && layoutMode != CREATE` 필터도 **그대로 유지**(NONE도 layout 레이어 제외 — 이미 맞는 동작, `CrudPromptBuilderService.java:236-241`의 동일 필터도 마찬가지).

### 4. Tool 파라미터/설명 갱신

`CrudPromptBuilderTool.java`

- `buildFullCrudPrompt` / `buildMasterDetailPrompt` / `buildBoardFeature` 3개 `@Tool` description의 `layoutMode` 설명에 `"none"` 옵션을 추가한다.
  - `"none": layout 참조 없는 독립 화면 생성. layout 파일 유무와 무관하게 동작(부재 검증 생략)`
- 생성 파일 수 텍스트에 NONE 케이스 반영(레이어 수는 REUSE와 동일 — layout 5종 제외).

### 5. Claude 프롬프트 모드 (CRUD·MasterDetail)

`CrudPromptBuilderService.java` · `MasterDetailService.java`

- **§3에서 두 파일의 NONE `throw` 가드를 먼저 제거**하지 않으면 아래 안내 문구 추가 작업 자체가 도달 불가능한 코드가 된다(§3와 반드시 같은 커밋/PR에서 처리).
- `layoutMode=none` 안내 문구 추가: `layout:decorate` 사용 금지, breadcrumb 참조 제거, standalone head 자산 인라인 요구 사항을 프롬프트에 명시한다(1차 구현 계획 §6이 reuse/create만 다뤘던 것의 확장).

### 6. 문서/워크플로 갱신

- `CLAUDE.md` — 변경 없음(신규 Tool 없음, 파라미터 옵션 추가뿐).
- `WorkflowDefinitionRegistry`의 `crud-thymeleaf` workflow는 layoutMode=none 경로에서는 `generateThymeleafLayout` 선행 단계가 불필요하다. 다만 이 workflow는 문맥 감지(`thymeleaf` 키워드)로만 전환되므로, **NONE 전용 세부 분기까지는 이번 2차에서 다루지 않는다**(과설계 방지 — 필요성이 확인되면 3차로).

## 테스트 계획

**완료 게이트(필수, §6 근거) — NONE 관련 신규 테스트보다 우선 확인:**
- 기존 `CrudOrchestrationServiceTest`(11/16개, reuse 기본값·custom layoutView 포함) · `BoardOrchestrationServiceTest`(12/17개) · `MasterDetailOrchestrationServiceTest`(13-14/18-19개) · `*TemplateRenderer` 관련 테스트가 **수정 없이 전부 그대로 통과**해야 한다. 코드 변경으로 이 중 하나라도 고쳐야 한다면, NONE 구현이 REUSE/CREATE 동작을 바꿨다는 신호이므로 원인을 먼저 해소한다.
- **정정(재검토):** `CrudPromptBuilderService` 전용 단위 테스트 파일은 존재하지 않는다(`CrudPromptBuilderToolTest`가 `CrudPromptBuilderService`를 통째로 mock 처리하므로 실제 로직은 어디서도 직접 테스트되지 않음). `MasterDetailServiceTest`는 2개 테스트만 있고 그중 하나가 reuse 기본값(thymeleaf, "13개 파일")을 검증하지만, **create 모드 테스트는 없다**(1차 계획 문서가 "reuse/create 두 케이스로 분리 완료"라고 기록했으나 실제로는 반영되지 않았다 — 이번 2차 검토 중 발견). 따라서 "기존 테스트가 그대로 통과"라는 안전망이 이 두 파일에는 사실상 없다. §3에서 NONE 가드를 제거하기 전에 최소한 `MasterDetailServiceTest`에 create 모드 테스트를 먼저 보강하고, `CrudPromptBuilderService`도 최소 1개의 전용 테스트 파일을 신설해 reuse/create 회귀를 잡을 수 있게 만드는 작업을 §3와 함께 선행한다(§0의 회귀 안전망 확보 대상에 추가).
- `-body.html.ftl` 추출 후 기존 decorate형 렌더 결과가 **리팩터링 전과 바이트 단위로 동일**한지 회귀 검증(스냅샷 또는 문자열 비교) — §2 body 분리가 REUSE/CREATE 출력 자체를 바꾸지 않았음을 보장하는 핵심 테스트. §0의 "선행 분리 → 즉시 재검증" 순서와 짝을 이룬다.

신규 (NONE 전용):
- `CrudOrchestrationServiceTest` / `BoardOrchestrationServiceTest` / `MasterDetailOrchestrationServiceTest`
  - `layoutMode=none` → layout 부재 검증 없이 성공(레이어 수는 REUSE와 동일: CRUD 11 / Board 12 / MasterDetail 13(+1 EgovMainController=14)).
  - `layoutMode=none` 렌더 결과에 `layout:decorate`, `xmlns:layout`, breadcrumb `th:replace`가 전혀 없는지 검증.
  - `layoutMode=none` 렌더 결과에 `<link rel="stylesheet"` (styles.css)와 `<meta charset` 등 head 자산이 포함되는지 검증.
  - MasterDetail은 standalone 렌더 결과에도 `<th:block>` 구조가 유지되는지 검증(§1/§2 정정 — `<section>`으로 잘못 나오지 않는지).
- Renderer 3개: `renderByLayerKey(layerKey, model, layoutMode)` 신규 오버로드 — NONE이면 `-standalone.html.ftl` 소스를 사용하는지 검증.
- `CrudPromptBuilderServiceTest` / `MasterDetailServiceTest`: `layoutMode=none`이 더 이상 예외를 던지지 않고 NONE 안내 문구(§5)를 포함한 프롬프트를 반환하는지 검증(§6에서 확인한 별도 가드 2곳에 대한 직접 테스트).

## Verification

```bash
./gradlew build
./gradlew test --tests "*OrchestrationServiceTest" --tests "*TemplateRenderer*"
```

수동 E2E(2차 승인 후, egov-mysql/ollama 기동 시):
1. `buildFullCrudPrompt(..., viewType=thymeleaf, layoutMode=none)` → layout 파일 존재 여부와 무관하게 성공, `templates/employer/EgovEmployerList.html`이 완전한 standalone HTML로 생성되는지 확인(브라우저에서 단독으로 정상 렌더되는지).
2. 생성된 HTML에 `layout:decorate`/breadcrumb 참조가 없는지 grep 확인.
3. 동일 프로젝트에 layout이 이미 존재하는 상태에서 `layoutMode=none` 화면과 `layoutMode=reuse` 화면을 함께 생성해도 서로 간섭 없는지 확인.
