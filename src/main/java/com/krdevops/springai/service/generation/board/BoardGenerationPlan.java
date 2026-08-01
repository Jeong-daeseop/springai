package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.ThymeleafLayoutValidator;

import java.util.List;
import java.util.Map;

/** Board Planner가 계산한 읽기 전용 실행 계획. 파일·DB 쓰기는 포함하지 않는다. */
public record BoardGenerationPlan(
        BoardTableSet tables,
        Map<String, List<Map<String, Object>>> schemas,
        BoardProgramMetadata metadata,
        BoardTemplateModel model,
        ScreenSpecification screenSpecification,
        CrudViewType viewType,
        CrudLayoutMode layoutMode,
        ThymeleafLayoutValidator.LayoutReference layoutReference,
        List<String> warnings,
        BoardPlanFailure failure
) {
    public BoardGenerationPlan {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean failed() {
        return failure != null;
    }

    public static BoardGenerationPlan rejected(BoardPlanFailure failure) {
        return new BoardGenerationPlan(null, Map.of(), null, null, null, null, null,
                null, List.of(), failure);
    }
}
