package com.krdevops.springai.model.designsystem;

/** Registry Preview 또는 승인 반영 결과. */
public record ComponentRegistrySyncResult(
        Status status,
        ComponentRegistryDiff diff,
        ComponentRegistry registry,
        DesignSystemProfile profile,
        FailureReport failureReport
) {
    public ComponentRegistrySyncResult(
            Status status,
            ComponentRegistryDiff diff,
            ComponentRegistry registry,
            DesignSystemProfile profile
    ) {
        this(status, diff, registry, profile, FailureReport.from(diff));
    }

    public enum Status { PREVIEW, APPLIED, REJECTED }

    /**
     * 원자적 동기화 실패를 재시도 가능한 형태로 전달한다. Registry는 부분 저장하지 않는다.
     * {@code retryToken}은 비밀값이 아니라 {@code profileId:candidateVersion} 형태의 예측 가능한
     * 식별자다(DEC-07 redaction 감사 결과, 2026-07-28). 실제 접근 통제는 이 값이 아니라
     * `/api/**`에 걸린 X-API-Key/단기 토큰 인증이 담당하므로, 이 값 자체를 비밀로 취급하거나
     * 인가 수단으로 사용하지 않는다.
     */
    public record FailureReport(
            boolean retryable,
            java.util.List<String> failedTargets,
            java.util.List<String> errorCodes,
            String retryToken
    ) {
        public FailureReport {
            failedTargets = failedTargets == null ? java.util.List.of() : java.util.List.copyOf(failedTargets);
            errorCodes = errorCodes == null ? java.util.List.of() : java.util.List.copyOf(errorCodes);
        }

        private static FailureReport from(ComponentRegistryDiff diff) {
            if (diff == null || diff.valid()) {
                return null;
            }
            java.util.List<DesignSystemIssue> failures = diff.issues().stream()
                    .filter(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                            || issue.severity() == DesignSystemIssue.Severity.FATAL)
                    .toList();
            java.util.List<String> retryableCodes = java.util.List.of(
                    "FIGMA_API_LIMIT", "FIGMA_API_UNAVAILABLE", "COMPONENT_NOT_CURRENT", "VARIABLE_NOT_CURRENT");
            boolean retryable = failures.stream().anyMatch(issue -> retryableCodes.contains(issue.code()));
            String token = retryable ? diff.profileId() + ":" + diff.candidateVersion() : null;
            return new FailureReport(
                    retryable,
                    failures.stream().map(DesignSystemIssue::targetId).filter(java.util.Objects::nonNull).distinct().toList(),
                    failures.stream().map(DesignSystemIssue::code).distinct().toList(),
                    token);
        }
    }
}
