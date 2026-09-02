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
import java.util.Map;

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
                8, 1.0, "rgba(255,87,51,1.00)", null, null, null, List.of(),
                List.of(new UiDesignSpec.PaintSpec("SOLID", true, 0.5, "rgba(255,87,51,0.80)")), List.of());
        ScreenSpecification specification = new ScreenSpecification(
                "spec-3", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, LocalDateTime.now(),
                null, null, List.of(), List.of(button));

        String prompt = formatter.format(specification);

        assertThat(prompt).contains("[opacity 적용 규칙]:");
        assertThat(prompt).contains(
                "componentStyles/geometry의 rgba alpha에는 color.a × paint.opacity가 이미 반영되어 있습니다.");
        assertThat(prompt).contains("geometry.opacity는 해당 노드의 로컬 opacity이며 null은 1.0입니다.");
        assertThat(prompt).contains(
                "cumulativeNodeOpacity = ancestor opacity × ... × current node opacity");
        assertThat(prompt).contains(
                "effectivePaintAlpha = rgba alpha × cumulativeNodeOpacity");
        assertThat(prompt).contains("geometry.fills/strokes 배열은 Figma paint 순서를 보존하며 첫 유효 SOLID 색상보다 우선합니다.");
        assertThat(prompt).contains("\"fills\":[{\"type\":\"SOLID\"");
        assertThat(prompt).contains(
                "paint/node opacity를 rgba와 CSS opacity 양쪽에 중복 적용하지 마세요.");
        assertThat(prompt).contains(
                "실제 노드는 componentGeometry 값을 우선하고 componentStyles는 geometry 스타일이 없을 때만 fallback으로 사용하세요.");
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
        assertThat(prompt).doesNotContain("[opacity 적용 규칙]");
    }

    @Test
    void includesTokensBlockWhenPresent() {
        ScreenSpecification specification = new ScreenSpecification(
                "spec-5", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, LocalDateTime.now(),
                null, null, List.of(), List.of(),
                Map.of("backgroundColor", "rgba(255,255,255,1.00)"));

        String prompt = formatter.format(specification);

        assertThat(prompt).contains("tokens(화면 전체 배경색·폰트 참고값):");
        assertThat(prompt).contains("- backgroundColor = rgba(255,255,255,1.00)");
    }

    @Test
    void omitsTokensBlockWhenEmpty() {
        ScreenSpecification specification = new ScreenSpecification(
                "spec-6", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LocalDateTime.now());

        String prompt = formatter.format(specification);

        assertThat(prompt).doesNotContain("tokens(");
    }

    @Test
    void includesGradientCssHintsForGeometryPaints() {
        UiDesignSpec.PaintSpec gradient = new UiDesignSpec.PaintSpec("GRADIENT_LINEAR", true, 1, null,
                List.of(new UiDesignSpec.PaintSpec.GradientStop(0, "rgba(0,0,0,1.00)"),
                        new UiDesignSpec.PaintSpec.GradientStop(1, "rgba(255,255,255,1.00)")), List.of());
        UiDesignSpec.NodeGeometry geometry = new UiDesignSpec.NodeGeometry(
                "1:9", "RECTANGLE", "Gradient", 0, 0, 10, 10, null, null, null, null,
                null, null, List.of(), List.of(gradient), List.of());
        ScreenSpecification specification = new ScreenSpecification(
                "spec-gradient", 1, ScreenSpecStatus.APPROVED, "화면", "crud", "CRUD_LIST",
                "com", "T", List.of(DataSourceSpec.primary("com", "T")), List.of(), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN, ActionPlacement.TOP_RIGHT,
                SearchPanelPlacement.NONE, LocalDateTime.now(), null, null, List.of(), List.of(geometry));

        assertThat(formatter.format(specification)).contains("gradientCssHints(자동 변환 참고값):")
                .contains("1:9 background: linear-gradient");
    }
}
