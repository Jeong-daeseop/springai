package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.service.figma.FigmaBuilderTestFixtures;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-T01: FORM Builder의 고정 fixture 결과를 검증한다. */
class FormFigmaScreenBuilderTest {

    private final FormFigmaScreenBuilder builder = new FormFigmaScreenBuilder();
    private final LogicalNodeIdFactory idFactory = new LogicalNodeIdFactory();

    @Test
    void buildsFormPageWithFormSectionValidationSummaryAndActions() {
        ScreenSpecification spec = FigmaBuilderTestFixtures.userManagementSpec();
        PageSpec registPage = spec.pages().get(1);

        FigmaNodeSpec root = builder.build(spec, registPage, idFactory);

        assertThat(root.type()).isEqualTo("egov.formPage");
        assertThat(root.children()).extracting(FigmaNodeSpec::type)
                .containsExactly("krds.pageHeader", "egov.formContainer", "egov.validationSummary", "egov.actionArea");
        assertThat(root.children().get(1).properties()).containsEntry("semanticRole", "form.container");
        assertThat(root.children().get(1).children()).extracting(FigmaNodeSpec::type)
                .containsExactly("egov.formSection");
        assertThat(root.children().get(0).nodeType()).isEqualTo(FigmaNodeSpec.NodeType.COMPONENT);
        assertThat(root.children().get(0).properties()).containsEntry("semanticRole", "page.header");

        FigmaNodeSpec formSection = root.children().get(1).children().get(0);
        assertThat(formSection.children()).hasSize(2);
        assertThat(formSection.children()).extracting(FigmaNodeSpec::type)
                .containsExactly("krds.textField", "krds.select");
        assertThat(formSection.children()).allMatch(node -> (boolean) node.properties().get("required"));

        FigmaNodeSpec validationSummary = root.children().get(2);
        assertThat(validationSummary.properties()).containsEntry("requiredFieldCount", 2L);
    }
}
