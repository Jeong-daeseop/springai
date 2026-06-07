package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * LLM 라우팅 정책 서비스.
 * 작업 유형(taskType) 또는 모델명(modelName) 기준으로
 * OpenAI / Ollama ChatClient를 반환한다.
 *
 * Claude는 MCP SSE로 직접 연결되므로 여기서는 OpenAI / Ollama만 처리한다.
 */
@Service
public class LlmRouterService {

    private final ChatClient openAiChatClient;
    private final ChatClient ollamaChatClient;

    public LlmRouterService(
            @Qualifier("openAiChatClient") ChatClient openAiChatClient,
            @Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
        this.openAiChatClient = openAiChatClient;
        this.ollamaChatClient = ollamaChatClient;
    }

    public enum LlmTarget { CLAUDE, OPENAI, OLLAMA }

    /**
     * taskType 또는 모델명을 받아 적절한 ChatClient를 반환한다.
     * 모델명(gpt-, o1, o3 접두사)은 OpenAI, 나머지는 Ollama로 라우팅한다.
     * taskType은 CLASSIFICATION/SIMPLE_QUERY=OpenAI, SENSITIVE_DATA=Ollama.
     */
    public ChatClient route(String modelOrTaskType) {
        if (modelOrTaskType == null) return ollamaChatClient;

        // 모델명 기반 라우팅 (웹 채팅 경로)
        if (modelOrTaskType.startsWith("gpt-") ||
            modelOrTaskType.startsWith("o1")   ||
            modelOrTaskType.startsWith("o3")) {
            return openAiChatClient;
        }

        // taskType 기반 라우팅 (REST API 경로)
        return switch (modelOrTaskType.toUpperCase()) {
            case "CLASSIFICATION", "SIMPLE_QUERY" -> openAiChatClient;
            case "SENSITIVE_DATA"                 -> ollamaChatClient;
            default                               -> ollamaChatClient;
        };
    }

    /**
     * taskType에 따라 적절한 LLM을 선택해 프롬프트를 실행하고 결과를 반환한다.
     * 하위 호환성 유지 — ToolApiController, RagController에서 호출.
     *
     * @throws IllegalArgumentException taskType이 CODE_GENERATION인 경우 (MCP SSE로 처리)
     */
    public String chat(String taskType, String userMessage) {
        if ("CODE_GENERATION".equalsIgnoreCase(taskType)) {
            throw new IllegalArgumentException(
                "CODE_GENERATION은 Claude Desktop MCP SSE 채널로 처리합니다.");
        }
        return route(taskType).prompt().user(userMessage).call().content();
    }
}
