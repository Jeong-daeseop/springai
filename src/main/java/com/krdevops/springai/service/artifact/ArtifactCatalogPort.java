package com.krdevops.springai.service.artifact;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;

import java.util.List;
import java.util.Optional;

/**
 * ARCH-0503/0506: Artifact 메타데이터와 Operation↔Artifact 연결을 담당한다.
 * {@link #save(Artifact)}는 contentHash 기준 멱등이다 — 동일 내용은 항상 같은 레코드를 반환한다.
 */
public interface ArtifactCatalogPort {

    Artifact save(Artifact artifact);

    Optional<Artifact> findByContentHash(String contentHash);

    Optional<Artifact> findById(String artifactId);

    List<Artifact> findAll();

    void linkToOperation(String operationId, String operationType, String artifactId);

    List<Artifact> findByOperation(String operationId);

    void updateStatus(String artifactId, ArtifactStatus status);
}
