<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/controller

## Purpose
채팅 웹 UI 및 REST API 컨트롤러 패키지. SSE 스트리밍 채팅, 세션 관리, 문서 업로드, Ollama 모델 관리를 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovWebController.java` | `/` 루트 — `chat.html` Thymeleaf 뷰 반환 |
| `EgovOllamaChatController.java` | `/api/chat/**` — SSE 스트리밍 채팅, RAG 채팅, 세션별 이력 조회 |
| `EgovChatSessionController.java` | `/api/chat/sessions/**` — 세션 목록, 세션 삭제, 메시지 이력 조회 |
| `EgovDocumentController.java` | `/api/documents/**` — PDF/텍스트 문서 업로드 → RAG 임베딩 |
| `EgovOllamaModelController.java` | `/api/ollama/**` — Ollama 모델 목록 조회, 모델 Pull 진행률 SSE |

## For AI Agents

### Working In This Directory
- SSE 엔드포인트: `text/event-stream` Content-Type, `Flux<ServerSentEvent>` 반환
- 파일 업로드: `multipart/form-data`, 최대 50MB (`spring.servlet.multipart.max-file-size`)
- 모든 `/api/chat/**`, `/api/documents/**`, `/api/ollama/**`는 인증 없이 허용 (`SecurityConfig`)
- 세션 ID: 클라이언트가 UUID 생성 후 요청 헤더 또는 파라미터로 전달

### Common Patterns
```java
// SSE 스트리밍 응답 패턴
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> stream(@RequestParam String sessionId, @RequestParam String message) { ... }
```

## Dependencies

### Internal
- `chat/service/EgovSessionAwareChatService.java`
- `chat/service/EgovChatSessionService.java`
- `chat/service/EgovDocumentService.java`
- `chat/service/EgovOllamaModelService.java`

<!-- MANUAL: -->
