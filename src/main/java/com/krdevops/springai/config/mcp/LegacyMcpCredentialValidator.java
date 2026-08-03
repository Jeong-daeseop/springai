package com.krdevops.springai.config.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Clock;
import java.time.Instant;

/** ARCH-0111/0112: COMPATIBILITY 모드에서만 기존 Tool 인자 secret을 audit 가능한 경로로 허용한다. */
@Component
public class LegacyMcpCredentialValidator {
    private static final List<String> LEGACY_FIELDS = List.of("figmaMcpSecret", "sharedSecret");

    private final ObjectMapper objectMapper;
    private final List<String> configuredSecrets;
    private final Clock clock;
    private final Instant validUntil;

    @Autowired
    public LegacyMcpCredentialValidator(
            ObjectMapper objectMapper,
            McpSecurityProperties properties,
            @Value("${app.figma.mcp-shared-secret:}") String figmaSecret,
            @Value("${app.thymeleaf.mcp-shared-secret:${app.figma.mcp-shared-secret:}}") String thymeleafSecret) {
        this(objectMapper, List.of(normalize(figmaSecret), normalize(thymeleafSecret)),
                Clock.systemUTC(), properties.getLegacyCredentialValidUntil());
    }

    LegacyMcpCredentialValidator(ObjectMapper objectMapper, List<String> configuredSecrets,
                                 Clock clock, Instant validUntil) {
        this.objectMapper = objectMapper;
        this.configuredSecrets = configuredSecrets.stream().filter(value -> !value.isBlank()).distinct().toList();
        this.clock = clock;
        this.validUntil = validUntil;
    }

    static LegacyMcpCredentialValidator disabled() {
        return new LegacyMcpCredentialValidator(new ObjectMapper(), List.of(), Clock.systemUTC(), null);
    }

    public boolean isValid(String toolInput) {
        if (toolInput == null || configuredSecrets.isEmpty()
                || validUntil == null || !clock.instant().isBefore(validUntil)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(toolInput);
            for (String field : LEGACY_FIELDS) {
                JsonNode candidate = root.get(field);
                if (candidate != null && candidate.isTextual()) {
                    for (String expected : configuredSecrets) {
                        if (McpCredentialValidator.constantTimeEquals(expected, candidate.textValue())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 파싱 불가능한 입력은 인증 실패로 취급한다. 입력 원문은 로그로 남기지 않는다.
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
