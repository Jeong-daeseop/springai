package com.krdevops.springai.config.observability;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

public final class ObservabilityContextHolder {

    private static final ThreadLocal<ObservabilityContext> CURRENT = new ThreadLocal<>();
    private static final String[] MDC_KEYS = {
            "correlationId", "operationId", "artifactId", "actorId", "channel", "eventName"
    };

    private ObservabilityContextHolder() { }

    public static ObservabilityContext current() {
        ObservabilityContext context = CURRENT.get();
        return context == null
                ? new ObservabilityContext(UUID.randomUUID().toString(), null, null, "system", "INTERNAL")
                : context;
    }

    public static Scope open(ObservabilityContext context) {
        ObservabilityContext previous = CURRENT.get();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        CURRENT.set(sanitize(context));
        applyMdc(CURRENT.get());
        return () -> {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            MDC.clear();
            if (previousMdc != null) MDC.setContextMap(previousMdc);
        };
    }

    public static Scope openOperation(String operationId) {
        return open(current().withOperation(identifier(operationId)));
    }

    public static Scope openArtifact(String artifactId) {
        return open(current().withArtifact(identifier(artifactId)));
    }

    public static void setActor(String actorId) {
        ObservabilityContext updated = current().withActor(identifier(actorId));
        CURRENT.set(updated);
        applyMdc(updated);
    }

    public static void setEventName(String eventName) {
        put("eventName", identifier(eventName));
    }

    public static Scope openEvent(String eventName) {
        String previous = MDC.get("eventName");
        setEventName(eventName);
        return () -> put("eventName", previous);
    }

    public static Runnable wrap(Runnable task) {
        ObservabilityContext captured = current();
        return () -> {
            try (Scope ignored = open(captured)) {
                task.run();
            }
        };
    }

    private static ObservabilityContext sanitize(ObservabilityContext value) {
        if (value == null) return current();
        return new ObservabilityContext(identifier(value.correlationId()), identifier(value.operationId()),
                identifier(value.artifactId()), identifier(value.actorId()), identifier(value.channel()));
    }

    public static String identifier(String value) {
        if (value == null || value.isBlank()) return null;
        String sanitized = value.replaceAll("[^A-Za-z0-9._:@-]", "_");
        return sanitized.substring(0, Math.min(128, sanitized.length()));
    }

    private static void applyMdc(ObservabilityContext context) {
        put("correlationId", context.correlationId());
        put("operationId", context.operationId());
        put("artifactId", context.artifactId());
        put("actorId", context.actorId());
        put("channel", context.channel());
    }

    private static void put(String key, String value) {
        if (value == null) MDC.remove(key); else MDC.put(key, value);
    }

    public static void clear() {
        CURRENT.remove();
        for (String key : MDC_KEYS) MDC.remove(key);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
