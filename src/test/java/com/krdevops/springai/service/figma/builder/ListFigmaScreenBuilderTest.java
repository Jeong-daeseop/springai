package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.service.figma.FigmaBuilderTestFixtures;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-T01/T02: LIST Builder의 고정 fixture 결과와 결정론적 출력을 검증한다. */
class ListFigmaScreenBuilderTest {

    private final ListFigmaScreenBuilder builder = new ListFigmaScreenBuilder();
    private final LogicalNodeIdFactory idFactory = new LogicalNodeIdFactory();

    @Test
    void buildsListPageWithSearchPanelTableAndActions() {
        ScreenSpecification spec = FigmaBuilderTestFixtures.userManagementSpec();
        PageSpec listPage = spec.pages().get(0);

        FigmaNodeSpec root = builder.build(spec, listPage, idFactory);

        assertThat(root.logicalNodeId()).isEqualTo("list");
        assertThat(root.nodeType()).isEqualTo(FigmaNodeSpec.NodeType.PAGE);
        assertThat(root.type()).isEqualTo("egov.listPage");
        assertThat(root.children()).extracting(FigmaNodeSpec::type)
                .containsExactly("krds.pageHeader", "krds.searchPanel", "egov.resultToolbar",
                        "krds.dataTable", "krds.pagination", "egov.actionArea");
        assertThat(root.children().get(0).nodeType()).isEqualTo(FigmaNodeSpec.NodeType.COMPONENT);
        assertThat(root.children().get(0).properties()).containsEntry("semanticRole", "page.header");

        FigmaNodeSpec searchPanel = root.children().get(1);
        assertThat(searchPanel.logicalNodeId()).isEqualTo("list/search");
        assertThat(searchPanel.nodeType()).isEqualTo(FigmaNodeSpec.NodeType.COMPONENT);
        assertThat(searchPanel.type()).isEqualTo("krds.searchPanel");
        assertThat(searchPanel.properties())
                .containsEntry("semanticRole", "search.panel")
                .containsEntry("fieldCount", 2)
                .containsEntry("label", "검색어")
                .containsEntry("placeholder", "검색어를 입력하세요");
        assertThat(searchPanel.children()).isEmpty();

        FigmaNodeSpec dataTable = root.children().get(3);
        assertThat(dataTable.children()).hasSize(4);
        assertThat(dataTable.properties()).containsEntry("layoutRecipe", "krds.dataTable.v1");
        FigmaNodeSpec row = dataTable.children().get(1);
        assertThat(row.nodeType()).isEqualTo(FigmaNodeSpec.NodeType.REPEAT);
        assertThat(row.children()).hasSizeGreaterThanOrEqualTo(5);

        FigmaNodeSpec actionArea = root.children().get(5);
        assertThat(actionArea.children()).extracting(node -> node.properties().get("actionType"))
                .containsExactly("CREATE");
    }

    @Test
    void buildIsDeterministicForSameInput() {
        ScreenSpecification spec = FigmaBuilderTestFixtures.userManagementSpec();
        PageSpec listPage = spec.pages().get(0);

        FigmaNodeSpec first = builder.build(spec, listPage, idFactory);
        FigmaNodeSpec second = builder.build(spec, listPage, idFactory);

        assertThat(first).isEqualTo(second);
    }
}
