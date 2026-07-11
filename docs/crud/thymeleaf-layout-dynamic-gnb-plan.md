# Thymeleaf GNB 동적 렌더링 구현 계획

이 문서는 `thymeleaf-layout-dynamic-gnb-design.md`(설계 검토, 1~10절)의 후속 실행 계획이다. 설계 검토에서 확정된 결론(`ThymeleafLayoutTool`이 GNB 컴포넌트 소유)과, 이번 문서 6.2에서 확정한 등록 방식(`servlet-context.xml` **최초 생성은 `ProjectInitializrTool`, GNB 인터셉터 등록 patch는 `ThymeleafLayoutTool`이 수행**)을 실제 변경 파일·순서로 구체화한다.

**진행 상황**: Phase 1·2·3·4·5 구현 완료(전체 테스트 통과). Phase 6만 남음. 구현 중 계획과 달라진 세부사항은 각 절에 "[Phase N 구현 완료, 계획 대비 변경]" 표시와 함께 갱신했다.

## 1. 목표

`egov-web2`에서 재현된 문제 — 도메인 화면을 여러 개 생성하면 GNB에 마지막으로 본 도메인 이름만 남고 이전 도메인이 안 보이는 현상 — 을 근본적으로 해결한다. GNB를 `COMTNMENUINFO`(트리) + `COMTNPROGRMLIST`(URL) 조인 기반으로 매 요청마다 동적 렌더링하도록 바꾼다. LNB는 현행(화면별 로컬 메뉴) 유지.

## 2. 확정 전제 (설계 검토 6절의 미결정 항목에 대한 이번 계획의 기본값)

| 항목 | 이번 계획의 기본값 | 비고 |
|---|---|---|
| GNB 데이터 소스 | `COMTNMENUINFO` 동적 조회 | 이번 작업의 목표 자체 |
| LNB 처리 | **1차는 현행 유지**(Controller가 화면별 목록/등록 액션 링크를 직접 주입) | 동적화 검토 결과는 10절 참고 — 기술적으로 가능하나 별도 전제조건 필요해 1차 범위에서 제외 |
| 메뉴 자동 등록 | **수동 유지** — `buildFullCrudPrompt` 등이 `COMTNMENUINFO`에 자동 INSERT하지 않음 | DB 쓰기 자동화는 범위 밖. `MenuTool.generateMenuInsertSql()` 안내 유지. **단, 수동 등록 시 anchor 규칙은 10절 참고** |
| 신규/빈 메뉴 프로젝트 처리 | `gnb.html`이 "홈"을 **항상 정적으로 1개** 렌더링하고, `gnbMenus`가 비어 있으면 그 외에는 아무것도 안 그림 | "홈"을 인터셉터 fallback 리스트에도 넣으면 중복 렌더링됨 — 5.4 참고 |
| 공통 컴포넌트 생성 위치 | `ThymeleafLayoutTool` | 설계 검토 8~9절 결론 |
| `layoutMode=create` 3중 중복(`crud`/`board`/`masterdetail` `gnb.html.ftl`) | **1단계에서 모두 동일하게 동적 템플릿으로 맞춘다.** 공용 템플릿으로 통합하는 리팩터링(설계 검토 8.4의 3번)은 후속 과제로 미룬다 | 최소 범위로 우선 기능 완성 |
| WAR vs Boot 지원 범위 | **1차는 WAR만 지원.** Boot는 `servlet-context.xml` 자체가 없어 인터셉터 등록 메커니즘이 XML patch가 아니라 `WebMvcConfigurer` Java 설정이라 별도 작업이 필요 | 6.2 참고. GNB 컴포넌트 파일은 Boot에서도 생성되지만 미등록 상태로 남음 — Tool 응답에 명시 필요 |

## 3. 최종 아키텍처

```
브라우저 요청
    │
    ▼
[신규] EgovGnbMenuInterceptor (HandlerInterceptor, postHandle) [Phase 1~2 구현 완료]
    │  1) modelAndView == null / !hasView() / redirect view / skip 경로면 즉시 skip (5.4 참고)
    │  2) COMTNMENUINFO(UPPER_MENU_NO=0) + COMTNPROGRMLIST JOIN 조회 (실패/빈 결과 시 List.of())
    │     → modelAndView.addObject("gnbMenus", List<GnbMenuVO>)
    │  3) request.getServletPath()(컨텍스트 경로 제외)와 각 menu.url을 비교해 현재 선택된 최상위 메뉴 계산
    │     → modelAndView.addObject("currentTopMenuNo", Long) (일치하는 메뉴 없으면 null)
    ▼
gnb.html (Thymeleaf)  ── th:each="menu : ${gnbMenus}" ──▶ 상단 메뉴 렌더링
    │
lnb.html / breadcrumb.html  ── 기존처럼 Controller가 lnbTitle/lnbMenus/breadcrumbs 주입 (변경 없음)
```

조회 SQL 뼈대(`MenuRepository.java`의 `SELECT MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR FROM COMTNMENUINFO WHERE UPPER_MENU_NO = 0 ... ORDER BY MENU_ORDR` 패턴 재사용, `COMTNPROGRMLIST` JOIN 추가):
```sql
SELECT m.MENU_NO, m.MENU_NM, m.MENU_ORDR, p.URL
FROM COMTNMENUINFO m
LEFT JOIN COMTNPROGRMLIST p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM
WHERE m.UPPER_MENU_NO = 0
  AND p.URL IS NOT NULL
ORDER BY m.MENU_ORDR
```
(`p.URL IS NOT NULL` 조건의 배경은 5.4의 "null URL 제외" 방어 정책 참고)

