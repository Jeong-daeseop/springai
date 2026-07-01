# styles.css 중심 정적 리소스 전환 영향검토

## 목적

`initializeProject`가 생성하는 기존 정적 리소스 구조를 Claude Design 결과물 기준의 `styles.css` 중심 구조로 전환할 때 수정해야 할 위치와 영향을 정리한다.

현재 구조는 다음 파일을 생성하고, CRUD/게시판/마스터-디테일 화면이 이 경로를 참조한다.

```text
src/main/webapp/resources/css/krds.min.css
src/main/webapp/resources/js/krds.min.js
```

전환 후 권장 구조는 다음과 같다.

```text
src/main/webapp/resources/css/styles.css
src/main/webapp/resources/css/_ds_bundle.css
src/main/webapp/resources/css/fonts/fonts.css
src/main/webapp/resources/css/fonts/PretendardGOV-Regular.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Medium.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Bold.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Regular.subset.woff
src/main/webapp/resources/css/fonts/PretendardGOV-Medium.subset.woff
src/main/webapp/resources/css/fonts/PretendardGOV-Bold.subset.woff
src/main/webapp/resources/js/krds.min.js
```

## 리소스 역할 비교

| 파일 | 역할 | 비고 |
|---|---|---|
| `styles.css` | CSS 진입점 | `fonts/fonts.css`, `_ds_bundle.css`를 import |
| `_ds_bundle.css` | KRDS 스타일 본체 | KRDS 토큰과 `.krds-*` 컴포넌트 클래스 포함 |
| `fonts/fonts.css` | Pretendard GOV 폰트 등록 | `.woff`, `.woff2` 파일 필요 |
| `krds.min.js` | KRDS DOM 동작 | 메뉴, 탭, 아코디언, 모달, 툴팁 등 초기화. 유지 필요 |
| `_ds_bundle.js` | Claude Design/React 컴포넌트 번들 | `krds.min.js` 대체 불가 |

`styles.css` 내용은 다음 구조다.

```css
@import "./fonts/fonts.css";
@import "./_ds_bundle.css";
```

따라서 HTML에서는 `styles.css` 하나만 링크하면 된다.

```html
<link rel="stylesheet" th:href="@{/resources/css/styles.css}">
```

단, `krds.min.js`는 CSS 번들과 별개로 유지해야 한다.

```html
<script th:src="@{/resources/js/krds.min.js}"></script>
```

## 공식 KRDS 폰트 기준

KRDS 공식 사이트는 `Pretendard GOV`를 사용하며, 폰트 CSS는 KRDS 서버 내부 파일이 아니라 jsDelivr CDN을 참조한다.

```html
<link rel="stylesheet" as="style" crossorigin
      href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard-gov-dynamic-subset.min.css" />
```

생성 프로젝트는 전자정부/기관망 배포 가능성을 고려해야 하므로 최종적으로는 로컬 포함 방식이 안전하다.

다만 전환 범위를 줄이기 위해 단계는 다음처럼 나눈다.

| 단계 | 방식 | 설명 |
|---|---|---|
| 1차 | CDN 방식 | `styles.css` + `_ds_bundle.css`를 먼저 적용하고, 폰트는 공식 KRDS와 같은 jsDelivr Pretendard GOV CDN을 사용 |
| 2차 | 로컬 복사 방식 | `fonts/fonts.css`와 `.woff2` 폰트 파일을 프로젝트에 복사해 내부망 배포 안정성 확보 |

1차에서는 폰트 바이너리 저장 구조 변경 없이 CSS 전환 영향을 먼저 검증할 수 있다. 2차에서 로컬 폰트 복사 구조를 추가한다.

## 수정 대상

### 1. 초기 생성 파일 계획

수정 파일:

```text
src/main/java/com/krdevops/springai/service/initializr/FilePlanFactory.java
```

현재 `warFiles()`는 다음 파일을 생성한다.

```text
src/main/webapp/resources/css/krds.min.css
src/main/webapp/resources/js/krds.min.js
```

최종 변경 후에는 다음 파일을 생성하도록 바꾼다.

```text
src/main/webapp/resources/css/styles.css
src/main/webapp/resources/css/_ds_bundle.css
src/main/webapp/resources/css/fonts/fonts.css
src/main/webapp/resources/css/fonts/PretendardGOV-Regular.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Medium.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Bold.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Regular.subset.woff
src/main/webapp/resources/css/fonts/PretendardGOV-Medium.subset.woff
src/main/webapp/resources/css/fonts/PretendardGOV-Bold.subset.woff
src/main/webapp/resources/js/krds.min.js
```

