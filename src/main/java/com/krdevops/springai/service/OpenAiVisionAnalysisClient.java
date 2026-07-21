package com.krdevops.springai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.design-vision", name = "provider", havingValue = "openai")
public class OpenAiVisionAnalysisClient extends AbstractChatVisionAnalysisClient {

    public OpenAiVisionAnalysisClient(
            @Qualifier("openAiVisionChatClient") ChatClient chatClient,
            @Value("${app.design-vision.openai-model:gpt-4o-mini}") String model) {
        super(chatClient, model);
    }

    @Override
    public String providerId() {
        return "openai";
    }
}
