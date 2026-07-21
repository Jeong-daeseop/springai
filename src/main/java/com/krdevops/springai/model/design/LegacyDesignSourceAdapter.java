package com.krdevops.springai.model.design;

/** 이전 flat JSON을 공통 source metadata로 승격한다. */
public final class LegacyDesignSourceAdapter {

    private LegacyDesignSourceAdapter() {
    }

    public static DesignSourceMetadata adapt(
            DesignSourceType sourceType, String sourcePath, String pageRange, FigmaSource figmaSource) {
        DesignSourceType type = sourceType == null ? DesignSourceType.FILE : sourceType;
        return switch (type) {
            case FILE -> new FileDesignSourceMetadata(sourcePath, pageRange);
            case FIGMA -> {
                if (figmaSource == null) {
                    String legacyFileKey = sourcePath != null && sourcePath.startsWith("figma://")
                            ? sourcePath.substring("figma://".length()).split("#", 2)[0] : sourcePath;
                    yield new FigmaDesignSourceMetadata(legacyFileKey, null, null);
                }
                yield new FigmaDesignSourceMetadata(
                        figmaSource.fileKey(), figmaSource.nodeId(), figmaSource.fileVersion());
            }
            case WEB_CAPTURE -> throw new IllegalArgumentException(
                    "WEB_CAPTURE source에는 sourceMetadata가 필요합니다.");
        };
    }
}