**URL 비교 시 `getServletPath()` 사용 [Phase 1~2 구현 완료]**: 현재 선택된 메뉴 판정은 `request.getRequestURI()`(컨텍스트 경로 포함)가 아니라 `request.getServletPath()`(컨텍스트 경로 제외, 예: `/emp/list.do`)를 쓴다. WAR가 `/egov-web2` 같은 비루트 컨텍스트 경로 아래 배포되면 `getRequestURI()`는 `/egov-web2/emp/list.do`를 반환해 `COMTNPROGRMLIST.URL`(`/emp/list.do`, 컨텍스트 상대 경로)과 항상 어긋난다. `getServletPath()`를 쓰면 배포 컨텍스트 경로와 무관하게 항상 정확히 매칭된다.

## 4. 대상 프로젝트(egov-web2 등)에 새로 생성될 파일

프로젝트는 MyBatis를 쓰므로(CLAUDE.md 전역 규칙: "인라인 SQL 금지", "MyBatis #{} 사용"), 대상 프로젝트에 심는 조회 컴포넌트도 MyBatis 기반으로 작성한다. 패키지는 기존 `MainController`와 동일하게 `{packageName}.cmm.*`(공통 영역)에 둔다.

**패키지 경로 정책(중요, 5.2와 반드시 일치해야 함)**: `FilePlanFactory.mainController()`(`ProjectInitializrTool`)는 `s.packageName()+".cmm.web"`처럼 **사용자가 실제로 입력한 packageName을 그대로**(예: `kr.go.sample.emp`든 `egovframework.let.emp`든) 파일 경로 계산에 쓴다 — `"egovframework.let."` 접두사가 있다고 가정하지 않는다. GNB 컴포넌트도 이 방식을 그대로 따른다. 반면 `CrudLayerDefinition.COMMON_LAYERS`(기존 CRUD VO/Mapper/Service 등)는 `"src/main/java/egovframework/let/{PKG}/..."`처럼 `"egovframework.let."` 접두사가 하드코딩되어 있어 **다른 패키지를 쓰면 이미 어긋나는 기존 제약**이다(이번에 새로 생긴 문제가 아니라 기존 CRUD 생성 시스템 전체의 기존 제약). GNB 컴포넌트는 이 기존 CRUD 레이어의 제약을 물려받지 않고, `MainController`처럼 **모든 packageName을 올바르게 지원**하도록 5.2에서 경로 계산을 다시 정리한다.

| 파일 | 위치 | 역할 |
|---|---|---|
| `GnbMenuVO.java` | `src/main/java/{pkgPath}/cmm/vo/` | MENU_NO/MENU_NM/MENU_ORDR/URL 보유 |
| `GnbMenuMapper.java` | `src/main/java/{pkgPath}/cmm/service/` | MyBatis Mapper 인터페이스 |
| `GnbMenuMapper.xml` | `src/main/resources/egovframework/mapper/cmm/` | 3절의 SELECT 쿼리 |
| `EgovGnbMenuInterceptor.java` | `src/main/java/{pkgPath}/cmm/web/` | `HandlerInterceptor.postHandle()`에서 조회 후 `gnbMenus`(빈 결과/조회 실패 시 `List.of()`) + `currentTopMenuNo` 모델 주입. 정적 리소스·리다이렉트·`modelAndView==null` 요청은 조회 자체를 skip(5.4) |

`{pkgPath}` = `ThymeleafLayoutTool.generateThymeleafLayout()`에 새로 추가되는 `packageName` 파라미터(아래 5.1) 기준.

## 5. springai(이 저장소) 측 변경

### 5.1 `ThymeleafLayoutTool.java` — 파라미터 추가

```java
public String generateThymeleafLayout(
        String outputPath,
        @Nullable String layoutBasePath,
        @Nullable Boolean overwriteLayout,
        @Nullable String packageName)   // 신규
```
- `packageName`은 시그니처상 `@Nullable`(nullable)로 두지만, **사실상 필수 파라미터로 취급한다.** 미입력 시 기본값 `"egovframework.let.sample"`(`ProjectInitializrService.getConfigTemplate()`과 동일한 관례)을 적용하되 — 이 기본값은 실제 프로젝트의 packageName과 다를 위험이 크다(사용자가 `initializeProject(packageName="egovframework.let.emp")`로 만든 프로젝트에서 `packageName`을 빼먹고 `generateThymeleafLayout`을 부르면, `EgovGnbMenuInterceptor`가 `egovframework.let.sample.cmm.web`에 생성되어 실제 CRUD 패키지(`egovframework.let.emp.*`)와 어긋난다). 따라서:
  1. `@Tool(description=...)`에 "이 프로젝트의 `initializeProject()`에 전달했던 packageName과 **반드시 동일한 값**을 넘겨야 한다"고 강하게 명시한다(단순 파라미터 설명이 아니라 경고 수준 문구).
  2. `packageName`을 입력하지 않아 기본값이 적용된 경우, `generateThymeleafLayout()` 응답 맨 앞에 `"⚠ packageName 미지정 — 기본값 'egovframework.let.sample' 사용. 실제 프로젝트 packageName과 다르면 GNB 컴포넌트가 컴파일되지 않거나 등록되지 않습니다."` 경고 문구를 항상 반환한다(조용히 넘어가지 않는다).
- 기존 3개 파라미터만 쓰던 호출부(`docs/tool-reference/ThymeleafLayoutTool_기능및역할_상세설명.md`의 예시문 등)도 갱신 필요.

### 5.2 `CrudLayerDefinition.java` — 신규 레이어 4종 추가 [Phase 1 구현 완료]

```java
public static final String LAYOUT_GNB_MENU_VO       = "layoutGnbMenuVo";
public static final String LAYOUT_GNB_MENU_MAPPER    = "layoutGnbMenuMapper";
public static final String LAYOUT_GNB_MENU_MAPPER_XML= "layoutGnbMenuMapperXml";
public static final String LAYOUT_GNB_MENU_INTERCEPTOR = "layoutGnbMenuInterceptor";
```

