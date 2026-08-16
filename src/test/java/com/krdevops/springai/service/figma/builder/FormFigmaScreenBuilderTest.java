package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.service.figma.FigmaBuilderTestFixtures;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void exposesPagePurposeAndFieldValidationMetadata() {
        ScreenSpecification spec = FigmaBuilderTestFixtures.userManagementSpec();
        PageSpec answerCreate = new PageSpec(
                "qna-answer-create", "QNA_ANSWER_FORM", spec.pages().get(1).fields(),
                spec.pages().get(1).actions());

        FigmaNodeSpec root = builder.build(spec, answerCreate, idFactory);
        assertThat(root.children().get(0).properties()).containsEntry("title", "답변 등록");

        FigmaNodeSpec field = root.children().get(1).children().get(0).children().get(0);
        assertThat(field.properties())
                .containsEntry("labelRequired", true)
                .containsKey("helperText")
                .containsKey("errorMessage");
    }

    @Test
    void exposesDataRolesForEmailAndEmailReplyInlineLayout() {
        ScreenSpecification spec = FigmaBuilderTestFixtures.userManagementSpec();
        ScreenFieldBinding template = spec.pages().get(1).fields().get(0);
        ScreenFieldBinding email = new ScreenFieldBinding(
                "email", "이메일주소", UiFieldRole.EMAIL, template.semanticRole(), template.mode(),
                template.source(), true, true, false, false, "TEXT", 1.0);
        ScreenFieldBinding emailReply = new ScreenFieldBinding(
                "emailReplyYn", "이메일답변여부", UiFieldRole.EMAIL_REPLY, template.semanticRole(), template.mode(),
                template.source(), true, false, false, false, "CHECKBOX", 1.0);
        PageSpec page = new PageSpec("qna-create", "QNA_FORM", List.of(email, emailReply), List.of());

        FigmaNodeSpec root = builder.build(spec, page, idFactory);
        FigmaNodeSpec form = root.children().get(1).children().get(0);

        assertThat(form.children()).extracting(node -> node.properties().get("dataRole"))
                .containsExactly("EMAIL", "EMAIL_REPLY");
    }
}
