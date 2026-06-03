<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# resources

## Purpose
Spring Boot 애플리케이션 리소스 루트. 설정 파일, 로깅 설정, HTML 템플릿, 정적 자원을 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `application.yaml` | Spring Boot 핵심 설정 — MCP stdio 모드, Redis, Ollama, VectorStore 설정 |
| `logback-spring.xml` | Logback 로깅 설정 — 파일 출력(`/tmp/springai-mcp.log`), stdout 억제 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `templates/` | Thymeleaf HTML 템플릿 (see `templates/AGENTS.md`) |
| `static/` | CSS/JS 정적 자원 (see `static/AGENTS.md`) |
| `mapper/` | MyBatis XML 매퍼 파일 (현재 미사용 — JdbcTemplate 사용 중) |
| `model/` | ML 모델 파일 저장 디렉토리 (.gitkeep) |

## For AI Agents

### Working In This Directory
- `application.yaml` 수정 시 `web-application-type: none`과 `transport: stdio` 절대 변경 금지
  — 변경 시 Claude Desktop MCP 연결이 끊김
- 로그 설정에서 stdout 출력을 활성화하면 JSON-RPC 프로토콜이 오염됨

### Common Patterns
- 환경별 설정은 `application-{profile}.yaml`로 분리
- 민감 정보(DB 비밀번호 등)는 환경 변수로 주입 권장

## Dependencies

### External
- Spring Boot Auto-configuration이 `application.yaml`을 자동 로드

<!-- MANUAL: -->
