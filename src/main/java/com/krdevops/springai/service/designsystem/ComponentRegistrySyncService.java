package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.designsystem.ComponentBinding;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryDiff;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySyncResult;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableBinding;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R4: Author Plugin이 내보낸 Published Registry 후보를 검증하고, 이전 버전과 비교한 뒤
 * 사람의 명시적 확인을 거쳐 불변 Snapshot으로 저장한다.
 */
@Service
public class ComponentRegistrySyncService {

    private final ComponentRegistryRepository registryRepository;
    private final DesignSystemProfileRepository profileRepository;
    private final ComponentRegistryValidator validator;
    private final FigmaLibraryInventoryRepository inventoryRepository;
    private final FigmaPropertyDriftValidator driftValidator;
    private final FigmaReviewHistoryRepository reviewRepository;

    public ComponentRegistrySyncService(
            ComponentRegistryRepository registryRepository,
            DesignSystemProfileRepository profileRepository,
            ComponentRegistryValidator validator
    ) {
        this(registryRepository, profileRepository, validator, null, null, null);
    }

    @Autowired
    public ComponentRegistrySyncService(
            ComponentRegistryRepository registryRepository,
            DesignSystemProfileRepository profileRepository,
            ComponentRegistryValidator validator,
            FigmaLibraryInventoryRepository inventoryRepository,
            FigmaPropertyDriftValidator driftValidator,
            FigmaReviewHistoryRepository reviewRepository
    ) {
        this.registryRepository = registryRepository;
        this.profileRepository = profileRepository;
        this.validator = validator;
        this.inventoryRepository = inventoryRepository;
        this.driftValidator = driftValidator;
        this.reviewRepository = reviewRepository;
    }

    public ComponentRegistrySyncResult preview(ComponentRegistry candidate) {
        List<DesignSystemIssue> issues = new ArrayList<>(validator.validatePublished(candidate));
        DesignSystemProfile profile = profileRepository
                .findVersion(candidate.profileId(), candidate.profileVersion())
                .orElse(null);
        validateProfile(candidate, profile, issues);

        ComponentRegistry previous = registryRepository.findLatest(candidate.profileId()).orElse(null);
        issues.addAll(validator.validateNewEntryLifecycle(candidate, previous));
        validateActualFigmaInventory(candidate, issues);
        List<ComponentRegistryDiff.Change> changes = calculateChanges(previous, candidate, issues);
        boolean valid = issues.stream().noneMatch(issue ->
                issue.severity() == DesignSystemIssue.Severity.FATAL
                        || issue.severity() == DesignSystemIssue.Severity.ERROR);
        ComponentRegistryDiff diff = new ComponentRegistryDiff(
                candidate.profileId(),
                candidate.registryVersion(),
                previous == null ? null : previous.registryVersion(),
                valid,
                issues,
                changes);
        return new ComponentRegistrySyncResult(
                ComponentRegistrySyncResult.Status.PREVIEW, diff, candidate, profile);
    }

    /** KRV-065: Registry 승인 시 계약 JSON뿐 아니라 실제 Published Figma Inventory의 State Variant도 검사한다. */
    private void validateActualFigmaInventory(ComponentRegistry candidate, List<DesignSystemIssue> issues) {
        if (inventoryRepository == null || driftValidator == null) return; // 단위 테스트용 레거시 생성자
        var inventory = inventoryRepository.findLatest(candidate.profileId(), candidate.registryVersion()).orElse(null);
        if (inventory == null) {
            issues.add(issue("FIGMA_INVENTORY_SNAPSHOT_MISSING",
                    "Registry 승인 전에 실제 Figma Library Inventory Snapshot이 필요합니다.", candidate.registryVersion()));
            return;
        }
        candidate.components().forEach((logicalType, contract) -> {
            var actual = inventory.components().get(logicalType);
            issues.addAll(driftValidator.validate(logicalType, contract, actual));
            contract.variantAxes().values().stream()
                    .filter(axis -> "state".equalsIgnoreCase(axis.logicalName()))
                    .findFirst().ifPresent(axis -> {
                        var property = actual == null ? null : actual.properties().get(axis.figmaProperty());
                        Set<String> values = property == null ? Set.of() : property.values().stream()
                                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                                .collect(java.util.stream.Collectors.toSet());
                        if (!values.contains("focus")) {
                            issues.add(issue("ACCESSIBILITY_FOCUS_STATE_MISSING",
                                    logicalType + "에 Focus State Variant가 없습니다.", logicalType));
                        }
                        if (!values.contains("disabled")) {
                            issues.add(issue("ACCESSIBILITY_DISABLED_STATE_MISSING",
                                    logicalType + "에 Disabled State Variant가 없습니다.", logicalType));
                        }
                        boolean field = contract.roles().stream().anyMatch(role -> role.code().startsWith("field."));
                        if (field && !values.contains("error")) {
                            issues.add(issue("ACCESSIBILITY_ERROR_STATE_MISSING",
                                    logicalType + "에 Error State Variant가 없습니다.", logicalType));
                        }
                        if (field && !(values.contains("readonly") || values.contains("read-only") || values.contains("view"))) {
                            issues.add(issue("ACCESSIBILITY_READ_ONLY_STATE_MISSING",
                                    logicalType + "에 Read-only State Variant가 없습니다.", logicalType));
                        }
                    });
        });
    }

