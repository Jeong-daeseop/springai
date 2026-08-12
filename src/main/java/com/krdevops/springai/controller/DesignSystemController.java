package com.krdevops.springai.controller;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySyncResult;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
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
            @RequestBody ComponentRegistry registry
    ) {
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
            @RequestBody ComponentRegistry registry
    ) {
        requireSameProfile(profileId, registry);
        try {
            return queryService.applyRegistry(registry, confirmed);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("COMPONENT_REGISTRY_INVALID", exception.getMessage());
        }
    }

    @PostMapping("/{profileId}/registries/retry")
    public ComponentRegistrySyncResult retryRegistry(
            @PathVariable String profileId,
            @RequestParam String retryToken,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @RequestBody ComponentRegistry registry
    ) {
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

    private void requireSameProfile(String profileId, ComponentRegistry registry) {
        if (!profileId.equals(registry.profileId())) {
            throw new FigmaRequestException(
                    "COMPONENT_REGISTRY_PROFILE_MISMATCH",
                    "Path profileId와 Registry profileId가 다릅니다.");
        }
    }

    private FigmaResourceNotFoundException notFound(String code, IllegalArgumentException exception) {
        return new FigmaResourceNotFoundException(code, exception.getMessage());
    }
}
