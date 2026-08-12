package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaContractApprovalServiceTest {
    private final ScreenPatternRepository patterns = mock(ScreenPatternRepository.class);
    private final VariantRuleSetRepository rules = mock(VariantRuleSetRepository.class);
    private final FigmaReviewHistoryRepository reviews = mock(FigmaReviewHistoryRepository.class);
    private final FigmaContractApprovalService service = new FigmaContractApprovalService(patterns, rules, reviews);

    @Test
    void pattern은Draft승인후Approved를Publish하고사람증적을남긴다() {
        var approved = new ScreenPatternDefinition(ScreenPattern.CRUD_LIST, "2.0.0",
                ScreenPatternDefinition.Status.APPROVED, List.of());
        var published = new ScreenPatternDefinition(ScreenPattern.CRUD_LIST, "2.0.0",
                ScreenPatternDefinition.Status.PUBLISHED, List.of());
        when(patterns.transition(ScreenPattern.CRUD_LIST, "2.0.0",
                ScreenPatternDefinition.Status.DRAFT, ScreenPatternDefinition.Status.APPROVED)).thenReturn(approved);
        when(patterns.transition(ScreenPattern.CRUD_LIST, "2.0.0",
                ScreenPatternDefinition.Status.APPROVED, ScreenPatternDefinition.Status.PUBLISHED)).thenReturn(published);

        assertThat(service.approvePattern(ScreenPattern.CRUD_LIST, "2.0.0", "ds-owner", "검토 완료").status())
                .isEqualTo(ScreenPatternDefinition.Status.APPROVED);
        assertThat(service.publishPattern(ScreenPattern.CRUD_LIST, "2.0.0", "ds-owner", "배포 승인").status())
                .isEqualTo(ScreenPatternDefinition.Status.PUBLISHED);
        verify(reviews).save(argThat(event -> event.targetType() == FigmaReviewEvent.TargetType.SCREEN_PATTERN
                && event.eventType() == FigmaReviewEvent.EventType.APPROVAL && "ds-owner".equals(event.actor())));
        verify(reviews).save(argThat(event -> event.targetType() == FigmaReviewEvent.TargetType.SCREEN_PATTERN
                && event.eventType() == FigmaReviewEvent.EventType.PUBLISH));
    }

    @Test
    void ruleSet승인에는승인자가필수다() {
        assertThatThrownBy(() -> service.approveRuleSet("rules", "2.0.0", " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor");
    }

    @Test
    void ruleSet은Approved에서만Publish된다() {
        var published = new VariantRuleSet("rules", "2.0.0", "krds", "registry-2",
                VariantRuleSet.Status.PUBLISHED, List.of());
        when(rules.transition("rules", "2.0.0", VariantRuleSet.Status.APPROVED,
                VariantRuleSet.Status.PUBLISHED)).thenReturn(published);

        assertThat(service.publishRuleSet("rules", "2.0.0", "ds-owner", "승인").status())
                .isEqualTo(VariantRuleSet.Status.PUBLISHED);
        verify(reviews).save(argThat(event -> event.targetType() == FigmaReviewEvent.TargetType.VARIANT_RULE_SET
                && event.eventType() == FigmaReviewEvent.EventType.PUBLISH));
    }
}
