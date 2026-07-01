# CrudPromptBuilderTool layout/default.html 적용 영향평가

작성일: 2026-06-22

## 목적

`/Users/jeongdaeseob/workspace-spring-ai/egov-boot-web/src/main/resources/templates/layout/default.html` 형태의 공통 Thymeleaf 레이아웃을 `CrudPromptBuilderTool`의 주요 메서드에 적용할 때의 영향 범위와 우선순위를 평가한다.

검토 대상:

- `buildFullCrudPrompt`
- `buildMasterDetailPrompt`
- `buildJoinSelectPrompt`
- `buildBoardFeature`

## 현재 상태 요약

| 메서드 | 현재 동작 | Thymeleaf 지원 | `layout/default.html` 생성 | 화면 `layout:decorate` 적용 |
|---|---|---:|---:|---:|
| `buildFullCrudPrompt` | CRUD 전체 자동 생성 또는 프롬프트 반환 | 지원 | 적용됨 | 적용됨 |
| `buildMasterDetailPrompt` | 마스터-디테일 생성 지시 프롬프트 반환 | 미지원 | 없음 | 없음 |
| `buildJoinSelectPrompt` | JOIN SELECT 수정 지시 프롬프트 반환 | 해당 없음 | 없음 | 없음 |
| `buildBoardFeature` | BBS 업무 단위 소스 자동 생성 | 지원 | 없음 | 없음 |

현재 공통 레이아웃 생성은 `buildFullCrudPrompt` 경로에만 구현되어 있다.

- `CrudLayerDefinition.THYMELEAF_LAYERS`에 `layoutHtml` 포함
- `CrudTemplateRenderer`에 `layoutHtml -> layout/default.html.ftl` 매핑 존재
- CRUD Thymeleaf 화면은 `layout:decorate="~{layout/default}"` 사용

반면 `buildBoardFeature`는 Thymeleaf 화면을 생성하지만 독립 HTML 구조이며, `layout/default.html` 생성 layer가 없다.

## 대상별 영향평가

### 1. buildFullCrudPrompt

현재 이미 적용되어 있다.

적용 상태:

- Thymeleaf 선택 시 `layout/default.html` 포함 12개 파일 생성
- 목록/상세/등록/수정 화면에서 `layout:decorate="~{layout/default}"` 사용
- `ThymeleafRuntimeConfigurer`가 Maven 의존성과 ViewResolver 설정을 보강

적용 경로는 두 갈래다.

- `llmProvider="auto"`: `CrudOrchestrationService.orchestrate(...)`가 `CrudLayerDefinition.forViewType(...)` 기준으로 FTL을 직접 렌더링하고 파일을 저장한다.
- `llmProvider="claude"`: `CrudPromptBuilderService.appendViewTypeInstruction(...)`가 `layout/default.html` 생성 지시와 `layout:decorate` 지시를 프롬프트에 포함한다.

따라서 layout 정책을 바꿀 때는 자동 생성 경로의 FTL/Layer 정의와 Claude 프롬프트 지시를 함께 맞춰야 한다.

추가 검토 사항:

- 현재 CRUD용 `layout/default.html.ftl`은 `egov-boot-web`의 layout과 완전히 동일하지 않다.
- 여러 기능이 같은 layout을 쓰려면 이 파일을 표준 layout 템플릿으로 정리해야 한다.
- WAR 프로젝트의 정적 리소스 경로는 `/resources/css/krds.min.css`, Boot 프로젝트는 `/css/krds.min.css`일 수 있어 경로 정책이 필요하다.

권장:

- 기존 적용은 유지한다.
- 공통 layout 표준화가 필요하면 CRUD layout 템플릿을 먼저 기준 템플릿으로 정비한다.

### 2. buildBoardFeature

적용 필요성이 가장 높다.

현재 문제:

