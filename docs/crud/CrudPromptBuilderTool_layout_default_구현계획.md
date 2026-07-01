# CrudPromptBuilderTool layout/default.html 구현계획

작성일: 2026-06-22

## 목적

`CrudPromptBuilderTool_layout_default_영향평가.md`를 바탕으로 `layout/default.html` 공통 Thymeleaf 레이아웃 적용 작업의 구현 순서와 검증 기준을 정의한다.

1차 구현 대상은 `buildBoardFeature`다. `buildMasterDetailPrompt`와 `buildJoinSelectPrompt`는 책임과 구현 방식이 다르므로 후속 단계로 분리한다.

## 기본 방침

- `buildFullCrudPrompt`의 기존 layout 적용 경로는 유지한다.
- `buildBoardFeature(viewType="thymeleaf")`는 CRUD와 동일하게 `layout/default.html`을 생성하고, BBS 화면 4개가 이를 참조하도록 변경한다.
- `BoardLayerDefinition`, `BoardTemplateRenderer`, Board Thymeleaf FTL 변경은 하나의 원자적 변경으로 처리한다.
- `ThymeleafRuntimeConfigurer`는 Board 경로에서 이미 호출되므로 Maven 의존성 및 ViewResolver 보강 로직은 재사용한다.
- `buildJoinSelectPrompt`에는 layout 생성 책임을 넣지 않는다.

## 1차 구현: buildBoardFeature layout 적용

### 1. Layout 템플릿 참조 정책 결정

선택안:

| 안 | 방식 | 장점 | 단점 |
|---|---|---|---|
| A | `BoardTemplateRenderer`에서 `crud/layout/default.html.ftl` 재사용 | 중복 없음, 빠른 적용 | CRUD 경로명을 Board가 직접 참조 |
| B | `templates/layout/default.html.ftl`로 공통 이동 | 가장 명확한 공유 구조 | CRUD renderer base 경로 조정 필요 |
| C | `templates/board/layout/default.html.ftl` 신설 | Board 독립성 높음 | layout 중복 발생 |

권장: A안으로 시작한다.

이유:

- `boardFreemarkerConfiguration`의 template base는 `templates`이므로 `crud/layout/default.html.ftl` 참조가 가능하다.
- CRUD layout과 Board layout을 우선 일치시킬 수 있다.
- 공통 경로 이동은 CRUD renderer 설정까지 건드리므로 1차 작업 범위를 키운다.

확인 완료:

- `crud/layout/default.html.ftl`은 FreeMarker `${...}` 변수를 참조하지 않는다.
- 파일은 정적 HTML과 Thymeleaf 속성(`th:href`, `layout:fragment`) 중심이므로 `BoardTemplateRenderer.toDataModel()`과 데이터 모델 불일치가 발생하지 않는다.
- 따라서 A안은 1차 구현에 즉시 사용할 수 있다.

사이드바 정책:

- 1차 구현에서는 `crud/layout/default.html.ftl`의 정적 더미 사이드바를 그대로 공유한다.
- CRUD/BBS별 동적 메뉴, 활성 메뉴 표시, 권한 기반 메뉴 노출은 후속 작업으로 분리한다.
- 1차 목표는 layout 연결과 렌더링 안정성 검증이다.

### 2. BoardLayerDefinition 수정

대상:

- `src/main/java/com/krdevops/springai/model/board/BoardLayerDefinition.java`

변경:

- `THYMELEAF_LAYERS`에 `layoutHtml` 추가
- `resolveFileName()`에 `layoutHtml` 특수 케이스 추가

예상 형태:

```java
public static final List<BoardLayerDefinition> THYMELEAF_LAYERS = concat(
        COMMON_LAYERS,
        new BoardLayerDefinition("layoutHtml", "layout/default.html", "src/main/resources/templates/"),
        new BoardLayerDefinition("thymeleafList", "List.html", "src/main/resources/templates/{DOMAIN_LC}/"),
        ...
);
```

```java
public static String resolveFileName(String layerKey, String domain, String suffix) {
    return switch (layerKey) {
        case "layoutHtml" -> "layout/default.html";
        case "vo", "searchVo", "mapper", "mapperXml", "service" -> domain + suffix;
        default -> "Egov" + domain + suffix;
    };
}
```

주의:

- `layoutHtml` 케이스가 없으면 `EgovBbslayout/default.html` 같은 잘못된 파일명이 생성된다.

### 3. BoardTemplateRenderer 수정

대상:

- `src/main/java/com/krdevops/springai/service/BoardTemplateRenderer.java`

변경:

```java
LAYER_TEMPLATE_MAP.put("layoutHtml", "crud/layout/default.html.ftl");
```

주의:

