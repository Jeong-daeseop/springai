<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/context

## Purpose
채팅 세션 컨텍스트 객체 패키지. 세션별 대화 상태를 캡슐화합니다.

## Key Files

| File | Description |
|------|-------------|
| `SessionContext.java` | 채팅 세션 컨텍스트 — 세션 ID, 대화 이력, 사용자 정보 등 상태 보유 |

## For AI Agents

### Working In This Directory
- 세션 상태 필드 추가 시 Redis 직렬화 호환성 확인 필요
- `chat/repository/EgovRedisChatMemoryRepository.java`에서 이 객체를 저장/조회

## Dependencies

### Internal
- `chat/repository/` — 컨텍스트 영속화
- `chat/service/` — 서비스 레이어에서 컨텍스트 조회/갱신

<!-- MANUAL: -->
