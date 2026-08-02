package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportMetadata;
import com.krdevops.springai.model.figma.DesignSystemProfileSnapshot;
import com.krdevops.springai.model.figma.ComponentRegistrySnapshot;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignArtifactFigmaExportTest {

    @TempDir
    Path root;

    @Test
    void savesImmutableVersionedFigmaArtifactsAndAllowsIdempotentRetry() {
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(root);
        DesignArtifactService service = new DesignArtifactService(
                properties, new ObjectMapper().findAndRegisterModules());
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 27, 12, 0);

        var first = service.saveFigmaExport(
                spec("사용자 목록"), FigmaExportResult.Status.SUCCESS, List.of(), generatedAt);
        var retried = service.saveFigmaExport(
                spec("사용자 목록"), FigmaExportResult.Status.SUCCESS, List.of(), generatedAt);

        assertThat(retried).isEqualTo(first);
        Path artifact = root.resolve(first.relativePath());
        assertThat(artifact.resolve("figma-screen-spec.json")).isRegularFile();
        assertThat(artifact.resolve("figma-generation-report.json")).isRegularFile();
        assertThat(artifact.resolve("metadata.json")).isRegularFile();

        assertThatThrownBy(() -> service.saveFigmaExport(
                spec("변경된 화면"), FigmaExportResult.Status.SUCCESS, List.of(), generatedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_ARTIFACT_VERSION_CONFLICT");
    }

    @Test
    void savesImmutableBundleArtifactWithContentHash() {
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(root);
        DesignArtifactService service = new DesignArtifactService(
                properties, new ObjectMapper().findAndRegisterModules());
        FigmaExportBundle bundle = bundle("사용자 목록");

        var first = service.saveFigmaExportBundle(bundle);
        var retried = service.saveFigmaExportBundle(bundle);

        assertThat(retried).isEqualTo(first);
        assertThat(first.contentHash()).matches("^[a-f0-9]{64}$");
        Path artifact = root.resolve(first.relativePath());
        assertThat(artifact.resolve("figma-export-bundle.json")).isRegularFile();
        assertThat(artifact.resolve("figma-screen-spec.json")).isRegularFile();
        assertThat(artifact.resolve("metadata.json")).isRegularFile();

        assertThatThrownBy(() -> service.saveFigmaExportBundle(bundle("변경된 화면")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_BUNDLE_ARTIFACT_VERSION_CONFLICT");
    }

    private FigmaExportBundle bundle(String name) {
        LocalDateTime capturedAt = LocalDateTime.of(2026, 8, 2, 12, 0);
        DesignSystemProfile profile = new DesignSystemProfile(
                "ftc-krds", "FTC KRDS", "1.0.0", "registry-1", null,
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
        ComponentRegistry registry = new ComponentRegistry(
                "ftc-krds", "1.0.0", "registry-1", null, Map.of());
        return new FigmaExportBundle(
                spec(name),
                new DesignSystemProfileSnapshot(profile, capturedAt),
                new ComponentRegistrySnapshot(registry, capturedAt),
                new FigmaExportMetadata(capturedAt, FigmaScreenSpec.SCHEMA_VERSION,
                        1, "1.0.0", "registry-1"));
    }

    private FigmaScreenSpec spec(String name) {
        return new FigmaScreenSpec(
                "user-list", 1, "spec-user", 1,
                FigmaScreenType.LIST, LayoutPattern.STANDARD, name, null,
                "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("ftc-krds", "1.0.0", "registry-1"),
                new FigmaNodeSpec("user-list", FigmaNodeSpec.NodeType.PAGE,
                        "egov.listPage", Map.of(), List.of()),
                List.of());
    }
}
