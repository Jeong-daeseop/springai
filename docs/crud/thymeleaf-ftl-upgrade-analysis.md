# CrudPromptBuilderTool FTL → egov-boot-web 수준 업그레이드 분석

> 분석 기준일: 2026-06-21
> 비교 대상
> - **현재 FTL**: `springai/src/main/resources/templates/crud/thymeleaf-*.ftl`
> - **목표 구조**: `egov-boot-web/src/main/resources/templates/` (직원정보 CRUD 실제 구현체)
>
> **주의**: 이 문서는 CRUD 생성기(`templates/crud/`)만 다룹니다.
> 게시판 생성기(`templates/board/`)는 별도 업그레이드 검토가 필요합니다.
>
> **JSP 범위 확인**: 최초 분석 범위는 Thymeleaf FTL이었고, JSP는 "회귀 확인" 수준으로만 언급되어 있었습니다.
> 본 문서에는 아래 `## 6. JSP 구현 포함 여부 및 추가 구현계획` 섹션을 추가하여 JSP 업그레이드 범위를 별도로 정의합니다.

---

## 1. 현재 FTL 생성 결과 vs egov-boot-web 구조 비교

### 1-1. 레이아웃 구조

| 항목 | egov-boot-web | 현재 FTL 생성 결과 |
|---|---|---|
| 공통 레이아웃 | `templates/layout/default.html` 존재 | 없음 (standalone HTML) |
| 레이아웃 적용 | `layout:decorate="~{layout/default}"` | 미사용 |
| 콘텐츠 슬롯 | `layout:fragment="content"` | 없음 |
| 헤더 | 고정 topHeader (브랜드 + 로그아웃) | 없음 |
| 사이드바 | 고정 좌측 220px 네비게이션 | 없음 |
| 페이지 타이틀 패턴 | `layout:title-pattern="$CONTENT_TITLE - eGovFrame"` | 개별 `<title>` |
| 추가 head 슬롯 | `layout:fragment="head"` | 없음 |
| 추가 script 슬롯 | `layout:fragment="scripts"` | 없음 |

**결론**: Thymeleaf Layout Dialect 미적용이 가장 큰 구조적 차이.

---

### 1-2. CSS / JS 의존성

| 라이브러리 | egov-boot-web | 현재 FTL 생성 결과 | 비고 |
|---|---|---|---|
| Bootstrap 5.3.3 | CDN 포함 | **없음** | |
| Bootstrap Icons 1.11.3 | CDN 포함 | **없음** | |
| KRDS CSS | `/css/krds.min.css` | `/resources/css/krds.min.css` | 경로 다름 |
| KRDS JS | `/resources/js/krds.min.js` | 동일 | |

> **경로 차이 배경**: `egov-boot-web`은 Spring Boot 자동 정적 리소스 서빙(`/css/`)을 씁니다.
> 반면 WAR 생성 프로젝트(`ProjectInitializr`)는 `src/main/webapp/resources/css/krds.min.css`를 생성하고
> `servlet-context.xml`이 `/resources/**`로 매핑합니다.
> **현재 FTL의 `/resources/css/` 경로는 WAR 생성물 기준으로 정확합니다.**
> `/css/`로 바꾸면 생성된 WAR 프로젝트에서 CSS가 404 납니다.

현재 FTL은 이미 KRDS 컴포넌트 클래스를 사용합니다:
```html
<!-- thymeleaf-list.html.ftl — 이미 사용 중인 KRDS 클래스 -->
<div class="fieldset"> / <div class="form-conts keyword-sch">
<select class="krds-form-select medium">
<input class="krds-input medium">
<button class="krds-btn primary medium">
<div class="krds-structured-list-table"> / <div class="krds-table-wrap">
<div class="krds-pagination">
```

---

### 1-3. 페이지네이션 방식

**egov-boot-web** — 전체 페이지 번호 표시:
```html
<a th:each="pageNo : ${#numbers.sequence(
                paginationInfo.firstPageNoOnPageList,
                paginationInfo.lastPageNoOnPageList)}"
   class="page-link"
   th:classappend="${pageNo == paginationInfo.currentPageNo} ? 'active'"
   th:href="@{/cop/emp/list.do(pageIndex=${pageNo}, ...)}">
```

