<!-- Parent: ../../../java/AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# com.krdevops.springai (Root Package)

## Purpose
MCP 서버 애플리케이션의 루트 패키지. `SpringaiApplication.java` 진입점과
chat, config, controller, mapper, service, tools, vo 하위 패키지로 구성됩니다.

## Key Files

| File | Description |
|------|-------------|
| `SpringaiApplication.java` | `@SpringBootApplication` 진입점 — MCP 서버 부트스트랩 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `chat/` | 채팅 세션, RAG, Ollama 통합 기능 (see `chat/AGENTS.md`) |
| `config/` | 글로벌 설정 — McpConfig, OllamaConfig, SecurityConfig, VectorStoreConfig (see `config/AGENTS.md`) |
| `controller/` | HTTP API 컨트롤러 — RagController, ToolApiController (see `controller/AGENTS.md`) |
| `mapper/` | JdbcTemplate 기반 Repository (see `mapper/AGENTS.md`) |
| `service/` | 비즈니스 로직 서비스 21종 (see `service/AGENTS.md`) |
| `tools/` | MCP Tool 구현체 19종 (see `tools/AGENTS.md`) |
| `vo/` | Value Object — EmployeeVO (see `vo/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 신규 패키지 추가 시 위 패키지 구조 패턴 준수
- `SpringaiApplication.java`는 수정하지 않음 (Spring Boot 표준 진입점)

### Common Patterns
- MCP Tool 흐름: `tools/*.java` → `service/*.java` → `mapper/*.java` (DB) 또는 외부 API
- 모든 MCP Tool은 `config/McpConfig.java`에 `MethodToolCallbackProvider`로 등록

## Dependencies

### External
- Spring Boot 4.0.6 (`@SpringBootApplication`)
- Spring AI MCP Server Starter

<!-- MANUAL: -->
