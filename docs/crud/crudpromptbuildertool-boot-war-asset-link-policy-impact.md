# CrudPromptBuilderTool BOOT/WAR 자원 링크 정책 반영 현황

## 목적

`CrudPromptBuilderTool`과 관련 생성 서비스가
BOOT/WAR 프로젝트에서 정적 리소스 링크를 현재 어떤 기준으로 다루는지 정리한다.

대상 자원:

- `styles.css`
- `_ds_bundle.css`
- `krds.min.js`

---

## 현재 확정 정책

현재 정책은 다음으로 확정되어 코드에 반영되었다.

- 화면 링크 URL은 WAR/BOOT 모두 `/resources/**` 사용
- WAR 파일 위치는 `src/main/webapp/resources/**`
- BOOT 파일 위치는 `src/main/resources/static/resources/**`
- `_ds_bundle.css`는 `styles.css` 내부 `@import` 대상으로 유지

즉, BOOT를 위해 별도 `/css/**`, `/js/**` 경로로 분기하지 않는다.

---

## 반영 이유

현재 CRUD/Board/MasterDetail 생성 템플릿은 이미 `/resources/**` 경로를 전제로 하고 있다.

대표 예:

```html
<link rel="stylesheet" th:href="@{/resources/css/styles.css}">
<script th:src="@{/resources/js/krds.min.js}"></script>
```

이 전제를 유지하면 다음 장점이 있다.

- WAR 템플릿과 BOOT 템플릿을 별도 분기하지 않아도 된다
- 기존 FTL/JSP 링크 규칙을 그대로 유지할 수 있다
- `CrudPromptBuilderTool` 설명문과 생성 프롬프트가 단일 규칙으로 정리된다

---

## 현재 코드 반영 범위

### 1. `CrudPromptBuilderTool`

아래 메서드 설명문이 현재 정책으로 업데이트되었다.

- `buildFullCrudPrompt`
- `buildMasterDetailPrompt`
- `buildBoardFeature`

공통 안내 내용:

- 생성 화면은 `/resources/css/styles.css`, `/resources/js/krds.min.js` 사용
- WAR는 `webapp/resources/**`, BOOT는 `static/resources/**`에 파일 생성
- `_ds_bundle.css`는 화면에서 직접 링크하지 않음

### 2. `CrudPromptBuilderService`

Thymeleaf 생성 지시문이 `/resources/**` 공통 정책 기준으로 수정되었다.

포함 내용:

- `layout/default.html`은 `/resources/css/styles.css`와 `/resources/js/krds.min.js` 사용
- WAR/BOOT 모두 URL은 `/resources/**` 유지
- `krds-alert` 같은 미확인 클래스를 고정 규칙으로 강제하지 않음

### 3. `MasterDetailService`

화면 타입 설명과 Thymeleaf 예시에도 같은 정책이 반영되었다.

- 정적 리소스 경로 설명 추가
- BOOT 파일은 `static/resources/**`에 생성된다고 명시
- 레이아웃 예시에 `/resources/**` 링크 반영

### 4. `ProjectInitializrTool` / `FilePlanFactory`

자원 링크 정책이 실제 파일 생성 위치와 어긋나지 않도록
BOOT 정적 자산 생성 경로를 `static/resources/**`로 맞췄다.

---

## 폐기된 선택지

현재 구현에서는 아래 선택지를 채택하지 않았다.

### 1. BOOT 전용 `/css/**`, `/js/**` 분기

채택하지 않은 이유:

- 템플릿과 설명문이 projectType-aware로 복잡해진다
- WAR/BOOT 공통 화면 생성 규칙이 깨진다

### 2. `assetBasePath` 변수화

채택하지 않은 이유:

- 지금 단계에서는 필요 이상으로 렌더링 계약이 복잡해진다
- 현재 요구사항은 URL 통일 유지가 더 직접적이다

---

## 영향 정리

현재 정책 덕분에 아래 항목이 단순해졌다.

- Thymeleaf layout FTL 경로 유지
- JSP 화면 경로 유지
- `CrudPromptBuilderTool` 설명문 단일화
- BOOT/WAR 공통 문서화

대신 전제는 하나다.

- `initializeProject()`가 BOOT일 때도 반드시 `static/resources/**` 자산 3종을 생성해야 한다

이 전제는 현재 `FilePlanFactory`, `ProjectValidator`, 테스트로 보장하고 있다.

---

## 결론

문서 초안 단계에서 열어 두었던 “정책 A/B/C” 검토는 끝났다.
현재 코드는 정책 A를 구현 완료한 상태로 보면 된다.

- URL: `/resources/**` 공통
- WAR 저장 경로: `webapp/resources/**`
- BOOT 저장 경로: `static/resources/**`

따라서 이후 문서와 README는 이 기준으로 통일하는 것이 맞다.