- `viewType=thymeleaf`일 때 BBS 화면 HTML은 생성하지만, 공통 header/sidebar/content layout이 없다.
- 생성된 파일은 `src/main/resources/templates/bbs/EgovBbs*.html` 독립 HTML이다.
- 같은 프로젝트에서 CRUD는 layout 적용, BBS는 미적용이 되어 UI 일관성이 깨진다.

반영 범위:

- `BoardLayerDefinition.THYMELEAF_LAYERS`에 `layoutHtml` 추가
- `BoardLayerDefinition.resolveFileName()`에 `layoutHtml -> layout/default.html` 특수 케이스 추가
- `BoardTemplateRenderer`에 `layoutHtml` 매핑 추가
- `board/thymeleaf-list.html.ftl`
- `board/thymeleaf-detail.html.ftl`
- `board/thymeleaf-regist.html.ftl`
- `board/thymeleaf-updt.html.ftl`
- `CrudPromptBuilderTool.buildBoardFeature` 설명 문구의 생성 파일 수 갱신
- Board 생성 테스트 추가

예상 변경:

- BBS Thymeleaf 생성 파일 수: 12개 -> 13개
- 생성 경로에 `src/main/resources/templates/layout/default.html` 추가
- BBS 화면은 `layout:decorate="~{layout/default}"`와 `layout:fragment="content"` 구조로 변경

구현상 필수 확인 사항:

- `BoardTemplateRenderer`는 `@Qualifier("boardFreemarkerConfiguration")`을 사용한다.
- `boardFreemarkerConfiguration`의 template base는 `templates`이므로, 다음 중 하나를 명확히 선택해야 한다.
  - 기존 CRUD layout 재사용: `BoardTemplateRenderer`에 `layoutHtml -> crud/layout/default.html.ftl` 매핑
  - 공유 layout으로 이동: `templates/layout/default.html.ftl`을 만들고 CRUD/Board 양쪽 renderer가 공유
  - Board 전용 layout 사용: `templates/board/layout/default.html.ftl` 추가 후 매핑
- 현재 구조에서 `layoutHtml`을 `board/layout/default.html.ftl`로 매핑하려면 해당 파일이 실제로 필요하다.
- `BoardLayerDefinition.resolveFileName()`에 `layoutHtml` 케이스를 추가하지 않으면 default 분기 때문에 `Egov{Domain}layout/default.html` 같은 잘못된 파일명이 생성된다.
- 필요한 코드 형태는 CRUD와 동일하게 아래 분기를 추가하는 것이다.

```java
case "layoutHtml" -> "layout/default.html";
```

- `CrudPromptBuilderTool.buildBoardFeature` 설명에는 현재 파일 수 문구가 명확하지 않지만, 적용 후 운영자 혼선을 줄이려면 `Thymeleaf: layout/default.html 포함 13개` 같은 설명을 추가하는 것이 좋다.
- `buildBoardFeature`는 `llmProvider` 분기 없이 항상 `BoardOrchestrationService.orchestrate(...)`를 호출한다. 즉 Board FTL과 layer 정의를 수정하면 생성 결과에 바로 반영된다.
- `BoardOrchestrationService`는 `viewType=thymeleaf`일 때 이미 `ThymeleafRuntimeConfigurer.ensureThymeleafRuntime(...)`를 호출한다. 따라서 `layoutHtml` layer 추가 시 Maven 의존성 및 `servlet-context.xml` ViewResolver 보강 로직은 별도 신규 구현이 아니라 기존 경로를 재사용한다.
- layout FTL을 CRUD와 공유하거나 공통 경로로 이동할 경우, `BoardTemplateRenderer.toDataModel()`이 해당 layout FTL에서 참조하는 변수를 제공하는지 확인해야 한다. 현재 공통 후보 변수는 `packageName`, `domain`, `domainLc`, `domainKr`, `urlPrefix`, `date`, `egovVersion`, `jakartaValidation`이며, layout FTL이 정적 HTML 위주라면 호환 가능성이 높지만 구현 전 확인이 필요하다.
- `BoardLayerDefinition.THYMELEAF_LAYERS` 추가, `resolveFileName("layoutHtml")` 특수 케이스, `BoardTemplateRenderer` 매핑은 하나의 원자적 변경으로 배포해야 한다. 셋 중 하나라도 빠지면 잘못된 파일명 생성, `renderByLayerKey("layoutHtml")` 예외, 또는 런타임 layout 미존재 문제가 발생한다.
- 다만 그만큼 `board/thymeleaf-*.html.ftl` 4개는 단순히 `layout:decorate` 속성만 붙이는 수준이 아니라, 독립 HTML의 `<body><main>...</main></body>` 구조를 `layout:fragment="content"` 블록 중심으로 재배치해야 한다.

