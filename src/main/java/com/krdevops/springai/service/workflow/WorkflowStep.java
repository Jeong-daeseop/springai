package com.krdevops.springai.service.workflow;

public record WorkflowStep(int no, String name, String tool, String desc, String[] keywords) {}
