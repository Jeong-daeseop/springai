package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry v3 후보를 Catalog와 검증하고 사람 승인 후 불변 Snapshot으로 저장한다. */
@Service
public class ComponentRegistrySnapshotV3SyncService {

    private final ComponentCatalogLoader catalogLoader;
    private final ComponentRegistryBindingValidator validator;
    private final ComponentRegistrySnapshotV3Repository repository;
    private final ObjectMapper canonicalMapper;
    private final ComponentRegistryBreakingChangeAnalyzer breakingChangeAnalyzer = new ComponentRegistryBreakingChangeAnalyzer();

    @Autowired
    public ComponentRegistrySnapshotV3SyncService(ComponentCatalogLoader catalogLoader,
            ComponentRegistryBindingValidator validator,
            ComponentRegistrySnapshotV3Repository repository) {
        this(catalogLoader, validator, repository, new ObjectMapper().findAndRegisterModules());
    }

    public ComponentRegistrySnapshotV3SyncService(ComponentCatalogLoader catalogLoader,
            ComponentRegistryBindingValidator validator,
            ComponentRegistrySnapshotV3Repository repository,
            ObjectMapper objectMapper) {
        this.catalogLoader = catalogLoader;
        this.validator = validator;
        this.repository = repository;
        this.canonicalMapper = objectMapper.copy().findAndRegisterModules()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public Preview preview(ComponentRegistrySnapshotV3 candidate) {
        var loaded = catalogLoader.load(candidate.catalogVersion());
        List<DesignSystemIssue> issues = validator.validate(
                loaded.catalog(), loaded.contentHash(), candidate, false);
        return new Preview(!blocking(issues), issues, loaded.contentHash());
    }

    @Transactional
    public ComponentRegistrySnapshotV3 apply(
            ComponentRegistrySnapshotV3 candidate, boolean confirmed, String actor) {
        return apply(candidate, confirmed, false, actor);
    }

    public ComponentRegistrySnapshotV3 apply(
            ComponentRegistrySnapshotV3 candidate, boolean confirmed,
            boolean breakingChangeConfirmed, String actor) {
        if (!confirmed) throw new IllegalArgumentException("Registry 반영에는 사람의 명시적 확인이 필요합니다.");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Registry 승인 actor는 필수입니다.");
        Preview preview = preview(candidate);
        if (!preview.valid()) throw new RegistryV3RejectedException(preview.issues());
        String calculatedHash = computeContentHash(candidate);
        if (!calculatedHash.equals(candidate.contentHash())) {
            throw new RegistryV3RejectedException(List.of(new DesignSystemIssue(
                    "REGISTRY_CONTENT_HASH_MISMATCH", DesignSystemIssue.Severity.ERROR,
                    "Registry Snapshot 내용 Hash가 실제 Binding 내용과 다릅니다.", candidate.registryVersion())));
        }

        ComponentRegistrySnapshotV3 previous = repository.findLatestApproved(candidate.profileId()).orElse(null);
        if (previous != null && !previous.registryVersion().equals(candidate.registryVersion())) {
            var breakingChanges = breakingChangeAnalyzer.analyzeRegistryV3(previous, candidate);
            if (!breakingChanges.isEmpty() && !breakingChangeConfirmed) {
                throw new RegistryV3RejectedException(List.of(new DesignSystemIssue(
                        "REGISTRY_BREAKING_CHANGE_REQUIRES_APPROVAL", DesignSystemIssue.Severity.ERROR,
                        "Published Component/Variant Key Breaking Change는 별도 승인이 필요합니다.",
                        candidate.registryVersion())));
            }
        }

        ComponentRegistrySnapshotV3 existing = repository
                .findVersion(candidate.profileId(), candidate.registryVersion()).orElse(null);
        if (existing != null) {
            if (sameCandidate(existing, candidate, preview.catalogHash())) return existing;
            throw new IllegalStateException("REGISTRY_VERSION_CONFLICT: 같은 버전에 다른 Snapshot이 있습니다.");
        }

        ComponentRegistrySnapshotV3 approved = new ComponentRegistrySnapshotV3(
                candidate.schemaVersion(), candidate.profileId(), candidate.profileVersion(),
                candidate.registryVersion(), candidate.catalogVersion(), preview.catalogHash(),
                candidate.library(), candidate.bindings(), candidate.variables(), candidate.sourceRevision(),
                actor, Instant.now(), candidate.contentHash());
        List<DesignSystemIssue> approvalIssues = validator.validate(
                catalogLoader.load(approved.catalogVersion()).catalog(), preview.catalogHash(), approved, true);
        if (blocking(approvalIssues)) throw new RegistryV3RejectedException(approvalIssues);

        repository.saveImmutable(approved);
        return approved;
    }

    private boolean sameCandidate(ComponentRegistrySnapshotV3 existing,
            ComponentRegistrySnapshotV3 candidate, String catalogHash) {
        return java.util.Objects.equals(existing.schemaVersion(), candidate.schemaVersion())
                && java.util.Objects.equals(existing.profileId(), candidate.profileId())
                && java.util.Objects.equals(existing.profileVersion(), candidate.profileVersion())
                && java.util.Objects.equals(existing.registryVersion(), candidate.registryVersion())
                && java.util.Objects.equals(existing.catalogVersion(), candidate.catalogVersion())
                && java.util.Objects.equals(existing.catalogHash(), catalogHash)
                && java.util.Objects.equals(existing.library(), candidate.library())
                && java.util.Objects.equals(existing.bindings(), candidate.bindings())
                && java.util.Objects.equals(existing.variables(), candidate.variables())
                && java.util.Objects.equals(existing.sourceRevision(), candidate.sourceRevision())
                && java.util.Objects.equals(existing.contentHash(), candidate.contentHash());
    }

    private boolean blocking(List<DesignSystemIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                || issue.severity() == DesignSystemIssue.Severity.FATAL);
    }

    /** 승인 메타데이터를 제외한 Published Binding payload의 결정론적 SHA-256. */
    public String computeContentHash(ComponentRegistrySnapshotV3 candidate) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", candidate.schemaVersion());
            payload.put("profileId", candidate.profileId());
            payload.put("profileVersion", candidate.profileVersion());
            payload.put("registryVersion", candidate.registryVersion());
            payload.put("catalogVersion", candidate.catalogVersion());
            payload.put("catalogHash", candidate.catalogHash());
            payload.put("library", candidate.library());
            payload.put("bindings", candidate.bindings());
            payload.put("variables", candidate.variables());
            payload.put("sourceRevision", candidate.sourceRevision());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalMapper.writeValueAsBytes(payload)));
        } catch (Exception exception) {
            throw new IllegalStateException("Registry v3 content hash 검증에 실패했습니다.", exception);
        }
    }

    public record Preview(boolean valid, List<DesignSystemIssue> issues, String catalogHash) {
        public Preview {
            issues = List.copyOf(issues);
        }
    }

    public static class RegistryV3RejectedException extends IllegalArgumentException {
        private final List<DesignSystemIssue> issues;
        public RegistryV3RejectedException(List<DesignSystemIssue> issues) {
            super("Registry v3 후보가 Catalog 계약을 통과하지 못했습니다.");
            this.issues = List.copyOf(issues);
        }
        public List<DesignSystemIssue> issues() { return issues; }
    }
}
