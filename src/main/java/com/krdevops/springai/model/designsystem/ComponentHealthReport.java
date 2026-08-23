package com.krdevops.springai.model.designsystem;

import java.util.List;

public record ComponentHealthReport(String componentId, Status status, List<String> issues, int usageCount) {
    public ComponentHealthReport {
        if (componentId == null || componentId.isBlank() || status == null || usageCount < 0) throw new IllegalArgumentException("Component Health 값이 올바르지 않습니다.");
        issues = List.copyOf(issues == null ? List.of() : issues);
    }
    public enum Status { HEALTHY, WARNING, BLOCKED }
}
