package com.krdevops.springai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CRUD_명시적_승인_단계_구현_명세서.md §4 옵션 B — 특정 테이블(또는 전체)에 한해 승인된 화면명세
 * 없이는 auto 생성을 차단하는 조건부 승인 게이트 설정. 기본값은 빈 목록·false라 이 기능을
 * 켜기 전까지는 기존 동작이 전혀 바뀌지 않는다.
 */
@ConfigurationProperties(prefix = "app.crud-generation")
public class CrudGenerationApprovalProperties {

    private List<String> approvalRequiredTables = List.of();
    private boolean approvalRequiredForAll = false;

    public List<String> getApprovalRequiredTables() {
        return approvalRequiredTables;
    }

    public void setApprovalRequiredTables(List<String> approvalRequiredTables) {
        this.approvalRequiredTables = approvalRequiredTables == null ? List.of() : approvalRequiredTables;
    }

    public boolean isApprovalRequiredForAll() {
        return approvalRequiredForAll;
    }

    public void setApprovalRequiredForAll(boolean approvalRequiredForAll) {
        this.approvalRequiredForAll = approvalRequiredForAll;
    }
}
