package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

/**
 * LLM 라우팅 정책 서비스.
 * 작업 유형에 따라 Claude(MCP SSE) / OpenAI / Ollama 중 하나로 라우팅한다.
 *
 * Claude는 MCP SSE로 직접 연결되므로 여기서는 OpenAI / Ollama만 직접 호출한다.
 */
@Service
@RequiredArgsConstructor
public class LlmRouterService {

    private final OpenAiChatModel openAiChatModel;
    private final OllamaChatModel ollamaChatModel;

    public enum LlmTarget { CLAUDE, OPENAI, OLLAMA }

    /**
     * 작업 유형 문자열을 받아 LLM 타겟을 반환한다.
     */
    public LlmTarget route(String taskType) {
        if (taskType == null) return LlmTarget.CLAUDE;

        return switch (taskType.toUpperCase()) {
            case "CODE_GENERATION"  -> LlmTarget.CLAUDE;   // 코드 생성 — 품질 우선 (Claude Desktop via SSE)
            case "CLASSIFICATION"   -> LlmTarget.OPENAI;   // 단순 분류/요약 — 비용 절감
            case "SIMPLE_QUERY"     -> LlmTarget.OPENAI;   // 단순 조회 — 비용 절감
            case "SENSITIVE_DATA"   -> LlmTarget.OLLAMA;   // 민감 데이터 — 사내 처리 (외부 전송 금지)
            default                 -> LlmTarget.CLAUDE;
        };
    }

    /**
     * taskType에 따라 적절한 LLM을 선택해 프롬프트를 실행하고 결과를 반환한다.
     * Claude(CODE_GENERATION)는 MCP SSE 채널이므로 이 메서드에서 처리하지 않는다.
     *
     * @throws IllegalArgumentException taskType이 CLAUDE인 경우 (MCP SSE로 처리)
     */
    public String chat(String taskType, String userMessage) {
        LlmTarget target = route(taskType);

        return switch (target) {
            case OPENAI -> {
                ChatResponse response = openAiChatModel.call(new Prompt(userMessage));
                yield response.getResult().getOutput().getText();
            }
            case OLLAMA -> {
                ChatResponse response = ollamaChatModel.call(new Prompt(userMessage));
                yield response.getResult().getOutput().getText();
            }
            case CLAUDE -> throw new IllegalArgumentException(
                "CODE_GENERATION은 Claude Desktop MCP SSE 채널로 처리합니다.");
        };
    }
}
