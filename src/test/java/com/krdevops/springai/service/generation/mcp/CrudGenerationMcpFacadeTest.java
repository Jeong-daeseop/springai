package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.generation.api.DispatchCrudGenerationUseCase;
import com.krdevops.springai.service.generation.crud.CrudToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;

class CrudGenerationMcpFacadeTest {

    @Test
    void facade_delegatesOnlyToDispatchUseCase() {
        DispatchCrudGenerationUseCase dispatch = mock(DispatchCrudGenerationUseCase.class);
        CrudGenerationResultFormatter formatter = mock(CrudGenerationResultFormatter.class);
        CrudToolResult result = new CrudToolResult.Orchestrated(
                new CrudOrchestrationResult(false, "com", "EMP", "Employer", "/tmp/out",
                        java.util.List.of("EgovEmployerList.jsp"), java.util.List.of(), "OK", "history"));
        when(dispatch.execute(any())).thenReturn(result);
        when(formatter.format(result)).thenReturn("formatted");

        CrudGenerationMcpFacade facade = new CrudGenerationMcpFacade(dispatch, formatter);
        String response = facade.buildFullCrudPrompt(
                "com", "EMP", "Employer", "egovframework.let.emp", "/tmp/out", "auto",
                "5.0", "jsp", null, null, null, null, null, null, null, null, null, null);

        assertThat(response).isEqualTo("formatted");
        ArgumentCaptor<CrudGenerationCommand> command =
                ArgumentCaptor.forClass(CrudGenerationCommand.class);
        verify(dispatch).execute(command.capture());
        assertThat(command.getValue().rendererProfileReference().profileId())
                .isEqualTo("thymeleaf-krds");
        verify(formatter).format(result);
    }

    @Test
    void facade확장진입점이명시적RendererProfile참조를Command에전달한다() {
        DispatchCrudGenerationUseCase dispatch = mock(DispatchCrudGenerationUseCase.class);
        CrudGenerationResultFormatter formatter = mock(CrudGenerationResultFormatter.class);
        CrudToolResult result = new CrudToolResult.Prompted(
                new com.krdevops.springai.service.generation.model.PromptGenerationResult("prompt"));
        when(dispatch.execute(any())).thenReturn(result);
        when(formatter.format(result)).thenReturn("formatted");
        CrudGenerationMcpFacade facade = new CrudGenerationMcpFacade(dispatch, formatter);

        facade.buildFullCrudPrompt(
                "com", "EMP", "Employer", "egovframework.let.emp", "/tmp/out", "auto",
                "5.0", "thymeleaf", null, null, null, null, null, null, null, null,
                null, "thymeleaf-custom", "2.0", "d".repeat(64));

        ArgumentCaptor<CrudGenerationCommand> command =
                ArgumentCaptor.forClass(CrudGenerationCommand.class);
        verify(dispatch).execute(command.capture());
        assertThat(command.getValue().rendererProfileReference().profileId())
                .isEqualTo("thymeleaf-custom");
        assertThat(command.getValue().rendererProfileReference().version()).isEqualTo("2.0");
        assertThat(command.getValue().rendererProfileReference().contentHash())
                .isEqualTo("d".repeat(64));
    }
}
