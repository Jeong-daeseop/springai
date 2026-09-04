# Thymeleaf GNB 다중 도메인 메뉴 노출 설계 검토

> **[정정 노트]** 본문의 `COMTNMENUINFO`/`COMTNPROGRMLIST` 표기는 설계 시점 가정이다. 실제 구현은 `LETTNMENUINFO`/`LETTNPROGRMLIST` 기준이며(`ebt` DB 스키마), 테이블명은 `generateThymeleafLayout()`의 `menuTableName`/`programTableName` 파라미터로 변경 가능하다(기본값 `LETTN*`). 설계 논리(옵션 A/B/C 비교, 2단 트리 구조 등)는 테이블명과 무관하게 유효하므로 본문은 검토 기록으로 보존한다.

## 1. 문제 현상

`egov-web2`에 `generateThymeleafLayout` → `buildFullCrudPrompt(domain=Menu)` → `buildFullCrudPrompt(domain=Faq)` 순서로 생성한 뒤, FAQ 화면을 열면 상단 GNB에 "FAQINFO 관리"만 보이고 이전에 생성한 "MENUINFO 관리"는 보이지 않는다.

파일 자체는 삭제되지 않았다. `layout/*.html` 5종 모두 최초 생성 시각 그대로 존재하며 내용도 정상이다. 원인은 삭제가 아니라 **GNB 렌더링 방식**에 있다.

## 2. 현재 구조

### 2.1 `layout/gnb.html`
```html
<li>
    <a th:href="${lnbMenus[0].url}" class="gnb-main-trigger is-link"
       th:text="${lnbTitle}"
       th:classappend="${#strings.startsWith(currentMenuId, 'crud-')} ? 'gnb-active'">업무관리</a>
</li>
<li><a th:href="@{/}" class="gnb-main-trigger is-link">시스템관리</a></li>
<li><a th:href="@{/}" class="gnb-main-trigger is-link">고객지원</a></li>
```
"업무관리" 위치가 **단일 슬롯**이며, 현재 렌더링 중인 화면의 `${lnbTitle}` / `${lnbMenus[0].url}` 값 하나만 표시한다. "시스템관리"/"고객지원"은 실제 링크가 없는 정적 placeholder다.

### 2.2 생성된 Controller
`EgovMenuController.java`, `EgovFaqController.java` 모두 아래 패턴으로 각자 **자기 도메인만** 모델에 주입한다.
```java
private void populateLayoutModel(ModelMap model, String currentMenuId, String currentPageLabel) {
    model.addAttribute("currentMenuId", currentMenuId);
    model.addAttribute("lnbTitle", "FAQINFO 관리");       // 도메인마다 자기 이름만
    model.addAttribute("lnbMenus", List.of(...));         // 도메인마다 자기 하위 링크만
    ...
}
```

### 2.3 COMTNMENUINFO와의 관계
프로젝트에는 이미 `MenuTool`(`getMenuStructure`, `generateMenuInsertSql`)이 있고 `COMTNMENUINFO`(MENU_NO/UPPER_MENU_NO/MENU_NM/MENU_ORDR)가 실제 메뉴 트리를 담는 테이블이다. 하지만:

- `buildFullCrudPrompt`는 화면/Java/Mapper만 생성하며 `COMTNMENUINFO`에 아무것도 등록하지 않는다. 메뉴 등록은 `MenuTool.generateMenuInsertSql()`이 SQL을 반환하면 **사용자가 직접 실행**하는 별도 수동 단계다(CLAUDE.md 전체 워크플로우 Step 6).
- 설령 `COMTNMENUINFO`에 메뉴가 등록되어 있어도, `gnb.html`/`lnb.html`은 **DB를 전혀 조회하지 않는다.** 순수하게 Controller가 넘긴 모델 값만 그린다.

즉 두 체계(생성된 화면의 정적 GNB 슬롯 vs. 실제 메뉴 관리 테이블)가 서로 연결되어 있지 않다.

## 3. 근본 원인

GNB는 설계상 "사이트 전체 메뉴 트리"가 아니라 "현재 보고 있는 화면 하나의 placeholder"다. 도메인을 여러 개 생성해도 GNB에 누적되지 않는 것은 버그가 아니라 **의도된 적 없는 설계 공백**이다 — 애초에 다중 도메인 누적을 지원하도록 만들어진 적이 없다.

## 4. 설계 옵션

