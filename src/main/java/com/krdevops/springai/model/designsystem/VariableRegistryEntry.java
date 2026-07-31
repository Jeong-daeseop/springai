package com.krdevops.springai.model.designsystem;

/** Published Figma Variable과 Variable Collection의 공개 Key를 보존한다. */
public record VariableRegistryEntry(
        String variableKey,
        String variableName,
        String collectionKey,
        String collectionName,
        String resolvedType,
        ComponentRegistryEntry.PublishStatus publishStatus
) {
    public VariableRegistryEntry {
        if (variableKey == null || variableKey.isBlank()) {
            throw new IllegalArgumentException("variableKey는 필수입니다.");
        }
        publishStatus = publishStatus == null
                ? ComponentRegistryEntry.PublishStatus.UNPUBLISHED
                : publishStatus;
    }
}
