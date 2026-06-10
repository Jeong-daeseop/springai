# MCP 연결 오류 해결 가이드

> 작성일: 2026-06-08  
> 적용 버전: Spring Boot 4.1.0-RC1 / Spring AI 2.0.0-RC1 / MCP SDK 2.0.0-RC1

---

## 배경

Claude Desktop ↔ springai-mcp 서버 연결 시 발생한 일련의 오류를 진단하고 해결한 과정을 기록한다.

```
Claude Desktop
    │ mcp-remote 0.1.16 (HTTP 브릿지)
    ▼
http://localhost:8080/mcp  (Streamable HTTP)
    ▼
Spring AI MCP Server (WebMVC)
    ▼
Tools / RAG / DB
```

---

## 해결된 이슈 목록

### 1. StreamableHTTPError 500 — mcp-remote가 `/sse`에 POST 전송

**원인**  
mcp-remote 최신 버전이 Streamable HTTP(POST) 방식으로 요청을 보내는데, `/sse`는 GET 전용 SSE 엔드포인트라 500 반환.

**해결**  
`claude_desktop_config.json`에 `--transport sse-only` 옵션 추가 → GET 전용 SSE 방식 강제.

```json
"args": ["-y", "mcp-remote@0.1.16", "http://localhost:8080/sse",
         "--allow-http", "--transport", "sse-only"]
```

---

### 2. `Invalid transport strategy: sse`

**원인**  
`--transport sse`는 mcp-remote에서 유효하지 않은 값.  
유효값: `sse-only`, `http-only`, `sse-first`, `http-first`

**해결**  
`sse` → `sse-only` 로 수정.

---

### 3. `AsyncRequestNotUsableException` → `HttpMessageNotWritableException` 연쇄

**원인**  
SSE 클라이언트가 연결을 끊으면 `AsyncRequestNotUsableException` 발생.  
`GlobalExceptionHandler#handleGeneral`이 이를 받아 `Map` 응답을 SSE 스트림에 쓰려다  
`Content-Type: text/event-stream`에 맞는 컨버터 없어 2차 예외 발생.

**해결**  
① `AsyncRequestNotUsableException` 전용 void 핸들러 추가  
② `handleGeneral`에 `response.isCommitted()` 체크 추가

```java
@ExceptionHandler(AsyncRequestNotUsableException.class)
public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
    log.debug("SSE 클라이언트 연결 종료: {}", ex.getMessage());
}

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
```

---

### 4. `soLinger SocketException: Invalid argument` (macOS NIO)

**원인**  
Tomcat `SocketProperties.setProperties(Socket)` 내부:

```java
if (soLingerOn != null && soLingerTime != null) {
    socket.setSoLinger(soLingerOn.booleanValue(), soLingerTime.intValue());
}
```

Java NIO `SocketAdaptor.setSoLinger(false, -1)` → `setsockopt(SO_LINGER, -1)` → macOS **EINVAL**.

Spring Boot 4.x가 기본적으로 `soLingerOn`, `soLingerTime`을 non-null로 설정함.  
`setSoLingerOn(boolean)` setter가 primitive를 받으므로 null로 되돌리는 public API 없음.

**해결**  
커스텀 logback Filter로 해당 메시지만 정밀 차단.

```java
// SoLingerLogFilter.java
public class SoLingerLogFilter extends Filter<ILoggingEvent> {
    private static final String TARGET_LOGGER = "org.apache.tomcat.util.net.NioEndpoint";
    private static final String TARGET_MESSAGE = "Error setting socket options";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (TARGET_LOGGER.equals(event.getLoggerName())
                && event.getMessage() != null
                && event.getMessage().startsWith(TARGET_MESSAGE)) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
```

```xml
<!-- logback-spring.xml -->
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <filter class="com.krdevops.springai.config.SoLingerLogFilter"/>
    ...
</appender>
```

> **비추천 방법**
> - `server.tomcat.socket.so-linger: 0` → TCP RST 강제 종료로 응답 유실 위험
> - `NioEndpoint level="OFF"` → 다른 중요한 Tomcat 에러까지 숨김

