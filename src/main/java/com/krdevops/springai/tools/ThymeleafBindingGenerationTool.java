package com.krdevops.springai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
import com.krdevops.springai.service.thymeleaf.ThymeleafBindingGenerationService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** MCP에서 WP6 Binding 생성 파이프라인을 시작하는 얇은 인증 래퍼. */
@Component
@RequiredArgsConstructor
public class ThymeleafBindingGenerationTool {

    private final ThymeleafToolAuthorizationService authorization;
    private final ThymeleafBindingGenerationService generationService;
    private final ObjectMapper objectMapper;

    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = "프로젝트의 JSP·Controller·VO를 안전하게 분석해 Binding Contract 기반 Thymeleaf를 생성하고, "
            + "파일을 쓰지 않은 PREVIEW_READY 승인 Operation을 만듭니다. REVIEW_REQUIRED 계약은 Preview 전에 차단합니다.")
    public String previewThymeleafBindingGeneration(
            String sharedSecret, ThymeleafBindingPreviewRequest request) {
        authorization.authorize(sharedSecret);
        try {
            return objectMapper.writeValueAsString(generationService.preview(request));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Thymeleaf Binding Preview 응답 직렬화 실패", exception);
        }
    }
}
