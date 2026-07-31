package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD 레이어를 기존 {@link CrudTemplateRenderer}로 렌더링한다.
 *
 * <p>레이어 하나가 실패해도 나머지는 계속 렌더링하고, 실패한 레이어는 순서를 유지한 채
 * {@code renderFailure}가 채워진 상태로 남는다 — Executor가 같은 순서로 순회하므로 기존의
 * 성공/실패 목록 순서가 그대로 재현된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrudGenerationRenderer implements GenerationRenderer {

    private final CrudTemplateRenderer crudTemplateRenderer;

    @Override
    public RenderedGenerationPlan render(GenerationBlueprint blueprint) {
        List<RenderedFilePlan> files = new ArrayList<>();
        for (FileBlueprint file : blueprint.files()) {
            CrudRenderRequest request = (CrudRenderRequest) file.renderRequest();
            try {
                files.add(RenderedFilePlan.rendered(file, renderSource(request)));
            } catch (Exception e) {
                files.add(RenderedFilePlan.failed(file, new GenerationFailure(
                        file.layerKey(), file.displayName() + " — 오류: " + e.getMessage())));
                log.error("[pipeline] 렌더링 실패: layer={}, error={}", file.layerKey(), e.getMessage());
            }
        }
        return new RenderedGenerationPlan(
                blueprint.context(), files, blueprint.processors(), blueprint.warnings());
    }

    private String renderSource(CrudRenderRequest request) {
        if (request.viewType() != CrudViewType.THYMELEAF) {
            return crudTemplateRenderer.renderByLayerKey(request.layerKey(), request.model());
        }
        return crudTemplateRenderer.renderByLayerKey(
                request.layerKey(), request.model(),
                request.layoutReference().layoutView(),
                request.layoutReference().breadcrumbView(),
                request.layoutReference().layoutBasePath(),
                request.layoutMode());
    }
}
