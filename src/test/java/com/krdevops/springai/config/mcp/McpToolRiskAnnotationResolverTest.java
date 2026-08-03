package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolRiskAnnotationResolverTest {

    private final McpToolRiskAnnotationResolver resolver = new McpToolRiskAnnotationResolver();

    @McpToolRisk(McpToolRiskLevel.FILE_WRITE)
    void annotatedMethod() {
    }

    void unannotatedMethod() {
    }

    @Test
    void resolvesAnnotatedMethod() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("annotatedMethod");
        assertThat(resolver.resolve(method)).isEqualTo(McpToolRiskLevel.FILE_WRITE);
    }

    @Test
    void throwsWithMethodNameForUnannotatedMethod() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("unannotatedMethod");
        assertThatThrownBy(() -> resolver.resolve(method))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unannotatedMethod")
                .hasMessageContaining(getClass().getName());
    }
}
