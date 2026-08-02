package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("I-4A: LayoutTypeResolver 테스트")
class LayoutTypeResolverTest {

    @Autowired
    private LayoutTypeResolver resolver;

    @Test
    @DisplayName("NULL 입력시 기본값 STANDARD")
    void testNullInputLayoutDensity() {
        var decision = resolver.resolveLayoutDensity(null);
        assertEquals(LayoutDensity.STANDARD, decision.density());
        assertTrue(decision.confidence() >= 0.7);
    }

    @Test
    @DisplayName("기본값: STANDARD layout")
    void testStandardLayoutDensity() {
        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "일반 폼",
            "CRUD", null, "db1", "table1",
            List.of(), List.of(), List.of(),
            LocalDateTime.now()
        );

        var decision = resolver.resolveLayoutDensity(spec);
        assertEquals(LayoutDensity.STANDARD, decision.density());
    }

    @Test
    @DisplayName("기본값: SINGLE_COLUMN form layout")
    void testSingleColumnFormLayout() {
        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "간단한 폼",
            "CRUD", null, "db1", "table1",
            List.of(), List.of(), List.of(),
            LocalDateTime.now()
        );

        var decision = resolver.resolveFormColumnLayout(spec);
        assertEquals(FormColumnLayout.SINGLE_COLUMN, decision.columnLayout());
        assertTrue(decision.confidence() >= 0.7);
    }

    @Test
    @DisplayName("LIST 화면: actionPlacement는 TOP_RIGHT")
    void testListActionPlacement() {
        var decision = resolver.resolveActionPlacement("LIST");
        assertEquals(ActionPlacement.TOP_RIGHT, decision.placement());
        assertEquals(0.9, decision.confidence(), 0.01);
    }

    @Test
    @DisplayName("FORM 화면: actionPlacement는 BOTTOM_RIGHT")
    void testFormActionPlacement() {
        var decision = resolver.resolveActionPlacement("FORM");
        assertEquals(ActionPlacement.BOTTOM_RIGHT, decision.placement());
        assertEquals(0.9, decision.confidence(), 0.01);
    }

    @Test
    @DisplayName("DETAIL 화면: actionPlacement는 TOP_RIGHT")
    void testDetailActionPlacement() {
        var decision = resolver.resolveActionPlacement("DETAIL");
        assertEquals(ActionPlacement.TOP_RIGHT, decision.placement());
    }

    @Test
    @DisplayName("LIST 화면: searchPanel은 ABOVE_TABLE")
    void testListSearchPanelPlacement() {
        var decision = resolver.resolveSearchPanelPlacement("LIST");
        assertEquals(SearchPanelPlacement.ABOVE_TABLE, decision.placement());
        assertEquals(0.95, decision.confidence(), 0.01);
    }

    @Test
    @DisplayName("FORM 화면: searchPanel은 NONE")
    void testFormSearchPanelPlacement() {
        var decision = resolver.resolveSearchPanelPlacement("FORM");
        assertEquals(SearchPanelPlacement.NONE, decision.placement());
        assertEquals(0.9, decision.confidence(), 0.01);
    }

    @Test
    @DisplayName("NULL archetype: 기본값")
    void testNullArchetypeActionPlacement() {
        var decision = resolver.resolveActionPlacement(null);
        assertEquals(ActionPlacement.TOP_RIGHT, decision.placement());
        assertTrue(decision.confidence() >= 0.7);
    }
}
