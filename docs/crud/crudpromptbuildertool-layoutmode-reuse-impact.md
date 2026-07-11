# CrudPromptBuilderTool layoutMode=reuse 기본값 변경 영향평가

    ## 1. 배경

현재 `CrudPromptBuilderTool`의 Thymeleaf 자동 생성 경로는 CRUD 화면 파일과 공통 layout 파일을 같은 생성 단위로 처리한다.

대상 layout 파일:

- `src/main/resources/templates/layout/default.html`
- `src/main/resources/templates/layout/gnb.html`
- `src/main/resources/templates/layout/lnb.html`
- `src/main/resources/templates/layout/breadcrumb.html`
- `src/main/resources/templates/layout/footer.html`

이 구조에서는 프로젝트에 CRUD 화면을 추가할 때마다 기존 layout 파일이 다시 생성된다. 사용자가 layout을 수정했거나 앞선 기능 생성에서 메뉴/구조를 조정한 경우, 다음 생성 작업이 기존 layout을 덮어써 초기화되는 문제가 발생한다.

## 2. 변경 목표

`layoutMode` 기본값을 `reuse`로 변경한다.

목표 동작:

- `ProjectInitializrTool`은 프로젝트 골격과 정적 리소스를 생성한다.
- 신규 layout 전용 Tool이 공통 Thymeleaf layout 파일만 생성한다.
- `CrudPromptBuilderTool`의 `build*` 생성은 기본적으로 기존 layout을 재사용하고 화면/Java/Mapper 파일만 생성한다.
- layout 정보를 명시하면 해당 layout 경로를 기준으로 화면을 생성한다.
- layout 정보를 주지 않고 `layoutMode=none`을 선택하면 layout 없이 순수 화면 파일을 생성할 수 있다.

## 3. 현재 구조

### 3.1 단일 CRUD

파일:

- `src/main/java/com/krdevops/springai/model/crud/CrudLayerDefinition.java`
- `src/main/java/com/krdevops/springai/service/CrudOrchestrationService.java`

현재 `CrudLayerDefinition.THYMELEAF_LAYERS`는 아래 layout 레이어를 포함한다.

- `layoutHtml`
- `layoutGnbHtml`
- `layoutLnbHtml`
- `layoutBreadcrumbHtml`
- `layoutFooterHtml`

따라서 `buildFullCrudPrompt(..., viewType="thymeleaf", llmProvider="auto")` 실행 시 layout 파일이 저장된다.

### 3.2 게시판(BBS)

파일:

- `src/main/java/com/krdevops/springai/model/board/BoardLayerDefinition.java`
- `src/main/java/com/krdevops/springai/service/BoardOrchestrationService.java`

현재 `BoardLayerDefinition.THYMELEAF_LAYERS`도 layout 레이어 5개를 포함한다.

따라서 `buildBoardFeature(..., viewType="thymeleaf")` 실행 시 layout 파일이 저장된다.

### 3.3 마스터-디테일

파일:

- `src/main/java/com/krdevops/springai/model/masterdetail/MasterDetailLayerDefinition.java`
- `src/main/java/com/krdevops/springai/service/MasterDetailOrchestrationService.java`

현재 `MasterDetailLayerDefinition.THYMELEAF_LAYERS`도 layout 레이어 5개를 포함한다.

따라서 `buildMasterDetailPrompt(..., viewType="thymeleaf", llmProvider="auto")` 실행 시 layout 파일이 저장된다.

## 4. 제안 설계

### 4.1 신규 Tool

신규 Tool 이름 후보:

```text
generateThymeleafLayout
```

역할:

- 공통 Thymeleaf layout 파일만 생성한다.
- CRUD/Board/MasterDetail 도메인 파일은 생성하지 않는다.
- 기본적으로 기존 파일이 있으면 덮어쓰지 않는다.

권장 파라미터:

```text
generateThymeleafLayout(
  outputPath,
  layoutName = "default",
  overwriteLayout = false,
  includeFooter = true
)
```

생성 파일:

```text
src/main/resources/templates/layout/default.html
src/main/resources/templates/layout/gnb.html
src/main/resources/templates/layout/lnb.html
src/main/resources/templates/layout/breadcrumb.html
src/main/resources/templates/layout/footer.html
```

주의:

- 현재 `default.html`은 `layout/footer`를 참조한다.
- 따라서 `footer.html`을 생성 대상에서 제외하려면 `default.html`에서도 footer include를 제거해야 한다.
- 1차 구현에서는 `footer.html`도 생성 대상에 포함하는 것이 안전하다.

