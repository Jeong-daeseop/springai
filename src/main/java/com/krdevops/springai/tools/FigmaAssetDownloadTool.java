package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.service.DesignReferenceAnalysisService;
import com.krdevops.springai.service.FigmaAssetDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FigmaAssetDownloadTool {

    private final DesignReferenceAnalysisService designReferenceAnalysisService;
    private final FigmaAssetDownloadService assetDownloadService;

    @McpToolRisk(McpToolRiskLevel.FILE_WRITE)
    @Tool(description = """
            analyzeFigmaReference()가 반환한 analysisId로 원본 Figma 파일을 다시 조회해, 그 화면에
            있던 아이콘/이미지(VECTOR 또는 IMAGE 채우기 노드)를 다운로드해 생성 프로젝트의
            src/main/resources/static/images/figma/ 아래에 저장합니다.
            analysisId가 Figma 출처가 아니면(analyzeDesignReference()로 만든 이미지/PDF 분석 등)
            실패합니다. 렌더 실패한 개별 노드는 조용히 건너뛰고 나머지만 저장합니다.
            ⚠ 허용 이미지 호스트(*.figma.com)는 이 환경에서 실제 API 응답으로 검증하지 못한
            추정치입니다 — 실사용 전 재확인이 필요할 수 있습니다.
            analysisId: analyzeFigmaReference()가 반환한 analysisId
            outputPath: 이미지가 저장될 생성 프로젝트 루트(승인된 출력 경로)
            """)
    public List<String> downloadFigmaAssets(String analysisId, String outputPath) {
        DesignAnalysisResult analysis = designReferenceAnalysisService.get(analysisId);
        if (analysis.figmaSource() == null) {
            throw new IllegalArgumentException(
                    "Figma 출처 분석 결과가 아닙니다(analyzeFigmaReference로 만든 analysisId가 필요합니다): "
                            + analysisId);
        }
        List<String> imageNodeIds = analysis.uiSpec().imageNodeIds();
        return assetDownloadService.downloadAssets(
                analysis.figmaSource().fileKey(), imageNodeIds, outputPath);
    }
}