**⚠ 구현 중 발견해 계획을 수정한 지점**: 최초 계획은 이 4개 키를 `LAYOUT_LAYER_KEYS`/`THYMELEAF_LAYERS`에 합쳐 `thymeleafLayoutLayers()`가 "HTML 5종 + 신규 4종 = 9종"을 함께 순회하도록 하려 했다. 실제로 넣어보니 `CrudOrchestrationService`(및 Board/MasterDetail 동급 서비스)의 `layoutMode=create` 경로가 **도메인 생성마다** `forViewType()` → `THYMELEAF_LAYERS`를 순회하며 `isLayoutLayer()`인 항목을 함께 렌더링한다는 걸 확인했다. GNB 컴포넌트를 여기 섞으면:
- 도메인을 만들 때마다(N개 도메인 생성 시 N번) 프로젝트 전역 공용 파일(인터셉터 등)을 또 만들려고 시도해 충돌하고,
- 그 경로가 쓰는 `renderByLayerKey(layerKey, model, ...)`(도메인별 `CrudTemplateModel` 기반 렌더러)는애초에 GNB 레이어의 `packageName`(프로젝트 공용) 기반 렌더링을 처리할 수 없어 예외가 난다.

그래서 **`LAYOUT_LAYER_KEYS`/`THYMELEAF_LAYERS`에는 추가하지 않고**, 완전히 별도의 상수 리스트로 분리했다:

```java
public static final List<CrudLayerDefinition> GNB_MENU_COMPONENT_LAYERS = List.of(
    new CrudLayerDefinition(LAYOUT_GNB_MENU_VO,          "GnbMenuVO.java",              "src/main/java/{PKG}/cmm/vo/"),
    new CrudLayerDefinition(LAYOUT_GNB_MENU_MAPPER,      "GnbMenuMapper.java",          "src/main/java/{PKG}/cmm/service/"),
    new CrudLayerDefinition(LAYOUT_GNB_MENU_MAPPER_XML,  "GnbMenuMapper.xml",           "src/main/resources/egovframework/mapper/cmm/"),
    new CrudLayerDefinition(LAYOUT_GNB_MENU_INTERCEPTOR, "EgovGnbMenuInterceptor.java", "src/main/java/{PKG}/cmm/web/")
);
```
`ThymeleafLayoutTool`만 이 리스트를 순회하며, `forViewType()`/`layoutMode=create` 경로에는 절대 노출되지 않는다.

- **경로 계산 방식(4절의 충돌 해소, 그대로 확정)**: 별도 경로 계산 코드를 `ThymeleafLayoutTool`에 새로 만들지 않고, 기존 `resolveSubPath(pkgSub, domainLc)`(단순 문자열 치환) 메서드를 **그대로** 재사용한다. `{PKG}` 플레이스홀더 이름은 기존과 같지만, **이 4개 레이어를 렌더링할 때만 `resolveSubPath()`에 넘기는 `pkgSub` 값을 다르게 계산한다**: `pkgSub = packageName.replace(".", "/")`(전체 packageName을 그대로 슬래시 치환, `"egovframework.let."` 제거 없음). 기존 `COMMON_LAYERS`용 `pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/")`(접두사 제거 후 치환)와는 **의도적으로 다른 계산식**이며, `FilePlanFactory.mainController()`가 `s.packageName()+".cmm.web"`으로 이미 하고 있는 것과 같은 방식이다.
- 이 방식으로 `packageName="egovframework.let.emp"`든 `packageName="kr.go.sample.emp"`든 동일하게 올바른 경로가 나온다 — **1차 구현에서 `"egovframework.let."` 접두사로 패키지를 제한하지 않는다.**
- `GnbMenuMapper.xml`(XML 1종)은 `{PKG}`/`{DOMAIN_LC}`가 필요 없는 고정 경로라서 플레이스홀더 없이 그대로 둔다.
- `resolveFileName()`은 건드리지 않았다 — `GNB_MENU_COMPONENT_LAYERS`의 `fileNameSuffix()`가 이미 완전한 파일명(`"GnbMenuVO.java"` 등)을 담고 있어서, `ThymeleafLayoutTool`이 `layer.fileNameSuffix()`를 그대로 쓰면 되고 `resolveFileName()`의 도메인 기반 분기 로직을 거칠 필요가 없다.

### 5.3 신규 FreeMarker 템플릿 4개 [Phase 1 구현 완료]

`templates/crud/layout/` 아래:
- `gnb-menu-vo.java.ftl` — `menuNo(Long)`/`menuNm(String)`/`menuOrdr(Integer)`/`url(String)` 4필드, `@Getter @Setter`.
- `gnb-menu-mapper.java.ftl` — 기존 `crud/mapper.java.ftl`과 동일한 관례로 `org.apache.ibatis.annotations.Mapper`의 `@Mapper`(이름 없는 기본형)를 인터페이스에 붙인다. 파라미터가 `Long upperMenuNo` 단일값이라 `@Param("upperMenuNo")`를 명시해 MyBatis 바인딩을 XML의 `#{upperMenuNo}`와 명확히 연결한다(도메인 VO를 통째로 넘기는 기존 Mapper들과 다른 유일한 파라미터 스타일).
- `gnb-menu-mapper.xml.ftl` — 3절 SQL 그대로, `resultMap`은 `<id>`를 맨 앞에(PK 우선 순서, 기존 `crud/mapper.xml.ftl` 버그 수정과 동일한 원칙).
- `gnb-menu-interceptor.java.ftl` — **[1차 구현 제약] `jakarta.servlet.*`만 지원(eGovFrame 5.0 전용)**. `egovVersion` 파라미터를 `generateThymeleafLayout`에 추가하는 건 계획에 없던 범위 확장이라 1차에서는 하지 않았고, `javax.servlet`(eGovFrame 4.3) 지원은 후속 과제로 남긴다. `@Tool(description=...)`에 이 제약을 명시했다.

### 5.4 `gnb.html.ftl` 3종 동적 렌더링으로 교체 [Phase 3 구현 완료]

