package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-T07(R1-T02에서 이관): 필수값·중복 logicalNodeId 검증. */
class FigmaScreenSpecValidatorTest {

    private final FigmaScreenSpecValidator validator = new FigmaScreenSpecValidator();

    @Test
    void detectsDuplicateLogicalNodeIdAcrossTree() {
        FigmaNodeSpec leaf = new FigmaNodeSpec("list/table/userName", FigmaNodeSpec.NodeType.TEXT, "krds.tableCell", Map.of(), List.of());
        FigmaNodeSpec duplicateSibling = new FigmaNodeSpec("list/table/userName", FigmaNodeSpec.NodeType.TEXT, "krds.tableCell", Map.of(), List.of());
        FigmaNodeSpec root = new FigmaNodeSpec("list", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of(leaf, duplicateSibling));

        List<FigmaExportIssue> issues = validator.validate(spec(root));

        assertThat(issues).extracting(FigmaExportIssue::code).contains("DUPLICATE_LOGICAL_NODE_ID");
    }

    @Test
    void detectsUnsupportedControlFallbackFlag() {
        FigmaNodeSpec leaf = new FigmaNodeSpec(
                "list/form/weird", FigmaNodeSpec.NodeType.COMPONENT, "krds.textField",
                Map.of("unsupportedControl", true), List.of());
        FigmaNodeSpec root = new FigmaNodeSpec("list", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of(leaf));

        List<FigmaExportIssue> issues = validator.validate(spec(root));

        assertThat(issues).extracting(FigmaExportIssue::code).contains("UNSUPPORTED_CONTROL");
    }

    @Test
    void validTreeProducesNoIssues() {
        FigmaNodeSpec leaf = new FigmaNodeSpec("list/table/userName", FigmaNodeSpec.NodeType.TEXT, "krds.tableCell", Map.of(), List.of());
        FigmaNodeSpec root = new FigmaNodeSpec("list", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of(leaf));

        assertThat(validator.validate(spec(root))).isEmpty();
    }

    @Test
    void schemaAndSemanticIssuesShareJsonPointerAndLogicalNodeId() {
        FigmaNodeSpec invalid = new FigmaNodeSpec(
                "list invalid", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of());

        assertThat(validator.validate(spec(invalid)))
                .anySatisfy(issue -> {
                    assertThat(issue.code()).startsWith("SCHEMA_");
                    assertThat(issue.jsonPointer()).isEqualTo("/content/logicalNodeId");
                    assertThat(issue.logicalNodeId()).isEqualTo("list invalid");
                });
    }

    private FigmaScreenSpec spec(FigmaNodeSpec content) {
        return new FigmaScreenSpec(
                "list", 1, "spec-user-management", 1,
                FigmaScreenType.LIST, LayoutPattern.STANDARD, "사용자 목록", null, "DESKTOP",
                "APPROVED", new FigmaScreenSpec.DesignSystemRef("krds", "1.0", "2026.07"),
                content, List.of());
    }
}
