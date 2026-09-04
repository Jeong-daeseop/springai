package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.designsystem.KrdsComponentFragmentWriter;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 픽셀 재현(V2_APPLY) 경로 전용 — CRUD 화면이 {@code th:replace}로 부르는 KRDS 컴포넌트 fragment
 * 6종을 대상 프로젝트에 기록한다. {@link KrdsComponentFragmentWriter} 위임.
 *
 * <p>{@code model.designComponents()}가 비어 있으면(=V2_PREVIEW 이하, 승인 Mapping 미해석) 화면이
 * fragment를 참조하지 않으므로 {@link #supports}가 false를 반환해 조용히 제외된다 —
 * 이 경우 생성 산출물은 기존과 바이트 동일하다.
 */
@Component
@RequiredArgsConstructor
public class CrudComponentFragmentProcessor implements GenerationStageProcessor {

    static final String ID = "crudComponentFragmentProcessor";

    private final KrdsComponentFragmentWriter fragmentWriter;

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
        if (!CrudViewType.THYMELEAF.value().equals(context.viewType())) {
            return false;
        }
        CrudTemplateModel model = context.attribute(CrudGenerationAttributes.MODEL);
        return model != null && !model.designComponents().isEmpty();
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        KrdsComponentFragmentWriter.FragmentWriteResult result =
                fragmentWriter.ensureComponentFragments(context.context().outputPath());
        if (!result.failed()) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed("KRDS 컴포넌트 fragment 기록 실패",
                List.of(new GenerationFailure(ID, "templates/components — " + result.message())));
    }
}
