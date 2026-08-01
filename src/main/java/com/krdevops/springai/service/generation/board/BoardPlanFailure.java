package com.krdevops.springai.service.generation.board;

import java.util.List;

/** 게시판 Blueprint를 만들기 전에 생성이 중단된 이유. */
public record BoardPlanFailure(Kind kind, String summary, List<String> messages) {
    public BoardPlanFailure {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public enum Kind {
        TABLE_NOT_FOUND,
        INVALID_PACKAGE,
        METADATA_BLOCKED,
        ALIAS_CONFLICT,
        LAYOUT_MISSING
    }
}
