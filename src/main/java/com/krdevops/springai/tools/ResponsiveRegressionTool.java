package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
import com.krdevops.springai.model.capture.ResponsiveRegressionReport;
import com.krdevops.springai.service.ResponsiveRegressionAnalyzer;
import com.krdevops.springai.service.WebCaptureOrchestrationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ResponsiveRegressionTool {

    private final WebCaptureOrchestrationService service;

    public ResponsiveRegressionTool(WebCaptureOrchestrationService service) {
        this.service = service;
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            claude 경로로 생성된 화면이 componentGeometry 반응형 가드레일 지시를 실제로
            지켰는지 확인합니다. captureWebPageMultiViewport와 동일하게 Desktop/Tablet/Mobile
            3개 viewport를 캡처하고, 컴포넌트가 브레이크포인트 사이에서 재배치(MOVED)됐는지
            집계해 반환합니다. MOVED는 가드레일 위반(좌표를 인라인/고정폭으로 옮김) 의심
            신호일 뿐이며, 의도된 반응형 재배치일 수도 있으므로 최종 판단은 사람이 해야
            합니다. 서버는 미리 실행되어 있어야 합니다.
            """)
    public ResponsiveRegressionReport checkResponsiveRegression(CaptureWebPageRequest request) {
        RenderedDesignBundle bundle = service.captureMultiViewport(request);
        return ResponsiveRegressionAnalyzer.analyze(bundle);
    }
}
