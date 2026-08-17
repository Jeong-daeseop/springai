package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.designsystem.DesignSystemSpec;
import com.krdevops.springai.service.figma.FigmaMcpFacadeService;
import com.krdevops.springai.service.figma.FigmaToolAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/** R6-022/023: DesignSystemSpec 검증과 Registry 드리프트 감사 MCP Tool. */
@Component
@RequiredArgsConstructor
public class DesignSystemTool {

    private final FigmaMcpFacadeService facadeService;
    private final FigmaToolAuthorizationService authorization;

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            에이전트가 만든 DesignSystemSpec의 논리 Component·Property·Pattern 참조를 검증합니다.
            figmaMcpSecret 인증값이 반드시 필요합니다.
            이 도구는 Library를 Publish하지 않으며, 실제 Publish는 사람이 Preview를 검토·승인한 뒤 수행합니다.
            """)
    public String validateDesignSystemSpec(
            String figmaMcpSecret,
            DesignSystemSpec spec
    ) {
        authorization.authorize(figmaMcpSecret);
        return facadeService.validateDesignSystem(spec);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            저장된 ComponentRegistry와 DesignSystemProfile의 Publish 상태·버전 드리프트를 점검합니다.
            figmaMcpSecret 인증값이 반드시 필요합니다.
            Component/Variable 공개 Key 원문은 응답하지 않으며 삭제·교체는 사람 승인 없이 수행하지 않습니다.
            """)
    public String auditComponentRegistry(
            String figmaMcpSecret,
            String profileId,
            String registryVersion
    ) {
        authorization.authorize(figmaMcpSecret);
        return facadeService.auditRegistry(profileId, registryVersion);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            화면 생성 전에 Registry/Profile 드리프트와 필수 논리 컴포넌트의 alias·replacement 해석 가능 여부를 점검합니다.
            figmaMcpSecret 인증값이 반드시 필요하며 Published Component Key 원문은 응답하지 않습니다.
            expectedLayoutPolicyVersion을 지정하면 Profile의 layoutPolicyVersion과 다를 때 실패합니다(생략 가능, R1-015).
            """)
    public String preflightComponentRegistry(
            String figmaMcpSecret,
            String profileId,
            String registryVersion,
            List<String> requiredLogicalTypes,
            String expectedLayoutPolicyVersion
    ) {
        authorization.authorize(figmaMcpSecret);
        return facadeService.preflightRegistry(
                profileId, registryVersion, requiredLogicalTypes, expectedLayoutPolicyVersion);
    }

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            참조 Figma 파일(fileKey)의 Styles에서 뽑은 색상·타이포·간격·radius Token 후보와
            운영 DesignSystemProfile(profileId)의 Token 차이를 계산해 반환합니다. 조회 전용이며
            Profile이나 Figma Library는 절대 쓰지 않습니다 — 반영은 사람이 별도로 승인해야 합니다.
            figmaMcpSecret 인증값이 반드시 필요합니다.

            출력의 각 항목은 MATCHED(이미 Profile에 있고 바인딩됨)/NEW_CANDIDATE(Profile에 없는 새 후보)/
            UNBOUND_IN_PROFILE(Profile에 있지만 아직 Variable에 바인딩 안 됨) 중 하나로 분류됩니다.
            """)
    public String previewStyleTokenDiff(String figmaMcpSecret, String fileKey, String profileId) {
        authorization.authorize(figmaMcpSecret);
        return facadeService.previewStyleTokenDiff(fileKey, profileId);
    }
}
