package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/**
 * 같은 화면을 다시 생성할 때, 마지막으로 적용됐던 {@link ThymeleafBindingContract}와 새로 조립한
 * 계약을 비교한 결과. {@link #requiresReview()}가 true면 자동 승인하지 않고 사람이 명시적으로
 * 검토해야 한다.
 */
public record RegenerationDiffResult(
        boolean hasPrevious,
        boolean permissionChanged,
        boolean httpMethodChanged,
        List<String> addedSecurityEvidence,
        List<String> removedSecurityEvidence,
        String previousHttpMethod,
        String currentHttpMethod
) {
    public RegenerationDiffResult {
        addedSecurityEvidence = addedSecurityEvidence == null ? List.of() : List.copyOf(addedSecurityEvidence);
        removedSecurityEvidence = removedSecurityEvidence == null ? List.of() : List.copyOf(removedSecurityEvidence);
    }

    public static RegenerationDiffResult none() {
        return new RegenerationDiffResult(false, false, false, List.of(), List.of(), null, null);
    }

    /** 권한 evidence 또는 CSRF 보호 상태(httpMethod 대리 신호)가 바뀌었으면 자동 통과시키지 않는다. */
    public boolean requiresReview() {
        return permissionChanged || httpMethodChanged;
    }
}
