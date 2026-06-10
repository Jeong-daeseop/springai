<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/dto

## Purpose
채팅 요청/응답 데이터 전달 객체(DTO) 패키지.

## Key Files

| File | Description |
|------|-------------|
| `ChatMessageDto.java` | 채팅 메시지 DTO — 역할(user/assistant), 내용, 타임스탬프 |
| `ChatSession.java` | 채팅 세션 DTO — 세션 ID, 생성 시각, 메시지 목록 |

## For AI Agents

### Working In This Directory
- Lombok `@Data` 또는 `@Value` 사용
- JSON 직렬화: Jackson (`jackson-datatype-jsr310` — `LocalDateTime` 지원)

<!-- MANUAL: -->
