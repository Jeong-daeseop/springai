package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * R6-T10: Figma Team Styles API 응답.
 * GET /v1/teams/{team_id}/styles
 *
 * <p>파일 단위 {@link FigmaStylesResponse}와 달리 Team 전체 Library Inventory를 대상으로 하므로
 * 결과가 방대할 수 있어 {@code page_size}/{@code after} cursor 기반 pagination을 실제로 지원한다.
 */
public record FigmaTeamStylesResponse(
        @JsonProperty("meta")
        Meta meta,

        @JsonProperty("error")
        boolean error,

        @JsonProperty("status")
        int status
) {
    public record Meta(
            @JsonProperty("styles")
            List<StyleRef> styles,

            @JsonProperty("cursor")
            Cursor cursor
    ) {
        public Meta {
            styles = styles == null ? List.of() : List.copyOf(styles);
        }
    }

    public record Cursor(
            @JsonProperty("before")
            Long before,

            @JsonProperty("after")
            Long after
    ) {}

    public record StyleRef(
            @JsonProperty("key")
            String key,

            @JsonProperty("file_key")
            String fileKey,

            @JsonProperty("node_id")
            String nodeId,

            @JsonProperty("style_type")
            String styleType,

            @JsonProperty("name")
            String name,

            @JsonProperty("description")
            String description
    ) {}
}