변경 전 Board Thymeleaf 구조:

```html
<html xmlns:th="http://www.thymeleaf.org">
<head>...</head>
<body>
<main>...</main>
</body>
</html>
```

변경 후 목표 구조:

```html
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>...</title>
</head>
<th:block layout:fragment="content">
    ...
</th:block>
</html>
```

리스크:

- 기존 프로젝트에 이미 `layout/default.html`이 있으면 덮어쓰기 가능성이 있다.
- CRUD와 BBS가 같은 layout을 공유할 경우 메뉴 활성화, 사이드바 항목, URL 경로 차이를 어떻게 처리할지 결정해야 한다.
- `egov-boot-web` layout을 그대로 복사하면 WAR 프로젝트의 정적 리소스 경로와 맞지 않을 수 있다.
- layout에서 Spring Security expression을 사용하면 보안 미설정 프로젝트에서 런타임 문제가 날 수 있다.

권장:

- 우선 `buildBoardFeature`에 반영한다.
- layout 파일은 CRUD와 공유 가능한 보수적 버전으로 둔다.
- 이미 layout 파일이 있는 경우 무조건 덮어쓰지 않는 정책을 검토한다.

### 3. buildMasterDetailPrompt

바로 반영하기에는 영향이 크다.

현재 구조:

- 자동 생성이 아니라 생성 지시 프롬프트를 반환한다.
- `viewType` 파라미터가 없다.
- JSP 중심 생성 지시가 고정되어 있다.
- `CrudPromptBuilderTool`의 `@Tool` 설명은 "총 12개"라고 안내하지만, `MasterDetailService.buildMasterDetailPrompt()` 본문은 현재 `[생성 파일 목록 — 13개]`와 Step 1~13을 출력한다. layout 적용 이전에 이 파일 수 불일치도 함께 정리해야 한다.
- Detail JSP 안에 마스터 정보와 디테일 그리드 탭을 구성하라는 패턴이 포함되어 있다.

반영하려면 필요한 변경:

- `CrudPromptBuilderTool.buildMasterDetailPrompt(...)`에 `viewType`, `egovVersion` 추가 검토
- `MasterDetailService.buildMasterDetailPrompt(...)` 시그니처 확장
- JSP 지시와 Thymeleaf 지시 분기
- Thymeleaf 선택 시 `layout/default.html` 생성 지시 추가
- Detail 화면의 마스터 + 디테일 그리드 탭을 Thymeleaf 문법으로 별도 작성
- 파일 수 문구 갱신
- MCP Tool metadata 변경 영향 검토
- `CrudPromptBuilderTool`의 `@Tool` 설명에 고정된 "JSP 5개", "총 12개" 문구도 함께 수정해야 한다.
- MCP 클라이언트가 Tool schema를 캐시할 수 있으므로 파라미터 추가 후에는 서버 재시작과 클라이언트 schema 갱신 확인이 필요하다.

리스크:

