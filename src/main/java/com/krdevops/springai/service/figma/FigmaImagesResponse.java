package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * R6-T10: {@code GET /v1/images/{fileKey}?ids=...} 응답. Figma는 이미지 렌더링에 실패한
 * 개별 노드는 {@code images} 맵에서 값을 {@code null}로 반환한다(전체 오류는 {@code err}).
 */
public record FigmaImagesResponse(
        @JsonProperty("err")
        String err,

        @JsonProperty("images")
        Map<String, String> images
) {
}
