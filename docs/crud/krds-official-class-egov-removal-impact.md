
# egov-* 클래스 제거 및 KRDS 공식 클래스 전환 — 완료 현황

> 최종 갱신: 2026-07-01 — 현재 코드 기준으로 재작성.
>
> 참조:
> - `/Users/jeongdaeseob/Downloads/KRDS Design System`
> - `docs/crud/thymeleaf-ftl-design-template-gap-analysis.md`

---

## 결론

`egov-*` 클래스 제거 및 KRDS 공식 클래스 전환이 **완료**되었다.

레이아웃 2개와 화면 FTL 11개 전체에서 `egov-*` 참조가 0건이다.
두 레이아웃(`crud/layout/default.html.ftl`, `masterdetail/layout/default.html.ftl`)은 내용이 동일하며
`krds-main-menu` GNB·`data-layout-sidebar` LNB·inline styled Footer·`styles.css` 링크를 포함한다.

검증:
```bash
grep -r "egov-" src/main/resources/templates/crud/ \
                src/main/resources/templates/board/ \
                src/main/resources/templates/masterdetail/ \
    --include="*.ftl"
# 결과: 0건
```

---

## 완료된 항목

### 레이아웃

| 파일 | 완료 내용 |
|---|---|
| `crud/layout/default.html.ftl` | `egov-*` CSS 블록·마크업 제거. `krds-main-menu` GNB, `data-layout-sidebar` LNB, inline Footer, `styles.css` 링크 적용 |
| `masterdetail/layout/default.html.ftl` | crud/layout과 동일 구조로 재작성 (GNB/LNB/Footer 모두 포함) |

### 화면 FTL

| 파일 | 완료 내용 |
|---|---|
| `crud/thymeleaf-list.html.ftl` | `krds-breadcrumb-wrap`, inline page title, inline search box, `krds-form-select`·`krds-input`·`krds-btn`, `data-row-link` 행 클릭, `krds-pagination` |
| `crud/thymeleaf-detail.html.ftl` | `krds-breadcrumb-wrap`, inline page title, inline alert, `krds-table-wrap`, `krds-btn` |
| `crud/thymeleaf-regist.html.ftl` | `krds-breadcrumb-wrap`, inline page title, `krds-input`, `krds-btn` |
| `crud/thymeleaf-updt.html.ftl` | 동일 |
| `board/thymeleaf-list.html.ftl` | `krds-breadcrumb-wrap`, inline page title, `krds-form-select`·`krds-input`·`krds-btn`, `krds-pagination` |
| `board/thymeleaf-detail.html.ftl` | `krds-breadcrumb-wrap`, inline page title, inline alert, `krds-table-wrap`, `krds-btn` |
| `board/thymeleaf-regist.html.ftl` | `krds-breadcrumb-wrap`, `krds-table-wrap`, `krds-input`, `krds-btn` |
| `board/thymeleaf-updt.html.ftl` | 동일 |
| `masterdetail/thymeleaf-list.html.ftl` | `krds-breadcrumb-wrap`, inline page title, `krds-form-select`·`krds-input`·`krds-btn`, `data-row-link` 행 클릭, `krds-pagination` |
| `masterdetail/thymeleaf-detail.html.ftl` | `krds-breadcrumb-wrap`, inline page title, inline alert, `krds-table-wrap`, `krds-btn` |
| `masterdetail/thymeleaf-regist.html.ftl` | `krds-breadcrumb-wrap`, `krds-input`, `krds-btn` |

### 생성기/서비스

| 파일 | 완료 내용 |
|---|---|
| `FilePlanFactory.java` | 출력 경로·파일 계획에서 `styles.css` 기준으로 변경 |
| `ProjectValidator.java` | `styles.css` / `_ds_bundle.css` 기준으로 변경 |
| `CrudPromptBuilderTool.java` | Tool 설명에서 KRDS 공식 클래스 기준으로 문구 변경 |

---

## 전환 클래스 목록

| 이전 클래스 | 전환 결과 |
|---|---|
| `egov-masthead`, `egov-header`, `egov-brand`, `egov-shell` | 제거. 레이아웃 구조 inline style로 재작성 |
| `egov-lnb`, `egov-lnb-*` | 제거. `data-layout-sidebar` + inline style LNB |
| `egov-footer`, `egov-footer-*` | 제거. inline styled footer |
| `egov-breadcrumb` | `krds-breadcrumb-wrap` |
| `egov-page-title` | inline style div + `krds-btn` |
| `egov-section-title` | inline style h2 또는 section 헤더 |
| `egov-search-box`, `egov-search-row` | inline style 컨테이너 + `krds-form-select`·`krds-input`·`krds-btn` |
| `egov-row-link` | `data-row-link="true"` 속성 + `tr[data-row-link][data-href]` JS selector |
| `egov-alert` | inline style div + `role="alert"` |
| `egov-required` | inline style `<span>` |
| `egov-error` | inline style 오류 텍스트 |
| `egov-btn-area`, `egov-btn-group` | inline style flex div |
| `egov-btn`, `egov-table-wrap`, `egov-input`, `egov-pagination` 등 | `krds-btn`, `krds-table-wrap`, `krds-input`, `krds-pagination` 등으로 전환 |

---

## 잔여 항목 (Design Templates와의 구조 Gap)

`egov-*` 제거는 완료되었으나 Design Templates 원본과의 구조 차이가 남아 있다.
상세 내용은 `docs/crud/thymeleaf-ftl-design-template-gap-analysis.md` 참조.

| 항목 | 상태 |
|---|---|
| GNB/LNB 업무별 메뉴 (조직별 실제 메뉴) | △ generic 구조 |
| Board 공지 고정 행, 첨부 아이콘 컬럼 | ❌ 미구현 |
| Board textarea / 파일 업로드 | ❌ 미구현 |
| 이전글/다음글 네비게이션 (Board 상세) | ❌ 미구현 |
| MasterDetail 체크박스 + 일괄삭제 + 상태 배지 | ❌ 미구현 |
| 삭제 확인 모달 (전체) | △ `confirm()` 팝업 사용 중 |
| 저장·삭제 완료 토스트 (전체) | ❌ 페이지 리다이렉트 방식 |
