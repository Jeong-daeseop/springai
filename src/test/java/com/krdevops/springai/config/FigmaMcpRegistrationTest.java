package com.krdevops.springai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FigmaMcpRegistrationTest {

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Test
    void registersFigmaExportAndDesignSystemCallbacks() {
        assertThat(Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .contains(
                        "generateFigmaScreenSpec",
                        "validateFigmaScreenSpec",
                        "validateDesignSystemSpec",
                        "auditComponentRegistry",
                        "preflightComponentRegistry");
    }
}
