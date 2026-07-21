package com.krdevops.springai.service;

import com.krdevops.springai.model.capture.SafeDesignProjection;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RenderedDesignSpecMapperTest {

    @Test
    void emitsOnlyScreenSpecificationLayoutContractValues() {
        var projection = new SafeDesignProjection("목록", 1440, 1440,
                List.of(new SafeDesignProjection.SafeComponent("SEARCH_PANEL", 0.9, List.of("role=search"))),
                List.of(), List.of(), Map.of(), List.of());

        var layout = new RenderedDesignSpecMapper().map(projection, "crud").layout();

        assertThat(FormColumnLayout.from(layout.formColumnLayout()))
                .isEqualTo(FormColumnLayout.SINGLE_COLUMN);
        assertThat(ActionPlacement.from(layout.actionPlacement())).isEqualTo(ActionPlacement.TOP_RIGHT);
        assertThat(SearchPanelPlacement.from(layout.searchPanelPlacement()))
                .isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
    }
}
