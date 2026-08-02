package com.krdevops.springai.model.figma.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * I-1: Figma 디자인 요청의 통일된 계약.
 * 7가지 요청 유형 모두 이 구조로 통합됨.
 * 명세서 §5.1 기반.
 */
public record FigmaDesignRequest(
        @JsonProperty("type")
        FigmaDesignRequestType type,

        @JsonProperty("prompt")
        String prompt,

        @JsonProperty("fileKey")
        String fileKey,

        @JsonProperty("referenceNodeIds")
        List<String> referenceNodeIds,

        @JsonProperty("editableNodeIds")
        List<String> editableNodeIds,

        @JsonProperty("imageNodeIds")
        List<String> imageNodeIds,

        @JsonProperty("targetPlatform")
        String targetPlatform,

        @JsonProperty("components")
        List<String> components,

        @JsonProperty("screens")
        List<FigmaScreenRequest> screens
) {
    public FigmaDesignRequest {
        if (type == null) {
            throw new IllegalArgumentException("type은 필수입니다");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 필수입니다");
        }
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey는 필수입니다");
        }
    }

    /**
     * TEXT_DESCRIPTION 요청 생성 헬퍼
     */
    public static FigmaDesignRequest textDescription(String prompt, String fileKey) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.TEXT_DESCRIPTION,
                prompt, fileKey,
                null, null, null, null, null, null
        );
    }

    /**
     * REFERENCE_STYLE 요청 생성 헬퍼
     */
    public static FigmaDesignRequest referenceStyle(String prompt, String fileKey, List<String> nodeIds) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.REFERENCE_STYLE,
                prompt, fileKey,
                nodeIds, null, null, null, null, null
        );
    }

    /**
     * MODIFY_EXISTING 요청 생성 헬퍼
     */
    public static FigmaDesignRequest modifyExisting(String prompt, String fileKey, List<String> editableNodeIds) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.MODIFY_EXISTING,
                prompt, fileKey,
                null, editableNodeIds, null, null, null, null
        );
    }

    /**
     * IMAGE_REFERENCE 요청 생성 헬퍼
     */
    public static FigmaDesignRequest imageReference(String prompt, String fileKey, List<String> imageNodeIds) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.IMAGE_REFERENCE,
                prompt, fileKey,
                null, null, imageNodeIds, null, null, null
        );
    }

    /**
     * MULTI_SCREEN_FLOW 요청 생성 헬퍼
     */
    public static FigmaDesignRequest multiScreenFlow(String prompt, String fileKey, List<FigmaScreenRequest> screens) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.MULTI_SCREEN_FLOW,
                prompt, fileKey,
                null, null, null, null, null, screens
        );
    }

    /**
     * COMPONENT_SPECIFIED 요청 생성 헬퍼
     */
    public static FigmaDesignRequest componentSpecified(String prompt, String fileKey, List<String> componentNames) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.COMPONENT_SPECIFIED,
                prompt, fileKey,
                null, null, null, null, componentNames, null
        );
    }

    /**
     * PLATFORM_CONVERT 요청 생성 헬퍼
     */
    public static FigmaDesignRequest platformConvert(String prompt, String fileKey, List<String> sourceNodeIds, String targetPlatform) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.PLATFORM_CONVERT,
                prompt, fileKey,
                sourceNodeIds, null, null, targetPlatform, null, null
        );
    }
}