**현재 FTL** — 이전/다음 버튼만 (`thymeleaf-list.html.ftl:80-87`):
```html
<a th:if="${searchVO.pageIndex > 1}" ...>이전</a>
<span th:text="${searchVO.pageIndex}">1</span>
<a th:if="${paginationInfo.totalPageCount > searchVO.pageIndex}" ...>다음</a>
```

---

### 1-4. Controller 패턴

| 항목 | egov-boot-web | 현재 FTL 생성 결과 |
|---|---|---|
| HTTP 메서드 구분 | `@GetMapping` / `@PostMapping` | `@RequestMapping` (GET·POST 혼용) |
| 등록·수정 후 처리 | `redirect:` + `RedirectAttributes` | `forward:` |
| 성공 메시지 전달 | `addFlashAttribute("message", "...")` | 없음 |
| 화면 메시지 표시 | `th:if="${message}"` alert 박스 | 없음 |

> **⚠️ @GetMapping 전환 시 검색 폼 충돌 주의**
> 현재 Thymeleaf·JSP 검색 폼은 모두 `method="post"`입니다
> (`thymeleaf-list.html.ftl:13`, `jsp-list.jsp.ftl:21`).
> 목록 조회를 `@GetMapping`으로 전환하면 검색 submit 시 **405 Method Not Allowed**가 발생합니다.
> 구현 시 반드시 다음 중 하나를 선택해야 합니다.
>
> - **권장**: 검색 폼을 `method="get"`으로 변경 (`thymeleaf-list.html.ftl`, `jsp-list.jsp.ftl` 동시 수정)
> - **대안**: 목록 엔드포인트에 `@RequestMapping(value="...", method={GET, POST})` 유지

---

### 1-5. UI 컴포넌트

| 항목 | egov-boot-web | 현재 FTL 생성 결과 |
|---|---|---|
| 브레드크럼 | `<nav aria-label="breadcrumb">` | 없음 |
| 카드 레이아웃 | Bootstrap `card / card-header / card-body` | 없음 |
| 섹션 분할 상세 | 카드 3개로 필드 그룹화 | 단일 table |
| 배지 처리 | `th:switch` → 재직/휴직/퇴직 badge | 없음 |
| 행 클릭 → 상세 | `data-href` + JS addEventListener | 관리 버튼 방식 |
| 페이지 헤더 | 제목 + 브레드크럼 `d-flex` 영역 | `<h2>` 단순 텍스트 |
| 빈 목록 처리 | `th:if="${#lists.isEmpty(resultList)}"` | 동일 ✅ |
| 날짜 포맷 | `#temporals.format(..., 'yyyy-MM-dd HH:mm')` | 없음 |
| 인증 사용자명 | `${#authentication?.name}` | 없음 (layout 미포함) |

---

## 2. 항목별 구현 가능 여부

### ✅ FTL 수정만으로 구현 가능

| 항목 | 필요 FTL 작업 |
|---|---|
| Bootstrap CDN + Bootstrap Icons | 신규 `layout/default.html.ftl`의 `<head>`에 CDN 태그 추가 |
| 전체 페이지 번호 페이지네이션 | `thymeleaf-list.html.ftl:80-87` 페이지네이션 블록을 `#numbers.sequence` 방식으로 교체 |
| 성공 메시지 alert 박스 | `thymeleaf-list.html.ftl`에 `th:if="${message}"` 블록 추가 |
| 브레드크럼 | 각 FTL 페이지 헤더 영역에 `<nav>` 추가 |
| 카드 레이아웃 | 테이블을 `card / card-header / card-body` 구조로 감싸기 |
| 행 클릭 → 상세 이동 | `data-href` + `<script>` 블록 추가 |

---

### ⚠️ FTL 외 Java/XML도 함께 수정해야 하는 항목

#### Layout Dialect 적용

`layout:decorate`는 **Thymeleaf Layout Dialect 라이브러리**가 런타임에 존재해야 동작합니다.
FTL로 `layout:decorate` 구문을 생성하더라도, 대상 프로젝트에서 다음 두 가지가 없으면 TemplateProcessingException이 납니다.

