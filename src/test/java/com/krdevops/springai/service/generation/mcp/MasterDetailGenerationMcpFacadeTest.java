package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.generation.api.DispatchMasterDetailGenerationUseCase;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MasterDetailGenerationMcpFacadeTest {

    @Test
    void facade_delegatesOnlyToDispatchUseCase() {
        DispatchMasterDetailGenerationUseCase dispatch = mock(DispatchMasterDetailGenerationUseCase.class);
        MasterDetailGenerationResultFormatter formatter = mock(MasterDetailGenerationResultFormatter.class);
        MasterDetailToolResult result = new MasterDetailToolResult.Orchestrated(
                new MasterDetailOrchestrationResult(false, "com", "MASTER", "DETAIL", "Order", "/tmp/out",
                        java.util.List.of("EgovOrderList.jsp"), java.util.List.of(), "OK", "history"));
        when(dispatch.execute(any())).thenReturn(result);
        when(formatter.format(result)).thenReturn("formatted");

        MasterDetailGenerationMcpFacade facade = new MasterDetailGenerationMcpFacade(dispatch, formatter);
        String response = facade.buildMasterDetailPrompt(
                "com", "MASTER", "DETAIL", "Order", "egovframework.let.order", "/tmp/out",
                "jsp", "5.0", "auto", null, null, null, null, null);

        assertThat(response).isEqualTo("formatted");
        verify(dispatch).execute(any());
        verify(formatter).format(result);
    }
}
