package com.krdevops.springai.chat.service.impl;

import com.krdevops.springai.chat.config.rag.transformers.EgovCompressionQueryTransformer;
import com.krdevops.springai.chat.context.SessionContext;
import com.krdevops.springai.chat.response.TechnologyResponse;
import com.krdevops.springai.chat.service.EgovSessionAwareChatService;
import com.krdevops.springai.chat.util.EgovThinkTagOutputConverter;
import com.krdevops.springai.service.ContextAssembler;
import com.krdevops.springai.service.EgovPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class EgovSessionAwareChatServiceImpl implements EgovSessionAwareChatService {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final EgovCompressionQueryTransformer compressionTransformer;
    private final EgovPromptBuilder promptBuilder;
    private final ContextAssembler contextAssembler;

    @Value("${rag.enable-query-compression:true}")
    private boolean enableQueryCompression;

    @Value("${rag.top-k:3}")
    private int topK;

    private final StructuredOutputConverter<TechnologyResponse> technologyOutputConverter =
        EgovThinkTagOutputConverter.of(TechnologyResponse.class);

    @Override
    public Flux<ChatResponse> streamRagResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("RAG 스트리밍 - 세션: {}, 질문: {}", sessionId, query);

        try {
            String searchQuery = enableQueryCompression
                ? compressionTransformer.compress(query, sessionId)
                : query;

            String context = contextAssembler.build(searchQuery, topK);

            var promptSpec = chatClient.prompt();

            if (!context.isEmpty()) {
                log.info("통합 컨텍스트 적용 - 세션: {}, chars={}", sessionId, context.length());
                promptSpec = promptSpec.system(promptBuilder.assembledSystemPrompt(context));
            } else {
                log.info("컨텍스트 없음 - 세션: {}", sessionId);
                promptSpec = promptSpec.system(promptBuilder.systemRole());
            }

            var requestSpec = promptSpec.user(query);
            if (model != null && !model.trim().isEmpty()) {
                requestSpec = requestSpec.options(ChatOptions.builder().model(model).temperature(0.3));
            }

            return requestSpec
                .advisors(messageChatMemoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse();
        } catch (Exception e) {
            log.error("RAG 스트리밍 오류 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }

    @Override
    public Flux<ChatResponse> streamSimpleResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("일반 스트리밍 - 세션: {}, 질문: {}", sessionId, query);

        try {
            var requestSpec = chatClient.prompt().user(query);
            if (model != null && !model.trim().isEmpty()) {
                requestSpec = requestSpec.options(ChatOptions.builder().model(model).temperature(0.3));
            }

            return requestSpec
                .advisors(messageChatMemoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse();
        } catch (Exception e) {
            log.error("일반 스트리밍 오류 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }

    @Override
    public TechnologyResponse getTechnologyInfoAsJson(String query) {
        try {
            return chatClient.prompt()
                .user(u -> u.text("다음 질문에 대해 기술 정보를 제공해주세요: {query}")
                    .param("query", query))
                .call()
                .entity(technologyOutputConverter);
        } catch (Exception e) {
            log.error("JSON 응답 생성 오류", e);
            return new TechnologyResponse("알 수 없음", "알 수 없음", "오류가 발생했습니다", null, null);
        }
    }
}
