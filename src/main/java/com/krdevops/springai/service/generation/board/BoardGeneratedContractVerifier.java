package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifier;
import com.krdevops.springai.service.generation.pipeline.VerificationResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Board 레이어가 빠짐없이 저장되었는지 검사하는 전용 계약 감사. */
@Component
public class BoardGeneratedContractVerifier implements GenerationVerifier {

    @Override
    public String id() {
        return "boardGeneratedContractVerifier";
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.VERIFY;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean supports(GenerationContext context) {
        return "board".equals(context.feature());
    }

    @Override
    public VerificationResult verify(GenerationProcessingContext context) {
        CrudViewType viewType = CrudViewType.from(context.context().viewType());
        List<String> expected = BoardLayerDefinition.forViewType(viewType).stream()
                .filter(layer -> !(viewType == CrudViewType.THYMELEAF
                        && BoardLayerDefinition.isLayoutLayer(layer.layerKey())))
                .map(layer -> BoardLayerDefinition.resolveFileName(
                        layer.layerKey(), context.context().domain(), layer.fileNameSuffix()))
                .toList();
        List<String> missing = expected.stream()
                .filter(name -> !context.execution().succeededNames().contains(name))
                .toList();
        if (missing.isEmpty()) {
            return VerificationResult.none();
        }
        return new VerificationResult(
                "\n\n[게시판 생성 계약 감사]\n누락 파일: " + String.join(", ", missing),
                missing.stream().map(name -> new GenerationFailure(id(), "누락 파일 — " + name)).toList());
    }
}
