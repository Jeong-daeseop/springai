package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.contract.PlatformLayoutPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentSwapPolicyResolverTest {

    private final ComponentSwapPolicyResolver resolver = new ComponentSwapPolicyResolver();

    @Test
    void resolvesConfiguredSwapForPlatform() {
        PlatformLayoutPolicy policy = policyWith(
                new PlatformLayoutPolicy.ComponentSwapRule(
                        "krds.button.large", "krds.button.small", "MOBILE", "터치 영역 축소"));

        ComponentSwapPolicyResolver.Resolution result =
                resolver.resolve(policy, "MOBILE", "krds.button.large");

        assertThat(result.swapped()).isTrue();
        assertThat(result.resolvedLogicalType()).isEqualTo("krds.button.small");
        assertThat(result.reason()).isEqualTo("터치 영역 축소");
    }

    @Test
    void returnsOriginalTypeWhenNoRuleMatches() {
        PlatformLayoutPolicy policy = policyWith(
                new PlatformLayoutPolicy.ComponentSwapRule(
                        "krds.button.large", "krds.button.small", "MOBILE", "터치 영역 축소"));

        ComponentSwapPolicyResolver.Resolution result =
                resolver.resolve(policy, "DESKTOP", "krds.button.large");

        assertThat(result.swapped()).isFalse();
        assertThat(result.resolvedLogicalType()).isEqualTo("krds.button.large");
    }

    @Test
    void returnsOriginalTypeWhenPolicyIsNull() {
        ComponentSwapPolicyResolver.Resolution result =
                resolver.resolve(null, "MOBILE", "krds.button.large");

        assertThat(result.swapped()).isFalse();
        assertThat(result.resolvedLogicalType()).isEqualTo("krds.button.large");
    }

    @Test
    void rejectsAmbiguousSwapRules() {
        PlatformLayoutPolicy policy = policyWith(
                new PlatformLayoutPolicy.ComponentSwapRule(
                        "krds.button.large", "krds.button.small", "MOBILE", "사유1"),
                new PlatformLayoutPolicy.ComponentSwapRule(
                        "krds.button.large", "krds.button.medium", "MOBILE", "사유2"));

        assertThatThrownBy(() -> resolver.resolve(policy, "MOBILE", "krds.button.large"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPONENT_SWAP_AMBIGUOUS");
    }

    private PlatformLayoutPolicy policyWith(PlatformLayoutPolicy.ComponentSwapRule... rules) {
        return new PlatformLayoutPolicy("v1", List.of(), List.of(rules), "hash");
    }
}
