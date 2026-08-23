package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Preview Gate를 통과한 Mapping 후보를 사람 확인 후 불변 승인 Version으로 저장한다. */
@Service
public class DesignCodeComponentMappingApprovalService {

    private final DesignCodeComponentMappingRepository repository;
    private final DesignCodeComponentMappingPreviewService previewService;
    private final DesignCodeComponentMappingHashService hashService;
    private final Clock clock;

    @Autowired
    public DesignCodeComponentMappingApprovalService(
            DesignCodeComponentMappingRepository repository,
            DesignCodeComponentMappingPreviewService previewService,
            DesignCodeComponentMappingHashService hashService) {
        this(repository, previewService, hashService, Clock.systemUTC());
    }

    DesignCodeComponentMappingApprovalService(
            DesignCodeComponentMappingRepository repository,
            DesignCodeComponentMappingPreviewService previewService,
            DesignCodeComponentMappingHashService hashService,
            Clock clock) {
        this.repository = repository;
        this.previewService = previewService;
        this.hashService = hashService;
        this.clock = clock;
    }

    @Transactional
    public DesignCodeComponentMapping approve(
            Path projectRoot,
            ComponentCatalog catalog,
            String catalogHash,
            ComponentRegistrySnapshotV3 registry,
            DesignCodeComponentMapping candidate,
            String rendererProfile,
            boolean confirmed,
            boolean breakingChangeConfirmed,
            String actor) {
        if (!confirmed) throw new IllegalArgumentException("Mapping 승인에는 사람의 명시적 확인이 필요합니다.");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Mapping 승인 actor는 필수입니다.");
        if (candidate == null) throw new IllegalArgumentException("candidate Mapping은 필수입니다.");
        if (candidate.status() != DesignCodeComponentMapping.Status.DRAFT
                && candidate.status() != DesignCodeComponentMapping.Status.REVIEW_REQUIRED) {
            throw new IllegalArgumentException("DRAFT 또는 REVIEW_REQUIRED Mapping만 승인할 수 있습니다.");
        }
        String calculatedHash = hashService.compute(candidate);
        if (!calculatedHash.equals(candidate.contentHash())) {
            throw new MappingApprovalRejectedException("MAPPING_CONTENT_HASH_MISMATCH", List.of());
        }

        DesignCodeComponentMapping existing = repository
                .findVersion(candidate.mappingId(), candidate.version()).orElse(null);
        if (existing != null) {
            if (existing.status() == DesignCodeComponentMapping.Status.APPROVED
                    && samePayload(existing, candidate)) return existing;
            throw new IllegalStateException("MAPPING_VERSION_CONFLICT: 같은 버전에 다른 Mapping이 있습니다.");
        }

        DesignCodeComponentMappingPreviewService.Preview preview = previewService.preview(
                projectRoot, catalog, catalogHash, registry, candidate, rendererProfile);
        if (!preview.approvalReady()) {
            throw new MappingApprovalRejectedException("MAPPING_PREVIEW_NOT_APPROVAL_READY", preview.issues());
        }
        if (preview.diff().hasBreakingChanges() && !breakingChangeConfirmed) {
            throw new MappingApprovalRejectedException(
                    "MAPPING_BREAKING_CHANGE_REQUIRES_APPROVAL", preview.issues());
        }

        DesignCodeComponentMapping approved = new DesignCodeComponentMapping(
                candidate.mappingId(), candidate.version(), DesignCodeComponentMapping.Status.APPROVED,
                candidate.contentHash(), candidate.logicalType(), candidate.figmaComponentSetKey(),
                candidate.thymeleafFragment(), candidate.propertyMappings(), candidate.slotMappings(),
                candidate.fixtureModel(), candidate.supportedRendererProfiles(), candidate.sourceRevision(),
                actor.trim(), Instant.now(clock));
        repository.saveImmutable(approved);
        return approved;
    }

    private boolean samePayload(
            DesignCodeComponentMapping existing,
            DesignCodeComponentMapping candidate) {
        return Objects.equals(existing.mappingId(), candidate.mappingId())
                && Objects.equals(existing.version(), candidate.version())
                && Objects.equals(existing.contentHash(), candidate.contentHash())
                && Objects.equals(existing.logicalType(), candidate.logicalType())
                && Objects.equals(existing.figmaComponentSetKey(), candidate.figmaComponentSetKey())
                && Objects.equals(existing.thymeleafFragment(), candidate.thymeleafFragment())
                && Objects.equals(existing.propertyMappings(), candidate.propertyMappings())
                && Objects.equals(existing.slotMappings(), candidate.slotMappings())
                && Objects.equals(existing.fixtureModel(), candidate.fixtureModel())
                && Objects.equals(existing.supportedRendererProfiles(), candidate.supportedRendererProfiles())
                && Objects.equals(existing.sourceRevision(), candidate.sourceRevision());
    }

    public static final class MappingApprovalRejectedException extends IllegalStateException {
        private final String errorCode;
        private final List<DesignCodeComponentMappingPreviewService.PreviewIssue> issues;

        public MappingApprovalRejectedException(
                String errorCode,
                List<DesignCodeComponentMappingPreviewService.PreviewIssue> issues) {
            super(errorCode);
            this.errorCode = errorCode;
            this.issues = List.copyOf(issues);
        }

        public String errorCode() {
            return errorCode;
        }

        public List<DesignCodeComponentMappingPreviewService.PreviewIssue> issues() {
            return issues;
        }
    }
}
