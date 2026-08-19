package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.model.capture.WebCaptureSessionRequest;
import com.krdevops.springai.model.capture.WebCaptureSessionResponse;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import com.krdevops.springai.service.resilience.ExternalDependency;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WebCaptureClient {
    private final WebCaptureProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final ExternalCallGuard guard;

    public WebCaptureClient(WebCaptureProperties properties, ObjectMapper objectMapper,
                            ExternalCallGuard guard) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.guard = guard;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public byte[] capture(CaptureWebPageRequest request, URI validatedUrl,
                          String captureId, String documentKey) {
        ensureEnabled();
        return guard.execute(ExternalDependency.EXTRACTOR,
                () -> captureOnce(request, validatedUrl, captureId, documentKey));
    }

    private byte[] captureOnce(CaptureWebPageRequest request, URI validatedUrl,
                               String captureId, String documentKey) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("captureId", captureId);
            body.put("documentKey", documentKey);
            body.put("url", validatedUrl.toString());
            body.put("profile", request.profile());
            body.put("viewport", request.viewport());
            body.put("readiness", request.readiness());
            body.put("sensitiveSelectors", properties.getSensitiveSelectors());
            body.put("allowedOrigins", properties.getAllowedOrigins());
            body.put("allowedResourceOrigins", properties.getAllowedResourceOrigins());
            if (request.storageStateRef() != null) {
                body.put("storageStateRef", request.storageStateRef());
            }
            if (request.interactions() != null) {
                body.put("interactions", request.interactions());
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint("/v1/captures"))
                    .timeout(Duration.ofSeconds(properties.getResponseTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/vnd.springai.figpack+zip")
                    .header("X-Extractor-Key", properties.getExtractorApiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                    .build();
            HttpResponse<byte[]> response = client.send(httpRequest,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("extractor가 오류 상태를 반환했습니다: " + response.statusCode());
            }
            if (response.body().length > properties.getMaxResponseMb() * 1024L * 1024L) {
                throw new IllegalStateException("extractor 응답 크기 제한을 초과했습니다.");
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("extractor 호출 실패", e);
        }
    }

    /**
     * 04번 문서 R6(§9): extractor {@code POST /v1/sessions}를 호출해 로그인을 수행하고 opaque
     * sessionId를 발급받는다. allowedOrigins는 caller가 아니라 서버 설정({@code
     * WebCaptureProperties})을 그대로 쓴다 — capture()와 동일한 "허용 origin은 서버가 소유" 원칙.
     */
    public WebCaptureSessionResponse createSession(WebCaptureSessionRequest request, URI validatedLoginUrl) {
        ensureEnabled();
        return guard.execute(ExternalDependency.EXTRACTOR,
                () -> createSessionOnce(request, validatedLoginUrl));
    }

    private WebCaptureSessionResponse createSessionOnce(WebCaptureSessionRequest request, URI validatedLoginUrl) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("loginUrl", validatedLoginUrl.toString());
            body.put("allowedOrigins", properties.getAllowedOrigins());
            if (request.preClickSelector() != null) {
                body.put("preClickSelector", request.preClickSelector());
            }
            body.put("usernameSelector", request.usernameSelector());
            body.put("username", request.username());
            body.put("passwordSelector", request.passwordSelector());
            body.put("password", request.password());
            body.put("submitSelector", request.submitSelector());
            if (request.successSelector() != null) {
                body.put("successSelector", request.successSelector());
            }
            if (request.timeoutMillis() != null) {
                body.put("timeoutMillis", request.timeoutMillis());
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint("/v1/sessions"))
                    .timeout(Duration.ofSeconds(properties.getResponseTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-Extractor-Key", properties.getExtractorApiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "extractor 세션 생성이 실패했습니다(" + response.statusCode() + "): " + response.body());
            }
            return objectMapper.readValue(response.body(), WebCaptureSessionResponse.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("extractor 세션 생성 호출 실패", e);
        }
    }

    public boolean health() {
        if (!properties.isEnabled()) return false;
        try {
            return guard.execute(ExternalDependency.EXTRACTOR, () -> {
                HttpRequest request = HttpRequest.newBuilder(endpoint("/v1/health"))
                        .timeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                        .header("X-Extractor-Key", properties.getExtractorApiKey()).GET().build();
                return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
            });
        } catch (Exception e) {
            return false;
        }
    }

    private URI endpoint(String path) {
        return properties.getExtractorBaseUrl().resolve(path);
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) throw new IllegalStateException("WEB_CAPTURE 기능이 비활성 상태입니다.");
    }
}
