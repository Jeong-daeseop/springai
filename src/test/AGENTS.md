<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# test

## Purpose
JUnit 5 기반 테스트 코드 루트. 현재 애플리케이션 컨텍스트 로드 테스트만 존재하며 향후 단위/통합 테스트가 추가될 예정입니다.

## Key Files

| File | Description |
|------|-------------|
| `java/com/krdevops/springai/SpringaiApplicationTests.java` | Spring Boot 컨텍스트 로드 기본 테스트 |

## For AI Agents

### Working In This Directory
- 테스트 클래스는 `java/com/krdevops/springai/` 패키지 하위에 작성
- 실행: `./gradlew test`

### Testing Requirements
- MCP 서버는 `stdio` 모드이므로 HTTP 통합 테스트 대신 서비스 레이어 단위 테스트 권장
- DB 테스트 시 Docker `egov-mysql` 컨테이너 기동 필요: `docker start egov-mysql`

<!-- MANUAL: -->
