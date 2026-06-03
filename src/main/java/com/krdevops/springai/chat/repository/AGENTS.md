<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/repository

## Purpose
채팅 메모리 영속화 레이어. Redis를 사용하여 세션별 대화 이력을 저장하고 조회합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovRedisChatMemoryRepository.java` | Spring AI `ChatMemoryRepository` Redis 구현체 — 세션 ID 기반 대화 이력 CRUD |

## For AI Agents

### Working In This Directory
- Spring AI `ChatMemoryRepository` 인터페이스 구현
- Redis 키 패턴: `chat:memory:{sessionId}`
- TTL 설정은 `chat/config/EgovRedisConfig.java` 또는 이 클래스에서 관리

### Testing Requirements
- Redis 통합 테스트 시 Embedded Redis 또는 Testcontainers 사용 권장
- 단위 테스트 시 `RedisTemplate` 목(Mock) 처리

## Dependencies

### Internal
- `chat/config/EgovRedisConfig.java` — Redis 연결 설정
- `chat/config/EgovChatMemoryConfig.java` — 이 저장소를 ChatMemory 빈에 주입

### External
- Spring Data Redis (`RedisTemplate`)
- Lettuce Redis 클라이언트

<!-- MANUAL: -->
