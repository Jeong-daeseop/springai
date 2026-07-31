package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;

import java.util.List;

/**
 * {@link CrudGenerationPlanner} 산출물 — Blueprint 또는 Preflight 실패 중 하나만 채워진다.
 *
 * <p>{@link #metadata}/{@link #model}/{@link #warnings}는 성공 경로에서 결과 VO의 14개 필드를
 * 조립할 때 필요해 함께 실어 나른다.
 */
public record CrudGenerationPlan(
        GenerationBlueprint blueprint,
        CrudPlanFailure failure,
        CrudProgramMetadata metadata,
        CrudTemplateModel model,
        List<String> warnings
) {
    public CrudGenerationPlan {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean failed() {
        return failure != null;
    }

    public static CrudGenerationPlan rejected(CrudPlanFailure failure) {
        return new CrudGenerationPlan(null, failure, null, null, List.of());
    }
}
