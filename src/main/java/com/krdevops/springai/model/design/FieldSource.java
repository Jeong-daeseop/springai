package com.krdevops.springai.model.design;

public record FieldSource(
        FieldSourceType type,
        String tableAlias,
        String column,
        String expression,
        String codeGroup
) {
    public static FieldSource column(String tableAlias, String column) {
        return new FieldSource(FieldSourceType.COLUMN, tableAlias, column, null, null);
    }

    public static FieldSource joinColumn(String tableAlias, String column) {
        return new FieldSource(FieldSourceType.JOIN_COLUMN, tableAlias, column, null, null);
    }

    public static FieldSource derived(String expression) {
        return new FieldSource(FieldSourceType.DERIVED, null, null, expression, null);
    }

    public static FieldSource commonCode(String tableAlias, String column, String codeGroup) {
        return new FieldSource(FieldSourceType.COMMON_CODE, tableAlias, column, null, codeGroup);
    }

    public static FieldSource unmapped() {
        return new FieldSource(FieldSourceType.UNMAPPED, null, null, null, null);
    }
}
