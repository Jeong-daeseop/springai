package com.krdevops.springai.model.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.role.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageSpecTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void nullSelectionSourceDefaultsToDefault() {
        PageSpec page = new PageSpec("list", "CRUD_LIST", List.of(), List.of(), null);

        assertThat(page.selectionSource()).isEqualTo(FieldSelectionSource.DEFAULT);
    }

    @Test
    void legacyStringActionIsReadAndWrittenAsStructuredAction() throws Exception {
        PageSpec page = objectMapper.readValue("""
                {"id":"list","template":"CRUD_LIST","fields":[],"actions":["SEARCH"]}
                """, PageSpec.class);

        assertThat(page.actions()).singleElement().satisfies(action -> {
            assertThat(action.command()).isEqualTo("SEARCH");
            assertThat(action.role()).isEqualTo(SemanticRole.ACTION_PRIMARY);
        });
        assertThat(objectMapper.writeValueAsString(page.actions().get(0)))
                .contains("\"command\":\"SEARCH\"", "\"role\":\"action.primary\"");
    }

    @Test
    void structuredActionRoundTrips() throws Exception {
        PageSpec source = new PageSpec("detail", "CRUD_DETAIL", List.of(), PageSpec.migrateActions("DELETE"));

        PageSpec restored = objectMapper.readValue(objectMapper.writeValueAsString(source), PageSpec.class);

        assertThat(restored).isEqualTo(source);
    }
}
