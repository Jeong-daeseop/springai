package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.service.figma.FigmaMcpFacadeService;
import com.krdevops.springai.service.figma.FigmaToolAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** R6-020/021: ScreenSpecification → FigmaScreenSpec 생성·검증 MCP Tool. */
@Component
@RequiredArgsConstructor
public class FigmaExportTool {

    private final FigmaMcpFacadeService facadeService;
    private final FigmaToolAuthorizationService authorization;

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            승인된 ScreenSpecification에서 FigmaScreenSpec을 생성합니다.
            figmaMcpSecret 인증값이 반드시 필요합니다.
            이 도구는 Figma Library Publish·삭제·Component Key 교체를 수행하지 않으며,
            Publish와 Breaking 변경은 사람의 Preview 승인 후 별도 절차로 진행해야 합니다.
            """)
    public String generateFigmaScreenSpec(
            String figmaMcpSecret,
            FigmaScreenExportRequest request
    ) {
        authorization.authorize(figmaMcpSecret);
        return facadeService.generateScreen(request);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            저장된 FigmaScreenSpec의 필수값·logicalNodeId·지원 Component 문제를 검증합니다.
            figmaMcpSecret 인증값이 반드시 필요하며 Registry 공개 Key는 응답에 포함하지 않습니다.
            """)
    public String validateFigmaScreenSpec(
            String figmaMcpSecret,
            String screenId,
            Integer version
    ) {
        authorization.authorize(figmaMcpSecret);
        return facadeService.validateScreen(screenId, version);
    }
}
