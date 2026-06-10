<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# test

## Purpose
JUnit 5 기반 테스트 코드 루트. 운영 코드와 동일한 패키지 구조를 유지합니다.

## Key Files

| File | Description |
|------|-------------|
| `java/com/krdevops/springai/SpringaiApplicationTests.java` | 스프링 컨텍스트 로드 통합 테스트 |

## For AI Agents

### Working In This Directory
- 테스트 실행: `./gradlew test`
- 단일 클래스: `./gradlew test --tests "com.krdevops.springai.SpringaiApplicationTests"`
- JUnit 5 + Spring Boot Test 사용

### Testing Requirements
- 통합 테스트는 Redis, MySQL(egov-mysql Docker 컨테이너) 실행 상태 필요

<!-- MANUAL: -->