- A안을 선택한 경우 `crud/layout/default.html.ftl`을 사용한다.
- C안을 선택하면 `board/layout/default.html.ftl` 파일을 추가하고 매핑도 해당 경로로 바꾼다.
- 매핑 누락 시 `renderByLayerKey("layoutHtml", model)`에서 `IllegalArgumentException`이 발생한다.

### 4. Board Thymeleaf FTL 4개 구조 변경

대상:

- `src/main/resources/templates/board/thymeleaf-list.html.ftl`
- `src/main/resources/templates/board/thymeleaf-detail.html.ftl`
- `src/main/resources/templates/board/thymeleaf-regist.html.ftl`
- `src/main/resources/templates/board/thymeleaf-updt.html.ftl`

변경 전 구조:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>...</title>
</head>
<body>
<main>
...
</main>
</body>
</html>
```

변경 후 구조:

```html
<!DOCTYPE html>
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

작업 포인트:

- `<body>`, `<main>` 래핑 제거 또는 content fragment 내부로 이동
- 제목/검색/목록/폼 영역을 `layout:fragment="content"` 내부로 배치
- 기존 Thymeleaf expression 유지
- 필요 시 Bootstrap/KRDS class를 점진적으로 추가하되, 1차 목표는 layout 연결과 렌더링 안정성으로 제한

### 5. Tool 설명 문구 갱신

대상:

- `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java`

변경:

- `buildBoardFeature` 설명에 viewType별 생성 파일 수를 추가한다.

예시:

```text
viewType:
  - "jsp"       : JSP 화면 4개 포함 12개 파일 생성
  - "thymeleaf" : layout/default.html + HTML 화면 4개 포함 13개 파일 생성
```

### 6. 테스트 추가

신규 테스트 권장:

- `BoardLayerDefinitionTest`
  - Thymeleaf layer 수가 13개인지 확인
  - `layoutHtml`이 포함되는지 확인
  - `resolveFileName("layoutHtml", "Bbs", "layout/default.html")` 결과가 `layout/default.html`인지 확인

- `BoardTemplateRendererTest` 또는 통합 테스트
  - 기존 `CrudTemplateRendererTest` 패턴을 참고해 FreeMarker `Configuration`을 직접 초기화하거나 테스트 컨텍스트에서 `boardFreemarkerConfiguration`을 주입받는 방식으로 작성한다.
  - 실제 FTL 렌더링을 검증하는 테스트이므로 템플릿 파일은 mock 처리하지 않는다.
  - `renderByLayerKey("layoutHtml", model)` 성공 확인
  - `thymeleafList` 결과에 `layout:decorate="~{layout/default}"` 포함 확인
  - `thymeleafList` 결과에 `layout:fragment="content"` 포함 확인

- `BoardOrchestrationServiceTest`
  - Mock 기반 단위 테스트로 작성한다.
  - `BoardSchemaService`, `BoardModelFactory`, `BoardTemplateRenderer`, `CodeService`, `CodeValidatorService`, `GenerationHistoryService`, `ThymeleafRuntimeConfigurer`를 mock 처리한다.
  - 실제 DB 테이블(`COMTNBBS` 등)과 실제 파일 시스템에 의존하는 통합 테스트는 1차 자동 테스트 범위에서 제외한다.
  - `viewType=thymeleaf`일 때 성공 파일 수가 13개인지 확인
  - `layout/default.html` 저장 경로 확인
  - `thymeleafRuntimeConfigurer.ensureThymeleafRuntime(...)` 호출 유지 확인

기존 테스트 영향:

- Board 전용 테스트가 현재 부족하므로 신규 테스트가 필요하다.
- CRUD 테스트는 기존 12개 파일 기대값을 유지해야 한다.

### 7. 검증 명령

SpringAI 프로젝트:

```bash
./gradlew test
```

생성 샘플 프로젝트:

사전 조건: `initializeProject`와 `buildBoardFeature(viewType="thymeleaf")`로 BBS 소스를 생성한 프로젝트가 있어야 한다.

```bash
/opt/homebrew/bin/mvn -f /Users/jeongdaeseob/workspace-egov/egov-bbs/pom.xml test
```

생성 결과 수동 확인:

```text
src/main/resources/templates/layout/default.html
src/main/resources/templates/bbs/EgovBbsList.html
src/main/resources/templates/bbs/EgovBbsDetail.html
src/main/resources/templates/bbs/EgovBbsRegist.html
src/main/resources/templates/bbs/EgovBbsUpdt.html
```

각 BBS HTML 확인:

- `layout:decorate="~{layout/default}"` 존재
- `layout:fragment="content"` 존재
- 독립 `<body><main>` 구조가 남아 있지 않은지 확인

## 2차 구현: buildMasterDetailPrompt 확장

### 현황

