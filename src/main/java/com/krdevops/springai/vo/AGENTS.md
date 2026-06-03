<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# vo

## Purpose
Value Object 패키지. DB 테이블과 매핑되는 도메인 객체를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `EmployeeVO.java` | `COMTNEMPLYRINFO` 테이블 매핑 VO — 직원 정보 필드 정의 |

## For AI Agents

### Working In This Directory
- VO 클래스는 DB 컬럼과 1:1 매핑
- Lombok `@Data` 또는 `@Getter`/`@Setter` 사용
- 비즈니스 로직 포함 금지 — 순수 데이터 컨테이너

### Common Patterns
```java
@Data
public class MyVO {
    private String id;
    private String name;
    // DB 컬럼과 매핑
}
```

## Dependencies

### Internal
- `mapper/` — Repository에서 VO를 조회 결과 매핑에 사용

<!-- MANUAL: -->
