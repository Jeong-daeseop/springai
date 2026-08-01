package com.krdevops.springai.service.generation.masterdetail;

import java.util.List;

public record MasterDetailPlanFailure(Kind kind, String summary, List<String> messages) {
    public enum Kind { INVALID_PACKAGE, TABLE_NOT_FOUND, RELATION_NOT_FOUND, LAYOUT_MISSING }

    public MasterDetailPlanFailure {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
