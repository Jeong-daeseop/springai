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
        String registryHash,
        /** R5-040: 이 Bundle이 어떤 FigmaDesignOperation에서 생성됐는지. 7가지 요청 오케스트레이션 경로가 아니면 null. */
        String operationId
) {
    public FigmaExportMetadata {
        exportedAt = exportedAt == null ? LocalDateTime.now() : exportedAt;
        figmaScreenSpecSchemaVersion = figmaScreenSpecSchemaVersion == null
                ? FigmaScreenSpec.SCHEMA_VERSION : figmaScreenSpecSchemaVersion;
    }

    /** operationId 도입 전 호출자 호환. */
    public FigmaExportMetadata(
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
        this(exportedAt, figmaScreenSpecSchemaVersion, screenSpecificationVersion,
                designSystemProfileVersion, registryVersion, screenPatternVersion,
                variantRuleSetVersion, componentContractVersion, catalogVersion,
                catalogHash, registryHash, null);
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
                null, null, null, null);
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
                variantRuleSetVersion, componentContractVersion, null, null, null, null);
    }

    /** R5-040 operationId 도입 전 기존 필드 그대로에 operationId만 채워 넣는다. */
    public FigmaExportMetadata withOperationId(String operationId) {
        return new FigmaExportMetadata(
                exportedAt, figmaScreenSpecSchemaVersion, screenSpecificationVersion,
                designSystemProfileVersion, registryVersion, screenPatternVersion,
                variantRuleSetVersion, componentContractVersion, catalogVersion,
                catalogHash, registryHash, operationId);
    }

    public boolean hasSsotEvidence() {
        return catalogVersion != null && !catalogVersion.isBlank()
                && catalogHash != null && catalogHash.matches("[a-f0-9]{64}")
                && registryHash != null && registryHash.matches("[a-f0-9]{64}");
    }
}