`crud/layout/gnb.html.ftl`, `board/layout/gnb.html.ftl`, `masterdetail/layout/gnb.html.ftl` 각각 계획한 패턴 그대로 교체했다(편차 없음). 실제로 바뀐 부분은 3파일 모두 아래 `<li>` 블록(기존 "업무관리"/"소식·뉴스" 단일 슬롯 + `시스템관리`/`고객지원` 정적 placeholder 2개, 총 3개 `<li>`)뿐이었고, 그 외 배너/헤더/로고 마크업은 3파일이 이미 동일했다.

**구현 메모(계획에 없던 세부사항)**: 이 파일들은 FreeMarker가 먼저 렌더링한 뒤 그 결과를 Thymeleaf가 처리하는 2단 구조라, 템플릿 안의 모든 Thymeleaf `${...}` 표현식은 FreeMarker가 자기 것으로 오인하지 않도록 `${r"..."}"`(raw 문자열) 로 감싸야 한다(기존 5종 HTML 레이어도 전부 이 패턴을 쓰고 있었음 — 새로 발명한 게 아니라 기존 관례를 그대로 따름). 예: `th:each="menu : ${gnbMenus}"`는 실제 파일에 `th:each="menu : ${r"${gnbMenus}"}"`로 작성된다. **`CrudTemplateRendererTest`/`BoardTemplateRendererTest`/`MasterDetailTemplateRendererTest`에 실제 FreeMarker `Configuration` 빈으로 렌더링해 최종 출력에 이스케이프가 깨지지 않고 정확한 Thymeleaf 문법(`th:each="menu : ${gnbMenus}"` 등, raw 형태)이 나오는지 검증하는 테스트를 추가했다** — 이는 8절 "FreeMarker 생성 결과 검증"을 세 렌더러 모두에 대해 실제로 수행한 것이다.

교체된 최종 패턴(3파일 동일):

```html
<ul class="gnb-menu" style="justify-content:flex-end;">
    <li>
        <a th:href="@{/}" class="gnb-main-trigger is-link"
           th:classappend="${currentMenuId == 'home'} ? 'gnb-active'">홈</a>
    </li>
    <li th:each="menu : ${gnbMenus}" th:if="${menu.url != null}">
        <a th:href="@{${menu.url}}" class="gnb-main-trigger is-link"
           th:text="${menu.menuNm}"
           th:classappend="${menu.menuNo == currentTopMenuNo} ? 'gnb-active'"></a>
    </li>
</ul>
```
- "홈"은 `gnbMenus`와 무관하게 **템플릿에 항상 정적으로 1개** 둔다. 인터셉터 fallback 리스트에는 "홈"을 넣지 않는다(조회 실패/빈 결과 시 `gnbMenus = List.of()`) — 그래야 신규/빈 메뉴 프로젝트에서 "홈"이 중복 렌더링되지 않는다.
- `currentTopMenuNo`는 `gnb.html`이 계산하지 않는다. `EgovGnbMenuInterceptor`가 요청 URI와 각 `menu.url`을 비교해 계산한 값을 모델에 실어 보낸다(3절 참고).
- **`null` URL 제외는 데이터 단계와 템플릿 단계 양쪽에서 방어한다(둘 다 필요, 어느 한쪽만으로 대체하지 않는다)**:
  1. **1차 방어(데이터)**: `GnbMenuMapper.xml`의 SELECT에 `AND p.URL IS NOT NULL` 조건을 추가해, `COMTNPROGRMLIST` JOIN 실패(`PROGRM_FILE_NM` 매핑 누락 등)로 URL이 없는 메뉴는 애초에 `gnbMenus`에 담기지 않도록 한다.
  2. **2차 방어(템플릿)**: 그럼에도 `th:if="${menu.url != null}"`는 템플릿에 남겨둔다 — SQL 필터를 우회하는 다른 호출 경로(예: 향후 캐싱 계층에서 stale 데이터가 섞이는 경우)에 대한 방어선이다.
  - `/`나 `javascript:void(0)` 같은 대체 링크는 만들지 않는다 — 조건을 만족 못 하면 그 메뉴 항목 자체를 렌더링하지 않는다.
- **URL 표기 규칙을 확정한다**: `COMTNPROGRMLIST.URL`은 `MenuTool.generateMenuInsertSql()`이 항상 `urlPrefix + "/" + progrmFileNm + ".do"` 형태(선행 `/` 포함, 컨텍스트 상대 경로)로 생성하므로, 값 자체는 이미 `/emp/list.do`처럼 `/`로 시작한다고 가정한다. 다만 링크는 `th:href="${menu.url}"`(원본 그대로 출력)이 아니라 **`th:href="@{${menu.url}}"`**(Thymeleaf 링크 표현식)로 렌더링한다 — `@{}`를 쓰면 WAR가 컨텍스트 경로(예: `/egov-web2`) 아래 배포되어도 Thymeleaf가 컨텍스트 경로를 자동으로 앞에 붙여준다. 이미 생성된 화면들(`EgovFaqList.html`의 `th:href="@{/faq/faqRegistView.do}"` 등)도 전부 이 방식을 쓰고 있어 기존 관례와도 일치한다. `/`로 시작하지 않는 값이 수동으로 잘못 입력된 경우까지는 1차 구현 범위에서 보정하지 않는다(향후 `EgovGnbMenuInterceptor`에서 정규화하는 것을 후속 과제로 남긴다).
- 기존 `${lnbTitle}`/`${lnbMenus[0].url}` 단일 슬롯 로직은 제거.

### 5.5 `CrudTemplateRenderer` [Phase 1~2 구현 완료, 계획 대비 변경]

당초 계획은 `renderLayoutByLayerKey(layerKey, layoutBasePath, packageName)` 3-파라미터 오버로드로 HTML/GNB 레이어를 통합 처리하려 했으나, GNB 컴포넌트에는 `layoutBasePath` 개념 자체가 없어(레이아웃 스킨과 무관한 프로젝트 전역 고정 경로) 안 쓰는 파라미터를 억지로 끼워 넣는 셈이었다. 그래서 **별도 메서드로 확정**했다:

