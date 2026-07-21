package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationQueryContractFactoryTest {

    private final GenerationQueryContractFactory factory = new GenerationQueryContractFactory();

    @Test
    void buildsJoinAndCommonCodeProjectionsWithRequestedPrimaryAlias() {
        var contract = factory.create(specification(), physicalFields(), "b");

        assertThat(contract.joins()).hasSize(2);
        assertThat(contract.joins().get(0).onExpression())
                .isEqualTo("b.ORGNZT_ID = dept.ORGNZT_ID");
        assertThat(contract.joins().get(1).onExpression())
                .isEqualTo("b.STATUS_CODE = cc1.CODE AND cc1.CODE_ID = 'COM001'");
        assertThat(contract.projections()).extracting("selectExpression")
                .containsExactly("dept.ORGNZT_NM AS department", "cc1.CODE_NM AS status");
        assertThat(contract.displayFields()).extracting("javaName")
                .containsExactly("department", "status");
    }

    @Test
    void rejectsJoinAliasReservedByTargetTemplate() {
        ScreenSpecification original = specification();
        ScreenSpecification collision = new ScreenSpecification(
                original.id(), original.version(), original.status(), original.screenName(),
                original.featureType(), original.archetype(), original.database(), original.primaryTable(),
                List.of(
                        DataSourceSpec.primary("com", "EMPLOYEE"),
                        new DataSourceSpec("join-m", "com", "COMTNORGNZTINFO", "m", false,
                                "LEFT", "t.ORGNZT_ID = m.ORGNZT_ID")),
                original.pages(), original.issues(), original.createdAt());

        assertThatThrownBy(() -> factory.create(collision, physicalFields(), "b", java.util.Set.of("b", "m")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("예약 alias");
    }

    static ScreenSpecification specification() {
        ScreenFieldBinding department = new ScreenFieldBinding(
                "department", "부서", UiFieldRole.DEPARTMENT,
                FieldSource.joinColumn("dept", "ORGNZT_NM"),
                true, false, false, false, "TEXT", 1.0);
        ScreenFieldBinding status = new ScreenFieldBinding(
                "status", "상태", UiFieldRole.STATUS,
                FieldSource.commonCode("t", "STATUS_CODE", "COM001"),
                true, false, true, false, "SELECT", 1.0);
        return new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "직원", "crud", "CRUD",
                "com", "EMPLOYEE",
                List.of(
                        DataSourceSpec.primary("com", "EMPLOYEE"),
                        new DataSourceSpec("join-dept", "com", "COMTNORGNZTINFO", "dept", false,
                                "LEFT", "t.ORGNZT_ID = dept.ORGNZT_ID")),
                List.of(new PageSpec("list", "CRUD_LIST", List.of(department, status), List.of())),
                List.of(), LocalDateTime.now());
    }

    static List<FieldModel> physicalFields() {
        return List.of(
                new FieldModel("ID", "id", "Long", "ID", true, true, false, null, "BIGINT"),
                new FieldModel("ORGNZT_ID", "orgnztId", "String", "부서ID", false, false, true, 20, "VARCHAR"),
                new FieldModel("STATUS_CODE", "statusCode", "String", "상태코드", false, false, true, 20, "VARCHAR"));
    }
}