### 4.2 CrudPromptBuilderTool layoutMode

`buildFullCrudPrompt`, `buildBoardFeature`, `buildMasterDetailPrompt`에 `layoutMode`를 추가한다.

권장값:

```text
layoutMode = "reuse" | "create" | "none"
```

기본값:

```text
reuse
```

동작:

| layoutMode | 동작 |
|---|---|
| `reuse` | layout 파일은 생성/저장하지 않는다. 화면은 기존 layout을 참조한다. |
| `create` | layout 파일까지 생성한다. 1차 구현에서는 기존 layout 파일을 보존하고, 강제 갱신은 `generateThymeleafLayout(overwriteLayout=true)`로 수행한다. |
| `none` | layout 참조 없이 순수 화면 파일을 생성한다. |

추가 파라미터 후보:

```text
layoutView = "layout/default"
breadcrumbView = "layout/breadcrumb"
```

`overwriteLayout`은 `build*` Tool 파라미터로 노출하지 않는다.

- `layoutMode=reuse`: layout 파일을 생성하지 않는다.
- `layoutMode=create`: layout 레이어를 포함하되 기존 layout 파일은 보존한다.
- 기존 layout을 강제로 갱신해야 할 때는 `generateThymeleafLayout(overwriteLayout=true)`를 먼저 실행한다.

`overwriteLayout` description은 `generateThymeleafLayout`에만 둔다. 이렇게 해야 MCP 클라이언트가 `layoutMode=reuse, overwriteLayout=true`처럼 의미 없는 조합을 만들 여지를 줄일 수 있다.

### 4.3 주어진 layout 정보 사용

사용자가 layout 정보를 주는 경우:

```text
layoutView = "layout/admin/default"
breadcrumbView = "layout/admin/breadcrumb"
```

화면 파일은 아래처럼 생성되어야 한다.

```html
layout:decorate="~{layout/admin/default}"
<th:block th:replace="~{layout/admin/breadcrumb :: breadcrumb}"></th:block>
```

layout 파일 자체는 수정하지 않는다.

메뉴 추가는 layout 파일을 직접 수정하는 방식보다 Controller 모델 계약으로 처리하는 것이 현재 구조에 맞다.

현재 모델 계약:

- `lnbTitle`
- `lnbMenus`
- `breadcrumbs`
- `currentMenuId`

## 5. 변경 목록

### 5.1 신규 enum 또는 값 객체

후보 파일:

```text
src/main/java/com/krdevops/springai/model/crud/CrudLayoutMode.java
```

역할:

- `reuse`, `create`, `none` 문자열 정규화
- 미입력 기본값 `reuse` 처리
- 잘못된 값 입력 시 명확한 오류 메시지 반환

### 5.2 레이어 정의 분리

대상:

- `CrudLayerDefinition`
- `BoardLayerDefinition`
- `MasterDetailLayerDefinition`

현재:

```text
THYMELEAF_LAYERS = COMMON + LAYOUT + SCREEN
```

변경:

```text
THYMELEAF_LAYOUT_LAYERS = LAYOUT
THYMELEAF_SCREEN_LAYERS = SCREEN
```

또는 메서드로 분리:

```java
forViewType(viewType, layoutMode)
layoutLayers()
screenLayers(viewType)
```

주의:

- 기존 `THYMELEAF_LAYERS` 상수를 그대로 두고 `THYMELEAF_LAYERS_REUSE`, `THYMELEAF_LAYERS_CREATE`를 추가하면 호출부가 어떤 상수를 써야 하는지 혼동될 수 있다.
- 권장 구현은 정책별 상수를 외부에 직접 노출하지 않고 `forViewType(viewType, layoutMode)` 같은 단일 진입점으로 숨기는 방식이다.
- `layoutLayers()`는 `generateThymeleafLayout` 전용으로만 사용한다.
- `screenLayers(viewType)`는 테스트와 단건 화면 생성 Tool에서 재사용할 수 있다.

### 5.3 OrchestrationService 변경

대상:

- `CrudOrchestrationService`
- `BoardOrchestrationService`
- `MasterDetailOrchestrationService`

변경:

- `layoutMode` 파라미터 추가
- `layoutMode=reuse`일 때 layout 레이어 저장 제외
- `layoutMode=create`일 때 layout 레이어 포함
- `layoutMode=none`일 때 layout 레이어 저장 제외
- `layoutMode=none`일 때 화면 템플릿도 layout 참조 없는 버전을 사용해야 함

