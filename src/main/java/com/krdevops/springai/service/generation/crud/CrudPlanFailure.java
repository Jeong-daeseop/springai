package com.krdevops.springai.service.generation.crud;

import java.util.List;

/**
 * Preflight/Planning 단계에서 생성이 중단된 사유 — 파일을 하나도 쓰지 않은 상태다.
 *
 * <p>기존 {@code CrudOrchestrationService.orchestrate()}의 4개 조기 반환 분기를 그대로 옮긴 것이며,
 * {@link CrudGenerationResultAssembler}가 이를 기존 {@code CrudOrchestrationResult}의 4가지 실패
 * 형태로 되돌린다. 각 {@link Kind}마다 채워지는 필드가 다른 이유는 기존 코드가 분기마다 서로 다른
 * 생성자를 쓰고 있었기 때문이며, 그 관찰 가능한 차이를 그대로 보존한다.
 */
public record CrudPlanFailure(
        Kind kind,
        String validationSummary,
        List<String> failedFiles,
        String menuIntegrationStatus,
        String resolvedProgramName,
        String resolvedProgramUrl,
        String canonicalUrl,
        List<String> warnings
) {
    public enum Kind { TABLE_NOT_FOUND, METADATA_BLOCKED, ALIAS_CONFLICT, LAYOUT_MISSING }

    public CrudPlanFailure {
        failedFiles = failedFiles == null ? List.of() : List.copyOf(failedFiles);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static CrudPlanFailure tableNotFound() {
        return new CrudPlanFailure(Kind.TABLE_NOT_FOUND, "", List.of(), null, null, null, null, List.of());
    }

    /** 기존 코드가 9-arg 생성자를 쓰던 분기 — 메타데이터/경로 필드가 비는 동작까지 보존한다. */
    public static CrudPlanFailure layoutMissing(String message) {
        return new CrudPlanFailure(Kind.LAYOUT_MISSING, "layout 검증 실패", List.of(message),
                null, null, null, null, List.of());
    }
}
