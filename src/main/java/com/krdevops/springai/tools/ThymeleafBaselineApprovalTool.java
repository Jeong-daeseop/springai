package com.krdevops.springai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;
import com.krdevops.springai.model.thymeleaf.BaselineApprovalRequest;
import com.krdevops.springai.service.thymeleaf.BaselineApprovalService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/** REST와 동일한 {@link BaselineApprovalService}를 쓰는 시각 baseline 승인 MCP 진입점. */
@Component
@RequiredArgsConstructor
public class ThymeleafBaselineApprovalTool {
    private final ThymeleafToolAuthorizationService authorization;
    private final BaselineApprovalService baselineApproval;
    private final ObjectMapper objectMapper;

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "적용된 Thymeleaf 화면을 지금 렌더해 시각 비교 baseline으로 승인합니다. "
            + "url 또는 renderedHtml 중 하나만 지정하며, 요청한 viewport가 하나라도 렌더에 실패하면 "
            + "아무 baseline도 갱신하지 않습니다.")
    public String approveThymeleafBaseline(
            String sharedSecret, String operationId, String screenId, String relativeFile,
            String url, String renderedHtml, List<String> viewports,
            List<String> maskSelectors, String readySelector) {
        authorization.authorize(sharedSecret);
        var result = baselineApproval.approve(new BaselineApprovalRequest(
                operationId, screenId, relativeFile, url, renderedHtml,
                viewports, maskSelectors, readySelector));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Thymeleaf baseline 승인 응답 직렬화 실패", exception);
        }
    }
}
