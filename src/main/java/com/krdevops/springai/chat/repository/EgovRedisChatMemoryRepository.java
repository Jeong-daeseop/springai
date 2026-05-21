package com.krdevops.springai.chat.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EgovRedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String CHAT_MEMORY_KEY_PREFIX = "chat:memory:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public EgovRedisChatMemoryRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            String key = CHAT_MEMORY_KEY_PREFIX + conversationId;
            List<Map<String, Object>> simple = messages.stream()
                .map(this::messageToMap).collect(Collectors.toList());
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(simple));
            log.debug("Redis 채팅 메모리 저장: {} - {}개", conversationId, messages.size());
        } catch (Exception e) {
            log.error("채팅 메모리 저장 실패: {}", conversationId, e);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            String key = CHAT_MEMORY_KEY_PREFIX + conversationId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return new ArrayList<>();

            List<Map<String, Object>> maps = objectMapper.readValue(
                value.toString(), new TypeReference<>() {});
            return maps.stream().map(this::mapToMessage)
                .filter(Objects::nonNull).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("채팅 메모리 조회 실패: {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(CHAT_MEMORY_KEY_PREFIX + conversationId);
        log.debug("Redis 채팅 메모리 삭제: {}", conversationId);
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(CHAT_MEMORY_KEY_PREFIX + "*");
        if (keys == null) return new ArrayList<>();
        return keys.stream()
            .map(k -> k.substring(CHAT_MEMORY_KEY_PREFIX.length()))
            .collect(Collectors.toList());
    }

    private Map<String, Object> messageToMap(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("messageType", message.getMessageType().name());
        String content;
        if (message instanceof UserMessage m) {
            content = m.getText();
        } else if (message instanceof AssistantMessage m) {
            content = m.getText();
        } else if (message instanceof SystemMessage m) {
            content = m.getText();
        } else {
            try {
                Method getText = message.getClass().getMethod("getText");
                content = (String) getText.invoke(message);
            } catch (Exception e) {
                content = "";
            }
        }
        map.put("content", content);
        return map;
    }

    private Message mapToMessage(Map<String, Object> map) {
        try {
            String type = (String) map.get("messageType");
            String content = (String) map.get("content");
            if (type == null || content == null) return null;
            return switch (type) {
                case "USER" -> new UserMessage(content);
                case "ASSISTANT" -> new AssistantMessage(content);
                case "SYSTEM" -> new SystemMessage(content);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Map→Message 변환 실패: {}", map, e);
            return null;
        }
    }
}
