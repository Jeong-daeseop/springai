package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentHealthReport;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ComponentHealthAggregator {
    public ComponentHealthReport aggregate(String componentId, boolean blocked, List<String> issues, int usageCount) {
        List<String> normalized = issues == null ? List.of() : issues.stream().filter(v -> v != null && !v.isBlank()).distinct().sorted().toList();
        ComponentHealthReport.Status status = blocked ? ComponentHealthReport.Status.BLOCKED : normalized.isEmpty() ? ComponentHealthReport.Status.HEALTHY : ComponentHealthReport.Status.WARNING;
        return new ComponentHealthReport(componentId, status, normalized, usageCount);
    }
}
