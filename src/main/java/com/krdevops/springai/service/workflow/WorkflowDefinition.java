package com.krdevops.springai.service.workflow;

import java.util.List;

public record WorkflowDefinition(String type, String title, List<WorkflowStep> steps) {}
