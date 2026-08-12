package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenActionSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LIST 화면 공통 Builder(11번 §6): egov.listPage 아래
 * pageHeader → searchPanel → resultToolbar → dataTable → krds.pagination → actionArea 순서로 조립한다.
 */
@Component
public class ListFigmaScreenBuilder implements FigmaScreenBuilder {

    @Override
    public FigmaScreenType supportedType() {
        return FigmaScreenType.LIST;
    }

    @Override
    public FigmaNodeSpec build(ScreenSpecification screenSpecification, PageSpec page, LogicalNodeIdFactory idFactory) {
        String pageId = page.id();
        List<FigmaNodeSpec> children = new ArrayList<>();
        children.add(BuilderSupport.pageHeader(pageId, screenSpecification, idFactory));

        List<ScreenFieldBinding> searchFields = page.fields().stream().filter(ScreenFieldBinding::searchable).toList();
        if (!searchFields.isEmpty()) {
            children.add(searchPanel(pageId, screenSpecification, searchFields, idFactory));
        }

        children.add(resultToolbar(pageId, idFactory));
        children.add(dataTable(pageId, page.fields(), idFactory));
        children.add(pagination(pageId, idFactory));
        // SEARCH는 searchPanel의 검색 버튼으로 이미 표현되고, VIEW_DETAIL/UPDATE/DELETE는
        // 행을 선택해야 의미가 있는 행 단위 동작이라 목록 화면의 actionArea에는 CREATE만 둔다
        // (ActionPlacement가 "등록 버튼(주요 액션)의 배치 위치"만 표현하는 것과 일치).
        List<ScreenActionSpec> pageLevelActions = page.actions().stream()
                .filter(action -> "CREATE".equals(action.command())).toList();
        children.add(BuilderSupport.actionArea(pageId, screenSpecification, pageLevelActions, idFactory));

        return new FigmaNodeSpec(
                idFactory.page(pageId), FigmaNodeSpec.NodeType.PAGE, "egov.listPage",
                Map.of("density", screenSpecification.layoutDensity().name(),
                        "layoutRecipe", "krds.listPage.v1", "contentMaxWidth", 1280,
                        "contentMinWidth", 960, "sectionGap", 40), children);
    }

    private FigmaNodeSpec searchPanel(
            String pageId, ScreenSpecification screenSpecification, List<ScreenFieldBinding> searchFields, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.section(pageId, "search"), FigmaNodeSpec.NodeType.COMPONENT, "krds.searchPanel",
                Map.of("semanticRole", SemanticRole.SEARCH_PANEL.code(),
                        "placement", screenSpecification.searchPanelPlacement().name(),
                        "fieldCount", searchFields.size(),
                        "searchFieldIds", searchFields.stream().map(ScreenFieldBinding::id).toList(),
                        "label", "검색어",
                        "placeholder", "검색어를 입력하세요",
                        "componentMaxWidth", 960), List.of());
    }

    private FigmaNodeSpec resultToolbar(String pageId, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.section(pageId, "toolbar"), FigmaNodeSpec.NodeType.SECTION, "egov.resultToolbar",
                Map.of(), List.of());
    }

    /** 전체 Table은 Published Cell Instance를 조립하는 krds.dataTable Layout Recipe로 표현한다. */
    private FigmaNodeSpec dataTable(String pageId, List<ScreenFieldBinding> fields, LogicalNodeIdFactory idFactory) {
        List<ScreenFieldBinding> visible = fields.stream().filter(ScreenFieldBinding::visible).toList();
        List<ScreenFieldBinding> visibleFields = visible.size() >= 5 ? visible : fields.stream().limit(5).toList();
        List<FigmaNodeSpec> mappedColumns = java.util.stream.IntStream.range(0, visibleFields.size())
                .mapToObj(index -> {
                    ScreenFieldBinding field = visibleFields.get(index);
                    return new FigmaNodeSpec(
                        idFactory.field(pageId, "table", field.id()), FigmaNodeSpec.NodeType.COMPONENT, "krds.tableCell",
                        Map.of("semanticRole", SemanticRole.DATA_TABLE_CELL.code(),
                                "label", field.label(), "sortable", field.sortable(),
                                "columnWidthPercent", columnWidthPercent(index)), List.of());
                })
                .toList();
        List<FigmaNodeSpec> columns = new ArrayList<>(mappedColumns);
        for (int index = columns.size(); !columns.isEmpty() && index < 5; index++) {
            columns.add(new FigmaNodeSpec(
                    idFactory.field(pageId, "table", "column-" + (index + 1)),
                    FigmaNodeSpec.NodeType.COMPONENT, "krds.tableCell",
                    Map.of("semanticRole", SemanticRole.DATA_TABLE_CELL.code(),
                            "label", "컬럼 " + (index + 1), "sortable", false,
                            "columnWidthPercent", columnWidthPercent(index)), List.of()));
        }
        FigmaNodeSpec header = new FigmaNodeSpec(
                idFactory.section(pageId, "table/header"), FigmaNodeSpec.NodeType.SECTION, "krds.dataTable.header",
                Map.of("rowType", "HEADER"), cloneColumns(pageId, "header", columns));
        List<FigmaNodeSpec> rows = java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(index -> new FigmaNodeSpec(
                        idFactory.section(pageId, "table/row-" + index), FigmaNodeSpec.NodeType.REPEAT,
                        "krds.dataTable.row", Map.of("rowType", "BODY", "sampleRow", index),
                        cloneColumns(pageId, "row-" + index, columns)))
                .toList();
        List<FigmaNodeSpec> tableChildren = new ArrayList<>();
        tableChildren.add(header);
        tableChildren.addAll(rows);
        return new FigmaNodeSpec(
                idFactory.section(pageId, "table"), FigmaNodeSpec.NodeType.SECTION, "krds.dataTable",
                Map.of("semanticRole", SemanticRole.DATA_TABLE.code(),
                        "emptyStateMessage", "조회된 데이터가 없습니다.", "loadingStateSupported", true,
                        "columnCount", columns.size(), "sampleRowCount", 3,
                        "layoutRecipe", "krds.dataTable.v1"), tableChildren);
    }

    private FigmaNodeSpec pagination(String pageId, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.section(pageId, "pagination"), FigmaNodeSpec.NodeType.COMPONENT, "krds.pagination",
                Map.of("semanticRole", SemanticRole.DATA_PAGINATION.code()), List.of());
    }

    private List<FigmaNodeSpec> cloneColumns(String pageId, String rowId, List<FigmaNodeSpec> columns) {
        return java.util.stream.IntStream.range(0, columns.size())
                .mapToObj(index -> {
                    FigmaNodeSpec source = columns.get(index);
                    return new FigmaNodeSpec(pageId + "/table/" + rowId + "/cell-" + (index + 1),
                            source.nodeType(), source.type(), source.properties(), source.children());
                }).toList();
    }

    private int columnWidthPercent(int index) {
        return switch (index) {
            case 0 -> 8;
            case 1 -> 32;
            default -> 15;
        };
    }
}
