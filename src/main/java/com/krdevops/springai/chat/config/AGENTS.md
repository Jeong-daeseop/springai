<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/config

## Purpose
채팅 기능 전용 설정 클래스. 비동기 처리, 채팅 메모리, RAG 파이프라인, Redis 연결 설정을 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovAsyncConfig.java` | 스트리밍 응답용 비동기 실행기(TaskExecutor) 설정 |
| `EgovChatMemoryConfig.java` | Spring AI ChatMemory 빈 설정 — 대화 이력 저장 전략 |
| `EgovRagConfig.java` | RAG 파이프라인 설정 — QuestionAnswerAdvisor, 임베딩 모델, 검색 파라미터 |
| `EgovRedisConfig.java` | Redis 연결 설정 — Lettuce 클라이언트, 직렬화 설정 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `rag/transformers/` | RAG 쿼리 변환기 (see `rag/transformers/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- `EgovRagConfig.java` 수정 시 `rag/transformers/` 내 커스텀 변환기와 연계 확인
- Redis 설정 변경 시 `chat/repository/EgovRedisChatMemoryRepository.java`와 호환성 검토

### Common Patterns
- Spring AI `@Bean` 설정으로 Advisor 체인 구성
- RAG 검색 파라미터(top-k, similarity threshold)는 `EgovRagConfig`에서 중앙 관리

## Dependencies

### Internal
- `chat/repository/EgovRedisChatMemoryRepository.java` — Redis 메모리 저장소
- `rag/transformers/EgovCompressionQueryTransformer.java` — 쿼리 압축 변환기

### External
- Spring AI VectorStore / ChatMemory
- Redis (Lettuce)
- Ollama 임베딩 모델

<!-- MANUAL: -->
