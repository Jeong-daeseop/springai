# ThymeleafLayoutTool 기능 및 역할 상세 설명

## 개요

`ThymeleafLayoutTool`은 **Thymeleaf 공통 layout 파일 5종**(`default.html`, `gnb.html`, `lnb.html`, `breadcrumb.html`, `footer.html`)과 **GNB 동적 메뉴 컴포넌트 4종**(`GnbMenuVO.java`, `GnbMenuMapper.java`/`.xml`, `EgovGnbMenuInterceptor.java`)을 생성하고, WAR 프로젝트의 `servlet-context.xml`에 인터셉터 등록 블록을 자동 patch하는 MCP Tool입니다.

`CrudPromptBuilderTool`의 Thymeleaf 생성 경로(`buildFullCrudPrompt` / `buildBoardFeature` / `buildMasterDetailPrompt`)는 `layoutMode=reuse`가 기본값이라 화면 생성 시 layout 파일을 다시 만들지 않습니다. 따라서 **신규 프로젝트에서는 화면 생성 Tool을 호출하기 전에 이 Tool을 먼저 실행**해 layout과 GNB 메뉴 컴포넌트를 준비해야 합니다.

GNB(상단 메뉴)는 정적 placeholder가 아니라 `COMTNMENUINFO`(`UPPER_MENU_NO=0`) + `COMTNPROGRMLIST` 조인 기반으로 **매 요청마다 동적 렌더링**됩니다. 설계·구현 배경은 `docs/crud/thymeleaf-layout-dynamic-gnb-design.md`(설계 검토)와 `docs/crud/thymeleaf-layout-dynamic-gnb-plan.md`(구현 계획, Phase 1~4 완료)를 참고하세요.

---

## 구성 레이어

```
ThymeleafLayoutTool (MCP Tool 진입점)
  ├── CrudTemplateRenderer      — layout 5종 + GNB 컴포넌트 4종 FreeMarker 렌더링
  ├── CodeService               — 파일 저장
  ├── ThymeleafLayoutValidator  — layoutBasePath 정규화 + 생성 후 존재 검증
  └── (내부) patchServletContextXml — servlet-context.xml에 인터셉터 등록 블록 patch
```

---

## 기능: `generateThymeleafLayout(outputPath, layoutBasePath, overwriteLayout, packageName)`

### 파라미터

| 파라미터 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `outputPath` | Y | - | 프로젝트 루트 절대경로 |
| `layoutBasePath` | N | `"layout"` | `templates` 아래 layout base 경로. `"layout/admin"`처럼 하위 경로 지정 가능 |
| `overwriteLayout` | N | `false` | 기존 layout/GNB 컴포넌트 파일 덮어쓰기 여부. `false`면 이미 있는 파일은 보존만 하고 건너뜀 |
| `packageName` | **사실상 필수** | `"egovframework.let.sample"` | GNB 메뉴 컴포넌트가 생성될 패키지(예: `egovframework.let.emp`). **`initializeProject()`에 전달했던 packageName과 반드시 동일해야 합니다** — 다르면 `EgovGnbMenuInterceptor`가 실제 CRUD 패키지와 어긋난 위치에 생성되어 동작하지 않습니다. 미입력 시 기본값이 적용되고 응답 맨 앞에 경고 문구가 반환됩니다 |

### 생성 파일

**layout HTML 5종** (기본 `layoutBasePath="layout"` 기준):
```
src/main/resources/templates/layout/default.html
src/main/resources/templates/layout/gnb.html
src/main/resources/templates/layout/lnb.html
src/main/resources/templates/layout/breadcrumb.html
src/main/resources/templates/layout/footer.html
```
`layoutBasePath="layout/admin"`이면 위 5개 파일이 모두 `templates/layout/admin/` 아래에 생성됩니다.

