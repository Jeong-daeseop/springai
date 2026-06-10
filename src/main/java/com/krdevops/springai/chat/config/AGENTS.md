<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/config

## Purpose
채팅 모듈 전용 설정 클래스 패키지. 비동기 처리, Redis 채팅 메모리, RAG 파이프라인을 구성합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovAsyncConfig.java` | `@EnableAsync` + `ThreadPoolTaskExecutor` 설정 — SSE 스트리밍용 비동기 스레드 풀 |
| `EgovChatMemoryConfig.java` | `MessageWindowChatMemory` 빈 설정 — Redis 기반, 최대 8개 메시지 윈도우 |
| `EgovRagConfig.java` | RAG 파이프라인 구성 — `QuestionAnswerAdvisor` + `EgovCompressionQueryTransformer` 조합 |
| `EgovRedisConfig.java` | Redis 연결 설정 — `RedisTemplate`, `EgovRedisChatMemoryRepository` 빈 등록 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `rag/` | RAG 관련 커스텀 컴포넌트 (see `rag/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- `EgovChatMemoryConfig`: `chat.memory.max-messages` 값(`application.yaml`)으로 윈도우 크기 제어
- `EgovRagConfig`: RAG 활성화 여부는 `rag.enable-query-compression` 설정으로 토글 가능
- `EgovAsyncConfig`: SSE 스트리밍 타임아웃은 `server.tomcat.connection-timeout` (1시간) 참조

## Dependencies

### Internal
- `chat/repository/EgovRedisChatMemoryRepository.java`
- `chat/config/rag/transformers/EgovCompressionQueryTransformer.java`

### External
- Spring AI `MessageWindowChatMemory`, `QuestionAnswerAdvisor`
- Spring Data Redis

<!-- MANUAL: -->
