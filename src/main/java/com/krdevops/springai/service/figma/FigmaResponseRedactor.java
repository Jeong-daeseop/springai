package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

/**
 * Figma MCP 응답 민감정보 정제.
 * 2-A6 Redaction 정책 구현
 *
 * Token, API Key, 노드 ID, 파일 키 등을 마스킹
 */
@Service
public class FigmaResponseRedactor {

    /**
     * 응답 문자열에서 민감정보를 마스킹한다.
     */
    public String redact(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }

        String redacted = response;

        // Figma 접근 토큰 마스킹
        redacted = redactPattern(redacted, "(?i)(figma[_-]?token|access[_-]?token)\\s*[:=]\\s*[^,\\s]+", "***REDACTED***");

        // API 키 마스킹
        redacted = redactPattern(redacted, "(?i)(api[_-]?key|secret)\\s*[:=]\\s*[^,\\s]+", "***REDACTED***");

        // URL의 쿼리 파라미터 마스킹
        redacted = redactPattern(redacted, "([?&])(token|key|access_token)=[^&\\s]+", "$1$2=***REDACTED***");

        // 이메일 마스킹
        redacted = redactPattern(redacted, "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+)", "***@***");

        return redacted;
    }

    /**
     * 특정 필드를 마스킹한다.
     */
    public String redactField(String value, String fieldName) {
        if (value == null || fieldName == null) {
            return value;
        }

        if (isSensitiveField(fieldName)) {
            return "***REDACTED***";
        }

        return value;
    }

    /**
     * 민감한 필드인지 판정한다.
     */
    public boolean isSensitiveField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("token") ||
               lower.contains("key") ||
               lower.contains("secret") ||
               lower.contains("password") ||
               lower.contains("credential") ||
               lower.contains("access_token") ||
               lower.contains("api_key");
    }

    private String redactPattern(String text, String pattern, String replacement) {
        try {
            return text.replaceAll(pattern, replacement);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 로그 메시지 정제.
     */
    public String redactLog(String message) {
        if (message == null) return null;
        return redact(message);
    }

    /**
     * Exception 메시지 정제.
     */
    public String redactException(Exception e) {
        if (e == null) return null;
        return redact(e.getMessage());
    }
}
