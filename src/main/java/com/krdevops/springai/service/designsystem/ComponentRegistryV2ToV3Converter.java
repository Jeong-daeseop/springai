package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** 기존 ComponentRegistry v2를 승인 전 Registry v3 Binding 후보로 결정론적으로 변환한다. */
@Service
public class ComponentRegistryV2ToV3Converter {

    private final ObjectMapper canonicalMapper;

    public ComponentRegistryV2ToV3Converter(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy().findAndRegisterModules()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public Conversion convert(ComponentRegistry legacy, ComponentCatalogLoader.LoadedCatalog loadedCatalog) {
        if (legacy == null || loadedCatalog == null) {
            throw new IllegalArgumentException("Legacy Registry와 Catalog는 필수입니다.");
        }
        ComponentCatalog catalog = loadedCatalog.catalog();
        Map<String, ComponentRegistrySnapshotV3.Binding> bindings = new TreeMap<>();
        Map<String, String> skipped = new TreeMap<>();
        legacy.components().forEach((logicalType, entry) -> {
            ComponentCatalog.Entry contract = catalog.components().get(logicalType);
            if (contract != null && !contract.atomicComponent()) {
                skipped.put(logicalType, "NON_ATOMIC_CATALOG_ENTRY");
                return;
            }
            bindings.put(logicalType, new ComponentRegistrySnapshotV3.Binding(
                    entry.componentSetKey(), entry.componentName(), entry.publishStatus(),
                    normalizeLifecycle(entry), entry.variants()));
        });

        Map<String, Object> variables = new TreeMap<>();
        variables.putAll(legacy.variables());
        String contentHash = contentHash(legacy, catalog.contractVersion(), loadedCatalog.contentHash(),
                bindings, variables);
        ComponentRegistrySnapshotV3 candidate = new ComponentRegistrySnapshotV3(
                ComponentRegistrySnapshotV3.SCHEMA_VERSION,
                legacy.profileId(), legacy.profileVersion(), legacy.registryVersion(),
                catalog.contractVersion(), loadedCatalog.contentHash(), legacy.library(),
                bindings, variables, "registry-v2:" + legacy.registryVersion(),
                null, null, contentHash);
        return new Conversion(candidate, Map.copyOf(skipped));
    }

    private ComponentRegistryEntry.LifecycleStatus normalizeLifecycle(ComponentRegistryEntry entry) {
        if (entry.lifecycleStatus() == ComponentRegistryEntry.LifecycleStatus.ACTIVE
                && entry.publishStatus() == ComponentRegistryEntry.PublishStatus.CURRENT) {
            return ComponentRegistryEntry.LifecycleStatus.CURRENT;
        }
        return entry.lifecycleStatus();
    }

    private String contentHash(ComponentRegistry legacy, String catalogVersion, String catalogHash,
            Map<String, ComponentRegistrySnapshotV3.Binding> bindings, Map<String, Object> variables) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", ComponentRegistrySnapshotV3.SCHEMA_VERSION);
            payload.put("profileId", legacy.profileId());
            payload.put("profileVersion", legacy.profileVersion());
            payload.put("registryVersion", legacy.registryVersion());
            payload.put("catalogVersion", catalogVersion);
            payload.put("catalogHash", catalogHash);
            payload.put("library", legacy.library());
            payload.put("bindings", bindings);
            payload.put("variables", variables);
            payload.put("sourceRevision", "registry-v2:" + legacy.registryVersion());
            byte[] bytes = canonicalMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Registry v3 content hash 계산 실패", e);
        }
    }

    public record Conversion(
            ComponentRegistrySnapshotV3 candidate,
            Map<String, String> skippedBindings
    ) {}
}
