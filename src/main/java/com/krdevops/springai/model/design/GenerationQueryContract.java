package com.krdevops.springai.model.design;

import com.krdevops.springai.model.crud.FieldModel;

import java.util.List;

public record GenerationQueryContract(
        List<GenerationJoinModel> joins,
        List<GenerationProjectionModel> projections
) {
    public GenerationQueryContract {
        joins = joins == null ? List.of() : List.copyOf(joins);
        projections = projections == null ? List.of() : List.copyOf(projections);
    }

    public static GenerationQueryContract empty() {
        return new GenerationQueryContract(List.of(), List.of());
    }

    public boolean hasJoins() {
        return !joins.isEmpty();
    }

    public List<FieldModel> displayFields() {
        return projections.stream().map(GenerationProjectionModel::field).toList();
    }
}
