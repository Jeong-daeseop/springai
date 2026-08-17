package com.krdevops.springai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySyncResult;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import com.krdevops.springai.service.designsystem.FigmaContractApprovalService;
import com.krdevops.springai.service.designsystem.KrdsRuntimeContractImportService;
import com.krdevops.springai.service.designsystem.ComponentRegistryMigrationService;
import com.krdevops.springai.service.designsystem.ComponentCatalogMigrationService;
import com.krdevops.springai.service.designsystem.ComponentCatalogV1ToV2Converter;
import com.krdevops.springai.service.designsystem.ComponentRegistryRollbackService;
import com.krdevops.springai.service.designsystem.ComponentRegistryOperationalValidationService;
import com.krdevops.springai.service.designsystem.ComponentRegistryObservationService;
import com.krdevops.springai.service.designsystem.ComponentRegistryDualReadService;
import com.krdevops.springai.service.designsystem.ComponentRegistryInventoryValidator;
import com.krdevops.springai.service.designsystem.ComponentCatalogLoader;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistryResolutionComparisonReport;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.service.figma.FigmaRollbackRehearsalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** R6-005~008: Profile·Registry·Preview Review API. */
@RestController
@RequestMapping("/api/design-systems")
@RequiredArgsConstructor
public class DesignSystemController {

    private final DesignSystemQueryService queryService;
    private final FigmaLibraryInventoryRepository inventoryRepository;
    private final FigmaContractApprovalService contractApprovalService;
    private final KrdsRuntimeContractImportService runtimeContractImportService;
    private final FigmaRollbackRehearsalService rollbackRehearsalService;
    private final ComponentRegistryMigrationService registryMigrationService;
    private final ComponentCatalogMigrationService catalogMigrationService;
    private final ComponentRegistryRollbackService registryRollbackService;
    private final ComponentRegistryOperationalValidationService operationalValidationService;
    private final ComponentRegistryObservationService observationService;
    private final ComponentRegistryDualReadService dualReadService;
    private final ComponentRegistrySnapshotV3Repository registryV3Repository;
    private final ComponentRegistryInventoryValidator inventoryValidator;
    private final ComponentCatalogLoader catalogLoader;
    private final ObjectMapper objectMapper;

    /** Legacy Catalog v1을 v2 후보로 변환하고 누락 합성 대상·계약 오류를 보여준다. */
    @PostMapping("/catalog/migrate-v2/preview")
    public ComponentCatalogV1ToV2Converter.Conversion previewCatalogV2Migration() {
        return catalogMigrationService.preview();
    }

    /** 기존 Registry v2를 저장하지 않고 v3 후보로 변환·교차검증한다. */
    @PostMapping("/{profileId}/registries/{registryVersion}/migrate-v3/preview")
    public ComponentRegistryMigrationService.MigrationPreview previewRegistryV3Migration(
            @PathVariable String profileId,
            @PathVariable String registryVersion,
            @RequestParam(defaultValue = "2.0.0") String catalogVersion) {
        return registryMigrationService.preview(profileId, registryVersion, catalogVersion);
    }

    /** Preview와 동일 후보를 사람 승인 후 Registry v3 불변 Snapshot으로 저장한다. */
    @PostMapping("/{profileId}/registries/{registryVersion}/migrate-v3/apply")
    public ComponentRegistrySnapshotV3 applyRegistryV3Migration(
            @PathVariable String profileId,
            @PathVariable String registryVersion,
            @RequestParam(defaultValue = "2.0.0") String catalogVersion,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @RequestParam(defaultValue = "false") boolean breakingChangeConfirmed,
            @RequestParam String actor) {
        return registryMigrationService.apply(
                profileId, registryVersion, catalogVersion, confirmed, breakingChangeConfirmed, actor);
    }

