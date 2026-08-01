package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.ThymeleafLayoutValidator;

import java.util.List;

public record MasterDetailGenerationPlan(
        MasterDetailTemplateModel model,
        ScreenSpecification screenSpecification,
        CrudViewType viewType,
        CrudLayoutMode layoutMode,
        ThymeleafLayoutValidator.LayoutReference layoutReference,
        List<String> warnings,
        MasterDetailPlanFailure failure
) {
    public MasterDetailGenerationPlan {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean failed() { return failure != null; }

    public static MasterDetailGenerationPlan rejected(MasterDetailPlanFailure failure) {
        return new MasterDetailGenerationPlan(null, null, null, null, null, List.of(), failure);
    }
}
