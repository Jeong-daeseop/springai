package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** KRV-071: Pattern·Rule Set의 내용은 고정하고 승인 상태와 사람의 승인 증적만 전환한다. */
@Service
public class FigmaContractApprovalService {
    private final ScreenPatternRepository patternRepository;
    private final VariantRuleSetRepository ruleSetRepository;
    private final FigmaReviewHistoryRepository reviewRepository;

    public FigmaContractApprovalService(ScreenPatternRepository patternRepository,
            VariantRuleSetRepository ruleSetRepository, FigmaReviewHistoryRepository reviewRepository) {
        this.patternRepository = patternRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ScreenPatternDefinition approvePattern(
            ScreenPattern pattern, String version, String actor, String comment) {
        requireActor(actor);
        ScreenPatternDefinition changed = patternRepository.transition(pattern, version,
                ScreenPatternDefinition.Status.DRAFT, ScreenPatternDefinition.Status.APPROVED);
        record(FigmaReviewEvent.TargetType.SCREEN_PATTERN, pattern.code(), version,
                FigmaReviewEvent.EventType.APPROVAL, changed.status().name(), actor, comment);
        return changed;
    }

    @Transactional
    public ScreenPatternDefinition publishPattern(
            ScreenPattern pattern, String version, String actor, String comment) {
        requireActor(actor);
        ScreenPatternDefinition changed = patternRepository.transition(pattern, version,
                ScreenPatternDefinition.Status.APPROVED, ScreenPatternDefinition.Status.PUBLISHED);
        record(FigmaReviewEvent.TargetType.SCREEN_PATTERN, pattern.code(), version,
                FigmaReviewEvent.EventType.PUBLISH, changed.status().name(), actor, comment);
        return changed;
    }

    @Transactional
    public VariantRuleSet approveRuleSet(String id, String version, String actor, String comment) {
        requireActor(actor);
        VariantRuleSet changed = ruleSetRepository.transition(id, version,
                VariantRuleSet.Status.DRAFT, VariantRuleSet.Status.APPROVED);
        record(FigmaReviewEvent.TargetType.VARIANT_RULE_SET, id, version,
                FigmaReviewEvent.EventType.APPROVAL, changed.status().name(), actor, comment);
        return changed;
    }

    @Transactional
    public VariantRuleSet publishRuleSet(String id, String version, String actor, String comment) {
        requireActor(actor);
        VariantRuleSet changed = ruleSetRepository.transition(id, version,
                VariantRuleSet.Status.APPROVED, VariantRuleSet.Status.PUBLISHED);
        record(FigmaReviewEvent.TargetType.VARIANT_RULE_SET, id, version,
                FigmaReviewEvent.EventType.PUBLISH, changed.status().name(), actor, comment);
        return changed;
    }

    private void record(FigmaReviewEvent.TargetType type, String id, String version,
            FigmaReviewEvent.EventType eventType, String status, String actor, String comment) {
        reviewRepository.save(new FigmaReviewEvent(UUID.randomUUID().toString(), type, id, version,
                eventType, status, actor, comment, LocalDateTime.now()));
    }

    private void requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("승인자 actor는 필수입니다.");
        }
    }
}
