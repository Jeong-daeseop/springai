package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.config.CrudGenerationApprovalProperties;
import org.springframework.stereotype.Component;

/**
 * CRUD_명시적_승인_단계_구현_명세서.md §4 옵션 B. {@code approvalRequiredForAll}이면 테이블·
 * viewType에 상관없이 항상 승인을 요구하고, 아니면 {@code approvalRequiredTables}에 등록된
 * 테이블만 대상으로 한다.
 */
@Component
public class CrudGenerationApprovalPolicy {

    private final CrudGenerationApprovalProperties properties;

    public CrudGenerationApprovalPolicy(CrudGenerationApprovalProperties properties) {
        this.properties = properties;
    }

    public boolean requiresApproval(String tableName) {
        if (properties.isApprovalRequiredForAll()) {
            return true;
        }
        if (tableName == null) {
            return false;
        }
        return properties.getApprovalRequiredTables().stream()
                .anyMatch(configured -> configured.equalsIgnoreCase(tableName));
    }
}
