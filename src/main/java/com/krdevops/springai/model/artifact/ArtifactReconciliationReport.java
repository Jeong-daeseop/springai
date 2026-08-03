package com.krdevops.springai.model.artifact;

import java.util.List;

/**
 * ARCH-0509/0511: catalog와 filesystem 비교 결과. {@code dryRun=true}이면 보고만 하고
 * 아무 것도 이동·상태변경하지 않는다. {@code dryRun=false}일 때만 {@code quarantinedContentHashes}가
 * 실제로 처리된 orphan 목록이 된다.
 */
public record ArtifactReconciliationReport(
        boolean dryRun,
        List<String> orphanContentHashes,
        List<Artifact> missingArtifacts,
        List<String> quarantinedContentHashes
) {
    public ArtifactReconciliationReport {
        orphanContentHashes = List.copyOf(orphanContentHashes == null ? List.of() : orphanContentHashes);
        missingArtifacts = List.copyOf(missingArtifacts == null ? List.of() : missingArtifacts);
        quarantinedContentHashes = List.copyOf(quarantinedContentHashes == null ? List.of() : quarantinedContentHashes);
    }

    public boolean isClean() {
        return orphanContentHashes.isEmpty() && missingArtifacts.isEmpty();
    }
}
