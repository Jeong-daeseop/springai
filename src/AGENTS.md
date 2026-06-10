<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# src

## Purpose
애플리케이션 소스 코드 루트. Java 메인 소스(`main/`)와 테스트 소스(`test/`)로 구성됩니다.
표준 Maven/Gradle 디렉터리 레이아웃을 따릅니다.

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `main/` | 운영 코드 — Java 소스, 리소스, 템플릿 (see `main/AGENTS.md`) |
| `test/` | 테스트 코드 — JUnit 5 테스트 클래스 (see `test/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 새 기능은 `main/java/` 하위 적절한 패키지에 추가
- 테스트는 `test/java/` 하위 동일 패키지 구조 유지

### Testing Requirements
- `./gradlew test` — 전체 테스트 실행

<!-- MANUAL: -->