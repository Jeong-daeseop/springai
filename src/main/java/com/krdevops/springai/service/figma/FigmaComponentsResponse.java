package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Figma Components API 응답.
 * GET /v1/files/{fileKey}/components
 */
public record FigmaComponentsResponse(
        @JsonProperty("meta")
        Meta meta,

        @JsonProperty("error")
        boolean error,

        @JsonProperty("status")
        int status
) {
    public record Meta(
            @JsonProperty("components")
            List<ComponentRef> components
    ) {}

    public record ComponentRef(
            @JsonProperty("key")
            String key,

            @JsonProperty("file_key")
            String fileKey,

            @JsonProperty("node_id")
            String nodeId,

            @JsonProperty("name")
            String name,

            @JsonProperty("description")
            String description,

            @JsonProperty("containing_frame")
            ContainingFrame containingFrame
    ) {
        public record ContainingFrame(
                @JsonProperty("node_id")
                String nodeId,

                @JsonProperty("name")
                String name
        ) {}
    }
}
