package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** R1-T04/T05: DesignSystemProfileRepository 버전별 저장·조회와 스키마 초기화 반복 실행 안전성. */
class DesignSystemProfileRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final DesignSystemProfileRepository repository = new DesignSystemProfileRepository(
            jdbcTemplate, new ObjectMapper().findAndRegisterModules());

    @Test
    void createTableIfNotExistsIsIdempotent() {
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
    }

    @Test
    void findLatestReturnsMostRecentlySavedVersion() {
        repository.createTableIfNotExists();
        String profileId = "test-" + UUID.randomUUID();
        try {
            repository.save(profile(profileId, "1.0", DesignSystemProfile.Status.DRAFT));
            repository.save(profile(profileId, "1.1", DesignSystemProfile.Status.PUBLISHED));

            assertThat(repository.findVersion(profileId, "1.0")).isPresent()
                    .get().extracting(DesignSystemProfile::status).isEqualTo(DesignSystemProfile.Status.DRAFT);
            assertThat(repository.findLatest(profileId)).isPresent()
                    .get().extracting(DesignSystemProfile::version).isEqualTo("1.1");
        } finally {
            jdbcTemplate.update("DELETE FROM AI_DESIGN_SYSTEM_PROFILE WHERE PROFILE_ID = ?", profileId);
        }
    }

    private DesignSystemProfile profile(String id, String version, DesignSystemProfile.Status status) {
        return new DesignSystemProfile(id, "테스트 프로필", version, "2026.07", null, status, Map.of(), Map.of());
    }
}
