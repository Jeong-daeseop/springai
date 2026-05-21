package com.krdevops.springai.chat.controller;

import com.krdevops.springai.chat.dto.ChatMessageDto;
import com.krdevops.springai.chat.dto.ChatSession;
import com.krdevops.springai.chat.service.EgovChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EgovChatSessionController {

    private final EgovChatSessionService egovChatSessionService;

    @PostMapping
    public ResponseEntity<ChatSession> createNewSession() {
        try {
            ChatSession session = egovChatSessionService.createNewSession();
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            log.error("세션 생성 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ChatSession>> getAllSessions() {
        try {
            return ResponseEntity.ok(egovChatSessionService.getAllSessions());
        } catch (Exception e) {
            log.error("세션 목록 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getSessionMessages(@PathVariable String sessionId) {
        try {
            if (!egovChatSessionService.sessionExists(sessionId)) {
                return ResponseEntity.notFound().build();
            }
            List<Message> messages = egovChatSessionService.getSessionMessages(sessionId);
            List<ChatMessageDto> dtos = messages.stream().map(m -> {
                String content;
                if (m instanceof UserMessage u) content = u.getText();
                else if (m instanceof AssistantMessage a) content = a.getText();
                else if (m instanceof SystemMessage s) content = s.getText();
                else {
                    try {
                        Method getText = m.getClass().getMethod("getText");
                        content = (String) getText.invoke(m);
                    } catch (Exception e) {
                        content = "";
                    }
                }
                return new ChatMessageDto(m.getMessageType().name(), content);
            }).collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("세션 메시지 조회 실패: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{sessionId}/title")
    public ResponseEntity<Void> updateSessionTitle(@PathVariable String sessionId,
                                                    @RequestBody UpdateTitleRequest request) {
        try {
            if (!egovChatSessionService.sessionExists(sessionId)) return ResponseEntity.notFound().build();
            egovChatSessionService.updateSessionTitle(sessionId, request.getTitle());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("세션 제목 업데이트 실패: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        try {
            if (!egovChatSessionService.sessionExists(sessionId)) return ResponseEntity.notFound().build();
            egovChatSessionService.deleteSession(sessionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("세션 삭제 실패: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class UpdateTitleRequest {
        private String title;
        public UpdateTitleRequest() {}
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
