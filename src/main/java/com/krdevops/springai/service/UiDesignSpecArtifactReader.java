package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.FigmaDesignSourceMetadata;
import com.krdevops.springai.model.design.FileDesignSourceMetadata;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.service.artifact.ArtifactCatalogPort;
import com.krdevops.springai.service.artifact.ArtifactStorePort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Artifact Catalog/Store에 보관된 Design IR을 v2 읽기 모델로 제공한다.
 * v1 원본은 변경하거나 재저장하지 않고, Legacy Adapter를 통한 메모리 View만 반환한다.
 */
@Service
public class UiDesignSpecArtifactReader {

    private static final String JSON_MEDIA_TYPE = "application/json";

    private final ArtifactCatalogPort catalog;
    private final ArtifactStorePort store;
    private final ObjectMapper objectMapper;
    private final UiDesignSpecV1ToV2Adapter legacyAdapter;

    public UiDesignSpecArtifactReader(
            ArtifactCatalogPort catalog,
            ArtifactStorePort store,
            ObjectMapper objectMapper,
            UiDesignSpecV1ToV2Adapter legacyAdapter) {
        this.catalog = catalog;
        this.store = store;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.legacyAdapter = legacyAdapter;
    }

    public ReadResult read(String artifactId) {
        Artifact artifact = catalog.findById(requireText(artifactId))
                .orElseThrow(() -> new DesignIrReadException(
                        "DESIGN_IR_NOT_FOUND", "Design IR Artifact를 찾을 수 없습니다: " + artifactId));
        requireReadable(artifact);
        byte[] content = readAndVerify(artifact);

        try {
            JsonNode root = objectMapper.readTree(content);
            if (root == null || !root.isObject()) {
                throw new DesignIrReadException("DESIGN_IR_JSON_INVALID",
                        "Design IR JSON 최상위 값은 Object여야 합니다.");
            }
            if (UiDesignSpecV2.SCHEMA_VERSION.equals(root.path("schemaVersion").asText(null))) {
                return readV2(artifact, root);
            }
            if (root.has("uiSpec")) {
                return readLegacyAnalysis(artifact, root);
            }
            if (root.has("archetype")) {
                return readLegacySpec(artifact, root);
            }
            throw new DesignIrReadException("DESIGN_IR_SCHEMA_UNSUPPORTED",
                    "지원하는 UiDesignSpec v1/v2 계약을 판별할 수 없습니다.");
        } catch (DesignIrReadException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new DesignIrReadException("DESIGN_IR_JSON_INVALID",
                    "Design IR JSON 계약을 읽을 수 없습니다.", exception);
        }
    }

    private ReadResult readV2(Artifact artifact, JsonNode root) throws IOException {
        if (!"UI_DESIGN_SPEC_V2".equals(artifact.artifactType())) {
            throw new DesignIrReadException("DESIGN_IR_TYPE_MISMATCH",
                    "v2 JSON은 UI_DESIGN_SPEC_V2 Artifact Type이어야 합니다.");
        }
        UiDesignSpecV2 spec = objectMapper.treeToValue(root, UiDesignSpecV2.class);
        if (!artifact.artifactId().equals(spec.specId())) {
            throw new DesignIrReadException("DESIGN_IR_ID_MISMATCH",
                    "Artifact ID와 UiDesignSpecV2 specId가 일치하지 않습니다.");
        }
        return new ReadResult(artifact, spec, false, List.of());
    }

    private ReadResult readLegacyAnalysis(Artifact artifact, JsonNode root) throws IOException {
        String declaredVersion = root.path("uiSpecSchemaVersion").asText(UiDesignSpec.SCHEMA_VERSION);
        if (!UiDesignSpec.SCHEMA_VERSION.equals(declaredVersion)) {
            throw new DesignIrReadException("DESIGN_IR_SCHEMA_UNSUPPORTED",
                    "지원하지 않는 Legacy UiDesignSpec Version입니다: " + declaredVersion);
        }
        DesignAnalysisResult analysis = objectMapper.treeToValue(root, DesignAnalysisResult.class);
        if (analysis.uiSpec() == null) {
            throw new DesignIrReadException("DESIGN_IR_JSON_INVALID",
                    "DesignAnalysisResult에 uiSpec이 없습니다.");
        }
        List<String> warnings = new ArrayList<>(analysis.warnings());
        warnings.add("UiDesignSpec v1 Artifact를 v2 읽기 View로 변환했습니다. 원본은 변경되지 않았습니다.");
        UiDesignSpecV2 converted = legacyAdapter.adapt(
                artifact.artifactId(), analysis.uiSpec(), sourceOf(artifact, analysis));
        return new ReadResult(artifact, converted, true, warnings);
    }

