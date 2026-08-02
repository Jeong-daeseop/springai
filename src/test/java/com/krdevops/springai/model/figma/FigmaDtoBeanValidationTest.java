package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.design.ScreenSpecStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaDtoBeanValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void exportRequestVersionUsesPositiveConstraint() {
        FigmaScreenExportRequest request = new FigmaScreenExportRequest(
                "spec-user", 0, "user-list", "ftc-krds",
                "DESKTOP", FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("screenSpecificationVersion");
    }

    @Test
    void screenSpecCascadesIntoLogicalNodeValidation() {
        FigmaScreenSpec spec = new FigmaScreenSpec(
                "user-list", 1, "spec-user", 1,
                FigmaScreenType.LIST, LayoutPattern.STANDARD, "사용자 목록",
                null, "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("ftc-krds", "1.0.0", "registry-1"),
                new FigmaNodeSpec("user-list", FigmaNodeSpec.NodeType.PAGE,
                        "egov.listPage", Map.of(), List.of()),
                List.of());

        assertThat(validator.validate(spec))
                .extracting(violation -> violation.getPropertyPath().toString())
                .isEmpty();
    }
}
