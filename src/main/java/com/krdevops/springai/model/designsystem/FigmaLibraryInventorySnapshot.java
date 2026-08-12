package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.service.designsystem.FigmaPropertyDriftValidator.LibraryComponentSnapshot;

import java.time.Instant;
import java.util.Map;

/** Author Plugin 또는 Figma Library 수집기가 전달한 실제 Published Property Inventory. */
public record FigmaLibraryInventorySnapshot(
        String profileId,
        String registryVersion,
        String inventoryVersion,
        Instant capturedAt,
        Map<String, LibraryComponentSnapshot> components
) {
    public FigmaLibraryInventorySnapshot {
        if (profileId == null || profileId.isBlank()) throw new IllegalArgumentException("profileId는 필수입니다.");
        if (registryVersion == null || registryVersion.isBlank()) throw new IllegalArgumentException("registryVersion은 필수입니다.");
        if (inventoryVersion == null || inventoryVersion.isBlank()) throw new IllegalArgumentException("inventoryVersion은 필수입니다.");
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        components = components == null ? Map.of() : Map.copyOf(components);
    }
}