**GNB 메뉴 컴포넌트 4종** (`packageName` 기준, `layoutBasePath`와 무관하게 항상 고정 위치):
```
src/main/java/{packageName 경로}/cmm/vo/GnbMenuVO.java
src/main/java/{packageName 경로}/cmm/service/GnbMenuMapper.java
src/main/resources/egovframework/mapper/cmm/GnbMenuMapper.xml
src/main/java/{packageName 경로}/cmm/web/EgovGnbMenuInterceptor.java
```
예: `packageName="egovframework.let.emp"` → `src/main/java/egovframework/let/emp/cmm/vo/GnbMenuVO.java`. **`egovframework.let.*` 접두사를 강제하지 않습니다** — `kr.go.sample.emp` 같은 임의의 packageName도 정확히 지원합니다(`FilePlanFactory.mainController()`와 동일한 방식).

**servlet-context.xml patch** (WAR 프로젝트, 파일이 이미 존재할 때만):
```
src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml
```
에 아래 블록을 `</beans>` 직전에 삽입합니다(이미 등록되어 있으면 skip):
```xml
<mvc:interceptors>
    <mvc:interceptor>
        <mvc:mapping path="/**"/>
        <bean class="{packageName}.cmm.web.EgovGnbMenuInterceptor" autowire="constructor"/>
    </mvc:interceptor>
</mvc:interceptors>
```

### 처리 흐름

```
1. ThymeleafLayoutValidator.normalizeLayoutBasePath(layoutBasePath)
   - 미입력 시 "layout" 기본값 적용, 앞뒤 "/" 정규화
   - packageName 미입력 시 "egovframework.let.sample" 기본값 적용 + 응답에 경고 문구 추가

2. layout HTML 5종 순회 (CrudLayerDefinition.thymeleafLayoutLayers())
   - overwriteLayout=false + 파일 존재 → 보존(skip)
   - 그 외 → CrudTemplateRenderer.renderLayoutByLayerKey()로 렌더링 후 CodeService.saveGeneratedCode()로 저장

3. GNB 메뉴 컴포넌트 4종 순회 (CrudLayerDefinition.GNB_MENU_COMPONENT_LAYERS)
   - pkgSub = packageName.replace(".", "/") (접두사 제거 없는 전체 치환)
   - overwriteLayout=false + 파일 존재 → 보존(skip)
   - 그 외 → CrudTemplateRenderer.renderGnbMenuComponent()로 렌더링 후 저장
   - 이 4종은 도메인별 CRUD 생성 경로(layoutMode=create)와 완전히 분리되어 있어,
     도메인을 여러 개 생성해도 이 파일들이 반복 생성/충돌되지 않습니다

4. ThymeleafLayoutValidator.validateExisting()
   - layout HTML 5종이 모두 존재하는지 재확인 (GNB 컴포넌트는 이 검증에 포함되지 않음)
   - 누락 시 [검증 실패] + 누락 파일 목록 반환

5. servlet-context.xml patch
   - 파일 없음 → 건너뜀 + 안내(Boot 프로젝트면 정상 / WAR면 initializeProject() 확인 또는 수동 등록 안내)
   - EgovGnbMenuInterceptor 이미 등록됨 → skip
   - </beans> 정확히 1개 → 그 직전에 인터셉터 등록 블록 삽입
   - </beans> 0개 또는 2개 이상 → 파일을 건드리지 않고 실패 메시지 반환
```

### 응답 형식 예시

