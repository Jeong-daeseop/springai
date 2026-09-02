package com.krdevops.springai.service;

import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.UiDesignSpec;
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
    void preservesDesignContextWhenJoinIsAdded() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        TableRelationService relations = mock(TableRelationService.class);
        ScreenDataBindingResolver resolver = new ScreenDataBindingResolver(schema, relations);
        ScreenSpecification specification = specification(UiFieldRole.DEPARTMENT, "ORGNZT_ID");
        when(relations.getPhysicalFkParents("com", "LETTNEMPLYRINFO")).thenReturn(List.of(
                new TableRelationService.RelationInfo(
                        "LETTNORGNZTINFO", "ORGNZT_ID", "ORGNZT_ID",
                        TableRelationService.RelationType.FK_PARENT)));
        when(relations.getImplicitJoinCandidates("com", "LETTNEMPLYRINFO")).thenReturn(List.of());
        when(schema.fetchColumns("com", "LETTNORGNZTINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ORGNZT_NM")));

        ScreenSpecification resolved = resolver.resolve(specification);

        assertThat(resolved).isNotSameAs(specification);
        assertThat(resolved.dataSources()).hasSize(2);
        assertThat(resolved.pages().get(0).fields().get(0).source().type())
                .isEqualTo(FieldSourceType.JOIN_COLUMN);
        assertThat(resolved.componentStyles()).isEqualTo(specification.componentStyles());
        assertThat(resolved.componentGeometry()).isEqualTo(specification.componentGeometry());
        assertThat(resolved.tokens()).isEqualTo(specification.tokens());
    }

    @Test
    void returnsOriginalInstanceWhenNoJoinIsResolved() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        TableRelationService relations = mock(TableRelationService.class);
        ScreenDataBindingResolver resolver = new ScreenDataBindingResolver(schema, relations);
        ScreenSpecification specification = specification(UiFieldRole.TITLE, "USER_NM");
        when(relations.getPhysicalFkParents("com", "LETTNEMPLYRINFO")).thenReturn(List.of());
        when(relations.getImplicitJoinCandidates("com", "LETTNEMPLYRINFO")).thenReturn(List.of());

        ScreenSpecification resolved = resolver.resolve(specification);

        assertThat(resolved).isSameAs(specification);
    }

    private ScreenSpecification specification(UiFieldRole role, String column) {
        UiDesignSpec.ComponentSpec style = new UiDesignSpec.ComponentSpec(
                "FORM", List.of("department"), "rgba(255,255,255,1.00)", "rgba(0,0,0,1.00)");
        UiDesignSpec.NodeGeometry geometry = new UiDesignSpec.NodeGeometry(
                "1:1", "FRAME", "Form", 0, 0, 800, 600,
                8, 0.9, "rgba(255,255,255,1.00)", null, null, null, List.of());
        ScreenFieldBinding field = new ScreenFieldBinding(
                "department", "부서", role, FieldSource.column("t", column),
                true, true, true, true, "TEXT", 1.0);
        return new ScreenSpecification(
                "spec-join", 1, ScreenSpecStatus.REVIEW_REQUIRED, "직원", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(new PageSpec("list", "CRUD_LIST", List.of(field), PageSpec.migrateActions("SEARCH"))),
                List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, LocalDateTime.now(),
                null, null, List.of(style), List.of(geometry), Map.of("fontFamily", "Pretendard"));
    }
}