디렉터리 생성 목록도 확인해야 한다.

```text
src/main/webapp/resources/css
src/main/webapp/resources/css/fonts
src/main/webapp/resources/js
```

현재 `resources/css/fonts` 디렉터리는 별도 등록되어 있지 않으므로 추가가 필요하다.

1차 CDN 방식에서는 `resources/css/fonts` 디렉터리와 폰트 파일 생성을 보류할 수 있다. 이 경우 `styles.css`는 `fonts/fonts.css`를 import하지 않거나, `fonts.css`가 CDN `@import`를 사용하도록 별도 구성해야 한다.

### 2. 정적 템플릿 렌더러

수정 파일:

```text
src/main/java/com/krdevops/springai/service/initializr/template/StaticTemplateRenderer.java
src/main/java/com/krdevops/springai/service/initializr/template/DefaultStaticTemplateRenderer.java
```

현재 메서드:

```text
krdsCss()
krdsJs()
```

권장 변경:

```text
stylesCss()
dsBundleCss()
fontsCss()
fontRegularWoff2()
fontMediumWoff2()
fontBoldWoff2()
fontRegularWoff()
fontMediumWoff()
fontBoldWoff()
krdsJs()
```

폰트 파일은 바이너리이므로 현재 문자열 기반 템플릿 로더 구조와 맞지 않을 수 있다. 구현 방식은 단계별로 나눈다.

| 단계 | 결론 | 설명 |
|---|---|---|
| 1차 | CDN 사용 | 공식 KRDS와 같은 Pretendard GOV CDN을 참조해 바이너리 저장 문제를 피한다 |
| 2차 | 로컬 복사 | `fonts/fonts.css`와 `.woff2` 파일을 classpath 리소스에서 생성 프로젝트로 복사한다 |

2차 로컬 복사 구현 방식은 둘 중 하나를 선택해야 한다.

| 방식 | 설명 | 영향 |
|---|---|---|
| 바이너리 복사 지원 추가 | `FilePlan` 또는 별도 copier가 byte 배열을 저장 | 구조 변경 큼, 가장 정확 |
| Base64 템플릿화 | 폰트 파일을 Base64 문자열로 저장 후 decode | 구현은 단순하지만 파일 크기와 관리 부담 증가 |

현재 구조가 텍스트 템플릿 중심이면, 단기적으로는 `fonts.css`만 생성하고 폰트는 CDN을 쓰는 방식도 가능하다. 다만 폐쇄망 안정성은 떨어진다.

`.woff` 필수 여부는 다음처럼 정한다.

| 파일 | 필수 여부 | 이유 |
|---|---|---|
| `.woff2` | 필수 | 최신 브라우저 기준 기본 웹폰트 포맷 |
| `.woff` | 선택 | 구형 브라우저 호환용 fallback |

따라서 2차 로컬 복사 구현 시 검증 필수 항목은 `.woff2` 중심으로 두고, `.woff`는 선택 리소스로 관리한다.

### 3. 정적 템플릿 파일

수정/추가 위치:

```text
src/main/resources/templates/egov/
```

현재 파일:

```text
krds.min.css.tpl
krds.min.js.tpl
```

권장 추가/교체:

```text
styles.css.tpl
_ds_bundle.css.tpl
fonts.css.tpl
krds.min.js.tpl
```

`krds.min.js.tpl`은 유지한다.

`_ds_bundle.js`는 추가하지 않는다. 이 파일은 Claude Design/React 컴포넌트용이며 기존 KRDS DOM 초기화 스크립트가 아니다.

`_ds_bundle.css.tpl`은 약 760KB로 다른 템플릿보다 크다. 텍스트 템플릿으로 classpath에 두는 방식도 가능하지만, 장기적으로는 다음 방식을 검토한다.

| 방식 | 설명 | 검토 포인트 |
|---|---|---|
| `.tpl` 텍스트 로딩 | 기존 `ClassPathTemplateLoader` 흐름 유지 | 구현 단순, 파일이 큰 템플릿으로 관리됨 |
| classpath 직접 복사 | `templates/egov/_ds_bundle.css` 같은 원본 리소스를 그대로 복사 | 치환 불필요한 대용량 정적 파일에 적합 |
| 외부 디자인 리소스 동기화 | `/Users/.../KRDS Design System`에서 복사 | 개발자 로컬 경로 의존성이 생기므로 빌드 재현성 낮음 |

권장은 **classpath 직접 복사 방식**이다. `_ds_bundle.css`는 변수 치환이 필요 없는 정적 파일이므로 템플릿 렌더링보다 리소스 복사가 더 자연스럽다.

