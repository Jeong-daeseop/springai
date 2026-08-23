package com.krdevops.springai.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ARCH-0307/0308/0309: 실제 로컬 MySQL(egov-mysql 컨테이너)을 대상으로 empty DB migration,
 * 기존(데이터 있는) DB baseline/upgrade, 반복 실행 안전성을 검증한다.
 *
 * <p>DB 연결이 필요한 통합 테스트라 {@code -Pci}에서 제외된다(다른 {@code *IntegrationTest}와 동일).
 */
class FlywayMigrationIntegrationTest {

    private static final String HOST_URL_PREFIX =
            "jdbc:mysql://localhost:3306/";
    private static final String URL_SUFFIX =
            "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    private static final String DB_USER = System.getenv().getOrDefault("DB_USERNAME", "ebt");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "ebt01");
    // ebt 계정은 ebt/let 스키마에만 국한된 최소 권한이라(GRANT ALL ON ebt.*) 새 스키마를
    // 만들 수 없다. root는 이 테스트가 스키마 생성·권한 부여·삭제(cleanup)에만 짧게 쓰고,
    // 실제 Flyway migration 자체는 항상 ebt 계정으로 실행해 운영과 동일한 권한 경계를 지킨다.
    private static final String ROOT_PASSWORD = System.getenv().getOrDefault("DB_ROOT_PASSWORD", "admin");

    private String createdTestSchema;

    private JdbcTemplate rootJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(HOST_URL_PREFIX + URL_SUFFIX, "root", ROOT_PASSWORD));
    }

    @AfterEach
    void dropTestSchema() {
        if (createdTestSchema == null) {
            return;
        }
        rootJdbc().execute("DROP DATABASE IF EXISTS " + createdTestSchema);
    }

    private String createGrantedTestSchema() {
        String schema = "flyway_empty_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate root = rootJdbc();
        root.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        root.execute("GRANT ALL PRIVILEGES ON " + schema + ".* TO '" + DB_USER + "'@'%'");
        root.execute("FLUSH PRIVILEGES");
        return schema;
    }

    @Test
    void emptyDatabaseMigration_createsAllExpectedTables() {
        createdTestSchema = createGrantedTestSchema();

        // 완전히 새 DB이므로 baseline 설정 없이 모든 migration이 그대로 실행되어야 한다(ARCH-0307).
        Flyway flyway = Flyway.configure()
                .dataSource(HOST_URL_PREFIX + createdTestSchema + URL_SUFFIX, DB_USER, DB_PASSWORD)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // 미래에 migration이 늘어나도 깨지지 않도록, 정확한 총 개수 대신 이미 있는 최소
        // 테이블 집합(V1)이 전부 포함됐는지와 migrate()가 pending 없이 끝났는지만 확인한다.
        JdbcTemplate testSchemaJdbc = new JdbcTemplate(
                new DriverManagerDataSource(HOST_URL_PREFIX + createdTestSchema + URL_SUFFIX, DB_USER, DB_PASSWORD));
        List<String> tables = testSchemaJdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME LIKE 'AI\\_%'",
                String.class, createdTestSchema);

        assertThat(tables).contains(
                "AI_COMPONENT_REGISTRY", "AI_DESIGN_ANALYSIS", "AI_DESIGN_SYSTEM_PROFILE",
                "AI_DESIGN_CODE_COMPONENT_MAPPING",
                "AI_FIGMA_DESIGN_OPERATION", "AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY",
                "AI_FIGMA_GENERATION_REPORT", "AI_FIGMA_REVIEW_HISTORY", "AI_FIGMA_SCREEN_SPEC",
                "AI_FIGMA_REFINEMENT_SET", "AI_FIGMA_REFINEMENT_PATCH",
                "AI_GENERATION_HISTORY", "AI_SCREEN_SPECIFICATION",
                "AI_THYMELEAF_PROJECT_OPERATION", "AI_THYMELEAF_PROJECT_OPERATION_IDEMPOTENCY");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void existingPopulatedDatabase_baselinesWithoutModifyingData() {
        // 이 프로젝트의 실제 개발 DB(ebt)는 이미 데이터가 있는 상태에서 Flyway를 도입했다.
        // baseline-on-migrate=true + baseline-version=1이면 V1을 실행하지 않고 기록만 해야 한다.
        JdbcTemplate ebt = new JdbcTemplate(
                new DriverManagerDataSource(HOST_URL_PREFIX + "ebt" + URL_SUFFIX, DB_USER, DB_PASSWORD));
        Long beforeCount = ebt.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'ebt' AND TABLE_NAME LIKE 'AI\\_%'",
                Long.class);

        Flyway flyway = Flyway.configure()
                .dataSource(HOST_URL_PREFIX + "ebt" + URL_SUFFIX, DB_USER, DB_PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        flyway.migrate();

        Long afterCount = ebt.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'ebt' AND TABLE_NAME LIKE 'AI\\_%'",
                Long.class);

        // baseline이 데이터를 지우거나 만들지 않는다는 것만 확인한다(V1은 실행되지 않고 기록만
        // 되므로 afterCount는 최소 beforeCount 이상이어야 한다 — 이후 신규 migration이 새
        // 테이블을 더할 수 있으니 정확히 같다고 단정하지 않는다).
        assertThat(afterCount).isGreaterThanOrEqualTo(beforeCount);

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void repeatedMigrate_onSameTargetIsIdempotentWithStableChecksum() {
        Flyway flyway = Flyway.configure()
                .dataSource(HOST_URL_PREFIX + "ebt" + URL_SUFFIX, DB_USER, DB_PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        // 반복 실행해도 checksum 불일치나 예외 없이 항상 같은 결과여야 한다(ARCH-0309).
        flyway.migrate();
        MigrationInfo firstRun = flyway.info().current();
        flyway.migrate();
        MigrationInfo secondRun = flyway.info().current();

        assertThat(secondRun.getChecksum()).isEqualTo(firstRun.getChecksum());
        assertThat(secondRun.getVersion()).isEqualTo(firstRun.getVersion());
    }

    @Test
    void checksumMismatch_blocksSubsequentMigrateInsteadOfSilentlyApplying() {
        // ARCH-0304: validate-on-migrate가 실제로 기동을 막는지 checksum을 임의로 깨뜨려 확인한다.
        createdTestSchema = createGrantedTestSchema();
        Flyway flyway = Flyway.configure()
                .dataSource(HOST_URL_PREFIX + createdTestSchema + URL_SUFFIX, DB_USER, DB_PASSWORD)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        JdbcTemplate root = rootJdbc();
        root.update("UPDATE " + createdTestSchema + ".flyway_schema_history SET checksum = -1 WHERE version = '1'");

        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
    }
}