```
=== Thymeleaf layout 생성 결과 ===

출력 경로: /Users/user/Desktop/egov-generated/emp
layoutBasePath: layout
packageName: egovframework.let.emp

  생성: /Users/user/Desktop/egov-generated/emp/src/main/resources/templates/layout/default.html
  생성: /Users/user/Desktop/egov-generated/emp/src/main/resources/templates/layout/gnb.html
  생성: /Users/user/Desktop/egov-generated/emp/src/main/resources/templates/layout/lnb.html
  생성: /Users/user/Desktop/egov-generated/emp/src/main/resources/templates/layout/breadcrumb.html
  생성: /Users/user/Desktop/egov-generated/emp/src/main/resources/templates/layout/footer.html
  생성: /Users/user/Desktop/egov-generated/emp/src/main/java/egovframework/let/emp/cmm/vo/GnbMenuVO.java
  생성: /Users/user/Desktop/egov-generated/emp/src/main/java/egovframework/let/emp/cmm/service/GnbMenuMapper.java
  생성: /Users/user/Desktop/egov-generated/emp/src/main/resources/egovframework/mapper/cmm/GnbMenuMapper.xml
  생성: /Users/user/Desktop/egov-generated/emp/src/main/java/egovframework/let/emp/cmm/web/EgovGnbMenuInterceptor.java

[검증 완료] layout 5종이 모두 존재합니다.

[servlet-context.xml 등록]
  등록: .../src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml 에 EgovGnbMenuInterceptor patch 완료 (bean class=egovframework.let.emp.cmm.web.EgovGnbMenuInterceptor, autowire=constructor)
```

기존 파일이 있고 `overwriteLayout=false`(기본값)인 경우 layout/GNB 컴포넌트 모두 `보존:`으로 표시되고, `servlet-context.xml`에 이미 등록되어 있으면 `보존: ... (EgovGnbMenuInterceptor 이미 등록됨)`으로 표시됩니다.

`packageName` 미입력 시 응답 맨 앞에 다음 경고가 추가됩니다:
```
⚠ packageName 미지정 — 기본값 'egovframework.let.sample' 사용. 실제 프로젝트 packageName과 다르면 GNB 컴포넌트가 컴파일되지 않거나 등록되지 않습니다.
```

---

## 중요 제약사항

| 항목 | 내용 |
|---|---|
| 실행 시점 | `buildFullCrudPrompt`/`buildBoardFeature`/`buildMasterDetailPrompt`(Thymeleaf, `layoutMode=reuse`) **실행 전**에 먼저 호출 |
| `packageName` 일치 | `initializeProject()`에 전달한 packageName과 반드시 동일해야 GNB 컴포넌트가 올바른 위치에 생성되고 CRUD 코드와 패키지가 맞음 |
| 기본 보존 정책 | `overwriteLayout` 기본값 `false` — 사용자가 수정한 layout/GNB 컴포넌트를 실수로 덮어쓰지 않음 |
| `layoutMode=reuse`와의 관계 | layout 파일이 없는 상태에서 `layoutMode=reuse`로 화면을 생성하면 실패 안내와 함께 거부됨 |
| `layoutMode=create`와의 관계 | 화면 생성 Tool이 layout HTML 레이어까지 함께 생성하므로 이 Tool을 별도로 먼저 부를 필요는 없음. **단, GNB 메뉴 컴포넌트 4종은 `layoutMode=create` 경로에서 생성되지 않으므로**(도메인마다 반복 생성되면 안 되는 프로젝트 전역 산출물이라 의도적으로 분리) GNB 동적 렌더링을 쓰려면 이 Tool을 최소 1회는 호출해야 함 |
| `layoutBasePath` 일치 | `generateThymeleafLayout(layoutBasePath="layout/admin")`으로 만든 경로는 화면 생성 시 `layoutView="layout/admin/default"`, `breadcrumbView="layout/admin/breadcrumb"`로 동일하게 지정해야 함 |
| **WAR 전용 (1차 구현)** | Boot 프로젝트는 `servlet-context.xml` 자체가 없어(auto-configuration 방식) 인터셉터 등록이 불가능합니다. GNB 컴포넌트 파일은 생성되지만 등록되지 않아 동작하지 않습니다. Boot 지원(`WebMvcConfigurer` 방식)은 후속 과제 |
| **Jakarta Servlet 전용 (1차 구현)** | `EgovGnbMenuInterceptor`는 `jakarta.servlet.*`만 지원합니다(eGovFrame 5.0 전용). eGovFrame 4.3(`javax.servlet`)은 미지원이며 후속 과제 |
| 메뉴 데이터는 별도 등록 필요 | `COMTNMENUINFO`에 실제 메뉴가 없으면 GNB에는 "홈"만 보입니다. `MenuTool.generateMenuInsertSql()`로 메뉴를 등록해야 GNB에 항목이 나타납니다(자동 등록 안 함) |

