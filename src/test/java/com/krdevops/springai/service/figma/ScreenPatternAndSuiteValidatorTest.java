package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.ScreenSuiteManifest;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenPatternAndSuiteValidatorTest {
    @Test
    void missingRequiredSlotIsBlocked() {
        assertThat(new ScreenPatternValidator().validate(listPattern(), page(
                roleNode("header", "page.header"))))
                .extracting(issue -> issue.code()).contains("PATTERN_REQUIRED_SLOT_MISSING");
    }

    @Test
    void childRoleOutsideAllowedChildrenIsBlocked() {
        FigmaNodeSpec table = roleNode("table", "data.table",
                roleNode("wrong-child", "field.text"));

        assertThat(new ScreenPatternValidator().validate(listPattern(), page(
                roleNode("header", "page.header"), table)))
                .extracting(issue -> issue.code())
                .contains("PATTERN_CHILD_ROLE_NOT_ALLOWED");
    }

    @Test
    void slotOrderViolationIsBlocked() {
        assertThat(new ScreenPatternValidator().validate(listPattern(), page(
                roleNode("header", "page.header"),
                roleNode("pagination", "data.pagination"),
                roleNode("table", "data.table", roleNode("cell", "data.table.cell")))))
                .extracting(issue -> issue.code())
                .contains("PATTERN_SLOT_ORDER_VIOLATION");
    }

    @Test
    void roleOutsidePatternAndUnknownRoleAreBlocked() {
        assertThat(new ScreenPatternValidator().validate(listPattern(), page(
                roleNode("header", "page.header"),
                roleNode("table", "data.table", roleNode("cell", "data.table.cell")),
                roleNode("delete", "action.destructive"),
                roleNode("legacy-search", "search.region"))))
                .extracting(issue -> issue.code())
                .contains("PATTERN_ROLE_NOT_ALLOWED", "PATTERN_UNKNOWN_ROLE");
    }

    @Test
    void validRoleTreePassesCardinalityHierarchyAndOrder() {
        assertThat(new ScreenPatternValidator().validate(listPattern(), page(
                roleNode("header", "page.header"),
                roleNode("search", "search.panel"),
                roleNode("table", "data.table", roleNode("cell", "data.table.cell")),
                roleNode("pagination", "data.pagination"),
                roleNode("create", "action.primary"))))
                .isEmpty();
    }

    @Test
    void qnaSuiteMissingOneOfSixScreensIsBlocked() {
        ScreenSuiteManifest manifest = new ScreenSuiteManifest("qna", "1.0.0", List.of(
                expected("qna-list", ScreenPattern.CRUD_LIST), expected("qna-create", ScreenPattern.CRUD_CREATE),
                expected("qna-detail", ScreenPattern.CRUD_DETAIL), expected("qna-answer-list", ScreenPattern.CRUD_LIST),
                expected("qna-answer-detail", ScreenPattern.CRUD_DETAIL), expected("qna-answer-create", ScreenPattern.CRUD_CREATE)));
        List<ScreenSuiteManifestValidator.ActualScreen> five = manifest.screens().stream().limit(5)
                .map(screen -> new ScreenSuiteManifestValidator.ActualScreen(screen.screenId(), screen.pattern())).toList();
        assertThat(new ScreenSuiteManifestValidator().validate(manifest, five))
                .extracting(issue -> issue.code()).contains("REQUIRED_SCREEN_MISSING");
    }

    private ScreenSuiteManifest.ExpectedScreen expected(String id, ScreenPattern pattern) {
        return new ScreenSuiteManifest.ExpectedScreen(id, pattern, true);
    }

    private ScreenPatternDefinition listPattern() {
        return new ScreenPatternDefinition(ScreenPattern.CRUD_LIST, "1.0.0", List.of(
                new ScreenPatternDefinition.SlotDefinition(SemanticRole.PAGE_HEADER, 1, 1, List.of(), 0),
                new ScreenPatternDefinition.SlotDefinition(SemanticRole.SEARCH_PANEL, 0, 1, List.of(), 1),
                new ScreenPatternDefinition.SlotDefinition(
                        SemanticRole.DATA_TABLE, 1, 1, List.of(SemanticRole.DATA_TABLE_CELL), 2),
                new ScreenPatternDefinition.SlotDefinition(SemanticRole.DATA_PAGINATION, 0, 1, List.of(), 3),
                new ScreenPatternDefinition.SlotDefinition(SemanticRole.ACTION_PRIMARY, 0, 2, List.of(), 4)));
    }

    private FigmaNodeSpec page(FigmaNodeSpec... children) {
        return new FigmaNodeSpec("page", FigmaNodeSpec.NodeType.PAGE, "egov.page",
                Map.of(), List.of(children));
    }

    private FigmaNodeSpec roleNode(String id, String role, FigmaNodeSpec... children) {
        return new FigmaNodeSpec(id, FigmaNodeSpec.NodeType.SECTION, "test." + id,
                Map.of("semanticRole", role), List.of(children));
    }
}
