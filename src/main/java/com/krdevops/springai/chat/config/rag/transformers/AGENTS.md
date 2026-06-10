<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/config/rag/transformers

## Purpose
RAG 쿼리 변환기 패키지. 벡터 검색 전 사용자 쿼리를 압축·정제하는 변환기를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovCompressionQueryTransformer.java` | 쿼리 압축 변환기 — qwen3:1.7b(경량 Ollama 모델)로 쿼리를 검색 최적화 문장으로 압축, `rag.enable-query-compression=false` 시 통과(passthrough) |

## For AI Agents

### Working In This Directory
- 압축 모델: `rag.compression.model` 설정값 (기본 `qwen3:1.7b`) — 항상 Ollama 로컬 처리
- `rag.enable-query-compression: false`로 비활성화하면 원본 쿼리 그대로 사용
- 압축 결과는 `QuestionAnswerAdvisor`에 전달되어 Vector Store 유사 검색에 사용됨
- 주의: 압축 실패(Ollama 미실행 등) 시 원본 쿼리로 폴백 처리 여부 확인 필요

## Dependencies

### Internal
- `chat/config/EgovRagConfig.java` — 빈 등록 및 `QuestionAnswerAdvisor`와 연결

### External
- Spring AI `QueryTransformer` 인터페이스
- Ollama ChatClient (qwen3:1.7b)

<!-- MANUAL: -->
