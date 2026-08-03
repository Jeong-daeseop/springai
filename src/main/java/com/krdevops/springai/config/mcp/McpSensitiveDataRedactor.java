package com.krdevops.springai.config.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ARCH-0115: MCP 응답·오류의 secret/token/API key/fileKey를 공통 마스킹한다. */
@Component
public class McpSensitiveDataRedactor {
    private static final String MASK = "[REDACTED]";
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)([\\\"]?(?:api[-_]?key|access[-_]?token|token|secret|authorization|password|fileKey)[\\\"]?\\s*[:=]\\s*[\\\"]?)([^\\\"\\s,}]+)");

    private final List<String> configuredSecrets;

    @Autowired
    public McpSensitiveDataRedactor(
            McpSecurityProperties properties,
            @Value("${app.figma.mcp-shared-secret:}") String figmaSecret,
            @Value("${app.thymeleaf.mcp-shared-secret:${app.figma.mcp-shared-secret:}}") String thymeleafSecret) {
        this(List.of(
                normalize(properties.getSharedToken()),
                normalize(properties.getPreviousSharedToken()),
                normalize(figmaSecret), normalize(thymeleafSecret)));
    }

    McpSensitiveDataRedactor(List<String> configuredSecrets) {
        this.configuredSecrets = configuredSecrets.stream()
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    static McpSensitiveDataRedactor noop() {
        return new McpSensitiveDataRedactor(List.of());
    }

    public String redact(String value) {
        if (value == null || value.isEmpty()) return value;
        String redacted = value;
        for (String secret : configuredSecrets) {
            redacted = redacted.replace(secret, MASK);
        }
        Matcher matcher = NAMED_SECRET.matcher(redacted);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + MASK));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
