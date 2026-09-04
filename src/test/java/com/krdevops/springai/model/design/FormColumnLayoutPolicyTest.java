package com.krdevops.springai.model.design;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("FormColumnLayoutPolicy")
class FormColumnLayoutPolicyTest {

    @Test
    @DisplayName("스키마 전용(screenSpec=null): 폼 필드 10개 이상이면 TWO_COLUMN")
    void schemaOnly_tenOrMore_twoColumn() {
        assertEquals(FormColumnLayout.TWO_COLUMN, FormColumnLayoutPolicy.resolve(10, null));
        assertEquals(FormColumnLayout.TWO_COLUMN, FormColumnLayoutPolicy.resolve(25, null));
    }

    @Test
    @DisplayName("스키마 전용(screenSpec=null): 폼 필드 10개 미만이면 SINGLE_COLUMN")
    void schemaOnly_belowTen_singleColumn() {
        assertEquals(FormColumnLayout.SINGLE_COLUMN, FormColumnLayoutPolicy.resolve(9, null));
        assertEquals(FormColumnLayout.SINGLE_COLUMN, FormColumnLayoutPolicy.resolve(0, null));
    }

    @Test
    @DisplayName("디자인 참조가 TWO_COLUMN 명시: 필드 수와 무관하게 TWO_COLUMN")
    void designRef_explicitTwoColumn_wins() {
        assertEquals(FormColumnLayout.TWO_COLUMN,
                FormColumnLayoutPolicy.resolve(3, spec(FormColumnLayout.TWO_COLUMN)));
    }

    @Test
    @DisplayName("디자인 참조 존재 + SINGLE_COLUMN: 필드 수 heuristic 미발동, SINGLE_COLUMN")
    void designRef_singleColumn_noHeuristic() {
        assertEquals(FormColumnLayout.SINGLE_COLUMN,
                FormColumnLayoutPolicy.resolve(50, spec(FormColumnLayout.SINGLE_COLUMN)));
    }

    private static ScreenSpecification spec(FormColumnLayout layout) {
        return new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "n", "crud", null, "db", "t",
                List.of(), List.of(), List.of(),
                LayoutDensity.STANDARD, layout, LocalDateTime.now());
    }
}
