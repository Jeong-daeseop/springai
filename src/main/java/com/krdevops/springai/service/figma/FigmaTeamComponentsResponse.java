package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * R6-T10: Figma Team Components API 응답.
 * GET /v1/teams/{team_id}/components
 *
 * <p>파일 단위 {@link FigmaComponentsResponse}와 달리 Team 전체 Library Inventory를 대상으로 하므로
 * 결과가 방대할 수 있어 {@code page_size}/{@code after} cursor 기반 pagination을 실제로 지원한다.
 */
public record FigmaTeamComponentsResponse(
        @JsonProperty("meta")
        Meta meta,

        @JsonProperty("error")
        boolean error,

        @JsonProperty("status")
        int status
) {
    public record Meta(
            @JsonProperty("components")
            List<ComponentRef> components,

            @JsonProperty("cursor")
            Cursor cursor
    ) {
        public Meta {
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    public record Cursor(
            @JsonProperty("before")
            Long before,

            @JsonProperty("after")
            Long after
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
