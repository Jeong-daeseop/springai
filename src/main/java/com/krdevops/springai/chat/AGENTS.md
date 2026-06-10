<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat

## Purpose
Thymeleaf 웹 UI 기반 채팅 모듈. RAG 통합, Redis 세션 메모리, Ollama/OpenAI LLM 연동,
SSE 스트리밍 채팅을 제공합니다. MCP Tool과는 독립적인 웹 채팅 레이어입니다.

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `config/` | 채팅 모듈 설정 — Async, ChatMemory(Redis), RAG 파이프라인 (see `config/AGENTS.md`) |
| `context/` | 채팅 컨텍스트 관련 유틸리티 (see `context/AGENTS.md`) |
| `controller/` | 채팅 웹/REST 컨트롤러 — SSE 스트리밍, 세션 관리, 문서 업로드 (see `controller/AGENTS.md`) |
| `dto/` | 채팅 데이터 전달 객체 — `ChatMessageDto`, `ChatSession` (see `dto/AGENTS.md`) |
| `repository/` | Redis 기반 채팅 메모리 저장소 (see `repository/AGENTS.md`) |
| `response/` | 채팅 응답 DTO — `DocumentStatusResponse`, `TechnologyResponse` (see `response/AGENTS.md`) |
| `service/` | 채팅 서비스 인터페이스 — 세션, 문서, Ollama 모델, RAG 채팅 (see `service/AGENTS.md`) |
| `util/` | 채팅 유틸리티 — 응답 정리, Think 태그 처리, 문서 해시, JSON 프롬프트 (see `util/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 채팅 흐름: 브라우저 → `EgovOllamaChatController` → `EgovSessionAwareChatServiceImpl` → Ollama/OpenAI
- RAG 파이프라인: `EgovRagConfig`에서 `QuestionAnswerAdvisor` + `EgovCompressionQueryTransformer` 구성
- 세션 메모리: Redis (`EgovRedisChatMemoryRepository`) — 최대 8개 메시지
- SSE 스트리밍: `/api/chat/stream` (EventSource)

### Testing Requirements
- Redis 서버 실행 필요 (`docker start redis-stack` 또는 로컬 Redis)
- Ollama 실행 필요 (`ollama serve`, qwen3:8b 모델 pull 필요)

<!-- MANUAL: -->