1. **pom.xml 의존성** — `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect` 추가
   - 현재 `ensureThymeleafPomDependency()` (`ThymeleafRuntimeConfigurer.java:36-57`)는
     `thymeleaf-spring6` 존재 여부만 확인하고 있으면 즉시 `return`합니다.
     **기존 Thymeleaf 프로젝트에는 layout-dialect가 없어도 조기 return으로 건너뜁니다.**
   - `thymeleaf-spring6` 체크와 `layout-dialect` 체크를 **독립적으로 분리**해야 합니다:
     ```java
     // 현재 (문제): spring6 있으면 바로 return → layout-dialect 삽입 불가
     if (pom.contains("<artifactId>thymeleaf-spring6</artifactId>")) return;

     // 수정 후: 각각 독립 체크
     if (!pom.contains("thymeleaf-spring6"))       { /* spring6 삽입 */ }
     if (!pom.contains("thymeleaf-layout-dialect")) { /* layout-dialect 삽입 */ }
     ```

2. **`SpringTemplateEngine` bean 보강** — servlet-context.xml의 engine bean에 dialect 등록
   - 현재 `ensureThymeleafServletContext()` (`ThymeleafRuntimeConfigurer.java:68`)는
     `ThymeleafViewResolver` 존재 여부만 확인하고 있으면 즉시 `return`합니다.
     **기존 Thymeleaf가 설정된 프로젝트에는 `additionalDialects`가 추가되지 않습니다.**
   - servlet-context 보강도 `ThymeleafViewResolver` 여부와 `LayoutDialect` 여부를 **독립적으로** 체크해야 합니다:
     ```java
     if (!xml.contains("ThymeleafViewResolver"))    { /* ViewResolver 전체 bean 삽입 */ }
     else if (!xml.contains("LayoutDialect"))       { /* additionalDialects만 삽입 */ }
     ```
   - **merge 정책 주의**: 기존 `thymeleafTemplateEngine` bean에 이미 `<property name="additionalDialects">` 블록이 있는 경우,
     단순 문자열 삽입은 중복 `<property>` 또는 `<set>` 구조를 만듭니다.
     `additionalDialects` 존재 여부를 별도로 확인해 **없으면 property 전체 삽입, 있으면 `<set>` 안에 `LayoutDialect` bean만 추가**하는 로직이 필요합니다.

| 수정 대상 | 수정 내용 |
|---|---|
| `ThymeleafRuntimeConfigurer.java:36-57` | `thymeleaf-spring6` 체크와 `layout-dialect` 체크를 독립 분기로 분리 |
| `ThymeleafRuntimeConfigurer.java:68` | `ThymeleafViewResolver` 체크와 `LayoutDialect` 체크를 독립 분기로 분리 |
| `ThymeleafRuntimeConfigurer.java:85-89` | `thymeleafTemplateEngine` bean에 `additionalDialects` 삽입 로직 추가 |
| `thymeleaf-*.html.ftl` 4개 | `layout:decorate="~{layout/default}"` + `layout:fragment="content"` 추가 |
| `templates/crud/layout/default.html.ftl` | **신규** — 헤더 + 사이드바 + Bootstrap CDN + content/scripts 슬롯 |

#### layoutHtml 레이어 추가

`layout/default.html`은 도메인 이름과 무관한 **고정 경로·고정 파일명**입니다.
그러나 현재 `CrudLayerDefinition.resolveFileName()` (`CrudLayerDefinition.java:66-70`)은
기존 레이어 키가 없으면 `"Egov" + domain + suffix`를 반환합니다.
`layoutHtml` 레이어를 단순 추가하면 `EgovEmployerLayout.html` 같은 이름이 생성됩니다.

또한 `CrudTemplateRenderer.LAYER_TEMPLATE_MAP` (`CrudTemplateRenderer.java:23-42`)에
`layoutHtml` → `layout/default.html.ftl` 매핑이 없으므로 렌더링도 불가합니다.

| 수정 대상 | 수정 내용 |
|---|---|
| `CrudLayerDefinition.java:66-70` | `layoutHtml` 키를 고정 파일명 `"layout/default.html"`로 분기 처리 |
| `CrudLayerDefinition.java:41-47` | `THYMELEAF_LAYERS`에 `layoutHtml` 레이어 추가 (서브 경로: `src/main/resources/templates/`) |
| `CrudTemplateRenderer.java:23-42` | `"layoutHtml" → "layout/default.html.ftl"` 매핑 추가 |
| `controller.java.ftl` | `@GetMapping`/`@PostMapping` + `redirect:` + `RedirectAttributes` |

