package com.krdevops.springai.tools;

import com.krdevops.springai.model.capture.CaptureProfile;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.model.capture.ComponentMatch;
import com.krdevops.springai.model.capture.ReadinessSpec;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
import com.krdevops.springai.model.capture.ResponsiveRegressionReport;
import com.krdevops.springai.model.capture.ViewportSpec;
import com.krdevops.springai.service.WebCaptureOrchestrationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResponsiveRegressionToolTest {

    @Test
    void delegatesToMultiViewportCaptureAndAnalyzesResult() {
        WebCaptureOrchestrationService service = mock(WebCaptureOrchestrationService.class);
        ResponsiveRegressionTool tool = new ResponsiveRegressionTool(service);

        CaptureWebPageRequest request = new CaptureWebPageRequest(
                "http://localhost:8080/emp/list", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(),
                new ReadinessSpec(null, null, 30000), "crud");
        RenderedDesignBundle bundle = new RenderedDesignBundle(
                RenderedDesignBundle.SCHEMA_VERSION, "bundle-1",
                Map.of("desktop", "artifact-1"),
                List.of(new ComponentMatch("table.list", Map.of("desktop", "n1"), ComponentMatch.Status.MATCHED_ALL)),
                List.of(), List.of());
        when(service.captureMultiViewport(request)).thenReturn(bundle);

        ResponsiveRegressionReport report = tool.checkResponsiveRegression(request);

        assertThat(report.bundleId()).isEqualTo("bundle-1");
        assertThat(report.matchedAllCount()).isEqualTo(1);
        assertThat(report.movedCount()).isZero();
        verify(service).captureMultiViewport(request);
    }
}