### 옵션 A — GNB를 COMTNMENUINFO 기반 동적 렌더링으로 전환 (DB 조회)
- 모든 요청에 공통으로 최상위 메뉴 트리(`UPPER_MENU_NO = 0`)를 조회해 `gnbMenus` 모델 속성으로 주입.
- 주입 지점은 각 Controller가 아니라 **Spring `@ControllerAdvice`(`@ModelAttribute`) 또는 `HandlerInterceptor`** — 도메인 Controller마다 반복 구현하지 않도록 공통화.
- `gnb.html`을 `<li th:each="menu : ${gnbMenus}">`로 변경.
- 장점: 실제 메뉴 등록 상태와 화면이 항상 일치, `MenuTool`과 자연스럽게 연결.
- 단점: (1) 신규 프로젝트는 `COMTNMENUINFO`가 비어있어 GNB가 텅 빌 수 있음 → fallback 필요. (2) 요청마다 DB 조회 — 캐싱 필요. (3) `buildFullCrudPrompt` 실행만으로는 메뉴가 자동 등록되지 않으므로, 화면 생성 후 여전히 `MenuTool.generateMenuInsertSql()` 실행이 필요하다는 안내를 강화해야 함(또는 자동 등록 여부를 별도로 결정).

### 옵션 B — 정적 메뉴 레지스트리 파일 누적
- 프로젝트 내 `menu-registry.json` 같은 파일에 생성된 도메인을 추가/관리하고 `gnb.html`이 이를 읽어 렌더링.
- 장점: DB 연동 불필요, 구현이 단순.
- 단점: `COMTNMENUINFO`(실제 운영 메뉴 테이블)와 별개의 이원화된 메뉴 체계가 생겨 eGovFrame 표준 구조와 어긋남. 운영 단계에서 메뉴 관리 화면(MenuTool 기반)과 실제 노출 메뉴가 불일치할 위험.

### 옵션 C — 하이브리드 (권장)
- **LNB는 현재 방식 유지**: 도메인별 화면(목록/등록/상세/수정) 링크는 지금처럼 Controller가 `lnbTitle`/`lnbMenus`로 주입 — 이건 "현재 보고 있는 업무의 하위 기능 메뉴"라는 역할에 맞는 정상 UX다. 문제 없음.
- **GNB만 옵션 A로 전환**: 사이트 전체 대분류는 `COMTNMENUINFO`의 최상위(`UPPER_MENU_NO=0`) 트리로 항상 전체 노출.

이렇게 하면 "FAQ 화면에서 이전 Menu 메뉴가 사라진다"는 현상이 해소된다 — GNB는 등록된 모든 최상위 업무를 항상 보여주고, LNB만 현재 화면의 세부 기능을 보여준다.

## 5. 옵션 C 적용 시 예상 변경 지점

| 파일/영역 | 변경 내용 |
|---|---|
| `templates/egov/*` (ProjectInitializrTool 골격) 신규 | GNB 메뉴 트리를 조회해 모델에 주입하는 공통 컴포넌트(`@ControllerAdvice` 또는 `HandlerInterceptor`) 추가 — 프로젝트 생성 시점에 미리 심어둬야 도메인 생성마다 반복 구현을 피할 수 있음 |
| `templates/crud/layout/default.html.ftl` 등 `gnb.html` 생성 템플릿 | 정적 `<li>` 3개 → `th:each="menu : ${gnbMenus}"` 동적 렌더링으로 교체 |
| `EgovMenuController.java` 등 생성 Controller 템플릿 | `lnbTitle`/`lnbMenus`/`breadcrumbs` 로직은 유지(LNB용), `gnbMenus` 주입은 공통 컴포넌트로 이관되므로 Controller에서 제거 |
| `MenuTool` / `CrudPromptBuilderTool` 워크플로우 | 화면 생성 후 `COMTNMENUINFO` 등록을 여전히 수동으로 안내할지, 자동 INSERT까지 포함할지 결정 필요 — 자동 실행은 DB 변경을 자동화하는 것이므로 신중한 검토 필요(현재 SqlTool 계열은 "직접 실행 안 함" 원칙을 따름) |
| `COMTNMENUINFO` 데이터 없는 신규 프로젝트 | GNB fallback 처리("홈"만 노출 등) 필요 |

## 6. 결정이 필요한 항목