---

### ⚠️ Java 모델 수정이 추가로 필요한 항목

| 항목 | 이유 | 필요 변경 |
|---|---|---|
| 코드값 배지 (`th:switch`) | 어떤 컬럼이 공통코드 컬럼인지 스키마만으로 알 수 없음 | `CrudTemplateModel`에 `badgeFields` 메타 추가 |
| 섹션별 필드 그룹화 (상세 카드 분할) | 필드를 의미 단위로 묶는 정보가 없음 | `sectionGroups` 메타 또는 고정 섹션 템플릿 |
| 날짜 필드 `#temporals.format()` | `CrudMappingUtils.java:38-39`가 `date`/`datetime`/`timestamp`를 모두 `String`으로 반환해 `LocalDate`/`LocalDateTime` 타입이 생성되지 않음 | **선택 A (현행 유지)**: `String` 출력 그대로 사용. FTL 변경 없음 / **선택 B (전면 변경)**: `CrudMappingUtils`의 날짜 타입 매핑을 `LocalDate`/`LocalDateTime`으로 변경 + VO import + MyBatis `jdbcType=DATE` 처리까지 함께 수정 |

---

### ❌ 현재 범위 외 (별도 작업 필요)

| 항목 | 이유 |
|---|---|
| 사이드바 메뉴 동적 구성 | COMTNMENUINFO DB 조회 + `GlobalModelAdvice`와 연동 필요 |
| Spring Security 인증 표시 | `SecurityConfig` + `#authentication` 객체 필요 |
| 공통코드 select 자동 생성 | CommonCodeTool 연동 + 어떤 필드가 코드 필드인지 판별 필요 |

---

## 3. 업그레이드 시 신규/변경 파일 목록

### FTL 파일

| 파일 | 변경 유형 | 주요 변경 내용 |
|---|---|---|
| `templates/crud/layout/default.html.ftl` | **신규** | 헤더 + 사이드바 + content/scripts 슬롯 + Bootstrap CDN. 여러 도메인 CRUD를 순차 생성해도 내용이 동일하므로 overwrite 허용. 사용자 커스터마이징 보호는 후속 과제 |
| `templates/crud/thymeleaf-list.html.ftl` | **수정** | `layout:decorate` 추가, 카드, 브레드크럼, 전체 페이지 번호, 행 클릭, 메시지 |
| `templates/crud/thymeleaf-detail.html.ftl` | **수정** | `layout:decorate` 추가, 카드, 브레드크럼 |
| `templates/crud/thymeleaf-regist.html.ftl` | **수정** | `layout:decorate` 추가, 카드, 브레드크럼 |
| `templates/crud/thymeleaf-updt.html.ftl` | **수정** | `layout:decorate` 추가, 카드, 브레드크럼 |
| `templates/crud/controller.java.ftl` | **수정** | `@GetMapping`/`@PostMapping`, `redirect:`, `RedirectAttributes` |

### Java 파일

| 파일 | 변경 유형 | 수정 라인 | 주요 변경 내용 |
|---|---|---|---|
| `model/crud/CrudLayerDefinition.java` | **수정** | :41-47, :66-70 | `THYMELEAF_LAYERS`에 `layoutHtml` 추가 + `resolveFileName()` 분기 |
| `service/CrudTemplateRenderer.java` | **수정** | :23-42 | `LAYER_TEMPLATE_MAP`에 `layoutHtml` 매핑 추가 |
| `service/ThymeleafRuntimeConfigurer.java` | **수정** | :36, :74-96 | layout-dialect pom 의존성 + `additionalDialects` XML 삽입 |

---

## 4. egov-boot-web 실제 구조 요약 (참고)

