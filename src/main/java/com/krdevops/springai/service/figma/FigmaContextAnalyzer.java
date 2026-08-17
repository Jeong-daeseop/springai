package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Figma 디자인 컨텍스트를 LLM 구조화 출력으로 분석한다.
 * R6-042: FigmaContextAnalyzer 구현
 *
 * 입력: 자연어 요청 + Figma 노드 정보
 * 출력: 업무 도메인, 화면 유형, Layout 패턴, 필수 컴포넌트, 불확실성 판정
 */
@Service
public class FigmaContextAnalyzer {

    private final ChatClient chatClient;
    private final FigmaResponseRedactor redactor;

    public FigmaContextAnalyzer(
            @Qualifier("openAiChatClient") ChatClient chatClient,
            FigmaResponseRedactor redactor) {
        this.chatClient = chatClient;
        this.redactor = redactor;
    }

    /**
     * 자연어 요청과 Figma 노드를 분석해서 domain, screenType, layoutPattern, 필수 컴포넌트를
     * 판정한다. Spring AI `.entity()` 구조화 출력을 사용해 LLM 응답을 {@link FigmaScreenType}/
     * {@link LayoutPattern} enum으로 직접 역직렬화하며, 수동 JSON 문자열 파싱을 하지 않는다.
     */
    public FigmaContextAnalysis analyze(String prompt, JsonNode figmaNodeData) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 필수입니다");
        }
        try {
            LlmAnalysisResponse response = chatClient.prompt()
                    .user(buildAnalysisPrompt(prompt, figmaNodeData))
                    .call()
                    .entity(LlmAnalysisResponse.class);
            if (response == null) {
                return FigmaContextAnalysis.uncertain("비전 모델이 빈 분석 결과를 반환했습니다.");
            }
            return FigmaContextAnalysis.from(response);
        } catch (Exception e) {
            // LLM 호출 실패 시 UNCERTAIN으로 기본값 반환. R6-041: 예외 메시지에
            // LLM key·Figma access token·image URL이 섞여 나올 수 있어 reason에 담기 전에 redact한다.
            return FigmaContextAnalysis.uncertain("LLM 분석 실패: " + redactor.redact(e.getMessage()));
        }
    }

    private String buildAnalysisPrompt(String prompt, JsonNode figmaNodeData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Figma 디자인 분석 요청:\n\n");
        sb.append("요청: ").append(prompt).append("\n\n");

        if (figmaNodeData != null && !figmaNodeData.isEmpty()) {
            sb.append("Figma 구조:\n");
            sb.append(figmaNodeData.toPrettyString()).append("\n\n");
        }

        sb.append("다음을 분석하세요.\n");
        sb.append("domain: 업무 도메인을 나타내는 짧은 영문 소문자 단어(예: user, order, payment, board).\n");
        sb.append("screenType: 화면 유형.\n");
        sb.append("layoutPattern: Layout 패턴.\n");
        sb.append("requiredComponentLogicalTypes: 필요한 KRDS 논리 컴포넌트 타입 목록(예: krds.button).\n");
        sb.append("uncertainty: 판단 불확실도(0.0=확실, 1.0=매우 불확실).\n");
        sb.append("reason: 판단 근거.\n");

        return sb.toString();
    }

    /** LLM 구조화 출력 스키마 — Spring AI {@code .entity()}가 이 record 형태로 응답을 강제한다. */
    record LlmAnalysisResponse(
            String domain,
            FigmaScreenType screenType,
            LayoutPattern layoutPattern,
            List<String> requiredComponentLogicalTypes,
            double uncertainty,
            String reason
    ) {
    }

    /**
     * Figma 컨텍스트 분석 결과.
     */
    public record FigmaContextAnalysis(
            String domain,
            FigmaScreenType screenType,
            LayoutPattern layoutPattern,
            List<String> requiredComponentLogicalTypes,
            double uncertainty,
            String reason,
            boolean uncertain            // 신뢰도 낮음
    ) {
        static FigmaContextAnalysis from(LlmAnalysisResponse response) {
            double uncertainty = clamp(response.uncertainty());
            return new FigmaContextAnalysis(
                    response.domain(),
                    response.screenType() == null ? FigmaScreenType.FORM : response.screenType(),
                    response.layoutPattern() == null ? LayoutPattern.STANDARD : response.layoutPattern(),
                    response.requiredComponentLogicalTypes() == null
                            ? List.of() : List.copyOf(response.requiredComponentLogicalTypes()),
                    uncertainty,
                    response.reason(),
                    uncertainty > 0.5);
        }

        public static FigmaContextAnalysis uncertain(String reason) {
            return new FigmaContextAnalysis(
                    null, FigmaScreenType.FORM, LayoutPattern.STANDARD, List.of(), 0.9, reason, true);
        }

        public boolean hasHighConfidence() {
            return uncertainty < 0.3 && !uncertain;
        }

        public boolean requiresReview() {
            return uncertainty > 0.5 || uncertain;
        }

        private static double clamp(double value) {
            if (Double.isNaN(value)) {
                return 0.9;
            }
            return Math.max(0.0, Math.min(1.0, value));
        }
    }
}
