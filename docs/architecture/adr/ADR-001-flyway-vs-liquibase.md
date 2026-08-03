# ADR-001: DB Migration 도구로 Flyway를 선택한다

> 상태: 채택
> 작성일: 2026-08-03
> 관련 작업: `ARCH-WP3` (`ARCH-0301`)

## 배경

9개 Repository가 `@PostConstruct`에서 `CREATE TABLE IF NOT EXISTS`를 직접 실행해 10개
애플리케이션 관리 테이블(`AI_*`)을 만든다. schema version이 없고, 여러 인스턴스가 동시에 기동하면
DDL 경쟁이 발생할 수 있으며, "실패 시 안전한 기동 실패"가 보장되지 않는다. 계획서
`ARCH-WP3`는 이를 versioned migration으로 전환하도록 요구한다.

## 검토한 선택지

### Flyway

- SQL 파일(`V1__xxx.sql`) 기반 — 이 프로젝트가 이미 전부 순수 SQL(JdbcTemplate raw SQL, MyBatis
  미사용)로 되어 있어 학습 비용 없이 그대로 옮길 수 있다.
- Spring Boot가 `flyway-core`를 classpath에서 감지하면 `DataSource` bean 생성 직후 자동으로
  migration을 실행한다(`FlywayAutoConfiguration`) — 별도 부트스트랩 코드 불필요.
- `spring.flyway.baseline-on-migrate=true` + `baseline-version`으로 "이미 데이터가 있는 기존
  DB"와 "완전히 새 DB"를 하나의 migration 세트로 함께 지원한다 — 지금 상황(로컬 `ebt` DB에
  이미 10개 테이블과 실 데이터가 있음)에 정확히 맞는 기능이다.
- migration 실패 시 기본적으로 애플리케이션 기동이 실패한다(`ARCH-0304` 요구사항을 별도 구현
  없이 충족).
- Spring Boot Gradle 플러그인의 dependency-management BOM이 버전을 관리해 `build.gradle`에
  버전 문자열을 직접 명시할 필요가 없다.

### Liquibase

- Changelog가 XML/YAML/JSON 중심이라, 이 프로젝트의 "순수 SQL, 프레임워크 추상화 최소화"
  기조와 맞지 않는다.
- rollback changeset을 별도로 관리해야 하는데, 이 프로젝트는 앞으로도 DB rollback보다
  `ApprovedProjectWritePort`류의 애플리케이션 레벨 보상 rollback(WP7)을 쓰는 방향이라 Liquibase의
  강점(구조화된 rollback)을 활용할 유인이 적다.
- 기능은 Flyway와 대등하지만, 이 프로젝트에 새 학습 비용을 추가할 이유가 없다.

## 결정

**Flyway**를 채택한다. `MySqlDatabaseType` 지원을 위해 `flyway-mysql`을 함께 추가한다(Flyway
10부터 DB별 모듈이 분리됨).

## 결과

- `src/main/resources/db/migration/`에 `V<n>__<description>.sql` 명명 규칙을 쓴다(`ARCH-0303`).
- 기존 10개 테이블은 `V1__baseline_existing_ai_tables.sql`로 표현한다(`ARCH-0305`).
- 신규 Operation/Artifact 스키마(`ARCH-0312`~`0317`)는 `ARCH-WP4`가 실제 Java 모델
  (`Operation`, `OperationRevision`, `OperationEvent` 등)을 확정한 뒤 추가 migration으로
  작성한다 — 지금 스키마를 먼저 만들면 모델 설계가 바뀔 때 migration을 또 고쳐야 하므로
  이 ADR 범위에서는 제외한다.
- `@PostConstruct` DDL은 이번 단계에서 제거하지 않고 `app.db.legacy-repository-ddl-enabled`
  feature flag 뒤로만 옮긴다(`ARCH-0310`). 전체 환경에서 Flyway만으로 충분함을 확인한 뒤
  별도 커밋으로 제거한다(`ARCH-0311`).