### 4. 프로젝트 검증 로직

수정 파일:

```text
src/main/java/com/krdevops/springai/service/initializr/ProjectValidator.java
```

현재 필수 파일 검증 대상:

```text
src/main/webapp/resources/css/krds.min.css
src/main/webapp/resources/js/krds.min.js
```

변경 후 검증 대상:

```text
src/main/webapp/resources/css/styles.css
src/main/webapp/resources/css/_ds_bundle.css
src/main/webapp/resources/css/fonts/fonts.css
src/main/webapp/resources/css/fonts/PretendardGOV-Regular.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Medium.subset.woff2
src/main/webapp/resources/css/fonts/PretendardGOV-Bold.subset.woff2
src/main/webapp/resources/js/krds.min.js
```

검증 기준은 다음처럼 둔다.

| 리소스 | 검증 |
|---|---|
| `styles.css` | 필수 |
| `_ds_bundle.css` | 필수 |
| `fonts/fonts.css` | 2차 로컬 복사 방식에서 필수 |
| `.woff2` | 2차 로컬 복사 방식에서 필수 |
| `.woff` | 선택 |
| `krds.min.js` | 필수 |

현재 `ProjectValidator.validateResult()`는 `s.boot()` 여부로만 필수 파일 목록을 분기한다. 1차 CDN 방식과 2차 로컬 복사 방식은 검증 대상이 달라지므로, 단계 정보를 표현하는 설계가 필요하다.

권장안은 다음과 같다.

| 안 | 설명 | 판단 |
|---|---|---|
| `ProjectSpec`에 `assetMode` 또는 `fontMode` 추가 | `CDN`, `LOCAL` 같은 값을 두고 검증 목록을 분기 | 명시적이지만 API 파라미터와 호출부 변경 필요 |
| 1차에서는 폰트 로컬 파일을 warn 처리 | `styles.css`, `_ds_bundle.css`, `krds.min.js`는 필수, `fonts/fonts.css`와 폰트 파일은 누락 시 warn | 현재 구조 변경이 작아 1차 전환에 적합 |
| 항상 로컬 폰트 필수 | 구현 단순 | 바이너리 복사 구조가 먼저 필요해 1차 전환 범위가 커짐 |

1차 전환에서는 **폰트 로컬 파일을 warn 처리**하는 방식을 권장한다. 2차에서 `ProjectSpec`에 `assetMode` 또는 `fontMode`를 추가해 로컬 폰트 필수 검증으로 승격한다.

### 5. MCP Tool 설명 문구

수정 파일:

```text
src/main/java/com/krdevops/springai/tools/ProjectInitializrTool.java
```

현재 설명에는 다음 리소스가 노출된다.

```text
resources/css/krds.min.css, resources/js/krds.min.js
```

변경 후:

```text
resources/css/styles.css, resources/css/_ds_bundle.css, resources/css/fonts/*, resources/js/krds.min.js
```

사용자가 `initializeProject` 결과를 보고 실제 생성 파일을 이해할 수 있도록 결과 메시지도 같은 기준으로 맞춘다.

### 6. Thymeleaf 레이아웃 템플릿

수정 파일:

```text
src/main/resources/templates/masterdetail/layout/default.html.ftl
```

파일 존재 확인 결과, 현재 해당 파일은 존재한다. 따라서 `buildMasterDetailPrompt`의 Thymeleaf 레이아웃 수정 대상에 포함하는 것이 확정이다.

현재:

```html
<link rel="stylesheet" th:href="@{/resources/css/krds.min.css}">
<script th:src="@{/resources/js/krds.min.js}"></script>
```

변경 후:

```html
<link rel="stylesheet" th:href="@{/resources/css/styles.css}">
<script th:src="@{/resources/js/krds.min.js}"></script>
```

`buildMasterDetailPrompt`는 이 파일의 영향을 받는다.

### 7. CRUD JSP 템플릿

수정 파일:

```text
src/main/resources/templates/crud/jsp-list.jsp.ftl
src/main/resources/templates/crud/jsp-detail.jsp.ftl
src/main/resources/templates/crud/jsp-regist.jsp.ftl
src/main/resources/templates/crud/jsp-updt.jsp.ftl
```

확인 결과, 현재 CRUD JSP 템플릿은 `egov-layout.css`를 별도로 링크하지 않는다. JSP 4개 파일은 `krds.min.css`와 `krds.min.js`만 참조한다.

현재 JSP:

```jsp
<link rel="stylesheet" href="<%=contextPath%>/resources/css/krds.min.css">
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
```

변경 후:

