package com.krdevops.springai.policy;

import com.krdevops.springai.model.design.UiFieldRole;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 화면 역할과 eGovFrame 관례 컬럼 사이의 공통 정책. */
public final class UiFieldRolePolicy {

    private static final Map<UiFieldRole, List<String>> ROLE_COLUMNS = roleColumns();

    public static final List<UiFieldRole> LIST_ROLE_PRIORITY = List.of(
            UiFieldRole.ID, UiFieldRole.TITLE, UiFieldRole.CATEGORY, UiFieldRole.STATUS,
            UiFieldRole.AUTHOR, UiFieldRole.DEPARTMENT, UiFieldRole.CREATED_AT,
            UiFieldRole.UPDATED_AT, UiFieldRole.ATTACHMENT, UiFieldRole.SORT_ORDER);

    private UiFieldRolePolicy() {
    }

    public static List<String> candidateColumns(UiFieldRole role) {
        return ROLE_COLUMNS.getOrDefault(role, List.of());
    }

    public static UiFieldRole inferRole(String columnName) {
        if (columnName == null) return UiFieldRole.GENERIC;
        return ROLE_COLUMNS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(columnName::equalsIgnoreCase))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(UiFieldRole.GENERIC);
    }

    private static Map<UiFieldRole, List<String>> roleColumns() {
        Map<UiFieldRole, List<String>> result = new LinkedHashMap<>();
        result.put(UiFieldRole.ID, List.of("ID", "NTT_ID", "BBS_ID"));
        result.put(UiFieldRole.TITLE, List.of("NTT_SJ", "TITLE", "SUBJECT", "SJ"));
        result.put(UiFieldRole.CONTENT, List.of("NTT_CN", "CONTENT", "CONTENTS", "CN"));
        result.put(UiFieldRole.CATEGORY, List.of("CATEGORY", "CATEGORY_CODE", "CL_CODE", "BBS_ID"));
        result.put(UiFieldRole.STATUS, List.of("USE_AT", "STATUS", "STATUS_CODE", "EMPLYR_STTUS_CODE"));
        result.put(UiFieldRole.AUTHOR, List.of("NTCR_NM", "WRTER_NM", "FRST_REGISTER_ID", "AUTHOR"));
        result.put(UiFieldRole.DEPARTMENT, List.of("ORGNZT_ID", "DEPT_ID", "DEPARTMENT_ID"));
        result.put(UiFieldRole.CREATED_AT, List.of("FRST_REGIST_PNTTM", "CREATED_AT", "REG_DATE"));
        result.put(UiFieldRole.UPDATED_AT, List.of("LAST_UPDT_PNTTM", "UPDATED_AT", "MOD_DATE"));
        result.put(UiFieldRole.ATTACHMENT, List.of("ATCH_FILE_ID", "FILE_ID"));
        result.put(UiFieldRole.SORT_ORDER, List.of("SORT_ORDR", "SORT_ORDER", "ORDR"));
        return Map.copyOf(result);
    }
}
