package com.krdevops.springai.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EgovRagConfig {

    @Value("${rag.similarity.threshold:0.30}")
    private double similarityThreshold;

    @Value("${rag.top-k:3}")
    private int topK;

    /** Spring AI 자동 구성의 ChatModel 중복 후보 해소 — Ollama를 기본 ChatModel로 지정 */
    @Bean
    @Primary
    public ChatModel primaryChatModel(OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }

    /** 동적 라우팅용 OpenAI 전용 ChatClient */
    @Bean("openAiChatClient")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    /** 동적 라우팅용 Ollama 전용 ChatClient */
    @Bean("ollamaChatClient")
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel).build();
    }

    /**
     * 디자인 비전 분석 전용 OpenAiChatModel.
     * 오토컨피그 openAiChatModel(toolCallingManager 필요)을 재사용하면
     * openAiChatModel → toolCallingManager → allToolCallbacks → designReferenceTool
     * → openAiVisionAnalysisClient → openAiChatModel 순환 참조가 발생하므로,
     * builder()로 직접 생성해 ToolCallingManager 의존 체인에서 분리한다.
     */
    @Bean
    public OpenAiChatModel visionOpenAiChatModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${app.design-vision.openai-model:gpt-4o-mini}") String visionModel) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(apiKey)
                .model(visionModel)
                .build();
        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }

    /** 디자인 비전 분석 전용 ChatClient */
    @Bean("openAiVisionChatClient")
    public ChatClient openAiVisionChatClient(
            @Qualifier("visionOpenAiChatModel") OpenAiChatModel visionOpenAiChatModel) {
        return ChatClient.builder(visionOpenAiChatModel).build();
    }

    /**
     * 디자인 비전 분석 전용 OllamaChatModel.
     * 오토컨피그 ollamaChatModel(toolCallingManager 필요)을 재사용하면
     * ollamaChatModel → toolCallingManager → allToolCallbacks → designReferenceTool
     * → ollamaVisionAnalysisClient → ollamaChatModel 순환 참조가 발생하므로,
     * builder()로 직접 생성해 ToolCallingManager 의존 체인에서 분리한다.
     */
    @Bean
    public OllamaChatModel visionOllamaChatModel(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.design-vision.ollama-model:qwen2.5vl:7b}") String visionModel) {
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(visionModel)
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(options)
                .build();
    }

    /** 디자인 비전 분석 전용 ChatClient (Ollama) */
    @Bean("ollamaVisionChatClient")
    public ChatClient ollamaVisionChatClient(
            @Qualifier("visionOllamaChatModel") OllamaChatModel visionOllamaChatModel) {
        return ChatClient.builder(visionOllamaChatModel).build();
    }

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression("type == 'document'")  // 교재/docs 문서만 검색 (source_code, history 제외)
                        .build())
                .build();
    }
}
