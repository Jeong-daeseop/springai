package com.krdevops.springai.model.contract;

import com.krdevops.springai.model.figma.FigmaScreenType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultLayoutPolicyTest {

    @Test
    void layoutForReturnsSpecByScreenType() {
        DefaultLayoutPolicy policy = validPolicy();

        assertThat(policy.layoutFor(FigmaScreenType.LIST).gridColumns()).isEqualTo(12);
        assertThat(policy.layoutFor(FigmaScreenType.DETAIL).gridColumns()).isEqualTo(8);
    }

    @Test
    void rejectsMissingScreenType() {
        assertThatThrownBy(() -> new DefaultLayoutPolicy("v1", Map.of(
                FigmaScreenType.LIST, spec(12)
        ), "hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORM");
    }

    @Test
    void rejectsBlankPolicyVersion() {
        assertThatThrownBy(() -> new DefaultLayoutPolicy("", fullMap(), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveGridColumns() {
        assertThatThrownBy(() -> spec(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DefaultLayoutPolicy validPolicy() {
        return new DefaultLayoutPolicy("v1", Map.of(
                FigmaScreenType.LIST, spec(12),
                FigmaScreenType.FORM, spec(12),
                FigmaScreenType.DETAIL, spec(8),
                FigmaScreenType.DASHBOARD, spec(12)
        ), "hash");
    }

    private Map<FigmaScreenType, DefaultLayoutPolicy.ScreenLayoutSpec> fullMap() {
        return Map.of(
                FigmaScreenType.LIST, spec(12),
                FigmaScreenType.FORM, spec(12),
                FigmaScreenType.DETAIL, spec(8),
                FigmaScreenType.DASHBOARD, spec(12)
        );
    }

    private DefaultLayoutPolicy.ScreenLayoutSpec spec(int gridColumns) {
        return new DefaultLayoutPolicy.ScreenLayoutSpec(gridColumns, 16, 24, "STANDARD");
    }
}
