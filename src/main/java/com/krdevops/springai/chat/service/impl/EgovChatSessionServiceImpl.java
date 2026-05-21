package com.krdevops.springai.chat.service.impl;

import com.krdevops.springai.chat.dto.ChatSession;
import com.krdevops.springai.chat.repository.EgovRedisChatMemoryRepository;
import com.krdevops.springai.chat.service.EgovChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EgovChatSessionServiceImpl implements EgovChatSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatMemory chatMemory;
    private final EgovRedisChatMemoryRepository redisChatMemoryRepository;

    private static final String SESSIONS_LIST_KEY = "chat:sessions:list";
    private static final String SESSION_INFO_KEY_PREFIX = "chat:session:";
    private static final String SESSION_INFO_KEY_SUFFIX = ":info";

    @Override
    public ChatSession createNewSession() {
        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX;

        Map<String, Object> info = new HashMap<>();
        info.put("title", "새 채팅");
        info.put("createdAt", LocalDateTime.now().toString());
        info.put("lastMessageAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(sessionKey, info);
        redisTemplate.opsForSet().add(SESSIONS_LIST_KEY, sessionId);

        log.debug("새 채팅 세션 생성: {}", sessionId);
        return new ChatSession(sessionId, "새 채팅", LocalDateTime.now());
    }

    @Override
    public List<ChatSession> getAllSessions() {
        Set<Object> sessionIds = redisTemplate.opsForSet().members(SESSIONS_LIST_KEY);
        if (sessionIds == null || sessionIds.isEmpty()) return new ArrayList<>();

        return sessionIds.stream().map(id -> {
            String key = SESSION_INFO_KEY_PREFIX + id + SESSION_INFO_KEY_SUFFIX;
            Map<Object, Object> info = redisTemplate.opsForHash().entries(key);
            if (info.isEmpty()) return null;
            try {
                return new ChatSession(
                    (String) id,
                    (String) info.get("title"),
                    LocalDateTime.parse((String) info.get("createdAt")),
                    LocalDateTime.parse((String) info.get("lastMessageAt"))
                );
            } catch (Exception e) {
                log.warn("세션 정보 파싱 실패: {}", id, e);
                return null;
            }
        }).filter(Objects::nonNull)
          .sorted((a, b) -> b.getLastMessageAt().compareTo(a.getLastMessageAt()))
          .collect(Collectors.toList());
    }

    @Override
    public List<Message> getSessionMessages(String sessionId) {
        return chatMemory.get(sessionId).stream()
            .filter(m -> m instanceof UserMessage || m instanceof AssistantMessage)
            .collect(Collectors.toList());
    }

    @Override
    public void updateSessionTitle(String sessionId, String title) {
        String key = SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX;
        redisTemplate.opsForHash().put(key, "title", title);
        redisTemplate.opsForHash().put(key, "lastMessageAt", LocalDateTime.now().toString());
    }

    @Override
    public void updateLastMessageTime(String sessionId) {
        String key = SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX;
        redisTemplate.opsForHash().put(key, "lastMessageAt", LocalDateTime.now().toString());
    }

    @Override
    public String generateSessionTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) return "새 채팅";
        String title = firstMessage.trim();
        return title.length() > 30 ? title.substring(0, 27) + "..." : title;
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(SESSIONS_LIST_KEY, sessionId));
    }

    @Override
    public void deleteSession(String sessionId) {
        redisTemplate.opsForSet().remove(SESSIONS_LIST_KEY, sessionId);
        redisTemplate.delete(SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX);
        redisChatMemoryRepository.deleteByConversationId(sessionId);
        log.debug("세션 삭제: {}", sessionId);
    }
}
