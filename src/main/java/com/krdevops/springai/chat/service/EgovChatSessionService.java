package com.krdevops.springai.chat.service;

import com.krdevops.springai.chat.dto.ChatSession;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface EgovChatSessionService {
    ChatSession createNewSession();
    List<ChatSession> getAllSessions();
    List<Message> getSessionMessages(String sessionId);
    void updateSessionTitle(String sessionId, String title);
    void updateLastMessageTime(String sessionId);
    String generateSessionTitle(String firstMessage);
    boolean sessionExists(String sessionId);
    void deleteSession(String sessionId);
}
