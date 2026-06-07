package com.krdevops.springai.chat.controller;

import com.krdevops.springai.chat.service.EgovChatSessionService;
import com.krdevops.springai.chat.service.EgovSessionAwareChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EgovOllamaChatController {

    private final EgovSessionAwareChatService egovSessionAwareChatService;
    private final EgovChatSessionService egovChatSessionService;

    @GetMapping(value = "/ai/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamRagResponse(
            @RequestParam(defaultValue = "질문을 입력하세요") String message,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String sessionId) {

        log.info("RAG 스트리밍 수신: {}, 세션: {}", message, sessionId);
        String resolvedSessionId = setupSession(sessionId, message);

        return egovSessionAwareChatService.streamRagResponse(message, model, resolvedSessionId)
            .mapNotNull(response -> {
                var result = response.getResult();
                if (result == null || result.getOutput() == null) return null;
                return result.getOutput().getText();
            })
            .filter(text -> text != null)
            .map(EgovOllamaChatController::encodeToken);
    }

    @GetMapping(value = "/ai/simple/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSimpleResponse(
            @RequestParam(defaultValue = "질문을 입력하세요") String message,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String sessionId) {

        log.info("일반 스트리밍 수신: {}, 세션: {}", message, sessionId);
        String resolvedSessionId = setupSession(sessionId, message);

        return egovSessionAwareChatService.streamSimpleResponse(message, model, resolvedSessionId)
            .mapNotNull(response -> {
                var result = response.getResult();
                if (result == null || result.getOutput() == null) return null;
                return result.getOutput().getText();
            })
            .filter(text -> text != null)
            .map(EgovOllamaChatController::encodeToken);
    }

    /** SSE는 data: 뒤 첫 공백을 자동 제거하므로 URL 인코딩해서 전송 */
    private static String encodeToken(String text) {
        return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String setupSession(String sessionId, String message) {
        if (sessionId != null && !sessionId.isEmpty() && egovChatSessionService.sessionExists(sessionId)) {
            List<Message> history = egovChatSessionService.getSessionMessages(sessionId);
            if (history.isEmpty()) {
                String title = egovChatSessionService.generateSessionTitle(message);
                egovChatSessionService.updateSessionTitle(sessionId, title);
            } else {
                egovChatSessionService.updateLastMessageTime(sessionId);
            }
            return sessionId;
        }
        return "default";
    }
}
