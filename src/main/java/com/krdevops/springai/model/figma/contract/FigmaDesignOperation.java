package com.krdevops.springai.model.figma.contract;

import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * I-1: Figma 디자인 요청 처리 Operation.
 * 요청의 생명주기와 상태를 불변 revision으로 추적.
 * 동일 requestHash로 재시도해도 Operation 중복 생성 없음(멱등성).
 * 명세서 §3.2 데이터 흐름 및 R1-014/R1-029 기반.
 */
public record FigmaDesignOperation(
        @JsonProperty("operationId")
        String operationId,

        @JsonProperty("revision")
        int revision,

        @JsonProperty("request")
        FigmaDesignRequest request,

        @JsonProperty("hash")
        String hash,

        @JsonProperty("status")
        FigmaDesignOperationStatus status,

        @JsonProperty("sourceRevision")
        SourceRevisionRef sourceRevision,

        @JsonProperty("issues")
        List<GenerationIssue> issues,

        @JsonProperty("artifacts")
        List<ArtifactRef> artifacts,

        @JsonProperty("createdAt")
        Instant createdAt,

        @JsonProperty("updatedAt")
        Instant updatedAt
) {
    /**
     * 다음 revision으로 상태 전이.
     * revision은 자동 증가, updatedAt은 현재 시간으로 갱신.
     */
    public FigmaDesignOperation withNextRevision(
            FigmaDesignOperationStatus nextStatus,
            SourceRevisionRef nextSourceRevision,
            List<GenerationIssue> nextIssues,
            List<ArtifactRef> nextArtifacts
    ) {
        return new FigmaDesignOperation(
                operationId,
                revision + 1,
                request,
                hash,
                nextStatus,
                nextSourceRevision,
                nextIssues,
                nextArtifacts,
                createdAt,
                Instant.now()
        );
    }

    /**
     * 22/23번 문서 A-01b: {@code appendTransitionWithRequest}(A-01c)가 request 자체를 갱신하며
     * 다음 revision으로 전이할 때 사용한다. request가 바뀌면 hash도 함께 바뀌므로 두 값을 한
     * 번에 받는다 — 이 record는 해시 알고리즘을 모르므로 호출자(Repository)가 재계산해서 넘긴다.
     */
    public FigmaDesignOperation withRequestAndNextRevision(
            FigmaDesignRequest nextRequest,
            String nextHash,
            FigmaDesignOperationStatus nextStatus,
            SourceRevisionRef nextSourceRevision,
            List<GenerationIssue> nextIssues,
            List<ArtifactRef> nextArtifacts
    ) {
        return new FigmaDesignOperation(
                operationId,
                revision + 1,
                nextRequest,
                nextHash,
                nextStatus,
                nextSourceRevision,
                nextIssues,
                nextArtifacts,
                createdAt,
                Instant.now()
        );
    }

    /**
     * 요청 hash 반환 (멱등성 추적용)
     */
    public String requestHash() {
        return hash;
    }

    /**
     * 상태가 최종 상태인지 확인
     */
    public boolean isTerminal() {
        return status == FigmaDesignOperationStatus.APPLIED ||
               status == FigmaDesignOperationStatus.FAILED ||
               status == FigmaDesignOperationStatus.CONFLICT ||
               status == FigmaDesignOperationStatus.REJECTED;
    }
}