```
templates/
├── layout/
│   └── default.html          ← Bootstrap 5.3.3 CDN + KRDS + 헤더(52px) + 사이드바(220px)
│                               layout:fragment="content" 슬롯 제공
└── cop/
    └── emp/
        ├── EmployyrInfoList.html    ← layout:decorate + 검색 + 카드 테이블 + 전체 페이지 번호
        ├── EmployyrInfoDetail.html  ← layout:decorate + 카드 3섹션 (인적/연락/소속)
        ├── EmployyrInfoRegist.html  ← layout:decorate + 카드 4섹션 + 비밀번호 확인 JS
        └── EmployyrInfoUpdt.html    ← layout:decorate + 카드 4섹션
```

**Controller 패턴:**
```java
@GetMapping("/list.do")    → 목록 (PaginationInfo)
@GetMapping("/detail.do")  → 상세
@GetMapping("/addView.do") → 등록 화면
@PostMapping("/add.do")    → 등록 처리 → redirect + FlashAttribute
@GetMapping("/editView.do") → 수정 화면
@PostMapping("/edit.do")   → 수정 처리 → redirect + FlashAttribute
@PostMapping("/delete.do") → 삭제 처리 → redirect + FlashAttribute
```

---

## 5. 범위 외: Board Thymeleaf 템플릿 현황

`templates/board/thymeleaf-*.ftl` 4개는 현재 standalone HTML로 작성되어 있습니다
(`layout:decorate` 미사용). CRUD 업그레이드와 별도로 동일한 작업이 필요하며,
board는 composite PK(BBS_ID + NTT_ID) 처리가 포함되어 있어 별도 검토가 권장됩니다.

---

## 6. JSP 구현 포함 여부 및 추가 구현계획

### 6-1. 현재 문서에 JSP 구현이 포함되어 있는가?

**결론: 포함되어 있지 않습니다.**

현재 문서의 주 대상은 다음 4개 Thymeleaf 템플릿입니다.

```text
templates/crud/thymeleaf-list.html.ftl
templates/crud/thymeleaf-detail.html.ftl
templates/crud/thymeleaf-regist.html.ftl
templates/crud/thymeleaf-updt.html.ftl
```

JSP는 기존 회귀 확인 대상으로만 언급되어 있습니다.

```text
빌드 검증 (`./gradlew build`) — 기존 JSP CRUD 회귀 확인 필수
```

따라서 JSP를 egov-boot-web 수준에 맞춰 함께 개선하려면 별도 작업 계획이 필요합니다.

---

### 6-2. 현재 JSP FTL 상태

현재 CRUD JSP 템플릿은 이미 KRDS 정적 리소스와 일부 KRDS 컴포넌트 클래스를 사용합니다.

| 파일 | 현재 상태 |
|---|---|
| `templates/crud/jsp-list.jsp.ftl` | KRDS 검색폼, 테이블, 버튼, `<ui:pagination>` 사용 |
| `templates/crud/jsp-detail.jsp.ftl` | KRDS 테이블/버튼 사용 |
| `templates/crud/jsp-regist.jsp.ftl` | Spring form taglib + KRDS input/error 클래스 사용 |
| `templates/crud/jsp-updt.jsp.ftl` | Spring form taglib + KRDS input/error 클래스 사용 |

정적 리소스 경로는 WAR 생성물 기준으로 맞습니다.

```jsp
<link rel="stylesheet" href="<%=contextPath%>/resources/css/krds.min.css">
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
```

이 경로는 `ProjectInitializr`가 생성하는 다음 파일 위치와 맞물립니다.

```text
src/main/webapp/resources/css/krds.min.css
src/main/webapp/resources/js/krds.min.js
```

---

### 6-3. JSP 업그레이드 목표

Thymeleaf는 `layout/default.html` + Layout Dialect 방향으로 개선한다.
반면 JSP는 Layout Dialect가 아니라 JSP 표준 방식으로 공통 레이아웃을 구성해야 합니다.

JSP 권장 방향:

1. `WEB-INF/jsp/common/` 아래 공통 include 파일을 생성한다.
2. 4개 JSP FTL이 공통 include를 사용한다.
3. KRDS 컴포넌트 규약 안에서 검색폼/목록/상세/등록/수정 레이아웃을 정리한다.
4. Controller PRG 패턴은 JSP/Thymeleaf 공통 Java 템플릿 변경으로 반영한다.

---

### 6-4. JSP 신규/변경 파일 계획