주의:

- 단순히 layout 레이어만 제외하면 `reuse`는 정상이다.
- `none`은 화면 템플릿 내용 자체가 달라져야 하므로 별도 템플릿 또는 렌더링 옵션이 필요하다.

### 5.4 Renderer 변경

대상:

- `CrudTemplateRenderer`
- `BoardTemplateRenderer`
- `MasterDetailTemplateRenderer`

필요 변경:

- `layoutView`, `breadcrumbView`, `layoutBasePath`, `layoutMode`를 FreeMarker 데이터 모델에 전달
- 화면 FTL에서 하드코딩된 `layout/default`, `layout/breadcrumb` 대신 전달값 사용
- `default.html.ftl` 내부의 `layout/gnb`, `layout/lnb`, `layout/footer` 참조도 `layoutBasePath` 기준으로 치환

이름 충돌 방지:

- `layoutTemplate`라는 이름은 기존 FreeMarker 템플릿 파일 자체 또는 layout 생성 템플릿과 혼동될 수 있다.
- 화면에서 Thymeleaf가 참조할 layout 경로는 `layoutView` 또는 `layoutDecorateView`가 더 명확하다.
- breadcrumb partial 경로는 `breadcrumbView`를 사용한다.

현재 하드코딩 예:

```html
layout:decorate="~{layout/default}"
<th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>
```

변경 후보:

```html
layout:decorate="~{${layoutView}}"
<th:block th:replace="~{${breadcrumbView} :: breadcrumb}"></th:block>
```

`default.html.ftl` 내부 partial 참조 변경 후보:

```html
<th:block th:replace="~{${layoutBasePath}/gnb :: gnb}"></th:block>
<th:block th:replace="~{${layoutBasePath}/lnb :: lnb}"></th:block>
<th:block th:replace="~{${layoutBasePath}/footer :: footer}"></th:block>
```

이 변경이 없으면 `generateThymeleafLayout(layoutBasePath="layout/admin")`으로 생성한 `layout/admin/default.html`이
여전히 최상위 `layout/gnb`, `layout/lnb`, `layout/footer`를 참조해 런타임 오류 또는 의도치 않은 layout 혼용이 발생할 수 있다.

`layoutView`, `breadcrumbView`는 FreeMarker 렌더링 시점에 먼저 치환된다. Thymeleaf는 생성 결과인 `~{layout/default}` 또는 `~{layout/admin/default}`만 해석하므로 실행 시점 충돌 우려는 없다.

검증은 문법 충돌이 아니라 아래 항목에 집중한다.

- FreeMarker 렌더링 결과에 미치환 `${layoutView}`가 남지 않는지 확인
- FreeMarker 렌더링 결과에 미치환 `${layoutBasePath}`가 남지 않는지 확인
- 생성 HTML의 `layout:decorate` 경로가 전달 파라미터와 일치하는지 확인
- breadcrumb partial 경로가 전달 파라미터와 일치하는지 확인
- `layoutBasePath="layout/admin"`일 때 `default.html` 내부 partial 참조가 모두 `layout/admin/*`로 생성되는지 확인

### 5.5 Tool 변경

대상:

- `CrudPromptBuilderTool`
- `McpConfig`

변경:

- `generateThymeleafLayout` 신규 Tool 추가
- 신규 Tool을 별도 Tool 클래스로 만들 경우 `McpConfig.allToolCallbacks()` 파라미터와 `toolObjects(...)` 목록에 등록
- 신규 Tool을 `CrudPromptBuilderTool` 메서드로 추가할 경우 `McpConfig` 변경은 불필요
- `buildFullCrudPrompt` 파라미터에 `layoutMode`, `layoutView`, `breadcrumbView` 추가
- `buildBoardFeature` 파라미터에 동일 추가
- `buildMasterDetailPrompt` 파라미터에 동일 추가
- Tool description의 생성 파일 수 수정
- `overwriteLayout`은 `generateThymeleafLayout`에만 노출한다. `build*` Tool에는 노출하지 않는다.

권장:

- `generateThymeleafLayout`는 책임이 독립적이므로 `ThymeleafLayoutTool` 또는 `LayoutTemplateTool` 별도 클래스로 분리한다.
- `generateThymeleafLayout` description에는 `overwriteLayout`이 이 Tool에서만 적용된다고 명시한다.
- 이 경우 `src/main/java/com/krdevops/springai/config/McpConfig.java` 등록 변경이 필수다.
- Tool 목록 문서와 `CLAUDE.md`의 "현재 등록된 MCP Tool" 섹션도 함께 갱신한다.