```java
public String renderGnbMenuComponent(String layerKey, String packageName)
```
- `LAYER_TEMPLATE_MAP`에 4개 템플릿(`layout/gnb-menu-{vo,mapper,mapper.xml,interceptor}.*.ftl`)을 추가로 등록해 재사용.
- 데이터 모델은 `packageName`과 `date`(오늘 날짜, 다른 템플릿과 동일한 관례)만 넣는다 — `CrudTemplateModel` 전체를 만들 필요가 없다.
- `BoardTemplateRenderer`/`MasterDetailTemplateRenderer`는 **건드리지 않았다** — 5.2에서 GNB 컴포넌트를 `layoutMode=create` 경로(Board/MasterDetail 포함)에서 완전히 분리했으므로, 이 두 렌더러가 GNB 컴포넌트를 알 필요가 없어졌다.

### 5.6 `ThymeleafLayoutTool.generateThymeleafLayout()` 본체 [Phase 1~2 구현 완료, 계획 대비 변경]

당초 계획은 "HTML 5종 + GNB 4종 = 9종을 `thymeleafLayoutLayers()`로 통합 순회"였으나, 실제 기존 코드를 보니 `ThymeleafLayoutTool`은 HTML 5종을 `thymeleafLayoutLayers()` + **로컬 `switch`문**(파일명만 결정, `resolveSubPath()`는 안 씀 — 대신 `Paths.get(outputPath, "src/main/resources/templates", layoutBasePath, fileName)`으로 직접 조합)으로 처리하고 있었다. 이 기존 방식은 그대로 두고, GNB 4종은 **별도의 두 번째 루프**로 추가했다:

1. 기존 HTML 5종 루프는 무변경.
2. `pkgSub = packageName.replace(".", "/")` 계산(접두사 제거 없는 전체 치환, 5.2와 동일 계산식).
3. `CrudLayerDefinition.GNB_MENU_COMPONENT_LAYERS`(4종)를 순회하며 `layer.resolveSubPath(pkgSub, "") + layer.fileNameSuffix()`로 상대 경로를 만든다(`fileNameSuffix()`가 이미 완전한 파일명이라 `resolveFileName()` 호출은 불필요).
4. `crudTemplateRenderer.renderGnbMenuComponent(layer.layerKey(), resolvedPackageName)`로 렌더링, 기존과 동일한 `overwriteLayout` 정책(존재 시 보존/`true`면 갱신) 적용.
5. `packageName` 미입력 시 응답 맨 앞에 경고 문구(5.1) 출력, 응답 끝에 "인터셉터는 파일만 생성했고 `servlet-context.xml` 등록은 별도(Phase 4)"라는 안내를 덧붙인다.

## 6. `ProjectInitializrTool` 측 변경

### 6.1 `DispatcherServletBuilder.java` — component-scan 필터 확장

현재:
```xml
<context:component-scan base-package="%s" use-default-filters="false">
    <context:include-filter type="annotation"
        expression="org.springframework.stereotype.Controller"/>
</context:component-scan>
```
`HandlerInterceptor` 구현체는 `@Component` 없이 `<mvc:interceptors>`로 명시 등록하는 편이 애노테이션 필터를 넓히는 것보다 안전(불필요한 클래스가 스캔되는 부작용 방지). 등록 블록 자체는 아래와 같다(실제 삽입 시점/주체는 6.2에서 확정):
```xml
<mvc:interceptors>
    <mvc:interceptor>
        <mvc:mapping path="/**"/>
        <bean class="${packageName}.cmm.web.EgovGnbMenuInterceptor" autowire="constructor"/>
    </mvc:interceptor>
</mvc:interceptors>
```
**[Phase 4 구현 완료, 계획에 없던 세부사항 추가]**: 원래 스니펫에는 `autowire` 속성이 없었는데, 실제 구현 중 확인해보니 **이게 없으면 컨텍스트 로딩 자체가 실패한다.** `EgovGnbMenuInterceptor`는 `@RequiredArgsConstructor`로 `GnbMenuMapper` 하나만 받는 생성자만 있고 기본(무인자) 생성자가 없는데, `autowire` 속성 없는 `<bean class="..."/>` 는 Spring이 기본 생성자로 인스턴스화를 시도해 `BeanCreationException`이 난다. `autowire="constructor"`를 지정해야 Spring이 단일 생성자를 찾아 `GnbMenuMapper` 타입 빈을 상위(root) 컨텍스트까지 탐색해 주입한다(MyBatis `@Mapper` 빈은 root context에 등록되지만, 자식 context의 constructor-autowire는 부모 계층까지 탐색하므로 정상 주입된다).
- `<mvc:mapping path="/**"/>`만으로는 정적 리소스(`/resources/**`), API, 에러 페이지까지 인터셉터가 그대로 타면서 불필요한 DB 조회가 발생한다. **경로 자체를 XML에서 제외 패턴으로 좁히지 않고**(설정 복잡도 증가 방지), `EgovGnbMenuInterceptor.postHandle()` 내부에서 아래 조건이면 조회 없이 즉시 return하도록 명시한다:
  - `modelAndView == null`
  - `modelAndView.isReference() && modelAndView.getViewName().startsWith("redirect:")`
  - 요청 URI가 `/resources/**`, `/css/**`, `/js/**`, `/images/**`, `/api/**`, `/mcp/**`, `/ai/**`, `/error` 로 시작
- `EgovGnbMenuInterceptor`가 아직 생성되지 않은 프로젝트(예: `ThymeleafLayoutTool`을 아직 안 부른 JSP 전용 프로젝트)에서는 빈 클래스 참조로 인해 컨텍스트 로딩이 실패할 위험이 있다 — **해결 필요**: `initializeProject()` 시점에는 이 인터셉터 등록 블록을 넣지 않고, `ThymeleafLayoutTool`이 인터셉터 파일을 생성할 때 `servlet-context.xml`에 등록 블록을 **추가(patch)**하는 방식으로 전환하는 것이 더 안전하다 (아래 6.2, **이번 계획의 기본안**).

