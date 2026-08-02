package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;

import java.time.Instant;
import java.util.List;

/**
 * I-2E 최종 산출물. Thymeleaf HTML 생성(I-4)은 이 계약의 {@code fields}/{@code route}/
 * {@code displayFieldNames}/{@code primaryDisplayAttributeName}만 신뢰하며 JSP 원문을 다시
 * 해석하지 않는다.
 */
public record ThymeleafBindingContract(
        String screenId,
        LegacyScreenRole screenRole,
        ThymeleafRouteBinding route,
        List<ThymeleafFieldBinding> fields,
        List<String> displayFieldNames,
        String primaryDisplayAttributeName,
        List<String> modelAttributesResolved,
        List<String> modelAttributesUnresolved,
        BindingContractStatus status,
        List<GenerationIssue> issues,
        SourceRevisionRef sourceRevision,
        Instant createdAt
) {
    public ThymeleafBindingContract {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다.");
        }
        if (screenRole == null) {
            throw new IllegalArgumentException("screenRole은 필수입니다.");
        }
        if (route == null) {
            throw new IllegalArgumentException("route는 필수입니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다.");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        displayFieldNames = displayFieldNames == null ? List.of() : List.copyOf(displayFieldNames);
        modelAttributesResolved = modelAttributesResolved == null ? List.of() : List.copyOf(modelAttributesResolved);
        modelAttributesUnresolved = modelAttributesUnresolved == null
                ? List.of() : List.copyOf(modelAttributesUnresolved);
        issues = issues == null ? List.of() : List.copyOf(issues);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
