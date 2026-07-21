package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenDataBindingResolverTest {

    @Test
    void promotesDepartmentIdOnlyWhenRelationAndDisplayColumnAreUnambiguous() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        TableRelationService relations = mock(TableRelationService.class);
        when(relations.getPhysicalFkParents("com", "EMPLOYEE")).thenReturn(List.of(
                new TableRelationService.RelationInfo(
                        "COMTNORGNZTINFO", "ORGNZT_ID", "ORGNZT_ID",
                        TableRelationService.RelationType.FK_PARENT)));
        when(relations.getImplicitJoinCandidates("com", "EMPLOYEE")).thenReturn(List.of());
        when(schema.fetchColumns("com", "COMTNORGNZTINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ORGNZT_NM")));
        ScreenDataBindingResolver resolver = new ScreenDataBindingResolver(schema, relations);

        ScreenSpecification result = resolver.resolve(specification());

        assertThat(result.dataSources()).hasSize(2);
        assertThat(result.dataSources().get(1).joinExpression())
                .isEqualTo("t.ORGNZT_ID = j1.ORGNZT_ID");
        assertThat(result.pages().get(0).fields().get(0).source().type())
                .isEqualTo(FieldSourceType.JOIN_COLUMN);
        assertThat(result.pages().get(0).fields().get(0).source().column())
                .isEqualTo("ORGNZT_NM");
        assertThat(result.pages().get(0).selectionSource())
                .isEqualTo(FieldSelectionSource.DESIGN_REFERENCE);
    }

    @Test
    void resolvePreservesFormColumnLayoutWhenJoinIsPromoted() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        TableRelationService relations = mock(TableRelationService.class);
        when(relations.getPhysicalFkParents("com", "EMPLOYEE")).thenReturn(List.of(
                new TableRelationService.RelationInfo(
                        "COMTNORGNZTINFO", "ORGNZT_ID", "ORGNZT_ID",
                        TableRelationService.RelationType.FK_PARENT)));
        when(relations.getImplicitJoinCandidates("com", "EMPLOYEE")).thenReturn(List.of());
        when(schema.fetchColumns("com", "COMTNORGNZTINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ORGNZT_NM")));
        ScreenDataBindingResolver resolver = new ScreenDataBindingResolver(schema, relations);

        ScreenSpecification result = resolver.resolve(specification(FormColumnLayout.TWO_COLUMN));

        // JOIN 승격이 실제로 일어났는지 먼저 확인(회귀 테스트가 아무 것도 검증하지 않는 상태로
        // 통과하는 것을 방지 — dataSources가 2개면 resolveJoin()이 실행된 것)
        assertThat(result.dataSources()).hasSize(2);
        assertThat(result.formColumnLayout()).isEqualTo(FormColumnLayout.TWO_COLUMN);
    }

    @Test
    void resolvePreservesActionAndSearchPanelPlacementWhenJoinIsPromoted() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        TableRelationService relations = mock(TableRelationService.class);
        when(relations.getPhysicalFkParents("com", "EMPLOYEE")).thenReturn(List.of(
                new TableRelationService.RelationInfo(
                        "COMTNORGNZTINFO", "ORGNZT_ID", "ORGNZT_ID",
                        TableRelationService.RelationType.FK_PARENT)));
        when(relations.getImplicitJoinCandidates("com", "EMPLOYEE")).thenReturn(List.of());
        when(schema.fetchColumns("com", "COMTNORGNZTINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ORGNZT_NM")));
        ScreenDataBindingResolver resolver = new ScreenDataBindingResolver(schema, relations);

        ScreenSpecification result = resolver.resolve(specification(
                FormColumnLayout.SINGLE_COLUMN, ActionPlacement.BOTTOM_RIGHT, SearchPanelPlacement.NONE));

        assertThat(result.dataSources()).hasSize(2);
        assertThat(result.actionPlacement()).isEqualTo(ActionPlacement.BOTTOM_RIGHT);
        assertThat(result.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.NONE);
    }

    private ScreenSpecification specification() {
        return specification(FormColumnLayout.SINGLE_COLUMN);
    }

    private ScreenSpecification specification(FormColumnLayout formColumnLayout) {
        return specification(formColumnLayout, ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE);
    }

    private ScreenSpecification specification(
            FormColumnLayout formColumnLayout,
            ActionPlacement actionPlacement,
            SearchPanelPlacement searchPanelPlacement) {
        ScreenFieldBinding field = new ScreenFieldBinding(
                "department", "부서", UiFieldRole.DEPARTMENT, FieldSource.column("t", "ORGNZT_ID"),
                true, false, true, true, "TEXT", 1.0);
        return new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "직원", "crud", "CRUD",
                "com", "EMPLOYEE", List.of(DataSourceSpec.primary("com", "EMPLOYEE")),
                List.of(new PageSpec("list", "CRUD_LIST", List.of(field), List.of("SEARCH"),
                        FieldSelectionSource.DESIGN_REFERENCE)),
                List.of(), LayoutDensity.STANDARD, formColumnLayout,
                actionPlacement, searchPanelPlacement, LocalDateTime.now());
    }
}