### 6.2 확정안 — 등록 시점을 `ProjectInitializrTool`에서 `ThymeleafLayoutTool`로 이전 [Phase 4 구현 완료]

6.1의 위험 때문에, `<mvc:interceptors>` 블록 삽입은 `ProjectInitializrTool.initializeProject()`가 아니라 **`ThymeleafLayoutTool.generateThymeleafLayout()`이 기존 `servlet-context.xml`을 읽어 인터셉터 등록 블록만 삽입하는 방식으로 확정한다.**
- 장점: 인터셉터 클래스가 실제로 생성되는 시점과 등록 시점이 항상 일치 — JSP 전용 프로젝트는 이 블록 자체가 생기지 않음.
- **소유권 정리**: `servlet-context.xml` **최초 생성**은 여전히 `ProjectInitializrTool` 전속이다. `ThymeleafLayoutTool`은 이 기능에 한해 기존 파일에 등록 블록만 **patch**한다(파일을 새로 만들지 않음). 이 문서 상단(1문단)에도 이 결론을 반영했다.
- **1차 구현 방식(최소 범위, 계획대로 구현)**: XML 파서 기반 삽입은 구현 부담이 커서 1차 범위에서 제외했다. 대신
  1. `servlet-context.xml` 내용에 `EgovGnbMenuInterceptor` 클래스 참조 문자열이 이미 있으면 **skip**(중복 등록 방지, 재실행 안전).
  2. 없으면 6.1의 `<mvc:interceptors>` 블록 문자열을 `</beans>` 태그 **직전**에 삽입.
  - `</beans>`가 파일에 정확히 1번만 존재한다는 전제로 구현했다 — `countOccurrences()`로 세어 0개/2개 이상이면 파일을 건드리지 않고 실패 메시지만 반환.
  - XML 파서 기반으로의 고도화는 9절 리스크에 기록된 그대로 후속 과제로 남겼다.
- **`servlet-context.xml`이 없을 때의 정책 — [계획 대비 단순화]**: 당초 계획은 "Boot 프로젝트"와 "WAR 비표준 구조"를 구분해 서로 다른 메시지를 주려 했으나, `ThymeleafLayoutTool`은 `projectType`(WAR/Boot) 정보를 애초에 받지 않으므로 **둘을 구분할 방법이 없다.** 그래서 실제로는 파일이 없으면(이유 불문) **하나의 안내 메시지**로 통합했다: "Boot 프로젝트라면 정상입니다(1차 미지원 — `WebMvcConfigurer` 방식 별도 필요). WAR 프로젝트라면 `initializeProject()`로 먼저 생성했는지 확인하거나 `<mvc:interceptors>` 블록을 수동으로 추가하세요." 두 경우 모두 파일을 새로 만들지 않고, VO/Mapper/Interceptor 파일 생성 자체는 계속 진행한다는 동작(등록 실패가 파일 생성까지 막지 않음)은 계획대로다.

### 6.3 기존 프로젝트(`egov-web2`) 마이그레이션

설계 검토 9.4와 동일한 결론 — `initializeProject()` 재실행은 위험하므로, `egov-web2`에는 `servlet-context.xml`의 `<mvc:interceptors>` 블록만 수동 패치로 반영한다.

## 7. 구현 순서 (Phase)

1. **[완료] Phase 1 — 대상 프로젝트 산출물 템플릿 작성**: `gnb-menu-vo/mapper/mapper.xml/interceptor.*.ftl` 4종 작성, `CrudLayerDefinition`에 레이어 키 4종 + `GNB_MENU_COMPONENT_LAYERS`(당초 계획의 "9종 통합"에서 별도 리스트로 변경, 5.2 참고) 추가.
2. **[완료] Phase 2 — 렌더러/Tool 확장**: `CrudTemplateRenderer`에 `renderGnbMenuComponent()` 신규 메서드 추가(당초 계획의 3-파라미터 오버로드에서 변경, 5.5 참고). `BoardTemplateRenderer`/`MasterDetailTemplateRenderer`는 변경 없음. `ThymeleafLayoutTool`에 `packageName` 파라미터, GNB 4종 생성 루프, 미지정 시 경고 문구 추가. 전체 테스트 스위트 통과 확인(`ThymeleafLayoutToolTest`에 신규 테스트 3개 추가).
3. **[완료] Phase 3 — `gnb.html.ftl` 3종 동적 렌더링 전환**: `crud`/`board`/`masterdetail` 동시 반영, fallback("홈"만) 처리 포함. 3개 렌더러 테스트에 실제 FreeMarker 렌더링 검증 추가, 전체 테스트 스위트 통과 확인.
4. **[완료] Phase 4 — `servlet-context.xml` 패치 로직**: 6.2 확정안에 따라 `ThymeleafLayoutTool`에 "이미 등록되어 있으면 skip, 없으면 `</beans>` 직전 삽입" 로직 추가. `autowire="constructor"` 필요성 발견·반영(6.1), Boot/WAR-비표준 메시지 통합(6.2). 5개 신규 테스트 추가, 전체 테스트 스위트 통과.
5. **[완료] Phase 5 — 문서/워크플로우 갱신**: `CLAUDE.md`("ThymeleafLayoutTool 사용법" 섹션에 packageName 필수 안내·GNB 동적 렌더링·1차 제약 반영), `ThymeleafLayoutTool_기능및역할_상세설명.md`(전면 재작성 — 파라미터/생성 파일/응답 예시/GNB 동작 방식/관련 파일 모두 갱신), `docs/tool-reference/MCP_Tool_전체목록.md`(4-파라미터 시그니처 + 워크플로우 Step 4 갱신), `WorkflowDefinitionRegistry.java`의 `crud-thymeleaf`/`project-setup-crud` 워크플로우 Step 설명에 "GNB 동적 메뉴 컴포넌트 4종" 및 "packageName 필수" 반영(`WorkflowGuideService.java`는 하드코딩된 layout 관련 텍스트가 없어 변경 불필요). 전체 테스트 스위트 통과 확인.
6. **Phase 6 — `egov-web2` 수동 마이그레이션 검증**: 새 구현을 `egov-web2`에서 실제로 실행해 GNB가 Menu+Faq 도메인을 동시에 보여주는지 확인.

