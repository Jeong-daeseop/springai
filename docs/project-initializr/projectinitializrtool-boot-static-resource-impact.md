# ProjectInitializrTool BOOT 정적 리소스 생성 반영 현황

## 목적

`ProjectInitializrTool`의 BOOT 분기에서 FTC/KRDS 기반 Thymeleaf 화면이 바로 동작하도록
정적 리소스 생성과 검증이 현재 코드에 어떻게 반영되었는지 정리한다.

---

## 현재 구현 상태

현재 BOOT 프로젝트 초기화 시 아래 디렉터리를 생성한다.

- `src/main/resources/static/resources/css`
- `src/main/resources/static/resources/js`
- `src/main/resources/templates`

참고:

- `src/main/java/com/krdevops/springai/service/initializr/FilePlanFactory.java`

또한 아래 정적 리소스를 실제 생성한다.

- `src/main/resources/static/resources/css/styles.css`
- `src/main/resources/static/resources/css/_ds_bundle.css`
- `src/main/resources/static/resources/js/krds.min.js`

함께 생성되는 BOOT 기본 파일:

- `src/main/resources/application.yml`
- `src/main/resources/logback-spring.xml`
- `{Domain}Application.java`
- `{Domain}ApplicationTests.java`

WAR 분기는 기존대로 아래 경로를 생성한다.

- `src/main/webapp/resources/css/styles.css`
- `src/main/webapp/resources/css/_ds_bundle.css`
- `src/main/webapp/resources/js/krds.min.js`

---

## 자원 URL 정책

현재 정책은 파일 저장 위치와 화면 링크 URL을 분리해서 유지한다.

- WAR 파일 위치: `src/main/webapp/resources/**`
- BOOT 파일 위치: `src/main/resources/static/resources/**`
- 화면 링크 URL: WAR/BOOT 공통으로 `/resources/**`

즉 BOOT도 `static/resources/**` 아래에 파일을 생성해서,
Spring Boot 기본 정적 리소스 매핑만으로 `/resources/**` URL을 그대로 사용할 수 있게 맞춘 상태다.

---

## 반영된 코드 범위

### 1. `FilePlanFactory`

BOOT 분기에 아래 변경이 반영되었다.

- 디렉터리 생성 경로를 `static/css`, `static/js`에서 `static/resources/css`, `static/resources/js`로 변경
- `styles.css`, `_ds_bundle.css`, `krds.min.js` FilePlan 추가

### 2. `ProjectValidator`

BOOT 결과 검증 필수 파일에 아래 항목이 추가되었다.

- `src/main/resources/static/resources/css/styles.css`
- `src/main/resources/static/resources/css/_ds_bundle.css`
- `src/main/resources/static/resources/js/krds.min.js`

WAR 필수 파일도 현재 자산 체계에 맞춰 아래 기준으로 정리되었다.

- `src/main/webapp/resources/css/styles.css`
- `src/main/webapp/resources/css/_ds_bundle.css`
- `src/main/webapp/resources/js/krds.min.js`

### 3. `ProjectInitializrTool`

툴 설명문도 현재 구현 기준으로 반영되었다.

- WAR: `webapp/resources/**`
- BOOT: `static/resources/**`

---

## 연계 반영 사항

정적 리소스 생성만 바꾸면 문서와 생성 프롬프트가 stale 되므로,
아래 컴포넌트들도 같이 수정되었다.

- `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java`
- `src/main/java/com/krdevops/springai/service/CrudPromptBuilderService.java`
- `src/main/java/com/krdevops/springai/service/MasterDetailService.java`

현재 안내 문구는 모두 아래 정책을 전제로 한다.

- 생성 화면은 `/resources/css/styles.css`, `/resources/js/krds.min.js` 사용
- WAR는 `webapp/resources/**`, BOOT는 `static/resources/**`에 파일 생성
- `_ds_bundle.css`는 `styles.css` 내부 `@import` 대상으로 취급

---

## 테스트 반영 상태

아래 테스트로 BOOT 정적 리소스 생성과 검증을 확인한다.

- `src/test/java/com/krdevops/springai/service/initializr/ProjectInitializrBoot50StaticResourceWorkflowTest.java`
- `src/test/java/com/krdevops/springai/service/initializr/ProjectInitializrWar50ManualWorkflowTest.java`

검증 포인트:

- BOOT 디렉터리 계획에 `static/resources/css`, `static/resources/js`가 포함되는지
- BOOT 파일 계획에 정적 리소스 3종이 포함되는지
- BOOT 결과 검증 시 누락 파일 경고가 기대 경로로 나오는지

---

## 결론

문서 초안 단계에서 남아 있던 “BOOT는 정적 리소스를 생성하지 않는다”는 상태는 더 이상 현재 코드와 맞지 않는다.

현재 구현은 다음까지 완료된 상태다.

- BOOT 정적 리소스 3종 생성
- BOOT 검증 규칙 추가
- `ProjectInitializrTool` 설명 반영
- `CrudPromptBuilderTool` 계열의 `/resources/**` 공통 URL 정책 반영
- 회귀 테스트 추가
