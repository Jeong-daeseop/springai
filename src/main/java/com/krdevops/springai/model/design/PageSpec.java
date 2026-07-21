package com.krdevops.springai.model.design;

import java.util.List;

public record PageSpec(
        String id,
        String template,
        List<ScreenFieldBinding> fields,
        List<String> actions,
        FieldSelectionSource selectionSource
) {
    public PageSpec {
        fields = fields == null ? List.of() : List.copyOf(fields);
        actions = actions == null ? List.of() : List.copyOf(actions);
        selectionSource = selectionSource == null ? FieldSelectionSource.DEFAULT : selectionSource;
    }

    /** selectionSource 도입 전 Java 호출자와 저장 명세 호환. */
    public PageSpec(String id, String template, List<ScreenFieldBinding> fields, List<String> actions) {
        this(id, template, fields, actions, FieldSelectionSource.DEFAULT);
    }
}
