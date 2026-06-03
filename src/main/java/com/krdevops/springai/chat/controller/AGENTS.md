<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/controller

## Purpose
채팅 기능 HTTP 컨트롤러 5종. 세션 관리, 문서 업로드/관리, Ollama 모델 조회,
스트리밍 채팅, 웹 UI 라우팅을 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovChatSessionController.java` | 채팅 세션 CRUD API — 세션 생성/조회/삭제 |
| `EgovDocumentController.java` | 문서 업로드 및 VectorStore 임베딩 API |
| `EgovOllamaChatController.java` | Ollama LLM 스트리밍 채팅 API (SSE) |
| `EgovOllamaModelController.java` | Ollama 설치 모델 목록 조회 API |
| `EgovWebController.java` | 웹 UI 페이지 라우팅 (`chat.html` 뷰 반환) |

## For AI Agents

### Working In This Directory
- SSE(Server-Sent Events) 스트리밍 응답은 `EgovOllamaChatController`에서 처리
- 문서 임베딩 흐름: `EgovDocumentController` → `EgovDocumentService` → VectorStore
- 컨트롤러는 요청/응답 처리만, 로직은 `chat/service/`에 위임

### Common Patterns
- `@RestController` + `@RequestMapping("/api/chat/...")`
- 스트리밍: `Flux<ServerSentEvent<String>>` 반환 타입

## Dependencies

### Internal
- `chat/service/` — 채팅 비즈니스 로직
- `chat/dto/` — 요청/응답 DTO

<!-- MANUAL: -->
