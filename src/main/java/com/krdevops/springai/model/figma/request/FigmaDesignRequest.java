package com.krdevops.springai.model.figma.request;

import com.krdevops.springai.model.contract.DesignSystemSnapshotRef;

import java.time.Instant;
import java.util.List;

/**
 * 7가지 Figma 디자인 요청의 공통 봉투(envelope). {@code requestId}는 클라이언트 상관관계 ID일 뿐이며
 * {@link com.krdevops.springai.service.contract.OperationHashFactory}가 계산하는 requestHash가
 * 멱등 처리의 기준이다(requestId·requestedAt은 해시에서 제외).
 */
public record FigmaDesignRequest(
        String requestId,
        @jakarta.validation.constraints.NotNull FigmaDesignRequestType requestType,
        String fileKey,
        @jakarta.validation.constraints.NotEmpty List<FigmaDesignScreenRequest> screens,
        @jakarta.validation.constraints.NotNull DesignSystemSnapshotRef snapshotRef,
        Instant requestedAt
) {
    public FigmaDesignRequest {
        if (requestType == null) {
            throw new IllegalArgumentException("requestType은 필수입니다.");
        }
        if (screens == null || screens.isEmpty()) {
            throw new IllegalArgumentException("screens는 최소 1개 이상이어야 합니다.");
        }
        if (snapshotRef == null) {
            throw new IllegalArgumentException("snapshotRef는 필수입니다.");
        }
        screens = List.copyOf(screens);
        requestedAt = requestedAt == null ? Instant.now() : requestedAt;
    }
}
