package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DETAIL 화면 공통 Builder(R2-005, P1): egov.detailPage 아래
 * pageHeader → detailSection(읽기 전용 라벨·값) → actionArea 순서로 조립한다.
 */
@Component
public class DetailFigmaScreenBuilder implements FigmaScreenBuilder {

    @Override
    public FigmaScreenType supportedType() {
        return FigmaScreenType.DETAIL;
    }

    @Override
    public FigmaNodeSpec build(ScreenSpecification screenSpecification, PageSpec page, LogicalNodeIdFactory idFactory) {
        String pageId = page.id();
        List<FigmaNodeSpec> children = new ArrayList<>();
        children.add(BuilderSupport.pageHeader(pageId, screenSpecification, idFactory));
        children.add(detailSection(pageId, page.fields(), idFactory));
        children.add(BuilderSupport.actionArea(pageId, screenSpecification, page.actions(), idFactory));

        return new FigmaNodeSpec(
                idFactory.page(pageId), FigmaNodeSpec.NodeType.PAGE, "egov.detailPage",
                Map.of("density", screenSpecification.layoutDensity().name()), children);
    }

    private FigmaNodeSpec detailSection(String pageId, List<ScreenFieldBinding> fields, LogicalNodeIdFactory idFactory) {
        List<FigmaNodeSpec> children = fields.stream()
                .filter(ScreenFieldBinding::visible)
                .map(field -> new FigmaNodeSpec(
                        idFactory.field(pageId, "detail", field.id()), FigmaNodeSpec.NodeType.COMPONENT,
                        FieldComponentMapper.logicalType(field),
                        FieldComponentMapper.properties(field, FieldMode.READ_ONLY), List.of()))
                .toList();
        return new FigmaNodeSpec(
                idFactory.section(pageId, "detail"), FigmaNodeSpec.NodeType.SECTION, "egov.detailSection",
                Map.of("semanticRole", SemanticRole.FORM_SECTION.code()), children);
    }
}
