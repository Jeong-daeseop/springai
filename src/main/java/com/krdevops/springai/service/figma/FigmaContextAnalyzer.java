package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Figma 디자인 컨텍스트를 LLM으로 분석.
 * 2-A3 FigmaContextAnalyzer 구현
 *
 * 입력: 자연어 요청 + Figma 노드 정보
 * 출력: 화면 타입, 필드 역할, 불확실성 판정
 */
@Service
public class FigmaContextAnalyzer {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public FigmaContextAnalyzer(
            @Qualifier("openAiChatClient") ChatClient chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 자연어 요청과 Figma 노드를 분석해서 화면 타입, 필드 역할, 필수 컴포넌트를 판정한다.
     */
    public FigmaContextAnalysis analyze(String prompt, JsonNode figmaNodeData) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 필수입니다");
        }

        try {
            // LJavaScript 프롬프트 구성
            String analysisPrompt = buildAnalysisPrompt(prompt, figmaNodeData);

            // Spring AI ChatClient로 구조화된 출력 요청
            String response = chatClient.prompt()
                    .user(analysisPrompt)
                    .call()
                    .content();

            // 응답 파싱
            return parseAnalysisResponse(response);
        } catch (Exception e) {
            // LLM 호출 실패 시 UNCERTAIN으로 기본값 반환
            return FigmaContextAnalysis.uncertain(
                    "LLM 분석 실패: " + e.getMessage()
            );
        }
    }

    /**
     * LLM 분석 프롬프트 구성.
     */
    private String buildAnalysisPrompt(String prompt, JsonNode figmaNodeData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Figma 디자인 분석 요청:\n\n");
        sb.append("요청: ").append(prompt).append("\n\n");

        if (figmaNodeData != null && !figmaNodeData.isEmpty()) {
            sb.append("Figma 구조:\n");
            sb.append(figmaNodeData.toPrettyString()).append("\n\n");
        }

        sb.append("다음을 JSON 형식으로 분석하세요:\n");
        sb.append("{\n");
        sb.append("  \"screenType\": \"LIST|FORM|DETAIL\",\n");
        sb.append("  \"layoutPattern\": \"STANDARD|MASTER_DETAIL|DASHBOARD\",\n");
        sb.append("  \"requiredComponentLogicalTypes\": [\"krds.button\", ...],\n");
        sb.append("  \"fieldRoles\": {\"필드명\": \"SEARCH_INPUT|DISPLAY_FIELD|ACTION_BUTTON|...\"...},\n");
        sb.append("  \"uncertainty\": 0.0,\n");
        sb.append("  \"reason\": \"분석 근거\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * LLM 응답 파싱.
     */
    private FigmaContextAnalysis parseAnalysisResponse(String response) {
        try {
            // JSON 추출 (응답에 JSON이 포함되어 있을 수 있음)
            String jsonPart = extractJsonFromResponse(response);
            JsonNode analysisNode = objectMapper.readTree(jsonPart);

            String screenType = analysisNode.path("screenType").asText("FORM");
            String layoutPattern = analysisNode.path("layoutPattern").asText("STANDARD");
            double uncertainty = analysisNode.path("uncertainty").asDouble(0.5);
            String reason = analysisNode.path("reason").asText("분석 완료");

            List<String> requiredTypes = new ArrayList<>();
            analysisNode.path("requiredComponentLogicalTypes").forEach(node ->
                    requiredTypes.add(node.asText())
            );

            return new FigmaContextAnalysis(
                    screenType,
                    layoutPattern,
                    requiredTypes,
                    uncertainty,
                    reason,
                    false
            );
        } catch (Exception e) {
            return FigmaContextAnalysis.uncertain("응답 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * 응답에서 JSON 부분 추출.
     */
    private String extractJsonFromResponse(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start == -1 || end == -1) {
            throw new IllegalArgumentException("응답에 JSON이 없습니다");
        }

        return response.substring(start, end + 1);
    }

    /**
     * Figma 컨텍스트 분석 결과.
     */
    public record FigmaContextAnalysis(
            String screenType,           // LIST, FORM, DETAIL
            String layoutPattern,        // STANDARD, MASTER_DETAIL, DASHBOARD
            List<String> requiredComponentLogicalTypes,
            double uncertainty,          // 0.0 ~ 1.0
            String reason,
            boolean uncertain            // 신뢰도 낮음
    ) {
        public static FigmaContextAnalysis uncertain(String reason) {
            return new FigmaContextAnalysis(
                    "FORM",
                    "STANDARD",
                    List.of(),
                    0.9,
                    reason,
                    true
            );
        }

        public boolean hasHighConfidence() {
            return uncertainty < 0.3 && !uncertain;
        }

        public boolean requiresReview() {
            return uncertainty > 0.5 || uncertain;
        }
    }
}
