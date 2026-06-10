<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# js

## Purpose
채팅 UI에서 사용하는 JavaScript 라이브러리 디렉터리.

## Key Files

| File | Description |
|------|-------------|
| `marked.min.js` | Markdown → HTML 변환 라이브러리 (채팅 응답 렌더링용) |

## For AI Agents

### Working In This Directory
- 외부 라이브러리 추가 시 CDN 대신 이 디렉터리에 번들로 포함
- `chat.html` 템플릿에서 `th:src="@{/js/marked.min.js}"` 패턴으로 참조

<!-- MANUAL: -->
