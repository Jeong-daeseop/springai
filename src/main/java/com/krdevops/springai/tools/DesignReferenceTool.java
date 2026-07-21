package com.krdevops.springai.tools;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SemanticDesignCandidate;
import com.krdevops.springai.service.DesignReferenceAnalysisService;
import com.krdevops.springai.service.ScreenSpecificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DesignReferenceTool {

    private final DesignReferenceAnalysisService designReferenceAnalysisService;
    private final ScreenSpecificationService screenSpecificationService;

    @Tool(description = """
        PNG/JPEG 또는 이미지형 PDF 디자인 참조를 분석하여 UiDesignSpec을 생성합니다.
        보안 검토 후 app.design-vision.provider가 openai 또는 ollama로 설정된 환경에서만 실행됩니다.
        pageRange는 PDF의 1-based 페이지 범위이며 예: 1-3,5 형식입니다.
        반환된 analysisId를 createScreenSpecification의 designAnalysisId로 전달하세요.
        """)
    public DesignAnalysisResult analyzeDesignReference(
            String referencePath, @Nullable String pageRange, @Nullable String featureType) {
        return designReferenceAnalysisService.analyze(referencePath, pageRange, featureType);
    }

    @Tool(description = """
        Figma Design의 단일 화면 FRAME URL에서 레이어 트리와 기하 정보를 분석하여 UiDesignSpec을 생성합니다.
        https://www.figma.com/file/... 또는 /design/... URL만 허용하며 node-id가 필요합니다.
        SECTION, CANVAS, DOCUMENT 등 여러 화면을 포함할 수 있는 노드는 지원하지 않습니다.
        URL의 node-id와 별도 nodeId가 모두 있으면 두 값이 일치해야 합니다.
        app.design-vision.figma.enabled=true이고 FIGMA_ACCESS_TOKEN이 설정된 환경에서만 실행됩니다.
        반환된 analysisId를 createScreenSpecification의 designAnalysisId로 전달하세요.
        """)
    public DesignAnalysisResult analyzeFigmaReference(
            String figmaUrl, @Nullable String nodeId, @Nullable String featureType) {
        return designReferenceAnalysisService.analyzeFigma(figmaUrl, nodeId, featureType);
    }

    @Tool(description = """
        화면 설명으로 과거 디자인 분석의 시맨틱 후보를 찾고 현재 provider/model/promptVersion 및
        기대 archetype, featureType과의 호환성을 재검증합니다. reusable=true여도 자동 대체하지 않으며,
        사용자가 분석 결과를 확인한 뒤 designAnalysisId로 선택해야 합니다.
        """)
    public List<SemanticDesignCandidate> findReusableDesignAnalyses(
            String query, @Nullable String expectedArchetype,
            @Nullable String expectedFeatureType, int topK) {
        return designReferenceAnalysisService.findReusableCandidates(
                query, expectedArchetype, expectedFeatureType, topK);
    }

    /** Java 호출자 하위 호환용. MCP에는 위 4인자 Tool만 노출한다. */
    public List<SemanticDesignCandidate> findReusableDesignAnalyses(
            String query, @Nullable String expectedArchetype, int topK) {
        return designReferenceAnalysisService.findReusableCandidates(query, expectedArchetype, topK);
    }

    @Tool(description = """
        DB 스키마와 선택적인 디자인 분석 결과를 결합하여 실행 가능한 ScreenSpecification을 만듭니다.
        명확한 표준 매핑은 APPROVED, 미매핑·JOIN·불확실성이 있으면 REVIEW_REQUIRED로 반환합니다.
        designAnalysisId가 없으면 DB 스키마만으로 표준 CRUD 화면명세를 생성합니다.
        """)
    public ScreenSpecification createScreenSpecification(
            String database,
            String tableName,
            @Nullable String screenName,
            @Nullable String featureType,
            @Nullable String designAnalysisId,
            @Nullable List<String> listColumns,
            @Nullable List<String> detailColumns) {
        DesignAnalysisResult analysis = designAnalysisId == null || designAnalysisId.isBlank()
                ? null : designReferenceAnalysisService.get(designAnalysisId);
        return screenSpecificationService.create(database, tableName, screenName, featureType,
                analysis == null ? null : analysis.uiSpec(), listColumns, detailColumns);
    }

    /** Java 호출자 하위 호환용. MCP에는 위 단일 7인자 Tool만 노출한다. */
    public ScreenSpecification createScreenSpecification(
            String database,
            String tableName,
            @Nullable String screenName,
            @Nullable String featureType,
            @Nullable String designAnalysisId) {
        DesignAnalysisResult analysis = designAnalysisId == null || designAnalysisId.isBlank()
                ? null : designReferenceAnalysisService.get(designAnalysisId);
        return screenSpecificationService.create(database, tableName, screenName, featureType,
                analysis == null ? null : analysis.uiSpec());
    }

    @Tool(description = "미해결 이슈가 없는 ScreenSpecification을 승인 상태로 확정합니다.")
    public ScreenSpecification approveScreenSpecification(String screenSpecificationId) {
        return screenSpecificationService.approve(screenSpecificationId);
    }

    @Tool(description = """
        REVIEW_REQUIRED 화면명세의 미매핑 필드·JOIN·Action을 수정한 전체 명세를 새 버전으로 저장합니다.
        주 데이터베이스와 주 테이블은 변경할 수 없으며, COLUMN 출처는 실제 스키마에 존재하는지 다시 검증합니다.
        반환 상태가 APPROVED일 때만 코드 생성 Tool의 screenSpecificationId로 전달하세요.
        """)
    public ScreenSpecification reviseScreenSpecification(ScreenSpecification specification) {
        return screenSpecificationService.revise(specification);
    }

    @Tool(description = "저장된 최신 ScreenSpecification을 ID로 조회합니다.")
    public ScreenSpecification getScreenSpecification(String screenSpecificationId) {
        return screenSpecificationService.get(screenSpecificationId);
    }
}
