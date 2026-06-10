<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/repository

## Purpose
채팅 메모리 저장소 패키지. Redis를 백엔드로 사용하는 Spring AI `ChatMemoryRepository` 구현체를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovRedisChatMemoryRepository.java` | `ChatMemoryRepository` 구현 — Redis Hash에 세션별 메시지 이력 저장, TTL 관리 |

## For AI Agents

### Working In This Directory
- 세션 키 패턴: `chat:memory:{sessionId}`
- 메시지 직렬화: Jackson JSON
- TTL: Redis 기본값 (만료 없음) — 필요 시 `EgovRedisConfig`에서 TTL 설정 추가
- `EgovChatMemoryConfig`에서 `MessageWindowChatMemory`에 주입됨

## Dependencies

### External
- Spring Data Redis
- Spring AI `ChatMemoryRepository`

<!-- MANUAL: -->
