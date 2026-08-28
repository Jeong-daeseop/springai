package com.krdevops.springai.model.design;

import java.time.LocalDateTime;
import java.util.List;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.jspecify.annotations.Nullable;

public record ScreenSpecification(
        String id,
        int version,
        ScreenSpecStatus status,
        String screenName,
        String featureType,
        String archetype,
        String database,
        String primaryTable,
        List<DataSourceSpec> dataSources,
        List<PageSpec> pages,
        List<SpecIssue> issues,
        LayoutDensity layoutDensity,
        FormColumnLayout formColumnLayout,
        ActionPlacement actionPlacement,
        SearchPanelPlacement searchPanelPlacement,
        LocalDateTime createdAt,
        @Nullable VersionedArtifactReference uiDesignSpecReference,
        @Nullable VersionedArtifactReference designSystemSnapshotReference,
        List<UiDesignSpec.ComponentSpec> componentStyles,
        List<UiDesignSpec.NodeGeometry> componentGeometry
) {
    public ScreenSpecification {
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        pages = pages == null ? List.of() : List.copyOf(pages);
        issues = issues == null ? List.of() : List.copyOf(issues);
        layoutDensity = layoutDensity == null ? LayoutDensity.STANDARD : layoutDensity;
        formColumnLayout = formColumnLayout == null ? FormColumnLayout.SINGLE_COLUMN : formColumnLayout;
        actionPlacement = actionPlacement == null ? ActionPlacement.TOP_RIGHT : actionPlacement;
        searchPanelPlacement = searchPanelPlacement == null ? SearchPanelPlacement.ABOVE_TABLE : searchPanelPlacement;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        componentStyles = componentStyles == null ? List.of() : List.copyOf(componentStyles);
        componentGeometry = componentGeometry == null ? List.of() : List.copyOf(componentGeometry);
    }

    /** componentGeometry 도입 전 호출자 호환. */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName,
            String featureType, String archetype, String database, String primaryTable,
            List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
            LayoutDensity layoutDensity, FormColumnLayout formColumnLayout,
            ActionPlacement actionPlacement, SearchPanelPlacement searchPanelPlacement,
            LocalDateTime createdAt,
            @Nullable VersionedArtifactReference uiDesignSpecReference,
            @Nullable VersionedArtifactReference designSystemSnapshotReference,
            List<UiDesignSpec.ComponentSpec> componentStyles) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, layoutDensity, formColumnLayout, actionPlacement,
                searchPanelPlacement, createdAt, uiDesignSpecReference, designSystemSnapshotReference,
                componentStyles, List.of());
    }

    /** componentStyles 도입 전 호출자 호환. */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName,
            String featureType, String archetype, String database, String primaryTable,
            List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
            LayoutDensity layoutDensity, FormColumnLayout formColumnLayout,
            ActionPlacement actionPlacement, SearchPanelPlacement searchPanelPlacement,
            LocalDateTime createdAt,
            @Nullable VersionedArtifactReference uiDesignSpecReference,
            @Nullable VersionedArtifactReference designSystemSnapshotReference) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, layoutDensity, formColumnLayout, actionPlacement,
                searchPanelPlacement, createdAt, uiDesignSpecReference, designSystemSnapshotReference,
                List.of());
    }

    /** v2 Design IR 참조 도입 전 전체 생성자 호출 호환. */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName,
            String featureType, String archetype, String database, String primaryTable,
            List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
            LayoutDensity layoutDensity, FormColumnLayout formColumnLayout,
            ActionPlacement actionPlacement, SearchPanelPlacement searchPanelPlacement,
            LocalDateTime createdAt) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, layoutDensity, formColumnLayout,
                actionPlacement, searchPanelPlacement, createdAt, null, null, List.of());
    }

    /** actionPlacement/searchPanelPlacement 도입 전 호출자 호환. */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName,
            String featureType, String archetype, String database, String primaryTable,
            List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
            LayoutDensity layoutDensity, FormColumnLayout formColumnLayout, LocalDateTime createdAt) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, layoutDensity, formColumnLayout,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, createdAt, null, null);
    }

    /** formColumnLayout 도입 전 호출자 호환. */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName,
            String featureType, String archetype, String database, String primaryTable,
            List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
            LayoutDensity layoutDensity, LocalDateTime createdAt) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, layoutDensity, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, createdAt, null, null);
    }

    /** layoutDensity/formColumnLayout 도입 전 호출자 및 기존 JSON 호환. */
    public ScreenSpecification(
            String id, int version, ScreenSpecStatus status, String screenName,
            String featureType, String archetype, String database, String primaryTable,
            List<DataSourceSpec> dataSources, List<PageSpec> pages, List<SpecIssue> issues,
            LocalDateTime createdAt) {
        this(id, version, status, screenName, featureType, archetype, database, primaryTable,
                dataSources, pages, issues, LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, createdAt, null, null);
    }

    public ScreenSpecification withValidation(ScreenSpecStatus newStatus, List<SpecIssue> newIssues) {
        return new ScreenSpecification(id, version, newStatus, screenName, featureType, archetype,
                database, primaryTable, dataSources, pages, newIssues, layoutDensity, formColumnLayout,
                actionPlacement, searchPanelPlacement, createdAt,
                uiDesignSpecReference, designSystemSnapshotReference, componentStyles, componentGeometry);
    }

    public ScreenSpecification withStatus(ScreenSpecStatus newStatus) {
        return withValidation(newStatus, issues);
    }

    public ScreenSpecification withDesignContext(
            VersionedArtifactReference designReference,
            @Nullable VersionedArtifactReference designSystemReference,
            List<SpecIssue> additionalIssues) {
        java.util.ArrayList<SpecIssue> combined = new java.util.ArrayList<>(issues);
        if (additionalIssues != null) combined.addAll(additionalIssues);
        return new ScreenSpecification(id, version, status, screenName, featureType, archetype,
                database, primaryTable, dataSources, pages, combined, layoutDensity, formColumnLayout,
                actionPlacement, searchPanelPlacement, createdAt,
                designReference, designSystemReference, componentStyles, componentGeometry);
    }
}