---

### 5. MCP 프로토콜 버전 불일치 (`2024-11-05` vs `2025-11-25`)

**원인**  
- Spring AI 2.0.0-M8 + `transport: sse` 조합은 `2024-11-05`만 지원
- `HttpServletSseServerTransportProvider.protocolVersions()` = `["2024-11-05"]`
- mcp-remote 0.1.16은 `2025-11-25` 요청 → 서버가 다운그레이드 제안 → 연결 불안정

**해결**  
Spring AI + Spring Boot 업그레이드 후 Streamable HTTP 트랜스포트로 전환.

| 항목 | 이전 | 이후 |
|---|---|---|
| Spring Boot | `4.0.6` | `4.1.0-RC1` |
| Spring AI | `2.0.0-M8` | `2.0.0-RC1` |
| Jedis | `7.0.0` (강제 다운그레이드) | `7.4.1` |
| `spring-ai-advisors-vector-store` | 사용 | `spring-ai-vector-store-advisor` (RC1 renamed) |
| `VectorStoreConfig` | `JedisPooled` | `RedisClient.create(URI)` |
| MCP 트랜스포트 | SSE (`2024-11-05` only) | Streamable HTTP (`2025-11-25` 지원) |

**`application.yaml`**
```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE   # SSE → Streamable HTTP 전환
      streamable-http:
        keep-alive-interval: 30s
```

**`claude_desktop_config.json`**
```json
"args": ["-y", "mcp-remote@0.1.16", "http://localhost:8080/mcp",
         "--allow-http", "--transport", "http-only"]
```

> **참고**: Spring AI RC1 auto-configuration 조건
> - `protocol: SSE` → `matchIfMissing = true` (기본값 = SSE)
> - `protocol: STREAMABLE` → `matchIfMissing = false` (반드시 명시 필요)

---

### 6. `NoResourceFoundException` (404) ERROR 로그

**원인**  
Streamable HTTP 전환 후 `/sse` 엔드포인트 없어짐. 이전 캐시가 남아 있는 mcp-remote가 `/sse`를 요청하면 `GlobalExceptionHandler#handleGeneral`이 ERROR로 기록.

**해결**  
① `NoResourceFoundException` 전용 핸들러 추가 (DEBUG 레벨)  
② `SecurityConfig`에서 `/sse` 제거, `/mcp/**` 유지  
③ mcp-remote 캐시 초기화: `rm -rf ~/.mcp-remote-*`

---

## 파일 변경 요약

| 파일 | 변경 내용 |
|---|---|
| `build.gradle` | Spring Boot `4.1.0-RC1`, Spring AI `2.0.0-RC1`, `spring-ai-vector-store-advisor` |
| `application.yaml` | `transport: sse` 제거, `protocol: STREAMABLE` 추가 |
| `config/VectorStoreConfig.java` | `JedisPooled` → `RedisClient` |
| `config/TomcatSocketConfig.java` | `TomcatConnectorCustomizer` (soLingerOn 설정 시도용, 보조) |
| `config/SoLingerLogFilter.java` | soLinger 에러 메시지 정밀 차단 |
| `config/SecurityConfig.java` | `/sse` 제거, `/mcp/**` 유지 |
| `controller/GlobalExceptionHandler.java` | `AsyncRequestNotUsableException`, `NoResourceFoundException` 핸들러 추가, `handleGeneral` void 전환 |
| `logback-spring.xml` | `SoLingerLogFilter` 적용 |
| `claude_desktop_config.json` | URL `/sse`→`/mcp`, transport `sse-only`→`http-only` |
| `.mcp.json` | 동일 |

---

## 최종 연결 흐름

```
mcp-remote 0.1.16
  POST http://localhost:8080/mcp  (Streamable HTTP, --transport http-only)
    ↓
WebMvcStreamableServerTransportProvider
  protocolVersions = ["2025-06-18", "2025-11-25"]
    ↓
McpAsyncServer
  Protocol: 2025-11-25 ← 직접 지원 (다운그레이드 없음)
    ↓
43개 MCP Tool 정상 응답
```
