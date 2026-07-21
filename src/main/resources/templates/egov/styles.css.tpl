@import "https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard-gov-dynamic-subset.min.css";
@import "./_ds_bundle.css";

/* 공통 라벨 폰트 크기 토큰 재정의.
   _ds_bundle.css의 --krds-pc-font-size-label-*(기본 xsmall 1.3rem=20.8px / small 1.5rem=24px /
   medium 1.7rem=27.2px / large 1.9rem=30.4px)는 버튼(.krds-btn), 입력창(.krds-input),
   셀렉트(.krds-form-select), 태그/칩 등 거의 모든 컴포넌트가 기본값으로 참조하는 루트 토큰인데,
   값 자체가 비정상적으로 크다(html 루트 폰트는 16px 정상이라 rem 스케일 문제가 아니다).
   컴포넌트별 선택자로 맞서면 번들 내부 규칙보다 특이도가 낮아 무시되므로,
   테이블 폰트 토큰과 동일하게 토큰 값 자체를 여기서 재정의해 전체 화면(버튼/입력창/레이아웃
   글자 크기)이 조화롭게 보이도록 맞춘다. */
:root {
    --krds-pc-font-size-label-xsmall: 12px;
    --krds-pc-font-size-label-small: 13px;
    --krds-pc-font-size-label-medium: 14px;
    --krds-pc-font-size-label-large: 16px;
    --krds-mobile-font-size-label-xsmall: 12px;
    --krds-mobile-font-size-label-small: 13px;
    --krds-mobile-font-size-label-medium: 14px;
    --krds-mobile-font-size-label-large: 16px;
}

/* === egov-board-crud:start === */
/* 화면 스코프 → 구조 클래스 → 요소 modifier 순으로 책임을 좁힌다. */
:root {
    --egov-screen-control-height: 48px;
    --egov-screen-control-padding-x: 16px;
    --egov-screen-textarea-min-height: 220px;
    --egov-screen-font-size: 16px;
    --egov-screen-link-font-size: 13px;
}
.egov-crud-page { font-size: var(--egov-screen-font-size); line-height: 1.5; }
.egov-crud-page .egov-search-form { width: 100%; }
.egov-crud-page .egov-control {
    min-height: var(--egov-screen-control-height);
    padding-inline: var(--egov-screen-control-padding-x);
    font-size: var(--egov-screen-font-size);
}
.egov-crud-page textarea.egov-control.egov-textarea {
    min-height: var(--egov-screen-textarea-min-height);
    padding-block: 12px;
}
.egov-crud-page .egov-btn { min-height: var(--egov-screen-control-height); font-size: 15px; }
.egov-crud-page .egov-btn.small { min-height: 40px; }
.egov-crud-page .egov-list-table,
.egov-crud-page .egov-form-table { width: 100%; font-size: 15px; }
.egov-crud-page .egov-pagination,
.egov-crud-page .egov-pagination ol { display: flex; align-items: center; justify-content: center; }
.egov-crud-page .egov-pagination a,
.egov-crud-page .egov-pagination li { min-width: 40px; min-height: 40px; line-height: 40px; text-align: center; }
.krds-btn {
    --krds-button--size-height-medium: 38px;
    --krds-button--size-height-small: 34px;
    --krds-button--size-height-large: 38px;
    --krds-button--padding-x-medium: 16px;
}
.krds-input {
    --krds-input--size-height-medium: 38px;
    --krds-input--size-height-large: 38px;
    --krds-input--size-height-small: 34px;
    --krds-input--textarea-size-height: var(--egov-screen-textarea-min-height);
}
.krds-form-select {
    --krds-form-select--size-height-medium: 38px;
    --krds-form-select--size-height-large: 38px;
    --krds-form-select--size-height-small: 34px;
}
.krds-table-wrap .tbl.data {
    --krds-table--data-tbody-padding: 10px;
    --krds-table--data-tbody-padding-sides: 16px;
    --krds-table--data-thead-th-padding: 10px;
    --krds-table--data-thead-th-padding-sides: 16px;
}
.krds-pagination { display: flex; align-items: center; justify-content: center; gap: 4px; }
.krds-pagination ol { display: flex; align-items: center; gap: 4px; list-style: none; margin: 0; padding: 0; }
.krds-pagination li a,
.krds-pagination > a[class^="btn-"] {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 32px;
    height: 32px;
    padding: 0 6px;
    border-radius: 4px;
    font-size: 14px;
}
.egov-crud-page .egov-primary-text,
.egov-crud-page .egov-detail-link,
.egov-crud-page .egov-file-detail-link,
.egov-crud-page .egov-file-empty,
.egov-crud-page .egov-post-nav-link { font-size: var(--egov-screen-link-font-size); }
/* === egov-board-crud:end === */

