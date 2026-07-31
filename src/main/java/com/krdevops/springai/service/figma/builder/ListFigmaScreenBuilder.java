package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
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
        List<String> pageLevelActions = page.actions().stream().filter("CREATE"::equals).toList();
        children.add(BuilderSupport.actionArea(pageId, screenSpecification, pageLevelActions, idFactory));

        return new FigmaNodeSpec(
                idFactory.page(pageId), FigmaNodeSpec.NodeType.PAGE, "egov.listPage",
                Map.of("density", screenSpecification.layoutDensity().name()), children);
    }

    private FigmaNodeSpec searchPanel(
            String pageId, ScreenSpecification screenSpecification, List<ScreenFieldBinding> searchFields, LogicalNodeIdFactory idFactory) {
        List<FigmaNodeSpec> children = new ArrayList<>();
        for (ScreenFieldBinding field : searchFields) {
            children.add(BuilderSupport.fieldComponent(pageId, "search", field, idFactory));
        }
        children.add(BuilderSupport.actionButton(pageId, "SEARCH", idFactory));
        return new FigmaNodeSpec(
                idFactory.section(pageId, "search"), FigmaNodeSpec.NodeType.SECTION, "egov.searchPanel",
                Map.of("placement", screenSpecification.searchPanelPlacement().name()), children);
    }

    private FigmaNodeSpec resultToolbar(String pageId, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.section(pageId, "toolbar"), FigmaNodeSpec.NodeType.SECTION, "egov.resultToolbar",
                Map.of(), List.of());
    }

    /** R2-007: 반복 행은 REPEAT 노드 하나로 표현하고, 빈 상태 메시지를 속성으로 남긴다. */
    private FigmaNodeSpec dataTable(String pageId, List<ScreenFieldBinding> fields, LogicalNodeIdFactory idFactory) {
        List<ScreenFieldBinding> visibleFields = fields.stream().filter(ScreenFieldBinding::visible).toList();
        List<FigmaNodeSpec> columns = visibleFields.stream()
                .map(field -> new FigmaNodeSpec(
                        idFactory.field(pageId, "table", field.id()), FigmaNodeSpec.NodeType.TEXT, "krds.tableCell",
                        Map.of("label", field.label(), "sortable", field.sortable()), List.of()))
                .toList();
        FigmaNodeSpec row = new FigmaNodeSpec(
                idFactory.section(pageId, "table/row"), FigmaNodeSpec.NodeType.REPEAT, "egov.dataTable.row",
                Map.of(), columns);
        return new FigmaNodeSpec(
                idFactory.section(pageId, "table"), FigmaNodeSpec.NodeType.SECTION, "egov.dataTable",
                Map.of("emptyStateMessage", "조회된 데이터가 없습니다.", "loadingStateSupported", true,
                        "columnCount", visibleFields.size()),
                List.of(row));
    }

    private FigmaNodeSpec pagination(String pageId, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.section(pageId, "pagination"), FigmaNodeSpec.NodeType.COMPONENT, "krds.pagination",
                Map.of(), List.of());
    }
}
