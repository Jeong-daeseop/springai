<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# controller

## Purpose
REST API 컨트롤러 패키지. RAG 문서 처리, Tool 직접 호출 API, 전역 예외 처리를 담당합니다.
Claude Desktop은 MCP SSE(`/mcp/**`)로 연결되며, 이 패키지는 REST API 클라이언트용입니다.

## Key Files

| File | Description |
|------|-------------|
| `RagController.java` | `/api/rag/**` — 문서 임베딩(`/ingest`), 유사 검색(`/search`), RAG 응답(`/chat`) |
| `ToolApiController.java` | `/api/tools/**` — OpenAI Function Calling용 Tool REST API (테이블 스키마, 직원 CRUD, LLM 라우팅) |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` 전역 예외 처리 — `IllegalArgumentException` 400, 기타 500 |

## For AI Agents

### Working In This Directory
- 모든 `/api/**` 엔드포인트는 `SecurityConfig`의 X-API-Key 인증 필요 (단 `/api/chat/**` 등 일부 예외)
- `LlmRouterService.chat("CODE_GENERATION", ...)` 호출 금지 — `IllegalArgumentException` 발생 (MCP SSE 전용)
- taskType 값: `CLASSIFICATION`, `SIMPLE_QUERY` → OpenAI / `SENSITIVE_DATA` → Ollama

### Common Patterns
```java
// RAG 응답 요청 예
POST /api/rag/chat
{"query": "eGovFrame 로그인 처리", "taskType": "SIMPLE_QUERY", "topK": 3}

// LLM 라우팅 요청 예
POST /api/tools/chat
{"taskType": "SENSITIVE_DATA", "message": "처리할 내용"}
```

## Dependencies

### Internal
- `service/RagService.java` — 벡터 검색, 임베딩
- `service/LlmRouterService.java` — OpenAI/Ollama 라우팅
- `service/SchemaService.java`, `CodeService.java`, `EmployeeService.java`

<!-- MANUAL: -->