/* === egov-table-density:start === */
.egov-density-compact .tbl.data th,
.egov-density-compact .tbl.data td { padding-block: 6px; }
.egov-density-comfortable .tbl.data th,
.egov-density-comfortable .tbl.data td { padding-block: 16px; }
/* === egov-table-density:end === */

/* === egov-form-column-layout:start === */
.egov-form-table.egov-layout-two-col th { width: 90px; }
.egov-form-table.egov-layout-two-col td { width: auto; }
.form-row-two-col { display: flex; gap: 16px; }
.form-row-two-col .form-group { flex: 1; min-width: 0; }
/* === egov-form-column-layout:end === */

/* tbl col: 라벨-값 정의 테이블 (상세/등록/수정 화면) */
.krds-table-wrap .tbl.col { border-top: 2px solid #1e2124; width: 100%; }
.krds-table-wrap .tbl.col th,
.krds-table-wrap .tbl.col td { padding: 12px 16px; border-bottom: 1px solid #e8eaec; vertical-align: middle; font-size: 14px; }
.krds-table-wrap .tbl.col th { background: #f4f5f6; border-right: 1px solid #e8eaec; font-weight: 700; color: #1e2124; width: 180px; min-width: 120px; white-space: nowrap; }
.krds-table-wrap .tbl.col td { color: #464c53; }

/* tbl data: 목록 테이블 (목록 화면).
   _ds_bundle.css가 .tbl.data thead th / .tbl.data tbody th,.krds-table-wrap .tbl.data tbody td
   규칙으로 font-size를 --krds-table--data-thead-pc-font-size(기본 1.5rem=24px) /
   --krds-table--data-tbody-pc-font-size(기본 1.7rem=27.2px) 토큰에 연결해두는데, 이 선택자가
   .krds-table-wrap .tbl.data th/td 보다 특이도가 높아(thead/tbody 타입 셀렉터 포함) font-size를
   직접 지정해도 무시된다. 그래서 선택자로 맞서지 않고 토큰 값 자체를 여기서 재정의한다 —
   이렇게 하면 번들의 모든 .tbl.data 규칙이 자동으로 이 값을 참조하게 된다. */
.krds-table-wrap .tbl.data {
    --krds-table--data-thead-pc-font-size: 14px;
    --krds-table--data-thead-mobile-font-size: 14px;
    --krds-table--data-tbody-pc-font-size: 13px;
    --krds-table--data-tbody-mobile-font-size: 13px;
}

/* 버튼/입력창/셀렉트 높이 토큰 재정의 (실사고, 2026-07-13 확인).
   위 폰트 크기 토큰만 줄이면 "글자는 작은데 박스는 그대로 큰" 불균형이 남는다 — 폰트와
   별개로 _ds_bundle.css의 --krds-button/input/form-select--size-height-*(medium 기준
   --krds-size-height-7 = 4.8rem = 76.8px, large는 --krds-size-height-8 = 5.6rem = 89.6px)가
   업무화면 컨트롤(36~40px) 대비 2배 이상 크다. 이 토큰은 .krds-btn/.krds-input/
   .krds-form-select 컴포넌트 선택자 자기 자신 위에서 재선언되므로 :root에 재정의해도
   조용히 무시된다 — 반드시 같은 컴포넌트 선택자에 재정의해야 특이도가 같아 소스 순서상
   이 파일이 이긴다. */
.krds-btn {
    --krds-button--size-height-medium: 38px;
    --krds-button--size-height-small: 34px;
    --krds-button--size-height-large: 38px;
    --krds-button--padding-x-medium: 16px;
}
.krds-input {
    --krds-input--size-height-medium: 38px;
    --krds-input--size-height-large: 38px;
    --krds-input--size-height-small: 34px;
    --krds-input--textarea-size-height: var(--egov-screen-textarea-min-height);
}
.krds-form-select {
    --krds-form-select--size-height-medium: 38px;
    --krds-form-select--size-height-large: 38px;
    --krds-form-select--size-height-small: 34px;
}
.krds-table-wrap .tbl.data {
    --krds-table--data-tbody-padding: 10px;
    --krds-table--data-tbody-padding-sides: 16px;
    --krds-table--data-thead-th-padding: 10px;
    --krds-table--data-thead-th-padding-sides: 16px;
}

/* 페이지네이션 (실사고, 2026-07-13 확인).
   _ds_bundle.css의 실제 pagination 컴포넌트는 .page-navi/.page-link 클래스를 전제로
   --krds-pagination--size-height 토큰을 적용하는데, 이 프로젝트의 실제 생성 화면
   (예: EgovNoticeList.html)은 레거시 eGovFrame JSP <ui:pagination> 태그립 관례를 그대로
   따라 <nav class="krds-pagination"><a class="btn-first|btn-prev|btn-next|btn-last">,
   <ol><li><a>페이지번호</a></li></ol> 구조로 생성된다. btn-first/btn-prev/btn-next/btn-last와
   페이지번호 <a>는 _ds_bundle.css 어디에도 대응 규칙이 없어 스타일을 전혀 못 받고 브라우저
   기본값(16px)으로 렌더링된다 — "페이지 번호 글자 크기가 크다"는 증상의 직접 원인. 실제
   생성되는 마크업 기준으로 별도 규칙을 추가한다.

   추가 실사고(2026-07-14 확인): nav.krds-pagination 자체에 display:flex/align-items가
   없으면, 직계 자식인 btn-first/btn-prev/btn-next/btn-last <a>(높이 32px 고정 → stretch가
   무효화되어 기본값인 flex-start=위쪽 정렬)와 <ol>(높이 auto → nav 높이만큼 stretch된 뒤 그
   안에서 align-items:center로 li를 중앙 정렬)가 서로 다른 기준으로 정렬되어, 페이지 번호
   숫자 배지만 텍스트 링크보다 아래로 처져 보인다. 아래 .krds-pagination(nav 셀렉터 자체)
   규칙이 이 정렬 기준을 통일시켜 배지를 텍스트와 같은 라인으로 끌어올린다. */
.krds-pagination {
    display: flex;
    align-items: center;
    gap: 4px;
}
.krds-pagination ol {
    display: flex;
    align-items: center;
    gap: 4px;
    list-style: none;
    margin: 0;
    padding: 0;
}
.krds-pagination li a,
.krds-pagination > a[class^="btn-"] {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 32px;
    height: 32px;
    padding: 0 6px;
    border-radius: 4px;
    font-size: 14px;
    color: #464c53;
    text-decoration: none;
}
.krds-pagination li.on a {
    background: #256ef4;
    color: #fff;
    font-weight: 700;
}
.krds-pagination li a:hover,
.krds-pagination > a[class^="btn-"]:hover {
    background: #f4f5f6;
}

/* eGovFrame generated Thymeleaf screens */
* { box-sizing: border-box; }
html { font-size: 16px; }
body {
    margin: 0;
    font-family: "Pretendard GOV", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    color: #1e2124;
    background: #fff;
    line-height: 1.5;
}
a { color: inherit; text-decoration: none; }
button, input, select, textarea { font: inherit; }
input:focus, select:focus, textarea:focus, button:focus-visible {
    outline: 2px solid #256ef4;
    outline-offset: -1px;
}
input::placeholder, textarea::placeholder { color: #8a949e; }

.egov-header-top {
    background: #f4f5f6;
    border-bottom: 1px solid #e6e8ea;
    color: #464c53;
    font-size: 13px;
}
.egov-header-top-inner {
    max-width: 1200px;
    margin: 0 auto;
    padding: 8px 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}
.egov-header {
    position: relative;
    z-index: 100;
    background: #fff;
    border-bottom: 1px solid #e6e8ea;
}
.egov-header-inner {
    max-width: 1200px;
    margin: 0 auto;
    padding: 22px 24px 18px;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
}
.egov-header-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #083891;
    font-size: 20px;
    font-weight: 800;
    white-space: nowrap;
}
.egov-brand-mark {
    border-radius: 50%;
    background: #083891;
    color: #fff;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-weight: 800;
}
.egov-brand-mark.header { width: 36px; height: 36px; font-size: 14px; }
.egov-brand-mark.footer { width: 28px; height: 28px; font-size: 12px; }
/* GNB 브랜드 로고 이미지(generateThymeleafLayout이 복사하는 egov-logo.png).
   원본 172x40 비율을 유지한 채 헤더 높이(36px 아이콘 기준)에 맞춘다. */
.egov-brand-logo { display: block; height: 36px; width: auto; }

.egov-main-menu { position: relative; width: 100%; }
.egov-main-menu-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: 8px 12px;
}
.egov-mega-item { position: static; }
.egov-main-menu-link {
    display: flex;
    align-items: center;
    min-height: 40px;
    padding: 8px 12px;
    border-radius: 4px;
    color: #1e2124;
    font-size: 17px;
    font-weight: 700;
    line-height: 1.35;
    white-space: normal;
}
.egov-main-menu-link:hover { background: #f4f5f6; }
.egov-main-menu-link.gnb-active {
    color: #083891;
    font-weight: 800;
    background: #eef3fe;
    box-shadow: inset 0 -3px 0 #083891;
}
.egov-mega-panel {
    display: none;
    position: absolute;
    top: 100%;
    left: 50%;
    width: 100vw;
    transform: translateX(-50%);
    z-index: 120;
    padding: 0;
    border-top: 1px solid #e6e8ea;
    border-bottom: 1px solid #e6e8ea;
    background: #fff;
    box-shadow: 0 16px 28px rgba(0, 0, 0, .08);
}
.egov-mega-item:hover > .egov-mega-panel,
.egov-mega-item:focus-within > .egov-mega-panel { display: block; }
.egov-dropdown-inner {
    max-width: 1200px;
    min-height: 180px;
    margin: 0 auto;
    display: grid;
    grid-template-columns: 280px 1fr;
}
.egov-dropdown-side {
    list-style: none;
    margin: 0;
    padding: 16px 0;
    background: #f4f5f6;
    border-left: 1px solid #e6e8ea;
    border-right: 1px solid #e6e8ea;
}
.egov-dropdown-side-link {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 48px;
    padding: 0 20px;
    color: #1e2124;
    font-size: 16px;
    font-weight: 700;
    line-height: 1.4;
}
.egov-dropdown-side-link::after {
    content: ">";
    color: #8a949e;
    font-weight: 800;
}
.egov-dropdown-side-link.is-active {
    color: #083891;
    background: #fff;
    border-left: 4px solid #256ef4;
    padding-left: 16px;
}
.egov-dropdown-content { padding: 28px 32px 36px; background: #fff; }
.egov-dropdown-group { display: none; }
.egov-dropdown-group.is-active { display: block; }
.egov-dropdown-title {
    margin: 0 0 22px;
    padding-bottom: 18px;
    border-bottom: 1px solid #e6e8ea;
    color: #083891;
    font-size: 16px;
    font-weight: 800;
    line-height: 1.35;
}
.egov-dropdown-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 18px 36px;
}
.egov-dropdown-link {
    display: block;
    min-height: 32px;
    padding: 2px 0;
    color: #1e2124;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.45;
}
.egov-dropdown-link::before {
    content: "-";
    margin-right: 8px;
    color: #256ef4;
    font-weight: 800;
}
.egov-dropdown-link:hover,
.egov-dropdown-link:focus {
    color: #083891;
    text-decoration: underline;
    text-underline-offset: 3px;
}

.egov-breadcrumb-band {
    border-top: 1px solid #e6e8ea;
    border-bottom: 1px solid #e6e8ea;
    background: #fff;
}
.egov-breadcrumb {
    max-width: 1200px;
    min-height: 56px;
    margin: 0 auto;
    padding: 0 24px;
    display: flex;
    align-items: center;
    gap: 10px;
    color: #58616a;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.45;
}
.egov-breadcrumb a { color: #58616a; }
.egov-breadcrumb a:hover {
    color: #083891;
    text-decoration: underline;
    text-underline-offset: 3px;
}
.egov-breadcrumb-home { color: #8a949e; font-size: 18px; line-height: 1; }
.egov-breadcrumb-separator { color: #b1b8be; font-weight: 700; }
.egov-breadcrumb-current { color: #1e2124; font-weight: 800; }

.egov-layout-shell {
    max-width: 1200px;
    margin: 0 auto;
    padding: 32px 24px 60px;
    display: flex;
    align-items: flex-start;
    gap: 32px;
}
.egov-layout-content { flex: 1; min-width: 0; }
.egov-layout-shell.egov-main-shell,
.egov-layout-shell:has(.egov-main-dashboard) { display: block; max-width: 1200px; }
.egov-layout-shell:has(.egov-main-dashboard) .egov-lnb { display: none; }
.egov-main-content { width: 100%; }
.egov-main-dashboard { padding: 8px 0 24px; }
.egov-main-hero {
    padding: 32px 0 28px;
    border-bottom: 2px solid #1e2124;
}
.egov-main-kicker {
    margin: 0 0 8px;
    color: #256ef4;
    font-size: 15px;
    font-weight: 800;
}
.egov-main-title {
    margin: 0 0 14px;
    color: #1e2124;
    font-size: 32px;
    font-weight: 800;
    line-height: 1.25;
}
.egov-main-description {
    max-width: 760px;
    margin: 0;
    color: #464c53;
    font-size: 17px;
    line-height: 1.7;
}
.egov-main-metrics {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
    margin: 24px 0;
}
.egov-main-metric,
.egov-main-card {
    border: 1px solid #d8dde3;
    border-radius: 8px;
    background: #fff;
}
.egov-main-metric { padding: 18px 20px; }
.egov-main-metric-label {
    display: block;
    margin-bottom: 8px;
    color: #58616a;
    font-size: 13px;
    font-weight: 700;
}
.egov-main-metric-value {
    display: block;
    color: #1e2124;
    font-size: 20px;
    font-weight: 800;
    line-height: 1.3;
}
.egov-main-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
}
.egov-main-card { padding: 22px; }
.egov-main-card h2 {
    margin: 0 0 10px;
    color: #1e2124;
    font-size: 18px;
    font-weight: 800;
}
.egov-main-card p {
    margin: 0;
    color: #464c53;
    font-size: 15px;
    line-height: 1.65;
}
.egov-standalone-shell {
    max-width: 1200px;
    margin: 0 auto;
    padding: 32px 24px 60px;
}
.egov-lnb { width: 200px; flex: none; }
.egov-lnb-title {
    display: block;
    padding-bottom: 12px;
    margin-bottom: 8px;
    border-bottom: 2px solid #083891;
    color: #083891;
    font-size: 16px;
    font-weight: 800;
}
.egov-lnb-list,
.egov-lnb-sublist { list-style: none; margin: 0; padding: 0; }
.egov-lnb-sublist { padding: 2px 0 6px 14px; }
.lnb-link {
    display: block;
    padding: 7px 10px;
    border-radius: 2px;
    color: #58616a;
    font-size: 15px;
    line-height: 1.45;
}
.lnb-link.lnb-active { color: #256ef4; font-weight: 700; background: #eef3fe; }
.lnb-link:hover { background: #f4f5f6; }
.lnb-link.lnb-group-label { color: #1e2124; font-weight: 700; cursor: default; }
.lnb-link.lnb-group-label:hover { background: none; }
.lnb-link.has-children {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.lnb-sublink { display: block; font-size: 13px; }
.lnb-chevron { color: #8a949e; font-weight: 700; }
.egov-lnb-empty { padding: 8px 12px; color: #8a949e; font-size: 14px; }

.egov-page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding-bottom: 16px;
    margin-bottom: 20px;
    border-bottom: 2px solid #1e2124;
}
.egov-page-title { margin: 0; font-size: 28px; font-weight: 800; }
.egov-toast {
    position: fixed;
    bottom: 32px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 20px;
    border-radius: 8px;
    background: #1e2124;
    color: #fff;
    font-size: 14px;
    font-weight: 600;
    z-index: 9999;
    box-shadow: 0 4px 16px rgba(0, 0, 0, .18);
    white-space: nowrap;
}
.egov-toast-icon { color: #5cb85c; }
.egov-search-panel {
    margin-bottom: 16px;
    padding: 16px 20px;
    border: 1px solid #e8eaec;
    border-radius: 6px;
    background: #f6f7f8;
}
.egov-search-row { display: flex; align-items: center; gap: 8px; }
.egov-search-condition { min-width: 120px; }
.egov-inline-action {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    white-space: nowrap;
}
.egov-list-summary {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
}
.egov-page-unit-label { margin-left: auto; color: #58616a; font-size: 13px; }
.egov-page-unit { width: auto; min-width: 84px; }
.egov-muted-text { color: #6d7882; font-size: 13px; }
.egov-subtle-text { color: #8a949e; font-size: 13px; }
.egov-primary-text { color: #083891; font-size: var(--egov-screen-link-font-size); }
.egov-col-check { width: 40px; }
.egov-col-no { width: 64px; }
.egov-col-file { width: 70px; }
.egov-col-actions { width: 120px; }
.egov-col-narrow { width: 8%; }
.egov-col-action-percent { width: 10%; }
.egov-detail-label-col { width: 25%; }
.egov-jsp-inline-form { display: inline; }
.egov-row-link { cursor: pointer; }
.egov-row-notice { background: #eef3fe; }
.egov-table-no { color: #8a949e; font-size: 13px; text-align: center; }
.egov-detail-link { color: #1e2124; font-size: var(--egov-screen-link-font-size); font-weight: 500; }
.egov-detail-link.strong { color: #256ef4; font-family: monospace; font-weight: 700; }
.egov-table-actions { text-align: center; }
.egov-notice-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 34px;
    height: 22px;
    padding: 0 7px;
    border-radius: 3px;
    background: #083891;
    color: #fff;
    font-size: 11px;
    font-weight: 700;
}
.egov-status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 34px;
    height: 22px;
    padding: 0 7px;
    border-radius: 3px;
    background: #eef3fe;
    color: #083891;
    font-size: 12px;
    font-weight: 700;
}
.egov-status-badge.positive { background: #e8f5e9; color: #2e7d32; }
.egov-status-badge.negative { background: #fce4ec; color: #c62828; }
.egov-file-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 4px;
    color: #256ef4;
}
.egov-file-detail-link {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #256ef4;
    font-size: var(--egov-screen-link-font-size);
    font-weight: 700;
}
.egov-file-id { color: #8a949e; font-size: 13px; font-weight: 500; }
.egov-file-empty { color: #cdd1d5; font-size: var(--egov-screen-link-font-size); }
.egov-sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
}
.egov-empty-cell {
    padding: 52px 0;
    color: #8a949e;
    text-align: center;
}
.egov-empty-cell.compact { padding: 32px 0; }
.egov-empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
}
.egov-form-required-guide {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 4px;
    margin-bottom: 12px;
    color: #6d7882;
    font-size: 13px;
}
.egov-char-count { margin: 6px 0 0; color: #6d7882; font-size: 12px; text-align: right; }
.egov-radio-group { display: flex; align-items: center; gap: 18px; }
.egov-radio-group label { display: inline-flex; align-items: center; gap: 6px; cursor: pointer; font-size: 14px; }
.egov-required-mark {
    margin-left: 2px;
    color: #d9363e;
    font-weight: 700;
}
.egov-field-error {
    display: flex;
    align-items: center;
    gap: 4px;
    margin: 5px 0 0;
    color: #d9363e;
    font-size: 12px;
}
.egov-form-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: 28px;
}
.egov-button-group { display: flex; gap: 8px; }
.egov-section { margin-bottom: 32px; }
.egov-section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
}
.egov-section-title {
    display: flex;
    align-items: center;
    gap: 7px;
    margin: 0 0 12px;
    color: #083891;
    font-size: 16px;
    font-weight: 800;
}
.egov-section-header .egov-section-title { margin-bottom: 0; }
.egov-section-count { color: #8a949e; font-size: 13px; font-weight: 400; }
.egov-readonly-value { color: #1e2124; font-weight: 500; }
.egov-textarea {
    width: 100%;
    min-height: 220px;
    resize: vertical;
    line-height: 1.7;
}
.egov-attachment-box {
    margin-top: 24px;
    border: 1px solid #e8eaec;
    border-radius: 6px;
    overflow: hidden;
}
.egov-definition-grid {
    display: grid;
    grid-template-columns: 140px 1fr;
}
.egov-definition-label {
    padding: 14px 16px;
    background: #f6f7f8;
    color: #464c53;
    font-weight: 700;
}
.egov-definition-value { padding: 14px 16px; }
.egov-post-nav {
    margin-top: 24px;
    border: 1px solid #e8eaec;
    border-radius: 8px;
    overflow: hidden;
}
.egov-post-nav-list { margin: 0; }
.egov-post-nav-row {
    display: flex;
    align-items: stretch;
}
.egov-post-nav-row + .egov-post-nav-row { border-top: 1px solid #e8eaec; }
.egov-post-nav-term {
    min-width: 80px;
    padding: 14px 16px;
    background: #f6f7f8;
    color: #464c53;
    display: flex;
    align-items: center;
    flex: none;
    font-size: 13px;
    font-weight: 700;
}
.egov-post-nav-desc {
    flex: 1;
    margin: 0;
    padding: 14px 16px;
    display: flex;
    align-items: center;
}
.egov-post-nav-link { color: #256ef4; font-size: var(--egov-screen-link-font-size); font-weight: 500; }
.egov-post-nav-empty { color: #8a949e; font-size: 13px; }
.egov-modal {
    border: 0;
    border-radius: 12px;
    padding: 32px;
    min-width: 320px;
    max-width: 400px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, .18);
}
.egov-modal-title {
    margin: 0 0 10px;
    color: #1e2124;
    font-size: 18px;
    font-weight: 800;
}
.egov-modal-desc {
    margin: 0 0 24px;
    color: #58616a;
    font-size: 14px;
    line-height: 1.6;
}
.egov-modal-actions { display: flex; justify-content: flex-end; gap: 8px; }
.egov-hidden { display: none; }

.egov-footer { background: #f4f5f6; border-top: 1px solid #e8eaec; }
.egov-footer-inner {
    max-width: 1200px;
    margin: 0 auto;
    padding: 28px 24px 24px;
    color: #464c53;
    font-size: 13px;
}
.egov-footer-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
    color: #083891;
    font-size: 16px;
    font-weight: 800;
}
.egov-footer-text { margin: 0 0 4px; }
.egov-footer-text.last { margin: 0; }
.egov-footer-bottom { border-top: 1px solid #e0e2e4; }
.egov-footer-bottom-inner {
    max-width: 1200px;
    height: 48px;
    margin: 0 auto;
    padding: 0 24px;
    display: flex;
    align-items: center;
    gap: 20px;
    font-size: 13px;
}
.egov-footer-policy { color: #256ef4; font-weight: 700; }
.egov-footer-copy { margin-left: auto; color: #8a949e; }

@media (max-width: 900px) {
    .egov-layout-shell { display: block; padding: 24px 16px 48px; }
    .egov-lnb { width: auto; margin-bottom: 24px; }
    .egov-main-title { font-size: 28px; }
    .egov-main-metrics,
    .egov-main-grid { grid-template-columns: 1fr; }
    .egov-header-inner { flex-wrap: wrap; }
    .egov-header-inner .egov-main-menu { width: 100%; }
    .egov-mega-panel { position: static; width: auto; transform: none; margin-top: 8px; box-shadow: none; }
    .egov-dropdown-inner { display: block; min-height: 0; }
    .egov-dropdown-side { border-left: 0; border-right: 0; }
    .egov-dropdown-content { padding: 20px 16px 24px; }
    .egov-dropdown-list { grid-template-columns: 1fr; gap: 8px; }
    .egov-search-row { flex-wrap: wrap; }
}
