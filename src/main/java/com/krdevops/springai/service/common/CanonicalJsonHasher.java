package com.krdevops.springai.service.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * I-1: Canonical JSON Hash 함수.
 *
 * 입력 객체를 JSON으로 직렬화하고, 다음 정규화를 적용한 후 SHA-256 해시를 생성합니다:
 * 1. 객체 필드 알파벳순 정렬
 * 2. 배열 순서 유지
 * 3. 스페이스 제거
 *
 * 같은 데이터는 필드 순서와 무관하게 동일한 hash를 생성합니다.
 * 버전, snapshot, profile 등 메타데이터 변경 시 hash가 변경되어 멱등성을 보장합니다.
 */
@Component
public class CanonicalJsonHasher {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String HASH_PREFIX = "sha256:";
    private static final int HASH_HEX_LENGTH = 64;

    /**
     * 객체의 canonical JSON hash를 계산합니다.
     * @param object 직렬화할 객체
     * @return "sha256:..." 형식의 16진수 문자열 (소문자)
     * @throws IllegalArgumentException 객체가 null이거나 직렬화 실패 시
     */
    public static String computeCanonicalHash(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Hash 대상 객체는 null이 될 수 없습니다");
        }

        try {
            // 1. 객체 → Map으로 변환 (객체는 Map으로 직렬화됨)
            String json = objectMapper.writeValueAsString(object);
            JsonNode node = objectMapper.readTree(json);

            // 2. JSON 노드를 정규화 (필드 알파벳순)
            JsonNode normalized = normalizeJsonNode(node);

            // 3. 정규화된 JSON으로부터 canonical 문자열 생성
            String canonical = objectMapper.writeValueAsString(normalized);

            // 4. SHA-256 계산
            byte[] hash = computeSha256(canonical);

            // 5. 16진수 문자열 반환
            return HASH_PREFIX + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalArgumentException("Hash 계산 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 노드를 정규화합니다 (필드 알파벳순).
     * 배열은 순서 유지, 객체는 키 정렬.
     */
    private static JsonNode normalizeJsonNode(JsonNode node) {
        if (node.isObject()) {
            // 객체의 필드를 TreeMap으로 수집하여 자동 정렬
            TreeMap<String, JsonNode> sortedFields = new TreeMap<>();
            node.fieldNames().forEachRemaining(key ->
                sortedFields.put(key, normalizeJsonNode(node.get(key)))
            );

            // TreeMap을 ObjectNode로 변환
            ObjectNode result = objectMapper.createObjectNode();
            sortedFields.forEach((key, value) -> result.set(key, value));

            return result;
        } else if (node.isArray()) {
            // 배열의 각 요소를 정규화 (순서 유지)
            var array = objectMapper.createArrayNode();
            for (JsonNode element : node) {
                array.add(normalizeJsonNode(element));
            }
            return array;
        } else {
            // 원시값은 그대로
            return node;
        }
    }

    /**
     * SHA-256 해시 계산.
     */
    private static byte[] computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(content.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    /**
     * Hash 형식 검증 (테스트 용도).
     */
    public static boolean isValidHashFormat(String hash) {
        return hash != null && hash.matches("^sha256:[a-f0-9]{" + HASH_HEX_LENGTH + "}$");
    }

    /**
     * Hash 값 추출 (정보성).
     */
    public static String extractHashValue(String hash) {
        if (isValidHashFormat(hash)) {
            return hash.substring(HASH_PREFIX.length());
        }
        throw new IllegalArgumentException("Invalid hash format: " + hash);
    }
}
