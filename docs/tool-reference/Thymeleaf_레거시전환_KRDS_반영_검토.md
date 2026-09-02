# JSP→Thymeleaf 레거시 전환(섹션 14~17)의 KRDS 디자인 시스템 반영 지점 검토

> 2026-09-02, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> 아키텍처 다이어그램 아티팩트 14~17번 섹션(승인 워크플로우 / 웹 캡처 가이드 / Figma 디자인 생성 /
> JSP→Thymeleaf Binding 생성)의 흐름에서 KRDS 디자인 시스템이 실제로 어디에 반영되는지 확인해달라는
> 요청에 대한 검토다.

---

## 1. 배경

CRUD/Board **자동 생성** 파이프라인(아키텍처 아티팩트 5~6번 섹션 계열)에는 `KrdsStylesConfigurer`가
`styles.css`에 KRDS CSS 계약을 patch하는 지점이 명확히 있다. 이 문서가 다루는 **JSP→Thymeleaf 레거시
전환** 파이프라인(14~17번 섹션)도 생성 결과 HTML에 `krds-*` 클래스를 쓰는데, 그 스타일이 실제로 어디서
채워지는지 확인이 필요했다.

---

## 2. 섹션별 확인 결과

| 섹션 | 내용 | KRDS 관여 여부 | 근거 |
|---|---|---|---|
| **14** | `ThymeleafProjectWorkflowService` 승인 워크플로우(`PREVIEW_READY`→`APPROVED`→`APPLIED`) | 없음 | `ThymeleafProjectWorkflowService.java`에서 `css`/`krds` 관련 코드 grep **0건** |
| **15** | 브라우저 캡처 실행 가이드 | 없음 | `WebCaptureProjectionPolicy`는 필드/라벨/텍스트만 추출, 디자인 시스템과 무관 |
| **16** | Figma 디자인 생성(Schema-first/Design-first) | 있으나 별개 소비처 | `DesignSystemQueryService`가 `DEFAULT_PROFILE_ID="krds"`로 `ComponentRegistry` 참조하지만, 산출물은 Figma Bundle/Artifact(`FigmaDesignOperationStatus.PREVIEW_READY`)에서 끝남 — 실제 Thymeleaf 코드 생성과 연결되는 지점 없음(grep으로 확인: `createDesignFromText`/`createDesignFromReference`/`createDesignFromImage`/`createDesignWithComponents` 호출부가 `FigmaDesignOrchestrationTool.java`에만 있고, `applyThymeleafProject`/`BindingComposer` 쪽에서 이 산출물을 참조하는 코드 없음) |
| **17** | JSP→Thymeleaf Binding Contract 생성 → `BindingComposer.compose()` | **클래스명만 하드코딩, 스타일 정의는 없음** | §3 참고 |

---

## 3. 섹션 17 상세 — 클래스명은 있지만 스타일 정의로 이어지지 않음

### 3-1. 템플릿에 KRDS 클래스명이 리터럴로 박혀 있음(확인됨)

`BindingComposer.compose()`가 `templates/legacy-thymeleaf/{list,form,detail}.html.ftl`을 렌더링한다.

```
list.html.ftl:19   class="krds-btn primary medium egov-btn egov-btn-register"
list.html.ftl:30   class="krds-input medium egov-control"
list.html.ftl:36   class="krds-table-wrap egov-density-${layoutDensity?lower_case}"
form.html.ftl:31,46  krds-table-wrap / krds-input
detail.html.ftl:20,37 krds-table-wrap
```

### 3-2. 이 클래스들의 실제 CSS 정의는 이 파이프라인에서 patch되지 않음

`KrdsStylesConfigurer`(`styles.css`에 `.krds-btn`/`.krds-input`/`.krds-table-wrap` 룰과
`--krds-button--size-height-medium` 등 CSS 변수를 멱등 patch하는 서비스)의 실제 호출처는:

```
BoardGeneratedCodeAuditor.java
BoardCssProcessor.java              (Board 자동 생성 파이프라인)
CrudFormColumnCssProcessor.java     (CRUD 자동 생성 파이프라인)
CrudTableDensityCssProcessor.java   (CRUD 자동 생성 파이프라인)
```

**전부 CRUD/Board 자동 생성 파이프라인(섹션 5~6 계열) 전용이며, JSP→Thymeleaf 마이그레이션
(섹션 17)과는 별개 코드 경로다.** `ThymeleafProjectWorkflowService`/`BindingComposer` 어디에서도
`KrdsStylesConfigurer`를 참조하지 않는다(grep 확인).

### 3-3. KRDS 원본 CSS/JS 자산은 완전히 다른 단계(프로젝트 초기화)에서만 배치됨

