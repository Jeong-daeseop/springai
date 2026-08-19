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
        String operationId,

        /** 이 Bundle이 어느 생성 경로에서 나왔는지. null이면 compact constructor가 STANDARD로 채운다. */
        Origin origin
) {
    /** .figpack/7가지 요청 등 Bundle 생성 경로 구분. 캔버스 노드가 아니라 Bundle 메타데이터에만 기록된다. */
    public enum Origin {
        /** 일반 CRUD 생성(buildFullCrudPrompt 등) 또는 origin을 아직 채우지 않은 기본값. */
        STANDARD,
        /** 7가지 디자인 요청 오케스트레이션(FigmaDesignOrchestrationService.generateBundle) 경로. */
        ORCHESTRATED,
        /** .figpack 하이브리드(FigmaHybridExportService) 경로. */
        HYBRID
    }

    public FigmaExportMetadata {
        exportedAt = exportedAt == null ? LocalDateTime.now() : exportedAt;
        figmaScreenSpecSchemaVersion = figmaScreenSpecSchemaVersion == null
                ? FigmaScreenSpec.SCHEMA_VERSION : figmaScreenSpecSchemaVersion;
        origin = origin == null ? Origin.STANDARD : origin;
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
                catalogHash, registryHash, null, null);
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
                null, null, null, null, null);
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
                variantRuleSetVersion, componentContractVersion, null, null, null, null, null);
    }

    /** R5-040 operationId 도입 전 기존 필드 그대로에 operationId만 채워 넣는다. 기존 origin은 보존한다. */
    public FigmaExportMetadata withOperationId(String operationId) {
        return new FigmaExportMetadata(
                exportedAt, figmaScreenSpecSchemaVersion, screenSpecificationVersion,
                designSystemProfileVersion, registryVersion, screenPatternVersion,
                variantRuleSetVersion, componentContractVersion, catalogVersion,
                catalogHash, registryHash, operationId, origin);
    }

    /** 기존 필드 그대로에 origin만 채워 넣는다. 기존 operationId는 보존한다. */
    public FigmaExportMetadata withOrigin(Origin origin) {
        return new FigmaExportMetadata(
                exportedAt, figmaScreenSpecSchemaVersion, screenSpecificationVersion,
                designSystemProfileVersion, registryVersion, screenPatternVersion,
                variantRuleSetVersion, componentContractVersion, catalogVersion,
                catalogHash, registryHash, operationId, origin);
    }

    public boolean hasSsotEvidence() {
        return catalogVersion != null && !catalogVersion.isBlank()
                && catalogHash != null && catalogHash.matches("[a-f0-9]{64}")
                && registryHash != null && registryHash.matches("[a-f0-9]{64}");
    }
}
