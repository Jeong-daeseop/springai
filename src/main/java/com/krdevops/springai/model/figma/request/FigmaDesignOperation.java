package com.krdevops.springai.model.figma.request;

import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;

import java.time.Instant;
import java.util.List;

/**
 * {@link FigmaDesignRequest}를 안전하고 멱등적인 Preview Operation으로 저장한 결과.
 * {@code revision}은 이 Operation의 불변 이력 안에서 단조 증가하는 저장 순번이며,
 * {@code status}는 항상 그 revision 시점의 최종 상태를 나타낸다(같은 revision을 덮어쓰지 않는다).
 */
public record FigmaDesignOperation(
        @jakarta.validation.constraints.NotBlank String operationId,
        @jakarta.validation.constraints.Positive int revision,
        @jakarta.validation.constraints.NotNull FigmaDesignRequest request,
        @jakarta.validation.constraints.NotBlank String requestHash,
        @jakarta.validation.constraints.NotNull FigmaDesignOperationStatus status,
        SourceRevisionRef sourceRevision,
        List<GenerationIssue> issues,
        List<ArtifactRef> artifacts,
        @jakarta.validation.constraints.NotNull Instant createdAt,
        @jakarta.validation.constraints.NotNull Instant updatedAt
) {
    public FigmaDesignOperation {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId는 필수입니다.");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision은 1 이상이어야 합니다.");
        }
        if (request == null) {
            throw new IllegalArgumentException("request는 필수입니다.");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash는 필수입니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다.");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public FigmaDesignOperation withNextRevision(
            FigmaDesignOperationStatus nextStatus,
            SourceRevisionRef nextSourceRevision,
            List<GenerationIssue> nextIssues,
            List<ArtifactRef> nextArtifacts
    ) {
        return new FigmaDesignOperation(
                operationId, revision + 1, request, requestHash, nextStatus,
                nextSourceRevision != null ? nextSourceRevision : sourceRevision,
                nextIssues, nextArtifacts, createdAt, Instant.now());
    }
}
