# BOOT/WAR Thymeleaf README 반영 영향 검토

## 대상 문서

- `templates/boot-thymeleaf/mcp/README.md`
- `templates/war-thymeleaf/mcp/README.md`

검토 대상 코드는 다음 두 축이다.

- `ProjectInitializrTool` / `ProjectInitializrService` / `FilePlanFactory`
- `CrudPromptBuilderTool` / Thymeleaf FTL 템플릿

---

## 주요 영향

### 1. ProjectInitializrTool의 BOOT 분기는 README 요구를 충족하지 못함

두 README 모두 Boot Thymeleaf 구조에서 `static/css/ftc-portal.css` 같은 정적 리소스 배치를 전제로 한다.

- 참고: `templates/boot-thymeleaf/mcp/README.md:40`
- 참고: `templates/war-thymeleaf/mcp/README.md:40`

그런데 현재 BOOT 생성은 아래 디렉터리만 만들고 실제 정적 리소스 파일은 생성하지 않는다.

- `src/main/resources/static/css`
- `src/main/resources/static/js`
- `src/main/resources/templates`

참고:

- `src/main/java/com/krdevops/springai/service/initializr/FilePlanFactory.java:61`
- `src/main/java/com/krdevops/springai/service/initializr/FilePlanFactory.java:122`

반대로 WAR 분기만 아래 파일을 실제 생성한다.

- `src/main/webapp/resources/css/styles.css`
- `src/main/webapp/resources/css/_ds_bundle.css`
- `src/main/webapp/resources/js/krds.min.js`

참고:

- `src/main/java/com/krdevops/springai/service/initializr/FilePlanFactory.java:91`

즉, README를 기준으로 보면 BOOT 정적 리소스 생성이 누락된 상태다.

### 2. CrudPromptBuilderTool의 현재 계약과 README의 MCP 설계가 다름

README는 아래처럼 화면별 개별 Tool 구조를 전제로 한다.

- `generateBoardList`
- `generateBoardDetail`
- `generateMasterList`

참고:

- `templates/boot-thymeleaf/mcp/README.md:227`

현재 구현은 아래처럼 오케스트레이션 단위 Tool 구조다.

- `buildFullCrudPrompt`
- `buildMasterDetailPrompt`
- `buildBoardFeature`

참고:

- `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java:28`
- `src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java:141`

따라서 README를 그대로 반영하려면 다음 둘 중 하나가 필요하다.

- Tool API 자체를 새로 추가
- README를 현재 Tool API에 맞게 재작성

### 3. Thymeleaf 레이아웃 계약도 충돌함

README는 다음 구조를 전제로 한다.

- `layout/default.html`
- `gnb.html`
- `lnb.html`
- `breadcrumb.html`
- `footer.html`

또한 컨트롤러가 다음 모델 속성을 내려준다고 가정한다.

- `lnbTitle`
- `lnbMenus`
- `breadcrumbs`
- `currentMenuId`

참고:

- `templates/boot-thymeleaf/mcp/README.md:14`
- `templates/boot-thymeleaf/mcp/README.md:196`

현재 생성 템플릿은 단일 `layout/default.html.ftl` 중심이고, 위 모델 속성 계약이 없다.

참고:

- `src/main/resources/templates/board/layout/default.html.ftl:1`

즉, README 수준으로 맞추려면 템플릿 파일 구조뿐 아니라 Controller/Model 계약도 같이 바뀌어야 한다.

### 4. WAR README 자체가 stale 가능성이 높음

`templates/war-thymeleaf/mcp/README.md`도 같은 내용으로 `egov-boot-web 적용 위치`를 설명하고 있다.

참고:

- `templates/war-thymeleaf/mcp/README.md:40`

즉, WAR 문서라기보다 Boot 문서를 복제한 상태로 보인다.
이 상태로 구현 기준 문서로 쓰면 WAR/BOOT 경로를 혼동시킬 가능성이 높다.

---

## 반영 가능 범위

### ProjectInitializrTool에 반영 가능한 것

- BOOT 분기에서도 `src/main/resources/static/css/styles.css`, `static/css/_ds_bundle.css`, `static/js/krds.min.js` 생성
- Tool 설명문에 WAR/BOOT별 정적 리소스 경로를 분리 명시
- `ProjectValidator`도 BOOT일 때 정적 리소스 존재 검증 추가

### CrudPromptBuilderTool에 반영 가능한 것

- Thymeleaf 템플릿을 `layout/default + partial` 구조로 재편
- 화면 템플릿에서 breadcrumb/LNB/GNB 활성 상태용 모델 속성 계약 추가
- README의 FTC 스타일을 `styles.css.tpl`과 Thymeleaf FTL에 흡수

---

## 그대로 반영하기 어려운 것

- README의 개별 MCP Tool 이름 체계 (`generateBoardList` 등)
- README의 `mcp-templates` 중심 구조
- 현재 `buildBoardFeature` / `buildFullCrudPrompt` 오케스트레이션과 다른 호출 모델

즉, README는 참고 설계로는 쓸 수 있지만 현재 코드에 그대로 덮어쓰기에는 구조 차이가 있다.

---

## 권장 순서

1. `war README`와 `boot README`를 먼저 분리 정정
2. `ProjectInitializrTool`에 BOOT 정적 리소스 생성 추가
3. `styles.css.tpl`을 FTC 기준으로 교체 또는 확장
4. `CrudPromptBuilderTool` Thymeleaf 레이아웃을 partial 구조로 개편
5. 필요하면 화면별 세분 Tool은 별도 신규 MCP Tool로 추가

---

## 결론

두 README의 아이디어는 반영 가치가 있다.
다만 현재 코드와는 아래 세 가지가 다르다.

- 정적 리소스 경로
- Tool API 구조
- 레이아웃/모델 계약

즉시 반영 가능한 것은 다음 두 가지다.

- `ProjectInitializrTool`의 BOOT 정적 리소스 생성 추가
- `CrudPromptBuilderTool`의 partial 레이아웃 분리

그 외 항목은 README를 현재 설계에 맞게 재작성한 뒤 반영하는 것이 적절하다.