## 8. 테스트 계획

- **[완료]** `ThymeleafLayoutToolTest`: 신규 4개 레이어 파일 생성(`generateThymeleafLayout_writesGnbMenuComponentFourFilesUnderPackagePath`) / `overwriteLayout=false` 보존(`generateThymeleafLayout_overwriteFalse_preservesExistingGnbMenuComponentFile`) / `packageName` 미입력 시 기본값+경고(`generateThymeleafLayout_packageNameMissing_usesDefaultAndWarns`) 3개 신규 테스트 추가, 기존 2개 테스트는 4-파라미터 시그니처로 갱신.
- **[완료]** `CrudTemplateRendererTest`: `renderGnbMenuComponent_vo/mapper/mapperXml/interceptor_*` 4개 테스트로 GNB 컴포넌트 4종 각각의 패키지 선언·핵심 필드·`@Param`·`resultMap` PK 우선순서·`p.URL IS NOT NULL`·skip 경로·`getServletPath()` 사용 여부를 실제 FreeMarker 렌더링 결과 문자열로 검증(당초 계획한 "신규 `EgovGnbMenuInterceptorTemplateTest`(가칭)"는 별도 클래스 대신 `CrudTemplateRendererTest`에 통합).
- **`gnb.html.ftl` 3종 검증은 두 단계로 분리한다(FreeMarker 생성 단계와 Thymeleaf 런타임 렌더링 단계는 서로 다른 엔진이라 한 테스트로 합칠 수 없다)**:
  1. **[완료] FreeMarker 생성 결과 검증**: `CrudTemplateRendererTest.layoutGnbHtml_containsDynamicMenuLoopAndHomeStaysStatic()` / `BoardTemplateRendererTest.layoutGnbHtml_...()` / `MasterDetailTemplateRendererTest.layoutGnbHtml_...()` 3개 테스트로 `th:each="menu : ${gnbMenus}"`, `th:href="@{${menu.url}}"`, `th:if="${menu.url != null}"` 등이 실제 FreeMarker 렌더링 결과에 정확히(raw 문자열 그대로) 포함되고 옛 `lnbMenus[0]`/`시스템관리`/`고객지원` 텍스트가 완전히 사라졌는지 3개 렌더러 모두에서 검증했다.
  2. **Thymeleaf 런타임 렌더링 검증**(미착수): 1단계에서 생성된 `gnb.html` 텍스트를 실제 Thymeleaf `TemplateEngine`에 태우고, `gnbMenus`에 빈 리스트/N개 리스트를 모델로 넣어 렌더링한 뒤 "홈"만 남는지, N개가 모두 노출되는지, `url == null` 항목이 제외되는지를 **실제 HTML 출력 문자열**로 검증한다. 계획대로 이 저장소에 별도 테스트를 추가하지 않고 Phase 6(`egov-web2` 수동 검증)에서 브라우저로 직접 확인하는 것으로 대체한다.
- **[완료]** `servlet-context.xml` 패치 로직(6.2) 단위 테스트 5개 — 파일 없음(건너뜀+안내), (a) 미등록 상태에서 정상 삽입(`<mvc:interceptors>`가 `</beans>`보다 먼저 오는지까지 확인), (b) 이미 등록된 상태에서 재호출 시 파일 내용이 1바이트도 안 바뀌고 skip, (c-1) `</beans>` 0개, (c-2) `</beans>` 2개인 비정상 파일에서 파일을 건드리지 않고 실패 메시지만 반환하는지 모두 검증.
- `EgovGnbMenuInterceptor` 로직 단위 테스트: `modelAndView == null`/redirect/정적 리소스 경로일 때 조회를 skip하는지, `menu.url == null`인 항목이 `gnbMenus`에서 렌더링 제외되는지, `currentTopMenuNo` 계산이 URI-매칭 실패 시 `null`을 반환하는지.

## 9. 리스크 재확인

- **N+1/성능**: 인터셉터가 매 요청마다 `COMTNMENUINFO` 풀 스캔 — 요청량이 많은 실제 운영 환경에서는 캐싱(예: `@Cacheable` 또는 애플리케이션 시작 시 1회 로드 후 TTL 갱신)이 필요할 수 있다. 1차 구현에서는 캐싱 없이 단순 조회로 시작하고, 필요 시 후속 과제로 캐싱을 추가한다. `postHandle` 내부 skip 조건(6.1)으로 정적 리소스/리다이렉트 요청의 불필요한 조회는 1차에서부터 차단한다.
- **`servlet-context.xml` 문자열 기반 패치의 한계**(6.2): 1차 구현은 XML 파서가 아니라 문자열 삽입(`</beans>` 직전)이라, `</beans>`가 정확히 1개라는 전제가 깨지면(비정상 편집 등) 안전하게 실패해야 한다. 기존 등록 여부를 문자열 포함 검사로 판단하므로 재실행에는 안전(중복 삽입 없음)하지만, 향후 XML 파서 기반으로 고도화하는 것을 후속 과제로 남긴다.
- **`layoutMode=create`와의 정합성**: Phase 1~3을 `board`/`masterdetail`에도 동일 적용해야 사각지대가 안 생긴다(2절 표에서 이미 범위에 포함).

## 10. LNB 동적화 검토 (2차 후보, 1차 범위 아님)

