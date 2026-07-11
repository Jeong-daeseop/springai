# buildFullCrudPrompt 인라인 스타일 제거 수정요구 명세서

> 기준일: 2026-07-09  
> 대상: `buildFullCrudPrompt`, `generateThymeleafLayout`, `buildBoardFeature`, `buildMasterDetailPrompt`  
> 목적: 생성 화면마다 인라인 스타일을 반복 생성하지 않고, `initializeProject()`가 생성하는 공통 `styles.css` 기준으로 화면 스타일을 고정한다.

## 1. 배경

현재 Thymeleaf 화면 생성 템플릿은 화면 본문과 layout 템플릿에 `style="..."` 속성을 직접 포함한다.

이 방식은 생성 결과가 즉시 보기 좋게 나오는 장점은 있지만, 다음 문제가 있다.

- 화면마다 동일한 레이아웃/간격/색상 규칙이 반복된다.
- 생성 후 디자인 수정이 필요할 때 HTML 파일을 다수 수정해야 한다.
- `styles.css`가 공통 디자인 기준점인데, 실제 화면 세부 스타일은 각 템플릿의 인라인 스타일에 분산된다.
- 인라인 스타일 우선순위가 높아 `_ds_bundle.css` 또는 프로젝트 공통 CSS로 후속 조정하기 어렵다.
- `crud`, `board`, `masterdetail` 템플릿이 유사한 스타일을 서로 복제한다.

따라서 생성 화면의 시각 규칙은 공통 CSS로 이전하고, HTML/Thymeleaf 템플릿은 의미 있는 class와 data attribute를 사용하는 구조로 전환한다.

## 2. 현재 상태

### 2.1 공통 CSS 생성 구조

`initializeProject()`는 WAR/Boot 프로젝트 모두에 공통 CSS를 생성한다.

- WAR: `src/main/webapp/resources/css/styles.css`
- Boot: `src/main/resources/static/resources/css/styles.css`

원본 템플릿은 다음 파일이다.

- `src/main/resources/templates/egov/styles.css.tpl`

현재 `styles.css.tpl`는 다음 역할을 한다.

- Pretendard GOV 폰트 import
- `_ds_bundle.css` import
- KRDS label font-size token 보정
- `tbl.col`, `tbl.data` 테이블 스타일 보정

### 2.2 수정 전 인라인 스타일 사용량

수정 전 FreeMarker 템플릿 기준 `style="..."` 사용량은 다음과 같았다.

| 영역 | 인라인 스타일 수 |
|------|------------------|
| `crud` | 85 |
| `board` | 96 |
| `masterdetail` | 92 |

`buildFullCrudPrompt(viewType="thymeleaf")`의 직접 대상은 `crud` 영역이다. 다만 `board`, `masterdetail`도 동일한 화면 패턴을 사용하므로 정책 일관성을 위해 후속 적용 대상에 포함한다.

## 3. 범위 구분

### 3.1 1차 필수 범위

`buildFullCrudPrompt(viewType="thymeleaf")` 생성 결과에서 인라인 스타일을 제거한다.

대상 파일:

- `src/main/resources/templates/crud/thymeleaf-list-body.html.ftl`
- `src/main/resources/templates/crud/thymeleaf-detail-body.html.ftl`
- `src/main/resources/templates/crud/thymeleaf-regist-body.html.ftl`
- `src/main/resources/templates/crud/thymeleaf-updt-body.html.ftl`
- `src/main/resources/templates/crud/layout/default.html.ftl`
- `src/main/resources/templates/crud/layout/gnb.html.ftl`
- `src/main/resources/templates/crud/layout/lnb.html.ftl`
- `src/main/resources/templates/crud/layout/footer.html.ftl`

공통 CSS 대상:

- `src/main/resources/templates/egov/styles.css.tpl`

### 3.2 2차 확장 범위

동일 정책을 BBS 전용 생성과 마스터-디테일 생성에도 적용한다.

대상 디렉터리:

- `src/main/resources/templates/board/`
- `src/main/resources/templates/masterdetail/`

