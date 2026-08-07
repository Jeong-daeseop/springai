package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;

/** runner를 직접 지정하는 생성자가 package-private이라, 다른 패키지의 테스트를 위해 여기서만 연다. */
public final class TestBrowserGateExecutors {

    private TestBrowserGateExecutors() { }

    public static BrowserValidationGateExecutor withRunner(
            ObjectMapper objectMapper, Path runner, Duration timeout) {
        return new BrowserValidationGateExecutor(objectMapper, "node", runner, timeout);
    }
}
