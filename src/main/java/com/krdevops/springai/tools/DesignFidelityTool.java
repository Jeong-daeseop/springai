package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignFidelityReport;
import com.krdevops.springai.service.DesignFidelityComparator;
import com.krdevops.springai.service.DesignReferenceAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesignFidelityTool {

    private final DesignReferenceAnalysisService designReferenceAnalysisService;
    private final DesignFidelityComparator comparator;

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            Figma 원본 디자인 분석 결과와, 생성된 화면을 captureWebPage() + analyzeCapturedDesign()으로
            재캡처해 분석한 결과를 비교해 구조적 일치도를 계산합니다.
            픽셀 이미지 비교가 아니라 archetype/컴포넌트 타입/필드 역할/액션 타입 집합의 Jaccard
            유사도(0.0~1.0)이며, 시각적(색상·정확한 좌표) 일치를 보장하지 않습니다.
            originalAnalysisId: analyzeFigmaReference()가 반환한 analysisId입니다.
            renderedAnalysisId: 생성된 화면을 captureWebPage()로 캡처한 뒤 analyzeCapturedDesign()으로
              만든 analysisId입니다.
            두 analysisId 모두 존재하지 않으면 실패합니다.
            """)
    public DesignFidelityReport compareDesignFidelity(String originalAnalysisId, String renderedAnalysisId) {
        DesignAnalysisResult original = designReferenceAnalysisService.get(originalAnalysisId);
        DesignAnalysisResult rendered = designReferenceAnalysisService.get(renderedAnalysisId);
        return comparator.compare(
                originalAnalysisId, original.uiSpec(), renderedAnalysisId, rendered.uiSpec());
    }
}
