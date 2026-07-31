package com.krdevops.springai.tools;

import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.service.figma.FigmaMcpFacadeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaExportToolTest {

    @Test
    void delegatesToAuthenticatedFacadeWithoutBusinessLogic() {
        FigmaMcpFacadeService facade = mock(FigmaMcpFacadeService.class);
        FigmaExportTool tool = new FigmaExportTool(facade);
        FigmaScreenExportRequest request = new FigmaScreenExportRequest(
                "users", 3, "user-list", "ftc-krds", "DESKTOP", null, null);
        when(facade.generateScreen("secret", request)).thenReturn("{\"status\":\"SUCCESS\"}");

        String result = tool.generateFigmaScreenSpec("secret", request);

        assertThat(result).isEqualTo("{\"status\":\"SUCCESS\"}");
        verify(facade).generateScreen("secret", request);
    }
}
