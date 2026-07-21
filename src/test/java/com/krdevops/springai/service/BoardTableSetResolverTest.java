package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardTableSetResolverTest {

    private final CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
    private final BoardTableSetResolver resolver = new BoardTableSetResolver(schema);

    @Test
    void blankTablesPreferCompleteLettnFamily() {
        when(schema.tableExists("let", "LETTNBBS")).thenReturn(true);
        when(schema.tableExists("let", "LETTNBBSMASTER")).thenReturn(true);
        when(schema.tableExists("let", "LETTNBBSUSE")).thenReturn(true);
        when(schema.tableExists("let", "LETTNFILE")).thenReturn(true);
        when(schema.tableExists("let", "LETTNFILEDETAIL")).thenReturn(true);

        var result = resolver.resolve("let", null, null, null, null, null);

        assertThat(result.mainTable()).isEqualTo("LETTNBBS");
        assertThat(result.masterTable()).isEqualTo("LETTNBBSMASTER");
        assertThat(result.useTable()).isEqualTo("LETTNBBSUSE");
        assertThat(result.fileDetailTable()).isEqualTo("LETTNFILEDETAIL");
    }

    @Test
    void explicitTablesAlwaysWin() {
        var result = resolver.resolve("let", "X_BBS", "X_MASTER", "X_USE", "X_FILE", "X_DETAIL");

        assertThat(result.mainTable()).isEqualTo("X_BBS");
        assertThat(result.masterTable()).isEqualTo("X_MASTER");
        assertThat(result.useTable()).isEqualTo("X_USE");
    }

    @Test
    void explicitLettnMainKeepsLettnFamilyForBlankCompanions() {
        when(schema.tableExists("let", "LETTNBBSUSE")).thenReturn(true);

        var result = resolver.resolve("let", "LETTNBBS", null, null, null, null);

        assertThat(result.masterTable()).isEqualTo("LETTNBBSMASTER");
        assertThat(result.useTable()).isEqualTo("LETTNBBSUSE");
    }
}
