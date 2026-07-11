package com.krdevops.springai.service;

import com.krdevops.springai.service.workflow.WorkflowDefinition;
import com.krdevops.springai.service.workflow.WorkflowDefinitionRegistry;
import com.krdevops.springai.service.workflow.WorkflowGuideRenderer;
import com.krdevops.springai.service.workflow.WorkflowProgressDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowGuideService {

    private final WorkflowDefinitionRegistry registry;
    private final WorkflowProgressDetector progressDetector = new WorkflowProgressDetector();
    private final WorkflowGuideRenderer guideRenderer = new WorkflowGuideRenderer();

    public String suggestNextStep(String currentContext) {
        String type = isThymeleafContext(currentContext) ? "crud-thymeleaf" : "crud";
        WorkflowDefinition definition = registry.getOrDefault(type);
        int completed = progressDetector.detectCompletedStep(definition, currentContext);
        return guideRenderer.render(definition, completed);
    }

    /** 문맥에 Thymeleaf/layout 생성 관련 언급이 있으면 layout 단계가 포함된 workflow를 사용한다. */
    private boolean isThymeleafContext(String currentContext) {
        if (currentContext == null) {
            return false;
        }
        String ctx = currentContext.toLowerCase();
        return ctx.contains("thymeleaf") || ctx.contains("templates/layout");
    }

    public String suggestProjectSetupCrudWorkflow(String currentContext) {
        WorkflowDefinition definition = registry.find("project-setup-crud")
                .orElseThrow(() -> new IllegalStateException("project-setup-crud workflow not found"));
        int completedStep = progressDetector.detectCompletedStep(definition, currentContext);
        return guideRenderer.render(definition, completedStep);
    }

    public String suggestSecurityMenuAuthWorkflow(String currentContext) {
        WorkflowDefinition definition = registry.find("security-menu-auth")
                .orElseThrow(() -> new IllegalStateException("security-menu-auth workflow not found"));
        int completed = progressDetector.detectCompletedStep(definition, currentContext);
        return guideRenderer.render(definition, completed);
    }
}
