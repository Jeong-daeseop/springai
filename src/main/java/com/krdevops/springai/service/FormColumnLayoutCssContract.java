package com.krdevops.springai.service;

/**
 * 생성 프로젝트의 등록/수정 폼 2단 배치 CSS와 런타임 보강 로직이 공유하는 marker 계약.
 */
public final class FormColumnLayoutCssContract {

    public static final String START_MARKER = "/* === egov-form-column-layout:start === */";
    public static final String END_MARKER = "/* === egov-form-column-layout:end === */";
    public static final String CSS = """

/* === egov-form-column-layout:start === */
.krds-table-wrap .tbl.col.egov-layout-two-col { table-layout: fixed; }
.krds-table-wrap .tbl.col.egov-layout-two-col th { width: 140px; white-space: normal; }
.krds-table-wrap .tbl.col.egov-layout-two-col td { width: auto; }
.krds-table-wrap .tbl.col.egov-layout-two-col th:empty,
.krds-table-wrap .tbl.col.egov-layout-two-col td:empty { border: 0; background: transparent; }
@media (max-width: 768px) {
  .krds-table-wrap .tbl.col.egov-layout-two-col { table-layout: auto; }
  .krds-table-wrap .tbl.col.egov-layout-two-col tr,
  .krds-table-wrap .tbl.col.egov-layout-two-col th,
  .krds-table-wrap .tbl.col.egov-layout-two-col td { display: block; width: auto; }
  .krds-table-wrap .tbl.col.egov-layout-two-col th:empty,
  .krds-table-wrap .tbl.col.egov-layout-two-col td:empty { display: none; }
}
.form-row-two-col { display: flex; gap: 16px; }
.form-row-two-col .form-group { flex: 1; min-width: 0; }
/* === egov-form-column-layout:end === */
""";

    private FormColumnLayoutCssContract() {
    }
}
