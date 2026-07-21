package com.krdevops.springai.model.capture;

public record ReadinessSpec(String readySelector, String hiddenSelector, int timeoutMillis) {
    public ReadinessSpec {
        timeoutMillis = timeoutMillis == 0 ? 30000 : timeoutMillis;
        if (timeoutMillis < 1000 || timeoutMillis > 120000) {
            throw new IllegalArgumentException("readiness timeout은 1~120초여야 합니다.");
        }
    }
}
