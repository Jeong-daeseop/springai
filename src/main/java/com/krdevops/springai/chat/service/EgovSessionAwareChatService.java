package com.krdevops.springai.chat.service;

import com.krdevops.springai.chat.response.TechnologyResponse;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public interface EgovSessionAwareChatService {
    Flux<ChatResponse> streamRagResponse(String query, String model, String sessionId);
    Flux<ChatResponse> streamSimpleResponse(String query, String model, String sessionId);
    TechnologyResponse getTechnologyInfoAsJson(String query);
}
