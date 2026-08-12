package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.UiFieldRole;

import java.util.List;

/** R2 Builder 테스트가 공유하는 "사용자 목록/등록" 샘플 ScreenSpecification. */
public final class FigmaBuilderTestFixtures {

    private FigmaBuilderTestFixtures() {
    }

    public static ScreenSpecification userManagementSpec() {
        PageSpec listPage = new PageSpec("list", "CRUD_LIST", List.of(
                field("userId", "사용자ID", UiFieldRole.ID, true, false, false, false, "TEXT"),
                field("userName", "사용자명", UiFieldRole.TITLE, true, false, true, true, "TEXT"),
                field("userStatus", "사용 상태", UiFieldRole.STATUS, true, false, true, false, "SELECT")
        ), PageSpec.migrateActions("SEARCH", "CREATE", "VIEW_DETAIL", "UPDATE", "DELETE"), FieldSelectionSource.DEFAULT);

        PageSpec registPage = new PageSpec("regist", "CRUD_FORM", List.of(
                field("userName", "사용자명", UiFieldRole.TITLE, true, true, false, false, "TEXT"),
                field("userStatus", "사용 상태", UiFieldRole.STATUS, true, true, false, false, "SELECT")
        ), PageSpec.migrateActions("SAVE", "CANCEL"), FieldSelectionSource.DEFAULT);

        return new ScreenSpecification(
                "spec-user-management", 3, ScreenSpecStatus.APPROVED,
                "사용자 목록", "crud", "CRUD_LIST", "ebt", "COMTNEMPLYRINFO",
                List.of(), List.of(listPage, registPage), List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, null);
    }

    private static ScreenFieldBinding field(
            String id, String label, UiFieldRole role, boolean visible, boolean required,
            boolean searchable, boolean sortable, String control) {
        return new ScreenFieldBinding(
                id, label, role, FieldSource.column("t", id), visible, required, searchable, sortable, control, 1.0);
    }
}
