<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# springai

## Purpose
Spring Boot 4.0.6 + Spring AI 2.0.0-M6 기반 MCP(Model Context Protocol) 서버 애플리케이션.
eGovFrame 5.0 표준 CRUD 소스 자동 생성, RAG(Retrieval Augmented Generation) 기반 문서 검색,
다중 LLM 도구 오케스트레이션을 Claude Desktop에 노출하는 MCP 서버입니다.
HTTP 서버 없이 `stdio` 트랜스포트로 Claude Desktop과 JSON-RPC 통신합니다.

## Key Files

| File | Description |
|------|-------------|
| `build.gradle` | Gradle 빌드 설정 — Spring AI, Spring Boot, Redis, Ollama 의존성 관리 |
| `settings.gradle` | Gradle 프로젝트 이름 및 설정 |
| `CLAUDE.md` | Claude Code 전용 가이드 — 빌드 명령, 아키텍처, MCP Tool 등록 패턴 |
| `gradlew` / `gradlew.bat` | Gradle Wrapper 실행 스크립트 |
| `HELP.md` | Spring Initializr 기본 도움말 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/` | 애플리케이션 소스 코드 전체 (see `src/AGENTS.md`) |
| `docs/` | 설계 문서, 구현 분석, 아키텍처 결정 기록 (see `docs/AGENTS.md`) |
| `gradle/wrapper/` | Gradle Wrapper 바이너리 및 설정 |
| `logs/` | 런타임 로그 파일 |
| `org/` | Spring AI 참조 소스 (QuestionAnswerAdvisor 등 커스터마이징용) |

## For AI Agents

### Working In This Directory
- 새 MCP Tool 추가 시 `tools/` 패키지에 클래스 작성 후 `config/McpConfig.java`에 빈 등록 필수
- 빌드: `./gradlew bootJar` → `build/libs/springai-0.0.1-SNAPSHOT.jar`
- 로그는 stdout이 아닌 `/tmp/springai-mcp.log`에만 기록 (JSON-RPC stdout 오염 방지)
- `application.yaml`에서 `web-application-type: none`, `transport: stdio` 유지 필수

### Testing Requirements
- `./gradlew test` — 전체 테스트
- `./gradlew test --tests "com.krdevops.springai.SpringaiApplicationTests"` — 단일 클래스
- Claude Desktop 재시작 후 MCP 연결 로그 확인: `~/Library/Logs/Claude/mcp-server-springai-mcp.log`

### Common Patterns
- MCP Tool: `@Tool(description = "한국어 상세 설명")` + `McpConfig`에 `MethodToolCallbackProvider` 등록
- DB 접근: `JdbcTemplate` (MyBatis 미지원)
- 레이어드 아키텍처: Controller → Service → Repository/Tool

## Dependencies

### External
- Spring Boot 4.0.6 — 애플리케이션 프레임워크
- Spring AI 2.0.0-M6 — AI/LLM 통합 (MCP, RAG, Ollama)
- Redis — 채팅 세션 메모리 저장소
- Ollama — 로컬 LLM 실행 엔진
- Docker `egov-mysql` — eGovFrame MySQL DB (port 3306)

<!-- MANUAL: -->