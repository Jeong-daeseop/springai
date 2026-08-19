package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.SafeDesignProjection;
import com.krdevops.springai.policy.WebCaptureProjectionPolicy;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** R7-002: 저장소에 보관된 실제 .figpack을 변환 품질 fixture로 사용한다. */
class FigpackOperationalFixtureTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void listFigpackIsMappedAndQualityEvaluated() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/figpack/list.figpack")) {
            assertThat(input).as("운영 figpack fixture").isNotNull();
            try (ZipInputStream zip = new ZipInputStream(input)) {
                RenderedDesignDocument document = null;
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if ("document.json".equals(entry.getName())) {
                        document = objectMapper.readValue(zip, RenderedDesignDocument.class);
                        break;
                    }
                }
                assertThat(document).isNotNull();
                assertThat(document.schemaVersion()).isEqualTo(RenderedDesignDocument.SCHEMA_VERSION);
                SafeDesignProjection projection = new WebCaptureProjectionPolicy().project(document);
                var spec = new RenderedDesignSpecMapper().map(projection, "crud");
                var quality = new FigmaUiDesignSpecQualityEvaluator().evaluate(spec);
                assertThat(spec.archetype()).isNotBlank();
                assertThat(quality.score()).isGreaterThanOrEqualTo(0.6);
            }
        }
    }
}
