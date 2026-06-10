package com.krdevops.springai.chat.service.impl;

import com.krdevops.springai.chat.config.rag.transformers.EgovCompressionQueryTransformer;
import com.krdevops.springai.chat.response.TechnologyResponse;
import com.krdevops.springai.chat.service.EgovSessionAwareChatService;
import com.krdevops.springai.chat.util.EgovThinkTagOutputConverter;
import com.krdevops.springai.service.ContextAssembler;
import com.krdevops.springai.service.EgovPromptBuilder;
import com.krdevops.springai.service.LlmRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class EgovSessionAwareChatServiceImpl implements EgovSessionAwareChatService {

    private final ChatClient openAiChatClient;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final EgovCompressionQueryTransformer compressionTransformer;
    private final EgovPromptBuilder promptBuilder;
    private final ContextAssembler contextAssembler;
    private final LlmRouterService llmRouterService;

    public EgovSessionAwareChatServiceImpl(
            @Qualifier("openAiChatClient") ChatClient openAiChatClient,
            MessageChatMemoryAdvisor messageChatMemoryAdvisor,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            EgovCompressionQueryTransformer compressionTransformer,
            EgovPromptBuilder promptBuilder,
            ContextAssembler contextAssembler,
            LlmRouterService llmRouterService) {
        this.openAiChatClient = openAiChatClient;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.compressionTransformer = compressionTransformer;
        this.promptBuilder = promptBuilder;
        this.contextAssembler = contextAssembler;
        this.llmRouterService = llmRouterService;
    }

    /** 모델명으로 OpenAI/Ollama 클라이언트 동적 선택 — LlmRouterService 위임 */
    private ChatClient selectClient(String model) {
        ChatClient client = llmRouterService.route(model);
        log.info("LlmRouterService 라우팅: model={} → {}", model,
            client == openAiChatClient ? "OpenAI" : "Ollama");
        return client;
    }

    @Value("${rag.enable-query-compression:true}")
    private boolean enableQueryCompression;

    private final StructuredOutputConverter<TechnologyResponse> technologyOutputConverter =
        EgovThinkTagOutputConverter.of(TechnologyResponse.class);

    @Override
    public Flux<ChatResponse> streamRagResponse(String query, String model, String sessionId) {
        log.info("RAG 스트리밍 - 세션: {}, 질문: {}", sessionId, query);

        // 쿼리 압축(Ollama 동기 호출)을 boundedElastic 스레드로 오프로드 — HTTP 요청 스레드 블로킹 방지
        return Mono.fromCallable(() -> enableQueryCompression
                    ? compressionTransformer.compress(query, sessionId)
                    : query)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(searchQuery -> streamRagFromQuery(searchQuery, model, sessionId))
                .doOnError(e -> log.error("RAG 스트리밍 오류 - 세션: {}", sessionId, e));
    }

    private Flux<ChatResponse> streamRagFromQuery(String searchQuery, String model, String sessionId) {
        try {
            String context = contextAssembler.build(searchQuery);

            var promptSpec = selectClient(model).prompt();

            if (!context.isEmpty()) {
                log.info("통합 컨텍스트 적용 - 세션: {}, chars={}", sessionId, context.length());
                promptSpec = promptSpec.system(promptBuilder.assembledSystemPrompt(context));
            } else {
                log.info("RAG 문서 모드 - 세션: {}", sessionId);
                promptSpec = promptSpec.system(promptBuilder.ragSystemPrompt(""));
            }

            var requestSpec = promptSpec.user(searchQuery);
            if (model != null && !model.trim().isEmpty()) {
                requestSpec = requestSpec.options(ChatOptions.builder().model(model).temperature(0.3));
            }

            StringBuilder responseBuffer = new StringBuilder();
            return requestSpec
                .advisors(questionAnswerAdvisor, messageChatMemoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse()
                .doOnNext(cr -> {
                    if (cr.getResult() != null && cr.getResult().getOutput() != null) {
                        String text = cr.getResult().getOutput().getText();
                        if (text != null) responseBuffer.append(text);
                    }
                })
                .doOnComplete(() -> {
                    boolean isOpenAi = model != null &&
                        (model.startsWith("gpt-") || model.startsWith("o1") || model.startsWith("o3"));
                    if (isOpenAi) {
                        log.info("OpenAI 응답 완료 - 세션: {}, 모델: {}", sessionId, model);
                    } else {
                        log.info("Ollama 응답 완료 - 세션: {}, 모델: {}", sessionId, model);
                    }
                });
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    @Override
    public Flux<ChatResponse> streamSimpleResponse(String query, String model, String sessionId) {
        log.info("일반 스트리밍 - 세션: {}, 질문: {}", sessionId, query);

        try {
            var requestSpec = selectClient(model).prompt().user(query);
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
            return openAiChatClient.prompt()
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
