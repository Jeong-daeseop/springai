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
  layoutTemplate = "layout/default",
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
| `create` | layout 파일까지 생성한다. `overwriteLayout=false`면 기존 layout은 보호한다. |
| `none` | layout 참조 없이 순수 화면 파일을 생성한다. |

추가 파라미터 후보:

```text
layoutTemplate = "layout/default"
breadcrumbTemplate = "layout/breadcrumb"
overwriteLayout = false
```

### 4.3 주어진 layout 정보 사용

사용자가 layout 정보를 주는 경우:

```text
layoutTemplate = "layout/admin/default"
breadcrumbTemplate = "layout/admin/breadcrumb"
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
THYMELEAF_LAYERS_CREATE = COMMON + LAYOUT + SCREEN
THYMELEAF_LAYERS_REUSE = COMMON + SCREEN
THYMELEAF_LAYERS_NONE = COMMON + SCREEN
```

또는 메서드로 분리:

```java
forViewType(viewType, layoutMode)
layoutLayers()
screenLayers(viewType)
```

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

- `layoutTemplate`, `breadcrumbTemplate`, `layoutMode`를 FreeMarker 데이터 모델에 전달
- 화면 FTL에서 하드코딩된 `layout/default`, `layout/breadcrumb` 대신 전달값 사용

현재 하드코딩 예:

```html
layout:decorate="~{layout/default}"
<th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>
```

변경 후보:

```html
layout:decorate="~{${layoutTemplate}}"
<th:block th:replace="~{${breadcrumbTemplate} :: breadcrumb}"></th:block>
```

단, Thymeleaf 표현식과 FreeMarker 치환 구문이 겹치지 않도록 실제 FTL 문법 검증이 필요하다.

### 5.5 Tool 변경

대상:

- `CrudPromptBuilderTool`

변경:

- `generateThymeleafLayout` 신규 Tool 추가
- `buildFullCrudPrompt` 파라미터에 `layoutMode`, `layoutTemplate`, `breadcrumbTemplate`, `overwriteLayout` 추가
- `buildBoardFeature` 파라미터에 동일 추가
- `buildMasterDetailPrompt` 파라미터에 동일 추가
- Tool description의 생성 파일 수 수정

### 5.6 프롬프트 모드 변경

대상:

- `CrudPromptBuilderService`
- `MasterDetailService`

`llmProvider="claude"` 모드에서도 안내 문구를 변경해야 한다.

현재 Thymeleaf 안내는 layout 파일 생성을 전제로 한다.

변경:

- `layoutMode=reuse`: layout 파일 생성 Step 제거
- `layoutMode=create`: layout 파일 생성 Step 유지
- `layoutMode=none`: `layout:decorate` 사용 금지 안내

## 6. 생성 파일 수 변화

`layoutMode=reuse` 기본값 기준:

| 생성 경로 | 현재 Thymeleaf 생성 수 | 변경 후 기본 생성 수 | 비고 |
|---|---:|---:|---|
| 단일 CRUD | 16 | 11 | layout 5개 제외 |
| Board | 17 | 12 | layout 5개 제외 |
| MasterDetail | 18 | 13 | layout 5개 제외 |

`layoutMode=create` 사용 시:

| 생성 경로 | 생성 수 |
|---|---:|
| 단일 CRUD | 16 |
| Board | 17 |
| MasterDetail | 18 |

`MasterDetailOrchestrationService`는 추가로 `EgovMainController.java`를 생성하므로 결과 요약의 성공 파일 수는 위 표보다 1개 많을 수 있다.

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
- `layoutTemplate` 지정 시 화면 소스의 `layout:decorate` 값 검증
- `layoutMode=none` 지정 시 화면 소스에 `layout:decorate`와 breadcrumb partial 참조가 없는지 검증

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
3. layout 파일 존재 확인 로직 추가
4. `CrudLayerDefinition`의 Thymeleaf 레이어를 layout/screen으로 분리
5. `CrudOrchestrationService`에 `layoutMode` 반영
6. `BoardLayerDefinition`, `BoardOrchestrationService`에 동일 반영
7. `MasterDetailLayerDefinition`, `MasterDetailOrchestrationService`에 동일 반영
8. 화면 FTL에 `layoutTemplate`, `breadcrumbTemplate` 반영
9. `layoutMode=none`용 화면 생성 정책 결정 및 구현
10. Tool description, README, workflow 문서 갱신
11. 테스트 수정 및 전체 테스트 실행

## 10. 1차 구현 범위 제안

1차에서는 `layoutMode=reuse/create`만 구현하고 `none`은 2차로 미루는 것을 권장한다.

이유:

- `reuse/create`는 레이어 저장 정책 변경이 핵심이다.
- `none`은 화면 FTL 구조를 별도로 바꿔야 하므로 영향 범위가 더 크다.

1차 구현:

- `generateThymeleafLayout`
- `layoutMode=reuse` 기본값
- `layoutMode=create`
- `overwriteLayout=false`
- layout 파일 없을 때 실패 안내

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
| `overwriteLayout` 기본값 | `false` | 사용자가 수정한 layout 보호 |
| `layoutMode=none` 구현 시점 | 2차 | 화면 템플릿 구조 변경 영향이 큼 |

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
