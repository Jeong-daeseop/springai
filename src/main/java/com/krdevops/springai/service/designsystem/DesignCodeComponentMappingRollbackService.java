package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/** 과거 승인 Mapping 의미 payload를 새 Version 후보로 복원하고 동일 승인 Gate를 다시 통과시킨다. */
@Service
public class DesignCodeComponentMappingRollbackService {

    private final DesignCodeComponentMappingRepository repository;
    private final DesignCodeComponentMappingApprovalService approvalService;
    private final DesignCodeComponentMappingHashService hashService;

    public DesignCodeComponentMappingRollbackService(
            DesignCodeComponentMappingRepository repository,
            DesignCodeComponentMappingApprovalService approvalService,
            DesignCodeComponentMappingHashService hashService) {
        this.repository = repository;
        this.approvalService = approvalService;
        this.hashService = hashService;
    }

    public DesignCodeComponentMapping rollback(
            Path projectRoot,
            ComponentCatalog catalog,
            String catalogHash,
            ComponentRegistrySnapshotV3 registry,
            String mappingId,
            String targetVersion,
            String newVersion,
            String rendererProfile,
            boolean confirmed,
            boolean breakingChangeConfirmed,
            String actor) {
        if (!confirmed) throw new IllegalArgumentException("Mapping Rollback에는 운영자의 명시적 확인이 필요합니다.");
        requireText(mappingId, "mappingId");
        requireText(targetVersion, "targetVersion");
        requireText(newVersion, "newVersion");
        if (targetVersion.equals(newVersion)) {
            throw new IllegalArgumentException("Rollback은 과거 Version을 덮어쓰지 않고 새 Version을 사용해야 합니다.");
        }
        DesignCodeComponentMapping target = repository.findVersion(mappingId, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ROLLBACK_TARGET_NOT_FOUND: Mapping Version을 찾을 수 없습니다."));
        if (target.status() != DesignCodeComponentMapping.Status.APPROVED) {
            throw new IllegalArgumentException(
                    "ROLLBACK_TARGET_NOT_APPROVED: 승인된 Mapping Version만 복원할 수 있습니다.");
        }
        if (repository.findVersion(mappingId, newVersion).isPresent()) {
            throw new IllegalStateException("MAPPING_VERSION_CONFLICT: Rollback 새 Version이 이미 존재합니다.");
        }

        DesignCodeComponentMapping unhashed = copyAsCandidate(target, newVersion, "0".repeat(64));
        DesignCodeComponentMapping candidate = copyAsCandidate(
                target, newVersion, hashService.compute(unhashed));
        return approvalService.approve(projectRoot, catalog, catalogHash, registry, candidate,
                rendererProfile, true, breakingChangeConfirmed, actor);
    }

    private DesignCodeComponentMapping copyAsCandidate(
            DesignCodeComponentMapping target, String newVersion, String contentHash) {
        return new DesignCodeComponentMapping(
                target.mappingId(), newVersion, DesignCodeComponentMapping.Status.REVIEW_REQUIRED,
                contentHash, target.logicalType(), target.figmaComponentSetKey(),
                target.thymeleafFragment(), target.propertyMappings(), target.slotMappings(),
                target.fixtureModel(), target.supportedRendererProfiles(), target.sourceRevision(),
                null, null);
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "는 필수입니다.");
    }
}
