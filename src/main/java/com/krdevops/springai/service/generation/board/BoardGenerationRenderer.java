package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationWarning;
import com.krdevops.springai.service.generation.model.ProcessorStep;
import com.krdevops.springai.service.generation.model.RenderRequest;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.model.FailurePolicy;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.processor.SharedProcessorIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** BoardGenerationPlan을 파일별 Source 실행 계획으로 변환한다. */
@Component
@RequiredArgsConstructor
public class BoardGenerationRenderer {

    private final BoardTemplateRenderer templateRenderer;

    public RenderedGenerationPlan render(BoardGenerationPlan plan, BoardGenerationCommand command) {
        if (plan.failed()) {
            throw new IllegalArgumentException("실패한 BoardGenerationPlan은 렌더링할 수 없습니다: "
                    + plan.failure().summary());
        }

        String pkgSub = command.packageName().replace("egovframework.let.", "").replace('.', '/');
        BoardTemplateModel model = plan.model();
        List<RenderedFilePlan> files = new ArrayList<>();
        for (BoardLayerDefinition layer : BoardLayerDefinition.forViewType(plan.viewType())) {
            if (plan.viewType() == CrudViewType.THYMELEAF
                    && BoardLayerDefinition.isLayoutLayer(layer.layerKey())
                    && plan.layoutMode() != CrudLayoutMode.CREATE) {
                continue;
            }

            String fileName = BoardLayerDefinition.resolveFileName(
                    layer.layerKey(), command.domain(), layer.fileNameSuffix());
            String subPath = layer.resolveSubPath(pkgSub, model.domainLc());
            Path target = Path.of(command.outputPath().toString(), subPath + fileName);
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
                "board", command.database(), plan.tables().mainTable(), command.domain(), command.packageName(),
                command.outputPath().toString(), command.egovVersion(), plan.viewType().value(),
                Map.of(BoardGenerationAttributes.MODEL, model,
                        BoardGenerationAttributes.VIEW_TYPE, plan.viewType(),
                        "tables", plan.tables(), "metadata", plan.metadata()));
        List<GenerationWarning> warnings = plan.warnings().stream().map(GenerationWarning::new).toList();
        List<ProcessorStep> processors = List.of(
                new ProcessorStep(BoardCssProcessor.ID, GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE),
                new ProcessorStep(BoardEntryPointProcessor.ID, GenerationStage.POST_WRITE, 110, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.THYMELEAF_RUNTIME,
                        GenerationStage.POST_WRITE, 200, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.CONTROLLER_SCAN,
                        GenerationStage.POST_WRITE, 210, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.MYBATIS_RUNTIME,
                        GenerationStage.POST_WRITE, 300, FailurePolicy.CONTINUE));
        return new RenderedGenerationPlan(context, files, processors, warnings);
    }
}
