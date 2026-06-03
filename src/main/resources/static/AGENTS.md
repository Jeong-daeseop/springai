<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# resources/static

## Purpose
Spring Boot 정적 자원 루트. 브라우저에 직접 서빙되는 CSS, JavaScript 파일을 포함합니다.

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `js/` | JavaScript 라이브러리 및 스크립트 (see `js/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 정적 파일은 `/static/{경로}` URL로 자동 서빙 (Spring Boot 기본 설정)
- 외부 라이브러리 추가 시 CDN 대신 로컬 파일로 포함 권장 (오프라인 환경 대응)

<!-- MANUAL: -->