---

## 전체 워크플로우 (신규 프로젝트 기준)

```
Step 1. initializeProject(packageName="egovframework.let.emp", ...)
        → 프로젝트 골격 + 정적 리소스(styles.css, krds.min.js) 생성

Step 2. generateThymeleafLayout(outputPath, packageName="egovframework.let.emp")
        → layout 5종(default/gnb/lnb/breadcrumb/footer) +
          GNB 메뉴 컴포넌트 4종(VO/Mapper/MapperXml/Interceptor) 생성 +
          servlet-context.xml 인터셉터 등록 patch

Step 3. buildFullCrudPrompt(..., viewType="thymeleaf")
        → layoutMode 기본값 reuse로 layout 재사용, 화면/Java/Mapper만 생성

Step 4. (선택) 추가 도메인 CRUD/Board/MasterDetail 반복 생성
        → 이미 layout·GNB 컴포넌트가 있으므로 Step 2 재실행 불필요

Step 5. (선택) MenuTool.generateMenuInsertSql(...)
        → GNB/LNB에 실제로 노출될 메뉴 등록 (수동 실행 필요)
```

---

## 테스트 예시문

### 기본 layout + GNB 컴포넌트 생성

```
generateThymeleafLayout(outputPath="/Users/user/Desktop/egov-generated/emp", packageName="egovframework.let.emp") 로 Thymeleaf 공통 layout과 GNB 메뉴 컴포넌트 만들어줘
```

```
/Users/user/Desktop/egov-generated/emp 프로젝트(packageName=egovframework.let.emp)에 Thymeleaf 공통 layout 5종과 GNB 동적 메뉴 컴포넌트 생성해줘
```

### layoutBasePath 지정 (하위 경로)

```
/Users/user/Desktop/egov-generated/admin-site 프로젝트(packageName=egovframework.let.admin)에
layoutBasePath="layout/admin" 으로 관리자용 Thymeleaf layout 만들어줘
```

### 기존 layout/GNB 컴포넌트 강제 갱신

```
/Users/user/Desktop/egov-generated/emp 의 Thymeleaf layout과 GNB 컴포넌트를 최신 버전으로 덮어써줘 (overwriteLayout=true)
```

### 전체 흐름 조합

```
1. initializeProject(projectName="emp-web", packageName="egovframework.let.emp", ..., projectType="boot", egovVersion="5.0")
2. generateThymeleafLayout(outputPath="/Users/user/Desktop/egov-generated/emp-web", packageName="egovframework.let.emp")
3. buildFullCrudPrompt(database="ebt", tableName="COMTNEMPLYRINFO", domain="Employer",
   packageName="egovframework.let.emp", outputPath="...", viewType="thymeleaf")
   → layoutMode 미지정 시 reuse 기본값으로 Step 2에서 만든 layout을 그대로 사용
4. MenuTool.generateMenuInsertSql(upperMenuNo="0", urlPrefix="/emp/employer", menuNm="직원관리", progrmFileNm="EgovEmployerList")
   → GNB에 "직원관리"가 실제로 노출되도록 메뉴 등록
```

### layout 없이 화면부터 생성 시도 (실패 케이스)

```
generateThymeleafLayout 없이 buildFullCrudPrompt(..., viewType="thymeleaf") 만 바로 실행해줘
```
→ `layoutMode=reuse` 기본값 + layout 파일 없음 → 실패 응답과 함께 `generateThymeleafLayout(outputPath=...)` 먼저 실행하라는 안내가 반환됩니다.

### packageName 미지정 (경고 케이스)