| 항목 | 옵션 | 권장 |
|---|---|---|
| GNB 데이터 소스 | COMTNMENUINFO 동적 조회(A/C) vs. 정적 레지스트리 파일(B) | COMTNMENUINFO 동적 조회 — 이미 있는 `MenuTool` 체계와 일치 |
| LNB 처리 | 현행 유지 vs. GNB와 동일하게 DB화 | 현행 유지 — 화면별 세부 기능 링크로는 지금 방식이 맞음 |
| 메뉴 자동 등록 | `buildFullCrudPrompt`가 COMTNMENUINFO에 자동 INSERT vs. 계속 `MenuTool` 수동 실행 안내 | 수동 실행 유지 — DB 쓰기 자동화는 별도 승인 필요한 리스크 |
| 신규 프로젝트(메뉴 없음) 처리 | GNB 비움 vs. "홈"만 노출 | "홈"만 노출(fallback) |
| 공통 GNB 주입 위치 | 매 Controller 템플릿에 반복 삽입 vs. 프로젝트 골격에 공통 컴포넌트 1개 배치 | 공통 컴포넌트 1개 — 반복 방지 |

## 7. 결론

"메뉴가 없어졌다"는 현상은 파일 삭제가 아니라, GNB가 애초에 다중 도메인을 누적 표시하도록 설계된 적이 없어서 발생하는 정상 동작이다. 근본적으로 해결하려면 GNB를 `COMTNMENUINFO` 기반 동적 렌더링(옵션 C)으로 전환해야 하며, 이는 `ProjectInitializrTool`(공통 컴포넌트 골격), `CrudTemplateRenderer`/`gnb.html` 템플릿, 생성 Controller 템플릿 3곳에 걸친 변경이 필요하다.

## 8. 처리 위치 검토: `ThymeleafLayoutTool` vs `CrudPromptBuilderTool`

### 8.1 현재 코드 확인 결과

`layout/gnb.html` 출력 파일을 만드는 경로가 실제로는 **하나가 아니라 두 개**다.

| 경로 | 트리거 | 사용 템플릿 | 실행 빈도 |
|---|---|---|---|
| `ThymeleafLayoutTool.generateThymeleafLayout()` | 명시적 단독 호출 | `templates/crud/layout/gnb.html.ftl` (`crudTemplateRenderer`) | 프로젝트당 1회(기본 `overwriteLayout=false`로 재실행 시 보존) |
| `CrudPromptBuilderTool.build*(layoutMode="create")` | `buildFullCrudPrompt`/`buildBoardFeature`/`buildMasterDetailPrompt` | 각각 `templates/crud\|board\|masterdetail/layout/gnb.html.ftl` (`crudTemplateRenderer`/`boardTemplateRenderer`/`masterDetailTemplateRenderer`) | 도메인 생성마다(테이블 수만큼 N회) |

세 템플릿(`crud/board/masterdetail`)의 `gnb.html.ftl`을 비교하면 "업무관리"/"소식·뉴스" 같은 라벨 텍스트 1줄만 다르고 나머지는 동일한 근접 중복 파일이다. 즉 GNB HTML은 이미 **소유권이 한 곳에 있지 않다.**

### 8.2 두 Tool의 책임 성격 차이

| | `ThymeleafLayoutTool` | `CrudPromptBuilderTool` |
|---|---|---|
| 실행 단위 | 프로젝트당 1회 | 도메인(테이블)당 1회, N개 도메인 생성 시 N회 반복 |
| 기존 안전장치 | `overwriteLayout=false` 기본값 — 재실행해도 기존 파일 보존 | 없음 — 매 호출이 곧 파일 쓰기 |
| `layoutMode=reuse`(기본값)에서의 역할 | layout 소유자 | layout 파일 **비접근** (재사용만) |
| `layoutMode=create`에서의 역할 | 관여 없음 | layout 레이어까지 직접 렌더링(현재 유일하게 중복 발생 지점) |

### 8.3 판단

**`ThymeleafLayoutTool`에서 처리하는 것을 권장한다.**

