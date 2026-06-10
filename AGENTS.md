<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# springai

## Purpose
Spring Boot 4.1.0-RC1 + Spring AI 2.0.0-RC1 기반 MCP(Model Context Protocol) 서버 애플리케이션.
eGovFrame 5.0 표준 CRUD 소스 자동 생성, RAG(Retrieval Augmented Generation) 기반 문서 검색,
다중 LLM 도구 오케스트레이션을 Claude Desktop/Web에 노출하는 MCP 서버입니다.
Streamable HTTP 트랜스포트로 클라이언트와 JSON-RPC 통신하며, 웹 UI(Thymeleaf + SSE)도 제공합니다.

## Key Files

| File | Description |
|------|-------------|
| `build.gradle` | Gradle 빌드 설정 — Spring AI 2.0.0-RC1, Spring Boot 4.1.0-RC1, Redis, Ollama, OpenAI 의존성 |
| `settings.gradle` | Gradle 프로젝트 이름(springai) 설정 |
| `CLAUDE.md` | Claude Code 전용 가이드 — 빌드 명령, 아키텍처, MCP Tool 등록 패턴 |
| `.env.example` | 필수 환경변수 템플릿 (OPENAI_API_KEY, APP_API_KEY, DB_USERNAME 등) |
| `gradlew` / `gradlew.bat` | Gradle Wrapper 실행 스크립트 |
| `HELP.md` | Spring Initializr 기본 도움말 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/` | 애플리케이션 소스 코드 전체 (see `src/AGENTS.md`) |
| `docs/` | 설계 문서, 구현 분석, 아키텍처 결정 기록 (50+ 마크다운 파일) |
| `gradle/wrapper/` | Gradle Wrapper 바이너리 및 설정 |
| `logs/` | 런타임 로그 파일 |
| `org/` | Spring AI 참조 소스 (QuestionAnswerAdvisor 등 커스터마이징 참고용) |

## For AI Agents

### Working In This Directory
- 새 MCP Tool 추가: `tools/` 패키지에 `@Tool` 클래스 작성 → `config/McpConfig.java`의 `allToolCallbacks` 빈에 등록
- 빌드: `./gradlew bootJar` → `build/libs/springai-0.0.1-SNAPSHOT.jar`
- 로그: `/tmp/springai-mcp.log` (stdout은 JSON-RPC 전용 — 출력 오염 금지)
- `application.yaml`에서 `web-application-type: servlet`, `protocol: STREAMABLE` 유지 필수
- Transport: Streamable HTTP (`/mcp/**` 경로) — stdio가 아님

### Testing Requirements
- `./gradlew test` — 전체 테스트
- `./gradlew test --tests "com.krdevops.springai.SpringaiApplicationTests"` — 단일 클래스
- MCP 연결 로그: `~/Library/Logs/Claude/mcp-server-springai-mcp.log`

### Common Patterns
- MCP Tool: `@Tool(description = "한국어 상세 설명")` + `McpConfig`에 `MethodToolCallbackProvider` 등록
- DB 접근: `JdbcTemplate` (MyBatis 미사용)
- 레이어드 아키텍처: Controller → Service → Repository/Tool
- 보안: X-API-Key 헤더 인증 (`/api/**`), `/mcp/**` · `/ai/**` · `/api/chat/**`는 인증 없이 허용

## Dependencies

### External
- Spring Boot 4.1.0-RC1 — 애플리케이션 프레임워크
- Spring AI 2.0.0-RC1 — AI/LLM 통합 (MCP Streamable HTTP, RAG, Ollama, OpenAI)
- Redis Stack — 채팅 메모리 + Vector Store (RedisSearch)
- Ollama — 로컬 LLM (qwen3:8b 기본, 쿼리 압축은 qwen3:1.7b)
- OpenAI — 클라우드 LLM (gpt-4o-mini 기본)
- ONNX Transformers — 로컬 임베딩 (ko-sroberta-multitask)
- Docker `egov-mysql` — eGovFrame MySQL DB (port 3306)

<!-- MANUAL: -->
