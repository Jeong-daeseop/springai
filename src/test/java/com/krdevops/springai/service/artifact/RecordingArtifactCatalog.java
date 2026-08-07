package com.krdevops.springai.service.artifact;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** DB 없이 {@link ArtifactService}의 ingest+link 결과를 그대로 들여다보기 위한 테스트용 catalog. */
public final class RecordingArtifactCatalog implements ArtifactCatalogPort {

    private final List<Artifact> rows = new ArrayList<>();
    private final Map<String, List<String>> links = new LinkedHashMap<>();

    @Override
    public Artifact save(Artifact artifact) {
        return findByContentHash(artifact.contentHash()).orElseGet(() -> {
            rows.add(artifact);
            return artifact;
        });
    }

    @Override
    public Optional<Artifact> findByContentHash(String contentHash) {
        return rows.stream().filter(row -> row.contentHash().equals(contentHash)).findFirst();
    }

    @Override
    public Optional<Artifact> findById(String artifactId) {
        return rows.stream().filter(row -> row.artifactId().equals(artifactId)).findFirst();
    }

    @Override
    public List<Artifact> findAll() {
        return List.copyOf(rows);
    }

    @Override
    public void linkToOperation(String operationId, String operationType, String artifactId) {
        links.computeIfAbsent(operationId, key -> new ArrayList<>()).add(artifactId);
    }

    @Override
    public List<Artifact> findByOperation(String operationId) {
        List<String> linked = links.getOrDefault(operationId, List.of());
        return rows.stream().filter(row -> linked.contains(row.artifactId())).toList();
    }

    @Override
    public void updateStatus(String artifactId, ArtifactStatus status) {
        for (int index = 0; index < rows.size(); index++) {
            Artifact row = rows.get(index);
            if (row.artifactId().equals(artifactId)) {
                rows.set(index, new Artifact(row.artifactId(), row.artifactType(), row.mediaType(),
                        row.sizeBytes(), row.contentHash(), row.sourceRevision(), row.storageUri(),
                        status, row.createdAt()));
            }
        }
    }

    public List<Artifact> ofType(String artifactType) {
        return rows.stream().filter(row -> row.artifactType().equals(artifactType)).toList();
    }
}
