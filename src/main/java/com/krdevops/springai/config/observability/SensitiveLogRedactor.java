package com.krdevops.springai.config.observability;

import java.util.regex.Pattern;

public final class SensitiveLogRedactor {

    private static final int MAX_MESSAGE_LENGTH = 4_096;
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[-_]?key|authorization|file[-_]?content|source[-_]?content|request[-_]?body|response[-_]?body)(\\s*[:=]\\s*)([^\\s,;}{]+)");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\\\"(?:password|passwd|secret|token|api[-_]?key|authorization|file[-_]?content|source[-_]?content|request[-_]?body|response[-_]?body)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");

    private SensitiveLogRedactor() { }

    public static String redact(String value) {
        if (value == null) return "";
        String redacted = BEARER.matcher(value).replaceAll("Bearer [REDACTED]");
        redacted = KEY_VALUE.matcher(redacted).replaceAll("$1$2[REDACTED]");
        redacted = JSON_SECRET.matcher(redacted).replaceAll("$1[REDACTED]$2");
        if (redacted.length() > MAX_MESSAGE_LENGTH) {
            redacted = redacted.substring(0, MAX_MESSAGE_LENGTH) + "...[TRUNCATED]";
        }
        return redacted;
    }
}
