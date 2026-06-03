<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# resources/templates

## Purpose
Thymeleaf HTML 템플릿 디렉토리. 웹 UI 페이지를 제공합니다.

## Key Files

| File | Description |
|------|-------------|
| `chat.html` | 채팅 웹 UI — Ollama LLM과 실시간 스트리밍 대화, 문서 업로드, 세션 관리 |

## For AI Agents

### Working In This Directory
- `chat.html`은 SSE(Server-Sent Events)로 스트리밍 응답을 수신하는 프론트엔드
- `static/js/marked.min.js`를 로드하여 마크다운 렌더링 수행
- API 엔드포인트 경로 변경 시 이 템플릿의 `fetch` 호출 URL도 함께 수정 필요

### Common Patterns
- Thymeleaf `th:*` 속성 방식 사용
- JavaScript ES6+ 인라인 스크립트 (최소화 권장)
- SSE: `EventSource` API로 `/api/chat/stream` 구독

## Dependencies

### Internal
- `chat/controller/EgovWebController.java` — 이 템플릿을 뷰로 반환
- `static/js/marked.min.js` — 마크다운 파서

<!-- MANUAL: -->