### 5.6 프롬프트 모드 변경

대상:

- `CrudPromptBuilderService`
- `MasterDetailService`
- Board 프롬프트/안내 생성 경로

`llmProvider="claude"` 모드에서도 안내 문구를 변경해야 한다.

현재 Thymeleaf 안내는 layout 파일 생성을 전제로 한다.

변경:

- `layoutMode=reuse`: layout 파일 생성 Step 제거
- `layoutMode=create`: layout 파일 생성 Step 유지
- `layoutMode=none`: `layout:decorate` 사용 금지 안내

주의:

- 현재 `buildBoardFeature`는 auto 오케스트레이션 중심이지만, Board 관련 README/샘플/향후 claude 모드 안내에 layout 생성 Step이 있으면 동일하게 변경해야 한다.
- Board 쪽 안내 생성이 별도 Service에 없더라도 `CrudPromptBuilderTool` description과 `templates/*/mcp/README.md`의 Board 예시는 반드시 갱신 대상이다.

### 5.7 layout 존재 확인

`layoutMode=reuse`는 기존 layout 파일이 있다는 전제에서만 안전하다.

확인 대상 파일은 `layoutView`에서 resolved base를 구한 뒤 그 아래 5종 전체다. 기본 `layoutView="layout/default"`이면:

```text
{outputPath}/src/main/resources/templates/layout/default.html
{outputPath}/src/main/resources/templates/layout/gnb.html
{outputPath}/src/main/resources/templates/layout/lnb.html
{outputPath}/src/main/resources/templates/layout/breadcrumb.html
{outputPath}/src/main/resources/templates/layout/footer.html
```

예를 들어 `layoutView="layout/admin/default"`이면 확인 대상은
`{outputPath}/src/main/resources/templates/layout/admin/{default,gnb,lnb,breadcrumb,footer}.html`이다.
`breadcrumbView`는 같은 base의 `breadcrumb.html`과 일치해야 한다.

확인 위치:

- `CrudOrchestrationService`: 단일 CRUD 생성 전 확인
- `BoardOrchestrationService`: Board 생성 전 확인
- `MasterDetailOrchestrationService`: MasterDetail 생성 전 확인
- `generateThymeleafLayout`: 생성 후 결과 검증

권장 구현:

- 공통 Service로 `ThymeleafLayoutService` 또는 `ThymeleafLayoutValidator`를 둔다.
- `layoutMode=reuse`이고 resolved base 아래 필수 layout 5종 중 하나라도 없으면 파일 저장 전에 실패 결과를 반환한다.
- 실패 메시지에는 `generateThymeleafLayout(outputPath=...)` 실행 안내를 포함한다.

### 5.8 EgovMainController.java 처리

`MasterDetailOrchestrationService`는 Thymeleaf 레이어와 별도로 `EgovMainController.java`를 추가 생성한다.

이 파일은 layout 레이어도 화면 레이어도 아니다.

분류:

```text
COMMON + SCREEN + AUXILIARY
```

처리 원칙:

- `layoutMode`와 무관하게 기존처럼 생성한다.
- `layoutMode=reuse`에서도 생성 대상에 포함한다.
- 생성 파일 수 표에는 MasterDetail의 추가 파일로 별도 표기한다.
- 향후 `mainControllerMode = "update" | "skip"` 같은 별도 정책이 필요하면 layoutMode와 분리한다.

## 6. 생성 파일 수 변화

`layoutMode=reuse` 기본값 기준:

| 생성 경로 | 현재 Thymeleaf 생성 수 | 변경 후 기본 생성 수 | 비고 |
|---|---:|---:|---|
| 단일 CRUD | 16 | 11 | layout 5개 제외 |
| Board | 17 | 12 | layout 5개 제외 |
| MasterDetail | 18 | 13 | layout 5개 제외 |

MasterDetail auto 생성 결과는 `EgovMainController.java`를 추가로 저장한다.

| 생성 경로 | 변경 후 기본 레이어 수 | auto 결과 성공 파일 수 |
|---|---:|---:|
| MasterDetail | 13 | 14 |

`layoutMode=create` 사용 시:

| 생성 경로 | 생성 수 |
|---|---:|
| 단일 CRUD | 16 |
| Board | 17 |
| MasterDetail | 18 |

`layoutMode=create`에서도 MasterDetail auto 결과는 `EgovMainController.java` 때문에 성공 파일 수가 19개가 될 수 있다.

