package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/** axe-core가 발견한 접근성 규칙 위반 증적. */
public record AxeViolation(
        String id,
        String impact,
        String description,
        String helpUrl,
        List<String> tags,
        List<String> targets
) {
    public AxeViolation {
        tags = tags == null ? List.of() : List.copyOf(tags);
        targets = targets == null ? List.of() : List.copyOf(targets);
    }
}
