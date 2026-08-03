package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.FigmaImportArtifact;
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
