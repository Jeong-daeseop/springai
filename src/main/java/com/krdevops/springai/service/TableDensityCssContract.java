package com.krdevops.springai.service;

/**
 * 생성 프로젝트의 표 밀도 CSS와 런타임 보강 로직이 공유하는 marker 계약.
 */
public final class TableDensityCssContract {

    public static final String START_MARKER = "/* === egov-table-density:start === */";
    public static final String END_MARKER = "/* === egov-table-density:end === */";
    public static final String CSS = """

/* === egov-table-density:start === */
.egov-density-compact .tbl.data th,
.egov-density-compact .tbl.data td { padding-block: 6px; }
.egov-density-comfortable .tbl.data th,
.egov-density-comfortable .tbl.data td { padding-block: 16px; }
/* === egov-table-density:end === */
""";

    private TableDensityCssContract() {
    }
}