| 파일 | 변경 유형 | 주요 내용 |
|---|---|---|
| `templates/crud/jsp/common/header.jsp.ftl` | **신규** | 공통 `<head>`, KRDS CSS, 페이지 title, top header |
| `templates/crud/jsp/common/sidebar.jsp.ftl` | **신규** | 좌측 메뉴 영역. 1차 구현은 정적 메뉴 |
| `templates/crud/jsp/common/footer.jsp.ftl` | **신규** | KRDS JS, 공통 closing markup |
| `templates/crud/jsp-list.jsp.ftl` | **수정** | 공통 include 적용, 검색/목록/페이지네이션 레이아웃 정리 |
| `templates/crud/jsp-detail.jsp.ftl` | **수정** | 공통 include 적용, 상세 카드/테이블 구조 정리 |
| `templates/crud/jsp-regist.jsp.ftl` | **수정** | 공통 include 적용, 입력폼 그룹 정리 |
| `templates/crud/jsp-updt.jsp.ftl` | **수정** | 공통 include 적용, 입력폼 그룹 정리 |
| `templates/crud/controller.java.ftl` | **수정** | `@GetMapping`/`@PostMapping`, `redirect:`, `RedirectAttributes` 적용 |

---

### 6-5. JSP 레이어 정의 변경

JSP 공통 include 파일도 생성 대상에 포함하려면 `CrudLayerDefinition`과 `CrudTemplateRenderer`에 JSP 전용 공통 레이어가 필요합니다.

| 수정 대상 | 수정 내용 |
|---|---|
| `CrudLayerDefinition.java` | `JSP_LAYERS`에 `jspHeader`, `jspSidebar`, `jspFooter` 추가 |
| `CrudLayerDefinition.resolveFileName()` | 공통 JSP 파일은 도메인 접두어 없이 고정 파일명으로 분기 |
| `CrudTemplateRenderer.java` | `jspHeader`, `jspSidebar`, `jspFooter` → `jsp/common/*.jsp.ftl` 매핑 추가 |

예상 출력 경로:

```text
src/main/webapp/WEB-INF/jsp/common/header.jsp
src/main/webapp/WEB-INF/jsp/common/sidebar.jsp
src/main/webapp/WEB-INF/jsp/common/footer.jsp
```

JSP 공통 파일은 `viewType=jsp`일 때만 생성합니다.
`viewType=thymeleaf`일 때는 Thymeleaf `layout/default.html`만 생성합니다.

---

### 6-6. JSP 공통 include 적용 방식

각 JSP 화면 상단:

```jsp
<%@ include file="/WEB-INF/jsp/common/header.jsp" %>
<%@ include file="/WEB-INF/jsp/common/sidebar.jsp" %>
```

각 JSP 화면 하단:

```jsp
<%@ include file="/WEB-INF/jsp/common/footer.jsp" %>
```

주의:

- `header.jsp`에는 `contextPath` 선언을 한 번만 둔다.
- 개별 JSP에서 중복으로 `<html>`, `<head>`, KRDS CSS/JS를 선언하지 않는다.
- `<%@ taglib %>` 선언은 공통 파일로 옮길지, 화면별로 유지할지 하나로 통일한다.
- JSP include는 컴파일 시점 include라서 taglib 중복 선언에 주의한다.

---

### 6-7. JSP UI 개선 항목

| 항목 | 구현 방식 |
|---|---|
| 페이지 헤더 | 제목 + breadcrumb 영역 추가 |
| 검색 영역 | 현재 `sch-form-wrap` / `input-group` 유지, 한 줄 배치 유지 |
| 목록 | `krds-table-wrap` 유지, 행 클릭 또는 상세 버튼 중 하나로 통일 |
| 페이지네이션 | 기존 `<ui:pagination>` 유지. eGovFrame JSP 표준과 맞음 |
| 상세 | 단일 테이블에서 카드형 섹션 또는 KRDS 정보 테이블로 정리 |
| 등록/수정 | `form:form`, `form:input`, `form:errors` 유지. 필드 간격/버튼 영역 정리 |
| 메시지 | `RedirectAttributes`의 flash message를 JSP에서 `<c:if test="${not empty message}">`로 출력 |

---

### 6-8. JSP 구현 시 주의할 점

