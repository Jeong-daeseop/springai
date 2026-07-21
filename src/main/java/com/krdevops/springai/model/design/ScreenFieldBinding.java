package com.krdevops.springai.model.design;

public record ScreenFieldBinding(
        String id,
        String label,
        UiFieldRole role,
        FieldSource source,
        boolean visible,
        boolean required,
        boolean searchable,
        boolean sortable,
        String control,
        double confidence
) {
}