- Tool schema가 바뀌므로 기존 클라이언트 호출 호환성을 고려해야 한다.
- 프롬프트 기반 생성은 자동 템플릿 렌더링보다 결과 일관성이 낮다.
- Master/Detail 전용 Thymeleaf 템플릿이 없으므로 단순 문구 추가만으로 품질을 보장하기 어렵다.

권장:

- 단기적으로는 `viewType`을 optional 파라미터로 추가하고, 기본값은 기존 JSP로 유지한다.
- 중장기적으로는 `MasterDetailOrchestrationService`를 만들어 자동 생성 방식으로 전환한 뒤 layout을 적용한다.

### 4. buildJoinSelectPrompt

직접 적용 대상이 아니다.

현재 구조:

- 화면 파일을 생성하지 않는다.
- 기존 CRUD 결과물에 JOIN SELECT, VO 추가 필드, Mapper resultMap 항목을 추가하라는 보조 지시만 반환한다.

layout 적용 관점:

- `layout/default.html` 생성이나 수정 책임을 넣으면 메서드 책임이 섞인다.
- 이 메서드는 기존 화면의 content 영역 안에 조인 필드 표시를 추가하도록 안내하는 것이 적절하다.

반영 가능한 최소 변경:

- 안내 문구 추가
  - Thymeleaf CRUD 화면에 적용할 경우 기존 `layout:decorate` 구조는 유지한다.
  - 목록/상세 화면의 `layout:fragment="content"` 내부에 조인 표시 필드만 추가한다.
  - layout 파일은 `buildFullCrudPrompt(viewType="thymeleaf")` 또는 별도 layout 생성 경로에서 관리한다.

권장:

- `buildJoinSelectPrompt`에는 layout 생성 로직을 추가하지 않는다.
- 안내 문구만 보강한다.

## 우선순위

1. `buildBoardFeature` 적용
   - 실사용 영향이 가장 크고, 현재 Thymeleaf 화면이 독립 HTML이라 UI 일관성이 깨진다.

2. 공통 layout 템플릿 표준화
   - CRUD와 BBS가 같은 `layout/default.html`을 공유할지 결정한다.
   - WAR/Boot 정적 리소스 경로 차이를 정리한다.

3. `buildMasterDetailPrompt` 확장
   - optional `viewType`, `egovVersion` 추가로 하위 호환성을 유지한다.
   - 가능하면 자동 생성 서비스화 후 layout 적용한다.

4. `buildJoinSelectPrompt` 안내 보강
   - layout 직접 생성은 제외한다.
   - 기존 Thymeleaf content 영역 내부 수정 지시만 추가한다.

## 권장 구현 순서

1. 공통 layout 템플릿 기준 결정
2. `boardFreemarkerConfiguration` 기준에서 layout 템플릿 참조 경로 결정
3. `buildBoardFeature`에 `layoutHtml` layer 추가
4. `BoardLayerDefinition.resolveFileName()`에 `layoutHtml` 특수 케이스 추가
5. `BoardTemplateRenderer`에 `layoutHtml` 매핑 추가
6. BBS Thymeleaf 화면 4개를 layout fragment 구조로 변경
7. `CrudPromptBuilderTool.buildBoardFeature` 설명과 Board 생성 결과 파일 수, 테스트 갱신
8. `buildMasterDetailPrompt`의 optional `viewType` 확장 설계
9. `buildJoinSelectPrompt` 안내 문구 보강

## 결론

`layout/default.html` 적용은 네 메서드에 동일하게 넣을 수 있는 성격이 아니다.

- `buildFullCrudPrompt`: 이미 적용됨
- `buildBoardFeature`: 적용 우선순위 높음
- `buildMasterDetailPrompt`: 인터페이스 확장과 프롬프트 재설계 필요
- `buildJoinSelectPrompt`: 직접 적용하지 않고 안내만 보강

가장 안전한 1차 작업은 `buildBoardFeature`에 CRUD와 동일한 layout 생성/참조 구조를 적용하는 것이다.
