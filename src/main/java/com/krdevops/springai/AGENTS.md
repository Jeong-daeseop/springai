<!-- Parent: ../../../../../../java/AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# com.krdevops.springai (Root Package)

## Purpose
Spring Boot 4.1.0-RC1 + Spring AI 2.0.0-RC1 MCP 서버 애플리케이션의 루트 패키지.
`SpringaiApplication.java`가 진입점이며, 하위 패키지에 기능별 모듈이 배치됩니다.

## Key Files

| File | Description |
|------|-------------|
| `SpringaiApplication.java` | `@SpringBootApplication` 진입점. `RedisVectorStoreAutoConfiguration` 제외(수동 설정), `@ConfigurationPropertiesScan` 활성화 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `chat/` | Thymeleaf 웹 UI 기반 채팅 모듈 — RAG, 세션, Ollama 연동 (see `chat/AGENTS.md`) |
| `config/` | Spring 설정 클래스 — MCP, Security, VectorStore, AppProperties (see `config/AGENTS.md`) |
| `controller/` | REST API 컨트롤러 — RAG, Tool API, 전역 예외 처리 (see `controller/AGENTS.md`) |
| `mapper/` | JdbcTemplate 기반 Repository — 직원, 코드 생성 이력 (see `mapper/AGENTS.md`) |
| `service/` | 비즈니스 서비스 레이어 — eGovFrame CRUD 생성, RAG, LLM 라우팅 등 (see `service/AGENTS.md`) |
| `tools/` | MCP Tool 클래스 — Claude Desktop에 노출되는 19개 도구 (see `tools/AGENTS.md`) |
| `vo/` | Value Object — EmployeeVO (see `vo/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 새 MCP Tool: `tools/` 패키지에 작성 후 `config/McpConfig.java`의 `allToolCallbacks` 빈에 추가
- 서비스 로직: `service/` 패키지에 작성, Tool은 위임(delegate)만 수행
- 레이어드 아키텍처 엄수: `tools/` → `service/` → `mapper/` (DB)

### Common Patterns
```java
// MCP Tool 등록 패턴
@Component
@RequiredArgsConstructor
public class MyTool {
    @Tool(description = "Claude가 선택할 한국어 설명")
    public String myMethod(String param) { ... }
}
// McpConfig.allToolCallbacks에 추가 필수
```

## Dependencies

### Internal
- `config/McpConfig.java` — 모든 Tool 빈 등록 중앙화
- `config/SecurityConfig.java` — X-API-Key 인증 필터

### External
- Spring AI `@Tool` 어노테이션
- Lombok `@RequiredArgsConstructor`

<!-- MANUAL: -->
