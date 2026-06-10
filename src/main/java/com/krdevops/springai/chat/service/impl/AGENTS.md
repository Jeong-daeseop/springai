<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/service/impl

## Purpose
채팅 서비스 구현체 패키지. `chat/service/` 인터페이스의 실제 비즈니스 로직을 담당합니다.
`EgovSessionAwareChatServiceImpl`이 핵심 구현체로 가장 복잡한 클래스입니다(38회 수정).

## Key Files

| File | Description |
|------|-------------|
| `EgovSessionAwareChatServiceImpl.java` | 핵심 채팅 구현 — RAG Advisor + ChatMemory + LlmRouter 통합, SSE 스트리밍 및 블로킹 채팅 |
| `EgovDocumentServiceImpl.java` | 문서 처리 구현 — PDF/텍스트 파싱, 청킹, Vector Store 임베딩, 해시 기반 중복 방지 |
| `EgovChatSessionServiceImpl.java` | 세션 관리 구현 — Redis에서 세션 목록 조회, 세션 삭제, 메시지 이력 조회 |
| `EgovOllamaModelServiceImpl.java` | Ollama 모델 관리 구현 — Ollama REST API 호출, 모델 Pull SSE 진행률 스트리밍 |

## For AI Agents

### Working In This Directory
- `EgovSessionAwareChatServiceImpl`: `LlmRouterService.route(modelName)`으로 모델 선택 → `ChatClient` 빌더 패턴으로 Advisor 체인 구성
- RAG Advisor 순서: `EgovCompressionQueryTransformer` → `QuestionAnswerAdvisor` (쿼리 압축 후 벡터 검색)
- `EgovDocumentServiceImpl`: `EgovDocumentHashUtil`로 해시 비교 후 변경된 문서만 재임베딩
- Think 태그 처리: `EgovThinkTagOutputConverter`를 응답 후처리에 적용 (qwen3 전용)

### Common Patterns
```java
// ChatClient Advisor 체인 구성 패턴 (EgovSessionAwareChatServiceImpl)
chatClient.prompt()
    .advisors(
        compressionTransformer,   // 1. 쿼리 압축
        questionAnswerAdvisor,    // 2. RAG 검색
        new MessageChatMemoryAdvisor(chatMemory, sessionId, maxMessages) // 3. 세션 메모리
    )
    .user(userMessage)
    .stream().content();
```

## Dependencies

### Internal
- `chat/config/EgovRagConfig.java` — Advisor 빈 주입
- `chat/config/EgovChatMemoryConfig.java` — ChatMemory 빈 주입
- `chat/util/` — 응답 정리, Think 태그 제거, 문서 해시
- `service/LlmRouterService.java` — 모델 라우팅

### External
- Spring AI `ChatClient`, `MessageChatMemoryAdvisor`, `QuestionAnswerAdvisor`
- Spring AI PDF Document Reader

<!-- MANUAL: -->