    @Transactional
    public ComponentRegistrySyncResult apply(ComponentRegistry candidate, boolean confirmed) {
        return apply(candidate, confirmed, null, null);
    }

    @Transactional
    public ComponentRegistrySyncResult apply(
            ComponentRegistry candidate, boolean confirmed, String actor, String comment) {
        if (!confirmed) {
            throw new IllegalArgumentException("Registry 반영에는 사람의 명시적 확인이 필요합니다.");
        }
        ComponentRegistrySyncResult preview = preview(candidate);
        if (!preview.diff().valid()) {
            return new ComponentRegistrySyncResult(
                    ComponentRegistrySyncResult.Status.REJECTED,
                    preview.diff(),
                    candidate,
                    preview.profile());
        }

        ComponentRegistry existingVersion = registryRepository
                .findVersion(candidate.profileId(), candidate.registryVersion())
                .orElse(null);
        if (existingVersion != null && !existingVersion.equals(candidate)) {
            List<DesignSystemIssue> issues = new ArrayList<>(preview.diff().issues());
            issues.add(issue("REGISTRY_VERSION_CONFLICT",
                    "같은 registryVersion에 다른 내용이 이미 저장되어 있습니다.", candidate.registryVersion()));
            ComponentRegistryDiff conflictDiff = new ComponentRegistryDiff(
                    preview.diff().profileId(),
                    preview.diff().candidateVersion(),
                    preview.diff().previousVersion(),
                    false,
                    issues,
                    preview.diff().changes());
            return new ComponentRegistrySyncResult(
                    ComponentRegistrySyncResult.Status.REJECTED,
                    conflictDiff,
                    candidate,
                    preview.profile());
        }

        DesignSystemProfile publishedProfile = toPublishedProfile(preview.profile(), candidate);
        if (existingVersion == null) {
            registryRepository.saveImmutable(candidate);
        }
        profileRepository.save(publishedProfile);
        recordRegistryApproval(candidate, actor, comment);
        return new ComponentRegistrySyncResult(
                ComponentRegistrySyncResult.Status.APPLIED,
                preview.diff(),
                candidate,
                publishedProfile);
    }

