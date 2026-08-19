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
        ResolvedComponentRegistrySnapshot resolvedComponentRegistry,
        ScreenPatternSnapshot screenPattern,
        VariantRuleSetSnapshot variantRuleSet,
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
        if (metadata.hasSsotEvidence()) {
            if (resolvedComponentRegistry == null) {
                throw new IllegalArgumentException("SSOT Bundle에는 resolvedComponentRegistry가 필수입니다.");
            }
            if (!java.util.Objects.equals(metadata.catalogVersion(), resolvedComponentRegistry.catalogVersion())
                    || !java.util.Objects.equals(metadata.catalogHash(), resolvedComponentRegistry.catalogHash())
                    || !java.util.Objects.equals(metadata.registryVersion(), resolvedComponentRegistry.registryVersion())
                    || !java.util.Objects.equals(metadata.registryHash(), resolvedComponentRegistry.registryHash())) {
                throw new IllegalArgumentException("BUNDLE_SSOT_EVIDENCE_MISMATCH: Metadata와 Resolved Registry 증적이 다릅니다.");
            }
        }
        if (FigmaScreenSpec.SCHEMA_VERSION_V2.equals(metadata.figmaScreenSpecSchemaVersion())
                && (screenPattern == null || variantRuleSet == null)) {
            throw new IllegalArgumentException("v2 Bundle에는 Pattern과 Variant Rule Set Snapshot이 필수입니다.");
        }
    }

    /** Role·Variant v2 도입 전 Bundle Java 호출자 호환. */
    public FigmaExportBundle(
            FigmaScreenSpec figmaScreenSpec,
            DesignSystemProfileSnapshot designSystemProfile,
            ComponentRegistrySnapshot componentRegistry,
            ScreenPatternSnapshot screenPattern,
            VariantRuleSetSnapshot variantRuleSet,
            FigmaExportMetadata metadata
    ) {
        this(figmaScreenSpec, designSystemProfile, componentRegistry, null,
                screenPattern, variantRuleSet, metadata);
    }

    public FigmaExportBundle(
            FigmaScreenSpec figmaScreenSpec,
            DesignSystemProfileSnapshot designSystemProfile,
            ComponentRegistrySnapshot componentRegistry,
            FigmaExportMetadata metadata
    ) {
        this(figmaScreenSpec, designSystemProfile, componentRegistry, null, null, null, metadata);
    }

    /**
     * R5-040: 7가지 요청 오케스트레이션이 Bundle을 Operation의 Artifact로 저장하기 직전,
     * Plugin이 Preview에서 읽을 수 있도록 metadata에 operationId를 새겨 넣는다.
     */
    public FigmaExportBundle withOperationId(String operationId) {
        return new FigmaExportBundle(
                figmaScreenSpec, designSystemProfile, componentRegistry, resolvedComponentRegistry,
                screenPattern, variantRuleSet, metadata.withOperationId(operationId));
    }

    /** 이 Bundle이 어느 생성 경로(오케스트레이션/하이브리드)에서 나왔는지 metadata에 새겨 넣는다. */
    public FigmaExportBundle withOrigin(FigmaExportMetadata.Origin origin) {
        return new FigmaExportBundle(
                figmaScreenSpec, designSystemProfile, componentRegistry, resolvedComponentRegistry,
                screenPattern, variantRuleSet, metadata.withOrigin(origin));
    }
}