    /** 운영자가 대상 버전을 명시하고 확인한 경우에만 이전 승인 Snapshot을 연결한다. */
    @PostMapping("/{profileId}/registries/{registryVersion}/rollback-v3")
    public ComponentRegistrySnapshotV3 rollbackRegistryV3(
            @PathVariable String profileId,
            @PathVariable String registryVersion,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @RequestParam String actor) {
        return registryRollbackService.rollback(profileId, registryVersion, confirmed, actor);
    }

    /** 운영 중인 승인 Registry v3 Snapshot 전체를 Catalog·Hash 기준으로 일괄 검증한다. */
    @GetMapping("/registries/validate-v3")
    public ComponentRegistryOperationalValidationService.BatchResult validateAllRegistryV3() {
        return operationalValidationService.validateAll();
    }

    /** Legacy/v3 Resolver 관찰 비교를 실행하고 결과 Report를 저장한다. */
    @PostMapping("/registries/observe-v3")
    public ComponentRegistryResolutionComparisonReport observeRegistryV3(
            @RequestBody ObservationRequest request) {
        return observationService.compare(request.profileId(), request.legacyVersion(), request.resolvedVersion(),
                request.logicalTypes() == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(request.logicalTypes()));
    }

    /** Legacy 결과를 선택값으로 유지하면서 v3 Resolver를 병렬 실행하고 차이를 저장한다. */
    @PostMapping("/registries/dual-read-v3")
    public ComponentRegistryDualReadService.DualReadResult dualReadRegistryV3(
            @RequestBody ObservationRequest request) {
        return dualReadService.read(request.profileId(), request.legacyVersion(), request.resolvedVersion(),
                request.logicalTypes() == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(request.logicalTypes()));
    }

    /** Registry v3 Binding의 Published Component/Variant Key를 최신 Figma Inventory와 교차 검증한다. */
    @GetMapping("/{profileId}/registries/{registryVersion}/inventory/validate-v3")
    public InventoryValidationResponse validateRegistryV3Inventory(
            @PathVariable String profileId, @PathVariable String registryVersion) {
        var registry = registryV3Repository.findVersion(profileId, registryVersion)
                .orElseThrow(() -> new FigmaRequestException("REGISTRY_V3_NOT_FOUND", "Registry v3 Snapshot이 없습니다."));
        var inventory = inventoryRepository.findLatest(profileId, registryVersion)
                .orElseThrow(() -> new FigmaRequestException("FIGMA_INVENTORY_SNAPSHOT_MISSING", "Figma Inventory가 없습니다."));
        var issues = inventoryValidator.validate(registry, inventory,
                catalogLoader.load(registry.catalogVersion()).catalog());
        return new InventoryValidationResponse(profileId, registryVersion, issues.isEmpty(), issues);
    }

    /** 운영 KRDS Runtime Registry/Rule Set 후보를 DB에 불변 Snapshot으로 Import한다. */
    @PostMapping("/runtime-contracts/qna/import")
    public KrdsRuntimeContractImportService.ImportResult importQnaRuntimeContracts() {
        return runtimeContractImportService.importDefaultQnaRuleSet();
    }

    @PostMapping("/runtime-contracts/rule-set/import")
    public KrdsRuntimeContractImportService.ImportResult importRuleSet(
            @RequestBody VariantRuleSet ruleSet) {
        return runtimeContractImportService.importRuleSet(ruleSet);
    }

    @PostMapping("/rollback-rehearsals/preview")
    public FigmaRollbackRehearsalService.RehearsalResult previewRollback(
            @RequestBody FigmaRollbackRehearsalService.RehearsalRequest request) {
        return rollbackRehearsalService.preview(request);
    }

    @PostMapping("/patterns/{pattern}/versions/{version}/approve")
    public ScreenPatternDefinition approvePattern(@PathVariable ScreenPattern pattern, @PathVariable String version,
            @RequestBody ApprovalRequest request) {
        return contractApprovalService.approvePattern(pattern, version, request.actor(), request.comment());
    }

