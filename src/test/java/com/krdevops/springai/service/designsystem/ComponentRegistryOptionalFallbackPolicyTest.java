package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRegistryOptionalFallbackPolicyTest {
    private final ComponentRegistryOptionalFallbackPolicy policy = new ComponentRegistryOptionalFallbackPolicy();

    @Test
    void optionalMissingBindingFallsBackOnlyInPreview() {
        var optional = new ComponentCatalog.Entry(ComponentCatalog.Kind.COMPONENT,
                ComponentCatalog.Requirement.OPTIONAL, java.util.List.of(), null, java.util.Map.of(),
                java.util.List.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), null, null);
        assertThat(policy.decide(optional, null, true).fallback()).isTrue();
        assertThat(policy.decide(optional, null, false).blocked()).isTrue();
    }
}