```jsp
<link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
```

`buildFullCrudPrompt(..., viewType="jsp")`에 영향이 있다.

참고로 `masterdetail/layout/default.html.ftl`은 이전에 `krds.min.css`와 `egov-layout.css`를 함께 링크했지만, 현재는 `egov-layout.css` 링크를 제거한 상태다. 따라서 후속 검증은 masterdetail Thymeleaf 레이아웃과 과거 Bootstrap/eGov 보정 스타일 누락 여부를 중심으로 진행해야 한다.

### 8. CRUD Thymeleaf 레이아웃

수정 파일:

```text
src/main/resources/templates/crud/layout/default.html.ftl
```

이 파일은 현재 외부 CSS 링크가 없다. `<style>` 블록 안의 인라인 CSS만으로 `.egov-*` 레이아웃, 테이블, 폼 스타일을 정의한다.

따라서 `styles.css` 추가는 기존 `krds.min.css` 링크를 대체하는 것이 아니라, **CRUD Thymeleaf 레이아웃에 KRDS/FTC 공통 CSS를 처음 추가하는 변경**이다. `buildFullCrudPrompt(..., viewType="thymeleaf")`와 `buildBoardFeature(..., viewType="thymeleaf")`가 영향을 받는다.

권장 변경은 다음이다.

```html
<link rel="stylesheet" th:href="@{/resources/css/styles.css}">
```

단, 기존 `.egov-*` 클래스는 `_ds_bundle.css`에 없으므로 인라인 CSS를 즉시 제거하면 화면이 깨질 수 있다. 반대로 `styles.css`를 추가하면 KRDS 기본 태그/컴포넌트 스타일이 새로 들어오기 때문에 기존 인라인 CSS와 일부 우선순위 충돌이 생길 수 있다.

권장 순서:

1. `styles.css` 링크 추가
2. 기존 인라인 `.egov-*` CSS 유지
3. `egov-layout.css` 제거로 누락되는 스타일이 있는지 확인
4. 화면 검증 후 필요한 부분만 별도 `egov-crud.css` 또는 `egov-layout.css` 후속 파일로 분리 검토

특히 기존 `egov-layout.css.tpl`의 `#topHeader`, `#sidebar`, `#mainContent`, `.card`, `.table`, `.pagination` 보정 스타일이 새 `styles.css` 구조에서 실제로 필요한지 확인해야 한다. 현재 FTC/KRDS Thymeleaf 레이아웃은 `.egov-*` 인라인 CSS 중심이라 직접 영향은 제한적이지만, JSP 또는 과거 Bootstrap 기반 화면이 남아 있으면 스타일 누락 가능성이 있다.

화면 검증 시 다음을 확인한다.

```text
- body/font 기본 스타일이 의도대로 적용되는지
- .egov-table-wrap 내부 table 스타일이 KRDS 기본 table 스타일과 충돌하지 않는지
- button, input, select 기본 스타일이 과도하게 바뀌지 않는지
- GNB/LNB 간격, 페이지 타이틀, 검색 박스 높이가 유지되는지
- 모바일 폭에서 인라인 CSS와 KRDS 반응형 스타일이 충돌하지 않는지
```

### 9. Board 전용 Thymeleaf 템플릿

수정 파일:

```text
src/main/resources/templates/board/thymeleaf-list.html.ftl
src/main/resources/templates/board/thymeleaf-detail.html.ftl
src/main/resources/templates/board/thymeleaf-regist.html.ftl
src/main/resources/templates/board/thymeleaf-updt.html.ftl
```

현재 보드 화면은 `layout/default`를 사용하므로 CSS 링크는 주로 `crud/layout/default.html.ftl` 영향으로 처리된다. 다만 보드 템플릿 내부에서 KRDS 클래스를 직접 사용할 계획이면 `_ds_bundle.css`가 제공하는 클래스명과 일치하는지 별도 확인이 필요하다.

### 10. 테스트

수정 파일:

```text
src/test/java/com/krdevops/springai/service/initializr/ProjectInitializrWar50ManualWorkflowTest.java
```

현재 기대 파일:

```text
src/main/webapp/resources/css/krds.min.css
src/main/webapp/resources/js/krds.min.js
```

변경 후 기대 파일:

```text
src/main/webapp/resources/css/styles.css
src/main/webapp/resources/css/_ds_bundle.css
src/main/webapp/resources/css/fonts/fonts.css
src/main/webapp/resources/js/krds.min.js
```

폰트 바이너리까지 생성 검증에 포함할 경우 테스트 리소스 준비 방식도 함께 변경해야 한다.

### 11. 문서