`com.COMTNMENUINFO` 실데이터로 LNB를 `COMTNMENUINFO` 기반 동적 렌더링(자식 조회: `WHERE UPPER_MENU_NO = currentTopMenuNo`)으로 바꾸는 것도 검토했다. **기술적으로는 GNB와 완전히 동일한 메커니즘(같은 인터셉터, 조회 파라미터만 다름)으로 가능하지만, 아래 전제조건이 해결되지 않으면 1차 범위에 포함하지 않는다.**

### 10.1 확인한 사실

```sql
SELECT COUNT(*) FROM com.COMTNMENUINFO WHERE UPPER_MENU_NO = 5000000  →  64건
SELECT MENU_NO, MENU_NM, PROGRM_FILE_NM FROM com.COMTNMENUINFO
  WHERE UPPER_MENU_NO = 5000000 AND MENU_NM LIKE '%FAQ%'
  →  5110000 | FAQ관리 | FaqListInqire
```

- `COMTNMENUINFO`는 정확히 **2단 트리**다: 레벨 1(`UPPER_MENU_NO=0`)이 9개 대분류(사용자디렉토리/보안/통계·리포팅/협업/사용자지원/시스템관리/시스템·서비스연계/자산관리/요소기술), 레벨 2가 각 대분류의 개별 업무 화면(예: `5000000` 사용자지원 밑에 64개). 레벨 3(CRUD 액션 단위)은 없다 — `FAQ관리`(5110000)조차 자식이 0건이다.
- `UPPER_MENU_NO=5000000`을 GNB anchor로 잘못 쓰면(레벨을 착각해 "레벨 1"로 취급) GNB에 64개 항목이 뜨는 UX 실패가 확인됐다 — 이는 GNB 후보가 아니라 정확히 **LNB가 다뤄야 할 레벨**이라는 걸 데이터로 재확인한 것이다.
- 레벨 2에는 이미 **eGovFrame 프레임워크가 기본 제공하는 샘플 메뉴**가 대량으로 들어있고(예: `5000000` 밑 64개), 그중 `FAQ관리`(5110000, `PROGRM_FILE_NM=FaqListInqire`)는 우리가 `buildFullCrudPrompt`로 생성한 `EgovFaqController`(FAQINFO 관리)와 **이름은 같지만 실체는 다른, 완전히 별개의 기존 화면**이다.

### 10.2 리스크

1. **명명 충돌**: 생성한 도메인을 기존 대분류(`0`~`9000000`) 밑에 `MenuTool.generateMenuInsertSql()`로 등록하면, 이미 존재하는 동명 항목(`FAQ관리` 등)과 라벨이 겹쳐 사용자가 어느 게 어느 화면인지 혼란스러울 수 있다.
2. **프레임워크 샘플 데이터와의 결합**: 레벨 2 대분류들은 eGovFrame 표준프레임워크의 공용 샘플 관리 화면(쪽지/회의실/휴가/당직 등)이라, 우리가 생성하는 CRUD 도메인과 논리적으로 무관하다. 여기 얹으면 LNB(형제 목록)에 우리 도메인과 eGovFrame 샘플 화면이 뒤섞여 나온다.
3. **메뉴 미등록 시 LNB가 완전히 빔**: 2절의 "메뉴 자동 등록: 수동 유지" 결정과 맞물려, 사용자가 `MenuTool`로 등록을 안 하면 LNB가 통째로 비어 레이아웃이 어색해진다(GNB는 "홈" 정적 항목으로 최소 방어가 있지만 LNB는 그런 fallback이 없다).

### 10.3 전제조건 — "프로젝트 전용 최상위 메뉴" 선등록

위 리스크를 없애려면, 기존 대분류(`0`~`9000000`)를 재사용하지 말고 **프로젝트마다 자기 전용 대분류를 `UPPER_MENU_NO=0` 밑에 새로 하나 등록**해야 한다.

```
1. MenuTool.generateMenuInsertSql(upperMenuNo="0", urlPrefix="/{project}", menuNm="{프로젝트명} 업무", progrmFileNm="dir")
   → 예: MENU_NO=9010000 같은 프로젝트 전용 신규 대분류 등록 (수동 실행, 2절의 "메뉴 자동 등록: 수동 유지" 원칙과 일관됨)
2. 이후 Menu/Faq 등 생성 도메인은 이 신규 대분류(9010000) 밑에만 등록 — 기존 0~9000000 계열 대분류는 건드리지 않음
3. GNB 조회는 그대로 WHERE UPPER_MENU_NO = 0 (전체 대분류) — 프로젝트 전용 대분류 1개가 기존 9개 옆에 자연스럽게 추가됨
4. LNB 조회는 WHERE UPPER_MENU_NO = currentTopMenuNo — 사용자가 이 프로젝트 대분류를 선택했을 때만 우리가 생성한 도메인들만 형제로 보이고, eGovFrame 샘플 64개와는 섞이지 않음
```

### 10.4 결론

- **GNB의 조회 anchor(`UPPER_MENU_NO=0`)는 그대로 둔다** — 레벨을 바꿀 필요가 없다. `5000000` 같은 레벨 2 값을 GNB anchor로 쓰는 것은 기각한다(10.1).
- **LNB 동적화는 2차 과제로 분리한다.** 1차(GNB만 동적화, LNB는 Controller 현행 유지)가 먼저 필요하고, LNB까지 동적화하려면 10.3의 "프로젝트 전용 최상위 메뉴 선등록"이 선행 조건이다 — 이 조건이 없으면 10.2의 3가지 리스크가 그대로 재현된다.
- 2차를 진행하게 되면 `WorkflowGuideService`/`WorkflowDefinitionRegistry`의 안내에 "GNB/LNB 동적화를 쓰려면 `MenuTool`로 프로젝트 전용 대분류를 먼저 등록하라"는 단계가 추가되어야 한다.

이 문서는 구현 계획이며, 실제 코드 변경은 진행하지 않았다. 승인해주시면 Phase 1부터 순서대로 진행한다.