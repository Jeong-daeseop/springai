<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# config

## Purpose
Spring 설정 클래스 패키지. MCP Tool 등록, 보안, Vector Store, 애플리케이션 속성을 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `McpConfig.java` | 모든 MCP Tool을 `MethodToolCallbackProvider`로 한 곳에 등록 — 19개 Tool 빈 주입 |
| `SecurityConfig.java` | Spring Security 설정 — X-API-Key 필터, `/mcp/**`·`/api/chat/**` 인증 없이 허용 |
| `VectorStoreConfig.java` | Redis Vector Store 수동 설정 — `RedisClient`, `RedisVectorStore` 빈 생성 (Auto Config 제외됨) |
| `AppProperties.java` | `app.*` 설정 바인딩 — `apiKey`, `openaiModels`, `documentPaths` |
| `EgovProperties.java` | `egov.*` 설정 바인딩 — `output.basePath` (생성 코드 저장 경로) |
| `SoLingerLogFilter.java` | TCP SO_LINGER 관련 로그 노이즈 억제 필터 |

## For AI Agents

### Working In This Directory
- 새 Tool 추가 시 반드시 `McpConfig.allToolCallbacks()`의 파라미터와 `toolObjects(...)` 목록 양쪽에 추가
- `SecurityConfig`: `/api/chat/**`, `/api/ollama/**`, `/api/documents/**`는 인증 없이 허용 (`permitAll`)
- `VectorStoreConfig`: `RedisVectorStoreAutoConfiguration`이 `SpringaiApplication`에서 제외되므로 이 클래스에서 수동 등록
- 새 `@ConfigurationProperties` 추가 시 `@ConfigurationPropertiesScan`이 자동 감지 (별도 등록 불필요)

### Common Patterns
```java
// AppProperties 접근 예
@RequiredArgsConstructor
public class SomeService {
    private final AppProperties appProperties;
    // appProperties.getApiKey(), appProperties.getDocumentPaths()
}
```

## Dependencies

### Internal
- `SpringaiApplication.java` — `exclude = RedisVectorStoreAutoConfiguration.class`, `@ConfigurationPropertiesScan`

### External
- Spring Security 6.x
- Spring AI `ToolCallbackProvider`, `MethodToolCallbackProvider`
- Redis Jedis client (`RedisClient`)

<!-- MANUAL: -->
