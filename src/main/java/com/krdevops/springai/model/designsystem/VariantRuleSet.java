package com.krdevops.springai.model.designsystem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record VariantRuleSet(
        String id,
        String version,
        String profileId,
        String registryVersion,
        Status status,
        List<VariantRule> rules
) {
    public VariantRuleSet {
        if (id == null || id.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Rule Set id와 version은 필수입니다.");
        }
        if (profileId == null || profileId.isBlank() || registryVersion == null || registryVersion.isBlank()) {
            throw new IllegalArgumentException("Rule Set profileId와 registryVersion은 필수입니다.");
        }
        status = status == null ? Status.DRAFT : status;
        rules = rules == null ? List.of() : List.copyOf(rules);
        Set<String> ids = new HashSet<>();
        for (VariantRule rule : rules) {
            if (!ids.add(rule.ruleId())) {
                throw new IllegalArgumentException("Variant Rule ID가 중복되었습니다: " + rule.ruleId());
            }
        }
    }

    public enum Status { DRAFT, APPROVED, PUBLISHED, SUPERSEDED }
}
