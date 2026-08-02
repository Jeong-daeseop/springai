package com.krdevops.springai.service.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;

/**
 * I-0 content hash 정규화 규칙: 임의 객체를 key 정렬된 canonical JSON으로 직렬화한 뒤
 * SHA-256 hex를 계산한다. 같은 값이면 필드 순서·직렬화 시점과 무관하게 항상 같은 해시를
 * 만들어 Operation 멱등 저장(request hash 기반)의 기준으로 쓸 수 있다.
 */
@Component
public class OperationHashFactory {

    private final ObjectMapper objectMapper;

    public OperationHashFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public String canonicalHash(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value는 필수입니다.");
        }
        JsonNode node = objectMapper.valueToTree(value);
        return sha256Hex(canonicalize(node));
    }

    /** I-2B의 원본 파일 hash·project fingerprint처럼 임의 byte[]를 그대로 해시해야 할 때 사용한다. */
    public String sha256Hex(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private String canonicalize(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (var entry : sorted.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(escape(entry.getKey())).append("\":")
                        .append(canonicalize(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (JsonNode item : node) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(canonicalize(item));
            }
            return builder.append(']').toString();
        }
        return node.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sha256Hex(String canonicalJson) {
        return sha256Hex(canonicalJson.getBytes(StandardCharsets.UTF_8));
    }
}
