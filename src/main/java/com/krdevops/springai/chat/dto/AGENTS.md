<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/dto

## Purpose
채팅 기능 데이터 전송 객체(DTO) 패키지. API 요청/응답 및 세션 표현 객체를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `ChatMessageDto.java` | 채팅 메시지 요청/응답 DTO — 역할(role), 내용(content), 세션 ID |
| `ChatSession.java` | 채팅 세션 표현 객체 — 세션 ID, 생성 시각, 메시지 이력 |

## For AI Agents

### Working In This Directory
- DTO는 컨트롤러 ↔ 서비스 경계에서만 사용
- JSON 직렬화 필드명은 카멜케이스 유지 (프론트엔드 `chat.html`과 계약)
- Redis 저장 시 `ChatSession`이 직렬화되므로 필드 타입 변경 주의

## Dependencies

### Internal
- `chat/controller/` — 컨트롤러에서 요청 파라미터 바인딩
- `chat/service/` — 서비스 레이어 입출력

<!-- MANUAL: -->
