package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/** WP8 ARCH-0810: viewport별로 승격된 baseline과 그 승인 증적 Artifact. */
public record BaselineApprovalResult(
        String operationId,
        String screenId,
        List<ViewportApproval> viewports
) {
    public BaselineApprovalResult {
        viewports = viewports == null ? List.of() : List.copyOf(viewports);
    }

    /** {@code previousContentHash}가 {@code null}이면 이 viewport의 최초 승인이다. */
    public record ViewportApproval(
            String viewport,
            String newContentHash,
            String previousContentHash,
            String baselineArtifactId,
            String approvalArtifactId) { }
}