1. **Bootstrap 의존성은 선택 사항**
   - Thymeleaf 목표 구조는 egov-boot-web을 따라 Bootstrap CDN을 포함하지만, JSP는 현재 KRDS 중심으로 충분히 구성 가능합니다.
   - JSP까지 Bootstrap을 넣으면 KRDS와 스타일 우선순위 충돌 가능성이 있으므로 1차 구현은 KRDS 중심을 권장합니다.

2. **정적 리소스 경로는 `/resources/**` 유지**
   - JSP는 현재 WAR 프로젝트 구조와 동일하게 `<%=contextPath%>/resources/css/krds.min.css`를 유지해야 합니다.
   - `/css/krds.min.css`로 바꾸지 않습니다.

3. **Controller 변경은 JSP/Thymeleaf 공통 영향**
   - `controller.java.ftl`은 JSP와 Thymeleaf가 공유합니다.
   - `redirect:` + flash message 적용 시 JSP와 Thymeleaf 양쪽 화면 모두 `message` 렌더링을 지원해야 합니다.

4. **공통 include 생성은 멱등성 고려 필요**
   - 여러 도메인 CRUD를 생성할 때 `common/header.jsp`가 계속 덮어써질 수 있습니다.
   - 1차 구현에서는 동일 내용이면 덮어써도 무방하지만, 향후 사용자 커스터마이징 보호 정책이 필요합니다.

---

### 6-9. JSP 권장 구현 순서

1. `jsp/common/header.jsp.ftl`, `sidebar.jsp.ftl`, `footer.jsp.ftl` 신규 작성
2. `CrudLayerDefinition.java`의 `JSP_LAYERS`에 JSP 공통 레이어 추가
3. `CrudTemplateRenderer.java`에 JSP 공통 레이어 매핑 추가
4. 4개 JSP FTL에서 중복 `<html>/<head>/script>` 제거 후 공통 include 적용
5. JSP 목록/상세/등록/수정 화면의 KRDS 컴포넌트 배치 정리
6. `controller.java.ftl`의 PRG 패턴 변경 후 JSP/Thymeleaf 양쪽 메시지 표시 검증
7. `viewType=jsp`, `viewType=thymeleaf` 각각 생성 후 대상 프로젝트 Maven compile 및 화면 접속 검증

---

## 7. 권장 구현 순서

> **URL 패턴 범위**: 이 업그레이드는 **HTTP 메서드(`@GetMapping`/`@PostMapping`)와 PRG 패턴만 변경**합니다.
> URL 자체는 현재 생성 계약(`/emp/employerList.do`, `/emp/employerRegistView.do` 등, `CrudModelFactory.java:65`)을 유지합니다.
> egov-boot-web 방식(`/list.do`, `/add.do`)으로 URL까지 바꾸면 메뉴·권한 SQL, index.jsp 링크, 생성된 화면 간 링크 전체에 영향이 생기므로 별도 계획이 필요합니다.

1. `ThymeleafRuntimeConfigurer.java` 수정 — `thymeleaf-spring6` / `layout-dialect` 체크 독립 분리 + `additionalDialects` XML 삽입 (:36-57, :68)
2. `CrudLayerDefinition.java` 수정 — `layoutHtml` 레이어 추가 + `resolveFileName()` 분기 (:41-47, :66-70)
3. `CrudTemplateRenderer.java` 수정 — `LAYER_TEMPLATE_MAP`에 `layoutHtml` 매핑 추가 (:23-42)
4. `layout/default.html.ftl` 신규 작성 (헤더·사이드바·슬롯·Bootstrap CDN)
5. 4개 Thymeleaf FTL 수정 — `layout:decorate` 적용 + 카드·브레드크럼·페이지네이션 개선
6. `controller.java.ftl` 수정 — `@GetMapping`/`@PostMapping` + PRG 패턴 전환
   **→ 6-a**: `thymeleaf-list.html.ftl` + `jsp-list.jsp.ftl` 검색 폼을 `method="get"`으로 동시 변경 (미적용 시 목록 검색 405 발생)
7. JSP 업그레이드를 함께 진행할 경우 `## 6-9. JSP 권장 구현 순서`를 별도 브랜치/커밋 단위로 적용
8. 빌드 검증 (`./gradlew build`) — `viewType=jsp`, `viewType=thymeleaf` 양쪽 생성 회귀 확인 필수
