package com.krdevops.springai.model.figma;

/**
 * DEC-10=FILE(파일 우선 입력)일 때 Plugin에 전달하는 단일 입력 파일의 계약.
 * FigmaScreenSpec만으로는 Profile/Registry 조회 경로가 정의되지 않는 문제(R0-018)를
 * 이 Bundle이 하나의 파일 안에서 해결한다.
 */
public record FigmaExportBundle(
        FigmaScreenSpec figmaScreenSpec,
        DesignSystemProfileSnapshot designSystemProfile,
        ComponentRegistrySnapshot componentRegistry,
        FigmaExportMetadata metadata
) {
    public static final String SCHEMA_VERSION = "figma-export-bundle-v1";

    public FigmaExportBundle {
        if (figmaScreenSpec == null) {
            throw new IllegalArgumentException("figmaScreenSpec은 필수입니다.");
        }
        if (designSystemProfile == null) {
            throw new IllegalArgumentException("designSystemProfile은 필수입니다.");
        }
        if (componentRegistry == null) {
            throw new IllegalArgumentException("componentRegistry는 필수입니다.");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata는 필수입니다.");
        }
    }
}
