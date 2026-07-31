package com.krdevops.springai.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 전역 예외 핸들러 — 컨트롤러에서 처리되지 않은 예외를 통일된 에러 응답으로 변환한다.
 *
 * 응답 형식: {"error": "메시지"} + 적절한 HTTP 상태 코드
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 검증 실패 → 400 Bad Request */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /** @Pattern 등 @Validated path/request param 위반 → 400 Bad Request */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /** Figma/Design System 요청 계약 위반 → 코드가 포함된 400 표준 오류. */
    @ExceptionHandler(FigmaRequestException.class)
    public ResponseEntity<FigmaApiError> handleFigmaRequest(
            FigmaRequestException ex,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(new FigmaApiError(
                ex.code(), ex.getMessage(), request.getRequestURI(), java.time.OffsetDateTime.now()));
    }

    /** Figma/Design System 리소스 없음 → 코드가 포함된 404 표준 오류. */
    @ExceptionHandler(FigmaResourceNotFoundException.class)
    public ResponseEntity<FigmaApiError> handleFigmaNotFound(
            FigmaResourceNotFoundException ex,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new FigmaApiError(
                ex.code(), ex.getMessage(), request.getRequestURI(), java.time.OffsetDateTime.now()));
    }

    /** 잘못된 인수 → 400 Bad Request */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /** 보안 예외 (Path Traversal 등) → 403 Forbidden */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException ex) {
        log.warn("보안 예외 차단: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    /** 존재하지 않는 경로 요청 → 404 (ERROR 로깅 없음) */
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource(NoResourceFoundException ex, HttpServletResponse response) throws IOException {
        log.debug("존재하지 않는 리소스 요청: {}", ex.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Not Found\"}");
    }

    /** SSE 클라이언트 연결 끊김 — 정상적인 클라이언트 disconnected 이벤트, 응답 불필요 */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        log.debug("SSE 클라이언트 연결 종료: {}", ex.getMessage());
    }

    /** MCP Streamable HTTP DeferredResult timeout — 클라이언트 재연결 시 정상 발생, 에러 아님 */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.debug("MCP async 연결 timeout (클라이언트 재연결 정상 사이클): {}", ex.getMessage());
    }

    /** 그 외 미처리 예외 → 500 Internal Server Error
     *  SSE 등 응답이 이미 커밋된 경우(Content-Type: text/event-stream)에는 body 쓰기를 시도하지 않는다. */
    @ExceptionHandler(Exception.class)
    public void handleGeneral(Exception ex, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            log.debug("미처리 예외 (응답 커밋됨, 응답 생략): {}", ex.getMessage());
            return;
        }
        log.error("미처리 예외", ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"서버 오류가 발생했습니다.\"}");
    }
}