실제 KRDS 자산(`_ds_bundle.css`, `krds.min.js`, `styles.css` 뼈대)이 프로젝트에 놓이는 지점은
`FilePlanFactory`(`ProjectInitializrTool.initializeProject()` 도구, 프로젝트 골격 생성 단계) 하나뿐이다:

```
FilePlanFactory.java:110-115  WAR: styles.css / _ds_bundle.css / krds.min.js
FilePlanFactory.java:154-159  Boot: 동일 3종(static 경로)
FilePlanFactory.java:349-350  layout에 <link rel="stylesheet" ... styles.css> / <script ... krds.min.js> 삽입
```

이건 섹션 14~17보다 훨씬 앞선, **완전히 별개의 일회성 단계**다.

---

## 4. 실질적 위험

섹션 17은 정의상 "이미 존재하는 레거시 JSP 프로젝트"를 변환 대상으로 삼는다. 그 프로젝트가 이 도구의
`initializeProject()`를 거쳐 만들어진 게 아니라면(실제 마이그레이션 대상 레거시 프로젝트는 대부분
그렇지 않을 가능성이 높다), 생성된 화면에는 `krds-btn`/`krds-input`/`krds-table-wrap` **클래스명만
존재**하고 그 스타일 정의 자체가 대상 프로젝트에 없어서 **화면에 스타일이 전혀 반영되지 않는 상태**가
될 수 있다.

`ThymeleafProjectWorkflowService`나 `BindingComposer` 어디에도 "대상 프로젝트에 이 CSS/JS 자산이
이미 있는지" 검증하는 코드가 없다 — 이건 추정이 아니라 호출 그래프상 연결 자체가 없다는 사실로
확인된 gap이다.

---

## 5. 결론

1. 섹션 14(승인)·15(캡처)는 KRDS와 무관하다.
2. 섹션 16(Figma 디자인 생성)의 KRDS 참조는 Figma 목업 생성용이며 실제 Thymeleaf 코드 생성 결과와
   연결되지 않는다.
3. 섹션 17(JSP→Thymeleaf 변환)은 KRDS 클래스명을 HTML에 하드코딩하지만, 그 클래스의 실제 CSS
   정의를 채우는 `KrdsStylesConfigurer`를 호출하지 않으며, KRDS 원본 자산 배치는 훨씬 이전
   단계(`initializeProject()`)에서만 이루어진다.
4. 결과적으로 **섹션 17만 단독으로 실행되는 경로(초기화 없이 기존 레거시 프로젝트를 바로 변환하는
   경우)에서는 KRDS 스타일이 실제로 적용되지 않을 위험**이 코드 근거로 확인된다.

이 문서는 검토 결과만 담으며, 대응 방안(예: `BindingComposer`/`ThymeleafProjectWorkflowService`에
CSS/JS 자산 존재 여부 검증 게이트 추가, 또는 `KrdsStylesConfigurer`를 이 경로에도 연결)은 별도
구현계획 문서로 승인 후 진행한다.

---

## 6. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `service/thymeleaf/BindingComposer.java` | 섹션 17 핵심 — `legacy-thymeleaf/*.html.ftl` 렌더링, KRDS/CSS 관련 로직 없음 |
| `templates/legacy-thymeleaf/list.html.ftl`, `form.html.ftl`, `detail.html.ftl` | `krds-btn`/`krds-input`/`krds-table-wrap` 클래스명 하드코딩 |
| `service/KrdsStylesConfigurer.java` | KRDS CSS 계약을 `styles.css`에 patch — 섹션 17에서 미호출 |
| `service/generation/board/BoardCssProcessor.java`, `service/generation/crud/CrudFormColumnCssProcessor.java`, `service/generation/crud/CrudTableDensityCssProcessor.java` | `KrdsStylesConfigurer`의 실제 호출처(전부 CRUD/Board 자동 생성 파이프라인) |
| `service/initializr/FilePlanFactory.java` | KRDS 원본 자산(`_ds_bundle.css`/`krds.min.js`/`styles.css`) 배치 — 프로젝트 초기화 단계, 섹션 14~17과 별개 |
| `service/thymeleaf/ThymeleafProjectWorkflowService.java` | 섹션 14 승인 워크플로우 — CSS/KRDS 관련 코드 0건 |
| `service/figma/FigmaDesignOrchestrationService.java`, `tools/FigmaDesignOrchestrationTool.java` | 섹션 16 — KRDS `ComponentRegistry` 참조는 Figma Bundle 생성에서 끝남 |
| `service/designsystem/DesignSystemQueryService.java` | `DEFAULT_PROFILE_ID = "krds"`로 `ComponentRegistry` 조회 |
