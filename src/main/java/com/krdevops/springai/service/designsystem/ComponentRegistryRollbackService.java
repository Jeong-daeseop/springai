package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import org.springframework.stereotype.Service;

/** 운영자가 대상 버전을 명시한 경우에만 Registry v3 이전 Snapshot을 연결한다. */
@Service
public class ComponentRegistryRollbackService {

    private final ComponentRegistrySnapshotV3Repository repository;

    public ComponentRegistryRollbackService(ComponentRegistrySnapshotV3Repository repository) {
        this.repository = repository;
    }

    public ComponentRegistrySnapshotV3 rollback(String profileId, String targetVersion,
                                                boolean confirmed, String actor) {
        if (!confirmed) {
            throw new IllegalArgumentException("Registry Rollback에는 운영자의 명시적 확인이 필요합니다.");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Registry Rollback actor는 필수입니다.");
        }
        if (targetVersion == null || targetVersion.isBlank()) {
            throw new IllegalArgumentException("Rollback 대상 Registry 버전은 필수입니다.");
        }
        ComponentRegistrySnapshotV3 snapshot = repository.findVersion(profileId, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ROLLBACK_TARGET_NOT_FOUND: 승인된 Registry Snapshot이 없습니다."));
        if (!snapshot.approved()) {
            throw new IllegalArgumentException("ROLLBACK_TARGET_NOT_APPROVED: 승인된 Snapshot만 Rollback할 수 있습니다.");
        }
        return snapshot;
    }
}
