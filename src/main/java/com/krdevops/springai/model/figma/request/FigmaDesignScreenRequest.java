package com.krdevops.springai.model.figma.request;

import java.util.List;

/**
 * {@link FigmaDesignRequest} 내 화면 1개에 대한 요청. 7개 요청 유형이 필요한 필드를 서로 다르게
 * 쓰므로 필드는 전부 선택으로 두고, 유형별 필수 조합 검증은 I-3 {@code FigmaContextAnalyzer}가
 * 담당한다(이 record는 구조적 계약만 강제한다).
 */
public record FigmaDesignScreenRequest(
        @jakarta.validation.constraints.NotBlank String pageId,
        String screenName,
        String textPrompt,
        String referenceFileKey,
        String referenceNodeId,
        String imageAssetRef,
        List<String> requiredComponentLogicalTypes,
        String targetPlatform,
        String existingScreenId
) {
    public FigmaDesignScreenRequest {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId는 필수입니다.");
        }
        requiredComponentLogicalTypes = requiredComponentLogicalTypes == null
                ? List.of() : List.copyOf(requiredComponentLogicalTypes);
    }
}