## 7. 테스트 영향

수정 필요 테스트:

- `CrudOrchestrationServiceTest`
- `BoardOrchestrationServiceTest`
- `MasterDetailTemplateRendererTest`
- `BoardLayerDefinitionTest`
- `MasterDetailServiceTest`
- `CrudPromptBuilderToolTest`

주요 변경:

- 기본 Thymeleaf 성공 파일 수 기대값 수정
- 기본 생성 결과에서 `layout/default.html`, `layout/gnb.html` 저장 검증 제거
- `layoutMode=create` 별도 테스트 추가
- `generateThymeleafLayout` Tool 테스트 추가
- `layoutView` 지정 시 화면 소스의 `layout:decorate` 값 검증
- `layoutBasePath="layout/admin"` 지정 시 `default.html` 내부 `gnb/lnb/footer` 참조가 모두 같은 base로 생성되는지 검증
- 커스텀 layout 경로에서 resolved base 아래 5종 중 하나라도 누락되면 저장 전에 실패하는지 검증
- `layoutMode=none` 지정 시 화면 소스에 `layout:decorate`와 breadcrumb partial 참조가 없는지 검증
- `generateThymeleafLayout(overwriteLayout=false)`에서 기존 layout 파일이 보호되는지 검증
- `generateThymeleafLayout(overwriteLayout=true)`에서 기존 layout 파일이 갱신되는지 검증
- `layoutMode=reuse`에서 layout 파일이 없으면 저장 전에 실패하는지 검증
- `generateThymeleafLayout` 신규 Tool이 `McpConfig`에 등록되는지 검증
- MasterDetail auto 결과에서 `EgovMainController.java`가 `layoutMode`와 무관하게 유지되는지 검증
- `CLAUDE.md`의 MCP Tool 개수와 Tool 목록이 신규 Tool 추가 후 갱신되는지 확인

## 8. 호환성 영향

### 8.1 MCP 클라이언트 호출

새 파라미터는 nullable/optional로 추가해야 한다.

기존 호출:

```text
buildFullCrudPrompt(..., viewType="thymeleaf")
```

변경 후 동작:

```text
layoutMode 미입력 → reuse
```

주의:

- 기존 사용자는 같은 호출로 더 이상 layout 파일을 받지 않는다.
- 이것은 의도된 동작이지만, 기존 자동 생성 파일 수를 기대하는 클라이언트에는 변경으로 보일 수 있다.

### 8.2 기존 프로젝트

기존 프로젝트에 layout 파일이 없는 상태에서 `layoutMode=reuse`로 화면만 생성하면 런타임 오류가 발생할 수 있다.

대응:

- `generateThymeleafLayout` 먼저 실행하도록 workflow 안내
- `CrudPromptBuilderTool`에서 `layoutMode=reuse`이고 layout 파일이 없으면 경고 또는 실패 처리

권장:

```text
layoutMode=reuse + layout 파일 없음 → 실패 처리
```

이유:

- 화면은 생성되지만 런타임에서 깨지는 상태를 만들 수 있다.
- MCP 응답에서 `generateThymeleafLayout` 실행을 안내하는 편이 안전하다.

### 8.3 ProjectInitializrTool

`ProjectInitializrTool`은 현재 정적 리소스와 프로젝트 골격을 담당한다.

layout 파일은 프로젝트 골격보다 CRUD 화면 정책에 가깝기 때문에 `ProjectInitializrTool`에 직접 넣기보다 `generateThymeleafLayout` Tool로 분리하는 것이 낫다.

## 9. 구현 순서

1. `CrudLayoutMode` 추가
2. `generateThymeleafLayout` Tool 및 Service 추가
3. `generateThymeleafLayout`를 별도 Tool 클래스로 만들 경우 `McpConfig` 등록 추가
4. layout 파일 존재 확인 로직 추가
5. `CrudLayerDefinition`의 Thymeleaf 레이어를 layout/screen으로 분리하고 `forViewType(viewType, layoutMode)` 단일 진입점 제공
6. `CrudOrchestrationService`에 `layoutMode` 반영
7. `BoardLayerDefinition`, `BoardOrchestrationService`에 동일 반영
8. `MasterDetailLayerDefinition`, `MasterDetailOrchestrationService`에 동일 반영
9. MasterDetail의 `EgovMainController.java`는 layoutMode와 독립된 auxiliary 생성물로 유지
10. 화면 FTL에 `layoutView`, `breadcrumbView` 반영
11. `default.html.ftl` 내부 partial 참조에 `layoutBasePath` 반영
12. `layoutMode=none`용 화면 생성 정책 결정 및 구현
13. `generateThymeleafLayout` Tool description에 `overwriteLayout` 적용 조건 명시
14. Tool description, README, workflow 문서, `WorkflowGuideTool`, `WorkflowGuideService.suggestNextStep`, `CLAUDE.md` 갱신
15. 테스트 수정 및 전체 테스트 실행

