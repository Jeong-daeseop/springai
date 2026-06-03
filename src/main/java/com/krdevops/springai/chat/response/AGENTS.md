<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/response

## Purpose
채팅 API 응답 전용 DTO 패키지. 컨트롤러가 클라이언트에 반환하는 구조화된 응답 객체를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `DocumentStatusResponse.java` | 문서 업로드/임베딩 상태 응답 — 성공/실패, 처리된 청크 수 등 |
| `TechnologyResponse.java` | 기술 스택 정보 응답 DTO |

## For AI Agents

### Working In This Directory
- 응답 DTO는 불변(immutable) 설계 권장 (`@Value` 또는 Java record 사용)
- 클라이언트 API 계약이므로 필드명 변경 시 프론트엔드 영향 확인 필요

## Dependencies

### Internal
- `chat/controller/` — 컨트롤러에서 응답 객체 생성하여 반환

<!-- MANUAL: -->