### 3.3 선택 범위

`layoutMode=none` standalone 템플릿과 JSP 템플릿에도 인라인 스타일이 일부 존재한다.

- `thymeleaf-*-standalone.html.ftl`
- `jsp-*.jsp.ftl`

요구사항을 "Thymeleaf layout 기반 화면"으로 한정하면 선택 범위에서 제외할 수 있다. 요구사항을 "생성 화면 전체"로 정의하면 별도 단계에서 포함해야 한다.

## 4. 수정 요구사항

### 4.1 공통 CSS 클래스 추가

`styles.css.tpl`에 생성 화면 전용 공통 클래스를 추가한다.

필수 클래스 후보:

| 클래스 | 용도 |
|--------|------|
| `.egov-layout-shell` | layout의 전체 본문 shell |
| `.egov-layout-content` | layout의 실제 콘텐츠 영역 |
| `.egov-page-header` | 목록/상세/등록/수정 상단 제목 영역 |
| `.egov-page-title` | 화면 제목 |
| `.egov-search-panel` | 목록 검색 박스 |
| `.egov-search-row` | 검색 항목 flex row |
| `.egov-inline-action` | 아이콘+텍스트 버튼 내부 정렬 |
| `.egov-list-summary` | 목록 총 건수/페이지 정보 영역 |
| `.egov-muted-text` | 보조 텍스트 |
| `.egov-primary-text` | 강조 텍스트 |
| `.egov-table-no` | 목록 번호 셀 |
| `.egov-row-link` | 클릭 가능한 목록 행 |
| `.egov-detail-link` | 목록 셀의 상세 링크 |
| `.egov-table-actions` | 테이블 관리 버튼 셀 |
| `.egov-empty-state` | 데이터 없음 상태 |
| `.egov-form-required-guide` | 필수 입력 안내 |
| `.egov-required-mark` | 필수 표시 별표 |
| `.egov-field-error` | 폼 검증 오류 메시지 |
| `.egov-form-actions` | 등록/수정 하단 버튼 영역 |
| `.egov-button-group` | 버튼 묶음 |
| `.egov-toast` | 처리 결과 토스트 |
| `.egov-toast-icon` | 토스트 아이콘 |
| `.egov-section` | 상세 화면 section |
| `.egov-section-title` | 상세 화면 section 제목 |
| `.egov-modal` | 삭제 확인 dialog |
| `.egov-modal-title` | dialog 제목 |
| `.egov-modal-desc` | dialog 설명 |
| `.egov-hidden` | 숨김 form |
| `.egov-footer` | 공통 footer |
| `.egov-footer-inner` | footer 내부 layout |
| `.egov-brand-mark` | 브랜드 원형 마크 |
| `.egov-lnb` | LNB aside |
| `.egov-lnb-title` | LNB 제목 |
| `.egov-lnb-list` | LNB 목록 |
| `.egov-header-top` | GNB 상단 얇은 바 |
| `.egov-header-top-inner` | GNB 상단 내부 |
| `.egov-header` | 메인 header |
| `.egov-header-inner` | header 내부 |
| `.egov-header-brand` | header 브랜드 영역 |

### 4.2 Thymeleaf 본문 템플릿 수정

`crud/thymeleaf-*-body.html.ftl`의 인라인 스타일을 class로 치환한다.

예시:

```html
<div style="display:flex;align-items:center;...">
```

변경:

```html
<div class="egov-page-header">
```

다음 항목은 반드시 class 기반으로 바꾼다.

- 페이지 헤더
- 제목
- 검색 패널
- 검색 form row
- 버튼 내 아이콘 정렬
- 목록 요약
- 행 클릭 cursor
- 번호 셀 정렬
- 상세 링크 강조
- 관리 버튼 셀
- 데이터 없음 상태
- 필수 입력 안내
- 필수 표시
- 필드 오류
- 하단 버튼 영역
- 삭제 확인 dialog
- hidden delete form
- 토스트 alert

### 4.3 layout 템플릿 수정

`crud/layout/*.html.ftl`의 인라인 스타일을 class로 치환한다.

