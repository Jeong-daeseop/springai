<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# controller

## Purpose
HTTP REST API 컨트롤러 패키지. RAG 문서 검색 API와 Tool 실행 API를 노출합니다.
MCP 도구와 별도로 웹 클라이언트에서 직접 호출할 수 있는 엔드포인트를 제공합니다.

## Key Files

| File | Description |
|------|-------------|
| `RagController.java` | RAG 기반 문서 검색 REST API — 질의 응답 엔드포인트 |
| `ToolApiController.java` | MCP Tool 직접 호출 REST API — 웹 UI에서 Tool 실행 |

## For AI Agents

### Working In This Directory
- 컨트롤러는 요청/응답 처리만 담당, 비즈니스 로직은 `service/`에 위임
- `@RequestMapping` URL은 소문자 + 하이픈 패턴 사용

### Testing Requirements
- MCP 서버는 `web-application-type: none`이므로 `@WebMvcTest` 사용 불가
- 서비스 레이어 목(Mock) 기반 단위 테스트 작성

## Dependencies

### Internal
- `service/` — 비즈니스 로직 서비스
- `chat/service/` — 채팅 관련 서비스

<!-- MANUAL: -->
