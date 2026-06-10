package com.krdevops.springai.model;

import java.util.List;

public record SqlPlan(
        String title,
        List<String> statements,
        List<String> warnings,
        List<String> nextSteps
) {}