대상:

- header/GNB top bar
- header inner
- brand mark
- layout shell
- content section
- LNB title/list
- footer

`default.html.ftl` 내부 `<style>` 블록도 가능하면 `styles.css.tpl`로 이전한다.

단, 화면별 동작에 필요한 최소 script는 유지할 수 있다.

### 4.4 생성 도구 설명 갱신

Tool description과 문서에 다음 정책을 명시한다.

- 생성 화면은 `/resources/css/styles.css`의 공통 class를 기준으로 스타일링한다.
- Thymeleaf 화면 템플릿은 인라인 style을 생성하지 않는다.
- `_ds_bundle.css`는 직접 link하지 않고 `styles.css` 내부 import를 유지한다.
- 디자인 변경은 생성 HTML이 아니라 `styles.css.tpl`에서 한다.

대상:

- `CrudPromptBuilderTool`
- `ThymeleafLayoutTool`
- `buildFullCrudPrompt_사용가이드.md`

### 4.5 검증 기준 추가

생성 결과 검증에 다음 기준을 추가한다.

- `src/main/resources/templates/{domain}/Egov*.html`에 `style="`가 없어야 한다.
- layout 기반 생성 결과의 `templates/layout/*.html`에 `style="`가 없어야 한다.
- 예외가 필요한 경우 문서화된 allowlist로 관리한다.

## 5. 수정 목록

### 5.1 1차 수정 목록: `buildFullCrudPrompt`

| 순서 | 파일 | 수정 내용 |
|------|------|-----------|
| 1 | `styles.css.tpl` | CRUD/공통 layout class 추가 |
| 2 | `crud/thymeleaf-list-body.html.ftl` | 목록 화면 인라인 style 제거 및 class 치환 |
| 3 | `crud/thymeleaf-detail-body.html.ftl` | 상세 화면 인라인 style 제거 및 class 치환 |
| 4 | `crud/thymeleaf-regist-body.html.ftl` | 등록 화면 인라인 style 제거 및 class 치환 |
| 5 | `crud/thymeleaf-updt-body.html.ftl` | 수정 화면 인라인 style 제거 및 class 치환 |
| 6 | `crud/layout/default.html.ftl` | layout shell/content style class화, 내부 style block 축소 |
| 7 | `crud/layout/gnb.html.ftl` | header/GNB 인라인 style 제거 |
| 8 | `crud/layout/lnb.html.ftl` | LNB 인라인 style 제거 |
| 9 | `crud/layout/footer.html.ftl` | footer 인라인 style 제거 |
| 10 | `CrudPromptBuilderTool.java` | Tool 설명에 공통 CSS 정책 반영 |
| 11 | `CrudTemplateRenderer` 또는 검증기 | 필요 시 style 속성 금지 검증 추가 |

### 5.2 2차 수정 목록: `buildBoardFeature`

| 순서 | 파일 | 수정 내용 |
|------|------|-----------|
| 1 | `board/thymeleaf-list-body.html.ftl` | 목록 화면 class 기반 전환 |
| 2 | `board/thymeleaf-detail-body.html.ftl` | 상세 화면 class 기반 전환 |
| 3 | `board/thymeleaf-regist-body.html.ftl` | 등록 화면 class 기반 전환 |
| 4 | `board/thymeleaf-updt-body.html.ftl` | 수정 화면 class 기반 전환 |
| 5 | `board/layout/*.html.ftl` | layout 인라인 style 제거 |
| 6 | `buildBoardFeature` Tool 설명 | 공통 CSS 정책 반영 |

### 5.3 3차 수정 목록: `buildMasterDetailPrompt`

| 순서 | 파일 | 수정 내용 |
|------|------|-----------|
| 1 | `masterdetail/thymeleaf-list-body.html.ftl` | 목록 화면 class 기반 전환 |
| 2 | `masterdetail/thymeleaf-detail-body.html.ftl` | 상세 화면 class 기반 전환 |
| 3 | `masterdetail/thymeleaf-regist-body.html.ftl` | 등록 화면 class 기반 전환 |
| 4 | `masterdetail/layout/*.html.ftl` | layout 인라인 style 제거 |
| 5 | `MasterDetailService` 또는 관련 Tool 설명 | 공통 CSS 정책 반영 |

