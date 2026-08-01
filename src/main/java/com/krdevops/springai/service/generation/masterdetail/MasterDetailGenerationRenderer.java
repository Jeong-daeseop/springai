package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.masterdetail.MasterDetailLayerDefinition;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationWarning;
import com.krdevops.springai.service.generation.model.RenderRequest;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.model.ProcessorStep;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.model.FailurePolicy;
import com.krdevops.springai.service.generation.pipeline.processor.SharedProcessorIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** MasterDetailGenerationPlan을 파일별 렌더링 계획으로 변환한다. */
@Component
@RequiredArgsConstructor
public class MasterDetailGenerationRenderer {

    private final MasterDetailTemplateRenderer templateRenderer;

    public RenderedGenerationPlan render(MasterDetailGenerationPlan plan,
                                         MasterDetailGenerationCommand command) {
        if (plan.failed()) {
            throw new IllegalArgumentException("실패한 MasterDetailGenerationPlan은 렌더링할 수 없습니다: "
                    + plan.failure().summary());
        }
        MasterDetailTemplateModel model = plan.model();
        String pkgSub = command.packageName().replace("egovframework.let.", "").replace('.', '/');
        List<RenderedFilePlan> files = new ArrayList<>();
        for (MasterDetailLayerDefinition layer : MasterDetailLayerDefinition.forViewType(plan.viewType())) {
            if (plan.viewType() == CrudViewType.THYMELEAF
                    && MasterDetailLayerDefinition.isLayoutLayer(layer.layerKey())
                    && plan.layoutMode() != CrudLayoutMode.CREATE) {
                continue;
            }
            String fileName = MasterDetailLayerDefinition.resolveFileName(layer, model.domain(), model.detail().domain());
            Path target = Path.of(command.outputPath().toString(),
                    layer.resolveSubPath(pkgSub, model.domainLc()) + fileName);
            FileBlueprint blueprint = new FileBlueprint(layer.layerKey(), fileName, target, new RenderRequest() { });
            try {
                String source = plan.viewType() == CrudViewType.THYMELEAF
                        ? templateRenderer.renderByLayerKey(layer.layerKey(), model,
                        plan.layoutReference().layoutView(), plan.layoutReference().breadcrumbView(),
                        plan.layoutReference().layoutBasePath(), plan.layoutMode())
                        : templateRenderer.renderByLayerKey(layer.layerKey(), model);
                files.add(RenderedFilePlan.rendered(blueprint, source));
            } catch (Exception exception) {
                files.add(RenderedFilePlan.failed(blueprint, new GenerationFailure(
                        fileName, fileName + " — 오류: " + exception.getMessage())));
            }
        }
        GenerationContext context = new GenerationContext(
                "master-detail", command.database(), command.masterTable(), command.domain(), command.packageName(),
                command.outputPath().toString(), command.egovVersion(), plan.viewType().value(),
                Map.of("masterDetail.model", model, "masterDetail.viewType", plan.viewType()));
        List<ProcessorStep> processors = List.of(
                new ProcessorStep(MasterDetailMainControllerProcessor.ID,
                        GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE),
                new ProcessorStep(MasterDetailServletScanProcessor.ID,
                        GenerationStage.POST_WRITE, 110, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.THYMELEAF_RUNTIME,
                        GenerationStage.POST_WRITE, 200, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.CONTROLLER_SCAN,
                        GenerationStage.POST_WRITE, 210, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.MYBATIS_RUNTIME,
                        GenerationStage.POST_WRITE, 300, FailurePolicy.CONTINUE));
        return new RenderedGenerationPlan(context, files, processors,
                plan.warnings().stream().map(GenerationWarning::new).toList());
    }
}
