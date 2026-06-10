<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# resources

## Purpose
애플리케이션 설정 파일, Thymeleaf 템플릿, 정적 자원, 로깅 설정이 위치하는 디렉터리입니다.

## Key Files

| File | Description |
|------|-------------|
| `application.yaml` | 핵심 설정 — MCP Streamable HTTP, Ollama, OpenAI, Redis Vector Store, RAG, 보안, DB |
| `logback-spring.xml` | Logback 로깅 설정 — `/tmp/springai-mcp.log` 파일 출력 전용 (stdout 오염 방지) |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `templates/` | Thymeleaf HTML 템플릿 (see `templates/AGENTS.md`) |
| `static/` | 정적 자원 (JS 라이브러리 등, see `static/AGENTS.md`) |
| `mapper/` | MyBatis XML Mapper (현재 미사용 — JdbcTemplate 사용) |
| `model/` | ONNX 모델 파일 위치 (gitkeep — 실제 파일은 `~/models/ko-sroberta/` 참조) |

## For AI Agents

### Working In This Directory
- `application.yaml` 수정 시 환경변수 기본값 패턴 유지: `${VAR:defaultValue}`
- stdout 출력 추가 금지 — logback은 파일만 기록
- `web-application-type: servlet`, `protocol: STREAMABLE` 변경 금지

### Common Patterns
```yaml
# 환경변수 + 기본값 패턴
url: ${REDIS_URI:redis://localhost:6379}
model: ${OLLAMA_MODEL:qwen3:8b}
```

## Dependencies

### External
- Spring Boot 자동 설정 (`application.yaml` 키 바인딩)
- Logback (`logback-spring.xml` 프로파일 기반 설정)

<!-- MANUAL: -->
