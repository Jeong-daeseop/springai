package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.masterdetail.MasterDetailLayerDefinition;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDetailGenerationRendererTest {

    @Mock MasterDetailTemplateRenderer templateRenderer;

    @Test
    void reuseSkipsLayoutLayersAndPreservesMasterDetailFileCount() {
        MasterDetailTemplateModel model = org.mockito.Mockito.mock(MasterDetailTemplateModel.class);
        var master = org.mockito.Mockito.mock(com.krdevops.springai.model.crud.CrudTemplateModel.class);
        var detail = org.mockito.Mockito.mock(com.krdevops.springai.model.crud.CrudTemplateModel.class);
        when(model.domain()).thenReturn("Order");
        when(model.domainLc()).thenReturn("order");
        when(model.detail()).thenReturn(detail);
        when(detail.domain()).thenReturn("OrderItem");
        when(templateRenderer.renderByLayerKey(anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn("html");
        var plan = new MasterDetailGenerationPlan(model, null, CrudViewType.THYMELEAF, CrudLayoutMode.REUSE,
                new ThymeleafLayoutValidator.LayoutReference("layout/default", "layout/breadcrumb", "layout"),
                List.of(), null);
        var command = new MasterDetailGenerationCommand("com", "MASTER", "DETAIL", "Order",
                "egovframework.let.order", Path.of("/tmp/out"), "auto", "5.0", "thymeleaf", null, null);

        var result = new MasterDetailGenerationRenderer(templateRenderer).render(plan, command);

        assertThat(result.files()).hasSize((int) MasterDetailLayerDefinition.forViewType(CrudViewType.THYMELEAF).stream()
                .filter(layer -> !MasterDetailLayerDefinition.isLayoutLayer(layer.layerKey())).count());
        assertThat(result.files()).allMatch(file -> file.rendered());
        assertThat(result.context().feature()).isEqualTo("master-detail");
    }
}
