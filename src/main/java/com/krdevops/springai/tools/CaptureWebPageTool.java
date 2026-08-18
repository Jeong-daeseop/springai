package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.service.WebCaptureOrchestrationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CaptureWebPageTool {
    private final WebCaptureOrchestrationService service;

    public CaptureWebPageTool(WebCaptureOrchestrationService service) {
        this.service = service;
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            허용된 로컬 또는 개발 화면 URL을 Chromium으로 분석하여 Figma import와
            화면명세 생성에 사용할 Design Artifact를 만듭니다. JSP/Thymeleaf 등 서버 템플릿
            엔진을 구분하지 않고 렌더링된 최종 HTML을 캡처합니다. LOCAL_WEB 화면과 단일
            desktop viewport를 지원하며 서버는 미리 실행되어 있어야 합니다. 인증이 필요하면
            Extractor의 POST /v1/sessions가 발급한 불투명 sessionId를 storageStateRef로 전달할
            수 있습니다. 비밀번호·쿠키·토큰 원문은 이 Tool에 전달하지 마세요.
            """)
    public CaptureArtifactSummary captureWebPage(CaptureWebPageRequest request) {
        return service.capture(request);
    }
}
