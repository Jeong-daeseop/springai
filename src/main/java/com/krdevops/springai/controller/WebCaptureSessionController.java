package com.krdevops.springai.controller;

import com.krdevops.springai.model.capture.WebCaptureSessionRequest;
import com.krdevops.springai.model.capture.WebCaptureSessionResponse;
import com.krdevops.springai.service.WebCaptureOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 04번 문서 R6(§9): 인증 세션(로그인) 발급 전용 REST 진입점. 원문 username/password가 필요해
 * MCP(LLM) 경로에는 절대 노출하지 않는다 — 기존 {@code /api/**} X-API-Key 인증을 통과한
 * 운영자가 직접 호출하고, 발급된 opaque {@code sessionId}(storageStateRef)만
 * {@code captureWebPage} MCP Tool로 넘겨 인증 캡처를 이어간다.
 */
@RestController
@RequestMapping("/api/web-capture/sessions")
@RequiredArgsConstructor
public class WebCaptureSessionController {

    private final WebCaptureOrchestrationService orchestrationService;

    @PostMapping
    public WebCaptureSessionResponse createSession(@RequestBody WebCaptureSessionRequest request) {
        return orchestrationService.createSession(request);
    }
}