이유:
1. **실행 빈도 불일치**: GNB DB 조회 컴포넌트(예: `@ControllerAdvice`/`HandlerInterceptor` Java 파일)는 프로젝트에 정확히 1개만 있어야 하는 인프라 성격의 산출물이다. `CrudPromptBuilderTool`은 도메인마다 반복 호출되므로, 거기서 만들면 두 번째 도메인부터 동일 파일을 또 만들려다 충돌하거나 덮어쓰는 문제가 생긴다 — 애초에 `layoutMode=reuse` 기본값을 도입한 이유(도메인 생성마다 layout이 초기화되는 문제)와 정확히 같은 종류의 위험이다.
2. **기존 안전장치와의 정합성**: `overwriteLayout=false` 기본값이 정확히 "인프라 파일은 한 번만 만들고 이후엔 보존" 시맨틱이다. 새 Java 컴포넌트도 같은 시맨틱이 필요하므로 이미 있는 장치를 재사용할 수 있는 `ThymeleafLayoutTool` 쪽이 자연스럽다.
3. **`layoutMode=reuse`가 이미 확립한 경계**: 기본 워크플로우(`generateThymeleafLayout` → `build*`)에서 `CrudPromptBuilderTool`은 layout 관련 파일에 전혀 손대지 않는다. GNB 동적 렌더링도 "layout 인프라"이므로 이 경계를 유지하는 편이 일관적이다.

### 8.4 단, 선결 조건 — `layoutMode=create` 경로의 3중 중복 정리

`ThymeleafLayoutTool`에만 넣더라도, `layoutMode=create`가 여전히 `board/masterdetail/crud` 3개의 독립된 `gnb.html.ftl`을 통해 **자기 힘으로** layout을 만들 수 있는 한, 이 경로로 생성된 프로젝트는 GNB가 동적 렌더링으로 바뀌지 않는 사각지대로 남는다.

따라서 이번 기능을 넣으려면:
1. `ThymeleafLayoutTool` 쪽에 GNB DB 조회 Java 컴포넌트 생성 로직을 추가하고,
2. `crud/board/masterdetail` 3개의 `gnb.html.ftl`을 동일한 `${gnbMenus}` 기반 동적 템플릿으로 통일하며(라벨 1줄 차이는 파라미터화),
3. 가능하면 `layoutMode=create` 경로가 `crud/layout/*.ftl`을 **공용으로 재사용**하도록 `BoardTemplateRenderer`/`MasterDetailTemplateRenderer`의 `LAYER_TEMPLATE_MAP`을 정리해 3중 중복 자체를 없애는 것이 근본적으로 더 안전하다.

3번은 이번 기능과 별개로도 가치 있는 정리이지만 범위가 커서, 최소 범위로 가려면 1·2만 하고 3은 후속 과제로 미루는 것도 가능하다.

## 9. 처리 위치 재검토: `ProjectInitializrTool` vs `ThymeleafLayoutTool`

CSS/JS(`styles.css`, `_ds_bundle.css`, `krds.min.js`)는 `ThymeleafLayoutTool`이 아니라 `ProjectInitializrTool`이 만든다는 기존 전례가 있다. GNB 메뉴 컴포넌트도 "프로젝트 전역 인프라"라는 점에서 CSS와 비슷해 보이므로, 8절의 결론(`ThymeleafLayoutTool`)이 이 전례와 상충하지 않는지 재검토했다.

### 9.1 CSS와의 유사점·차이점

| | CSS/JS (`ProjectInitializrTool`) | GNB 메뉴 컴포넌트 (검토 대상) |
|---|---|---|
| 소비 주체 | JSP 화면과 Thymeleaf 화면 **모두** 동일하게 `/resources/**` URL로 참조 | Thymeleaf `gnb.html`의 `th:each="menu : ${gnbMenus}"` **에서만** 소비됨 — JSP 경로에는 대응하는 공통 레이아웃/데코레이터 개념 자체가 없음 |
| viewType 의존성 | 없음 (view 기술과 무관하게 항상 필요) | 있음 (Thymeleaf Layout Dialect 구조에 결합) — JSP만 쓰는 프로젝트에는 불필요한 산출물이 됨 |
| 생성 시점 | 프로젝트 생성 시 1회, 이후 도메인 생성과 무관 | 마찬가지로 1회성이 맞지만, Thymeleaf를 아예 안 쓸 수도 있는 프로젝트에도 무조건 심는 것은 CSS와 성격이 다름 |

즉 "프로젝트당 1회"라는 성격은 같지만, CSS는 view 기술과 무관한 반면 GNB 메뉴 컴포넌트는 Thymeleaf Layout Dialect 구조(`gnb.html`)에 종속적이다. 이 차이가 8절의 결론(`ThymeleafLayoutTool`)을 그대로 유지시킨다 — **위치 판단 기준은 "1회성이냐"가 아니라 "어떤 view 기술에 종속적이냐"다.**

### 9.2 새로 발견한 기술적 제약 — `servlet-context.xml`의 component-scan 필터