```
generateThymeleafLayout(outputPath="/Users/user/Desktop/egov-generated/emp") 로 layout 만들어줘 (packageName은 안 줄게)
```
→ 기본값 `egovframework.let.sample`이 적용되고, 응답 맨 앞에 실제 프로젝트 packageName과 다르면 GNB 컴포넌트가 동작하지 않는다는 경고가 반환됩니다.

---

## GNB 동적 렌더링 동작 방식 (참고)

1. `EgovGnbMenuInterceptor`(`HandlerInterceptor.postHandle()`)가 매 요청마다 `COMTNMENUINFO`(`UPPER_MENU_NO=0`) + `COMTNPROGRMLIST`를 조인 조회해 `gnbMenus`(최상위 메뉴 목록)와 `currentTopMenuNo`(현재 선택된 메뉴, `request.getServletPath()` 기준 매칭)를 모델에 주입합니다.
2. `gnb.html`은 "홈"을 항상 정적으로 1개 렌더링하고, 그 뒤로 `th:each="menu : ${gnbMenus}"`로 실제 메뉴를 그립니다. `menu.url == null`인 항목(SQL 단계에서 이미 `p.URL IS NOT NULL`로 걸러지지만 템플릿에서도 2차 방어)은 렌더링에서 제외됩니다.
3. 조회 실패/빈 결과 시 `gnbMenus`는 빈 리스트가 되어 "홈"만 남습니다(홈이 중복 렌더링되지 않도록 fallback 리스트에 "홈"을 넣지 않음).
4. `/resources/**`, `/css/**`, `/js/**`, `/images/**`, `/api/**`, `/mcp/**`, `/ai/**`, `/error` 요청과 `modelAndView == null`/`redirect:` 응답은 조회 자체를 skip합니다.
5. LNB(좌측 메뉴)는 이번 기능과 무관하게 **기존처럼 각 생성 Controller가 `lnbTitle`/`lnbMenus`/`breadcrumbs`를 직접 주입**합니다(현행 유지 — LNB 동적화는 `docs/crud/thymeleaf-layout-dynamic-gnb-plan.md` 10절에서 별도 전제조건과 함께 2차 과제로 분리됨).

---

## 관련 파일

| 파일 | 역할 |
|---|---|
| `tools/ThymeleafLayoutTool.java` | MCP Tool 진입점 (`@Tool` 어노테이션), `packageName` 파라미터, `patchServletContextXml()` |
| `service/CrudTemplateRenderer.java` | layout 5종 렌더링(`renderLayoutByLayerKey`) + GNB 컴포넌트 4종 렌더링(`renderGnbMenuComponent`) |
| `service/CodeService.java` | 렌더링 결과 파일 저장 |
| `service/ThymeleafLayoutValidator.java` | `layoutBasePath` 정규화 + layout HTML 5종 존재 검증 |
| `model/crud/CrudLayerDefinition.java` | `thymeleafLayoutLayers()`(layout 5종) / `GNB_MENU_COMPONENT_LAYERS`(GNB 컴포넌트 4종, 도메인별 CRUD 경로와 분리) |
| `model/crud/CrudLayoutMode.java` | `reuse` / `create` / `none` 모드 정규화 |
| `templates/crud/layout/*.ftl` | layout HTML 5종 + GNB 메뉴 컴포넌트 4종(`gnb-menu-vo/mapper/mapper.xml/interceptor.*.ftl`) FreeMarker 템플릿 원본 |

## 관련 문서

- `docs/crud/crudpromptbuildertool-layoutmode-reuse-plan.md` — `layoutMode=reuse` 기본값 도입 설계
- `docs/crud/crudpromptbuildertool-layoutmode-reuse-impact.md` — 도입 영향평가
- `docs/crud/thymeleaf-layout-dynamic-gnb-design.md` — GNB 동적 렌더링 설계 검토(1~10절)
- `docs/crud/thymeleaf-layout-dynamic-gnb-plan.md` — GNB 동적 렌더링 구현 계획 및 Phase별 진행 상황(Phase 1~4 완료)
