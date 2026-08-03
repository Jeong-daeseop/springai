package com.krdevops.springai.tools;

import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.service.figma.FigmaMcpFacadeService;
import com.krdevops.springai.service.figma.FigmaToolAuthorizationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaExportToolTest {

    @Test
    void delegatesToAuthenticatedFacadeWithoutBusinessLogic() {
        FigmaMcpFacadeService facade = mock(FigmaMcpFacadeService.class);
        FigmaToolAuthorizationService authorization = mock(FigmaToolAuthorizationService.class);
        FigmaExportTool tool = new FigmaExportTool(facade, authorization);
        FigmaScreenExportRequest request = new FigmaScreenExportRequest(
                "users", 3, "user-list", "ftc-krds", "DESKTOP", null, null);
        when(facade.generateScreen(request)).thenReturn("{\"status\":\"SUCCESS\"}");

        String result = tool.generateFigmaScreenSpec("secret", request);

        assertThat(result).isEqualTo("{\"status\":\"SUCCESS\"}");
        verify(authorization).authorize("secret");
        verify(facade).generateScreen(request);
    }
}
