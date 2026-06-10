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
        WorkflowDefinition definition = registry.getOrDefault("crud");
        int completed = progressDetector.detectCompletedStep(definition, currentContext);
        return guideRenderer.render(definition, completed);
    }

    public String suggestSecurityMenuAuthWorkflow(String currentContext) {
        WorkflowDefinition definition = registry.find("security-menu-auth")
                .orElseThrow(() -> new IllegalStateException("security-menu-auth workflow not found"));
        int completed = progressDetector.detectCompletedStep(definition, currentContext);
        return guideRenderer.render(definition, completed);
    }
}
