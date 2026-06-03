<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/config/rag/transformers

## Purpose
Spring AI RAG 파이프라인의 쿼리 변환기 커스터마이징 패키지.
`org/springframework/ai/chat/client/advisor/` 참조 소스를 기반으로 구현된 커스텀 변환기를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovCompressionQueryTransformer.java` | 대화 이력을 압축하여 독립적인 단일 질의로 변환 — 멀티턴 대화에서 RAG 검색 품질 향상 |

## For AI Agents

### Working In This Directory
- Spring AI `QueryTransformer` 인터페이스 구현체
- `org/springframework/ai/chat/client/advisor/vectorstore/` 참조 소스 변경 시 이 클래스와 호환성 확인
- `EgovRagConfig.java`에서 이 변환기를 빈으로 주입하여 사용

### Common Patterns
- `QueryTransformer` 인터페이스 구현
- 대화 이력(ChatMemory) + 현재 질의 → 압축된 독립 질의 생성

## Dependencies

### Internal
- `chat/config/EgovRagConfig.java` — 변환기 등록
- `org/springframework/ai/chat/client/advisor/vectorstore/QuestionAnswerAdvisor.java` — 참조 소스

### External
- Spring AI `QueryTransformer`
- Ollama LLM (압축 쿼리 생성)

<!-- MANUAL: -->