`ProjectInitializrTool`이 만드는 `servlet-context.xml`(`DispatcherServletBuilder.java`)은 다음과 같이 **`@Controller` 애노테이션만** 스캔하도록 명시적으로 제한되어 있다.

```xml
<context:component-scan base-package="${scanBasePackage}" use-default-filters="false">
    <context:include-filter type="annotation"
        expression="org.springframework.stereotype.Controller"/>
</context:component-scan>
```

`@ControllerAdvice`는 `@Controller`가 아니라 `@Component`로 메타 애노테이션되어 있으므로, 이 필터로는 자동 스캔되지 않는다. 즉 GNB 메뉴 컴포넌트를 **어느 Tool에 구현하든** `servlet-context.xml`의 component-scan 필터를 넓히거나(`ControllerAdvice` include-filter 추가), `<mvc:interceptors>`에 `HandlerInterceptor`로 명시 등록하는 방식으로 바꿔야 한다. 이 설정 파일은 `ProjectInitializrTool` 전속 소유물이다.

### 9.3 결론 — 두 Tool이 서로 다른 부분을 나눠 맡아야 한다

| 산출물 | 담당 Tool | 이유 |
|---|---|---|
| GNB 메뉴 컴포넌트 `.java` 소스 파일 + `gnb.html` 소비 로직 | `ThymeleafLayoutTool` | Thymeleaf 전용, 1회성 + `overwriteLayout` 보존 시맨틱과 일치 (8절) |
| `servlet-context.xml`의 component-scan 필터 확장 또는 `<mvc:interceptors>` 등록 | `ProjectInitializrTool` | 이 파일의 유일한 생성 주체이므로 다른 선택지가 없음 |

8절의 결론은 유지되지만, 단독으로 `ThymeleafLayoutTool`만 고쳐서는 기능이 동작하지 않는다 — `ProjectInitializrTool`이 만드는 `servlet-context.xml`도 함께 바뀌어야 하는 **교차 Tool 의존**이 존재한다는 점을 명시적으로 남긴다.

### 9.4 기존 프로젝트 마이그레이션 주의

`ProjectInitializrTool.initializeProject()`는 `ThymeleafLayoutTool`의 `overwriteLayout`과 달리 **기존 파일 보존 로직이 전혀 없다**(`FilePlanExecutor`에 존재 여부 체크 없음). 따라서:

- 신규 프로젝트는 이번 변경이 반영된 `initializeProject()`를 한 번만 실행하면 `servlet-context.xml`에 필터/인터셉터 등록이 자동 반영된다.
- `egov-web2`처럼 **이미 생성된 기존 프로젝트**는 `initializeProject()`를 재실행하면 사용자가 직접 수정했을 수 있는 `pom.xml`/`web.xml` 등 다른 파일까지 통째로 덮어써 위험하다. 기존 프로젝트에는 이번 세션에서 `MainController.java`를 수동 반영했던 것과 동일하게, `servlet-context.xml`의 해당 부분만 **수동 패치**로 반영하는 것이 안전하다.

## 10. 역방향 검증 — CSS를 `ProjectInitializrTool`에서 `ThymeleafLayoutTool`로 옮긴다면?

9절 결론(판단 기준은 "1회성"이 아니라 "view 기술 종속성")을 반대 방향으로도 검증했다. 현재 `ProjectInitializrTool`이 담당하는 `styles.css`/`_ds_bundle.css`/`krds.min.js`를 `ThymeleafLayoutTool`로 옮긴다고 가정하면 아래 문제가 코드로 확인된다.

1. **JSP 전용 프로젝트가 CSS를 못 받는다.** `CrudPromptBuilderTool`의 `viewType` 기본값은 `"jsp"`이고, JSP 화면도 `initializeProject()`가 만든 `/resources/css/styles.css`를 그대로 참조한다(Tool description에 명시). `ThymeleafLayoutTool`은 Thymeleaf 전용 워크플로우에서만 호출되는 **선택적** Tool이므로, CSS가 여기로 옮겨가면 JSP만 쓰는 프로젝트(기본 경로)는 `generateThymeleafLayout()`을 부를 이유가 없어 CSS 자체가 아예 생성되지 않는다 — 모든 JSP 화면이 스타일 깨진 상태가 된다.

