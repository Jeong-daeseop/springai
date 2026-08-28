package com.krdevops.springai.service;

import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreenSpecAssemblerTest {

    private final ScreenSpecAssembler assembler = new ScreenSpecAssembler(new ScreenSpecValidator());

    @Test
    void standardBoardColumnsAreAutomaticallyApproved() {
        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "board", columns(), null);

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.APPROVED);
        assertThat(result.primaryTable()).isEqualTo("LETTNBBS");
        assertThat(result.pages()).extracting("id")
                .containsExactly("list", "detail", "regist", "updt");
        assertThat(result.pages().get(0).fields())
                .anySatisfy(field -> {
                    assertThat(field.dataRole()).isEqualTo(UiFieldRole.TITLE);
                    assertThat(field.source().column()).isEqualTo("NTT_SJ");
                });
    }

    @Test
    void unknownVisualFieldRequiresReview() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "BOARD_LIST", null, List.of(), List.of(),
                List.of(new UiDesignSpec.FieldHint(
                        "department", "담당부서", UiFieldRole.DEPARTMENT, "TEXT", 0.7)),
                Map.of(), List.of(), List.of());

        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "board", columns(), uiSpec);

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.REVIEW_REQUIRED);
        assertThat(result.pages().get(0).fields())
                .anySatisfy(field -> assertThat(field.source().type()).isEqualTo(FieldSourceType.UNMAPPED));
        assertThat(result.issues()).extracting("code").contains("NO_COLUMN_CANDIDATE", "FIELD_UNMAPPED");
    }

    @Test
    void rowNumberIsMappedToDerivedExpressionWithoutPhysicalColumn() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "BOARD_LIST", null, List.of(), List.of(),
                List.of(new UiDesignSpec.FieldHint(
                        "rowNumber", "번호", UiFieldRole.ROW_NUMBER, "TEXT", 0.95)),
                Map.of(), List.of(), List.of());

        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "board", columns(), uiSpec);

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.APPROVED);
        assertThat(result.pages().get(0).fields())
                .filteredOn(field -> field.dataRole() == UiFieldRole.ROW_NUMBER)
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.source().type()).isEqualTo(FieldSourceType.DERIVED);
                    assertThat(field.source().expression()).isEqualTo("PAGE_ROW_NUMBER");
                });
    }

    @Test
    void codeColumnRequiresCodeGroupReview() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST", null, List.of(), List.of(),
                List.of(new UiDesignSpec.FieldHint(
                        "status", "상태", UiFieldRole.STATUS, "SELECT", 0.9)),
                Map.of(), List.of(), List.of());
        List<Map<String, Object>> statusColumns = List.of(
                column("STATUS_CODE", "varchar", "NO", "상태", ""));

        ScreenSpecification result = assembler.assemble(
                "com", "STATUS_TABLE", "상태", "crud", statusColumns, uiSpec);

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.REVIEW_REQUIRED);
        assertThat(result.pages().get(0).fields())
                .anySatisfy(field -> assertThat(field.source().type()).isEqualTo(FieldSourceType.COMMON_CODE));
        assertThat(result.issues()).extracting("code").contains("COMMON_CODE_GROUP_REQUIRED");
    }

    @Test
    void explicitListAndDetailColumnsAreRecordedPerPage() {
        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "board", columns(), null,
                List.of("NTT_SJ"), List.of("NTT_CN"));

        assertThat(result.pages().get(0).selectionSource()).isEqualTo(FieldSelectionSource.EXPLICIT);
        assertThat(result.pages().get(0).fields())
                .extracting(field -> field.source().column())
                .containsExactly("NTT_ID", "NTT_SJ");
        assertThat(result.pages().get(1).selectionSource()).isEqualTo(FieldSelectionSource.EXPLICIT);
        assertThat(result.pages().get(1).fields())
                .extracting(field -> field.source().column())
                .containsExactly("NTT_ID", "NTT_CN");
    }

    @Test
    void designHintsOnlySubsetTheTargetPageAndPreserveDensity() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST", new UiDesignSpec.LayoutSpec(null, null, "compact"),
                List.of(), List.of(),
                List.of(new UiDesignSpec.FieldHint(
                        "title", "제목", UiFieldRole.TITLE, "TEXT", 1.0)),
                Map.of(), List.of(), List.of());

        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec);

        assertThat(result.layoutDensity()).isEqualTo(LayoutDensity.COMPACT);
        assertThat(result.pages().get(0).selectionSource())
                .isEqualTo(FieldSelectionSource.DESIGN_REFERENCE);
        assertThat(result.pages().get(0).fields()).hasSize(2);
        assertThat(result.pages().get(1).selectionSource()).isEqualTo(FieldSelectionSource.DEFAULT);
        assertThat(result.pages().get(1).fields()).hasSize(columns().size());
    }

    @Test
    void emptyExplicitListsMeanNoExplicitSelection() {
        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), null,
                List.of("  "), List.of());

        assertThat(result.pages()).extracting("selectionSource")
                .containsOnly(FieldSelectionSource.DEFAULT);
    }

    @Test
    void explicitSelectionRejectsMoreThanSixColumnsIncludingCompositePk() {
        List<Map<String, Object>> manyColumns = List.of(
                column("PK_A", "varchar", "NO", "PK A", "PRI"),
                column("PK_B", "varchar", "NO", "PK B", "PRI"),
                column("COL_1", "varchar", "YES", "1", ""),
                column("COL_2", "varchar", "YES", "2", ""),
                column("COL_3", "varchar", "YES", "3", ""),
                column("COL_4", "varchar", "YES", "4", ""),
                column("COL_5", "varchar", "YES", "5", ""));

        assertThatThrownBy(() -> assembler.assemble(
                "com", "MULTI_PK", "복합키", "crud", manyColumns, null,
                List.of("COL_1", "COL_2", "COL_3", "COL_4", "COL_5"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 6개");
    }

    @Test
    void unsupportedExplicitDensityIsRejectedImmediately() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST", new UiDesignSpec.LayoutSpec(null, null, "dense-ish"),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of());

        assertThatThrownBy(() -> assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("layout density");
    }

    @Test
    void designHintsOnlySubsetTheTargetPageAndPreserveFormColumnLayout() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST", new UiDesignSpec.LayoutSpec(null, null, null, "two-column"),
                List.of(), List.of(),
                List.of(new UiDesignSpec.FieldHint(
                        "title", "제목", UiFieldRole.TITLE, "TEXT", 1.0)),
                Map.of(), List.of(), List.of());

        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec);

        assertThat(result.formColumnLayout()).isEqualTo(FormColumnLayout.TWO_COLUMN);
    }

    @Test
    void unsupportedExplicitFormColumnLayoutIsRejectedImmediately() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST", new UiDesignSpec.LayoutSpec(null, null, null, "three-column"),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of());

        assertThatThrownBy(() -> assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("form column layout");
    }

    @Test
    void designHintsPreserveActionAndSearchPanelPlacement() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST",
                new UiDesignSpec.LayoutSpec(null, null, null, null, "bottom-right", "none"),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of());

        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec);

        assertThat(result.actionPlacement()).isEqualTo(ActionPlacement.BOTTOM_RIGHT);
        assertThat(result.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.NONE);
    }

    @Test
    void unsupportedExplicitActionPlacementIsRejectedImmediately() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST",
                new UiDesignSpec.LayoutSpec(null, null, null, null, "bottom-center", null),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of());

        assertThatThrownBy(() -> assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action placement");
    }

    @Test
    void unsupportedExplicitSearchPanelPlacementIsRejectedImmediately() {
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST",
                new UiDesignSpec.LayoutSpec(null, null, null, null, null, "beside-table"),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of());

        assertThatThrownBy(() -> assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("search panel placement");
    }

    @Test
    void componentStylesArePassedThroughFromUiDesignSpec() {
        UiDesignSpec.ComponentSpec actionGroup = new UiDesignSpec.ComponentSpec(
                "ACTION_GROUP", List.of("Primary Button"), "rgba(255,87,51,1.00)", "rgba(0,0,0,1.00)");
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST", null, List.of(actionGroup), List.of(), List.of(),
                Map.of(), List.of(), List.of());

        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec);

        assertThat(result.componentStyles()).containsExactly(actionGroup);
    }

    @Test
    void componentStylesDefaultToEmptyWhenUiSpecIsNull() {
        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "board", columns(), null);

        assertThat(result.componentStyles()).isEmpty();
    }

    @Test
    void componentGeometryIsPassedThroughFromUiDesignSpec() {
        UiDesignSpec.NodeGeometry root = new UiDesignSpec.NodeGeometry(
                "1:1", "FRAME", "목록", 0, 0, 1440, 900,
                null, null, null, null, null, null, List.of());
        UiDesignSpec uiSpec = new UiDesignSpec(
                "CRUD_LIST", null, List.of(), List.of(), List.of(),
                Map.of(), List.of(), List.of(), List.of(root));

        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "crud", columns(), uiSpec);

        assertThat(result.componentGeometry()).containsExactly(root);
    }

    @Test
    void componentGeometryDefaultsToEmptyWhenUiSpecIsNull() {
        ScreenSpecification result = assembler.assemble(
                "com", "LETTNBBS", "공지사항", "board", columns(), null);

        assertThat(result.componentGeometry()).isEmpty();
    }

    private List<Map<String, Object>> columns() {
        return List.of(
                column("NTT_ID", "bigint", "NO", "게시물ID", "PRI"),
                column("NTT_SJ", "varchar", "NO", "제목", ""),
                column("NTT_CN", "text", "YES", "내용", ""),
                column("FRST_REGIST_PNTTM", "datetime", "NO", "등록일", ""),
                column("ATCH_FILE_ID", "varchar", "YES", "첨부파일", ""));
    }

    private Map<String, Object> column(
            String name, String type, String nullable, String comment, String key) {
        return Map.of(
                "COLUMN_NAME", name,
                "DATA_TYPE", type,
                "IS_NULLABLE", nullable,
                "COLUMN_COMMENT", comment,
                "COLUMN_KEY", key);
    }
}
