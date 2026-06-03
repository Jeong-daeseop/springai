<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/service

## Purpose
채팅 기능 서비스 인터페이스 정의 패키지. 세션 관리, 문서 처리, Ollama 모델 조회,
세션 인식 채팅 서비스의 계약(interface)을 정의합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovChatSessionService.java` | 채팅 세션 CRUD 서비스 인터페이스 |
| `EgovDocumentService.java` | 문서 업로드/임베딩/관리 서비스 인터페이스 |
| `EgovOllamaModelService.java` | Ollama 설치 모델 목록 조회 서비스 인터페이스 |
| `EgovSessionAwareChatService.java` | 세션 기반 RAG + 멀티턴 대화 서비스 인터페이스 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `impl/` | 서비스 인터페이스 구현체 (see `impl/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 인터페이스만 이 패키지에, 구현체는 반드시 `impl/` 패키지에 배치
- 인터페이스 변경 시 `impl/` 구현체와 동기화 필수

### Common Patterns
```java
public interface EgovMyService {
    ResultType doSomething(ParamType param);
}
```

## Dependencies

### Internal
- `impl/` — 구현체
- `chat/dto/` — 입출력 타입

<!-- MANUAL: -->
