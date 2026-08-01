package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.masterdetail.MasterDetailLayerDefinition;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifier;
import com.krdevops.springai.service.generation.pipeline.VerificationResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Master/Detail 생성 레이어 누락을 검사한다. */
@Component
public class MasterDetailGeneratedContractVerifier implements GenerationVerifier {
    @Override public String id() { return "masterDetailGeneratedContractVerifier"; }
    @Override public GenerationStage stage() { return GenerationStage.VERIFY; }
    @Override public int order() { return 200; }
    @Override public boolean supports(GenerationContext context) { return "master-detail".equals(context.feature()); }

    @Override
    public VerificationResult verify(GenerationProcessingContext context) {
        CrudViewType viewType = CrudViewType.from(context.context().viewType());
        com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel model =
                context.context().attribute("masterDetail.model");
        List<String> expected = MasterDetailLayerDefinition.forViewType(viewType).stream()
                .filter(layer -> !(viewType == CrudViewType.THYMELEAF
                        && MasterDetailLayerDefinition.isLayoutLayer(layer.layerKey())))
                .map(layer -> MasterDetailLayerDefinition.resolveFileName(layer,
                        context.context().domain(), model.detail().domain()))
                .toList();
        List<String> missing = expected.stream()
                .filter(name -> !context.execution().succeededNames().contains(name)).toList();
        if (missing.isEmpty()) return VerificationResult.none();
        return new VerificationResult("\n\n[Master/Detail 생성 계약 감사]\n누락 파일: " + String.join(", ", missing),
                missing.stream().map(name -> new GenerationFailure(id(), "누락 파일 — " + name)).toList());
    }

}
