package com.krdevops.springai.config.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class StructuredJsonLogLayout extends LayoutBase<ILoggingEvent> {

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        fields.put("level", event.getLevel().levelStr);
        fields.put("event", value(event, "eventName", "application_log"));
        fields.put("logger", event.getLoggerName());
        fields.put("thread", event.getThreadName());
        fields.put("message", SensitiveLogRedactor.redact(event.getFormattedMessage()));
        copyMdc(event, fields, "correlationId", "operationId", "artifactId", "actorId", "channel");
        if (event.getThrowableProxy() != null) {
            fields.put("exceptionType", event.getThrowableProxy().getClassName());
        }
        return toJson(fields) + System.lineSeparator();
    }

    private String value(ILoggingEvent event, String key, String fallback) {
        String value = event.getMDCPropertyMap().get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private void copyMdc(ILoggingEvent event, Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = event.getMDCPropertyMap().get(key);
            if (value != null && !value.isBlank()) fields.put(key, value);
        }
    }

    private String toJson(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (var entry : fields.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }
}

