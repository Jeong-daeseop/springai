package com.krdevops.springai.model.designsystem;

import java.util.List;

/** 사람이 Registry 반영 전에 확인하는 Publish 후보 변경 집합이다. */
public record ComponentRegistryDiff(
        String profileId,
        String candidateVersion,
        String previousVersion,
        boolean valid,
        List<DesignSystemIssue> issues,
        List<Change> changes
) {
    public ComponentRegistryDiff {
        issues = issues == null ? List.of() : List.copyOf(issues);
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public record Change(
            String logicalId,
            AssetType assetType,
            ChangeType changeType,
            String previousKey,
            String candidateKey
    ) {}

    public enum AssetType { COMPONENT, VARIABLE }

    public enum ChangeType { ADD, UPDATE, NO_CHANGE, DEPRECATE }
}
