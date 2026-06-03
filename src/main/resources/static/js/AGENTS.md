<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# resources/static/js

## Purpose
JavaScript 라이브러리 및 프론트엔드 스크립트 디렉토리.

## Key Files

| File | Description |
|------|-------------|
| `marked.min.js` | Marked.js 마크다운 파서 (minified) — `chat.html`에서 LLM 응답 마크다운 렌더링에 사용 |

## For AI Agents

### Working In This Directory
- `marked.min.js`는 외부 라이브러리 — 직접 수정 금지
- 버전 업그레이드 시 `chat.html`의 API 호환성 확인 (`marked.parse()` 등)

## Dependencies

### Internal
- `templates/chat.html` — 이 파일을 로드하여 마크다운 렌더링

<!-- MANUAL: -->
