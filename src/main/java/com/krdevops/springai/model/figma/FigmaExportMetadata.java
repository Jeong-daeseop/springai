package com.krdevops.springai.model.figma;

import java.time.LocalDateTime;

/** FigmaExportBundle에 동봉되는 산출물 메타데이터(R2-035): 각 조각의 스키마·버전을 함께 기록한다. */
public record FigmaExportMetadata(
        LocalDateTime exportedAt,
        String figmaScreenSpecSchemaVersion,
        int screenSpecificationVersion,
        String designSystemProfileVersion,
        String registryVersion,
        String screenPatternVersion,
        String variantRuleSetVersion,
        String componentContractVersion,
        String catalogVersion,
        String catalogHash,
        String registryHash
) {
    public FigmaExportMetadata {
        exportedAt = exportedAt == null ? LocalDateTime.now() : exportedAt;
        figmaScreenSpecSchemaVersion = figmaScreenSpecSchemaVersion == null
                ? FigmaScreenSpec.SCHEMA_VERSION : figmaScreenSpecSchemaVersion;
    }

    /** Role·Variant v2 버전 도입 전 호출자 호환. */
    public FigmaExportMetadata(
            LocalDateTime exportedAt,
            String figmaScreenSpecSchemaVersion,
            int screenSpecificationVersion,
            String designSystemProfileVersion,
            String registryVersion
    ) {
        this(exportedAt, figmaScreenSpecSchemaVersion, screenSpecificationVersion,
                designSystemProfileVersion, registryVersion, null, null, null,
                null, null, null);
    }

    /** SSOT 증적 도입 전 Role·Variant v2 호출자 호환. */
    public FigmaExportMetadata(
            LocalDateTime exportedAt,
            String figmaScreenSpecSchemaVersion,
            int screenSpecificationVersion,
            String designSystemProfileVersion,
            String registryVersion,
            String screenPatternVersion,
            String variantRuleSetVersion,
            String componentContractVersion
    ) {
        this(exportedAt, figmaScreenSpecSchemaVersion, screenSpecificationVersion,
                designSystemProfileVersion, registryVersion, screenPatternVersion,
                variantRuleSetVersion, componentContractVersion, null, null, null);
    }

    public boolean hasSsotEvidence() {
        return catalogVersion != null && !catalogVersion.isBlank()
                && catalogHash != null && catalogHash.matches("[a-f0-9]{64}")
                && registryHash != null && registryHash.matches("[a-f0-9]{64}");
    }
}
