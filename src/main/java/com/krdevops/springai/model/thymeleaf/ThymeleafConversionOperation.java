package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;

import java.time.Instant;
import java.util.List;

/**
 * I-5B Project Operation의 불변 revision 1개. {@code revision}은 이 Operation의 저장 이력 안에서
 * 단조 증가하는 순번이며, 상태 전이마다 새 revision을 추가한다(기존 revision을 덮어쓰지 않는다).
 * {@code renderedHtml}은 {@link ThymeleafConversionOperationStatus#PREVIEW_READY} 이상에서만 채워진다.
 */
public record ThymeleafConversionOperation(
        String operationId,
        int revision,
        String screenId,
        LegacyScreenRole screenRole,
        ThymeleafConversionOperationStatus status,
        String targetRelativePath,
        ThymeleafBindingContract contract,
        String renderedHtml,
        SourceRevisionRef sourceRevision,
        List<GenerationIssue> issues,
        List<ArtifactRef> artifacts,
        Instant createdAt,
        Instant updatedAt
) {
    public ThymeleafConversionOperation {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId는 필수입니다.");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision은 1 이상이어야 합니다.");
        }
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다.");
        }
        if (screenRole == null) {
            throw new IllegalArgumentException("screenRole은 필수입니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다.");
        }
        if (targetRelativePath == null || targetRelativePath.isBlank()) {
            throw new IllegalArgumentException("targetRelativePath는 필수입니다.");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public ThymeleafConversionOperation withNextRevision(
            ThymeleafConversionOperationStatus nextStatus,
            ThymeleafBindingContract nextContract,
            String nextRenderedHtml,
            SourceRevisionRef nextSourceRevision,
            List<GenerationIssue> nextIssues,
            List<ArtifactRef> nextArtifacts
    ) {
        return new ThymeleafConversionOperation(
                operationId, revision + 1, screenId, screenRole, nextStatus, targetRelativePath,
                nextContract != null ? nextContract : contract,
                nextRenderedHtml != null ? nextRenderedHtml : renderedHtml,
                nextSourceRevision != null ? nextSourceRevision : sourceRevision,
                nextIssues, nextArtifacts, createdAt, Instant.now());
    }
}
