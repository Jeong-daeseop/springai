package com.krdevops.springai.model.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignSourceMetadataTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void webCaptureMetadataRoundTripsPolymorphically() throws Exception {
        DesignAnalysisResult source = DesignAnalysisResult.webCapture("analysis", "hash",
                new WebCaptureDesignSourceMetadata("artifact", "document", "content",
                        "rendered-design-document-v1"), "mapper-v1", "crud",
                UiDesignSpec.empty("CRUD_LIST"), List.of(), LocalDateTime.now());

        DesignAnalysisResult restored = mapper.readValue(mapper.writeValueAsBytes(source),
                DesignAnalysisResult.class);

        assertThat(restored.sourceMetadata()).isInstanceOf(WebCaptureDesignSourceMetadata.class);
        assertThat(restored.sourceType()).isEqualTo(DesignSourceType.WEB_CAPTURE);
    }

    @Test
    void rejectsMismatchedTypeAndSubtype() {
        assertThatThrownBy(() -> new DesignAnalysisResult("id", "hash", null, null,
                DesignSourceType.FILE, null, "v1", UiDesignSpec.SCHEMA_VERSION, "crud",
                "web-capture", "deterministic-mapper", "v1", List.of(),
                UiDesignSpec.empty("CRUD_LIST"), List.of(), LocalDateTime.now(),
                new WebCaptureDesignSourceMetadata("a", "d", "c", "v1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
