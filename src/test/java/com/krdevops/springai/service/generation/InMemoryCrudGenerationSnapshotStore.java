package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 실 MySQL 없이 Ownership 시나리오를 테스트하기 위한 인메모리 {@link CrudGenerationSnapshotStore}. */
public class InMemoryCrudGenerationSnapshotStore implements CrudGenerationSnapshotStore {

    private final Map<String, GenerationOwnershipManifest> latestByOperationId = new LinkedHashMap<>();

    @Override
    public Optional<GenerationOwnershipManifest> findLatest(String operationId) {
        return Optional.ofNullable(latestByOperationId.get(operationId));
    }

    @Override
    public void save(String operationId, GenerationOwnershipManifest manifest) {
        latestByOperationId.put(operationId, manifest);
    }
}
