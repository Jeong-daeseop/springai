package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;

import java.time.Instant;
import java.util.List;

/**
 * I-2E 최종 산출물. Thymeleaf HTML 생성(I-4)은 이 계약의 {@code fields}/{@code route}/
 * {@code displayFieldNames}/{@code primaryDisplayAttributeName}만 신뢰하며 JSP 원문을 다시
 * 해석하지 않는다.
 *
 * <p>WP6 3차 pass(ARCH-0619/0620): {@code secondaryDisplayFieldNames}/
 * {@code secondaryDisplayAttributeName}은 DETAIL 화면이 주 root(예: 게시글 본문) 외에
 * forEach로 순회하는 두 번째 root(예: 첨부파일 목록, master-detail의 하위 표)를 가질 때만
 * 채워진다. {@link com.krdevops.springai.service.thymeleaf.BindingContractAssembler}가 그 두
 * 번째 root의 필드를 별도로 제공된 VO evidence와 대조해 검증했을 때만 채워지며, 검증할 VO가 없으면
 * 비워둔 채 {@link BindingContractStatus#REVIEW_REQUIRED}로 막는다.
 */
public record ThymeleafBindingContract(
        String screenId,
        LegacyScreenRole screenRole,
        ThymeleafRouteBinding route,
        List<ThymeleafFieldBinding> fields,
        List<String> displayFieldNames,
        String primaryDisplayAttributeName,
        List<String> secondaryDisplayFieldNames,
        String secondaryDisplayAttributeName,
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
        secondaryDisplayFieldNames = secondaryDisplayFieldNames == null
                ? List.of() : List.copyOf(secondaryDisplayFieldNames);
        modelAttributesResolved = modelAttributesResolved == null ? List.of() : List.copyOf(modelAttributesResolved);
        modelAttributesUnresolved = modelAttributesUnresolved == null
                ? List.of() : List.copyOf(modelAttributesUnresolved);
        issues = issues == null ? List.of() : List.copyOf(issues);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** secondary root 도입 전 호출자 호환. */
    public ThymeleafBindingContract(
            String screenId, LegacyScreenRole screenRole, ThymeleafRouteBinding route,
            List<ThymeleafFieldBinding> fields, List<String> displayFieldNames, String primaryDisplayAttributeName,
            List<String> modelAttributesResolved, List<String> modelAttributesUnresolved,
            BindingContractStatus status, List<GenerationIssue> issues,
            SourceRevisionRef sourceRevision, Instant createdAt) {
        this(screenId, screenRole, route, fields, displayFieldNames, primaryDisplayAttributeName,
                List.of(), null, modelAttributesResolved, modelAttributesUnresolved,
                status, issues, sourceRevision, createdAt);
    }
}
