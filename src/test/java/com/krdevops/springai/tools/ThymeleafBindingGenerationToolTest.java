package com.krdevops.springai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
import com.krdevops.springai.service.thymeleaf.ThymeleafBindingGenerationService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ThymeleafBindingGenerationToolTest {

    @Test
    void authorizationRunsBeforeSourceGeneration() {
        ThymeleafToolAuthorizationService authorization = mock(ThymeleafToolAuthorizationService.class);
        ThymeleafBindingGenerationService service = mock(ThymeleafBindingGenerationService.class);
        ThymeleafBindingPreviewRequest request = mock(ThymeleafBindingPreviewRequest.class);
        org.mockito.Mockito.doThrow(new SecurityException("denied"))
                .when(authorization).authorize("wrong");
        ThymeleafBindingGenerationTool tool = new ThymeleafBindingGenerationTool(
                authorization, service, new ObjectMapper());

        assertThatThrownBy(() -> tool.previewThymeleafBindingGeneration("wrong", request))
                .isInstanceOf(SecurityException.class);
        verify(authorization).authorize("wrong");
        verify(service, never()).preview(request);
    }
}
