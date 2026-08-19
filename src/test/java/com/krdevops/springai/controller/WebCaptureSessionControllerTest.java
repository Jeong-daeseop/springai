package com.krdevops.springai.controller;

import com.krdevops.springai.model.capture.WebCaptureSessionRequest;
import com.krdevops.springai.model.capture.WebCaptureSessionResponse;
import com.krdevops.springai.service.WebCaptureOrchestrationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 04번 문서 R6(§9): 세션 발급 REST 진입점이 오케스트레이션 서비스에만 위임하는지 검증한다. */
class WebCaptureSessionControllerTest {

    @Test
    void createSessionDelegatesToOrchestrationService() {
        WebCaptureOrchestrationService orchestrationService = mock(WebCaptureOrchestrationService.class);
        WebCaptureSessionController controller = new WebCaptureSessionController(orchestrationService);
        WebCaptureSessionRequest request = new WebCaptureSessionRequest(
                "http://localhost:8080/login.do", null, "#username", "e2e-user",
                "#password", "e2e-pass", "#submit", "#dashboard", 15000);
        WebCaptureSessionResponse expected = new WebCaptureSessionResponse(
                "11111111-1111-4111-8111-111111111111", "2026-08-19T00:00:00Z");
        when(orchestrationService.createSession(any())).thenReturn(expected);

        WebCaptureSessionResponse result = controller.createSession(request);

        assertThat(result).isEqualTo(expected);
        verify(orchestrationService).createSession(request);
    }
}
