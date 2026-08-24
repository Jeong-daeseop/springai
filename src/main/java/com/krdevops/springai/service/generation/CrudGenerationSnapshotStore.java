package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;

import java.util.Optional;

/** CRUD 생성 Region Ownership의 Base(직전 Apply 성공 시점) 스냅샷 저장소. */
public interface CrudGenerationSnapshotStore {

    Optional<GenerationOwnershipManifest> findLatest(String operationId);

    /** {@code writePort.apply()}가 APPLIED를 반환한 직후에만 호출한다 — revision은 자동 +1. */
    void save(String operationId, GenerationOwnershipManifest manifest);
}
