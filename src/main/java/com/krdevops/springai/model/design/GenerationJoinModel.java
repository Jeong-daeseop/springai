package com.krdevops.springai.model.design;

/** FreeMarker가 안전하게 렌더링할 수 있도록 검증 완료된 JOIN 절을 분리한 모델. */
public record GenerationJoinModel(
        String joinType,
        String schema,
        String table,
        String alias,
        String onExpression
) {
}
