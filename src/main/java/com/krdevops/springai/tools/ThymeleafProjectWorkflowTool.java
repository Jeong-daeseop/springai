package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.service.thymeleaf.ThymeleafProjectWorkflowService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/** REST와 동일 Workflow를 사용하는 승인 기반 Thymeleaf MCP 진입점. */
@Component
@RequiredArgsConstructor
public class ThymeleafProjectWorkflowTool {
    private final ThymeleafToolAuthorizationService authorization;
    private final ThymeleafProjectWorkflowService workflow;
    private final ObjectMapper objectMapper;

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "Thymeleaf 생성 파일의 Preview와 diff 기준 hash를 만들며 대상 프로젝트는 변경하지 않습니다.")
    public String previewThymeleafProject(
            String sharedSecret, String projectRoot, Map<String, String> generatedFiles) {
        authorization.authorize(sharedSecret);
        return json(workflow.preview(Path.of(projectRoot), generatedFiles));
    }

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "Preview hash가 일치하는 Thymeleaf Project Operation을 명시적으로 승인합니다.")
    public String approveThymeleafProject(String sharedSecret, String operationId, String previewHash) {
        authorization.authorize(sharedSecret);
        return json(workflow.approve(operationId, previewHash));
    }

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "승인된 Thymeleaf Project Operation을 source revision 재검증 후 원자 적용합니다.")
    public String applyThymeleafProject(String sharedSecret, String operationId) {
        authorization.authorize(sharedSecret);
        return json(workflow.apply(operationId));
    }

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "적용된 Thymeleaf 파일의 검증 Gate를 다시 실행합니다.")
    public String revalidateThymeleafProject(String sharedSecret, String operationId) {
        authorization.authorize(sharedSecret);
        return json(workflow.revalidate(operationId));
    }

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "Thymeleaf Project Operation의 현재 상태와 Preview hash를 조회합니다.")
    public String getThymeleafProjectReport(String sharedSecret, String operationId) {
        authorization.authorize(sharedSecret);
        return json(workflow.find(operationId).orElseThrow(
                () -> new IllegalArgumentException("THYMELEAF_OPERATION_NOT_FOUND: " + operationId)));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Thymeleaf MCP 응답 직렬화 실패", exception);
        }
    }
}
