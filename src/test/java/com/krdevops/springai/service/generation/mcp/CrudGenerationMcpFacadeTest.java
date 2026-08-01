package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.generation.api.DispatchCrudGenerationUseCase;
import com.krdevops.springai.service.generation.crud.CrudToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
                "5.0", "jsp", null, null, null, null, null, null, null, null, null);

        assertThat(response).isEqualTo("formatted");
        verify(dispatch).execute(any());
        verify(formatter).format(result);
    }
}
