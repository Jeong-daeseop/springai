<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/service/impl

## Purpose
채팅 서비스 인터페이스 구현체 패키지. Redis 채팅 메모리, RAG 검색, Ollama 스트리밍,
문서 임베딩 등 핵심 채팅 로직을 구현합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovSessionAwareChatServiceImpl.java` | 핵심 구현 — RAG + 채팅 메모리 + 멀티턴 대화 + Ollama 스트리밍 통합 |
| `EgovDocumentServiceImpl.java` | 문서 파싱 → 청킹 → 임베딩 → VectorStore 저장 파이프라인 |
| `EgovChatSessionServiceImpl.java` | Redis 기반 세션 생성/조회/삭제 구현 |
| `EgovOllamaModelServiceImpl.java` | Ollama REST API 호출로 설치 모델 목록 조회 |

## For AI Agents

### Working In This Directory
- `EgovSessionAwareChatServiceImpl.java`는 가장 복잡한 클래스 — RAG Advisor 체인, 쿼리 변환, 스트리밍 응답 통합
- 문서 임베딩 변경 시 `EgovDocumentServiceImpl.java`의 청킹 전략(`ChunkService`) 검토

### Common Patterns
```java
@Service
@RequiredArgsConstructor
public class EgovMyServiceImpl implements EgovMyService {
    private final SomeDependency dep;

    @Override
    public Result doSomething(Param param) { ... }
}
```

### Testing Requirements
- `EgovSessionAwareChatServiceImpl` 테스트 시 `ChatClient`, `VectorStore`, `ChatMemory` 모두 목 처리 필요

## Dependencies

### Internal
- `chat/config/EgovRagConfig.java` — RAG Advisor 빈 주입
- `chat/repository/EgovRedisChatMemoryRepository.java` — 대화 이력
- `chat/util/` — 응답 정제, 프롬프트 템플릿
- `service/RagService.java` — VectorStore 검색

### External
- Spring AI `ChatClient` / `VectorStore` / `ChatMemory`
- Ollama HTTP API

<!-- MANUAL: -->
