package com.krdevops.springai.service;

/**
 * 생성 프로젝트의 등록/수정 폼 2단 배치 CSS와 런타임 보강 로직이 공유하는 marker 계약.
 */
public final class FormColumnLayoutCssContract {

    public static final String START_MARKER = "/* === egov-form-column-layout:start === */";
    public static final String END_MARKER = "/* === egov-form-column-layout:end === */";
    public static final String CSS = """

/* === egov-form-column-layout:start === */
.egov-form-table.egov-layout-two-col th { width: 90px; }
.egov-form-table.egov-layout-two-col td { width: auto; }
.form-row-two-col { display: flex; gap: 16px; }
.form-row-two-col .form-group { flex: 1; min-width: 0; }
/* === egov-form-column-layout:end === */
""";

    private FormColumnLayoutCssContract() {
    }
}
