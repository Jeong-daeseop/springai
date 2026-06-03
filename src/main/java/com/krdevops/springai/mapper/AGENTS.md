<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# mapper (Repository)

## Purpose
데이터 접근 레이어. `JdbcTemplate` 기반 Repository 클래스를 포함합니다.
Spring Boot 4.x에서 MyBatis 미지원으로 인해 JdbcTemplate을 직접 사용합니다.

## Key Files

| File | Description |
|------|-------------|
| `EmployeeRepository.java` | `COMTNEMPLYRINFO` 테이블 CRUD — 직원 정보 조회/등록/수정/삭제 |
| `GenerationHistoryRepository.java` | 코드 생성 이력 저장/조회 — 생성된 eGovFrame 소스 추적 |

## For AI Agents

### Working In This Directory
- MyBatis 사용 불가 — 반드시 `JdbcTemplate` 사용
- SQL 인라인 작성 시 `#{}`스타일 대신 `?` 플레이스홀더 사용 (JdbcTemplate 방식)
- SQL Injection 방지를 위해 파라미터 바인딩 필수, 동적 SQL은 `StringBuilder` + 조건 분기

### Common Patterns
```java
@Repository
@RequiredArgsConstructor
public class MyRepository {
    private final JdbcTemplate jdbcTemplate;
    // ...
}
```

## Dependencies

### External
- Spring JDBC (`JdbcTemplate`)
- Docker `egov-mysql` 컨테이너 (DB: `com`, User: `com`, Password: `com01`)

<!-- MANUAL: -->
