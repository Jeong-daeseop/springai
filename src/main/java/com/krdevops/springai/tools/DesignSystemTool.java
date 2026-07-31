package com.krdevops.springai.tools;

import com.krdevops.springai.model.designsystem.DesignSystemSpec;
import com.krdevops.springai.service.figma.FigmaMcpFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/** R6-022/023: DesignSystemSpec 검증과 Registry 드리프트 감사 MCP Tool. */
@Component
@RequiredArgsConstructor
public class DesignSystemTool {

    private final FigmaMcpFacadeService facadeService;

    @Tool(description = """
            에이전트가 만든 DesignSystemSpec의 논리 Component·Property·Pattern 참조를 검증합니다.
            figmaMcpSecret 인증값이 반드시 필요합니다.
            이 도구는 Library를 Publish하지 않으며, 실제 Publish는 사람이 Preview를 검토·승인한 뒤 수행합니다.
            """)
    public String validateDesignSystemSpec(
            String figmaMcpSecret,
            DesignSystemSpec spec
    ) {
        return facadeService.validateDesignSystem(figmaMcpSecret, spec);
    }

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
        return facadeService.auditRegistry(figmaMcpSecret, profileId, registryVersion);
    }

    @Tool(description = """
            화면 생성 전에 Registry/Profile 드리프트와 필수 논리 컴포넌트의 alias·replacement 해석 가능 여부를 점검합니다.
            figmaMcpSecret 인증값이 반드시 필요하며 Published Component Key 원문은 응답하지 않습니다.
            """)
    public String preflightComponentRegistry(
            String figmaMcpSecret,
            String profileId,
            String registryVersion,
            List<String> requiredLogicalTypes
    ) {
        return facadeService.preflightRegistry(
                figmaMcpSecret, profileId, registryVersion, requiredLogicalTypes);
    }
}
