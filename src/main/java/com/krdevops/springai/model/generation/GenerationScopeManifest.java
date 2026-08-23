package com.krdevops.springai.model.generation;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.VersionedArtifactReference;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 생성·검증·보존 범위를 고정하는 불변 Scope Manifest.
 *
 * <p>목록은 Builder에서 참조 식별자 순으로 정렬하고, contentHash는 manifestId와
 * 목록·선택 사유·미해결 의존성을 포함한 canonical payload에서 계산한다. 따라서
 * 같은 범위는 입력 순서가 달라도 동일한 Manifest hash를 만든다.</p>
 */
public record GenerationScopeManifest(
        String manifestId,
        String contentHash,
        List<VersionedArtifactReference> rootArtifacts,
        List<VersionedArtifactReference> dependencyArtifacts,
        List<VersionedArtifactReference> validationOnlyArtifacts,
        List<VersionedArtifactReference> preservedArtifacts,
        List<String> affectedScreens,
        String selectionReason,
        List<String> unresolvedDependencies
) {
    public GenerationScopeManifest {
        manifestId = requireToken(manifestId, "manifestId");
        contentHash = ContentHashes.requireValid(contentHash);
        rootArtifacts = immutableRefs(rootArtifacts, "rootArtifacts");
        dependencyArtifacts = immutableRefs(dependencyArtifacts, "dependencyArtifacts");
        validationOnlyArtifacts = immutableRefs(validationOnlyArtifacts, "validationOnlyArtifacts");
        preservedArtifacts = immutableRefs(preservedArtifacts, "preservedArtifacts");
        assertDisjoint(rootArtifacts, dependencyArtifacts, "rootArtifacts", "dependencyArtifacts");
        assertDisjoint(rootArtifacts, validationOnlyArtifacts, "rootArtifacts", "validationOnlyArtifacts");
        assertDisjoint(rootArtifacts, preservedArtifacts, "rootArtifacts", "preservedArtifacts");
        assertDisjoint(dependencyArtifacts, validationOnlyArtifacts, "dependencyArtifacts", "validationOnlyArtifacts");
        assertDisjoint(dependencyArtifacts, preservedArtifacts, "dependencyArtifacts", "preservedArtifacts");
        assertDisjoint(validationOnlyArtifacts, preservedArtifacts, "validationOnlyArtifacts", "preservedArtifacts");
        affectedScreens = immutableText(affectedScreens, "affectedScreens");
        selectionReason = requireText(selectionReason, "selectionReason");
        unresolvedDependencies = immutableText(unresolvedDependencies, "unresolvedDependencies");
    }

    public static Builder builder(String manifestId) {
        return new Builder(manifestId);
    }

    /** Builder가 계산한 hash와 현재 필드가 일치하는지 검증한다. */
    public boolean hasValidContentHash() {
        return contentHash.equals(ContentHashes.sha256Hex(canonicalPayload().getBytes(StandardCharsets.UTF_8)));
    }

    public static final class Builder {
        private final String manifestId;
        private List<VersionedArtifactReference> rootArtifacts = List.of();
        private List<VersionedArtifactReference> dependencyArtifacts = List.of();
        private List<VersionedArtifactReference> validationOnlyArtifacts = List.of();
        private List<VersionedArtifactReference> preservedArtifacts = List.of();
        private List<String> affectedScreens = List.of();
        private String selectionReason;
        private List<String> unresolvedDependencies = List.of();

        private Builder(String manifestId) {
            this.manifestId = manifestId;
        }

        public Builder rootArtifacts(List<VersionedArtifactReference> value) {
            rootArtifacts = value == null ? List.of() : value; return this;
        }
        public Builder dependencyArtifacts(List<VersionedArtifactReference> value) {
            dependencyArtifacts = value == null ? List.of() : value; return this;
        }
        public Builder validationOnlyArtifacts(List<VersionedArtifactReference> value) {
            validationOnlyArtifacts = value == null ? List.of() : value; return this;
        }
        public Builder preservedArtifacts(List<VersionedArtifactReference> value) {
            preservedArtifacts = value == null ? List.of() : value; return this;
        }
        public Builder affectedScreens(List<String> value) {
            affectedScreens = value == null ? List.of() : value; return this;
        }
        public Builder selectionReason(String value) {
            selectionReason = value; return this;
        }
        public Builder unresolvedDependencies(List<String> value) {
            unresolvedDependencies = value == null ? List.of() : value; return this;
        }

        public GenerationScopeManifest build() {
            GenerationScopeManifest draft = new GenerationScopeManifest(
                    manifestId, "0".repeat(64), rootArtifacts, dependencyArtifacts,
                    validationOnlyArtifacts, preservedArtifacts, affectedScreens,
                    selectionReason, unresolvedDependencies);
            return new GenerationScopeManifest(
                    draft.manifestId(), ContentHashes.sha256Hex(draft.canonicalPayload()
                            .getBytes(StandardCharsets.UTF_8)), draft.rootArtifacts(),
                    draft.dependencyArtifacts(), draft.validationOnlyArtifacts(),
                    draft.preservedArtifacts(), draft.affectedScreens(), draft.selectionReason(),
                    draft.unresolvedDependencies());
        }
    }

    private String canonicalPayload() {
        return manifestId + "|root=" + canonicalRefs(rootArtifacts)
                + "|dependency=" + canonicalRefs(dependencyArtifacts)
                + "|validation=" + canonicalRefs(validationOnlyArtifacts)
                + "|preserved=" + canonicalRefs(preservedArtifacts)
                + "|screens=" + String.join(",", affectedScreens)
                + "|reason=" + selectionReason
                + "|unresolved=" + String.join(",", unresolvedDependencies);
    }

    private static String canonicalRefs(List<VersionedArtifactReference> refs) {
        return refs.stream().map(GenerationScopeManifest::refKey).sorted().reduce("", (a, b) ->
                a.isEmpty() ? b : a + ";" + b);
    }

    private static String refKey(VersionedArtifactReference ref) {
        return ref.artifactId() + ":" + ref.artifactType() + ":" + ref.schemaVersion()
                + ":" + ref.contentHash() + ":" + (ref.sourceRevision() == null ? "" : ref.sourceRevision());
    }

    private static List<VersionedArtifactReference> immutableRefs(
            List<VersionedArtifactReference> values, String field) {
        List<VersionedArtifactReference> result = new ArrayList<>(values == null ? List.of() : values);
        if (result.stream().anyMatch(value -> value == null)) throw new IllegalArgumentException(field + "에 null 참조가 있습니다.");
        result.sort(Comparator.comparing(GenerationScopeManifest::refKey));
        return List.copyOf(result);
    }

    private static List<String> immutableText(List<String> values, String field) {
        List<String> result = (values == null ? List.<String>of() : values).stream()
                .map(value -> requireText(value, field + " 항목")).distinct().sorted().toList();
        return List.copyOf(result);
    }

    private static void assertDisjoint(List<VersionedArtifactReference> left,
                                       List<VersionedArtifactReference> right,
                                       String leftName, String rightName) {
        Set<String> keys = new HashSet<>(left.stream().map(GenerationScopeManifest::refKey).toList());
        right.stream().map(GenerationScopeManifest::refKey).filter(keys::contains).findFirst()
                .ifPresent(key -> { throw new IllegalArgumentException(
                        "Scope Artifact가 " + leftName + "·" + rightName + "에 중복됩니다: " + key); });
    }

    private static String requireToken(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " 형식이 올바르지 않습니다.");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "는 필수입니다.");
        return value.trim();
    }
}
