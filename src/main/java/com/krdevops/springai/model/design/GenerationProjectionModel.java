package com.krdevops.springai.model.design;

import com.krdevops.springai.model.crud.FieldModel;

/** JOIN/공통코드 표시 필드의 SELECT projection과 VO 필드를 함께 표현한다. */
public record GenerationProjectionModel(
        FieldModel field,
        String selectExpression
) {
}
