package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenActionSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.UiFieldRole;
import com.krdevops.springai.model.design.role.ComponentState;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KRV-012: 기존 문자열 Action/Control을 Semantic Context로 정규화하는 변환기 검증. */
class ScreenSemanticNormalizerTest {

    private final ScreenSemanticNormalizer normalizer = new ScreenSemanticNormalizer();

    @Test
    void listTemplateResolvesToCrudList() {
        assertThat(normalizer.pattern(page("CRUD_LIST", List.of()))).isEqualTo(ScreenPattern.CRUD_LIST);
    }

    @Test
    void detailTemplateResolvesToCrudDetail() {
        assertThat(normalizer.pattern(page("CRUD_DETAIL", List.of()))).isEqualTo(ScreenPattern.CRUD_DETAIL);
    }

    @Test
    void formTemplateWithUpdateActionResolvesToCrudEdit() {
        assertThat(normalizer.pattern(page("CRUD_FORM", List.of("UPDATE", "CANCEL")))).isEqualTo(ScreenPattern.CRUD_EDIT);
    }

    @Test
    void formTemplateWithoutUpdateActionResolvesToCrudCreate() {
        assertThat(normalizer.pattern(page("CRUD_FORM", List.of("SAVE", "CANCEL")))).isEqualTo(ScreenPattern.CRUD_CREATE);
    }

    @Test
    void unrecognizedTemplateThrowsScreenPatternNotResolved() {
        assertThatThrownBy(() -> normalizer.pattern(page("DASHBOARD", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCREEN_PATTERN_NOT_RESOLVED");
    }

    @Test
    void fieldRoleMapsKnownControlsToSemanticRole() {
        assertThat(normalizer.fieldRole(fieldWithControl("TEXT"))).isEqualTo(SemanticRole.FIELD_TEXT);
        assertThat(normalizer.fieldRole(fieldWithControl("NUMBER"))).isEqualTo(SemanticRole.FIELD_TEXT);
        assertThat(normalizer.fieldRole(fieldWithControl("DATE"))).isEqualTo(SemanticRole.FIELD_TEXT);
        assertThat(normalizer.fieldRole(fieldWithControl("TEXTAREA"))).isEqualTo(SemanticRole.FIELD_TEXTAREA);
        assertThat(normalizer.fieldRole(fieldWithControl("SELECT"))).isEqualTo(SemanticRole.FIELD_SELECT);
        assertThat(normalizer.fieldRole(fieldWithControl("CHECKBOX"))).isEqualTo(SemanticRole.FIELD_CHECKBOX);
    }

    @Test
    void unsupportedControlThrowsSemanticRoleNotDerived() {
        assertThatThrownBy(() -> normalizer.fieldRole(fieldWithControl("RICH_TEXT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEMANTIC_ROLE_NOT_DERIVED");
    }

    @Test
    void detailPatternProducesReadOnlyFieldMode() {
        assertThat(normalizer.fieldMode(ScreenPattern.CRUD_DETAIL)).isEqualTo(FieldMode.READ_ONLY);
    }

    @Test
    void nonDetailPatternProducesEditableFieldMode() {
        assertThat(normalizer.fieldMode(ScreenPattern.CRUD_CREATE)).isEqualTo(FieldMode.EDITABLE);
        assertThat(normalizer.fieldMode(ScreenPattern.CRUD_EDIT)).isEqualTo(FieldMode.EDITABLE);
        assertThat(normalizer.fieldMode(ScreenPattern.CRUD_LIST)).isEqualTo(FieldMode.EDITABLE);
    }

    @Test
    void deleteActionResolvesToDestructiveRole() {
        ScreenActionSpec action = normalizer.action("DELETE");
        assertThat(action.role()).isEqualTo(SemanticRole.ACTION_DESTRUCTIVE);
        assertThat(action.state()).isEqualTo(ComponentState.DEFAULT);
    }

    @Test
    void listCancelViewDetailAndBackResolveToSecondaryRole() {
        for (String command : List.of("LIST", "CANCEL", "VIEW_DETAIL", "BACK")) {
            assertThat(normalizer.action(command).role())
                    .as("command=" + command)
                    .isEqualTo(SemanticRole.ACTION_SECONDARY);
        }
    }

    @Test
    void searchCreateSaveUpdateResolveToPrimaryRole() {
        for (String command : List.of("SEARCH", "CREATE", "SAVE", "UPDATE")) {
            assertThat(normalizer.action(command).role())
                    .as("command=" + command)
                    .isEqualTo(SemanticRole.ACTION_PRIMARY);
        }
    }

    @Test
    void backActionHasAKoreanLabel() {
        assertThat(normalizer.action("BACK").label()).isEqualTo("목록");
    }

    @Test
    void unknownActionThrowsSemanticRoleNotDerived() {
        assertThatThrownBy(() -> normalizer.action("EXPORT_EXCEL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEMANTIC_ROLE_NOT_DERIVED");
    }

    @Test
    void actionCommandIsNormalizedToUppercaseRegardlessOfInputCase() {
        assertThat(normalizer.action("delete").command()).isEqualTo("DELETE");
        assertThat(normalizer.action("delete").role()).isEqualTo(SemanticRole.ACTION_DESTRUCTIVE);
    }

    private PageSpec page(String template, List<String> actions) {
        return new PageSpec("p", template, List.of(),
                actions.stream().map(com.krdevops.springai.model.design.ScreenActionSpec::fromLegacyCommand).toList());
    }

    private ScreenFieldBinding fieldWithControl(String control) {
        return new ScreenFieldBinding("f", "Field", UiFieldRole.GENERIC,
                FieldSource.column("t", "COL"), true, false, false, false, control, 1.0);
    }
}
