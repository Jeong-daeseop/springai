package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.KrdsAssetVerifier;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CRUD 생성 전에 KRDS 원본 CSS/JavaScript 자산이 함께 존재하는지 검증한다.
 */
@Component
public class KrdsAssetVerificationProcessor implements GenerationStageProcessor {

    static final String ID = "krdsAssetVerificationProcessor";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.PRE_WRITE;
    }

    @Override
    public boolean supports(GenerationContext context) {
        return true;
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        if (KrdsAssetVerifier.hasCompleteAssets(context.context().outputPath())) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed("KRDS 원본 자산 없음",
                List.of(new GenerationFailure(ID,
                        "_ds_bundle.css/krds.min.js가 없습니다 — "
                                + "ProjectInitializrTool.initializeProject()를 먼저 실행하세요.")));
    }
}
