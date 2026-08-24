package com.krdevops.springai.model.generation;

import com.krdevops.springai.model.artifact.ContentHashes;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 파일별 Generated·Binding·Protected·Unknown Region 소유권과 병합 정책. */
public record GenerationOwnershipManifest(
        String manifestId,
        String contentHash,
        List<ArtifactOwnership> artifacts
) {
    public GenerationOwnershipManifest {
        if (manifestId == null || !manifestId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("manifestId 형식이 올바르지 않습니다.");
        }
        contentHash = ContentHashes.requireValid(contentHash);
        artifacts = artifacts == null ? List.of() : artifacts.stream()
                .sorted(Comparator.comparing(ArtifactOwnership::artifactPath)).toList();
        Set<String> paths = new HashSet<>();
        artifacts.forEach(artifact -> {
            if (!paths.add(artifact.artifactPath())) throw new IllegalArgumentException("artifactPath가 중복됩니다: " + artifact.artifactPath());
        });
    }

    public static Builder builder(String manifestId) { return new Builder(manifestId); }

    public boolean hasValidContentHash() {
        return contentHash.equals(ContentHashes.sha256Hex(canonical().getBytes(StandardCharsets.UTF_8)));
    }

    /** {@code artifactPath}에 해당하는 Region 목록. 없으면 빈 리스트(3-way 비교에서 Base 없음으로 취급). */
    public List<Region> regionsFor(String artifactPath) {
        return artifacts.stream()
                .filter(artifact -> artifact.artifactPath().equals(artifactPath))
                .findFirst()
                .map(ArtifactOwnership::regions)
                .orElse(List.of());
    }

    public static final class Builder {
        private final String manifestId;
        private List<ArtifactOwnership> artifacts = List.of();
        private Builder(String manifestId) { this.manifestId = manifestId; }
        public Builder artifacts(List<ArtifactOwnership> value) { artifacts = value == null ? List.of() : value; return this; }
        public GenerationOwnershipManifest build() {
            GenerationOwnershipManifest draft = new GenerationOwnershipManifest(manifestId, "0".repeat(64), artifacts);
            return new GenerationOwnershipManifest(manifestId,
                    ContentHashes.sha256Hex(draft.canonical().getBytes(StandardCharsets.UTF_8)), draft.artifacts());
        }
    }

    private String canonical() {
        return manifestId + artifacts.stream().map(ArtifactOwnership::canonical)
                .sorted().reduce("", (a, b) -> a + "|" + b);
    }

    public record ArtifactOwnership(String artifactPath, List<Region> regions,
                                    MergePolicy mergePolicy, String owner) {
        public ArtifactOwnership {
            if (artifactPath == null || artifactPath.isBlank()) throw new IllegalArgumentException("artifactPath는 필수입니다.");
            if (mergePolicy == null) throw new IllegalArgumentException("mergePolicy는 필수입니다.");
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner는 필수입니다.");
            regions = regions == null ? List.of() : regions.stream().sorted(Comparator.comparing(Region::regionId)).toList();
            Set<String> ids = new HashSet<>();
            regions.forEach(region -> { if (!ids.add(region.regionId())) throw new IllegalArgumentException("regionId가 중복됩니다: " + region.regionId()); });
        }
        private String canonical() { return artifactPath + ":" + mergePolicy + ":" + owner + regions.stream().map(Region::canonical).sorted().reduce("", (a,b) -> a + ";" + b); }
    }

    public record Region(String regionId, RegionType regionType, String contentHash) {
        public Region {
            if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId는 필수입니다.");
            if (regionType == null) throw new IllegalArgumentException("regionType은 필수입니다.");
            contentHash = ContentHashes.requireValid(contentHash);
        }
        private String canonical() { return regionId + ":" + regionType + ":" + contentHash; }
    }

    public enum RegionType { GENERATED, BINDING, PROTECTED, UNKNOWN }
    public enum MergePolicy { REGENERATE, CONTRACT_ONLY, PRESERVE, REVIEW_CONFLICT }
}
