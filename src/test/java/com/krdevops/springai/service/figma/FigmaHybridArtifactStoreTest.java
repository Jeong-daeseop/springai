package com.krdevops.springai.service.figma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidate;
import com.krdevops.springai.model.figma.hybrid.HybridConversionReport;
import com.krdevops.springai.model.figma.hybrid.HybridReferenceSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FigmaHybridArtifactStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void 기존captureArtifact디렉터리에후보를원자적으로저장하고읽는다() throws Exception {
        String artifactId = UUID.randomUUID().toString();
        Files.createDirectory(temporaryDirectory.resolve(artifactId));
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(temporaryDirectory);
        FigmaHybridArtifactStore store =
                new FigmaHybridArtifactStore(properties, new ObjectMapper().findAndRegisterModules());
        FigmaHybridCandidate candidate = new FigmaHybridCandidate(
                artifactId,
                new HybridReferenceSnapshot(
                        artifactId, "REFERENCE_SNAPSHOT", "source.figpack", "document.json",
                        "preview.png", "doc", "hash", "v1", null, null, null,
                        "DESKTOP", 1440, 900, List.of(), List.of(), List.of()),
                FigmaBuilderTestFixtures.userManagementSpec()
                        .withStatus(ScreenSpecStatus.REVIEW_REQUIRED),
                List.of(),
                new HybridConversionReport(
                        artifactId, "hash", 1, 1, 1, 1, 2, 5, 7,
                        List.of(), List.of(), "DESKTOP", "DESKTOP", true,
                        null, null, null),
                true, null);

        store.saveCandidate(candidate);

        assertThat(store.readCandidate(artifactId)).isEqualTo(candidate);
        assertThat(Files.isRegularFile(
                temporaryDirectory.resolve(artifactId).resolve("hybrid-candidate.json"))).isTrue();
    }

    @Test
    void captureArtifact가없는경로에는Hybrid파일을쓰지않는다() {
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(temporaryDirectory);
        FigmaHybridArtifactStore store =
                new FigmaHybridArtifactStore(properties, new ObjectMapper().findAndRegisterModules());
        String artifactId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> store.readCandidate(artifactId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효한 capture artifact");
    }
}
