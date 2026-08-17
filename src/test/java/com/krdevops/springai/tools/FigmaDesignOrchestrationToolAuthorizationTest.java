package com.krdevops.springai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.service.designsystem.ComponentSwapPolicyResolver;
import com.krdevops.springai.service.figma.FigmaDesignOrchestrationService;
import com.krdevops.springai.service.figma.FigmaPlatformConversionService;
import com.krdevops.springai.service.figma.FigmaToolAuthorizationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FigmaDesignOrchestrationToolAuthorizationTest {

    @Test
    void authenticationRunsBeforeOrchestration() {
        FigmaDesignOrchestrationService orchestration = mock(FigmaDesignOrchestrationService.class);
        FigmaDesignOrchestrationTool tool = new FigmaDesignOrchestrationTool(
                orchestration, new FigmaToolAuthorizationService("expected"), new ObjectMapper(),
                new FigmaPlatformConversionService(new ComponentSwapPolicyResolver()));

        assertThatThrownBy(() -> tool.createDesignFromText("wrong", "화면", "file"))
                .isInstanceOf(SecurityException.class);
        verify(orchestration, never()).processTextRequest("화면", "file");
    }
}