- 현재는 자동 생성이 아니라 프롬프트 문자열을 반환한다.
- `viewType`, `egovVersion` 파라미터가 없다.
- `CrudPromptBuilderTool` 설명은 총 12개라고 안내하지만, `MasterDetailService` 본문은 `[생성 파일 목록 — 13개]`와 Step 1~13을 출력한다.

### 선행 정리

1. 파일 수 불일치 정리
   - `MasterDetailService` 본문은 현재 Step 1~13을 출력하므로 13개가 실제 기준이다.
   - `CrudPromptBuilderTool.buildMasterDetailPrompt`의 `@Tool` 설명을 "총 12개"에서 "총 13개"로 수정하는 방향이 맞다.

2. 파라미터 확장 방식 결정
   - 기존 호출 호환성을 위해 nullable optional 파라미터를 뒤에 추가한다.

예상 시그니처:

```java
// CrudPromptBuilderTool
public String buildMasterDetailPrompt(
        String database,
        String masterTable,
        String detailTable,
        String domain,
        String packageName,
        String outputPath,
        @Nullable String egovVersion,
        @Nullable String viewType)
```

동일한 파라미터 확장은 `MasterDetailService.buildMasterDetailPrompt(...)`에도 적용한다. `CrudPromptBuilderTool`만 확장하고 Service 호출부를 그대로 두면 컴파일 또는 호출 불일치가 발생한다.

3. MCP schema 갱신
   - Tool 시그니처 변경 후 서버 재시작 필요
   - 클라이언트가 캐시한 Tool schema 갱신 확인 필요

### 구현 방향

- 기본값은 기존 JSP 유지
- `viewType=thymeleaf`일 때만 layout 지시 추가
- Thymeleaf용 Detail 화면 지시를 별도 섹션으로 작성
- 마스터 정보 + 디테일 그리드 탭을 `layout:fragment="content"` 내부에 생성하도록 명시

주의:

- 프롬프트 기반 생성은 자동 FTL 렌더링보다 결과 일관성이 낮다.
- 장기적으로는 `MasterDetailOrchestrationService` 자동 생성 방식이 더 안전하다.

## 3차 구현: buildJoinSelectPrompt 안내 보강

### 현황

- 화면 파일 생성 메서드가 아니다.
- 기존 CRUD 결과물에 JOIN SELECT, VO 필드, resultMap 항목을 추가하는 보조 지시다.

### 구현 방향

- layout 생성 로직은 추가하지 않는다.
- 안내 문구만 보강한다.

추가할 안내:

```text
Thymeleaf CRUD 화면에 적용하는 경우 기존 layout/default.html 및 layout:decorate 구조는 유지하세요.
목록/상세 화면에서는 layout:fragment="content" 내부에 조인 표시 필드만 추가하세요.
layout 파일 생성 또는 수정은 buildFullCrudPrompt(viewType="thymeleaf") 또는 buildBoardFeature(viewType="thymeleaf") 경로에서 관리합니다.
```

## 배포 원칙

`buildBoardFeature`의 1차 변경은 아래 항목을 하나의 커밋/배포 단위로 묶는다.

- `BoardLayerDefinition.THYMELEAF_LAYERS`에 `layoutHtml` 추가
- `BoardLayerDefinition.resolveFileName()`의 `layoutHtml` 특수 케이스
- `BoardTemplateRenderer`의 `layoutHtml` 매핑
- Board Thymeleaf FTL 4개 fragment 구조 변경
- Tool 설명 문구 갱신
- Board 관련 테스트

원자적으로 배포해야 하는 이유:

- layer만 추가하고 renderer 매핑이 없으면 `renderByLayerKey("layoutHtml")` 예외가 발생한다.
- renderer 매핑만 있고 layer가 없으면 layout 파일이 생성되지 않는다.
- `resolveFileName()` 케이스가 없으면 잘못된 파일명이 생성된다.
- 화면 FTL만 layout을 참조하고 layout 파일이 없으면 런타임 Thymeleaf view 오류가 발생한다.

## 완료 기준

1. `buildBoardFeature(viewType="thymeleaf")` 결과에 `layout/default.html` 포함
2. BBS Thymeleaf 생성 성공 파일 수 13개
3. BBS HTML 4개가 `layout:decorate="~{layout/default}"` 사용
4. `ThymeleafRuntimeConfigurer` 기존 보강 경로 정상 유지
5. `./gradlew test` 통과
6. 샘플 eGovFrame 프로젝트 Maven 빌드 통과

## 우선순위

아래 순서는 중요도와 착수 순서다. 다만 1번과 2번은 배포 원칙에 따라 같은 커밋/배포 단위로 묶는다.

1. `buildBoardFeature` layout 적용 + Board 테스트 추가
2. `buildMasterDetailPrompt` 파일 수 불일치 정리
3. `buildMasterDetailPrompt` optional `viewType` 확장
4. `buildJoinSelectPrompt` 안내 문구 보강
