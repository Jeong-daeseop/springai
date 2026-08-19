package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.FigmaBundleImportArtifact;
import com.krdevops.springai.model.capture.FigmaImportArtifact;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.service.WebCaptureAnalysisService;
import com.krdevops.springai.service.WebCaptureHealthService;
import com.krdevops.springai.model.capture.WebCaptureHealth;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class DesignArtifactTool {
    private final DesignArtifactService artifactService;
    private final WebCaptureAnalysisService analysisService;
    private final WebCaptureHealthService healthService;

    public DesignArtifactTool(DesignArtifactService artifactService, WebCaptureAnalysisService analysisService,
                              WebCaptureHealthService healthService) {
        this.artifactService = artifactService;
        this.analysisService = analysisService;
        this.healthService = healthService;
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = "저장된 Design Artifact의 메타데이터, 요약과 경고를 조회합니다.")
    public CaptureArtifactSummary getDesignArtifact(String artifactId) {
        return artifactService.get(artifactId);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = "검증된 Design Artifact를 Figma Plugin에서 가져올 수 있는 .figpack 파일로 내보냅니다.")
    public FigmaImportArtifact prepareFigmaImport(String artifactId) {
        return artifactService.prepareFigmaImport(artifactId);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            captureWebPageMultiViewport가 반환한 RenderedDesignBundle을 Figma Plugin에서
            가져올 수 있는 zip 파일 하나(bundle.json + viewport별 .figpack)로 내보냅니다.
            Plugin은 네트워크 접근이 없어(networkAccess:none) 이 zip 파일을 로컬 파일 선택
            대화상자로 직접 골라야 합니다. captureWebPageMultiViewport의 반환값을 그대로
            bundle 인자에 전달하세요.
            """)
    public FigmaBundleImportArtifact prepareFigmaBundleImport(RenderedDesignBundle bundle) {
        return artifactService.prepareFigmaBundleImport(bundle);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            Design Artifact를 기존 UiDesignSpec 분석 결과로 결정론적으로 변환합니다.
            반환된 analysisId는 createScreenSpecification에 전달할 수 있습니다.
            """)
    public DesignAnalysisResult analyzeCapturedDesign(String artifactId, @Nullable String featureType) {
        return analysisService.analyze(artifactId, featureType);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = "WEB_CAPTURE 기능, extractor와 artifact 저장소 준비 상태를 확인합니다.")
    public WebCaptureHealth getWebCaptureStatus() {
        return healthService.check();
    }
}
