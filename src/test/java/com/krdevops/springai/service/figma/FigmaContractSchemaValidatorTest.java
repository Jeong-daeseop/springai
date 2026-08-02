package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaContractSchemaValidatorTest {

    private final FigmaContractSchemaValidator validator =
            new FigmaContractSchemaValidator(new ObjectMapper().findAndRegisterModules());

    @Test
    void 유효한_화면은_Schema_오류가_없다() {
        assertThat(validator.validate(screen("user-list", "user-list"))).isEmpty();
    }

    @Test
    void Schema_오류는_JSON_Pointer와_logicalNodeId를_반환한다() {
        var screenIdIssues = validator.validate(screen("UserList", "user-list"));
        assertThat(screenIdIssues)
                .extracting(FigmaContractSchemaValidator.SchemaViolation::jsonPointer)
                .contains("/screenId");

        var nodeIssues = validator.validate(screen("user-list", "user list"));
        assertThat(nodeIssues)
                .anySatisfy(issue -> {
                    assertThat(issue.jsonPointer()).isEqualTo("/content/logicalNodeId");
                    assertThat(issue.logicalNodeId()).isEqualTo("user list");
                    assertThat(issue.code()).startsWith("SCHEMA_");
                });
    }

    private FigmaScreenSpec screen(String screenId, String logicalNodeId) {
        return new FigmaScreenSpec(
                screenId, 1, "5d424e0b-5b86-4ccb-a75b-f8d4a6a76164", 1,
                FigmaScreenType.LIST, LayoutPattern.STANDARD, "사용자 목록", null,
                "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("ftc-krds", "1.0.0", "registry-1"),
                new FigmaNodeSpec(logicalNodeId, FigmaNodeSpec.NodeType.PAGE,
                        "egov.listPage", Map.of(), List.of()),
                List.of());
    }
}
