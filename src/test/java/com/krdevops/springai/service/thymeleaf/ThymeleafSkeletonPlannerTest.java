package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.SkeletonSlotKind;
import com.krdevops.springai.model.thymeleaf.ThymeleafSkeleton;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Skeleton은 screenRole만으로 구조를 결정하며 어떤 필드 값도 담지 않는다. */
class ThymeleafSkeletonPlannerTest {

    private final ThymeleafSkeletonPlanner planner = new ThymeleafSkeletonPlanner();

    @Test
    void listRoleProducesSearchFormAndDataTableSlots() {
        ThymeleafSkeleton skeleton = planner.plan("emp-list", LegacyScreenRole.LIST, "직원 목록");
        assertThat(skeleton.slots()).containsExactly(
                SkeletonSlotKind.SEARCH_FORM, SkeletonSlotKind.DATA_TABLE, SkeletonSlotKind.ACTION_BAR);
        assertThat(skeleton.layoutFragmentRef()).isEqualTo("layout/default");
    }

    @Test
    void formRoleProducesFormFieldsSlot() {
        ThymeleafSkeleton skeleton = planner.plan("emp-form", LegacyScreenRole.FORM, "직원 등록");
        assertThat(skeleton.slots()).containsExactly(
                SkeletonSlotKind.FORM_FIELDS, SkeletonSlotKind.ACTION_BAR);
    }

    @Test
    void detailRoleProducesDisplayFieldsSlot() {
        ThymeleafSkeleton skeleton = planner.plan("emp-detail", LegacyScreenRole.DETAIL, "직원 상세");
        assertThat(skeleton.slots()).containsExactly(
                SkeletonSlotKind.DISPLAY_FIELDS, SkeletonSlotKind.ACTION_BAR);
    }
}
