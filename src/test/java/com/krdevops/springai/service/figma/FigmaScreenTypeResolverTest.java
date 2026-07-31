package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R2-T06: 기존 archetype별 Builder 선택(screenType/layoutPattern 판정)과 미등록값 거부. */
class FigmaScreenTypeResolverTest {

    private final FigmaScreenTypeResolver resolver = new FigmaScreenTypeResolver();

    @Test
    void resolvesListTypeFromPageTemplateSuffix() {
        PageSpec page = page("CRUD_LIST");
        assertThat(resolver.resolveScreenType(page, spec("CRUD_LIST"))).isEqualTo(FigmaScreenType.LIST);
    }

    @Test
    void resolvesFormTypeFromRegistSuffixFallback() {
        PageSpec page = new PageSpec("regist", null, List.of(), List.of(), FieldSelectionSource.DEFAULT);
        assertThat(resolver.resolveScreenType(page, spec("CRUD_REGIST"))).isEqualTo(FigmaScreenType.FORM);
    }

    @Test
    void resolvesDetailTypeFromBoardDetailTemplate() {
        PageSpec page = page("BOARD_DETAIL");
        assertThat(resolver.resolveScreenType(page, spec("BOARD_LIST"))).isEqualTo(FigmaScreenType.DETAIL);
    }

    @Test
    void rejectsUnmappedSuffix() {
        PageSpec page = page("CRUD_DASHBOARD");
        assertThatThrownBy(() -> resolver.resolveScreenType(page, spec("CRUD_DASHBOARD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvesMasterDetailLayoutPatternFromArchetypeKeyword() {
        assertThat(resolver.resolveLayoutPattern(spec("MASTER_DETAIL"))).isEqualTo(LayoutPattern.MASTER_DETAIL);
    }

    @Test
    void resolvesStandardLayoutPatternByDefault() {
        assertThat(resolver.resolveLayoutPattern(spec("CRUD_LIST"))).isEqualTo(LayoutPattern.STANDARD);
    }

    @Test
    void screenTypeAndLayoutPatternDoNotConflictForMasterDetailArchetype() {
        // MASTER_DETAIL 자체는 접미사가 없으므로 PageSpec.template(base+"_DETAIL" 등)으로 screenType을 판정하고,
        // layoutPattern은 archetype 문자열 자체의 키워드로 독립 판정한다 — 서로 다른 필드라 충돌하지 않는다.
        PageSpec page = page("MASTER_DETAIL");
        ScreenSpecification masterDetailSpec = spec("MASTER_DETAIL");

        assertThat(resolver.resolveScreenType(page, masterDetailSpec)).isEqualTo(FigmaScreenType.DETAIL);
        assertThat(resolver.resolveLayoutPattern(masterDetailSpec)).isEqualTo(LayoutPattern.MASTER_DETAIL);
    }

    private PageSpec page(String template) {
        return new PageSpec("p", template, List.of(), List.of(), FieldSelectionSource.DEFAULT);
    }

    private ScreenSpecification spec(String archetype) {
        return new ScreenSpecification(
                "s", 1, ScreenSpecStatus.APPROVED, "화면", "crud", archetype, "ebt", "T",
                List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, null);
    }
}
