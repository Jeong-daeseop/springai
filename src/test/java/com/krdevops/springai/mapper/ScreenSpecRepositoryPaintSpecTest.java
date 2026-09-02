package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ScreenSpecRepositoryPaintSpecTest {

    @Test
    void saveAndFindVersionRoundTripPreservesPaintArrays() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ScreenSpecRepository repository = new ScreenSpecRepository(
                jdbc, new ObjectMapper(), new LegacyRepositoryDdlProperties());
        UiDesignSpec.NodeGeometry geometry = new UiDesignSpec.NodeGeometry(
                "1:2", "COMPONENT", "Primary Button", 10, 20, 120, 44,
                8, 0.8, "rgba(0,80,200,1.00)", null, null, null, List.of(),
                List.of(new UiDesignSpec.PaintSpec("SOLID", true, 0.5, "rgba(0,80,200,0.80)")),
                List.of(new UiDesignSpec.PaintSpec("IMAGE", true, 1.0, null)));
        ScreenSpecification original = new ScreenSpecification(
                "spec-paint", 1, ScreenSpecStatus.APPROVED, "공지", "board", "BOARD",
                "com", "LETTNBBS", List.of(DataSourceSpec.primary("com", "LETTNBBS")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, java.time.LocalDateTime.now(),
                null, null, List.of(), List.of(geometry), java.util.Map.of());

        repository.save(original);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        String json = (String) values.getValue()[5];
        when(jdbc.queryForList(anyString(), eq(String.class), eq("spec-paint"), eq(1)))
                .thenReturn(List.of(json));

        ScreenSpecification restored = repository.findVersion("spec-paint", 1).orElseThrow();

        assertThat(restored.componentGeometry().get(0).fills()).isEqualTo(geometry.fills());
        assertThat(restored.componentGeometry().get(0).strokes()).isEqualTo(geometry.strokes());
    }
}
