package com.krdevops.springai.service;

import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V2BusinessContractPrecedenceTest {

    private final ScreenSpecValidator validator = new ScreenSpecValidator();
    private final ScreenSpecAssembler assembler = new ScreenSpecAssembler(validator);
    private final UiDesignSpecV2ToV1Projection projection = new UiDesignSpecV2ToV1Projection();

    @Test
    void 시각_Action_Route_Permission_후보는_업무_Action을_만들지_못한다() {
        UiDesignSpecV2 spec = design(List.of(
                node("action-admin-export", "action-candidate"),
                node("route-delete-all", "route-candidate"),
                node("permission-super-admin", "permission-candidate")));

        UiDesignSpec visual = projection.project(spec, "crud");
        ScreenSpecification result = assembler.assemble(
                "egov", "QNA", "문의", "crud", columns(), visual);

        assertThat(visual.actions()).isEmpty();
        assertThat(result.pages()).flatExtracting(page -> page.actions())
                .extracting(action -> action.command())
                .contains("SEARCH", "CREATE", "VIEW_DETAIL", "UPDATE", "DELETE")
                .doesNotContain("CUSTOM", "ADMIN_EXPORT", "DELETE_ALL", "SUPER_ADMIN");
    }

    @Test
    void DB에_없는_시각_Field는_업무_Field로_확정되지_않고_검토_대상이_된다() {
        UiDesignSpec visual = projection.project(
                design(List.of(node("field-secretVisualOnly", "field-candidate"))), "crud");

        ScreenSpecification result = assembler.assemble(
                "egov", "QNA", "문의", "crud", columns(), visual);

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("NO_COLUMN_CANDIDATE");
            assertThat(issue.fieldId()).isEqualTo("secretVisualOnly");
        });
        assertThat(result.pages()).flatExtracting(page -> page.fields())
                .noneMatch(field -> field.source() != null
                        && "secretVisualOnly".equalsIgnoreCase(field.source().column()));
    }

    @Test
    void 명시적_컬럼_선택은_시각_Field_후보보다_우선한다() {
        UiDesignSpec visual = projection.project(
                design(List.of(node("field-title", "field-candidate"))), "crud");

        ScreenSpecification result = assembler.assemble(
                "egov", "QNA", "문의", "crud", columns(), visual,
                List.of("STATUS"), List.of("TITLE"));

        var listPage = result.pages().stream().filter(page -> page.id().equals("list")).findFirst().orElseThrow();
        assertThat(listPage.selectionSource()).isEqualTo(FieldSelectionSource.EXPLICIT);
        assertThat(listPage.fields()).anySatisfy(field ->
                assertThat(field.source().column()).isEqualTo("STATUS"));
        assertThat(listPage.fields()).noneMatch(field -> field.source() != null
                && "TITLE".equals(field.source().column()) && !"QNA_ID".equals(field.source().column()));
    }

    private UiDesignSpecV2 design(List<UiDesignSpecV2.SemanticNode> nodes) {
        List<String> ids = nodes.stream().map(UiDesignSpecV2.SemanticNode::semanticId).toList();
        return new UiDesignSpecV2(
                "ui-1", "2.0", "a".repeat(64),
                new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", "r1"),
                null, nodes, List.of(),
                List.of(new UiDesignSpecV2.ResponsiveStructure("desktop", ids, ids)),
                List.of(), List.of(), 0.95);
    }

    private UiDesignSpecV2.SemanticNode node(String id, String role) {
        return new UiDesignSpecV2.SemanticNode(
                id, role, null,
                new UiDesignSpecV2.InferenceEvidence(
                        List.of("1:1"), 0.95, "TEST", false, false),
                List.of());
    }

    private List<Map<String, Object>> columns() {
        return List.of(
                column("QNA_ID", "문의 ID", "PRI", "NO"),
                column("TITLE", "제목", "", "NO"),
                column("STATUS", "상태", "", "YES"));
    }

    private Map<String, Object> column(
            String name, String comment, String key, String nullable) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("COLUMN_NAME", name);
        column.put("COLUMN_COMMENT", comment);
        column.put("COLUMN_KEY", key);
        column.put("IS_NULLABLE", nullable);
        column.put("DATA_TYPE", "varchar");
        return column;
    }
}
