package com.krdevops.springai.model.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesignAnalysisResultCompatibilityTest {

    @Test
    void readsLegacyFileAnalysisJsonWithNewDefaults() throws Exception {
        String json = """
                {
                  "analysisId":"legacy-1", "sourceHash":"hash", "sourcePath":"/tmp/ref.png",
                  "provider":"openai", "model":"gpt-4o-mini", "promptVersion":"v1",
                  "pages":[1], "uiSpec":{"archetype":"BOARD_LIST"}, "warnings":[]
                }
                """;

        DesignAnalysisResult result = new ObjectMapper().findAndRegisterModules()
                .readValue(json, DesignAnalysisResult.class);

        assertThat(result.sourceType()).isEqualTo(DesignSourceType.FILE);
        assertThat(result.analysisContractVersion()).isEqualTo("v1");
        assertThat(result.uiSpecSchemaVersion()).isEqualTo(UiDesignSpec.SCHEMA_VERSION);
        assertThat(result.featureType()).isEqualTo("board");
        assertThat(result.figmaSource()).isNull();
    }

    @Test
    void infersMasterDetailFeatureTypeWhenArchetypeIsMasterDetail() throws Exception {
        String json = """
                {
                  "analysisId":"legacy-2", "sourceHash":"hash", "sourcePath":"/tmp/ref.png",
                  "provider":"openai", "model":"gpt-4o-mini", "promptVersion":"v1",
                  "pages":[1], "uiSpec":{"archetype":"MASTER_DETAIL"}, "warnings":[]
                }
                """;

        DesignAnalysisResult result = new ObjectMapper().findAndRegisterModules()
                .readValue(json, DesignAnalysisResult.class);

        assertThat(result.featureType()).isEqualTo("master-detail");
    }

    @Test
    void infersCrudFeatureTypeWhenArchetypeIsNeitherBoardNorMasterDetail() throws Exception {
        String json = """
                {
                  "analysisId":"legacy-3", "sourceHash":"hash", "sourcePath":"/tmp/ref.png",
                  "provider":"openai", "model":"gpt-4o-mini", "promptVersion":"v1",
                  "pages":[1], "uiSpec":{"archetype":"CRUD_LIST"}, "warnings":[]
                }
                """;

        DesignAnalysisResult result = new ObjectMapper().findAndRegisterModules()
                .readValue(json, DesignAnalysisResult.class);

        assertThat(result.featureType()).isEqualTo("crud");
    }
}
