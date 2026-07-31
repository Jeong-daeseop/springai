package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.model.RenderRequest;

/** {@code CrudTemplateRenderer.renderByLayerKey(...)} 호출에 필요한 인자 묶음. */
public record CrudRenderRequest(
        String layerKey,
        CrudTemplateModel model,
        CrudViewType viewType,
        ThymeleafLayoutValidator.LayoutReference layoutReference,
        CrudLayoutMode layoutMode
) implements RenderRequest {
}