수정 대상:

```text
docs/project-initializr/
docs/crud/
```

현재 문서에는 `/resources/css/krds.min.css`, `/resources/js/krds.min.js` 기준 설명이 남아 있다.

전환 후 다음 기준으로 문서를 갱신한다.

```text
/resources/css/styles.css
/resources/css/_ds_bundle.css
/resources/css/fonts/*
/resources/js/krds.min.js
```

## 생성기별 영향

| 생성기 | 영향 | 설명 |
|---|---|---|
| `initializeProject` | 큼 | 정적 리소스 생성 파일 목록과 검증 기준 변경 |
| `buildFullCrudPrompt` | 중간 | JSP/Thymeleaf 화면의 CSS 링크 변경 |
| `buildMasterDetailPrompt` | 중간 | `masterdetail/layout/default.html.ftl` 링크 변경 |
| `buildBoardFeature` | 중간 | `crud/layout/default.html.ftl`을 공유하므로 Thymeleaf 보드 화면 영향 |
| `buildJoinSelectPrompt` | 없음 | `CrudPromptBuilderTool.buildJoinSelectPrompt()`가 `MasterDetailService.buildJoinSelectPrompt()` 문자열 결과만 반환하며 파일 저장/HTML 레이어 렌더링을 호출하지 않음 |

## 리스크

| 리스크 | 설명 | 대응 |
|---|---|---|
| 폰트 파일 저장 방식 | 현재 템플릿 렌더러는 텍스트 중심이라 바이너리 폰트 저장이 별도 설계 필요 | `FilePlan` 바이너리 지원 또는 리소스 복사 유틸 추가 |
| 인라인 CSS 중복 | `crud/layout/default.html.ftl`에 `.egov-*` CSS가 많아 `styles.css`와 역할이 겹칠 수 있음 | 1차 전환에서는 유지, 2차로 별도 CSS 분리 |
| `egov-layout.css` 제거 | 기존 Bootstrap/eGov 보정 스타일이 사라질 수 있음 | 생성 화면별로 `#topHeader`, `#sidebar`, `#mainContent`, `.table`, `.pagination` 사용 여부 확인 |
| `_ds_bundle.css.tpl` 대용량화 | 약 760KB CSS를 템플릿으로 관리하면 가독성과 관리성이 낮음 | classpath 직접 복사 방식 검토 |
| JS 오해 | `_ds_bundle.js`를 `krds.min.js` 대체로 착각할 수 있음 | `krds.min.js` 유지 명시 |
| 공식 KRDS CDN 의존 | CDN 방식은 내부망에서 실패 가능 | 로컬 폰트 포함 권장 |
| 기존 문서 불일치 | 문서와 실제 생성 파일이 달라질 수 있음 | 전환 PR에 문서 갱신 포함 |
| 테스트 실패 | 기대 파일 목록이 기존 CSS 기준 | 테스트 기대값 동시 수정 |

## 권장 적용 순서

1. `initializeProject`의 정적 리소스 생성 구조를 `styles.css` 중심으로 변경한다.
2. `ProjectValidator`, 테스트 기대 파일 목록을 함께 수정한다.
3. `masterdetail/layout/default.html.ftl`, CRUD JSP 템플릿의 CSS 링크를 변경한다.
4. `crud/layout/default.html.ftl`에는 `styles.css` 링크를 추가하되 기존 인라인 CSS는 유지한다.
5. `buildFullCrudPrompt`, `buildMasterDetailPrompt`, `buildBoardFeature`로 샘플 프로젝트를 생성한다.
6. 생성 프로젝트에서 `./gradlew build -x test`를 실행해 빌드를 확인한다.
7. 브라우저에서 목록/상세/등록/수정 화면을 확인해 스타일과 JS 동작을 검증한다.

## 결론

`styles.css` 중심 구조로 전환하려면 단순히 `krds.min.css` 파일명만 바꾸면 안 된다. `styles.css`가 import하는 `_ds_bundle.css`, `fonts/fonts.css`, 실제 폰트 파일까지 생성해야 하며, `krds.min.js`는 그대로 유지해야 한다.

가장 안전한 전환안은 다음과 같다.

```text
CSS:
  /resources/css/styles.css
  /resources/css/_ds_bundle.css
  /resources/css/fonts/fonts.css
  /resources/css/fonts/*.woff2
  /resources/css/fonts/*.woff

JS:
  /resources/js/krds.min.js
```

`_ds_bundle.css`는 `krds.min.css`를 대체할 수 있지만, `_ds_bundle.js`는 `krds.min.js`를 대체할 수 없다.
