package com.krdevops.springai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.figma.FigmaDesignOrchestrationService;
import com.krdevops.springai.service.figma.FigmaToolAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** 승인된 화면명세를 실제 Bundle Artifact로 만드는 인증된 MCP 진입점. */
@Component
@RequiredArgsConstructor
public class FigmaApprovedSpecificationTool {
    private final FigmaToolAuthorizationService authorization;
    private final FigmaDesignOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    @Tool(description = "APPROVED ScreenSpecification을 Figma Bundle Artifact로 생성하고 PREVIEW_READY Operation을 반환합니다.")
    public String createFigmaBundleFromApprovedSpecification(
            String figmaMcpSecret,
            FigmaDesignRequest designRequest,
            FigmaScreenExportRequest exportRequest) {
        authorization.authorize(figmaMcpSecret);
        try {
            return objectMapper.writeValueAsString(
                    orchestrationService.processApprovedSpecificationRequest(designRequest, exportRequest));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Figma Operation 직렬화 실패", exception);
        }
    }
}