## 10. 1차 구현 범위 제안

1차에서는 `layoutMode=reuse/create`만 구현하고 `none`은 2차로 미루는 것을 권장한다.

이유:

- `reuse/create`는 레이어 저장 정책 변경이 핵심이다.
- `none`은 화면 FTL 구조를 별도로 바꿔야 하므로 영향 범위가 더 크다.

1차 구현:

- `generateThymeleafLayout`
- `layoutMode=reuse` 기본값
- `layoutMode=create`
- `generateThymeleafLayout(overwriteLayout=false)` 기본값
- layout 파일 없을 때 실패 안내
- `McpConfig` 등록
- `CLAUDE.md` MCP Tool 목록 갱신
- MasterDetail `EgovMainController.java` 유지
- `WorkflowGuideTool` / `WorkflowGuideService.suggestNextStep` 안내 갱신
- `overwriteLayout`은 `generateThymeleafLayout`에서만 적용되도록 description과 테스트 반영

2차 구현:

- `layoutMode=none`
- layout 없는 독립 화면 템플릿
- breadcrumb partial 제거

## 11. 결정 필요 항목

| 항목 | 권장값 | 이유 |
|---|---|---|
| `layoutMode` 기본값 | `reuse` | layout 덮어쓰기 문제를 기본 동작에서 차단 |
| layout 파일 없을 때 | 실패 | 런타임 오류가 나는 화면 생성 방지 |
| `footer.html` 생성 여부 | 포함 | 현재 `default.html`이 footer partial을 참조 |
| `overwriteLayout` 기본값 | `false` | `generateThymeleafLayout` 재실행 시 사용자가 수정한 layout 보호 |
| `layoutMode=none` 구현 시점 | 2차 | 화면 템플릿 구조 변경 영향이 큼 |
| `generateThymeleafLayout` 구현 위치 | 별도 Tool 클래스 | `CrudPromptBuilderTool` 비대화 방지. 단 `McpConfig` 등록 필수 |
| layout 파일 없을 때 확인 위치 | OrchestrationService 진입부 | 일부 파일만 저장된 실패 상태 방지 |
| `EgovMainController.java` | auxiliary 생성물로 유지 | layoutMode와 책임이 다름 |
| `CLAUDE.md` 갱신 | 포함 | MCP Tool 개수와 목록 불일치 방지 |
| 레이어 접근 방식 | `forViewType(viewType, layoutMode)` 단일 진입점 | 정책별 상수 직접 사용으로 인한 호출 혼선 방지 |
| 화면 layout 경로 파라미터명 | `layoutView`, `breadcrumbView` | `layoutTemplate` 이름 충돌 방지 |
| `overwriteLayout` 적용 범위 | `generateThymeleafLayout` 전용 | `build*`에서 의미 없는 조합 방지 |
| workflow Tool 갱신 | 포함 | 새 순서인 `generateThymeleafLayout` 후 `build*` 실행을 Tool 안내와 일치 |

## 12. 결론

`layoutMode` 기본값을 `reuse`로 변경하는 것은 현재 문제를 해결하는 방향으로 타당하다.

다만 단순히 layout 레이어를 제거하는 변경만으로는 충분하지 않다. layout이 없는 프로젝트에서 화면만 생성되는 실패 상태를 막기 위해 `generateThymeleafLayout` Tool과 layout 존재 검증을 함께 넣어야 한다.

권장 1차 변경 단위:

- `generateThymeleafLayout` 신규 추가
- `layoutMode=reuse` 기본값 적용
- `layoutMode=create` 지원
- `overwriteLayout=false` 기본값 적용
- `build*` Tool의 기본 Thymeleaf 생성에서 layout 파일 제외
- layout 파일이 없으면 명확한 안내와 함께 실패
- 신규 Tool의 `McpConfig` 등록 및 `CLAUDE.md` Tool 목록 갱신
- `EgovMainController.java`는 MasterDetail auxiliary 생성물로 유지
- `WorkflowGuideTool`과 `WorkflowGuideService.suggestNextStep`에 새 순서 반영
