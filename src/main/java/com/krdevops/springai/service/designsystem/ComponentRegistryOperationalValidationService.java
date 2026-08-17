package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 운영 중인 모든 승인 Registry v3 Snapshot을 Catalog와 일괄 교차검증한다. */
@Service
public class ComponentRegistryOperationalValidationService {

    private final ComponentRegistrySnapshotV3Repository repository;
    private final ComponentCatalogLoader catalogLoader;
    private final ComponentRegistryBindingValidator validator;
    private final ComponentRegistrySnapshotV3SyncService syncService;

    public ComponentRegistryOperationalValidationService(
            ComponentRegistrySnapshotV3Repository repository,
            ComponentCatalogLoader catalogLoader,
            ComponentRegistryBindingValidator validator,
            ComponentRegistrySnapshotV3SyncService syncService) {
        this.repository = repository;
        this.catalogLoader = catalogLoader;
        this.validator = validator;
        this.syncService = syncService;
    }

    public BatchResult validateAll() {
        List<SnapshotResult> results = new ArrayList<>();
        for (ComponentRegistrySnapshotV3 snapshot : repository.findAllApproved()) {
            List<DesignSystemIssue> issues;
            try {
                var loaded = catalogLoader.load(snapshot.catalogVersion());
                issues = new ArrayList<>(validator.validate(loaded.catalog(), loaded.contentHash(), snapshot, true));
                if (!syncService.computeContentHash(snapshot).equals(snapshot.contentHash())) {
                    issues.add(new DesignSystemIssue("REGISTRY_CONTENT_HASH_MISMATCH",
                            DesignSystemIssue.Severity.ERROR, "Registry Snapshot 내용 Hash가 다릅니다.",
                            snapshot.registryVersion()));
                }
            } catch (RuntimeException exception) {
                issues = List.of(new DesignSystemIssue("REGISTRY_SNAPSHOT_VALIDATION_FAILED",
                        DesignSystemIssue.Severity.ERROR, exception.getMessage(), snapshot.registryVersion()));
            }
            results.add(new SnapshotResult(snapshot.profileId(), snapshot.registryVersion(),
                    issues.stream().noneMatch(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                            || issue.severity() == DesignSystemIssue.Severity.FATAL), List.copyOf(issues)));
        }
        return new BatchResult(results.stream().allMatch(SnapshotResult::valid), List.copyOf(results));
    }

    public record BatchResult(boolean valid, List<SnapshotResult> snapshots) {
        public BatchResult { snapshots = List.copyOf(snapshots); }
    }
    public record SnapshotResult(String profileId, String registryVersion, boolean valid,
                                 List<DesignSystemIssue> issues) {
        public SnapshotResult { issues = List.copyOf(issues); }
    }
}
