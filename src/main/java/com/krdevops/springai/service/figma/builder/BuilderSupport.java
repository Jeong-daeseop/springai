package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenActionSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.role.SemanticRole;
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
                idFactory.section(pageId, "header"), FigmaNodeSpec.NodeType.COMPONENT, "krds.pageHeader",
                Map.of("semanticRole", SemanticRole.PAGE_HEADER.code(),
                        "title", screenSpecification.screenName()), List.of());
    }

    static FigmaNodeSpec actionArea(
            String pageId, ScreenSpecification screenSpecification, List<ScreenActionSpec> actions, LogicalNodeIdFactory idFactory) {
        List<FigmaNodeSpec> buttons = actions.stream()
                .map(action -> actionButton(pageId, action, idFactory))
                .toList();
        return new FigmaNodeSpec(
                idFactory.section(pageId, "action"), FigmaNodeSpec.NodeType.SECTION, "egov.actionArea",
                Map.of("placement", screenSpecification.actionPlacement().name()), buttons);
    }

    static FigmaNodeSpec actionButton(String pageId, ScreenActionSpec semanticAction, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.action(pageId, semanticAction.command()), FigmaNodeSpec.NodeType.COMPONENT, "krds.button",
                Map.of("semanticRole", semanticAction.role().code(),
                        "actionType", semanticAction.command(),
                        "label", semanticAction.label(),
                        "state", semanticAction.state().name()),
                List.of());
    }

    static FigmaNodeSpec fieldComponent(
            String pageId, String section, ScreenFieldBinding field, LogicalNodeIdFactory idFactory) {
        return new FigmaNodeSpec(
                idFactory.field(pageId, section, field.id()), FigmaNodeSpec.NodeType.COMPONENT,
                FieldComponentMapper.logicalType(field), FieldComponentMapper.properties(field), List.of());
    }

}
