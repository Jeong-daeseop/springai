package com.krdevops.springai.model.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.designsystem.ComponentBinding;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableBinding;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** R1-T01: FigmaScreenSpec/FigmaExportBundle의 DTO 직렬화 round-trip 검증. */
class FigmaModelSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void figmaScreenSpecRoundTripsThroughJson() throws Exception {
        FigmaScreenSpec spec = sampleFigmaScreenSpec();

        String json = objectMapper.writeValueAsString(spec);
        FigmaScreenSpec restored = objectMapper.readValue(json, FigmaScreenSpec.class);

        assertThat(restored).isEqualTo(spec);
    }

    @Test
    void figmaExportBundleRoundTripsThroughJson() throws Exception {
        FigmaExportBundle bundle = new FigmaExportBundle(
                sampleFigmaScreenSpec(),
                new DesignSystemProfileSnapshot(sampleProfile(), LocalDateTime.now()),
                new ComponentRegistrySnapshot(sampleRegistry(), LocalDateTime.now()),
                new FigmaExportMetadata(LocalDateTime.now(), FigmaScreenSpec.SCHEMA_VERSION, 3, "1.0", "2026.07"));

        String json = objectMapper.writeValueAsString(bundle);
        FigmaExportBundle restored = objectMapper.readValue(json, FigmaExportBundle.class);

        assertThat(restored).isEqualTo(bundle);
    }

    private FigmaScreenSpec sampleFigmaScreenSpec() {
        FigmaNodeSpec searchField = new FigmaNodeSpec(
                "user-list/search/userName", FigmaNodeSpec.NodeType.COMPONENT, "krds.textField",
                Map.of("label", "사용자명", "required", true), List.of());
        FigmaNodeSpec root = new FigmaNodeSpec(
                "user-list-page", FigmaNodeSpec.NodeType.PAGE, "egov.listPage",
                Map.of("title", "사용자 목록"), List.of(searchField));
        return new FigmaScreenSpec(
                "user-list", 1, "spec-user-management", 3,
                FigmaScreenType.LIST, LayoutPattern.STANDARD,
                "사용자 목록", "/user/list", "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("krds", "1.0", "2026.07"),
                root,
                List.of());
    }

    private DesignSystemProfile sampleProfile() {
        return new DesignSystemProfile(
                "krds", "KRDS Design System", "1.0", "2026.07", "KRDS_FIGMA_LIBRARY_FILE_KEY",
                DesignSystemProfile.Status.PUBLISHED,
                Map.of("krds.button", new ComponentBinding("FIGMA_BUTTON_KEY", ComponentBinding.BindingStatus.BOUND)),
                Map.of("color.primary", new VariableBinding("VAR_COLOR_PRIMARY", "Colors", ComponentBinding.BindingStatus.BOUND)));
    }

    private ComponentRegistry sampleRegistry() {
        return new ComponentRegistry(
                "krds", "1.0", "2026.07",
                new ComponentRegistry.LibraryRef("KRDS_FIGMA_LIBRARY_FILE_KEY", "KRDS Design System"),
                Map.of("krds.button", new ComponentRegistryEntry(
                        "FIGMA_BUTTON_COMPONENT_SET_KEY",
                        Map.of("label", new ComponentRegistryEntry.PropertyMapping(
                                "Label", ComponentRegistryEntry.PropertyType.TEXT, null)))));
    }
}
