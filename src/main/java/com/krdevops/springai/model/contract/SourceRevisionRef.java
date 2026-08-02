package com.krdevops.springai.model.contract;

/**
 * Operation이 전제로 삼은 원본 소스(예: Figma 파일/노드, JSP 프로젝트 파일 트리)의
 * 특정 시점 좌표. Apply 직전 재조회한 값과 비교해 낙관적 충돌(CONFLICT)을 판정하는 데 쓴다.
 */
public record SourceRevisionRef(
        @jakarta.validation.constraints.NotBlank String sourceId,
        @jakarta.validation.constraints.NotBlank String revisionToken,
        @jakarta.validation.constraints.NotNull java.time.Instant capturedAt
) {
    public SourceRevisionRef {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId는 필수입니다.");
        }
        if (revisionToken == null || revisionToken.isBlank()) {
            throw new IllegalArgumentException("revisionToken은 필수입니다.");
        }
        capturedAt = capturedAt == null ? java.time.Instant.now() : capturedAt;
    }
}
