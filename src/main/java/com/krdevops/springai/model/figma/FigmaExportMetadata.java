package com.krdevops.springai.model.figma;

import java.time.LocalDateTime;

/** FigmaExportBundle에 동봉되는 산출물 메타데이터(R2-035): 각 조각의 스키마·버전을 함께 기록한다. */
public record FigmaExportMetadata(
        LocalDateTime exportedAt,
        String figmaScreenSpecSchemaVersion,
        int screenSpecificationVersion,
        String designSystemProfileVersion,
        String registryVersion
) {
    public FigmaExportMetadata {
        exportedAt = exportedAt == null ? LocalDateTime.now() : exportedAt;
        figmaScreenSpecSchemaVersion = figmaScreenSpecSchemaVersion == null
                ? FigmaScreenSpec.SCHEMA_VERSION : figmaScreenSpecSchemaVersion;
    }
}