    @PostMapping("/patterns/{pattern}/versions/{version}/publish")
    public ScreenPatternDefinition publishPattern(@PathVariable ScreenPattern pattern, @PathVariable String version,
            @RequestBody ApprovalRequest request) {
        return contractApprovalService.publishPattern(pattern, version, request.actor(), request.comment());
    }

    @PostMapping("/rule-sets/{id}/versions/{version}/approve")
    public VariantRuleSet approveRuleSet(@PathVariable String id, @PathVariable String version,
            @RequestBody ApprovalRequest request) {
        return contractApprovalService.approveRuleSet(id, version, request.actor(), request.comment());
    }

    @PostMapping("/rule-sets/{id}/versions/{version}/publish")
    public VariantRuleSet publishRuleSet(@PathVariable String id, @PathVariable String version,
            @RequestBody ApprovalRequest request) {
        return contractApprovalService.publishRuleSet(id, version, request.actor(), request.comment());
    }

    /** Author Plugin/Figma 수집 결과를 불변 Inventory Snapshot으로 등록한다. */
    @PostMapping("/{profileId}/registries/{registryVersion}/inventory")
    public FigmaLibraryInventorySnapshot importInventory(
            @PathVariable String profileId,
            @PathVariable String registryVersion,
            @RequestBody FigmaLibraryInventorySnapshot snapshot
    ) {
        if (!profileId.equals(snapshot.profileId())
                || !registryVersion.equals(snapshot.registryVersion())) {
            throw new FigmaRequestException("FIGMA_INVENTORY_VERSION_MISMATCH",
                    "경로의 Profile/Registry 버전과 Inventory Snapshot이 일치하지 않습니다.");
        }
        inventoryRepository.saveImmutable(snapshot);
        return snapshot;
    }

    @GetMapping("/{profileId}/registries/{registryVersion}/inventory")
    public FigmaLibraryInventorySnapshot latestInventory(
            @PathVariable String profileId,
            @PathVariable String registryVersion
    ) {
        return inventoryRepository.findLatest(profileId, registryVersion)
                .orElseThrow(() -> new FigmaRequestException("FIGMA_INVENTORY_SNAPSHOT_MISSING",
                        "해당 Registry 버전의 Figma Inventory가 없습니다."));
    }

    @GetMapping("/{profileId}")
    public DesignSystemProfile latestProfile(@PathVariable String profileId) {
        try {
            return queryService.findLatestProfile(profileId);
        } catch (IllegalArgumentException exception) {
            throw notFound("DESIGN_SYSTEM_PROFILE_NOT_FOUND", exception);
        }
    }

    @GetMapping("/{profileId}/versions/{version}")
    public DesignSystemProfile profileVersion(@PathVariable String profileId, @PathVariable String version) {
        try {
            return queryService.findProfileVersion(profileId, version);
        } catch (IllegalArgumentException exception) {
            throw notFound("DESIGN_SYSTEM_PROFILE_NOT_FOUND", exception);
        }
    }

    @GetMapping("/{profileId}/registries")
    public ComponentRegistry latestRegistry(@PathVariable String profileId) {
        try {
            return queryService.findLatestRegistry(profileId);
        } catch (IllegalArgumentException exception) {
            throw notFound("COMPONENT_REGISTRY_NOT_FOUND", exception);
        }
    }

    @GetMapping("/{profileId}/registries/{version}")
    public ComponentRegistry registryVersion(@PathVariable String profileId, @PathVariable String version) {
        try {
            return queryService.findRegistryVersion(profileId, version);
        } catch (IllegalArgumentException exception) {
            throw notFound("COMPONENT_REGISTRY_NOT_FOUND", exception);
        }
    }

    @PostMapping("/{profileId}/registries/preview")
    public ComponentRegistrySyncResult previewRegistry(
            @PathVariable String profileId,
            @RequestBody String body
    ) {
        ComponentRegistry registry = parseRegistry(body);
        requireSameProfile(profileId, registry);
        try {
            return queryService.previewRegistry(registry);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("COMPONENT_REGISTRY_INVALID", exception.getMessage());
        }
    }

