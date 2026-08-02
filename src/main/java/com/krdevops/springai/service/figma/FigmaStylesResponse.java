package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Figma Styles API 응답.
 * GET /v1/files/{fileKey}/styles
 */
public record FigmaStylesResponse(
        @JsonProperty("meta")
        Meta meta,

        @JsonProperty("error")
        boolean error,

        @JsonProperty("status")
        int status
) {
    public record Meta(
            @JsonProperty("styles")
            List<StyleRef> styles
    ) {}

    public record StyleRef(
            @JsonProperty("key")
            String key,

            @JsonProperty("file_key")
            String fileKey,

            @JsonProperty("node_id")
            String nodeId,

            @JsonProperty("style_type")
            String styleType,  // "FILL", "STROKE", "TEXT", "EFFECT", "GRID"

            @JsonProperty("name")
            String name,

            @JsonProperty("description")
            String description
    ) {}
}