    private ReadResult readLegacySpec(Artifact artifact, JsonNode root) throws IOException {
        UiDesignSpec legacy = objectMapper.treeToValue(root, UiDesignSpec.class);
        UiDesignSpecV2 converted = legacyAdapter.adapt(
                artifact.artifactId(), legacy,
                new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.IMAGE, null, null,
                        sourceRevision(artifact, artifact.contentHash())));
        return new ReadResult(artifact, converted, true, List.of(
                "출처 Envelope가 없는 UiDesignSpec v1을 IMAGE Legacy Source로 변환했습니다.",
                "원본 Artifact는 변경되지 않았습니다."));
    }

    private UiDesignSpecV2.Source sourceOf(Artifact artifact, DesignAnalysisResult analysis) {
        String revision = sourceRevision(artifact, analysis.sourceHash());
        if (analysis.sourceType() == DesignSourceType.FIGMA
                && analysis.sourceMetadata() instanceof FigmaDesignSourceMetadata figma) {
            return new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.FIGMA,
                    figma.fileKey(), figma.nodeId(), sourceRevision(artifact, figma.fileVersion()));
        }
        if (analysis.sourceType() == DesignSourceType.WEB_CAPTURE) {
            return new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.WEB_CAPTURE,
                    null, null, revision);
        }
        UiDesignSpecV2.SourceType type = UiDesignSpecV2.SourceType.IMAGE;
        if (analysis.sourceMetadata() instanceof FileDesignSourceMetadata file
                && file.sourcePath() != null
                && file.sourcePath().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            type = UiDesignSpecV2.SourceType.PDF;
        }
        return new UiDesignSpecV2.Source(type, null, null, revision);
    }

    private byte[] readAndVerify(Artifact artifact) {
        try {
            byte[] content = store.read(artifact.contentHash())
                    .orElseThrow(() -> new DesignIrReadException(
                            "DESIGN_IR_CONTENT_MISSING", "Design IR Artifact 바이트를 찾을 수 없습니다."));
            if (!artifact.contentHash().equals(ContentHashes.sha256Hex(content))) {
                throw new DesignIrReadException("DESIGN_IR_HASH_MISMATCH",
                        "Catalog Hash와 저장된 Design IR 바이트 Hash가 일치하지 않습니다.");
            }
            return content;
        } catch (IOException exception) {
            throw new UncheckedIOException("Design IR Artifact를 읽는 중 오류가 발생했습니다.", exception);
        }
    }

    private void requireReadable(Artifact artifact) {
        if (artifact.status() != ArtifactStatus.ACTIVE) {
            throw new DesignIrReadException("DESIGN_IR_NOT_ACTIVE",
                    "ACTIVE 상태가 아닌 Design IR Artifact는 읽을 수 없습니다.");
        }
        if (!JSON_MEDIA_TYPE.equalsIgnoreCase(artifact.mediaType())) {
            throw new DesignIrReadException("DESIGN_IR_MEDIA_TYPE_UNSUPPORTED",
                    "Design IR Artifact는 application/json이어야 합니다.");
        }
    }

    private String sourceRevision(Artifact artifact, String fallback) {
        return artifact.sourceRevision() != null && !artifact.sourceRevision().isBlank()
                ? artifact.sourceRevision() : requireText(fallback);
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new DesignIrReadException("DESIGN_IR_ARGUMENT_INVALID", "Artifact ID 또는 Source Revision이 없습니다.");
        }
        return value.trim();
    }

    public record ReadResult(
            Artifact artifact,
            UiDesignSpecV2 spec,
            boolean legacyConverted,
            List<String> warnings
    ) {
        public ReadResult {
            if (artifact == null || spec == null) throw new IllegalArgumentException("artifact와 spec은 필수입니다.");
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public static final class DesignIrReadException extends IllegalStateException {
        private final String errorCode;

        public DesignIrReadException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public DesignIrReadException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
