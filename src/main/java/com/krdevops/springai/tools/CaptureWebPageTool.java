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
            운영자가 POST /api/web-capture/sessions(X-API-Key 인증, 원문 username/password
            필요 — 이 REST 엔드포인트만 credential을 받습니다)를 먼저 호출해 발급받은 opaque
            sessionId를 storageStateRef로 전달할 수 있습니다. 비밀번호·쿠키·토큰 원문은 이 Tool에
            전달하지 마세요.

            SPA/동적 화면은 interactions(최대 20개, 순서대로 실행)로 상태를 재현할 수 있습니다.
            허용 type은 click/fill/select/hover(selector 필수)·keydown(value=키 이름, 예: "Enter",
            selector는 선택)·scroll(selector 선택, 없으면 한 화면 높이만큼 스크롤)뿐이며 임의
            selector/action을 그 외 방식으로 실행할 수 없습니다. 같은 URL이라도 interactions
            조합이 다르면 서로 다른 상태로 간주되어 별도 Artifact가 생성됩니다.
            """)
    public CaptureArtifactSummary captureWebPage(CaptureWebPageRequest request) {
        return service.capture(request);
    }
}
