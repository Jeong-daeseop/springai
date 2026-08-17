package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 운영 Registry v2의 v3 전환 Preview와 명시적 승인 적용을 제공한다. */
@Service
public class ComponentRegistryMigrationService {

    private final ComponentRegistryRepository legacyRepository;
    private final ComponentCatalogLoader catalogLoader;
    private final ComponentRegistryV2ToV3Converter converter;
    private final ComponentRegistrySnapshotV3SyncService syncService;

    public ComponentRegistryMigrationService(ComponentRegistryRepository legacyRepository,
            ComponentCatalogLoader catalogLoader,
            ComponentRegistryV2ToV3Converter converter,
            ComponentRegistrySnapshotV3SyncService syncService) {
        this.legacyRepository = legacyRepository;
        this.catalogLoader = catalogLoader;
        this.converter = converter;
        this.syncService = syncService;
    }

    public MigrationPreview preview(String profileId, String registryVersion, String catalogVersion) {
        ComponentRegistry legacy = legacyRepository.findVersion(profileId, registryVersion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Legacy Registry 버전을 찾을 수 없습니다: " + profileId + "/" + registryVersion));
        var conversion = converter.convert(legacy, catalogLoader.load(catalogVersion));
        var validation = syncService.preview(conversion.candidate());
        List<DesignSystemIssue> issues = new ArrayList<>(validation.issues());
        conversion.skippedBindings().forEach((logicalType, reason) -> issues.add(new DesignSystemIssue(
                "NON_ATOMIC_BINDING_DROPPED", DesignSystemIssue.Severity.WARNING,
                "Pattern/Page Template Binding은 v3 원자 Binding에서 제외됩니다: " + reason, logicalType)));
        boolean valid = issues.stream().noneMatch(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                || issue.severity() == DesignSystemIssue.Severity.FATAL);
        return new MigrationPreview(valid, conversion.candidate(), List.copyOf(issues),
                legacy.components().size(), conversion.candidate().bindings().size(),
                conversion.skippedBindings());
    }

    public ComponentRegistrySnapshotV3 apply(String profileId, String registryVersion,
            String catalogVersion, boolean confirmed, String actor) {
        return apply(profileId, registryVersion, catalogVersion, confirmed, false, actor);
    }

    public ComponentRegistrySnapshotV3 apply(String profileId, String registryVersion,
            String catalogVersion, boolean confirmed, boolean breakingChangeConfirmed, String actor) {
        MigrationPreview preview = preview(profileId, registryVersion, catalogVersion);
        if (!preview.valid()) throw new ComponentRegistrySnapshotV3SyncService.RegistryV3RejectedException(preview.issues());
        return breakingChangeConfirmed
                ? syncService.apply(preview.candidate(), confirmed, true, actor)
                : syncService.apply(preview.candidate(), confirmed, actor);
    }

    public record MigrationPreview(
            boolean valid,
            ComponentRegistrySnapshotV3 candidate,
            List<DesignSystemIssue> issues,
            int legacyBindingCount,
            int migratedBindingCount,
            Map<String, String> skippedBindings
    ) {}
}
