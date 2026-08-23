package com.krdevops.springai.model.contract;

import com.krdevops.springai.model.artifact.ContentHashes;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * 5축 파이프라인 계약이 불변 산출물을 참조할 때 사용하는 공통 식별자.
 * ID만으로 최신 내용을 암묵 조회하지 않고 Schema Version과 내용 Hash를 함께 고정한다.
 */
public record VersionedArtifactReference(
        String artifactId,
        String artifactType,
        String schemaVersion,
        String contentHash,
        @Nullable String sourceRevision
) {
    private static final Pattern ARTIFACT_TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern SCHEMA_VERSION = Pattern.compile("^[1-9][0-9]*\\.[0-9]+(?:\\.[0-9]+)?$");

    public VersionedArtifactReference {
        artifactId = requireToken(artifactId, "artifactId");
        artifactType = requireToken(artifactType, "artifactType");
        if (schemaVersion == null || !SCHEMA_VERSION.matcher(schemaVersion).matches()) {
            throw new IllegalArgumentException("schemaVersion은 1.0 또는 1.0.0 형식이어야 합니다.");
        }
        contentHash = ContentHashes.requireValid(contentHash);
        sourceRevision = normalizeOptional(sourceRevision);
    }

    public boolean identifies(VersionedArtifactReference other) {
        return other != null
                && artifactId.equals(other.artifactId)
                && artifactType.equals(other.artifactType)
                && schemaVersion.equals(other.schemaVersion)
                && contentHash.equals(other.contentHash);
    }

    private static String requireToken(String value, String field) {
        if (value == null || !ARTIFACT_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 형식이 올바르지 않습니다.");
        }
        return value;
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