### 5.4 선택 수정 목록

| 파일군 | 판단 기준 |
|--------|-----------|
| `thymeleaf-*-standalone.html.ftl` | 완료: `egov-standalone-shell` 공통 class로 전환 |
| `jsp-*.jsp.ftl` | 완료: 잔여 col width/form display inline style을 공통 class로 전환 |
| `docs/tool-reference/*.md` | 공개 Tool 문서를 최신 정책으로 맞출 때 수정 |

## 6. 수용 기준

다음 조건을 모두 만족하면 완료로 본다.

- `buildFullCrudPrompt(viewType="thymeleaf", layoutMode="reuse")` 생성 결과의 화면 HTML에 `style="`가 없다.
- `generateThymeleafLayout()`으로 생성한 layout HTML에 `style="`가 없다.
- 생성 화면은 `/resources/css/styles.css`만으로 기존 수준의 레이아웃, 간격, 색상, 버튼 정렬을 유지한다.
- `gradle build` 또는 `./gradlew build`가 성공한다.
- 신규 생성 프로젝트에서 목록/상세/등록/수정 화면이 정상 렌더링된다.
- 테이블 헤더, 검색 영역, pagination, 토스트, 삭제 dialog가 모바일/데스크톱에서 겹치지 않는다.

## 7. 테스트 계획

### 7.1 단위 검증

- FreeMarker 렌더링 테스트
- 생성 HTML 내 `style="` 문자열 검사
- `styles.css.tpl`에 필수 class 존재 검사

### 7.2 통합 검증

1. `initializeProject(...)`
2. `generateThymeleafLayout(...)`
3. `buildFullCrudPrompt(..., viewType="thymeleaf", layoutMode="reuse")`
4. 생성 프로젝트 빌드
5. 생성 HTML의 style 속성 검사

### 7.3 시각 검증

가능하면 Playwright 또는 브라우저 캡처로 다음 화면을 확인한다.

- 목록
- 상세
- 등록
- 수정
- 삭제 확인 dialog
- 검색 결과 없음 상태
- 모바일 폭

## 8. 리스크 및 대응

| 리스크 | 설명 | 대응 |
|--------|------|------|
| KRDS 번들 CSS와 충돌 | 인라인보다 class 우선순위가 낮아질 수 있음 | `.krds-table-wrap .egov-*` 등 필요한 범위만 특이도 보강 |
| 기존 생성 프로젝트 미반영 | 템플릿 수정만으로 이미 생성된 프로젝트는 바뀌지 않음 | 재생성 또는 별도 마이그레이션 안내 |
| `board`, `masterdetail`와 시각 불일치 | `crud`만 먼저 바꾸면 도구별 결과가 달라짐 | 1차 후 동일 class를 재사용해 2차 확장 |
| standalone 화면 누락 | `layoutMode=none` 사용 시 인라인 style이 남을 수 있음 | `thymeleaf-*-standalone.html.ftl` wrapper를 `egov-standalone-shell`로 전환 |
| allowlist 필요 | `display:none` 같은 단순 상태 style이 남을 수 있음 | `.egov-hidden` 등 class로 대체하고 예외 최소화 |

## 9. 관련 파일

- [styles.css.tpl](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/resources/templates/egov/styles.css.tpl)
- [crud/thymeleaf-list-body.html.ftl](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/resources/templates/crud/thymeleaf-list-body.html.ftl)
- [crud/thymeleaf-detail-body.html.ftl](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/resources/templates/crud/thymeleaf-detail-body.html.ftl)
- [crud/thymeleaf-regist-body.html.ftl](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/resources/templates/crud/thymeleaf-regist-body.html.ftl)
- [crud/thymeleaf-updt-body.html.ftl](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/resources/templates/crud/thymeleaf-updt-body.html.ftl)
- [crud/layout/default.html.ftl](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/resources/templates/crud/layout/default.html.ftl)
- [CrudPromptBuilderTool.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java)
- [CrudTemplateRenderer.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/service/CrudTemplateRenderer.java)
- [CrudOrchestrationService.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/service/CrudOrchestrationService.java)

