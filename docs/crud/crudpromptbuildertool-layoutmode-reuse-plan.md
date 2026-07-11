# layoutMode=reuse 기본값 도입 — 구현 계획 및 완료 기록

## 구현 완료 상태

상태: **1차 구현 완료**

완료 내용:

- `generateThymeleafLayout` 신규 MCP Tool 추가
- `layoutMode` 기본값 `reuse` 적용
- `layoutMode=reuse`에서 layout 부재 시 저장 전 실패 + `generateThymeleafLayout(...)` 안내
- `layoutMode=create`에서 layout 레이어 포함 생성
- `layoutView`, `breadcrumbView`, `layoutBasePath` 동적화
- `default.html.ftl` 내부 `gnb/lnb/footer` partial 참조를 `layoutBasePath` 기준으로 동적화
- CRUD/Board/MasterDetail 자동 생성 경로 반영
- CRUD/MasterDetail Claude 프롬프트 모드 반영
- `CrudPromptBuilderTool`의 `buildFullCrudPrompt`, `buildMasterDetailPrompt`, `buildBoardFeature` optional 파라미터 반영
- `ProjectInitializrTool` 결과 후속 안내, workflow registry, `CLAUDE.md` Tool 목록 반영
- 기존 테스트 갱신 및 전체 테스트 통과

검증:

