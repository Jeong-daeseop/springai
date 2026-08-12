package com.krdevops.springai.model.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.role.SemanticRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticRoleSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void semanticCodeRoundTripsWithoutEnumNameLeak() throws Exception {
        String json = objectMapper.writeValueAsString(SemanticRole.PAGE_HEADER);
        assertThat(json).isEqualTo("\"page.header\"");
        assertThat(objectMapper.readValue(json, SemanticRole.class)).isEqualTo(SemanticRole.PAGE_HEADER);
    }

    @Test
    void unknownRoleIsRejected() {
        assertThatThrownBy(() -> objectMapper.readValue("\"blue.button\"", SemanticRole.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
