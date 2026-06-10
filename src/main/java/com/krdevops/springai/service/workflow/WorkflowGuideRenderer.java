package com.krdevops.springai.service.workflow;

import java.util.List;

public class WorkflowGuideRenderer {

    public String render(WorkflowDefinition definition, int completedStep) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(definition.title()).append(" ===\n\n");

        List<WorkflowStep> steps = definition.steps();

        for (WorkflowStep step : steps) {
            if (step.no() <= completedStep) {
                sb.append("✅ ").append(step.no()).append(". ").append(step.name())
                        .append(" [").append(step.tool()).append("]\n");
            } else if (step.no() == completedStep + 1) {
                sb.append("▶ ").append(step.no()).append(". ").append(step.name())
                        .append(" [").append(step.tool()).append("] ← 다음 단계\n")
                        .append("   └ ").append(step.desc()).append("\n");
            } else {
                sb.append("☐ ").append(step.no()).append(". ").append(step.name())
                        .append(" [").append(step.tool()).append("]\n");
            }
        }

        return sb.toString();
    }
}
