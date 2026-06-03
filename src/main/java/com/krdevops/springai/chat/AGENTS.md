<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat

## Purpose
채팅 세션 관리, RAG 통합, Ollama LLM 연동을 담당하는 서브 도메인 패키지.
세션 기반 대화 컨텍스트 유지, 문서 임베딩/검색, 스트리밍 응답을 처리합니다.

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `config/` | 채팅 전용 설정 — AsyncConfig, ChatMemoryConfig, RagConfig, RedisConfig (see `config/AGENTS.md`) |
| `context/` | 세션 컨텍스트 객체 — SessionContext (see `context/AGENTS.md`) |
| `controller/` | 채팅 HTTP 컨트롤러 5종 (see `controller/AGENTS.md`) |
| `dto/` | 채팅 DTO — ChatMessageDto, ChatSession (see `dto/AGENTS.md`) |
| `repository/` | Redis 채팅 메모리 Repository (see `repository/AGENTS.md`) |
| `response/` | 응답 DTO — DocumentStatusResponse, TechnologyResponse (see `response/AGENTS.md`) |
| `service/` | 채팅 서비스 인터페이스 4종 + 구현체 (see `service/AGENTS.md`) |
| `util/` | 채팅 유틸리티 — 해시, 프롬프트 템플릿, 응답 정제, Think 태그 파서 (see `util/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 채팅 기능 추가 시 이 패키지 내 레이어드 아키텍처 준수
- Redis 세션 저장소 의존: `docker start egov-mysql` 외에 Redis 서버도 필요

### Common Patterns
- 세션 식별자 기반 대화 컨텍스트 유지
- Ollama 로컬 LLM → Spring AI ChatClient → 스트리밍 응답

## Dependencies

### Internal
- `service/RagService.java` — VectorStore 기반 문서 검색
- `config/VectorStoreConfig.java` — VectorStore 빈

### External
- Redis — 채팅 메모리 영속화
- Ollama — 로컬 LLM 엔진
- Spring AI ChatClient / VectorStore

<!-- MANUAL: -->
