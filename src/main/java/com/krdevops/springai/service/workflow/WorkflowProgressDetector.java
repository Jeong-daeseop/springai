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

        // 1단계부터 연속 완료 단계 계산
        int maxContinuous = 0;
        for (WorkflowStep step : definition.steps()) {
            if (completed.contains(step.no())) {
                maxContinuous = step.no();
            } else {
                break;
            }
        }

        if (maxContinuous > 0) {
            return maxContinuous;
        }

        // 연속 감지 없으면 감지된 최대 단계를 완료로 추정
        // 예: "메뉴 SQL 생성 완료" → 5단계 감지 → completedStep = 5
        return completed.stream().max(Integer::compareTo).orElse(0);
    }
}
