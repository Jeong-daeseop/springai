package com.krdevops.springai.model.design;

import java.time.LocalDateTime;
import java.util.List;

public record DesignAnalysisResult(
        String analysisId,
        String sourceHash,
        String sourcePath,
        String pageRange,
        DesignSourceType sourceType,
        FigmaSource figmaSource,
        String analysisContractVersion,
        String uiSpecSchemaVersion,
        String featureType,
        String provider,
        String model,
        String promptVersion,
        List<Integer> pages,
        UiDesignSpec uiSpec,
        List<String> warnings,
        LocalDateTime createdAt,
        DesignSourceMetadata sourceMetadata
) {
    public DesignAnalysisResult {
        sourceMetadata = sourceMetadata == null
                ? LegacyDesignSourceAdapter.adapt(sourceType, sourcePath, pageRange, figmaSource)
                : sourceMetadata;
        sourceType = sourceType == null ? sourceMetadata.sourceType() : sourceType;
        if (sourceType != sourceMetadata.sourceType()) {
            throw new IllegalArgumentException("sourceType과 sourceMetadata subtype이 일치하지 않습니다.");
        }
        analysisContractVersion = analysisContractVersion == null || analysisContractVersion.isBlank()
                ? promptVersion : analysisContractVersion;
        uiSpecSchemaVersion = uiSpecSchemaVersion == null || uiSpecSchemaVersion.isBlank()
                ? UiDesignSpec.SCHEMA_VERSION : uiSpecSchemaVersion;
        featureType = featureType == null || featureType.isBlank()
                ? inferFeatureType(uiSpec) : featureType;
        pages = pages == null ? List.of() : List.copyOf(pages);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    /** 공통 sourceMetadata 도입 전 canonical constructor 호출자 호환. */
    public DesignAnalysisResult(
            String analysisId, String sourceHash, String sourcePath, String pageRange,
            DesignSourceType sourceType, FigmaSource figmaSource,
            String analysisContractVersion, String uiSpecSchemaVersion, String featureType,
            String provider, String model, String promptVersion, List<Integer> pages,
            UiDesignSpec uiSpec, List<String> warnings, LocalDateTime createdAt) {
        this(analysisId, sourceHash, sourcePath, pageRange, sourceType, figmaSource,
                analysisContractVersion, uiSpecSchemaVersion, featureType, provider, model,
                promptVersion, pages, uiSpec, warnings, createdAt, null);
    }

    /** Figma 출처 필드 도입 전 호출자 호환. */
    public DesignAnalysisResult(
            String analysisId, String sourceHash, String sourcePath, String pageRange,
            String provider, String model, String promptVersion, List<Integer> pages,
            UiDesignSpec uiSpec, List<String> warnings, LocalDateTime createdAt) {
        this(analysisId, sourceHash, sourcePath, pageRange, DesignSourceType.FILE, null,
                promptVersion, UiDesignSpec.SCHEMA_VERSION, null,
                provider, model, promptVersion, pages, uiSpec, warnings, createdAt, null);
    }

    public static DesignAnalysisResult webCapture(
            String analysisId, String sourceHash, WebCaptureDesignSourceMetadata metadata,
            String contractVersion, String featureType, UiDesignSpec uiSpec,
            List<String> warnings, LocalDateTime createdAt) {
        return new DesignAnalysisResult(analysisId, sourceHash, null, null,
                DesignSourceType.WEB_CAPTURE, null, contractVersion, UiDesignSpec.SCHEMA_VERSION,
                featureType, "web-capture", "deterministic-mapper", contractVersion,
                List.of(), uiSpec, warnings, createdAt, metadata);
    }

    private static String inferFeatureType(UiDesignSpec uiSpec) {
        if (uiSpec == null || uiSpec.archetype() == null) return "crud";
        String archetype = uiSpec.archetype().toUpperCase();
        if (archetype.startsWith("MASTER_DETAIL")) return "master-detail";
        if (archetype.startsWith("BOARD")) return "board";
        return "crud";
    }
}