```bash
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

남은 항목:

- `layoutMode=none`은 2차 구현 대상이다.
- MCP 실서버를 통한 수동 E2E는 별도 확인이 필요하다.
- **정정(2차 검토 중 재확인):** `CrudPromptBuilderService`(Claude 프롬프트 모드) 전용 테스트가 없고, `MasterDetailServiceTest`엔 create 모드 테스트가 빠져 있다. "기존 테스트 갱신 및 전체 테스트 통과"는 auto(orchestration) 경로 기준이며, Claude 프롬프트 모드의 reuse/create 분기는 실질적으로 테스트 보호를 받지 못하는 상태다(§10 참조).
- **정정(추가 재확인, 이후 보강 완료):** `BoardOrchestrationServiceTest`(당시 10개)·`MasterDetailOrchestrationServiceTest`(당시 3개)는 `CrudOrchestrationServiceTest`(13개)에 있는 저장 실패/렌더 예외/검증 예외/이력 예외 테스트가 없었고, MasterDetail은 create 모드·jsp 모드·tableNotFound 테스트도 없어 세 도메인 중 가장 얇았다. Board에 4개, MasterDetail에 9개(에러 처리 4개 + create/jsp 모드·tableNotFound·ensureThymeleafRuntime 5개) 테스트를 추가해 현재 Board 14개·MasterDetail 12개·CRUD 13개로 격차를 해소했다(§10 참조).

## Context

`CrudPromptBuilderTool`의 Thymeleaf 자동 생성(`llmProvider=auto`)과 Claude 프롬프트 모드(`llmProvider=claude`)는
매 CRUD/Board/MasterDetail 생성마다 공통 layout 파일 5종(`layout/default·gnb·lnb·breadcrumb·footer.html`)을
함께 생성한다. 프로젝트에 화면을 추가할 때마다 layout이 다시 생성되어, **사용자가 수정한 layout이나 앞선
기능에서 조정한 메뉴가 덮어써지는 문제**가 있다.

해결: `layoutMode`(reuse|create|none) 파라미터를 도입하고 **기본값을 reuse**로 두어, 기본 생성에서는 layout을
건드리지 않는다. layout 전용 신규 툴 `generateThymeleafLayout`을 분리한다. 추가로 화면이 참조할 layout 경로를
`layoutView`/`breadcrumbView`로 지정할 수 있게 한다.

**설계 검증 결과(코드 대조 완료):** layout FTL 5종은 CRUD 필드·테이블 같은 화면 도메인 값을 하드코딩하지 않는다.
LNB는 Controller 모델 계약(`lnbTitle`, `lnbMenus`, `currentMenuId`)을 Thymeleaf 런타임 식(`${r"..."}` pass-through)으로
사용하므로 "한 번 생성해 공유"하는 reuse 모델이 정합적이다.
(참조: `crud/layout/gnb.html.ftl:24-26`, `crud/layout/lnb.html.ftl:3-9`)

유의: `crud/board/masterdetail`의 `gnb.html.ftl`은 2번째 GNB 메뉴 active 조건이 각각 `crud-`/`board-`/`masterdetail-`로
하드코딩되어 완전히 동일하지 않다. `generateThymeleafLayout` 1차 구현은 `crud/layout/*.html.ftl`을 렌더링 소스로
사용하되, 공유 GNB를 다른 화면군에서 재사용하면 해당 메뉴 active 하이라이트가 기대와 다를 수 있음을 알려진 제약으로 둔다.

## 사용자 결정 사항

- **layout 부재 시(reuse):** 실패 + 안내. layout 파일이 없으면 저장 전에 실패하고 `generateThymeleafLayout` 실행을 안내한다.
- **1차 범위:** 커스텀 경로 포함. `layoutView`/`breadcrumbView` 동적 치환까지 1차에 넣는다. (`none` 모드만 2차로 미룸)

## 문서 대조 결과 (정확도)

정확: 레이어 수 CRUD=16 / Board=17 / MasterDetail=18 (각 5개가 layout 레이어). CRUD·MasterDetail claude 모드 프롬프트가 layout 생성을 안내함. 화면 FTL이 `~{layout/default}`·`~{layout/breadcrumb}`를 리터럴로 하드코딩함.

정정/유의:
- **Board는 claude 모드가 없다.** `buildBoardFeature`는 항상 `orchestrate`(결정적)만 탄다 → §5.6 프롬프트 변경 대상은 CRUD·MasterDetail 둘뿐. Board는 orchestration 변경만 필요.
- **WorkflowGuideTool/suggestNextStep에는 현재 layout 단계가 없다.** 다만 `ProjectInitializrTool`은 layout 파일을 생성하지 않으므로, `layoutMode=reuse` 기본값에서는 신규 프로젝트의 첫 Thymeleaf 생성이 표준적으로 "layout 부재 실패 + 안내" 경로를 탄다. 최초 실행 경험을 줄이기 위해 `generateThymeleafLayout` 선행 안내를 1차 범위에 포함한다.
- **MasterDetail 저장 파일 수는 layer 수 +1.** `EgovMainController.java`가 `MasterDetailOrchestrationService.updateDefaultMainController`(라인 133-165)에서 생성되어 `succeeded`에 추가됨(라인 162). 따라서 reuse=13레이어→저장 14, create=18레이어→저장 19. layoutMode와 무관하게 유지.
- **공유 base renderer 없음.** 3개 renderer가 각자 `toDataModel`을 가짐 → 동일 변경을 3곳에 반복.
- **templates/{boot,war}-thymeleaf/layout/*.html은 ProjectInitializrService/FilePlanFactory에서 참조하지 않는다.** 새 프로젝트 초기화만으로는 layout이 준비되지 않는다. 따라서 `generateThymeleafLayout`은 선택 보조 Tool이 아니라 Thymeleaf 생성 workflow의 선행 단계다.

## 구현 계획

### 1. `CrudLayoutMode` enum 신규
`src/main/java/com/krdevops/springai/model/crud/CrudLayoutMode.java`
- 값: `REUSE`, `CREATE`, `NONE`. `from(String)`으로 정규화(null/blank → `REUSE`), 잘못된 값은 명확한 예외 메시지.
- 3개 도메인 공용으로 사용(별도 Board/MasterDetail용 enum 만들지 않음).

구현 결과: **완료**

### 2. 레이어 정의 분리 (layout 레이어 식별)
`CrudLayerDefinition.java` · `BoardLayerDefinition.java` · `MasterDetailLayerDefinition.java`
- 5개 layout layerKey(`layoutHtml`, `layoutGnbHtml`, `layoutLnbHtml`, `layoutBreadcrumbHtml`, `layoutFooterHtml`)를 식별하는 상수/헬퍼 추가: 예) `static final Set<String> LAYOUT_LAYER_KEYS`, `boolean isLayoutLayer(String key)`.
- `forViewType`는 그대로 두고(전체 레이어 반환), **필터링은 OrchestrationService에서** 수행(레이어 목록 상수를 mode별로 복제하지 않음 — 문서 §5.2의 `REUSE`/`NONE` 상수 중복 문제 회피).

구현 결과: **완료**

### 3. OrchestrationService — layoutMode 반영 + 부재 검증 (3개)
`CrudOrchestrationService.java` · `BoardOrchestrationService.java` · `MasterDetailOrchestrationService.java`
- `orchestrate(...)`에 `layoutMode`, `layoutView`, `breadcrumbView` 파라미터 추가(기존 시그니처는 오버로드로 하위호환 유지).
- 저장 루프(예: `CrudOrchestrationService` 라인 80-100) 진입 전:
  - **부재 검증:** `layoutMode=reuse`이면 `layoutView`에서 base path를 해석하고, resolved base 아래의 5개 파일(`default.html`, `gnb.html`, `lnb.html`, `breadcrumb.html`, `footer.html`) 전체를 확인한다. 예를 들어 `layoutView="layout/admin/default"`이면 `{outputPath}/src/main/resources/templates/layout/admin/*.html` 5종이 모두 필요하다. 누락 시 **저장 전에 실패 결과 반환** + `generateThymeleafLayout(outputPath=..., layoutBasePath="layout/admin")` 실행 안내 메시지.
  - `breadcrumbView`는 resolved base의 `breadcrumb.html`과 일치해야 한다. 기본값이면 `layout/default` + `layout/breadcrumb`를 사용한다.
  - **필터:** 루프 상단에서 `isLayoutLayer(key) && layoutMode!=CREATE`면 `continue`(reuse/none은 layout 저장 제외).
- `EgovMainController.java`(MasterDetail)는 layoutMode와 무관하게 기존대로 생성.
- 공통 부재 검증은 `ThymeleafLayoutValidator`(신규, service 패키지)로 추출해 3곳에서 재사용.

구현 결과: **완료**

### 4. `generateThymeleafLayout` 신규 툴 + McpConfig 등록
- 신규 클래스 `src/main/java/com/krdevops/springai/tools/ThymeleafLayoutTool.java` (`@Component`, `@Tool`).
  - 파라미터: `outputPath`, `layoutBasePath="layout"`, `overwriteLayout=false`.
  - 1차에서는 `includeFooter`를 제공하지 않는다. `default.html`이 `footer.html`을 참조하므로 layout 5종을 항상 생성한다.
  - `layoutBasePath="layout"`이면 `{outputPath}/src/main/resources/templates/layout/*.html`에 저장한다.
  - `layoutBasePath="layout/admin"`이면 `{outputPath}/src/main/resources/templates/layout/admin/*.html`에 저장한다.
  - `overwriteLayout=false`면 기존 파일을 보존한다.
  - 생성 후 필수 layout 파일 존재를 검증한다.
  - 렌더링은 기존 도메인 모델 기반 `CrudTemplateRenderer.renderByLayerKey("layoutHtml", model)`를 직접 재사용하지 않는다. layout 전용 렌더링 메서드를 추가해 도메인 없는 Map 데이터 모델로 렌더링한다.
- `src/main/java/com/krdevops/springai/config/McpConfig.java`: `allToolCallbacks` 빈 파라미터와 `toolObjects(...)`에 `thymeleafLayoutTool` 추가.

구현 결과: **완료**

- `ThymeleafLayoutTool.generateThymeleafLayout(outputPath, layoutBasePath, overwriteLayout)` 추가
- `CrudTemplateRenderer.renderLayoutByLayerKey(layerKey, layoutBasePath)` 추가
- `overwriteLayout=false`일 때 기존 파일 보존
- 생성 후 `ThymeleafLayoutValidator`로 layout 5종 검증

### 5. Renderer + 화면 FTL — 커스텀 layout 경로 동적화
`CrudTemplateRenderer.java` · `BoardTemplateRenderer.java` · `MasterDetailTemplateRenderer.java`
- 각 `toDataModel(...)`에 키 추가: `layoutView`(기본 `"layout/default"`), `breadcrumbView`(기본 `"layout/breadcrumb"`).
- 화면 FTL 11종(라인 5, 10) 하드코딩을 동적 치환으로 변경:
  - `layout:decorate="~{layout/default}"` → `layout:decorate="~{${layoutView}}"`
  - `<th:block th:replace="~{layout/breadcrumb :: breadcrumb}">` → `~{${breadcrumbView} :: breadcrumb}`
  - 대상: `crud/thymeleaf-{list,detail,regist,updt}.html.ftl`, `board/thymeleaf-{list,detail,regist,updt}.html.ftl`, `masterdetail/thymeleaf-{list,detail,regist}.html.ftl`
- `default.html.ftl` 내부 partial 참조도 `layoutBasePath` 기준으로 동적 치환한다.
  - `<th:block th:replace="~{layout/gnb :: gnb}">` → `~{${layoutBasePath}/gnb :: gnb}`
  - `<th:block th:replace="~{layout/lnb :: lnb}">` → `~{${layoutBasePath}/lnb :: lnb}`
  - `<th:block th:replace="~{layout/footer :: footer}">` → `~{${layoutBasePath}/footer :: footer}`
  - 이렇게 해야 `generateThymeleafLayout(layoutBasePath="layout/admin")`으로 생성한 `layout/admin/default.html`이 같은 base의 `gnb/lnb/footer`를 참조한다.
- `${layoutView}`는 FreeMarker 렌더 시점 치환(런타임 Thymeleaf `${r"..."}`와 실행 시점이 달라 충돌 없음). 검증은 "렌더 결과에 미치환 `${layoutView}`이 남지 않는지"로 한다.

구현 결과: **완료**

### 6. Claude 프롬프트 모드 (CRUD·MasterDetail만)
`CrudPromptBuilderService.java`(라인 248-260) · `MasterDetailService.java`(라인 91-92, 120-124, 365-411)
- `layoutMode=reuse`: layout 파일 생성 안내 Step 제거, 화면 상단 `layout:decorate` 안내는 유지(참조는 함).
- `layoutMode=create`: 기존 layout 생성 안내 유지.
- 파일 목록/개수 텍스트를 mode에 맞게 조정(예: MasterDetail reuse는 13개, create는 18개).

구현 결과: **완료**

### 7. Tool 파라미터 추가
`CrudPromptBuilderTool.java` — `buildFullCrudPrompt`, `buildMasterDetailPrompt`, `buildBoardFeature`에
`@Nullable layoutMode`, `@Nullable layoutView`, `@Nullable breadcrumbView` 추가(모두 optional).
- `overwriteLayout`은 `generateThymeleafLayout`에만 노출(문서 정합성). `build*`에는 넣지 않음 — description에 "layout은 `generateThymeleafLayout`로 생성" 명시.
- 각 `@Tool` description의 생성 파일 수/설명(라인 57·122·186 부근) 갱신.

구현 결과: **완료**

### 8. WorkflowGuideTool / 최초 실행 안내 갱신

`ProjectInitializrTool`은 layout 파일을 생성하지 않는다. 따라서 `layoutMode=reuse` 기본값을 적용하면 신규 프로젝트에서 첫 Thymeleaf CRUD 생성은 layout 부재로 실패하는 것이 정상 경로가 된다.

이 경험을 줄이기 위해 1차 구현에 아래 변경을 포함한다.

- `WorkflowGuideService.suggestNextStep`에서 `viewType=thymeleaf`, `Thymeleaf`, `templates/layout` 문맥을 감지하면 `generateThymeleafLayout` 선행 실행 안내
- `ProjectInitializrTool` 결과의 다음 단계 문구에 Thymeleaf 선택 시 `generateThymeleafLayout` 필요성을 추가
- README/사용가이드에 표준 순서 명시

구현 결과:

- **완료:** `ResultBuilder`의 `initializeProject` 결과 다음 단계에 `generateThymeleafLayout` 선행 안내 추가
- **완료:** `WorkflowDefinitionRegistry`의 `project-setup-crud` workflow에 `generateThymeleafLayout` 단계 추가
- **완료:** `CLAUDE.md` Tool 목록을 20개로 갱신하고 `ThymeleafLayoutTool` 추가
- **완료:** `WorkflowDefinitionRegistry`에 `crud-thymeleaf` workflow(15단계, 2번째 단계가 `generateThymeleafLayout`)를 신규 추가하고, `WorkflowGuideService.suggestNextStep`이 문맥에서 `thymeleaf`/`templates/layout` 언급을 감지하면 이 workflow로 전환하도록 반영. 기존 `suggestNextStep("")` 기본 동작(JSP 14단계)은 그대로 유지

표준 Thymeleaf 순서:

```text
1. initializeProject(...)
2. generateThymeleafLayout(outputPath=..., layoutBasePath="layout")
3. buildFullCrudPrompt/buildBoardFeature/buildMasterDetailPrompt(..., viewType="thymeleaf", layoutMode="reuse")
```

## 구현 전 보완사항

아래 항목은 구현 착수 전에 계획에 반영하거나 구현 중 첫 단계에서 결정해야 한다.

### 1. layout 경로 파라미터 이름 통일

기존 초안의 `layoutTemplate`/`breadcrumbTemplate`는 FreeMarker 템플릿 파일명 또는 layout 생성 템플릿과 혼동될 수 있다.

결정:

- 화면이 참조할 Thymeleaf layout 경로는 `layoutView` 사용
- breadcrumb partial 경로는 `breadcrumbView` 사용
- 문서, Tool description, renderer data model, 테스트 모두 같은 이름 사용

### 2. `layoutMode=create`와 `overwriteLayout` 정책 정합성

이전 초안에는 `overwriteLayout`을 `generateThymeleafLayout`에만 노출하면서, 수동 E2E에는 `buildFullCrudPrompt(..., layoutMode=create)`가 `overwriteLayout` 정책대로 동작한다고 적혀 있어 모순이 있었다.

1차 결정:

- `overwriteLayout`은 `generateThymeleafLayout`에만 노출한다.
- `build*` Tool은 기본적으로 layout을 생성하지 않는다.
- `layoutMode=create`를 1차에 유지한다면, 기존 layout 파일이 있을 때는 덮어쓰지 않는 보존 정책으로 고정한다.
- 기존 layout을 강제로 갱신해야 할 때는 `generateThymeleafLayout(overwriteLayout=true)`를 먼저 실행하도록 안내한다.

향후 대안:

- `build*`에서 `layoutMode=create`를 제거하고 `reuse`만 기본 지원한다.
- 이 경우 layout 생성 책임은 `generateThymeleafLayout`로 완전히 분리된다.

### 3. layout 렌더링용 renderer/model 방식 결정

`CrudTemplateRenderer.renderByLayerKey("layoutHtml", model)`를 재사용하려면 `CrudTemplateModel`이 필요하다. 현재 renderer의 `toDataModel()`은 layout FTL이 도메인 값을 쓰지 않아도 `model.packageName()`, `model.pk()` 등 여러 필드를 무조건 읽는다.

구현 선택지:

- 더미 `CrudTemplateModel`을 완전하게 만들어 전달
- `CrudTemplateRenderer`에 layout 전용 `renderLayoutByLayerKey(layerKey)` 추가
- 공통 `renderByLayerKey(String layerKey, Map<String, Object> dataModel)` 추가

권장:

- layout 전용 렌더링 메서드를 추가한다.
- 도메인 모델이 필요 없는 layout 생성에 더미 CRUD 모델을 쓰지 않는다.

계획 반영:

- 1차 구현의 `ThymeleafLayoutTool`은 layout 전용 렌더링 메서드를 사용한다.
- 기존 `CrudTemplateRenderer.renderByLayerKey(layerKey, CrudTemplateModel)`에 더미 모델을 넘기지 않는다.

### 4. layout 없음 실패 결과 표현 방식

현재 `CrudOrchestrationResult`, `BoardOrchestrationResult`, `MasterDetailOrchestrationResult`는 `tableNotFound`만 별도 실패 상태로 가진다. layout 없음은 테이블 미존재가 아니므로 별도 표현이 필요하다.

구현 선택지:

- `layoutMissing` 같은 상태 필드 추가
- `generationBlockedReason` 같은 범용 실패 사유 필드 추가
- `failedFiles`에 사유를 넣고 `formatResult()`가 성공 헤더 대신 실패 헤더를 표시하도록 수정

권장:

- 범용 실패 사유 필드를 추가하거나, 최소한 `failedFiles`만 있고 `succeededFiles`가 비어 있으면 "생성 실패"로 포맷한다.
- layout 없음 상태에서 일부 파일이 저장되는 일이 없도록 저장 루프 전에 반환한다.

### 5. `includeFooter` 1차 범위 정리

`generateThymeleafLayout` 파라미터에 `includeFooter=true`가 있지만, 현재 `default.html`은 `layout/footer`를 참조한다.

문제:

- `includeFooter=false`를 지원하려면 `footer.html` 생성을 빼는 것만으로는 부족하다.
- `default.html`의 footer include도 조건부 제거되어야 한다.

권장:

- 1차에서는 `includeFooter` 파라미터를 제거한다.
- layout 5종을 항상 생성한다.
- footer 제외 옵션은 2차에서 `default.html` 템플릿 분기와 함께 구현한다.

계획 반영:

- `generateThymeleafLayout` 1차 파라미터에서 `includeFooter`를 제거한다.
- layout 필수 파일은 5종으로 고정한다.

### 6. 커스텀 layout 경로와 `generateThymeleafLayout` 범위 정합성

1차 범위가 커스텀 `layoutView`/`breadcrumbView`를 포함한다면, layout 생성 Tool도 커스텀 경로를 생성할 수 있어야 한다.

이전 초안의 문제:

- `build*`는 `layoutView="layout/admin/default"`를 받을 수 있다.
- 하지만 `generateThymeleafLayout`은 고정 경로 `templates/layout/*.html`만 생성한다.

권장:

- `generateThymeleafLayout`에 `layoutBasePath = "layout"` 파라미터를 추가한다.
- `layoutBasePath="layout/admin"`이면 아래 파일을 생성한다.

```text
src/main/resources/templates/layout/admin/default.html
src/main/resources/templates/layout/admin/gnb.html
src/main/resources/templates/layout/admin/lnb.html
src/main/resources/templates/layout/admin/breadcrumb.html
src/main/resources/templates/layout/admin/footer.html
```

- `layoutView`, `breadcrumbView`는 `layoutBasePath`와 일치하도록 안내한다.
- `default.html`이 참조하는 `gnb/lnb/footer` partial도 같은 `layoutBasePath` 아래를 보도록 렌더링한다.
- layout 부재 검증도 `layoutView`/`breadcrumbView` 2개 파일만 보지 않고, resolved base 아래 5개 파일 전체를 확인한다.

계획 반영:

- `generateThymeleafLayout` 1차 파라미터에 `layoutBasePath`를 추가한다.
- 기본 `layoutView="layout/default"`, `breadcrumbView="layout/breadcrumb"`는 `layoutBasePath="layout"`과 대응한다.
- 커스텀 `layoutView="layout/admin/default"`는 `layoutBasePath="layout/admin"`으로 layout을 먼저 생성한 뒤 사용한다.
- `default.html.ftl` 내부 partial 경로를 `layoutBasePath`로 치환한다.

구현 결과: **완료**

### 7. WorkflowGuideTool 갱신 여부

초안은 `WorkflowGuideTool/suggestNextStep`에 layout 단계가 없어 정정 불필요라고 판단했다.

다만 기본 워크플로가 `generateThymeleafLayout` 실행 후 `build*` 실행으로 바뀌므로, Tool 내부 안내가 Thymeleaf 생성 경로를 다루는 경우 갱신 대상에 포함하는 것이 안전하다.

권장:

- `WorkflowGuideService.suggestNextStep`에서 `viewType=thymeleaf` 또는 Thymeleaf 생성 문맥을 감지할 수 있으면 `generateThymeleafLayout` 선행 안내를 추가한다.
- 최소한 workflow 문서와 README에는 새 순서를 반영한다.

계획 반영:

- `WorkflowGuideTool` / `WorkflowGuideService.suggestNextStep` 갱신을 1차 구현 범위에 포함한다.
- `ProjectInitializrTool` 결과의 다음 단계 안내도 함께 갱신한다.

### 8. 보완사항 병합 상태

| 항목 | 계획 반영 위치 | 상태 |
|---|---|---|
| `layoutView`/`breadcrumbView` 이름 통일 | 구현 계획 3, 5, 7 | 완료 |
| `overwriteLayout`은 layout Tool 전용 | 구현 계획 4, 7 | 완료 |
| layout 전용 렌더링 메서드 | 구현 계획 4 | 완료 |
| layout 없음 실패 결과 | 구현 계획 3, 보완사항 4 | 완료: `failedFiles`만 있고 `succeededFiles`가 비어 있으면 Tool 포맷터가 생성 실패 헤더 출력 |
| `includeFooter` 제거 | 구현 계획 4 | 완료 |
| `layoutBasePath` 추가 | 구현 계획 4, 5, 보완사항 6 | 완료 |
| `default.html.ftl` 내부 partial 경로 동적화 | 구현 계획 5, 보완사항 6 | 완료 |
| resolved base 아래 layout 5종 검증 | 구현 계획 3, 보완사항 6, 테스트 | 완료: CRUD/Board/MasterDetail Orchestration 테스트에 커스텀 `layoutView`(`layout/admin/...`) 기준 부재 실패·전체 존재 성공 케이스 각각 추가 |
| WorkflowGuide 갱신 | 구현 계획 8 | 완료: registry workflow, Initializr 결과 안내, `suggestNextStep` 문맥 감지(`crud-thymeleaf`) 반영 |

### 9. 문서 갱신
- `CLAUDE.md` "현재 등록된 MCP Tool" 섹션: 툴 1개 추가 반영(개수·표).
- `docs/crud/crudpromptbuildertool-layoutmode-reuse-impact.md`에 위 정정사항(Board claude 모드 부재, WorkflowGuide 무관, EgovMainController +1) 반영.

구현 결과:

- 완료: `CLAUDE.md` Tool 개수 19개 → 20개, `ThymeleafLayoutTool` 추가
- 완료: `docs/crud/crudpromptbuildertool-layoutmode-reuse-impact.md` 갱신
- 완료: 본 문서에 구현 완료 상태 추가

### 10. 테스트
수정:
- `CrudOrchestrationServiceTest:140`(16→reuse 기본 11), `:142-146` layout 저장 검증 제거 → create 모드 테스트로 이동.
- `BoardOrchestrationServiceTest:77`(17→12), `:110-112` layout 검증을 create 모드로 이동.
- `BoardLayerDefinitionTest:13`: `THYMELEAF_LAYERS` 전체(17) 검증은 유지(정의는 안 줄임), layout 필터는 Orchestration 테스트에서 검증.
- `MasterDetailServiceTest:66-80`(claude): reuse 기본은 layout 미포함(13), create는 18 — 두 케이스로 분리.

**정정(2차 `layoutMode=none` 검토 중 재확인 — 실제로는 미반영):** 위 `MasterDetailServiceTest` create 케이스 분리와 "완료" 표시가 실제 코드와 다르다.
- `MasterDetailServiceTest.java`는 현재 테스트가 2개뿐이다: `buildMasterDetailPrompt_defaultViewTypeKeepsJspOutput`(jsp)과 `buildMasterDetailPrompt_thymeleafViewTypeReturnsLayoutAndHtmlInstructions`(thymeleaf, layoutMode 미전달 → reuse 기본값, "13개 파일"). **create 모드를 명시적으로 호출하는 테스트는 없다.**
- `CrudPromptBuilderService`는 **전용 단위 테스트 파일 자체가 없다.** `CrudPromptBuilderToolTest`가 `CrudPromptBuilderService`를 Mockito로 완전히 mock 처리하기 때문에, `buildFullCrudPrompt`의 layoutMode 분기(REUSE/CREATE 텍스트 차이, 레이어 필터 등) 로직은 어떤 테스트로도 직접 검증되지 않는다.
- 즉 이 두 서비스는 "Claude 프롬프트 모드"에서 REUSE/CREATE 텍스트를 만드는 실제 코드지만, 회귀를 잡아줄 자동 테스트가 사실상 없는 상태로 1차가 종료됐다. 2차(`layoutMode=none`) 착수 전에 최소한의 안전망(reuse/create 각 1개 테스트, `CrudPromptBuilderService` 전용 테스트 파일 신설)을 먼저 보강하는 것을 권장한다(자세한 내용은 [[crudpromptbuildertool-layoutmode-none-plan]] §0/테스트 계획 참조).

**정정(추가 재확인, 이후 보강 완료) — Board·MasterDetail의 Orchestration 테스트 자체도 CRUD 대비 얇았다.** `BoardOrchestrationService.java`/`MasterDetailOrchestrationService.java`는 `CrudOrchestrationService`와 완전히 동일한 에러 처리 코드(저장 실패 분기, 렌더링 예외 catch, 검증 예외 catch, 이력 저장 예외 catch)를 갖고 있었지만, 검증하는 테스트가 도메인마다 크게 차이났었다.

| | 발견 당시 `@Test` 개수 | 보강 후 | 추가한 테스트 |
|---|---:|---:|---|
| `CrudOrchestrationServiceTest` | 13 | 13 | (기준, 변경 없음) — 저장 실패/렌더 예외/검증 예외/이력 예외 4종 모두 있었음 |
| `BoardOrchestrationServiceTest` | 10 | 14 | `jsp_saveFails_recordsInFailedFiles`, `jsp_renderThrows_recordsInFailedFiles`, `jsp_validationThrows_resultStillReturned`, `jsp_historyThrows_resultStillReturned` 4종 추가 |
| `MasterDetailOrchestrationServiceTest` | 3 | 12 | 위 4종(저장 실패/렌더 예외/검증 예외/이력 예외) + `jsp_succeededFiles_is14`, `thymeleafCreateMode_succeededFiles_is19`, `tableNotFound_returnsNotFoundResult`, `thymeleaf_ensureThymeleafRuntime_isCalled`, `jsp_ensureThymeleafRuntime_notCalled` 총 9종 추가 |

`./gradlew build` 전체 통과 확인 완료. 이 공백은 이번 `layoutMode` 작업이 만든 것이 아니라 원래부터 있던 격차였지만, 1차/2차 작업 모두 이 3개 서비스의 같은 저장 루프·에러 처리 코드를 반복 수정하므로 회귀 안전망 관점에서 함께 보강했다.

신규:
- `ThymeleafLayoutTool` 저장/`overwriteLayout=false` 보존 테스트.
- reuse + layout 부재 → 실패 결과 반환 테스트(3개 서비스).
- `layoutView` 지정 시 화면 소스의 `layout:decorate` 값 검증, 미치환 `${layoutView}` 부재 검증.
- `layoutBasePath="layout/admin"` 생성 시 `layout/admin/default.html` 내부의 `gnb/lnb/footer` 참조가 모두 `layout/admin/*`로 생성되는지 검증.
- reuse + 커스텀 layout 경로에서 resolved base 아래 5종 중 하나라도 없으면 저장 전에 실패하는지 검증.
- MasterDetail auto 결과에 `EgovMainController.java`가 layoutMode 무관하게 유지되는지.
- `McpConfig`에 신규 툴 등록 확인.

구현 결과:

- **부분완료(정정):** Orchestration/Workflow 테스트는 새 정책에 맞게 갱신됨. 다만 Prompt 쪽(`CrudPromptBuilderService`/`MasterDetailService`)은 `MasterDetailServiceTest`에 create 모드 테스트가 빠져 있고 `CrudPromptBuilderService` 전용 테스트 파일 자체가 없어, "새 정책에 맞게 갱신"이라 보기 어렵다(위 §10 정정 참조).
- 완료: `ThymeleafLayoutTool` 단독 테스트 추가
- 완료: CRUD/Board/MasterDetail layout 부재 실패 전용 테스트 추가
- 완료: `layoutBasePath="layout/admin"` 렌더링 및 `default.html` 내부 partial 경로 테스트 추가
- 완료: `overwriteLayout=false` 기존 파일 보존 테스트 추가
- 완료: CRUD/Board/MasterDetail Orchestration에 커스텀 `layoutView`(`layout/admin/...`) 기준 "resolved base 5종 중 하나 누락 시 실패"·"5종 모두 존재 시 커스텀 경로로 렌더링 성공" 테스트 각 2건씩 추가
- 완료: `./gradlew test` 전체 통과

## 구현 순서

1. 완료: `CrudLayoutMode` + `ThymeleafLayoutValidator`
2. 완료: 레이어 정의에 `LAYOUT_LAYER_KEYS`/`isLayoutLayer` 추가 (3개)
3. 완료: layout 전용 렌더링 메서드 추가
4. 완료: `ThymeleafLayoutTool(layoutBasePath, overwriteLayout)` + `McpConfig` 등록
5. 완료: OrchestrationService 3개: layoutMode 필터 + 부재 검증 + 파라미터
6. 완료: Renderer 3개 `toDataModel`에 layoutView/breadcrumbView/layoutBasePath 키
7. 완료: 화면 FTL 11종과 `default.html.ftl` 내부 partial 참조 동적 치환
8. 완료(코드)/테스트 미흡: Claude 프롬프트(CRUD·MasterDetail) — 코드는 반영됐으나 회귀 테스트가 부족함(§10 정정 참조)
9. 완료: `build*` 툴 파라미터 + description
10. 완료: Workflow/Initializr 다음 단계 안내 갱신
11. 완료: CLAUDE.md·설계문서 갱신
12. 완료(auto 경로)/일부 누락(claude 경로): 테스트 수정 → 전체 실행 통과 — `./gradlew test`는 통과하지만, 이는 `CrudPromptBuilderService`/`MasterDetailService`의 reuse/create 분기가 애초에 테스트 대상이 아니어서 실패할 수조차 없었기 때문이다(§10 정정 참조)

## Verification

```bash
# 빌드 + 전체 테스트
./gradlew test

# 핵심 테스트만
./gradlew test --tests "*OrchestrationServiceTest" --tests "*LayerDefinitionTest" \
  --tests "*MasterDetailServiceTest" --tests "*ThymeleafLayoutTool*"
```

실행 결과:

```text
./gradlew test
BUILD SUCCESSFUL
```

주의:

- 이번 검증은 `./gradlew test` 기준이다.
- MCP 실서버 수동 E2E는 아직 별도 실행하지 않았다.

수동 E2E (MCP, egov-mysql·ollama 기동 후):
1. 신규 `outputPath`에 `buildFullCrudPrompt(viewType=thymeleaf, llmProvider=auto)` → **layout 부재로 실패 + `generateThymeleafLayout` 안내** 확인.
2. `generateThymeleafLayout(outputPath=..., layoutBasePath="layout")` → `templates/layout/*.html` 5종 생성 확인. 재실행 시 `overwriteLayout=false`로 미변경 확인.
3. 다시 `buildFullCrudPrompt(...)` → 화면/Java/Mapper만 생성(layout 미변경), `layout/default` 참조 정상 렌더 확인.
4. `buildFullCrudPrompt(..., layoutMode=create)` → layout까지 생성. 1차 권장안 기준으로 기존 layout 파일은 보존되고, 강제 갱신은 `generateThymeleafLayout(overwriteLayout=true)`로 수행.
5. `generateThymeleafLayout(outputPath=..., layoutBasePath="layout/admin")` 후 `buildFullCrudPrompt(..., layoutView="layout/admin/default", breadcrumbView="layout/admin/breadcrumb")` → 생성 화면의 `layout:decorate="~{layout/admin/default}"` 확인, `layout/admin/default.html` 내부의 `gnb/lnb/footer` 참조가 모두 `layout/admin/*`인지 확인, 미치환 `${layoutView}`/`${layoutBasePath}` 없음.
6. `suggestNextStep` 또는 ProjectInitializr 후속 안내에서 Thymeleaf 선택 시 `generateThymeleafLayout` 선행 안내 확인.
7. MasterDetail auto → `EgovMainController.java` 유지 확인(reuse=14, create=19 저장).
