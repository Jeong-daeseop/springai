package com.krdevops.springai.chat.config.rag.transformers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EgovCompressionQueryTransformer {

    private final ChatMemory chatMemory;
    private final ChatClient ollamaChatClient;

    @Value("${rag.compression.model:qwen3:1.7b}")
    private String compressionModel;

    public EgovCompressionQueryTransformer(ChatMemory chatMemory,
                                           @Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
        this.chatMemory = chatMemory;
        this.ollamaChatClient = ollamaChatClient;
    }

    /**
     * 대화 히스토리를 참고해 후속 질문을 독립적인 질문으로 압축(재작성)한다.
     *
     * @param query     원본 질문
     * @param sessionId 세션 ID
     * @return 압축된 질문 (히스토리 없거나 압축 실패 시 원본 반환)
     */
    public String compress(String query, String sessionId) {
        log.info("쿼리 압축 시작 - 세션: {}, 질문: {}", sessionId, query);

        if (sessionId == null || sessionId.trim().isEmpty() || "default".equals(sessionId)) {
            return query;
        }

        if (isIncompleteQuery(query)) return query;

        List<Message> history;
        try {
            history = chatMemory.get(sessionId);
            if (history == null || history.isEmpty()) return query;
        } catch (Exception e) {
            log.warn("히스토리 조회 오류 - 세션: {}", sessionId, e);
            return query;
        }

        String historyText = history.stream()
            .map(m -> {
                String role = m instanceof UserMessage ? "사용자" : "어시스턴트";
                String content;
                if (m instanceof UserMessage um) content = um.getText();
                else if (m instanceof AssistantMessage am) content = am.getText();
                else content = "";
                return role + ": " + content;
            })
            .collect(Collectors.joining("\n"));

        String prompt = """
            You are a query rewriting assistant. Rewrite follow-up questions into standalone questions.

            STRICT RULES:
            - Output ONLY the rewritten question
            - Keep the SAME language as the original question
            - Replace pronouns with specific terms from history

            History:
            """ + historyText + """

            Follow-up Question:
            """ + query + """

            Rewritten Question:""";

        try {
            String compressed = ollamaChatClient.prompt()
                .user(prompt)
                .options(OllamaChatOptions.builder()
                    .model(compressionModel)
                    .temperature(0.0)
                    .numCtx(2048)
                    .numPredict(128)
                    .disableThinking())
                .call()
                .content();

            if (compressed == null || compressed.isBlank()) return query;

            if (compressed.contains("<think>")) {
                String[] parts = compressed.split("</think>");
                compressed = parts.length > 1 ? parts[1].trim() : query;
            }

            if (isLikelyAnswer(compressed)) {
                compressed = query;
            }

            log.info("압축 완료: '{}'", compressed);
            return compressed.trim();
        } catch (Exception e) {
            log.warn("쿼리 압축 오류 - 원본 쿼리 사용", e);
            return query;
        }
    }

    private boolean isLikelyAnswer(String text) {
        if (text == null) return false;
        if (text.contains("```") || text.contains("function") || text.contains("return ")) return true;
        return text.length() > 200;
    }

    private boolean isIncompleteQuery(String query) {
        if (query == null || query.trim().length() < 5) return true;
        return query.trim().split("\\s+").length < 2;
    }
}
