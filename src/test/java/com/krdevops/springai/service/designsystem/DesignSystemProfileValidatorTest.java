package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentBinding;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesignSystemProfileValidatorTest {

    private final DesignSystemProfileValidator validator = new DesignSystemProfileValidator();

    @Test
    void detectsBoundComponentWithoutComponentSetKey() {
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0", "2026.07", null, DesignSystemProfile.Status.DRAFT,
                Map.of("krds.button", new ComponentBinding(null, ComponentBinding.BindingStatus.BOUND)),
                Map.of());

        List<DesignSystemIssue> issues = validator.validate(profile);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("BOUND_COMPONENT_WITHOUT_KEY");
    }

    @Test
    void detectsBoundVariableWithoutVariableId() {
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0", "2026.07", null, DesignSystemProfile.Status.DRAFT,
                Map.of(),
                Map.of("color.primary", new VariableBinding(null, "Colors", ComponentBinding.BindingStatus.BOUND)));

        List<DesignSystemIssue> issues = validator.validate(profile);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("BOUND_VARIABLE_WITHOUT_ID");
    }

    @Test
    void publishedWithoutLibraryFileKeyIsError() {
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0", "2026.07", null, DesignSystemProfile.Status.PUBLISHED,
                Map.of(), Map.of());

        List<DesignSystemIssue> issues = validator.validate(profile);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("PUBLISHED_WITHOUT_FILE_KEY");
    }

    @Test
    void fullyBoundPublishedProfileProducesNoIssues() {
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0", "2026.07", "FILE_KEY", DesignSystemProfile.Status.PUBLISHED,
                Map.of("krds.button", new ComponentBinding("BUTTON_KEY", ComponentBinding.BindingStatus.BOUND)),
                Map.of("color.primary", new VariableBinding("VAR_ID", "Colors", ComponentBinding.BindingStatus.BOUND)));

        assertThat(validator.validate(profile)).isEmpty();
    }
}
