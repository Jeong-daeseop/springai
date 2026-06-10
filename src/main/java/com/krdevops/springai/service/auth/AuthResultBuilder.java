package com.krdevops.springai.service.auth;

import com.krdevops.springai.model.SqlPlan;
import org.springframework.stereotype.Component;

@Component
public class AuthResultBuilder {

    public String render(SqlPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(plan.title()).append(" ===\n\n");

        for (String stmt : plan.statements()) {
            sb.append(stmt).append("\n\n");
        }

        if (!plan.warnings().isEmpty()) {
            sb.append("【주의사항】\n");
            for (String w : plan.warnings()) {
                sb.append(w).append("\n");
            }
            sb.append("\n");
        }

        if (!plan.nextSteps().isEmpty()) {
            sb.append("【다음 단계】\n");
            for (String step : plan.nextSteps()) {
                sb.append(step).append("\n");
            }
        }

        return sb.toString();
    }
}
