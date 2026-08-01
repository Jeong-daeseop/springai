package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardGenerationRendererTest {

    @Mock BoardTemplateRenderer templateRenderer;

    @Test
    void reuseSkipsLayoutLayersAndPreservesBoardFileCount() {
        BoardTemplateModel model = org.mockito.Mockito.mock(BoardTemplateModel.class);
        when(model.domainLc()).thenReturn("bbs");
        when(templateRenderer.renderByLayerKey(anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn("html");

        BoardProgramMetadata metadata = org.mockito.Mockito.mock(BoardProgramMetadata.class);
        BoardGenerationPlan plan = new BoardGenerationPlan(
                new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", "LETTNFILE", "LETTNFILEDETAIL"),
                Map.of(), metadata, model, null, CrudViewType.THYMELEAF, CrudLayoutMode.REUSE,
                new ThymeleafLayoutValidator.LayoutReference("layout/default", "layout/breadcrumb", "layout"),
                List.of(), null);
        BoardGenerationCommand command = new BoardGenerationCommand(
                "com", "Bbs", "egovframework.let.bbs", Path.of("/tmp/out"),
                null, null, null, null, null, "5.0", "thymeleaf", null, null, null, null);

        var result = new BoardGenerationRenderer(templateRenderer).render(plan, command);

        assertThat(result.files()).hasSize(
                (int) BoardLayerDefinition.forViewType(CrudViewType.THYMELEAF).stream()
                        .filter(layer -> !BoardLayerDefinition.isLayoutLayer(layer.layerKey())).count());
        assertThat(result.files()).allMatch(file -> file.rendered());
        assertThat(result.context().feature()).isEqualTo("board");
    }
}
