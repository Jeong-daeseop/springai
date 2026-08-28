package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DesignFidelityReport;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesignFidelityComparatorTest {

    private final DesignFidelityComparator comparator = new DesignFidelityComparator();

    @Test
    void identicalSpecsScoreFullMatchOnEveryDimension() {
        UiDesignSpec spec = spec("CRUD_LIST",
                List.of("TABLE", "SEARCH_PANEL"),
                List.of(UiFieldRole.TITLE, UiFieldRole.AUTHOR),
                List.of("CREATE", "SEARCH"));

        DesignFidelityReport report = comparator.compare("original-1", spec, "rendered-1", spec);

        assertThat(report.archetypeMatch()).isEqualTo(1.0);
        assertThat(report.componentOverlapRatio()).isEqualTo(1.0);
        assertThat(report.fieldRoleOverlapRatio()).isEqualTo(1.0);
        assertThat(report.actionOverlapRatio()).isEqualTo(1.0);
        assertThat(report.missingInRendered()).isEmpty();
        assertThat(report.extraInRendered()).isEmpty();
    }

    @Test
    void partiallyOverlappingSpecsScoreBetweenZeroAndOne() {
        UiDesignSpec original = spec("BOARD_LIST",
                List.of("TABLE", "SEARCH_PANEL"),
                List.of(UiFieldRole.TITLE, UiFieldRole.AUTHOR),
                List.of("CREATE", "SEARCH"));
        UiDesignSpec rendered = spec("BOARD_LIST",
                List.of("TABLE", "PAGINATION"),
                List.of(UiFieldRole.TITLE),
                List.of("CREATE", "DELETE"));

        DesignFidelityReport report = comparator.compare("original-1", original, "rendered-1", rendered);

        assertThat(report.archetypeMatch()).isEqualTo(1.0);
        assertThat(report.componentOverlapRatio()).isEqualTo(1.0 / 3.0);
        assertThat(report.fieldRoleOverlapRatio()).isEqualTo(1.0 / 2.0);
        assertThat(report.actionOverlapRatio()).isEqualTo(1.0 / 3.0);
        assertThat(report.missingInRendered())
                .contains("component:SEARCH_PANEL", "fieldRole:AUTHOR", "action:SEARCH");
        assertThat(report.extraInRendered())
                .contains("component:PAGINATION", "action:DELETE");
    }

    @Test
    void completelyDifferentSpecsScoreZeroOnEveryOverlapDimension() {
        UiDesignSpec original = spec("CRUD_LIST",
                List.of("TABLE"), List.of(UiFieldRole.TITLE), List.of("CREATE"));
        UiDesignSpec rendered = spec("BOARD_FORM",
                List.of("FORM"), List.of(UiFieldRole.STATUS), List.of("CANCEL"));

        DesignFidelityReport report = comparator.compare("original-1", original, "rendered-1", rendered);

        assertThat(report.archetypeMatch()).isEqualTo(0.0);
        assertThat(report.componentOverlapRatio()).isEqualTo(0.0);
        assertThat(report.fieldRoleOverlapRatio()).isEqualTo(0.0);
        assertThat(report.actionOverlapRatio()).isEqualTo(0.0);
        assertThat(report.missingInRendered())
                .contains("component:TABLE", "fieldRole:TITLE", "action:CREATE");
        assertThat(report.extraInRendered())
                .contains("component:FORM", "fieldRole:STATUS", "action:CANCEL");
    }

    private UiDesignSpec spec(
            String archetype, List<String> componentTypes, List<UiFieldRole> roles, List<String> actionTypes) {
        List<UiDesignSpec.ComponentSpec> components = componentTypes.stream()
                .map(type -> new UiDesignSpec.ComponentSpec(type, List.of()))
                .toList();
        List<UiDesignSpec.FieldHint> fieldHints = roles.stream()
                .map(role -> new UiDesignSpec.FieldHint(role.name().toLowerCase(), role.name(), role, "TEXT", 1.0))
                .toList();
        List<UiDesignSpec.ActionSpec> actions = actionTypes.stream()
                .map(type -> new UiDesignSpec.ActionSpec(type, "PRIMARY"))
                .toList();
        return new UiDesignSpec(archetype, null, components, actions, fieldHints,
                Map.of(), List.of(), List.of());
    }
}
