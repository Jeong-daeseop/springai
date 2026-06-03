<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# config (Global)

## Purpose
애플리케이션 레벨 글로벌 설정 클래스 패키지. MCP Tool 등록, Ollama 모델 설정,
Spring Security, VectorStore 빈 설정을 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `McpConfig.java` | MCP Tool 등록 허브 — 모든 `*Tool` 클래스를 `MethodToolCallbackProvider`로 등록 |
| `OllamaConfig.java` | Ollama LLM 클라이언트 설정 — 모델명, 호스트, 옵션 |
| `SecurityConfig.java` | Spring Security 설정 — 엔드포인트 인가 규칙 |
| `VectorStoreConfig.java` | VectorStore 빈 설정 — 임베딩 모델, 저장소 타입 |

## For AI Agents

### Working In This Directory
- **신규 MCP Tool 추가 시 `McpConfig.java`에 반드시 빈 등록 필수**
  ```java
  @Bean
  public ToolCallbackProvider myToolCallbacks(MyTool myTool) {
      return MethodToolCallbackProvider.builder().toolObjects(myTool).build();
  }
  ```
- `SecurityConfig.java`에서 `permitAll()` 남용 금지 (egov-security.md 규칙 준수)

### Common Patterns
- `@Configuration` + `@Bean` 방식 사용
- 채팅 전용 설정은 `chat/config/` 패키지 사용

## Dependencies

### Internal
- `tools/` — 모든 Tool 클래스가 McpConfig에서 참조됨

### External
- Spring AI MCP Server
- Spring Security
- Ollama Java Client

<!-- MANUAL: -->