2. **`ProjectValidator`의 필수 파일 검증이 깨진다.** `ProjectValidator.java` 32~50번째 줄에서 `styles.css`(WAR: `src/main/webapp/resources/css/styles.css`, BOOT: `src/main/resources/static/resources/css/styles.css`)를 `initializeProject()` 자체의 **필수 파일**로 검증하고 있다. CSS 생성 주체를 옮기면 `initializeProject()`를 정상 실행해도 이 검증이 항상 "필수 파일 누락" 경고를 내게 되어, Tool 자체의 성공 판정 로직을 다시 짜야 한다.

3. **WAR/Boot 경로 분기 정보가 `ThymeleafLayoutTool`에 없다.** CSS 출력 경로는 WAR(`webapp/resources/css/`)와 Boot(`static/resources/css/`)가 다르며, 이 분기는 `ProjectSpec.boot()` 기반으로 `FilePlanFactory`가 처리한다. 반면 `ThymeleafLayoutTool.generateThymeleafLayout(outputPath, layoutBasePath, overwriteLayout)`은 `projectType`(WAR/Boot)을 아예 모른다. CSS를 옮기려면 이 Tool에 없던 파라미터를 새로 추가해야 하는데, Thymeleaf 템플릿 출력 경로(`src/main/resources/templates/...`)는 WAR/Boot 구분이 필요 없어서 지금까지 이 정보가 필요 없었다 — CSS 하나 때문에 API 표면이 늘어난다.

4. **`overwriteLayout` 플래그의 의미가 애매해진다.** 지금 `overwriteLayout`은 "layout HTML 5종"만 가리킨다. CSS까지 같은 플래그를 쓰면, 사용자가 layout HTML만 최신화하려고 `overwriteLayout=true`를 켰다가 손으로 커스터마이징한 CSS까지 통째로 날아갈 위험이 생긴다. 반대로 별도 플래그(`overwriteCss` 등)를 추가하면 파라미터만 늘어난다.

5. **Tool 설명과 실제 산출물이 어긋난다.** `ThymeleafLayoutTool`의 `@Tool(description=...)`은 "Thymeleaf 공통 layout 파일 5종을 생성합니다"로 명시되어 있다(CLAUDE.md는 "Claude가 이 설명으로 tool을 선택함"이라고 강조). CSS/JS는 Thymeleaf 전용 자산이 아닌데 여기 섞이면, LLM이 이 Tool의 역할을 오판하거나(예: "CSS만 바꿔달라"는 요청에 layout 5종까지 재생성 시도) 설명과 실제 동작이 어긋난다.

6. **`layoutBasePath`의 반복 호출 모델과 충돌한다.** `generateThymeleafLayout`은 `layoutBasePath="layout/admin"`처럼 같은 프로젝트에 여러 layout 세트를 만들 수 있도록 설계되어 있다(반복 가능한 리소스). CSS는 프로젝트에 1개만 있어야 하는 단일 리소스라, 같은 Tool에 넣으면 "관리자용 layout을 추가로 만들 때 CSS도 또 만들 것인가"라는 불필요한 모델링 충돌이 생긴다.

7. **기존 테스트 자산이 전부 재작성 대상이 된다.** `ProjectInitializrWar50ManualWorkflowTest` 등 `FilePlanFactory.warFiles()/bootFiles()`가 CSS를 포함한다고 가정한 테스트가 이미 존재한다. CSS 생성 주체를 옮기면 이 테스트들의 기대값을 전부 다시 써야 한다.

**결론**: CSS를 `ThymeleafLayoutTool`로 옮기면 (1) JSP 프로젝트 파손, (2) 자체 필수 파일 검증 파손, (3) 없던 WAR/Boot 파라미터 신설, (4) 플래그 의미 충돌, (5) Tool 설명-동작 불일치, (6) 반복 가능 리소스 모델과의 충돌, (7) 기존 테스트 파손까지 7가지 구체적 문제가 발생한다. 이는 9절의 판단 기준("view 기술 종속성이 있는 산출물만 `ThymeleafLayoutTool`로")이 정확히 CSS가 `ProjectInitializrTool`에 남아야 하는 이유이자, GNB 메뉴 컴포넌트가 반대로 `ThymeleafLayoutTool`에 있어야 하는 이유와 대칭된다는 것을 역방향으로 재확인해 준다.

이 문서는 분석 및 설계 검토 결과이며, 구현은 진행하지 않았다. 위 "결정이 필요한 항목"에 대한 방향이 확정되면 별도 구현 계획을 세워 승인 후 진행한다.