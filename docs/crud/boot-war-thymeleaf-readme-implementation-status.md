# BOOT/WAR Thymeleaf README 반영 진행 현황

## 기준 문서

- `docs/crud/boot-war-thymeleaf-readme-impact-analysis.md`

이 문서는 영향 검토 문서에서 제시한 반영 항목을
현재 코드 기준으로 `완료` / `미완료`로 추적하기 위한 상태 문서다.

---

## 현재 요약

- 완료 6건
- 미완료 0건

핵심 상태:

- `ProjectInitializrTool`의 BOOT 정적 리소스 생성 추가: 완료
- WAR/BOOT README 정적 자산 경로 정정: 완료
- `/resources/**` 공통 자산 URL 정책 반영: 완료
- `CrudPromptBuilderTool` partial 레이아웃 분리: 완료
- README의 Controller/Model 계약 반영: 완료
- README를 현재 API 기준으로 재작성: 완료

---

## 완료

### 1. `ProjectInitializrTool`의 BOOT 정적 리소스 생성 추가

상태: `완료`

반영 내용:

- BOOT 정적 자산 생성 경로 추가
  - `src/main/resources/static/resources/css/styles.css`
  - `src/main/resources/static/resources/css/_ds_bundle.css`
  - `src/main/resources/static/resources/js/krds.min.js`
- BOOT 결과 검증 규칙 추가
- Tool 설명문 반영

근거 파일:

- `src/main/java/com/krdevops/springai/service/initializr/FilePlanFactory.java`
- `src/main/java/com/krdevops/springai/service/initializr/ProjectValidator.java`
- `src/main/java/com/krdevops/springai/tools/ProjectInitializrTool.java`
- `src/test/java/com/krdevops/springai/service/initializr/ProjectInitializrBoot50StaticResourceWorkflowTest.java`

### 2. WAR/BOOT README 정적 자산 경로 정정

상태: `완료`

반영 내용:

- BOOT README를 `static/resources/**` 기준으로 수정
- WAR README를 `webapp/resources/**` 기준으로 수정
- 두 문서 모두 `/resources/**` 공통 URL 정책 명시
- `_ds_bundle.css`는 `styles.css` 내부 `@import` 대상으로 정리

근거 파일:

- `templates/boot-thymeleaf/mcp/README.md`
- `templates/war-thymeleaf/mcp/README.md`

### 3. `/resources/**` 공통 자산 URL 정책 반영

상태: `완료`

반영 내용:

- WAR/BOOT 모두 화면 링크 URL은 `/resources/**` 유지
- WAR 파일 저장 위치는 `webapp/resources/**`
- BOOT 파일 저장 위치는 `static/resources/**`
- `CrudPromptBuilderTool` 및 생성 서비스 안내 문구 반영

근거 파일:

- `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java`
- `src/main/java/com/krdevops/springai/service/CrudPromptBuilderService.java`
- `src/main/java/com/krdevops/springai/service/MasterDetailService.java`

### 4. `CrudPromptBuilderTool` partial 레이아웃 분리

상태: `완료`

반영 내용:

- Thymeleaf 레이아웃을 아래 partial 생성 구조로 확장
  - `layout/default.html`
  - `layout/gnb.html`
  - `layout/lnb.html`
  - `layout/breadcrumb.html`
  - `layout/footer.html`
- `default.html`은 `th:replace`로 partial을 조합
- list/detail/regist/updt 화면은 breadcrumb 반복 마크업 대신 partial include 사용
- CRUD / Board / MasterDetail 레이어 정의와 렌더러 매핑 확장
- 파일 수 반영
  - CRUD Thymeleaf: 12개 → 16개
  - Board Thymeleaf: 13개 → 17개
  - MasterDetail Thymeleaf: 14개 → 18개

근거 파일:

- `src/main/java/com/krdevops/springai/model/crud/CrudLayerDefinition.java`
- `src/main/java/com/krdevops/springai/model/board/BoardLayerDefinition.java`
- `src/main/java/com/krdevops/springai/model/masterdetail/MasterDetailLayerDefinition.java`
- `src/main/java/com/krdevops/springai/service/CrudTemplateRenderer.java`
- `src/main/java/com/krdevops/springai/service/BoardTemplateRenderer.java`
- `src/main/java/com/krdevops/springai/service/MasterDetailTemplateRenderer.java`
- `src/main/resources/templates/crud/layout/*.ftl`
- `src/main/resources/templates/board/layout/*.ftl`
- `src/main/resources/templates/masterdetail/layout/*.ftl`

### 5. README의 Controller/Model 계약 반영

상태: `완료`

반영 내용:

- Controller 템플릿이 아래 모델 속성을 뷰 렌더링 전에 주입하도록 확장
  - `lnbTitle`
  - `lnbMenus`
  - `breadcrumbs`
  - `currentMenuId`
- CRUD / Board / MasterDetail Controller FTL에 `populateLayoutModel(...)` 공통 메서드 추가
- partial 레이아웃이 위 모델 속성을 기준으로 실제 렌더링하도록 수정

근거 파일:

- `src/main/resources/templates/crud/controller.java.ftl`
- `src/main/resources/templates/board/controller.java.ftl`
- `src/main/resources/templates/masterdetail/controller.java.ftl`
- `src/main/resources/templates/crud/layout/{gnb,lnb,breadcrumb}.html.ftl`
- `src/main/resources/templates/board/layout/{gnb,lnb,breadcrumb}.html.ftl`
- `src/main/resources/templates/masterdetail/layout/{gnb,lnb,breadcrumb}.html.ftl`

### 6. README를 현재 API 기준으로 재작성

상태: `완료`

반영 내용:

- README의 화면별 `generate*` Tool 설명을 실제 MCP Tool 체계로 교체
  - `buildFullCrudPrompt`
  - `buildMasterDetailPrompt`
  - `buildBoardFeature`
  - `buildJoinSelectPrompt`
- 사용 예시를 현재 파라미터 구조 기준으로 교체
- `auto / claude` 모드 차이 설명 추가
- 화면 1개 생성기가 아니라 기능 세트 생성기라는 점을 문서화

근거 파일:

- `templates/boot-thymeleaf/mcp/README.md`
- `templates/war-thymeleaf/mcp/README.md`

---

## 후속 검토 항목

현재 기준으로 필수 미완료 항목은 없다.

선택 과제로는 아래가 남아 있다.

- 화면별 세분 MCP Tool (`generateBoardList`류) 신규 추가 여부 검토

## 진행 체크리스트

- [x] BOOT 정적 리소스 생성 추가
- [x] BOOT 검증 규칙 추가
- [x] WAR/BOOT README 자산 경로 정정
- [x] `/resources/**` 공통 URL 정책 반영
- [x] partial 레이아웃 분리
- [x] Controller/Model 계약 반영
- [x] README를 현재 API 기준으로 재작성
- [ ] 화면별 세분 MCP Tool 신규 추가 여부 검토

---

## 권장 다음 순서

1. 화면별 세분 MCP Tool (`generateBoardList`류) 신규 추가 필요성 재검토

---

## 메모

`docs/crud/boot-war-thymeleaf-readme-impact-analysis.md`는 최초 영향 검토 시점의 문서라
현재 완료된 항목까지 모두 반영한 최신 상태 문서는 아니다.

진행 추적과 현재 상태 확인은 이 문서를 기준으로 보는 것이 맞다.
