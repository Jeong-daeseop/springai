package com.krdevops.springai.model.design;

public record DataSourceSpec(
        String id,
        String schema,
        String table,
        String alias,
        boolean primary,
        String joinType,
        String joinExpression
) {
    public static DataSourceSpec primary(String schema, String table) {
        return new DataSourceSpec("primary", schema, table, "t", true, null, null);
    }
}
