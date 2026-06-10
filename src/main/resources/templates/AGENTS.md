<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# templates

## Purpose
Thymeleaf HTML 템플릿 디렉터리. 웹 UI 채팅 화면을 제공합니다.

## Key Files

| File | Description |
|------|-------------|
| `chat.html` | RAG + 세션 채팅 웹 UI — SSE 스트리밍, marked.js 마크다운 렌더링, 세션 관리 포함 |

## For AI Agents

### Working In This Directory
- Thymeleaf 3.x 문법 사용 (`th:*` 속성)
- SSE 연결: `/api/chat/stream` 엔드포인트 (EventSource API)
- 인라인 스크립트 최소화, XSS 방지 필수
- Spring Security `th:action` CSRF 토큰 주의 (현재 `/mcp/**`만 CSRF 비활성화)

<!-- MANUAL: -->