## 10. 구현 상태

### 완료

- `styles.css.tpl`
  - CRUD/layout 공통 `egov-*` 클래스 추가
  - 기존 `crud/layout/default.html.ftl` 내부 `<style>` 블록에 있던 GNB/LNB/layout 규칙 이전
  - 페이지 헤더, 검색 패널, 목록 요약, 토스트, modal, empty state, form action 등 공통 화면 클래스 추가
- `crud/thymeleaf-list-body.html.ftl`
  - 목록 화면 인라인 `style` 제거
  - col width, 행 pointer, 번호 셀, 상세 링크, empty state를 class 기반으로 변경
- `crud/thymeleaf-detail-body.html.ftl`
  - 상세 화면 인라인 `style` 제거
  - 토스트, section, 버튼 영역, 삭제 dialog, hidden form을 class 기반으로 변경
- `crud/thymeleaf-regist-body.html.ftl`
  - 등록 화면 인라인 `style` 제거
  - 필수 표시, 필드 오류, 하단 버튼 영역을 class 기반으로 변경
- `crud/thymeleaf-updt-body.html.ftl`
  - 수정 화면 인라인 `style` 제거
  - 읽기 전용 값, 필수 표시, 필드 오류, 하단 버튼 영역을 class 기반으로 변경
- `crud/layout/default.html.ftl`
  - 내부 `<style>` 블록 제거
  - layout shell/content를 class 기반으로 변경
- `crud/layout/gnb.html.ftl`
  - header/GNB 인라인 `style` 제거
  - header top, brand, mark를 class 기반으로 변경
- `crud/layout/lnb.html.ftl`
  - LNB 인라인 `style` 제거
  - sidebar/title/list/sublist/empty state를 class 기반으로 변경
- `crud/layout/footer.html.ftl`
  - footer 인라인 `style` 제거
  - footer inner, brand, policy, copy 영역을 class 기반으로 변경
- `CrudPromptBuilderTool.java`
  - Thymeleaf 화면/layout이 `styles.css`의 `egov-*` 공통 클래스를 사용한다는 설명 추가
- `ThymeleafLayoutTool.java`
  - layout 생성 결과가 인라인 style이 아닌 공통 CSS 기반이라는 설명 추가
- `board/*`
  - Thymeleaf layout, list/detail/regist/updt body, standalone wrapper의 인라인 style 제거
  - 게시판 첨부파일, 공지 행, 이전글/다음글, textarea UI를 공통 `egov-*` class 기반으로 변경
- `masterdetail/*`
  - Thymeleaf layout, list/detail/regist body, standalone wrapper의 인라인 style 제거
  - 선택삭제, 상태 배지, 하위목록 section, modal, empty state를 공통 `egov-*` class 기반으로 변경
- `crud`, `board`, `masterdetail` standalone 템플릿
  - `<style>` 블록 제거
  - wrapper inline style을 `.egov-standalone-shell`로 변경
- JSP 템플릿 잔여 inline style
  - `crud/jsp-list.jsp.ftl` col width class화
  - `crud/jsp-detail.jsp.ftl`, `board/jsp-detail.jsp.ftl` inline form class화
- 테스트
  - `CrudTemplateRendererTest`, `BoardTemplateRendererTest`, `MasterDetailTemplateRendererTest`를 공통 CSS class 기준으로 갱신
  - 템플릿 전체 `style="`, `<style>`, `th:styleappend` 검색 결과 없음

### 검증 완료

- `rg -n 'style="|<style|th:styleappend' src/main/resources/templates -g '*.ftl'`
  - 결과 없음
- `./gradlew test`
  - BUILD SUCCESSFUL

### 남은 확인

- 실제 생성 프로젝트에서 브라우저 렌더링 시각 검증
- 공개 Tool reference 문서 최신화 여부 결정
