package com.krdevops.springai.model.thymeleaf;

import java.time.Instant;

/**
 * I-2 분석의 입력. {@code projectRootPath}는 {@code LegacySourceInventoryService}가 허용된 실제
 * 경로인지 검증하는 기준 root이고, 나머지 3개 경로는 그 root에 대한 상대 경로다.
 */
public record LegacyConversionRequest(
        String conversionId,
        @jakarta.validation.constraints.NotBlank String projectRootPath,
        @jakarta.validation.constraints.NotBlank String screenId,
        @jakarta.validation.constraints.NotNull LegacyScreenRole screenRole,
        @jakarta.validation.constraints.NotBlank String jspRelativePath,
        @jakarta.validation.constraints.NotBlank String controllerRelativePath,
        @jakarta.validation.constraints.NotBlank String voRelativePath,
        Instant requestedAt
) {
    public LegacyConversionRequest {
        if (projectRootPath == null || projectRootPath.isBlank()) {
            throw new IllegalArgumentException("projectRootPath는 필수입니다.");
        }
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다.");
        }
        if (screenRole == null) {
            throw new IllegalArgumentException("screenRole은 필수입니다.");
        }
        if (jspRelativePath == null || jspRelativePath.isBlank()) {
            throw new IllegalArgumentException("jspRelativePath는 필수입니다.");
        }
        if (controllerRelativePath == null || controllerRelativePath.isBlank()) {
            throw new IllegalArgumentException("controllerRelativePath는 필수입니다.");
        }
        if (voRelativePath == null || voRelativePath.isBlank()) {
            throw new IllegalArgumentException("voRelativePath는 필수입니다.");
        }
        requestedAt = requestedAt == null ? Instant.now() : requestedAt;
    }
}