    private void recordRegistryApproval(ComponentRegistry candidate, String actor, String comment) {
        if (reviewRepository == null) return; // 레거시 단위 테스트 생성자
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Registry 승인에는 Design System Owner actor가 필요합니다.");
        }
        reviewRepository.save(new FigmaReviewEvent(
                UUID.randomUUID().toString(), FigmaReviewEvent.TargetType.COMPONENT_REGISTRY,
                candidate.profileId(), candidate.registryVersion(), FigmaReviewEvent.EventType.APPROVAL,
                "APPROVED", actor, comment, LocalDateTime.now()));
        reviewRepository.save(new FigmaReviewEvent(
                UUID.randomUUID().toString(), FigmaReviewEvent.TargetType.COMPONENT_REGISTRY,
                candidate.profileId(), candidate.registryVersion(), FigmaReviewEvent.EventType.REGISTRY_SYNC,
                "APPLIED", actor, comment, LocalDateTime.now()));
    }

    /** 실패 보고의 retryToken과 동일 후보를 다시 검증한 뒤 원자적으로 재적용한다. */
    @Transactional
    public ComponentRegistrySyncResult retry(ComponentRegistry candidate, String retryToken, boolean confirmed) {
        String expectedToken = candidate.profileId() + ":" + candidate.registryVersion();
        if (retryToken == null || !expectedToken.equals(retryToken)) {
            throw new IllegalArgumentException("Registry 재시도 토큰이 후보 버전과 일치하지 않습니다.");
        }
        return apply(candidate, confirmed);
    }

    @Transactional
    public DesignSystemProfile rollbackProfile(
            String profileId,
            String profileVersion,
            String registryVersion,
            boolean confirmed
    ) {
        if (!confirmed) {
            throw new IllegalArgumentException("Registry 롤백에는 사람의 명시적 확인이 필요합니다.");
        }
        ComponentRegistry registry = registryRepository.findVersion(profileId, registryVersion)
                .orElseThrow(() -> new IllegalArgumentException("Registry 버전을 찾을 수 없습니다: " + registryVersion));
        DesignSystemProfile profile = profileRepository.findVersion(profileId, profileVersion)
                .orElseThrow(() -> new IllegalArgumentException("DesignSystemProfile을 찾을 수 없습니다: " + profileVersion));
        DesignSystemProfile rolledBack = toPublishedProfile(profile, registry);
        profileRepository.save(rolledBack);
        return rolledBack;
    }

    private void validateProfile(
            ComponentRegistry candidate,
            DesignSystemProfile profile,
            List<DesignSystemIssue> issues
    ) {
        if (profile == null) {
            issues.add(issue("PROFILE_NOT_FOUND", "Registry와 연결할 DesignSystemProfile이 없습니다.", candidate.profileId()));
            return;
        }
        if (profile.status() != DesignSystemProfile.Status.APPROVED
                && profile.status() != DesignSystemProfile.Status.PUBLISHED) {
            issues.add(issue("PROFILE_NOT_APPROVED", "승인된 Profile만 Published Registry와 연결할 수 있습니다.", profile.id()));
        }
        if (candidate.library() == null || candidate.library().fileKey() == null
                || candidate.library().fileKey().isBlank()) {
            issues.add(issue("LIBRARY_FILE_KEY_MISSING", "Published Library fileKey가 필요합니다.", candidate.profileId()));
        } else if (profile.libraryFileKey() != null
                && !profile.libraryFileKey().equals(candidate.library().fileKey())) {
            issues.add(issue("LIBRARY_FILE_KEY_MISMATCH", "Profile과 Registry의 Library fileKey가 다릅니다.", profile.id()));
        }
    }

    private List<ComponentRegistryDiff.Change> calculateChanges(
            ComponentRegistry previous,
            ComponentRegistry candidate,
            List<DesignSystemIssue> issues
    ) {
        List<ComponentRegistryDiff.Change> changes = new ArrayList<>();
        Map<String, String> oldComponents = new LinkedHashMap<>();
        Map<String, String> oldVariables = new LinkedHashMap<>();
        if (previous != null) {
            previous.components().forEach((id, entry) -> oldComponents.put(id, entry.componentSetKey()));
            previous.variables().forEach((id, entry) -> oldVariables.put(id, entry.variableKey()));
            if (previous.library() != null && candidate.library() != null
                    && !java.util.Objects.equals(previous.library().fileKey(), candidate.library().fileKey())) {
                issues.add(issue("LIBRARY_FILE_KEY_CHANGED",
                        "Library fileKey가 변경되었습니다. 파일 이동 또는 Library 교체를 명시적으로 검토해야 합니다.",
                        candidate.profileId()));
            }
            detectComponentDrift(previous, candidate, issues);
        }
        diffAssets(ComponentRegistryDiff.AssetType.COMPONENT, oldComponents,
                candidate.components().entrySet().stream().collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().componentSetKey()),
                        LinkedHashMap::putAll),
                changes, issues);
        diffAssets(ComponentRegistryDiff.AssetType.VARIABLE, oldVariables,
                candidate.variables().entrySet().stream().collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().variableKey()),
                        LinkedHashMap::putAll),
                changes, issues);
        return changes;
    }

    private void detectComponentDrift(
            ComponentRegistry previous,
            ComponentRegistry candidate,
            List<DesignSystemIssue> issues
    ) {
        candidate.components().forEach((logicalType, current) -> {
            ComponentRegistryEntry before = previous.components().get(logicalType);
            if (before == null) return;
            if (!java.util.Objects.equals(before.componentName(), current.componentName())) {
                issues.add(new DesignSystemIssue(
                        "COMPONENT_NAME_CHANGED",
                        DesignSystemIssue.Severity.WARNING,
                        logicalType + "의 Figma 컴포넌트 이름이 변경되었습니다.",
                        logicalType));
            }
            before.properties().forEach((propertyName, oldMapping) -> {
                ComponentRegistryEntry.PropertyMapping newMapping = current.properties().get(propertyName);
                if (newMapping == null) {
                    issues.add(issue("COMPONENT_PROPERTY_REMOVED",
                            logicalType + "." + propertyName + " 속성이 제거되었습니다.", logicalType));
                    return;
                }
                if (oldMapping.type() != newMapping.type()) {
                    issues.add(issue("COMPONENT_PROPERTY_TYPE_CHANGED",
                            logicalType + "." + propertyName + " 속성 타입이 변경되었습니다.", logicalType));
                }
                if (oldMapping.type() == ComponentRegistryEntry.PropertyType.VARIANT
                        && !newMapping.values().keySet().containsAll(oldMapping.values().keySet())) {
                    issues.add(issue("COMPONENT_VARIANT_VALUE_REMOVED",
                            logicalType + "." + propertyName + "의 기존 Variant 값이 제거되었습니다.", logicalType));
                }
            });
        });
    }

    private void diffAssets(
            ComponentRegistryDiff.AssetType type,
            Map<String, String> previous,
            Map<String, String> candidate,
            List<ComponentRegistryDiff.Change> changes,
            List<DesignSystemIssue> issues
    ) {
        candidate.forEach((logicalId, candidateKey) -> {
            String previousKey = previous.remove(logicalId);
            ComponentRegistryDiff.ChangeType changeType;
            if (previousKey == null) {
                changeType = ComponentRegistryDiff.ChangeType.ADD;
            } else if (previousKey.equals(candidateKey)) {
                changeType = ComponentRegistryDiff.ChangeType.NO_CHANGE;
            } else {
                changeType = ComponentRegistryDiff.ChangeType.UPDATE;
                issues.add(issue(
                        type == ComponentRegistryDiff.AssetType.COMPONENT
                                ? "COMPONENT_KEY_CHANGED" : "VARIABLE_KEY_CHANGED",
                        logicalId + "의 Published Key가 변경되었습니다. Migration 승인 전에는 반영할 수 없습니다.",
                        logicalId));
            }
            changes.add(new ComponentRegistryDiff.Change(logicalId, type, changeType, previousKey, candidateKey));
        });
        previous.forEach((logicalId, previousKey) ->
                changes.add(new ComponentRegistryDiff.Change(
                        logicalId, type, ComponentRegistryDiff.ChangeType.DEPRECATE, previousKey, null)));
    }

    private DesignSystemProfile toPublishedProfile(
            DesignSystemProfile profile,
            ComponentRegistry registry
    ) {
        Map<String, ComponentBinding> components = new LinkedHashMap<>();
        registry.components().forEach((logicalId, entry) -> components.put(
                logicalId,
                new ComponentBinding(entry.componentSetKey(), ComponentBinding.BindingStatus.BOUND)));
        Map<String, VariableBinding> variables = new LinkedHashMap<>();
        registry.variables().forEach((logicalId, entry) -> variables.put(
                logicalId,
                new VariableBinding(entry.variableKey(), entry.collectionName(), ComponentBinding.BindingStatus.BOUND)));
        return new DesignSystemProfile(
                profile.id(),
                profile.name(),
                profile.version(),
                registry.registryVersion(),
                registry.library().fileKey(),
                DesignSystemProfile.Status.PUBLISHED,
                components,
                variables);
    }

    private DesignSystemIssue issue(String code, String message, String targetId) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, targetId);
    }
}
