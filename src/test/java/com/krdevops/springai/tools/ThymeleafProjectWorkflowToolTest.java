package com.krdevops.springai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.service.thymeleaf.ThymeleafProjectWorkflowService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThymeleafProjectWorkflowToolTest {

    @Test
    void authorizationRunsBeforeWorkflowAccess() {
        ThymeleafToolAuthorizationService authorization = mock(ThymeleafToolAuthorizationService.class);
        ThymeleafProjectWorkflowService workflow = mock(ThymeleafProjectWorkflowService.class);
        when(workflow.find("op-1")).thenThrow(new AssertionError("workflow must not be called"));
        org.mockito.Mockito.doThrow(new SecurityException("denied"))
                .when(authorization).authorize("wrong");
        ThymeleafProjectWorkflowTool tool = new ThymeleafProjectWorkflowTool(
                authorization, workflow, new ObjectMapper());

        assertThatThrownBy(() -> tool.getThymeleafProjectReport("wrong", "op-1"))
                .isInstanceOf(SecurityException.class);
        verify(authorization).authorize("wrong");
        verify(workflow, never()).find("op-1");
    }
}
