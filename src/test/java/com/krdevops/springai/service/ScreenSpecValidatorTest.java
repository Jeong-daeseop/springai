package com.krdevops.springai.service;

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

class ScreenSpecValidatorTest {

    private final ScreenSpecValidator validator = new ScreenSpecValidator();

    @Test
    void missingPrimarySourceBlocksApproval() {
        ScreenSpecification draft = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.DRAFT, "화면", "crud", "CRUD",
                null, null, List.of(), List.of(), List.of(), LocalDateTime.now());

        ScreenSpecification validated = validator.validate(draft);

        assertThat(validated.status()).isEqualTo(ScreenSpecStatus.REVIEW_REQUIRED);
        assertThat(validated.issues()).extracting("code")
                .contains("PRIMARY_DATA_SOURCE_REQUIRED", "PAGE_REQUIRED");
        assertThatThrownBy(() -> validator.approve(draft))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsafeJoinExpression() {
        ScreenFieldBinding field = new ScreenFieldBinding(
                "department", "부서", UiFieldRole.DEPARTMENT,
                FieldSource.joinColumn("j1", "ORGNZT_NM"),
                true, false, false, false, "TEXT", 1.0);
        ScreenSpecification specification = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.DRAFT, "직원", "crud", "CRUD",
                "com", "EMPLOYEE",
                List.of(
                        DataSourceSpec.primary("com", "EMPLOYEE"),
                        new DataSourceSpec("join-j1", "com", "COMTNORGNZTINFO", "j1", false,
                                "LEFT", "t.ORGNZT_ID = j1.ORGNZT_ID; DELETE FROM EMPLOYEE")),
                List.of(new PageSpec("list", "CRUD_LIST", List.of(field), List.of())),
                List.of(), LocalDateTime.now());

        ScreenSpecification result = validator.validate(specification);

        assertThat(result.status()).isEqualTo(ScreenSpecStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).extracting("code").contains("UNSAFE_JOIN_EXPRESSION");
    }
}
