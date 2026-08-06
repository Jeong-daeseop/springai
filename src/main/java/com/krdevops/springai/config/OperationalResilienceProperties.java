package com.krdevops.springai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.resilience")
public class OperationalResilienceProperties {

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Bulkhead bulkhead = new Bulkhead();
    private final Timeout timeout = new Timeout();

    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public Bulkhead getBulkhead() { return bulkhead; }
    public Timeout getTimeout() { return timeout; }

    public static class CircuitBreaker {
        private int failureThreshold = 5;
        private Duration openDuration = Duration.ofSeconds(30);
        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int value) { failureThreshold = positive(value, "failure-threshold"); }
        public Duration getOpenDuration() { return openDuration; }
        public void setOpenDuration(Duration value) { openDuration = positive(value, "open-duration"); }
    }

    public static class Bulkhead {
        private int captureConcurrency = 4;
        private int indexingConcurrency = 2;
        private int chatConcurrency = 8;
        public int getCaptureConcurrency() { return captureConcurrency; }
        public void setCaptureConcurrency(int value) { captureConcurrency = positive(value, "capture-concurrency"); }
        public int getIndexingConcurrency() { return indexingConcurrency; }
        public void setIndexingConcurrency(int value) { indexingConcurrency = positive(value, "indexing-concurrency"); }
        public int getChatConcurrency() { return chatConcurrency; }
        public void setChatConcurrency(int value) { chatConcurrency = positive(value, "chat-concurrency"); }
    }

    public static class Timeout {
        private Duration mysqlConnect = Duration.ofSeconds(30);
        private Duration redisConnect = Duration.ofSeconds(3);
        private Duration redisRead = Duration.ofSeconds(5);
        private Duration openaiConnect = Duration.ofSeconds(5);
        private Duration openaiFirstToken = Duration.ofSeconds(30);
        private Duration openaiIdle = Duration.ofSeconds(45);
        private Duration ollamaConnect = Duration.ofSeconds(3);
        private Duration ollamaFirstToken = Duration.ofSeconds(60);
        private Duration ollamaIdle = Duration.ofSeconds(60);
        private Duration figmaConnect = Duration.ofSeconds(5);
        private Duration figmaTotal = Duration.ofSeconds(30);
        private Duration extractorConnect = Duration.ofSeconds(3);
        private Duration extractorTotal = Duration.ofSeconds(60);
        private Duration sseTotal = Duration.ofMinutes(5);

        public Duration getMysqlConnect() { return mysqlConnect; }
        public void setMysqlConnect(Duration v) { mysqlConnect = positive(v, "mysql-connect"); }
        public Duration getRedisConnect() { return redisConnect; }
        public void setRedisConnect(Duration v) { redisConnect = positive(v, "redis-connect"); }
        public Duration getRedisRead() { return redisRead; }
        public void setRedisRead(Duration v) { redisRead = positive(v, "redis-read"); }
        public Duration getOpenaiConnect() { return openaiConnect; }
        public void setOpenaiConnect(Duration v) { openaiConnect = positive(v, "openai-connect"); }
        public Duration getOpenaiFirstToken() { return openaiFirstToken; }
        public void setOpenaiFirstToken(Duration v) { openaiFirstToken = positive(v, "openai-first-token"); }
        public Duration getOpenaiIdle() { return openaiIdle; }
        public void setOpenaiIdle(Duration v) { openaiIdle = positive(v, "openai-idle"); }
        public Duration getOllamaConnect() { return ollamaConnect; }
        public void setOllamaConnect(Duration v) { ollamaConnect = positive(v, "ollama-connect"); }
        public Duration getOllamaFirstToken() { return ollamaFirstToken; }
        public void setOllamaFirstToken(Duration v) { ollamaFirstToken = positive(v, "ollama-first-token"); }
        public Duration getOllamaIdle() { return ollamaIdle; }
        public void setOllamaIdle(Duration v) { ollamaIdle = positive(v, "ollama-idle"); }
        public Duration getFigmaConnect() { return figmaConnect; }
        public void setFigmaConnect(Duration v) { figmaConnect = positive(v, "figma-connect"); }
        public Duration getFigmaTotal() { return figmaTotal; }
        public void setFigmaTotal(Duration v) { figmaTotal = positive(v, "figma-total"); }
        public Duration getExtractorConnect() { return extractorConnect; }
        public void setExtractorConnect(Duration v) { extractorConnect = positive(v, "extractor-connect"); }
        public Duration getExtractorTotal() { return extractorTotal; }
        public void setExtractorTotal(Duration v) { extractorTotal = positive(v, "extractor-total"); }
        public Duration getSseTotal() { return sseTotal; }
        public void setSseTotal(Duration v) { sseTotal = positive(v, "sse-total"); }
    }

    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException("app.resilience." + name + "는 1 이상이어야 합니다.");
        return value;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("app.resilience.timeout." + name + "는 양수여야 합니다.");
        }
        return value;
    }
}

