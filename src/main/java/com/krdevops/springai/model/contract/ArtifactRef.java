package com.krdevops.springai.model.contract;

/**
 * 저장된 산출물(Bundle, Report, 생성 파일 등)을 가리키는 공통 참조.
 * {@code artifactType}은 도메인이 자유롭게 정의하는 개방형 문자열이다
 * (예: "FIGMA_EXPORT_BUNDLE", "THYMELEAF_GENERATION_REPORT").
 */
public record ArtifactRef(
        @jakarta.validation.constraints.NotBlank String artifactId,
        @jakarta.validation.constraints.NotBlank String artifactType,
        @jakarta.validation.constraints.NotBlank String uri,
        @jakarta.validation.constraints.NotBlank String contentHash,
        @jakarta.validation.constraints.NotNull java.time.Instant createdAt
) {
    private static final java.util.regex.Pattern SHA256_HEX =
            java.util.regex.Pattern.compile("^[a-f0-9]{64}$");

    public ArtifactRef {
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId는 필수입니다.");
        }
        if (artifactType == null || artifactType.isBlank()) {
            throw new IllegalArgumentException("artifactType은 필수입니다.");
        }
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("uri는 필수입니다.");
        }
        if (contentHash == null || !SHA256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash는 64자 소문자 SHA-256 hex여야 합니다.");
        }
        createdAt = createdAt == null ? java.time.Instant.now() : createdAt;
    }
}
