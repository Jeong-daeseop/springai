package com.krdevops.springai.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * WP7 6차 pass/ARCH-0704: {@link ProjectRootRegistryRepository}가 실 MySQL로 등록/조회/멱등
 * insert를 제공하는지 검증한다.
 */
class ProjectRootRegistryRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    private ProjectRootRegistryRepository newRepository() {
        return new ProjectRootRegistryRepository(jdbcTemplate);
    }

    @Test
    void unregisteredRoot_isNotRegistered() {
        ProjectRootRegistryRepository repository = newRepository();
        String root = "/tmp/never-registered-" + UUID.randomUUID();

        assertThat(repository.isRegistered(root)).isFalse();
    }

    @Test
    void registeredRoot_isRegistered() {
        ProjectRootRegistryRepository repository = newRepository();
        String root = "/tmp/registered-" + UUID.randomUUID();

        repository.register(root, "tester", "TEST");

        assertThat(repository.isRegistered(root)).isTrue();
    }

    @Test
    void registeringSameRootTwice_isIdempotent() {
        ProjectRootRegistryRepository repository = newRepository();
        String root = "/tmp/idempotent-" + UUID.randomUUID();

        repository.register(root, "tester", "TEST");

        assertThatCode(() -> repository.register(root, "tester-again", "TEST"))
                .doesNotThrowAnyException();
        assertThat(repository.isRegistered(root)).isTrue();
    }
}
