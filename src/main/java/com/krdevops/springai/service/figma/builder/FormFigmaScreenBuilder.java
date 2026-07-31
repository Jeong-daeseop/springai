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
 * FORM 화면(등록/수정) 공통 Builder(11번 §6): egov.formPage 아래
 * pageHeader → formSection → validationSummary → actionArea 순서로 조립한다.
 */
@Component
public class FormFigmaScreenBuilder implements FigmaScreenBuilder {

    @Override
    public FigmaScreenType supportedType() {
        return FigmaScreenType.FORM;
    }

    @Override
    public FigmaNodeSpec build(ScreenSpecification screenSpecification, PageSpec page, LogicalNodeIdFactory idFactory) {
        String pageId = page.id();
        List<FigmaNodeSpec> children = new ArrayList<>();
        children.add(BuilderSupport.pageHeader(pageId, screenSpecification, idFactory));
        children.add(formSection(pageId, screenSpecification, page.fields(), idFactory));
        children.add(validationSummary(pageId, page.fields(), idFactory));
        children.add(BuilderSupport.actionArea(pageId, screenSpecification, page.actions(), idFactory));

        return new FigmaNodeSpec(
                idFactory.page(pageId), FigmaNodeSpec.NodeType.PAGE, "egov.formPage",
                Map.of("density", screenSpecification.layoutDensity().name()), children);
    }

    private FigmaNodeSpec formSection(
            String pageId, ScreenSpecification screenSpecification, List<ScreenFieldBinding> fields, LogicalNodeIdFactory idFactory) {
        List<FigmaNodeSpec> children = fields.stream()
                .filter(ScreenFieldBinding::visible)
                .map(field -> BuilderSupport.fieldComponent(pageId, "form", field, idFactory))
                .toList();
        return new FigmaNodeSpec(
                idFactory.section(pageId, "form"), FigmaNodeSpec.NodeType.SECTION, "egov.formSection",
                Map.of("columnLayout", screenSpecification.formColumnLayout().name()), children);
    }

    private FigmaNodeSpec validationSummary(String pageId, List<ScreenFieldBinding> fields, LogicalNodeIdFactory idFactory) {
        long requiredCount = fields.stream().filter(ScreenFieldBinding::required).count();
        return new FigmaNodeSpec(
                idFactory.section(pageId, "validation"), FigmaNodeSpec.NodeType.SECTION, "egov.validationSummary",
                Map.of("requiredFieldCount", requiredCount), List.of());
    }
}
