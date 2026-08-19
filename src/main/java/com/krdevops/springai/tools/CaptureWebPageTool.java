package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
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

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            captureWebPage와 동일한 화면을 Desktop(1440)/Tablet(768)/Mobile(390) 3개
            viewport(모두 height=1200)로 각각 캡처하고, 같은 selectorHint(태그+id+첫 class)를
            가진 컴포넌트를 viewport 사이에서 대응시킨 결과(RenderedDesignBundle)를 반환합니다.
            요청 파라미터는 captureWebPage와 동일하되 viewport 필드는 무시되고 3종 모두
            자동으로 캡처됩니다. 일부 viewport 캡처가 실패해도 나머지가 성공하면 경고와 함께
            부분 결과를 반환하며, 3개 모두 실패한 경우에만 오류를 던집니다. 컴포넌트 대응은
            부모 selectorHint 비교만 근거로 사용하며(임의 픽셀 임계값 없음), 모든 viewport에
            존재하고 부모가 같으면 MATCHED_ALL, 일부 viewport에 없으면 HIDDEN_IN_SOME, 모든
            viewport에 있지만 부모 selectorHint가 다르면 MOVED로 분류합니다.
            """)
    public RenderedDesignBundle captureWebPageMultiViewport(CaptureWebPageRequest request) {
        return service.captureMultiViewport(request);
    }
}