    @PostMapping("/{profileId}/registries/apply")
    public ComponentRegistrySyncResult applyRegistry(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String comment,
            @RequestBody String body
    ) {
        ComponentRegistry registry = parseRegistry(body);
        requireSameProfile(profileId, registry);
        try {
            return queryService.applyRegistry(registry, confirmed, actor, comment);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("COMPONENT_REGISTRY_INVALID", exception.getMessage());
        }
    }

    @PostMapping("/{profileId}/registries/retry")
    public ComponentRegistrySyncResult retryRegistry(
            @PathVariable String profileId,
            @RequestParam String retryToken,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @RequestBody String body
    ) {
        ComponentRegistry registry = parseRegistry(body);
        requireSameProfile(profileId, registry);
        try {
            return queryService.retryRegistry(registry, retryToken, confirmed);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("COMPONENT_REGISTRY_RETRY_INVALID", exception.getMessage());
        }
    }

    @PostMapping("/{profileId}/registries/preflight")
    public DesignSystemQueryService.RegistryPreflightResult preflightRegistry(
            @PathVariable String profileId,
            @RequestBody RegistryPreflightRequest request
    ) {
        try {
            return queryService.preflightRegistry(
                    profileId, request.registryVersion(), request.requiredLogicalTypes());
        } catch (IllegalArgumentException exception) {
            throw notFound("COMPONENT_REGISTRY_NOT_FOUND", exception);
        }
    }

    @PostMapping("/{profileId}/rollback")
    public DesignSystemProfile rollback(
            @PathVariable String profileId,
            @RequestParam String profileVersion,
            @RequestParam String registryVersion,
            @RequestParam(defaultValue = "false") boolean confirmed
    ) {
        try {
            return queryService.rollbackProfile(
                    profileId, profileVersion, registryVersion, confirmed);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("DESIGN_SYSTEM_ROLLBACK_INVALID", exception.getMessage());
        }
    }

    @PostMapping("/reviews")
    public FigmaReviewEvent saveReview(@RequestBody FigmaReviewEvent event) {
        return queryService.saveReview(event);
    }

    @GetMapping("/reviews/{targetType}/{targetId}/{targetVersion}")
    public List<FigmaReviewEvent> reviews(
            @PathVariable FigmaReviewEvent.TargetType targetType,
            @PathVariable String targetId,
            @PathVariable String targetVersion
    ) {
        return queryService.findReviews(targetType, targetId, targetVersion);
    }

    public record RegistryPreflightRequest(
            String registryVersion,
            List<String> requiredLogicalTypes
    ) {}

    public record ApprovalRequest(String actor, String comment) {}

    public record ObservationRequest(String profileId, String legacyVersion,
                                     String resolvedVersion, List<String> logicalTypes) {}

    public record InventoryValidationResponse(String profileId, String registryVersion,
                                               boolean valid, List<com.krdevops.springai.model.designsystem.DesignSystemIssue> issues) {}

    private void requireSameProfile(String profileId, ComponentRegistry registry) {
        if (!profileId.equals(registry.profileId())) {
            throw new FigmaRequestException(
                    "COMPONENT_REGISTRY_PROFILE_MISMATCH",
                    "Path profileId와 Registry profileId가 다릅니다.");
        }
    }

    private ComponentRegistry parseRegistry(String body) {
        try {
            return objectMapper.readValue(body, ComponentRegistry.class);
        } catch (Exception exception) {
            throw new FigmaRequestException(
                    "COMPONENT_REGISTRY_JSON_INVALID",
                    "Registry JSON을 읽을 수 없습니다: " + exception.getMessage());
        }
    }

    private FigmaResourceNotFoundException notFound(String code, IllegalArgumentException exception) {
        return new FigmaResourceNotFoundException(code, exception.getMessage());
    }
}
