package com.krdevops.springai.model.design;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageSpecTest {

    @Test
    void nullSelectionSourceDefaultsToDefault() {
        PageSpec page = new PageSpec("list", "CRUD_LIST", List.of(), List.of(), null);

        assertThat(page.selectionSource()).isEqualTo(FieldSelectionSource.DEFAULT);
    }
}
