<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/response

## Purpose
채팅 API 응답 전용 DTO 패키지. 컨트롤러가 클라이언트에 반환하는 구조화된 응답 객체를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `DocumentStatusResponse.java` | 문서 업로드·임베딩 결과 응답 — 상태, 청크 수, 문서 ID |
| `TechnologyResponse.java` | 기술 스택 정보 응답 — 현재 활성 LLM 모델, RAG 상태 등 |

## For AI Agents

### Working In This Directory
- 불변 객체 권장: Lombok `@Value` 또는 Java `record`
- 새 엔드포인트 추가 시 전용 Response 클래스 작성

<!-- MANUAL: -->
