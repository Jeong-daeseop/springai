package com.krdevops.springai.model.design;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenSpecificationTest {

    @Test
    void legacyThirteenArgConstructorDefaultsFormColumnLayoutToSingleColumn() {
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(),
                LayoutDensity.COMPACT, LocalDateTime.now());

        assertThat(spec.formColumnLayout()).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
        assertThat(spec.layoutDensity()).isEqualTo(LayoutDensity.COMPACT);
    }

    @Test
    void legacyTwelveArgConstructorDefaultsLayoutDensityAndFormColumnLayout() {
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(), LocalDateTime.now());

        assertThat(spec.layoutDensity()).isEqualTo(LayoutDensity.STANDARD);
        assertThat(spec.formColumnLayout()).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
    }

    @Test
    void nullFormColumnLayoutNormalizesToSingleColumn() {
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, null, LocalDateTime.now());

        assertThat(spec.formColumnLayout()).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
    }

    @Test
    void withStatusPreservesFormColumnLayout() {
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.TWO_COLUMN, LocalDateTime.now());

        ScreenSpecification approved = spec.withStatus(ScreenSpecStatus.APPROVED);

        assertThat(approved.formColumnLayout()).isEqualTo(FormColumnLayout.TWO_COLUMN);
        assertThat(approved.status()).isEqualTo(ScreenSpecStatus.APPROVED);
    }

    @Test
    void legacyFourteenArgConstructorDefaultsActionAndSearchPanelPlacement() {
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.TWO_COLUMN, LocalDateTime.now());

        assertThat(spec.actionPlacement()).isEqualTo(ActionPlacement.TOP_RIGHT);
        assertThat(spec.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
    }

    @Test
    void nullActionAndSearchPanelPlacementNormalizeToDefaults() {
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN, null, null, LocalDateTime.now());

        assertThat(spec.actionPlacement()).isEqualTo(ActionPlacement.TOP_RIGHT);
        assertThat(spec.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
    }

    @Test
    void withStatusPreservesActionAndSearchPanelPlacement() {
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.BOTTOM_RIGHT, SearchPanelPlacement.NONE, LocalDateTime.now());

        ScreenSpecification approved = spec.withStatus(ScreenSpecStatus.APPROVED);

        assertThat(approved.actionPlacement()).isEqualTo(ActionPlacement.BOTTOM_RIGHT);
        assertThat(approved.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.NONE);
    }

    @Test
    void withStatusAndWithDesignContextPreserveTokens() {
        Map<String, String> tokens = Map.of("backgroundColor", "rgba(255,255,255,1.00)");
        ScreenSpecification spec = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                "com", "TBL", List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, LocalDateTime.now(),
                null, null, List.of(), List.of(), tokens);

        ScreenSpecification afterStatusChange = spec.withStatus(ScreenSpecStatus.APPROVED);
        assertThat(afterStatusChange.tokens()).isEqualTo(tokens);

        ScreenSpecification afterDesignContext = spec.withDesignContext(
                new com.krdevops.springai.model.contract.VersionedArtifactReference(
                        "artifact-1", "UI_DESIGN_SPEC", "1.0",
                        "a".repeat(64), null),
                null, List.of());
        assertThat(afterDesignContext.tokens()).isEqualTo(tokens);
    }
}
