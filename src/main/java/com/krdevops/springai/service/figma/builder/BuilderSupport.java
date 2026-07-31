package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;

import java.util.List;
import java.util.Map;

/** LIST/FORM/DETAIL Builder가 공통으로 쓰는 pageHeader/actionArea/필드 노드 생성 로직. */
final class BuilderSupport {

    private BuilderSupport() {
    }

    static FigmaNodeSpec pageHeader(
            String pageId, ScreenSpecification screenSpecification, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.section(pageId, "header"), FigmaNodeSpec.NodeType.SECTION, "egov.pageHeader",
                Map.of("title", screenSpecification.screenName()), List.of());
    }

    static FigmaNodeSpec actionArea(
            String pageId, ScreenSpecification screenSpecification, List<String> actions, LogicalNodeIdFactory idFactory) {
        List<FigmaNodeSpec> buttons = actions.stream()
                .map(action -> actionButton(pageId, action, idFactory))
                .toList();
        return new FigmaNodeSpec(
                idFactory.section(pageId, "action"), FigmaNodeSpec.NodeType.SECTION, "egov.actionArea",
                Map.of("placement", screenSpecification.actionPlacement().name()), buttons);
    }

    static FigmaNodeSpec actionButton(String pageId, String action, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.action(pageId, action), FigmaNodeSpec.NodeType.COMPONENT, "krds.button",
                Map.of("actionType", action, "variant", primaryActions().contains(action) ? "primary" : "secondary"),
                List.of());
    }

    static FigmaNodeSpec fieldComponent(
            String pageId, String section, ScreenFieldBinding field, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.field(pageId, section, field.id()), FigmaNodeSpec.NodeType.COMPONENT,
                FieldComponentMapper.logicalType(field), FieldComponentMapper.properties(field), List.of());
    }

    private static List<String> primaryActions() {
        return List.of("SEARCH", "CREATE", "SAVE", "UPDATE");
    }
}
