# ProjectInitializrTool BOOT 정적 리소스 생성 실제 반영안

## 목표

BOOT 프로젝트 초기화 시에도 WAR와 같은 화면 자산 계약을 유지하도록
정적 리소스를 `static/resources/**`에 생성한다.

생성 대상:

- `src/main/resources/static/resources/css/styles.css`
- `src/main/resources/static/resources/css/_ds_bundle.css`
- `src/main/resources/static/resources/js/krds.min.js`

---

## 실제 반영 내용

### 1. `FilePlanFactory.bootFiles()`

BOOT 파일 계획에 아래 3개가 추가되었다.

```java
FilePlan.of("src/main/resources/static/resources/css/styles.css", RESOURCE, stpl::stylesCss)
FilePlan.of("src/main/resources/static/resources/css/_ds_bundle.css", RESOURCE, stpl::dsBundleCss)
FilePlan.of("src/main/resources/static/resources/js/krds.min.js", RESOURCE, stpl::krdsJs)
```

또한 BOOT 디렉터리 계획도 아래처럼 조정되었다.

- `src/main/resources/static/resources/css`
- `src/main/resources/static/resources/js`

핵심은 BOOT에서도 URL을 `/resources/**`로 유지하기 위해
파일 시스템 경로를 `static/resources/**`로 맞춘 점이다.

### 2. `ProjectValidator.validateResult()`

BOOT 필수 파일 검증이 아래 기준으로 확장되었다.

- `src/main/resources/application.yml`
- `src/main/resources/static/resources/css/styles.css`
- `src/main/resources/static/resources/css/_ds_bundle.css`
- `src/main/resources/static/resources/js/krds.min.js`

### 3. `ProjectInitializrTool` 설명문

설명문이 현재 구현 기준으로 정리되었다.

- WAR: `resources/css/styles.css`, `resources/css/_ds_bundle.css`, `resources/js/krds.min.js`
- BOOT: `static/resources/css/styles.css`, `static/resources/css/_ds_bundle.css`, `static/resources/js/krds.min.js`

---

## 함께 반영된 정책

이 변경은 단순 파일 추가가 아니라 자원 링크 정책까지 포함한다.

- 화면 링크 URL은 WAR/BOOT 모두 `/resources/**`
- BOOT 저장 경로만 `static/resources/**`
- `_ds_bundle.css`는 `styles.css`가 내부에서 불러오는 구조 유지

이 정책이 반영된 파일:

- `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java`
- `src/main/java/com/krdevops/springai/service/CrudPromptBuilderService.java`
- `src/main/java/com/krdevops/springai/service/MasterDetailService.java`

---

## 테스트 반영안

추가된 테스트:

- `ProjectInitializrBoot50StaticResourceWorkflowTest`

검증 항목:

- BOOT 디렉터리 계획이 `static/resources/**` 경로를 포함하는지
- BOOT 파일 계획이 정적 리소스 3개를 포함하는지
- 검증기 경고가 새 경로 기준으로 동작하는지

기존 WAR 테스트도 현재 자산 체계 기준으로 유지된다.

---

## 구현 전 문서와 달라진 점

초안 문서에는 BOOT 경로가 아래처럼 적혀 있었다.

- `src/main/resources/static/css/styles.css`
- `src/main/resources/static/js/krds.min.js`

현재 구현은 그렇게 하지 않는다.

최종 구현 경로는 아래다.

- `src/main/resources/static/resources/css/styles.css`
- `src/main/resources/static/resources/css/_ds_bundle.css`
- `src/main/resources/static/resources/js/krds.min.js`

이 차이는 의도된 것이다.
`/css/**`나 `/js/**`로 분기하지 않고, 기존 FTL/JSP가 그대로 `/resources/**`를 쓰게 하기 위한 결정이다.

---

## 결론

현재 코드는 “BOOT도 정적 리소스를 생성한다” 수준을 넘어서,
“BOOT도 WAR와 동일한 `/resources/**` URL 계약을 유지한다”까지 반영한 상태다.

따라서 이후 문서, README, 툴 설명은 모두 `static/resources/**` 기준으로 맞추는 것이 맞다.
