package com.krdevops.springai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.design-vision", name = "provider", havingValue = "ollama")
public class OllamaVisionAnalysisClient extends AbstractChatVisionAnalysisClient {

    public OllamaVisionAnalysisClient(
            @Qualifier("ollamaVisionChatClient") ChatClient chatClient,
            @Value("${app.design-vision.ollama-model:qwen2.5vl:7b}") String model) {
        super(chatClient, model);
    }

    @Override
    public String providerId() {
        return "ollama";
    }
}
