package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.service.figma.FigmaScreenTypeResolver;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessFigmaScreenBuilderTest {

    private final LogicalNodeIdFactory ids = new LogicalNodeIdFactory();
    private final FigmaScreenTypeResolver resolver = new FigmaScreenTypeResolver();

    @Test
    void userUpdateAndDetailReuseFormAndDetailBuilders() {
        PageSpec update = new PageSpec(
                "user-form", "CRUD_FORM", List.of(field("userId", "사용자 ID")),
                List.of("SAVE", "CANCEL"));
        PageSpec detail = new PageSpec(
                "user-detail", "CRUD_DETAIL", List.of(field("userId", "사용자 ID")),
                List.of("UPDATE", "LIST"));
        ScreenSpecification spec = specification("CRUD_DETAIL", List.of(update, detail));

        FigmaNodeSpec updateRoot = new FormFigmaScreenBuilder().build(spec, update, ids);
        FigmaNodeSpec detailRoot = new DetailFigmaScreenBuilder().build(spec, detail, ids);

        assertThat(resolver.resolveScreenType(update, spec)).isEqualTo(FigmaScreenType.FORM);
        assertThat(updateRoot.type()).isEqualTo("egov.formPage");
        assertThat(updateRoot.children()).extracting(FigmaNodeSpec::type)
                .contains("egov.formSection", "egov.validationSummary");
        assertThat(resolver.resolveScreenType(detail, spec)).isEqualTo(FigmaScreenType.DETAIL);
        assertThat(detailRoot.type()).isEqualTo("egov.detailPage");
        assertThat(detailRoot.children()).extracting(FigmaNodeSpec::type)
                .contains("egov.detailSection");
    }

    @Test
    void boardListAndRegistrationPreserveSearchTableAndFormSemantics() {
        ScreenFieldBinding title = new ScreenFieldBinding(
                "title", "제목", UiFieldRole.TITLE, FieldSource.column("B", "NTT_SJ"),
                true, true, true, true, "TEXT", 1.0);
        PageSpec list = new PageSpec(
                "board-list", "BOARD_LIST", List.of(title), List.of("SEARCH", "CREATE"));
        PageSpec form = new PageSpec(
                "board-form", "BOARD_FORM", List.of(title), List.of("SAVE", "CANCEL"));
        ScreenSpecification spec = specification("BOARD_LIST", List.of(list, form));

        FigmaNodeSpec listRoot = new ListFigmaScreenBuilder().build(spec, list, ids);
        FigmaNodeSpec formRoot = new FormFigmaScreenBuilder().build(spec, form, ids);

        assertThat(listRoot.children()).extracting(FigmaNodeSpec::type)
                .contains("egov.searchPanel", "egov.dataTable", "krds.pagination");
        assertThat(formRoot.children()).extracting(FigmaNodeSpec::type)
                .contains("egov.formSection", "egov.validationSummary");
    }

    @Test
    void masterDetailUsesIndependentScreenTypeAndLayoutPattern() {
        PageSpec master = new PageSpec(
                "order-list", "MASTER_LIST", List.of(field("orderId", "주문 ID")),
                List.of("SEARCH", "CREATE"));
        PageSpec detail = new PageSpec(
                "order-detail", "MASTER_DETAIL", List.of(field("itemId", "품목 ID")),
                List.of("UPDATE"));
        ScreenSpecification spec = specification("MASTER_DETAIL", List.of(master, detail));

        assertThat(resolver.resolveScreenType(master, spec)).isEqualTo(FigmaScreenType.LIST);
        assertThat(resolver.resolveScreenType(detail, spec)).isEqualTo(FigmaScreenType.DETAIL);
        assertThat(resolver.resolveLayoutPattern(spec)).isEqualTo(LayoutPattern.MASTER_DETAIL);
        assertThat(new ListFigmaScreenBuilder().build(spec, master, ids).type())
                .isEqualTo("egov.listPage");
        assertThat(new DetailFigmaScreenBuilder().build(spec, detail, ids).type())
                .isEqualTo("egov.detailPage");
    }

    private ScreenFieldBinding field(String id, String label) {
        return new ScreenFieldBinding(
                id, label, UiFieldRole.ID, FieldSource.column("T", id),
                true, true, false, true, "TEXT", 1.0);
    }

    private ScreenSpecification specification(String archetype, List<PageSpec> pages) {
        return new ScreenSpecification(
                "spec-" + archetype.toLowerCase().replace('_', '-'), 1,
                ScreenSpecStatus.APPROVED, "업무 화면", "CRUD", archetype,
                "com", "TABLE", List.of(), pages, List.of(), LocalDateTime.now());
    }
}
