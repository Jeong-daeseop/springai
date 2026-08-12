package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.designsystem.VariantRuleSet;

import java.time.LocalDateTime;

/** Bundle 생성 시점에 고정한 Published Variant Rule Set 계약. */
public record VariantRuleSetSnapshot(VariantRuleSet ruleSet, LocalDateTime snapshotAt) {
    public VariantRuleSetSnapshot {
        if (ruleSet == null) throw new IllegalArgumentException("ruleSet Snapshot은 필수입니다.");
        snapshotAt = snapshotAt == null ? LocalDateTime.now() : snapshotAt;
    }
}
