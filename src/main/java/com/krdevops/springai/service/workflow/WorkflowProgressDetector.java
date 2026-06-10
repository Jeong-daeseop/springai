package com.krdevops.springai.service.workflow;

import java.util.HashSet;
import java.util.Set;

public class WorkflowProgressDetector {

    public int detectCompletedStep(WorkflowDefinition definition, String context) {
        if (context == null || context.isBlank()) {
            return 0;
        }
        String ctx = context.toLowerCase();
        Set<Integer> completed = new HashSet<>();

        for (WorkflowStep step : definition.steps()) {
            for (String keyword : step.keywords()) {
                if (ctx.contains(keyword.toLowerCase())) {
                    completed.add(step.no());
                    break;
                }
            }
        }

        if (completed.isEmpty()) {
            return 0;
        }

        int maxContinuous = 0;
        for (WorkflowStep step : definition.steps()) {
            if (completed.contains(step.no())) {
                maxContinuous = step.no();
            } else {
                break;
            }
        }
        return maxContinuous;
    }
}
