package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenSpecificationPromptFormatterTest {

    private final ScreenSpecificationPromptFormatter formatter =
            new ScreenSpecificationPromptFormatter(new ObjectMapper());

    @Test
    void includesComponentStylesBlockWhenPresent() {
        UiDesignSpec.ComponentSpec actionGroup = new UiDesignSpec.ComponentSpec(
                "ACTION_GROUP", List.of("Primary Button"), "rgba(255,87,51,1.00)", "rgba(0,0,0,1.00)");
        ScreenSpecification specification = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, LocalDateTime.now(),
                null, null, List.of(actionGroup));

        String prompt = formatter.format(specification);

        assertThat(prompt).contains("componentStyles:");
        assertThat(prompt).contains(
                "- ACTION_GROUP backgroundColor=rgba(255,87,51,1.00) borderColor=rgba(0,0,0,1.00)");
    }

    @Test
    void omitsComponentStylesBlockWhenEmpty() {
        ScreenSpecification specification = new ScreenSpecification(
                "spec-2", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LocalDateTime.now());

        String prompt = formatter.format(specification);

        assertThat(prompt).doesNotContain("componentStyles:");
    }

    @Test
    void includesComponentGeometryJsonBlockWhenPresent() {
        UiDesignSpec.NodeGeometry button = new UiDesignSpec.NodeGeometry(
                "1:2", "COMPONENT", "Primary Button", 1200, 760, 120, 44,
                8, 1.0, "rgba(255,87,51,1.00)", null, null, null, List.of());
        ScreenSpecification specification = new ScreenSpecification(
                "spec-3", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, LocalDateTime.now(),
                null, null, List.of(), List.of(button));

        String prompt = formatter.format(specification);

        assertThat(prompt).contains("componentGeometry(JSON");
        assertThat(prompt).contains("\"nodeId\":\"1:2\"");
        assertThat(prompt).contains("\"backgroundColor\":\"rgba(255,87,51,1.00)\"");
    }

    @Test
    void omitsComponentGeometryBlockWhenEmpty() {
        ScreenSpecification specification = new ScreenSpecification(
                "spec-4", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LocalDateTime.now());

        String prompt = formatter.format(specification);

        assertThat(prompt).doesNotContain("componentGeometry(JSON");
    }
}
