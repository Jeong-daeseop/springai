package com.krdevops.springai.controller;

import com.krdevops.springai.service.figma.FigmaFileAllowlistValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** R6-041: FigmaAllowlistException은 이전에 별도 핸들러가 없어 일반 500으로 새어 나갔다. */
    @Test
    void allowlistViolationReturnsForbiddenWithCode() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/figma/orchestration/design");
        FigmaFileAllowlistValidator.FigmaAllowlistException exception =
                new FigmaFileAllowlistValidator.FigmaAllowlistException(
                        "FIGMA_FILE_NOT_ALLOWED", "Figma 파일 접근이 허용되지 않습니다: unknown-file");

        ResponseEntity<FigmaApiError> response = handler.handleFigmaAllowlistViolation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("FIGMA_FILE_NOT_ALLOWED");
        assertThat(response.getBody().path()).isEqualTo("/api/figma/orchestration/design");
    }
}
