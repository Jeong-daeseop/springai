<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# mapper

## Purpose
JdbcTemplate 기반 Repository 클래스 패키지. eGovFrame MySQL DB(`egov-mysql`)에 직접 접근합니다.
MyBatis 미사용 — Spring Boot 4.x 호환성으로 JdbcTemplate 사용.

## Key Files

| File | Description |
|------|-------------|
| `EmployeeRepository.java` | `COMTNEMPLYRINFO` 테이블 CRUD — 직원 목록 조회, 단건 조회, 등록/수정/삭제 |
| `GenerationHistoryRepository.java` | eGovFrame 코드 생성 이력 저장/조회 — 생성된 파일 경로, 테이블명, 타임스탬프 |

## For AI Agents

### Working In This Directory
- SQL은 클래스 내 `String` 상수 또는 인라인으로 관리 (MyBatis XML 없음)
- SQL 파라미터는 반드시 `?` 플레이스홀더 사용 (SQL Injection 방지)
- DB: `com` / User: `com` / Password: `com01` (Docker `egov-mysql`, port 3306)

### Common Patterns
```java
// JdbcTemplate 조회 패턴
jdbcTemplate.query("SELECT * FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?",
    ps -> ps.setString(1, emplyrId),
    (rs, rowNum) -> mapToVO(rs));
```

## Dependencies

### External
- Spring JDBC `JdbcTemplate`
- Docker `egov-mysql` (mysql:8.0, port 3306)

<!-- MANUAL: -->
