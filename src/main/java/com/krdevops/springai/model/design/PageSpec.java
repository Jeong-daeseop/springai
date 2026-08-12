package com.krdevops.springai.model.design;

import java.util.List;
import java.util.Arrays;

public record PageSpec(
        String id,
        String template,
        List<ScreenFieldBinding> fields,
        List<ScreenActionSpec> actions,
        FieldSelectionSource selectionSource
) {
    public PageSpec {
        fields = fields == null ? List.of() : List.copyOf(fields);
        actions = actions == null ? List.of() : List.copyOf(actions);
        selectionSource = selectionSource == null ? FieldSelectionSource.DEFAULT : selectionSource;
    }

    /** selectionSource 도입 전 Java 호출자와 저장 명세 호환. */
    public PageSpec(String id, String template, List<ScreenFieldBinding> fields, List<ScreenActionSpec> actions) {
        this(id, template, fields, actions, FieldSelectionSource.DEFAULT);
    }

    /** v1 Java 호출부를 명시적으로 Migration할 때만 사용하는 변환 Factory. */
    public static List<ScreenActionSpec> migrateActions(String... legacyActions) {
        return legacyActions == null ? List.of()
                : Arrays.stream(legacyActions).map(ScreenActionSpec::fromLegacyCommand).toList();
    }
}
