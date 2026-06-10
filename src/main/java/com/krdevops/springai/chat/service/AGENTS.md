<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/service

## Purpose
채팅 서비스 인터페이스 패키지. 구현체는 `impl/` 하위에 위치합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovSessionAwareChatService.java` | 세션 인식 채팅 서비스 인터페이스 — RAG + 메모리 통합 스트리밍/블로킹 채팅 |
| `EgovChatSessionService.java` | 채팅 세션 관리 인터페이스 — 세션 목록, 세션 삭제, 메시지 이력 조회 |
| `EgovDocumentService.java` | 문서 처리 서비스 인터페이스 — PDF/텍스트 파일 → RAG 임베딩 |
| `EgovOllamaModelService.java` | Ollama 모델 관리 인터페이스 — 설치된 모델 목록, 모델 Pull |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `impl/` | 서비스 구현체 (see `impl/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 인터페이스만 정의 — 구현은 `impl/` 패키지에 작성
- `EgovSessionAwareChatService`가 핵심 서비스 — 가장 복잡한 구현체 (`EgovSessionAwareChatServiceImpl`)

<!-- MANUAL: -->
